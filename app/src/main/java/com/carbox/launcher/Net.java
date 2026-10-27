package com.carbox.launcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal HTTP on a background pool; results come back on the main thread. */
final class Net {
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);

    private Net() {
    }

    interface Result {
        /** body is null on failure. */
        void on(String body, int code);
    }

    static void get(String url, Map<String, String> headers, Result r) {
        request("GET", url, headers, null, r);
    }

    static void post(String url, Map<String, String> headers, String body, Result r) {
        request("POST", url, headers, body, r);
    }

    static void request(String method, String url, Map<String, String> headers, String body, Result r) {
        pool.execute(() -> {
            String out = null;
            int code = -1;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(10000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", "CarBoxLauncher/2.0 (Android)");
                if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) c.setRequestProperty(e.getKey(), e.getValue());
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close();
                    out = bo.toString("UTF-8");
                }
                if (code >= 400) out = null;
            } catch (Exception ignored) {
                out = null;
            } finally {
                if (c != null) c.disconnect();
            }
            final String fo = out;
            final int fc = code;
            if (r != null) U.ui.post(() -> r.on(fo, fc));
        });
    }

    static void bg(Runnable r) {
        pool.execute(r);
    }
}
