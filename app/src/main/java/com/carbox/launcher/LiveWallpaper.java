package com.carbox.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The animated wallpaper behind the home screen: a moving scene (road, fish tank, coal engine, flux
 * capacitor, pistons, cogs) with lighting (time of day) and weather overlays that can be put over any
 * wallpaper. Gentle and ~20 fps so it doesn't distract or load the box; still when animations are off.
 */
final class LiveWallpaper extends View {
    static final String LIGHTING = "fx_lighting", WEATHER = "fx_weather";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(7);
    private final float[][] stars = new float[60][3], clouds = new float[6][4], drops = new float[80][3],
            bubbles = new float[24][3], sparks = new float[40][4], fish = new float[7][8];
    private Themes.Sky sky;
    private String scene = "black";
    private long flashAt, last;
    private boolean on;

    LiveWallpaper(Context c) {
        super(c);
        for (float[] s : stars) {
            s[0] = rnd.nextFloat();
            s[1] = rnd.nextFloat() * 0.42f;
            s[2] = rnd.nextFloat() * 6.28f;
        }
        for (float[] k : clouds) {
            k[0] = rnd.nextFloat();
            k[1] = 0.05f + rnd.nextFloat() * 0.25f;
            k[2] = 0.12f + rnd.nextFloat() * 0.16f;
            k[3] = 0.004f + rnd.nextFloat() * 0.006f;
        }
        for (float[] d : drops) {
            d[0] = rnd.nextFloat();
            d[1] = rnd.nextFloat();
            d[2] = 0.6f + rnd.nextFloat() * 0.6f;
        }
        for (float[] b : bubbles) {
            b[0] = rnd.nextFloat();
            b[1] = rnd.nextFloat();
            b[2] = 0.3f + rnd.nextFloat() * 0.9f;
        }
        for (float[] s : sparks) reseedSpark(s);
        for (int i = 0; i < fish.length; i++) {
            fish[i][0] = rnd.nextFloat();
            fish[i][1] = 0.15f + rnd.nextFloat() * 0.6f;
            fish[i][2] = (rnd.nextBoolean() ? 1 : -1) * (0.03f + rnd.nextFloat() * 0.05f); // speed/dir
            fish[i][3] = 0.6f + rnd.nextFloat() * 0.8f;   // size
            fish[i][4] = rnd.nextInt(FISH_COLS.length);   // colour
            fish[i][5] = rnd.nextFloat() * 6.28f;         // swim phase
            fish[i][6] = Math.signum(fish[i][2]);         // which way it faces (eases through 0 when it turns round)
            fish[i][7] = 0.35f + rnd.nextFloat() * 0.65f; // depth: 1 near, small and misty when far
        }
    }

    private void reseedSpark(float[] s) {
        s[0] = 0.35f + rnd.nextFloat() * 0.3f;  // x near the firebox
        s[1] = 0.95f;                            // start low
        s[2] = -(0.4f + rnd.nextFloat() * 0.7f); // up speed
        s[3] = rnd.nextFloat();                  // life
    }

    /** The animated scene, or "black"/"color"/"image" for a still wallpaper (overlays still apply). */
    void setScene(String s) {
        scene = s == null ? "black" : s;
        invalidate();
    }

    void update(Themes.Sky k) {
        sky = k;
        invalidate();
    }

    void run(boolean r) {
        on = r;
        last = 0;
        invalidate();
    }

    /** This wallpaper is one of the animated scenes right now (so Glass should blur the live frame, not the flat base). */
    boolean isAnimated() {
        return animated(scene);
    }

    /** The base colour behind the current scene. */
    int baseColor() {
        return base(scene);
    }

    /** The base colour behind each animated scene, so Glass cards tint to match. */
    static int base(String scene) {
        switch (scene) {
            case "fishtank":
                return 0xFF0B3A5B;
            case "engine":
                return 0xFF161210;
            case "flux":
                return 0xFF07090F;
            case "pistons":
                return 0xFF1A1D22;
            case "cogs":
                return 0xFF241B10;
            case "road":
                return 0xFF0E1322;
            default:
                return 0xFF000000;
        }
    }

    static boolean animated(String s) {
        return "road".equals(s) || "fishtank".equals(s) || "engine".equals(s) || "flux".equals(s)
                || "pistons".equals(s) || "cogs".equals(s);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.2f, (now - last) / 1000f);
        last = now;
        boolean anim = on && Ui.ms(1) > 0;
        if (!anim) dt = 0;
        float t = now / 1000f;

        boolean moving = false;
        switch (scene) {
            case "road":
                moving = road(c, w, h, t, dt);
                break;
            case "fishtank":
                moving = fishtank(c, w, h, t, dt);
                break;
            case "engine":
                moving = engine(c, w, h, t, dt);
                break;
            case "flux":
                moving = flux(c, w, h, t);
                break;
            case "pistons":
                moving = pistons(c, w, h, t);
                break;
            case "cogs":
                moving = cogs(c, w, h, t);
                break;
            default: // black / colour / image: the root background shows; overlays still apply
        }

        boolean weather = Prefs.flag(WEATHER, true) && sky != null;
        boolean lighting = Prefs.flag(LIGHTING, true) && sky != null;
        if (lighting) lighting(c, w, h);
        if (weather) weather(c, w, h, t, dt, anim);

        if (anim && moving && c.isHardwareAccelerated()) Themes.glassTick(); // glass cards redraw, so the scene moves behind them
        if (anim && (moving || (weather && sky.weather >= 1) || (weather && sky.night))) postInvalidateDelayed(50);
    }

    // ---- lighting: a wash by time of day, over any wallpaper
    private void lighting(Canvas c, int w, int h) {
        int wash;
        if (sky.night) wash = 0x66060A1A;        // deep blue darkening at night
        else if (sky.sunHeight < 0.25f) wash = 0x33FF9A4E; // warm at dawn / dusk
        else wash = 0x11FFF4D6;                   // a touch of daylight
        p.setShader(null);
        p.setColor(wash);
        c.drawRect(0, 0, w, h, p);
    }

    // ---- weather overlay (clouds, rain, stars, storm flash), over any wallpaper
    private void weather(Canvas c, int w, int h, float t, float dt, boolean anim) {
        if (sky.night && sky.weather <= 1) {
            for (float[] s : stars) {
                float a = 0.35f + 0.35f * (float) Math.sin(t * 1.3f + s[2]);
                p.setColor(((int) (a * (sky.weather == 1 ? 120 : 200)) << 24) | 0xFFFFFF);
                c.drawCircle(s[0] * w, s[1] * h, Ui.uf(1.1f), p);
            }
        }
        if (sky.weather >= 1) {
            p.setColor(sky.night ? 0x2A2C3040 : sky.weather >= 3 ? 0x403A3F48 : 0x40FFFFFF);
            int n = sky.weather == 1 ? 4 : clouds.length;
            for (int i = 0; i < n; i++) {
                float[] k = clouds[i];
                k[0] += k[3] * dt;
                if (k[0] > 1.3f) k[0] = -0.3f;
                float cx = k[0] * w, cy = k[1] * h, r = k[2] * w * 0.5f;
                c.drawOval(cx - r, cy - r * 0.35f, cx + r, cy + r * 0.35f, p);
                c.drawOval(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.35f, cy + r * 0.2f, p);
            }
        }
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
        if (sky.weather == 4 && anim) {
            long now = SystemClock.uptimeMillis();
            if (now - flashAt > 9000 && rnd.nextFloat() < 0.01f) flashAt = now;
            long f = now - flashAt;
            if (f < 220) {
                p.setColor(((int) (80 * (1 - f / 220f)) << 24) | 0xFFFFFF);
                c.drawRect(0, 0, w, h, p);
            }
        }
    }

    // ---- scenes. Each returns true while it needs more frames.

    private boolean road(Canvas c, int w, int h, float t, float dt) {
        float hz = h * 0.46f, cx = w * 0.5f;
        int[] skyC = sky != null ? sky.sky : new int[]{0xFF0A1226, 0xFF1C2A52, 0xFF6A4A6E, 0xFFE58A4E};
        p.setShader(new LinearGradient(0, 0, 0, hz, skyC, new float[]{0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hz, p);
        p.setShader(new LinearGradient(0, hz, 0, h, sky != null ? sky.ground : 0xFF1E2A22, 0xFF0C120E, Shader.TileMode.CLAMP));
        c.drawRect(0, hz, w, h, p);
        p.setShader(null);
        float bw = w * 0.62f;
        Path road = new Path();
        road.moveTo(cx - w * 0.006f, hz);
        road.lineTo(cx + w * 0.006f, hz);
        road.lineTo(cx + bw, h);
        road.lineTo(cx - bw, h);
        road.close();
        p.setShader(new LinearGradient(0, hz, 0, h, 0xFF3A3D45, 0xFF16181C, Shader.TileMode.CLAMP));
        c.drawPath(road, p);
        p.setShader(null);
        // white edge lines
        p.setColor(0xCCE8E8E8);
        for (int side = -1; side <= 1; side += 2) {
            Path e = new Path();
            e.moveTo(cx + side * w * 0.0045f, hz);
            e.lineTo(cx + side * w * 0.005f, hz);
            e.lineTo(cx + side * bw * 0.93f, h);
            e.lineTo(cx + side * bw * 0.9f, h);
            e.close();
            c.drawPath(e, p);
        }
        // dashed centre line scrolling toward you (the phase grows with time)
        p.setColor(0xE6F2C14E);
        float phase = (t * 0.6f) % 1f;
        for (int i = -1; i < 10; i++) {
            float d0 = frac(i * 0.16f + phase), d1 = d0 + 0.08f;
            if (d1 > 1f) continue;
            float y1 = road(hz, h, d0), y2 = road(hz, h, d1);
            float w1 = w * 0.012f * (y1 - hz) / (h - hz), w2 = w * 0.012f * (y2 - hz) / (h - hz);
            Path d = new Path();
            d.moveTo(cx - w1, y1);
            d.lineTo(cx + w1, y1);
            d.lineTo(cx + w2, y2);
            d.lineTo(cx - w2, y2);
            d.close();
            c.drawPath(d, p);
        }
        return true;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** Screen y for depth d (0 far at the horizon, 1 near the bottom), with perspective. */
    private static float road(float hz, float h, float d) {
        return hz + (h - hz) * (d * d);
    }

    private static final int[] FISH_COLS = {0xFFF2A03C, 0xFFE24B4A, 0xFF5DCAA5, 0xFFF2D14E, 0xFF85B7EB};

    private final Path tmp = new Path();

    private static int mix(int a, int b, float f) {
        return android.graphics.Color.rgb((int) (android.graphics.Color.red(a) * (1 - f) + android.graphics.Color.red(b) * f),
                (int) (android.graphics.Color.green(a) * (1 - f) + android.graphics.Color.green(b) * f),
                (int) (android.graphics.Color.blue(a) * (1 - f) + android.graphics.Color.blue(b) * f));
    }

    /** A leaf of seaweed: a tapering blade that sways more towards its tip. */
    private void blade(Canvas c, float bx, float by, float ht, float sway, float t, float ph, int col) {
        tmp.reset();
        int n = 8;
        float[] xs = new float[n + 1], ys = new float[n + 1], ws = new float[n + 1];
        for (int k = 0; k <= n; k++) {
            float u = k / (float) n;
            xs[k] = bx + (float) Math.sin(t * 1.1f + ph + u * 3.2f) * sway * u;
            ys[k] = by - ht * u;
            ws[k] = Ui.uf(6) * (1 - u * 0.88f);
        }
        tmp.moveTo(xs[0] - ws[0], ys[0]);
        for (int k = 1; k <= n; k++) tmp.lineTo(xs[k] - ws[k], ys[k]);
        for (int k = n; k >= 0; k--) tmp.lineTo(xs[k] + ws[k], ys[k]);
        tmp.close();
        p.setColor(col);
        c.drawPath(tmp, p);
        p.setColor(mix(col, 0xFFFFFFFF, 0.18f)); // midrib
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.uf(1));
        tmp.reset();
        tmp.moveTo(xs[0], ys[0]);
        for (int k = 1; k < n; k++) tmp.lineTo(xs[k], ys[k]);
        c.drawPath(tmp, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** A branching coral; the tips sway a little. */
    private void coral(Canvas c, float x, float y, float len, float ang, int depth, float t, int col) {
        float sw = (float) Math.sin(t * 0.8f + x * 0.02f) * 0.06f;
        float ex = x + (float) Math.sin(ang + sw) * len, ey = y - (float) Math.cos(ang + sw) * len;
        p.setStrokeWidth(Ui.uf(1.5f + depth * 1.6f));
        p.setColor(col);
        c.drawLine(x, y, ex, ey, p);
        if (depth == 0) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(mix(col, 0xFFFFFFFF, 0.4f));
            c.drawCircle(ex, ey, Ui.uf(2.6f), p);
            p.setStyle(Paint.Style.STROKE);
            return;
        }
        coral(c, ex, ey, len * 0.72f, ang - 0.5f, depth - 1, t, col);
        coral(c, ex, ey, len * 0.72f, ang + 0.45f, depth - 1, t, col);
    }

    private void fish(Canvas c, float x, float y, float sz, float dir, float t, float ph, int col, float alpha) {
        int belly = mix(col, 0xFFFFFFFF, 0.5f), dark = mix(col, 0xFF000000, 0.35f);
        int al = (int) (255 * alpha) << 24;
        c.save();
        c.translate(x, y);
        c.scale(dir, 1);
        c.rotate((float) Math.cos(t * 1.3f + ph) * 4f);
        float wag = (float) Math.sin(t * 6f + ph);
        // tail (forked), wagging at its root
        c.save();
        c.translate(-sz * 0.8f, 0);
        c.rotate(wag * 18f);
        tmp.reset();
        tmp.moveTo(0, 0);
        tmp.lineTo(-sz * 0.85f, -sz * 0.62f);
        tmp.quadTo(-sz * 0.55f, 0, -sz * 0.85f, sz * 0.62f);
        tmp.close();
        p.setColor(al | (mix(col, 0xFF000000, 0.12f) & 0xFFFFFF));
        c.drawPath(tmp, p);
        c.restore();
        // dorsal fin
        tmp.reset();
        tmp.moveTo(-sz * 0.35f, -sz * 0.36f);
        tmp.quadTo(-sz * 0.05f + wag * sz * 0.05f, -sz * 0.95f, sz * 0.35f, -sz * 0.4f);
        tmp.close();
        p.setColor(al | (dark & 0xFFFFFF));
        c.drawPath(tmp, p);
        // body and lighter belly
        // a streamlined body, shaded dark on the back to light on the belly
        tmp.reset();
        tmp.moveTo(sz, sz * 0.02f);
        tmp.cubicTo(sz * 0.7f, -sz * 0.5f, -sz * 0.2f, -sz * 0.62f, -sz * 0.85f, -sz * 0.12f);
        tmp.lineTo(-sz * 0.85f, sz * 0.12f);
        tmp.cubicTo(-sz * 0.2f, sz * 0.58f, sz * 0.7f, sz * 0.45f, sz, sz * 0.02f);
        tmp.close();
        p.setShader(new LinearGradient(0, -sz * 0.6f, 0, sz * 0.5f, new int[]{al | (dark & 0xFFFFFF), al | (col & 0xFFFFFF), al | (belly & 0xFFFFFF)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(tmp, p);
        p.setShader(null);
        if (alpha > 0.85f && sz > Ui.uf(11)) { // fine scales, clipped to the body
            c.save();
            c.clipPath(tmp);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, sz * 0.04f));
            p.setColor(0x26000000);
            for (int row = -2; row <= 2; row++)
                for (int k = 0; k < 8; k++) {
                    float cx = -sz * 0.75f + k * sz * 0.22f + ((row & 1) == 0 ? 0 : sz * 0.11f), cy = row * sz * 0.17f;
                    c.drawArc(cx - sz * 0.1f, cy - sz * 0.09f, cx + sz * 0.1f, cy + sz * 0.09f, -70, 140, false, p);
                }
            p.setStyle(Paint.Style.FILL);
            c.restore();
        }
        p.setColor(((int) (0x30 * alpha) << 24) | 0xFFFFFF); // a soft highlight along the back
        c.drawOval(-sz * 0.6f, -sz * 0.42f, sz * 0.55f, -sz * 0.22f, p);
        if (alpha > 0.6f) {
            // two stripes across the body
            p.setColor(0x44000000);
            c.drawOval(-sz * 0.12f, -sz * 0.5f, sz * 0.06f, sz * 0.5f, p);
            c.drawOval(-sz * 0.5f, -sz * 0.44f, -sz * 0.36f, sz * 0.44f, p);
            // gill line
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.uf(1));
            p.setColor(0x55000000);
            c.drawArc(sz * 0.25f, -sz * 0.3f, sz * 0.5f, sz * 0.3f, 100, 160, false, p);
            p.setStyle(Paint.Style.FILL);
            // pectoral fin, flapping
            c.save();
            c.translate(sz * 0.15f, sz * 0.1f);
            c.rotate(-25 + (float) Math.sin(t * 5f + ph) * 18f);
            p.setColor(0xAA000000 | (belly & 0xFFFFFF));
            c.drawOval(-sz * 0.45f, -sz * 0.08f, 0, sz * 0.12f, p);
            c.restore();
            // eye with a highlight
            p.setColor(0xFFFFFFFF);
            c.drawCircle(sz * 0.6f, -sz * 0.12f, sz * 0.14f, p);
            p.setColor(0xFF0A0A0A);
            c.drawCircle(sz * 0.63f, -sz * 0.12f, sz * 0.085f, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(sz * 0.65f, -sz * 0.16f, sz * 0.03f, p);
        }
        c.restore();
    }

    private boolean fishtank(Canvas c, int w, int h, float t, float dt) {
        t *= 0.55f; // calmer: everything in the tank moves at about half speed
        dt *= 0.55f;
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF2A8FC4, 0xFF0F4A70, 0xFF062538}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // sunbeams, drifting and breathing
        for (int i = 0; i < 4; i++) {
            float x = w * (0.1f + i * 0.25f) + (float) Math.sin(t * 0.2f + i) * w * 0.03f;
            int ba = (int) (0x1C + 0x0C * Math.sin(t * 0.5f + i * 2));
            p.setShader(new LinearGradient(0, 0, 0, h * 0.9f, (ba << 24) | 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)); // rays fade with depth
            tmp.reset();
            tmp.moveTo(x, 0);
            tmp.lineTo(x + w * 0.06f, 0);
            tmp.lineTo(x + w * 0.2f, h);
            tmp.lineTo(x + w * 0.09f, h);
            tmp.close();
            c.drawPath(tmp, p);
        }
        p.setShader(null);
        // rippling surface light near the top
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.uf(1.5f));
        for (int r = 0; r < 4; r++) {
            p.setColor(0x16FFFFFF);
            tmp.reset();
            for (int i = 0; i <= 24; i++) {
                float xx = w * i / 24f, yy = h * (0.03f + r * 0.035f) + (float) Math.sin(t * 0.9f + i * 0.7f + r * 1.7f) * Ui.uf(3);
                if (i == 0) tmp.moveTo(xx, yy);
                else tmp.lineTo(xx, yy);
            }
            c.drawPath(tmp, p);
        }
        p.setStyle(Paint.Style.FILL);
        // far away fish, faint and small
        for (int i = 0; i < 4; i++) {
            float fx = frac(t * (0.012f + i * 0.004f) + i * 0.27f), dr = i % 2 == 0 ? 1 : -1;
            fish(c, (dr > 0 ? fx : 1 - fx) * (w + 80) - 40, h * (0.2f + i * 0.13f), Ui.uf(6), dr, t, i * 1.7f, 0xFF6FA8CC, 0.35f);
        }
        // drifting specks in the water
        for (float[] s : stars) {
            float px = frac(s[0] + (float) Math.sin(t * 0.1f + s[2]) * 0.02f + t * 0.002f), py = s[1] * 2.1f + 0.02f;
            p.setColor(((int) (0x22 + 0x22 * Math.sin(t * 1.5f + s[2] * 3)) << 24) | 0xDFF3FF);
            c.drawCircle(px * w, py * h, Ui.uf(1.2f), p);
        }
        // sandy floor with ripples
        p.setShader(new LinearGradient(0, h - Ui.uf(34), 0, h, 0xFFD6C08C, 0xFFA38B57, Shader.TileMode.CLAMP));
        tmp.reset();
        tmp.moveTo(0, h);
        tmp.lineTo(0, h - Ui.uf(22));
        for (int i = 0; i <= 16; i++) tmp.lineTo(w * i / 16f, h - Ui.uf(24) + (float) Math.sin(i * 0.9f) * Ui.uf(6) + (float) Math.sin(i * 2.3f) * Ui.uf(2));
        tmp.lineTo(w, h);
        tmp.close();
        c.drawPath(tmp, p);
        p.setShader(null);
        // caustics: bright wobbling light patches dancing on the sand
        for (int i = 0; i < 14; i++) {
            float cx = w * frac(i * 0.37f + 0.05f * (float) Math.sin(t * 0.3f + i)) , cy = h - Ui.uf(10) - Ui.uf(12) * frac(i * 0.61f);
            float rw = Ui.uf(14 + 10 * frac(i * 0.29f)) * (0.8f + 0.3f * (float) Math.sin(t * 1.4f + i * 2));
            p.setColor(((int) (0x22 + 0x14 * Math.sin(t * 1.1f + i * 1.7f)) << 24) | 0xFFF6D8);
            c.drawOval(cx - rw, cy - rw * 0.22f, cx + rw, cy + rw * 0.22f, p);
        }
        // pebbles and shells
        for (int i = 0; i < 26; i++) {
            float px = w * frac(i * 0.381f + 0.07f), py = h - Ui.uf(4) - Ui.uf(14) * frac(i * 0.618f), pr = Ui.uf(1.5f + 2.5f * frac(i * 0.27f));
            p.setColor(i % 5 == 0 ? 0xFFF3E6CF : mix(0xFF8C7648, 0xFFE8D8B0, frac(i * 0.43f)));
            c.drawOval(px - pr * 1.3f, py - pr, px + pr * 1.3f, py + pr, p);
        }
        // rocks with a lit top
        float[][] rocks = {{0.18f, 30, 14}, {0.46f, 22, 10}, {0.83f, 36, 17}};
        for (float[] rk : rocks) {
            float rx = rk[0] * w, ry = h - Ui.uf(14), rw = Ui.uf(rk[1]), rh = Ui.uf(rk[2]);
            p.setColor(0xFF4D5560);
            c.drawOval(rx - rw, ry - rh, rx + rw, ry + rh * 0.7f, p);
            p.setColor(0xFF6E7884);
            c.drawOval(rx - rw * 0.8f, ry - rh * 0.95f, rx + rw * 0.3f, ry, p);
        }
        // coral
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        coral(c, w * 0.32f, h - Ui.uf(16), Ui.uf(26), -0.2f, 3, t, 0xFFE0607E);
        coral(c, w * 0.62f, h - Ui.uf(15), Ui.uf(22), 0.15f, 3, t, 0xFFF0925A);
        coral(c, w * 0.93f, h - Ui.uf(15), Ui.uf(20), -0.1f, 2, t, 0xFFB26ADB);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
        // seaweed: a darker back row, then a brighter front row
        for (int s = 0; s < 7; s++) blade(c, w * (0.05f + s * 0.15f), h - Ui.uf(16), Ui.uf(120 + (s % 3) * 28), Ui.uf(16), t, s * 1.3f, 0xFF1F6B45);
        for (int s = 0; s < 6; s++) blade(c, w * (0.1f + s * 0.17f), h - Ui.uf(14), Ui.uf(80 + (s % 4) * 22), Ui.uf(12), t, s * 0.9f + 2, 0xFF35A06A);
        // bubble stream from the big rock
        float sx = w * 0.83f;
        for (int k = 0; k < 9; k++) {
            float ph = frac(t * 0.35f + k / 9f);
            float bx2 = sx + (float) Math.sin(t * 2 + k) * Ui.uf(4 + ph * 8), by2 = h - Ui.uf(28) - ph * h * 0.85f, br = Ui.uf(1.5f + ph * 4);
            p.setColor(0x33CDEBFF);
            c.drawCircle(bx2, by2, br, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.uf(1));
            p.setColor(0x88E8F6FF);
            c.drawCircle(bx2, by2, br, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCCFFFFFF);
            c.drawCircle(bx2 - br * 0.35f, by2 - br * 0.35f, br * 0.25f, p);
        }
        // loose bubbles
        for (float[] b : bubbles) {
            b[1] -= b[2] * dt * 0.25f;
            if (b[1] < 0) {
                b[1] = 1;
                b[0] = rnd.nextFloat();
            }
            float bx3 = b[0] * w + (float) Math.sin(t * 2 + b[0] * 10) * Ui.uf(4), by3 = b[1] * h, br3 = Ui.uf(2 + b[2] * 2);
            p.setColor(0x33CDEBFF);
            c.drawCircle(bx3, by3, br3, p);
            p.setColor(0xAAFFFFFF);
            c.drawCircle(bx3 - br3 * 0.35f, by3 - br3 * 0.35f, br3 * 0.28f, p);
        }
        // fish: far ones first (smaller, mistier), turning round at the glass instead of vanishing
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) { // water between the far and near fish: a blue haze
                p.setShader(new LinearGradient(0, 0, 0, h, 0x1A2A8FC4, 0x330B3A5B, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                p.setShader(null);
            }
            for (float[] f : fish) {
                if ((f[7] < 0.6f) != (pass == 0)) continue;
                float speed = Math.abs(f[2]) * (0.75f + 0.25f * (float) Math.sin(t * 0.4f + f[5])) * (0.6f + 0.6f * f[7]);
                float sign = Math.signum(f[2]);
                f[0] += sign * speed * dt * 0.15f;
                if ((f[0] > 1.05f && sign > 0) || (f[0] < -0.05f && sign < 0)) f[2] = -f[2]; // turn round
                f[6] += (Math.signum(f[2]) - f[6]) * Math.min(1f, dt * 2.5f); // the body swings round
                float y = f[1] * h + (float) Math.sin(t * 0.9f + f[5]) * Ui.uf(10) * f[7];
                float sz = Ui.uf(17) * f[3] * (0.55f + 0.5f * f[7]);
                int col = mix(FISH_COLS[(int) f[4]], 0xFF1B5E88, (1 - f[7]) * 0.55f); // far: lost in the blue
                if (pass == 1) { // its soft shadow on the sand
                    p.setColor(0x22000000);
                    c.drawOval(f[0] * w - sz, h - Ui.uf(16), f[0] * w + sz, h - Ui.uf(10), p);
                }
                fish(c, f[0] * w, y, sz, f[6], t, f[5], col, 0.6f + 0.4f * f[7]);
            }
        }
        // the glass of the tank: dark edges, a rim light along the top and a faint reflection streak
        p.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, Ui.uf(26), 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, Ui.uf(26), p);
        p.setShader(null);
        p.setColor(0x0DFFFFFF);
        tmp.reset();
        tmp.moveTo(w * 0.04f, 0);
        tmp.lineTo(w * 0.1f, 0);
        tmp.lineTo(w * 0.04f, h);
        tmp.lineTo(-w * 0.02f, h);
        tmp.close();
        c.drawPath(tmp, p);
        return true;
    }

    private boolean engine(Canvas c, int w, int h, float t, float dt) {
        c.drawColor(0xFF161210);
        // the firebox glow, flickering
        float cx = w * 0.5f, cy = h * 0.72f;
        float flick = 0.8f + 0.2f * (float) Math.sin(t * 9) * (float) Math.sin(t * 13);
        p.setShader(new RadialGradient(cx, cy, w * 0.5f * flick, new int[]{0xFFFFC65A, 0x80FF5A1E, 0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.35f, w, h, p);
        p.setShader(null);
        // grate bars
        p.setColor(0xFF0B0908);
        for (int i = 0; i <= 10; i++) {
            float x = w * i / 10f;
            p.setStrokeWidth(Ui.uf(5));
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(x, h * 0.8f, x, h, p);
        }
        p.setStyle(Paint.Style.FILL);
        // flames
        for (int i = 0; i < 7; i++) {
            float fx = w * (0.18f + i * 0.11f);
            float base = h * 0.86f, hgt = h * (0.22f + 0.12f * (float) Math.sin(t * 6 + i * 1.7f));
            Path fl = new Path();
            fl.moveTo(fx - Ui.uf(16), base);
            fl.quadTo(fx - Ui.uf(10), base - hgt * 0.5f, fx + (float) Math.sin(t * 7 + i) * Ui.uf(8), base - hgt);
            fl.quadTo(fx + Ui.uf(10), base - hgt * 0.5f, fx + Ui.uf(16), base);
            fl.close();
            p.setColor(i % 2 == 0 ? 0xCCFF7A1E : 0xBBFFC23A);
            c.drawPath(fl, p);
        }
        // rising sparks
        p.setColor(0xFFFFB84D);
        for (float[] s : sparks) {
            s[1] += s[2] * dt;
            s[3] -= dt * 0.4f;
            if (s[3] <= 0 || s[1] < 0.2f) reseedSpark(s);
            p.setAlpha((int) (200 * Math.max(0, s[3])));
            c.drawCircle(s[0] * w + (float) Math.sin(t * 3 + s[0] * 30) * Ui.uf(6), s[1] * h, Ui.uf(1.8f), p);
        }
        p.setAlpha(255);
        return true;
    }

    private boolean flux(Canvas c, int w, int h, float t) {
        c.drawColor(0xFF07090F);
        float cx = w * 0.5f, cy = h * 0.48f, len = h * 0.33f;
        double[] ang = {-Math.PI / 2, Math.PI / 6, Math.PI - Math.PI / 6}; // Y shape
        float cycle = (t * 0.9f) % 1f;
        for (double a : ang) {
            float ex = cx + (float) Math.cos(a) * len, ey = cy + (float) Math.sin(a) * len;
            p.setStrokeWidth(Ui.uf(9));
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xFF1B2436); // tube
            c.drawLine(cx, cy, ex, ey, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 6; i++) {
                float f = i / 6f;
                float x = ex + (cx - ex) * f, y = ey + (cy - ey) * f; // lights race toward the hub
                float lit = frac(cycle + f); // brightest when near the leading edge
                int a2 = (int) (255 * Math.max(0, 1 - Math.abs(lit - cycle) * 6));
                p.setColor((Math.min(255, a2) << 24) | 0x7CF9FF);
                c.drawCircle(x, y, Ui.uf(5), p);
            }
        }
        // hub pulse
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 6);
        p.setShader(new RadialGradient(cx, cy, Ui.uf(26) + pulse * Ui.uf(18),
                new int[]{0xFFFFFFFF, 0xCC7CF9FF, 0x00000000}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, Ui.uf(44), p);
        p.setShader(null);
        return true;
    }

    private boolean pistons(Canvas c, int w, int h, float t) {
        c.drawColor(0xFF1A1D22);
        int n = 4;
        float cw = w / (float) n, top = h * 0.12f, bottom = h * 0.62f, stroke = bottom - top;
        float crankY = h * 0.82f, crankR = Ui.uf(26);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float x = cw * (i + 0.5f);
            // cylinder
            p.setColor(0xFF2A2F37);
            c.drawRoundRect(x - cw * 0.32f, top - Ui.uf(10), x + cw * 0.32f, crankY, Ui.uf(8), Ui.uf(8), p);
            float phase = (float) (t * 3.2 + i * Math.PI / 2); // firing order
            float py = top + stroke * 0.5f * (1 - (float) Math.cos(phase)); // piston top
            float crankAx = x + (float) Math.sin(phase) * crankR, crankAy = crankY + (float) Math.cos(phase) * crankR;
            // connecting rod
            p.setColor(0xFF8A9099);
            p.setStrokeWidth(Ui.uf(7));
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(x, py + Ui.uf(14), crankAx, crankAy, p);
            p.setStyle(Paint.Style.FILL);
            // piston
            p.setColor(0xFFC3C9D2);
            c.drawRoundRect(x - cw * 0.26f, py, x + cw * 0.26f, py + Ui.uf(28), Ui.uf(5), Ui.uf(5), p);
            // spark at the top of the stroke
            if (Math.cos(phase) < -0.9f) {
                p.setColor(0xFFFFE08A);
                c.drawCircle(x, top - Ui.uf(2), Ui.uf(5), p);
            }
            // crank journal
            p.setColor(0xFF6B7179);
            c.drawCircle(crankAx, crankAy, Ui.uf(6), p);
        }
        // crankshaft axis
        p.setColor(0xFF4A5059);
        c.drawRect(0, crankY - Ui.uf(2), w, crankY + Ui.uf(2), p);
        return true;
    }

    private boolean cogs(Canvas c, int w, int h, float t) {
        c.drawColor(0xFF241B10);
        // a few meshing gears; speed is opposite in sign and scaled by 1/teeth so they mesh
        gear(c, w * 0.32f, h * 0.44f, Ui.uf(90), 18, t * 0.6f, 0xFFB8862B);
        gear(c, w * 0.62f, h * 0.44f, Ui.uf(60), 12, -t * 0.9f + 0.26f, 0xFFCFA14A);
        gear(c, w * 0.5f, h * 0.78f, Ui.uf(45), 9, t * 1.2f, 0xFF9C7324);
        gear(c, w * 0.82f, h * 0.68f, Ui.uf(38), 8, -t * 1.35f, 0xFFD8B25A);
        return true;
    }

    private void gear(Canvas c, float cx, float cy, float r, int teeth, float rot, int col) {
        p.setColor(col);
        Path g = new Path();
        float inner = r * 0.82f;
        for (int i = 0; i < teeth; i++) {
            double a0 = rot + i * 2 * Math.PI / teeth, a1 = a0 + Math.PI / teeth * 0.6, a2 = a0 + Math.PI / teeth, a3 = a0 + Math.PI / teeth * 1.6;
            pt(g, cx, cy, inner, a0, i == 0);
            pt(g, cx, cy, r, a0 + Math.PI / teeth * 0.2, false);
            pt(g, cx, cy, r, a1, false);
            pt(g, cx, cy, inner, a2, false);
            pt(g, cx, cy, inner, a3, false);
        }
        g.close();
        c.drawPath(g, p);
        // hub
        p.setColor(0xFF3A2C14);
        c.drawCircle(cx, cy, r * 0.3f, p);
        p.setColor(0xFF1C150A);
        c.drawCircle(cx, cy, r * 0.12f, p);
        // spokes
        p.setStrokeWidth(Ui.uf(5));
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0x55000000);
        for (int i = 0; i < 5; i++) {
            double a = rot + i * 2 * Math.PI / 5;
            c.drawLine(cx, cy, cx + (float) Math.cos(a) * r * 0.78f, cy + (float) Math.sin(a) * r * 0.78f, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void pt(Path g, float cx, float cy, float r, double a, boolean move) {
        float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
        if (move) g.moveTo(x, y);
        else g.lineTo(x, y);
    }
}
