package com.carbox.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Glass theme only: a light glassy sheen over the map, like looking through a magnifying glass.
 * A soft shine in the top-left, a faint dark edge, and a thin bright rim. Never takes touches.
 * Round on the Centre Stage (the map is a circle there), else it fills the small map.
 */
final class LensOverlay extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    boolean round;

    LensOverlay(Context c) {
        super(c);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (!Themes.on()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float rad = round ? (Math.min(w, h) - 12) / 2f : Math.max(w, h) * 0.75f;
        float rim = Ui.uf(2);
        int save = cv.save();
        if (round) {
            android.graphics.Path clip = new android.graphics.Path();
            clip.addCircle(cx, cy, rad, android.graphics.Path.Direction.CW);
            cv.clipPath(clip);
        }
        // glass curve: clear in the middle, a little darker and cooler toward the edge
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, rad, new int[]{0x00000000, 0x00000000, 0x2A0A1220}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        // the shine: a soft white glow high on the left, like light on a lens
        float sx = cx - rad * 0.38f, sy = cy - rad * 0.45f, sr = rad * 0.75f;
        p.setShader(new RadialGradient(sx, sy, sr, new int[]{0x38FFFFFF, 0x10FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        cv.restoreToCount(save);
        if (round) {
            // no rim here: the map page draws the round rim itself, cut away round N E S W
        } else {
            r.set(rim / 2, rim / 2, w - rim / 2, h - rim / 2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(rim);
            p.setColor(0x44FFFFFF);
            cv.drawRoundRect(r, Ui.uf(16), Ui.uf(16), p);
        }
    }
}
