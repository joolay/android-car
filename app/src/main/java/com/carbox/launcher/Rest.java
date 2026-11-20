package com.carbox.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.LinearLayout;

import java.util.Locale;

/**
 * Driver fatigue reminders. Counts driving time and distance since the last real break, and when
 * either passes its limit it beeps and/or says "time to take a break" over the music (ducking it),
 * then repeats every 15 minutes until you stop. A stop of 15 minutes or more (car parked, or the box
 * off) counts as a break and starts the count again. Runs off the launcher's GPS, which keeps going
 * while other apps are open, so it works with Maps or Spotify in front.
 */
final class Rest {
    static final String ON = "rest_on", HOURS = "rest_hours", KM = "rest_km", ALERT = "rest_alert", BREAK = "rest_break";
    private static final String DRIVE = "rest_drive_ms", METERS = "rest_m", LAST_MOVE = "rest_last_move";
    static final long REPEAT_MS = 15 * 60000L;
    private static final float MOVING = 2.0f; // m/s, about 7 km/h

    static long driveMs;          // driving since the last break
    static double meters;         // distance since the last break
    static long lastMovingAt;     // wall clock, survives the box switching off
    private static long lastTick, lastAlertAt, lastSaved;
    private static double lastTrip = -1;
    private static boolean wasMoving;

    private static Context ctx;
    private static TextToSpeech tts;
    private static boolean ttsReady;
    private static AudioFocusRequest focus;

    private Rest() {
    }

    static void init(Context c) {
        if (ctx != null) return;
        ctx = c.getApplicationContext();
        driveMs = Prefs.lng(DRIVE, 0);
        meters = Prefs.lng(METERS, 0);
        lastMovingAt = Prefs.lng(LAST_MOVE, 0);
        Gps.listen(Rest::onFix);
    }

    // ------------------------------------------------------------------ settings

    static boolean on() {
        return Prefs.flag(ON, true);
    }

    /** Remind after this much driving (minutes): 90, 120, 150 or 180. */
    static int limitMin() {
        return Prefs.num(HOURS, 120);
    }

    /** Remind after this distance in km, 0 = off. */
    static int limitKm() {
        return Prefs.num(KM, 200);
    }

    /** "beep", "voice" or "both". */
    static String alert() {
        return Prefs.str(ALERT, "both");
    }

    /** Minutes stopped that count as a break. */
    static int breakMin() {
        return Prefs.num(BREAK, 15);
    }

    static boolean due() {
        return driveMs >= limitMin() * 60000L || (limitKm() > 0 && meters >= limitKm() * 1000.0);
    }

    /** Fraction of the way to the next break (0–1), by whichever limit is closer. */
    static float progress() {
        float t = driveMs / (limitMin() * 60000f);
        float d = limitKm() > 0 ? (float) (meters / (limitKm() * 1000.0)) : 0;
        return Math.min(1f, Math.max(t, d));
    }

    static boolean moving() {
        return Gps.fresh() && Gps.speed > MOVING;
    }

    /** Stopped for this long right now (ms), 0 while driving. */
    static long stoppedMs() {
        if (moving() || lastMovingAt == 0) return 0;
        return Math.max(0, System.currentTimeMillis() - lastMovingAt);
    }

    // ------------------------------------------------------------------ counting

    private static void onFix() {
        long now = System.currentTimeMillis();
        boolean mv = moving();
        double trip = Gps.tripMeters;
        double dd = lastTrip < 0 ? 0 : trip - lastTrip;
        lastTrip = trip;
        if (mv) {
            // a long enough stop since we last moved (parked, or the box was off) is a break
            if (lastMovingAt > 0 && now - lastMovingAt >= breakMin() * 60000L) reset();
            if (wasMoving && lastTick > 0) driveMs += Math.min(now - lastTick, 10000);
            if (dd > 0 && dd < 500) meters += dd;
            lastMovingAt = now;
            if (on() && due() && now - lastAlertAt >= REPEAT_MS) {
                lastAlertAt = now;
                remind(false);
            }
            if (now - lastSaved > 30000) save();
        } else if (wasMoving) {
            save(); // just stopped: remember when, in case the box is switched off
        }
        wasMoving = mv;
        lastTick = now;
    }

    private static void save() {
        lastSaved = System.currentTimeMillis();
        Prefs.put(DRIVE, driveMs);
        Prefs.put(METERS, (long) meters);
        Prefs.put(LAST_MOVE, lastMovingAt);
    }

    /** Start counting from zero ("I've had a break"). */
    static void reset() {
        driveMs = 0;
        meters = 0;
        lastAlertAt = 0;
        save();
    }

    // ------------------------------------------------------------------ the alert

    static String message() {
        boolean byDistance = limitKm() > 0 && meters >= limitKm() * 1000.0 && driveMs < limitMin() * 60000L;
        String what;
        if (byDistance) {
            boolean m = Widgets.mph();
            long d = Math.round(meters / (m ? 1609.34 : 1000.0));
            what = "You've driven " + d + (m ? " miles" : " kilometres") + " without a break.";
        } else {
            long min = Math.max(1, driveMs / 60000);
            long h = min / 60, mm = min % 60;
            String t = h > 0 ? h + (h == 1 ? " hour" : " hours") + (mm >= 5 ? " and " + mm + " minutes" : "") : mm + " minutes";
            what = "You've been driving for " + t + ".";
        }
        return what + " Time to stop and rest for " + breakMin() + " minutes.";
    }

    /** Beep and/or speak over the music. test = from the settings page. */
    static void remind(boolean test) {
        if (ctx == null) return;
        String a = alert();
        boolean beep = !"voice".equals(a), voice = !"beep".equals(a);
        String text = test ? "This is how your break reminder will sound. " + message() : message();
        if (Alerts.expand("rest")) Ui.ui.post(() -> Alerts.showNotice("TIME FOR A BREAK", "Stop and rest", message(), 0xFF4A3410));
        else {
            Ui.ui.post(() -> android.widget.Toast.makeText(ctx, "Time for a break", android.widget.Toast.LENGTH_LONG).show());
            Alerts.front("rest");
        }
        duck(true);
        long delay = 0;
        if (beep) {
            beeps();
            delay = 1400;
        }
        if (voice) Ui.ui.postDelayed(() -> speak(text), delay);
        else Ui.ui.postDelayed(() -> duck(false), delay + 300);
    }

    private static void beeps() {
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            for (int i = 0; i < 3; i++) Ui.ui.postDelayed(() -> tg.startTone(ToneGenerator.TONE_PROP_BEEP, 220), i * 400L);
            Ui.ui.postDelayed(tg::release, 1400);
        } catch (Exception ignored) {
        }
    }

    private static void speak(String text) {
        speak(text, null, null);
    }

    /**
     * Say something over the music (ducking it), e.g. the travel prayer at start-up. If the voice
     * engine has no voice for `loc`, it says `fallback` in the normal language instead.
     */
    static void say(Context c, String text, Locale loc, String fallback) {
        init(c);
        duck(true);
        speak(text, loc, fallback);
    }

    private static void go(String text, Locale loc, String fallback) {
        String t = text;
        if (loc != null && tts.isLanguageAvailable(loc) >= 0) tts.setLanguage(loc);
        else {
            if (loc != null && fallback != null) t = fallback;
            if (tts.setLanguage(Locale.getDefault()) < 0) tts.setLanguage(Locale.UK);
        }
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "rest");
    }

    private static void speak(String text, Locale loc, String fallback) {
        if (tts == null) {
            tts = new TextToSpeech(ctx, s -> {
                ttsReady = s == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    int r = tts.setLanguage(Locale.getDefault());
                    if (r < 0) tts.setLanguage(Locale.UK);
                    tts.setAudioAttributes(attrs());
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                        }

                        @Override
                        public void onDone(String id) {
                            Ui.ui.post(() -> duck(false));
                        }

                        @Override
                        public void onError(String id) {
                            Ui.ui.post(() -> duck(false));
                        }
                    });
                    go(text, loc, fallback);
                } else {
                    beeps(); // no voice engine: fall back to beeps
                    Ui.ui.postDelayed(() -> duck(false), 1600);
                }
            });
            return;
        }
        if (ttsReady) go(text, loc, fallback);
        else duck(false);
    }

    private static android.media.MediaPlayer player;

    /** Car Launcher is playing a recording right now (the splash voice, a reminder). */
    /** The voice (recording or spoken text) is still going. */
    static boolean speaking() {
        try {
            return talking() || (tts != null && tts.isSpeaking());
        } catch (Exception e) {
            return false;
        }
    }

    static boolean talking() {
        try {
            return player != null && player.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Play a recording over the music (ducking it): an app asset such as "voice/islam1_orig.mp3",
     * or a file the user picked. Returns false if it can't be played, so the caller can fall back.
     */
    static boolean play(Context c, String asset, android.net.Uri uri) {
        init(c);
        try {
            if (player != null) player.release();
            player = new android.media.MediaPlayer();
            player.setAudioAttributes(attrs());
            if (uri != null) player.setDataSource(ctx, uri);
            else {
                android.content.res.AssetFileDescriptor fd = ctx.getAssets().openFd(asset);
                player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
            }
            player.setOnCompletionListener(mp -> {
                duck(false);
                mp.release();
                if (player == mp) player = null;
            });
            player.prepare();
            duck(true);
            player.start();
            return true;
        } catch (Exception e) {
            if (player != null) player.release();
            player = null;
            return false;
        }
    }

    private static AudioAttributes attrs() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    /** Lower the music while we talk, like a navigation prompt. */
    private static void duck(boolean down) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (down) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs()).build();
                am.requestAudioFocus(focus);
            } else if (focus != null) {
                am.abandonAudioFocusRequest(focus);
                focus = null;
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ settings page

    static Panels.Page page;

    static void setup() {
        Panels.Page p = page = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Beeps or speaks over your music when it's time to stop and rest, then every 15 minutes until you do. A stop of "
                    + breakMin() + " minutes or more counts as a break.", 13, Ui.SUB, false));
            b.addView(Panels.switchRow(0, 0, "Break reminders", null, on(), v -> {
                Prefs.put(ON, v);
                Panels.refresh();
            }), Widgets.top(8));
            int h = limitMin();
            b.addView(Panels.row("Remind after", seg(c, HOURS, 90, "1½ h", h), seg(c, HOURS, 120, "2 h", h), seg(c, HOURS, 150, "2½ h", h), seg(c, HOURS, 180, "3 h", h)), Widgets.top(6));
            int km = limitKm();
            boolean m = Widgets.mph();
            b.addView(Panels.row("Or after", seg(c, KM, 0, "Off", km), seg(c, KM, 150, m ? "95 mi" : "150 km", km),
                    seg(c, KM, 200, m ? "125 mi" : "200 km", km), seg(c, KM, 300, m ? "185 mi" : "300 km", km)), Widgets.top(6));
            String a = alert();
            b.addView(Panels.row("Alert", Ui.seg(c, "Beep", "beep".equals(a), () -> set(ALERT, "beep")), Ui.seg(c, "Voice", "voice".equals(a), () -> set(ALERT, "voice")),
                    Ui.seg(c, "Both", "both".equals(a), () -> set(ALERT, "both"))), Widgets.top(6));
            b.addView(Panels.switchRow(0, 0, "Expand the alert into the tile area", "Off: it shows inside the Driving timer tile", Prefs.flag("expand_rest", true), v -> Prefs.put("expand_rest", v)), Widgets.top(4));
            int br = breakMin();
            b.addView(Panels.row("Break counts after", seg(c, BREAK, 10, "10 min", br), seg(c, BREAK, 15, "15 min", br), seg(c, BREAK, 20, "20 min", br)), Widgets.top(6));
            LinearLayout btns = Ui.row(c);
            btns.addView(Ui.button(c, "Test alert", false, () -> remind(true)));
            btns.addView(Ui.gap(c, 10, 1));
            btns.addView(Ui.button(c, "I've had a break", true, () -> {
                reset();
                Panels.refresh();
            }));
            b.addView(btns, Widgets.top(14));
            return Panels.panel("Driving timer", b, 640, 440);
        };
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }

    private static android.view.View seg(Context c, String key, int v, String label, int cur) {
        return Ui.seg(c, label, v == cur, () -> {
            Prefs.put(key, v);
            Panels.refresh();
        });
    }

    private static void set(String key, String v) {
        Prefs.put(key, v);
        Panels.refresh();
    }
}
