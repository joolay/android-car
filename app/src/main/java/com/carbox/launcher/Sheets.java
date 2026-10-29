package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Panels and popups drawn over the home screen (instead of Android dialogs), so they match
 * the launcher's look. Pages stack: the back arrow returns to the previous page.
 */
final class Sheets {
    interface Page {
        View build();
    }

    private static FrameLayout layer, tileLayer, fullLayer;
    private static boolean full; // true: over the whole screen (tile editor); false: inside the tile grid
    private static final List<Boolean> modes = new ArrayList<>(); // full-screen or not, per open page
    private static Context ctx;
    private static final List<Page> stack = new ArrayList<>();
    private static final List<Runnable> onClose = new ArrayList<>();
    private static int scrimColor = 0x99000000;

    private Sheets() {
    }

    /** Panels open inside the 2×4 tile area (tiles); a few full-screen ones use the whole screen (full). */
    static void attach(FrameLayout tiles, FrameLayout fullScreen) {
        tileLayer = layer = tiles;
        fullLayer = fullScreen;
        ctx = tiles.getContext();
    }

    /** Like show(), but over the whole screen. */
    static void showFull(Page p) {
        stack.clear();
        stack.add(p);
        modes.clear();
        modes.add(true);
        scrimColor = 0x99000000;
        full = true;
        render();
    }

    static boolean open() {
        return layer != null && layer.getChildCount() > 0;
    }

    /** Replace everything with a new page stack. */
    static void show(Page p) {
        stack.clear();
        stack.add(p);
        modes.clear();
        modes.add(false);
        scrimColor = 0x99000000;
        full = false;
        grow = true;
        render();
    }

    private static boolean grow; // the next tile-area panel grows out of the tile that opened it

    /** Open a page on top of the current one (back arrow returns). */
    static void push(Page p) {
        stack.add(p);
        modes.add(full);
        render();
    }

    /** Open a page on top, over the whole screen (Back returns to the page before, in its own size). */
    static void pushFull(Page p) {
        stack.add(p);
        modes.add(true);
        full = true;
        render();
    }

    static void back() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            if (!modes.isEmpty()) modes.remove(modes.size() - 1);
            if (!modes.isEmpty()) full = modes.get(modes.size() - 1);
            render();
        } else close();
    }

    /** Rebuild the current page (after a setting changes). */
    static void refresh() {
        if (!stack.isEmpty()) render();
    }

    static void close() {
        Keyboard.hide();
        stack.clear();
        modes.clear();
        if (tileLayer != null) tileLayer.removeAllViews();
        if (fullLayer != null) fullLayer.removeAllViews();
        full = false;
        layer = tileLayer;
        for (Runnable r : new ArrayList<>(onClose)) r.run();
        onClose.clear();
        if (MainActivity.I != null) {
            MainActivity.I.tickerTitle(null); // the ticker comes back
            MainActivity.I.mapAside(false); // and the map
        }
    }

    /** A panel is open in the tile area. */
    static boolean inTiles() {
        return !stack.isEmpty() && !full;
    }

    static void whenClosed(Runnable r) {
        onClose.add(r);
    }

    /** Copy of the open pages, to bring them back later (e.g. after a splash preview). */
    static List<Page> saveStack() {
        return new ArrayList<>(stack);
    }

    static void restoreStack(List<Page> pages) {
        if (pages == null || pages.isEmpty()) return;
        List<Boolean> m = new ArrayList<>();
        for (Page pg : pages) {
            int at = stack.indexOf(pg);
            m.add(at >= 0 && at < modes.size() ? modes.get(at) : full);
        }
        stack.clear();
        stack.addAll(pages);
        modes.clear();
        modes.addAll(m);
        full = m.get(m.size() - 1);
        scrimColor = 0x99000000;
        render();
    }

    static Page top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    static int depth() {
        return stack.size();
    }

    private static void render() {
        FrameLayout target = full ? fullLayer : tileLayer;
        if (target == null) return;
        if (layer != null && layer != target) layer.removeAllViews();
        layer = target;
        layer.removeAllViews();
        View scrim = new View(ctx);
        scrim.setBackgroundColor(scrimColor);
        // tapping beside a panel closes it, except full-screen pages (they have Done / ✕)
        scrim.setOnClickListener(v -> {
            if (!full || (cardPage != null && top() == cardPage)) close();
        });
        layer.addView(scrim, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        if (MainActivity.I != null) MainActivity.I.tickerTitle(null); // the page sets its title there again if it has one
        View page = stack.get(stack.size() - 1).build();
        page.setClickable(true); // swallow taps so they don't close the sheet
        page.setOnTouchListener((v, e) -> {
            touched();
            return false;
        });
        if (!full) page.setLayoutParams(new FrameLayout.LayoutParams(U.MATCH, U.MATCH)); // fill the tile area exactly
        else if (page.getLayoutParams() == null) page.setLayoutParams(centered(640, 440));
        layer.addView(page);
        if (grow && !full && MainActivity.I != null) MainActivity.I.growIn(page, null);
        if (!full && MainActivity.I != null) MainActivity.I.mapAside(true); // the big map steps aside for the panel
        grow = false;
    }

    /** Lighter scrim for small anchored cards. */
    static void showCard(Page p) {
        stack.clear();
        stack.add(p);
        modes.clear();
        modes.add(true);
        scrimColor = 0x59000000;
        full = true; // rail pop-ups sit next to the rail, over the whole screen, as before
        render();
        // pop out of the Rail Bar icon that was tapped
        if (layer != null && layer.getChildCount() > 1 && MainActivity.I != null) MainActivity.I.popFromRail(layer.getChildAt(layer.getChildCount() - 1));
        // close by themselves after the set time (Settings → Rail Bar); a touch restarts it
        U.ui.removeCallbacks(autoClose);
        int secs = Store.num("card_secs", 15);
        if (secs > 0) U.ui.postDelayed(autoClose, secs * 1000L);
        cardPage = p;
    }

    private static Page cardPage;
    private static final Runnable autoClose = () -> {
        if (!stack.isEmpty() && stack.get(stack.size() - 1) == cardPage) close();
    };

    /** True when what's open sits in the tile area or is a rail pop-up (these minimise when idle). */
    static boolean minimisable() {
        if (!open()) return false;
        return !full || (cardPage != null && top() == cardPage);
    }

    /** Restart a pop-up's timer (called on touch). */
    static void touched() {
        if (cardPage == null || stack.isEmpty() || stack.get(stack.size() - 1) != cardPage) return;
        U.ui.removeCallbacks(autoClose);
        int secs = Store.num("card_secs", 15);
        if (secs > 0) U.ui.postDelayed(autoClose, secs * 1000L);
    }

    static FrameLayout.LayoutParams centered(float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? (int) w : U.u(w), h < 0 ? (int) h : U.u(h), Gravity.CENTER);
        return lp;
    }

    /** Anchored to the right rail at a given top. */
    static FrameLayout.LayoutParams anchored(float w, float h, float right, float top) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(U.u(w), h < 0 ? (int) h : U.u(h), Gravity.TOP | Gravity.END);
        lp.rightMargin = U.u(right);
        lp.topMargin = U.u(top);
        return lp;
    }

    // ------------------------------------------------------------------ standard panel

    /** A titled panel. body is added below the header; scrolls if taller than the panel. */
    static LinearLayout panel(String title, View body, float w, float h) {
        LinearLayout p = U.col(ctx);
        p.setBackground(U.round(U.CARD, 16)); // same corners as the tiles it covers
        U.pad(p, 20, 14, 20, 14);
        p.setLayoutParams(centered(w, h));

        // in the tile area the title bar goes up into the ticker's place, so the page gets the whole tile area
        boolean up = !full && MainActivity.I != null && MainActivity.I.canHoldTitle();
        LinearLayout head = U.row(ctx);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        float ic = up ? 20 : 24;
        if (stack.size() > 1) {
            View back = U.icon(ctx, R.drawable.ic_back, U.TEXT);
            back.setOnClickListener(v -> back());
            head.addView(back, U.lpu(ic, ic));
            head.addView(U.gap(ctx, up ? 10 : 12, 1));
        }
        TextView t = U.line(ctx, title, up ? 15 : 20, U.TEXT, true);
        head.addView(t, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        View x = U.icon(ctx, R.drawable.ic_x, U.SUB);
        x.setOnClickListener(v -> close());
        head.addView(x, U.lpu(ic, ic));
        if (up) {
            head.setBackground(U.round(U.CARD, 12));
            U.pad(head, 10, 0, 10, 0);
            U.pad(p, 20, 12, 20, 12);
            MainActivity.I.tickerTitle(head);
        } else p.addView(head, U.lp(U.MATCH, U.WRAP));

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.MATCH, 0, 1);
        lp.topMargin = up ? 0 : U.u(14);
        p.addView(sv, lp);
        return p;
    }

    static LinearLayout panel(String title, View body) {
        return panel(title, body, 640, 440);
    }

    /** A label + content row used throughout settings. */
    static LinearLayout row(String label, View... content) {
        LinearLayout r = U.row(ctx);
        TextView l = U.text(ctx, label, 14, U.SUB, false);
        r.addView(l, U.lpu(120, U.WRAP));
        LinearLayout c = U.row(ctx);
        for (View v : content) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
            lp.rightMargin = U.u(10);
            c.addView(v, lp);
        }
        r.addView(c, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        U.pad(r, 0, 9, 0, 9);
        return r;
    }

    /** Tappable list row: title, value, chevron. */
    static LinearLayout item(String title, String value, Runnable click) {
        LinearLayout r = U.row(ctx);
        r.setMinimumHeight(U.u(52));
        r.addView(U.line(ctx, title, 15, U.TEXT, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
        if (value != null) r.addView(U.line(ctx, value, 13, U.SUB, false));
        if (click != null) {
            r.addView(U.text(ctx, "  ›", 15, U.SUB, false));
            r.setOnClickListener(v -> click.run());
        }
        return r;
    }

    /** Title + subtitle + switch. */
    static LinearLayout switchRow(int icon, int iconTint, String title, String sub, boolean on, U.Cb<Boolean> cb) {
        LinearLayout r = U.row(ctx);
        U.pad(r, 0, 12, 0, 12);
        r.setMinimumHeight(U.u(56));
        if (icon != 0) {
            r.addView(U.icon(ctx, icon, iconTint), U.lpu(26, 26));
            r.addView(U.gap(ctx, 14, 1));
        }
        LinearLayout t = U.col(ctx);
        t.addView(U.text(ctx, title, 15, U.TEXT, false));
        if (sub != null) {
            TextView s = U.text(ctx, sub, 12, U.SUB, false);
            s.setPadding(0, U.u(3), 0, 0);
            t.addView(s);
        }
        r.addView(t, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        r.addView(new U.Toggle(ctx, on, cb));
        return r;
    }

    static View section(String s) {
        TextView t = U.text(ctx, s.toUpperCase(), 11, U.SUB, false);
        t.setLetterSpacing(0.05f);
        U.pad(t, 0, 14, 0, 4);
        return t;
    }

    static Context ctx() {
        return ctx;
    }
}
