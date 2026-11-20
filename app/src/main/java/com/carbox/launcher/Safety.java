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
        String s = Prefs.str("safety_items", DEFAULT).trim();
        return (s.isEmpty() ? DEFAULT : s).split("\\|");
    }

    /** Checked items as a bit mask; cleared when it's a fresh start. */
    static long done() {
        long at = Prefs.lng("safety_at", 0), now = System.currentTimeMillis();
        boolean newDay = at > 0 && !sameDay(at, now);
        if (at > 0 && (now - at > RESET || newDay) && !Logbook.driving()) {
            Prefs.put("safety_done", 0L);
            Prefs.put("safety_at", 0L);
        }
        return Prefs.lng("safety_done", 0);
    }

    static void toggle(int i) {
        Prefs.put("safety_done", done() ^ (1L << i));
        Prefs.put("safety_at", System.currentTimeMillis());
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
        Panels.Page p = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            String[] it = items();
            long d = done();
            for (int i = 0; i < it.length; i++) {
                final int k = i;
                boolean on = (d & (1L << i)) != 0;
                LinearLayout row = Ui.row(c);
                row.setMinimumHeight(Ui.u(42));
                row.addView(tick(c, on), Ui.lpu(24, 24));
                TextView t = Ui.line(c, it[i], 15, on ? Ui.SUB : Ui.TEXT, false);
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
                tl.leftMargin = Ui.u(12);
                row.addView(t, tl);
                row.setOnClickListener(v -> {
                    toggle(k);
                    Panels.refresh();
                });
                if (i > 0) b.addView(Ui.hr(c));
                b.addView(row);
            }
            LinearLayout r = Ui.row(c);
            r.addView(Ui.button(c, "Start again", false, () -> {
                Prefs.put("safety_done", 0L);
                Panels.refresh();
            }));
            r.addView(Ui.gap(c, 10, 1));
            r.addView(Ui.button(c, "All done", true, () -> {
                Prefs.put("safety_done", (1L << items().length) - 1);
                Prefs.put("safety_at", System.currentTimeMillis());
                Panels.refresh();
            }));
            b.addView(r, Widgets.top(12));
            b.addView(Panels.section("Edit the checklist (one item per line)"));
            android.widget.EditText e = Widgets.field("Checklist items", String.join("\n", it), false,
                    s -> Prefs.put("safety_items", s.trim().replaceAll("\\s*\\n+\\s*", "|")));
            e.setSingleLine(false);
            e.setMinLines(4);
            b.addView(e, Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Ui.button(c, "Restore the usual list", false, () -> {
                Prefs.put("safety_items", DEFAULT);
                Prefs.put("safety_done", 0L);
                Panels.refresh();
            }), Widgets.top(8));
            return Panels.panel(String.format(Locale.US, "Safety check · %d of %d", count(), it.length), b, 620, 440);
        };
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
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
        g.setStroke(Ui.u(w), color);
        return g;
    }

    /** Round tick drawn to its own size, so it is the same crisp circle at any size. */
    static View tick(Context c, boolean on) {
        return new View(c) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path path = new android.graphics.Path();

            @Override
            protected void onDraw(android.graphics.Canvas cv) {
                float s = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f, r = s / 2f - Ui.u(1.5f);
                p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                if (on) {
                    p.setStyle(android.graphics.Paint.Style.FILL);
                    p.setColor(Ui.GREEN);
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
                    p.setStrokeWidth(Ui.u(2));
                    p.setColor(Ui.SUB);
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
            p.setColor(all ? Ui.GREEN : Ui.CARD3);
            cv.drawCircle(cx, cy, rad, p);
            if (!all && !none) {
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                p.setColor(Ui.GREEN);
                cv.drawArc(r, -90, 360f * done / Math.max(1, total), false, p);
            }
            // person: head, shoulders and body
            float u = s / 92f; // drawn on a 92-unit square, like the mockup
            int body = all ? Ui.GREEN : none ? AMBER : Ui.TEXT;
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
            p.setColor(all || !none ? Ui.GREEN : Ui.TEXT);
            p.setStrokeWidth(4 * u);
            cv.drawLine(cx + 8 * u, cy - 7 * u, cx - 12 * u, cy + 20 * u, p);
            if (all) { // tick badge
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(Ui.GREEN);
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
            LinearLayout l = Widgets.card(c, Ui.CARD);
            l.addView(Widgets.label(c, "SAFETY CHECK"));
            badge = new Badge(c);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.MATCH, 0, 1);
            bl.topMargin = Ui.u(4);
            bl.bottomMargin = Ui.u(4);
            l.addView(badge, bl); // takes the room that's left, drawn square in the middle
            status = Ui.line(c, "", 13, Ui.TEXT, true);
            status.setGravity(Gravity.CENTER);
            l.addView(status, Ui.lp(Ui.MATCH, Ui.WRAP));
            l.setOnClickListener(v -> list());
            Ui.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
            int n = items().length, k = Math.min(count(), n);
            badge.set(k, n);
            if (k >= n) {
                status.setText("Ready to drive");
                status.setTextColor(Ui.GREEN);
            } else if (k == 0) {
                status.setText("Tap to check");
                status.setTextColor(0xFFE8A33D);
            } else {
                status.setText(k + " of " + n);
                status.setTextColor(Ui.TEXT);
            }
        }
    }
}
