package com.carbox.launcher;

import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Synced lyrics filling the 2×4 tile area: the current line large and bright, its neighbours
 * dimmer, scrolling by itself with the song. Stays up until closed or the song ends (no idle
 * timeout), and you can scroll back with a finger.
 */
final class LyricsView {
    private static FrameLayout shown;
    private static ScrollView sv;
    private static LinearLayout lines;
    private static TextView head;
    private static String forKey;
    private static int cur = -2;
    private static long touchedAt;
    private static final List<TextView> views = new ArrayList<>();

    private LyricsView() {
    }

    private static long changedAt; // when the song changed and its lyrics weren't there yet

    private static android.widget.TextView barTitle; // the song's name up in the ticker's place

    static boolean isShown() {
        return shown != null;
    }

    static void toggle() {
        if (shown != null) hide();
        else show();
    }

    static void show() {
        MainActivity a = MainActivity.I;
        NowPlaying np = NowPlaying.I;
        if (a == null || np == null || np.lyricCount() == 0) return;
        shown = new FrameLayout(a);
        shown.setClickable(true);
        shown.setBackground(Themes.card(Ui.CARD, 16));
        a.mapAside(true); // the big map steps aside for the lyrics
        head = Ui.line(a, "", 11, Ui.SUB, true);
        head.setLetterSpacing(0.06f);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.TOP);
        hl.setMargins(Ui.u(18), Ui.u(12), Ui.u(48), 0);
        shown.addView(head, hl);
        sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(Ui.u(40));
        sv.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) touchedAt = System.currentTimeMillis(); // let a finger take over for a few seconds
            return false;
        });
        lines = Ui.col(a);
        lines.setGravity(Gravity.CENTER_HORIZONTAL);
        sv.addView(lines);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH);
        sl.setMargins(Ui.u(16), Ui.u(32), Ui.u(16), Ui.u(8));
        shown.addView(sv, sl);
        View x = Ui.icon(a, R.drawable.ic_x, Ui.SUB);
        Ui.pad(x, 8, 8, 8, 8);
        x.setOnClickListener(v -> hide());
        // title (song) and close go up into the News Ticker's place; the words get the whole stage
        LinearLayout bar = Ui.row(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.TextView bt = Ui.line(a, "", 14, Ui.TEXT, true);
        bar.addView(bt, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        View settings = Ui.icon(a, R.drawable.ic_gear, Ui.SUB); // Now Playing's settings
        Ui.pad(settings, 5, 5, 5, 5);
        settings.setContentDescription("Music settings");
        settings.setOnClickListener(v -> SettingsPanels.music());
        bar.addView(settings, Ui.lpu(30, 30));
        bar.addView(Ui.gap(a, 8, 1));
        bar.addView(x, Ui.lpu(30, 30));
        barTitle = bt;
        if (a.titleToTicker(bar)) {
            head.setVisibility(View.GONE);
            sl.setMargins(Ui.u(16), Ui.u(8), Ui.u(16), Ui.u(8));
            sv.requestLayout();
        } else {
            ((android.view.ViewGroup) bar).removeView(x);
            shown.addView(x, Ui.flpu(46, 46, Gravity.TOP | Gravity.END));
            barTitle = null;
        }
        a.tileArea.addView(shown, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        // grows out of the Now playing tile
        final FrameLayout s0 = shown;
        s0.setAlpha(0f);
        s0.post(() -> {
            float[] t = fromNowPlaying(s0);
            if (t == null) {
                s0.setAlpha(1f);
                return;
            }
            s0.setPivotX(0);
            s0.setPivotY(0);
            s0.setTranslationX(t[0]);
            s0.setTranslationY(t[1]);
            s0.setScaleX(t[2]);
            s0.setScaleY(t[3]);
            s0.setAlpha(0.6f);
            s0.animate().translationX(0).translationY(0).scaleX(1).scaleY(1).alpha(1f).setDuration(Ui.ms(340))
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        });
        forKey = null;
        changedAt = 0;
        Ui.ui.post(tick);
        np.update(); // Now playing shows the album art while the lyrics are open
    }

    static void hide() {
        Ui.ui.removeCallbacks(tick);
        final FrameLayout s0 = shown;
        shown = null;
        views.clear();
        if (MainActivity.I != null) {
            MainActivity.I.mapAside(false);
            if (barTitle != null) MainActivity.I.tickerTitle(null); // the News Ticker comes back
        }
        barTitle = null;
        if (NowPlaying.I != null) NowPlaying.I.update();
        if (s0 == null || s0.getParent() == null) return;
        float[] t = fromNowPlaying(s0);
        Runnable gone = () -> {
            if (s0.getParent() != null) ((android.view.ViewGroup) s0.getParent()).removeView(s0);
        };
        if (t == null) {
            gone.run();
            return;
        }
        // shrinks back into the Now playing tile
        s0.setPivotX(0);
        s0.setPivotY(0);
        s0.animate().translationX(t[0]).translationY(t[1]).scaleX(t[2]).scaleY(t[3]).alpha(0.2f).setDuration(Ui.ms(280))
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f)).withEndAction(gone).start();
    }

    /** Move and scale that put the lyrics window exactly over the Now playing tile: {dx, dy, sx, sy}. */
    private static float[] fromNowPlaying(View v) {
        MainActivity a = MainActivity.I;
        if (a == null || a.nowPlaying == null || v.getWidth() == 0 || a.nowPlaying.view.getWidth() == 0) return null;
        int[] np = new int[2], ta = new int[2];
        a.nowPlaying.view.getLocationOnScreen(np);
        a.tileArea.getLocationOnScreen(ta);
        return new float[]{np[0] - ta[0], np[1] - ta[1], a.nowPlaying.view.getWidth() / (float) v.getWidth(), a.nowPlaying.view.getHeight() / (float) v.getHeight()};
    }

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (shown == null) return;
            NowPlaying np = NowPlaying.I;
            // another song: stay open if it has lyrics too (they may take a few seconds to load), else minimise
            if (forKey != null && !"none".equals(forKey) && (np == null || !np.lyricKey().equals(forKey))) {
                if (np != null && np.lyricCount() > 0) changedAt = 0; // the new song's lyrics: rebuilt below
                else {
                    long now = System.currentTimeMillis();
                    if (changedAt == 0) changedAt = now;
                    if (np == null || now - changedAt > 8000) {
                        hide();
                        return;
                    }
                    Ui.ui.postDelayed(this, 250);
                    return;
                }
            }
            if (np == null || np.lyricCount() == 0) { // song without lyrics: say so, keep waiting
                if (forKey == null || !forKey.equals("none")) {
                    lines.removeAllViews();
                    views.clear();
                    TextView t = Ui.text(shown.getContext(), "No lyrics for this song", 16, Ui.SUB, false);
                    lines.addView(t, Widgets.top(80));
                    forKey = "none";
                }
            } else {
                if (!np.lyricKey().equals(forKey)) build(np);
                follow(np);
            }
            Ui.ui.postDelayed(this, 250);
        }
    };

    private static void build(NowPlaying np) {
        forKey = np.lyricKey();
        head.setText(("LYRICS · " + np.songTitle() + " · " + np.songArtist()).toUpperCase(java.util.Locale.ROOT));
        if (barTitle != null) barTitle.setText(np.songTitle() + " · " + np.songArtist());
        lines.removeAllViews();
        views.clear();
        lines.addView(new View(shown.getContext()), Ui.lpu(1, 90)); // room so the first line can sit in the middle
        for (int i = 0; i < np.lyricCount(); i++) {
            String s = np.lyricLine(i);
            TextView t = Ui.text(shown.getContext(), s.isEmpty() ? "♪" : s, 15, Ui.MUTED, false);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
            lp.topMargin = Ui.u(8);
            lines.addView(t, lp);
            views.add(t);
        }
        lines.addView(new View(shown.getContext()), Ui.lpu(1, 120));
        cur = -2;
    }

    private static void follow(NowPlaying np) {
        int idx = np.currentLyric();
        if (idx == cur) return;
        cur = idx;
        for (int i = 0; i < views.size(); i++) {
            TextView t = views.get(i);
            int d = Math.abs(i - idx);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(d == 0 ? 22 : d == 1 ? 16 : 14));
            t.setTextColor(d == 0 ? Ui.TEXT : d == 1 ? Ui.SUB : Ui.MUTED);
            t.setTypeface(null, d == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (idx < 0 || idx >= views.size() || System.currentTimeMillis() - touchedAt < 4000) return;
        TextView t = views.get(idx);
        t.post(() -> sv.smoothScrollTo(0, Math.max(0, t.getTop() - sv.getHeight() / 2 + t.getHeight() / 2)));
    }
}
