package com.carbox.launcher;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** About Car Launcher (version and features) and crash reports kept on the box. */
final class About {
    private static final String CRASHES = "crash_log";
    private static final String[][] FEATURES = {
            {"Home screen", "Tiles and folders you can drag, loop round and edit; Left Widgets, dock, rail and live map"},
            {"Driving", "Speed with speed-limit warnings, trip, driving timer with break reminders, Go to places, Park here"},
            {"Music", "Now Playing with synced lyrics, start-up playlist (Liked Songs), auto-start apps"},
            {"Phone", "Own dialler and contacts, BT Phone calls shown in Car Launcher, voice assistants"},
            {"Information", "Weather, calendar, news and traffic, turn-by-turn in the tile area"},
            {"Faith add-ons", "Prayer times, qibla, salaah announcements, travel prayers, Hijri calendar"},
            {"Home Assistant", "Smart-home buttons, car location, arriving-home buttons"},
            {"Extras", "Floating back button and shortcut circle, car keyboard, startup optimiser, set-up checklist"}};

    private About() {
    }

    /** Keep the error if Car Launcher crashes, then let Android carry on as normal. */
    static void catchCrashes(Context c) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        if (prev != null && prev.getClass().getName().startsWith(About.class.getName())) return;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            record(c, t.getName(), e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    /** Save an error to the crash reports (also used for errors caught before they could crash). */
    static void record(Context c, String where, Throwable e) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String trace = sw.toString();
            if (trace.length() > 3000) trace = trace.substring(0, 3000) + "\n…";
            List<String> l = new ArrayList<>(Prefs.list(CRASHES));
            String report = new java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.US).format(new java.util.Date()) + " · " + version(c) + " · " + where + "\n" + trace;
            l.add(0, report);
            while (l.size() > 5) l.remove(l.size() - 1);
            Prefs.putList(CRASHES, l);
            Trace.event(c, "ERROR in " + where + ": " + trace);
            // queued to go online (sent at once if it can be, else next time Car Launcher opens)
            List<String> q = new ArrayList<>(Prefs.list(UNSENT));
            q.add(report);
            while (q.size() > 10) q.remove(0);
            Prefs.putList(UNSENT, q);
            Prefs.put("crash_new", true);
            Prefs.flush();
            sendPending(c); // errors that didn't close the app go online straight away
        } catch (Throwable ignored) {
        }
    }

    // ---- crash reports online: sent to a private ntfy.sh topic (free, no account); a GitHub Actions job
    // (crashes.yml) copies them into the repo's "crashes" branch, where they can be read later
    static final String TOPIC = "carlauncher-crash-4d6dd33614e4914e";
    private static final String UNSENT = "crash_unsent";
    static final String SEND = "crash_send";

    /** Send any crash reports still waiting (on start, and right after a crash). */
    static void sendPending(Context c) {
        if (!Prefs.flag(SEND, true)) return;
        List<String> q = Prefs.list(UNSENT);
        if (q.isEmpty()) return;
        String report = q.get(0);
        java.util.Map<String, String> h = new java.util.HashMap<>();
        h.put("Title", "Car Launcher crash " + version(c));
        h.put("Tags", "warning");
        String body = "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE + "\n" + report;
        if (body.length() > 3900) body = body.substring(0, 3900) + "\n…";
        Net.post("https://ntfy.sh/" + TOPIC, h, body, (res, code) -> {
            if (res == null || code < 200 || code >= 300) return; // try again next time
            List<String> left = new ArrayList<>(Prefs.list(UNSENT));
            left.remove(report);
            Prefs.putList(UNSENT, left);
            if (!left.isEmpty()) sendPending(c); // the next one
        });
    }

    static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** After a crash: offer the report once, next time Car Launcher opens. */
    static void offerReport() {
        if (MainActivity.I != null) sendPending(MainActivity.I); // anything from last time goes online now
        if (!Prefs.flag("crash_new", false) || Panels.open()) return;
        Prefs.put("crash_new", false);
        Panels.show(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Car Launcher closed unexpectedly last time. The error was saved; a photo of it helps get it fixed.", 14, Ui.TEXT2, false));
            LinearLayout r = Ui.row(c);
            r.addView(Ui.button(c, "View report", true, About::crashes));
            r.addView(Ui.gap(c, 10, 1));
            r.addView(Ui.button(c, "Not now", false, Panels::close));
            b.addView(r, Widgets.top(14));
            return Panels.panel("Something went wrong", b, 480, 260);
        });
    }

    static void show() {
        Panels.Page p = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.line(c, "Car Launcher " + version(c), 22, Ui.TEXT, true));
            b.addView(Ui.text(c, "A home screen made for Android car boxes. Built for Dr. Joolay.", 13, Ui.SUB, false), Widgets.top(2));
            b.addView(Panels.section("Features"));
            for (String[] f : FEATURES) {
                LinearLayout row = Ui.col(c);
                row.setBackground(Themes.inner(Ui.CARD2, 12));
                Ui.pad(row, 12, 8, 12, 8);
                row.addView(Ui.line(c, f[0], 14, Ui.TEXT, true));
                row.addView(Ui.text(c, f[1], 12, Ui.SUB, false), Widgets.top(2));
                b.addView(row, Widgets.top(6));
            }
            b.addView(Panels.section("Help"));
            int n = Prefs.list(CRASHES).size();
            b.addView(Panels.item("Crash reports", n == 0 ? "None" : n + " saved", About::crashes));
            b.addView(Panels.switchRow(0, 0, "Send crash reports online", "So they can be looked at from anywhere (only the error, version and box model)",
                    Prefs.flag(SEND, true), on -> Prefs.put(SEND, on)));
            b.addView(Panels.item("Last BT Phone notifications", null, SettingsPanels::callLog));
            return Panels.panel("About", b, 640, 440);
        };
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }

    static void crashes() {
        Panels.Page p = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            List<String> l = Prefs.list(CRASHES);
            if (l.isEmpty()) b.addView(Ui.text(c, "No crashes recorded. If Car Launcher ever closes by itself, the error is saved here.", 13, Ui.SUB, false));
            for (String s : l) {
                TextView t = Ui.text(c, s, 10, Ui.TEXT2, false);
                t.setTypeface(android.graphics.Typeface.MONOSPACE);
                t.setBackground(Themes.inner(Ui.CARD2, 10));
                Ui.pad(t, 10, 8, 10, 8);
                t.setTextIsSelectable(true);
                b.addView(t, Widgets.top(6));
            }
            if (!l.isEmpty()) {
                LinearLayout r = Ui.row(c);
                r.addView(Ui.button(c, "Copy", false, () -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Car Launcher crash", String.join("\n\n", l)));
                    android.widget.Toast.makeText(c, "Copied", android.widget.Toast.LENGTH_SHORT).show();
                }));
                r.addView(Ui.gap(c, 10, 1));
                r.addView(Ui.button(c, "Clear", false, () -> {
                    Prefs.putList(CRASHES, new ArrayList<>());
                    Panels.refresh();
                }));
                b.addView(r, Widgets.top(10));
            }
            return Panels.panel("Crash reports", b, 700, 440);
        };
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }
}
