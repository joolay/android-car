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
    static Sheets.Page page;

    private TileEditor() {
    }

    static void open() {
        page = TileEditor::build;
        Sheets.showFull(page); // the editor uses the whole screen
    }

    private static View build() {
        Context c = Sheets.ctx();
        LinearLayout card = U.col(c);
        card.setBackground(U.round(U.CARD, 22));
        U.pad(card, 22, 16, 22, 16);
        card.setLayoutParams(Sheets.centered(784, 464));

        LinearLayout head = U.row(c);
        LinearLayout titles = U.col(c);
        titles.addView(U.line(c, "Edit tiles", 20, U.TEXT, true));
        titles.addView(U.line(c, "Hold a tile and drag to move it · tap to change or remove · swipe to see more", 12, U.SUB, false), Widgets.top(2));
        head.addView(titles, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        head.addView(U.button(c, "Done", true, () -> {
            Sheets.close();
            if (MainActivity.I != null) MainActivity.I.tiles.refresh();
        }));
        card.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setFillViewport(true);
        Board board = new Board(c, hs);
        hs.addView(board, new FrameLayout.LayoutParams(U.WRAP, U.MATCH));
        LinearLayout.LayoutParams hl = U.weight(1, false);
        hl.topMargin = U.u(14);
        card.addView(hs, hl);
        return card;
    }

    // ------------------------------------------------------------------ cards

    private static View cardFor(Context c, int index, JSONObject spec) {
        boolean folder = spec == null || "f".equals(spec.optString("t"));
        String id = spec == null ? "none" : spec.optString("id");
        boolean empty = !folder && "none".equals(id);
        LinearLayout l = U.col(c);
        l.setBackground(empty ? U.outline(U.CARD2, U.LINE, 1, 14) : U.round(folder ? U.CARD2 : 0xFF1A2740, 14));
        U.pad(l, 12, 12, 12, 10);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(U.oval(folder ? U.CARD3 : U.ACCENT));
        ic.addView(U.icon(c, folder ? R.drawable.ic_folder : empty ? R.drawable.ic_plus : R.drawable.ic_apps, U.TEXT), U.flpu(18, 18, Gravity.CENTER));
        l.addView(ic, U.lpu(34, 34));
        l.addView(new View(c), U.weight(1, false));
        String name = folder ? (spec == null ? "Folder" : spec.optString("name", "Folder")) : Widgets.name(id);
        TextView t = U.text(c, name, 14, empty ? U.SUB : U.TEXT, true);
        t.setMaxLines(2);
        l.addView(t);
        String sub;
        if (folder) {
            JSONArray apps = spec == null ? null : spec.optJSONArray("apps");
            int n = apps == null ? 0 : apps.length();
            sub = "Folder · " + n + (n == 1 ? " app" : " apps");
        } else sub = empty ? "Tap to choose" : "Widget";
        l.addView(U.line(c, sub, 11, U.SUB, false), Widgets.top(2));
        TextView num = U.text(c, String.valueOf(index + 1), 11, U.MUTED, true);
        FrameLayout f = new FrameLayout(c);
        f.addView(l, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.END);
        nl.setMargins(0, U.u(10), U.u(12), 0);
        f.addView(num, nl);
        return f;
    }

    private static View addCard(Context c) {
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(U.outline(0, U.ACCENT, 2, 14));
        TextView plus = U.text(c, "+", 30, U.ACCENT_SOFT, true);
        plus.setGravity(Gravity.CENTER);
        l.addView(plus);
        TextView t = U.text(c, "Add tile", 13, U.ACCENT_SOFT, false);
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
                hs.scrollBy(edgeDir * U.u(14), 0);
                follow(lastRawX, lastRawY);
                U.ui.postDelayed(this, 16);
            }
        };
        private int edgeDir;
        private float lastRawX, lastRawY;

        Board(Context c, HorizontalScrollView hs) {
            super(c);
            this.hs = hs;
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            setClipChildren(false);
            fill();
        }

        private void fill() {
            removeAllViews();
            JSONArray s = Tiles.specs();
            count = s.length();
            for (int i = 0; i < count; i++) addView(cardFor(getContext(), i, s.optJSONObject(i)));
            addView(addCard(getContext()));
        }

        private float gap() {
            return U.u(10);
        }

        private float cellH() {
            return (getHeight() - gap()) / 2f;
        }

        private float cellW() {
            // a bit narrower than the height so 5 columns show and you can see there are more
            return Math.min(U.u(140), cellH() * 0.95f);
        }

        @Override
        protected void onMeasure(int ws, int hsSpec) {
            int h = MeasureSpec.getSize(hsSpec);
            float ch = (h - gap()) / 2f, cw = Math.min(U.u(140), ch * 0.95f);
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
            v.setTranslationZ(U.uf(12));
            v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(100).start();
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
                w.animate().translationX((pos / 2 - i / 2) * step).translationY((pos % 2 - i % 2) * rowStep).setDuration(150).start();
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
                    if (i < count) U.ui.postDelayed(pickUp, 280);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lastRawX = e.getRawX();
                    lastRawY = e.getRawY();
                    if (!dragging) {
                        if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                            moved = true;
                            U.ui.removeCallbacks(pickUp); // a swipe, not a hold: let the row scroll
                        }
                        return true;
                    }
                    follow(lastRawX, lastRawY);
                    // near either edge of the window: keep scrolling
                    int[] o = new int[2];
                    hs.getLocationOnScreen(o);
                    float rx = lastRawX - o[0], zone = U.uf(48);
                    int dir = rx > hs.getWidth() - zone ? 1 : rx < zone ? -1 : 0;
                    if (dir != edgeDir) {
                        edgeDir = dir;
                        U.ui.removeCallbacks(edge);
                        if (dir != 0) U.ui.post(edge);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    U.ui.removeCallbacks(pickUp);
                    U.ui.removeCallbacks(edge);
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
                            Tiles.addTile();
                            fill();
                            hs.post(() -> hs.fullScroll(View.FOCUS_RIGHT));
                        } else AppPanels.tileMenu(dragIndex);
                    }
                    return true;
            }
            return true;
        }

        private void move(int from, int to) {
            JSONArray s = Tiles.specs();
            java.util.List<Object> l = new java.util.ArrayList<>();
            for (int k = 0; k < s.length(); k++) l.add(s.opt(k));
            l.add(to, l.remove(from));
            JSONArray out = new JSONArray();
            for (Object o : l) out.put(o);
            Tiles.save(out);
            if (MainActivity.I != null) MainActivity.I.tiles.refresh();
        }
    }
}
