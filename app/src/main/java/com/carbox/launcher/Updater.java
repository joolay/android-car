package com.carbox.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.SystemClock;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Updates from the GitHub releases (Settings → System → Updates). The project is private, so it needs
 * a read-only GitHub key, typed in once. It checks a minute after start and every 6 hours, downloads a
 * newer APK and installs it once the car is parked (Android shows its own "Update" confirmation).
 */
final class Updater {
    private static final String REPO = "joolay/CarLauncher";
    static final String KEY = "upd_key", AUTO = "upd_auto";
    /**
     * TEMPORARY, the owner's own box only: read-only key for this private project, built in so nothing
     * needs typing. REMOVE before the app goes on the Play Store (and cancel this key on GitHub).
     */
    private static final String BUILT_IN_KEY = "REMOVED";

    /** The key typed in Settings, else the built-in one. */
    static String key() {
        String k = Prefs.str(KEY, "").trim();
        return k.isEmpty() ? BUILT_IN_KEY : k;
    }
    private static final String ACTION = "com.carbox.launcher.INSTALL_RESULT";
    private static boolean busy, receiver;
    private static String status = "";

    private Updater() {
    }

    /** Started from the home screen: first check 20 s in (after the splash), then every 6 hours. */
    static void start(Context c) {
        final Context app = c.getApplicationContext();
        Ui.ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (Prefs.flag(AUTO, true)) check(app, false);
                Ui.ui.postDelayed(this, 6 * 3600 * 1000L);
            }
        }, 20000);
    }

    /** "0.27" → {0, 27}. */
    private static int[] ver(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)").matcher(s == null ? "" : s);
        return m.find() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : new int[]{0, 0};
    }

    private static boolean newer(String tag, String mine) {
        int[] a = ver(tag), b = ver(mine);
        return a[0] != b[0] ? a[0] > b[0] : a[1] > b[1];
    }

    private static HttpURLConnection open(String url, String key, String accept) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setInstanceFollowRedirects(false); // the download moves to GitHub's file store, which mustn't get the key
        h.setConnectTimeout(20000);
        h.setReadTimeout(60000);
        if (key != null) h.setRequestProperty("Authorization", "Bearer " + key);
        h.setRequestProperty("Accept", accept);
        h.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        return h;
    }

    /** Look for a newer release; download and install it if there is one. */
    static void check(Context c, boolean byHand) {
        final Context app = c.getApplicationContext();
        String key = key();
        if (key.isEmpty()) {
            say(app, "Updates need your GitHub key: Settings → System → Updates", byHand);
            return;
        }
        if (busy) return;
        busy = true;
        Net.bg(() -> {
            try {
                status = "Checking…";
                HttpURLConnection h = open("https://api.github.com/repos/" + REPO + "/releases?per_page=1", key, "application/vnd.github+json");
                int code = h.getResponseCode();
                if (code == 401 || code == 403 || code == 404) {
                    status = "The GitHub key doesn't work (" + code + ")";
                    say(app, status, byHand);
                    return;
                }
                String body = read(h.getInputStream());
                org.json.JSONArray a = new org.json.JSONArray(body);
                if (a.length() == 0) {
                    status = "No releases yet";
                    say(app, status, byHand);
                    return;
                }
                org.json.JSONObject r = a.getJSONObject(0);
                String tag = r.optString("tag_name"), mine = About.version(app);
                Prefs.put("upd_checked", System.currentTimeMillis());
                if (!newer(tag, mine)) {
                    status = "Up to date (" + mine + ")";
                    say(app, status, byHand);
                    return;
                }
                org.json.JSONArray assets = r.optJSONArray("assets");
                long id = -1;
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    org.json.JSONObject as = assets.getJSONObject(i);
                    if (as.optString("name").endsWith(".apk")) id = as.optLong("id");
                }
                if (id < 0) {
                    status = tag + " has no APK";
                    say(app, status, byHand);
                    return;
                }
                status = "Downloading " + tag + "…";
                say(app, status, byHand);
                File apk = new File(app.getCacheDir(), "update.apk");
                HttpURLConnection d = open("https://api.github.com/repos/" + REPO + "/releases/assets/" + id, key, "application/octet-stream");
                int dc = d.getResponseCode();
                if (dc / 100 == 3) { // follow it to the file store, without the key
                    String loc = d.getHeaderField("Location");
                    d.disconnect();
                    d = open(loc, null, "application/octet-stream");
                    dc = d.getResponseCode();
                }
                if (dc != 200) {
                    status = "Download failed (" + dc + ")";
                    say(app, status, byHand);
                    return;
                }
                try (InputStream in = d.getInputStream(); OutputStream out = new java.io.FileOutputStream(apk)) {
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                }
                status = tag + " downloaded · tap Install";
                Trace.event(app, "Update " + tag + " downloaded");
                final String t = tag;
                Ui.ui.post(() -> ask(app, apk, t, byHand));
            } catch (Exception e) {
                status = "Couldn't check: no connection?";
                say(app, status, byHand);
            } finally {
                busy = false;
            }
        });
    }

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    private static void say(Context c, String s, boolean show) {
        if (show) Ui.ui.post(() -> android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_LONG).show());
        Ui.ui.post(Panels::refresh);
    }

    /** Asks on the home screen: Install now or Later (never while driving, on a call or over another page). */
    private static void ask(Context c, File apk, String tag, boolean byHand) {
        MainActivity a = MainActivity.I;
        if (byHand && !Gps.moving() && CallState.state == CallState.NONE) {
            // asked for from Settings: no extra prompt (Settings' closing used to sweep it away), straight to
            // Android's own "Update this app?" once Settings has gone
            if (Panels.open()) Panels.close();
            Trace.event(c, "Update " + tag + " downloaded by hand: installing");
            Ui.ui.postDelayed(() -> install(c, apk, tag), 700);
            return;
        }
        if (a == null || MainActivity.frontAt == 0 || Gps.moving() || Panels.open() || CallState.state != CallState.NONE) {
            Ui.ui.postDelayed(() -> ask(c, apk, tag, false), 30000);
            return;
        }
        Panels.show(() -> {
            Context x = Panels.ctx();
            android.widget.LinearLayout b = Ui.col(x);
            b.addView(Ui.text(x, "Car Launcher " + tag.replace("v", "").replace("-alpha", "") + " is ready. Install it now?", 17, Ui.TEXT, true));
            b.addView(Ui.text(x, "It takes a few seconds; Android asks you to tap Update, then Car Launcher starts again.", 15, Ui.TEXT2, false), Widgets.top(8));
            android.widget.LinearLayout btns = Ui.row(x);
            btns.addView(Ui.button(x, "Install now", true, () -> {
                Panels.close();
                install(c, apk, tag);
            }));
            btns.addView(Ui.gap(x, 10, 1));
            btns.addView(Ui.button(x, "Later", false, Panels::close));
            b.addView(btns, Widgets.top(18));
            return Panels.panel("Update available", b, 560, 300);
        });
    }

    static void install(Context c, File apk, String tag) {
        if (!c.getPackageManager().canRequestPackageInstalls()) { // Android's "Install unknown apps" for Car Launcher
            status = tag + " is ready: allow Car Launcher to install apps, then tap Install";
            android.widget.Toast.makeText(c, "Allow Car Launcher to install its update", android.widget.Toast.LENGTH_LONG).show();
            try {
                c.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
            Panels.refresh();
            return;
        }
        try {
            listen(c);
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(c.getPackageName());
            int id = pi.createSession(p);
            try (PackageInstaller.Session s = pi.openSession(id)) {
                try (OutputStream o = s.openWrite("update.apk", 0, apk.length()); InputStream in = new java.io.FileInputStream(apk)) {
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
                    s.fsync(o);
                }
                Intent done = new Intent(ACTION).setPackage(c.getPackageName());
                android.app.PendingIntent pend = android.app.PendingIntent.getBroadcast(c, id, done,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_MUTABLE);
                s.commit(pend.getIntentSender());
            }
            status = "Installing " + tag + "…";
            Trace.event(c, "Installing update " + tag);
        } catch (Exception e) {
            status = "Couldn't install " + tag + ": " + e.getMessage();
        }
        Panels.refresh();
    }

    /** Android answers the install here: it asks you to confirm (one tap), then Car Launcher restarts as the new version. */
    private static void listen(Context c) {
        if (receiver) return;
        receiver = true;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context x, Intent in) {
                int st = in.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = in.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) x.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } else if (st != PackageInstaller.STATUS_SUCCESS) {
                    status = "Update not installed: " + in.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    Trace.event(x, status);
                    Panels.refresh();
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION);
        if (android.os.Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(r, f);
    }

    /** Settings page. */
    static void settings() {
        SettingsPanels.open(() -> {
            Context c = Panels.ctx();
            android.widget.LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Car Launcher can update itself from your GitHub releases. Your project is private, so it needs a GitHub key "
                    + "that can only read it: on github.com → Settings → Developer settings → Fine-grained tokens, make one for CarLauncher "
                    + "with Contents: Read-only, and paste it here. Updates install when the car is parked; Android asks you to tap Update.", 12, Ui.SUB, false));
            final String[] k = {Prefs.str(KEY, "")};
            b.addView(Widgets.field("GitHub key (github_pat_…)", k[0], true, t -> {
                k[0] = t;
                Prefs.put(KEY, t.trim());
            }), Widgets.top(10));
            b.addView(Panels.switchRow(0, 0, "Check automatically", "At every start, then every 6 hours; asks before installing", Prefs.flag(AUTO, true),
                    v -> Prefs.put(AUTO, v)), Widgets.top(8));
            long at = Prefs.lng("upd_checked", 0);
            String st = !status.isEmpty() ? status : at > 0 ? "Last checked " + NewsTicker.ago(at) : "Not checked yet";
            b.addView(Ui.text(c, "This version: " + About.version(c) + " · " + st, 12, Ui.SUB, false), Widgets.top(6));
            android.widget.LinearLayout btns = Ui.row(c);
            btns.addView(Ui.button(c, "Check now", true, () -> check(c, true)));
            File apk = new File(c.getCacheDir(), "update.apk");
            if (apk.exists() && status.contains("downloaded") || status.contains("allow Car Launcher")) {
                btns.addView(Ui.gap(c, 10, 1));
                btns.addView(Ui.button(c, "Install", false, () -> install(c, apk, "the update")));
            }
            b.addView(btns, Widgets.top(10));
            return Panels.panel("Updates", b, 600, 400);
        });
    }
}
