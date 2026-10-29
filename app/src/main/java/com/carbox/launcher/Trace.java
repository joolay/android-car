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
 * Troubleshooting mode: from the next start of Car Launcher until it's switched off, everything is
 * logged (Car Launcher's own Android log, plus what it does: starts, screens, music, voice, errors).
 * Switched off, the log is zipped and sent online (ntfy.sh attachment on the crash topic, collected
 * into the repo's "crashes" branch under logs/ by crashes.yml).
 */
final class Trace {
    static final String ON = "trace_on", SINCE = "trace_since", PENDING = "trace_pending";
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

    /** Called first thing when Car Launcher starts: logging begins if the mode is on. */
    static void atStart(Context c) {
        if (running || !Store.flag(ON, false)) return;
        running = true;
        long n = Store.lng("trace_starts", 0) + 1;
        Store.put("trace_starts", n);
        event(c, "=== Car Launcher started (" + About.version(c) + ", " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + ", Android " + android.os.Build.VERSION.RELEASE + ", box on for " + SystemClock.elapsedRealtime() / 1000 + " s)");
        try { // Car Launcher's own Android log for this start, kept to about 6 MB
            File f = new File(dir(c), "android-" + n + ".log");
            logcat = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "threadtime", "--pid=" + android.os.Process.myPid(),
                    "-f", f.getAbsolutePath(), "-r", "2048", "-n", "2"});
        } catch (Exception e) {
            event(c, "Android log not available: " + e);
        }
    }

    static boolean on() {
        return Store.flag(ON, false);
    }

    /** One line in the troubleshooting log (only while it's running). */
    static void event(Context c, String what) {
        if (!running || c == null) return;
        try (FileWriter w = new FileWriter(new File(dir(c), "events.log"), true)) {
            w.write(new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date()) + "  " + what + "\n");
        } catch (Exception ignored) {
        }
    }

    static void event(String what) {
        event(MainActivity.I, what);
    }

    /** Switch on: it starts logging from the next start of Car Launcher. */
    static void arm() {
        Store.put(ON, true);
        Store.put(SINCE, System.currentTimeMillis());
    }

    /** Switch off: stop, zip it all and send it online. */
    static void stopAndSend(Context c) {
        event(c, "=== Troubleshooting mode switched off");
        Store.put(ON, false);
        running = false;
        final Process lc = logcat;
        logcat = null;
        Store.put(PENDING, true);
        final Context app = c.getApplicationContext();
        Net.bg(() -> { // the last few seconds were still in the log's buffer: take them straight from Android first
            try {
                File f = new File(dir(app), "android-last.log");
                Process d = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "--pid=" + android.os.Process.myPid(), "-f", f.getAbsolutePath()});
                d.waitFor();
            } catch (Exception ignored) {
            }
            if (lc != null) lc.destroy();
            U.ui.post(() -> send(app));
        });
    }

    /** Size of what's been logged so far. */
    static long size(Context c) {
        long t = 0;
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) t += f.length();
        return t;
    }

    /** Zip the log and send it (kept until it has gone, so it can be sent again). */
    static void send(Context c) {
        final Context app = c.getApplicationContext();
        Net.bg(() -> {
            String result;
            try {
                File zip = new File(app.getCacheDir(), "troubleshooting.zip");
                File[] fs = dir(app).listFiles();
                if (fs == null || fs.length == 0) {
                    Store.put(PENDING, false);
                    U.ui.post(() -> toast(app, "There was nothing in the troubleshooting log"));
                    return;
                }
                try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(zip))) {
                    for (File f : fs) {
                        z.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
                        java.nio.file.Files.copy(f.toPath(), z);
                        z.closeEntry();
                    }
                }
                String name = "troubleshooting-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".zip";
                java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL("https://ntfy.sh/" + About.TOPIC).openConnection();
                h.setRequestMethod("PUT");
                h.setDoOutput(true);
                h.setConnectTimeout(20000);
                h.setReadTimeout(60000);
                h.setRequestProperty("Filename", name);
                h.setRequestProperty("Title", "Troubleshooting log " + About.version(app));
                h.setRequestProperty("Message", "Troubleshooting log from " + android.os.Build.MODEL + ", " + Store.lng("trace_starts", 0) + " starts");
                h.setFixedLengthStreamingMode(zip.length());
                try (java.io.OutputStream o = h.getOutputStream()) {
                    java.nio.file.Files.copy(zip.toPath(), o);
                }
                int code = h.getResponseCode();
                h.disconnect();
                //noinspection ResultOfMethodCallIgnored
                zip.delete();
                if (code >= 200 && code < 300) {
                    for (File f : fs) //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    Store.put(PENDING, false);
                    Store.put("trace_starts", 0L);
                    result = "Troubleshooting log sent";
                } else result = "Couldn't send the troubleshooting log (" + code + "). Try again in Settings → Troubleshooting";
            } catch (Exception e) {
                result = "Couldn't send the troubleshooting log: no connection? Try again in Settings → Troubleshooting";
            }
            final String r = result;
            U.ui.post(() -> {
                toast(app, r);
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
            b.addView(U.text(c, "Switch on and logging starts straight away. A restart of Car Launcher (or the box) is a good idea so the start-up is "
                    + "logged too. When the problem has happened, switch it off and the log is sent online, so it can be looked at.", 13, U.SUB, false));
            b.addView(Sheets.switchRow(0, 0, "Troubleshooting mode", on() ? "Logging now · switch off to send the log" : "Off",
                    on(), v -> {
                        if (v) {
                            arm();
                            atStart(c); // logging from now on
                            event(c, "=== Switched on from Settings (no restart yet)");
                        } else stopAndSend(c);
                        Sheets.refresh();
                    }), Widgets.top(10));
            if (on()) b.addView(Sheets.item("Restart Car Launcher", "Suggested, so the start-up is in the log too", () -> {
                if (MainActivity.I != null) MainActivity.I.restartApp();
            }), Widgets.top(6));
            long sz = size(c);
            if (sz > 0) b.addView(U.text(c, "Logged so far: " + Math.max(1, sz / 1024) + " KB", 12, U.SUB, false), Widgets.top(6));
            if (!on() && Store.flag(PENDING, false))
                b.addView(U.button(c, "Send the log again", true, () -> send(c)), Widgets.top(10));
            return Sheets.panel("Troubleshooting", b, 560, 330);
        });
    }
}
