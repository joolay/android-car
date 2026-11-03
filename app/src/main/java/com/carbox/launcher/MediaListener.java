package com.carbox.launcher;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Notification access lets the launcher (1) see which app is playing music and control it, and
 * (2) read Google Maps / Waze navigation notifications to show the next turn on the map tile.
 */
public class MediaListener extends NotificationListenerService {

    // ------------------------------------------------------------------ media

    static MediaController controller;

    /** The media session of one app (e.g. Spotify), or null if it has none right now. */
    static MediaController controllerFor(String pkg) {
        if (pkg == null) return null;
        if (controller != null && pkg.equals(controller.getPackageName())) return controller;
        try {
            if (msm != null) for (MediaController m : msm.getActiveSessions(component(ctx))) if (pkg.equals(m.getPackageName())) return m;
        } catch (Exception ignored) {
        }
        return null;
    }
    private static final List<Runnable> mediaListeners = new ArrayList<>();
    private static MediaSessionManager msm;
    private static final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = MediaListener::pick;
    private static final MediaController.Callback cb = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            notifyMedia();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) Ui.ui.post(() -> {
                if (msm != null) {
                    try {
                        pick(msm.getActiveSessions(component(ctx)));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @Override
        public void onMetadataChanged(MediaMetadata m) {
            notifyMedia();
        }

        @Override
        public void onSessionDestroyed() {
            controller = null;
            notifyMedia();
        }
    };
    private static Context ctx;

    static ComponentName component(Context c) {
        return new ComponentName(c, MediaListener.class);
    }

    static boolean hasAccess(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    static void requestAccess(Context c) {
        Status.open(c, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    }

    static void listenMedia(Runnable r) {
        mediaListeners.add(r);
    }

    /** Called by the home screen once access is granted. */
    static void attach(Context c) {
        ctx = c.getApplicationContext();
        if (msm != null || !hasAccess(c)) return;
        try {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            msm.addOnActiveSessionsChangedListener(sessionsChanged, component(ctx), Ui.ui);
            pick(msm.getActiveSessions(component(ctx)));
        } catch (Exception e) {
            msm = null;
        }
    }

    /** Prefer whichever session is playing; otherwise keep the current one. */
    private static void pick(List<MediaController> list) {
        if (list == null) return;
        MediaController best = null;
        for (MediaController m : list) {
            PlaybackState s = m.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                best = m;
                break;
            }
        }
        if (best == null) {
            if (controller != null) {
                for (MediaController m : list) if (m.getSessionToken().equals(controller.getSessionToken())) return;
            }
            if (!list.isEmpty()) best = list.get(0);
        }
        if (best == null && controller == null) return;
        if (controller != null && best != null && controller.getSessionToken().equals(best.getSessionToken())) return;
        if (controller != null) controller.unregisterCallback(cb);
        controller = best;
        if (controller != null) controller.registerCallback(cb, Ui.ui);
        notifyMedia();
    }

    private static void notifyMedia() {
        for (Runnable r : new ArrayList<>(mediaListeners)) r.run();
    }

    static boolean playing() {
        if (controller == null) return false;
        PlaybackState s = controller.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    /** Current position in ms, extrapolated from the last state update. */
    static long position() {
        if (controller == null) return 0;
        PlaybackState s = controller.getPlaybackState();
        if (s == null) return 0;
        long p = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            p += (long) ((android.os.SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        return Math.max(0, p);
    }

    // ------------------------------------------------------------------ navigation

    static final class Nav {
        String distance, instruction, eta;
        Bitmap icon;
    }

    static Nav nav;
    static String navPkg;
    private static final List<Runnable> navListeners = new ArrayList<>();
    private static final Pattern DIST = Pattern.compile("^\\s*[\\d.,]+\\s*(m|km|ft|mi|yd|metres|meters|miles)\\b.*", Pattern.CASE_INSENSITIVE);

    static void listenNav(Runnable r) {
        navListeners.add(r);
    }

    private static boolean isNavApp(String pkg) {
        for (String n : Actions.NAV_APPS) if (n.equals(pkg)) return true;
        return false;
    }

    @Override
    public void onListenerConnected() {
        ctx = getApplicationContext();
        Prefs.init(this); // may start before the launcher at boot
        Ui.ui.post(() -> attach(ctx));
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) onNotificationPosted(sbn);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Prefs.init(this);
        // calls from BT Phone: show Car Launcher's own call screen and press BT Phone's buttons
        final Notification cn = sbn.getNotification();
        if (CallState.isCallNotification(this, sbn.getPackageName(), cn)) {
            final String key = sbn.getKey(), pkg = sbn.getPackageName();
            if (Prefs.flag("call_screen", true)) Ui.ui.post(() -> CallState.fromNotification(this, key, pkg, cn));
            return;
        }
        if (!isNavApp(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) == 0) return;
        Bundle e = n.extras;
        String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(e.getCharSequence(Notification.EXTRA_TEXT));
        String big = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String sub = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
        if (title == null && text == null) return;
        Nav v = new Nav();
        if (title != null && DIST.matcher(title).matches()) {
            v.distance = title;
            v.instruction = big != null ? big : text;
        } else if (text != null && DIST.matcher(text).matches()) {
            v.distance = text;
            v.instruction = title;
        } else {
            v.instruction = title;
            v.eta = text;
        }
        if (sub != null) v.eta = sub;
        v.icon = bitmap(n);
        final Nav fv = v;
        final String pkg = sbn.getPackageName();
        NavBanner.seen(pkg, n.getChannelId());
        Ui.ui.post(() -> {
            boolean started = nav == null;
            nav = fv;
            navPkg = pkg;
            for (Runnable r : new ArrayList<>(navListeners)) r.run();
            if (started && MainActivity.I != null) Splash.maybeShow(MainActivity.I, "nav");
            if (started) Ui.ui.postDelayed(NavBanner::offer, 2500);
        });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        final String key = sbn.getKey();
        Ui.ui.post(() -> CallState.notificationRemoved(key));
        if (!isNavApp(sbn.getPackageName()) || !sbn.getPackageName().equals(navPkg)) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) == 0) return;
        Ui.ui.post(() -> {
            nav = null;
            for (Runnable r : new ArrayList<>(navListeners)) r.run();
        });
    }

    private Bitmap bitmap(Notification n) {
        try {
            Icon ic = n.getLargeIcon();
            if (ic != null) {
                Drawable d = ic.loadDrawable(this);
                if (d != null) {
                    int s = Ui.u(52);
                    Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(b);
                    d.setBounds(0, 0, s, s);
                    d.draw(c);
                    return b;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String str(CharSequence s) {
        if (s == null) return null;
        String t = s.toString().trim();
        return t.isEmpty() ? null : t;
    }
}
