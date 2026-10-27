package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid of tiles, 2 rows high. Four columns fit on screen; with more than 8 tiles the extra
 * columns sit to the right and the grid swipes sideways, snapping to whole columns.
 * Each tile is a folder of apps or a widget. Long-press a tile to enter edit mode, then drag to
 * reorder (drag to the edge to scroll); tap a tile's ⋯ for Replace with widget etc.
 */
final class Tiles {
    static final int COLS = 4, ROWS = 2, COUNT = COLS * ROWS; // on screen at once
    static final String N = "tiles_n";

    /** Tiles switched on: 8 to 16, always a whole number of columns. */
    static int count() {
        int n = Store.num(N, COUNT);
        return Math.max(COUNT, Math.min(16, n - n % ROWS));
    }

    static void setCount(int n) {
        Store.put(N, n);
        if (MainActivity.I != null) MainActivity.I.tiles.refresh();
    }

    static int columns() {
        return count() / ROWS;
    }

    /** Where tile i sits: the first 8 fill the 4 visible columns row by row (as before); extra tiles fill extra columns top to bottom. */
    static int colOf(int i) {
        return i < COUNT ? i % COLS : COLS + (i - COUNT) / ROWS;
    }

    static int rowOf(int i) {
        return i < COUNT ? i / COLS : (i - COUNT) % ROWS;
    }

    static int indexOf(int col, int row) {
        return col < COLS ? row * COLS + col : COUNT + (col - COLS) * ROWS + row;
    }
    private final MainActivity a;
    final Grid view;
    private final List<Widgets.W> widgets = new ArrayList<>();
    private boolean editing, running;

    Tiles(MainActivity a) {
        this.a = a;
        view = new Grid(a);
        Apps.listen(this::refresh);
        refresh();
    }

    // ------------------------------------------------------------------ specs

    static JSONArray specs() {
        JSONArray s = Store.arr(Store.TILES);
        if (s.length() == 0) s = defaults();
        while (s.length() < COUNT) s.put(folder("Apps", new JSONArray()));
        while (s.length() < count()) s.put(widget("none")); // new tiles start empty: tap to choose a widget
        return s;
    }

    static void save(JSONArray s) {
        Store.put(Store.TILES, s);
    }

    static JSONObject widget(String id) {
        JSONObject o = new JSONObject();
        try {
            o.put("t", "w");
            o.put("id", id);
            o.put("cfg", new JSONObject());
        } catch (Exception ignored) {
        }
        return o;
    }

    static JSONObject folder(String name, JSONArray apps) {
        JSONObject o = new JSONObject();
        try {
            o.put("t", "f");
            o.put("name", name);
            o.put("apps", apps);
        } catch (Exception ignored) {
        }
        return o;
    }

    private static JSONArray defaults() {
        JSONArray s = new JSONArray();
        s.put(widget("carconnect"));
        s.put(widget("nextprayer"));
        s.put(widget("ha"));
        JSONArray music = new JSONArray();
        for (String p : Actions.MUSIC) {
            Apps.App ap = Apps.byPkg(p);
            if (ap != null) music.put(ap.key);
        }
        s.put(folder("Music", music));
        s.put(widget("speed"));
        s.put(widget("qibla"));
        s.put(widget("goto"));
        JSONArray apps = new JSONArray();
        int n = 0;
        for (Apps.App ap : Apps.visible()) {
            if (n++ >= 8) break;
            apps.put(ap.key);
        }
        s.put(folder("Apps", apps));
        if (!Apps.all.isEmpty()) save(s); // wait until the app list has loaded before remembering defaults
        return s;
    }

    static void setSpec(int i, JSONObject spec) {
        JSONArray s = specs();
        try {
            s.put(i, spec);
        } catch (Exception ignored) {
        }
        save(s);
        if (MainActivity.I != null) MainActivity.I.tiles.refresh();
    }

    // ------------------------------------------------------------------ build

    void refresh() {
        view.removeAllViews();
        widgets.clear();
        JSONArray s = specs();
        for (int i = 0, n = count(); i < n; i++) {
            JSONObject spec = s.optJSONObject(i);
            if (spec == null) spec = folder("Apps", new JSONArray());
            view.addView(tile(i, spec));
        }
        if (running) for (Widgets.W w : widgets) w.tick();
        view.clampScroll();
    }

    private View tile(int index, JSONObject spec) {
        FrameLayout f = new FrameLayout(a);
        View content;
        if ("w".equals(spec.optString("t"))) {
            Widgets.W w = Widgets.make(a, index, spec);
            widgets.add(w);
            content = w.v;
        } else content = folderView(index, spec);
        f.addView(content, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        MapPanel.clip(f, 16);

        if (editing) {
            View ring = new View(a);
            ring.setBackground(U.outline(0, U.ACCENT, 2, 16));
            f.addView(ring, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            View block = new View(a); // swallow taps on the content while editing
            block.setOnClickListener(v -> AppPanels.tileMenu(index));
            f.addView(block, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            TextView more = U.text(a, "⋯", 16, U.TEXT, true);
            more.setGravity(Gravity.CENTER);
            more.setBackground(U.oval(U.ACCENT));
            more.setOnClickListener(v -> AppPanels.tileMenu(index));
            FrameLayout.LayoutParams ml = U.flpu(26, 26, Gravity.TOP | Gravity.END);
            ml.setMargins(0, U.u(6), U.u(6), 0);
            f.addView(more, ml);
        } else {
            f.setOnLongClickListener(v -> {
                setEditing(true);
                return true;
            });
        }
        return f;
    }

    private View folderView(int index, JSONObject spec) {
        LinearLayout l = U.col(a);
        l.setBackground(U.round(U.CARD, 16));
        U.pad(l, 14, 14, 14, 14);
        TextView name = U.line(a, spec.optString("name", "Folder"), 15, U.TEXT, true);
        l.addView(name);
        JSONArray apps = spec.optJSONArray("apps");
        List<Apps.App> list = new ArrayList<>();
        if (apps != null) for (int i = 0; i < apps.length(); i++) {
            Apps.App ap = Apps.get(apps.optString(i));
            if (ap != null) list.add(ap);
        }
        TextView count = U.text(a, list.size() + (list.size() == 1 ? " app" : " apps"), 11, U.SUB, false);
        LinearLayout.LayoutParams cl = U.lp(U.WRAP, U.WRAP);
        cl.topMargin = U.u(3);
        l.addView(count, cl);
        l.addView(new View(a), U.weight(1, false));
        // 2×2 icons
        for (int r = 0; r < 2; r++) {
            LinearLayout row = U.row(a);
            for (int k = 0; k < 2; k++) {
                int i = r * 2 + k;
                FrameLayout cell = new FrameLayout(a);
                if (i < list.size()) {
                    final Apps.App ap = list.get(i);
                    ImageView iv = new ImageView(a);
                    iv.setImageDrawable(ap.icon);
                    cell.addView(iv, U.flpu(38, 38, Gravity.CENTER));
                    cell.setOnClickListener(v -> Apps.launch(a, ap));
                    cell.setOnLongClickListener(v -> {
                        setEditing(true);
                        return true;
                    });
                }
                if (i == 3 && list.size() > 4) {
                    cell.removeAllViews();
                    TextView more = U.text(a, "+" + (list.size() - 3), 13, U.TEXT, true);
                    more.setGravity(Gravity.CENTER);
                    more.setBackground(U.round(U.CARD2, 10));
                    cell.addView(more, U.flpu(38, 38, Gravity.CENTER));
                    cell.setOnClickListener(v -> AppPanels.folder(index));
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, U.u(44), 1));
            }
            LinearLayout.LayoutParams rl = U.lp(U.MATCH, U.WRAP);
            if (r == 1) rl.topMargin = U.u(4);
            l.addView(row, rl);
        }
        l.setOnClickListener(v -> AppPanels.folder(index));
        if (list.isEmpty()) {
            l.removeViewAt(l.getChildCount() - 1);
            l.removeViewAt(l.getChildCount() - 1);
            TextView add = U.text(a, "Tap to add apps", 12, U.SUB, false);
            l.addView(add);
        }
        return l;
    }

    // ------------------------------------------------------------------ edit mode

    boolean editing() {
        return editing;
    }

    void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        a.editMode(on);
        refresh();
    }

    void start() {
        running = true;
        U.ui.post(tick);
    }

    void stop() {
        running = false;
        U.ui.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            for (Widgets.W w : widgets) {
                try {
                    w.tick();
                } catch (Exception ignored) {
                }
            }
            if (running) U.ui.postDelayed(this, 1000);
        }
    };

    /**
     * Lays tiles out 2 rows high with 8 du gaps, 4 columns per screen width, extra columns off to the
     * right. Swipes sideways (snapping to columns), draws a small position bar in the gap between the
     * rows, and handles dragging in edit mode.
     */
    final class Grid extends ViewGroup {
        private int dragIndex = -1;
        private float downX, downY, lastX;
        private boolean dragging, swiping;
        private int scrollStart;
        private final int slop;
        private android.view.VelocityTracker vt;
        private android.animation.ValueAnimator snap;
        private final android.graphics.Paint bar = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();

        Grid(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setClipChildren(false);
            setWillNotDraw(false);
        }

        private float gap() {
            return U.u(8);
        }

        private float cellW() {
            return (getWidth() - gap() * (COLS - 1)) / COLS;
        }

        private float cellH() {
            return (getHeight() - gap() * (ROWS - 1)) / (float) ROWS;
        }

        private float colStep() {
            return cellW() + gap();
        }

        private int maxScroll() {
            return Math.max(0, Math.round((columns() - COLS) * colStep()));
        }

        void clampScroll() {
            if (getWidth() == 0) return;
            int x = Math.max(0, Math.min(maxScroll(), getScrollX()));
            if (x != getScrollX()) scrollTo(x, 0);
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
            setMeasuredDimension(w, h);
            int cw = Math.round((w - gap() * (COLS - 1)) / COLS), ch = Math.round((h - gap() * (ROWS - 1)) / ROWS);
            for (int i = 0; i < getChildCount(); i++)
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int rr, int b) {
            float cw = cellW(), ch = cellH();
            for (int i = 0; i < getChildCount(); i++) {
                int x = Math.round(colOf(i) * (cw + gap())), y = Math.round(rowOf(i) * (ch + gap()));
                getChildAt(i).layout(x, y, x + Math.round(cw), y + Math.round(ch));
            }
            clampScroll();
        }

        /** Thin bar in the gap between the two rows showing which columns are on screen. */
        @Override
        public void onDrawForeground(android.graphics.Canvas cv) {
            super.onDrawForeground(cv);
            int cols = columns();
            if (cols <= COLS) return;
            float w = U.u(64), h = U.u(3), x0 = getScrollX() + (getWidth() - w) / 2f, y = cellH() + (gap() - h) / 2f;
            r.set(x0, y, x0 + w, y + h);
            bar.setColor(0x33FFFFFF);
            cv.drawRoundRect(r, h / 2, h / 2, bar);
            float part = w * COLS / cols, pos = maxScroll() == 0 ? 0 : getScrollX() / (float) maxScroll();
            r.left = x0 + (w - part) * pos;
            r.right = r.left + part;
            bar.setColor(0xCCFFFFFF);
            cv.drawRoundRect(r, h / 2, h / 2, bar);
        }

        private int indexAt(float x, float y) {
            int col = Math.max(0, Math.min(columns() - 1, (int) ((x + getScrollX()) / colStep())));
            int row = Math.max(0, Math.min(ROWS - 1, (int) (y / (cellH() + gap()))));
            return indexOf(col, row);
        }

        // ---- swiping between columns

        private void snapTo(int target) {
            if (snap != null) snap.cancel();
            target = Math.max(0, Math.min(maxScroll(), target));
            snap = android.animation.ValueAnimator.ofInt(getScrollX(), target);
            snap.setDuration(220);
            snap.setInterpolator(new android.view.animation.DecelerateInterpolator());
            snap.addUpdateListener(an -> {
                scrollTo((Integer) an.getAnimatedValue(), 0);
                invalidate();
            });
            snap.start();
        }

        /** Settle on a whole column, or jump a full screen of 4 columns on a quick flick. */
        private void settle(float velocity) {
            float step = colStep();
            int col = Math.round(getScrollX() / step);
            float fling = U.uf(500);
            if (velocity < -fling) col = (int) Math.ceil(scrollStart / step) + COLS;   // flicked left: next screen
            else if (velocity > fling) col = (int) Math.floor(scrollStart / step) - COLS; // flicked right: back
            snapTo(Math.round(col * step));
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = e.getX();
                    downY = e.getY();
                    dragIndex = indexAt(downX, downY);
                    dragging = false;
                    swiping = false;
                    scrollStart = getScrollX();
                    if (snap != null) snap.cancel();
                    if (vt != null) vt.recycle();
                    vt = android.view.VelocityTracker.obtain();
                    vt.addMovement(e);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(e);
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (editing) {
                        if (!dragging && Math.hypot(dx, dy) > slop) {
                            dragging = true;
                            View v = getChildAt(dragIndex);
                            if (v != null) {
                                // raise it above the others without changing child order (layout is by index)
                                v.setTranslationZ(U.uf(12));
                                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start();
                            }
                            return true;
                        }
                        return false;
                    }
                    if (columns() > COLS && Math.abs(dx) > slop * 1.5f && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        swiping = true;
                        lastX = e.getX();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true; // the tile under the finger gets a cancel, so no tap or long-press fires
                    }
                    return false;
            }
            return false;
        }

        private final Runnable edgeScroll = new Runnable() {
            @Override
            public void run() {
                if (!dragging) return;
                int dir = edgeDir;
                if (dir != 0) {
                    int before = getScrollX();
                    snapTo(Math.round((Math.round(before / colStep()) + dir) * colStep()));
                    U.ui.postDelayed(this, 650);
                } else edgePending = false;
            }
        };
        private int edgeDir;
        private boolean edgePending;

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (vt != null) vt.addMovement(e);
            // a swipe that started on the gap between tiles never passes through onInterceptTouchEvent
            if (!editing && !swiping && e.getActionMasked() == MotionEvent.ACTION_MOVE && columns() > COLS
                    && Math.abs(e.getX() - downX) > slop * 1.5f && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY) * 1.2f) {
                swiping = true;
                lastX = e.getX();
            }
            if (swiping) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE:
                        int x = Math.round(getScrollX() - (e.getX() - lastX));
                        // a little resistance past either end
                        if (x < 0 || x > maxScroll()) x = Math.round(getScrollX() - (e.getX() - lastX) / 3f);
                        scrollTo(x, 0);
                        lastX = e.getX();
                        invalidate();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        swiping = false;
                        float v = 0;
                        if (vt != null) {
                            vt.computeCurrentVelocity(1000);
                            v = vt.getXVelocity();
                        }
                        settle(v);
                        return true;
                }
                return true;
            }
            if (!editing || !dragging) return true;
            View v = getChildAt(dragIndex);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (v != null) {
                        v.setTranslationX(e.getX() + getScrollX() - downX - scrollStart);
                        v.setTranslationY(e.getY() - downY);
                    }
                    // hold a tile near the left or right edge to scroll to more columns
                    float edge = U.uf(36);
                    edgeDir = columns() <= COLS ? 0 : e.getX() > getWidth() - edge ? 1 : e.getX() < edge ? -1 : 0;
                    if (edgeDir != 0 && !edgePending) {
                        edgePending = true;
                        U.ui.postDelayed(edgeScroll, 450);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    edgeDir = 0;
                    edgePending = false;
                    U.ui.removeCallbacks(edgeScroll);
                    int to = indexAt(e.getX(), e.getY());
                    dragging = false;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && to != dragIndex) move(dragIndex, to);
                    dragIndex = -1;
                    refresh();
                    return true;
            }
            return true;
        }
    }

    /** Moves a tile and shifts the others along, like a phone home screen. */
    private void move(int from, int to) {
        JSONArray s = specs();
        List<JSONObject> l = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) l.add(s.optJSONObject(i));
        if (from < 0 || from >= l.size() || to < 0 || to >= l.size()) return;
        JSONObject x = l.remove(from);
        l.add(to, x);
        JSONArray out = new JSONArray();
        for (JSONObject o : l) out.put(o);
        save(out);
    }
}
