package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The Top Left Widget: the small panel above Now playing while the map is big. Swipe it to go
 * through the widgets chosen for it; hold it to choose them. Each one is the same widget as in the
 * tiles (same settings) in its own Top Left design.
 */
final class TopLeft {
    private static final String KEY = "tl_widgets";

    private TopLeft() {
    }

    /** The chosen widgets, in order (Go to and Speed to start with, as before). */
    static List<String> list() {
        List<String> out = new ArrayList<>();
        for (String id : Store.has(KEY) ? Store.list(KEY) : java.util.Arrays.asList("goto", "speed"))
            if (known(id) && Faith.widgetAllowed(id) && !out.contains(id)) out.add(id);
        if (out.isEmpty()) out.add("goto");
        return out;
    }

    private static boolean known(String id) {
        for (String[] w : Widgets.CATALOG) if (w[0].equals(id)) return true;
        return false;
    }

    /** The one showing now. */
    static String current() {
        List<String> l = list();
        String id = Store.str("map_mini", l.get(0));
        return l.contains(id) ? id : l.get(0);
    }

    /** Next (dir 1) or previous (dir -1) chosen widget. */
    static String step(int dir) {
        List<String> l = list();
        int i = l.indexOf(current());
        String id = l.get(((i + dir) % l.size() + l.size()) % l.size());
        Store.put("map_mini", id);
        return id;
    }

    /** Same widget as in the tiles (same settings) if it's there, else a fresh one. Index -1 when not in the tiles. */
    static int tileIndex(String id) {
        JSONArray s = Tiles.specs();
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return i;
        }
        return -1;
    }

    static Widgets.W make(Context c, String id) {
        int idx = tileIndex(id);
        JSONObject spec = idx >= 0 ? Tiles.specs().optJSONObject(idx) : Tiles.widget(id);
        Widgets.TL = true;
        try {
            return Widgets.make(c, idx, spec);
        } finally {
            Widgets.TL = false;
        }
    }

    private static void toggle(String id) {
        List<String> l = new ArrayList<>(list());
        if (l.contains(id)) {
            if (l.size() > 1) l.remove(id); // always at least one
        } else l.add(id);
        Store.putList(KEY, l);
        if (!l.contains(Store.str("map_mini", ""))) Store.put("map_mini", l.get(0));
    }

    /** Hold the Top Left Widget: choose which widgets it swipes through. */
    static void chooser() {
        Sheets.showFull(() -> {
            Context c = Sheets.ctx();
            LinearLayout card = U.col(c);
            card.setBackground(U.round(U.CARD, 18));
            U.pad(card, 20, 14, 20, 14);
            card.setLayoutParams(Sheets.centered(784, 464));
            LinearLayout head = U.row(c);
            LinearLayout titles = U.col(c);
            titles.addView(U.line(c, "Top Left Widget", 20, U.TEXT, true));
            titles.addView(U.line(c, "Highlighted ones are in it · tap to add or take off · swipe the widget to go through them", 12, U.SUB, false), top(2));
            head.addView(titles, new LinearLayout.LayoutParams(0, U.WRAP, 1));
            head.addView(U.button(c, "Done", true, () -> {
                Sheets.close();
                refresh();
            }));
            card.addView(head);
            List<String> on = list();
            LinearLayout cols = U.row(c);
            cols.setGravity(Gravity.TOP);
            List<String[]> all = new ArrayList<>();
            for (String[] w : Widgets.CATALOG) if (Faith.widgetAllowed(w[0])) all.add(w);
            for (int k = 0; k < all.size(); k += 2) {
                LinearLayout col = U.col(c);
                for (int r = 0; r < 2; r++) {
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(U.u(150), 0, 1);
                    cl.setMargins(U.u(5), U.u(5), U.u(5), U.u(5));
                    col.addView(k + r < all.size() ? cell(c, all.get(k + r), on) : new View(c), cl);
                }
                cols.addView(col, new LinearLayout.LayoutParams(U.WRAP, U.MATCH));
            }
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setFillViewport(true);
            hs.addView(cols, new android.widget.FrameLayout.LayoutParams(U.WRAP, U.MATCH));
            LinearLayout.LayoutParams hl = U.weight(1, false);
            hl.topMargin = U.u(8);
            card.addView(hs, hl);
            return card;
        });
    }

    private static View cell(Context c, String[] w, List<String> on) {
        boolean sel = on.contains(w[0]);
        LinearLayout cell = U.col(c);
        U.pad(cell, 5, 5, 5, 6);
        cell.setBackground(sel ? U.outline(0xFF15233A, U.ACCENT, 3, 16) : U.round(U.CARD2, 16));
        cell.addView(Widgets.preview(c, w[0]), new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        String label = sel ? (on.indexOf(w[0]) + 1) + ". " + w[1] : w[1];
        TextView nm = U.line(c, label, 12, sel ? U.ACCENT_SOFT : U.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        cell.addView(nm, top(5));
        cell.setOnClickListener(v -> {
            toggle(w[0]);
            Sheets.refresh();
        });
        U.press(cell);
        return cell;
    }

    /** Rebuild the panel after a change. */
    static void refresh() {
        if (MainActivity.I != null && MainActivity.I.map != null) MainActivity.I.map.remini();
    }

    private static LinearLayout.LayoutParams top(float du) {
        LinearLayout.LayoutParams l = U.lp(U.MATCH, U.WRAP);
        l.topMargin = U.u(du);
        return l;
    }
}
