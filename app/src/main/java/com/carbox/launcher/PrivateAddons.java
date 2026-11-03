package com.carbox.launcher;

import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Private add-ons (unofficial, personal use only): Google live traffic over the map and Spotify lyrics.
 * Hidden from everyone until the owner's secret code is typed in: Settings → About, tap the version 7 times.
 * Only the code's SHA-256 is in the app, never the code itself. Unlocking is kept on that box only;
 * "Lock again" hides them.
 */
final class PrivateAddons {
    /** SHA-256 of the owner's code (lower-case hex). Empty: no code set yet, nothing can be unlocked. */
    private static final String CODE_SHA256 = "";
    private static final String KEY = "private_addons";
    static final String TRAFFIC = "private_gtraffic", SPOTIFY = "private_spotify";

    private PrivateAddons() {
    }

    /** The code has been entered on this box. */
    static boolean unlocked() {
        return !CODE_SHA256.isEmpty() && CODE_SHA256.equals(Prefs.str(KEY, ""));
    }

    /** One private add-on is unlocked and switched on. */
    static boolean on(String which) {
        return unlocked() && Prefs.flag(which, true);
    }

    static String sha256(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(s.trim().getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static int taps;
    private static long tapAt;

    /** About's version line: 7 quick taps ask for the code (or open the private add-ons when unlocked). */
    static void versionTapped() {
        long now = System.currentTimeMillis();
        taps = now - tapAt < 1500 ? taps + 1 : 1;
        tapAt = now;
        if (taps >= 7) {
            taps = 0;
            if (unlocked()) page();
            else ask();
        }
    }

    private static void ask() {
        Panels.push(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            final String[] code = {""};
            EditText f = Widgets.field("Code", "", true, t -> code[0] = t);
            b.addView(f, Ui.lp(Ui.MATCH, Ui.WRAP));
            TextView err = Ui.text(c, "", 12, Ui.RED, false);
            b.addView(err, SettingsPanels.top(6));
            b.addView(Ui.button(c, "Done", true, () -> {
                String h = sha256(code[0]);
                if (!CODE_SHA256.isEmpty() && CODE_SHA256.equals(h)) {
                    Prefs.put(KEY, h);
                    applied();
                    Panels.back();
                    page();
                } else err.setText("That's not it.");
            }), SettingsPanels.top(12));
            return Panels.panel("Private", b, 420, 230);
        });
    }

    private static void page() {
        Panels.push(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Unofficial extras for your own box. Not for the Play Store.", 13, Ui.SUB, false));
            b.addView(Panels.switchRow(0, 0, "Google live traffic", "Google's traffic lines over the map", Prefs.flag(TRAFFIC, true), on -> {
                Prefs.put(TRAFFIC, on);
                applied();
            }), SettingsPanels.top(10));
            b.addView(Panels.switchRow(0, 0, "Spotify lyrics", "Spotify's own lyrics (sign in under Music settings)", Prefs.flag(SPOTIFY, true), on -> {
                Prefs.put(SPOTIFY, on);
                applied();
            }), SettingsPanels.top(8));
            b.addView(Panels.item("Lock again", "Hides these until the code is typed in again", () -> {
                Prefs.put(KEY, "");
                applied();
                Panels.back();
            }), SettingsPanels.top(10));
            return Panels.panel("Private add-ons", b, 520, 330);
        });
    }

    /** Something changed: the map and lyrics pick it up. */
    private static void applied() {
        if (MainActivity.I != null && MainActivity.I.map != null) MainActivity.I.map.reload();
    }
}
