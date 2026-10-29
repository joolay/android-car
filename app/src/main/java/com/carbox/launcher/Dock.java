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

    /** The dock stays the same size with or without the news ticker (the map and tiles take that space). */
    static float scale() {
        return Store.flag("ticker_on", true) ? 1f : 1.12f; // a touch bigger in the taller dock
    }

    private static float fit = 1f; // shrinks everything a little when the full dock wouldn't fit its width

    private static float s(float du) {
        return du * scale() * fit;
    }
    /** Icons in the dock before All apps: 5 to 8 (the dialler counts as one when shown). */
    static int icons() {
        return Math.max(5, Math.min(8, Store.num("dock_icons", 7)));
    }

    static boolean dialler() {
        return Store.flag("dock_dialer", true);
    }

    Dock(Activity a) {
        this.a = a;
        view = U.row(a);
        view.setBackground(U.round(U.CARD, 16));
        U.pad(view, 10, 0, 10, 0);
        Apps.listen(this::render);
        // re-fit whenever the dock's width changes (the lock comes and goes beside it)
        view.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) v.post(this::render);
        });
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
        // everything at full size: padding, dialler, icons, dividers, All apps; if that's wider than the
        // dock, it all shrinks together so All apps is never cut off
        int apps = pinned.size() + recent.size();
        float need = 20 + (dialler() ? 42 + 1 : 0) + apps * 48 + (!pinned.isEmpty() && !recent.isEmpty() ? 1 : 0) + 1 + 14 + 40 + 6;
        need = U.uf(need * scale());
        int avail = view.getWidth();
        fit = avail > 0 ? Math.min(1f, avail / need) : 1f;
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
        dial.addView(U.icon(a, R.drawable.ic_call, U.TEXT), U.flpu(s(22), s(22), Gravity.CENTER));
        dial.setOnClickListener(v -> { // press again to close
            if (PhoneScreen.isShown()) PhoneScreen.hide();
            else PhoneScreen.show(null);
        });
        dial.setContentDescription("Phone");
        U.press(dial);
        view.addView(dial, U.lpu(s(42), s(42)));
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
                LinearLayout.LayoutParams sl = U.lpu(s(10), s(10));
                if (k == 1) sl.leftMargin = U.u(s(3));
                row.addView(sq, sl);
            }
            LinearLayout.LayoutParams rl = U.lp(U.WRAP, U.WRAP);
            if (r == 1) rl.topMargin = U.u(s(3));
            mosaic.addView(row, rl);
        }
        all.addView(mosaic, new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER));
        all.setContentDescription("All apps");
        all.setOnClickListener(v -> { // tap: the tiles slide in (map home view), else the widget chooser
            if (MainActivity.I != null && MainActivity.mapHome()) MainActivity.I.toggleDrawer();
            else AppPanels.gallery(-1);
        });
        all.setOnLongClickListener(v -> { // hold: all apps
            AppPanels.allApps(false);
            return true;
        });
        U.press(all);
        LinearLayout.LayoutParams lp = U.lpu(s(40), s(40));
        lp.leftMargin = U.u(s(14));
        view.addView(all, lp);
    }

    private void add(Apps.App ap, boolean pin) {
        FrameLayout f = new FrameLayout(a);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(ap.icon);
        f.addView(iv, U.flpu(s(40), s(40), Gravity.CENTER));
        if (pin) {
            FrameLayout badge = new FrameLayout(a);
            badge.setBackground(U.oval(U.BG));
            badge.addView(U.icon(a, R.drawable.ic_pin, U.ACCENT), U.flpu(s(11), s(11), Gravity.CENTER));
            FrameLayout.LayoutParams bl = U.flpu(s(16), s(16), Gravity.TOP | Gravity.END);
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
        view.addView(f, U.lpu(s(48), s(48)));
    }

    private void divider() {
        View d = new View(a);
        d.setBackgroundColor(U.LINE);
        LinearLayout.LayoutParams lp = U.lpu(s(1), s(30));
        view.addView(d, lp);
    }

    private void spacer() {
        view.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
    }
}
