package com.carbox.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** A round padlock button whose shackle springs open or snaps shut. */
final class LockView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private float open; // 0 = locked, 1 = open
    private boolean locked;

    LockView(Context c) {
        super(c);
        setContentDescription("Keep the tiles open");
    }

    void set(boolean locked, boolean animate) {
        this.locked = locked;
        float to = locked ? 0 : 1;
        if (!animate) {
            open = to;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(open, to);
        a.setDuration(320);
        a.setInterpolator(new OvershootInterpolator(2.2f));
        a.addUpdateListener(v -> {
            open = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = Math.min(getWidth(), getHeight()), cx = getWidth() / 2f, cy = getHeight() / 2f, u = s / 30f;
        // button
        p.setStyle(Paint.Style.FILL);
        p.setColor(locked ? U.AMBER : 0xFF141922);
        cv.drawCircle(cx, cy, s / 2 - u, p);
        if (!locked) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(u);
            p.setColor(0x40FFFFFF);
            cv.drawCircle(cx, cy, s / 2 - u, p);
        }
        int ink = locked ? 0xFF0B0E13 : U.TEXT2;
        // body
        p.setStyle(Paint.Style.FILL);
        p.setColor(ink);
        r.set(cx - 7 * u, cy - 1 * u, cx + 7 * u, cy + 9 * u);
        cv.drawRoundRect(r, 2.5f * u, 2.5f * u, p);
        p.setColor(locked ? U.AMBER : 0xFF141922);
        cv.drawCircle(cx, cy + 4 * u, 1.6f * u, p);
        // shackle: lifts and swings open around its right foot
        cv.save();
        cv.translate(4 * u * open, -3 * u * open);
        cv.rotate(28 * open, cx + 4.5f * u, cy - 1 * u);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2.2f * u);
        p.setColor(ink);
        path.reset();
        path.moveTo(cx - 4.5f * u, cy - 1 * u);
        path.lineTo(cx - 4.5f * u, cy - 3.5f * u);
        r.set(cx - 4.5f * u, cy - 8 * u, cx + 4.5f * u, cy + 1 * u);
        path.arcTo(r, 180, 180);
        path.lineTo(cx + 4.5f * u, cy - 1 * u);
        cv.drawPath(path, p);
        cv.restore();
    }
}
