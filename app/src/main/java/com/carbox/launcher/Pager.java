package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Cells laid out as pages of cols × rows that slide sideways and snap, with page dots underneath
 * (All apps, folders).
 */
final class Pager extends LinearLayout {
    private final HorizontalScrollView hs;
    private final LinearLayout strip, dots;
    private final List<View> pages = new ArrayList<>();
    private int page, w;
    private boolean flung;

    Pager(Context c, List<View> cells, int cols, int rows, float gap) {
        super(c);
        setOrientation(VERTICAL);
        int per = Math.max(1, cols * rows);
        hs = new HorizontalScrollView(c) {
            @Override
            public void fling(int velocityX) { // a flick goes one page, never further
                flung = true;
                go(page + (velocityX > 0 ? 1 : -1));
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                boolean r = super.onTouchEvent(e);
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) post(() -> {
                    if (!flung) go(w > 0 ? Math.round(getScrollX() / (float) w) : page); // a slow drag: the nearest page
                    flung = false;
                });
                return r;
            }

            @Override
            protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                if (w > 0) dots(Math.round(l / (float) w));
            }
        };
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(OVER_SCROLL_NEVER);
        strip = U.row(c);
        strip.setGravity(Gravity.TOP);
        for (int i = 0; i < cells.size(); i += per) {
            List<View> sub = new ArrayList<>(cells.subList(i, Math.min(cells.size(), i + per)));
            LinearLayout pg = AppPanels.grid(c, sub, cols, gap);
            pages.add(pg);
            strip.addView(pg, new LayoutParams(0, LayoutParams.WRAP_CONTENT));
        }
        hs.addView(strip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(hs, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        dots = U.row(c);
        dots.setGravity(Gravity.CENTER);
        if (pages.size() > 1) {
            for (int i = 0; i < pages.size(); i++) {
                View d = new View(c);
                LayoutParams dl = U.lpu(7, 7);
                dl.setMargins(U.u(4), 0, U.u(4), 0);
                dots.addView(d, dl);
            }
            LayoutParams dl = U.lp(U.MATCH, U.u(16));
            dl.topMargin = U.u(4);
            addView(dots, dl);
        }
        dots(0);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        if (nw == w) return;
        w = nw;
        post(() -> { // every page exactly as wide as the pager
            for (View p : pages) p.getLayoutParams().width = w;
            strip.requestLayout();
            hs.post(() -> hs.scrollTo(page * w, 0));
        });
    }

    /** Next (1) or previous (-1) page, if there is one. */
    void turn(int dir) {
        go(page + dir);
    }

    private void go(int p) {
        page = Math.max(0, Math.min(pages.size() - 1, p));
        if (U.ms(1) == 0) hs.scrollTo(page * w, 0);
        else hs.smoothScrollTo(page * w, 0);
        dots(page);
    }

    private void dots(int at) {
        for (int i = 0; i < dots.getChildCount(); i++)
            dots.getChildAt(i).setBackground(U.oval(i == at ? U.ACCENT : U.LINE));
    }
}
