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
 * The tiles, 2 rows high, filled column by column. Up to 4 columns (8 tiles) show at once; any
 * number of tiles can be added and the grid slides sideways, revealing the next column as you go.
 * Each tile is a folder of apps or a widget. Long-press a tile to enter edit mode, then drag to
 * reorder (drag to the edge to scroll); tap a tile's ⋯ for Replace with widget etc.
 */
final class Tiles {
    static final int COLS = 4, ROWS = 2, COUNT = COLS * ROWS; // on screen at once

    /** How many tiles there are: any number, set by adding and removing tiles. */
    static int count() {
        return specs().length();
    }

    /** Column by column: tiles 0 and 1 are the first column, 2 and 3 the next, and so on. */
    static int colOf(int i) {
        return i / ROWS;
    }

    static int rowOf(int i) {
        return i % ROWS;
    }

    static int indexOf(int col, int row) {
        return col * ROWS + row;
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

    static void addTile() {
        JSONArray s = specs();
        s.put(widget("none"));
        save(s);
        if (MainActivity.I != null) {
            MainActivity.I.tiles.refresh();
            MainActivity.I.tiles.view.scrollToEnd();
        }
    }

    static void removeTile(int i) {
        JSONArray s = specs(), out = new JSONArray();
        for (int k = 0; k < s.length(); k++) if (k != i) out.put(s.opt(k));
        save(out);
        Store.put("tiles_empty", out.length() == 0);
        if (MainActivity.I != null) MainActivity.I.tiles.refresh();
    }

    static JSONArray specs() {
        JSONArray s = Store.arr(Store.TILES);
        if (s.length() == 0 && !Store.flag("tiles_empty", false)) s = defaults();
        if (!Store.flag("tiles_cm", false) && s.length() > 0) s = toColumns(s);
        return s;
    }

    /**
     * One-off: tiles used to be stored row by row (0-3 top row, 4-7 bottom). Re-order them column by
     * column so they stay where they were on screen, and drop extra tiles the old "Home tiles" count hid.
     */
    private static JSONArray toColumns(JSONArray s) {
        int keep = Math.min(s.length(), Math.max(COUNT, Store.num("tiles_n", COUNT)));
        JSONArray out = new JSONArray();
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++) {
                int old = r * COLS + c;
                if (old < Math.min(keep, COUNT)) out.put(s.opt(old));
            }
        for (int i = COUNT; i < keep; i++) out.put(s.opt(i)); // the extra columns were already column by column
        Store.put("tiles_cm", true);
        save(out);
        return out;
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
        s.put(widget(Faith.islam() ? "nextprayer" : "weather"));
        s.put(widget("ha"));
        JSONArray music = new JSONArray();
        for (String p : Actions.MUSIC) {
            Apps.App ap = Apps.byPkg(p);
            if (ap != null) music.put(ap.key);
        }
        s.put(folder("Music", music));
        s.put(widget("speed"));
        s.put(widget(Faith.islam() ? "qibla" : "trip"));
        s.put(widget("goto"));
        JSONArray apps = new JSONArray();
        int n = 0;
        for (Apps.App ap : Apps.visible()) {
            if (n++ >= 8) break;
            apps.put(ap.key);
        }
        s.put(folder("Apps", apps));
        // the list above is row by row; tiles are now stored column by column
        JSONArray cm = new JSONArray();
        for (int c = 0; c < COLS; c++) for (int r = 0; r < ROWS; r++) cm.put(s.opt(r * COLS + c));
        s = cm;
        Store.put("tiles_cm", true);
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
        for (int i = 0, n = s.length(); i < n; i++) {
            JSONObject spec = s.optJSONObject(i);
            if (spec == null) spec = widget("none");
            view.addView(tile(i, spec));
        }
        if (editing) view.addView(addTileView()); // "+" slot after the last tile
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
                TileEditor.open(); // full-screen tile editor
                return true;
            });
        }
        return f;
    }

    private View addTileView() {
        LinearLayout l = U.col(a);
        l.setGravity(Gravity.CENTER);
        l.setBackground(U.outline(0, U.ACCENT, 2, 16));
        TextView plus = U.text(a, "+", 30, U.ACCENT_SOFT, true);
        plus.setGravity(Gravity.CENTER);
        l.addView(plus);
        TextView t = U.text(a, "Add tile", 13, U.ACCENT_SOFT, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        l.setOnClickListener(v -> addTile());
        U.press(l);
        return l;
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
                        TileEditor.open(); // full-screen tile editor
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
     * Lays tiles out 2 rows high, column by column, 4 columns to a screen width. Slides sideways with
     * the finger and keeps gliding after a flick, then settles on a whole column. Columns sliding in
     * at either edge grow and fade in as they're revealed. A small bar between the rows shows where
     * you are. In edit mode tiles can be dragged, and holding one at the edge scrolls.
     */
    final class Grid extends ViewGroup {
        private int dragIndex = -1;
        private float downX, downY, lastX;
        private boolean dragging, swiping;
        private int scrollStart;
        private final int slop;
        private android.view.VelocityTracker vt;
        private final android.widget.OverScroller scroller;
        private final android.graphics.Paint bar = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();

        Grid(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            scroller = new android.widget.OverScroller(c, new android.view.animation.DecelerateInterpolator(1.6f));
            setClipChildren(false);
            setWillNotDraw(false);
            setOnLongClickListener(v -> { // long-press an empty spot (or an empty grid) to edit
                TileEditor.open(); // full-screen tile editor
                return true;
            });
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

        private int columns() {
            return (getChildCount() + ROWS - 1) / ROWS;
        }

        private int maxScroll() {
            return Math.max(0, Math.round((columns() - COLS) * colStep()));
        }

        void clampScroll() {
            if (getWidth() == 0) return;
            int x = Math.max(0, Math.min(maxScroll(), getScrollX()));
            if (x != getScrollX()) scrollTo(x, 0);
            reveal();
        }

        void scrollToEnd() {
            post(() -> glideTo(maxScroll()));
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

        @Override
        protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            reveal();
        }

        /** Tiles partly off either edge shrink a little and fade, so the next column eases into view. */
        private void reveal() {
            float w = getWidth(), sx = getScrollX();
            if (w == 0) return;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (i == dragIndex && dragging) continue;
                float left = v.getLeft() - sx, right = v.getRight() - sx, cw = v.getWidth();
                float out = 0; // how much of the tile is off screen, 0..1
                if (left < 0) out = Math.min(1, -left / cw);
                else if (right > w) out = Math.min(1, (right - w) / cw);
                float k = 1 - out;
                v.setAlpha(0.25f + 0.75f * k);
                float scale = 0.88f + 0.12f * k;
                v.setScaleX(scale);
                v.setScaleY(scale);
                v.setPivotX(left < 0 ? cw : 0);
                v.setPivotY(v.getHeight() / 2f);
            }
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
            r.left = x0 + (w - part) * Math.max(0, Math.min(1, pos));
            r.right = r.left + part;
            bar.setColor(0xCCFFFFFF);
            cv.drawRoundRect(r, h / 2, h / 2, bar);
        }

        private int indexAt(float x, float y) {
            int col = Math.max(0, (int) ((x + getScrollX()) / colStep()));
            int row = Math.max(0, Math.min(ROWS - 1, (int) (y / (cellH() + gap()))));
            return Math.min(getChildCount() - 1, indexOf(col, row));
        }

        // ---- sliding

        private int snap(float x) {
            return Math.max(0, Math.min(maxScroll(), Math.round(Math.round(x / colStep()) * colStep())));
        }

        private void glideTo(int x) {
            scroller.forceFinished(true);
            scroller.startScroll(getScrollX(), 0, x - getScrollX(), 0, 320);
            postInvalidateOnAnimation();
        }

        /** Keep gliding after a flick, then come to rest on a whole column. */
        private void fling(float velocity) {
            scroller.forceFinished(true);
            scroller.fling(getScrollX(), 0, Math.round(-velocity), 0, 0, maxScroll(), 0, 0);
            int end = snap(scroller.getFinalX());
            scroller.forceFinished(true);
            int dist = Math.abs(end - getScrollX());
            scroller.startScroll(getScrollX(), 0, end - getScrollX(), 0, Math.max(220, Math.min(700, (int) (dist / U.uf(1.2f)))));
            postInvalidateOnAnimation();
        }

        @Override
        public void computeScroll() {
            if (scroller.computeScrollOffset()) {
                scrollTo(scroller.getCurrX(), 0);
                postInvalidateOnAnimation();
            }
        }

        private boolean sideways(float dx, float dy) {
            return Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f;
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
                    boolean wasMoving = !scroller.isFinished();
                    scroller.forceFinished(true);
                    if (vt != null) vt.recycle();
                    vt = android.view.VelocityTracker.obtain();
                    vt.addMovement(e);
                    return wasMoving && !editing; // a touch while gliding just stops it
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(e);
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (editing) {
                        if (!dragging && Math.hypot(dx, dy) > slop && dragIndex < count()) {
                            dragging = true;
                            View v = getChildAt(dragIndex);
                            if (v != null) {
                                // raise it above the others without changing child order (layout is by index)
                                v.setTranslationZ(U.uf(12));
                                v.setAlpha(1f);
                                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start();
                            }
                            return true;
                        }
                        return false;
                    }
                    if (maxScroll() > 0 && sideways(dx, dy)) {
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
                if (!dragging || edgeDir == 0) {
                    edgePending = false;
                    return;
                }
                glideTo(snap(getScrollX() + edgeDir * colStep()));
                U.ui.postDelayed(this, 650);
            }
        };
        private int edgeDir;
        private boolean edgePending;

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (vt != null) vt.addMovement(e);
            if (!editing) {
                // a slide that started on a gap between tiles never passes through onInterceptTouchEvent
                if (!swiping && e.getActionMasked() == MotionEvent.ACTION_MOVE && maxScroll() > 0 && sideways(e.getX() - downX, e.getY() - downY)) {
                    swiping = true;
                    lastX = e.getX();
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.onTouchEvent(cancel); // no long-press once sliding
                    cancel.recycle();
                }
                if (!swiping) return super.onTouchEvent(e) || true;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE:
                        float d = e.getX() - lastX;
                        int x = getScrollX() - Math.round(d);
                        // stretchy past either end
                        if (x < 0 || x > maxScroll()) x = getScrollX() - Math.round(d / 3f);
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
                        fling(v);
                        return true;
                }
                return true;
            }
            if (!editing || !dragging) return super.onTouchEvent(e);
            View v = getChildAt(dragIndex);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (v != null) {
                        v.setTranslationX(e.getX() + getScrollX() - downX - scrollStart);
                        v.setTranslationY(e.getY() - downY);
                    }
                    // hold a tile near the left or right edge to bring in more columns
                    float edge = U.uf(36);
                    edgeDir = maxScroll() == 0 ? 0 : e.getX() > getWidth() - edge ? 1 : e.getX() < edge ? -1 : 0;
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
                    int to = Math.min(count() - 1, indexAt(e.getX(), e.getY()));
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
