package com.carbox.launcher;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Large "Time for Salaah" notice filling the 2×4 tile area at each prayer time. Tap or ✕ to close. */
final class SalahCard {
    private static FrameLayout shown;

    private SalahCard() {
    }

    static void show(String prayer) {
        MainActivity a = MainActivity.I;
        if (a == null) return;
        hide();
        shown = new FrameLayout(a);
        shown.setClickable(true);
        shown.setBackground(Themes.card(0xFF0F3D2E, 16));
        LinearLayout col = Ui.col(a);
        col.setGravity(Gravity.CENTER);
        Ui.pad(col, 20, 16, 20, 16);
        FrameLayout ic = new FrameLayout(a);
        ic.setBackground(Ui.oval(0x33FFFFFF));
        ic.addView(Ui.icon(a, R.drawable.ic_moon, Ui.MINT), Ui.flpu(34, 34, Gravity.CENTER));
        col.addView(ic, Ui.lpu(68, 68));
        col.addView(Ui.line(a, "TIME FOR SALAAH", 12, Ui.MINT, true), Widgets.top(14));
        TextView name = Ui.line(a, prayer, 44, Ui.TEXT, true);
        col.addView(name, Widgets.top(2));
        String next = "";
        double[] n = Prayer.next();
        if (n != null) next = "Next: " + Prayer.NAMES[(int) n[0]] + " at " + InfoPanels.clock(n[2]);
        col.addView(Ui.line(a, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date()) + (next.isEmpty() ? "" : "  ·  " + next), 15, Ui.TEXT2, false), Widgets.top(8));
        shown.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View x = Ui.icon(a, R.drawable.ic_x, Ui.TEXT2);
        Ui.pad(x, 8, 8, 8, 8);
        x.setOnClickListener(v -> hide());
        shown.addView(x, Ui.flpu(46, 46, Gravity.TOP | Gravity.END));
        shown.setOnClickListener(v -> hide());
        a.tileArea.addView(shown, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        a.growIn(shown, a.alertRect("salah")); // grows out of the prayer tile
        a.resetIdle(); // minimises by itself after the usual 15 s
    }

    static void hide() {
        if (shown == null) return;
        if (shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
    }
}
