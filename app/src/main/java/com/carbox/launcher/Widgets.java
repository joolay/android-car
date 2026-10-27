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
            {"rest", "Driving timer", "Time since your last break, with a beep or voice reminder to rest"},
            {"heading", "Heading", "Compass direction and altitude"},
            {"weather", "Weather", "Weather where you are"},
            {"goto", "Go to", "One tap to navigate home or to work"},
            {"phone", "Phone", "Contacts, favourites, recent calls and keypad"},
            {"quickdial", "Quick dial", "Four favourite contacts"},
            {"contact", "Contact", "One contact with their photo"},
            {"nextprayer", "Next prayer", "The next prayer and a countdown"},
            {"prayertimes", "Prayer times", "Today's five prayer times"},
            {"qibla", "Qibla", "Direction of the Kaaba from here"},
            {"ha", "Home Assistant", "Your own smart home toggles"}};

    static String name(String id) {
        for (String[] w : CATALOG) if (w[0].equals(id)) return w[1];
        return "none".equals(id) ? "Empty" : id;
    }

    static boolean configurable(String id) {
        return "goto".equals(id) || "quickdial".equals(id) || "contact".equals(id) || "ha".equals(id) || "rest".equals(id);
    }

    abstract static class W {
        final Context c;
        final int index;
        final JSONObject spec;
        final JSONObject cfg;
        View v;

        W(Context c, int index, JSONObject spec) {
            this.c = c;
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
            Tiles.setSpec(index, spec);
        }
    }

    static W make(Context c, int index, JSONObject spec) {
        String id = spec.optString("id");
        switch (id) {
            case "carconnect":
                return new CarConnect(c, index, spec);
            case "speed":
                return new Speed(c, index, spec);
            case "trip":
                return new Trip(c, index, spec);
            case "rest":
                return new RestW(c, index, spec);
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
            default:
                return new Empty(c, index, spec);
        }
    }

    /** Settings page for widgets that need setting up. */
    static void configure(int index) {
        JSONObject spec = Tiles.specs().optJSONObject(index);
        if (spec == null) return;
        String id = spec.optString("id");
        if ("goto".equals(id)) gotoSetup(index);
        else if ("quickdial".equals(id)) quickDialSetup(index);
        else if ("contact".equals(id)) contactSetup(index);
        else if ("ha".equals(id)) HA.setup(index);
        else if ("rest".equals(id)) Rest.setup();
    }

    // ------------------------------------------------------------------ shared bits

    static LinearLayout card(Context c, int bg) {
        LinearLayout l = U.col(c);
        l.setBackground(U.round(bg, 16));
        U.pad(l, 14, 14, 14, 14);
        return l;
    }

    static TextView label(Context c, String s) {
        TextView t = U.line(c, s, 11, U.SUB, true);
        t.setLetterSpacing(0.06f);
        return t;
    }

    static boolean mph() {
        return Store.flag("mph", false);
    }

    static LinearLayout.LayoutParams top(float m) {
        LinearLayout.LayoutParams lp = U.lp(U.MATCH, U.WRAP);
        lp.topMargin = U.u(m);
        return lp;
    }

    static final class Empty extends W {
        Empty(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            l.addView(U.text(c, "Tap to choose a widget", 13, U.SUB, false));
            l.setOnClickListener(x -> AppPanels.gallery(index));
            v = l;
        }
    }

    // ------------------------------------------------------------------ Car Connect

    static final class CarConnect extends W {
        CarConnect(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = U.col(c);
            l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1F4E9A, 0xFF13284A}));
            ((GradientDrawable) l.getBackground()).setCornerRadius(U.uf(16));
            U.pad(l, 14, 16, 14, 16);
            FrameLayout ic = new FrameLayout(c);
            ic.setBackground(U.oval(0x33FFFFFF));
            ic.addView(U.icon(c, R.drawable.ic_cast, U.TEXT), U.flpu(26, 26, Gravity.CENTER));
            l.addView(ic, U.lpu(48, 48));
            l.addView(new View(c), U.weight(1, false));
            l.addView(U.text(c, "Car Connect", 16, U.TEXT, true));
            TextView sub = U.text(c, "CarPlay · Auto\nMirror · Native", 11, 0xCCFFFFFF, false);
            sub.setLineSpacing(U.uf(2), 1);
            l.addView(sub, top(4));
            l.setOnClickListener(x -> AppPanels.connect());
            U.press(l);
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
            float sw = U.uf(7);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            r.set(cx - s / 2 + sw, cy - s / 2 + sw, cx + s / 2 - sw, cy + s / 2 - sw);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(U.CARD3);
            cv.drawArc(r, 135, 270, false, p);
            p.setColor(value > max * 0.67f ? U.AMBER : U.ACCENT);
            cv.drawArc(r, 135, 270 * Math.max(0, Math.min(1, value / max)), false, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(U.TEXT);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(U.uf(30));
            cv.drawText(big, cx, cy + U.uf(8), p);
            p.setTypeface(Typeface.DEFAULT);
            p.setColor(U.SUB);
            p.setTextSize(U.uf(11));
            cv.drawText(unit, cx, cy + U.uf(26), p);
        }
    }

    static final class Speed extends W {
        private final Dial dial;
        private final TextView topT;

        Speed(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            l.addView(label(c, "SPEED"));
            dial = new Dial(c);
            l.addView(dial, U.weight(1, false));
            topT = U.line(c, "", 11, U.SUB, false);
            topT.setGravity(Gravity.CENTER);
            l.addView(topT, U.lp(U.MATCH, U.WRAP));
            v = l;
            tick();
        }

        @Override
        void tick() {
            boolean m = mph();
            float f = m ? 2.23694f : 3.6f;
            dial.unit = m ? "mph" : "km/h";
            dial.max = m ? 110 : 180;
            if (Loc.speed >= 0 && Loc.fresh()) {
                dial.value = Loc.speed * f;
                dial.big = String.valueOf(Math.round(dial.value));
            } else {
                dial.value = 0;
                dial.big = "--";
            }
            dial.invalidate();
            float top = Loc.topSpeedToday * (m ? 0.621371f : 1f);
            topT.setText("Top today " + Math.round(top));
        }
    }

    // ------------------------------------------------------------------ Trip

    static final class Trip extends W {
        private final TextView dist, unit, drive, avg;

        Trip(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            l.addView(label(c, "TRIP"));
            l.addView(new View(c), U.weight(1, false));
            LinearLayout r = U.row(c);
            r.setGravity(Gravity.BOTTOM);
            dist = U.text(c, "0.0", 30, U.TEXT, true);
            r.addView(dist);
            unit = U.text(c, " km", 13, U.SUB, false);
            unit.setPadding(0, 0, 0, U.u(4));
            r.addView(unit);
            l.addView(r);
            l.addView(new View(c), U.weight(1, false));
            drive = U.line(c, "", 12, U.TEXT2, false);
            l.addView(drive);
            avg = U.line(c, "", 12, U.TEXT2, false);
            l.addView(avg, top(4));
            l.setOnClickListener(x -> Sheets.show(() -> {
                LinearLayout b = U.col(Sheets.ctx());
                b.addView(U.text(Sheets.ctx(), "Start a new trip from zero?", 15, U.TEXT2, false));
                LinearLayout btns = U.row(Sheets.ctx());
                btns.addView(U.button(Sheets.ctx(), "Reset trip", true, () -> {
                    Loc.resetTrip();
                    Sheets.close();
                    tick();
                }));
                btns.addView(U.gap(Sheets.ctx(), 10, 1));
                btns.addView(U.button(Sheets.ctx(), "Cancel", false, Sheets::close));
                b.addView(btns, top(18));
                return Sheets.panel("Trip", b, 400, 200);
            }));
            v = l;
            tick();
        }

        @Override
        void tick() {
            boolean m = mph();
            double d = Loc.tripMeters / (m ? 1609.34 : 1000.0);
            dist.setText(String.format(Locale.US, d < 100 ? "%.1f" : "%.0f", d));
            unit.setText(m ? " mi" : " km");
            drive.setText(U.dur(Loc.tripMovingMs) + " driving");
            double hrs = Loc.tripMovingMs / 3600000.0;
            avg.setText(hrs > 0.01 ? "Avg " + Math.round(d / hrs) + (m ? " mph" : " km/h") : "Avg --");
        }
    }

    // ------------------------------------------------------------------ Driving timer

    /** Thin rounded progress bar. */
    static final class Bar extends View {
        float value;
        int color = U.ACCENT;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float h = getHeight(), rad = h / 2;
            r.set(0, 0, getWidth(), h);
            p.setColor(U.CARD3);
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
            box = card(c, U.CARD);
            head = label(c, "DRIVING");
            box.addView(head);
            box.addView(new View(c), U.weight(1, false));
            time = U.text(c, "0:00", 30, U.TEXT, true);
            box.addView(time);
            bar = new Bar(c);
            LinearLayout.LayoutParams bl = U.lp(U.MATCH, U.u(6));
            bl.topMargin = U.u(6);
            box.addView(bar, bl);
            box.addView(new View(c), U.weight(1, false));
            sub = U.line(c, "", 12, U.TEXT2, false);
            box.addView(sub);
            sub2 = U.line(c, "", 12, U.SUB, false);
            box.addView(sub2, top(2));
            box.setOnClickListener(x -> Rest.setup());
            U.press(box);
            v = box;
            tick();
        }

        @Override
        void tick() {
            long min = Rest.driveMs / 60000;
            time.setText((min / 60) + ":" + U.two((int) (min % 60)));
            bar.value = Rest.progress();
            boolean due = Rest.due();
            long stopped = Rest.stoppedMs();
            int bg;
            String l2 = "";
            if (!Rest.on()) {
                head.setText("DRIVING");
                sub.setText("Reminders off");
                bar.color = U.MUTED;
                bg = U.CARD;
            } else if (due && stopped > 60000) {
                long left = Rest.breakMin() - stopped / 60000;
                head.setText("RESTING");
                sub.setText(left > 0 ? "Break done in" : "Break done");
                if (left > 0) l2 = left + " min";
                bar.color = U.TEAL;
                bg = U.CARD;
            } else if (due) {
                head.setText("TAKE A BREAK");
                sub.setText("Stop and rest");
                l2 = Rest.breakMin() + " minutes";
                bar.color = U.AMBER;
                bg = 0xFF4A3410;
            } else {
                head.setText("DRIVING");
                long leftMin = Rest.limitMin() - min;
                sub.setText("Break in " + U.dur(leftMin * 60000));
                if (Rest.limitKm() > 0) {
                    boolean m = mph();
                    double left = (Rest.limitKm() * 1000.0 - Rest.meters) / (m ? 1609.34 : 1000.0);
                    l2 = "or " + Math.max(0, Math.round(left)) + (m ? " mi" : " km");
                }
                bar.color = bar.value > 0.85f ? U.AMBER : U.ACCENT;
                bg = U.CARD;
            }
            sub2.setText(l2);
            sub2.setVisibility(l2.isEmpty() ? View.GONE : View.VISIBLE);
            box.setBackground(U.round(bg, 16));
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
            float cx = getWidth() / 2f, cy = getHeight() / 2f, rad = s / 2 - U.uf(2);
            p.setStyle(Paint.Style.FILL);
            p.setColor(U.CARD2);
            cv.drawCircle(cx, cy, rad, p);
            cv.save();
            cv.rotate(-rotation, cx, cy);
            p.setColor(U.MUTED);
            for (int i = 0; i < 72; i++) {
                float a = (float) Math.toRadians(i * 5);
                float len = i % 18 == 0 ? U.uf(7) : i % 2 == 0 ? U.uf(4) : U.uf(2);
                p.setStrokeWidth(i % 18 == 0 ? U.uf(2) : U.uf(1));
                cv.drawLine(cx + (float) Math.sin(a) * (rad - U.uf(3)), cy - (float) Math.cos(a) * (rad - U.uf(3)),
                        cx + (float) Math.sin(a) * (rad - U.uf(3) - len), cy - (float) Math.cos(a) * (rad - U.uf(3) - len), p);
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(U.uf(11));
            String[] nesw = {"N", "E", "S", "W"};
            for (int i = 0; i < 4; i++) {
                float a = (float) Math.toRadians(i * 90);
                float tr = rad - U.uf(18);
                p.setColor(i == 0 ? U.RED : U.SUB);
                float x = cx + (float) Math.sin(a) * tr, y = cy - (float) Math.cos(a) * tr + U.uf(4);
                cv.save();
                cv.rotate(rotation, x, y - U.uf(4));
                cv.drawText(nesw[i], x, y, p);
                cv.restore();
            }
            if (!Float.isNaN(marker)) {
                float a = (float) Math.toRadians(marker);
                p.setColor(U.GREEN);
                p.setStrokeWidth(U.uf(3));
                p.setStrokeCap(Paint.Cap.ROUND);
                cv.drawLine(cx, cy, cx + (float) Math.sin(a) * (rad - U.uf(26)), cy - (float) Math.cos(a) * (rad - U.uf(26)), p);
                if (markerIcon != null) {
                    float mx = cx + (float) Math.sin(a) * (rad - U.uf(14)), my = cy - (float) Math.cos(a) * (rad - U.uf(14));
                    int h = U.u(8);
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
                p.setColor(U.ACCENT);
                cv.drawPath(path, p);
            } else {
                p.setColor(U.TEXT);
                cv.drawCircle(cx, cy, U.uf(4), p);
            }
        }
    }

    static final class Heading extends W {
        private final Compass compass;
        private final TextView dir, alt;

        Heading(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            l.addView(label(c, "HEADING"));
            compass = new Compass(c);
            LinearLayout.LayoutParams cl = U.weight(1, false);
            cl.topMargin = U.u(6);
            cl.bottomMargin = U.u(6);
            l.addView(compass, cl);
            dir = U.line(c, "--", 16, U.TEXT, true);
            dir.setGravity(Gravity.CENTER);
            l.addView(dir, U.lp(U.MATCH, U.WRAP));
            alt = U.line(c, "", 11, U.SUB, false);
            alt.setGravity(Gravity.CENTER);
            l.addView(alt, top(3));
            v = l;
            tick();
        }

        @Override
        void tick() {
            if (!Float.isNaN(Loc.bearing) && Loc.fresh()) {
                compass.rotation = Loc.bearing;
                dir.setText(Weather.compass(Math.round(Loc.bearing)) + "  " + Math.round(Loc.bearing) + "°");
            } else {
                compass.rotation = 0;
                dir.setText(Loc.fresh() ? "Not moving" : "No GPS");
            }
            compass.invalidate();
            alt.setText(Double.isNaN(Loc.alt) ? "Altitude --" : "Altitude " + Math.round(mph() ? Loc.alt * 3.28084 : Loc.alt) + (mph() ? " ft" : " m"));
        }
    }

    // ------------------------------------------------------------------ Weather

    static final class WeatherW extends W {
        private final ImageView icon;
        private final TextView temp, desc, hilo, place;

        WeatherW(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            place = U.line(c, "", 11, U.SUB, true);
            l.addView(place);
            icon = U.icon(c, R.drawable.ic_w_partly, 0);
            LinearLayout.LayoutParams il = U.lpu(48, 48);
            il.topMargin = U.u(10);
            l.addView(icon, il);
            l.addView(new View(c), U.weight(1, false));
            temp = U.text(c, "--°", 34, U.TEXT, true);
            l.addView(temp);
            desc = U.line(c, "", 12, U.TEXT2, false);
            l.addView(desc, top(2));
            hilo = U.line(c, "", 12, U.SUB, false);
            l.addView(hilo, top(3));
            l.setOnClickListener(x -> InfoPanels.weather(0));
            U.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
            place.setText(Loc.town != null ? Loc.town.toUpperCase(Locale.ROOT) : "WEATHER");
            if (!Weather.ok) {
                desc.setText(Loc.has() ? "Loading…" : "Waiting for location");
                return;
            }
            icon.setImageResource(Weather.icon(Weather.code, Weather.isDay));
            temp.setText(Weather.t(Weather.temp));
            desc.setText(Weather.describe(Weather.code));
            Weather.Day d = Weather.today();
            if (d != null) hilo.setText("H " + Weather.t(d.hi) + "  L " + Weather.t(d.lo));
        }
    }

    // ------------------------------------------------------------------ Go to

    static final class GoTo extends W {
        GoTo(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            l.addView(U.text(c, "Go to", 15, U.TEXT, true));
            l.addView(new View(c), U.weight(1, false));
            l.addView(place(R.drawable.ic_home, "Home", "home"), U.lpu(U.MATCH, 52));
            LinearLayout.LayoutParams wl = U.lpu(U.MATCH, 52);
            wl.topMargin = U.u(8);
            l.addView(place(R.drawable.ic_work, "Work", "work"), wl);
            v = l;
        }

        private View place(int icon, String name, String key) {
            LinearLayout b = U.row(c);
            b.setBackground(U.round(U.CARD2, 12));
            U.pad(b, 10, 0, 8, 0);
            b.addView(U.icon(c, icon, U.ACCENT_SOFT), U.lpu(22, 22));
            TextView t = U.line(c, name, 14, U.TEXT, true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            tl.leftMargin = U.u(8);
            b.addView(t, tl);
            b.setOnClickListener(x -> {
                String a = cfg.optString(key, "");
                if (a.isEmpty()) gotoSetup(index);
                else Actions.navigateTo(c, a);
            });
            U.press(b);
            return b;
        }
    }

    static EditText field(String hint, String value, boolean secret, U.Cb<String> changed) {
        Context c = Sheets.ctx();
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(U.TEXT);
        e.setHintTextColor(U.MUTED);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, U.uf(14));
        e.setBackground(U.round(U.CARD2, 10));
        U.pad(e, 12, 9, 12, 9);
        if (secret) e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI | android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        Keyboard.attach(e, hint);
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

    static void gotoSetup(int index) {
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            JSONObject spec = Tiles.specs().optJSONObject(index);
            JSONObject cfg = spec.optJSONObject("cfg");
            if (cfg == null) cfg = new JSONObject();
            final JSONObject fc = cfg;
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Addresses open in your navigation app (" + (Actions.navPkg() == null ? "not chosen yet" : Apps.label(Actions.navPkg())) + ").", 13, U.SUB, false));
            b.addView(Sheets.section("Home"));
            b.addView(field("e.g. 12 Florida Road, Durban", fc.optString("home"), false, t -> put(fc, "home", t)), U.lp(U.MATCH, U.WRAP));
            b.addView(Sheets.section("Work"));
            b.addView(field("Work address", fc.optString("work"), false, t -> put(fc, "work", t)), U.lp(U.MATCH, U.WRAP));
            LinearLayout btns = U.row(c);
            btns.addView(U.button(c, "Save", true, () -> {
                put(spec, "cfg", fc);
                Sheets.close();
                Tiles.setSpec(index, spec);
            }));
            b.addView(btns, top(18));
            return Sheets.panel("Go to", b);
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

    static Bitmap photo(Context c, String uri) {
        if (uri == null || uri.isEmpty()) return null;
        try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(uri))) {
            return BitmapFactory.decodeStream(in);
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
            f.addView(iv, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            f.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline o) {
                    o.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            f.setClipToOutline(true);
        } else {
            f.setBackground(U.oval(AVATAR[Math.abs(p.name.hashCode()) % AVATAR.length]));
            TextView t = U.text(c, p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase(Locale.ROOT), size * 0.45f, U.TEXT, true);
            t.setGravity(Gravity.CENTER);
            f.addView(t, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        }
        return f;
    }

    /** Pick a phone contact (or type a number). */
    static void pickContact(String title, U.Cb<Person> cb) {
        MainActivity a = MainActivity.I;
        if (a != null && a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 3);
        }
        final String[] query = {""};
        Sheets.push(new Sheets.Page() {
            @Override
            public View build() {
                Context c = Sheets.ctx();
                LinearLayout b = U.col(c);
                LinearLayout list = U.col(c);
                EditText search = field("Search, or type a number", query[0], false, t -> {
                    query[0] = t;
                    fill(c, list, t, cb);
                });
                b.addView(search, U.lp(U.MATCH, U.WRAP));
                b.addView(list, top(8));
                fill(c, list, query[0], cb);
                return Sheets.panel(title, b);
            }
        });
    }

    private static void fill(Context c, LinearLayout list, String q, U.Cb<Person> cb) {
        list.removeAllViews();
        if (q.matches("[+0-9 ()-]{3,}")) {
            Person p = new Person();
            p.name = q;
            p.number = q;
            list.addView(Sheets.item("Use number " + q, null, () -> cb.on(p)));
        }
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            list.addView(U.text(c, "Allow contacts access to pick from your contacts.", 13, U.SUB, false));
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
                LinearLayout r = U.row(c);
                r.setMinimumHeight(U.u(48));
                r.addView(avatar(c, p, 34), U.lpu(34, 34));
                LinearLayout t = U.col(c);
                t.addView(U.line(c, p.name, 15, U.TEXT, false));
                t.addView(U.line(c, p.number, 12, U.SUB, false));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                tl.leftMargin = U.u(12);
                r.addView(t, tl);
                r.setOnClickListener(x -> cb.on(p));
                list.addView(r);
                n++;
            }
        } catch (Exception ignored) {
        }
        if (n == 0 && !q.matches("[+0-9 ()-]{3,}")) list.addView(U.text(c, "No contacts found", 13, U.SUB, false));
    }

    // ------------------------------------------------------------------ Phone

    static final class PhoneW extends W {
        PhoneW(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = U.col(c);
            l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1F7A46, 0xFF0F3D26}));
            ((GradientDrawable) l.getBackground()).setCornerRadius(U.uf(16));
            U.pad(l, 14, 16, 14, 16);
            FrameLayout ic = new FrameLayout(c);
            ic.setBackground(U.oval(0x33FFFFFF));
            ic.addView(U.icon(c, R.drawable.ic_call, U.TEXT), U.flpu(26, 26, Gravity.CENTER));
            l.addView(ic, U.lpu(48, 48));
            l.addView(new View(c), U.weight(1, false));
            l.addView(U.text(c, "Phone", 16, U.TEXT, true));
            l.addView(U.text(c, "Contacts · Keypad\nRecents", 11, 0xCCFFFFFF, false), top(4));
            l.setOnClickListener(x -> PhoneScreen.show(null));
            U.press(l);
            v = l;
        }
    }

    // ------------------------------------------------------------------ Quick dial

    static final class QuickDial extends W {
        QuickDial(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            U.pad(l, 12, 14, 12, 10);
            l.addView(U.text(c, "Quick dial", 15, U.TEXT, true));
            JSONArray people = cfg.optJSONArray("people");
            int shown = 0;
            for (int k = 0; people != null && k < people.length() && k < 4; k++) {
                Person p = person(people.optJSONObject(k));
                if (p == null) continue;
                LinearLayout r = U.row(c);
                r.addView(avatar(c, p, 26), U.lpu(26, 26));
                TextView n = U.line(c, p.name.split(" ")[0], 13, U.TEXT2, false);
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                nl.leftMargin = U.u(8);
                r.addView(n, nl);
                r.setOnClickListener(x -> {
                    if (MainActivity.I != null) Actions.call(MainActivity.I, p.number);
                });
                LinearLayout.LayoutParams rl = U.lpu(U.MATCH, 30);
                rl.topMargin = U.u(shown == 0 ? 10 : 4);
                l.addView(r, rl);
                shown++;
            }
            if (shown == 0) {
                TextView t = U.text(c, "Tap to choose four contacts", 12, U.SUB, false);
                l.addView(t, top(10));
                l.setOnClickListener(x -> quickDialSetup(index));
            }
            v = l;
        }
    }

    static void quickDialSetup(int index) {
        Sheets.show(new Sheets.Page() {
            @Override
            public View build() {
                Context c = Sheets.ctx();
                JSONObject spec = Tiles.specs().optJSONObject(index);
                JSONObject cfg = spec.optJSONObject("cfg");
                if (cfg == null) cfg = new JSONObject();
                JSONArray people = cfg.optJSONArray("people");
                if (people == null) people = new JSONArray();
                final JSONArray fp = people;
                final JSONObject fc = cfg;
                LinearLayout b = U.col(c);
                for (int k = 0; k < 4; k++) {
                    final int slot = k;
                    Person p = person(fp.optJSONObject(k));
                    b.addView(Sheets.item("Contact " + (k + 1), p == null ? "Choose" : p.name, () -> pickContact("Contact " + (slot + 1), chosen -> {
                        try {
                            fp.put(slot, json(chosen));
                        } catch (Exception ignored) {
                        }
                        put(fc, "people", fp);
                        put(spec, "cfg", fc);
                        Tiles.setSpec(index, spec);
                        Sheets.back();
                        Sheets.refresh();
                    })));
                }
                b.addView(U.text(c, "Calls go through: " + callsLabel() + ".", 12, U.SUB, false), top(10));
                return Sheets.panel("Quick dial", b);
            }
        });
    }

    static String callsLabel() {
        String m = Store.str(Store.CALLS, "bt");
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
            f.setBackground(U.round(U.CARD, 16));
            if (p == null) {
                LinearLayout l = card(c, U.CARD);
                l.addView(U.text(c, "Contact", 15, U.TEXT, true));
                l.addView(U.text(c, "Tap to choose who to call", 12, U.SUB, false), top(8));
                l.setOnClickListener(x -> contactSetup(index));
                v = l;
                return;
            }
            Bitmap bm = photo(c, p.photo);
            if (bm != null) {
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bm);
                f.addView(iv, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            } else {
                f.setBackground(U.round(AVATAR[Math.abs(p.name.hashCode()) % AVATAR.length], 16));
                TextView big = U.text(c, p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase(Locale.ROOT), 64, 0x99FFFFFF, true);
                big.setGravity(Gravity.CENTER);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(U.MATCH, U.MATCH);
                bl.bottomMargin = U.u(30);
                f.addView(big, bl);
            }
            View shade = new View(c);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xCC000000}));
            f.addView(shade, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            LinearLayout t = U.col(c);
            U.pad(t, 12, 0, 12, 12);
            t.addView(U.line(c, p.name, 15, U.TEXT, true));
            t.addView(U.line(c, p.number, 11, 0xCCFFFFFF, false), top(2));
            f.addView(t, new FrameLayout.LayoutParams(U.MATCH, U.WRAP, Gravity.BOTTOM));
            FrameLayout call = new FrameLayout(c);
            call.setBackground(U.oval(U.GREEN));
            call.addView(U.icon(c, R.drawable.ic_call, U.TEXT), U.flpu(18, 18, Gravity.CENTER));
            FrameLayout.LayoutParams cl = U.flpu(36, 36, Gravity.TOP | Gravity.END);
            cl.setMargins(0, U.u(10), U.u(10), 0);
            f.addView(call, cl);
            f.setOnClickListener(x -> {
                if (MainActivity.I != null) Actions.call(MainActivity.I, p.number);
            });
            U.press(f);
            v = f;
        }
    }

    static void contactSetup(int index) {
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Choose who this tile calls. Their contact photo fills the tile.", 13, U.SUB, false));
            b.addView(U.button(c, "Choose contact", true, () -> pickContact("Choose contact", p -> {
                JSONObject spec = Tiles.specs().optJSONObject(index);
                JSONObject cfg = new JSONObject();
                put(cfg, "person", json(p));
                put(spec, "cfg", cfg);
                Tiles.setSpec(index, spec);
                Sheets.close();
            })), top(16));
            return Sheets.panel("Contact", b, 480, 240);
        });
    }

    // ------------------------------------------------------------------ Next prayer

    static final class NextPrayer extends W {
        private final TextView name, time, pill;

        NextPrayer(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            LinearLayout head = U.row(c);
            head.addView(label(c, "NEXT"), new LinearLayout.LayoutParams(0, U.WRAP, 1));
            head.addView(U.icon(c, R.drawable.ic_moon, U.TEAL), U.lpu(18, 18));
            l.addView(head);
            l.addView(new View(c), U.weight(1, false));
            name = U.line(c, "", 18, U.TEXT2, true);
            l.addView(name);
            time = U.line(c, "--:--", 32, U.TEXT, true);
            l.addView(time, top(2));
            l.addView(new View(c), U.weight(1, false));
            pill = U.line(c, "", 12, U.MINT, true);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(U.round(0xFF143B2A, 12));
            U.pad(pill, 10, 6, 10, 6);
            l.addView(pill, U.lp(U.MATCH, U.WRAP));
            l.setOnClickListener(x -> InfoPanels.prayer());
            U.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
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
            pill.setText("in " + U.dur(ms + 59999));
        }
    }

    // ------------------------------------------------------------------ Prayer times

    static final class PrayerTimes extends W {
        private final TextView head;
        private final LinearLayout rows;
        private String lastKey = "";

        PrayerTimes(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            U.pad(l, 10, 14, 10, 10);
            head = label(c, "TODAY");
            head.setPadding(U.u(4), 0, 0, 0);
            l.addView(head);
            rows = U.col(c);
            LinearLayout.LayoutParams rl = U.weight(1, false);
            rl.topMargin = U.u(6);
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
                LinearLayout r = U.row(c);
                U.pad(r, 6, 0, 6, 0);
                if (next) r.setBackground(U.round(U.DGREEN, 8));
                r.addView(U.line(c, Prayer.NAMES[k], 12, next ? U.TEXT : U.TEXT2, next), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                r.addView(U.line(c, t == null ? "--:--" : InfoPanels.clock(t[k]), 12, next ? U.TEXT : U.SUB, next));
                rows.addView(r, U.weight(1, false));
            }
        }
    }

    // ------------------------------------------------------------------ Qibla

    static final class Qibla extends W {
        private final Compass compass;
        private final TextView deg, sub;

        Qibla(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = card(c, U.CARD);
            U.pad(l, 12, 12, 12, 12);
            compass = new Compass(c);
            compass.arrow = false;
            compass.markerIcon = c.getDrawable(R.drawable.ic_kaaba);
            l.addView(compass, U.weight(1, false));
            deg = U.line(c, "--°", 22, U.TEXT, true);
            deg.setGravity(Gravity.CENTER);
            l.addView(deg, top(8));
            sub = U.line(c, "from North", 11, U.SUB, false);
            sub.setGravity(Gravity.CENTER);
            l.addView(sub, top(2));
            l.setOnClickListener(x -> InfoPanels.qibla());
            v = l;
            tick();
        }

        @Override
        void tick() {
            if (!Loc.has()) {
                compass.marker = Float.NaN;
                deg.setText("--°");
                sub.setText("Waiting for GPS");
                compass.invalidate();
                return;
            }
            float q = (float) Prayer.qibla();
            compass.marker = q;
            boolean moving = !Float.isNaN(Loc.bearing) && Loc.fresh() && Loc.speed > 2;
            if (moving) {
                compass.rotation = Loc.bearing;
                compass.arrow = true;
                float rel = ((q - Loc.bearing) % 360 + 540) % 360 - 180;
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
