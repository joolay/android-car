package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Full-screen tile editor, opened by holding a tile. Every tile is shown as a card in two rows
 * that slide sideways (same order as the home screen: column by column). Hold a card briefly
 * and drag it to reorder; a quick swipe scrolls; tap a card to change or remove it; + adds one.
 */
final class TileEditor {
    static Panels.Page page;

    private TileEditor() {
    }

    static void open() {
        // the separate editor is gone: the widget page now shows your tiles first, to drag and reorder
        AppPanels.gallery(-1);
    }

    static void openOld() {
        page = TileEditor::build;
        Panels.showFull(page); // the editor uses the whole screen
    }

    private static View build() {
        Context c = Panels.ctx();
        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD, 22));
        Ui.pad(card, 22, 16, 22, 16);
        card.setLayoutParams(Panels.centered(784, 464));

        LinearLayout head = Ui.row(c);
        LinearLayout titles = Ui.col(c);
        titles.addView(Ui.line(c, "Edit tiles", 20, Ui.TEXT, true));
        titles.addView(Ui.line(c, "Hold a tile and drag to move it · tap to change or remove · + to add tiles", 12, Ui.SUB, false), Widgets.top(2));
        head.addView(titles, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        head.addView(Ui.button(c, "Done", true, () -> {
            Panels.close();
            if (MainActivity.I != null) MainActivity.I.tiles.refresh();
        }));
        card.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setFillViewport(true);
        Board board = new Board(c, hs);
        hs.addView(board, new FrameLayout.LayoutParams(Ui.WRAP, Ui.MATCH));
        LinearLayout.LayoutParams hl = Ui.weight(1, false);
        hl.topMargin = Ui.u(14);
        card.addView(hs, hl);
        return card;
    }

    // ------------------------------------------------------------------ cards

    private static View cardFor(Context c, int index, JSONObject spec) {
        boolean folder = spec == null || "f".equals(spec.optString("t"));
        String id = spec == null ? "none" : spec.optString("id");
        boolean empty = !folder && "none".equals(id);
        if (!folder && !empty) { // widgets: show the real thing, shrunk to fit
            FrameLayout f = new FrameLayout(c);
            f.addView(Widgets.preview(c, spec), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            f.addView(minusDot(c), Ui.flpu(24, 24, Gravity.TOP | Gravity.END));
            return f;
        }
        LinearLayout l = Ui.col(c);
        l.setBackground(empty ? Ui.outline(Ui.CARD2, Ui.LINE, 1, 14) : Ui.round(folder ? Ui.CARD2 : 0xFF1A2740, 14));
        Ui.pad(l, 12, 12, 12, 10);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.oval(folder ? Ui.CARD3 : Ui.ACCENT));
        ic.addView(Ui.icon(c, folder ? R.drawable.ic_folder : empty ? R.drawable.ic_plus : R.drawable.ic_apps, Ui.TEXT), Ui.flpu(18, 18, Gravity.CENTER));
        l.addView(ic, Ui.lpu(34, 34));
        l.addView(new View(c), Ui.weight(1, false));
        String name = folder ? (spec == null ? "Folder" : spec.optString("name", "Folder")) : Widgets.name(id);
        TextView t = Ui.text(c, name, 14, empty ? Ui.SUB : Ui.TEXT, true);
        t.setMaxLines(2);
        l.addView(t);
        String sub;
        if (folder) {
            JSONArray apps = spec == null ? null : spec.optJSONArray("apps");
            int n = apps == null ? 0 : apps.length();
            sub = "Folder · " + n + (n == 1 ? " app" : " apps");
        } else sub = empty ? "Tap to choose" : "Tile";
        l.addView(Ui.line(c, sub, 11, Ui.SUB, false), Widgets.top(2));
        FrameLayout f = new FrameLayout(c);
        f.addView(l, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        f.addView(minusDot(c), Ui.flpu(24, 24, Gravity.TOP | Gravity.END));
        return f;
    }

    /** Red dot with − in a card's corner: tap it to take that tile off the home screen. */
    private static View minusDot(Context c) {
        TextView m = Ui.text(c, "−", 16, Ui.TEXT, true);
        m.setGravity(Gravity.CENTER);
        m.setBackground(Ui.oval(Ui.RED));
        return m;
    }

    private static View addCard(Context c) {
        LinearLayout l = Ui.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(Ui.outline(0, Ui.ACCENT, 2, 14));
        TextView plus = Ui.text(c, "+", 30, Ui.ACCENT_SOFT, true);
        plus.setGravity(Gravity.CENTER);
        l.addView(plus);
        TextView t = Ui.text(c, "Add tiles", 13, Ui.ACCENT_SOFT, false);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        return l;
    }

    // ------------------------------------------------------------------ board

    /** Two rows of cards, column by column, with hold-and-drag reordering. */
    static final class Board extends ViewGroup {
        private final HorizontalScrollView hs;
        private final int slop;
        private int count;
        private float downX, downY;
        private int dragIndex = -1, overIndex = -1;
        private boolean dragging, moved;
        private final Runnable pickUp = this::pickUp;
        private final Runnable edge = new Runnable() {
            @Override
            public void run() {
                if (!dragging || edgeDir == 0) return;
                hs.scrollBy(edgeDir * Ui.u(14), 0);
                follow(lastRawX, lastRawY);
                Ui.ui.postDelayed(this, 16);
            }
        };
        private int edgeDir;
        private float lastRawX, lastRawY;

        private boolean addSlot = true;

        /** For the widget page: the home-screen tiles only, no "+" card. */
        Board(Context c, HorizontalScrollView hs, boolean addSlot) {
            this(c, hs);
            this.addSlot = addSlot;
            fill();
        }

        Board(Context c, HorizontalScrollView hs) {
            super(c);
            this.hs = hs;
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setClipChildren(false);
            fill();
        }

        private void fill() {
            removeAllViews();
            JSONArray s = TileDrawer.specs();
            count = s.length();
            for (int i = 0; i < count; i++) addView(cardFor(getContext(), i, s.optJSONObject(i)));
            if (addSlot) addView(addCard(getContext()));
        }

        private float gap() {
            return Ui.u(10);
        }

        private float cellH() {
            return (getHeight() - gap()) / 2f;
        }

        private float cellW() {
            // a bit narrower than the height so 5 columns show and you can see there are more
            return Math.min(Ui.u(140), cellH() * 0.76f); // the shape of a home-screen tile
        }

        @Override
        protected void onMeasure(int ws, int hsSpec) {
            int h = MeasureSpec.getSize(hsSpec);
            float ch = (h - gap()) / 2f, cw = Math.min(Ui.u(140), ch * 0.76f);
            int cols = (getChildCount() + 1) / 2;
            int w = Math.round(cols * cw + (cols - 1) * gap());
            setMeasuredDimension(Math.max(w, MeasureSpec.getSize(ws)), h);
            for (int i = 0; i < getChildCount(); i++)
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.round(cw), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(ch), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            float cw = cellW(), ch = cellH();
            for (int i = 0; i < getChildCount(); i++) {
                int x = Math.round((i / 2) * (cw + gap())), y = Math.round((i % 2) * (ch + gap()));
                getChildAt(i).layout(x, y, x + Math.round(cw), y + Math.round(ch));
            }
        }

        private int indexAt(float x, float y) {
            int col = Math.max(0, (int) (x / (cellW() + gap())));
            int row = y < cellH() + gap() / 2 ? 0 : 1;
            return Math.max(0, Math.min(count - 1, col * 2 + row));
        }

        private void pickUp() {
            if (dragIndex < 0 || dragIndex >= count) return;
            dragging = true;
            getParent().requestDisallowInterceptTouchEvent(true); // stop the scroll view taking over
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            View v = getChildAt(dragIndex);
            v.setTranslationZ(Ui.uf(12));
            v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(Ui.ms(100)).start();
        }

        /** Move the lifted card with the finger and slide the others aside to show where it'll land. */
        private void follow(float rawX, float rawY) {
            int[] o = new int[2];
            getLocationOnScreen(o);
            float x = rawX - o[0], y = rawY - o[1];
            View v = getChildAt(dragIndex);
            v.setTranslationX(x - (v.getLeft() + v.getWidth() / 2f));
            v.setTranslationY(y - (v.getTop() + v.getHeight() / 2f));
            int to = indexAt(x, y);
            if (to == overIndex) return;
            overIndex = to;
            float step = cellW() + gap(), rowStep = cellH() + gap();
            for (int i = 0; i < count; i++) {
                if (i == dragIndex) continue;
                int pos = i; // where this card would sit if the dragged card dropped at `to`
                if (dragIndex < to && i > dragIndex && i <= to) pos = i - 1;
                else if (dragIndex > to && i >= to && i < dragIndex) pos = i + 1;
                View w = getChildAt(i);
                w.animate().translationX((pos / 2 - i / 2) * step).translationY((pos % 2 - i % 2) * rowStep).setDuration(Ui.ms(150)).start();
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            return true; // the board handles taps, holds and drags; the scroll view still takes quick swipes
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    moved = false;
                    dragging = false;
                    overIndex = -1;
                    int i = Math.min(getChildCount() - 1, Math.max(0, (int) (downX / (cellW() + gap())) * 2 + (downY < cellH() + gap() / 2 ? 0 : 1)));
                    dragIndex = i;
                    if (i < count) Ui.ui.postDelayed(pickUp, 280);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lastRawX = e.getRawX();
                    lastRawY = e.getRawY();
                    if (!dragging) {
                        if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                            moved = true;
                            Ui.ui.removeCallbacks(pickUp); // a swipe, not a hold: let the row scroll
                        }
                        return true;
                    }
                    follow(lastRawX, lastRawY);
                    // near either edge of the window: keep scrolling
                    int[] o = new int[2];
                    hs.getLocationOnScreen(o);
                    float rx = lastRawX - o[0], zone = Ui.uf(48);
                    int dir = rx > hs.getWidth() - zone ? 1 : rx < zone ? -1 : 0;
                    if (dir != edgeDir) {
                        edgeDir = dir;
                        Ui.ui.removeCallbacks(edge);
                        if (dir != 0) Ui.ui.post(edge);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Ui.ui.removeCallbacks(pickUp);
                    Ui.ui.removeCallbacks(edge);
                    edgeDir = 0;
                    if (dragging) {
                        dragging = false;
                        int to = overIndex < 0 ? dragIndex : overIndex;
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && to != dragIndex) move(dragIndex, to);
                        fill();
                        return true;
                    }
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && !moved) {
                        if (dragIndex >= count) {
                            // no empty tiles any more: pick widgets straight away (blue = already on the home screen)
                            int e0 = TileDrawer.emptyIndex();
                            AppPanels.gallery(e0 >= 0 ? e0 : -1);
                        } else {
                            // a tap on the red − in the top-right corner removes the tile
                            View v = getChildAt(dragIndex);
                            if (v != null && downX > v.getRight() - Ui.u(34) && downY < v.getTop() + Ui.u(34)) {
                                TileDrawer.removeTile(dragIndex);
                                fill();
                                requestLayout();
                            } else AppPanels.tileMenu(dragIndex);
                        }
                    }
                    return true;
            }
            return true;
        }

        private void move(int from, int to) {
            JSONArray s = TileDrawer.specs();
            java.util.List<Object> l = new java.util.ArrayList<>();
            for (int k = 0; k < s.length(); k++) l.add(s.opt(k));
            l.add(to, l.remove(from));
            JSONArray out = new JSONArray();
            for (Object o : l) out.put(o);
            TileDrawer.save(out);
            if (MainActivity.I != null) MainActivity.I.tiles.refresh();
        }
    }
}
