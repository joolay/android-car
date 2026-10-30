package com.carbox.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

/**
 * Optional iOS-style glass look (Settings → Wallpaper → Glass look) for the dock, Rail Bar, Now playing,
 * the left widgets and the tiles: each card shows a blurred copy of the wallpaper behind it, a light
 * tint and a hairline edge. Off: the usual solid card. Also draws the "Road" wallpaper.
 */
final class Glass {
    static final String KEY = "glass_ui";
    private static Bitmap blur; // the whole wallpaper, blurred, at screen size
    private static int blurW, blurH;

    private Glass() {
    }

    static boolean on() {
        return Store.flag(KEY, false);
    }

    /** A card background: glass when the look is on, else the solid colour as before. */
    static Drawable card(int color, float radius) {
        return new Card(color, radius);
    }

    /** The wallpaper changed (or the look was switched): rebuild the blur and redraw every card. */
    static void refresh() {
        blur = null;
        MainActivity a = MainActivity.I;
        if (a != null) invalidateAll(a.getWindow().getDecorView());
    }

    private static void invalidateAll(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateAll(g.getChildAt(i));
        }
    }

    /** The screen's wallpaper, blurred: drawn small and scaled back up (soft, and cheap). */
    private static Bitmap blurred(View root) {
        int w = root.getWidth(), h = root.getHeight();
        if (w == 0 || h == 0) return null;
        if (blur != null && blurW == w && blurH == h) return blur;
        Drawable bg = root.getBackground();
        if (bg == null) return null;
        try {
            int sw = Math.max(1, w / 14), sh = Math.max(1, h / 14);
            Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(small);
            cv.scale(sw / (float) w, sh / (float) h);
            Rect old = bg.copyBounds();
            bg.setBounds(0, 0, w, h);
            bg.draw(cv);
            bg.setBounds(old);
            Bitmap mid = Bitmap.createScaledBitmap(small, sw * 3, sh * 3, true); // two soft steps: smoother blur
            blur = Bitmap.createScaledBitmap(mid, w, h, true);
            blurW = w;
            blurH = h;
        } catch (Throwable t) {
            blur = null;
        }
        return blur;
    }

    private static final class Card extends Drawable {
        private final int color;
        private final float r;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
                shine = new Paint(Paint.ANTI_ALIAS_FLAG), pic = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF box = new RectF();
        private final Path clip = new Path();
        private final int[] at = new int[2], ro = new int[2];

        Card(int color, float radius) {
            this.color = color;
            this.r = U.uf(radius);
            fill.setColor(color);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1f, U.uf(1)));
            edge.setColor(0x40FFFFFF);
        }

        @Override
        public void draw(Canvas c) {
            box.set(getBounds());
            View host = getCallback() instanceof View ? (View) getCallback() : null;
            MainActivity a = MainActivity.I;
            View root = a != null ? a.root : null;
            Bitmap b = on() && host != null && root != null ? blurred(root) : null;
            if (b == null) { // solid, as before
                c.drawRoundRect(box, r, r, fill);
                return;
            }
            clip.reset();
            clip.addRoundRect(box, r, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            host.getLocationInWindow(at);
            root.getLocationInWindow(ro);
            c.drawBitmap(b, ro[0] - at[0], ro[1] - at[1], pic); // the blurred wallpaper right behind this card
            fill.setColor(0x33141A24); // a little dark frost so text stays readable
            c.drawRect(box, fill);
            shine.setShader(new LinearGradient(0, box.top, 0, box.top + box.height() * 0.6f, 0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(box, shine);
            c.restore();
            fill.setColor(color);
            float e = edge.getStrokeWidth() / 2;
            box.inset(e, e);
            c.drawRoundRect(box, r, r, edge);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void getOutline(android.graphics.Outline o) { // keeps clipping and shadows rounded
            o.setRoundRect(getBounds(), r);
        }
    }

    // ------------------------------------------------------------------ the Road wallpaper

    /** A long tarred road running to the horizon at dusk, drawn for this screen size. */
    static Bitmap road(int w, int h) {
        Bitmap bm = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float hz = h * 0.46f, cx = w * 0.5f; // horizon and the vanishing point
        // sky: night blue to a warm glow at the horizon
        p.setShader(new LinearGradient(0, 0, 0, hz, new int[]{0xFF0A1226, 0xFF1C2A52, 0xFF6A4A6E, 0xFFE58A4E},
                new float[]{0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hz, p);
        // sun glow on the horizon
        p.setShader(new android.graphics.RadialGradient(cx, hz, w * 0.35f, 0x88FFC27A, 0x00FFC27A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hz, p);
        // far hills
        Path hills = new Path();
        hills.moveTo(0, hz);
        hills.cubicTo(w * 0.12f, hz - h * 0.07f, w * 0.25f, hz - h * 0.02f, w * 0.38f, hz - h * 0.05f);
        hills.cubicTo(w * 0.52f, hz - h * 0.09f, w * 0.66f, hz - h * 0.01f, w * 0.8f, hz - h * 0.06f);
        hills.cubicTo(w * 0.9f, hz - h * 0.08f, w * 0.96f, hz - h * 0.03f, w, hz - h * 0.04f);
        hills.lineTo(w, hz);
        hills.close();
        p.setShader(null);
        p.setColor(0xFF20223A);
        c.drawPath(hills, p);
        // ground either side: dark veld
        p.setShader(new LinearGradient(0, hz, 0, h, 0xFF1E2A22, 0xFF0C120E, Shader.TileMode.CLAMP));
        c.drawRect(0, hz, w, h, p);
        // the road: wide at the bottom, a point on the horizon
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
            float in = 0.9f, out = 0.93f;
            e.moveTo(cx + side * w * 0.0045f, hz);
            e.lineTo(cx + side * w * 0.005f, hz);
            e.lineTo(cx + side * bw * out, h);
            e.lineTo(cx + side * bw * in, h);
            e.close();
            c.drawPath(e, p);
        }
        // dashed centre line, the dashes shrinking towards the horizon
        p.setColor(0xE6F2C14E);
        float t = 0f;
        while (t < 1f) {
            float t2 = Math.min(1f, t + 0.06f);
            // perspective: screen y for depth t (0 near, 1 far)
            float y1 = h - (h - hz) * (1 - 1 / (1 + t * 9)) * 1.1f, y2 = h - (h - hz) * (1 - 1 / (1 + t2 * 9)) * 1.1f;
            if (y2 <= hz) break;
            float w1 = w * 0.012f * (y1 - hz) / (h - hz), w2 = w * 0.012f * (y2 - hz) / (h - hz);
            Path d = new Path();
            d.moveTo(cx - w1, y1);
            d.lineTo(cx + w1, y1);
            d.lineTo(cx + w2, y2);
            d.lineTo(cx - w2, y2);
            d.close();
            c.drawPath(d, p);
            t = t2 + 0.07f;
        }
        // a soft shade over the bottom so the dock reads well
        p.setShader(new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.6f, w, h, p);
        return bm;
    }
}
