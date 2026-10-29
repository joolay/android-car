package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Turn-by-turn from Google Maps / Waze (read from their navigation notification), shown large in
 * the 2×4 tile area while navigating, so the map panel can keep showing the map. ✕ hides it until
 * the next trip; tapping it opens the navigation app.
 */
final class NavCard {
    static final String WHERE = "nav_where"; // "tiles" (default) or "map"
    private static FrameLayout shown;
    private static boolean dismissed;

    private NavCard() {
    }

    /** "swap": while navigating, the map fills the tile area and the directions stay in the map panel. */
    static boolean swap() {
        return "swap".equals(Store.str(WHERE, "tiles"));
    }

    static boolean inTiles() {
        return "tiles".equals(Store.str(WHERE, "tiles"));
    }

    static void start() {
        MediaListener.listenNav(NavCard::update);
    }

    static void update() {
        MainActivity a = MainActivity.I;
        MediaListener.Nav n = MediaListener.nav;
        if (n == null) dismissed = false; // trip over: show again next time
        if (a == null || n == null || dismissed || !inTiles()) {
            hide();
            return;
        }
        if (shown == null) {
            shown = new FrameLayout(a);
            shown.setClickable(true);
            // above the tiles and panels, under a call card
            int at = a.tileArea.getChildCount();
            for (int i = 0; i < a.tileArea.getChildCount(); i++) if (a.tileArea.getChildAt(i).getTag() == CallCard.class) at = i;
            a.tileArea.addView(shown, at, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        }
        render(a, n);
    }

    static void hide() {
        if (shown == null) return;
        if (shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
    }

    private static void render(Context c, MediaListener.Nav n) {
        shown.removeAllViews();
        shown.setBackground(U.round(0xFF0F3D2E, 16));
        LinearLayout row = U.row(c);
        U.pad(row, 22, 16, 22, 16);

        // left: the turn
        LinearLayout turn = U.col(c);
        turn.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(c);
        if (n.icon != null) iv.setImageBitmap(n.icon);
        else iv.setImageResource(R.drawable.ic_turn);
        iv.setColorFilter(n.icon != null ? 0 : U.TEXT);
        turn.addView(iv, U.lpu(84, 84));
        TextView d = U.line(c, n.distance != null ? n.distance : "", 40, U.TEXT, true);
        turn.addView(d, Widgets.top(6));
        TextView road = U.text(c, n.instruction != null ? n.instruction : "", 20, U.TEXT2, false);
        road.setMaxLines(3);
        turn.addView(road, Widgets.top(4));
        row.addView(turn, new LinearLayout.LayoutParams(0, U.MATCH, 1.3f));

        // right: arrival
        LinearLayout side = U.col(c);
        side.setBackground(U.round(0x22FFFFFF, 14));
        U.pad(side, 16, 14, 16, 14);
        side.addView(U.line(c, "ARRIVAL", 11, U.MINT, true));
        TextView eta = U.text(c, n.eta != null ? n.eta : "—", 18, U.TEXT, false);
        eta.setMaxLines(3);
        side.addView(eta, Widgets.top(6));
        side.addView(new View(c), U.weight(1, false));
        side.addView(U.line(c, Apps.label(MediaListener.navPkg), 12, U.SUB, false));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, U.MATCH, 1);
        sl.leftMargin = U.u(16);
        row.addView(side, sl);
        shown.addView(row, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        shown.setOnClickListener(v -> {
            if (MainActivity.I != null && !Apps.launchPkg(MainActivity.I, MediaListener.navPkg)) Actions.openNavigation(MainActivity.I);
        });

        View x = U.icon(c, R.drawable.ic_x, U.TEXT2);
        U.pad(x, 8, 8, 8, 8);
        x.setOnClickListener(v -> {
            dismissed = true;
            hide();
        });
        shown.addView(x, U.flpu(46, 46, Gravity.TOP | Gravity.END));
    }
}
