package com.carbox.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.Calendar;

/**
 * At each salaah time (from the prayer time widget's times, with your adjustments) it pauses
 * whatever is playing and announces "It's time for Salaah" in a natural voice. Sunrise is skipped.
 */
final class Salah {
    static final String PAUSE = "salah_pause", ANNOUNCE = "salah_announce";
    private static final int[] FIVE = {0, 2, 3, 4, 5}; // Fajr, Dhuhr, Asr, Maghrib, Isha
    private static Context ctx;
    private static String lastFired = "";

    private Salah() {
    }

    static void start(Context c) {
        if (ctx != null) return;
        ctx = c.getApplicationContext();
        Ui.ui.post(check);
    }

    private static final Runnable check = new Runnable() {
        @Override
        public void run() {
            try {
                tick();
            } catch (Exception ignored) {
            }
            Ui.ui.postDelayed(this, 20000);
        }
    };

    private static void tick() {
        if (!Faith.islam() || (!Prefs.flag(PAUSE, true) && !Prefs.flag(ANNOUNCE, true))) return;
        double[] t = Prayer.times(0);
        if (t == null) return;
        Calendar k = Calendar.getInstance();
        int now = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
        for (int i : FIVE) {
            int at = (int) Math.floor(t[i] * 60 + 0.5); // to the nearest minute, as the widget shows it
            // fire within the first 2 minutes, once per prayer per day
            if (now >= at && now < at + 2) {
                String key = k.get(Calendar.DAY_OF_YEAR) + "-" + i;
                if (key.equals(lastFired)) return;
                lastFired = key;
                fire(Prayer.NAMES[i]);
                return;
            }
        }
    }

    static void fire(String name) {
        Alerts.salahAt = System.currentTimeMillis();
        Alerts.salahName = name;
        if (Alerts.expand("salah")) Ui.ui.post(() -> SalahCard.show(name)); // large notice in the tile area
        else Alerts.front("salah"); // shown in the prayer tile, moved to the first spot
        if (Prefs.flag(PAUSE, true)) pauseMedia();
        if (Prefs.flag(ANNOUNCE, true)) Ui.ui.postDelayed(() -> {
            if (!Rest.play(ctx, "voice/salah_" + name.toLowerCase(java.util.Locale.ROOT) + ".mp3", null))
                Rest.say(ctx, "It's time for Salaah. " + name + ".", null, null);
        }, 700);
    }

    /** Pause whatever app is playing, like pressing pause on the steering wheel. */
    private static void pauseMedia() {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null || !am.isMusicActive()) return;
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
    }
}
