package com.carbox.launcher;

import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Current, hourly and 7-day forecast from Open-Meteo (free, no account). */
final class Weather {
    static boolean ok;
    static double temp, feels, windKmh;
    static int code, humidity;
    static boolean isDay = true;
    static long updatedAt;          // wall clock ms
    static final List<double[]> hours = new ArrayList<>();  // [hourOfDay, temp, code, rainProb, isDay]
    static final List<Day> days = new ArrayList<>();

    static final class Day {
        String date;     // yyyy-MM-dd
        int code, rainProb;
        double hi, lo, rainMm, windKmh, uv;
        int windDir;
        String sunrise, sunset; // HH:mm
    }

    private static long fetchedAt;
    private static boolean loading;
    private static final List<Runnable> listeners = new ArrayList<>();

    private Weather() {
    }

    static void listen(Runnable r) {
        listeners.add(r);
    }

    private static boolean restored;

    /** The forecast from the last poll, kept so it shows straight away after a restart (or with no signal). */
    static void restore() {
        if (restored || ok) return;
        restored = true;
        String body = Prefs.str("weather_last", null);
        if (body == null) return;
        try {
            parse(new JSONObject(body));
            updatedAt = Prefs.lng("weather_at", 0);
            // drop the days that have gone by, and the hourly list once it's out of date
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
            while (!days.isEmpty() && days.get(0).date != null && days.get(0).date.compareTo(today) < 0) days.remove(0);
            if (System.currentTimeMillis() - updatedAt > 60 * 60 * 1000L) hours.clear();
            if (days.isEmpty()) return;
            ok = true;
            for (Runnable r : new ArrayList<>(listeners)) r.run();
        } catch (Exception ignored) {
        }
    }

    static void maybeFetch() {
        restore();
        if (!Gps.has() || loading) return;
        if (fetchedAt != 0 && SystemClock.elapsedRealtime() - fetchedAt < 20 * 60 * 1000L) return;
        fetch();
    }

    /** A poll is under way (the weather icons animate). */
    static boolean loading() {
        return loading;
    }

    private static void changed() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    static void fetch() {
        if (!Gps.has()) return;
        loading = true;
        changed();
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,"
                        + "wind_speed_10m_max,wind_direction_10m_dominant,uv_index_max,sunrise,sunset"
                        + "&timezone=auto&forecast_days=7&forecast_hours=12",
                Gps.lat, Gps.lon);
        Net.get(url, null, (body, code) -> {
            loading = false;
            if (body == null) {
                changed(); // stop the animation, keep the old forecast
                return;
            }
            try {
                parse(new JSONObject(body));
                fetchedAt = SystemClock.elapsedRealtime();
                updatedAt = System.currentTimeMillis();
                ok = true;
                Prefs.put("weather_last", body); // remembered for next time
                Prefs.put("weather_at", updatedAt);
            } catch (Exception ignored) {
            }
            changed();
        });
    }

    private static void parse(JSONObject j) throws Exception {
        JSONObject c = j.getJSONObject("current");
        temp = c.getDouble("temperature_2m");
        feels = c.optDouble("apparent_temperature", temp);
        humidity = c.optInt("relative_humidity_2m", 0);
        code = c.optInt("weather_code", 0);
        isDay = c.optInt("is_day", 1) == 1;
        windKmh = c.optDouble("wind_speed_10m", 0);

        hours.clear();
        JSONObject h = j.optJSONObject("hourly");
        if (h != null) {
            JSONArray t = h.getJSONArray("time"), tt = h.getJSONArray("temperature_2m"), wc = h.getJSONArray("weather_code");
            JSONArray pp = h.optJSONArray("precipitation_probability"), dd = h.optJSONArray("is_day");
            for (int i = 0; i < t.length() && hours.size() < 8; i++) {
                String ts = t.getString(i); // 2026-10-26T20:00
                int hr = Integer.parseInt(ts.substring(11, 13));
                hours.add(new double[]{hr, tt.getDouble(i), wc.getInt(i), pp == null ? 0 : pp.optDouble(i, 0), dd == null ? 1 : dd.optInt(i, 1)});
            }
        }
        days.clear();
        JSONObject d = j.getJSONObject("daily");
        JSONArray time = d.getJSONArray("time");
        for (int i = 0; i < time.length(); i++) {
            Day x = new Day();
            x.date = time.getString(i);
            x.code = d.getJSONArray("weather_code").optInt(i, 0);
            x.hi = d.getJSONArray("temperature_2m_max").optDouble(i, 0);
            x.lo = d.getJSONArray("temperature_2m_min").optDouble(i, 0);
            x.rainProb = d.getJSONArray("precipitation_probability_max").optInt(i, 0);
            x.rainMm = d.getJSONArray("precipitation_sum").optDouble(i, 0);
            x.windKmh = d.getJSONArray("wind_speed_10m_max").optDouble(i, 0);
            x.windDir = d.getJSONArray("wind_direction_10m_dominant").optInt(i, 0);
            x.uv = d.getJSONArray("uv_index_max").optDouble(i, 0);
            String sr = d.getJSONArray("sunrise").optString(i, ""), ss = d.getJSONArray("sunset").optString(i, "");
            x.sunrise = sr.length() >= 16 ? sr.substring(11, 16) : "";
            x.sunset = ss.length() >= 16 ? ss.substring(11, 16) : "";
            days.add(x);
        }
    }

    static Day today() {
        return days.isEmpty() ? null : days.get(0);
    }

    static String t(double c) {
        boolean cel = Prefs.flag(Prefs.CELSIUS, true);
        return Math.round(cel ? c : c * 9 / 5 + 32) + "°";
    }

    static int icon(int code, boolean day) {
        if (code == 0) return day ? R.drawable.ic_w_sun : R.drawable.ic_w_moon;
        if (code <= 2) return day ? R.drawable.ic_w_partly : R.drawable.ic_w_partly_n;
        if (code == 3) return R.drawable.ic_w_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_w_fog;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_w_snow;
        if (code >= 95) return R.drawable.ic_w_storm;
        if (code >= 51) return R.drawable.ic_w_rain;
        return R.drawable.ic_w_cloud;
    }

    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 65) return "Rain";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorms";
        return "—";
    }

    static String compass(int deg) {
        String[] d = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return d[(int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8];
    }
}
