package com.carbox.launcher;

/**
 * Parks the car by itself: stationary for 5 minutes saves the spot; and whenever the car pulls away
 * (every start, after at least 2 min still) the spot it left counts as the previous parked-here place.
 */
final class AutoPark {
    private static final long STAY = 5 * 60000L, MIN_STOP = 120000L;
    private static long since;      // when this stop began (0 = moving)
    private static double lat, lon;
    private static String town;
    private static boolean saved;   // this stop is already saved

    private AutoPark() {
    }

    static void start() {
        Gps.listen(AutoPark::onFix);
    }

    private static void onFix() {
        if (!Gps.has() || !Gps.fresh()) return;
        long now = System.currentTimeMillis();
        if (Gps.moving()) {
            if (since != 0 && !saved && now - since >= MIN_STOP) save(since); // pulled away: where it stood is the parked spot
            since = 0;
            return;
        }
        if (since == 0) { // a stop begins (or the box has just started)
            since = now;
            lat = Gps.lat;
            lon = Gps.lon;
            town = Gps.town;
            saved = false;
        } else if (!saved && now - since >= STAY) save(since);
    }

    private static void save(long at) {
        saved = true;
        Prefs.put("park_lat", String.valueOf(lat));
        Prefs.put("park_lon", String.valueOf(lon));
        Prefs.put("park_town", town == null ? "" : town);
        Prefs.put("park_at", at);
        Trace.event("auto park");
        if (HA.configured()) CarHA.send(true);
    }
}
