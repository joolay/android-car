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
 * The 4×2 grid of tiles. Each tile is a folder of apps or a widget.
 * Long-press a tile to enter edit mode, then drag to reorder; tap a tile's ⋯ for Replace with widget etc.
 */
final class Tiles {
    static final int COLS = 4, ROWS = 2, COUNT = COLS * ROWS;
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
        for (int i = 0; i < COUNT; i++) {
            JSONObject spec = s.optJSONObject(i);
            if (spec == null) spec = folder("Apps", new JSONArray());
            view.addView(tile(i, spec));
        }
        if (running) for (Widgets.W w : widgets) w.tick();
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

    /** Lays tiles out in a fixed 4×2 grid with 8 du gaps; handles dragging in edit mode. */
    final class Grid extends ViewGroup {
        private int dragIndex = -1;
        private float downX, downY;
        private boolean dragging;
        private final int slop;

        Grid(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setClipChildren(false);
        }

        private float cellW() {
            return (getWidth() - U.u(8) * (COLS - 1)) / (float) COLS;
        }

        private float cellH() {
            return (getHeight() - U.u(8) * (ROWS - 1)) / (float) ROWS;
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
            setMeasuredDimension(w, h);
            int cw = (w - U.u(8) * (COLS - 1)) / COLS, ch = (h - U.u(8) * (ROWS - 1)) / ROWS;
            for (int i = 0; i < getChildCount(); i++)
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            float cw = cellW(), ch = cellH();
            for (int i = 0; i < getChildCount(); i++) {
                int col = i % COLS, row = i / COLS;
                int x = Math.round(col * (cw + U.u(8))), y = Math.round(row * (ch + U.u(8)));
                getChildAt(i).layout(x, y, x + Math.round(cw), y + Math.round(ch));
            }
        }

        private int indexAt(float x, float y) {
            float cw = cellW() + U.u(8), ch = cellH() + U.u(8);
            int col = Math.max(0, Math.min(COLS - 1, (int) (x / cw)));
            int row = Math.max(0, Math.min(ROWS - 1, (int) (y / ch)));
            return row * COLS + col;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!editing) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    dragIndex = indexAt(downX, downY);
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
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
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!editing || !dragging) return true;
            View v = getChildAt(dragIndex);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (v != null) {
                        v.setTranslationX(e.getX() - downX);
                        v.setTranslationY(e.getY() - downY);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
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
        for (int i = 0; i < COUNT; i++) l.add(s.optJSONObject(i));
        JSONObject x = l.remove(from);
        l.add(to, x);
        JSONArray out = new JSONArray();
        for (JSONObject o : l) out.put(o);
        save(out);
    }
}
