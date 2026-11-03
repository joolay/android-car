package com.carbox.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Every widget that can sit in a tile. */
final class Widgets {
    private Widgets() {
    }

    static final String[][] CATALOG = {
            {"carconnect", "Car Connect", "CarPlay, Android Auto, mirror or native car"},
            {"speed", "Speed", "Current speed and today's top speed"},
            {"trip", "Trip", "Distance, time driving and average speed"},
            {"park", "Park here", "Save where you parked (and send it to Home Assistant)"},
            {"safety", "Safety check", "Pre-drive road safety checklist: tick off seatbelts, mirrors, lights, tyres and more"},
            {"logbook", "Logbook", "Logs every trip's mileage by itself. Mark trips Business or Personal, export to a file or email"},
            {"rest", "Driving timer", "Time since your last break, with a beep or voice reminder to rest"},
            {"heading", "Heading", "Compass direction and altitude"},
            {"weather", "Weather", "Weather where you are"},
            {"goto", "Go to", "One tap to navigate home or to work"},
            {"phone", "Phone", "Contacts, favourites, recent calls and keypad"},
            {"quickdial", "Quick dial", "Four favourite contacts"},
            {"contact", "Contact", "One contact with their photo"},
            {"whatsapp", "WhatsApp", "WhatsApp Web: your chats, linked from your phone"},
            {"nextprayer", "Next prayer", "The next prayer and a countdown"},
            {"prayertimes", "Prayer times", "Today's five prayer times"},
            {"qibla", "Qibla", "Direction of the Kaaba from here"},
            {"ha", "Home Assistant", "Your own smart home toggles"}};

    /** Categories for the tile and Left Widget choosers (Music is a Left Widget only). */
    static final String[][] CATS = {{"Driving", "speed,trip,rest,park,safety,logbook,heading,goto"}, {"Phone", "phone,quickdial,contact,whatsapp"},
            {"Car & home", "carconnect,ha"}, {"Weather", "weather"}, {"Faith", "nextprayer,prayertimes,qibla"}};

    static String name(String id) {
        if ("music".equals(id)) return "Music";
        for (String[] w : CATALOG) if (w[0].equals(id)) return w[1];
        return "none".equals(id) ? "Empty" : id;
    }

    /**
     * Grid cells a widget covers: {wide, tall}. Every widget is 1x1 for now; bigger ones (2x1 wide,
     * 1x2 tall, 2x2) just return their size here and the grid makes room for them.
     */
    static int[] size(String id) {
        switch (id == null ? "" : id) {
            default:
                return new int[]{1, 1};
        }
    }

    /** Widgets that can be on the home screen more than once (each set up separately). */
    static boolean multiple(String id) {
        return "contact".equals(id);
    }

    static boolean configurable(String id) {
        return "goto".equals(id) || "quickdial".equals(id) || "contact".equals(id) || "ha".equals(id) || "rest".equals(id) || "safety".equals(id) || "logbook".equals(id);
    }

    /**
     * Every widget has a design per place it can show:
     * MAIN on a left stage (roomier), EXPANDED on the Centre Stage (its full page), TILE in the Tile Drawer.
     */
    enum Profile {MAIN, EXPANDED, TILE}

    /** Tile sizes on the 4×2 Tile Drawer grid, width × height. Only 1×1 is made for now. */
    static final int[][] SPANS = {{1, 1}, {2, 1}, {1, 2}, {2, 2}};

    /** Which sizes a widget's Tile profile comes in (more get added when they're designed). */
    static int[][] spans(String id) {
        return new int[][]{{1, 1}};
    }

    /** A tile's size from its saved spec (1×1 unless set). */
    static int[] span(JSONObject spec) {
        return new int[]{Math.max(1, spec.optInt("w", 1)), Math.max(1, spec.optInt("h", 1))};
    }

    abstract static class W {
        final Context c;
        final int index;
        final JSONObject spec;
        final JSONObject cfg;
        final boolean tl; // MAIN profile (a left stage)
        final Profile profile;
        View v;

        /** Open this widget's EXPANDED profile on the Centre Stage (by default what tapping it opens). */
        void expand() {
            if (v != null) v.performClick();
        }

        W(Context c, int index, JSONObject spec) {
            this.c = c;
            tl = TL;
            profile = TL ? Profile.MAIN : Profile.TILE;
            this.index = index;
            this.spec = spec;
            JSONObject o = spec.optJSONObject("cfg");
            cfg = o != null ? o : new JSONObject();
        }

        void tick() {
        }

        void save() {
            try {
                spec.put("cfg", cfg);
            } catch (Exception ignored) {
            }
            TileDrawer.setSpec(index, spec);
        }
    }

    /** A widget in the given profile (MAIN for a left stage, TILE for the Tile Drawer). */
    static W make(Context c, int index, JSONObject spec, Profile p) {
        boolean was = TL;
        TL = p == Profile.MAIN;
        try {
            return make(c, index, spec);
        } finally {
            TL = was;
        }
    }

    static W make(Context c, int index, JSONObject spec) {
        String id = spec.optString("id");
        if (!Addons.unlocked(id)) return new Empty(c, index, spec); // an add-on that isn't unlocked
        switch (id) {
            case "carconnect":
                return new CarConnect(c, index, spec);
            case "speed":
                return new Speed(c, index, spec);
            case "trip":
                return new Trip(c, index, spec);
            case "safety":
                return new Safety.Tile(c, index, spec);
            case "logbook":
                return new Logbook.Tile(c, index, spec);
            case "rest":
                return new RestW(c, index, spec);
            case "park":
                return new Park(c, index, spec);
            case "heading":
                return new Heading(c, index, spec);
            case "weather":
                return new WeatherW(c, index, spec);
            case "goto":
                return new GoTo(c, index, spec);
            case "quickdial":
                return new QuickDial(c, index, spec);
            case "phone":
                return new PhoneW(c, index, spec);
            case "contact":
                return new Contact(c, index, spec);
            case "nextprayer":
                return new NextPrayer(c, index, spec);
            case "prayertimes":
                return new PrayerTimes(c, index, spec);
            case "qibla":
                return new Qibla(c, index, spec);
            case "ha":
                return new HA.Widget(c, index, spec);
            case "whatsapp":
                return new WhatsW(c, index, spec);
            default:
                return new Empty(c, index, spec);
        }
    }

    /**
     * A live, non-touchable preview of a widget: built at real tile size, then scaled to fit
     * whatever space it's given (the widget gallery and the tile editor).
     */
    /** True while a widget is being made in its MAIN profile (a left stage: roomier design). */
    static boolean TL;

    /** Text size: a size up in the Top Left Widget. */
    static float ts(float s) {
        return TL ? s * 1.25f : s;
    }

    /** Preview of a widget in its Top Left design (same shape as the Top Left Widget). */
    static View preview(Context c, String id) {
        int idx = LeftStages.tileIndex(id);
        JSONObject spec = idx >= 0 ? TileDrawer.specs().optJSONObject(idx) : TileDrawer.widget(id);
        int fw = Ui.u(200), fh = Ui.u(232);
        if (MainActivity.I != null && MainActivity.I.map != null && MainActivity.I.map.view.getWidth() > 0) {
            fw = MainActivity.I.map.view.getWidth();
            fh = MainActivity.I.map.view.getHeight();
        }
        TL = true;
        try {
            return preview(c, spec, fw, fh);
        } finally {
            TL = false;
        }
    }

    static View preview(Context c, JSONObject spec) {
        int tw = Ui.u(122), th = Ui.u(165);
        if (MainActivity.I != null && MainActivity.I.tiles.view.getWidth() > 0) {
            View t = MainActivity.I.tiles.view;
            tw = Math.round((t.getWidth() - Ui.u(8) * 3) / 4f);
            th = Math.round((t.getHeight() - Ui.u(8)) / 2f);
        }
        return preview(c, spec, tw, th);
    }

    private static View preview(Context c, JSONObject spec, int tw, int th) {
        final View live;
        try {
            W w = make(c, -1, spec);
            try {
                w.tick();
            } catch (Exception ignored) {
            }
            live = w.v;
        } catch (Exception e) {
            return new View(c);
        }
        final int fw = tw, fh = th;
        FrameLayout box = new FrameLayout(c) {
            @Override
            protected void onMeasure(int ws, int hs) {
                int w = MeasureSpec.getSize(ws), h = MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(hs) : Math.round(w * fh / (float) fw); // keep the tile's shape
                setMeasuredDimension(w, h);
                live.measure(MeasureSpec.makeMeasureSpec(fw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(fh, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                float s = Math.min((r - l) / (float) fw, (b - t) / (float) fh);
                live.layout(0, 0, fw, fh);
                live.setPivotX(0);
                live.setPivotY(0);
                live.setScaleX(s);
                live.setScaleY(s);
                live.setTranslationX(((r - l) - fw * s) / 2f);
            }

            @Override
            public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
                return true; // a picture of the widget, not the widget
            }
        };
        box.setClipChildren(true);
        box.addView(live);
        Maps.clip(box, 14);
        return box;
    }

    /** Settings page for widgets that need setting up. */
    static void configure(int index) {
        JSONObject spec = TileDrawer.specs().optJSONObject(index);
        if (spec == null) return;
        String id = spec.optString("id");
        if ("goto".equals(id)) gotoSetup(index);
        else if ("quickdial".equals(id)) quickDialSetup(index);
        else if ("contact".equals(id)) contactSetup(index);
        else if ("ha".equals(id)) HA.setup(index);
        else if ("rest".equals(id)) Rest.setup();
        else if ("safety".equals(id)) Safety.list();
        else if ("logbook".equals(id)) Logbook.settings();
    }

    // ------------------------------------------------------------------ shared bits

    static LinearLayout card(Context c, int bg) {
        LinearLayout l = Ui.col(c);
        l.setBackground(bg == Ui.CARD ? Themes.card(bg, 16) : Ui.round(bg, 16)); // glass in the Glass theme
        Ui.pad(l, 14, 14, 14, 14);
        return l;
    }

    static TextView label(Context c, String s) {
        TextView t = Ui.line(c, s, 11, Ui.SUB, true);
        t.setLetterSpacing(0.06f);
        return t;
    }

    static boolean mph() {
        return Prefs.flag("mph", false);
    }

    static LinearLayout.LayoutParams top(float m) {
        LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
        lp.topMargin = Ui.u(m);
        return lp;
    }

    static final class Empty extends W {
        Empty(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            l.addView(Ui.text(c, "Tap to choose a tile", 13, Ui.SUB, false));
            l.setOnClickListener(x -> AppPanels.gallery(index));
            v = l;
        }
    }

    // ------------------------------------------------------------------ Car Connect

    static final class CarConnect extends W {
        CarConnect(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = Ui.col(c);
            l.setBackground(Themes.on() ? Themes.card(0xFF1F4E9A, 16) : new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1F4E9A, 0xFF13284A}));
            if (l.getBackground() instanceof GradientDrawable) ((GradientDrawable) l.getBackground()).setCornerRadius(Ui.uf(16)); // glass cards are rounded already
            Ui.pad(l, 14, 16, 14, 16);
            FrameLayout ic = new FrameLayout(c);
            ic.setBackground(Ui.oval(0x33FFFFFF));
            ic.addView(Ui.icon(c, R.drawable.ic_cast, Ui.TEXT), Ui.flpu(26, 26, Gravity.CENTER));
            l.addView(ic, Ui.lpu(48, 48));
            l.addView(new View(c), Ui.weight(1, false));
            l.addView(Ui.text(c, "Car Connect", 16, Ui.TEXT, true));
            TextView sub = Ui.text(c, "CarPlay · Auto\nMirror · Native", 11, 0xCCFFFFFF, false);
            sub.setLineSpacing(Ui.uf(2), 1);
            l.addView(sub, top(4));
            l.setOnClickListener(x -> AppPanels.connect());
            Ui.press(l);
            v = l;
        }
    }

    // ------------------------------------------------------------------ Speed

    static final class Dial extends View {
        float value, max = 180;
        String big = "--", unit = "km/h";
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Dial(Context c) {
            super(c);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight());
            float sw = Ui.uf(7);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            r.set(cx - s / 2 + sw, cy - s / 2 + sw, cx + s / 2 - sw, cy + s / 2 - sw);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(Ui.CARD3);
            cv.drawArc(r, 135, 270, false, p);
            p.setColor(SpeedLimit.over() ? Ui.RED : value > max * 0.67f ? Ui.AMBER : Ui.ACCENT);
            cv.drawArc(r, 135, 270 * Math.max(0, Math.min(1, value / max)), false, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Ui.TEXT);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(Ui.uf(30));
            cv.drawText(big, cx, cy + Ui.uf(8), p);
            p.setTypeface(Typeface.DEFAULT);
            p.setColor(Ui.SUB);
            p.setTextSize(Ui.uf(11));
            cv.drawText(unit, cx, cy + Ui.uf(26), p);
        }
    }

    static final class Speed extends W {
        private final Dial dial;
        private final TextView topT, limit;
        private final LinearLayout box;
        private boolean lit;

        Speed(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            box = l;
            LinearLayout head = Ui.row(c);
            head.addView(label(c, "SPEED"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            // the road's speed limit, as a round road sign
            limit = Ui.text(c, "", 12, 0xFF111111, true);
            limit.setGravity(Gravity.CENTER);
            limit.setBackground(Ui.outline(0xFFFFFFFF, 0xFFE24B4A, 3, 15));
            limit.setVisibility(View.GONE);
            head.addView(limit, Ui.lpu(30, 30));
            l.addView(head);
            dial = new Dial(c);
            l.addView(dial, Ui.weight(1, false));
            topT = Ui.line(c, "", ts(11), Ui.SUB, false);
            topT.setGravity(Gravity.CENTER);
            l.addView(topT, Ui.lp(Ui.MATCH, Ui.WRAP));
            v = l;
            tick();
        }

        @Override
        void tick() {
            boolean m = mph();
            float f = m ? 2.23694f : 3.6f;
            dial.unit = m ? "mph" : "km/h";
            dial.max = m ? 110 : 180;
            if (Gps.speed >= 0 && Gps.fresh()) {
                dial.value = Gps.speed * f;
                dial.big = String.valueOf(Math.round(dial.value));
            } else {
                dial.value = 0;
                dial.big = "--";
            }
            dial.invalidate();
            float top = Gps.topSpeedToday * (m ? 0.621371f : 1f);
            topT.setText("Top today " + Math.round(top));
            // speed limit sign, and light the tile up red when over it
            if (SpeedLimit.kmh > 0) {
                limit.setText(String.valueOf(Math.round(SpeedLimit.kmh * (m ? 0.621371f : 1f))));
                limit.setVisibility(View.VISIBLE);
            } else limit.setVisibility(View.GONE);
            boolean over = SpeedLimit.over();
            if (over) dial.max = Math.max(dial.max, dial.value);
            if (over != lit) {
                lit = over;
                box.setBackground(over ? Themes.edged(0xFF5A1010, 0xFFE24B4A, 16) : Themes.card(Ui.CARD, 16));
                box.animate().cancel();
                box.setAlpha(1f);
                if (over) pulse();
            }
            if (over) {
                topT.setText("Over the limit");
                topT.setTextColor(0xFFFF8A80);
            } else topT.setTextColor(Ui.SUB);
        }

        /** A gentle glow pulse while over the limit. */
        private void pulse() {
            if (!lit) return;
            box.animate().alpha(0.7f).setDuration(Ui.ms(450)).withEndAction(() -> box.animate().alpha(1f).setDuration(Ui.ms(450)).withEndAction(this::pulse).start()).start();
        }
    }

    // ------------------------------------------------------------------ Trip

    static final class Trip extends W {
        private final TextView dist, unit, drive, avg;

        Trip(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            l.addView(label(c, "TRIP"));
            l.addView(new View(c), Ui.weight(1, false));
            LinearLayout r = Ui.row(c);
            r.setGravity(Gravity.BOTTOM);
            dist = Ui.text(c, "0.0", tl ? 44 : 30, Ui.TEXT, true);
            r.addView(dist);
            unit = Ui.text(c, " km", ts(13), Ui.SUB, false);
            unit.setPadding(0, 0, 0, Ui.u(4));
            r.addView(unit);
            l.addView(r);
            l.addView(new View(c), Ui.weight(1, false));
            drive = Ui.line(c, "", ts(12), Ui.TEXT2, false);
            l.addView(drive);
            avg = Ui.line(c, "", ts(12), Ui.TEXT2, false);
            l.addView(avg, top(4));
            l.setOnClickListener(x -> Panels.show(() -> {
                LinearLayout b = Ui.col(Panels.ctx());
                b.addView(Ui.text(Panels.ctx(), "Start a new trip from zero?", 15, Ui.TEXT2, false));
                LinearLayout btns = Ui.row(Panels.ctx());
                btns.addView(Ui.button(Panels.ctx(), "Reset trip", true, () -> {
                    Gps.resetTrip();
                    Panels.close();
                    tick();
                }));
                btns.addView(Ui.gap(Panels.ctx(), 10, 1));
                btns.addView(Ui.button(Panels.ctx(), "Cancel", false, Panels::close));
                b.addView(btns, top(18));
                return Panels.panel("Trip", b, 400, 200);
            }));
            v = l;
            tick();
        }

        @Override
        void tick() {
            boolean m = mph();
            double d = Gps.tripMeters / (m ? 1609.34 : 1000.0);
            dist.setText(String.format(Locale.US, d < 100 ? "%.1f" : "%.0f", d));
            unit.setText(m ? " mi" : " km");
            drive.setText(Ui.dur(Gps.tripMovingMs) + " driving");
            double hrs = Gps.tripMovingMs / 3600000.0;
            avg.setText(hrs > 0.01 ? "Avg " + Math.round(d / hrs) + (m ? " mph" : " km/h") : "Avg --");
        }
    }

    // ------------------------------------------------------------------ Park here

    static final class Park extends W {
        private final TextView title, sub, sub2;
        private final LinearLayout box;

        Park(Context c, int i, JSONObject s) {
            super(c, i, s);
            box = card(c, Ui.CARD);
            LinearLayout hd = Ui.row(c); // PARKING with a small P badge on the right
            hd.setGravity(Gravity.CENTER_VERTICAL);
            hd.addView(label(c, "PARKING"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            FrameLayout ic = new FrameLayout(c);
            ic.setBackground(Ui.oval(Ui.ACCENT));
            ic.addView(Ui.text(c, "P", 16, Ui.TEXT, true), Ui.flpu(16, 22, Gravity.CENTER));
            hd.addView(ic, Ui.lpu(28, 28));
            box.addView(hd, Ui.lp(Ui.MATCH, Ui.WRAP));
            box.addView(new View(c), Ui.weight(1, false));
            title = Ui.line(c, "", tl ? 34 : 26, Ui.TEXT, true); // the time it was parked, big
            box.addView(title);
            sub = Ui.text(c, "", ts(13), Ui.TEXT2, false); // town (two lines if it needs them)
            sub.setMaxLines(2);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            box.addView(sub, top(4));
            sub2 = Ui.line(c, "", ts(12), Ui.SUB, false); // how long ago
            box.addView(sub2, top(2));
            box.addView(new View(c), Ui.weight(1, false));
            box.setOnClickListener(x -> tapped());
            Ui.press(box);
            v = box;
            tick();
        }

        private void tapped() {
            if (Prefs.lng("park_at", 0) == 0) savePark();
            else Panels.show(() -> { // already parked: open, park again or clear
                Context k = Panels.ctx();
                LinearLayout b = Ui.col(k);
                b.addView(Ui.text(k, sub.getText() + " · " + sub2.getText(), 14, Ui.TEXT2, false));
                b.addView(Panels.item("Show in Maps", null, () -> {
                    Panels.close();
                    Actions.navigateTo(k, Prefs.str("park_lat", "") + "," + Prefs.str("park_lon", ""));
                }), top(8));
                b.addView(Panels.item("Park here again", "Replace with where the car is now", () -> {
                    Panels.close();
                    savePark();
                }));
                b.addView(Panels.item("Clear", null, () -> {
                    Prefs.put("park_at", 0L);
                    Panels.close();
                    tick();
                }));
                return Panels.panel("Parked", b, 460, 320);
            });
        }

        private void savePark() {
            if (!Gps.has()) {
                android.widget.Toast.makeText(c, "Waiting for GPS", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs.put("park_lat", String.valueOf(Gps.lat));
            Prefs.put("park_lon", String.valueOf(Gps.lon));
            Prefs.put("park_town", Gps.town == null ? "" : Gps.town);
            Prefs.put("park_at", System.currentTimeMillis());
            if (HA.configured()) CarHA.send(true); // also tells Home Assistant (if set up)
            android.widget.Toast.makeText(c, "Parking spot saved", android.widget.Toast.LENGTH_SHORT).show();
            tick();
        }

        @Override
        void tick() {
            long at = Prefs.lng("park_at", 0);
            if (at == 0) {
                title.setText("Park here");
                sub.setText("Tap to save this spot");
                sub2.setVisibility(View.GONE);
                box.setBackground(Themes.card(Ui.CARD, 16));
            } else {
                title.setText(new java.text.SimpleDateFormat(Prefs.flag(Prefs.H24, true) ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new java.util.Date(at)));
                String town = Prefs.str("park_town", "");
                sub.setText(town.isEmpty() ? "Parked here" : town);
                sub2.setText("Parked " + NewsTicker.ago(at));
                sub2.setVisibility(View.VISIBLE);
                box.setBackground(Themes.edged(Ui.CARD, Ui.ACCENT, 16)); // parked: blue edge (glass inside in Glass)
            }
        }
    }

    // ------------------------------------------------------------------ Driving timer

    /** Thin rounded progress bar. */
    static final class Bar extends View {
        float value;
        int color = Ui.ACCENT;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float h = getHeight(), rad = h / 2;
            r.set(0, 0, getWidth(), h);
            p.setColor(Ui.CARD3);
            cv.drawRoundRect(r, rad, rad, p);
            r.right = Math.max(h, getWidth() * Math.max(0, Math.min(1, value)));
            p.setColor(color);
            if (value > 0) cv.drawRoundRect(r, rad, rad, p);
        }
    }

    static final class RestW extends W {
        private final LinearLayout box;
        private final TextView head, time, sub, sub2;
        private final Bar bar;

        RestW(Context c, int i, JSONObject s) {
            super(c, i, s);
            box = card(c, Ui.CARD);
            head = label(c, "DRIVING");
            box.addView(head);
            box.addView(new View(c), Ui.weight(1, false));
            time = Ui.text(c, "0:00", tl ? 44 : 30, Ui.TEXT, true);
            box.addView(time);
            bar = new Bar(c);
            LinearLayout.LayoutParams bl = Ui.lp(Ui.MATCH, Ui.u(6));
            bl.topMargin = Ui.u(6);
            box.addView(bar, bl);
            box.addView(new View(c), Ui.weight(1, false));
            sub = Ui.line(c, "", ts(12), Ui.TEXT2, false);
            box.addView(sub);
            sub2 = Ui.line(c, "", ts(12), Ui.SUB, false);
            box.addView(sub2, top(2));
            box.setOnClickListener(x -> Rest.setup());
            Ui.press(box);
            v = box;
            tick();
        }

        @Override
        void tick() {
            long min = Rest.driveMs / 60000;
            time.setText((min / 60) + ":" + Ui.two((int) (min % 60)));
            bar.value = Rest.progress();
            boolean due = Rest.due();
            long stopped = Rest.stoppedMs();
            int bg;
            String l2 = "";
            if (!Rest.on()) {
                head.setText("DRIVING");
                sub.setText("Reminders off");
                bar.color = Ui.MUTED;
                bg = Ui.CARD;
            } else if (due && stopped > 60000) {
                long left = Rest.breakMin() - stopped / 60000;
                head.setText("RESTING");
                sub.setText(left > 0 ? "Break done in" : "Break done");
                if (left > 0) l2 = left + " min";
                bar.color = Ui.TEAL;
                bg = Ui.CARD;
            } else if (due) {
                head.setText("TAKE A BREAK");
                sub.setText("Stop and rest");
                l2 = Rest.breakMin() + " minutes";
                bar.color = Ui.AMBER;
                bg = 0xFF4A3410;
            } else {
                head.setText("DRIVING");
                long leftMin = Rest.limitMin() - min;
                long lm = Math.max(0, leftMin); // short so it fits: "Break in 2 h", "1 h 25", "40 min"
                sub.setText("Break in " + (lm < 60 ? lm + " min" : lm % 60 == 0 ? (lm / 60) + " h" : (lm / 60) + " h " + Ui.two((int) (lm % 60))));
                if (Rest.limitKm() > 0) {
                    boolean m = mph();
                    double left = (Rest.limitKm() * 1000.0 - Rest.meters) / (m ? 1609.34 : 1000.0);
                    l2 = "or " + Math.max(0, Math.round(left)) + (m ? " mi" : " km");
                }
                bar.color = bar.value > 0.85f ? Ui.AMBER : Ui.ACCENT;
                bg = Ui.CARD;
            }
            sub2.setText(l2);
            sub2.setVisibility(l2.isEmpty() ? View.GONE : View.VISIBLE);
            box.setBackground(Themes.card(bg, 16)); // amber/red when a break is due: tinted glass in Glass
            bar.invalidate();
        }
    }

    // ------------------------------------------------------------------ Heading

    /** A compass dial. rotation = degrees the dial is turned (so the car's heading points up). */
    static final class Compass extends View {
        float rotation;          // dial rotation
        float marker = Float.NaN; // marker bearing (e.g. qibla), relative to north
        boolean arrow = true;
        Drawable markerIcon;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Compass(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f, cy = getHeight() / 2f, rad = s / 2 - Ui.uf(2);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.CARD2);
            cv.drawCircle(cx, cy, rad, p);
            cv.save();
            cv.rotate(-rotation, cx, cy);
            p.setColor(Ui.MUTED);
            for (int i = 0; i < 72; i++) {
                float a = (float) Math.toRadians(i * 5);
                float len = i % 18 == 0 ? Ui.uf(7) : i % 2 == 0 ? Ui.uf(4) : Ui.uf(2);
                p.setStrokeWidth(i % 18 == 0 ? Ui.uf(2) : Ui.uf(1));
                cv.drawLine(cx + (float) Math.sin(a) * (rad - Ui.uf(3)), cy - (float) Math.cos(a) * (rad - Ui.uf(3)),
                        cx + (float) Math.sin(a) * (rad - Ui.uf(3) - len), cy - (float) Math.cos(a) * (rad - Ui.uf(3) - len), p);
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(Ui.uf(11));
            String[] nesw = {"N", "E", "S", "W"};
            for (int i = 0; i < 4; i++) {
                float a = (float) Math.toRadians(i * 90);
                float tr = rad - Ui.uf(18);
                p.setColor(i == 0 ? Ui.RED : Ui.SUB);
                float x = cx + (float) Math.sin(a) * tr, y = cy - (float) Math.cos(a) * tr + Ui.uf(4);
                cv.save();
                cv.rotate(rotation, x, y - Ui.uf(4));
                cv.drawText(nesw[i], x, y, p);
                cv.restore();
            }
            if (!Float.isNaN(marker)) {
                float a = (float) Math.toRadians(marker);
                p.setColor(Ui.GREEN);
                p.setStrokeWidth(Ui.uf(3));
                p.setStrokeCap(Paint.Cap.ROUND);
                cv.drawLine(cx, cy, cx + (float) Math.sin(a) * (rad - Ui.uf(26)), cy - (float) Math.cos(a) * (rad - Ui.uf(26)), p);
                if (markerIcon != null) {
                    float mx = cx + (float) Math.sin(a) * (rad - Ui.uf(14)), my = cy - (float) Math.cos(a) * (rad - Ui.uf(14));
                    int h = Ui.u(8);
                    cv.save();
                    cv.rotate(rotation, mx, my);
                    markerIcon.setBounds((int) mx - h, (int) my - h, (int) mx + h, (int) my + h);
                    markerIcon.draw(cv);
                    cv.restore();
                }
            }
            cv.restore();
            if (arrow) {
                // the car: fixed arrow pointing up
                android.graphics.Path path = new android.graphics.Path();
                float h = rad * 0.36f;
                path.moveTo(cx, cy - h);
                path.lineTo(cx + h * 0.55f, cy + h * 0.7f);
                path.lineTo(cx, cy + h * 0.35f);
                path.lineTo(cx - h * 0.55f, cy + h * 0.7f);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(Ui.ACCENT);
                cv.drawPath(path, p);
            } else {
                p.setColor(Ui.TEXT);
                cv.drawCircle(cx, cy, Ui.uf(4), p);
            }
        }
    }

    static final class Heading extends W {
        private final Compass compass;
        private final TextView dir, alt;

        Heading(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            l.addView(label(c, "HEADING"));
            compass = new Compass(c);
            LinearLayout.LayoutParams cl = Ui.weight(1, false);
            cl.topMargin = Ui.u(6);
            cl.bottomMargin = Ui.u(6);
            l.addView(compass, cl);
            dir = Ui.line(c, "--", tl ? 24 : 16, Ui.TEXT, true);
            dir.setGravity(Gravity.CENTER);
            l.addView(dir, Ui.lp(Ui.MATCH, Ui.WRAP));
            alt = Ui.line(c, "", ts(11), Ui.SUB, false);
            alt.setGravity(Gravity.CENTER);
            l.addView(alt, top(3));
            l.setOnClickListener(x -> InfoPanels.heading());
            Ui.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
            float h = Gps.heading(); // held at the last direction while parked
            if (!Float.isNaN(h)) {
                compass.rotation = h;
                dir.setText(Weather.compass(Math.round(h)) + "  " + Math.round(h) + "°");
            } else {
                compass.rotation = 0;
                dir.setText(Gps.fresh() ? "Drive to set" : "No GPS");
            }
            compass.invalidate();
            alt.setText(Double.isNaN(Gps.alt) ? (!Float.isNaN(h) && !Gps.moving() ? "Parked" : "") : "▲ " + Math.round(mph() ? Gps.alt * 3.28084 : Gps.alt) + (mph() ? " ft" : " m") + " high");
        }
    }

    // ------------------------------------------------------------------ Weather

    static final class WeatherW extends W {
        private final ImageView icon;
        private final TextView temp, desc, hilo, place;

        WeatherW(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            place = Ui.line(c, "", ts(11), Ui.SUB, true);
            l.addView(place);
            icon = Ui.icon(c, R.drawable.ic_w_partly, 0);
            temp = Ui.text(c, "--°", tl ? 48 : 34, Ui.TEXT, true);
            if (tl) { // Top Left: big icon beside a big temperature
                l.addView(new View(c), Ui.weight(1, false));
                LinearLayout r = Ui.row(c);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.addView(icon, Ui.lpu(68, 68));
                LinearLayout.LayoutParams tl2 = Ui.lp(Ui.WRAP, Ui.WRAP);
                tl2.leftMargin = Ui.u(10);
                r.addView(temp, tl2);
                l.addView(r);
                l.addView(new View(c), Ui.weight(1, false));
            } else {
                LinearLayout.LayoutParams il = Ui.lpu(48, 48);
                il.topMargin = Ui.u(10);
                l.addView(icon, il);
                l.addView(new View(c), Ui.weight(1, false));
                l.addView(temp);
            }
            desc = Ui.line(c, "", ts(12), Ui.TEXT2, false);
            l.addView(desc, top(2));
            hilo = Ui.line(c, "", ts(12), Ui.SUB, false);
            l.addView(hilo, top(3));
            l.setOnClickListener(x -> InfoPanels.weather(0));
            Ui.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
            place.setText(Gps.town != null ? Gps.town.toUpperCase(Locale.ROOT) : "WEATHER");
            if (!Weather.ok) {
                desc.setText(Gps.has() ? "Loading…" : "Waiting for location");
                return;
            }
            icon.setImageResource(Weather.icon(Weather.code, Weather.isDay));
            temp.setText(Weather.t(Weather.temp));
            desc.setText(Weather.describe(Weather.code));
            Weather.Day d = Weather.today();
            if (d != null) hilo.setText("H " + Weather.t(d.hi) + "  L " + Weather.t(d.lo));
        }
    }

    // ------------------------------------------------------------------ WhatsApp Web

    static final class WhatsW extends W {
        private final TextView sub, badge;

        WhatsW(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            LinearLayout head = Ui.row(c);
            head.addView(label(c, "WHATSAPP"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            badge = Ui.line(c, "", ts(12), Ui.TEXT, true); // unread chats
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(Ui.round(Ui.GREEN, 11));
            Ui.pad(badge, 7, 2, 7, 2);
            badge.setVisibility(View.GONE);
            head.addView(badge);
            l.addView(head);
            l.addView(new View(c), Ui.weight(1, false));
            android.widget.FrameLayout ic = new android.widget.FrameLayout(c);
            ic.setBackground(Ui.oval(0xFF1F7A46));
            ic.addView(Ui.icon(c, R.drawable.ic_chat, Ui.TEXT), Ui.flpu(tl ? 34 : 26, tl ? 34 : 26, Gravity.CENTER));
            LinearLayout.LayoutParams il = Ui.lpu(tl ? 64 : 50, tl ? 64 : 50);
            il.gravity = Gravity.CENTER_HORIZONTAL;
            l.addView(ic, il);
            l.addView(new View(c), Ui.weight(1, false));
            sub = Ui.line(c, "Tap to open", ts(12), Ui.SUB, false);
            sub.setGravity(Gravity.CENTER);
            l.addView(sub, Ui.lp(Ui.MATCH, Ui.WRAP));
            l.setOnClickListener(x -> WhatsWeb.open());
            Ui.press(l);
            v = l;
            if (index >= 0) WhatsWeb.web(c); // loads in the background, so chats are ready when it opens
            tick();
        }

        @Override
        void tick() {
            int n = WhatsWeb.unread();
            badge.setText(String.valueOf(n));
            badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
            sub.setText(n > 0 ? n + " unread chat" + (n == 1 ? "" : "s") : "Tap to open");
        }
    }

    // ------------------------------------------------------------------ Go to

    static final class GoTo extends W {
        GoTo(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            // tap the title (or anywhere but Home / Work) for more saved places
            LinearLayout head = Ui.row(c);
            head.addView(Ui.text(c, "Go to", 15, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            JSONArray more = cfg.optJSONArray("places");
            head.addView(Ui.text(c, (more != null && more.length() > 0 ? "+" + more.length() + " " : "") + "›", 13, Ui.ACCENT_SOFT, true));
            l.addView(head);
            l.setOnClickListener(x -> places(index));
            // Home and Work share the space under the title (big buttons, at least 52 high)
            LinearLayout.LayoutParams hl = Ui.weight(1, false);
            hl.topMargin = Ui.u(10);
            View home = place(R.drawable.ic_home, "Home", "home");
            home.setMinimumHeight(Ui.u(tl ? 52 : 40));
            l.addView(home, hl);
            LinearLayout.LayoutParams wl = Ui.weight(1, false);
            wl.topMargin = Ui.u(8);
            View work = place(R.drawable.ic_work, "Work", "work");
            work.setMinimumHeight(Ui.u(tl ? 52 : 40));
            l.addView(work, wl);
            v = l;
        }

        private View place(int icon, String name, String key) {
            LinearLayout b = Ui.row(c);
            b.setBackground(Themes.inner(Ui.CARD2, 12));
            b.setGravity(Gravity.CENTER_VERTICAL);
            // big in the Top Left Widget; in a tile (narrower) smaller so the name isn't cut off
            Ui.pad(b, tl ? 14 : 9, 0, tl ? 10 : 6, 0);
            b.addView(Ui.icon(c, icon, Ui.ACCENT_SOFT), Ui.lpu(tl ? 28 : 22, tl ? 28 : 22));
            TextView t = Ui.line(c, name, tl ? 18 : 15, Ui.TEXT, true);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            nl.leftMargin = Ui.u(tl ? 12 : 8);
            b.addView(t, nl);
            b.setOnClickListener(x -> {
                String a = cfg.optString(key, "");
                if (a.isEmpty()) gotoSetup(index);
                else Actions.navigateTo(c, a);
            });
            Ui.press(b);
            return b;
        }
    }

    static EditText field(String hint, String value, boolean secret, Ui.Cb<String> changed) {
        Context c = Panels.ctx();
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(14));
        e.setBackground(Themes.inner(Ui.CARD2, 10));
        Ui.pad(e, 12, 9, 12, 9);
        if (secret) e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI | android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        InAppKeyboard.attach(e, hint);
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                changed.on(s.toString().trim());
            }
        });
        return e;
    }

    /** All saved places: Home, Work and any others. Tap to navigate; add or remove places here. */
    static void places(int index) {
        Panels.Page pg = () -> {
            Context c = Panels.ctx();
            JSONObject spec = TileDrawer.specs().optJSONObject(index);
            JSONObject cfg = spec.optJSONObject("cfg");
            if (cfg == null) cfg = new JSONObject();
            final JSONObject fc = cfg;
            JSONArray more = fc.optJSONArray("places");
            if (more == null) more = new JSONArray();
            final JSONArray list = more;
            LinearLayout b = Ui.col(c);
            List<View> cells = new ArrayList<>();
            String[][] fixed = {{"home", "Home"}, {"work", "Work"}};
            for (String[] f : fixed) {
                String a = fc.optString(f[0], "");
                cells.add(placeRow(c, "home".equals(f[0]) ? R.drawable.ic_home : R.drawable.ic_work, f[1], a.isEmpty() ? "Tap to set" : a, () -> {
                    if (a.isEmpty()) gotoSetup(index);
                    else {
                        Panels.close();
                        Actions.navigateTo(c, a);
                    }
                }, null));
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                final int k = i;
                String a = o.optString("addr");
                cells.add(placeRow(c, R.drawable.ic_pin, o.optString("name"), a, () -> {
                    Panels.close();
                    Actions.navigateTo(c, a);
                }, () -> { // remove
                    JSONArray out = new JSONArray();
                    for (int q = 0; q < list.length(); q++) if (q != k) out.put(list.opt(q));
                    put(fc, "places", out);
                    put(spec, "cfg", fc);
                    TileDrawer.setSpec(index, spec);
                    Panels.refresh();
                }));
            }
            b.addView(AppPanels.grid(c, cells, 2, 8));
            // add a place
            b.addView(Panels.section("Add a place"));
            final String[] nm = {""}, ad = {""};
            // stacked, so it fits the tile area's width
            b.addView(field("Name, e.g. Gym", "", false, t -> nm[0] = t), top(4));
            LinearLayout add = Ui.row(c);
            add.addView(field("Address", "", false, t -> ad[0] = t), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            add.addView(Ui.gap(c, 8, 1));
            add.addView(Ui.button(c, "Add", true, () -> {
                if (nm[0].trim().isEmpty() || ad[0].trim().isEmpty()) return;
                JSONObject o = new JSONObject();
                put(o, "name", nm[0].trim());
                put(o, "addr", ad[0].trim());
                list.put(o);
                put(fc, "places", list);
                put(spec, "cfg", fc);
                TileDrawer.setSpec(index, spec);
                Panels.refresh();
            }));
            b.addView(add, top(8));
            b.addView(Panels.item("Change Home and Work", null, () -> gotoSetup(index)), top(8));
            return Panels.panel("Places", b);
        };
        Panels.show(pg); // always inside the 2×4 tile area
    }

    private static View placeRow(Context c, int icon, String name, String addr, Runnable go, Runnable remove) {
        LinearLayout r = Ui.row(c);
        r.setBackground(Themes.inner(Ui.CARD2, 12));
        Ui.pad(r, 12, 8, 8, 8);
        r.setMinimumHeight(Ui.u(56));
        r.addView(Ui.icon(c, icon, Ui.ACCENT_SOFT), Ui.lpu(22, 22));
        LinearLayout t = Ui.col(c);
        t.addView(Ui.line(c, name, 14, Ui.TEXT, true));
        t.addView(Ui.line(c, addr, 11, Ui.SUB, false), top(2));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(10);
        r.addView(t, tl);
        if (remove != null) {
            TextView x = Ui.text(c, "−", 16, Ui.TEXT, true);
            x.setGravity(Gravity.CENTER);
            x.setBackground(Ui.oval(Ui.RED));
            x.setOnClickListener(v -> remove.run());
            r.addView(x, Ui.lpu(24, 24));
        }
        r.setOnClickListener(v -> go.run());
        Ui.press(r);
        return r;
    }

    static void gotoSetup(int index) {
        Panels.show(() -> {
            Context c = Panels.ctx();
            JSONObject spec = TileDrawer.specs().optJSONObject(index);
            JSONObject cfg = spec.optJSONObject("cfg");
            if (cfg == null) cfg = new JSONObject();
            final JSONObject fc = cfg;
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Addresses open in your navigation app (" + (Actions.navPkg() == null ? "not chosen yet" : Apps.label(Actions.navPkg())) + ").", 13, Ui.SUB, false));
            b.addView(Panels.section("Home"));
            b.addView(field("e.g. 12 Florida Road, Durban", fc.optString("home"), false, t -> put(fc, "home", t)), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Panels.section("Work"));
            b.addView(field("Work address", fc.optString("work"), false, t -> put(fc, "work", t)), Ui.lp(Ui.MATCH, Ui.WRAP));
            LinearLayout btns = Ui.row(c);
            btns.addView(Ui.button(c, "Save", true, () -> {
                put(spec, "cfg", fc);
                Panels.close();
                TileDrawer.setSpec(index, spec);
            }));
            b.addView(btns, top(18));
            return Panels.panel("Go to", b);
        });
    }

    static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ contacts

    static final class Person {
        String name, number, photo;
    }

    static Person person(JSONObject o) {
        if (o == null) return null;
        Person p = new Person();
        p.name = o.optString("name");
        p.number = o.optString("number");
        p.photo = o.optString("photo", null);
        return p.number.isEmpty() ? null : p;
    }

    static JSONObject json(Person p) {
        JSONObject o = new JSONObject();
        put(o, "name", p.name);
        put(o, "number", p.number);
        if (p.photo != null) put(o, "photo", p.photo);
        return o;
    }

    private static final android.util.LruCache<String, Bitmap> photos = new android.util.LruCache<>(80);

    /** A contact photo, shrunk to avatar size and cached (full-size photos made the phone screen slow and crash). */
    static Bitmap photo(Context c, String uri) {
        if (uri == null || uri.isEmpty()) return null;
        Bitmap hit = photos.get(uri);
        if (hit != null) return hit;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(uri))) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= 160) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            Bitmap bm;
            try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(uri))) {
                bm = BitmapFactory.decodeStream(in, null, o);
            }
            if (bm != null) photos.put(uri, bm);
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    static final int[] AVATAR = {0xFF3D8BFD, 0xFF34C759, 0xFFFFB020, 0xFFAF52DE, 0xFFE24B4A, 0xFF5DCAA5};

    static View avatar(Context c, Person p, float size) {
        FrameLayout f = new FrameLayout(c);
        Bitmap bm = photo(c, p.photo);
        if (bm != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bm);
            f.addView(iv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            f.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline o) {
                    o.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            f.setClipToOutline(true);
        } else {
            f.setBackground(Ui.oval(AVATAR[Math.abs(p.name.hashCode()) % AVATAR.length]));
            TextView t = Ui.text(c, p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase(Locale.ROOT), size * 0.45f, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            f.addView(t, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        }
        return f;
    }

    /** Pick a phone contact (or type a number). */
    static void pickContact(String title, Ui.Cb<Person> cb) {
        MainActivity a = MainActivity.I;
        if (a != null && a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 3);
        }
        final String[] query = {""};
        Panels.push(new Panels.Page() {
            @Override
            public View build() {
                Context c = Panels.ctx();
                LinearLayout b = Ui.col(c);
                LinearLayout list = Ui.col(c);
                EditText search = field("Search, or type a number", query[0], false, t -> {
                    query[0] = t;
                    fill(c, list, t, cb);
                });
                b.addView(search, Ui.lp(Ui.MATCH, Ui.WRAP));
                b.addView(list, top(8));
                fill(c, list, query[0], cb);
                return Panels.panel(title, b);
            }
        });
    }

    private static void fill(Context c, LinearLayout list, String q, Ui.Cb<Person> cb) {
        list.removeAllViews();
        if (q.matches("[+0-9 ()-]{3,}")) {
            Person p = new Person();
            p.name = q;
            p.number = q;
            list.addView(Panels.item("Use number " + q, null, () -> cb.on(p)));
        }
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            list.addView(Ui.text(c, "Allow contacts access to pick from your contacts.", 13, Ui.SUB, false));
            return;
        }
        String sel = q.isEmpty() ? null : ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] args = q.isEmpty() ? null : new String[]{"%" + q + "%"};
        int n = 0;
        try (Cursor cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI}, sel, args,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE")) {
            while (cur != null && cur.moveToNext() && n < 60) {
                Person p = new Person();
                p.name = cur.getString(0) == null ? "" : cur.getString(0);
                p.number = cur.getString(1);
                p.photo = cur.getString(2);
                LinearLayout r = Ui.row(c);
                r.setMinimumHeight(Ui.u(48));
                r.addView(avatar(c, p, 34), Ui.lpu(34, 34));
                LinearLayout t = Ui.col(c);
                t.addView(Ui.line(c, p.name, 15, Ui.TEXT, false));
                t.addView(Ui.line(c, p.number, 12, Ui.SUB, false));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
                tl.leftMargin = Ui.u(12);
                r.addView(t, tl);
                r.setOnClickListener(x -> cb.on(p));
                list.addView(r);
                n++;
            }
        } catch (Exception ignored) {
        }
        if (n == 0 && !q.matches("[+0-9 ()-]{3,}")) list.addView(Ui.text(c, "No contacts found", 13, Ui.SUB, false));
    }

    // ------------------------------------------------------------------ Phone

    static final class PhoneW extends W {
        PhoneW(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = Ui.col(c);
            l.setBackground(Themes.on() ? Themes.card(0xFF1F7A46, 16) : new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1F7A46, 0xFF0F3D26}));
            if (l.getBackground() instanceof GradientDrawable) ((GradientDrawable) l.getBackground()).setCornerRadius(Ui.uf(16)); // glass cards are rounded already
            Ui.pad(l, 14, 16, 14, 16);
            FrameLayout ic = new FrameLayout(c);
            ic.setBackground(Ui.oval(0x33FFFFFF));
            ic.addView(Ui.icon(c, R.drawable.ic_call, Ui.TEXT), Ui.flpu(26, 26, Gravity.CENTER));
            l.addView(ic, Ui.lpu(48, 48));
            l.addView(new View(c), Ui.weight(1, false));
            l.addView(Ui.text(c, "Phone", 16, Ui.TEXT, true));
            l.addView(Ui.text(c, "Contacts · Keypad\nRecents", 11, 0xCCFFFFFF, false), top(4));
            l.setOnClickListener(x -> PhoneScreen.show(null));
            Ui.press(l);
            v = l;
        }
    }

    // ------------------------------------------------------------------ Quick dial

    static final class QuickDial extends W {
        QuickDial(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            Ui.pad(l, 12, 14, 12, 10);
            l.addView(Ui.text(c, "Quick dial", 15, Ui.TEXT, true));
            JSONArray people = cfg.optJSONArray("people");
            List<Person> ps = new ArrayList<>();
            for (int k = 0; people != null && k < people.length() && ps.size() < 4; k++) {
                Person p = person(people.optJSONObject(k));
                if (p != null) ps.add(p);
            }
            // the contacts as big faces filling the tile: one alone in the middle, else 2 × 2 (+ adds more)
            LinearLayout grid = Ui.col(c);
            int n = ps.size() <= 1 ? 1 : 4, cols = n == 1 ? 1 : 2;
            for (int r = 0; r < n / cols; r++) {
                LinearLayout row = Ui.row(c);
                for (int k = 0; k < cols; k++) {
                    int at = r * cols + k;
                    row.addView(at < ps.size() ? face(c, ps.get(at), n == 1 ? (tl ? 72 : 56) : (tl ? 48 : 36))
                            : addFace(c, n == 1 ? 56 : 36), new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
                }
                grid.addView(row, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
            }
            LinearLayout.LayoutParams gl = Ui.weight(1, false);
            gl.topMargin = Ui.u(6);
            l.addView(grid, gl);
            v = l;
        }

    private static View faceBox(Context c, View pic, float size, String name) {
        LinearLayout f = Ui.col(c);
        f.setGravity(Gravity.CENTER);
        f.addView(pic, Ui.lpu(size, size));
        TextView t = Ui.line(c, name, size > 50 ? 15 : 12, Ui.TEXT2, size > 50);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = Ui.lp(Ui.MATCH, Ui.WRAP);
        tl.topMargin = Ui.u(3);
        f.addView(t, tl);
        Ui.press(f);
        return f;
    }

    /** A quick-dial contact: face and first name; tap calls. */
    private View face(Context c, Person p, float size) {
        View f = faceBox(c, avatar(c, p, size), size, p.name.split(" ")[0]);
        f.setOnClickListener(x -> {
            if (MainActivity.I != null) Actions.call(MainActivity.I, p.number);
        });
        f.setContentDescription("Call " + p.name);
        return f;
    }

    /** An empty place: + chooses contacts. */
    private View addFace(Context c, float size) {
        FrameLayout ring = new FrameLayout(c);
        ring.setBackground(Ui.dashed(Ui.LINE, size / 2));
        ring.addView(Ui.icon(c, R.drawable.ic_plus, Ui.SUB), Ui.flpu(size * 0.4f, size * 0.4f, Gravity.CENTER));
        View f = faceBox(c, ring, size, "Add");
        f.setOnClickListener(x -> quickDialSetup(index));
        return f;
    }
    }

    static void quickDialSetup(int index) {
        Panels.show(new Panels.Page() {
            @Override
            public View build() {
                Context c = Panels.ctx();
                JSONObject spec = TileDrawer.specs().optJSONObject(index);
                JSONObject cfg = spec.optJSONObject("cfg");
                if (cfg == null) cfg = new JSONObject();
                JSONArray people = cfg.optJSONArray("people");
                if (people == null) people = new JSONArray();
                final JSONArray fp = people;
                final JSONObject fc = cfg;
                LinearLayout b = Ui.col(c);
                for (int k = 0; k < 4; k++) {
                    final int slot = k;
                    Person p = person(fp.optJSONObject(k));
                    b.addView(Panels.item("Contact " + (k + 1), p == null ? "Choose" : p.name, () -> pickContact("Contact " + (slot + 1), chosen -> {
                        try {
                            fp.put(slot, json(chosen));
                        } catch (Exception ignored) {
                        }
                        put(fc, "people", fp);
                        put(spec, "cfg", fc);
                        TileDrawer.setSpec(index, spec);
                        Panels.back();
                        Panels.refresh();
                    })));
                }
                b.addView(Ui.text(c, "Calls go through: " + callsLabel() + ".", 12, Ui.SUB, false), top(10));
                return Panels.panel("Quick dial", b);
            }
        });
    }

    static String callsLabel() {
        String m = Prefs.str(Prefs.CALLS, "bt");
        if ("bt".equals(m)) {
            String p = Actions.btDialler();
            return p == null ? "Bluetooth" : "Bluetooth · " + Apps.label(p);
        }
        return "connect".equals(m) ? "Car Connect" : "Box dialler";
    }

    // ------------------------------------------------------------------ Contact

    static final class Contact extends W {
        Contact(Context c, int i, JSONObject s) {
            super(c, i, s);
            Person p = person(cfg.optJSONObject("person"));
            FrameLayout f = new FrameLayout(c);
            f.setBackground(Themes.card(Ui.CARD, 16));
            if (p == null) {
                LinearLayout l = card(c, Ui.CARD);
                l.addView(Ui.text(c, "Contact", 15, Ui.TEXT, true));
                l.addView(Ui.text(c, "Tap to choose who to call", 12, Ui.SUB, false), top(8));
                l.setOnClickListener(x -> contactSetup(index));
                v = l;
                return;
            }
            Bitmap bm = photo(c, p.photo);
            if (bm != null) {
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bm);
                f.addView(iv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            } else {
                f.setBackground(Ui.round(AVATAR[Math.abs(p.name.hashCode()) % AVATAR.length], 16));
                TextView big = Ui.text(c, p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase(Locale.ROOT), 64, 0x99FFFFFF, true);
                big.setGravity(Gravity.CENTER);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH);
                bl.bottomMargin = Ui.u(30);
                f.addView(big, bl);
            }
            View shade = new View(c);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xCC000000}));
            f.addView(shade, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            LinearLayout t = Ui.col(c);
            Ui.pad(t, 12, 0, 12, 12);
            t.addView(Ui.line(c, p.name, 15, Ui.TEXT, true));
            t.addView(Ui.line(c, p.number, 11, 0xCCFFFFFF, false), top(2));
            f.addView(t, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM));
            FrameLayout call = new FrameLayout(c);
            call.setBackground(Ui.oval(Ui.GREEN));
            call.addView(Ui.icon(c, R.drawable.ic_call, Ui.TEXT), Ui.flpu(18, 18, Gravity.CENTER));
            FrameLayout.LayoutParams cl = Ui.flpu(36, 36, Gravity.TOP | Gravity.END);
            cl.setMargins(0, Ui.u(10), Ui.u(10), 0);
            f.addView(call, cl);
            f.setOnClickListener(x -> {
                if (MainActivity.I != null) Actions.call(MainActivity.I, p.number);
            });
            Ui.press(f);
            v = f;
        }
    }

    static void contactSetup(int index) {
        Panels.show(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Choose who this tile calls. Their contact photo fills the tile.", 13, Ui.SUB, false));
            b.addView(Ui.button(c, "Choose contact", true, () -> pickContact("Choose contact", p -> {
                JSONObject spec = TileDrawer.specs().optJSONObject(index);
                JSONObject cfg = new JSONObject();
                put(cfg, "person", json(p));
                put(spec, "cfg", cfg);
                TileDrawer.setSpec(index, spec);
                Panels.close();
            })), top(16));
            return Panels.panel("Contact", b, 480, 240);
        });
    }

    // ------------------------------------------------------------------ Next prayer

    static final class NextPrayer extends W {
        private final TextView name, time, pill;

        NextPrayer(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            LinearLayout head = Ui.row(c);
            head.addView(label(c, "NEXT"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            head.addView(Ui.icon(c, R.drawable.ic_moon, Ui.TEAL), Ui.lpu(18, 18));
            l.addView(head);
            l.addView(new View(c), Ui.weight(1, false));
            name = Ui.line(c, "", ts(18), Ui.TEXT2, true);
            l.addView(name);
            time = Ui.line(c, "--:--", tl ? 46 : 32, Ui.TEXT, true);
            l.addView(time, top(2));
            l.addView(new View(c), Ui.weight(1, false));
            pill = Ui.line(c, "", ts(12), Ui.MINT, true);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(Ui.round(0xFF143B2A, 12));
            Ui.pad(pill, 10, 6, 10, 6);
            l.addView(pill, Ui.lp(Ui.MATCH, Ui.WRAP));
            l.setOnClickListener(x -> InfoPanels.prayer());
            Ui.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
            if (Alerts.salahNow()) { // the salaah alert, inside the tile
                v.setBackground(Themes.edged(0xFF0F3D2E, Ui.TEAL, 16));
                name.setText("Time for Salaah");
                time.setText(Alerts.salahName == null ? "" : Alerts.salahName);
                pill.setText("Now");
                return;
            }
            v.setBackground(Themes.card(Ui.CARD, 16));
            double[] n = Prayer.next();
            if (n == null) {
                name.setText("Prayer");
                time.setText("--:--");
                pill.setText("Waiting for GPS");
                return;
            }
            name.setText(Prayer.NAMES[(int) n[0]]);
            time.setText(InfoPanels.clock(n[2]));
            long ms = Prayer.msUntil(n);
            pill.setText("in " + Ui.dur(ms + 59999));
        }
    }

    // ------------------------------------------------------------------ Prayer times

    static final class PrayerTimes extends W {
        private final TextView head;
        private final LinearLayout rows;
        private String lastKey = "";

        PrayerTimes(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            Ui.pad(l, 10, 14, 10, 10);
            head = label(c, "TODAY");
            head.setPadding(Ui.u(4), 0, 0, 0);
            l.addView(head);
            rows = Ui.col(c);
            LinearLayout.LayoutParams rl = Ui.weight(1, false);
            rl.topMargin = Ui.u(6);
            l.addView(rows, rl);
            l.setOnClickListener(x -> InfoPanels.prayer());
            v = l;
            tick();
        }

        @Override
        void tick() {
            double[] n = Prayer.next();
            int day = n == null ? 0 : (int) n[1];
            double[] t = Prayer.times(day);
            String key = n == null ? "none" : (int) n[0] + "/" + day;
            if (key.equals(lastKey)) return;
            lastKey = key;
            rows.removeAllViews();
            head.setText(day == 1 ? "TOMORROW" : "TODAY");
            int[] five = {0, 2, 3, 4, 5};
            for (int k : five) {
                boolean next = n != null && (int) n[0] == k;
                LinearLayout r = Ui.row(c);
                Ui.pad(r, 6, 0, 6, 0);
                if (next) r.setBackground(Ui.round(Ui.DGREEN, 8));
                r.addView(Ui.line(c, Prayer.NAMES[k], 12, next ? Ui.TEXT : Ui.TEXT2, next), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                r.addView(Ui.line(c, t == null ? "--:--" : InfoPanels.clock(t[k]), 12, next ? Ui.TEXT : Ui.SUB, next));
                rows.addView(r, Ui.weight(1, false));
            }
        }
    }

    // ------------------------------------------------------------------ Qibla

    static final class Qibla extends W {
        private final Compass compass;
        private final TextView deg, sub;

        Qibla(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, Ui.CARD);
            Ui.pad(l, 12, 12, 12, 12);
            compass = new Compass(c);
            compass.arrow = false;
            compass.markerIcon = c.getDrawable(R.drawable.ic_kaaba);
            l.addView(compass, Ui.weight(1, false));
            deg = Ui.line(c, "--°", 22, Ui.TEXT, true);
            deg.setGravity(Gravity.CENTER);
            l.addView(deg, top(8));
            sub = Ui.line(c, "from North", 11, Ui.SUB, false);
            sub.setGravity(Gravity.CENTER);
            l.addView(sub, top(2));
            l.setOnClickListener(x -> InfoPanels.qibla());
            v = l;
            tick();
        }

        @Override
        void tick() {
            if (!Gps.has()) {
                compass.marker = Float.NaN;
                deg.setText("--°");
                sub.setText("Waiting for GPS");
                compass.invalidate();
                return;
            }
            float q = (float) Prayer.qibla();
            compass.marker = q;
            float h = Gps.heading(); // last direction driven, held while parked
            if (!Float.isNaN(h)) {
                compass.rotation = h;
                compass.arrow = true;
                float rel = ((q - h) % 360 + 540) % 360 - 180;
                deg.setText(Math.abs(Math.round(rel)) + "°");
                sub.setText(Math.abs(rel) < 5 ? "straight ahead" : rel < 0 ? "to your left" : "to your right");
            } else {
                compass.rotation = 0;
                compass.arrow = false;
                deg.setText(Math.round(q) + "°");
                sub.setText("from North");
            }
            compass.invalidate();
        }
    }

    static Calendar now() {
        return Calendar.getInstance();
    }

    static List<String> ids() {
        List<String> l = new ArrayList<>();
        for (String[] w : CATALOG) l.add(w[0]);
        return l;
    }
}
