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
    /** Icons in the dock before All apps: 5 to 7 (the dialler counts as one when shown). */
    static int icons() {
        return Math.max(5, Math.min(7, Store.num("dock_icons", 6)));
    }

    static boolean dialler() {
        return Store.flag("dock_dialer", true);
    }

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
            if (ap != null && !Apps.hidden().contains(k) && !Apps.dockHidden().contains(k)) pinned.add(ap);
        }
        final int SLOTS = icons() - (dialler() ? 1 : 0);
        while (pinned.size() > SLOTS) pinned.remove(pinned.size() - 1);
        int left = Math.max(0, SLOTS - pinned.size());
        List<Apps.App> recent = Apps.recents(left);
        // icons share the width evenly, so 6 icons spread out instead of bunching on the left
        spacer();
        // the dialler first (can be switched off in Settings → Dock)
        if (dialler()) addDialler();
        for (Apps.App ap : pinned) {
            add(ap, true);
            spacer();
        }
        if (!pinned.isEmpty() && !recent.isEmpty()) {
            divider();
            spacer();
        }
        for (Apps.App ap : recent) {
            add(ap, false);
            spacer();
        }
        allApps();
    }

    private void addDialler() {
        FrameLayout dial = new FrameLayout(a);
        dial.setBackground(U.oval(U.GREEN));
        dial.addView(U.icon(a, R.drawable.ic_call, U.TEXT), U.flpu(22, 22, Gravity.CENTER));
        dial.setOnClickListener(v -> { // press again to close
            if (PhoneScreen.isShown()) PhoneScreen.hide();
            else PhoneScreen.show(null);
        });
        dial.setContentDescription("Phone");
        U.press(dial);
        view.addView(dial, U.lpu(42, 42));
        spacer();
        divider(); // separates the dialler from the apps
        spacer();
        dial.setOnLongClickListener(v -> { // hold: dock settings (e.g. to remove the dialler)
            SettingsPanels.dock();
            return true;
        });
    }

    private void allApps() {
        divider();
        FrameLayout all = new FrameLayout(a);
        // All apps: a little mosaic of four coloured squares on a dark tile
        all.setBackground(U.round(U.CARD2, 10));
        LinearLayout mosaic = U.col(a);
        int[][] colors = {{U.ACCENT, U.GREEN}, {U.AMBER, U.RED}};
        for (int r = 0; r < 2; r++) {
            LinearLayout row = U.row(a);
            for (int k = 0; k < 2; k++) {
                View sq = new View(a);
                sq.setBackground(U.round(colors[r][k], 3.5f));
                LinearLayout.LayoutParams sl = U.lpu(10, 10);
                if (k == 1) sl.leftMargin = U.u(3);
                row.addView(sq, sl);
            }
            LinearLayout.LayoutParams rl = U.lp(U.WRAP, U.WRAP);
            if (r == 1) rl.topMargin = U.u(3);
            mosaic.addView(row, rl);
        }
        all.addView(mosaic, new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER));
        all.setContentDescription("All apps");
        all.setOnClickListener(v -> { // tap: the tiles slide in (map home view), else the widget chooser
            if (MainActivity.I != null && MainActivity.mapHome() && MainActivity.I.map.expanded()) MainActivity.I.toggleDrawer();
            else AppPanels.gallery(-1);
        });
        all.setOnLongClickListener(v -> { // hold: all apps
            AppPanels.allApps(false);
            return true;
        });
        U.press(all);
        LinearLayout.LayoutParams lp = U.lpu(40, 40);
        lp.leftMargin = U.u(14);
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
        view.addView(f, U.lpu(48, 48));
    }

    private void divider() {
        View d = new View(a);
        d.setBackgroundColor(U.LINE);
        LinearLayout.LayoutParams lp = U.lpu(1, 30);
        view.addView(d, lp);
    }

    private void spacer() {
        view.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
    }
}
