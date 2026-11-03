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
            bubbles = new float[24][3], sparks = new float[40][4], fish = new float[6][5];
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

    private boolean fishtank(Canvas c, int w, int h, float t, float dt) {
        p.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF1E7BB0, 0xFF0B3A5B, 0xFF062538}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // sunbeams
        p.setColor(0x14FFFFFF);
        for (int i = 0; i < 3; i++) {
            float x = w * (0.2f + i * 0.3f) + (float) Math.sin(t * 0.2f + i) * w * 0.03f;
            Path beam = new Path();
            beam.moveTo(x, 0);
            beam.lineTo(x + w * 0.05f, 0);
            beam.lineTo(x + w * 0.16f, h);
            beam.lineTo(x + w * 0.08f, h);
            beam.close();
            c.drawPath(beam, p);
        }
        // sandy floor
        p.setColor(0xFFC9B07A);
        Path floor = new Path();
        floor.moveTo(0, h);
        floor.lineTo(0, h - Ui.uf(22));
        for (int i = 0; i <= 8; i++) floor.lineTo(w * i / 8f, h - Ui.uf(22) + (float) Math.sin(i * 1.3f) * Ui.uf(6));
        floor.lineTo(w, h);
        floor.close();
        c.drawPath(floor, p);
        // seaweed swaying
        p.setStrokeWidth(Ui.uf(5));
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFF2E8B57);
        for (int s = 0; s < 5; s++) {
            float bx = w * (0.1f + s * 0.2f);
            Path weed = new Path();
            weed.moveTo(bx, h - Ui.uf(18));
            for (int k = 1; k <= 5; k++) {
                float yy = h - Ui.uf(18) - k * Ui.uf(26);
                weed.lineTo(bx + (float) Math.sin(t * 1.2f + k * 0.6f + s) * Ui.uf(10), yy);
            }
            c.drawPath(weed, p);
        }
        p.setStyle(Paint.Style.FILL);
        // bubbles
        p.setColor(0x66CDEBFF);
        for (float[] b : bubbles) {
            b[1] -= b[2] * dt * 0.25f;
            if (b[1] < 0) {
                b[1] = 1;
                b[0] = rnd.nextFloat();
            }
            c.drawCircle(b[0] * w + (float) Math.sin(t * 2 + b[0] * 10) * Ui.uf(4), b[1] * h, Ui.uf(2 + b[2] * 2), p);
        }
        // fish
        for (float[] f : fish) {
            f[0] += f[2] * dt * 0.15f;
            if (f[0] < -0.1f) f[0] = 1.1f;
            if (f[0] > 1.1f) f[0] = -0.1f;
            float x = f[0] * w, y = f[1] * h, sz = Ui.uf(16) * f[3], dir = Math.signum(f[2]);
            p.setColor(FISH_COLS[(int) f[4]]);
            c.drawOval(x - sz, y - sz * 0.45f, x + sz, y + sz * 0.45f, p);
            Path tail = new Path(); // tail behind the swim direction
            tail.moveTo(x - dir * sz, y);
            tail.lineTo(x - dir * sz * 1.6f, y - sz * 0.5f);
            tail.lineTo(x - dir * sz * 1.6f, y + sz * 0.5f);
            tail.close();
            c.drawPath(tail, p);
            p.setColor(0xFF0A0A0A); // eye
            c.drawCircle(x + dir * sz * 0.55f, y - sz * 0.1f, Ui.uf(1.6f), p);
        }
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
