package com.carbox.launcher;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * The first-run welcome wizard: a card on the right while the real home screen shows on the left, so every
 * choice is seen at once. Steps: welcome, theme, wallpaper (with a preview of the living sky: night, rain,
 * storm…), Now Playing's side, the News Ticker, then the set-up checklist (permissions, default home).
 * Run again from Settings → Home screen → Welcome wizard.
 */
final class Wizard {
    private static FrameLayout shown;
    private static int step;
    private static final int STEPS = 6;

    private Wizard() {
    }

    static boolean done() {
        return Prefs.flag("wizard_done", false);
    }

    static void show() {
        MainActivity a = MainActivity.I;
        if (a == null || shown != null) return;
        step = 0;
        shown = new FrameLayout(a);
        shown.setClickable(true); // the home behind is a preview, not for tapping yet
        shown.setBackgroundColor(0x33000000);
        a.root.addView(shown, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        render();
    }

    private static void close() {
        Themes.Sky.previewHour = -1;
        Themes.Sky.previewCode = -1;
        Prefs.put("wizard_done", true);
        if (MainActivity.I != null) MainActivity.I.applyWallpaper();
        if (shown != null && shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
        // then what Android needs (notification access, default home…), if anything is missing
        if (MainActivity.I != null && (!MediaListener.hasAccess(MainActivity.I) || !SettingsPanels.isDefaultHome(MainActivity.I)))
            SettingsPanels.setupChecklist();
    }

    private static void go(int s) {
        step = Math.max(0, Math.min(STEPS - 1, s));
        Themes.Sky.previewHour = -1;
        Themes.Sky.previewCode = -1;
        if (MainActivity.I != null) MainActivity.I.applyWallpaper();
        render();
    }

    private static void render() {
        if (shown == null) return;
        Context c = shown.getContext();
        shown.removeAllViews();
        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD, 20));
        Ui.pad(card, 22, 18, 22, 14);
        card.setElevation(Ui.uf(10));
        card.setClickable(true);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Ui.u(400), Ui.MATCH, Gravity.END);
        cl.setMargins(0, Ui.u(14), Ui.u(14), Ui.u(14));
        shown.addView(card, cl);

        LinearLayout body = Ui.col(c);
        switch (step) {
            case 0:
                title(card, "Welcome to Car Launcher", "Let's set it up the way you like. Everything you pick shows on the home screen on the left straight away, and you can change it all later in Settings.");
                android.widget.ImageView logo = new android.widget.ImageView(c);
                logo.setImageResource(R.mipmap.ic_launcher);
                body.addView(logo, Ui.lpu(84, 84));
                break;
            case 1:
                title(card, "Theme", "How the stages, tiles, dock and Rail look.");
                LinearLayout row = Ui.row(c);
                row.addView(themeChoice(c, "modern"), new LinearLayout.LayoutParams(0, Ui.u(150), 1));
                row.addView(Ui.gap(c, 10, 1));
                row.addView(themeChoice(c, "glass"), new LinearLayout.LayoutParams(0, Ui.u(150), 1));
                row.addView(Ui.gap(c, 10, 1));
                row.addView(themeChoice(c, "simple"), new LinearLayout.LayoutParams(0, Ui.u(150), 1));
                body.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
                if (Themes.on()) frosting(c, body);
                break;
            case 2:
                title(card, "Wallpaper", "The Road follows the time of day and the weather where you are, with clouds, rain and stars moving gently. Try it:");
                String w = Prefs.str(Prefs.WALL, "road");
                LinearLayout wr = Ui.row(c);
                wr.addView(seg(c, "Road", "road".equals(w), () -> wall("road")));
                wr.addView(Ui.gap(c, 8, 1));
                wr.addView(seg(c, "Black", "black".equals(w), () -> wall("black")));
                wr.addView(Ui.gap(c, 8, 1));
                wr.addView(seg(c, "Colour…", "color".equals(w), SettingsPanels::colourPicker));
                wr.addView(Ui.gap(c, 8, 1));
                wr.addView(seg(c, "Image…", "image".equals(w), () -> {
                    if (MainActivity.I != null) MainActivity.I.pickWallpaperImage();
                }));
                body.addView(wr);
                if ("road".equals(w)) {
                    body.addView(Ui.text(c, "Preview the sky", 13, Ui.SUB, false), SettingsPanels.top(16));
                    String[][] looks = {{"Now", "-1", "-1"}, {"Morning", "9", "0"}, {"Sunset", "18.2", "0"}, {"Night", "23", "0"},
                            {"Cloudy", "13", "3"}, {"Rain", "15", "63"}, {"Storm", "20", "95"}};
                    LinearLayout g = Ui.col(c);
                    LinearLayout line = null;
                    for (int i = 0; i < looks.length; i++) {
                        if (i % 4 == 0) {
                            line = Ui.row(c);
                            g.addView(line, SettingsPanels.top(i == 0 ? 6 : 8));
                        }
                        final String[] l = looks[i];
                        boolean sel = Themes.Sky.previewHour == Double.parseDouble(l[1]) && Themes.Sky.previewCode == Integer.parseInt(l[2]);
                        line.addView(seg(c, l[0], sel, () -> {
                            Themes.Sky.previewHour = Double.parseDouble(l[1]);
                            Themes.Sky.previewCode = Integer.parseInt(l[2]);
                            if (MainActivity.I != null) MainActivity.I.applyWallpaper();
                            render();
                        }));
                        line.addView(Ui.gap(c, 6, 1));
                    }
                    body.addView(g);
                }
                break;
            case 3:
                title(card, "Now Playing", "Your music always sits on one of the left stages; the other shows Go To and the widgets you pick, and the small map when something opens in the middle.");
                LinearLayout nr = Ui.row(c);
                boolean top = LeftStages.nowPlayingTop();
                nr.addView(seg(c, "Top", top, () -> side(true)));
                nr.addView(Ui.gap(c, 8, 1));
                nr.addView(seg(c, "Bottom", !top, () -> side(false)));
                body.addView(nr);
                break;
            case 4:
                title(card, "News Ticker", "The strip above the middle. Tap its label to go round the ones that are on.");
                body.addView(Panels.switchRow(0, 0, "News", "Local headlines", NewsTicker.newsOn(), on -> NewsTicker.setOn(NewsTicker.NEWS_ON, on)));
                body.addView(Panels.switchRow(0, 0, "Traffic", "Traffic news for your area", NewsTicker.trafficOn(), on -> NewsTicker.setOn(NewsTicker.TRAFFIC_ON, on)), SettingsPanels.top(6));
                body.addView(Panels.switchRow(0, 0, "Lyrics", "The line being sung, while music plays", NewsTicker.lyricsOn(), on -> NewsTicker.setOn(NewsTicker.LYRICS_ON, on)), SettingsPanels.top(6));
                break;
            default:
                title(card, "All set", "Next, Android may ask for a few permissions so music, the map and calls work. Hold anything on the home screen for its settings.");
                body.addView(Ui.icon(c, R.drawable.ic_check, Ui.GREEN), Ui.lpu(60, 60));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(body);
        LinearLayout.LayoutParams sl = Ui.weight(1, false);
        sl.topMargin = Ui.u(14);
        card.addView(sv, sl);

        // dots, then back / skip / next
        LinearLayout dots = Ui.row(c);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < STEPS; i++) {
            View d = new View(c);
            d.setBackground(Ui.oval(i == step ? Ui.ACCENT : Ui.LINE));
            LinearLayout.LayoutParams p = Ui.lpu(7, 7);
            p.setMargins(Ui.u(3), 0, Ui.u(3), 0);
            dots.addView(d, p);
        }
        card.addView(dots, SettingsPanels.top(8));
        LinearLayout nav = Ui.row(c);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        if (step > 0) nav.addView(Ui.iconButton(c, R.drawable.ic_back, "Back", () -> go(step - 1)));
        else nav.addView(Ui.textButton(c, "Skip", false, Wizard::close));
        nav.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        if (step < STEPS - 1) nav.addView(Ui.iconButton(c, R.drawable.ic_fwd, "Next", true, () -> go(step + 1)));
        else nav.addView(Ui.textButton(c, "Start driving", true, Wizard::close));
        card.addView(nav, SettingsPanels.top(10));
    }

    private static void title(LinearLayout card, String t, String sub) {
        Context c = card.getContext();
        card.addView(Ui.line(c, t, 22, Ui.TEXT, true));
        TextView s = Ui.text(c, sub, 13, Ui.SUB, false);
        s.setLineSpacing(Ui.uf(2), 1);
        card.addView(s, SettingsPanels.top(6));
    }

    private static TextView seg(Context c, String label, boolean on, Runnable r) {
        return Ui.seg(c, label, on, () -> {
            r.run();
            render();
        });
    }

    /** A theme to tap: a little sample tile drawn in that theme. */
    private static View themeChoice(Context c, String mode) {
        boolean sel = Themes.mode().equals(mode);
        boolean glass = "glass".equals(mode), simple = "simple".equals(mode);
        LinearLayout l = Ui.col(c);
        l.setGravity(Gravity.CENTER);
        Ui.pad(l, 10, 10, 10, 10);
        l.setBackground(sel ? Ui.outline(0x00000000, Ui.ACCENT, 3, 16) : Ui.outline(0x00000000, Ui.LINE, 1, 16));
        LinearLayout sample = Ui.col(c);
        Ui.pad(sample, 10, 8, 10, 8);
        sample.setBackground(glass ? new GlassSample() : Ui.round(simple ? 0xFF161616 : Ui.CARD, 12));
        sample.addView(Ui.line(c, "SPEED", 10, Ui.SUB, true));
        sample.addView(Ui.line(c, "62 km/h", 18, Ui.TEXT, true), SettingsPanels.top(4));
        l.addView(sample, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        l.addView(Ui.line(c, glass ? "Glass" : simple ? "Simple" : "Modern", 14, sel ? Ui.ACCENT_SOFT : Ui.TEXT, true), SettingsPanels.top(8));
        l.setOnClickListener(v -> {
            Themes.setMode(mode);
            if (MainActivity.I != null) MainActivity.I.recreate(); // the whole home in the new look (the wizard comes back)
            Prefs.put("wizard_resume", 1);
        });
        Ui.press(l);
        return l;
    }

    /** Glass drawn even while Modern is on, so both can be compared. */
    private static final class GlassSample extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(android.graphics.Canvas cv) {
            android.graphics.RectF r = new android.graphics.RectF(getBounds());
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(0x26FFFFFF);
            cv.drawRoundRect(r, Ui.uf(12), Ui.uf(12), p);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            p.setStrokeWidth(Ui.uf(1));
            p.setColor(0x55FFFFFF);
            cv.drawRoundRect(r, Ui.uf(12), Ui.uf(12), p);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    private static void frosting(Context c, LinearLayout body) {
        TextView fl = Ui.text(c, frostLabel(Themes.frost()), 13, Ui.SUB, false);
        body.addView(fl, SettingsPanels.top(16));
        SeekBar fs = new SeekBar(c);
        fs.setMax(100);
        fs.setProgress(Themes.frost());
        fs.getProgressDrawable().setColorFilter(Ui.ACCENT, PorterDuff.Mode.SRC_IN);
        fs.getThumb().setColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN);
        fs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (!user) return;
                fl.setText(frostLabel(v));
                Themes.setFrost(v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        body.addView(fs, SettingsPanels.top(4));
    }

    static String frostLabel(int v) {
        return "Frosting \u00b7 " + v + "%" + (Math.abs(v - Themes.FROST_RECOMMENDED) <= 3 ? "  \u00b7 recommended" : "  (recommended " + Themes.FROST_RECOMMENDED + "%)");
    }

    private static void wall(String mode) {
        Prefs.put(Prefs.WALL, mode);
        if (MainActivity.I != null) MainActivity.I.applyWallpaper();
    }

    private static void side(boolean top) {
        Prefs.put(LeftStages.NP_TOP, top);
        if (MainActivity.I != null) MainActivity.I.placeStages();
    }

    /** After the theme step restarts the home screen, carry on where the wizard was. */
    static void resume() {
        if (Prefs.num("wizard_resume", 0) == 0) return;
        Prefs.put("wizard_resume", 0);
        Ui.ui.postDelayed(() -> {
            show();
            go(1);
        }, 800);
    }
}
