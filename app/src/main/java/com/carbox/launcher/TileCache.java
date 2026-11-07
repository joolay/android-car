package com.carbox.launcher;

import android.content.Context;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * A big on-disk cache for the live map's tiles, fonts and icons. Every tile the map shows is kept on the box
 * (up to MAX_MB, oldest-used dropped first) and shown again with no signal, so the roads you've driven stay on the map.
 */
final class TileCache {
    private static final long MAX_MB = 3072; // 3 GB
    private static File dir;
    private static long approx = -1;

    static boolean wants(String host) {
        return host != null && (host.endsWith("tile.openstreetmap.org") || host.equals("tiles.openfreemap.org")
                || host.equals("protomaps.github.io"));
    }

    static WebResourceResponse serve(Context c, WebResourceRequest r) {
        try {
            if (!"GET".equals(r.getMethod())) return null;
            String url = r.getUrl().toString();
            File f = file(c, url);
            // the map's style and tile list point at the current map data, so they're refreshed daily (kept copy used offline)
            boolean live = url.contains("/styles/") || url.matches(".*tiles\\.openfreemap\\.org/planet/?(\\?.*)?$");
            boolean stale = live && f.exists() && System.currentTimeMillis() - f.lastModified() > 86400000L;
            if (f.exists() && !stale) { // road maps change slowly: a kept tile is shown as is (and counts as recently used)
                f.setLastModified(System.currentTimeMillis());
                return reply(url, new FileInputStream(f));
            }
            byte[] data = download(url);
            if (data != null) {
                save(f, data);
                return reply(url, new ByteArrayInputStream(data));
            }
            if (stale) return reply(url, new FileInputStream(f));
        } catch (Throwable ignored) {
        }
        return null; // let the WebView try by itself
    }

    private static WebResourceResponse reply(String url, InputStream in) {
        String mime = url.endsWith(".png") ? "image/png" : url.endsWith(".pbf") ? "application/x-protobuf"
                : url.contains("/sprites/") && url.contains(".json") ? "application/json" : url.endsWith(".json") ? "application/json" : "application/octet-stream";
        if (url.contains("tiles.openfreemap.org/styles/")) mime = "application/json";
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        return new WebResourceResponse(mime, null, 200, "OK", h, in);
    }

    private static byte[] download(String url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(10000);
            con.setRequestProperty("User-Agent", "CarLauncher/1.0 (personal car launcher)");
            if (con.getResponseCode() != 200) return null;
            try (InputStream in = con.getInputStream(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
                return o.toByteArray();
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private static synchronized File file(Context c, String url) throws Exception {
        if (dir == null) {
            dir = new File(c.getFilesDir(), "tilecache");
            dir.mkdirs();
        }
        byte[] d = MessageDigest.getInstance("SHA-1").digest(url.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return new File(dir, sb.toString());
    }

    private static synchronized void save(File f, byte[] data) {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        } catch (Throwable t) {
            f.delete();
            return;
        }
        if (approx < 0) approx = size();
        approx += data.length;
        if (approx > MAX_MB * 1024 * 1024) trim();
    }

    private static long size() {
        long s = 0;
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) s += f.length();
        return s;
    }

    /** Drops the least recently used tiles until the cache is down to 90% of its limit. */
    private static void trim() {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs, Comparator.comparingLong(File::lastModified));
        long total = 0;
        for (File f : fs) total += f.length();
        long goal = MAX_MB * 1024 * 1024 * 9 / 10;
        for (File f : fs) {
            if (total <= goal) break;
            total -= f.length();
            f.delete();
        }
        approx = total;
    }
}
