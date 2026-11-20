package com.carbox.launcher;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Mileage logbook. A trip starts when the car moves off and ends after a 5-minute stop (or when
 * the box was switched off). Each trip is Business or Personal (button on the tile; the next trip
 * starts as whatever you chose last). Odometer readings come from the start reading you enter.
 * Export as a CSV file (Downloads) or send it by email from the widget's settings.
 */
final class Logbook {
    private static final String TRIPS = "log_trips", CUR = "log_cur";
    private static JSONObject cur;          // the trip in progress, or null
    private static double lastTrip = -1;
    private static long stoppedAt, savedAt;

    private Logbook() {
    }

    static void start() {
        String s = Prefs.str(CUR, "");
        try {
            if (!s.isEmpty()) cur = new JSONObject(s);
        } catch (Exception ignored) {
        }
        // the box was off: finish the trip that was going when it switched off
        if (cur != null && System.currentTimeMillis() - cur.optLong("last", 0) > 5 * 60000L) finish();
        Gps.listen(Logbook::onFix);
    }

    private static void onFix() {
        if (!Gps.fresh()) return;
        long now = System.currentTimeMillis();
        double trip = Gps.tripMeters, dd = lastTrip < 0 ? 0 : trip - lastTrip;
        lastTrip = trip;
        boolean moving = Gps.speed > 3f;
        if (cur == null && moving) begin();
        if (cur == null) return;
        try {
            if (dd > 0 && dd < 500) cur.put("m", cur.optDouble("m", 0) + dd);
            if (moving) {
                stoppedAt = 0;
                cur.put("last", now);
            } else if (stoppedAt == 0) stoppedAt = now;
            if (stoppedAt > 0 && now - stoppedAt > 5 * 60000L) finish(); // parked
            else if (now - savedAt > 30000) save();
        } catch (Exception ignored) {
        }
    }

    /** The reset button: the trip in progress ends here, and the next drive is never joined to it. */
    static void reset() {
        finish();
        Prefs.put(RESET_AT, System.currentTimeMillis());
    }

    private static final String RESET_AT = "log_reset_at";

    /**
     * A drive that starts within 15 minutes of the last trip's end carries on that trip (a stop at the shops,
     * a restart of the box), unless the reset button was pressed since. Returns true when it took the trip back.
     */
    private static boolean resume() {
        try {
            JSONArray a = Prefs.arr(TRIPS);
            JSONObject last = a.length() == 0 ? null : a.optJSONObject(a.length() - 1);
            if (last == null) return false;
            long end = last.optLong("end", 0), now = System.currentTimeMillis();
            if (end <= Prefs.lng(RESET_AT, 0) || now - end > 15 * 60000L || now < end) return false;
            a.remove(a.length() - 1);
            Prefs.put(TRIPS, a);
            Prefs.put("log_odo_km", Prefs.lng("log_odo_km", 0) - Math.round(last.optDouble("km", 0) * 10)); // finish() adds it back
            last.remove("end");
            last.remove("to");
            last.remove("km");
            last.put("last", now);
            cur = last;
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void begin() {
        if (resume()) return;
        try {
            cur = new JSONObject();
            cur.put("start", System.currentTimeMillis());
            cur.put("last", System.currentTimeMillis());
            cur.put("from", Gps.town == null ? "" : Gps.town);
            cur.put("purpose", Prefs.str("log_default", "Personal"));
            cur.put("odo0", odometer());
            save();
        } catch (Exception ignored) {
        }
    }

    private static void finish() {
        if (cur == null) return;
        try {
            cur.put("end", cur.optLong("last", System.currentTimeMillis()));
            cur.put("to", Gps.town == null ? "" : Gps.town);
            double km = cur.optDouble("m", 0) / 1000.0;
            cur.put("km", Math.round(km * 10) / 10.0);
            if (km >= 0.3) { // ignore moving the car a few metres
                JSONArray a = Prefs.arr(TRIPS);
                a.put(cur);
                while (a.length() > 1000) a.remove(0);
                Prefs.put(TRIPS, a);
                Prefs.put("log_odo_km", Prefs.lng("log_odo_km", 0) + Math.round(km * 10)); // tenths of km driven since the start reading
            }
        } catch (Exception ignored) {
        }
        cur = null;
        Prefs.put(CUR, "");
    }

    private static void save() {
        savedAt = System.currentTimeMillis();
        if (cur != null) Prefs.put(CUR, cur.toString());
    }

    /** Current odometer: the reading you entered plus what's been logged since. */
    static double odometer() {
        return Prefs.num("log_odo_start", 0) + Prefs.lng("log_odo_km", 0) / 10.0;
    }

    static boolean driving() {
        return cur != null;
    }

    static double currentKm() {
        return cur == null ? 0 : cur.optDouble("m", 0) / 1000.0;
    }

    static String purpose() {
        return cur != null ? cur.optString("purpose", "Personal") : Prefs.str("log_default", "Personal");
    }

    /** Business or Personal for this trip (and the next ones). */
    static void setPurpose(String p) {
        Prefs.put("log_default", p);
        try {
            if (cur != null) cur.put("purpose", p);
        } catch (Exception ignored) {
        }
        save();
    }

    /** Every finished trip, oldest first. */
    static JSONArray trips() {
        return Prefs.arr(TRIPS);
    }

    /** Business ⇄ Personal for this trip (and the next ones). */
    static void togglePurpose() {
        String p = "Business".equals(purpose()) ? "Personal" : "Business";
        Prefs.put("log_default", p);
        try {
            if (cur != null) cur.put("purpose", p);
        } catch (Exception ignored) {
        }
        save();
    }

    /** km this month: [business, personal] */
    static double[] month() {
        double[] t = new double[2];
        String m = new SimpleDateFormat("yyyyMM", Locale.US).format(new Date());
        JSONArray a = Prefs.arr(TRIPS);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !m.equals(new SimpleDateFormat("yyyyMM", Locale.US).format(new Date(o.optLong("start"))))) continue;
            t["Business".equals(o.optString("purpose")) ? 0 : 1] += o.optDouble("km", 0);
        }
        return t;
    }

    // ------------------------------------------------------------------ export

    static String csv() {
        StringBuilder b = new StringBuilder("Date,Start,End,From,To,Purpose,Odometer start,Odometer end,Distance (km)\n");
        SimpleDateFormat d = new SimpleDateFormat("yyyy-MM-dd", Locale.US), t = new SimpleDateFormat("HH:mm", Locale.US);
        JSONArray a = Prefs.arr(TRIPS);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            double o0 = o.optDouble("odo0", 0), km = o.optDouble("km", 0);
            b.append(d.format(new Date(o.optLong("start")))).append(',').append(t.format(new Date(o.optLong("start")))).append(',')
                    .append(t.format(new Date(o.optLong("end")))).append(',').append(q(o.optString("from"))).append(',').append(q(o.optString("to"))).append(',')
                    .append(o.optString("purpose")).append(',').append(o0 > 0 ? String.format(Locale.US, "%.1f", o0) : "").append(',')
                    .append(o0 > 0 ? String.format(Locale.US, "%.1f", o0 + km) : "").append(',').append(String.format(Locale.US, "%.1f", km)).append('\n');
        }
        return b.toString();
    }

    private static String q(String s) {
        return "\"" + s.replace("\"", "'") + "\"";
    }

    /** Save the CSV to Downloads; returns its address, or null. */
    static Uri exportFile(Context c) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, "Car logbook " + new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(new Date()) + ".csv");
            v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            Uri u = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) return null;
            try (java.io.OutputStream out = c.getContentResolver().openOutputStream(u)) {
                out.write(csv().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    static void email(Context c) {
        Uri u = exportFile(c);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/csv")
                .putExtra(Intent.EXTRA_SUBJECT, "Car logbook")
                .putExtra(Intent.EXTRA_TEXT, "Car logbook from Car Launcher (CSV attached).")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String to = Prefs.str("log_email", "");
        if (!to.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        if (u != null) i.putExtra(Intent.EXTRA_STREAM, u);
        try {
            c.startActivity(Intent.createChooser(i, "Send logbook").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.widget.Toast.makeText(c, "No email app on this box", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ settings (the widget's deeper page)

    static void settings() {
        Panels.Page p = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            double[] m = month();
            b.addView(Ui.text(c, String.format(Locale.US, "This month: %.0f km business · %.0f km personal", m[0], m[1]), 14, Ui.TEXT2, true));
            b.addView(Panels.section("Odometer"));
            b.addView(Widgets.field("Odometer reading now (km)", odometer() > 0 ? String.format(Locale.US, "%.0f", odometer()) : "", false, t -> {
                try {
                    Prefs.put("log_odo_start", Integer.parseInt(t.replaceAll("[^0-9]", "")));
                    Prefs.put("log_odo_km", 0L);
                } catch (Exception ignored) {
                }
            }), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Panels.section("Export"));
            b.addView(Widgets.field("Email address for the logbook", Prefs.str("log_email", ""), false, t -> Prefs.put("log_email", t.trim())), Ui.lp(Ui.MATCH, Ui.WRAP));
            LinearLayout r = Ui.row(c);
            r.addView(Ui.button(c, "Export to file", false, () -> {
                Uri u = exportFile(c);
                android.widget.Toast.makeText(c, u != null ? "Saved in Downloads" : "Couldn't save the file", android.widget.Toast.LENGTH_SHORT).show();
            }));
            r.addView(Ui.gap(c, 10, 1));
            r.addView(Ui.button(c, "Email", true, () -> email(c)));
            b.addView(r, Widgets.top(10));
            b.addView(Panels.section("Recent trips (tap to switch Business / Personal)"));
            JSONArray a = Prefs.arr(TRIPS);
            SimpleDateFormat f = new SimpleDateFormat("d MMM HH:mm", Locale.getDefault());
            if (a.length() == 0) b.addView(Ui.text(c, "No trips yet. They're logged by themselves while you drive.", 12, Ui.SUB, false));
            for (int i = a.length() - 1; i >= Math.max(0, a.length() - 30); i--) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                final int k = i;
                LinearLayout row = Ui.row(c);
                row.setMinimumHeight(Ui.u(40));
                String route = o.optString("from") + (o.optString("to").isEmpty() || o.optString("to").equals(o.optString("from")) ? "" : " → " + o.optString("to"));
                row.addView(Ui.line(c, f.format(new Date(o.optLong("start"))) + "  " + route, 13, Ui.TEXT2, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                row.addView(Ui.line(c, String.format(Locale.US, "%.1f km", o.optDouble("km", 0)), 13, Ui.TEXT, true));
                TextView pu = Ui.text(c, o.optString("purpose"), 11, Ui.TEXT, true);
                pu.setGravity(Gravity.CENTER);
                pu.setBackground(Ui.round("Business".equals(o.optString("purpose")) ? Ui.ACCENT : Ui.CARD3, 10));
                Ui.pad(pu, 8, 3, 8, 3);
                LinearLayout.LayoutParams pl = Ui.lp(Ui.WRAP, Ui.WRAP);
                pl.leftMargin = Ui.u(8);
                row.addView(pu, pl);
                row.setOnClickListener(v -> {
                    try {
                        JSONArray all = Prefs.arr(TRIPS);
                        JSONObject t = all.getJSONObject(k);
                        t.put("purpose", "Business".equals(t.optString("purpose")) ? "Personal" : "Business");
                        Prefs.put(TRIPS, all);
                    } catch (Exception ignored) {
                    }
                    Panels.refresh();
                });
                b.addView(Ui.hr(c));
                b.addView(row);
            }
            return Panels.panel("Logbook", b, 680, 440);
        };
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }

    // ------------------------------------------------------------------ the tile

    static final class Tile extends Widgets.W {
        private final TextView km, sub, btn;

        Tile(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = Widgets.card(c, Ui.CARD);
            l.addView(Widgets.label(c, "LOGBOOK"));
            l.addView(new View(c), Ui.weight(1, false));
            km = Ui.text(c, "", 26, Ui.TEXT, true);
            l.addView(km);
            sub = Ui.text(c, "", 12, Ui.SUB, false);
            sub.setMaxLines(2);
            l.addView(sub, Widgets.top(2));
            l.addView(new View(c), Ui.weight(1, false));
            btn = Ui.text(c, "", 13, Ui.TEXT, true);
            btn.setGravity(Gravity.CENTER);
            Ui.pad(btn, 8, 7, 8, 7);
            btn.setOnClickListener(v -> {
                togglePurpose();
                tick();
            });
            Ui.press(btn);
            l.addView(btn, Ui.lp(Ui.MATCH, Ui.WRAP));
            l.setOnClickListener(v -> settings());
            Ui.press(l);
            v = l;
            tick();
        }

        @Override
        void tick() {
            boolean b = "Business".equals(purpose());
            if (driving()) {
                km.setText(String.format(Locale.US, "%.1f km", currentKm()));
                sub.setText("This trip");
            } else {
                double[] m = month();
                km.setText(String.format(Locale.US, "%.0f km", m[0] + m[1]));
                sub.setText(String.format(Locale.US, "This month\n%.0f km business", m[0]));
            }
            btn.setText(b ? "Business" : "Personal");
            btn.setBackground(Ui.round(b ? Ui.ACCENT : Ui.CARD3, 12));
        }
    }
}
