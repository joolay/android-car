package com.carbox.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Animated, text-free status icons for the top bar. */
final class StatusIcons {

    static final int C_ON = 0xFFFFFFFF;
    static final int C_DIM = 0xFF2E3747;
    static final int C_OFF = 0xFF5C6675;
    static final int C_GREEN = 0xFF34C759;
    static final int C_AMBER = 0xFFFFB020;

    private StatusIcons() {
    }

    /** Base: a looping 0..1 phase that only runs while the icon needs to move. */
    abstract static class AnimatedIcon extends View {
        protected float phase;
        private final ValueAnimator loop;
        private boolean wantAnim;
        private boolean paused;

        AnimatedIcon(Context c, long periodMs) {
            super(c);
            loop = ValueAnimator.ofFloat(0f, 1f);
            loop.setDuration(periodMs);
            loop.setRepeatCount(ValueAnimator.INFINITE);
            loop.setInterpolator(new LinearInterpolator());
            loop.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
        }

        protected void setAnimating(boolean on) {
            wantAnim = on;
            sync();
        }

        /** Called when the home screen goes to the background, to save battery. */
        void setPaused(boolean p) {
            paused = p;
            sync();
        }

        private void sync() {
            boolean run = wantAnim && !paused && isAttachedToWindow();
            if (run && !loop.isStarted()) loop.start();
            else if (!run && loop.isStarted()) {
                loop.cancel();
                phase = 0f;
                invalidate();
            }
        }

        // live speed (0..1, how fast data is moving on this connection): the lit bars or arcs turn green by this much
        protected float speedT;
        private int speedKind = -1;
        private final Runnable speedTick = new Runnable() {
            @Override
            public void run() {
                if (speedKind < 0 || !isAttachedToWindow()) return;
                DataMeter.sample();
                float t = DataMeter.level(DataMeter.speed[speedKind][0] + DataMeter.speed[speedKind][1]);
                float was = speedT;
                speedT = t > speedT ? speedT + (t - speedT) * 0.7f : speedT + (t - speedT) * 0.35f; // quick up, easy down
                if (speedT < 0.02f) speedT = 0f;
                if (Math.abs(was - speedT) > 0.005f) invalidate();
                postDelayed(this, 1000);
            }
        };

        /** Colour this icon's signal by the speed of the given DataMeter connection (LTE, WIFI, HOTSPOT). */
        protected void trackSpeed(int kind) {
            // no speed colouring on the rail icons any more: the data meter's arrow shows download activity
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            sync();
            if (speedKind >= 0) post(speedTick);
        }

        @Override
        protected void onDetachedFromWindow() {
            loop.cancel();
            removeCallbacks(speedTick);
            super.onDetachedFromWindow();
        }

        /** Scale factor for drawing in a 24x24 design grid centred in the view's height. */
        protected float unit() {
            return getHeight() / 24f;
        }

        protected static Paint fill(int color) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            return p;
        }

        protected static Paint stroke(int color) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            return p;
        }
    }

    // ------------------------------------------------------------------ mobile signal

    /**
     * Four bars with the network type (LTE, 5G, H+, …) beside them.
     * Type is white when mobile data is connected, grey when it isn't.
     * No SIM = dim bars with a slash; SIM but no signal = a scanning sweep.
     */
    static final class CellIcon extends AnimatedIcon {
        private final Paint on = fill(C_ON), dim = fill(C_DIM), slash = stroke(C_OFF), scan = fill(0x6EFFFFFF);
        private final RectF r = new RectF(), part = new RectF(), green = new RectF();
        private final Paint speedPaint = fill(C_GREEN);
        private final Paint type = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int level;
        private boolean noSim = true;
        private String netType;
        private boolean dataOn;
        private float shown;            // animated level for smooth bar growth
        private ValueAnimator grow;

        CellIcon(Context c) {
            super(c, 1400);
            trackSpeed(DataMeter.LTE);
            type.setTypeface(Typeface.DEFAULT_BOLD);
            type.setTextAlign(Paint.Align.LEFT);
        }

        void setState(boolean noSim, int level, String netType, boolean dataOn) {
            level = Math.max(0, Math.min(4, level));
            if (noSim) netType = null;
            boolean typeChanged = netType == null ? this.netType != null : !netType.equals(this.netType);
            if (typeChanged || dataOn != this.dataOn) {
                this.netType = netType;
                this.dataOn = dataOn;
                type.setColor(dataOn ? C_ON : C_OFF);
                if (typeChanged) requestLayout();
                invalidate();
            }
            boolean changed = noSim != this.noSim || level != this.level;
            this.noSim = noSim;
            this.level = level;
            setAnimating(!noSim && level == 0);
            if (!changed) return;
            if (grow != null) grow.cancel();
            grow = ValueAnimator.ofFloat(shown, noSim ? 0 : level);
            grow.setDuration(Ui.ms(450));
            grow.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            grow.start();
        }

        private float typeWidth(float u) {
            if (netType == null) return 0f;
            type.setTextSize(11 * u);
            return type.measureText(netType) + 2 * u;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int h = MeasureSpec.getSize(hSpec);
            float u = h / 24f;
            setMeasuredDimension(Math.round(24 * u), h); // LTE / 5G sits inside, over the short bars
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = unit();
            float h = 24 * u;
            if (netType != null) { // small, top left, above the two short bars
                type.setTextSize(7.5f * u);
                c.drawText(netType, 0.5f * u, 7f * u, type);
            }
            float left0 = 0;
            float bw = 4.2f * u, gap = 1.6f * u, base = h - 3 * u;
            int sweep = (int) (phase * 5) % 5; // 0..4, 4 = all dark pause
            for (int i = 0; i < 4; i++) {
                float bh = (i + 1) * 4.5f * u;
                float l = left0 + 1.5f * u + i * (bw + gap);
                r.set(l, base - bh, l + bw, base);
                float fillAmount = Math.max(0f, Math.min(1f, shown - i));
                c.drawRoundRect(r, 1.2f * u, 1.2f * u, dim);
                if (!noSim && level == 0) {
                    if (i <= sweep && sweep < 4) c.drawRoundRect(r, 1.2f * u, 1.2f * u, scan);
                } else if (fillAmount > 0f) {
                    part.set(r.left, r.bottom - r.height() * fillAmount, r.right, r.bottom);
                    c.drawRoundRect(part, 1.2f * u, 1.2f * u, on);
                    // speed: the white bars turn green from the first bar up, as far as data is moving
                    float g = Math.max(0f, Math.min(1f, speedT * Math.min(4, level) - i));
                    if (g > 0f) {
                        green.set(part.left, part.bottom - part.height() * g, part.right, part.bottom);
                        c.drawRoundRect(green, 1.2f * u, 1.2f * u, speedPaint);
                    }
                }
            }
            if (noSim) {
                slash.setStrokeWidth(2.2f * u);
                c.drawLine(left0 + 2 * u, 4 * u, left0 + 22 * u, base, slash);
            }
        }
    }

    // ------------------------------------------------------------------ wi-fi

    /** Dot + three arcs. Off = grey with slash; on but not connected = arcs pulse outward. */
    static final class WifiIcon extends AnimatedIcon {
        static final int OFF = 0, SCANNING = 1, CONNECTED = 2;
        private final Paint on = stroke(C_ON), dim = stroke(C_DIM), dotOn = fill(C_ON), dotDim = fill(C_DIM);
        private final Paint slash = stroke(C_OFF);
        private final Paint speedStroke = stroke(C_GREEN), speedDot = fill(C_GREEN);
        private final RectF r = new RectF();
        private int state = OFF;
        private int arcs;               // 0..3 lit arcs when connected

        WifiIcon(Context c) {
            super(c, 1500);
            trackSpeed(DataMeter.WIFI);
        }

        void setState(int state, int level0to4) {
            int lit = level0to4 >= 4 ? 3 : level0to4 >= 3 ? 2 : level0to4 >= 2 ? 1 : 0;
            if (state == this.state && lit == arcs) return;
            this.state = state;
            this.arcs = lit;
            setAnimating(state == SCANNING);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = unit();
            float cx = getWidth() / 2f, cy = 20 * u;
            float sw = 2.4f * u;
            on.setStrokeWidth(sw);
            dim.setStrokeWidth(sw);
            int scanStep = (int) (phase * 4); // 0..3
            for (int i = 0; i < 3; i++) {
                float rad = (5.5f + i * 4.5f) * u;
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                boolean lit;
                if (state == CONNECTED) lit = i < arcs;
                else if (state == SCANNING) lit = i < scanStep;
                else lit = false;
                c.drawArc(r, 225, 90, false, lit ? on : dim);
                if (lit && state == CONNECTED) { // speed: the dot, then each arc outward, turns green
                    float g = Math.max(0f, Math.min(1f, speedT * (arcs + 1) - (i + 1)));
                    if (g > 0f) {
                        speedStroke.setStrokeWidth(sw);
                        speedStroke.setAlpha(Math.round(255 * g));
                        c.drawArc(r, 225, 90, false, speedStroke);
                    }
                }
            }
            boolean dotLit = state == CONNECTED || (state == SCANNING && scanStep > 0);
            c.drawCircle(cx, cy, 1.9f * u, dotLit ? dotOn : dotDim);
            if (state == CONNECTED) {
                float g = Math.max(0f, Math.min(1f, speedT * (arcs + 1)));
                if (g > 0f) {
                    speedDot.setAlpha(Math.round(255 * g));
                    c.drawCircle(cx, cy, 1.9f * u, speedDot);
                }
            }
            if (state == OFF) {
                slash.setStrokeWidth(2.2f * u);
                c.drawLine(cx - 10 * u, 4 * u, cx + 10 * u, 21 * u, slash);
            }
        }
    }

    // ------------------------------------------------------------------ hotspot

    /** Dot and two rings. Off = grey; on = white; the dot and rings turn green as hotspot data moves. */
    static final class HotspotIcon extends AnimatedIcon {
        private final Paint on = stroke(C_ON), off = stroke(C_OFF), dotOn = fill(C_ON), dotOff = fill(C_OFF);
        private final Paint gStroke = stroke(C_GREEN), gDot = fill(C_GREEN);
        private final RectF r = new RectF();
        private boolean isOn;

        HotspotIcon(Context c) {
            super(c, 1500);
            trackSpeed(DataMeter.HOTSPOT);
        }

        void setOn(boolean v) {
            if (v == isOn) return;
            isOn = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = unit();
            float cx = getWidth() / 2f, cy = 13 * u, sw = 2.2f * u;
            on.setStrokeWidth(sw);
            off.setStrokeWidth(sw);
            gStroke.setStrokeWidth(sw);
            for (int i = 0; i < 2; i++) {
                float rad = (5f + i * 4.6f) * u;
                r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                c.drawArc(r, 120, 300, false, isOn ? on : off); // open at the bottom
                float g = isOn ? Math.max(0f, Math.min(1f, speedT * 3 - (i + 1))) : 0f;
                if (g > 0f) {
                    gStroke.setAlpha(Math.round(255 * g));
                    c.drawArc(r, 120, 300, false, gStroke);
                }
            }
            c.drawCircle(cx, cy, 2f * u, isOn ? dotOn : dotOff);
            float g = isOn ? Math.max(0f, Math.min(1f, speedT * 3)) : 0f;
            if (g > 0f) {
                gDot.setAlpha(Math.round(255 * g));
                c.drawCircle(cx, cy, 2f * u, gDot);
            }
        }
    }

    // ------------------------------------------------------------------ gps

    /**
     * Satellite with a count beside it.
     * Fix = green, signal waves stream out. Searching = amber, satellite rocks and waves blink.
     * Off = grey with a slash.
     */
    static final class GpsIcon extends AnimatedIcon {
        static final int OFF = 0, SEARCHING = 1, FIX = 2;
        private final Paint body = fill(C_OFF), wave = stroke(C_OFF), slash = stroke(C_OFF);
        private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int state = -1;
        private int count = -1;

        GpsIcon(Context c) {
            super(c, 1600);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setTextAlign(Paint.Align.LEFT);
        }

        void setState(int state, int satellites) {
            if (state == this.state && satellites == count) return;
            boolean stateChanged = state != this.state;
            this.state = state;
            this.count = satellites;
            if (stateChanged) {
                int color = state == FIX ? C_GREEN : state == SEARCHING ? C_AMBER : C_OFF;
                body.setColor(color);
                wave.setColor(color);
                num.setColor(state == OFF ? C_OFF : C_ON);
                setAnimating(state != OFF);
            }
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int h = MeasureSpec.getSize(hSpec);
            int w = h + (count >= 0 && state != OFF ? Math.round(h * 0.75f) : 0);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas c) {
            float u = unit();
            float cx = 13 * u, cy = 11 * u;

            // Signal waves toward the bottom-left (towards "earth")
            if (state != OFF) {
                wave.setStrokeWidth(1.8f * u);
                for (int i = 0; i < 3; i++) {
                    float rad;
                    int alpha;
                    if (state == FIX) {
                        float t = (phase + i / 3f) % 1f;          // continuous outward stream
                        rad = (5f + t * 8f) * u;
                        alpha = (int) (255 * (1f - t));
                    } else {
                        rad = (6f + i * 3f) * u;                   // blink one by one
                        int step = (int) (phase * 4);
                        alpha = i < step ? 220 : 40;
                    }
                    wave.setAlpha(alpha);
                    r.set(cx - rad, cy - rad, cx + rad, cy + rad);
                    c.drawArc(r, 100, 70, false, wave);
                }
                wave.setAlpha(255);
            }

            // Satellite body + panels, rotated 45° (rocks gently while searching)
            float angle = 45f;
            if (state == SEARCHING) angle += (float) Math.sin(phase * 2 * Math.PI) * 12f;
            c.save();
            c.rotate(angle, cx, cy);
            r.set(cx - 3 * u, cy - 2.6f * u, cx + 3 * u, cy + 2.6f * u);
            c.drawRoundRect(r, 1 * u, 1 * u, body);                          // body
            r.set(cx - 11 * u, cy - 2.2f * u, cx - 5 * u, cy + 2.2f * u);
            c.drawRoundRect(r, 0.6f * u, 0.6f * u, body);                    // left panel
            r.set(cx + 5 * u, cy - 2.2f * u, cx + 11 * u, cy + 2.2f * u);
            c.drawRoundRect(r, 0.6f * u, 0.6f * u, body);                    // right panel
            r.set(cx - 5.2f * u, cy - 0.5f * u, cx + 5.2f * u, cy + 0.5f * u);
            c.drawRect(r, body);                                             // struts
            c.restore();

            if (state == OFF) {
                slash.setStrokeWidth(2.2f * u);
                c.drawLine(2 * u, 3 * u, 22 * u, 21 * u, slash);
            } else if (count >= 0) {
                num.setTextSize(13 * u);
                c.drawText(String.valueOf(count), 24 * u, 21 * u, num);
            }
        }
    }
}
