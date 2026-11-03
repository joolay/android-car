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
        f.setBackground(Ui.outline(0xBF0B0E13, Ui.ACCENT_SOFT, 1.5f, 15)); // same as the Lyrics button
        f.addView(Ui.icon(c, icon, Ui.ACCENT_SOFT), Ui.flpu(22, 22, Gravity.CENTER));
        f.setContentDescription(desc);
        f.setOnClickListener(v -> r.run());
        Ui.press(f);
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

    private ImageView appBadge;
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
            Ui.ui.postDelayed(this, 500);
        }
    };

    NowPlaying(Context c) {
        this.c = c;
        I = this;
        view = new SwipeBox(c, this::swapArt); // swipe: album art ⇄ the music app's icon
        view.setBackground(Themes.card(Ui.CARD, 16));
        Maps.clip(view, 16);

        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.addView(art, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x33000000, 0x22000000, 0xE6000000}));
        view.addView(shade, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // lyrics, centred in the upper part of the art
        lyricBox = Ui.col(c);
        lyricBox.setGravity(Gravity.CENTER);
        Ui.pad(lyricBox, 12, 10, 12, 0);
        for (int i = 0; i < 3; i++) {
            TextView t = Ui.text(c, "", i == 1 ? 14 : 12, i == 1 ? Ui.TEXT : 0x99FFFFFF, i == 1);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(2);
            t.setShadowLayer(Ui.uf(4), 0, 0, 0xCC000000);
            LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
            lp.topMargin = i == 0 ? 0 : Ui.u(5);
            lyricBox.addView(t, lp);
            lyricLines[i] = t;
        }
        lyricBox.setVisibility(View.GONE);
        lyricBox.setOnClickListener(v -> {
            lyricsHidden = true;
            lyricBox.setVisibility(View.GONE);
        });
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(Ui.MATCH, Ui.u(110), Gravity.TOP);
        view.addView(lyricBox, ll);

        // which app is playing: its icon on the album art's top-left corner
        appBadge = new ImageView(c);
        appBadge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        appBadge.setElevation(Ui.uf(3));
        appBadge.setVisibility(View.GONE);
        appBadge.setBackground(Ui.outline(0xBF0B0E13, Ui.ACCENT_SOFT, 1.5f, 15)); // same size and frame as the Lyrics button
        Ui.pad(appBadge, 6, 6, 6, 6);
        FrameLayout.LayoutParams abl = Ui.flpu(40, 40, Gravity.TOP | Gravity.START);
        abl.setMargins(Ui.u(10), Ui.u(10), 0, 0);
        view.addView(appBadge, abl);
        // ♪ Lyrics button: only when this song has lyrics; opens them in the tile area
        lyricsBtn = Ui.icon(c, R.drawable.ic_lyrics, Ui.ACCENT_SOFT);
        lyricsBtn.setBackground(Ui.outline(0xBF0B0E13, Ui.ACCENT_SOFT, 1.5f, 15));
        Ui.pad(lyricsBtn, 9, 9, 9, 9);
        lyricsBtn.setContentDescription("Lyrics");
        lyricsBtn.setVisibility(View.GONE);
        lyricsBtn.setOnClickListener(v -> LyricsView.toggle());
        Ui.press(lyricsBtn);
        FrameLayout.LayoutParams bl = Ui.flpu(40, 40, Gravity.TOP | Gravity.END);
        bl.setMargins(0, Ui.u(10), Ui.u(10), 0); // same corner spot and size as the map's traffic button
        view.addView(lyricsBtn, bl);
        // video apps: watch it in the tile area, or full screen
        videoBtns = Ui.row(c);
        // watch in the tiles: the video app comes up and a real Home press sends it into its
        // picture-in-picture window, which keeps clear of the map, dock and Rail Bar (so over the tiles)
        View pipBtn = videoBtn(R.drawable.ic_pip, "Picture-in-picture", () -> {
            MediaController m = MediaListener.controller;
            if (m == null) return;
            String pkg = m.getPackageName();
            Apps.launchPkg(c, pkg);
            Ui.ui.postDelayed(NowPlaying::pressHome, 1200);
        });
        pipBtn.setOnLongClickListener(v -> { // hold: that app's App info (Picture-in-picture is switched on there)
            MediaController m = MediaListener.controller;
            if (m != null) Apps.appInfo(c, m.getPackageName());
            return true;
        });
        videoBtns.addView(pipBtn, Ui.lpu(40, 40));
        LinearLayout.LayoutParams vg = Ui.lpu(40, 40);
        vg.leftMargin = Ui.u(8);
        videoBtns.addView(videoBtn(R.drawable.ic_maximize, "Video full screen", () -> {
            MediaController m = MediaListener.controller;
            if (m != null) Apps.launchPkg(c, m.getPackageName());
        }), vg);
        videoBtns.setVisibility(View.GONE);
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.END);
        vl.setMargins(0, Ui.u(10), Ui.u(10), 0); // full screen in the top-right corner, picture-in-picture beside it
        view.addView(videoBtns, vl);
        // live equaliser where the album art would be, when the art is hidden (app icon view)
        eq = new EqView(c, 18, 0x993D8BFD);
        eq.setVisibility(View.GONE);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(Ui.MATCH, Ui.u(70), Gravity.TOP);
        el.setMargins(Ui.u(14), Ui.u(54), Ui.u(14), 0); // centred across the tile
        view.addView(eq, view.indexOfChild(lyricsBtn), el);
        // the animation itself is chosen in Music settings (hold Now playing)


        content = Ui.col(c);
        Ui.pad(content, 12, 0, 12, 10);
        content.setGravity(Gravity.BOTTOM);
        title = Ui.line(c, "", 15, Ui.TEXT, true);
        content.addView(title);
        artist = Ui.line(c, "", 12, 0xCCFFFFFF, false);
        LinearLayout.LayoutParams al = Ui.lp(Ui.MATCH, Ui.WRAP);
        al.topMargin = Ui.u(2);
        content.addView(artist, al);
        // how far into the song: a thin bar with a dot under the artist; drag the dot (or tap the bar) to jump
        FrameLayout bar = new FrameLayout(c);
        View track = new View(c);
        track.setBackground(Ui.round(0x33FFFFFF, 2));
        bar.addView(track, new FrameLayout.LayoutParams(Ui.MATCH, Ui.u(3), Gravity.CENTER_VERTICAL));
        progress = new View(c);
        progress.setBackground(Ui.round(Ui.TEXT, 2));
        progress.setPivotX(0);
        progress.setScaleX(0);
        bar.addView(progress, new FrameLayout.LayoutParams(Ui.MATCH, Ui.u(3), Gravity.CENTER_VERTICAL));
        dot = new View(c);
        dot.setBackground(Ui.oval(Ui.TEXT));
        dot.setElevation(Ui.uf(2));
        bar.addView(dot, Ui.flpu(12, 12, Gravity.CENTER_VERTICAL | Gravity.START));
        bar.setOnTouchListener((v, e) -> {
            if (durationMs <= 0) return false;
            float f = Math.max(0f, Math.min(1f, (e.getX() - Ui.u(6)) / Math.max(1f, v.getWidth() - Ui.u(12))));
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.getParent().requestDisallowInterceptTouchEvent(true); // a drag here isn't a swipe of the tile
                    dragging = true;
                    dot.animate().scaleX(1.4f).scaleY(1.4f).setDuration(Ui.ms(120)).start();
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
                    dot.animate().scaleX(1f).scaleY(1f).setDuration(Ui.ms(120)).start();
                    return true;
            }
            return false;
        });
        LinearLayout.LayoutParams brl = Ui.lp(Ui.MATCH, Ui.u(18)); // taller than it looks, easy to grab
        brl.topMargin = Ui.u(2);
        content.addView(bar, brl);
        progressBar = bar;
        LinearLayout ctl = Ui.row(c);
        ctl.setGravity(Gravity.CENTER);
        View prev = Ui.icon(c, R.drawable.ic_prev, Ui.TEXT);
        prev.setOnClickListener(v -> transport(0));
        ctl.addView(prev, Ui.lpu(36, 36));
        FrameLayout play = new FrameLayout(c);
        play.setBackground(Ui.oval(Ui.TEXT));
        playBtn = Ui.icon(c, R.drawable.ic_play, 0xFF000000);
        play.addView(playBtn, Ui.flpu(26, 26, Gravity.CENTER));
        play.setOnClickListener(v -> transport(1));
        Ui.press(play);
        LinearLayout.LayoutParams pl = Ui.lpu(50, 50);
        pl.setMargins(Ui.u(20), 0, Ui.u(20), 0);
        ctl.addView(play, pl);
        View next = Ui.icon(c, R.drawable.ic_next, Ui.TEXT);
        next.setOnClickListener(v -> transport(2));
        ctl.addView(next, Ui.lpu(36, 36));
        LinearLayout.LayoutParams cl = Ui.lp(Ui.MATCH, Ui.WRAP);
        cl.topMargin = Ui.u(2);
        content.addView(ctl, cl);
        view.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        empty = Ui.text(c, "", 13, Ui.SUB, false);
        empty.setGravity(Gravity.CENTER);
        Ui.pad(empty, 14);
        view.addView(empty, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        // nothing playing: a folder of music apps instead of the empty message
        folder = new MusicFolder(c);
        folder.view.setVisibility(View.GONE);
        view.addView(folder.view, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
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
        return "icon".equals(Prefs.str("np_show", "art"));
    }

    // a song loaded: swipe 1 album art ⇄ animation, swipe 2 the music folder, swipe 3 its second page
    // (only if apps are there), then back to the song
    private boolean folderOver, swappedOnce;

    /** The folder brought up over a song goes away (an app was opened from it). */
    void closeFolder() {
        if (!folderOver) return;
        folderOver = false;
        swappedOnce = false;
        update();
    }

    private void swapArt() {
        if (folder != null && folderOver) {
            if (folder.hasNext()) folder.next();
            else {
                folderOver = false;
                swappedOnce = false;
                folder.endEdit();
                update();
            }
            return;
        }
        if (folder != null && folder.view.getVisibility() == View.VISIBLE) { // nothing playing: the music folder's pages
            folder.page(((SwipeBox) view).dir);
            return;
        }
        if (swappedOnce && folder != null) { // second swipe: the folder over the song
            folderOver = true;
            folder.first();
            update();
            return;
        }
        swappedOnce = true;
        Prefs.put("np_show", showIcon() ? "art" : "icon");
        art.setAlpha(0f);
        update();
        art.animate().alpha(1f).setDuration(Ui.ms(220)).start();
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
        Ui.ui.post(tick);
    }

    void stop() {
        running = false;
        Ui.ui.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showLyric();
            if (running) Ui.ui.postDelayed(this, 300);
        }
    };

    void update() {
        MediaController m = MediaListener.controller;
        MediaMetadata md = m == null ? null : m.getMetadata();
        if (md == null) {
            art.setImageDrawable(null);
            if (eq != null) eq.setVisibility(View.GONE);
            if (appBadge != null) appBadge.setVisibility(View.GONE);
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
            if (folderOver) folder.view.setVisibility(View.VISIBLE); // swiped to the folder over the song
            else {
                folder.endEdit(); // music started: edit mode over
                folder.view.setVisibility(View.GONE);
            }
        }
        if (videoBtns != null) videoBtns.setVisibility(isVideo(m.getPackageName()) ? View.VISIBLE : View.GONE);
        content.setVisibility(View.VISIBLE);
        Bitmap bm = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        android.graphics.drawable.Drawable appIcon = Apps.iconOf(m.getPackageName());
        boolean iconView = showIcon() && !(LyricsView.isShown() && bm != null); // lyrics open: the album art
        if (eq != null) eq.setVisibility(iconView || bm == null ? View.VISIBLE : View.GONE);
        appBadge.setImageDrawable(appIcon);
        appBadge.setVisibility(!(iconView && appIcon != null) && appIcon != null ? View.VISIBLE : View.GONE); // album art: app icon on its corner
        if (iconView && appIcon != null) { // the music app's icon, centred
            art.setImageDrawable(appIcon);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // small, top left, with the live equaliser beside and below it
            art.setPadding(Ui.u(12), Ui.u(10), Math.max(0, view.getWidth() - Ui.u(52)), Math.max(0, view.getHeight() - Ui.u(50)));
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
        MainActivity.reInvert(); // smart invert: album art and the app icon keep their true colours
        durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        progressBar.setVisibility(durationMs > 0 ? View.VISIBLE : View.INVISIBLE);
        Ui.ui.removeCallbacks(progressTick);
        if (durationMs > 0) Ui.ui.post(progressTick);
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
            if (t != null && a != null) {
                final String ft = t, fa = a;
                final long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (SpotifyLyrics.active()) { // Spotify's own lyrics first (signed in under Music settings), else the free source
                    SpotifyLyrics.fetch(c, SpotifyLyrics.trackId(md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)), t, a, (times, words) -> {
                        if (!key.equals(trackKey)) return;
                        if (words.isEmpty()) fetchLyrics(key, ft, fa, dur);
                        else {
                            lyricTimes.addAll(times);
                            lyricText.addAll(words);
                            showLyric();
                        }
                    });
                } else fetchLyrics(key, t, a, dur);
            }
        }
    }

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s*(.*)");

    // Free lyrics, tried in turn until one has them (after Spotify's own, when signed in):
    // 1. LRCLIB, exact song (timed)  2. LRCLIB search with a cleaned-up title (timed)
    // 3. LRCLIB's untimed words  4. lyrics.ovh (untimed). Untimed words are spread over the song's length.
    private void fetchLyrics(String key, String track, String artistName, long durMs) {
        java.util.Map<String, String> h = new java.util.HashMap<>();
        h.put("User-Agent", "CarLauncher (github.com/joolay/carlauncher)");
        String url = "https://lrclib.net/api/get?track_name=" + Uri.encode(track) + "&artist_name=" + Uri.encode(artistName)
                + (durMs > 0 ? "&duration=" + Math.round(durMs / 1000.0) : "");
        Net.get(url, h, (body, code) -> Ui.ui.post(() -> {
            if (!key.equals(trackKey)) return;
            String[] got = lrclib(body);
            if (got[0] != null && timed(key, got[0], "LRCLIB")) return;
            // 2: search, with "(feat. …)", " - Remastered" and the like taken off, first artist only
            String t2 = cleanTitle(track), a2 = artistName.split(",|&| feat| ft\\.| x ")[0].trim();
            Net.get("https://lrclib.net/api/search?track_name=" + Uri.encode(t2) + "&artist_name=" + Uri.encode(a2), h, (sb, sc) -> Ui.ui.post(() -> {
                if (!key.equals(trackKey)) return;
                String plain = got[1];
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(sb);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        double d = o.optDouble("duration", 0);
                        if (durMs > 0 && d > 0 && Math.abs(d - durMs / 1000.0) > 8) continue; // another version of the song
                        String syn = o.optString("syncedLyrics", "");
                        if (!syn.isEmpty() && !"null".equals(syn) && timed(key, syn, "LRCLIB search")) return;
                        String pl = o.optString("plainLyrics", "");
                        if (plain == null && !pl.isEmpty() && !"null".equals(pl)) plain = pl;
                    }
                } catch (Exception ignored) {
                }
                if (plain != null) {
                    untimed(key, plain, durMs, "LRCLIB (untimed)");
                    return;
                }
                // 4: lyrics.ovh, words only
                Net.get("https://api.lyrics.ovh/v1/" + Uri.encode(a2) + "/" + Uri.encode(t2), h, (ob, oc) -> Ui.ui.post(() -> {
                    if (!key.equals(trackKey)) return;
                    try {
                        String l = new JSONObject(ob).optString("lyrics", "");
                        if (!l.trim().isEmpty()) untimed(key, l, durMs, "lyrics.ovh (untimed)");
                        else Trace.event("Lyrics: none found for " + track);
                    } catch (Exception e) {
                        Trace.event("Lyrics: none found for " + track);
                    }
                }));
            }));
        }));
    }

    /** {synced, plain} from an LRCLIB answer (either may be null). */
    private static String[] lrclib(String body) {
        String[] out = new String[2];
        try {
            JSONObject o = new JSONObject(body);
            String s1 = o.optString("syncedLyrics", ""), s2 = o.optString("plainLyrics", "");
            if (!s1.isEmpty() && !"null".equals(s1)) out[0] = s1;
            if (!s2.isEmpty() && !"null".equals(s2)) out[1] = s2;
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String cleanTitle(String t) {
        return t.replaceAll("(?i)\\s*[\\(\\[](feat|ft|with|from|remaster|live|radio|edit|version|prod)[^\\)\\]]*[\\)\\]]", "")
                .replaceAll("(?i)\\s+-\\s+(remaster|live|radio|edit|single|mono|stereo|feat|from).*$", "").trim();
    }

    /** Timed (LRC) lyrics in: true when there were lines. */
    private boolean timed(String key, String synced, String from) {
        List<long[]> t = new ArrayList<>();
        List<String> w = new ArrayList<>();
        for (String ln : synced.split("\n")) {
            Matcher m = LRC.matcher(ln.trim());
            if (!m.matches()) continue;
            long ms = Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000;
            if (m.group(3) != null) {
                String f = m.group(3);
                ms += Long.parseLong(f) * (f.length() == 2 ? 10 : f.length() == 1 ? 100 : 1);
            }
            t.add(new long[]{ms});
            w.add(m.group(4));
        }
        if (w.isEmpty() || !key.equals(trackKey)) return false;
        lyricTimes.clear();
        lyricText.clear();
        lyricTimes.addAll(t);
        lyricText.addAll(w);
        Trace.event("Lyrics: " + w.size() + " lines from " + from);
        showLyric();
        return true;
    }

    /** Words without timing: spread evenly over the song (after a short intro), so they still move along. */
    private void untimed(String key, String plain, long durMs, String from) {
        if (!key.equals(trackKey)) return;
        List<String> w = new ArrayList<>();
        for (String ln : plain.split("\n")) if (!ln.trim().isEmpty()) w.add(ln.trim());
        if (w.isEmpty()) return;
        long dur = durMs > 0 ? durMs : 210000, start = dur / 12, span = dur - start - dur / 15;
        lyricTimes.clear();
        lyricText.clear();
        for (int i = 0; i < w.size(); i++) {
            lyricTimes.add(new long[]{start + span * i / w.size()});
            lyricText.add(w.get(i));
        }
        Trace.event("Lyrics: " + w.size() + " lines from " + from);
        showLyric();
    }

    private int shownIdx = -2;

    private void showLyric() {
        boolean video = MediaListener.controller != null && isVideo(MediaListener.controller.getPackageName());
        if (lyricsBtn != null) lyricsBtn.setVisibility(lyricTimes.isEmpty() || video ? View.GONE : View.VISIBLE); // a video's buttons have the corner
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
        lyricLines[1].animate().alpha(1f).setDuration(Ui.ms(250)).start();
    }
}
