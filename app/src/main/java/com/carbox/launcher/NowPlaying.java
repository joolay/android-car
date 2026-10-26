package com.carbox.launcher;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** "Now playing" card: song, artist, artwork and playback controls for whatever app is playing. */
final class NowPlaying {

    private static final int C_CARD = 0xFF171C24;
    private static final int C_CARD2 = 0xFF222A37;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_SUB = 0xFF9AA4B2;
    private static final int C_ACCENT = 0xFF3D8BFD;

    private final Context ctx;
    private final float density;
    private final Runnable openMusicApp;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ComponentName listener;

    private final FrameLayout root;
    private final View scrim;
    private final ImageView art;
    private final TextView title, artist;
    private final ImageView playButton;

    private MediaSessionManager sessions;
    private MediaController controller;
    private boolean running;

    NowPlaying(Context ctx, Runnable openMusicApp) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.openMusicApp = openMusicApp;
        this.listener = new ComponentName(ctx, MediaListener.class);

        // Album art fills the whole tile; a dark fade at the bottom keeps text and buttons readable
        root = new FrameLayout(ctx);
        root.setBackground(round(C_CARD, 22));
        root.setClipToOutline(true);

        art = new ImageView(ctx);
        root.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrim = new View(ctx);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x99000000, 0xE6000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.BOTTOM);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        title = new TextView(ctx);
        title.setTextColor(C_TEXT);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(dp(4), 0, dp(1), 0xCC000000);
        artist = new TextView(ctx);
        artist.setTextColor(0xFFD6DBE3);
        artist.setTextSize(14);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        artist.setShadowLayer(dp(4), 0, dp(1), 0xCC000000);
        content.addView(title);
        content.addView(artist);

        // Controls
        LinearLayout controls = new LinearLayout(ctx);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        ImageView prev = control(R.drawable.ic_prev, 0x80000000, 46);
        playButton = control(R.drawable.ic_play, C_ACCENT, 56);
        ImageView next = control(R.drawable.ic_next, 0x80000000, 46);
        prev.setOnClickListener(v -> previous());
        playButton.setOnClickListener(v -> playPause());
        next.setOnClickListener(v -> next());
        prev.setContentDescription("Previous");
        playButton.setContentDescription("Play or pause");
        next.setContentDescription("Next");
        controls.addView(prev, square(46, 0));
        controls.addView(playButton, square(56, 18));
        controls.addView(next, square(46, 18));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        content.addView(controls, clp);

        root.setOnClickListener(v -> openPlayer());
        showIdle();
    }

    View getView() {
        return root;
    }

    // ---------------------------------------------------------------- lifecycle

    void start() {
        running = true;
        if (!hasAccess()) {
            showNoAccess();
            return;
        }
        if (sessions == null) {
            MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (m == null) {
                showIdle();
                return;
            }
            try {
                m.addOnActiveSessionsChangedListener(sessionsListener, listener, ui);
                sessions = m;
            } catch (SecurityException e) {
                showNoAccess();
                return;
            }
        }
        refreshSessions();
    }

    void stop() {
        running = false;
        if (sessions != null) {
            try {
                sessions.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Exception ignored) {
            }
            sessions = null;
        }
        setController(null);
    }

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::pick;

    private void refreshSessions() {
        if (sessions == null) return;
        try {
            pick(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            showNoAccess();
        }
    }

    /** Prefer a session that's actually playing; otherwise the most recent one. */
    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        setController(best);
    }

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            ui.post(NowPlaying.this::refreshSessions);
        }
    };

    private void setController(MediaController c) {
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Exception ignored) {
            }
        }
        controller = c;
        if (controller != null) controller.registerCallback(callback, ui);
        render();
    }

    // ---------------------------------------------------------------- rendering

    private void render() {
        if (controller == null) {
            if (running && !hasAccess()) showNoAccess();
            else showIdle();
            return;
        }
        MediaMetadata md = controller.getMetadata();
        String t = md == null ? null : text(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String a = md == null ? null : text(md, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        title.setText(TextUtils.isEmpty(t) ? appName(controller.getPackageName()) : t);
        artist.setText(TextUtils.isEmpty(a) ? appName(controller.getPackageName()) : a);

        Bitmap bmp = null;
        if (md != null) {
            bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }
        art.clearColorFilter();
        if (bmp != null) {
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setPadding(0, 0, 0, 0);
            art.setImageBitmap(bmp);
            scrim.setVisibility(View.VISIBLE);
        } else {
            Drawable icon = appIcon(controller.getPackageName());
            if (icon != null) {
                iconArt();
                art.setImageDrawable(icon);
            } else {
                placeholderArt();
            }
        }

        PlaybackState s = controller.getPlaybackState();
        boolean playing = s != null && (s.getState() == PlaybackState.STATE_PLAYING
                || s.getState() == PlaybackState.STATE_BUFFERING);
        playButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void showIdle() {
        title.setText("Nothing playing");
        artist.setText("Tap to open music");
        placeholderArt();
        playButton.setImageResource(R.drawable.ic_play);
    }

    private void showNoAccess() {
        title.setText("Music");
        artist.setText("Tap to show song info");
        placeholderArt();
        playButton.setImageResource(R.drawable.ic_play);
    }

    /** No artwork: show an icon in the upper part of the tile, above the text and buttons. */
    private void iconArt() {
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setPadding(dp(40), dp(16), dp(40), dp(118));
        scrim.setVisibility(View.GONE);
    }

    private void placeholderArt() {
        iconArt();
        art.setImageResource(R.drawable.ic_music);
        art.setColorFilter(C_SUB);
    }

    // ---------------------------------------------------------------- actions

    private void playPause() {
        if (controller != null) {
            PlaybackState s = controller.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        } else {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    private void next() {
        if (controller != null) controller.getTransportControls().skipToNext();
        else mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    private void previous() {
        if (controller != null) controller.getTransportControls().skipToPrevious();
        else mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    /** Works without any permission – the last app that played audio receives it. */
    private void mediaKey(int code) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
    }

    private void openPlayer() {
        if (!hasAccess()) {
            try {
                Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return;
            } catch (Exception ignored) {
                // Some car boxes hide this screen; fall through to opening a music app
            }
        }
        if (controller != null) {
            PendingIntent pi = controller.getSessionActivity();
            if (pi != null) {
                try {
                    pi.send();
                    return;
                } catch (PendingIntent.CanceledException ignored) {
                }
            }
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(controller.getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return;
            }
        }
        openMusicApp.run();
    }

    // ---------------------------------------------------------------- helpers

    private boolean hasAccess() {
        String enabled = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(listener.flattenToString());
    }

    private static String text(MediaMetadata md, String... keys) {
        for (String k : keys) {
            CharSequence v = md.getText(k);
            if (!TextUtils.isEmpty(v)) return v.toString();
        }
        return null;
    }

    private String appName(String pkg) {
        try {
            return ctx.getPackageManager().getApplicationLabel(
                    ctx.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private ImageView control(int icon, int bg, int sizeDp) {
        ImageView iv = new ImageView(ctx);
        iv.setImageResource(icon);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(bg);
        iv.setBackground(d);
        int pad = dp(sizeDp / 4);
        iv.setPadding(pad, pad, pad, pad);
        return iv;
    }

    private LinearLayout.LayoutParams square(int sizeDp, int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        lp.leftMargin = dp(leftMarginDp);
        return lp;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
