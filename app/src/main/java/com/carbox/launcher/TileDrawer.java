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
final class TileDrawer {
    /** Space between tiles: 8, or 12 when the ticker is hidden (the same gap between tiles and above the dock). */
    static float gapDu = 8;
    static final int COLS = 4, ROWS = 2, COUNT = COLS * ROWS; // on screen at once

    /** How many tiles there are: any number, set by adding and removing tiles. */
    static int count() {
        return specs().length();
    }

    /** Column by column: tiles 0 and 1 are the first column, 2 and 3 the next, and so on. */
    /**
     * How many grid cells a tile covers: {wide, tall}. 1x1 normally; a tile can be 2x1, 1x2 or 2x2,
     * set by the widget itself (Widgets.size) or by "w"/"h" in its saved spec.
     */
    static int[] span(JSONObject spec) {
        int[] d = spec == null ? new int[]{1, 1} : Widgets.size(spec.optString("id"));
        int w = spec == null ? 0 : spec.optInt("w", 0), h = spec == null ? 0 : spec.optInt("h", 0);
        return new int[]{Math.max(1, Math.min(2, w > 0 ? w : d[0])), Math.max(1, Math.min(ROWS, h > 0 ? h : d[1]))};
    }

    /** Sizes of the tiles on screen, in the same order as the grid's children. */
    private final List<int[]> spans = new ArrayList<>();

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

    TileDrawer(MainActivity a) {
        this.a = a;
        view = new Grid(a);
        Apps.listen(this::refresh);
        refresh();
    }

    /** Index of the empty tile, or -1. Only one is allowed at a time. */
    static int emptyIndex() {
        JSONArray s = specs();
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && "w".equals(o.optString("t")) && "none".equals(o.optString("id"))) return i;
        }
        return -1;
    }

    static void addTile() {
        if (emptyIndex() >= 0) {
            android.widget.Toast.makeText(MainActivity.I, "Fill the empty tile first", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
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
        Prefs.put("tiles_empty", out.length() == 0);
        if (MainActivity.I != null) MainActivity.I.tiles.refresh();
    }

    private static String cleanRaw, cleanOut; // last stored tiles and their tidied form

    static JSONArray specs() {
        String raw = Prefs.str(Prefs.TILES, "[]");
        if (raw.equals(cleanRaw) && cleanOut != null) { // unchanged since last time: skip the tidy-up
            try {
                return new JSONArray(cleanOut);
            } catch (Exception ignored) {
            }
        }
        JSONArray s = Prefs.arr(Prefs.TILES);
        if (s.length() == 0 && !Prefs.flag("tiles_empty", false)) s = defaults();
        if (!Prefs.flag("tiles_cm", false) && s.length() > 0) s = toColumns(s);
        s = clean(s);
        cleanRaw = Prefs.str(Prefs.TILES, "[]");
        cleanOut = s.toString();
        return s;
    }

    /**
     * One-off: tiles used to be stored row by row (0-3 top row, 4-7 bottom). Re-order them column by
     * column so they stay where they were on screen, and drop extra tiles the old "Home tiles" count hid.
     */
    private static JSONArray toColumns(JSONArray s) {
        int keep = Math.min(s.length(), Math.max(COUNT, Prefs.num("tiles_n", COUNT)));
        JSONArray out = new JSONArray();
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS; r++) {
                int old = r * COLS + c;
                if (old < Math.min(keep, COUNT)) out.put(s.opt(old));
            }
        for (int i = COUNT; i < keep; i++) out.put(s.opt(i)); // the extra columns were already column by column
        Prefs.put("tiles_cm", true);
        save(out);
        return out;
    }

    /**
     * Tidies tiles left over from older versions: empty tiles, widgets that no longer exist,
     * a widget that appears more than once (keeps the first), empty filler folders, and folders
     * sharing a name (renamed "Music 2" etc.). Saves only if something changed.
     */
    private static JSONArray clean(JSONArray s) {
        JSONArray out = new JSONArray();
        java.util.Set<String> widgets = new java.util.HashSet<>(), folders = new java.util.HashSet<>();
        boolean changed = false;
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o == null) {
                changed = true;
                continue;
            }
            if ("f".equals(o.optString("t"))) {
                JSONArray apps = o.optJSONArray("apps");
                String name = o.optString("name", "Folder").trim();
                if ((apps == null || apps.length() == 0) && ("Apps".equals(name) || name.isEmpty())) { // old empty filler
                    changed = true;
                    continue;
                }
                String n = name.isEmpty() ? "Folder" : name, base = n;
                for (int k = 2; folders.contains(n.toLowerCase(java.util.Locale.ROOT)); k++) n = base + " " + k;
                if (!n.equals(name)) {
                    Widgets.put(o, "name", n);
                    changed = true;
                }
                folders.add(n.toLowerCase(java.util.Locale.ROOT));
                out.put(o);
            } else {
                String id = o.optString("id");
                boolean known = false;
                for (String[] w : Widgets.CATALOG) if (w[0].equals(id)) known = true;
                if (!known || (!widgets.add(id) && !Widgets.multiple(id))) { // gone, empty ("none") or a duplicate
                    changed = true;
                    continue;
                }
                out.put(o);
            }
        }
        if (changed) save(out);
        return out;
    }

    static void save(JSONArray s) {
        Prefs.put(Prefs.TILES, s);
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
        // then every other widget (that the faith add-on allows), so a new home screen has them all
        java.util.Set<String> have = new java.util.HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null) have.add(o.optString("id"));
        }
        for (String[] w : Widgets.CATALOG) if (Faith.widgetAllowed(w[0]) && have.add(w[0])) s.put(widget(w[0]));
        Prefs.put("tiles_cm", true);
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
        spans.clear();
        JSONArray s = specs();
        for (int i = 0, n = s.length(); i < n; i++) {
            JSONObject spec = s.optJSONObject(i);
            if (spec == null) spec = widget("none");
            spans.add(span(spec));
            view.addView(tile(i, spec));
        }
        if (editing) {
            spans.add(new int[]{1, 1});
            view.addView(addTileView()); // "+" slot after the last tile
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
        f.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        Maps.clip(f, 16);

        if (editing) {
            View ring = new View(a);
            ring.setBackground(Ui.outline(0, Ui.ACCENT, 2, 16));
            f.addView(ring, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            View block = new View(a); // swallow taps on the content while editing
            block.setOnClickListener(v -> editTile(index, spec));
            f.addView(block, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            // red − : take the tile off the home screen
            TextView minus = Ui.text(a, "−", 18, Ui.TEXT, true);
            minus.setGravity(Gravity.CENTER);
            minus.setBackground(Ui.oval(Ui.RED));
            minus.setOnClickListener(v -> TileDrawer.removeTile(index));
            FrameLayout.LayoutParams ml = Ui.flpu(28, 28, Gravity.TOP | Gravity.END);
            ml.setMargins(0, Ui.u(6), Ui.u(6), 0);
            f.addView(minus, ml);
            // Edit: choose a different widget (or change the folder)
            FrameLayout edit = new FrameLayout(a); // pencil: change this tile
            edit.setBackground(Ui.oval(Ui.ACCENT));
            edit.addView(Ui.icon(a, R.drawable.ic_edit, Ui.TEXT), Ui.flpu(18, 18, Gravity.CENTER));
            edit.setOnClickListener(v -> editTile(index, spec));
            edit.setContentDescription("Edit");
            FrameLayout.LayoutParams el = Ui.flpu(38, 38, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            el.bottomMargin = Ui.u(10);
            f.addView(edit, el);
        } else {
            f.setOnLongClickListener(v -> {
                setEditing(true); // tiles wiggle-free edit mode: drag, − to remove, Edit to change
                return true;
            });
        }
        return f;
    }

    /** Edit on a tile: widgets open the widget chooser for that tile; folders open their options. */
    private static void editTile(int index, JSONObject spec) {
        if ("f".equals(spec.optString("t"))) AppPanels.tileMenu(index);
        else AppPanels.gallery(index);
    }

    private View addTileView() {
        LinearLayout l = Ui.col(a);
        l.setGravity(Gravity.CENTER);
        l.setBackground(Ui.outline(0, Ui.ACCENT, 2, 16));
        TextView plus = Ui.text(a, "+", 30, Ui.ACCENT_SOFT, true);
        plus.setGravity(Gravity.CENTER);
        l.addView(plus);
        TextView t = Ui.text(a, "Add tiles", 13, Ui.ACCENT_SOFT, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        l.setOnClickListener(v -> AppPanels.gallery(-1)); // pick widgets to add
        Ui.press(l);
        return l;
    }

    private View folderView(int index, JSONObject spec) {
        LinearLayout l = Ui.col(a);
        l.setBackground(Themes.card(Ui.CARD, 16));
        Ui.pad(l, 14, 14, 14, 14);
        TextView name = Ui.line(a, spec.optString("name", "Folder"), 15, Ui.TEXT, true);
        l.addView(name);
        JSONArray apps = spec.optJSONArray("apps");
        List<Apps.App> list = new ArrayList<>();
        if (apps != null) for (int i = 0; i < apps.length(); i++) {
            Apps.App ap = Apps.get(apps.optString(i));
            if (ap != null) list.add(ap);
        }
        TextView count = Ui.text(a, list.size() + (list.size() == 1 ? " app" : " apps"), 11, Ui.SUB, false);
        LinearLayout.LayoutParams cl = Ui.lp(Ui.WRAP, Ui.WRAP);
        cl.topMargin = Ui.u(3);
        l.addView(count, cl);
        l.addView(new View(a), Ui.weight(1, false));
        // 2×2 icons
        for (int r = 0; r < 2; r++) {
            LinearLayout row = Ui.row(a);
            for (int k = 0; k < 2; k++) {
                int i = r * 2 + k;
                FrameLayout cell = new FrameLayout(a);
                if (i < list.size()) {
                    final Apps.App ap = list.get(i);
                    ImageView iv = new ImageView(a);
                    iv.setImageDrawable(ap.icon);
                    cell.addView(iv, Ui.flpu(38, 38, Gravity.CENTER));
                    cell.setOnClickListener(v -> Apps.launch(a, ap));
                    cell.setOnLongClickListener(v -> {
                        setEditing(true); // tiles wiggle-free edit mode: drag, − to remove, Edit to change
                        return true;
                    });
                }
                if (i == 3 && list.size() > 4) {
                    cell.removeAllViews();
                    TextView more = Ui.text(a, "+" + (list.size() - 3), 13, Ui.TEXT, true);
                    more.setGravity(Gravity.CENTER);
                    more.setBackground(Themes.inner(Ui.CARD2, 10));
                    cell.addView(more, Ui.flpu(38, 38, Gravity.CENTER));
                    cell.setOnClickListener(v -> AppPanels.folder(index));
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, Ui.u(44), 1));
            }
            LinearLayout.LayoutParams rl = Ui.lp(Ui.MATCH, Ui.WRAP);
            if (r == 1) rl.topMargin = Ui.u(4);
            l.addView(row, rl);
        }
        l.setOnClickListener(v -> AppPanels.folder(index));
        if (list.isEmpty()) {
            l.removeViewAt(l.getChildCount() - 1);
            l.removeViewAt(l.getChildCount() - 1);
            TextView add = Ui.text(a, "Tap to add apps", 12, Ui.SUB, false);
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
        Ui.ui.post(tick);
    }

    void stop() {
        running = false;
        Ui.ui.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            android.graphics.Rect vis = new android.graphics.Rect();
            for (Widgets.W w : widgets) {
                try {
                    if (w.v != null && !w.v.getGlobalVisibleRect(vis)) continue; // off screen (another column): skip
                    w.tick();
                } catch (Exception ignored) {
                }
            }
            if (running) Ui.ui.postDelayed(this, 1000);
        }
    };

    /**
     * Lays tiles out 2 rows high, column by column, 4 columns to a screen width. Slides sideways with
     * the finger and keeps gliding after a flick, then settles on a whole column. Columns sliding in
     * at either edge grow and fade in as they're revealed. A small bar between the rows shows where
     * you are. In edit mode a quick swipe still slides; hold a tile to pick it up and drag it
     * (holding it at the edge scrolls).
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
                setEditing(true); // tiles wiggle-free edit mode: drag, − to remove, Edit to change
                return true;
            });
        }

        private float gap() {
            return Ui.u(gapDu);
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

        // ---- where each tile sits. Tiles fill column by column, top to bottom; a bigger tile takes the
        // first place it fits (a 1x1 that comes later can fill a gap left before it).
        private int[] pc = new int[0], pr = new int[0];
        private int packed;

        private void pack() {
            int n = getChildCount();
            pc = new int[n];
            pr = new int[n];
            List<boolean[]> occ = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int[] sp = i < spans.size() ? spans.get(i) : new int[]{1, 1};
                int w = sp[0], h = sp[1];
                search:
                for (int c = 0; ; c++) {
                    for (int r = 0; r + h <= ROWS; r++) {
                        boolean free = true;
                        for (int dc = 0; dc < w && free; dc++)
                            for (int dr = 0; dr < h && free; dr++)
                                if (c + dc < occ.size() && occ.get(c + dc)[r + dr]) free = false;
                        if (!free) continue;
                        while (occ.size() < c + w) occ.add(new boolean[ROWS]);
                        for (int dc = 0; dc < w; dc++) for (int dr = 0; dr < h; dr++) occ.get(c + dc)[r + dr] = true;
                        pc[i] = c;
                        pr[i] = r;
                        break search;
                    }
                }
            }
            packed = occ.size();
        }

        private int[] spanOf(int i) {
            return i < spans.size() ? spans.get(i) : new int[]{1, 1};
        }

        private int columns() {
            if (pc.length != getChildCount()) pack();
            return Math.max(1, packed);
        }

        // ---- endless rotation: with more than 4 columns the tiles loop round (after the last
        // column comes the first again). Not while editing, where tiles are dragged in order.
        private int woff; // how far the loop has turned, in pixels

        boolean wrap() {
            return !editing && columns() > COLS;
        }

        private int pos() {
            return wrap() ? woff : getScrollX();
        }

        private void setPos(int x) {
            if (wrap()) {
                if (getScrollX() != 0) scrollTo(0, 0);
                woff = x;
                place();
                reveal();
                invalidate();
            } else scrollTo(x, 0);
        }

        /** Move each tile to its place on the loop (shown just left of the screen or across it). */
        private void place() {
            float step = colStep(), w = columns() * step;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (!wrap()) {
                    if (!(dragging && i == dragIndex)) v.setTranslationX(0);
                    continue;
                }
                float base = pc[i] * step, p = ((base - woff) % w + w) % w;
                if (p >= w - step) p -= w; // the column just before the first: show it on the left
                v.setTranslationX(p - base);
            }
        }

        private int maxScroll() {
            return Math.max(0, Math.round((columns() - COLS) * colStep()));
        }

        void clampScroll() {
            if (getWidth() == 0) return;
            if (wrap()) {
                setPos(woff);
                return;
            }
            woff = 0;
            place();
            int x = Math.max(0, Math.min(maxScroll(), getScrollX()));
            if (x != getScrollX()) scrollTo(x, 0);
            reveal();
        }

        /** Glide back so the first tile is on the left (the short way round when looping). */
        void toStart() {
            post(() -> {
                if (wrap()) {
                    int loop = Math.round(columns() * colStep()), m = ((woff % loop) + loop) % loop;
                    glideTo(m <= loop / 2 ? woff - m : woff + (loop - m));
                } else glideTo(0);
            });
        }

        void scrollToEnd() {
            post(() -> glideTo(wrap() ? Math.round((columns() - COLS) * colStep()) : maxScroll()));
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
            setMeasuredDimension(w, h);
            float cw = (w - gap() * (COLS - 1)) / COLS, ch = (h - gap() * (ROWS - 1)) / ROWS;
            pack();
            for (int i = 0; i < getChildCount(); i++) {
                int[] sp = spanOf(i);
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.round(cw * sp[0] + gap() * (sp[0] - 1)), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.round(ch * sp[1] + gap() * (sp[1] - 1)), MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int rr, int b) {
            float cw = cellW(), ch = cellH();
            if (pc.length != getChildCount()) pack();
            for (int i = 0; i < getChildCount(); i++) {
                int[] sp = spanOf(i);
                int x = Math.round(pc[i] * (cw + gap())), y = Math.round(pr[i] * (ch + gap()));
                getChildAt(i).layout(x, y, x + Math.round(cw * sp[0] + gap() * (sp[0] - 1)), y + Math.round(ch * sp[1] + gap() * (sp[1] - 1)));
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
                float tx = wrap() ? v.getTranslationX() : 0;
                float left = v.getLeft() + tx - sx, right = v.getRight() + tx - sx, cw = v.getWidth();
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
            float w = Ui.u(64), h = Ui.u(3), x0 = getScrollX() + (getWidth() - w) / 2f, y = cellH() + (gap() - h) / 2f;
            r.set(x0, y, x0 + w, y + h);
            bar.setColor(0x33FFFFFF);
            cv.drawRoundRect(r, h / 2, h / 2, bar);
            float loop = cols * colStep();
            float part = w * COLS / cols, pos = wrap() ? (((woff % loop) + loop) % loop) / loop * w / (w - part) : maxScroll() == 0 ? 0 : getScrollX() / (float) maxScroll();
            r.left = x0 + (w - part) * (wrap() ? Math.min(pos, 1) : Math.max(0, Math.min(1, pos)));
            r.right = r.left + part;
            bar.setColor(0xCCFFFFFF);
            cv.drawRoundRect(r, h / 2, h / 2, bar);
        }

        private int indexAt(float x, float y) {
            int col;
            if (wrap()) {
                float loop = columns() * colStep();
                col = (int) ((((x + woff) % loop) + loop) % loop / colStep());
            } else col = Math.max(0, (int) ((x + getScrollX()) / colStep()));
            int row = Math.max(0, Math.min(ROWS - 1, (int) (y / (cellH() + gap()))));
            if (pc.length != getChildCount()) pack();
            int best = getChildCount() - 1;
            for (int i = 0; i < getChildCount(); i++) { // the tile covering that cell (big tiles cover several)
                int[] sp = spanOf(i);
                if (col >= pc[i] && col < pc[i] + sp[0] && row >= pr[i] && row < pr[i] + sp[1]) return i;
                if (pc[i] <= col) best = i; // a gap: the last tile before it
            }
            return Math.max(0, best);
        }

        // ---- sliding

        private int snap(float x) {
            int v = Math.round(Math.round(x / colStep()) * colStep());
            return wrap() ? v : Math.max(0, Math.min(maxScroll(), v));
        }

        private void glideTo(int x) {
            scroller.forceFinished(true);
            scroller.startScroll(pos(), 0, x - pos(), 0, 320);
            postInvalidateOnAnimation();
        }

        /** Keep gliding after a flick, then come to rest on a whole column. */
        private void fling(float velocity) {
            scroller.forceFinished(true);
            scroller.fling(pos(), 0, Math.round(-velocity), 0, wrap() ? -1000000 : 0, wrap() ? 1000000 : maxScroll(), 0, 0);
            int end = snap(scroller.getFinalX());
            scroller.forceFinished(true);
            int dist = Math.abs(end - pos());
            scroller.startScroll(pos(), 0, end - pos(), 0, Math.max(220, Math.min(700, (int) (dist / Ui.uf(1.2f)))));
            postInvalidateOnAnimation();
        }

        @Override
        public void computeScroll() {
            if (scroller.computeScrollOffset()) {
                setPos(scroller.getCurrX());
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
                    scrollStart = pos();
                    boolean wasMoving = !scroller.isFinished();
                    scroller.forceFinished(true);
                    if (vt != null) vt.recycle();
                    vt = android.view.VelocityTracker.obtain();
                    vt.addMovement(e);
                    if (editing && dragIndex < count()) postDelayed(lift, 260); // hold a tile, then drag it
                    return wasMoving; // a touch while gliding just stops it
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(e);
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (editing) {
                        if (dragging) return true;
                        if (Math.hypot(dx, dy) > slop) removeCallbacks(lift); // moved before the hold: it's a slide
                    }
                    if ((wrap() || maxScroll() > 0) && sideways(dx, dy)) {
                        swiping = true;
                        lastX = e.getX();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true; // the tile under the finger gets a cancel, so no tap or long-press fires
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(lift);
                    return false;
            }
            return false;
        }

        /** Held still long enough: pick the tile up. */
        private final Runnable lift = () -> {
            if (!editing || swiping || dragIndex < 0 || dragIndex >= count()) return;
            dragging = true;
            View v = getChildAt(dragIndex);
            if (v != null) {
                // raise it above the others without changing child order (layout is by index)
                v.setTranslationZ(Ui.uf(12));
                v.setAlpha(1f);
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(Ui.ms(100)).start();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
            getParent().requestDisallowInterceptTouchEvent(true);
        };

        private final Runnable edgeScroll = new Runnable() {
            @Override
            public void run() {
                if (!dragging || edgeDir == 0) {
                    edgePending = false;
                    return;
                }
                glideTo(snap(pos() + edgeDir * colStep()));
                Ui.ui.postDelayed(this, 650);
            }
        };
        private int edgeDir;
        private boolean edgePending;

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (vt != null) vt.addMovement(e);
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) removeCallbacks(lift);
            if (!dragging) {
                // a slide that started on a gap between tiles never passes through onInterceptTouchEvent
                if (!swiping && e.getActionMasked() == MotionEvent.ACTION_MOVE && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) removeCallbacks(lift);
                if (!swiping && e.getActionMasked() == MotionEvent.ACTION_MOVE && (wrap() || maxScroll() > 0) && sideways(e.getX() - downX, e.getY() - downY)) {
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
                        int x = pos() - Math.round(d);
                        // stretchy past either end (no ends when the tiles loop round)
                        if (!wrap() && (x < 0 || x > maxScroll())) x = pos() - Math.round(d / 3f);
                        setPos(x);
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
            if (!editing) return super.onTouchEvent(e);
            View v = getChildAt(dragIndex);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (v != null) {
                        v.setTranslationX(e.getX() + getScrollX() - downX - scrollStart);
                        v.setTranslationY(e.getY() - downY);
                    }
                    // hold a tile near the left or right edge to bring in more columns
                    float edge = Ui.uf(36);
                    edgeDir = maxScroll() == 0 ? 0 : e.getX() > getWidth() - edge ? 1 : e.getX() < edge ? -1 : 0;
                    if (edgeDir != 0 && !edgePending) {
                        edgePending = true;
                        Ui.ui.postDelayed(edgeScroll, 450);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    edgeDir = 0;
                    edgePending = false;
                    Ui.ui.removeCallbacks(edgeScroll);
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
