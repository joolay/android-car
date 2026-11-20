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
        Gps.listen(SpeedCard::check);
    }

    private static void check() {
        boolean over = SpeedLimit.over();
        if (over && !wasOver) {
            if (Alerts.expand("speed")) show(); // just went over: expand (the Speed tile lights up either way)
            else Alerts.front("speed");
        }
        else if (!over && wasOver) hide(); // back under: close
        wasOver = over;
        if (shown != null) update();
    }

    private static void show() {
        MainActivity a = MainActivity.I;
        if (a == null || !a.isResumed2() || shown != null) return;
        shown = new FrameLayout(a);
        shown.setClickable(true);
        shown.setBackground(Ui.outline(0xFF5A1010, 0xFFE24B4A, 4, 16));
        LinearLayout row = Ui.row(a);
        row.setGravity(Gravity.CENTER);
        Ui.pad(row, 24, 16, 24, 16);
        LinearLayout left = Ui.col(a);
        left.addView(Ui.line(a, "SLOW DOWN", 14, 0xFFFF8A80, true));
        speed = Ui.line(a, "", 64, Ui.TEXT, true);
        left.addView(speed, Widgets.top(2));
        left.addView(Ui.line(a, Widgets.mph() ? "mph" : "km/h", 16, Ui.TEXT2, false));
        row.addView(left, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        sign = Ui.text(a, "", 34, 0xFF111111, true); // the limit, as a road sign
        sign.setGravity(Gravity.CENTER);
        sign.setBackground(Ui.outline(0xFFFFFFFF, 0xFFE24B4A, 10, 60));
        row.addView(sign, Ui.lpu(120, 120));
        shown.addView(row, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View x = Ui.icon(a, R.drawable.ic_x, Ui.TEXT2);
        Ui.pad(x, 8, 8, 8, 8);
        x.setOnClickListener(v -> hide());
        shown.addView(x, Ui.flpu(46, 46, Gravity.TOP | Gravity.END));
        shown.setOnClickListener(v -> hide());
        a.tileArea.addView(shown, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        a.growIn(shown, a.alertRect("speed")); // grows out of the speed tile
        update();
        a.resetIdle(); // minimises after 15 s like the other panels
    }

    private static void update() {
        boolean m = Widgets.mph();
        float f = m ? 2.23694f : 3.6f;
        speed.setText(String.valueOf(Math.round(Gps.speed * f)));
        sign.setText(String.valueOf(Math.round(SpeedLimit.kmh * (m ? 0.621371f : 1f))));
    }

    static void hide() {
        if (shown == null) return;
        if (shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
    }
}
