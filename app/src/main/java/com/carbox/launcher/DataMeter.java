package com.carbox.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.TrafficStats;
import android.os.SystemClock;
import android.view.View;

/**
 * Rail Bar data meter: a small gauge of how fast data is moving right now (down, with up as a thin
 * inner arc), the needle easing to each new reading, and the speed underneath.
 */
final class DataMeter extends View {
    /** Bytes per second right now, and totals since the box started. */
    static long down, up;
    private static long lastRx = -1, lastTx = -1, lastAt;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG),
            inner = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), r2 = new RectF();
    private float shownDown, shownUp; // 0..1 on the gauge, eased
    private boolean running;

    DataMeter(Context c) {
        super(c);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(U.LINE);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(U.ACCENT);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeCap(Paint.Cap.ROUND);
        inner.setColor(U.AMBER);
        text.setColor(U.TEXT);
        text.setFakeBoldText(true);
        text.setTextAlign(Paint.Align.CENTER);
        setContentDescription("Data meter");
    }

    /** Read the counters (once a second is plenty). */
    static void sample() {
        long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes(), now = SystemClock.elapsedRealtime();
        if (rx < 0 || tx < 0) return; // not supported on this box
        if (lastRx >= 0 && now > lastAt) {
            float s = (now - lastAt) / 1000f;
            down = Math.max(0, Math.round((rx - lastRx) / s));
            up = Math.max(0, Math.round((tx - lastTx) / s));
        }
        lastRx = rx;
        lastTx = tx;
        lastAt = now;
    }

    static long totalDown() {
        return Math.max(0, TrafficStats.getTotalRxBytes());
    }

    static long totalUp() {
        return Math.max(0, TrafficStats.getTotalTxBytes());
    }

    /** 1.2M, 340K, 0 (bytes per second, short for the rail). */
    static String rate(long b) {
        if (b < 1000) return b < 50 ? "0" : String.valueOf(b);
        if (b < 1000_000) return Math.round(b / 1000f) + "K";
        return String.format(java.util.Locale.US, b < 10_000_000 ? "%.1fM" : "%.0fM", b / 1_000_000f);
    }

    /** Short amount for the rail: 850K, 12M, 1.4G. */
    static String used(long b) {
        if (b < 1_000_000) return Math.round(b / 1000f) + "K";
        if (b < 1_000_000_000) return Math.round(b / 1_000_000f) + "M";
        return String.format(java.util.Locale.US, "%.1fG", b / 1_000_000_000f);
    }

    static String size(long b) {
        if (b < 1_000_000) return Math.round(b / 1000f) + " KB";
        if (b < 1_000_000_000) return Math.round(b / 1_000_000f) + " MB";
        return String.format(java.util.Locale.US, "%.2f GB", b / 1_000_000_000f);
    }

    /** Where a speed sits on the gauge: log scale, 10 KB/s to 10 MB/s fills it. */
    private static float level(long b) {
        if (b <= 1000) return 0f;
        return (float) Math.max(0, Math.min(1, (Math.log10(b) - 3) / 4.0));
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            sample();
            invalidate();
            if (running) postDelayed(this, 1000);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float sw = U.uf(4);
        float rad = Math.min(w / 2f - sw, h * 0.62f);
        float cx = w / 2f, cy = rad + sw;
        r.set(cx - rad, cy - rad, cx + rad, cy + rad);
        float rad2 = rad - sw * 1.6f;
        r2.set(cx - rad2, cy - rad2, cx + rad2, cy + rad2);
        track.setStrokeWidth(sw);
        arc.setStrokeWidth(sw);
        inner.setStrokeWidth(sw * 0.55f);
        // a 240° gauge, open at the bottom
        cv.drawArc(r, 150, 240, false, track);
        // ease the needle towards the reading (animations off: jump)
        float tD = level(down), tU = level(up);
        boolean anim = U.ms(1) > 0;
        shownDown = anim ? shownDown + (tD - shownDown) * 0.25f : tD;
        shownUp = anim ? shownUp + (tU - shownUp) * 0.25f : tU;
        if (shownDown > 0.005f) cv.drawArc(r, 150, 240 * shownDown, false, arc);
        if (shownUp > 0.005f) cv.drawArc(r2, 150, 240 * shownUp, false, inner);
        // the data actually used since the box started: down inside the gauge, up underneath
        text.setTextSize(U.uf(10));
        text.setColor(U.ACCENT_SOFT);
        cv.drawText("↓" + used(totalDown()), cx, cy + U.uf(3), text);
        text.setColor(U.AMBER);
        cv.drawText("↑" + used(totalUp()), cx, cy + rad + U.uf(7), text);
        // keep easing until it settles
        if (anim && (Math.abs(tD - shownDown) > 0.005f || Math.abs(tU - shownUp) > 0.005f)) postInvalidateOnAnimation();
    }
}
