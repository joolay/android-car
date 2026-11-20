package com.carbox.launcher;

/**
 * Everything that can be sold separately later: widgets, themes, map providers, the keyboard and faiths.
 * Each add-on has one ID; anything that offers it asks {@link #unlocked(String)} first.
 *
 * For now every add-on is unlocked. Before publishing: mark the free ones FREE, the rest PAID with
 * their Google Play product ID, and fill {@link #owned(String)} from Google Play Billing (purchases
 * are restored on a new box or after a reinstall).
 *
 * Not add-ons (part of the launcher, never sold separately): Now Playing and Maps.
 */
final class Addons {
    static final int WIDGET = 0, THEME = 1, MAP = 2, KEYBOARD = 3, FAITH = 4;
    static final boolean FREE = true, PAID = false;

    /** {kind, id, name, free?, Google Play product id (when paid)}. */
    static final Object[][] CATALOGUE = {
            // widgets (same IDs as Widgets.CATALOG)
            {WIDGET, "carconnect", "Car Connect", FREE, null},
            {WIDGET, "speed", "Speed", FREE, null},
            {WIDGET, "trip", "Trip", FREE, null},
            {WIDGET, "park", "Park here", FREE, null},
            {WIDGET, "safety", "Safety check", FREE, null},
            {WIDGET, "logbook", "Logbook", FREE, null},
            {WIDGET, "rest", "Driving timer", FREE, null},
            {WIDGET, "heading", "Heading", FREE, null},
            {WIDGET, "weather", "Weather", FREE, null},
            {WIDGET, "goto", "Go To", FREE, null},
            {WIDGET, "phone", "Phone", FREE, null},
            {WIDGET, "quickdial", "Quick dial", FREE, null},
            {WIDGET, "contact", "Contact", FREE, null},
            {WIDGET, "whatsapp", "WhatsApp", FREE, null},
            {WIDGET, "nextprayer", "Next prayer", FREE, null},
            {WIDGET, "prayertimes", "Prayer times", FREE, null},
            {WIDGET, "qibla", "Qibla", FREE, null},
            {WIDGET, "ha", "Home Assistant", FREE, null},
            // themes
            {THEME, "theme_modern", "Modern", FREE, null},
            {THEME, "theme_glass", "Glass", FREE, null},
            // map providers
            {MAP, "map_ofm", "OpenFreeMap", FREE, null},
            {MAP, "map_osm", "OpenStreetMap Standard", FREE, null},
            {MAP, "map_pmtiles", "Offline maps", FREE, null},
            {MAP, "map_google", "Google Maps (your own key)", FREE, null},
            // keyboard
            {KEYBOARD, "keyboard", "Car keyboard", FREE, null},
            // faiths: each brings its own widgets, travel prayers, calendar and announcements (Faith)
            {FAITH, "faith_islam", "Islam", FREE, null},
            {FAITH, "faith_christianity", "Christianity", FREE, null},
            {FAITH, "faith_judaism", "Judaism", FREE, null},
    };

    private Addons() {
    }

    /** Can this be used? Unknown IDs (and the fixtures) are always allowed. */
    static boolean unlocked(String id) {
        for (Object[] a : CATALOGUE) if (a[1].equals(id)) return (boolean) a[3] || owned((String) a[4]);
        return true;
    }

    /** Bought on Google Play, or everything unlocked with the owner's code (Private add-ons page). */
    static boolean owned(String product) {
        if (allUnlocked()) return true;
        return true; // until Google Play Billing is added: everything counts as owned (replace with the purchase check)
    }

    /** "Unlock all add-ons" on the Private add-ons page (needs the owner's code): no purchase needed on this box. */
    static boolean allUnlocked() {
        return PrivateAddons.unlocked() && Prefs.flag("addons_all_unlocked", false);
    }

    /** Map provider add-on ID for a provider key (osm, ofm, pmtiles, google). */
    static boolean mapUnlocked(String provider) {
        return unlocked("map_" + provider);
    }
}
