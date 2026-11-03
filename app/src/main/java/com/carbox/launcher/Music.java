package com.carbox.launcher;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Default music app, auto play when the car starts, and a fallback playlist. */
final class Music {
    private Music() {
    }

    static final String LAST = "last"; // "whatever played last time"

    /** The chosen music app's package, or null for "last app that played". */
    static String pkg() {
        String p = Prefs.str(Prefs.MUSIC_PKG, null);
        if (p == null) {
            // not chosen yet: the first well-known music app on the box
            for (String m : Actions.MUSIC) if (Apps.installed(m)) return m;
            return null;
        }
        return LAST.equals(p) || !Apps.installed(p) ? null : p;
    }

    static String label() {
        String p = Prefs.str(Prefs.MUSIC_PKG, null);
        if (LAST.equals(p)) return "Last app that played";
        String k = pkg();
        return k == null ? "Choose" : Apps.label(k);
    }

    /** Apps on the box that can play music (they offer a media browser service), well-known ones first. */
    static List<Apps.App> players(Context c) {
        List<Apps.App> out = new ArrayList<>();
        for (String m : Actions.MUSIC) {
            Apps.App a = Apps.byPkg(m);
            if (a != null) out.add(a);
        }
        try {
            for (ResolveInfo ri : c.getPackageManager().queryIntentServices(new Intent("android.media.browse.MediaBrowserService"), 0)) {
                Apps.App a = Apps.byPkg(ri.serviceInfo.packageName);
                if (a != null && !out.contains(a)) out.add(a);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * Is the music really playing? The music app's own playing state when it's known; otherwise
     * Android's "sound is playing" — but not while Car Launcher itself is talking (the splash voice
     * counted as music before, so a start-up that never played looked fine).
     */
    static boolean musicActive(Context c) {
        MediaController m = MediaListener.controller;
        String p = pkg();
        if (m != null && (p == null || p.equals(m.getPackageName()))) return MediaListener.playing();
        if (Rest.talking()) return false;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return am != null && am.isMusicActive();
    }

    // ------------------------------------------------------------------ start of drive

    /** Called first thing when the launcher starts (before the splash), once per car start. */
    // ---- what happened at the last car start (shown in Music settings, to see why music didn't start)
    static void log(String what) {
        java.util.List<String> l = new java.util.ArrayList<>(Prefs.list("music_log"));
        l.add(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + "  " + what);
        while (l.size() > 14) l.remove(0);
        Prefs.putList("music_log", l);
        Trace.event("Music: " + what);
    }

    /** At a car start with Play music at car start on: starts the music and returns true (the music app is then opened). */
    static boolean atStart(MainActivity a) {
        long bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (Math.abs(Prefs.lng("music_boot", 0) - bootAt) < 60000) return false;
        Prefs.put("music_boot", bootAt);
        Prefs.put("music_log", "[]");
        log("Car start (box on for " + SystemClock.elapsedRealtime() / 1000 + " s). App: " + label() + ", start with: " + startWith());
        if (!Prefs.flag(Prefs.MUSIC_AUTOPLAY, false)) { // still logged, so the reason shows in Music settings
            log("Play music at car start is off: nothing done");
            return false;
        }
        if (musicActive(a)) {
            log("Something is already playing: left alone");
            return false; // something is already playing (e.g. the phone over Bluetooth)
        }
        // the app is opened now (so it's loaded and ready), but the music only starts once the
        // splash voice is done: go() is called by the splash, or straight after it when it has no voice
        pending = () -> {
            guard(a);
            // the chosen playlist (e.g. Liked Songs) comes first when set to start with it
            if ("playlist".equals(startWith())) {
                playPlaylist(a, true);
                return;
            }
            resume(a);
            // the music app may still be waking up: try once more, then fall back to the playlist
            Ui.ui.postDelayed(() -> {
                if (!musicActive(a)) resume(a);
            }, 1500);
            Ui.ui.postDelayed(() -> {
                if (!musicActive(a) && !Prefs.str(Prefs.MUSIC_PLAYLIST, "").isEmpty()) playPlaylist(a, true);
            }, 5000);
        };
        Ui.ui.postDelayed(() -> go("waited 40 s"), 40000); // never left silent
        log("Opening " + label() + " (waits for the splash voice), then the splash");
        return true;
    }

    private static Runnable pending;

    /** Start the car-start music now (once): after the splash voice, or when there's none. */
    static void go(String why) {
        Runnable r = pending;
        pending = null;
        if (r == null) return;
        log("Starting the music (" + why + ")");
        r.run();
    }

    /** Waits for the splash voice to finish (it has a moment to begin), then starts the music. */
    static void afterVoice() {
        if (pending == null) return;
        final long from = SystemClock.elapsedRealtime();
        Ui.ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (pending == null) return;
                if (!Rest.speaking() || SystemClock.elapsedRealtime() - from > 35000) go("splash voice done");
                else Ui.ui.postDelayed(this, 300);
            }
        }, 1200);
    }

    // ---- start-up guard: for the first 45 s, nothing but your own pause button stops the music.
    // Touching the screen, closing the splash or other apps starting can make the music app pause
    // (or stop it loading); the guard simply presses play again.
    private static long guardUntil;
    private static boolean paused, started;

    private static void guard(Context c) {
        guardUntil = SystemClock.elapsedRealtime() + 45000;
        paused = false;
        started = false;
        Ui.ui.postDelayed(new Runnable() {
            int quiet;

            @Override
            public void run() {
                if (paused || SystemClock.elapsedRealtime() > guardUntil) return;
                // something else is playing now (a video in YouTube, say): leave it alone
                MediaController other = MediaListener.controller;
                String mine = pkg();
                if (other != null && mine != null && !mine.equals(other.getPackageName()) && MediaListener.playing()) {
                    log("Guard: " + Apps.label(other.getPackageName()) + " is playing now, stopped");
                    paused = true;
                    return;
                }
                if (musicActive(c)) {
                    started = true;
                    quiet = 0;
                } else if (++quiet >= 2) { // stopped (or not started) for 3 s: play again
                    quiet = 0;
                    MediaController mc = MediaListener.controller;
                    android.media.session.PlaybackState ps = mc == null ? null : mc.getPlaybackState();
                    android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
                    boolean net = cm != null && cm.getActiveNetwork() != null;
                    log("Guard: silent, pressing play again (session: " + (mc == null ? "none" : Apps.label(mc.getPackageName()) + " state " + (ps == null ? "?" : ps.getState()))
                            + ", internet " + (net ? "yes" : "no") + ")");
                    resume(c); // play on the music app's own session when it has one, else its play button (not any other app's)
                }
                Ui.ui.postDelayed(this, 1500);
            }
        }, 9000); // the first few seconds belong to the normal start (app waking up, playlist)
    }

    /** You pressed pause: the start-up guard stops. */
    static void userPaused() {
        paused = true;
    }

    /** You opened an app: if it isn't the music app, the start-up guard stops (it was pressing play over YouTube). */
    static void appOpened(String pkgName) {
        if (paused || SystemClock.elapsedRealtime() > guardUntil) return;
        String mine = pkg();
        if (mine != null && mine.equals(pkgName)) return;
        paused = true;
        log("Guard: you opened " + Apps.label(pkgName) + ", stopped");
    }

    /** Run r once the music is playing (or after maxMs), so other apps start after it. */
    static void whenStarted(Context c, Runnable r, long maxMs) {
        long end = SystemClock.elapsedRealtime() + maxMs;
        Ui.ui.post(new Runnable() {
            @Override
            public void run() {
                if (started || musicActive(c) || SystemClock.elapsedRealtime() > end || SystemClock.elapsedRealtime() > guardUntil) r.run();
                else Ui.ui.postDelayed(this, 500);
            }
        });
    }

    /** Press "play" on the chosen app (or on whatever played last), without opening it. */
    private static int tries;

    private static String startUri; // what was asked for at the car start
    private static int guardTries;

    static void resume(Context c) {
        String p = pkg();
        MediaController m = MediaListener.controller;
        if (m == null || (p != null && !p.equals(m.getPackageName()))) m = MediaListener.controllerFor(p); // the chosen app's own session, even if it isn't the "current" one
        // a session with nothing loaded yet (state 0) ignores play: the logs showed Spotify like that
        // for the first seconds, so then it's treated as no session (play button / Android's play key)
        android.media.session.PlaybackState st = m == null ? null : m.getPlaybackState();
        if (m != null && (st == null || st.getState() == android.media.session.PlaybackState.STATE_NONE)) {
            // nothing loaded: play and the play keys are ignored (the logs showed Spotify like that for a whole minute),
            // so every third try asks again for the playlist to be loaded and played
            if (startUri != null && guardTries++ % 3 == 0) {
                log("Nothing loaded in " + Apps.label(m.getPackageName()) + ": asking again for " + startUri);
                try {
                    m.getTransportControls().playFromUri(android.net.Uri.parse(startUri), null);
                    return;
                } catch (Exception ignored) {
                }
            }
            m = null;
        }
        if (m != null && (p == null || p.equals(m.getPackageName()))) {
            log("Play pressed on " + Apps.label(m.getPackageName()) + "'s session");
            m.getTransportControls().play();
            return;
        }
        // no session yet: take turns between the app's own play button and Android's play key
        // (Android sends that to the last app that played, which after a start is usually the music app)
        boolean viaAndroid = p == null || (tries++ % 2 == 1);
        log("No session from " + (p == null ? "a player" : Apps.label(p)) + " yet: " + (viaAndroid ? "Android's play key" : "its play button"));
        if (viaAndroid) {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
            }
            return;
        }
        mediaButton(c, p);
    }

    private static void mediaButton(Context c, String p) {
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(p)
                    .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY));
            try {
                c.sendBroadcast(i);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Start the fallback playlist. A name is searched in the music app ("play Liked Songs");
     * a link opens that playlist and presses play. Android only allows this by briefly opening the
     * music app, so afterwards the launcher comes back to the front.
     */
    /** "playlist" (start with the chosen playlist) or "last" (carry on with whatever played last). */
    static String startWith() {
        return Prefs.str("music_start", Prefs.str(Prefs.MUSIC_PLAYLIST, "").trim().isEmpty() ? "last" : "playlist");
    }

    /** Spotify's own address for a few names people type, so it plays that exact list. */
    private static String spotifyUri(String q) {
        String l = q.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "").trim();
        if (l.equals("liked songs") || l.equals("liked") || l.equals("my liked songs") || l.equals("favourites") || l.equals("favorites"))
            return "spotify:collection:tracks";
        if (q.startsWith("spotify:")) return q;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("open\\.spotify\\.com/(playlist|album|artist|track)/([A-Za-z0-9]+)").matcher(q);
        if (m.find()) return "spotify:" + m.group(1) + ":" + m.group(2);
        return null;
    }

    static void playPlaylist(Context c, boolean comeBack) {
        String q = Prefs.str(Prefs.MUSIC_PLAYLIST, "").trim();
        if (q.isEmpty()) return;
        String p = pkg();
        // Best way: ask the music app's media session directly (no screen switching).
        // At start-up the app may not be running yet, so wake it and wait for its session.
        String target = p != null ? p : "com.spotify.music";
        String uri = "com.spotify.music".equals(target) ? spotifyUri(q) : (q.contains("://") || q.contains(":") && !q.contains(" ") ? q : null);
        if (viaSession(c, target, q, uri, comeBack, 0)) return;
        playByOpening(c, q, p, comeBack);
    }

    private static boolean viaSession(Context c, String target, String q, String uri, boolean comeBack, int tries) {
        android.media.session.MediaController m = MediaListener.controllerFor(target);
        if (m != null) {
            try {
                log("Asked " + Apps.label(target) + " to play " + (uri != null ? uri : "\"" + q + "\"") + " (after " + tries * 0.4 + " s)");
                startUri = uri;
                if (uri != null) m.getTransportControls().playFromUri(Uri.parse(uri), null);
                else m.getTransportControls().playFromSearch(q, null);
                // check it took: if nothing plays within a few seconds, fall back to opening the app
                Ui.ui.postDelayed(() -> {
                    if (musicActive(c)) log("Music is playing");
                    else if (android.os.SystemClock.elapsedRealtime() - openedAt < 60000) // opened at the car start already:
                        log("Still silent after 5 s: leaving it to the guard (no second jump into " + Apps.label(target) + ")");
                    else {
                        log("Still silent after 5 s: opening " + Apps.label(target));
                        playByOpening(c, q, pkg(), comeBack);
                    }
                }, 5000);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        if (tries == 0) {
            if (!Apps.installed(target)) {
                log(target + " isn't installed");
                return false;
            }
            log("Waking " + Apps.label(target) + " (no music session yet)");
            mediaButton(c, target); // wakes the app (it may start its last song for a moment)
        }
        if (tries == 19) log(Apps.label(target) + " never showed a music session (is notification access on?)");
        if (tries >= 20) return false; // checks every 0.4 s for up to 8 s
        Ui.ui.postDelayed(() -> {
            if (!viaSession(c, target, q, uri, comeBack, tries + 1) && tries + 1 >= 20) playByOpening(c, q, pkg(), comeBack);
        }, 400);
        return true; // still waiting for the session
    }

    /** Older way: open the playlist in the music app, press play, then come back to the launcher. */
    private static void playByOpening(Context c, String q, String p, boolean comeBack) {
        Intent i;
        String su = "com.spotify.music".equals(p) || p == null ? spotifyUri(q) : null;
        if (su != null) q = su;
        boolean link = q.startsWith("http") || q.contains(":playlist:") || q.startsWith("spotify:");
        if (link) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(q));
        } else {
            i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .putExtra(SearchManager.QUERY, q)
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/playlist")
                    .putExtra("android.intent.extra.playlist", q);
        }
        if (p != null) i.setPackage(p);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            android.widget.Toast.makeText(c, "Couldn't open the playlist in " + label(), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final String fp = p;
        if (link && fp != null) Ui.ui.postDelayed(() -> mediaButton(c, fp), 2500);
        if (comeBack) Ui.ui.postDelayed(() -> {
            try {
                c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } catch (Exception ignored) {
            }
        }, link ? 4000 : 3500);
    }

    /** Open the default music app (used when nothing is playing). */
    static long openedAt; // when the music app was last brought up (car start)

    static boolean open(Context c) {
        String p = pkg();
        boolean ok = p != null && Apps.launchPkg(c, p);
        if (ok) openedAt = android.os.SystemClock.elapsedRealtime();
        return ok;
    }
}
