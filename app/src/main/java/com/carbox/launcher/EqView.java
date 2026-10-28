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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start(getContext());
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
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
        if (isShown()) postInvalidateOnAnimation();
    }
}
