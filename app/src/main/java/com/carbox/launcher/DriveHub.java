package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Drive: one widget with the main driving numbers (trip distance, time driving, time since a break)
 * and the Personal / Business logbook buttons. Tap it for the full picture in an expansion window.
 */
final class DriveHub {
    private DriveHub() {
    }

    private static String clock(long ms) {
        long m = Math.max(0, ms / 60000);
        return (m / 60) + ":" + String.format(Locale.US, "%02d", m % 60);
    }

    private static String dist(double meters) {
        boolean mi = Widgets.mph();
        double d = meters / (mi ? 1609.34 : 1000.0);
        return String.format(Locale.US, d < 100 ? "%.1f" : "%.0f", d) + (mi ? " mi" : " km");
    }

    private static String speed(double meters, long ms) {
        double hrs = ms / 3600000.0;
        if (hrs < 0.01) return "--";
        boolean mi = Widgets.mph();
        return Math.round(meters / (mi ? 1609.34 : 1000.0) / hrs) + (mi ? " mph" : " km/h");
    }

    /** Business or Personal buttons: the chosen one lit. */
    private static LinearLayout purposeRow(Context c, Runnable after) {
        LinearLayout r = Ui.row(c);
        for (String p : new String[]{"Personal", "Business"}) {
            if (r.getChildCount() > 0) r.addView(Ui.gap(c, 8, 1));
            TextView b = Ui.seg(c, p, p.equals(Logbook.purpose()), () -> {
                Logbook.setPurpose(p);
                after.run();
            });
            b.setTextSize(13);
            Ui.pad(b, 8, 9, 8, 9);
            r.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        }
        return r;
    }

    // ------------------------------------------------------------------ the widget

    static final class Tile extends Widgets.W {
        private final TextView dist, drive, since, buttonsHint;
        private final LinearLayout buttons;
        private String shownPurpose = "";

        Tile(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = Widgets.card(c, Ui.CARD);
            l.addView(Widgets.label(c, "DRIVE"));
            l.addView(new View(c), Ui.weight(1, false));

            LinearLayout nums = Ui.row(c);
            LinearLayout a = Ui.col(c);
            dist = Ui.text(c, "", tl ? 30 : 22, Ui.TEXT, true);
            a.addView(dist);
            a.addView(Ui.line(c, "Trip", Widgets.ts(11), Ui.SUB, false));
            LinearLayout b = Ui.col(c);
            drive = Ui.text(c, "", tl ? 30 : 22, Ui.TEXT, true);
            b.addView(drive);
            b.addView(Ui.line(c, "Driving", Widgets.ts(11), Ui.SUB, false));
            nums.addView(a, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            nums.addView(b, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            l.addView(nums);

            since = Ui.line(c, "", Widgets.ts(12), Ui.TEXT2, false);
            l.addView(since, Widgets.top(8));
            l.addView(new View(c), Ui.weight(1, false));

            buttonsHint = Ui.line(c, "Log this trip as", Widgets.ts(11), Ui.SUB, false);
            l.addView(buttonsHint);
            buttons = Ui.row(c);
            l.addView(buttons, Widgets.top(4));
            fillButtons();

            l.setOnClickListener(x -> expanded());
            Ui.press(l);
            v = l;
            tick();
        }

        private void fillButtons() {
            buttons.removeAllViews();
            shownPurpose = Logbook.purpose();
            LinearLayout r = purposeRow(c, this::tick);
            buttons.addView(r, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        }

        @Override
        void tick() {
            dist.setText(dist(Gps.tripMeters));
            drive.setText(clock(Gps.tripMovingMs));
            since.setText(Rest.stoppedMs() > 0 ? "Stopped " + Ui.dur(Rest.stoppedMs()) : "Since break " + clock(Rest.driveMs));
            if (!shownPurpose.equals(Logbook.purpose())) fillButtons(); // changed elsewhere, or just now
        }

        @Override
        void expand() {
            expanded();
        }
    }

    // ------------------------------------------------------------------ the expansion window

    private static void expanded() {
        Panels.show(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            boolean mi = Widgets.mph();

            // this trip
            b.addView(Ui.line(c, "THIS TRIP", 11, Ui.ACCENT_SOFT, true));
            LinearLayout r1 = Ui.row(c);
            r1.addView(stat(c, dist(Gps.tripMeters), "Distance"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r1.addView(stat(c, clock(Gps.tripMovingMs), "Time driving"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r1.addView(stat(c, speed(Gps.tripMeters, Gps.tripMovingMs), "Average speed"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r1.addView(stat(c, Math.round(Gps.topSpeedToday * (mi ? 2.23694f : 3.6f)) + (mi ? " mph" : " km/h"), "Top speed today"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            b.addView(r1, Widgets.top(6));

            // breaks
            b.addView(Ui.line(c, "BREAKS", 11, Ui.ACCENT_SOFT, true), Widgets.top(14));
            LinearLayout r2 = Ui.row(c);
            r2.addView(stat(c, clock(Rest.driveMs), "Driving since break"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r2.addView(stat(c, dist(Rest.meters), "Distance since break"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r2.addView(stat(c, Rest.on() ? Rest.limitMin() / 60.0 + " h" : "Off", "Reminder after"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r2.addView(stat(c, Rest.stoppedMs() > 0 ? Ui.dur(Rest.stoppedMs()) : "Driving", "Stopped for"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            b.addView(r2, Widgets.top(6));

            // logbook
            b.addView(Ui.line(c, "LOGBOOK", 11, Ui.ACCENT_SOFT, true), Widgets.top(14));
            double[] m = Logbook.month();
            double today = todayKm();
            LinearLayout r3 = Ui.row(c);
            r3.addView(stat(c, kmText(today), "Today"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r3.addView(stat(c, kmText(m[0]), "Business this month"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r3.addView(stat(c, kmText(m[1]), "Personal this month"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            r3.addView(stat(c, Logbook.odometer() > 0 ? String.format(Locale.US, "%.0f km", Logbook.odometer()) : "Not set", "Odometer"), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            b.addView(r3, Widgets.top(6));
            b.addView(Ui.line(c, "Log this trip as", 12, Ui.SUB, false), Widgets.top(10));
            b.addView(purposeRow(c, Panels::refresh), Widgets.top(4));

            // recent trips
            JSONArray a = Logbook.trips();
            if (a.length() > 0) {
                b.addView(Ui.line(c, "RECENT TRIPS", 11, Ui.ACCENT_SOFT, true), Widgets.top(14));
                SimpleDateFormat f = new SimpleDateFormat("EEE d MMM  HH:mm", Locale.US);
                for (int i = a.length() - 1; i >= 0 && i >= a.length() - 5; i--) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    String route = o.optString("from", "");
                    if (!o.optString("to", "").isEmpty() && !o.optString("to").equals(route)) route += (route.isEmpty() ? "" : " → ") + o.optString("to");
                    LinearLayout row = Ui.row(c);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout t = Ui.col(c);
                    t.addView(Ui.line(c, f.format(new Date(o.optLong("start"))), 12, Ui.TEXT, false));
                    if (!route.isEmpty()) t.addView(Ui.line(c, route, 11, Ui.SUB, false));
                    row.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                    row.addView(Ui.text(c, String.format(Locale.US, "%.1f km", o.optDouble("km", 0)), 13, Ui.TEXT, true));
                    TextView tag = Ui.text(c, o.optString("purpose", "Personal"), 11, Ui.TEXT2, false);
                    tag.setBackground(Themes.inner(Ui.CARD2, 10));
                    Ui.pad(tag, 8, 3, 8, 3);
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
                    tl.leftMargin = Ui.u(8);
                    row.addView(tag, tl);
                    b.addView(row, Widgets.top(8));
                }
            }

            // actions
            LinearLayout btns = Ui.row(c);
            btns.addView(Ui.button(c, "New trip", false, () -> {
                Gps.resetTrip();
                Panels.refresh();
            }));
            btns.addView(Ui.gap(c, 10, 1));
            btns.addView(Ui.button(c, "I had a break", false, () -> {
                Rest.reset();
                Panels.refresh();
            }));
            btns.addView(Ui.gap(c, 10, 1));
            btns.addView(Ui.button(c, "Logbook & export", true, Logbook::settings));
            b.addView(btns, Widgets.top(16));

            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(b);
            return Panels.panel("Drive", sv, 660, 440);
        });
    }

    private static View stat(Context c, String big, String small) {
        LinearLayout k = Ui.col(c);
        k.addView(Ui.line(c, big, 17, Ui.TEXT, true));
        k.addView(Ui.line(c, small, 11, Ui.SUB, false));
        return k;
    }

    private static String kmText(double km) {
        return String.format(Locale.US, "%.0f km", km);
    }

    private static double todayKm() {
        String d = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        double t = Logbook.currentKm();
        JSONArray a = Logbook.trips();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && d.equals(new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(o.optLong("start"))))) t += o.optDouble("km", 0);
        }
        return t;
    }
}
