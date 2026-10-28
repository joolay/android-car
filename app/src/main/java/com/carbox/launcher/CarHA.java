package com.carbox.launcher;

import android.content.Context;
import android.os.SystemClock;
import android.widget.LinearLayout;

import org.json.JSONObject;

/**
 * Sends the car's position to Home Assistant as "device_tracker.car" whenever it has been stopped
 * for 20 seconds (and once when it moves off). The box loses power the moment the car is switched
 * off, so the last stop it reported is where you parked: Home Assistant's map shows it, and an HA
 * automation can notify your phone. Uses the Home Assistant address and token already in Car Launcher.
 */
final class CarHA {
    static final String ON = "carha_on";
    private static long stoppedSince;
    private static boolean sentThisStop, moving;

    private CarHA() {
    }

    static void start() {
        Loc.listen(CarHA::onFix);
    }

    private static void onFix() {
        if (!Store.flag(ON, false) || !HA.configured() || !Loc.fresh()) return;
        long now = SystemClock.elapsedRealtime();
        boolean stopped = Loc.speed < 1f;
        if (stopped) {
            if (stoppedSince == 0) stoppedSince = now;
            if (!sentThisStop && now - stoppedSince >= 20000) {
                sentThisStop = true;
                moving = false;
                send(true);
            }
        } else {
            stoppedSince = 0;
            sentThisStop = false;
            if (!moving && Loc.speed > 3f) { // moved off: tell HA once that the car is driving
                moving = true;
                send(false);
            }
        }
    }

    static void send(boolean parked) {
        try {
            JSONObject a = new JSONObject();
            a.put("latitude", Loc.lat);
            a.put("longitude", Loc.lon);
            a.put("gps_accuracy", 15);
            a.put("source_type", "gps");
            a.put("friendly_name", "Car");
            a.put("icon", "mdi:car");
            a.put("parked", parked);
            a.put("speed_kmh", Math.round(Math.max(0, Loc.speed) * 3.6f));
            if (Loc.town != null) a.put("town", Loc.town);
            a.put("updated", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US).format(new java.util.Date()));
            JSONObject body = new JSONObject();
            body.put("state", parked ? "parked" : "driving");
            body.put("attributes", a);
            Net.post(HA.url() + "/api/states/device_tracker.car", HA.headers(), body.toString(), (r, code) -> U.ui.post(() -> {
                Store.put("carha_last", (r != null && code < 300 ? "Sent " : "Failed (" + code + ") ")
                        + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date()) + (parked ? " · parked" : " · driving"));
            }));
        } catch (Exception ignored) {
        }
    }

    static void settings() {
        Sheets.Page p = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Each time the car stops for 20 seconds, its position goes to Home Assistant as \"Car\" (device_tracker.car). The last one before you switch off is where you parked.", 13, U.SUB, false));
            b.addView(Sheets.switchRow(0, 0, "Send car location to Home Assistant", null, Store.flag(ON, false), v -> {
                Store.put(ON, v);
                Sheets.refresh();
            }), Widgets.top(6));
            b.addView(Sheets.section("Home Assistant"));
            b.addView(Widgets.field("Address, e.g. https://myhome.duckdns.org:8123", Store.str(Store.HA_URL, ""), false, t -> Store.put(Store.HA_URL, t.trim())), U.lp(U.MATCH, U.WRAP));
            b.addView(Widgets.field("Long-lived access token", Store.str(Store.HA_TOKEN, ""), true, t -> Store.put(Store.HA_TOKEN, t.trim())), Widgets.top(8));
            LinearLayout r = U.row(c);
            r.addView(U.button(c, "Send now", false, () -> {
                if (Loc.has() && HA.configured()) send(Loc.speed < 1f);
                U.ui.postDelayed(Sheets::refresh, 2500);
            }));
            r.addView(U.gap(c, 12, 1));
            r.addView(U.text(c, Store.str("carha_last", HA.configured() ? "Nothing sent yet" : "Add the address and token first"), 12, U.SUB, false));
            b.addView(r, Widgets.top(10));
            b.addView(U.text(c, "The address must work away from home (Nabu Casa, a Cloudflare tunnel or a port forward), since the car uses mobile data.", 12, U.MUTED, false), Widgets.top(10));
            return Sheets.panel("Car location", b, 640, 440);
        };
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }
}
