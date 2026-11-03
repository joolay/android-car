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
        Gps.listen(CarHA::onFix);
    }

    private static void onFix() {
        if (!Prefs.flag(ON, false) || !HA.configured() || !Gps.fresh()) return;
        long now = SystemClock.elapsedRealtime();
        boolean stopped = Gps.speed < 1f;
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
            if (!moving && Gps.speed > 3f) { // moved off: tell HA once that the car is driving
                moving = true;
                send(false);
            }
        }
    }

    static void send(boolean parked) {
        try {
            JSONObject a = new JSONObject();
            a.put("latitude", Gps.lat);
            a.put("longitude", Gps.lon);
            a.put("gps_accuracy", 15);
            a.put("source_type", "gps");
            a.put("friendly_name", "Car");
            a.put("icon", "mdi:car");
            a.put("parked", parked);
            a.put("speed_kmh", Math.round(Math.max(0, Gps.speed) * 3.6f));
            if (Gps.town != null) a.put("town", Gps.town);
            a.put("updated", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US).format(new java.util.Date()));
            JSONObject body = new JSONObject();
            body.put("state", parked ? "parked" : "driving");
            body.put("attributes", a);
            Net.post(HA.url() + "/api/states/device_tracker.car", HA.headers(), body.toString(), (r, code) -> Ui.ui.post(() -> {
                Prefs.put("carha_last", (r != null && code < 300 ? "Sent " : "Failed (" + code + ") ")
                        + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date()) + (parked ? " · parked" : " · driving"));
            }));
        } catch (Exception ignored) {
        }
    }

    static void settings() {
        Panels.Page p = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Each time the car stops for 20 seconds, its position goes to Home Assistant as \"Car\" (device_tracker.car). The last one before you switch off is where you parked.", 13, Ui.SUB, false));
            b.addView(Panels.switchRow(0, 0, "Send car location to Home Assistant", null, Prefs.flag(ON, false), v -> {
                Prefs.put(ON, v);
                Panels.refresh();
            }), Widgets.top(6));
            b.addView(Panels.section("Home Assistant"));
            b.addView(Widgets.field("Address, e.g. https://myhome.duckdns.org:8123", Prefs.str(Prefs.HA_URL, ""), false, t -> Prefs.put(Prefs.HA_URL, t.trim())), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Widgets.field("Long-lived access token", Prefs.str(Prefs.HA_TOKEN, ""), true, t -> Prefs.put(Prefs.HA_TOKEN, t.trim())), Widgets.top(8));
            LinearLayout r = Ui.row(c);
            r.addView(Ui.button(c, "Send now", false, () -> {
                if (Gps.has() && HA.configured()) send(Gps.speed < 1f);
                Ui.ui.postDelayed(Panels::refresh, 2500);
            }));
            r.addView(Ui.gap(c, 12, 1));
            r.addView(Ui.text(c, Prefs.str("carha_last", HA.configured() ? "Nothing sent yet" : "Add the address and token first"), 12, Ui.SUB, false));
            b.addView(r, Widgets.top(10));
            HomeApproach.addSettings(c, b);
            b.addView(Ui.text(c, "The address must work away from home (Nabu Casa, a Cloudflare tunnel or a port forward), since the car uses mobile data.", 12, Ui.MUTED, false), Widgets.top(10));
            return Panels.panel("Car location", b, 640, 440);
        };
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }
}
