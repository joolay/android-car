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
            {"Home screen", "Tiles of widgets and folders you can drag, loop round and edit; dock, rail and live map"},
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
            List<String> l = new ArrayList<>(Store.list(CRASHES));
            l.add(0, new java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.US).format(new java.util.Date()) + " · " + version(c) + " · " + where + "\n" + trace);
            while (l.size() > 5) l.remove(l.size() - 1);
            Store.putList(CRASHES, l);
            Store.put("crash_new", true);
            Store.flush();
        } catch (Throwable ignored) {
        }
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
        if (!Store.flag("crash_new", false) || Sheets.open()) return;
        Store.put("crash_new", false);
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Car Launcher closed unexpectedly last time. The error was saved; a photo of it helps get it fixed.", 14, U.TEXT2, false));
            LinearLayout r = U.row(c);
            r.addView(U.button(c, "View report", true, About::crashes));
            r.addView(U.gap(c, 10, 1));
            r.addView(U.button(c, "Not now", false, Sheets::close));
            b.addView(r, Widgets.top(14));
            return Sheets.panel("Something went wrong", b, 480, 260);
        });
    }

    static void show() {
        Sheets.Page p = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.line(c, "Car Launcher " + version(c), 22, U.TEXT, true));
            b.addView(U.text(c, "A home screen made for Android car boxes. Built for Dr. Joolay.", 13, U.SUB, false), Widgets.top(2));
            b.addView(Sheets.section("Features"));
            for (String[] f : FEATURES) {
                LinearLayout row = U.col(c);
                row.setBackground(U.round(U.CARD2, 12));
                U.pad(row, 12, 8, 12, 8);
                row.addView(U.line(c, f[0], 14, U.TEXT, true));
                row.addView(U.text(c, f[1], 12, U.SUB, false), Widgets.top(2));
                b.addView(row, Widgets.top(6));
            }
            b.addView(Sheets.section("Help"));
            int n = Store.list(CRASHES).size();
            b.addView(Sheets.item("Crash reports", n == 0 ? "None" : n + " saved", About::crashes));
            b.addView(Sheets.item("Last BT Phone notifications", null, SettingsPanels::callLog));
            return Sheets.panel("About", b, 640, 440);
        };
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }

    static void crashes() {
        Sheets.Page p = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            List<String> l = Store.list(CRASHES);
            if (l.isEmpty()) b.addView(U.text(c, "No crashes recorded. If Car Launcher ever closes by itself, the error is saved here.", 13, U.SUB, false));
            for (String s : l) {
                TextView t = U.text(c, s, 10, U.TEXT2, false);
                t.setTypeface(android.graphics.Typeface.MONOSPACE);
                t.setBackground(U.round(U.CARD2, 10));
                U.pad(t, 10, 8, 10, 8);
                t.setTextIsSelectable(true);
                b.addView(t, Widgets.top(6));
            }
            if (!l.isEmpty()) {
                LinearLayout r = U.row(c);
                r.addView(U.button(c, "Copy", false, () -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Car Launcher crash", String.join("\n\n", l)));
                    android.widget.Toast.makeText(c, "Copied", android.widget.Toast.LENGTH_SHORT).show();
                }));
                r.addView(U.gap(c, 10, 1));
                r.addView(U.button(c, "Clear", false, () -> {
                    Store.putList(CRASHES, new ArrayList<>());
                    Sheets.refresh();
                }));
                b.addView(r, Widgets.top(10));
            }
            return Sheets.panel("Crash reports", b, 700, 440);
        };
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }
}
