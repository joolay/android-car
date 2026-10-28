package com.carbox.launcher;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Offline map: a .pmtiles file on the box (picked once, read in place, never copied). The map page
 * asks for byte ranges of https://pmtiles.local/map.pmtiles and gets them straight from the file.
 * Maps come from Protomaps (a whole country or a drawn area, made with the pmtiles tool).
 */
final class OfflineMap {
    static final String URI = "pmtiles_uri", NAME = "pmtiles_name";
    static final int REQ_PICK = 44;
    private static ParcelFileDescriptor pfd;
    private static FileChannel ch;
    private static String open;

    private OfflineMap() {
    }

    static void pick(android.app.Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try {
            a.startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            android.widget.Toast.makeText(a, "No file picker on this box", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** From MainActivity.onActivityResult. */
    static void picked(Context c, Uri u) {
        try {
            c.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        String name = u.getLastPathSegment();
        try (android.database.Cursor cur = c.getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) name = cur.getString(0);
        } catch (Exception ignored) {
        }
        close();
        Store.put(URI, u.toString());
        Store.put(NAME, name == null ? "map.pmtiles" : name);
        Store.put(Store.MAP_PROVIDER, "pmtiles");
    }

    private static synchronized FileChannel channel(Context c) throws Exception {
        String u = Store.str(URI, "");
        if (ch != null && u.equals(open)) return ch;
        close();
        pfd = c.getContentResolver().openFileDescriptor(Uri.parse(u), "r");
        ch = new FileInputStream(pfd.getFileDescriptor()).getChannel();
        open = u;
        return ch;
    }

    private static synchronized void close() {
        try {
            if (ch != null) ch.close();
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {
        }
        ch = null;
        pfd = null;
        open = null;
    }

    static WebResourceResponse serve(Context c, WebResourceRequest r) {
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Access-Control-Allow-Headers", "Range, If-Match");
        h.put("Access-Control-Expose-Headers", "Content-Range, Content-Length, ETag");
        h.put("Accept-Ranges", "bytes");
        if ("OPTIONS".equalsIgnoreCase(r.getMethod()))
            return new WebResourceResponse("text/plain", null, 204, "No Content", h, new ByteArrayInputStream(new byte[0]));
        try {
            byte[] data;
            long size, from, to;
            synchronized (OfflineMap.class) {
                FileChannel f = channel(c);
                size = f.size();
                from = 0;
                to = size - 1;
                String range = null;
                for (Map.Entry<String, String> e : r.getRequestHeaders().entrySet())
                    if ("range".equalsIgnoreCase(e.getKey())) range = e.getValue();
                if (range != null && range.startsWith("bytes=")) {
                    String[] p = range.substring(6).split("-", 2);
                    if (!p[0].isEmpty()) from = Long.parseLong(p[0].trim());
                    if (p.length > 1 && !p[1].trim().isEmpty()) to = Math.min(size - 1, Long.parseLong(p[1].trim()));
                }
                if (to - from > 16 * 1024 * 1024) to = from + 16 * 1024 * 1024 - 1; // never the whole file at once
                ByteBuffer buf = ByteBuffer.allocate((int) (to - from + 1));
                long at = from;
                while (buf.hasRemaining()) {
                    int n = f.read(buf, at);
                    if (n <= 0) break;
                    at += n;
                }
                data = buf.array();
            }
            h.put("Content-Range", "bytes " + from + "-" + to + "/" + size);
            h.put("Content-Length", String.valueOf(data.length));
            return new WebResourceResponse("application/octet-stream", null, 206, "Partial Content", h, new ByteArrayInputStream(data));
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", null, 404, "Not Found", h, new ByteArrayInputStream(new byte[0]));
        }
    }
}
