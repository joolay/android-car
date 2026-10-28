package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Pre-drive road safety checklist. The tile shows the next unchecked item; tap the tick to check
 * it off, tap the tile for the whole list. The list starts again after the car has been parked
 * for 3 hours (or on a new day). Items can be edited in the widget's settings.
 */
final class Safety {
    static final String DEFAULT = "Seatbelts on for everyone|Seat and mirrors adjusted|Lights and indicators working|Tyres look OK|Enough fuel or charge|Phone mounted, hands-free ready|Windscreen and windows clear|Loose items and load secured|Route planned|Rested and fit to drive";
    private static final long RESET = 3 * 3600000L;

    private Safety() {
    }

    static String[] items() {
        String s = Store.str("safety_items", DEFAULT).trim();
        return (s.isEmpty() ? DEFAULT : s).split("\\|");
    }

    /** Checked items as a bit mask; cleared when it's a fresh start. */
    static long done() {
        long at = Store.lng("safety_at", 0), now = System.currentTimeMillis();
        boolean newDay = at > 0 && !sameDay(at, now);
        if (at > 0 && (now - at > RESET || newDay) && !Logbook.driving()) {
            Store.put("safety_done", 0L);
            Store.put("safety_at", 0L);
        }
        return Store.lng("safety_done", 0);
    }

    static void toggle(int i) {
        Store.put("safety_done", done() ^ (1L << i));
        Store.put("safety_at", System.currentTimeMillis());
    }

    static int count() {
        return Long.bitCount(done() & ((1L << items().length) - 1));
    }

    static int next() {
        long d = done();
        String[] it = items();
        for (int i = 0; i < it.length; i++) if ((d & (1L << i)) == 0) return i;
        return -1;
    }

    /** The whole list, and the deeper settings (edit items, start again). */
    static void list() {
        Sheets.Page p = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            String[] it = items();
            long d = done();
            for (int i = 0; i < it.length; i++) {
                final int k = i;
                boolean on = (d & (1L << i)) != 0;
                LinearLayout row = U.row(c);
                row.setMinimumHeight(U.u(42));
                row.addView(tick(c, on), U.lpu(24, 24));
                TextView t = U.line(c, it[i], 15, on ? U.SUB : U.TEXT, false);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                tl.leftMargin = U.u(12);
                row.addView(t, tl);
                row.setOnClickListener(v -> {
                    toggle(k);
                    Sheets.refresh();
                });
                if (i > 0) b.addView(U.hr(c));
                b.addView(row);
            }
            LinearLayout r = U.row(c);
            r.addView(U.button(c, "Start again", false, () -> {
                Store.put("safety_done", 0L);
                Sheets.refresh();
            }));
            r.addView(U.gap(c, 10, 1));
            r.addView(U.button(c, "All done", true, () -> {
                Store.put("safety_done", (1L << items().length) - 1);
                Store.put("safety_at", System.currentTimeMillis());
                Sheets.refresh();
            }));
            b.addView(r, Widgets.top(12));
            b.addView(Sheets.section("Edit the checklist (one item per line)"));
            android.widget.EditText e = Widgets.field("Checklist items", String.join("\n", it), false,
                    s -> Store.put("safety_items", s.trim().replaceAll("\\s*\\n+\\s*", "|")));
            e.setSingleLine(false);
            e.setMinLines(4);
            b.addView(e, U.lp(U.MATCH, U.WRAP));
            b.addView(U.button(c, "Restore the usual list", false, () -> {
                Store.put("safety_items", DEFAULT);
                Store.put("safety_done", 0L);
                Sheets.refresh();
            }), Widgets.top(8));
            return Sheets.panel(String.format(Locale.US, "Safety check · %d of %d", count(), it.length), b, 620, 440);
        };
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }

    private static boolean sameDay(long a, long b) {
        java.util.Calendar x = java.util.Calendar.getInstance(), y = java.util.Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(java.util.Calendar.YEAR) == y.get(java.util.Calendar.YEAR) && x.get(java.util.Calendar.DAY_OF_YEAR) == y.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private static android.graphics.drawable.GradientDrawable ring(int color, float w) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setStroke(U.u(w), color);
        return g;
    }

    /** Round tick drawn to its own size, so it is the same crisp circle at any size. */
    static View tick(Context c, boolean on) {
        return new View(c) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path path = new android.graphics.Path();

            @Override
            protected void onDraw(android.graphics.Canvas cv) {
                float s = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f, r = s / 2f - U.u(1.5f);
                p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                if (on) {
                    p.setStyle(android.graphics.Paint.Style.FILL);
                    p.setColor(U.GREEN);
                    cv.drawCircle(cx, cy, r, p);
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                    p.setStrokeWidth(s * 0.11f);
                    p.setColor(0xFF0B0E13);
                    path.reset();
                    path.moveTo(cx - s * 0.2f, cy + s * 0.01f);
                    path.lineTo(cx - s * 0.05f, cy + s * 0.15f);
                    path.lineTo(cx + s * 0.21f, cy - s * 0.13f);
                    cv.drawPath(path, p);
                } else {
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                    p.setStrokeWidth(U.u(2));
                    p.setColor(U.SUB);
                    cv.drawCircle(cx, cy, r, p);
                }
            }
        };
    }

    /**
     * The tile's picture: a seated person with a seatbelt inside a progress ring. Drawn to fit
     * whatever room the tile gives it (always square and centred), so it never looks stretched.
     */
    static final class Badge extends View {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private final android.graphics.Path path = new android.graphics.Path();
        private int done, total;

        Badge(Context c) {
            super(c);
        }

        void set(int done, int total) {
            if (done == this.done && total == this.total) return;
            this.done = done;
            this.total = total;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            float s = Math.min(getWidth(), getHeight());
            if (s <= 0) return;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, ring = s * 0.065f, rad = s / 2f - ring / 2f - 1;
            boolean all = total > 0 && done >= total, none = done == 0;
            int AMBER = 0xFFE8A33D;
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            // ring: grey track, green for what's checked
            p.setStrokeWidth(ring);
            p.setColor(all ? U.GREEN : U.CARD3);
            cv.drawCircle(cx, cy, rad, p);
            if (!all && !none) {
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                p.setColor(U.GREEN);
                cv.drawArc(r, -90, 360f * done / Math.max(1, total), false, p);
            }
            // person: head, shoulders and body
            float u = s / 92f; // drawn on a 92-unit square, like the mockup
            int body = all ? U.GREEN : none ? AMBER : U.TEXT;
            p.setColor(body);
            p.setStrokeWidth(3.5f * u);
            cv.drawCircle(cx, cy - 17 * u, 6 * u, p);
            path.reset();
            path.moveTo(cx - 14 * u, cy + 22 * u);
            path.lineTo(cx - 14 * u, cy + 2 * u);
            path.quadTo(cx - 14 * u, cy - 6 * u, cx - 6 * u, cy - 6 * u);
            path.lineTo(cx + 6 * u, cy - 6 * u);
            path.quadTo(cx + 14 * u, cy - 6 * u, cx + 14 * u, cy + 2 * u);
            path.lineTo(cx + 14 * u, cy + 22 * u);
            cv.drawPath(path, p);
            // seatbelt across
            p.setColor(all || !none ? U.GREEN : U.TEXT);
            p.setStrokeWidth(4 * u);
            cv.drawLine(cx + 8 * u, cy - 7 * u, cx - 12 * u, cy + 20 * u, p);
            if (all) { // tick badge
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(U.GREEN);
                float bx = cx + rad * 0.62f, by = cy + rad * 0.62f, br = 11 * u;
                cv.drawCircle(bx, by, br, p);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setColor(0xFF0B0E13);
                p.setStrokeWidth(3 * u);
                path.reset();
                path.moveTo(bx - 5 * u, by);
                path.lineTo(bx - 1 * u, by + 4 * u);
                path.lineTo(bx + 6 * u, by - 4 * u);
                cv.drawPath(path, p);
            }
        }
    }

    static final class Tile extends Widgets.W {
        private final TextView status;
        private final Badge badge;

        Tile(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = Widgets.card(c, U.CARD);
            l.addView(Widgets.label(c, "SAFETY CHECK"));
            badge = new Badge(c);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(U.MATCH, 0, 1);
            bl.topMargin = U.u(4);
            bl.bottomMargin = U.u(4);
            l.addView(badge, bl); // takes the room that's left, drawn square in the middle
            status = U.line(c, "", 13, U.TEXT, true);
            status.setGravity(Gravity.CENTER);
            l.addView(status, U.lp(U.MATCH, U.WRAP));
            l.setOnClickListener(v -> list());
            U.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
            int n = items().length, k = Math.min(count(), n);
            badge.set(k, n);
            if (k >= n) {
                status.setText("Ready to drive");
                status.setTextColor(U.GREEN);
            } else if (k == 0) {
                status.setText("Tap to check");
                status.setTextColor(0xFFE8A33D);
            } else {
                status.setText(k + " of " + n);
                status.setTextColor(U.TEXT);
            }
        }
    }
}
