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
        strip = Ui.row(c);
        strip.setGravity(Gravity.TOP);
        hs.addView(strip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        dots = Ui.row(c);
        dots.setGravity(Gravity.CENTER);
        this.cells = cells;
        this.cols = cols;
        this.gap = gap;
        auto = rows <= 0;
        // auto (rows 0): fill the height it's given, the dots always at the bottom
        addView(hs, auto ? new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1) : new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams dl = Ui.lp(Ui.MATCH, Ui.u(16));
        dl.topMargin = Ui.u(4);
        addView(dots, dl);
        if (!auto) build(rows);
    }

    private final List<View> cells;
    private final int cols;
    private final float gap;
    private final boolean auto;
    private int rowsBuilt;

    /** Lay the cells out as pages of cols × rows, with a dot for each page. */
    private void build(int rows) {
        rowsBuilt = rows;
        int per = Math.max(1, cols * rows);
        pages.clear();
        strip.removeAllViews();
        for (View v : cells) if (v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
        for (int i = 0; i < cells.size(); i += per) {
            List<View> sub = new ArrayList<>(cells.subList(i, Math.min(cells.size(), i + per)));
            LinearLayout pg = AppPanels.grid(getContext(), sub, cols, gap);
            pages.add(pg);
            strip.addView(pg, new LayoutParams(w > 0 ? w : 0, LayoutParams.WRAP_CONTENT));
        }
        dots.removeAllViews();
        if (pages.size() > 1) for (int i = 0; i < pages.size(); i++) {
            View d = new View(getContext());
            LayoutParams l = Ui.lpu(7, 7);
            l.setMargins(Ui.u(4), 0, Ui.u(4), 0);
            dots.addView(d, l);
        }
        dots.setVisibility(pages.size() > 1 ? VISIBLE : INVISIBLE);
        page = Math.min(page, Math.max(0, pages.size() - 1));
        dots(page);
    }

    /** How many rows fit: the height left above the dots, by one cell's height. */
    private int fitRows(int width, int height) {
        if (cells.isEmpty() || width <= 0) return 1;
        View c0 = cells.get(0);
        if (c0.getParent() != null) ((android.view.ViewGroup) c0.getParent()).removeView(c0);
        c0.measure(MeasureSpec.makeMeasureSpec(width / cols, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int ch = c0.getMeasuredHeight() + Ui.u(gap);
        return Math.max(1, (height - Ui.u(20)) / Math.max(1, ch));
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        if (auto && nh > 0 && (nw != w || nh != oh)) { // rows to fit this height
            int r = fitRows(nw, nh);
            w = nw;
            if (r != rowsBuilt) post(() -> build(r));
        }
        if (nw == w && !auto) return;
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
        if (Ui.ms(1) == 0) hs.scrollTo(page * w, 0);
        else hs.smoothScrollTo(page * w, 0);
        dots(page);
    }

    private void dots(int at) {
        for (int i = 0; i < dots.getChildCount(); i++)
            dots.getChildAt(i).setBackground(Ui.oval(i == at ? Ui.ACCENT : Ui.LINE));
    }
}
