package com.carbox.launcher;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Large speed warning in the 2×4 tile area when you go over the limit. Updates live; closes when you slow down. */
final class SpeedCard {
    private static FrameLayout shown;
    private static TextView speed, sign;
    private static boolean wasOver;

    private SpeedCard() {
    }

    static void start() {
        Loc.listen(SpeedCard::check);
    }

    private static void check() {
        boolean over = SpeedLimit.over();
        if (over && !wasOver) show();      // just went over: expand
        else if (!over && wasOver) hide(); // back under: close
        wasOver = over;
        if (shown != null) update();
    }

    private static void show() {
        MainActivity a = MainActivity.I;
        if (a == null || !a.isResumed2() || shown != null) return;
        shown = new FrameLayout(a);
        shown.setClickable(true);
        shown.setBackground(U.outline(0xFF5A1010, 0xFFE24B4A, 4, 16));
        LinearLayout row = U.row(a);
        row.setGravity(Gravity.CENTER);
        U.pad(row, 24, 16, 24, 16);
        LinearLayout left = U.col(a);
        left.addView(U.line(a, "SLOW DOWN", 14, 0xFFFF8A80, true));
        speed = U.line(a, "", 64, U.TEXT, true);
        left.addView(speed, Widgets.top(2));
        left.addView(U.line(a, Widgets.mph() ? "mph" : "km/h", 16, U.TEXT2, false));
        row.addView(left, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        sign = U.text(a, "", 34, 0xFF111111, true); // the limit, as a road sign
        sign.setGravity(Gravity.CENTER);
        sign.setBackground(U.outline(0xFFFFFFFF, 0xFFE24B4A, 10, 60));
        row.addView(sign, U.lpu(120, 120));
        shown.addView(row, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        View x = U.icon(a, R.drawable.ic_x, U.TEXT2);
        U.pad(x, 8, 8, 8, 8);
        x.setOnClickListener(v -> hide());
        shown.addView(x, U.flpu(40, 40, Gravity.TOP | Gravity.END));
        shown.setOnClickListener(v -> hide());
        a.tileArea.addView(shown, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        update();
        a.resetIdle(); // minimises after 15 s like the other panels
    }

    private static void update() {
        boolean m = Widgets.mph();
        float f = m ? 2.23694f : 3.6f;
        speed.setText(String.valueOf(Math.round(Loc.speed * f)));
        sign.setText(String.valueOf(Math.round(SpeedLimit.kmh * (m ? 0.621371f : 1f))));
    }

    static void hide() {
        if (shown == null) return;
        if (shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
    }
}
