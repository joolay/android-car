package com.carbox.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Now playing tile while nothing is playing: a folder of music apps (2×2). Starts with the
 * music apps on the box; apps can be added and removed. With more than 4, a maximise button on the
 * top-right corner opens them all as a grid.
 */
final class MusicFolder {
    private static final String KEY = "music_folder";
    /** Other common music and podcast apps, added at first if they're installed. */
    private static final String[] MORE = {"com.aspiro.tidal", "com.soundcloud.android", "com.audiomack", "com.boomplay.music",
            "com.tencent.ibg.joox", "au.com.shiftyjelly.pocketcasts", "com.bambuna.podcastaddict", "com.maxmpz.audioplayer",
            "org.videolan.vlc", "com.sec.android.app.music", "com.google.android.apps.podcasts"};
    private final Context c;
    final FrameLayout view;
    private final LinearLayout grid;
    private final View max;

    /** The folder's apps (package names), installed ones only. First time: the music apps on the box. */
    static List<String> apps(Context c) {
        List<String> out = new ArrayList<>();
        if (!Store.has(KEY)) {
            for (Apps.App a : Music.players(c)) {
                boolean known = false;
                for (String m : Actions.MUSIC) if (m.equals(a.pkg)) known = true;
                if ((known || !a.system) && !a.pkg.equals(c.getPackageName()) && !out.contains(a.pkg)) out.add(a.pkg);
            }
            for (String p : MORE) if (Apps.installed(p) && !out.contains(p)) out.add(p);
            Store.putList(KEY, out);
            return out;
        }
        for (String p : Store.list(KEY)) if (Apps.installed(p) && !out.contains(p)) out.add(p);
        return out;
    }

    static void add(Context c, String pkg) {
        List<String> l = apps(c);
        if (!l.contains(pkg)) l.add(pkg);
        Store.putList(KEY, l);
        changed();
    }

    static void remove(Context c, String pkg) {
        List<String> l = apps(c);
        l.remove(pkg);
        Store.putList(KEY, l);
        changed();
    }

    private static void changed() {
        if (NowPlaying.I != null) NowPlaying.I.update();
        Sheets.refresh();
    }

    MusicFolder(Context c) {
        this.c = c;
        view = new FrameLayout(c);
        LinearLayout box = U.col(c);
        U.pad(box, 12, 10, 12, 10);
        LinearLayout head = U.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(U.icon(c, R.drawable.ic_folder, U.ACCENT_SOFT), U.lpu(16, 16));
        LinearLayout.LayoutParams hl = U.lp(U.WRAP, U.WRAP);
        hl.leftMargin = U.u(6);
        head.addView(U.line(c, "Music", 13, U.TEXT2, true), hl);
        box.addView(head);
        grid = U.col(c);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(U.MATCH, 0, 1);
        gl.topMargin = U.u(6);
        box.addView(grid, gl);
        view.addView(box, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        // maximise, overlapping the top-right corner (only with more than 4 apps)
        FrameLayout m = new FrameLayout(c);
        m.setBackground(U.oval(U.CARD2));
        m.addView(U.icon(c, R.drawable.ic_maximize, U.TEXT), U.flpu(14, 14, Gravity.CENTER));
        m.setContentDescription("Show all music apps");
        m.setOnClickListener(v -> showAll(c));
        U.press(m);
        FrameLayout.LayoutParams ml = U.flpu(30, 30, Gravity.TOP | Gravity.END);
        ml.setMargins(0, U.u(4), U.u(4), 0);
        view.addView(m, ml);
        max = m;
    }

    void render() {
        grid.removeAllViews();
        List<String> l = apps(c);
        max.setVisibility(l.size() > 4 ? View.VISIBLE : View.GONE);
        List<View> cells = new ArrayList<>();
        for (int i = 0; i < Math.min(4, l.size()); i++) cells.add(cell(c, l.get(i), false));
        if (cells.size() < 4) cells.add(plus(c)); // room left: + adds an app
        for (int r = 0; r < 2; r++) {
            LinearLayout row = U.row(c);
            for (int k = 0; k < 2; k++) {
                int i = r * 2 + k;
                View v = i < cells.size() ? cells.get(i) : new View(c);
                row.addView(v, new LinearLayout.LayoutParams(0, U.MATCH, 1));
            }
            grid.addView(row, new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        }
    }

    /** An app: icon and name; tap opens it, hold (or −) removes it. */
    private static View cell(Context c, String pkg, boolean editable) {
        FrameLayout f = new FrameLayout(c);
        LinearLayout col = U.col(c);
        col.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(Apps.iconOf(pkg));
        col.addView(iv, U.lpu(editable ? 44 : 34, editable ? 44 : 34));
        TextView t = U.line(c, Apps.label(pkg), 10, U.SUB, false);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = U.lp(U.MATCH, U.WRAP);
        tl.topMargin = U.u(3);
        col.addView(t, tl);
        f.addView(col, new FrameLayout.LayoutParams(U.MATCH, U.MATCH, Gravity.CENTER));
        f.setOnClickListener(v -> {
            if (Sheets.open()) Sheets.close();
            Apps.launchPkg(c, pkg);
        });
        f.setOnLongClickListener(v -> { // hold: take it out of the folder
            if (editable) remove(c, pkg);
            else showAll(c);
            return true;
        });
        f.setContentDescription(Apps.label(pkg));
        U.press(f);
        if (editable) {
            FrameLayout minus = new FrameLayout(c);
            minus.setBackground(U.oval(U.RED));
            minus.addView(U.icon(c, R.drawable.ic_minus, U.TEXT), U.flpu(14, 14, Gravity.CENTER));
            minus.setContentDescription("Remove " + Apps.label(pkg));
            minus.setOnClickListener(v -> remove(c, pkg));
            FrameLayout.LayoutParams ml = U.flpu(22, 22, Gravity.TOP | Gravity.END);
            ml.setMargins(0, U.u(2), U.u(10), 0);
            f.addView(minus, ml);
        }
        return f;
    }

    private static View plus(Context c) {
        FrameLayout f = new FrameLayout(c);
        FrameLayout ring = new FrameLayout(c);
        ring.setBackground(U.outline(0, U.LINE, 1.5f, 10));
        ring.addView(U.icon(c, R.drawable.ic_plus, U.SUB), U.flpu(18, 18, Gravity.CENTER));
        f.addView(ring, U.flpu(34, 34, Gravity.CENTER));
        f.setContentDescription("Add a music app");
        f.setOnClickListener(v -> pickApp());
        U.press(f);
        return f;
    }

    private static void pickApp() {
        AppPanels.appPicker("Add to Music", a -> {
            add(Sheets.ctx(), a.pkg);
            if (Sheets.depth() > 1) Sheets.back();
            else Sheets.close();
        });
    }

    /** All the folder's apps as a grid: tap opens, − removes, + adds. */
    static void showAll(Context c0) {
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Tap to open. − takes an app out of the folder, + adds one.", 12, U.SUB, false));
            List<View> cells = new ArrayList<>();
            for (String p : apps(c)) cells.add(cell(c, p, true));
            cells.add(plus(c));
            final int COLS = 5;
            for (int i = 0; i < cells.size(); i += COLS) {
                LinearLayout row = U.row(c);
                for (int k = 0; k < COLS; k++) {
                    View v = i + k < cells.size() ? cells.get(i + k) : new View(c);
                    row.addView(v, new LinearLayout.LayoutParams(0, U.u(82), 1));
                }
                LinearLayout.LayoutParams rl = U.lp(U.MATCH, U.WRAP);
                rl.topMargin = U.u(8);
                b.addView(row, rl);
            }
            b.addView(Sheets.item("Reset", "Back to the music apps on this box", () -> {
                Store.put(KEY, (String) null);
                changed();
            }), U.lp(U.MATCH, U.WRAP));
            return Sheets.panel("Music", b);
        });
    }
}
