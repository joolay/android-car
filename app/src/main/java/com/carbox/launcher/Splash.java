package com.carbox.launcher;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen travel prayer (or your own text) shown when the car starts. Tap to close. */
final class Splash {
    private Splash() {
    }

    private static FrameLayout shown;
    private static U.Ring ring;
    private static long endsAt, startedAt;

    static void maybeShow(Activity a, String trigger) {
        if (!Store.flag(Store.SPLASH_ON, false)) return;
        String when = Store.str(Store.SPLASH_WHEN, "start");
        if ("nav".equals(when) != "nav".equals(trigger)) return;
        if ("day".equals(when) && "start".equals(trigger)) {
            String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
            if (today.equals(Store.str(Store.SPLASH_LAST_DAY, ""))) return;
            Store.put(Store.SPLASH_LAST_DAY, today);
        }
        if ("start".equals(trigger)) {
            // once per car start (boot), not every time the launcher reopens
            long bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
            if (Math.abs(Store.lng("splash_boot", 0) - bootAt) < 60000) return;
            Store.put("splash_boot", bootAt);
        }
        show(a);
    }

    static void show(Activity a) {
        dismiss();
        View content = build(a, Store.str(Store.PRAYER_LAYOUT, "stacked"));
        if (content == null) return;
        FrameLayout layer = new FrameLayout(a);
        layer.setClickable(true);
        layer.setOnClickListener(v -> dismiss());
        layer.addView(content, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        ViewGroup root = a.findViewById(android.R.id.content);
        root.addView(layer, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        layer.setAlpha(0f);
        layer.animate().alpha(1f).setDuration(300).start();
        shown = layer;
        int secs = Store.num(Store.SPLASH_SECS, 10);
        startedAt = SystemClock.elapsedRealtime();
        endsAt = secs <= 0 ? 0 : startedAt + secs * 1000L;
        U.ui.post(countdown);
    }

    private static final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            if (shown == null) return;
            long now = SystemClock.elapsedRealtime();
            if (endsAt != 0 && now >= endsAt) {
                dismiss();
                return;
            }
            if (ring != null && endsAt != 0) {
                long left = endsAt - now;
                ring.set(1f - left / (float) (endsAt - startedAt), String.valueOf((left + 999) / 1000), U.TEAL);
            }
            U.ui.postDelayed(this, 100);
        }
    };

    static boolean dismiss() {
        if (shown == null) return false;
        final FrameLayout s = shown;
        shown = null;
        ring = null;
        U.ui.removeCallbacks(countdown);
        s.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            if (s.getParent() != null) ((ViewGroup) s.getParent()).removeView(s);
        }).start();
        return true;
    }

    private static U.Ring newRing(Context c) {
        U.Ring r = new U.Ring(c);
        int secs = Store.num(Store.SPLASH_SECS, 10);
        r.set(0, secs > 0 ? String.valueOf(secs) : null, U.TEAL);
        if (secs <= 0) r.setVisibility(View.GONE);
        ring = r;
        return r;
    }

    /** layout: stacked | split | card. Returns null if nothing is set up to show. */
    static View build(Context c, String layout) {
        boolean custom = "custom".equals(Store.str(Store.SPLASH_TYPE, "prayer"));
        Prayers.P p = custom ? null : Prayers.get(Store.str(Store.PRAYER_ID, null));
        String main, sub, orig = null, tl = null, label;
        boolean rtl = false;
        int show = Store.num(Store.PRAYER_SHOW, 7);
        if (custom) {
            main = Store.str(Store.CUSTOM_TEXT, "");
            if (main.isEmpty()) return null;
            sub = Store.str(Store.CUSTOM_SUB, "");
            label = "";
        } else {
            if (p == null) return null;
            orig = (show & 1) != 0 ? p.orig : null;
            tl = (show & 2) != 0 ? p.translit : null;
            main = (show & 4) != 0 || orig == null ? p.english : null;
            sub = p.source;
            rtl = p.rtl;
            label = "TRAVEL PRAYER · " + p.faith.toUpperCase(Locale.ROOT);
        }
        if ("split".equals(layout) && orig != null) return split(c, label, orig, tl, main, sub);
        if ("card".equals(layout)) return card(c, label, orig, tl, main, sub, rtl);
        return stacked(c, label, orig, tl, main, sub, rtl, true);
    }

    private static LinearLayout topRow(Context c, String label) {
        LinearLayout top = U.row(c);
        TextView l = U.line(c, label, 13, U.TEAL, true);
        l.setLetterSpacing(0.08f);
        top.addView(l, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        top.addView(newRing(c), U.lpu(34, 34));
        return top;
    }

    private static View stacked(Context c, String label, String orig, String tl, String main, String sub, boolean rtl, boolean full) {
        LinearLayout l = U.col(c);
        if (full) l.setBackgroundColor(0xFF05080C);
        U.pad(l, 44, 34, 44, 34);
        l.addView(topRow(c, label));
        LinearLayout mid = U.col(c);
        mid.setGravity(Gravity.CENTER);
        if (orig != null) {
            TextView o = U.text(c, orig, orig.length() < 40 ? 56 : 46, U.TEXT, false);
            o.setGravity(Gravity.CENTER);
            o.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
            o.setLineSpacing(U.uf(8), 1);
            mid.addView(o, U.lp(U.MATCH, U.WRAP));
        }
        if (tl != null) {
            TextView t = U.text(c, tl, 17, U.SUB, false);
            t.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC));
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl2 = U.lp(U.MATCH, U.WRAP);
            tl2.topMargin = U.u(orig != null ? 18 : 0);
            mid.addView(t, tl2);
        }
        if (main != null) {
            TextView m = U.text(c, main, orig == null ? 32 : 25, U.TEXT2, false);
            m.setGravity(Gravity.CENTER);
            m.setLineSpacing(U.uf(5), 1);
            LinearLayout.LayoutParams ml = U.lp(U.MATCH, U.WRAP);
            ml.topMargin = U.u(orig != null || tl != null ? 18 : 0);
            mid.addView(m, ml);
        }
        l.addView(mid, U.weight(1, false));
        if (sub != null && !sub.isEmpty()) {
            TextView s = U.text(c, sub, 13, U.MUTED, false);
            s.setGravity(Gravity.CENTER);
            l.addView(s, U.lp(U.MATCH, U.WRAP));
        }
        return l;
    }

    private static View split(Context c, String label, String orig, String tl, String main, String sub) {
        LinearLayout l = U.row(c);
        l.setBackgroundColor(0xFF05080C);
        LinearLayout left = U.col(c);
        left.setGravity(Gravity.CENTER);
        left.setBackgroundColor(0xFF0F3D26);
        U.pad(left, 34, 30, 34, 30);
        TextView o = U.text(c, orig, orig.length() < 40 ? 50 : 40, U.TEXT, false);
        o.setGravity(Gravity.CENTER);
        o.setTextDirection(View.TEXT_DIRECTION_RTL);
        o.setLineSpacing(U.uf(8), 1);
        left.addView(o, U.lp(U.MATCH, U.WRAP));
        l.addView(left, U.weight(1, true));
        LinearLayout right = U.col(c);
        U.pad(right, 34, 30, 40, 30);
        right.addView(topRow(c, label));
        LinearLayout mid = U.col(c);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        if (main != null) {
            TextView m = U.text(c, main, 24, U.TEXT, false);
            m.setLineSpacing(U.uf(5), 1);
            mid.addView(m);
        }
        if (tl != null) {
            TextView t = U.text(c, tl, 15, U.SUB, false);
            t.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC));
            LinearLayout.LayoutParams tll = U.lp(U.MATCH, U.WRAP);
            tll.topMargin = U.u(16);
            mid.addView(t, tll);
        }
        right.addView(mid, U.weight(1, false));
        if (sub != null) right.addView(U.text(c, sub, 13, U.MUTED, false));
        l.addView(right, U.weight(1, true));
        return l;
    }

    private static View card(Context c, String label, String orig, String tl, String main, String sub, boolean rtl) {
        FrameLayout f = new FrameLayout(c);
        f.setBackgroundColor(0xB3000000);
        View inner = stacked(c, label, orig, tl, main, sub, rtl, false);
        inner.setBackground(U.round(U.CARD, 24));
        U.pad(inner, 34, 26, 34, 26);
        // shrink fonts slightly for the card
        f.addView(inner, Sheets.centered(640, 380));
        return f;
    }
}
