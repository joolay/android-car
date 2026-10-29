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

    NowPlaying(Context c) {
        this.c = c;
        I = this;
        view = new SwipeBox(c, this::swapArt); // swipe: album art ⇄ the music app's icon
        view.setBackground(U.round(U.CARD, 16));
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
        U.pad(lyricsBtn, 7, 7, 7, 7);
        lyricsBtn.setContentDescription("Lyrics");
        lyricsBtn.setVisibility(View.GONE);
        lyricsBtn.setOnClickListener(v -> LyricsCard.toggle());
        U.press(lyricsBtn);
        FrameLayout.LayoutParams bl = U.flpu(30, 30, Gravity.TOP | Gravity.END);
        bl.setMargins(0, U.u(8), U.u(8), 0);
        view.addView(lyricsBtn, bl);
        // live equaliser where the album art would be, when the art is hidden (app icon view)
        eq = new EqView(c, 18, 0x993D8BFD);
        eq.setVisibility(View.GONE);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(U.MATCH, U.u(70), Gravity.TOP);
        el.setMargins(U.u(14), U.u(54), U.u(14), 0);
        view.addView(eq, view.indexOfChild(lyricsBtn), el);


        content = U.col(c);
        U.pad(content, 12, 0, 12, 10);
        content.setGravity(Gravity.BOTTOM);
        title = U.line(c, "", 15, U.TEXT, true);
        content.addView(title);
        artist = U.line(c, "", 12, 0xCCFFFFFF, false);
        LinearLayout.LayoutParams al = U.lp(U.MATCH, U.WRAP);
        al.topMargin = U.u(2);
        content.addView(artist, al);
        LinearLayout ctl = U.row(c);
        ctl.setGravity(Gravity.CENTER);
        View prev = U.icon(c, R.drawable.ic_prev, U.TEXT);
        prev.setOnClickListener(v -> transport(0));
        ctl.addView(prev, U.lpu(30, 30));
        FrameLayout play = new FrameLayout(c);
        play.setBackground(U.oval(U.TEXT));
        playBtn = U.icon(c, R.drawable.ic_play, 0xFF000000);
        play.addView(playBtn, U.flpu(22, 22, Gravity.CENTER));
        play.setOnClickListener(v -> transport(1));
        U.press(play);
        LinearLayout.LayoutParams pl = U.lpu(42, 42);
        pl.setMargins(U.u(20), 0, U.u(20), 0);
        ctl.addView(play, pl);
        View next = U.icon(c, R.drawable.ic_next, U.TEXT);
        next.setOnClickListener(v -> transport(2));
        ctl.addView(next, U.lpu(30, 30));
        LinearLayout.LayoutParams cl = U.lp(U.MATCH, U.WRAP);
        cl.topMargin = U.u(8);
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
            if (folder != null) {
                folder.view.setVisibility(access ? View.VISIBLE : View.GONE);
                if (access) folder.render();
            }
            return;
        }
        empty.setVisibility(View.GONE);
        if (folder != null) folder.view.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        Bitmap bm = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        android.graphics.drawable.Drawable appIcon = Apps.iconOf(m.getPackageName());
        if (eq != null) eq.setVisibility(showIcon() || bm == null ? View.VISIBLE : View.GONE);
        if (showIcon() && appIcon != null) { // the music app's icon, centred
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
