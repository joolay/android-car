package com.carbox.launcher;

import android.content.Context;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Logs, always kept for the last 3 days (Settings → System → Logs): Car Launcher's own Android log
 * for each start, plus what it does (starts, apps opened, pages, music, voice button, errors). "Send
 * the last 3 days" zips them and sends them online (ntfy.sh attachment on the crash topic, collected
 * into the repo's "crashes" branch under logs/ by crashes.yml).
 */
final class Trace {
    static final String KEEP = "logs_keep", PENDING = "trace_pending";
    /** TEMPORARY (owner's box, before the Play Store): send the logs by themselves after every start. */
    static final String AUTO = "logs_auto";
    private static final long KEEP_MS = 3 * 24 * 3600 * 1000L, MAX_BYTES = 40L * 1024 * 1024;
    private static boolean running;
    private static Process logcat;
    private static File dir;

    private Trace() {
    }

    private static File dir(Context c) {
        if (dir == null) {
            dir = new File(c.getFilesDir(), "trace");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    static boolean on() {
        return Store.flag(KEEP, true);
    }

    /** Called first thing when Car Launcher starts: tidies out logs older than 3 days, then logs this start. */
    static void atStart(Context c) {
        if (running || !on()) return;
        running = true;
        prune(c);
        long n = Store.lng("trace_starts", 0) + 1;
        Store.put("trace_starts", n);
        event(c, "=== Car Launcher started (" + About.version(c) + ", " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + ", Android " + android.os.Build.VERSION.RELEASE + ", box on for " + SystemClock.elapsedRealtime() / 1000 + " s)");
        try { // Car Launcher's own Android log for this start, kept to about 2 MB
            String day = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File f = new File(dir(c), "android-" + day + ".log");
            logcat = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "threadtime", "--pid=" + android.os.Process.myPid(),
                    "-f", f.getAbsolutePath(), "-r", "1024", "-n", "1"});
        } catch (Exception e) {
            event(c, "Android log not available: " + e);
        }
        // 3 minutes in (so the start-up is in it): send what's new since the last time, by itself
        if (Store.flag(AUTO, true)) {
            final Context app = c.getApplicationContext();
            U.ui.postDelayed(() -> send(app, Store.lng("logs_auto_at", 0), true), 180000);
        }
    }

    /** Logs older than 3 days go, and the oldest ones too if they've grown past ~40 MB. */
    private static void prune(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs == null) return;
        long now = System.currentTimeMillis(), total = 0;
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); // newest first
        for (File f : fs) {
            if (now - f.lastModified() > KEEP_MS || total + f.length() > MAX_BYTES) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            } else total += f.length();
        }
    }

    /** One line in the log. */
    static void event(Context c, String what) {
        if (!running || c == null) return;
        String day = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        try (FileWriter w = new FileWriter(new File(dir(c), "events-" + day + ".log"), true)) {
            w.write(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + "  " + what + "\n");
        } catch (Exception ignored) {
        }
    }

    static void event(String what) {
        event(MainActivity.I, what);
    }

    /** Size of the logs kept. */
    static long size(Context c) {
        long t = 0;
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) t += f.length();
        return t;
    }

    /** Zip the last 3 days and send them online (the logs stay on the box). */
    static void send(Context c) {
        send(c, 0, false);
    }

    /** Send the logs changed since `since` (0 = all); `auto` = by itself after a start (quiet unless it fails). */
    static void send(Context c, long since, boolean auto) {
        final Context app = c.getApplicationContext();
        final long started = System.currentTimeMillis();
        event(app, auto ? "=== Logs sent by themselves (after start)" : "=== Logs sent from Settings");
        Store.put(PENDING, true);
        Net.bg(() -> {
            String result;
            try {
                // the last few seconds are still in Android's buffer: take them straight from there first
                try {
                    File last = new File(dir(app), "android-last.log");
                    Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "--pid=" + android.os.Process.myPid(),
                            "-f", last.getAbsolutePath()}).waitFor();
                } catch (Exception ignored) {
                }
                File zip = new File(app.getCacheDir(), "logs.zip");
                File[] fs = dir(app).listFiles(f -> f.lastModified() > since);
                if (fs == null || fs.length == 0) {
                    Store.put(PENDING, false);
                    if (!auto) U.ui.post(() -> toast(app, "There are no logs yet"));
                    return;
                }
                try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
                    for (File f : fs) {
                        z.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
                        java.nio.file.Files.copy(f.toPath(), z);
                        z.closeEntry();
                    }
                }
                String name = (auto ? "autolog-" : "logs-") + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".zip";
                java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL("https://ntfy.sh/" + About.TOPIC).openConnection();
                h.setRequestMethod("PUT");
                h.setDoOutput(true);
                h.setConnectTimeout(20000);
                h.setReadTimeout(120000);
                h.setRequestProperty("Filename", name);
                h.setRequestProperty("Title", "Logs " + About.version(app));
                h.setRequestProperty("Message", "Last 3 days of logs from " + android.os.Build.MODEL + " (" + Math.max(1, zip.length() / 1024) + " KB)");
                h.setFixedLengthStreamingMode(zip.length());
                try (java.io.OutputStream o = h.getOutputStream()) {
                    java.nio.file.Files.copy(zip.toPath(), o);
                }
                int code = h.getResponseCode();
                h.disconnect();
                //noinspection ResultOfMethodCallIgnored
                zip.delete();
                if (code >= 200 && code < 300) {
                    Store.put(PENDING, false);
                    if (auto) Store.put("logs_auto_at", started);
                    result = auto ? null : "Logs sent";
                } else result = "Couldn't send the logs (" + code + "). Try again in Settings → Logs";
            } catch (Exception e) {
                result = "Couldn't send the logs: no connection? Try again in Settings → Logs";
            }
            final String r = result;
            U.ui.post(() -> {
                if (r != null && !auto) toast(app, r);
                Sheets.refresh();
            });
        });
    }

    private static void toast(Context c, String s) {
        android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_LONG).show();
    }

    /** Settings page. */
    static void settings() {
        SettingsPanels.open(() -> {
            Context c = Sheets.ctx();
            android.widget.LinearLayout b = U.col(c);
            b.addView(U.text(c, "Car Launcher keeps a log of the last 3 days (what it did, and its own Android messages). "
                    + "When something goes wrong, tap Send so it can be looked at online.", 13, U.SUB, false));
            b.addView(Sheets.switchRow(0, 0, "Keep logs", on() ? "On · the last 3 days are kept" : "Off · nothing is logged", on(), v -> {
                Store.put(KEEP, v);
                if (v) atStart(c);
                else {
                    running = false;
                    if (logcat != null) logcat.destroy();
                    logcat = null;
                }
                Sheets.refresh();
            }), Widgets.top(10));
            b.addView(Sheets.switchRow(0, 0, "Send by themselves after every start", "For now, while Car Launcher is being built: the new logs go online 3 minutes after each start",
                    Store.flag(AUTO, true), v -> Store.put(AUTO, v)), Widgets.top(6));
            long sz = size(c);
            b.addView(U.text(c, "Kept now: " + Math.max(sz > 0 ? 1 : 0, sz / 1024) + " KB", 12, U.SUB, false), Widgets.top(6));
            android.widget.LinearLayout btns = U.row(c);
            btns.addView(U.button(c, Store.flag(PENDING, false) ? "Send again" : "Send the last 3 days", true, () -> send(c)));
            btns.addView(U.gap(c, 10, 1));
            btns.addView(U.button(c, "Restart Car Launcher", false, () -> {
                if (MainActivity.I != null) MainActivity.I.restartApp();
            }));
            b.addView(btns, Widgets.top(12));
            return Sheets.panel("Logs", b, 560, 330);
        });
    }
}
