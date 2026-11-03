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
 * The Glass theme (Settings → Wallpaper → Theme; the other is Modern): iOS-style glass for the dock, Rail Bar, Now playing,
 * the left widgets and the tiles: each card shows a blurred copy of the wallpaper behind it, a light
 * tint and a hairline edge. Off: the usual solid card. Also draws the "Road" wallpaper.
 */
final class Themes {
    static final String KEY = "glass_ui";
    private static Bitmap blur; // the whole wallpaper, blurred, at screen size
    private static int blurW, blurH;

    private Themes() {
    }

    /** The Glass theme is on (the default; Modern is the other). Locked: Modern. */
    /** The frosting we suggest: clear enough to see the wallpaper, solid enough to read. */
    static final int FROST_RECOMMENDED = 30;

    /** How frosted the glass is, 0 (clear) to 100 (almost solid). 30 to start. */
    static int frost() {
        return Math.max(0, Math.min(100, Prefs.num("glass_frost", 30)));
    }

    static void setFrost(int v) {
        Prefs.put("glass_frost", Math.max(0, Math.min(100, v)));
        refresh(); // new blur and frost on every card
    }

    static final String THEME = "theme"; // "modern", "glass" (default) or "simple"

    /** The chosen theme. */
    static String mode() {
        if (!Prefs.flag("glass_default_v1", false)) { // Glass became the default: switched on once for everyone
            Prefs.put("glass_default_v1", true);
            Prefs.put(THEME, "glass");
            if ("black".equals(Prefs.str(Prefs.WALL, "road"))) Prefs.put(Prefs.WALL, "road"); // glass needs a wallpaper behind it
        }
        String m = Prefs.str(THEME, Prefs.flag(KEY, true) ? "glass" : "modern"); // migrate the old glass on/off
        if ("glass".equals(m) && !Addons.unlocked("theme_glass")) return "modern";
        return m;
    }

    /** The Glass theme (see-through cards). */
    static boolean on() {
        return "glass".equals(mode());
    }

    /** The Simple theme: plain black background, solid cards, no window or widget animations. */
    static boolean simple() {
        return "simple".equals(mode());
    }

    static void setMode(String m) {
        Prefs.put(THEME, m);
        Prefs.put(KEY, "glass".equals(m)); // keep the old flag in step
        if ("glass".equals(m) && "black".equals(Prefs.str(Prefs.WALL, "road"))) Prefs.put(Prefs.WALL, "road");
    }

    /** A card background: glass when the look is on, else the solid colour as before. */
    static Drawable card(int color, float radius) {
        return new Card(color, radius);
    }

    /** A highlighted card (parked, speeding, next prayer): glass with a coloured edge in Glass, else solid with the edge. */
    static Drawable edged(int fill, int edge, float radius) {
        if (!on()) return Ui.outline(fill, edge, 3, radius);
        Card c = new Card(fill, radius);
        c.edge.setColor(edge);
        c.edge.setStrokeWidth(Ui.uf(2.5f));
        return c;
    }

    /** A button or row inside a card: a light see-through pane in the Glass theme, else the solid colour. */
    static Drawable inner(int color, float radius) {
        return new Inner(color, radius);
    }

    private static final class Inner extends Drawable {
        private final int color;
        private final float r;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        Inner(int color, float radius) {
            this.color = color;
            this.r = Ui.uf(radius);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1f, Ui.uf(1)));
            edge.setColor(0x2EFFFFFF);
        }

        @Override
        public void draw(Canvas c) {
            box.set(getBounds());
            boolean g = on();
            fill.setColor(g ? 0x1FFFFFFF : color);
            c.drawRoundRect(box, r, r, fill);
            if (g) {
                float e = edge.getStrokeWidth() / 2;
                box.inset(e, e);
                c.drawRoundRect(box, r, r, edge);
            }
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
        public void getOutline(android.graphics.Outline o) {
            o.setRoundRect(getBounds(), r);
        }
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
            int div = 8 + frost() / 5; // more frost: softer blur (8 … 28)
            int sw = Math.max(1, w / div), sh = Math.max(1, h / div);
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
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
                shine = new Paint(Paint.ANTI_ALIAS_FLAG), pic = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF box = new RectF();
        private final Path clip = new Path();
        private final int[] at = new int[2], ro = new int[2], now = new int[2];
        private int drawnX = Integer.MIN_VALUE, drawnY;
        private View followed;

        /**
         * A card that moves without redrawing (its parent slides or scrolls) would show the wallpaper from where it
         * was: before each frame, check where it is and redraw it if it has moved.
         */
        private void follow(View host) {
            if (followed == host) return;
            followed = host;
            host.getViewTreeObserver().addOnPreDrawListener(() -> {
                if (!host.isShown() || !on()) return true;
                host.getLocationInWindow(now);
                if (now[0] != drawnX || now[1] != drawnY) invalidateSelf();
                return true;
            });
        }

        Card(int color, float radius) {
            this.color = color;
            this.r = Ui.uf(radius);
            fill.setColor(color);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1f, Ui.uf(1)));
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
            drawnX = at[0];
            drawnY = at[1];
            follow(host);
            c.drawBitmap(b, ro[0] - at[0], ro[1] - at[1], pic); // the blurred wallpaper right behind this card
            // dark frost so text stays readable over a bright wallpaper; a coloured card (an alert) keeps its colour as tinted glass
            int fr = frost(); // 0 clear … 100 heavily frosted (Settings → Wallpaper)
            int dark = Math.round(0x10 + fr * (0xD0 - 0x10) / 100f), tint = Math.round(0x60 + fr * (0xE6 - 0x60) / 100f);
            fill.setColor(color == Ui.CARD || color == Ui.CARD2 ? (dark << 24) | 0x141A24 : (color & 0x00FFFFFF) | (tint << 24));
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
        // sky: its colours follow the time of day (sunrise and sunset where the car is) and the weather
        Sky k = Sky.now();
        p.setShader(new LinearGradient(0, 0, 0, hz, k.sky, new float[]{0f, 0.45f, 0.8f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, hz, p);
        // sun (or moon) glow, low at dawn and dusk, high at midday
        float sy = hz - (hz * 0.85f) * k.sunHeight;
        p.setShader(new android.graphics.RadialGradient(w * k.sunX, sy, w * 0.35f, k.glow, k.glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
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
        p.setColor(k.hills);
        c.drawPath(hills, p);
        // ground either side: dark veld
        p.setShader(new LinearGradient(0, hz, 0, h, k.ground, 0xFF0C120E, Shader.TileMode.CLAMP));
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

    /** The sky for now: time of day (from sunrise and sunset) and the weather. */
    static final class Sky {
        int[] sky;
        int glow, ground, hills;
        float sunHeight, sunX; // 0 horizon … 1 high; left … right across the day
        int weather; // 0 clear, 1 cloudy, 2 fog, 3 rain, 4 storm
        boolean night;

        // a preview (the welcome wizard): this hour and weather for a few seconds instead of the real ones
        static double previewHour = -1;
        static int previewCode = -1;

        static Sky now() {
            Sky k = new Sky();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            double h = previewHour >= 0 ? previewHour : cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60.0;
            double[] t = Prayer.times(0);
            double rise = t != null ? t[1] : 6, set = t != null ? t[4] : 18.5;
            // 0 deep night, 1 dawn/dusk, 2 day: how far into the day we are
            double dayFrac = (h - rise) / Math.max(1, set - rise);
            k.night = h < rise - 0.6 || h > set + 0.6;
            k.sunHeight = (float) Math.max(0, Math.sin(Math.PI * Math.max(0, Math.min(1, dayFrac))));
            k.sunX = (float) (0.2 + 0.6 * Math.max(0, Math.min(1, dayFrac)));
            boolean low = !k.night && k.sunHeight < 0.25f; // golden hour
            int wc = previewCode >= 0 ? previewCode : Weather.ok ? Weather.code : 0; // WMO weather codes
            k.weather = wc >= 95 ? 4 : (wc >= 51 && wc <= 82) ? 3 : (wc == 45 || wc == 48) ? 2 : wc >= 2 ? 1 : 0;
            if (k.night) {
                k.sky = new int[]{0xFF050912, 0xFF0B1430, 0xFF18224A, 0xFF2A2F5A};
                k.glow = 0x553A4A8A;
                k.ground = 0xFF0E1612;
                k.hills = 0xFF121528;
            } else if (low) { // dawn or dusk
                k.sky = new int[]{0xFF0A1226, 0xFF1C2A52, 0xFF6A4A6E, 0xFFE58A4E};
                k.glow = 0x88FFC27A;
                k.ground = 0xFF1E2A22;
                k.hills = 0xFF20223A;
            } else { // day
                k.sky = new int[]{0xFF2F6FC2, 0xFF5A93D8, 0xFF9CC3EA, 0xFFD8E8F2};
                k.glow = 0x66FFF4D6;
                k.ground = 0xFF3A5A34;
                k.hills = 0xFF4A6A7A;
            }
            // weather greys the sky out (more for rain and storms) and dims the ground
            float g = k.weather == 0 ? 0 : k.weather == 1 ? 0.45f : k.weather == 2 ? 0.65f : k.weather == 3 ? 0.7f : 0.85f;
            int grey = k.night ? 0xFF1A1E26 : 0xFF7A828C;
            if (k.weather == 4) grey = k.night ? 0xFF111319 : 0xFF3E434C;
            for (int i = 0; i < k.sky.length; i++) k.sky[i] = mix(k.sky[i], grey, g);
            k.glow = mix(k.glow, k.glow & 0x00FFFFFF, g);
            k.ground = mix(k.ground, 0xFF1A1E1C, g * 0.6f);
            k.hills = mix(k.hills, grey, g * 0.5f);
            return k;
        }

        static int mix(int a, int b, float f) {
            float r = 1 - f;
            return ((int) (((a >>> 24) & 255) * r + ((b >>> 24) & 255) * f) << 24) | ((int) (((a >> 16) & 255) * r + ((b >> 16) & 255) * f) << 16)
                    | ((int) (((a >> 8) & 255) * r + ((b >> 8) & 255) * f) << 8) | (int) ((a & 255) * r + (b & 255) * f);
        }

        /** A key that changes when the picture should be redrawn (about every 10 minutes, or the weather changes). */
        static String key() {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            return cal.get(java.util.Calendar.HOUR_OF_DAY) + ":" + cal.get(java.util.Calendar.MINUTE) / 10 + ":" + (Weather.ok ? Weather.code : -1)
                    + ":" + previewHour + ":" + previewCode;
        }
    }
}
