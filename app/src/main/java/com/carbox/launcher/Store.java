package com.carbox.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Everything the launcher remembers, kept in one SharedPreferences file. */
final class Store {
    private static SharedPreferences sp;

    private Store() {
    }

    static void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    static String str(String k, String d) {
        return sp.getString(k, d);
    }

    static int num(String k, int d) {
        return sp.getInt(k, d);
    }

    static long lng(String k, long d) {
        return sp.getLong(k, d);
    }

    static boolean flag(String k, boolean d) {
        return sp.getBoolean(k, d);
    }

    static void put(String k, String v) {
        if (v == null) sp.edit().remove(k).apply();
        else sp.edit().putString(k, v).apply();
    }

    /** Write to disk now (used when the app is about to crash). */
    static void flush() {
        sp.edit().commit();
    }

    static void put(String k, int v) {
        sp.edit().putInt(k, v).apply();
    }

    static void put(String k, long v) {
        sp.edit().putLong(k, v).apply();
    }

    static void put(String k, boolean v) {
        sp.edit().putBoolean(k, v).apply();
    }

    static boolean has(String k) {
        return sp.contains(k);
    }

    static JSONArray arr(String k) {
        try {
            return new JSONArray(sp.getString(k, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void put(String k, JSONArray a) {
        sp.edit().putString(k, a.toString()).apply();
    }

    static JSONObject obj(String k) {
        try {
            return new JSONObject(sp.getString(k, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static void put(String k, JSONObject o) {
        sp.edit().putString(k, o.toString()).apply();
    }

    static List<String> list(String k) {
        JSONArray a = arr(k);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static void putList(String k, List<String> l) {
        put(k, new JSONArray(l));
    }

    // ------------------------------------------------------------------ keys

    static final String TILES = "tiles";               // JSON array of tile specs
    static final String PINS = "pins";                 // pinned dock apps
    static final String RECENTS = "recents";           // launch history (newest first)
    static final String HIDDEN = "hidden";             // hidden apps
    static final String RAIL = "rail";                 // [{id, on}]
    static final String AUTOSTART = "autostart";       // [{key, wait, mode}]
    static final String AUTOSTART_ON = "autostart_on";
    static final String AUTOSTART_SHOW = "autostart_show"; // home | last
    static final String NAV_PKG = "nav_pkg";
    static final String ASSIST = "assist";             // gemini | external
    static final String ASSIST_PKG = "assist_pkg";
    static final String WALL = "wall";                 // black | color | image
    static final String WALL_COLOR = "wall_color";
    static final String CALLS = "calls";               // box | bt | connect
    static final String CALLS_BT_PKG = "calls_bt_pkg";
    static final String CALLS_FALLBACK = "calls_fallback"; // ask | box | none
    static final String BRIDGE_PKG = "bridge_pkg";
    static final String MIRROR_PKG = "mirror_pkg";
    static final String CARPLAY_PKG = "carplay_pkg";
    static final String AA_PKG = "aa_pkg";
    static final String MAP_PROVIDER = "map_provider"; // osm | google
    static final String MAP_KEY = "map_key";
    static final String NATIVE_PKG = "native_pkg";
    static final String TICKER_MODE = "ticker_mode";   // alt | news | traffic
    static final String TICKER_EVERY = "ticker_every"; // seconds
    static final String TICKER_BACK = "ticker_back";   // seconds, 0 = never
    static final String TICKER_SCROLL = "ticker_scroll";
    static final String NEWS_URL = "news_url";
    static final String TRAFFIC_URL = "traffic_url";
    static final String TOMTOM_KEY = "tomtom_key";
    static final String CAL_ALT = "cal_alt";           // islamic-umalqura | hebrew | ... | none
    static final String CAL_ADJ = "cal_adj";
    static final String CAL_WEEK_SUN = "cal_week_sun";
    static final String CAL_SPECIAL = "cal_special";   // set: islamic, jewish, chinese
    static final String CAL_IDS = "cal_ids";           // google calendar ids to show
    static final String CAL_VIEW_ALT = "cal_view_alt";
    static final String SPLASH_ON = "splash_on";
    static final String SPLASH_TYPE = "splash_type";   // prayer | custom
    static final String SPLASH_SECS = "splash_secs";   // 0 = until tapped
    static final String SPLASH_WHEN = "splash_when";   // start | day | nav
    static final String SPLASH_LAST_DAY = "splash_last_day";
    static final String PRAYER_ID = "prayer_id";
    static final String PRAYER_LAYOUT = "prayer_layout"; // stacked | split | card
    static final String PRAYER_SHOW = "prayer_show";   // bitmask 1 orig 2 translit 4 translation
    static final String CUSTOM_TEXT = "custom_text";
    static final String CUSTOM_SUB = "custom_sub";
    static final String PRAY_METHOD = "pray_method";
    static final String PRAY_ASR = "pray_asr";         // 1 shafi, 2 hanafi
    static final String PRAY_ADJ = "pray_adj";         // JSON array of 6 minute offsets
    static final String HA_URL = "ha_url";
    static final String HA_TOKEN = "ha_token";
    static final String CONN_PHONE = "conn_phone";
    static final String CONN_PHONE_SSID = "conn_phone_ssid";
    static final String CONN_PHONE_PASS = "conn_phone_pass";
    static final String CONN_HOTSPOT = "conn_hotspot";
    static final String CONN_TS = "conn_ts";
    static final String CONN_TS_RETRY = "conn_ts_retry";
    static final String LYRICS = "lyrics";
    static final String MUSIC_PKG = "music_pkg";        // package or "last"
    static final String MUSIC_AUTOPLAY = "music_autoplay";
    static final String MUSIC_PLAYLIST = "music_playlist";  // playlist name or link
    static final String NIGHT = "night";
    static final String CELSIUS = "celsius";
    static final String H24 = "h24";
    static final String TOP_SPEED = "top_speed";
    static final String TOP_SPEED_DAY = "top_speed_day";
}
