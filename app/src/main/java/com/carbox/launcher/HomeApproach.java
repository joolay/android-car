package com.carbox.launcher;

import android.content.Context;
import android.location.Location;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Arriving home: when the car comes within the set distance of home, the Home Assistant buttons
 * (gate, garage, alarm… from your Home Assistant tile) fill the 2×4 tile area, big enough to tap.
 * Nothing is opened or switched by itself: only when you press a button (gates, garages and locks
 * need a hold, as on the tile). Closes when you tap ✕, leave the area, or after 5 minutes.
 */
final class HomeApproach {
    static final String ON = "home_on";
    private static boolean inside;
    private static FrameLayout shown;
    static boolean near; // the HA tile lights up while true (when not expanding)

    private HomeApproach() {
    }

    static void start() {
        Loc.listen(HomeApproach::onFix);
    }

    static int radius() {
        return Store.num("home_radius", 500);
    }

    static boolean hasHome() {
        return !Store.str("home_lat", "").isEmpty();
    }

    private static void onFix() {
        if (!Store.flag(ON, true) || !hasHome() || !Loc.fresh()) return;
        float[] d = new float[1];
        Location.distanceBetween(Loc.lat, Loc.lon, Double.parseDouble(Store.str("home_lat", "0")), Double.parseDouble(Store.str("home_lon", "0")), d);
        if (!inside && d[0] < radius() && Loc.speed > 2f) { // driving in towards home
            inside = true;
            near = true;
            if (Alerts.expand("home")) show();
            else Alerts.front("home");
            U.ui.postDelayed(() -> {
                near = false;
                hide();
            }, 5 * 60000L);
        } else if (inside && d[0] > radius() + 300) { // left again: ready for the next arrival
            inside = false;
            near = false;
            hide();
        }
    }

    /** Entities from the first Home Assistant tile. */
    private static JSONArray entities() {
        JSONArray s = Tiles.specs();
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && "ha".equals(o.optString("id"))) {
                JSONObject cfg = o.optJSONObject("cfg");
                JSONArray e = cfg == null ? null : cfg.optJSONArray("entities");
                if (e != null) return e;
            }
        }
        return new JSONArray();
    }

    static void show() {
        MainActivity a = MainActivity.I;
        JSONArray ents = entities();
        if (a == null || !a.isResumed2() || ents.length() == 0 || !HA.configured()) return;
        hide();
        HA.maybeRefresh();
        shown = new FrameLayout(a);
        shown.setClickable(true);
        shown.setBackground(U.round(0xFF13284A, 16));
        LinearLayout col = U.col(a);
        U.pad(col, 18, 14, 18, 14);
        col.addView(U.line(a, "ALMOST HOME", 12, U.ACCENT_SOFT, true));
        col.addView(U.line(a, "Tap to switch · hold for gates, garage and locks", 12, U.SUB, false), Widgets.top(2));
        java.util.List<View> cells = new java.util.ArrayList<>();
        for (int i = 0; i < ents.length(); i++) cells.add(button(a, ents.optString(i)));
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(AppPanels.grid(a, cells, Math.min(3, Math.max(2, cells.size())), 10));
        LinearLayout.LayoutParams sl = U.weight(1, false);
        sl.topMargin = U.u(12);
        col.addView(sv, sl);
        shown.addView(col, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        View x = U.icon(a, R.drawable.ic_x, U.TEXT2);
        U.pad(x, 8, 8, 8, 8);
        x.setOnClickListener(v -> hide());
        shown.addView(x, U.flpu(40, 40, Gravity.TOP | Gravity.END));
        a.tileArea.addView(shown, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        a.growIn(shown, a.alertRect("home")); // grows out of the Home Assistant tile
    }

    private static View button(Context c, String id) {
        LinearLayout b = U.col(c);
        b.setGravity(Gravity.CENTER);
        b.setBackground(U.round(U.CARD2, 16));
        U.pad(b, 10, 14, 10, 12);
        b.setMinimumHeight(U.u(110));
        b.addView(U.icon(c, HA.icon(id), HA.isOn(id) ? U.AMBER : U.TEXT), U.lpu(36, 36));
        TextView n = U.line(c, HA.name(id), 15, U.TEXT, true);
        n.setGravity(Gravity.CENTER);
        b.addView(n, Widgets.top(8));
        TextView st = U.line(c, HA.stateLabel(id), 12, U.SUB, false);
        st.setGravity(Gravity.CENTER);
        b.addView(st, Widgets.top(2));
        if (HA.needsHold(id) || id.startsWith("alarm_control_panel.")) { // never by accident: gates, garages, locks, alarms
            b.setOnClickListener(v -> android.widget.Toast.makeText(c, "Hold to use " + HA.name(id), android.widget.Toast.LENGTH_SHORT).show());
            b.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                HA.activate(c, id);
                return true;
            });
        } else b.setOnClickListener(v -> HA.activate(c, id));
        U.press(b);
        return b;
    }

    static void hide() {
        if (shown == null) return;
        if (shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
    }

    /** Settings rows (shown in Settings → Car location). */
    static void addSettings(Context c, LinearLayout b) {
        b.addView(Sheets.section("Arriving home"));
        b.addView(Sheets.switchRow(0, 0, "Show Home Assistant buttons near home", "Big buttons in the tile area; nothing happens until you press one", Store.flag(ON, true), v -> Store.put(ON, v)));
        int r = radius();
        b.addView(Sheets.row("When within", seg(c, 300, "300 m", r), seg(c, 500, "500 m", r), seg(c, 1000, "1 km", r)), Widgets.top(4));
        LinearLayout row = U.row(c);
        row.addView(U.button(c, "Home is here", false, () -> {
            if (!Loc.has()) return;
            Store.put("home_lat", String.valueOf(Loc.lat));
            Store.put("home_lon", String.valueOf(Loc.lon));
            Sheets.refresh();
        }));
        row.addView(U.gap(c, 12, 1));
        row.addView(U.text(c, hasHome() ? "Home saved" : "Tap at home to save it (or set Home in the Go to tile)", 12, U.SUB, false));
        b.addView(row, Widgets.top(8));
    }

    private static View seg(Context c, int v, String label, int cur) {
        return U.seg(c, label, v == cur, () -> {
            Store.put("home_radius", v);
            Sheets.refresh();
        });
    }

    /** No home saved yet: look up the Go to tile's Home address once. */
    static void fromGoTo() {
        if (hasHome() || !android.location.Geocoder.isPresent()) return;
        JSONArray s = Tiles.specs();
        String addr = null;
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && "goto".equals(o.optString("id")) && o.optJSONObject("cfg") != null) addr = o.optJSONObject("cfg").optString("home", null);
        }
        if (addr == null || addr.isEmpty()) return;
        final String a = addr;
        Net.bg(() -> {
            try {
                java.util.List<android.location.Address> r = new android.location.Geocoder(MainActivity.I).getFromLocationName(a, 1);
                if (r != null && !r.isEmpty()) U.ui.post(() -> {
                    Store.put("home_lat", String.valueOf(r.get(0).getLatitude()));
                    Store.put("home_lon", String.valueOf(r.get(0).getLongitude()));
                });
            } catch (Exception ignored) {
            }
        });
    }
}
