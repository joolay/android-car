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
 * The Left Widgets, from one pool: every tile's widget in a roomier design, plus Music (Now playing
 * and the music folder, one widget).
 * - Top Left Widget: the small panel above the Bottom Left one while the map is big. Swipe it to go
 *   through the widgets chosen for it; hold it to choose them.
 * - Bottom Left Widget: one widget, Music to start with. Hold it to change it (with Music there, it's
 *   in Music settings, which holding opens).
 * Music can only be in one of the two places (there's one Now playing).
 */
final class TopLeft {
    private static final String KEY = "tl_widgets", BOTTOM = "bl_widget";
    static final String MUSIC = "music";

    private TopLeft() {
    }

    /** The Top Left Widget's chosen widgets, in order (Go to and Speed to start with). */
    static List<String> list() {
        List<String> out = new ArrayList<>();
        for (String id : Store.has(KEY) ? Store.list(KEY) : java.util.Arrays.asList("goto", "speed"))
            if (known(id) && Faith.widgetAllowed(id) && !out.contains(id) && !(MUSIC.equals(id) && MUSIC.equals(bottom()))) out.add(id);
        if (out.isEmpty()) out.add("goto");
        return out;
    }

    /** The Bottom Left Widget (Music to start with). */
    static String bottom() {
        String id = Store.str(BOTTOM, MUSIC);
        return known(id) && Faith.widgetAllowed(id) ? id : MUSIC;
    }

    static boolean known(String id) {
        if (MUSIC.equals(id)) return true;
        for (String[] w : Widgets.CATALOG) if (w[0].equals(id)) return true;
        return false;
    }

    /** The Top Left one showing now. */
    static String current() {
        List<String> l = list();
        String id = Store.str("map_mini", l.get(0));
        return l.contains(id) ? id : l.get(0);
    }

    /** Next (dir 1) or previous (dir -1) Top Left widget. */
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

    /** A widget in its Left Widget design (not for Music, which is the one Now playing). */
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

    private static void toggle(Context c, String id) {
        if (MUSIC.equals(id) && MUSIC.equals(bottom())) {
            android.widget.Toast.makeText(c, "Music is the Bottom Left Widget. Change that first to have it here.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        List<String> l = new ArrayList<>(list());
        if (l.contains(id)) {
            if (l.size() > 1) l.remove(id); // always at least one
        } else l.add(id);
        Store.putList(KEY, l);
        if (!l.contains(Store.str("map_mini", ""))) Store.put("map_mini", l.get(0));
    }

    private static void pickBottom(String id) {
        if (MUSIC.equals(id)) { // Music moves down here: off the Top Left
            List<String> l = new ArrayList<>(list());
            l.remove(MUSIC);
            if (l.isEmpty()) l.add("goto");
            Store.putList(KEY, l);
        }
        Store.put(BOTTOM, id);
    }

    /** Top Left Widget chooser. */
    static void chooser() {
        chooser(false);
    }

    /** Choose the Top Left Widget's widgets (several, swiped through) or the Bottom Left one (one). */
    static void chooser(boolean bottom) {
        keepX = 0;
        Sheets.showFull(() -> {
            Context c = Sheets.ctx();
            LinearLayout card = U.col(c);
            card.setBackground(Glass.card(U.CARD, 18));
            U.pad(card, 20, 14, 20, 14);
            card.setLayoutParams(Sheets.centered(784, 464));
            LinearLayout head = U.row(c);
            LinearLayout titles = U.col(c);
            titles.addView(U.line(c, bottom ? "Bottom Left Widget" : "Top Left Widget", 20, U.TEXT, true));
            titles.addView(U.line(c, bottom ? "Tap one to put it in the Bottom Left Widget · hold it there to change it again"
                    : "Highlighted ones are in it · tap to add or take off · swipe it to go through them", 12, U.SUB, false), top(2));
            head.addView(titles, new LinearLayout.LayoutParams(0, U.WRAP, 1));
            head.addView(U.seg(c, bottom ? "Top Left ›" : "Bottom Left ›", false, () -> chooser(!bottom))); // the other one
            head.addView(U.gap(c, 10, 1));
            head.addView(U.button(c, "Done", true, () -> {
                Sheets.close();
                refresh();
            }));
            card.addView(head);

            List<String> on = bottom ? java.util.Collections.singletonList(bottom()) : list();
            // grouped by category, with chips that jump to each (Music first: Now playing and the music folder)
            List<String[]> groups = new ArrayList<>();
            groups.add(new String[]{"Music", MUSIC});
            for (String[] g : Widgets.CATS) groups.add(g);
            LinearLayout blocks = U.row(c);
            blocks.setGravity(Gravity.TOP);
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            LinearLayout chips = U.row(c);
            java.util.Set<String> placed = new java.util.HashSet<>();
            for (int gi = 0; gi <= groups.size(); gi++) {
                List<String[]> items = new ArrayList<>();
                if (gi < groups.size()) {
                    for (String id : groups.get(gi)[1].split(","))
                        if (known(id) && Faith.widgetAllowed(id) && placed.add(id)) items.add(new String[]{id, Widgets.name(id)});
                } else for (String[] w : Widgets.CATALOG) if (Faith.widgetAllowed(w[0]) && placed.add(w[0])) items.add(w);
                if (items.isEmpty()) continue;
                String cat = gi < groups.size() ? groups.get(gi)[0] : "More";
                LinearLayout block = U.col(c);
                TextView ht = U.line(c, cat.toUpperCase(java.util.Locale.ROOT), 11, U.SUB, true);
                ht.setLetterSpacing(0.08f);
                U.pad(ht, 6, 0, 0, 0);
                block.addView(ht);
                LinearLayout cols = U.row(c);
                cols.setGravity(Gravity.TOP);
                for (int k = 0; k < items.size(); k += 2) {
                    LinearLayout col = U.col(c);
                    for (int r = 0; r < 2; r++) {
                        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(U.u(150), 0, 1);
                        cl.setMargins(U.u(5), U.u(5), U.u(5), U.u(5));
                        col.addView(k + r < items.size() ? cell(c, items.get(k + r), on, bottom) : new View(c), cl);
                    }
                    cols.addView(col, new LinearLayout.LayoutParams(U.WRAP, U.MATCH));
                }
                block.addView(cols, new LinearLayout.LayoutParams(U.WRAP, 0, 1));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(U.WRAP, U.MATCH);
                bl.rightMargin = U.u(14);
                blocks.addView(block, bl);
                TextView chip = U.seg(c, cat, false, () -> hs.smoothScrollTo(block.getLeft(), 0));
                LinearLayout.LayoutParams chl = U.lp(U.WRAP, U.WRAP);
                chl.rightMargin = U.u(8);
                chips.addView(chip, chl);
            }
            card.addView(chips, top(10));
            hs.setHorizontalScrollBarEnabled(false);
            hs.setFillViewport(true);
            shown = hs; // picking a widget redraws the page: stay where it was
            final int x0 = keepX;
            hs.post(() -> hs.scrollTo(x0, 0));
            hs.addView(blocks, new android.widget.FrameLayout.LayoutParams(U.WRAP, U.MATCH));
            LinearLayout.LayoutParams hl = U.weight(1, false);
            hl.topMargin = U.u(8);
            card.addView(hs, hl);
            return card;
        });
    }

    private static int keepX;
    private static android.widget.HorizontalScrollView shown;

    private static View cell(Context c, String[] w, List<String> on, boolean bottom) {
        boolean sel = on.contains(w[0]);
        boolean elsewhere = MUSIC.equals(w[0]) && (bottom ? list().contains(MUSIC) : MUSIC.equals(bottom()));
        LinearLayout cell = U.col(c);
        U.pad(cell, 5, 5, 5, 6);
        cell.setBackground(sel ? U.outline(0xFF15233A, U.ACCENT, 3, 16) : Glass.inner(U.CARD2, 16));
        cell.addView(MUSIC.equals(w[0]) ? musicPreview(c) : Widgets.preview(c, w[0]), new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        String label = sel && !bottom ? (on.indexOf(w[0]) + 1) + ". " + w[1] : w[1];
        if (elsewhere && !sel) label += bottom ? " · in Top Left" : " · in Bottom Left";
        TextView nm = U.line(c, label, 12, sel ? U.ACCENT_SOFT : U.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        cell.addView(nm, top(5));
        cell.setOnClickListener(v -> {
            if (shown != null) keepX = shown.getScrollX();
            if (bottom) pickBottom(w[0]);
            else toggle(c, w[0]);
            Sheets.refresh();
        });
        U.press(cell);
        return cell;
    }

    /** Music's picture in the chooser: Now playing with the music folder. */
    private static View musicPreview(Context c) {
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(Glass.card(U.CARD, 14));
        l.addView(U.icon(c, R.drawable.ic_music, U.ACCENT_SOFT), U.lpu(40, 40));
        TextView t = U.line(c, "Now playing", 13, U.TEXT, true);
        t.setGravity(Gravity.CENTER);
        l.addView(t, top(6));
        TextView s = U.line(c, "and the music folder", 11, U.SUB, false);
        s.setGravity(Gravity.CENTER);
        l.addView(s, top(2));
        return l;
    }

    /** Rebuild both Left Widgets after a change. */
    static void refresh() {
        MainActivity a = MainActivity.I;
        if (a == null) return;
        if (a.map != null) a.map.remini();
        a.bottomLeft();
    }

    private static LinearLayout.LayoutParams top(float du) {
        LinearLayout.LayoutParams l = U.lp(U.MATCH, U.WRAP);
        l.topMargin = U.u(du);
        return l;
    }
}
