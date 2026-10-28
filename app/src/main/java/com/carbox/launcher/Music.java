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
        String p = Store.str(Store.MUSIC_PKG, null);
        if (p == null) {
            // not chosen yet: the first well-known music app on the box
            for (String m : Actions.MUSIC) if (Apps.installed(m)) return m;
            return null;
        }
        return LAST.equals(p) || !Apps.installed(p) ? null : p;
    }

    static String label() {
        String p = Store.str(Store.MUSIC_PKG, null);
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

    static boolean musicActive(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return am != null && am.isMusicActive();
    }

    // ------------------------------------------------------------------ start of drive

    /** Called first thing when the launcher starts (before the splash), once per car start. */
    static void atStart(MainActivity a) {
        if (!Store.flag(Store.MUSIC_AUTOPLAY, false)) return;
        long bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (Math.abs(Store.lng("music_boot", 0) - bootAt) < 60000) return;
        Store.put("music_boot", bootAt);
        if (musicActive(a)) return; // something is already playing (e.g. the phone over Bluetooth)
        // the chosen playlist (e.g. Liked Songs) comes first when set to start with it
        if ("playlist".equals(startWith())) {
            playPlaylist(a, true);
            return;
        }
        resume(a);
        // the music app may still be waking up after boot: try once more, then fall back to the playlist
        U.ui.postDelayed(() -> {
            if (!musicActive(a)) resume(a);
        }, 1500);
        U.ui.postDelayed(() -> {
            if (!musicActive(a) && !Store.str(Store.MUSIC_PLAYLIST, "").isEmpty()) playPlaylist(a, true);
        }, 5000);
    }

    /** Press "play" on the chosen app (or on whatever played last), without opening it. */
    static void resume(Context c) {
        String p = pkg();
        MediaController m = MediaListener.controller;
        if (m != null && (p == null || p.equals(m.getPackageName()))) {
            m.getTransportControls().play();
            return;
        }
        if (p == null) {
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
        return Store.str("music_start", Store.str(Store.MUSIC_PLAYLIST, "").trim().isEmpty() ? "last" : "playlist");
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
        String q = Store.str(Store.MUSIC_PLAYLIST, "").trim();
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
                if (uri != null) m.getTransportControls().playFromUri(Uri.parse(uri), null);
                else m.getTransportControls().playFromSearch(q, null);
                // check it took: if nothing plays within a few seconds, fall back to opening the app
                U.ui.postDelayed(() -> {
                    if (!musicActive(c)) playByOpening(c, q, pkg(), comeBack);
                }, 5000);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        if (tries == 0) {
            if (!Apps.installed(target)) return false;
            mediaButton(c, target); // wakes the app (it may start its last song for a moment)
        }
        if (tries >= 20) return false; // checks every 0.4 s for up to 8 s
        U.ui.postDelayed(() -> {
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
        if (link && fp != null) U.ui.postDelayed(() -> mediaButton(c, fp), 2500);
        if (comeBack) U.ui.postDelayed(() -> {
            try {
                c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            } catch (Exception ignored) {
            }
        }, link ? 4000 : 3500);
    }

    /** Open the default music app (used when nothing is playing). */
    static boolean open(Context c) {
        String p = pkg();
        return p != null && Apps.launchPkg(c, p);
    }
}
