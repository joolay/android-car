package com.carbox.launcher;

/**
 * Unofficial Google live traffic over the OpenStreetMap / OpenFreeMap / offline map (assets/gtraffic.js),
 * kept apart from the official Google Maps API traffic layer (Google map with your own key).
 *
 * PERSONAL USE ONLY: Google's terms don't allow their tiles outside their own map.
 * Before the Play Store: set ENABLED to false (or delete this class and gtraffic.js).
 */
final class GoogleTraffic {
    static final boolean ENABLED = true;

    private GoogleTraffic() {
    }

    /** Traffic can be shown: the official layer on the Google map, or the unofficial tiles everywhere else. */
    static boolean available() {
        return MapPanel.google() || ENABLED;
    }

    static boolean on() {
        if (!Store.flag("map_traffic_on_default", false)) { // traffic on by default: switched on once for everyone
            Store.put("map_traffic_on_default", true);
            Store.put("map_traffic", true);
        }
        return available() && Store.flag("map_traffic", true);
    }

    /** Page set-up call: lets gtraffic.js run (or not) on this map. */
    static String setupJs() {
        return "if(window.GTraffic)GTraffic.allow(" + (ENABLED && !MapPanel.google()) + ");";
    }
}
