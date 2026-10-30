package com.carbox.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Album art filling the tile, title/artist, controls, and synced lyrics scrolling over the art. */
final class NowPlaying {
    private final Context c;
    final FrameLayout view;
    private final ImageView art;
    private final TextView title, artist, empty;
    private final ImageView playBtn;
    private final LinearLayout lyricBox, content;
    private final TextView[] lyricLines = new TextView[3];
    private boolean running, lyricsHidden;

    private String trackKey;
    private final List<long[]> lyricTimes = new ArrayList<>();
    private final List<String> lyricText = new ArrayList<>();

    static NowPlaying I;
    private View lyricsBtn;
    private EqView eq;
    private MusicFolder folder;
    private LinearLayout videoBtns;
    /** Apps whose "now playing" is a video. */
    static final String[] VIDEO = {"com.google.android.youtube", "app.revanced.android.youtube", "app.rvx.android.youtube",
            "com.netflix.mediaclient", "org.videolan.vlc", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro", "org.xbmc.kodi",
            "com.plexapp.android", "org.jellyfin.mobile", "com.stremio.one", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus",
            "com.showmax.app", "com.dstv.now.android", "com.google.android.apps.youtube.kids", "com.vanced.android.youtube"};

    /** Home the way a person presses it (the Call helper can), so video apps go picture-in-picture. */
    static void pressHome() {
        if (BackButtonService.I != null && BackButtonService.I.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)) return;
        Context x = MainActivity.I;
        if (x == null) return;
        try {
            x.startActivity(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
                    .setPackage(x.getPackageName()).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    static boolean isVideo(String pkg) {
        for (String v : VIDEO) if (v.equals(pkg)) return true;
        return false;
    }

    private View videoBtn(int icon, String desc, Runnable r) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(U.outline(0xBF0B0E13, U.ACCENT_SOFT, 1.5f, 15));
        f.addView(U.icon(c, icon, U.ACCENT_SOFT), U.flpu(16, 16, Gravity.CENTER));
        f.setContentDescription(desc);
        f.setOnClickListener(v -> r.run());
        U.press(f);
        return f;
    }

    int lyricCount() {
        return lyricText.size();
    }

    String lyricLine(int i) {
        return lyricText.get(i);
    }

    String lyricKey() {
        return trackKey == null ? "" : trackKey;
    }

    String songTitle() {
        return title.getText().toString();
    }

    String songArtist() {
        return artist.getText().toString();
    }

    /** Index of the line being sung now (-1 before the first). */
    int currentLyric() {
        long p = MediaListener.position() + 300;
        int idx = -1;
        for (int i = 0; i < lyricTimes.size(); i++) {
            if (lyricTimes.get(i)[0] <= p) idx = i;
            else break;
        }
        return idx;
    }

    private View progress, progressBar, dot;
    private boolean dragging;

    private void showProgress(float f) {
        f = Math.max(0f, Math.min(1f, f));
        progress.setScaleX(f);
        dot.setTranslationX(f * Math.max(0, progressBar.getWidth() - dot.getWidth()));
    }
    private long durationMs;
    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (durationMs <= 0 || MediaListener.controller == null) return;
            if (!dragging) showProgress(MediaListener.position() / (float) durationMs);
            U.ui.postDelayed(this, 500);
        }
    };

    NowPlaying(Context c) {
        this.c = c;
        I = this;
        view = new SwipeBox(c, this::swapArt); // swipe: album art ⇄ the music app's icon
        view.setBackground(Glass.card(U.CARD, 16));
        MapPanel.clip(view, 16);

        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.addView(art, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x33000000, 0x22000000, 0xE6000000}));
        view.addView(shade, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        // lyrics, centred in the upper part of the art
        lyricBox = U.col(c);
        lyricBox.setGravity(Gravity.CENTER);
        U.pad(lyricBox, 12, 10, 12, 0);
        for (int i = 0; i < 3; i++) {
            TextView t = U.text(c, "", i == 1 ? 14 : 12, i == 1 ? U.TEXT : 0x99FFFFFF, i == 1);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(2);
            t.setShadowLayer(U.uf(4), 0, 0, 0xCC000000);
            LinearLayout.LayoutParams lp = U.lp(U.MATCH, U.WRAP);
            lp.topMargin = i == 0 ? 0 : U.u(5);
            lyricBox.addView(t, lp);
            lyricLines[i] = t;
        }
        lyricBox.setVisibility(View.GONE);
        lyricBox.setOnClickListener(v -> {
            lyricsHidden = true;
            lyricBox.setVisibility(View.GONE);
        });
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(U.MATCH, U.u(110), Gravity.TOP);
        view.addView(lyricBox, ll);

        // ♪ Lyrics button: only when this song has lyrics; opens them in the tile area
        lyricsBtn = U.icon(c, R.drawable.ic_lyrics, U.ACCENT_SOFT);
        lyricsBtn.setBackground(U.outline(0xBF0B0E13, U.ACCENT_SOFT, 1.5f, 15));
        U.pad(lyricsBtn, 9, 9, 9, 9);
        lyricsBtn.setContentDescription("Lyrics");
        lyricsBtn.setVisibility(View.GONE);
        lyricsBtn.setOnClickListener(v -> LyricsCard.toggle());
        U.press(lyricsBtn);
        FrameLayout.LayoutParams bl = U.flpu(40, 40, Gravity.TOP | Gravity.END);
        bl.setMargins(0, U.u(10), U.u(10), 0); // same corner spot and size as the map's traffic button
        view.addView(lyricsBtn, bl);
        // video apps: watch it in the tile area, or full screen
        videoBtns = U.row(c);
        // watch in the tiles: the video app comes up and a real Home press sends it into its
        // picture-in-picture window, which keeps clear of the map, dock and Rail Bar (so over the tiles)
        View pipBtn = videoBtn(R.drawable.ic_apps, "Watch video over the tiles", () -> {
            MediaController m = MediaListener.controller;
            if (m == null) return;
            String pkg = m.getPackageName();
            Apps.launchPkg(c, pkg);
            U.ui.postDelayed(NowPlaying::pressHome, 1200);
        });
        pipBtn.setOnLongClickListener(v -> { // hold: that app's App info (Picture-in-picture is switched on there)
            MediaController m = MediaListener.controller;
            if (m != null) Apps.appInfo(c, m.getPackageName());
            return true;
        });
        videoBtns.addView(pipBtn, U.lpu(30, 30));
        LinearLayout.LayoutParams vg = U.lpu(30, 30);
        vg.leftMargin = U.u(6);
        videoBtns.addView(videoBtn(R.drawable.ic_maximize, "Video full screen", () -> {
            MediaController m = MediaListener.controller;
            if (m != null) Apps.launchPkg(c, m.getPackageName());
        }), vg);
        videoBtns.setVisibility(View.GONE);
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.END);
        vl.setMargins(0, U.u(15), U.u(58), 0); // left of the Lyrics button, clear of the app icon
        view.addView(videoBtns, vl);
        // live equaliser where the album art would be, when the art is hidden (app icon view)
        eq = new EqView(c, 18, 0x993D8BFD);
        eq.setVisibility(View.GONE);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(U.MATCH, U.u(70), Gravity.TOP);
        el.setMargins(U.u(14), U.u(54), U.u(14), 0); // centred across the tile
        view.addView(eq, view.indexOfChild(lyricsBtn), el);
        // the animation itself is chosen in Music settings (hold Now playing)


        content = U.col(c);
        U.pad(content, 12, 0, 12, 10);
        content.setGravity(Gravity.BOTTOM);
        title = U.line(c, "", 15, U.TEXT, true);
        content.addView(title);
        artist = U.line(c, "", 12, 0xCCFFFFFF, false);
        LinearLayout.LayoutParams al = U.lp(U.MATCH, U.WRAP);
        al.topMargin = U.u(2);
        content.addView(artist, al);
        // how far into the song: a thin bar with a dot under the artist; drag the dot (or tap the bar) to jump
        FrameLayout bar = new FrameLayout(c);
        View track = new View(c);
        track.setBackground(U.round(0x33FFFFFF, 2));
        bar.addView(track, new FrameLayout.LayoutParams(U.MATCH, U.u(3), Gravity.CENTER_VERTICAL));
        progress = new View(c);
        progress.setBackground(U.round(U.TEXT, 2));
        progress.setPivotX(0);
        progress.setScaleX(0);
        bar.addView(progress, new FrameLayout.LayoutParams(U.MATCH, U.u(3), Gravity.CENTER_VERTICAL));
        dot = new View(c);
        dot.setBackground(U.oval(U.TEXT));
        dot.setElevation(U.uf(2));
        bar.addView(dot, U.flpu(12, 12, Gravity.CENTER_VERTICAL | Gravity.START));
        bar.setOnTouchListener((v, e) -> {
            if (durationMs <= 0) return false;
            float f = Math.max(0f, Math.min(1f, (e.getX() - U.u(6)) / Math.max(1f, v.getWidth() - U.u(12))));
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true); // a drag here isn't a swipe of the tile
                    dragging = true;
                    dot.animate().scaleX(1.4f).scaleY(1.4f).setDuration(U.ms(120)).start();
                    showProgress(f);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    showProgress(f);
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    MediaController m = MediaListener.controller;
                    if (m != null) try {
                        m.getTransportControls().seekTo((long) (f * durationMs));
                    } catch (Exception ignored) {
                    }
                    // fall through
                case android.view.MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    dot.animate().scaleX(1f).scaleY(1f).setDuration(U.ms(120)).start();
                    return true;
            }
            return false;
        });
        LinearLayout.LayoutParams brl = U.lp(U.MATCH, U.u(18)); // taller than it looks, easy to grab
        brl.topMargin = U.u(2);
        content.addView(bar, brl);
        progressBar = bar;
        LinearLayout ctl = U.row(c);
        ctl.setGravity(Gravity.CENTER);
        View prev = U.icon(c, R.drawable.ic_prev, U.TEXT);
        prev.setOnClickListener(v -> transport(0));
        ctl.addView(prev, U.lpu(36, 36));
        FrameLayout play = new FrameLayout(c);
        play.setBackground(U.oval(U.TEXT));
        playBtn = U.icon(c, R.drawable.ic_play, 0xFF000000);
        play.addView(playBtn, U.flpu(26, 26, Gravity.CENTER));
        play.setOnClickListener(v -> transport(1));
        U.press(play);
        LinearLayout.LayoutParams pl = U.lpu(50, 50);
        pl.setMargins(U.u(20), 0, U.u(20), 0);
        ctl.addView(play, pl);
        View next = U.icon(c, R.drawable.ic_next, U.TEXT);
        next.setOnClickListener(v -> transport(2));
        ctl.addView(next, U.lpu(36, 36));
        LinearLayout.LayoutParams cl = U.lp(U.MATCH, U.WRAP);
        cl.topMargin = U.u(2);
        content.addView(ctl, cl);
        view.addView(content, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        empty = U.text(c, "", 13, U.SUB, false);
        empty.setGravity(Gravity.CENTER);
        U.pad(empty, 14);
        view.addView(empty, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        // nothing playing: a folder of music apps instead of the empty message
        folder = new MusicFolder(c);
        folder.view.setVisibility(View.GONE);
        view.addView(folder.view, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        Apps.listen(() -> { if (folder.view.getVisibility() == View.VISIBLE) folder.render(); });

        view.setOnClickListener(v -> {
            if (!MediaListener.hasAccess(c)) MediaListener.requestAccess(c);
            else if (MediaListener.controller != null) Apps.launchPkg(c, MediaListener.controller.getPackageName());
            else openMusicApp();
        });
        view.setOnLongClickListener(v -> { // music settings: app, auto play, playlist, lyrics
            SettingsPanels.music();
            return true;
        });
        MediaListener.listenMedia(this::update);
    }

    static boolean showIcon() {
        return "icon".equals(Store.str("np_show", "art"));
    }

    private void swapArt() {
        if (folder != null && folder.view.getVisibility() == View.VISIBLE) { // the music folder: next page of apps
            folder.page(((SwipeBox) view).dir);
            return;
        }
        Store.put("np_show", showIcon() ? "art" : "icon");
        art.setAlpha(0f);
        update();
        art.animate().alpha(1f).setDuration(U.ms(220)).start();
    }

    private void openMusicApp() {
        if (Music.open(c)) return;
        for (String p : Actions.MUSIC) if (Apps.launchPkg(c, p)) return;
        AppPanels.appPicker("Open music app", a -> Apps.launch(c, a));
    }

    private void transport(int which) {
        MediaController m = MediaListener.controller;
        if (m == null) {
            // nothing loaded yet: press play on the default music app without opening it
            if (which == 1) Music.resume(c);
            else openMusicApp();
            return;
        }
        MediaController.TransportControls t = m.getTransportControls();
        if (which == 0) t.skipToPrevious();
        else if (which == 2) t.skipToNext();
        else if (MediaListener.playing()) {
            Music.userPaused();
            t.pause();
        }
        else t.play();
    }

    void start() {
        running = true;
        MediaListener.attach(c);
        update();
        U.ui.post(tick);
    }

    void stop() {
        running = false;
        U.ui.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showLyric();
            if (running) U.ui.postDelayed(this, 300);
        }
    };

    void update() {
        MediaController m = MediaListener.controller;
        MediaMetadata md = m == null ? null : m.getMetadata();
        if (md == null) {
            art.setImageDrawable(null);
            if (eq != null) eq.setVisibility(View.GONE);
            content.setVisibility(View.INVISIBLE);
            lyricBox.setVisibility(View.GONE);
            boolean access = MediaListener.hasAccess(c);
            empty.setVisibility(access ? View.GONE : View.VISIBLE);
            if (!access) {
                empty.setText("Tap to allow notification access so music shows here");
                empty.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
            if (videoBtns != null) videoBtns.setVisibility(View.GONE);
            if (folder != null) {
                folder.view.setVisibility(access ? View.VISIBLE : View.GONE);
                if (access) folder.render();
            }
            return;
        }
        empty.setVisibility(View.GONE);
        if (folder != null) {
            folder.endEdit(); // music started: edit mode over
            folder.view.setVisibility(View.GONE);
        }
        if (videoBtns != null) videoBtns.setVisibility(isVideo(m.getPackageName()) ? View.VISIBLE : View.GONE);
        content.setVisibility(View.VISIBLE);
        Bitmap bm = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        android.graphics.drawable.Drawable appIcon = Apps.iconOf(m.getPackageName());
        boolean iconView = showIcon() && !(LyricsCard.isShown() && bm != null); // lyrics open: the album art
        if (eq != null) eq.setVisibility(iconView || bm == null ? View.VISIBLE : View.GONE);
        if (iconView && appIcon != null) { // the music app's icon, centred
            art.setImageDrawable(appIcon);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // small, top left, with the live equaliser beside and below it
            art.setPadding(U.u(12), U.u(10), Math.max(0, view.getWidth() - U.u(52)), Math.max(0, view.getHeight() - U.u(50)));
        } else {
            art.setPadding(0, 0, 0, 0);
            if (bm != null) {
                art.setImageBitmap(bm);
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                art.setImageResource(R.drawable.ic_music);
                art.setScaleType(ImageView.ScaleType.CENTER);
            }
        }
        durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        progressBar.setVisibility(durationMs > 0 ? View.VISIBLE : View.INVISIBLE);
        U.ui.removeCallbacks(progressTick);
        if (durationMs > 0) U.ui.post(progressTick);
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        title.setText(t == null ? "" : t);
        artist.setText(a == null ? Apps.label(m.getPackageName()) : a);
        playBtn.setImageResource(MediaListener.playing() ? R.drawable.ic_pause : R.drawable.ic_play);

        String key = t + "|" + a;
        if (!key.equals(trackKey)) {
            trackKey = key;
            lyricsHidden = false;
            lyricTimes.clear();
            if (lyricsBtn != null) lyricsBtn.setVisibility(View.GONE);
            lyricText.clear();
            lyricBox.setVisibility(View.GONE);
            if (t != null && a != null) fetchLyrics(key, t, a, md.getLong(MediaMetadata.METADATA_KEY_DURATION));
        }
    }

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s*(.*)");

    private void fetchLyrics(String key, String track, String artistName, long durMs) {
        String url = "https://lrclib.net/api/get?track_name=" + Uri.encode(track) + "&artist_name=" + Uri.encode(artistName)
                + (durMs > 0 ? "&duration=" + Math.round(durMs / 1000.0) : "");
        java.util.Map<String, String> h = new java.util.HashMap<>();
        h.put("User-Agent", "CarLauncher (github.com/joolay/carlauncher)");
        Net.get(url, h, (body, code) -> {
            if (body == null || !key.equals(trackKey)) return;
            try {
                String synced = new JSONObject(body).optString("syncedLyrics", "");
                if (synced.isEmpty() || "null".equals(synced)) return;
                for (String ln : synced.split("\n")) {
                    Matcher m = LRC.matcher(ln.trim());
                    if (!m.matches()) continue;
                    long ms = Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000;
                    if (m.group(3) != null) {
                        String f = m.group(3);
                        ms += Long.parseLong(f) * (f.length() == 2 ? 10 : f.length() == 1 ? 100 : 1);
                    }
                    lyricTimes.add(new long[]{ms});
                    lyricText.add(m.group(4));
                }
                showLyric();
            } catch (Exception ignored) {
            }
        });
    }

    private int shownIdx = -2;

    private void showLyric() {
        if (lyricsBtn != null) lyricsBtn.setVisibility(lyricTimes.isEmpty() ? View.GONE : View.VISIBLE);
        // no lyrics over the album art any more: the Lyrics button opens them in the tile area
        if (true) {
            if (lyricBox.getVisibility() == View.VISIBLE) lyricBox.setVisibility(View.GONE);
            return;
        }
        long p = MediaListener.position() + 300;
        int idx = -1;
        for (int i = 0; i < lyricTimes.size(); i++) {
            if (lyricTimes.get(i)[0] <= p) idx = i;
            else break;
        }
        lyricBox.setVisibility(View.VISIBLE);
        if (idx == shownIdx) return;
        shownIdx = idx;
        lyricLines[0].setText(idx > 0 ? lyricText.get(idx - 1) : "");
        lyricLines[1].setText(idx >= 0 ? (lyricText.get(idx).isEmpty() ? "♪" : lyricText.get(idx)) : "♪");
        lyricLines[2].setText(idx + 1 < lyricText.size() ? lyricText.get(idx + 1) : "");
        lyricLines[1].setAlpha(0f);
        lyricLines[1].animate().alpha(1f).setDuration(U.ms(250)).start();
    }
}
