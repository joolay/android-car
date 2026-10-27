package com.carbox.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tiny UI kit. Every size is given in "design units" for an 800 x 480 screen and scaled
 * to the real display, so the layout matches the mockups whatever the box's density is.
 */
final class U {
    private U() {
    }

    static final int BG = 0xFF0B0E13;
    static final int CARD = 0xFF171C24;
    static final int CARD2 = 0xFF222A37;
    static final int CARD3 = 0xFF2E3747;
    static final int LINE = 0xFF2A3342;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT2 = 0xFFD6DBE3;
    static final int SUB = 0xFF9AA4B2;
    static final int MUTED = 0xFF5C6675;
    static final int ACCENT = 0xFF3D8BFD;
    static final int ACCENT_SOFT = 0xFF85B7EB;
    static final int GREEN = 0xFF34C759;
    static final int DGREEN = 0xFF1F7A46;
    static final int TEAL = 0xFF5DCAA5;
    static final int MINT = 0xFF9FE1CB;
    static final int AMBER = 0xFFFFB020;
    static final int RED = 0xFFE24B4A;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static float S = 1f;
    static final Handler ui = new Handler(Looper.getMainLooper());

    /** Works out the scale from the real full-screen size. */
    static void init(Activity a) {
        DisplayMetrics dm = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        int w = Math.max(dm.widthPixels, dm.heightPixels);
        int h = Math.min(dm.widthPixels, dm.heightPixels);
        S = Math.min(w / 800f, h / 480f);
        if (S <= 0) S = 1f;
    }

    static int u(float v) {
        return Math.round(v * S);
    }

    static float uf(float v) {
        return v * S;
    }

    // ------------------------------------------------------------------ drawables

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(uf(radius));
        return g;
    }

    static GradientDrawable outline(int fill, int stroke, float width, float radius) {
        GradientDrawable g = round(fill, radius);
        g.setStroke(Math.max(1, u(width)), stroke);
        return g;
    }

    static GradientDrawable dashed(int stroke, float radius) {
        GradientDrawable g = round(Color.TRANSPARENT, radius);
        g.setStroke(u(2), stroke, uf(6), uf(4));
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    // ------------------------------------------------------------------ views

    static TextView text(Context c, CharSequence s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, uf(size));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView line(Context c, CharSequence s, float size, int color, boolean bold) {
        TextView t = text(c, s, size, color, bold);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static ImageView icon(Context c, int res, int tint) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        if (tint != 0) iv.setColorFilter(tint);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    /** Size in design units (MATCH / WRAP pass through). */
    static LinearLayout.LayoutParams lpu(float w, float h) {
        return new LinearLayout.LayoutParams(w < 0 ? (int) w : u(w), h < 0 ? (int) h : u(h));
    }

    static LinearLayout.LayoutParams weight(float wgt, boolean horizontal) {
        return horizontal ? new LinearLayout.LayoutParams(0, MATCH, wgt) : new LinearLayout.LayoutParams(MATCH, 0, wgt);
    }

    static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams lp, float l, float t, float r, float b) {
        lp.setMargins(u(l), u(t), u(r), u(b));
        return lp;
    }

    static FrameLayout.LayoutParams flp(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    static FrameLayout.LayoutParams flpu(float w, float h, int gravity) {
        return new FrameLayout.LayoutParams(w < 0 ? (int) w : u(w), h < 0 ? (int) h : u(h), gravity);
    }

    static View gap(Context c, float w, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(u(w), u(h)));
        return v;
    }

    static View hr(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, Math.max(1, u(1))));
        return v;
    }

    static void pad(View v, float l, float t, float r, float b) {
        v.setPadding(u(l), u(t), u(r), u(b));
    }

    static void pad(View v, float all) {
        pad(v, all, all, all, all);
    }

    /** Rounded "segment" chip used for choices. */
    static TextView seg(Context c, String label, boolean on, Runnable click) {
        TextView t = text(c, label, 14, TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        pad(t, 14, 8, 14, 8);
        t.setBackground(round(on ? ACCENT : CARD2, 16));
        if (click != null) t.setOnClickListener(v -> click.run());
        return t;
    }

    static TextView button(Context c, String label, boolean primary, Runnable click) {
        TextView t = text(c, label, 14, TEXT, primary);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        pad(t, 18, 9, 18, 9);
        t.setBackground(round(primary ? ACCENT : CARD2, 16));
        if (click != null) t.setOnClickListener(v -> click.run());
        return t;
    }

    static void press(View v) {
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) view.animate().scaleX(.96f).scaleY(.96f).setDuration(80).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            return false;
        });
    }

    interface Cb<T> {
        void on(T value);
    }

    /** iOS-style switch. */
    static final class Toggle extends View {
        private boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private Cb<Boolean> listener;

        Toggle(Context c, boolean on, Cb<Boolean> l) {
            super(c);
            this.on = on;
            this.listener = l;
            setOnClickListener(v -> {
                set(!this.on);
                if (listener != null) listener.on(this.on);
            });
            setLayoutParams(new LinearLayout.LayoutParams(u(40), u(24)));
        }

        void set(boolean v) {
            on = v;
            invalidate();
        }

        boolean isOn() {
            return on;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(on ? ACCENT : CARD3);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setColor(on ? TEXT : SUB);
            float d = h - uf(6);
            float cx = on ? w - uf(3) - d / 2 : uf(3) + d / 2;
            c.drawCircle(cx, h / 2, d / 2, p);
        }
    }

    /** Small progress ring with an optional number in the middle. */
    static final class Ring extends View {
        private float progress;
        private String label;
        private int color = ACCENT;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Ring(Context c) {
            super(c);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(float progress, String label, int color) {
            this.progress = Math.max(0, Math.min(1, progress));
            this.label = label;
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float sw = s * 0.13f;
            r.set(sw / 2 + 1, sw / 2 + 1, s - sw / 2 - 1, s - sw / 2 - 1);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(0x26FFFFFF);
            c.drawOval(r, p);
            p.setColor(color);
            c.drawArc(r, -90, 360 * progress, false, p);
            if (label != null) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(TEXT);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(s * 0.38f);
                c.drawText(label, s / 2, s / 2 + s * 0.13f, p);
            }
        }
    }

    static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    static String hm(double hours) {
        if (Double.isNaN(hours)) return "--:--";
        int mins = (int) Math.round(((hours % 24) + 24) % 24 * 60);
        if (mins >= 1440) mins -= 1440;
        return two(mins / 60) + ":" + two(mins % 60);
    }

    static String dur(long ms) {
        long m = Math.max(0, ms / 60000);
        if (m < 60) return m + " min";
        return (m / 60) + " h " + (m % 60) + " min";
    }
}
