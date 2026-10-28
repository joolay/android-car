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
                row.addView(tick(c, on), U.lpu(26, 26));
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

    static View tick(Context c, boolean on) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        f.setBackground(on ? U.oval(U.GREEN) : ring(U.SUB, 2));
        if (on) f.addView(U.text(c, "✓", 15, U.TEXT, true), U.flpu(U.WRAP, U.WRAP, Gravity.CENTER));
        return f;
    }

    static final class Tile extends Widgets.W {
        private final TextView head, item;
        private final LinearLayout bar;
        private final android.widget.FrameLayout box;

        Tile(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = Widgets.card(c, U.CARD);
            head = Widgets.label(c, "SAFETY CHECK");
            l.addView(head);
            bar = U.row(c);
            l.addView(bar, Widgets.top(6));
            l.addView(new View(c), U.weight(1, false));
            LinearLayout r = U.row(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            box = new android.widget.FrameLayout(c);
            box.setOnClickListener(v -> {
                int n = next();
                if (n >= 0) toggle(n);
                tick();
            });
            U.press(box);
            r.addView(box, U.lpu(34, 34));
            item = U.text(c, "", 15, U.TEXT, true);
            item.setMaxLines(2);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            il.leftMargin = U.u(10);
            r.addView(item, il);
            l.addView(r);
            l.addView(new View(c), U.weight(1, false));
            l.setOnClickListener(v -> list());
            U.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
            String[] it = items();
            int n = next(), k = count();
            head.setText(String.format(Locale.US, "SAFETY CHECK · %d/%d", k, it.length));
            bar.removeAllViews();
            long d = done();
            for (int i = 0; i < it.length; i++) {
                View seg = new View(c);
                seg.setBackground(U.round((d & (1L << i)) != 0 ? U.GREEN : U.CARD3, 2));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, U.u(4), 1);
                if (i > 0) sl.leftMargin = U.u(3);
                bar.addView(seg, sl);
            }
            box.removeAllViews();
            if (n < 0) {
                box.setBackground(U.oval(U.GREEN));
                box.addView(U.text(c, "✓", 18, U.TEXT, true), U.flpu(U.WRAP, U.WRAP, Gravity.CENTER));
                item.setText("All checked. Drive safely");
            } else {
                box.setBackground(ring(U.TEXT2, 2));
                item.setText(it[n]);
            }
        }
    }
}
