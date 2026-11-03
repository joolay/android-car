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
    private static final long MAX_SEND = 1024L * 1024; // per send: 1 MB
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
        return Prefs.flag(KEEP, true);
    }

    /** Called first thing when Car Launcher starts: tidies out logs older than 3 days, then logs this start. */
    static void atStart(Context c) {
        if (running || !on()) return;
        running = true;
        prune(c);
        // first thing, before the music: the logs since the last send (the previous drive), retried
        // every 15 s for 3 minutes while the box has no internet yet
        if (Prefs.flag(AUTO, true)) sendPrevious(c.getApplicationContext(), 0);
        long n = Prefs.lng("trace_starts", 0) + 1;
        Prefs.put("trace_starts", n);
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
        if (Prefs.flag(AUTO, true)) {
            final Context app = c.getApplicationContext();
            Ui.ui.postDelayed(() -> send(app, Prefs.lng("logs_auto_at", 0), true), 180000);
        }
    }

    private static void sendPrevious(Context app, int tries) {
        send(app, Prefs.lng("logs_auto_at", 0), true, () -> {
            if (tries < 12) Ui.ui.postDelayed(() -> sendPrevious(app, tries + 1), 15000);
            else event(app, "Previous logs couldn't be sent at start (no internet?)");
        });
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
        send(c, since, auto, null);
    }

    /** As above; `failed` runs (on the main thread) when the upload didn't go through. */
    static void send(Context c, long since, boolean auto, Runnable failed) {
        final Context app = c.getApplicationContext();
        final long started = System.currentTimeMillis();
        event(app, auto ? "=== Logs sent by themselves (after start)" : "=== Logs sent from Settings");
        Prefs.put(PENDING, true);
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
                    Prefs.put(PENDING, false);
                    if (!auto) Ui.ui.post(() -> toast(app, "There are no logs yet"));
                    return;
                }
                // at most 1 MB (quick to send): the event logs first,
                // then the Android logs, newest first; older ones that don't fit are left out
                java.util.Arrays.sort(fs, (x, y) -> {
                    boolean ex = x.getName().startsWith("events-"), ey = y.getName().startsWith("events-");
                    if (ex != ey) return ex ? -1 : 1;
                    return Long.compare(y.lastModified(), x.lastModified());
                });
                int left = 0;
                FileOutputStream fo = new FileOutputStream(zip);
                try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(fo)) {
                    for (File f : fs) {
                        // worst case the file doesn't shrink at all, so it only goes in if that still fits
                        if (fo.getChannel().size() + f.length() + 4096 > MAX_SEND) {
                            left++;
                            continue;
                        }
                        z.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
                        java.nio.file.Files.copy(f.toPath(), z);
                        z.closeEntry();
                    }
                }
                if (left > 0) event(app, "Logs: " + left + " older file(s) left out to stay under 1 MB");
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
                    Prefs.put(PENDING, false);
                    if (auto) Prefs.put("logs_auto_at", started);
                    result = auto ? null : "Logs sent";
                } else result = "Couldn't send the logs (" + code + "). Try again in Settings → Logs";
            } catch (Exception e) {
                result = "Couldn't send the logs: no connection? Try again in Settings → Logs";
            }
            final String r = result;
            final boolean bad = r != null && !"Logs sent".equals(r);
            Ui.ui.post(() -> {
                if (bad && failed != null) failed.run();
                if (r != null && !auto) toast(app, r);
                Panels.refresh();
            });
        });
    }

    private static void toast(Context c, String s) {
        android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_LONG).show();
    }

    /** Settings page. */
    static void settings() {
        SettingsPanels.open(() -> {
            Context c = Panels.ctx();
            android.widget.LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Car Launcher keeps a log of the last 3 days (what it did, and its own Android messages). "
                    + "When something goes wrong, tap Send so it can be looked at online.", 13, Ui.SUB, false));
            b.addView(Panels.switchRow(0, 0, "Keep logs", on() ? "On · the last 3 days are kept" : "Off · nothing is logged", on(), v -> {
                Prefs.put(KEEP, v);
                if (v) atStart(c);
                else {
                    running = false;
                    if (logcat != null) logcat.destroy();
                    logcat = null;
                }
                Panels.refresh();
            }), Widgets.top(10));
            b.addView(Panels.switchRow(0, 0, "Send by themselves after every start", "For now, while Car Launcher is being built: the new logs go online 3 minutes after each start",
                    Prefs.flag(AUTO, true), v -> Prefs.put(AUTO, v)), Widgets.top(6));
            long sz = size(c);
            b.addView(Ui.text(c, "Kept now: " + Math.max(sz > 0 ? 1 : 0, sz / 1024) + " KB", 12, Ui.SUB, false), Widgets.top(6));
            android.widget.LinearLayout btns = Ui.row(c);
            btns.addView(Ui.button(c, Prefs.flag(PENDING, false) ? "Send again" : "Send the last 3 days", true, () -> send(c)));
            btns.addView(Ui.gap(c, 10, 1));
            btns.addView(Ui.button(c, "Restart Car Launcher", false, () -> {
                if (MainActivity.I != null) MainActivity.I.restartApp();
            }));
            b.addView(btns, Widgets.top(12));
            return Panels.panel("Logs", b, 560, 330);
        });
    }
}
