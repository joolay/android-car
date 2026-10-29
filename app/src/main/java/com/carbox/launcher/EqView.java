package com.carbox.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.view.View;

/**
 * Live equaliser bars. Uses the sound actually playing (Android's visualizer on the whole output,
 * which needs the microphone permission Car Launcher already asks for voice typing); if the box
 * doesn't allow that, the bars dance to the beat-less rhythm of the music playing state instead.
 */
final class EqView extends View {
    private static Visualizer vis;
    private static int users;
    private static final float[] live = new float[64];
    private static long liveAt;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int bars, color;
    private final float[] level, peak;
    private float phase;

    EqView(Context c, int bars, int color) {
        super(c);
        this.bars = Math.min(bars, 64);
        this.color = color;
        level = new float[this.bars];
        peak = new float[this.bars];
    }

    private boolean using; // holding the shared visualizer

    // the live sound is only listened to while the animation is actually on screen and music is
    // playing, never at start-up (so it can't get in the way of the music app starting)
    private void use(boolean on) {
        if (on == using) return;
        using = on;
        if (on) start(getContext());
        else stop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        use(false);
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (!visible) use(false);
        else postInvalidateOnAnimation();
    }

    private static synchronized void start(Context c) {
        users++;
        if (vis != null) return;
        if (c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            vis = new Visualizer(0);
            vis.setEnabled(false);
            vis.setCaptureSize(Math.min(512, Visualizer.getCaptureSizeRange()[1]));
            vis.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] w, int rate) {
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                    int n = fft.length / 2;
                    for (int b = 0; b < 64; b++) {
                        // log-spaced bands: more detail in the low end, like a car stereo display
                        int from = (int) Math.max(1, Math.pow(n, b / 64f)), to = (int) Math.max(from + 1, Math.pow(n, (b + 1) / 64f));
                        float m = 0;
                        for (int k = from; k < to && k < n; k++) {
                            float re = fft[2 * k], im = fft[2 * k + 1];
                            m = Math.max(m, (float) Math.hypot(re, im));
                        }
                        live[b] = Math.min(1f, (float) (Math.log10(1 + m) / 2.2));
                    }
                    liveAt = System.currentTimeMillis();
                }
            }, Math.min(Visualizer.getMaxCaptureRate(), 20000), false, true);
            vis.setEnabled(true);
        } catch (Throwable t) {
            vis = null; // not allowed on this box: fall back to the gentle animation
        }
    }

    private static synchronized void stop() {
        users = Math.max(0, users - 1);
        if (users == 0 && vis != null) {
            try {
                vis.setEnabled(false);
                vis.release();
            } catch (Throwable ignored) {
            }
            vis = null;
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        boolean playing = MediaListener.playing();
        use(playing && isShown() && android.os.SystemClock.elapsedRealtime() > 60000); // not in the first minute after boot
        boolean real = System.currentTimeMillis() - liveAt < 500;
        phase += 0.09f;
        for (int i = 0; i < bars; i++) {
            float target;
            if (!playing) target = 0.04f;
            else if (real) target = live[Math.min(63, i * 64 / bars)];
            else { // no live sound: layered waves so it still feels like music
                double x = i / (double) bars;
                target = (float) (0.35 + 0.25 * Math.sin(phase * 1.7 + x * 9) * Math.sin(phase * 0.6 + x * 3.1)
                        + 0.2 * Math.sin(phase * 3.3 + i * 1.3) * (1 - x * 0.6));
                target = Math.max(0.06f, Math.min(1f, target));
            }
            level[i] += (target - level[i]) * (target > level[i] ? 0.55f : 0.18f);
            peak[i] = Math.max(level[i], peak[i] - 0.012f);
        }
        String style = style();
        if ("wave".equals(style)) {
            drawWave(cv, w, h);
        } else if ("pulse".equals(style)) {
            drawPulse(cv, w, h);
        } else drawBars(cv, w, h);
        if (isShown()) postInvalidateOnAnimation();
        else use(false);
    }

    /** "bars" (equaliser), "wave" (flowing wave) or "pulse" (rings that beat with the music). */
    static String style() {
        return Store.str("eq_style", "bars");
    }

    private final android.graphics.Path wave = new android.graphics.Path();

    private void drawWave(Canvas cv, float w, float h) {
        // two layered waves through the band levels: a soft filled one and a brighter line on top
        for (int layer = 0; layer < 2; layer++) {
            wave.reset();
            float mid = h * 0.62f;
            wave.moveTo(0, h);
            for (int x = 0; x <= 48; x++) {
                float fx = x / 48f;
                float lv = level[Math.min(bars - 1, (int) (fx * bars))];
                float y = mid - (lv * h * 0.55f) * (float) Math.sin(fx * Math.PI * (layer == 0 ? 3 : 5) + phase * (layer == 0 ? 1.4f : -2.1f))
                        - lv * h * 0.12f;
                wave.lineTo(fx * w, Math.max(0, Math.min(h, y)));
            }
            if (layer == 0) {
                wave.lineTo(w, h);
                wave.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor((color & 0x00FFFFFF) | 0x33000000);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(U.uf(2.5f));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(color);
            }
            cv.drawPath(wave, p);
        }
    }

    private float pulseLevel;

    private void drawPulse(Canvas cv, float w, float h) {
        // overall loudness (weighted to the bass) drives rings spreading from the centre
        float lv = 0;
        for (int i = 0; i < bars; i++) lv += level[i] * (i < bars / 3 ? 2f : 1f);
        lv /= bars * 1.33f;
        pulseLevel += (lv - pulseLevel) * 0.3f;
        float cx = w / 2, cy = h / 2, max = Math.min(w, h) / 2;
        p.setStyle(Paint.Style.STROKE);
        for (int k = 0; k < 3; k++) {
            float t = ((phase * 0.35f + k / 3f) % 1f); // rings keep spreading outwards
            float r = max * (0.25f + 0.75f * t) * (0.7f + 0.5f * pulseLevel);
            p.setStrokeWidth(U.uf(2) + U.uf(4) * pulseLevel * (1 - t));
            int a = (int) (200 * (1 - t) * (0.3f + pulseLevel));
            p.setColor((color & 0x00FFFFFF) | (Math.min(255, a) << 24));
            cv.drawCircle(cx, cy, r, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        cv.drawCircle(cx, cy, max * (0.12f + 0.22f * pulseLevel), p);
    }

    private void drawBars(Canvas cv, float w, float h) {
        float gap = w / bars * 0.28f, bw = w / bars - gap;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < bars; i++) {
            float x = i * (bw + gap) + gap / 2, bh = Math.max(U.uf(2), level[i] * h);
            p.setColor(color);
            cv.drawRoundRect(x, h - bh, x + bw, h, bw / 3, bw / 3, p);
            p.setColor((color & 0x00FFFFFF) | 0xCC000000);
            float py = h - peak[i] * h - U.uf(3);
            cv.drawRect(x, py, x + bw, py + U.uf(2), p);
        }
    }
}
