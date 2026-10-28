package com.carbox.launcher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.LinearLayout;

/**
 * Google Maps / Waze pop their own navigation banner (a heads-up notification) over the top of
 * Car Launcher. Two ways to stop it: switch off "Pop on screen" for that app's navigation
 * channel (one tap takes you there), or, with a one-off ADB grant, Car Launcher turns pop-up
 * banners off while it's on screen and back on when you leave it.
 */
final class NavBanner {
    static final String HIDE = "headsup_off";

    private NavBanner() {
    }

    /** Remember which app and channel the navigation banner comes from. */
    static void seen(String pkg, String channel) {
        if (channel != null) {
            Store.put("nav_banner_pkg", pkg);
            Store.put("nav_banner_ch", channel);
        }
    }

    static boolean canControl(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Called when Car Launcher comes to the front (true) or leaves it (false). */
    static void front(Context c, boolean front) {
        if (!Store.flag(HIDE, false) || !canControl(c)) return;
        try {
            Settings.Global.putInt(c.getContentResolver(), "heads_up_notifications_enabled", front ? 0 : 1);
        } catch (Exception ignored) {
        }
    }

    /** Open the navigation app's notification channel page, where "Pop on screen" can be switched off. */
    static void openChannel(Context c) {
        String pkg = Store.str("nav_banner_pkg", Actions.navPkg() != null ? Actions.navPkg() : "com.google.android.apps.maps");
        String ch = Store.str("nav_banner_ch", null);
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
        b.addView(Sheets.section("Navigation app's own banner"));
        b.addView(U.text(c, "Google Maps and Waze pop a banner over the top of the screen while navigating. The directions already show here, so it can go.", 12, U.SUB, false));
        b.addView(Sheets.item("Turn off its pop-up (\"Pop on screen\")", Apps.label(Store.str("nav_banner_pkg", Actions.navPkg() != null ? Actions.navPkg() : "com.google.android.apps.maps")), () -> openChannel(c)));
        boolean ok = canControl(c);
        b.addView(Sheets.switchRow(0, 0, "Hide all pop-up banners while Car Launcher is open",
                ok ? "Banners come back when you leave Car Launcher" : "Needs a one-off ADB command first (below)",
                Store.flag(HIDE, false) && ok, on -> {
                    Store.put(HIDE, on);
                    if (!on && canControl(c)) {
                        try {
                            Settings.Global.putInt(c.getContentResolver(), "heads_up_notifications_enabled", 1);
                        } catch (Exception ignored) {
                        }
                    } else front(c, true);
                    Sheets.refresh();
                }));
        if (!ok) b.addView(U.text(c, "adb shell pm grant " + c.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS", 12, U.ACCENT_SOFT, false), Widgets.top(4));
    }

    /** First time a banner shows while Car Launcher is open: offer to switch it off (asked once). */
    static void offer() {
        MainActivity a = MainActivity.I;
        if (a == null || !a.isResumed2() || Store.flag("nav_banner_asked", false) || Sheets.open()) return;
        if (Store.flag(HIDE, false) && canControl(a)) return;
        Store.put("nav_banner_asked", true);
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "The navigation app is showing its own banner on top. The directions are already on the map tile, so you can switch its pop-up off.", 14, U.TEXT2, false));
            LinearLayout r = U.row(c);
            r.addView(U.button(c, "Not now", false, Sheets::close));
            r.addView(U.gap(c, 10, 1));
            r.addView(U.button(c, "Turn off the banner", true, () -> {
                Sheets.close();
                openChannel(c);
            }));
            b.addView(r, Widgets.top(14));
            return Sheets.panel("Navigation banner", b, 520, 260);
        });
    }
}
