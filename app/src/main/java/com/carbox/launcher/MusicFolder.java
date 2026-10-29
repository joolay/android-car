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

    /** Move an app to another place in the folder. */
    static void move(Context c, int from, int to) {
        List<String> l = apps(c);
        if (from < 0 || from >= l.size() || to < 0 || to >= l.size()) return;
        l.add(to, l.remove(from));
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

    static MusicFolder I;
    private static boolean maximised; // the full grid is open: no maximise button on the tile

    MusicFolder(Context c) {
        this.c = c;
        I = this;
        view = new FrameLayout(c);
        LinearLayout box = U.col(c);
        U.pad(box, 8, 8, 8, 8);
        // no title: the icons fill the tile
        grid = U.col(c);
        box.addView(grid, new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        dots = U.row(c); // which page (swipe the tile for the next 4 apps)
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = U.lp(U.MATCH, U.u(10));
        dl.topMargin = U.u(2);
        box.addView(dots, dl);
        view.addView(box, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        // maximise, overlapping the top-right corner (only with more than 4 apps)
        FrameLayout m = new FrameLayout(c);
        m.setBackground(U.oval(U.CARD2));
        m.addView(U.icon(c, R.drawable.ic_maximize, U.TEXT), U.flpu(20, 20, Gravity.CENTER));
        m.setContentDescription("Show all music apps");
        m.setOnClickListener(v -> showAll(c));
        U.press(m);
        FrameLayout.LayoutParams ml = U.flpu(40, 40, Gravity.TOP | Gravity.END);
        ml.setMargins(0, U.u(4), U.u(4), 0);
        view.addView(m, ml);
        max = m;
    }

    private final LinearLayout dots;
    private int page;

    /** Pages of 4: the apps, then + (on the last page, or a page of its own when that one is full). */
    private int pages(int n) {
        return n / 4 + 1;
    }

    /** Swipe the tile: the next (dir 1) or previous (dir -1) page of apps slides in. */
    void page(int dir) {
        int n = pages(apps(c).size());
        if (n < 2) return;
        page = ((page + dir) % n + n) % n;
        float w = grid.getWidth();
        if (U.ms(1) == 0 || w == 0) {
            render();
            return;
        }
        grid.animate().translationX(-dir * w).alpha(0f).setDuration(U.ms(150)).withEndAction(() -> {
            render();
            grid.setTranslationX(dir * w);
            grid.animate().translationX(0).alpha(1f).setDuration(U.ms(200))
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        }).start();
    }

    void render() {
        grid.removeAllViews();
        List<String> l = apps(c);
        max.setVisibility(l.size() > 4 && !maximised ? View.VISIBLE : View.GONE);
        int n = pages(l.size());
        if (page >= n) page = n - 1;
        List<View> cells = new ArrayList<>();
        for (int i = page * 4; i < Math.min(page * 4 + 4, l.size()); i++) cells.add(cell(c, l.get(i), false));
        // hold and drag an app onto another's place to move it (a hold without moving opens the full grid)
        final int base = page * 4, onPage = cells.size();
        for (int i = 0; i < onPage; i++)
            Reorder.attach(cells.get(i), i, cells.subList(0, onPage), (from, to) -> move(c, base + from, base + to), () -> showAll(c));
        if (cells.size() < 4 && page == n - 1) cells.add(plus(c)); // room left on the last page: + adds an app
        dots.removeAllViews();
        if (n > 1) for (int i = 0; i < n; i++) {
            View d = new View(c);
            d.setBackground(U.oval(i == page ? U.ACCENT : U.LINE));
            LinearLayout.LayoutParams p = U.lpu(6, 6);
            p.setMargins(U.u(3), 0, U.u(3), 0);
            dots.addView(d, p);
        }
        dots.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
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
        if (editable) { // big grid: icon and name
            col.addView(iv, U.lpu(44, 44));
            TextView t = U.line(c, Apps.label(pkg), 10, U.SUB, false);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = U.lp(U.MATCH, U.WRAP);
            tl.topMargin = U.u(3);
            col.addView(t, tl);
        } else { // in the tile: just the icon, as big as the spot allows
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            U.pad(iv, 6, 6, 6, 6);
            col.addView(iv, U.lp(U.MATCH, U.MATCH));
        }
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
        ring.addView(U.icon(c, R.drawable.ic_plus, U.SUB), U.flpu(24, 24, Gravity.CENTER));
        f.addView(ring, U.flpu(48, 48, Gravity.CENTER));
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
        maximised = true;
        if (I != null) I.max.setVisibility(View.GONE);
        U.ui.post(() -> Sheets.whenClosed(() -> { // back on the tile when the grid closes
            maximised = false;
            if (I != null) I.render();
        }));
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Tap to open. − takes an app out of the folder, + adds one.", 12, U.SUB, false));
            List<View> cells = new ArrayList<>();
            List<String> all = apps(c);
            for (String p : all) cells.add(cell(c, p, true));
            // hold and drag to move an app; a hold without moving takes it out (as the − does)
            for (int i = 0; i < all.size(); i++) {
                final String p = all.get(i);
                Reorder.attach(cells.get(i), i, cells.subList(0, all.size()), (from, to) -> move(c, from, to), () -> remove(c, p));
            }
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
