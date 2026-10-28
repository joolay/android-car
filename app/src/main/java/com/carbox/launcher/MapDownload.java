package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Downloads an offline map of the area around the car, straight from the latest Protomaps world map
 * (a PMTiles file on the internet). Only the tiles inside the chosen circle are fetched, then a
 * small .pmtiles file is written in Car Launcher's own folder and used as the Offline map.
 */
final class MapDownload {
    private MapDownload() {
    }

    // ------------------------------------------------------------------ the panel

    private static volatile Thread job;
    private static volatile boolean cancel;
    private static volatile String status = "";
    private static volatile int percent = -1;
    private static volatile long estimate = -1;
    private static Plan plan;

    static void panel() {
        Sheets.Page pg = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            int km = Store.num("dl_km", 75), z = Store.num("dl_zoom", 15);
            b.addView(U.text(c, "Downloads the map around where the car is now, so the map works with no signal. It's saved on the box and used as the Offline map.", 13, U.TEXT2, false));
            b.addView(Sheets.row("Area", seg(c, "25 km", km == 25, () -> setKm(25)), seg(c, "75 km", km == 75, () -> setKm(75)),
                    seg(c, "150 km", km == 150, () -> setKm(150)), seg(c, "300 km", km == 300, () -> setKm(300)), seg(c, "500 km", km == 500, () -> setKm(500))), Widgets.top(10));
            b.addView(Sheets.row("Detail", seg(c, "Full (street level)", z == 15, () -> setZoom(15)), seg(c, "Less (smaller file)", z == 13, () -> setZoom(13))), Widgets.top(6));
            TextView st = U.text(c, "", 13, U.TEXT, false);
            ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            b.addView(st, Widgets.top(12));
            b.addView(bar, U.lp(U.MATCH, U.u(10)));
            LinearLayout r = U.row(c);
            boolean busy = job != null;
            if (busy) r.addView(U.button(c, "Stop", false, () -> cancel = true));
            else if (plan != null && plan.km == km && plan.zoom == z) {
                r.addView(U.button(c, "Download about " + mb(plan.bytes), true, () -> start(c, false)));
            } else r.addView(U.button(c, "Check size", true, () -> start(c, true)));
            b.addView(r, Widgets.top(10));
            String name = Store.str(OfflineMap.NAME, "");
            if (!name.isEmpty()) b.addView(U.text(c, "Current offline map: " + name, 12, U.SUB, false), Widgets.top(10));
            b.addView(U.text(c, "Map data © OpenStreetMap contributors, from Protomaps. Street-name lettering still needs internet the first time.", 11, U.SUB, false), Widgets.top(6));
            Runnable upd = new Runnable() {
                @Override
                public void run() {
                    if (!st.isAttachedToWindow() && st.getWidth() > 0) return;
                    st.setText(status.isEmpty() ? (Loc.has() ? "Centre: where the car is now" : "Waiting for GPS…") : status);
                    bar.setVisibility(percent >= 0 ? android.view.View.VISIBLE : android.view.View.GONE);
                    if (percent >= 0) bar.setProgress(percent);
                    U.ui.postDelayed(this, 400);
                }
            };
            U.ui.post(upd);
            return Sheets.panel("Download offline map", b);
        };
        if (Sheets.open()) Sheets.push(pg);
        else Sheets.show(pg);
    }

    private static TextView seg(Context c, String l, boolean on, Runnable r) {
        return U.seg(c, l, on, r);
    }

    private static void setKm(int km) {
        if (job != null) return;
        Store.put("dl_km", km);
        Sheets.refresh();
    }

    private static void setZoom(int z) {
        if (job != null) return;
        Store.put("dl_zoom", z);
        Sheets.refresh();
    }

    private static String mb(long b) {
        return b >= 1024L * 1024 * 1024 ? String.format(Locale.US, "%.1f GB", b / 1073741824.0) : Math.max(1, b / 1048576) + " MB";
    }

    private static void start(Context c, boolean onlyCheck) {
        if (job != null) return;
        if (!Loc.has()) {
            status = "Waiting for GPS: the map is downloaded around where the car is";
            return;
        }
        final double lat = Loc.lat, lon = Loc.lon;
        final int km = Store.num("dl_km", 75), z = Store.num("dl_zoom", 15);
        cancel = false;
        percent = 0;
        job = new Thread(() -> {
            try {
                if (onlyCheck || plan == null || plan.km != km || plan.zoom != z) plan = makePlan(lat, lon, km, z);
                if (!onlyCheck && !cancel) download(c, plan);
                else status = cancel ? "Stopped" : "About " + mb(plan.bytes) + " for " + km + " km around here. Tap Download.";
            } catch (Throwable t) {
                status = "Couldn't download: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
                About.record(c, "map download", t);
            } finally {
                percent = -1;
                job = null;
                U.ui.post(Sheets::refresh);
            }
        }, "map-download");
        job.start();
        Sheets.refresh();
    }

    // ------------------------------------------------------------------ PMTiles reading

    private static final class Header {
        long rootOff, rootLen, metaOff, metaLen, leafOff, leafLen, dataOff, dataLen;
        int internal, tileComp, tileType, minZ, maxZ;
    }

    /** One directory entry: tile id, offset, length, run length (0 = points to a leaf directory). */
    private static final class Dir {
        long[] id, off, len, run;
    }

    private static final class Plan {
        String url;
        int km, zoom;
        double lat, lon;
        Header h;
        byte[] meta;
        long[] ids, src;   // tile id and its source offset (within the tile data section)
        int[] len;
        long bytes;
    }

    private static String latestBuild() throws IOException {
        try {
            JSONArray a = new JSONArray(new String(get("https://build-metadata.protomaps.dev/builds.json", -1, -1), "UTF-8"));
            String best = null;
            for (int i = 0; i < a.length(); i++) {
                String k = a.getJSONObject(i).optString("key");
                if (k.endsWith(".pmtiles") && (best == null || k.compareTo(best) > 0)) best = k;
            }
            if (best != null) return "https://build.protomaps.com/" + best;
        } catch (Exception ignored) {
        }
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
        for (int i = 0; i < 14; i++) { // builds are daily; find the newest one that's there
            String u = "https://build.protomaps.com/" + f.format(cal.getTime()) + ".pmtiles";
            try {
                if (get(u, 0, 16).length == 16) return u;
            } catch (IOException ignored) {
            }
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        throw new IOException("no map build found online");
    }

    /** Bytes [off, off+len) of a web file (len -1 = all). */
    private static byte[] get(String u, long off, long len) throws IOException {
        HttpURLConnection h = open(u, off, len);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream o = new ByteArrayOutputStream(len > 0 ? (int) Math.min(len, 1 << 24) : 8192);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
                if (cancel) throw new IOException("stopped");
            }
            return o.toByteArray();
        } finally {
            h.disconnect();
        }
    }

    private static HttpURLConnection open(String u, long off, long len) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        h.setRequestProperty("User-Agent", "CarLauncher");
        if (off >= 0) h.setRequestProperty("Range", "bytes=" + off + "-" + (off + len - 1));
        int code = h.getResponseCode();
        if (code != 200 && code != 206) throw new IOException("server said " + code);
        if (off > 0 && code != 206) throw new IOException("server doesn't support partial downloads");
        return h;
    }

    private static Header header(byte[] b) throws IOException {
        if (b.length < 127 || b[0] != 'P' || b[1] != 'M' || b[7] != 3) throw new IOException("not a PMTiles v3 map");
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        Header h = new Header();
        h.rootOff = bb.getLong(8);
        h.rootLen = bb.getLong(16);
        h.metaOff = bb.getLong(24);
        h.metaLen = bb.getLong(32);
        h.leafOff = bb.getLong(40);
        h.leafLen = bb.getLong(48);
        h.dataOff = bb.getLong(56);
        h.dataLen = bb.getLong(64);
        h.internal = b[97];
        h.tileComp = b[98];
        h.tileType = b[99];
        h.minZ = b[100] & 0xFF;
        h.maxZ = b[101] & 0xFF;
        return h;
    }

    private static byte[] inflate(byte[] b, int comp) throws IOException {
        if (comp != 2) return b; // 1 = none
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(b))) {
            ByteArrayOutputStream o = new ByteArrayOutputStream(b.length * 4);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        }
    }

    private static Dir parse(byte[] raw) {
        int[] p = {0};
        int n = (int) varint(raw, p);
        Dir d = new Dir();
        d.id = new long[n];
        d.off = new long[n];
        d.len = new long[n];
        d.run = new long[n];
        long last = 0;
        for (int i = 0; i < n; i++) d.id[i] = last += varint(raw, p);
        for (int i = 0; i < n; i++) d.run[i] = varint(raw, p);
        for (int i = 0; i < n; i++) d.len[i] = varint(raw, p);
        for (int i = 0; i < n; i++) {
            long v = varint(raw, p);
            d.off[i] = v == 0 && i > 0 ? d.off[i - 1] + d.len[i - 1] : v - 1;
        }
        return d;
    }

    private static long varint(byte[] b, int[] p) {
        long v = 0;
        int shift = 0;
        while (true) {
            int x = b[p[0]++] & 0xFF;
            v |= (long) (x & 0x7F) << shift;
            if (x < 0x80) return v;
            shift += 7;
        }
    }

    /** Tile id on the Hilbert curve, as PMTiles numbers them. */
    static long tileId(int z, long x, long y) {
        long acc = ((1L << (2 * z)) - 1) / 3;
        long n = 1L << z, d = 0;
        for (long s = n / 2; s > 0; s /= 2) {
            long rx = (x & s) > 0 ? 1 : 0, ry = (y & s) > 0 ? 1 : 0;
            d += s * s * ((3 * rx) ^ ry);
            if (ry == 0) {
                if (rx == 1) {
                    x = s - 1 - x;
                    y = s - 1 - y;
                }
                long t = x;
                x = y;
                y = t;
            }
        }
        return acc + d;
    }

    private static Plan makePlan(double lat, double lon, int km, int maxZ) throws IOException {
        status = "Finding the latest world map…";
        Plan p = new Plan();
        p.url = latestBuild();
        p.km = km;
        p.zoom = maxZ;
        p.lat = lat;
        p.lon = lon;
        byte[] head = get(p.url, 0, 16384);
        p.h = header(head);
        Header h = p.h;
        maxZ = Math.min(maxZ, h.maxZ);
        byte[] rootRaw = h.rootOff + h.rootLen <= head.length ? Arrays.copyOfRange(head, (int) h.rootOff, (int) (h.rootOff + h.rootLen)) : get(p.url, h.rootOff, h.rootLen);
        Dir root = parse(inflate(rootRaw, h.internal));
        p.meta = h.metaLen > 0 ? get(p.url, h.metaOff, h.metaLen) : new byte[0];
        // every tile id inside the circle's box, zoom 0 up to the chosen detail
        double dLat = km / 111.32, dLon = km / (111.32 * Math.max(0.2, Math.cos(Math.toRadians(lat))));
        double w = lon - dLon, e = lon + dLon, s = Math.max(-85, lat - dLat), nn = Math.min(85, lat + dLat);
        long count = 0;
        for (int z = 0; z <= maxZ; z++) {
            long[] r = range(z, w, s, e, nn);
            count += (r[2] - r[0] + 1) * (r[3] - r[1] + 1);
        }
        if (count > 3_000_000) throw new IOException("that area is too big at this detail; choose a smaller area or less detail");
        long[] ids = new long[(int) count];
        int k = 0;
        for (int z = 0; z <= maxZ; z++) {
            long[] r = range(z, w, s, e, nn);
            for (long x = r[0]; x <= r[2]; x++) for (long y = r[1]; y <= r[3]; y++) ids[k++] = tileId(z, x, y);
        }
        Arrays.sort(ids);
        // look each one up in the map's directories (fetching the leaf directories it needs)
        long[] outId = new long[ids.length], src = new long[ids.length];
        int[] len = new int[ids.length];
        int found = 0;
        Map<Long, Dir> leaves = new HashMap<>();
        Map<Long, Boolean> unique = new HashMap<>();
        long bytes = 0;
        for (int i = 0; i < ids.length; i++) {
            if (cancel) throw new IOException("stopped");
            if ((i & 1023) == 0) {
                status = "Checking which map pieces are needed… " + (i * 100L / ids.length) + "%";
                percent = (int) (i * 100L / ids.length);
            }
            long id = ids[i];
            Dir d = root;
            for (int depth = 0; depth < 4 && d != null; depth++) {
                int j = find(d, id);
                if (j < 0) {
                    d = null;
                    break;
                }
                if (d.run[j] > 0) {
                    if (id < d.id[j] + d.run[j]) {
                        outId[found] = id;
                        src[found] = d.off[j];
                        len[found] = (int) d.len[j];
                        if (unique.put(d.off[j], Boolean.TRUE) == null) bytes += d.len[j];
                        found++;
                    }
                    d = null;
                    break;
                }
                long lo = h.leafOff + d.off[j];
                Dir leaf = leaves.get(lo);
                if (leaf == null) {
                    leaf = parse(inflate(get(p.url, lo, d.len[j]), h.internal));
                    leaves.put(lo, leaf);
                }
                d = leaf;
            }
        }
        p.ids = Arrays.copyOf(outId, found);
        p.src = Arrays.copyOf(src, found);
        p.len = Arrays.copyOf(len, found);
        p.bytes = bytes;
        percent = -1;
        return p;
    }

    private static int find(Dir d, long id) {
        int lo = 0, hi = d.id.length - 1, ans = -1;
        while (lo <= hi) {
            int m = (lo + hi) >>> 1;
            if (d.id[m] <= id) {
                ans = m;
                lo = m + 1;
            } else hi = m - 1;
        }
        return ans;
    }

    /** Tile x/y range covering a box at zoom z: {minX, minY, maxX, maxY}. */
    private static long[] range(int z, double w, double s, double e, double n) {
        long max = (1L << z) - 1;
        return new long[]{clamp(tx(w, z), max), clamp(ty(n, z), max), clamp(tx(e, z), max), clamp(ty(s, z), max)};
    }

    private static long clamp(long v, long max) {
        return Math.max(0, Math.min(max, v));
    }

    private static long tx(double lon, int z) {
        return (long) Math.floor((lon + 180) / 360 * (1L << z));
    }

    private static long ty(double lat, int z) {
        double r = Math.toRadians(lat);
        return (long) Math.floor((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * (1L << z));
    }

    // ------------------------------------------------------------------ download and write

    private static void download(Context c, Plan p) throws IOException {
        File dir = c.getExternalFilesDir("maps");
        if (dir == null) dir = new File(c.getFilesDir(), "maps");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("can't make the maps folder");
        if (dir.getUsableSpace() < p.bytes + p.bytes / 5 + 50L * 1024 * 1024) throw new IOException("not enough free space (" + mb(p.bytes) + " needed)");
        // unique pieces in file order, fetched in big runs
        long[] offs = p.src.clone();
        Arrays.sort(offs);
        int u = 0;
        for (int i = 0; i < offs.length; i++) if (i == 0 || offs[i] != offs[i - 1]) offs[u++] = offs[i];
        offs = Arrays.copyOf(offs, u);
        Map<Long, Integer> lenOf = new HashMap<>();
        for (int i = 0; i < p.src.length; i++) lenOf.put(p.src[i], p.len[i]);
        Map<Long, Long> newOff = new HashMap<>(offs.length * 2);
        File tmp = new File(dir, "download.tmp");
        long written = 0;
        try (OutputStream out = new java.io.BufferedOutputStream(new FileOutputStream(tmp), 1 << 16)) {
            int i = 0;
            while (i < offs.length) {
                if (cancel) throw new IOException("stopped");
                int j = i;
                long start = offs[i], end = offs[i] + lenOf.get(offs[i]);
                while (j + 1 < offs.length && offs[j + 1] - end < 256 * 1024 && offs[j + 1] + lenOf.get(offs[j + 1]) - start < 16L * 1024 * 1024) {
                    j++;
                    end = Math.max(end, offs[j] + lenOf.get(offs[j]));
                }
                byte[] run = get(p.url, p.h.dataOff + start, end - start);
                for (int k = i; k <= j; k++) {
                    int l = lenOf.get(offs[k]);
                    newOff.put(offs[k], written);
                    out.write(run, (int) (offs[k] - start), l);
                    written += l;
                }
                percent = (int) (written * 100 / Math.max(1, p.bytes));
                status = "Downloading… " + mb(written) + " of " + mb(p.bytes);
                i = j + 1;
            }
        }
        status = "Saving the map…";
        // the new map's directory: consecutive tiles with the same piece share one entry
        List<long[]> entries = new ArrayList<>();
        for (int i = 0; i < p.ids.length; i++) {
            long o = newOff.get(p.src[i]);
            long[] last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
            if (last != null && last[0] + last[3] == p.ids[i] && last[1] == o) last[3]++;
            else entries.add(new long[]{p.ids[i], o, p.len[i], 1});
        }
        byte[] root = dirBytes(entries);
        ByteArrayOutputStream leaves = new ByteArrayOutputStream();
        if (127 + root.length > 16384) {
            for (int size = 4096; ; size *= 2) {
                leaves.reset();
                List<long[]> rootE = new ArrayList<>();
                for (int i = 0; i < entries.size(); i += size) {
                    byte[] leaf = dirBytes(entries.subList(i, Math.min(entries.size(), i + size)));
                    rootE.add(new long[]{entries.get(i)[0], leaves.size(), leaf.length, 0});
                    leaves.write(leaf, 0, leaf.length);
                }
                root = dirBytes(rootE);
                if (127 + root.length <= 16384) break;
            }
        }
        long rootOff = 127, metaOff = rootOff + root.length, leafOff = metaOff + p.meta.length, dataOff = leafOff + leaves.size();
        ByteBuffer hb = ByteBuffer.allocate(127).order(ByteOrder.LITTLE_ENDIAN);
        hb.put("PMTiles".getBytes("US-ASCII")).put((byte) 3);
        hb.putLong(rootOff).putLong(root.length).putLong(metaOff).putLong(p.meta.length).putLong(leafOff).putLong(leaves.size())
                .putLong(dataOff).putLong(written).putLong(p.ids.length).putLong(entries.size()).putLong(newOff.size());
        hb.put((byte) 0).put((byte) 2).put((byte) p.h.tileComp).put((byte) p.h.tileType).put((byte) 0).put((byte) Math.min(p.zoom, p.h.maxZ));
        double dLat = p.km / 111.32, dLon = p.km / (111.32 * Math.max(0.2, Math.cos(Math.toRadians(p.lat))));
        hb.putInt((int) ((p.lon - dLon) * 1e7)).putInt((int) ((p.lat - dLat) * 1e7)).putInt((int) ((p.lon + dLon) * 1e7)).putInt((int) ((p.lat + dLat) * 1e7));
        hb.put((byte) Math.min(12, p.zoom)).putInt((int) (p.lon * 1e7)).putInt((int) (p.lat * 1e7));
        String name = "Around " + (Loc.town != null ? Loc.town : "here") + " " + p.km + " km " + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date()) + ".pmtiles";
        File outFile = new File(dir, name.replaceAll("[^A-Za-z0-9 ._-]", ""));
        OfflineMap.close();
        try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw"); FileChannel src = new java.io.FileInputStream(tmp).getChannel()) {
            raf.setLength(0);
            raf.write(hb.array());
            raf.write(root);
            raf.write(p.meta);
            raf.write(leaves.toByteArray());
            FileChannel dst = raf.getChannel();
            long pos = dataOff, done = 0, total = src.size();
            while (done < total) done += src.transferTo(done, total - done, dst.position(pos + done));
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        // older downloads are replaced by the new one (a file you picked yourself is left alone)
        File[] old = dir.listFiles();
        if (old != null) for (File f : old)
            if (!f.equals(outFile) && f.getName().endsWith(".pmtiles")) //noinspection ResultOfMethodCallIgnored
                f.delete();
        Store.put(OfflineMap.URI, android.net.Uri.fromFile(outFile).toString());
        Store.put(OfflineMap.NAME, outFile.getName());
        Store.put(Store.MAP_PROVIDER, "pmtiles");
        status = "Done: " + mb(outFile.length()) + ". The map now works offline around here.";
        U.ui.post(() -> {
            if (MainActivity.I != null) MainActivity.I.map.reload();
        });
    }

    private static byte[] dirBytes(List<long[]> e) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        put(o, e.size());
        long last = 0;
        for (long[] x : e) {
            put(o, x[0] - last);
            last = x[0];
        }
        for (long[] x : e) put(o, x[3]);
        for (long[] x : e) put(o, x[2]);
        for (int i = 0; i < e.size(); i++) {
            long[] x = e.get(i);
            if (i > 0 && x[1] == e.get(i - 1)[1] + e.get(i - 1)[2]) put(o, 0);
            else put(o, x[1] + 1);
        }
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(z)) {
            g.write(o.toByteArray());
        }
        return z.toByteArray();
    }

    private static void put(ByteArrayOutputStream o, long v) {
        while (v >= 0x80) {
            o.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write((int) v);
    }
}
