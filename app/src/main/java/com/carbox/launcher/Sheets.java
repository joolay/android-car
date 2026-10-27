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
        scrimColor = 0x99000000;
        full = false;
        render();
    }

    /** Open a page on top of the current one (back arrow returns). */
    static void push(Page p) {
        stack.add(p);
        render();
    }

    static void back() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
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
        if (tileLayer != null) tileLayer.removeAllViews();
        if (fullLayer != null) fullLayer.removeAllViews();
        full = false;
        layer = tileLayer;
        for (Runnable r : new ArrayList<>(onClose)) r.run();
        onClose.clear();
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
        stack.clear();
        stack.addAll(pages);
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
        scrim.setOnClickListener(v -> close());
        layer.addView(scrim, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        View page = stack.get(stack.size() - 1).build();
        page.setClickable(true); // swallow taps so they don't close the sheet
        page.setOnTouchListener((v, e) -> {
            touched();
            return false;
        });
        if (!full) page.setLayoutParams(new FrameLayout.LayoutParams(U.MATCH, U.MATCH)); // fill the tile area exactly
        else if (page.getLayoutParams() == null) page.setLayoutParams(centered(640, 440));
        layer.addView(page);
    }

    /** Lighter scrim for small anchored cards. */
    static void showCard(Page p) {
        stack.clear();
        stack.add(p);
        scrimColor = 0x59000000;
        full = true; // rail pop-ups sit next to the rail, over the whole screen, as before
        render();
        // close by themselves after the set time (Settings → Right bar icons); a touch restarts it
        U.ui.removeCallbacks(autoClose);
        int secs = Store.num("card_secs", 8);
        if (secs > 0) U.ui.postDelayed(autoClose, secs * 1000L);
        cardPage = p;
    }

    private static Page cardPage;
    private static final Runnable autoClose = () -> {
        if (!stack.isEmpty() && stack.get(stack.size() - 1) == cardPage) close();
    };

    /** Restart a pop-up's timer (called on touch). */
    static void touched() {
        if (cardPage == null || stack.isEmpty() || stack.get(stack.size() - 1) != cardPage) return;
        U.ui.removeCallbacks(autoClose);
        int secs = Store.num("card_secs", 8);
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

        LinearLayout head = U.row(ctx);
        if (stack.size() > 1) {
            View back = U.icon(ctx, R.drawable.ic_back, U.TEXT);
            back.setOnClickListener(v -> back());
            head.addView(back, U.lpu(24, 24));
            head.addView(U.gap(ctx, 12, 1));
        }
        TextView t = U.line(ctx, title, 20, U.TEXT, true);
        head.addView(t, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        View x = U.icon(ctx, R.drawable.ic_x, U.SUB);
        x.setOnClickListener(v -> close());
        head.addView(x, U.lpu(24, 24));
        p.addView(head, U.lp(U.MATCH, U.WRAP));

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.MATCH, 0, 1);
        lp.topMargin = U.u(14);
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
