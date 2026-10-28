package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Home Assistant over its REST API (works over Tailscale or a remote URL). */
final class HA {
    private HA() {
    }

    static final Map<String, JSONObject> states = new HashMap<>();
    private static final List<Runnable> listeners = new ArrayList<>();
    private static long fetchedAt;
    static String lastError;
    static int version;

    static String url() {
        String u = Store.str(Store.HA_URL, "").trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    static boolean configured() {
        return !url().isEmpty() && !Store.str(Store.HA_TOKEN, "").isEmpty();
    }

    static Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + Store.str(Store.HA_TOKEN, ""));
        h.put("Content-Type", "application/json");
        return h;
    }

    static void listen(Runnable r) {
        listeners.add(r);
    }

    /** Fetch all states (cheap enough for a home with a few hundred entities). */
    static void refresh(Runnable done) {
        if (!configured()) return;
        fetchedAt = android.os.SystemClock.elapsedRealtime();
        Net.get(url() + "/api/states", headers(), (body, code) -> {
            if (body == null || code != 200) {
                lastError = code == 401 ? "The token was refused (401)" : code > 0 ? "Home Assistant answered " + code : "Can't reach " + url();
            } else {
                lastError = null;
                try {
                    JSONArray a = new JSONArray(body);
                    states.clear();
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.getJSONObject(i);
                        states.put(o.getString("entity_id"), o);
                    }
                } catch (Exception e) {
                    lastError = "Unexpected reply from Home Assistant";
                }
            }
            version++;
            for (Runnable r : new ArrayList<>(listeners)) r.run();
            if (done != null) done.run();
        });
    }

    static void maybeRefresh() {
        if (android.os.SystemClock.elapsedRealtime() - fetchedAt > 15000) refresh(null);
    }

    static String domain(String id) {
        int d = id.indexOf('.');
        return d > 0 ? id.substring(0, d) : id;
    }

    static boolean controllable(String id) {
        switch (domain(id)) {
            case "light":
            case "switch":
            case "input_boolean":
            case "fan":
            case "cover":
            case "lock":
            case "scene":
            case "script":
            case "button":
            case "automation":
            case "media_player":
            case "climate":
            case "siren":
            case "valve":
                return true;
        }
        return false;
    }

    /** Doors, locks and the like need a press-and-hold so they can't be opened by a stray tap. */
    static boolean needsHold(String id) {
        String d = domain(id);
        return "lock".equals(d) || "cover".equals(d) || "valve".equals(d) || "siren".equals(d);
    }

    static String name(String id) {
        JSONObject o = states.get(id);
        String n = o == null ? null : o.optJSONObject("attributes") == null ? null : o.optJSONObject("attributes").optString("friendly_name", null);
        if (n != null && !n.isEmpty()) return n;
        String s = id.substring(id.indexOf('.') + 1).replace('_', ' ');
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String state(String id) {
        JSONObject o = states.get(id);
        return o == null ? null : o.optString("state");
    }

    static boolean isOn(String id) {
        String s = state(id);
        return s != null && (s.equals("on") || s.equals("open") || s.equals("opening") || s.equals("unlocked") || s.equals("playing") || s.equals("heat") || s.equals("cool"));
    }

    static int icon(String id) {
        String d = domain(id), n = id.toLowerCase(Locale.ROOT);
        if (n.contains("gate")) return R.drawable.ic_gate;
        if (n.contains("garage") || "cover".equals(d)) return R.drawable.ic_garage;
        switch (d) {
            case "light":
                return R.drawable.ic_bulb;
            case "lock":
                return R.drawable.ic_lock;
            case "scene":
            case "script":
            case "button":
            case "automation":
                return R.drawable.ic_bolt;
            case "climate":
                return R.drawable.ic_sun;
            case "media_player":
                return R.drawable.ic_music;
            case "siren":
                return R.drawable.ic_shield;
            default:
                return R.drawable.ic_power;
        }
    }

    static String stateLabel(String id) {
        String s = state(id);
        if (s == null) return "—";
        String d = domain(id);
        if ("scene".equals(d) || "script".equals(d) || "button".equals(d)) return "Run";
        if ("unavailable".equals(s)) return "Offline";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }

    static void activate(Context c, String id) {
        String d = domain(id);
        String svc;
        switch (d) {
            case "scene":
            case "script":
                svc = "turn_on";
                break;
            case "button":
                svc = "press";
                break;
            case "lock":
                svc = "locked".equals(state(id)) ? "unlock" : "lock";
                break;
            case "cover":
            case "valve":
                svc = "toggle";
                break;
            case "media_player":
                svc = "media_play_pause";
                break;
            default:
                svc = "toggle";
        }
        JSONObject body = new JSONObject();
        try {
            body.put("entity_id", id);
        } catch (Exception ignored) {
        }
        Net.post(url() + "/api/services/" + d + "/" + svc, headers(), body.toString(), (r, code) -> {
            if (r == null || code >= 300) Toast.makeText(c, "Home Assistant: couldn't change " + name(id), Toast.LENGTH_SHORT).show();
            U.ui.postDelayed(() -> refresh(null), 600);
        });
    }

    // ------------------------------------------------------------------ widget

    static final class Widget extends Widgets.W {
        private final LinearLayout grid;
        private final LinearLayout dots;
        private int page;

        Widget(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = Widgets.card(c, U.CARD);
            U.pad(l, 10, 12, 10, 10);
            LinearLayout head = U.row(c);
            TextView t = U.line(c, cfg.optString("title", "Home"), 15, U.TEXT, true);
            t.setPadding(U.u(4), 0, 0, 0);
            head.addView(t, new LinearLayout.LayoutParams(0, U.WRAP, 1));
            dots = U.row(c);
            head.addView(dots);
            l.addView(head);
            grid = U.col(c);
            LinearLayout.LayoutParams gl = U.weight(1, false);
            gl.topMargin = U.u(8);
            l.addView(grid, gl);
            v = l;
            render();
            seen = version;
            if (configured()) maybeRefresh();
        }

        private List<String> entities() {
            List<String> l = new ArrayList<>();
            JSONArray a = cfg.optJSONArray("entities");
            if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
            return l;
        }

        private int seen;

        @Override
        private boolean lit;

        void tick() {
            if (lit != HomeApproach.near) { // arriving home: the tile lights up (in-tile alert)
                lit = HomeApproach.near;
                v.setBackground(lit ? U.outline(0xFF13284A, U.ACCENT, 3, 16) : U.round(U.CARD, 16));
            }
            if (configured()) maybeRefresh();
            if (seen != version) {
                seen = version;
                render();
            }
        }

        void render() {
            grid.removeAllViews();
            dots.removeAllViews();
            List<String> ents = entities();
            if (!configured() || ents.isEmpty()) {
                TextView t = U.text(c, configured() ? "Tap to choose toggles" : "Tap to set up Home Assistant", 12, U.SUB, false);
                t.setPadding(U.u(4), 0, 0, 0);
                grid.addView(t);
                v.setOnClickListener(x -> setup(index));
                return;
            }
            v.setOnClickListener(null);
            int pages = (ents.size() + 3) / 4;
            if (page >= pages) page = 0;
            if (pages > 1) for (int p = 0; p < pages; p++) {
                final int pp = p;
                View d = new View(c);
                d.setBackground(U.oval(p == page ? U.TEXT : U.MUTED));
                LinearLayout.LayoutParams dl = U.lpu(6, 6);
                dl.leftMargin = U.u(5);
                d.setOnClickListener(x -> {
                    page = pp;
                    render();
                });
                FrameLayout hit = new FrameLayout(c); // bigger tap area
                hit.addView(d, U.flpu(6, 6, Gravity.CENTER));
                hit.setOnClickListener(x -> {
                    page = pp;
                    render();
                });
                dots.addView(hit, U.lpu(14, 20));
            }
            for (int r = 0; r < 2; r++) {
                LinearLayout row = U.row(c);
                for (int k = 0; k < 2; k++) {
                    int idx = page * 4 + r * 2 + k;
                    View b = idx < ents.size() ? button(ents.get(idx)) : new View(c);
                    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, U.MATCH, 1);
                    if (k == 1) bl.leftMargin = U.u(6);
                    row.addView(b, bl);
                }
                LinearLayout.LayoutParams rl = U.weight(1, false);
                if (r == 1) rl.topMargin = U.u(6);
                grid.addView(row, rl);
            }
        }

        private View button(String id) {
            boolean on = isOn(id);
            LinearLayout b = U.col(c);
            b.setGravity(Gravity.CENTER);
            b.setBackground(U.round(on ? 0xFF3A3115 : U.CARD2, 12));
            b.addView(U.icon(c, icon(id), on ? U.AMBER : U.SUB), U.lpu(22, 22));
            TextView n = U.line(c, name(id), 10, on ? U.TEXT : U.TEXT2, false);
            n.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nl = U.lp(U.MATCH, U.WRAP);
            nl.topMargin = U.u(4);
            U.pad(n, 3, 0, 3, 0);
            b.addView(n, nl);
            if (needsHold(id)) {
                View hold = new View(c);
                hold.setBackground(U.oval(U.AMBER));
                LinearLayout.LayoutParams hl = U.lpu(4, 4);
                hl.topMargin = U.u(3);
                hl.gravity = Gravity.CENTER_HORIZONTAL;
                b.addView(hold, hl);
                b.setOnClickListener(x -> Toast.makeText(c, "Hold to activate " + name(id), Toast.LENGTH_SHORT).show());
                b.setOnLongClickListener(x -> {
                    activate(c, id);
                    x.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    return true;
                });
            } else {
                b.setOnClickListener(x -> activate(c, id));
                b.setOnLongClickListener(x -> {
                    setup(index);
                    return true;
                });
            }
            U.press(b);
            return b;
        }
    }

    // ------------------------------------------------------------------ setup

    static void setup(int index) {
        Sheets.show(new Sheets.Page() {
            String filter = "";

            @Override
            public View build() {
                Context c = Sheets.ctx();
                JSONObject spec = Tiles.specs().optJSONObject(index);
                JSONObject cfg = spec.optJSONObject("cfg");
                if (cfg == null) cfg = new JSONObject();
                JSONArray ents = cfg.optJSONArray("entities");
                if (ents == null) ents = new JSONArray();
                final JSONObject fc = cfg;
                final JSONArray fe = ents;

                LinearLayout b = U.col(c);
                b.addView(Sheets.section("Server"));
                b.addView(Widgets.field("http://homeassistant:8123 or your Tailscale / Nabu Casa URL", Store.str(Store.HA_URL, ""), false,
                        t -> Store.put(Store.HA_URL, t)), U.lp(U.MATCH, U.WRAP));
                b.addView(Sheets.section("Long-lived access token"));
                b.addView(Widgets.field("Profile → Security → Long-lived access tokens", Store.str(Store.HA_TOKEN, ""), true,
                        t -> Store.put(Store.HA_TOKEN, t)), U.lp(U.MATCH, U.WRAP));
                LinearLayout btns = U.row(c);
                btns.addView(U.button(c, states.isEmpty() ? "Connect" : "Reload entities", true, () -> refresh(Sheets::refresh)));
                TextView status = U.text(c, lastError != null ? lastError : states.isEmpty() ? "" : "Connected · " + states.size() + " entities", 12,
                        lastError != null ? U.RED : U.GREEN, false);
                LinearLayout.LayoutParams sl = U.lp(U.WRAP, U.WRAP);
                sl.leftMargin = U.u(12);
                btns.addView(status, sl);
                b.addView(btns, Widgets.top(12));
                if (Store.flag(Store.CONN_TS, false) || Status.tailscaleInstalled())
                    b.addView(U.text(c, "Tip: with Tailscale on, use your Home Assistant's Tailscale name, e.g. http://homeassistant:8123", 11, U.SUB, false), Widgets.top(8));

                b.addView(Sheets.section("Tile title"));
                b.addView(Widgets.field("Home", fc.optString("title", "Home"), false, t -> Widgets.put(fc, "title", t)), U.lp(U.MATCH, U.WRAP));

                if (!states.isEmpty()) {
                    b.addView(Sheets.section("Toggles on this tile (4 per page, in the order you pick them)"));
                    LinearLayout fr = U.row(c);
                    final String[] typed = {filter};
                    fr.addView(Widgets.field("Filter by name", filter, false, t -> typed[0] = t), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                    fr.addView(U.gap(c, 10, 1));
                    fr.addView(U.button(c, "Filter", false, () -> {
                        filter = typed[0];
                        Sheets.refresh();
                    }));
                    b.addView(fr, U.lp(U.MATCH, U.WRAP));
                    List<String> ids = new ArrayList<>(states.keySet());
                    java.util.Collections.sort(ids);
                    for (int i = 0; i < fe.length(); i++) ids.remove(fe.optString(i));
                    List<String> ordered = new ArrayList<>();
                    for (int i = 0; i < fe.length(); i++) ordered.add(fe.optString(i));
                    ordered.addAll(ids);
                    int shown = 0;
                    for (String id : ordered) {
                        if (!controllable(id)) continue;
                        boolean sel = indexOf(fe, id) >= 0;
                        if (!sel && !filter.isEmpty() && !(id + " " + name(id)).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) continue;
                        if (!sel && shown++ > 80) continue;
                        int pos = indexOf(fe, id);
                        b.addView(Sheets.switchRow(icon(id), sel ? U.AMBER : U.SUB, (sel ? (pos + 1) + ". " : "") + name(id), id, sel, on -> {
                            if (on) fe.put(id);
                            else remove(fe, id);
                            Widgets.put(fc, "entities", fe);
                            Sheets.refresh();
                        }));
                    }
                }
                LinearLayout save = U.row(c);
                save.addView(U.button(c, "Save", true, () -> {
                    Widgets.put(fc, "entities", fe);
                    Widgets.put(spec, "cfg", fc);
                    Tiles.setSpec(index, spec);
                    Sheets.close();
                    refresh(null);
                }));
                b.addView(save, Widgets.top(16));
                return Sheets.panel("Home Assistant", b);
            }
        });
        if (configured() && states.isEmpty()) refresh(Sheets::refresh);
    }

    static int indexOf(JSONArray a, String s) {
        for (int i = 0; i < a.length(); i++) if (s.equals(a.optString(i))) return i;
        return -1;
    }

    static void remove(JSONArray a, String s) {
        int i = indexOf(a, s);
        if (i >= 0) a.remove(i);
    }
}
