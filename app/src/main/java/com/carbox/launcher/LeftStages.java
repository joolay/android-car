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

    /** The alternate stage's two faces: Go To (first, at start) and Drive; Next Prayer, Speed and Park here only turn in when needed. */
    static List<String> list() {
        List<String> out = new ArrayList<>();
        out.add("goto");
        out.add("drive");
        return out;
    }

    static boolean known(String id) {
        for (String[] w : Widgets.CATALOG) if (w[0].equals(id)) return true;
        return false;
    }

    static String current() {
        return "drive";
    }

    static String step(int dir) {
        return "drive";
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

    /** The only choice: which of the two (Drive, Now Playing) is on top and which below. */
    static void chooser(boolean unused) {
        Panels.show(() -> {
            Context c = Panels.ctx();
            LinearLayout card = Ui.col(c);
            card.setBackground(Themes.card(Ui.CARD, 18));
            Ui.pad(card, 20, 14, 20, 14);
            card.setLayoutParams(Panels.centered(560, 300));
            LinearLayout head = Ui.row(c);
            head.addView(Ui.line(c, "Left Stages", 20, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            head.addView(Ui.iconButton(c, R.drawable.ic_check, "Done", () -> {
                Panels.close();
                refresh();
            }));
            card.addView(head);
            card.addView(Ui.line(c, "Which one goes on top. Swipe a stage to turn it; Next Prayer, Speed and Park here turn in by themselves when needed.", 12, Ui.SUB, false), top(4));
            LinearLayout row = Ui.row(c);
            row.addView(option(c, "Drive", "Now Playing", !nowPlayingTop(), false), cellLp());
            row.addView(option(c, "Now Playing", "Drive", nowPlayingTop(), true), cellLp());
            card.addView(row, top(14));
            return card;
        });
    }

    private static LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, Ui.u(170), 1);
        l.setMargins(Ui.u(6), 0, Ui.u(6), 0);
        return l;
    }

    private static View option(Context c, String t, String b, boolean sel, boolean npTop) {
        LinearLayout cell = Ui.col(c);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(sel ? Ui.outline(0xFF15233A, Ui.ACCENT, 3, 16) : Themes.inner(Ui.CARD2, 16));
        cell.addView(Ui.line(c, "TOP", 11, Ui.SUB, true));
        cell.addView(Ui.line(c, t, 18, sel ? Ui.ACCENT_SOFT : Ui.TEXT, true), top(2));
        cell.addView(Ui.gap(c, 1, 14));
        cell.addView(Ui.line(c, "BOTTOM", 11, Ui.SUB, true));
        cell.addView(Ui.line(c, b, 18, sel ? Ui.ACCENT_SOFT : Ui.TEXT, true), top(2));
        cell.setOnClickListener(v -> {
            side(npTop);
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
        MainActivity.reWhite(); // White theme: left-stage widget icons/photos keep their true colours
    }

    private static LinearLayout.LayoutParams top(float du) {
        LinearLayout.LayoutParams l = Ui.lp(Ui.MATCH, Ui.WRAP);
        l.topMargin = Ui.u(du);
        return l;
    }
}
