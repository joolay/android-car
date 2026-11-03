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
final class Panels {
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

    private Panels() {
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
    private static boolean slide; // the next one slides down out of the ticker instead
    private static View slideView; // the page that slides back up when closed

    /** Open in the tile area sliding down out of the news ticker (and back up into it when closed). */
    static void showSlide(Page p) {
        slide = true;
        show(p);
    }

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
        InAppKeyboard.hide();
        stack.clear();
        modes.clear();
        final View sv = slideView;
        slideView = null;
        if (sv != null && tileLayer != null && sv.getParent() == tileLayer && Ui.ms(1) > 0 && sv.getHeight() > 0) {
            final FrameLayout tl = tileLayer; // slides back up into the ticker, then goes
            for (int i = tl.getChildCount() - 1; i >= 0; i--) if (tl.getChildAt(i) != sv) tl.removeViewAt(i);
            sv.animate().translationY(-sv.getHeight()).setDuration(Ui.ms(260)).setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                    .withEndAction(() -> {
                        if (sv.getParent() == tl) tl.removeView(sv);
                    }).start();
        } else if (tileLayer != null) tileLayer.removeAllViews();
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
        layer.addView(scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (MainActivity.I != null) MainActivity.I.tickerTitle(null); // the page sets its title there again if it has one
        View page = stack.get(stack.size() - 1).build();
        Trace.event("Opened a page (" + (full ? "full screen" : "tile area") + ", " + stack.size() + " deep)");
        page.setClickable(true); // swallow taps so they don't close the sheet
        page.setOnTouchListener((v, e) -> {
            touched();
            return false;
        });
        if (!full) page.setLayoutParams(new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH)); // fill the tile area exactly
        else if (page.getLayoutParams() == null) page.setLayoutParams(centered(640, 440));
        layer.addView(page);
        slideView = null;
        if (slide && !full) { // down out of the ticker, kept inside the tile area
            slideView = page;
            page.setAlpha(0f);
            page.post(() -> {
                page.setTranslationY(-page.getHeight());
                page.setAlpha(1f);
                page.animate().translationY(0).setDuration(Ui.ms(320)).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            });
        } else if (grow && !full && MainActivity.I != null) MainActivity.I.growIn(page, null);
        if (!full && MainActivity.I != null) MainActivity.I.mapAside(true); // the big map steps aside for the panel
        grow = false;
        slide = false;
        if (cardOverTiles && cardPage != null && top() == cardPage) fitTiles(); // a refreshed card keeps its place
    }

    private static boolean cardOverTiles;

    /** The card on top covers exactly the tile area. */
    private static void fitTiles() {
        if (!cardOverTiles || layer == null || layer.getChildCount() < 2 || MainActivity.I == null) return;
        View card = layer.getChildAt(layer.getChildCount() - 1);
        View ta = MainActivity.I.tileArea;
        int[] a = new int[2], o = new int[2];
        ta.getLocationOnScreen(a);
        layer.getLocationOnScreen(o);
        if (ta.getWidth() == 0) return;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ta.getWidth(), ta.getHeight(), Gravity.TOP | Gravity.START);
        lp.leftMargin = a[0] - o[0];
        lp.topMargin = a[1] - o[1];
        card.setLayoutParams(lp);
    }

    /** Lighter scrim for small anchored cards. */
    static void showCard(Page p) {
        showCard(p, false);
    }

    /** overTiles: the card covers exactly the tile area (on top of the grid or big map), nothing moves. */
    static void showCard(Page p, boolean overTiles) {
        stack.clear();
        stack.add(p);
        modes.clear();
        modes.add(true);
        scrimColor = 0x59000000;
        full = true; // rail pop-ups sit next to the rail, over the whole screen, as before
        render();
        cardOverTiles = overTiles;
        fitTiles();
        // pop out of the Rail Bar icon that was tapped
        if (layer != null && layer.getChildCount() > 1 && MainActivity.I != null) MainActivity.I.popFromRail(layer.getChildAt(layer.getChildCount() - 1));
        // close by themselves after the set time (Settings → Rail Bar); a touch restarts it
        Ui.ui.removeCallbacks(autoClose);
        int secs = Prefs.num("card_secs", 15);
        if (secs > 0) Ui.ui.postDelayed(autoClose, secs * 1000L);
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
        Ui.ui.removeCallbacks(autoClose);
        int secs = Prefs.num("card_secs", 15);
        if (secs > 0) Ui.ui.postDelayed(autoClose, secs * 1000L);
    }

    static FrameLayout.LayoutParams centered(float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? (int) w : Ui.u(w), h < 0 ? (int) h : Ui.u(h), Gravity.CENTER);
        return lp;
    }

    /** Anchored to the right rail at a given top. */
    static FrameLayout.LayoutParams anchored(float w, float h, float right, float top) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.u(w), h < 0 ? (int) h : Ui.u(h), Gravity.TOP | Gravity.END);
        lp.rightMargin = Ui.u(right);
        lp.topMargin = Ui.u(top);
        return lp;
    }

    // ------------------------------------------------------------------ standard panel

    /** A titled panel. body is added below the header; scrolls if taller than the panel. */
    static LinearLayout panel(String title, View body, float w, float h) {
        return panel(null, title, body, w, h, null);
    }

    /**
     * A panel whose title can be a view of its own (e.g. an editable name) and with an icon button in
     * the title bar beside ✕ (`action`, may be null).
     */
    static LinearLayout panel(View titleView, String title, View body, float w, float h, View action) {
        LinearLayout p = Ui.col(ctx);
        p.setBackground(Themes.card(Ui.CARD, 16)); // same corners as the tiles it covers; glass in the Glass theme
        Ui.pad(p, 20, 14, 20, 14);
        p.setLayoutParams(centered(w, h));

        // in the tile area the title bar goes up into the ticker's place, so the page gets the whole tile area
        boolean up = (!full || cardOverTiles) && MainActivity.I != null && MainActivity.I.canHoldTitle();
        LinearLayout head = Ui.row(ctx);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        float ic = up ? 20 : 24;
        if (stack.size() > 1) {
            View back = Ui.icon(ctx, R.drawable.ic_back, Ui.TEXT);
            back.setOnClickListener(v -> back());
            head.addView(back, Ui.lpu(ic, ic));
            head.addView(Ui.gap(ctx, up ? 10 : 12, 1));
        }
        View t = titleView != null ? titleView : Ui.line(ctx, title, up ? 15 : 20, Ui.TEXT, true);
        head.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        if (action != null) {
            head.addView(action, Ui.lpu(ic + 8, ic + 8));
            head.addView(Ui.gap(ctx, 12, 1));
        }
        View x = Ui.icon(ctx, R.drawable.ic_x, Ui.SUB);
        x.setOnClickListener(v -> close());
        head.addView(x, Ui.lpu(ic + 4, ic + 4)); // close: a touch bigger
        if (up) {
            Ui.pad(p, 20, 12, 20, 12);
            MainActivity.I.titleToTicker(head);
        } else p.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.MATCH, 0, 1);
        lp.topMargin = up ? 0 : Ui.u(14);
        p.addView(sv, lp);
        return p;
    }

    static LinearLayout panel(String title, View body) {
        return panel(title, body, 640, 440);
    }

    /** A label + content row used throughout settings. */
    static LinearLayout row(String label, View... content) {
        LinearLayout r = Ui.row(ctx);
        TextView l = Ui.text(ctx, label, 14, Ui.SUB, false);
        r.addView(l, Ui.lpu(120, Ui.WRAP));
        LinearLayout c = Ui.row(ctx);
        for (View v : content) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.rightMargin = Ui.u(10);
            c.addView(v, lp);
        }
        r.addView(c, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        Ui.pad(r, 0, 9, 0, 9);
        return r;
    }

    /** Tappable list row: title, value, chevron. */
    static LinearLayout item(String title, String value, Runnable click) {
        LinearLayout r = Ui.row(ctx);
        r.setMinimumHeight(Ui.u(52));
        r.addView(Ui.line(ctx, title, 15, Ui.TEXT, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        if (value != null) r.addView(Ui.line(ctx, value, 13, Ui.SUB, false));
        if (click != null) {
            r.addView(Ui.text(ctx, "  ›", 15, Ui.SUB, false));
            r.setOnClickListener(v -> click.run());
        }
        return r;
    }

    /** Title + subtitle + switch. */
    static LinearLayout switchRow(int icon, int iconTint, String title, String sub, boolean on, Ui.Cb<Boolean> cb) {
        LinearLayout r = Ui.row(ctx);
        Ui.pad(r, 0, 12, 0, 12);
        r.setMinimumHeight(Ui.u(56));
        if (icon != 0) {
            r.addView(Ui.icon(ctx, icon, iconTint), Ui.lpu(26, 26));
            r.addView(Ui.gap(ctx, 14, 1));
        }
        LinearLayout t = Ui.col(ctx);
        t.addView(Ui.text(ctx, title, 15, Ui.TEXT, false));
        if (sub != null) {
            TextView s = Ui.text(ctx, sub, 12, Ui.SUB, false);
            s.setPadding(0, Ui.u(3), 0, 0);
            t.addView(s);
        }
        r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        r.addView(new Ui.Toggle(ctx, on, cb));
        return r;
    }

    static View section(String s) {
        TextView t = Ui.text(ctx, s.toUpperCase(), 11, Ui.SUB, false);
        t.setLetterSpacing(0.05f);
        Ui.pad(t, 0, 14, 0, 4);
        return t;
    }

    static Context ctx() {
        return ctx;
    }
}
