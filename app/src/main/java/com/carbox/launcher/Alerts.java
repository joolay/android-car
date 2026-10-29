package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Widget alerts (salaah time, over the speed limit, time for a break). Each can either expand
 * into the 2×4 tile area or stay inside its own tile. One switch for all in Settings → Alerts,
 * plus one per widget (also in that widget's tile menu).
 */
final class Alerts {
    static final String GLOBAL = "expand_all";
    static final String[][] KINDS = {{"salah", "Salaah time", "Next prayer and Prayer times tiles"}, {"speed", "Over the speed limit", "Speed tile"},
            {"rest", "Time for a break", "Driving timer tile"}, {"home", "Arriving home", "Home Assistant tile"}};
    static long salahAt; // when the last salaah alert fired (tiles show it for 10 minutes)
    static String salahName;

    private Alerts() {
    }

    /** Expand this kind of alert into the tile area? */
    static boolean expand(String kind) {
        return Store.flag(GLOBAL, false) && Store.flag("expand_" + kind, true);
    }

    /** Which alert a widget has, or null. */
    static String kindOf(String widgetId) {
        if ("nextprayer".equals(widgetId) || "prayertimes".equals(widgetId)) return "salah";
        if ("speed".equals(widgetId)) return "speed";
        if ("rest".equals(widgetId)) return "rest";
        if ("ha".equals(widgetId)) return "home";
        return null;
    }

    /**
     * For an alert that doesn't expand: move its tile to the first spot and slide there, so the
     * newest alert is always first (the tile that was first moves to second, and so on).
     */
    static void front(String kind) {
        if (!Store.flag("alert_front", true)) return;
        U.ui.post(() -> {
            org.json.JSONArray s = Tiles.specs();
            int at = -1;
            for (int i = 0; i < s.length(); i++) {
                org.json.JSONObject o = s.optJSONObject(i);
                if (o != null && kind.equals(kindOf(o.optString("id")))) {
                    at = i;
                    break;
                }
            }
            if (at < 0) return;
            if (at > 0) {
                org.json.JSONArray out = new org.json.JSONArray();
                out.put(s.opt(at));
                for (int i = 0; i < s.length(); i++) if (i != at) out.put(s.opt(i));
                Tiles.save(out);
            }
            MainActivity a = MainActivity.I;
            if (a == null) return;
            if (at > 0) a.tiles.refresh();
            a.tiles.view.toStart();
            // map is the home view: the alert tile shows over the map in the first spot (highlighted);
            // it goes after the usual 15 s without a touch, or tap it to open the tiles
            if (MainActivity.mapHome() && a.map.expanded() && !a.drawerOpen) {
                a.alertTile(0);
                a.resetIdle();
            }
        });
    }

    static boolean salahNow() {
        return salahAt > 0 && System.currentTimeMillis() - salahAt < 10 * 60000L;
    }

    // ---- a simple large notice in the tile area (used for "time for a break")

    private static FrameLayout notice;

    static void showNotice(String label, String big, String sub, int bg) {
        MainActivity a = MainActivity.I;
        if (a == null || !a.isResumed2()) return;
        hideNotice();
        notice = new FrameLayout(a);
        notice.setClickable(true);
        notice.setBackground(U.round(bg, 16));
        LinearLayout col = U.col(a);
        col.setGravity(Gravity.CENTER);
        U.pad(col, 20, 16, 20, 16);
        col.addView(U.line(a, label, 13, U.AMBER, true));
        col.addView(U.line(a, big, 40, U.TEXT, true), Widgets.top(4));
        TextView s = U.text(a, sub, 15, U.TEXT2, false);
        s.setGravity(Gravity.CENTER);
        col.addView(s, Widgets.top(8));
        notice.addView(col, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        View x = U.icon(a, R.drawable.ic_x, U.TEXT2);
        U.pad(x, 8, 8, 8, 8);
        x.setOnClickListener(v -> hideNotice());
        notice.addView(x, U.flpu(46, 46, Gravity.TOP | Gravity.END));
        notice.setOnClickListener(v -> hideNotice());
        a.tileArea.addView(notice, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        a.growIn(notice, a.alertRect("rest")); // grows out of the driving timer tile
        a.resetIdle();
    }

    static void hideNotice() {
        if (notice == null) return;
        if (notice.getParent() != null) ((android.view.ViewGroup) notice.getParent()).removeView(notice);
        notice = null;
    }

    // ---- settings

    static void settings() {
        Sheets.Page p = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "When a tile has something to tell you, it can fill the 2×4 tile area for a moment, or just light up inside itself.", 13, U.SUB, false));
            b.addView(Sheets.switchRow(0, 0, "Expand alerts into the tile area", "Off: alerts only show inside their tiles", Store.flag(GLOBAL, false), v -> {
                Store.put(GLOBAL, v);
                Sheets.refresh();
            }), Widgets.top(8));
            if (Store.flag(GLOBAL, false)) {
                b.addView(Sheets.section("Per tile"));
                for (String[] k : KINDS)
                    b.addView(Sheets.switchRow(0, 0, k[1], k[2], Store.flag("expand_" + k[0], true), v -> Store.put("expand_" + k[0], v)));
            }
            b.addView(Sheets.section("Alerts that stay in their tile"));
            b.addView(Sheets.switchRow(0, 0, "Move the tile to the first spot", "Slides there; the newest alert is always first and the others move along",
                    Store.flag("alert_front", true), v -> Store.put("alert_front", v)));
            return Sheets.panel("Alerts", b, 600, 420);
        };
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }
}
