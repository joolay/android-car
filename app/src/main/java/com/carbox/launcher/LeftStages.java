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
final class LeftStages {
    private static final String KEY = "tl_widgets";

    private LeftStages() {
    }

    // The two left stages: Now Playing (a fixture, always on one of them) and the alternate stage, which
    // shows the alternate widgets (swiped through) and is where Maps minimises when something takes the
    // Centre Stage or the Tile Drawer opens. Start: Go To above, Now Playing below.
    static final String NP_TOP = "np_top";

    /** Now Playing is on the Top-Left Stage (else the Bottom-Left one, the start). */
    static boolean nowPlayingTop() {
        return Prefs.flag(NP_TOP, false);
    }

    /** The alternate stage's widgets, in order (Go To to start with). Never Now Playing or Maps. */
    static List<String> list() {
        List<String> out = new ArrayList<>();
        for (String id : Prefs.has(KEY) ? Prefs.list(KEY) : java.util.Collections.singletonList("goto"))
            if (known(id) && Faith.widgetAllowed(id) && Addons.unlocked(id) && !out.contains(id)) out.add(id);
        if (out.isEmpty()) out.add("goto");
        return out;
    }

    static boolean known(String id) {
        for (String[] w : Widgets.CATALOG) if (w[0].equals(id)) return true;
        return false;
    }

    /** The Top Left one showing now. */
    static String current() {
        List<String> l = list();
        String id = Prefs.str("map_mini", l.get(0));
        return l.contains(id) ? id : l.get(0);
    }

    /** Next (dir 1) or previous (dir -1) Top Left widget. */
    static String step(int dir) {
        List<String> l = list();
        int i = l.indexOf(current());
        String id = l.get(((i + dir) % l.size() + l.size()) % l.size());
        Prefs.put("map_mini", id);
        return id;
    }

    /** Same widget as in the tiles (same settings) if it's there, else a fresh one. Index -1 when not in the tiles. */
    static int tileIndex(String id) {
        JSONArray s = TileDrawer.specs();
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return i;
        }
        return -1;
    }

    /** A widget in its Left Widget design (not for Music, which is the one Now playing). */
    static Widgets.W make(Context c, String id) {
        int idx = tileIndex(id);
        JSONObject spec = idx >= 0 ? TileDrawer.specs().optJSONObject(idx) : TileDrawer.widget(id);
        return Widgets.make(c, idx, spec, Widgets.Profile.MAIN);
    }

    private static void toggle(Context c, String id) {
        List<String> l = new ArrayList<>(list());
        if (l.contains(id)) {
            if (l.size() > 1) l.remove(id); // always at least one
        } else l.add(id);
        Prefs.putList(KEY, l);
        if (!l.contains(Prefs.str("map_mini", ""))) Prefs.put("map_mini", l.get(0));
    }

    /** Put Now Playing on the top (or bottom) left stage; the alternate stage swaps to the other side. */
    private static void side(boolean top) {
        if (top == nowPlayingTop()) return;
        Prefs.put(NP_TOP, top);
        if (MainActivity.I != null) MainActivity.I.placeStages();
        Panels.refresh();
    }

    /** Top Left Widget chooser. */
    static void chooser() {
        chooser(false);
    }

    /** Choose the Top Left Widget's widgets (several, swiped through) or the Bottom Left one (one). */
    static void chooser(boolean bottom) {
        keepX = 0;
        Panels.showFull(() -> {
            Context c = Panels.ctx();
            LinearLayout card = Ui.col(c);
            card.setBackground(Themes.card(Ui.CARD, 18));
            Ui.pad(card, 20, 14, 20, 14);
            card.setLayoutParams(Panels.centered(784, 464));
            LinearLayout head = Ui.row(c);
            LinearLayout titles = Ui.col(c);
            titles.addView(Ui.line(c, (nowPlayingTop() ? "Bottom" : "Top") + "-Left Stage", 20, Ui.TEXT, true));
            titles.addView(Ui.line(c, "Highlighted ones are on it · tap to add or take off · swipe the stage to go through them", 12, Ui.SUB, false), top(2));
            head.addView(titles, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            // which side Now Playing sits on (the alternate stage takes the other)
            head.addView(Ui.text(c, "Now Playing", 13, Ui.SUB, false));
            head.addView(Ui.gap(c, 8, 1));
            head.addView(Ui.seg(c, "Top", nowPlayingTop(), () -> side(true)));
            head.addView(Ui.gap(c, 6, 1));
            head.addView(Ui.seg(c, "Bottom", !nowPlayingTop(), () -> side(false)));
            head.addView(Ui.gap(c, 12, 1));
            head.addView(Ui.iconButton(c, R.drawable.ic_check, "Done", () -> {
                Panels.close();
                refresh();
            }));
            card.addView(head);

            List<String> on = list();
            // grouped by category, with chips that jump to each
            List<String[]> groups = new ArrayList<>();
            for (String[] g : Widgets.CATS) groups.add(g);
            LinearLayout blocks = Ui.row(c);
            blocks.setGravity(Gravity.TOP);
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            LinearLayout chips = Ui.row(c);
            java.util.Set<String> placed = new java.util.HashSet<>();
            for (int gi = 0; gi <= groups.size(); gi++) {
                List<String[]> items = new ArrayList<>();
                if (gi < groups.size()) {
                    for (String id : groups.get(gi)[1].split(","))
                        if (known(id) && Faith.widgetAllowed(id) && Addons.unlocked(id) && placed.add(id)) items.add(new String[]{id, Widgets.name(id)});
                } else for (String[] w : Widgets.CATALOG) if (Faith.widgetAllowed(w[0]) && Addons.unlocked(w[0]) && placed.add(w[0])) items.add(w);
                if (items.isEmpty()) continue;
                String cat = gi < groups.size() ? groups.get(gi)[0] : "More";
                LinearLayout block = Ui.col(c);
                TextView ht = Ui.line(c, cat.toUpperCase(java.util.Locale.ROOT), 11, Ui.SUB, true);
                ht.setLetterSpacing(0.08f);
                Ui.pad(ht, 6, 0, 0, 0);
                block.addView(ht);
                LinearLayout cols = Ui.row(c);
                cols.setGravity(Gravity.TOP);
                for (int k = 0; k < items.size(); k += 2) {
                    LinearLayout col = Ui.col(c);
                    for (int r = 0; r < 2; r++) {
                        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.u(150), 0, 1);
                        cl.setMargins(Ui.u(5), Ui.u(5), Ui.u(5), Ui.u(5));
                        col.addView(k + r < items.size() ? cell(c, items.get(k + r), on, bottom) : new View(c), cl);
                    }
                    cols.addView(col, new LinearLayout.LayoutParams(Ui.WRAP, Ui.MATCH));
                }
                block.addView(cols, new LinearLayout.LayoutParams(Ui.WRAP, 0, 1));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.MATCH);
                bl.rightMargin = Ui.u(14);
                blocks.addView(block, bl);
                TextView chip = Ui.seg(c, cat, false, () -> hs.smoothScrollTo(block.getLeft(), 0));
                LinearLayout.LayoutParams chl = Ui.lp(Ui.WRAP, Ui.WRAP);
                chl.rightMargin = Ui.u(8);
                chips.addView(chip, chl);
            }
            card.addView(chips, top(10));
            hs.setHorizontalScrollBarEnabled(false);
            hs.setFillViewport(true);
            shown = hs; // picking a widget redraws the page: stay where it was
            final int x0 = keepX;
            hs.post(() -> hs.scrollTo(x0, 0));
            hs.addView(blocks, new android.widget.FrameLayout.LayoutParams(Ui.WRAP, Ui.MATCH));
            LinearLayout.LayoutParams hl = Ui.weight(1, false);
            hl.topMargin = Ui.u(8);
            card.addView(hs, hl);
            return card;
        });
    }

    private static int keepX;
    private static android.widget.HorizontalScrollView shown;

    private static View cell(Context c, String[] w, List<String> on, boolean bottom) {
        boolean sel = on.contains(w[0]);
        LinearLayout cell = Ui.col(c);
        Ui.pad(cell, 5, 5, 5, 6);
        cell.setBackground(sel ? Ui.outline(0xFF15233A, Ui.ACCENT, 3, 16) : Themes.inner(Ui.CARD2, 16));
        cell.addView(Widgets.preview(c, w[0]), new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        String label = sel ? (on.indexOf(w[0]) + 1) + ". " + w[1] : w[1];
        TextView nm = Ui.line(c, label, 12, sel ? Ui.ACCENT_SOFT : Ui.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        cell.addView(nm, top(5));
        cell.setOnClickListener(v -> {
            if (shown != null) keepX = shown.getScrollX();
            toggle(c, w[0]);
            Panels.refresh();
        });
        Ui.press(cell);
        return cell;
    }

    /** Rebuild both Left Widgets after a change. */
    static void refresh() {
        MainActivity a = MainActivity.I;
        if (a == null) return;
        if (a.map != null) a.map.remini();
    }

    private static LinearLayout.LayoutParams top(float du) {
        LinearLayout.LayoutParams l = Ui.lp(Ui.MATCH, Ui.WRAP);
        l.topMargin = Ui.u(du);
        return l;
    }
}
