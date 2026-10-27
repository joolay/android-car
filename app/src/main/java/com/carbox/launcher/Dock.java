package com.carbox.launcher;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/** Bottom dock: pinned apps, then recently used apps, then All apps. */
final class Dock {
    private final Activity a;
    final LinearLayout view;
    private static final int SLOTS = 9;

    Dock(Activity a) {
        this.a = a;
        view = U.row(a);
        view.setBackground(U.round(U.CARD, 16));
        U.pad(view, 14, 0, 14, 0);
        Apps.listen(this::render);
    }

    void render() {
        view.removeAllViews();
        List<Apps.App> pinned = new ArrayList<>();
        for (String k : Apps.pins()) {
            Apps.App ap = Apps.get(k);
            if (ap != null && !Apps.hidden().contains(k)) pinned.add(ap);
        }
        int left = Math.max(0, SLOTS - pinned.size());
        List<Apps.App> recent = Apps.recents(left);
        for (Apps.App ap : pinned) add(ap, true);
        if (!pinned.isEmpty() && !recent.isEmpty()) divider();
        for (Apps.App ap : recent) add(ap, false);
        // fill remaining space so All apps sits at the right end
        view.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        divider();
        FrameLayout all = new FrameLayout(a);
        all.setBackground(U.round(U.ACCENT, 10));
        all.addView(U.icon(a, R.drawable.ic_apps, U.TEXT), U.flpu(24, 24, Gravity.CENTER));
        all.setOnClickListener(v -> AppPanels.allApps(false));
        U.press(all);
        LinearLayout.LayoutParams lp = U.lpu(40, 40);
        lp.leftMargin = U.u(6);
        view.addView(all, lp);
    }

    private void add(Apps.App ap, boolean pin) {
        FrameLayout f = new FrameLayout(a);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(ap.icon);
        f.addView(iv, U.flpu(40, 40, Gravity.CENTER));
        if (pin) {
            FrameLayout badge = new FrameLayout(a);
            badge.setBackground(U.oval(U.BG));
            badge.addView(U.icon(a, R.drawable.ic_pin, U.ACCENT), U.flpu(11, 11, Gravity.CENTER));
            FrameLayout.LayoutParams bl = U.flpu(16, 16, Gravity.TOP | Gravity.END);
            f.addView(badge, bl);
        }
        f.setOnClickListener(v -> {
            Apps.launch(a, ap);
            U.ui.postDelayed(this::render, 800);
        });
        f.setOnLongClickListener(v -> {
            AppPanels.appMenu(ap, this::render);
            return true;
        });
        f.setContentDescription(ap.label);
        U.press(f);
        LinearLayout.LayoutParams lp = U.lpu(48, 48);
        lp.rightMargin = U.u(8);
        view.addView(f, lp);
    }

    private void divider() {
        View d = new View(a);
        d.setBackgroundColor(U.LINE);
        LinearLayout.LayoutParams lp = U.lpu(1, 30);
        lp.setMargins(U.u(4), 0, U.u(12), 0);
        view.addView(d, lp);
    }
}
