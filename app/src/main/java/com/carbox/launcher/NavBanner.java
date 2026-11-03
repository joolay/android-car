package com.carbox.launcher;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.LinearLayout;

/**
 * Google Maps / Waze pop their own navigation banner (a heads-up notification) over the top of
 * Car Launcher. One tap takes you to that app's navigation notification page, where "Pop on
 * screen" can be switched off; the directions are still read for the map tile.
 */
final class NavBanner {
    private NavBanner() {
    }

    /** Remember which app and channel the navigation banner comes from. */
    static void seen(String pkg, String channel) {
        if (channel != null) {
            Prefs.put("nav_banner_pkg", pkg);
            Prefs.put("nav_banner_ch", channel);
        }
    }

    /** Open the navigation app's notification channel page, where "Pop on screen" can be switched off. */
    static void openChannel(Context c) {
        String pkg = Prefs.str("nav_banner_pkg", Actions.navPkg() != null ? Actions.navPkg() : "com.google.android.apps.maps");
        String ch = Prefs.str("nav_banner_ch", null);
        Intent i = ch != null
                ? new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).putExtra(Settings.EXTRA_CHANNEL_ID, ch)
                : new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    /** Rows for the Map and navigation settings page. */
    static void rows(Context c, LinearLayout b) {
        b.addView(Panels.section("Navigation app's own banner"));
        b.addView(Ui.text(c, "Google Maps and Waze pop a banner over the top of the screen while navigating. The directions already show here, so it can go.", 12, Ui.SUB, false));
        b.addView(Panels.item("Turn off its pop-up (\"Pop on screen\")", Apps.label(Prefs.str("nav_banner_pkg", Actions.navPkg() != null ? Actions.navPkg() : "com.google.android.apps.maps")), () -> openChannel(c)));
    }

    /** First time a banner shows while Car Launcher is open: offer to switch it off (asked once). */
    static void offer() {
        MainActivity a = MainActivity.I;
        if (a == null || !a.isResumed2() || Prefs.flag("nav_banner_asked", false) || Panels.open()) return;
        Prefs.put("nav_banner_asked", true);
        Panels.show(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "The navigation app is showing its own banner on top. The directions are already on the map tile, so you can switch its pop-up off.", 14, Ui.TEXT2, false));
            LinearLayout r = Ui.row(c);
            r.addView(Ui.button(c, "Not now", false, Panels::close));
            r.addView(Ui.gap(c, 10, 1));
            r.addView(Ui.button(c, "Turn off the banner", true, () -> {
                Panels.close();
                openChannel(c);
            }));
            b.addView(r, Widgets.top(14));
            return Panels.panel("Navigation banner", b, 520, 260);
        });
    }
}
