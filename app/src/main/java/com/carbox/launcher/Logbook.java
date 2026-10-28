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
        String s = Store.str(CUR, "");
        try {
            if (!s.isEmpty()) cur = new JSONObject(s);
        } catch (Exception ignored) {
        }
        // the box was off: finish the trip that was going when it switched off
        if (cur != null && System.currentTimeMillis() - cur.optLong("last", 0) > 5 * 60000L) finish();
        Loc.listen(Logbook::onFix);
    }

    private static void onFix() {
        if (!Loc.fresh()) return;
        long now = System.currentTimeMillis();
        double trip = Loc.tripMeters, dd = lastTrip < 0 ? 0 : trip - lastTrip;
        lastTrip = trip;
        boolean moving = Loc.speed > 3f;
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

    private static void begin() {
        try {
            cur = new JSONObject();
            cur.put("start", System.currentTimeMillis());
            cur.put("last", System.currentTimeMillis());
            cur.put("from", Loc.town == null ? "" : Loc.town);
            cur.put("purpose", Store.str("log_default", "Personal"));
            cur.put("odo0", odometer());
            save();
        } catch (Exception ignored) {
        }
    }

    private static void finish() {
        if (cur == null) return;
        try {
            cur.put("end", cur.optLong("last", System.currentTimeMillis()));
            cur.put("to", Loc.town == null ? "" : Loc.town);
            double km = cur.optDouble("m", 0) / 1000.0;
            cur.put("km", Math.round(km * 10) / 10.0);
            if (km >= 0.3) { // ignore moving the car a few metres
                JSONArray a = Store.arr(TRIPS);
                a.put(cur);
                while (a.length() > 1000) a.remove(0);
                Store.put(TRIPS, a);
                Store.put("log_odo_km", Store.lng("log_odo_km", 0) + Math.round(km * 10)); // tenths of km driven since the start reading
            }
        } catch (Exception ignored) {
        }
        cur = null;
        Store.put(CUR, "");
    }

    private static void save() {
        savedAt = System.currentTimeMillis();
        if (cur != null) Store.put(CUR, cur.toString());
    }

    /** Current odometer: the reading you entered plus what's been logged since. */
    static double odometer() {
        return Store.num("log_odo_start", 0) + Store.lng("log_odo_km", 0) / 10.0;
    }

    static boolean driving() {
        return cur != null;
    }

    static double currentKm() {
        return cur == null ? 0 : cur.optDouble("m", 0) / 1000.0;
    }

    static String purpose() {
        return cur != null ? cur.optString("purpose", "Personal") : Store.str("log_default", "Personal");
    }

    /** Business ⇄ Personal for this trip (and the next ones). */
    static void togglePurpose() {
        String p = "Business".equals(purpose()) ? "Personal" : "Business";
        Store.put("log_default", p);
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
        JSONArray a = Store.arr(TRIPS);
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
        JSONArray a = Store.arr(TRIPS);
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
        String to = Store.str("log_email", "");
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
        Sheets.Page p = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            double[] m = month();
            b.addView(U.text(c, String.format(Locale.US, "This month: %.0f km business · %.0f km personal", m[0], m[1]), 14, U.TEXT2, true));
            b.addView(Sheets.section("Odometer"));
            b.addView(Widgets.field("Odometer reading now (km)", odometer() > 0 ? String.format(Locale.US, "%.0f", odometer()) : "", false, t -> {
                try {
                    Store.put("log_odo_start", Integer.parseInt(t.replaceAll("[^0-9]", "")));
                    Store.put("log_odo_km", 0L);
                } catch (Exception ignored) {
                }
            }), U.lp(U.MATCH, U.WRAP));
            b.addView(Sheets.section("Export"));
            b.addView(Widgets.field("Email address for the logbook", Store.str("log_email", ""), false, t -> Store.put("log_email", t.trim())), U.lp(U.MATCH, U.WRAP));
            LinearLayout r = U.row(c);
            r.addView(U.button(c, "Export to file", false, () -> {
                Uri u = exportFile(c);
                android.widget.Toast.makeText(c, u != null ? "Saved in Downloads" : "Couldn't save the file", android.widget.Toast.LENGTH_SHORT).show();
            }));
            r.addView(U.gap(c, 10, 1));
            r.addView(U.button(c, "Email", true, () -> email(c)));
            b.addView(r, Widgets.top(10));
            b.addView(Sheets.section("Recent trips (tap to switch Business / Personal)"));
            JSONArray a = Store.arr(TRIPS);
            SimpleDateFormat f = new SimpleDateFormat("d MMM HH:mm", Locale.getDefault());
            if (a.length() == 0) b.addView(U.text(c, "No trips yet. They're logged by themselves while you drive.", 12, U.SUB, false));
            for (int i = a.length() - 1; i >= Math.max(0, a.length() - 30); i--) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                final int k = i;
                LinearLayout row = U.row(c);
                row.setMinimumHeight(U.u(40));
                String route = o.optString("from") + (o.optString("to").isEmpty() || o.optString("to").equals(o.optString("from")) ? "" : " → " + o.optString("to"));
                row.addView(U.line(c, f.format(new Date(o.optLong("start"))) + "  " + route, 13, U.TEXT2, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                row.addView(U.line(c, String.format(Locale.US, "%.1f km", o.optDouble("km", 0)), 13, U.TEXT, true));
                TextView pu = U.text(c, o.optString("purpose"), 11, U.TEXT, true);
                pu.setGravity(Gravity.CENTER);
                pu.setBackground(U.round("Business".equals(o.optString("purpose")) ? U.ACCENT : U.CARD3, 10));
                U.pad(pu, 8, 3, 8, 3);
                LinearLayout.LayoutParams pl = U.lp(U.WRAP, U.WRAP);
                pl.leftMargin = U.u(8);
                row.addView(pu, pl);
                row.setOnClickListener(v -> {
                    try {
                        JSONArray all = Store.arr(TRIPS);
                        JSONObject t = all.getJSONObject(k);
                        t.put("purpose", "Business".equals(t.optString("purpose")) ? "Personal" : "Business");
                        Store.put(TRIPS, all);
                    } catch (Exception ignored) {
                    }
                    Sheets.refresh();
                });
                b.addView(U.hr(c));
                b.addView(row);
            }
            return Sheets.panel("Logbook", b, 680, 440);
        };
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }

    // ------------------------------------------------------------------ the tile

    static final class Tile extends Widgets.W {
        private final TextView km, sub, btn;

        Tile(Context c, int i, JSONObject s) {
            super(c, i, s);
            LinearLayout l = Widgets.card(c, U.CARD);
            l.addView(Widgets.label(c, "LOGBOOK"));
            l.addView(new View(c), U.weight(1, false));
            km = U.text(c, "", 26, U.TEXT, true);
            l.addView(km);
            sub = U.line(c, "", 11, U.SUB, false);
            l.addView(sub, Widgets.top(2));
            l.addView(new View(c), U.weight(1, false));
            btn = U.text(c, "", 13, U.TEXT, true);
            btn.setGravity(Gravity.CENTER);
            U.pad(btn, 8, 7, 8, 7);
            btn.setOnClickListener(v -> {
                togglePurpose();
                tick();
            });
            U.press(btn);
            l.addView(btn, U.lp(U.MATCH, U.WRAP));
            l.setOnClickListener(v -> settings());
            U.press(l);
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
                sub.setText(String.format(Locale.US, "This month · %.0f business", m[0]));
            }
            btn.setText(b ? "Business" : "Personal");
            btn.setBackground(U.round(b ? U.ACCENT : U.CARD3, 12));
        }
    }
}
