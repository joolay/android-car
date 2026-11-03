package com.carbox.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The Road wallpaper's live sky, drawn just above it and under everything else: drifting clouds when it's
 * cloudy, rain streaks when it rains, a flash now and then in a storm, twinkling stars on a clear night.
 * Gentle and slow (about 20 frames a second) so it doesn't distract or load the box; still with animations off.
 */
final class SkyFx extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(7);
    private final float[][] stars = new float[60][3], clouds = new float[6][4], drops = new float[70][3];
    private Themes.Sky sky;
    private long flashAt, last;
    private boolean on;

    SkyFx(Context c) {
        super(c);
        for (float[] s : stars) {
            s[0] = rnd.nextFloat();
            s[1] = rnd.nextFloat() * 0.42f;
            s[2] = rnd.nextFloat() * 6.28f;
        }
        for (float[] k : clouds) {
            k[0] = rnd.nextFloat();
            k[1] = 0.05f + rnd.nextFloat() * 0.25f;
            k[2] = 0.12f + rnd.nextFloat() * 0.16f; // size
            k[3] = 0.004f + rnd.nextFloat() * 0.006f; // speed (screen widths a second)
        }
        for (float[] d : drops) {
            d[0] = rnd.nextFloat();
            d[1] = rnd.nextFloat();
            d[2] = 0.6f + rnd.nextFloat() * 0.6f;
        }
    }

    /** The sky changed (time of day, weather): draw for it. */
    void update(Themes.Sky k) {
        sky = k;
        invalidate();
    }

    void run(boolean r) {
        on = r;
        last = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (sky == null || !"road".equals(Prefs.str(Prefs.WALL, "road"))) return;
        int w = getWidth(), h = getHeight();
        float hz = h * 0.46f;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.2f, (now - last) / 1000f);
        last = now;
        boolean anim = on && Ui.ms(1) > 0;
        if (!anim) dt = 0;
        float t = now / 1000f;
        // stars on a clear (or lightly cloudy) night
        if (sky.night && sky.weather <= 1) {
            for (float[] s : stars) {
                float a = 0.35f + 0.35f * (float) Math.sin(t * 1.3f + s[2]);
                p.setColor(((int) (a * (sky.weather == 1 ? 120 : 220)) << 24) | 0xFFFFFF);
                c.drawCircle(s[0] * w, s[1] * hz, Ui.uf(1.1f), p);
            }
        }
        // clouds drifting across
        if (sky.weather >= 1) {
            int col = sky.night ? 0x2A2C3040 : sky.weather >= 3 ? 0x403A3F48 : 0x48FFFFFF;
            p.setColor(col);
            int n = sky.weather == 1 ? 4 : clouds.length;
            for (int i = 0; i < n; i++) {
                float[] k = clouds[i];
                k[0] += k[3] * dt;
                if (k[0] > 1.3f) k[0] = -0.3f;
                float cx = k[0] * w, cy = k[1] * hz, r = k[2] * w * 0.5f;
                c.drawOval(cx - r, cy - r * 0.35f, cx + r, cy + r * 0.35f, p);
                c.drawOval(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.35f, cy + r * 0.2f, p);
            }
        }
        // rain
        if (sky.weather >= 3) {
            p.setStrokeWidth(Ui.uf(1.2f));
            p.setColor(sky.night ? 0x55A8B4C8 : 0x66D0DAE6);
            for (float[] d : drops) {
                d[1] += d[2] * dt * 1.4f;
                if (d[1] > 1) {
                    d[1] = 0;
                    d[0] = rnd.nextFloat();
                }
                float x = d[0] * w, y = d[1] * h, len = Ui.uf(14) * d[2];
                c.drawLine(x, y, x - len * 0.25f, y + len, p);
            }
        }
        // a lightning flash now and then in a storm
        if (sky.weather == 4 && anim) {
            if (now - flashAt > 9000 && rnd.nextFloat() < 0.01f) flashAt = now;
            long f = now - flashAt;
            if (f < 220) {
                p.setColor(((int) (90 * (1 - f / 220f)) << 24) | 0xFFFFFF);
                c.drawRect(0, 0, w, hz, p);
            }
        }
        if (anim && (sky.weather >= 1 || sky.night)) postInvalidateDelayed(50); // about 20 frames a second
    }
}
