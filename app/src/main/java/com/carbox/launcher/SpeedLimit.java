package com.carbox.launcher;

import android.location.Location;
import android.net.Uri;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The speed limit of the road you're on, from OpenStreetMap (free, no account). Looked up while
 * driving, at most every 15 s or 150 m. 0 = unknown (then the Speed tile just shows your speed).
 */
final class SpeedLimit {
    static String road;              // name (or number) of that road, when known
    static int kmh;                  // current limit in km/h, 0 = unknown
    private static Location lastAsked;
    private static long lastAt;
    private static boolean busy;

    private SpeedLimit() {
    }

    static void start() {
        Gps.listen(SpeedLimit::check);
    }

    private static void check() {
        if (busy || !Gps.fresh() || Gps.speed < 2f) return; // only while moving
        long now = SystemClock.elapsedRealtime();
        Location here = new Location("gps");
        here.setLatitude(Gps.lat);
        here.setLongitude(Gps.lon);
        if (lastAsked != null && now - lastAt < 15000 && lastAsked.distanceTo(here) < 150) return;
        busy = true;
        lastAsked = here;
        lastAt = now;
        String q = String.format(java.util.Locale.US,
                "[out:json][timeout:8];way(around:20,%.6f,%.6f)[highway][maxspeed];out tags 3;", Gps.lat, Gps.lon);
        Net.get("https://overpass-api.de/api/interpreter?data=" + Uri.encode(q), null, (body, code) -> Ui.ui.post(() -> {
            busy = false;
            if (body == null) return; // keep the last known limit on a network hiccup
            try {
                JSONArray els = new JSONObject(body).optJSONArray("elements");
                int best = 0;
                if (els != null) for (int i = 0; i < els.length(); i++) {
                    JSONObject tg = els.getJSONObject(i).optJSONObject("tags");
                    int v = parse(tg.optString("maxspeed"));
                    if (v > 0) {
                        String nm = tg.optString("name", tg.optString("ref", ""));
                        if (!nm.isEmpty()) road = nm;
                        best = v; // nearest road first
                        break;
                    }
                }
                kmh = best;
            } catch (Exception ignored) {
            }
        }));
    }

    /** "60", "60 mph", "ZA:urban" … → km/h. */
    static int parse(String s) {
        if (s == null || s.isEmpty()) return 0;
        String l = s.toLowerCase(java.util.Locale.ROOT).trim();
        if (l.endsWith("urban") && !l.endsWith("rural")) return l.contains("za") || l.contains("de") || l.contains("fr") ? 60 : 50;
        if (l.endsWith("rural")) return l.contains("za") ? 100 : 80;
        if (l.endsWith("motorway")) return 120;
        try {
            String n = l.replaceAll("[^0-9.]", "");
            if (n.isEmpty()) return 0;
            float v = Float.parseFloat(n);
            return Math.round(l.contains("mph") ? v * 1.609f : v);
        } catch (Exception e) {
            return 0;
        }
    }

    /** How far over the limit counts as speeding (km/h); a small margin for GPS error. */
    static int margin() {
        return Prefs.num("speed_margin", 5);
    }

    static boolean over() {
        return kmh > 0 && Gps.fresh() && Gps.speed * 3.6f > kmh + margin();
    }
}
