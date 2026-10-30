package com.carbox.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.List;

/**
 * Hold an app in a folder and drag it onto another app's place to move it there. A hold without
 * moving still does what holding used to do (the app menu, and so on).
 */
final class Reorder {
    interface Moved {
        void move(int from, int to);
    }

    /** Turn the page while an app is held at the edge: true to carry on dragging, false when the drag is over. */
    interface Turn {
        boolean go(int dir);
    }

    private Reorder() {
    }

    /** Make cells.get(index) draggable among `cells`; `hold` runs for a hold that doesn't move. */
    static void attach(View cell, int index, List<View> cells, Moved moved, Runnable hold) {
        attach(cell, index, cells, moved, hold, null, null);
    }

    /**
     * As above; holding the dragged app at the left or right edge of `host` for a moment turns the
     * page (`turn`). With no host given, the pages it sits in (a {@link Pager}) are used, if any.
     */
    static void attach(View cell, int index, List<View> cells, Moved moved, Runnable hold, View host0, Turn turn0) {
        attach(cell, index, cells, moved, hold, host0, turn0, false);
    }

    /** instant: no hold needed, the app lifts as soon as the finger moves (edit mode). */
    static void attach(View cell, int index, List<View> cells, Moved moved, Runnable hold, View host0, Turn turn0, boolean instant) {
        final int slop = ViewConfiguration.get(cell.getContext()).getScaledTouchSlop() * 2;
        final float[] down = new float[2];
        final boolean[] dragging = {false}, moving = {false};
        final ImageView[] ghost = {null};
        final int[] target = {-1};
        final int[] edge = {0};
        final Runnable[] turner = {null};
        final View[] host = {host0};
        final Turn[] turn = {turn0};
        cell.setOnLongClickListener(v -> {
            if (dragging[0]) return true; // already lifted by a move (instant)
            if (host[0] == null) { // inside pages: they turn
                for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent())
                    if (p instanceof Pager) {
                        Pager pg = (Pager) p;
                        host[0] = pg;
                        turn[0] = dir -> {
                            pg.turn(dir);
                            return true;
                        };
                        break;
                    }
            }
            dragging[0] = true;
            moving[0] = false;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true); // no scrolling while dragging
            ghost[0] = lift(v);
            return true;
        });
        cell.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    moving[0] = false;
                    if (instant && v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (!dragging[0] && instant && Math.hypot(dx, dy) > slop / 2f) { // edit mode: lift on the first move
                        v.cancelLongPress();
                        dragging[0] = true;
                        ghost[0] = lift(v);
                    }
                    if (!dragging[0]) return false;
                    if (!moving[0] && Math.hypot(dx, dy) > slop) moving[0] = true;
                    if (ghost[0] != null) {
                        ghost[0].setTranslationX(dx);
                        ghost[0].setTranslationY(dy);
                    }
                    // held at the left or right edge: the page turns after a moment (and again while held there)
                    int dir = 0;
                    Rect hr = new Rect();
                    if (host[0] != null && turn[0] != null && host[0].getGlobalVisibleRect(hr)) {
                        float band = Math.max(U.u(28), hr.width() * 0.1f);
                        if (e.getRawX() < hr.left + band) dir = -1;
                        else if (e.getRawX() > hr.right - band) dir = 1;
                    }
                    if (dir != edge[0]) {
                        edge[0] = dir;
                        if (turner[0] != null) v.removeCallbacks(turner[0]);
                        turner[0] = null;
                        if (dir != 0) {
                            final int d = dir;
                            turner[0] = new Runnable() {
                                @Override
                                public void run() {
                                    if (!dragging[0] || edge[0] != d) return;
                                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                                    if (turn[0].go(d)) v.postDelayed(this, 900);
                                    else { // the turn finished the move (e.g. the app went to the next page)
                                        dragging[0] = false;
                                        drop(ghost[0], v);
                                        ghost[0] = null;
                                        if (target[0] >= 0 && target[0] < cells.size()) cells.get(target[0]).setAlpha(1f);
                                        target[0] = -1;
                                        edge[0] = 0;
                                    }
                                }
                            };
                            v.postDelayed(turner[0], 650);
                        }
                    }
                    int t = at(cells, e.getRawX(), e.getRawY());
                    if (t != target[0]) {
                        if (target[0] >= 0 && target[0] != index) cells.get(target[0]).setAlpha(1f);
                        target[0] = t;
                        if (t >= 0 && t != index) cells.get(t).setAlpha(0.45f); // where it will go
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (turner[0] != null) v.removeCallbacks(turner[0]);
                    turner[0] = null;
                    edge[0] = 0;
                    if (!dragging[0]) return moving[0];
                    dragging[0] = false;
                    drop(ghost[0], v);
                    ghost[0] = null;
                    if (target[0] >= 0) cells.get(target[0]).setAlpha(1f);
                    int to = target[0];
                    target[0] = -1;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        if (moving[0] && to >= 0 && to != index) moved.move(index, to);
                        else if (!moving[0] && hold != null) hold.run();
                    }
                    return true;
            }
            return false;
        });
    }

    /** Which of the cells is under the finger (-1 for none). */
    private static int at(List<View> cells, float x, float y) {
        Rect r = new Rect();
        for (int i = 0; i < cells.size(); i++) {
            View c = cells.get(i);
            if (c.isShown() && c.getGlobalVisibleRect(r) && r.contains((int) x, (int) y)) return i;
        }
        return -1;
    }

    /** A picture of the cell, a little bigger, floating over everything (the cell itself fades). */
    private static ImageView lift(View v) {
        if (!(v.getContext() instanceof Activity) || v.getWidth() == 0) return null;
        FrameLayout root = ((Activity) v.getContext()).findViewById(android.R.id.content);
        if (root == null) return null;
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        ImageView g = new ImageView(v.getContext());
        g.setImageBitmap(b);
        int[] a = new int[2], o = new int[2];
        v.getLocationOnScreen(a);
        root.getLocationOnScreen(o);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(v.getWidth(), v.getHeight());
        lp.leftMargin = a[0] - o[0];
        lp.topMargin = a[1] - o[1];
        root.addView(g, lp);
        g.setElevation(U.uf(12));
        g.animate().scaleX(1.12f).scaleY(1.12f).setDuration(U.ms(120)).start();
        v.setAlpha(0.25f);
        return g;
    }

    private static void drop(ImageView g, View v) {
        v.setAlpha(1f);
        if (g == null) return;
        if (g.getParent() != null) ((FrameLayout) g.getParent()).removeView(g);
    }
}
