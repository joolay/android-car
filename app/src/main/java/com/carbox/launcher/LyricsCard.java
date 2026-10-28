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
 * dimmer, scrolling by itself with the song. Stays up until closed (no idle timeout), follows
 * the next song, and you can scroll back with a finger.
 */
final class LyricsCard {
    private static FrameLayout shown;
    private static ScrollView sv;
    private static LinearLayout lines;
    private static TextView head;
    private static String forKey;
    private static int cur = -2;
    private static long touchedAt;
    private static final List<TextView> views = new ArrayList<>();

    private LyricsCard() {
    }

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
        shown.setBackground(U.round(U.CARD, 16));
        head = U.line(a, "", 11, U.SUB, true);
        head.setLetterSpacing(0.06f);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(U.MATCH, U.WRAP, Gravity.TOP);
        hl.setMargins(U.u(18), U.u(12), U.u(48), 0);
        shown.addView(head, hl);
        sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(U.u(40));
        sv.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) touchedAt = System.currentTimeMillis(); // let a finger take over for a few seconds
            return false;
        });
        lines = U.col(a);
        lines.setGravity(Gravity.CENTER_HORIZONTAL);
        sv.addView(lines);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(U.MATCH, U.MATCH);
        sl.setMargins(U.u(16), U.u(32), U.u(16), U.u(8));
        shown.addView(sv, sl);
        View x = U.icon(a, R.drawable.ic_x, U.SUB);
        U.pad(x, 8, 8, 8, 8);
        x.setOnClickListener(v -> hide());
        shown.addView(x, U.flpu(40, 40, Gravity.TOP | Gravity.END));
        a.tileArea.addView(shown, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        forKey = null;
        U.ui.post(tick);
    }

    static void hide() {
        U.ui.removeCallbacks(tick);
        if (shown != null && shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
        views.clear();
    }

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (shown == null) return;
            NowPlaying np = NowPlaying.I;
            if (np == null || np.lyricCount() == 0) { // song without lyrics: say so, keep waiting
                if (forKey == null || !forKey.equals("none")) {
                    lines.removeAllViews();
                    views.clear();
                    TextView t = U.text(shown.getContext(), "No lyrics for this song", 16, U.SUB, false);
                    lines.addView(t, Widgets.top(80));
                    forKey = "none";
                }
            } else {
                if (!np.lyricKey().equals(forKey)) build(np);
                follow(np);
            }
            U.ui.postDelayed(this, 250);
        }
    };

    private static void build(NowPlaying np) {
        forKey = np.lyricKey();
        head.setText(("LYRICS · " + np.songTitle() + " · " + np.songArtist()).toUpperCase(java.util.Locale.ROOT));
        lines.removeAllViews();
        views.clear();
        lines.addView(new View(shown.getContext()), U.lpu(1, 90)); // room so the first line can sit in the middle
        for (int i = 0; i < np.lyricCount(); i++) {
            String s = np.lyricLine(i);
            TextView t = U.text(shown.getContext(), s.isEmpty() ? "♪" : s, 15, U.MUTED, false);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = U.lp(U.MATCH, U.WRAP);
            lp.topMargin = U.u(8);
            lines.addView(t, lp);
            views.add(t);
        }
        lines.addView(new View(shown.getContext()), U.lpu(1, 120));
        cur = -2;
    }

    private static void follow(NowPlaying np) {
        int idx = np.currentLyric();
        if (idx == cur) return;
        cur = idx;
        for (int i = 0; i < views.size(); i++) {
            TextView t = views.get(i);
            int d = Math.abs(i - idx);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, U.uf(d == 0 ? 22 : d == 1 ? 16 : 14));
            t.setTextColor(d == 0 ? U.TEXT : d == 1 ? U.SUB : U.MUTED);
            t.setTypeface(null, d == 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (idx < 0 || idx >= views.size() || System.currentTimeMillis() - touchedAt < 4000) return;
        TextView t = views.get(idx);
        t.post(() -> sv.smoothScrollTo(0, Math.max(0, t.getTop() - sv.getHeight() / 2 + t.getHeight() / 2)));
    }
}
