package com.carbox.launcher;

import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Download a video wallpaper straight into the app: searches Wikimedia Commons (free videos, no account or key
 * needed), lists WebM clips with their size and licence, downloads the one you tap and sets it as the wallpaper.
 */
final class WallpaperVideos {
    private WallpaperVideos() {
    }

    private static final class Clip {
        String title, url, licence;
        int w, h, index;
        long bytes;
    }

    private static final List<Clip> results = new ArrayList<>();
    private static String query = "aquarium", status = "Search for a clip, then tap it to download";
    private static boolean searching;
    private static String loadingUrl;      // the clip being downloaded now
    private static int loadingPct;
    private static long lastRefresh;

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "wallpaper");
        d.mkdirs();
        return d;
    }

    private static String fileName(Clip k) {
        String n = k.title.replaceFirst("^File:", "").replaceAll("[^A-Za-z0-9._-]+", "_");
        return n.length() > 80 ? n.substring(n.length() - 80) : n;
    }

    static void open() {
        if (results.isEmpty() && !searching) search();
        Panels.showFull(WallpaperVideos::page);
    }

    private static void search() {
        searching = true;
        status = "Searching…";
        String q = Uri.encode(query.trim() + " filetype:video");
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=40&gsrsearch=" + q
                + "&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiextmetadatafilter=LicenseShortName";
        Net.get(url, null, (body, code) -> {
            searching = false;
            results.clear();
            if (body == null) {
                status = "Couldn't reach Wikimedia Commons: check the internet and search again";
            } else try {
                JSONObject pages = new JSONObject(body).optJSONObject("query") == null ? null : new JSONObject(body).getJSONObject("query").optJSONObject("pages");
                if (pages != null) for (java.util.Iterator<String> it = pages.keys(); it.hasNext(); ) {
                    JSONObject p = pages.getJSONObject(it.next());
                    JSONArray ii = p.optJSONArray("imageinfo");
                    if (ii == null || ii.length() == 0) continue;
                    JSONObject info = ii.getJSONObject(0);
                    if (!"video/webm".equals(info.optString("mime"))) continue; // WebM plays on the box; Ogg doesn't
                    Clip k = new Clip();
                    k.title = p.optString("title");
                    k.url = info.optString("url");
                    k.w = info.optInt("width");
                    k.h = info.optInt("height");
                    k.bytes = info.optLong("size");
                    k.index = p.optInt("index", 999);
                    JSONObject md = info.optJSONObject("extmetadata");
                    k.licence = md != null && md.optJSONObject("LicenseShortName") != null ? md.getJSONObject("LicenseShortName").optString("value") : "";
                    results.add(k);
                }
                Collections.sort(results, (a, b) -> {
                    int ra = a.w >= 1280 ? 0 : 1, rb = b.w >= 1280 ? 0 : 1; // HD clips first, then by how well they match
                    return ra != rb ? ra - rb : a.index - b.index;
                });
                status = results.isEmpty() ? "No WebM videos for “" + query + "”: try another word" : results.size() + " clips · tap one to download it";
            } catch (Exception e) {
                status = "Couldn't read the results";
            }
            Panels.refresh();
        });
        Panels.refresh();
    }

    private static View page() {
        Context c = Panels.ctx();
        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD, 18));
        Ui.pad(card, 20, 14, 20, 14);
        card.setLayoutParams(Panels.centered(700, 452));
        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.line(c, "Video wallpapers", 20, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        head.addView(Ui.iconButton(c, R.drawable.ic_check, "Done", Panels::close));
        card.addView(head);

        LinearLayout sb = Ui.row(c);
        sb.setGravity(Gravity.CENTER_VERTICAL);
        EditText e = Widgets.field("Search free videos (aquarium, reef, jellyfish…)", query, false, s -> query = s);
        sb.addView(e, new LinearLayout.LayoutParams(0, Ui.u(44), 1));
        sb.addView(Ui.gap(c, 8, 1));
        sb.addView(Ui.button(c, "Search", true, () -> {
            InAppKeyboard.hide();
            if (!searching) search();
        }));
        card.addView(sb, top(10));
        if (!results.isEmpty() && loadingUrl == null) card.addView(Ui.seg(c, "Try a sample: best aquarium match", false, () -> {
            Clip best = null; // best-matching clip that's HD but not huge
            for (Clip k : results) if (k.w >= 1280 && k.bytes < 40e6 && (best == null || k.index < best.index)) best = k;
            if (best == null) for (Clip k : results) if (k.bytes < 40e6) { best = k; break; }
            if (best != null) choose(Panels.ctx(), best);
        }), top(6));
        card.addView(Ui.line(c, status, 12, Ui.SUB, false), top(6));

        LinearLayout list = Ui.col(c);
        File dir = dir(c);
        for (Clip k : results) {
            final Clip fk = k;
            File f = new File(dir, fileName(k));
            boolean have = f.exists() && f.length() > 0 && !k.url.equals(loadingUrl);
            LinearLayout row = Ui.row(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Ui.pad(row, 12, 9, 12, 9);
            row.setBackground(Themes.inner(Ui.CARD2, 12));
            LinearLayout t = Ui.col(c);
            String name = k.title.replaceFirst("^File:", "").replaceAll("\\.webm$", "").replace('_', ' ');
            t.addView(Ui.line(c, name, 14, Ui.TEXT, true));
            t.addView(Ui.line(c, k.w + "×" + k.h + " · " + String.format(Locale.US, "%.0f MB", k.bytes / 1e6) + (k.licence.isEmpty() ? "" : " · " + k.licence), 11, Ui.SUB, false), top(2));
            row.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            String act = k.url.equals(loadingUrl) ? loadingPct + "%" : have ? "Use" : "Download";
            TextView btn = Ui.seg(c, act, have || k.url.equals(loadingUrl), () -> choose(c, fk));
            row.addView(btn);
            LinearLayout.LayoutParams rl = Ui.lp(Ui.MATCH, Ui.WRAP);
            rl.topMargin = Ui.u(6);
            list.addView(row, rl);
        }
        list.addView(Ui.line(c, "Videos from Wikimedia Commons, each under its own free licence", 11, Ui.MUTED, false), top(10));
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        LinearLayout.LayoutParams sl = Ui.weight(1, false);
        sl.topMargin = Ui.u(6);
        card.addView(sv, sl);
        return card;
    }

    private static void choose(Context c, Clip k) {
        File f = new File(dir(c), fileName(k));
        if (f.exists() && f.length() > 0 && !k.url.equals(loadingUrl)) {
            use(f);
            return;
        }
        if (loadingUrl != null) return; // one at a time
        loadingUrl = k.url;
        loadingPct = 0;
        Panels.refresh();
        final Context app = c.getApplicationContext();
        Net.bg(() -> {
            File tmp = new File(f.getPath() + ".part");
            boolean ok = false;
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(k.url).openConnection();
                h.setConnectTimeout(15000);
                h.setReadTimeout(20000);
                h.setRequestProperty("User-Agent", "CarBoxLauncher/2.0 (Android)");
                long total = h.getContentLengthLong() > 0 ? h.getContentLengthLong() : k.bytes;
                try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[65536];
                    long got = 0;
                    for (int n; (n = in.read(buf)) > 0; ) {
                        out.write(buf, 0, n);
                        got += n;
                        final int pct = total > 0 ? (int) (got * 100 / total) : 0;
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - lastRefresh > 700) {
                            lastRefresh = now;
                            Ui.ui.post(() -> {
                                loadingPct = pct;
                                Panels.refresh();
                            });
                        }
                    }
                }
                ok = tmp.length() > 0 && tmp.renameTo(f);
            } catch (Exception e) {
                Trace.event("Video download: " + e);
            }
            final boolean done = ok;
            if (!done) tmp.delete();
            Ui.ui.post(() -> {
                loadingUrl = null;
                status = done ? "Downloaded" : "Download failed: check the internet and try again";
                if (done) use(f);
                else Panels.refresh();
            });
        });
    }

    private static void use(File f) {
        Prefs.put("wall_video_uri", Uri.fromFile(f).toString());
        Prefs.put(Prefs.WALL, "video");
        if (MainActivity.I != null) MainActivity.I.applyWallpaper();
        status = "Now your wallpaper";
        Panels.close();
    }

    private static LinearLayout.LayoutParams top(float du) {
        LinearLayout.LayoutParams l = Ui.lp(Ui.MATCH, Ui.WRAP);
        l.topMargin = Ui.u(du);
        return l;
    }
}
