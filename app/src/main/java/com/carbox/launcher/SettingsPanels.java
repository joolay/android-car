package com.carbox.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Everything behind the gear button. */
final class SettingsPanels {
    private SettingsPanels() {
    }

    static LinearLayout.LayoutParams top(float m) {
        return Widgets.top(m);
    }

    static Context c() {
        return Panels.ctx();
    }

    /** A small "are you sure?" page. */
    static void confirm(String title, String text, String yes, Runnable go) {
        Panels.push(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, text, 14, Ui.TEXT2, false));
            LinearLayout btns = Ui.row(c);
            btns.addView(Ui.button(c, yes, true, () -> {
                Panels.close();
                go.run();
            }));
            btns.addView(Ui.gap(c, 10, 1));
            btns.addView(Ui.button(c, "Cancel", false, Panels::back));
            b.addView(btns, top(16));
            return Panels.panel(title, b, 420, 220);
        });
    }

    static void open(Panels.Page p) {
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }

    // ------------------------------------------------------------------ main

    static void main() {
        Panels.showFull(SettingsPanels::mainPage); // full screen; its pages open on top of it
    }

    private static int tab; // the settings category showing (kept while Settings is reopened)

    /** A settings row for a tab: coloured icon, name with its current value underneath, and a switch or ›. */
    private static View bigRow(Context c, int icon, int color, String title, String value, Runnable open, String key, boolean def) {
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Themes.inner(Ui.CARD2, 12));
        Ui.pad(r, 10, 6, 10, 6);
        r.setMinimumHeight(Ui.u(58));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.oval(color));
        ic.addView(Ui.icon(c, icon, Ui.TEXT), Ui.flpu(17, 17, Gravity.CENTER));
        r.addView(ic, Ui.lpu(32, 32));
        LinearLayout tx = Ui.col(c);
        tx.addView(Ui.line(c, title, 15, Ui.TEXT, false));
        if (value != null && !value.isEmpty()) tx.addView(Ui.line(c, value, 12, Ui.SUB, false), top(2));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.setMargins(Ui.u(10), 0, Ui.u(6), 0);
        r.addView(tx, tl);
        if (key != null) r.addView(new Ui.Toggle(c, Prefs.flag(key, def), on -> {
            Prefs.put(key, on);
            if (Prefs.SPLASH_ON.equals(key) || Alerts.GLOBAL.equals(key)) Panels.refresh();
        }));
        else r.addView(Ui.text(c, "›", 17, Ui.MUTED, false));
        if (open != null) r.setOnClickListener(v -> open.run());
        Ui.press(r);
        return r;
    }

    /** A compact settings row: name, current value, and either a switch (on/off settings) or ›. */
    private static View textRow(Context c, String title, String value, Runnable open, String key, boolean def) {
        LinearLayout r = Ui.row(c);
        r.setBackground(Themes.inner(Ui.CARD2, 10));
        Ui.pad(r, 10, 0, 8, 0);
        r.setMinimumHeight(Ui.u(38));
        r.addView(Ui.line(c, title, 13, Ui.TEXT, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        if (value != null && !value.isEmpty()) {
            TextView v = Ui.line(c, value, 11, Ui.SUB, false);
            v.setMaxWidth(Ui.u(80));
            LinearLayout.LayoutParams vl = Ui.lp(Ui.WRAP, Ui.WRAP);
            vl.rightMargin = Ui.u(6);
            r.addView(v, vl);
        }
        if (key != null) r.addView(new Ui.Toggle(c, Prefs.flag(key, def), on -> {
            Prefs.put(key, on);
            if (Prefs.SPLASH_ON.equals(key) || Alerts.GLOBAL.equals(key)) Panels.refresh();
        }));
        else r.addView(Ui.text(c, "›", 15, Ui.MUTED, false));
        if (open != null) r.setOnClickListener(v -> open.run()); // tap the name for the full page
        Ui.press(r);
        return r;
    }

    /** One setting: icon, name and current value, all the same size. */
    private static View tile(Context c, int icon, int color, String title, String value, Runnable click) {
        LinearLayout r = Ui.row(c);
        r.setBackground(Themes.inner(Ui.CARD2, 14));
        Ui.pad(r, 8, 5, 6, 5);
        r.setMinimumHeight(Ui.u(40)); // compact
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.oval(color));
        ic.addView(Ui.icon(c, icon, Ui.TEXT), Ui.flpu(12, 12, Gravity.CENTER));
        r.addView(ic, Ui.lpu(22, 22));
        LinearLayout t = Ui.col(c);
        t.addView(Ui.line(c, title, 12, Ui.TEXT, true));
        if (value != null && !value.isEmpty()) t.addView(Ui.line(c, value, 10, Ui.SUB, false), top(0));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(6);
        r.addView(t, tl);
        r.addView(Ui.text(c, "›", 13, Ui.MUTED, false));
        r.setOnClickListener(v -> click.run());
        Ui.press(r);
        return r;
    }

    private static View mainPage() {
        Context c = c();
        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD, 18));
        Ui.pad(card, 20, 14, 20, 14);
        card.setLayoutParams(Panels.centered(784, 464));
        LinearLayout head = Ui.row(c);
        head.addView(Ui.line(c, "Settings", 20, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        View x = Ui.icon(c, R.drawable.ic_x, Ui.SUB);
        x.setOnClickListener(v -> Panels.close());
        head.addView(x, Ui.lpu(30, 30));
        card.addView(head);

        int railOn = 0;
        for (String[] e : Rail.order()) if ("1".equals(e[1])) railOn++;
        String w = Prefs.str(Prefs.WALL, "road");
        String as = Prefs.str(Prefs.ASSIST, "gemini"), ext = Prefs.str(Prefs.ASSIST_PKG, null);
        final int BLUE = 0xFF1F4E9A, GREEN = 0xFF1F7A46, AMBER = 0xFF8A5A10, PURPLE = 0xFF5A3A9A, GREY = Ui.CARD3;
        Object[][][] groups = {
                {{"Home screen"},
                        {R.drawable.ic_apps, PURPLE, "Left Stages", "Now Playing " + (LeftStages.nowPlayingTop() ? "top" : "bottom") + " · " + LeftStages.list().size() + " widgets", (Runnable) () -> LeftStages.chooser(false)},
                        {R.drawable.ic_check, PURPLE, "Welcome wizard", "Theme, wallpaper, Now Playing, ticker", (Runnable) () -> { Panels.close(); Wizard.show(); }},
                        {R.drawable.ic_photo, PURPLE, "Wallpaper", "image".equals(w) ? "Image" : "color".equals(w) ? "Colour" : "Black", (Runnable) SettingsPanels::wallpaper},
                        {R.drawable.ic_drag, PURPLE, "Rail Bar", railOn + " of " + Rail.IDS.length + " shown", (Runnable) SettingsPanels::railOrder},
                        {R.drawable.ic_apps, PURPLE, "Dock", Dock.icons() + " icons" + (Dock.dialler() ? " · dialler" : ""), (Runnable) SettingsPanels::dock},
                        {R.drawable.ic_hide, PURPLE, "Hidden from All apps", Apps.hidden().isEmpty() ? "None" : Apps.hidden().size() + " hidden", (Runnable) () -> hiddenApps(false)},
                        {R.drawable.ic_hide, PURPLE, "Hidden from Dock", Apps.dockHidden().isEmpty() ? "None" : Apps.dockHidden().size() + " hidden", (Runnable) () -> hiddenApps(true)},
                        {R.drawable.ic_sun, PURPLE, "Splash screen", null, (Runnable) SettingsPanels::splash, Prefs.SPLASH_ON, false},
                        {R.drawable.ic_bolt, PURPLE, "Animations", null, null, "anim_on", true}},
                {{"Driving"},
                        {R.drawable.ic_car, GREEN, "Driving timer", Ui.dur(Rest.limitMin() * 60000L), (Runnable) Rest::setup, Rest.ON, true},
                        {R.drawable.ic_pin, GREEN, "Car location", "Home Assistant", (Runnable) CarHA::settings, CarHA.ON, false},
                        {R.drawable.ic_bell, PURPLE, "Alerts expand", null, (Runnable) Alerts::settings, Alerts.GLOBAL, false}},
                {{"News Ticker"},
                        {R.drawable.ic_bell, GREEN, "Ticker options", tickerLabel(), (Runnable) SettingsPanels::news},
                        {R.drawable.ic_bell, GREEN, "Show the ticker", Prefs.flag("ticker_on", true) ? "On · tap to hide" : "Off · tap to show", (Runnable) () -> {
                            Prefs.put("ticker_on", !Prefs.flag("ticker_on", true));
                            Panels.close();
                            if (MainActivity.I != null) MainActivity.I.recreate();
                        }}},
                {{"Start-up"},
                        {R.drawable.ic_bolt, AMBER, "Auto start", Actions.autostart().length() + " apps", (Runnable) AppPanels::autostart},
                        {R.drawable.ic_wifi, AMBER, "Connections at start", connLabel(), (Runnable) SettingsPanels::connections}},
                {{"Phone and voice"},
                        {R.drawable.ic_phone, BLUE, "Calls", Widgets.callsLabel(), (Runnable) SettingsPanels::calls},
                        {R.drawable.ic_mic, BLUE, "Assistant", "external".equals(as) && ext != null ? Apps.label(ext) : "Gemini", (Runnable) SettingsPanels::assistant},
                        {R.drawable.ic_apps, BLUE, "Keyboard", "Car keyboard" + (CarIME.isDefault(c) ? " · on" : ""), (Runnable) SettingsPanels::keyboard}},
                {{"Time, calendar and faith"},
                        {R.drawable.ic_calendar, GREY, "Units and clock", (Prefs.flag(Prefs.CELSIUS, true) ? "°C" : "°F") + " · " + (Prefs.flag(Prefs.H24, true) ? "24 h" : "12 h"), (Runnable) SettingsPanels::units},
                        {R.drawable.ic_calendar, GREY, "Calendar", Cal.altLabel(), (Runnable) SettingsPanels::calendar},
                        {R.drawable.ic_moon, GREY, "Faith add-ons", Faith.label(), (Runnable) Faith::picker},
                        Faith.islam() ? new Object[]{R.drawable.ic_kaaba, GREY, "Prayer times", Prayer.METHODS[Prefs.num(Prefs.PRAY_METHOD, 0)], (Runnable) SettingsPanels::prayerTimes} : null},
                {{"System"},
                        {R.drawable.ic_check, GREY, "Set-up checklist", null, (Runnable) SettingsPanels::setupChecklist},
                        {R.drawable.ic_bolt, GREY, "Startup optimiser", null, (Runnable) Optimiser::show},
                        {R.drawable.ic_call, GREY, "Call helper", BackButtonService.enabled(c) ? "On" : "Off", (Runnable) SettingsPanels::floatingBack},
                        {R.drawable.ic_gear, GREY, "Android settings", null, (Runnable) SettingsPanels::androidSettings},
                        {R.drawable.ic_bolt, GREY, "Restart Car Launcher", "A fresh start of the app", (Runnable) () -> confirm("Restart Car Launcher?", "It closes and opens again in a moment.", "Restart", () -> {
                            if (MainActivity.I != null) MainActivity.I.restartApp();
                        })},
                        {R.drawable.ic_bolt, GREY, "Restart the box", "Opens the power menu", (Runnable) () -> {
                            Panels.close();
                            if (MainActivity.I != null) MainActivity.I.restartBox();
                        }},
                        {R.drawable.ic_refresh, GREY, "Check for updates", About.version(c) + " now", (Runnable) () -> Updater.check(c, true)},
                        {R.drawable.ic_bolt, GREY, "Updates", Updater.key().isEmpty() ? "Needs your GitHub key" : Prefs.flag(Updater.AUTO, true) ? "Automatic" : "By hand", (Runnable) Updater::settings},
                        {R.drawable.ic_bolt, GREY, "Logs", Trace.on() ? "Last 3 days kept · tap to send" : "Off", (Runnable) Trace::settings},
                        {R.drawable.ic_shield, GREY, "About", About.version(c), (Runnable) About::show}}};

        // categories as tabs down the left; the chosen one's settings fill the rest, two big rows across
        LinearLayout body = Ui.row(c);
        body.setGravity(Gravity.TOP);
        LinearLayout tabs = Ui.col(c);
        if (tab >= groups.length) tab = 0;
        for (int gi = 0; gi < groups.length; gi++) {
            final int g = gi;
            Object[] first = null;
            for (int k = 1; k < groups[gi].length && first == null; k++) first = groups[gi][k];
            LinearLayout t = Ui.row(c);
            t.setGravity(Gravity.CENTER_VERTICAL);
            Ui.pad(t, 10, 0, 8, 0);
            t.setMinimumHeight(Ui.u(44));
            boolean on = gi == tab;
            t.setBackground(on ? Themes.inner(Ui.CARD2, 12) : null);
            if (first != null) {
                FrameLayout ic = new FrameLayout(c);
                ic.setBackground(Ui.oval((Integer) first[1]));
                ic.addView(Ui.icon(c, (Integer) first[0], Ui.TEXT), Ui.flpu(15, 15, Gravity.CENTER));
                t.addView(ic, Ui.lpu(26, 26));
            }
            TextView tn = Ui.line(c, (String) groups[gi][0][0], 13, on ? Ui.TEXT : Ui.SUB, on);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            tl.leftMargin = Ui.u(10);
            t.addView(tn, tl);
            if (on) {
                View bar = new View(c);
                bar.setBackground(Ui.round(Ui.ACCENT, 2));
                t.addView(bar, Ui.lpu(3, 20));
            }
            t.setOnClickListener(v -> {
                tab = g;
                Panels.refresh();
            });
            Ui.press(t);
            tabs.addView(t, top(gi == 0 ? 0 : 4));
        }
        android.widget.ScrollView ts = new android.widget.ScrollView(c);
        ts.setVerticalScrollBarEnabled(false);
        ts.addView(tabs);
        body.addView(ts, Ui.lpu(196, Ui.MATCH));
        View div = new View(c);
        div.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams dl = Ui.lpu(1, Ui.MATCH);
        dl.setMargins(Ui.u(12), 0, Ui.u(14), 0);
        body.addView(div, dl);

        LinearLayout list = Ui.col(c);
        Object[][] g = groups[tab];
        TextView h = Ui.line(c, (String) g[0][0], 17, Ui.TEXT, true);
        list.addView(h);
        List<View> cells = new ArrayList<>();
        for (int k = 1; k < g.length; k++) {
            Object[] it = g[k];
            if (it == null) continue;
            cells.add(bigRow(c, (Integer) it[0], (Integer) it[1], (String) it[2], (String) it[3], (Runnable) it[4],
                    it.length > 5 ? (String) it[5] : null, it.length > 6 && (Boolean) it[6]));
        }
        list.addView(AppPanels.grid(c, cells, 2, 8), top(10));
        TextView hint = Ui.text(c, "More settings: hold the map for map and navigation, hold Now playing for music. Hold a tile for its own settings.", 12, Ui.SUB, false);
        list.addView(hint, top(16));
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        body.addView(sv, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        LinearLayout.LayoutParams bl = Ui.weight(1, false);
        bl.topMargin = Ui.u(12);
        card.addView(body, bl);
        return card;
    }

    /** Assistant for the voice button. */
    static void assistant() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
        // assistant
        String as = Prefs.str(Prefs.ASSIST, "gemini");
        String ext = Prefs.str(Prefs.ASSIST_PKG, null);
        b.addView(Panels.row("Assistant",
                Ui.seg(c, "Gemini", "gemini".equals(as), () -> {
                    Prefs.put(Prefs.ASSIST, "gemini");
                    Panels.refresh();
                }),
                Ui.seg(c, "external".equals(as) && ext != null ? "External · " + Apps.label(ext) + " ›" : "External ›", "external".equals(as), SettingsPanels::assistantPicker)));

            b.addView(Ui.text(c, "Tip: hold the voice button on the Rail Bar to pick any installed assistant for one go.", 12, Ui.SUB, false), top(10));
            String al = Prefs.str("assist_log", "");
            b.addView(Panels.section("Last time the voice button was pressed"));
            b.addView(Ui.text(c, al.isEmpty() ? "Not pressed yet." : al, 11, Ui.SUB, false));
            return Panels.panel("Assistant", b, 560, 340);
        });
    }

    /** Wallpaper: black, a colour or an image. */
    static void wallpaper() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
        // wallpaper
        String w = Prefs.str(Prefs.WALL, "road");
        View swatch = new View(c);
        swatch.setBackground(Ui.oval(Prefs.num(Prefs.WALL_COLOR, 0xFF1A2433)));
        LinearLayout colour = Ui.row(c);
        colour.setBackground(Ui.round("color".equals(w) ? Ui.ACCENT : Ui.CARD2, 16));
        Ui.pad(colour, 10, 6, 14, 6);
        colour.addView(swatch, Ui.lpu(18, 18));
        colour.addView(Ui.gap(c, 8, 1));
        colour.addView(Ui.text(c, "Colour", 14, Ui.TEXT, false));
        colour.setOnClickListener(v -> colourPicker());
        b.addView(Panels.row("Wallpaper",
                Ui.seg(c, "Black", "black".equals(w), () -> {
                    Prefs.put(Prefs.WALL, "black");
                    if (MainActivity.I != null) MainActivity.I.applyWallpaper();
                    Panels.refresh();
                }), colour,
                Ui.seg(c, "Road", "road".equals(w), () -> {
                    Prefs.put(Prefs.WALL, "road");
                    if (MainActivity.I != null) MainActivity.I.applyWallpaper();
                    Panels.refresh();
                }),
                Ui.seg(c, "image".equals(w) ? "Image ✓" : "Image…", "image".equals(w), () -> {
                    if (MainActivity.I != null) MainActivity.I.pickWallpaperImage();
                })));
        b.addView(Ui.hr(c));
        boolean glass = Themes.on();
        LinearLayout themes = Ui.row(c);
        if (Addons.unlocked("theme_modern")) themes.addView(Ui.seg(c, "Modern", !glass, () -> theme(false)));
        if (Addons.unlocked("theme_glass")) { themes.addView(Ui.gap(c, 8, 1)); themes.addView(Ui.seg(c, "Glass", glass, () -> theme(true))); }
        b.addView(Panels.row("Theme", themes));
        b.addView(Ui.text(c, "Glass: the dock, Rail Bar, Now playing, left widgets and tiles see-through, like iOS (best with a wallpaper)", 12, Ui.SUB, false), top(4));
        if (glass) { // how frosted the glass is: live as you slide
            TextView fl = Ui.text(c, "Frosting · " + Themes.frost() + "%", 13, Ui.SUB, false);
            b.addView(fl, top(14));
            SeekBar fs = new SeekBar(c);
            fs.setMax(100);
            fs.setProgress(Themes.frost());
            fs.getProgressDrawable().setColorFilter(Ui.ACCENT, PorterDuff.Mode.SRC_IN);
            fs.getThumb().setColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN);
            fs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int v, boolean user) {
                    if (!user) return;
                    fl.setText("Frosting · " + v + "%");
                    Themes.setFrost(v);
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar sb) {
                }
            });
            b.addView(fs, top(4));
            b.addView(Ui.text(c, "Low: clear glass, the wallpaper shows through. High: frosted, text easier to read.", 12, Ui.SUB, false), top(4));
        }

            return Panels.panel("Wallpaper", b, 600, 420);
        });
    }

    private static LinearLayout.LayoutParams margin() {
        LinearLayout.LayoutParams lp = Ui.weight(1, true);
        lp.leftMargin = Ui.u(10);
        return lp;
    }

    private static boolean bluetoothOn() {
        try {
            android.bluetooth.BluetoothManager m = (android.bluetooth.BluetoothManager) c().getSystemService(Context.BLUETOOTH_SERVICE);
            return m != null && m.getAdapter() != null && m.getAdapter().isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private static View quick(Context c, int icon, String label, boolean on, int onColor, Runnable click) {
        LinearLayout l = Ui.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(Ui.round(on ? onColor : Ui.CARD2, 14));
        l.addView(Ui.icon(c, icon, Ui.TEXT), Ui.lpu(22, 22));
        TextView t = Ui.line(c, label, 12, Ui.TEXT, on);
        l.addView(t, top(5));
        l.setOnClickListener(v -> click.run());
        Ui.press(l);
        return l;
    }

    static String tickerLabel() {
        java.util.List<String> on = new java.util.ArrayList<>();
        if (NewsTicker.newsOn()) on.add("News");
        if (NewsTicker.trafficOn()) on.add("Traffic");
        if (NewsTicker.lyricsOn()) on.add("Lyrics");
        return on.isEmpty() ? "Nothing chosen" : android.text.TextUtils.join(" · ", on);
    }

    static String connLabel() {
        int n = 0;
        if (Prefs.flag(Prefs.CONN_PHONE, false)) n++;
        if (Prefs.flag(Prefs.CONN_HOTSPOT, false)) n++;
        if (Prefs.flag(Prefs.CONN_TS, false)) n++;
        return n == 0 ? "Off" : n + " on";
    }

    static void about() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            String v = "";
            try {
                v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            b.addView(Ui.text(c, "Car Launcher " + v, 16, Ui.TEXT, true));
            b.addView(Ui.text(c, "Built for 800×480 car boxes. Map © OpenStreetMap contributors © CARTO. Weather by Open-Meteo. Lyrics by LRCLIB. Headlines from Google News.", 13, Ui.SUB, false), top(8));
            b.addView(Ui.text(c, "Make this your Home app: Android settings → Apps → Default apps → Home app.", 13, Ui.TEXT2, false), top(12));
            b.addView(Ui.button(c, "Default apps", false, () -> Status.open(c, Settings.ACTION_HOME_SETTINGS)), top(12));
            return Panels.panel("About", b, 520, 320);
        });
    }

    // ------------------------------------------------------------------ assistant

    static void assistantPicker() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "The mic button opens this app. Claude and ChatGPT open their app; tap their voice button to talk.", 12, Ui.SUB, false));
            boolean any = false;
            String cur = Prefs.str(Prefs.ASSIST_PKG, null);
            for (String[] a : Actions.ASSISTANTS) {
                if (Actions.GEMINI.equals(a[0])) continue;
                Apps.App app = Apps.byPkg(a[0]);
                if (app == null) continue;
                any = true;
                b.addView(appRow(c, app, a[0].equals(cur), () -> {
                    Prefs.put(Prefs.ASSIST, "external");
                    Prefs.put(Prefs.ASSIST_PKG, a[0]);
                    Panels.back();
                    Panels.refresh();
                }));
            }
            if (!any) b.addView(Ui.text(c, "None of Claude, ChatGPT, Copilot, Alexa, Perplexity or Meta AI are installed.", 13, Ui.TEXT2, false), top(10));
            b.addView(Panels.item("Other app…", null, () -> AppPanels.appPicker("Assistant app", app -> {
                Prefs.put(Prefs.ASSIST, "external");
                Prefs.put(Prefs.ASSIST_PKG, app.pkg);
                Panels.close();
            })), top(4));
            return Panels.panel("External assistant", b, 520, 400);
        });
    }

    static View appRow(Context c, Apps.App a, boolean selected, Runnable click) {
        LinearLayout r = Ui.row(c);
        r.setMinimumHeight(Ui.u(50));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(a.icon);
        r.addView(iv, Ui.lpu(32, 32));
        TextView t = Ui.line(c, a.label, 15, Ui.TEXT, selected);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(12);
        r.addView(t, tl);
        if (selected) r.addView(Ui.icon(c, R.drawable.ic_check, Ui.ACCENT), Ui.lpu(20, 20));
        r.setOnClickListener(v -> click.run());
        return r;
    }

    // ------------------------------------------------------------------ navigation app

    /** Map and navigation settings in one place (hold the map to open it). */
    static void navApp() {
        mapNav();
    }

    static void map() {
        mapNav();
    }

    static void mapNav() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.section("Navigation app"));
            navRows(c, b);
            b.addView(Panels.section("Map"));
            mapRows(c, b);
            NavBanner.rows(c, b);
            return Panels.panel("Map and navigation", b, 660, 440);
        });
    }

    private static void navRows(Context c, LinearLayout b) {
        {
            String cur = Actions.navPkg();
            for (String p : Actions.NAV_APPS) {
                Apps.App a = Apps.byPkg(p);
                if (a == null) continue;
                b.addView(appRow(c, a, p.equals(cur), () -> {
                    Prefs.put(Prefs.NAV_PKG, p);
                    Panels.refresh();
                }));
            }
            b.addView(Panels.item("Other app…", null, () -> AppPanels.appPicker("Navigation app", a -> {
                Prefs.put(Prefs.NAV_PKG, a.pkg);
                Panels.back();
                Panels.refresh();
            })));
            b.addView(Ui.text(c, "Turn-by-turn on the map tile works with Google Maps and Waze.", 12, Ui.SUB, false), top(8));
        }
    }

    // ------------------------------------------------------------------ music

    static void music() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            LinearLayout app = Ui.row(c);
            Ui.pad(app, 0, 10, 0, 10);
            LinearLayout t = Ui.col(c);
            t.addView(Ui.text(c, "Default music app", 15, Ui.TEXT, false));
            t.addView(Ui.text(c, "Used by auto play, the Now playing card and its play button", 12, Ui.SUB, false), top(3));
            app.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            String p = Music.pkg();
            if (p != null && Apps.iconOf(p) != null) {
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(Apps.iconOf(p));
                app.addView(iv, Ui.lpu(28, 28));
                app.addView(Ui.gap(c, 8, 1));
            }
            app.addView(Ui.text(c, Music.label() + " ›", 14, Ui.ACCENT_SOFT, true));
            app.setOnClickListener(v -> musicAppPicker());
            b.addView(app);
            b.addView(Panels.item("Now Playing's stage", LeftStages.nowPlayingTop() ? "Top-Left · change it" : "Bottom-Left · change it", () -> LeftStages.chooser(false)));
            if (SpotifyLyrics.ENABLED()) b.addView(Panels.item("Spotify lyrics", SpotifyLyrics.signedIn() ? "Signed in · Spotify's own lyrics first" : "Sign in for Spotify's own lyrics", SpotifyLyrics::settings));
            String st = EqView.style(); // the animation on Now playing (and where the art would be)
            b.addView(Panels.row("Animation", Ui.seg(c, "Equaliser", "bars".equals(st), () -> eqStyle("bars")),
                    Ui.seg(c, "Wave", "wave".equals(st), () -> eqStyle("wave")), Ui.seg(c, "Pulse", "pulse".equals(st), () -> eqStyle("pulse"))), top(8));
            b.addView(Ui.hr(c));
            b.addView(Panels.switchRow(0, 0, "Play music when the car starts", "Starts straight away, before the splash screen",
                    Prefs.flag(Prefs.MUSIC_AUTOPLAY, false), on -> Prefs.put(Prefs.MUSIC_AUTOPLAY, on)));
            b.addView(Ui.hr(c));
            String sw = Music.startWith();
            b.addView(Panels.row("At start, play", Ui.seg(c, "My playlist", "playlist".equals(sw), () -> {
                Prefs.put("music_start", "playlist");
                Panels.refresh();
            }), Ui.seg(c, "Whatever played last", "last".equals(sw), () -> {
                Prefs.put("music_start", "last");
                Panels.refresh();
            })), top(8));
            b.addView(Ui.text(c, "My playlist", 15, Ui.TEXT, false), top(12));
            b.addView(Ui.text(c, "\"Liked Songs\", a playlist name, or a playlist link shared from the app. Also used if nothing is loaded.", 12, Ui.SUB, false), top(3));
            LinearLayout pr = Ui.row(c);
            final String[] pl = {Prefs.str(Prefs.MUSIC_PLAYLIST, "")};
            pr.addView(Widgets.field("e.g. Liked Songs", pl[0], false, v -> {
                pl[0] = v;
                Prefs.put(Prefs.MUSIC_PLAYLIST, v);
            }), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            pr.addView(Ui.gap(c, 10, 1));
            pr.addView(Ui.button(c, "Test", false, () -> {
                Panels.close();
                Music.playPlaylist(c, true);
            }));
            b.addView(pr, top(10));

            java.util.List<String> ml = Prefs.list("music_log");
            b.addView(Panels.section("Last car start")); // always shown, so it's easy to find
            b.addView(Ui.text(c, ml.isEmpty() ? "Nothing logged yet: it fills in at the next car start." : String.join("\n", ml), 11, Ui.SUB, false));
            String eq = EqView.style();
            b.addView(Panels.row("Music animation", Ui.seg(c, "Equaliser", "bars".equals(eq), () -> { Prefs.put("eq_style", "bars"); Panels.refresh(); }),
                    Ui.seg(c, "Wave", "wave".equals(eq), () -> { Prefs.put("eq_style", "wave"); Panels.refresh(); }),
                    Ui.seg(c, "Pulse", "pulse".equals(eq), () -> { Prefs.put("eq_style", "pulse"); Panels.refresh(); })), top(12));
            return Panels.panel("Music", b, 640, 432);
        });
    }

    static void musicAppPicker() {
        Panels.push(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Music apps on this box are listed first", 12, Ui.SUB, false));
            String cur = Prefs.str(Prefs.MUSIC_PKG, null), now = Music.pkg();
            for (Apps.App a : Music.players(c)) {
                b.addView(appRow(c, a, !Music.LAST.equals(cur) && a.pkg.equals(now), () -> {
                    Prefs.put(Prefs.MUSIC_PKG, a.pkg);
                    Panels.back();
                    Panels.refresh();
                }));
            }
            b.addView(radio(c, "Last app that played", "Whatever played last time", Music.LAST.equals(cur), () -> {
                Prefs.put(Prefs.MUSIC_PKG, Music.LAST);
                Panels.back();
                Panels.refresh();
            }));
            b.addView(Panels.item("Other app…", null, () -> AppPanels.appPicker("Default music app", a -> {
                Prefs.put(Prefs.MUSIC_PKG, a.pkg);
                Panels.back();
                Panels.back();
                Panels.refresh();
            })));
            return Panels.panel("Default music app", b, 560, 420);
        });
    }

    // ------------------------------------------------------------------ keyboard & Android settings

    // ------------------------------------------------------------------ set-up checklist (first run)

    static Panels.Page setupPage;

    static boolean isDefaultHome(Context c) {
        android.content.pm.ResolveInfo r = c.getPackageManager().resolveActivity(
                new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && c.getPackageName().equals(r.activityInfo.packageName);
    }

    static void setupChecklist() {
        open(setupPage = () -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "A few Android permissions make everything work. Each opens the right Android screen; press Back to return here.", 13, Ui.SUB, false));
            boolean notif = MediaListener.hasAccess(c), home = isDefaultHome(c), usage = Apps.hasUsageAccess(), kb = CarIME.isDefault(c);
            b.addView(check(c, R.drawable.ic_lyrics, 0xFF1F4E9A, "Notification access", "Song info, lyrics and controls, and turn-by-turn on the map",
                    notif ? "Done" : "Allow", notif, () -> {
                        android.content.Intent i;
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            i = new android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, MediaListener.component(c).flattenToString());
                            try {
                                c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                                return;
                            } catch (Exception ignored) {
                            }
                        }
                        MediaListener.requestAccess(c);
                    }), top(10));
            b.addView(check(c, R.drawable.ic_home, Ui.CARD3, "Make Car Launcher your Home app", "So the Home button always comes back here",
                    home ? "Done" : "Choose", home, () -> Status.open(c, Settings.ACTION_HOME_SETTINGS)));
            b.addView(check(c, R.drawable.ic_apps, Ui.CARD3, "Usage access (optional)", "Shows the apps you really use in the dock",
                    usage ? "Done" : "Allow", usage, () -> Status.open(c, Settings.ACTION_USAGE_ACCESS_SETTINGS)));
            b.addView(check(c, R.drawable.ic_mic, Ui.CARD3, "Car keyboard (optional)", "Big keys and voice typing in every app",
                    kb ? "Done" : "Set up", kb, SettingsPanels::keyboard));
            boolean fb = BackButtonService.enabled(c);
            b.addView(check(c, R.drawable.ic_back, Ui.CARD3, "Call helper (optional)", "Shows and answers calls from the box's BT Phone app",
                    fb ? "Done" : "Set up", fb, SettingsPanels::floatingBack));
            boolean wg = Prefs.flag("widgets_seen", false);
            b.addView(check(c, R.drawable.ic_apps, Ui.CARD3, "Choose your tiles", "Pick what goes on your home screen",
                    wg ? "Done" : "Choose", wg, () -> {
                        Prefs.put("widgets_seen", true);
                        AppPanels.gallery(-1);
                    }));
            boolean fa = Prefs.flag("faith_seen", false);
            b.addView(check(c, R.drawable.ic_moon, Ui.CARD3, "Faith add-ons", "Prayer times, qibla, travel prayers: " + Faith.label(),
                    fa ? "Done" : "Choose", fa, Faith::picker));
            boolean opt = Prefs.flag("opt_seen", false);
            b.addView(check(c, R.drawable.ic_bolt, Ui.CARD3, "Startup optimiser (recommended)", "Find apps that clash with Car Launcher or slow the start-up",
                    opt ? "Done" : "Check", opt, Optimiser::show));
            b.addView(Ui.text(c, "Find this again any time in Settings → Android settings → Set-up checklist.", 12, Ui.SUB, false), top(10));
            return Panels.panel("Welcome to Car Launcher", b, 640, 440);
        });
    }

    private static View check(Context c, int icon, int color, String title, String sub, String btn, boolean done, Runnable click) {
        LinearLayout r = Ui.row(c);
        r.setMinimumHeight(Ui.u(54));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(done ? Ui.DGREEN : color, 10));
        ic.addView(Ui.icon(c, done ? R.drawable.ic_check : icon, Ui.TEXT), Ui.flpu(20, 20, Gravity.CENTER));
        r.addView(ic, Ui.lpu(36, 36));
        LinearLayout t = Ui.col(c);
        t.addView(Ui.text(c, title, 15, Ui.TEXT, false));
        t.addView(Ui.text(c, sub, 12, Ui.SUB, false), top(3));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.setMargins(Ui.u(14), 0, Ui.u(12), 0);
        r.addView(t, tl);
        r.addView(Ui.button(c, btn, !done, () -> {
            Prefs.put("setup_return", System.currentTimeMillis()); // coming back reopens the checklist
            click.run();
        }));
        LinearLayout w = Ui.col(c);
        w.addView(Ui.hr(c));
        w.addView(r);
        return w;
    }

    static Panels.Page floatPage;

    static void floatingBack() {
        open(floatPage = () -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Lets Car Launcher show incoming calls and press Answer, Decline and the keypad in the box's BT Phone app. It reads only BT Phone's own screens.", 13, Ui.SUB, false));
            boolean on = BackButtonService.enabled(c);
            if (!on) b.addView(step(c, 1, "Allow restricted settings", "Tap Turn on below first (Android will block it), then come back here: App info → ⋮ (top right) → Allow restricted settings",
                    "App info", false, () -> Apps.appInfo(c, c.getPackageName())), top(10));
            b.addView(step(c, on ? 1 : 2, "Turn on \"Car Launcher call helper\"", "In Android's Accessibility list",
                    on ? "Done" : "Turn on", on, () -> Status.open(c, Settings.ACTION_ACCESSIBILITY_SETTINGS)), on ? top(10) : Ui.lp(Ui.MATCH, Ui.WRAP));
            String st = BackButtonService.I != null ? "Running" : on ? "Switched on but not running: switch it off and on again in Accessibility" : "Off";
            b.addView(Ui.text(c, "Status: " + st, 13, BackButtonService.I != null ? Ui.GREEN : Ui.AMBER, true), top(8));
            return Panels.panel("Call helper", b, 640, 432);
        });
    }

    /** Android's "Display over other apps" page for Car Launcher (falls back to the full list). */
    static void overlaySettings(Context c) {
        try {
            c.startActivity(new android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + c.getPackageName())).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Status.open(c, Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        }
    }

    static void holdMenu() {
        Panels.push(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            java.util.List<String> chosen = QuickActions.chosen();
            b.addView(Ui.text(c, "Hold the back button to open these in a circle around it. Tap to add or remove (3 to 8). Numbers show the order, starting at the top.",
                    12, Ui.SUB, false));
            java.util.List<View> cells = new java.util.ArrayList<>();
            for (Object[] a : QuickActions.ALL) {
                final String id = (String) a[0];
                int pos = chosen.indexOf(id);
                boolean on = pos >= 0;
                LinearLayout r = Ui.row(c);
                r.setBackground(on ? Ui.outline(0xFF1A2740, Ui.ACCENT, 2, 10) : Themes.inner(Ui.CARD2, 10));
                Ui.pad(r, 10, 0, 10, 0);
                FrameLayout ic = new FrameLayout(c);
                ic.setBackground(Ui.oval(on ? Ui.ACCENT : Ui.CARD3));
                ic.addView(Ui.icon(c, (Integer) a[2], Ui.TEXT), Ui.flpu(16, 16, Gravity.CENTER));
                r.addView(ic, Ui.lpu(28, 28));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
                tl.leftMargin = Ui.u(10);
                r.addView(Ui.line(c, (String) a[1], 13, Ui.TEXT, false), tl);
                if (on) r.addView(Ui.text(c, String.valueOf(pos + 1), 12, Ui.ACCENT_SOFT, true));
                r.setMinimumHeight(Ui.u(40));
                r.setOnClickListener(v -> {
                    java.util.List<String> l = new java.util.ArrayList<>(QuickActions.chosen());
                    if (l.contains(id)) {
                        if (l.size() <= QuickActions.MIN) {
                            android.widget.Toast.makeText(c, "Keep at least 3 buttons", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        l.remove(id);
                    } else {
                        if (l.size() >= QuickActions.MAX) {
                            android.widget.Toast.makeText(c, "Up to 8 buttons — remove one first", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        l.add(id);
                    }
                    Prefs.putList("fb_menu", l);
                    Panels.refresh();
                });
                cells.add(r);
            }
            b.addView(AppPanels.grid(c, cells, 3, 8), top(10));
            return Panels.panel("Hold menu", b, 700, 440);
        });
    }

    private static TextView sizeSeg(Context c, String v, String label, String cur) {
        return Ui.seg(c, label, v.equals(cur), () -> {
            Prefs.put("fb_size", v);
            BackButtonService.refresh();
            Panels.refresh();
        });
    }

    static Panels.Page keyboardPage;

    static void keyboard() {
        open(keyboardPage = () -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Use the big Car keyboard (with voice typing) in every app on the box, not just the launcher.", 13, Ui.SUB, false));
            boolean en = CarIME.enabled(c), def = CarIME.isDefault(c);
            boolean mic = c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            b.addView(step(c, 1, "Turn on Car keyboard", "In Android's keyboard list. Android shows a standard warning for any keyboard; tap OK.", en ? "Done" : "Turn on", en, () ->
                    Status.open(c, Settings.ACTION_INPUT_METHOD_SETTINGS)), top(10));
            b.addView(step(c, 2, "Make it the default", "Pick \"Car keyboard\" from the list Android shows", def ? "Done" : "Choose", def, () -> {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
                Ui.ui.postDelayed(Panels::refresh, 4000);
            }));
            b.addView(step(c, 3, "Allow the microphone", "For the Voice button", mic ? "Done" : "Allow", mic, () -> {
                if (MainActivity.I != null) MainActivity.I.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 54);
            }));
            b.addView(Ui.text(c, "To switch back later, choose your old keyboard the same way.", 12, Ui.SUB, false), top(12));
            return Panels.panel("Keyboard", b, 640, 432);
        });
    }

    private static View step(Context c, int n, String title, String sub, String btn, boolean done, Runnable click) {
        LinearLayout r = Ui.row(c);
        r.setMinimumHeight(Ui.u(64));
        TextView num = Ui.text(c, done ? "✓" : String.valueOf(n), 14, Ui.TEXT, true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(Ui.oval(done ? Ui.DGREEN : Ui.CARD3));
        r.addView(num, Ui.lpu(30, 30));
        LinearLayout t = Ui.col(c);
        t.addView(Ui.text(c, title, 15, Ui.TEXT, false));
        t.addView(Ui.text(c, sub, 12, Ui.SUB, false), top(3));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.setMargins(Ui.u(14), 0, Ui.u(12), 0);
        r.addView(t, tl);
        r.addView(Ui.button(c, btn, !done, click));
        LinearLayout w = Ui.col(c);
        w.addView(Ui.hr(c));
        w.addView(r);
        return w;
    }

    /** True once if we've just come back from an Android page opened by the checklist (within 10 min). */
    static boolean backFromChecklist() {
        long at = Prefs.lng("setup_return", 0);
        if (at == 0) return false;
        Prefs.put("setup_return", 0L);
        return System.currentTimeMillis() - at < 600000;
    }

    /** Show the checklist again (or refresh it if it's already up). */
    static void reopenChecklist() {
        if (Panels.open() && Panels.top() == setupPage) Panels.refresh();
        else setupChecklist();
    }

    static void androidSettings() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.item("Set-up checklist", null, SettingsPanels::setupChecklist));
            b.addView(Ui.hr(c));
            b.addView(Panels.item("Call helper", BackButtonService.enabled(c) ? "On" : "Off", SettingsPanels::floatingBack));
            b.addView(Ui.hr(c));
            b.addView(Panels.item("Startup optimiser", null, Optimiser::show));
            b.addView(Ui.hr(c));
            String[][] items = {{"All Android settings", Settings.ACTION_SETTINGS}, {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS},
                    {"Wi-Fi", Settings.ACTION_WIFI_SETTINGS}, {"Sound", Settings.ACTION_SOUND_SETTINGS}, {"Display", Settings.ACTION_DISPLAY_SETTINGS},
                    {"Home app", Settings.ACTION_HOME_SETTINGS}, {"Apps", Settings.ACTION_APPLICATION_SETTINGS}};
            for (String[] it : items) {
                b.addView(Panels.item(it[0], null, () -> {
                    Status.open(c, it[1]);
                    Panels.close();
                }));
                b.addView(Ui.hr(c));
            }
            return Panels.panel("Android settings", b, 520, 432);
        });
    }

    // ------------------------------------------------------------------ map

    private static void mapRows(Context c, LinearLayout b) {
        {
            String prov = Maps.provider();
            if (Addons.mapUnlocked("osm")) b.addView(radio(c, "Standard", "OpenStreetMap. Free, no account. Dark style.", "osm".equals(prov), () -> setMap("osm")));
            if (Addons.mapUnlocked("ofm")) b.addView(radio(c, "OpenFreeMap", "Free vector map with sharp labels that stay upright as it turns.", "ofm".equals(prov), () -> setMap("ofm")));
            if ("ofm".equals(prov)) {
                String st = Prefs.str("ofm_style", "liberty");
                if ("dark".equals(st)) st = "liberty";
                b.addView(Panels.row("Day style", Ui.seg(c, "Liberty", "liberty".equals(st), () -> { Prefs.put("ofm_style", "liberty"); setMap("ofm"); }),
                        Ui.seg(c, "Positron", "positron".equals(st), () -> { Prefs.put("ofm_style", "positron"); setMap("ofm"); }),
                        Ui.seg(c, "Bright", "bright".equals(st), () -> { Prefs.put("ofm_style", "bright"); setMap("ofm"); })), top(4));
            }
            if (Addons.mapUnlocked("pmtiles")) b.addView(radio(c, "Offline map", "Download the area around you (or use a .pmtiles file). Works with no internet.", "pmtiles".equals(prov), () -> {
                if (Prefs.str(OfflineMap.URI, "").isEmpty()) MapDownload.panel();
                else setMap("pmtiles");
            }));
            {
                LinearLayout r = Ui.row(c);
                String nm = Prefs.str(OfflineMap.NAME, "");
                r.addView(Ui.line(c, nm.isEmpty() ? "No map file chosen" : nm, 13, nm.isEmpty() ? Ui.AMBER : Ui.TEXT2, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                r.addView(Ui.button(c, nm.isEmpty() ? "Choose file" : "Change file", false, () -> {
                    if (MainActivity.I != null) OfflineMap.pick(MainActivity.I);
                }));
                r.addView(Ui.gap(c, 8, 1));
                r.addView(Ui.button(c, "Download map here", true, MapDownload::panel));
                b.addView(r, top(4));
                b.addView(Ui.text(c, "Download map here fetches just the area around the car (choose how far and how detailed). Or choose a .pmtiles file you copied onto the box. Street-name lettering needs internet the first time; the map itself works offline.",
                        12, Ui.SUB, false), top(6));
            }
            if (Addons.mapUnlocked("google")) b.addView(radio(c, "Google Maps", "Needs your own Google Maps API key. Live traffic.", "google".equals(prov), () -> setMap("google")));
            String col = Maps.colours();
            b.addView(Panels.row("Colours", Ui.seg(c, "Auto (day / night)", "auto".equals(col), () -> { Prefs.put("map_colours", "auto"); setMap(prov); }),
                    Ui.seg(c, "Light", "light".equals(col), () -> { Prefs.put("map_colours", "light"); setMap(prov); }),
                    Ui.seg(c, "Dark", "dark".equals(col), () -> { Prefs.put("map_colours", "dark"); setMap(prov); })), top(10));
            b.addView(Ui.text(c, "Auto: light from sunrise, dark from sunset where the car is (switches by itself).", 12, Ui.SUB, false), top(4));
            if ("google".equals(prov)) {
                b.addView(Panels.section("Google Maps API key"));
                final String[] key = {Prefs.str(Prefs.MAP_KEY, "")};
                LinearLayout r = Ui.row(c);
                r.addView(Widgets.field("Paste your key (starts with AIza…)", key[0], true, t -> key[0] = t), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                r.addView(Ui.gap(c, 10, 1));
                r.addView(Ui.button(c, "Save", true, () -> {
                    Prefs.put(Prefs.MAP_KEY, key[0]);
                    reloadMap();
                    Panels.refresh();
                }));
                b.addView(r, Ui.lp(Ui.MATCH, Ui.WRAP));
                b.addView(Ui.text(c, Prefs.str(Prefs.MAP_KEY, "").isEmpty() ? "No key yet, so the map is using Standard." : "Key saved. If Google refuses it, the map goes back to Standard and tells you.",
                        12, Prefs.str(Prefs.MAP_KEY, "").isEmpty() ? Ui.AMBER : Ui.GREEN, false), top(8));
                b.addView(Ui.text(c, "Get a key at console.cloud.google.com: create a project, turn on \"Maps JavaScript API\", then Credentials → Create API key. Restrict the key to the Maps JavaScript API only. Google asks for a card, but light use like this should stay within its free monthly allowance.",
                        12, Ui.SUB, false), top(10));
            }
            String nw = Prefs.str(NavCard.WHERE, "tiles");
            b.addView(Panels.row("Turn-by-turn", Ui.seg(c, "In the tile area", "tiles".equals(nw), () -> { Prefs.put(NavCard.WHERE, "tiles"); NavCard.update(); reloadMap(); Panels.refresh(); }),
                    Ui.seg(c, "In the map panel", "map".equals(nw), () -> { Prefs.put(NavCard.WHERE, "map"); NavCard.update(); reloadMap(); Panels.refresh(); }),
        Ui.seg(c, "Swap: big map", "swap".equals(nw), () -> { Prefs.put(NavCard.WHERE, "swap"); NavCard.update(); reloadMap(); Panels.refresh(); })), top(8));
            boolean hu = Maps.headingUp();
            b.addView(Panels.row("Map faces", Ui.seg(c, "Direction of travel", hu, () -> { Prefs.put("map_heading_up", true); if (MainActivity.I != null) MainActivity.I.map.applyHeading(); Panels.refresh(); }),
                    Ui.seg(c, "North up", !hu, () -> { Prefs.put("map_heading_up", false); if (MainActivity.I != null) MainActivity.I.map.applyHeading(); Panels.refresh(); })), top(8));
            b.addView(Panels.item("Alternate stage", LeftStages.list().size() + " widgets · swipe it to go through them", LeftStages::chooser), top(8));
            b.addView(Ui.text(c, "Maps lives on the Centre Stage. When something else opens there, or the Tile Drawer slides in, it shrinks into the alternate stage, never Now Playing's.", 12, Ui.SUB, false), top(6));
        }
    }

    private static void remini() {
        if (MainActivity.I != null && MainActivity.I.map.expanded()) {
            MainActivity.I.map.expand(false);
            MainActivity.I.map.expand(true);
        }
    }

    private static void setMap(String p) {
        Prefs.put(Prefs.MAP_PROVIDER, p);
        reloadMap();
        Panels.refresh();
    }

    private static void reloadMap() {
        if (MainActivity.I != null) MainActivity.I.map.reload();
    }

    // ------------------------------------------------------------------ right bar order

    static void railOrder() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            List<String[]> order = Rail.order();
            for (int i = 0; i < order.size(); i++) {
                final int idx = i;
                String[] e = order.get(i);
                LinearLayout r = Ui.row(c);
                Ui.pad(r, 0, 4, 0, 4);
                TextView up = Ui.text(c, "▲", 15, i == 0 ? Ui.MUTED : Ui.TEXT2, false);
                Ui.pad(up, 8, 6, 8, 6);
                up.setOnClickListener(v -> {
                    if (idx == 0) return;
                    order.add(idx - 1, order.remove(idx));
                    Rail.saveOrder(order);
                    rebuildRail();
                    Panels.refresh();
                });
                r.addView(up);
                TextView dn = Ui.text(c, "▼", 15, i == order.size() - 1 ? Ui.MUTED : Ui.TEXT2, false);
                Ui.pad(dn, 8, 6, 12, 6);
                dn.setOnClickListener(v -> {
                    if (idx == order.size() - 1) return;
                    order.add(idx + 1, order.remove(idx));
                    Rail.saveOrder(order);
                    rebuildRail();
                    Panels.refresh();
                });
                r.addView(dn);
                r.addView(Ui.line(c, Rail.NAMES[Rail.indexOf(e[0])], 15, Ui.TEXT, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                r.addView(new Ui.Toggle(c, "1".equals(e[1]), on -> {
                    e[1] = on ? "1" : "0";
                    Rail.saveOrder(order);
                    rebuildRail();
                }));
                b.addView(r);
            }
            b.addView(Ui.text(c, "Switch an icon off to remove it from the Rail Bar; the others spread out to fill the space. The clock stays at the top and the mic at the bottom.", 12, Ui.SUB, false), top(8));
            int cs = Prefs.num("card_secs", 15);
            b.addView(Panels.row("Pop-ups close after", segNum(c, "card_secs", 5, "5 s", cs), segNum(c, "card_secs", 8, "8 s", cs),
                    segNum(c, "card_secs", 15, "15 s", cs), segNum(c, "card_secs", 30, "30 s", cs), segNum(c, "card_secs", 0, "Never", cs)), top(10));
            return Panels.panel("Rail Bar", b, 480, 440);
        });
    }

    private static void rebuildRail() {
        if (MainActivity.I != null) MainActivity.I.rail.build();
    }

    // ------------------------------------------------------------------ hidden apps

    /** Apps hidden from All apps (dock=false) or only from the dock (dock=true). */
    static void hiddenApps(boolean dock) {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            java.util.Set<String> set = dock ? Apps.dockHidden() : Apps.hidden();
            b.addView(Ui.text(c, dock ? "These never show in the dock (pinned or recent) but stay in All apps." : "These don't show in All apps or the dock.", 13, Ui.SUB, false));
            if (set.isEmpty()) b.addView(Ui.text(c, "None hidden.", 14, Ui.SUB, false), top(8));
            for (String k : new ArrayList<>(set)) {
                Apps.App a = Apps.byKey.get(k);
                if (a == null) continue;
                LinearLayout r = Ui.row(c);
                r.setMinimumHeight(Ui.u(50));
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(a.icon);
                r.addView(iv, Ui.lpu(32, 32));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
                tl.leftMargin = Ui.u(12);
                r.addView(Ui.line(c, a.label, 15, Ui.TEXT, false), tl);
                r.addView(Ui.seg(c, "Unhide", false, () -> {
                    if (dock) Apps.setDockHidden(k, false);
                    else Apps.setHidden(k, false);
                    if (MainActivity.I != null) MainActivity.I.dock.render();
                    Panels.refresh();
                }));
                b.addView(r);
            }
            b.addView(Ui.button(c, "+ Hide an app", false, () -> AppPanels.appPicker(dock ? "Hide from Dock" : "Hide from All apps", ap -> {
                if (dock) Apps.setDockHidden(ap.key, true);
                else Apps.setHidden(ap.key, true);
                if (MainActivity.I != null) MainActivity.I.dock.render();
                Panels.back();
            })), top(10));
            return Panels.panel(dock ? "Hidden from Dock" : "Hidden from All apps", b, 520, 420);
        });
    }

    /** Dock: the dialler button and how many icons (5–7, not counting All apps). */
    static void dock() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.switchRow(0, 0, "Dialler button", "The green phone button at the start of the dock", Dock.dialler(), v -> {
                Prefs.put("dock_dialer", v);
                if (MainActivity.I != null) MainActivity.I.dock.render();
            }));
            // also here (not only in News and traffic): hiding the ticker makes the dock bigger
            b.addView(Panels.switchRow(0, 0, "Show the news ticker", "Off: the map and tiles get its space", Prefs.flag("ticker_on", true), on -> {
                Prefs.put("ticker_on", on);
                if (MainActivity.I != null) MainActivity.I.recreate();
            }), top(4));
            int n = Dock.icons();
            List<View> segs = new ArrayList<>();
            for (int k = 5; k <= 8; k++) {
                final int v = k;
                segs.add(Ui.seg(c, String.valueOf(k), k == n, () -> {
                    Prefs.put("dock_icons", v);
                    if (MainActivity.I != null) MainActivity.I.dock.render();
                    Panels.refresh();
                }));
            }
            b.addView(Panels.row("Icons", segs.toArray(new View[0])), top(8));
            b.addView(Ui.text(c, "Not counting All apps. Pinned apps come first, then the ones you used last.", 12, Ui.SUB, false), top(4));
            b.addView(Panels.item("Hidden from Dock", Apps.dockHidden().size() + " hidden", () -> hiddenApps(true)), top(8));
            return Panels.panel("Dock", b, 520, 360);
        });
    }

    // ------------------------------------------------------------------ news & traffic

    static void news() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.switchRow(0, 0, "Show the news ticker", "Off: the map and tiles get its space (restarts the home screen)", Prefs.flag("ticker_on", true), on -> {
                Prefs.put("ticker_on", on);
                if (MainActivity.I != null) MainActivity.I.recreate();
            }));
            String bp = NewsTicker.browser();
            b.addView(Panels.item("Open stories in", bp != null ? Apps.label(bp) : "Android's default browser", NewsTicker::browserPicker), top(8));
            // what the ticker shows: tap its label to go round the ones that are on
            b.addView(Panels.section("Show in the ticker"));
            b.addView(Panels.switchRow(0, 0, "News", "Local headlines", NewsTicker.newsOn(), on -> { NewsTicker.setOn(NewsTicker.NEWS_ON, on); Panels.refresh(); }));
            b.addView(Panels.switchRow(0, 0, "Traffic", "Traffic news for your area", NewsTicker.trafficOn(), on -> { NewsTicker.setOn(NewsTicker.TRAFFIC_ON, on); Panels.refresh(); }));
            b.addView(Panels.switchRow(0, 0, "Lyrics", "The line being sung, while music plays", NewsTicker.lyricsOn(), on -> { NewsTicker.setOn(NewsTicker.LYRICS_ON, on); Panels.refresh(); }));
            if (SpotifyLyrics.ENABLED()) b.addView(Panels.item("Spotify lyrics", SpotifyLyrics.signedIn() ? "Signed in" : "Sign in for Spotify's own lyrics", SpotifyLyrics::settings));
            String m = Prefs.str(Prefs.TICKER_MODE, "alt");
            int every = Prefs.num(Prefs.TICKER_EVERY, 60);
            if ("alt".equals(m)) b.addView(Panels.row("Switch every", segNum(c, Prefs.TICKER_EVERY, 30, "30 s", every),
                    segNum(c, Prefs.TICKER_EVERY, 60, "1 min", every), segNum(c, Prefs.TICKER_EVERY, 120, "2 min", every), segNum(c, Prefs.TICKER_EVERY, 300, "5 min", every)));
            int back = Prefs.num(Prefs.TICKER_BACK, 60);
            b.addView(Panels.row("After a tap, go back", segNum(c, Prefs.TICKER_BACK, 30, "30 s", back), segNum(c, Prefs.TICKER_BACK, 60, "1 min", back),
                    segNum(c, Prefs.TICKER_BACK, 300, "5 min", back), segNum(c, Prefs.TICKER_BACK, 0, "Never", back)));
            b.addView(Panels.switchRow(0, 0, "Scrolling headlines", "Off shows one headline at a time", Prefs.flag(Prefs.TICKER_SCROLL, true),
                    on -> Prefs.put(Prefs.TICKER_SCROLL, on)));
            b.addView(Panels.section("News feed (RSS)"));
            b.addView(Widgets.field("Blank = Google News for " + (Gps.town != null ? Gps.town : "your area"), Prefs.str(Prefs.NEWS_URL, ""), false,
                    t -> Prefs.put(Prefs.NEWS_URL, t)), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Panels.section("Traffic feed (RSS)"));
            b.addView(Widgets.field("Blank = \"traffic\" news for your area", Prefs.str(Prefs.TRAFFIC_URL, ""), false,
                    t -> Prefs.put(Prefs.TRAFFIC_URL, t)), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Panels.section("TomTom key (optional, for live incidents)"));
            b.addView(Widgets.field("Free key from developer.tomtom.com", Prefs.str(Prefs.TOMTOM_KEY, ""), true,
                    t -> Prefs.put(Prefs.TOMTOM_KEY, t)), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Ui.button(c, "Reload headlines now", false, () -> {
                if (MainActivity.I != null) MainActivity.I.ticker.fetch();
            }), top(14));
            b.addView(Panels.switchRow(0, 0, "Show traffic when the car starts", "Opens the traffic list in the tile area for 15 seconds", Prefs.flag("traffic_start", true), v -> Prefs.put("traffic_start", v)), top(8));
            return Panels.panel("News and traffic", b, 660, 440);
        });
    }

    static TextView segStr(Context c, String key, String value, String label, String cur) {
        return Ui.seg(c, label, value.equals(cur), () -> {
            Prefs.put(key, value);
            Panels.refresh();
        });
    }

    static TextView segNum(Context c, String key, int value, String label, int cur) {
        return Ui.seg(c, label, value == cur, () -> {
            Prefs.put(key, value);
            Panels.refresh();
        });
    }

    // ------------------------------------------------------------------ calls

    static void callLog() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            java.util.List<String> cl = Prefs.list("call_log");
            if (cl.isEmpty()) b.addView(Ui.text(c, "Nothing yet. Make or receive a call, then come back. If this stays empty, BT Phone isn't posting notifications at all.", 13, Ui.SUB, false));
            for (String s : cl) {
                TextView t = Ui.text(c, s, 12, Ui.TEXT2, false);
                t.setBackground(Themes.inner(Ui.CARD2, 10));
                Ui.pad(t, 10, 8, 10, 8);
                b.addView(t, top(6));
            }
            b.addView(Ui.text(c, "Bluetooth permission (Nearby devices): " + (c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == android.content.pm.PackageManager.PERMISSION_GRANTED ? "allowed" : "not allowed")
                    + " · Notification access: " + (MediaListener.hasAccess(c) ? "on" : "off") + " · BT Phone app: " + (Actions.btDialler() == null ? "not found" : Apps.label(Actions.btDialler())), 12, Ui.SUB, false), top(12));
            return Panels.panel("Last BT Phone notifications", b, 640, 430);
        });
    }

    static void calls() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            // first, so it's easy to find: what BT Phone has sent (helps when calls aren't picked up)
            b.addView(Panels.item("Last BT Phone notifications", Prefs.list("call_log").size() + " recorded", SettingsPanels::callLog));
            b.addView(Ui.hr(c));
            b.addView(Ui.text(c, "Quick dial, Contact tiles and the assistant all call through this.", 12, Ui.SUB, false));
            String m = Prefs.str(Prefs.CALLS, "bt");
            String bt = Actions.btDialler();
            b.addView(radio(c, "Bluetooth dialler (default)", "Calls go through your paired phone · " + (bt == null ? "choose the app ›" : "app: " + Apps.label(bt) + " ›"),
                    "bt".equals(m), () -> {
                        Prefs.put(Prefs.CALLS, "bt");
                        if (Actions.btDialler() == null) AppPanels.btPhonePicker(a -> {
                            Prefs.put(Prefs.CALLS_BT_PKG, a.pkg); Actions.btReset();
                            Panels.back();
                            Panels.refresh();
                        });
                        else Panels.refresh();
                    }), top(6));
            b.addView(radio(c, "Box dialler", "Calls go out over the SIM card in the car box", "box".equals(m), () -> {
                Prefs.put(Prefs.CALLS, "box");
                Panels.refresh();
            }));
            b.addView(radio(c, "Through Car Connect", "Opens CarPlay / Android Auto to call from your phone", "connect".equals(m), () -> {
                Prefs.put(Prefs.CALLS, "connect");
                Panels.refresh();
            }));
            if ("bt".equals(m)) {
                b.addView(Panels.item("Bluetooth dialler app", bt == null ? "Choose" : Apps.label(bt), () -> AppPanels.btPhonePicker(a -> {
                    Prefs.put(Prefs.CALLS_BT_PKG, a.pkg); Actions.btReset();
                    Panels.back();
                    Panels.refresh();
                })), top(6));
                String fb = Prefs.str(Prefs.CALLS_FALLBACK, "ask");
                b.addView(Panels.row("If Bluetooth isn't connected", segStr(c, Prefs.CALLS_FALLBACK, "ask", "Ask", fb),
                        segStr(c, Prefs.CALLS_FALLBACK, "box", "Use box dialler", fb), segStr(c, Prefs.CALLS_FALLBACK, "none", "Do nothing", fb)));
            }
            b.addView(Panels.section("Call screen"), top(4));
            b.addView(Panels.switchRow(R.drawable.ic_call, Ui.GREEN, "Car Launcher call screen", "Big full-screen Answer / Decline for BT Phone calls (uses notification access)",
                    Prefs.flag("call_screen", true), v -> Prefs.put("call_screen", v)));
            boolean dialer = Actions.isDefaultDialer(c);
            b.addView(Panels.item("Make Car Launcher the box's Phone app", dialer ? "On" : "Off", () -> {
                if (dialer) {
                    Status.open(c, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                    return;
                }
                try {
                    android.app.role.RoleManager rm = (android.app.role.RoleManager) c.getSystemService(Context.ROLE_SERVICE);
                    if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER) && MainActivity.I != null)
                        MainActivity.I.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER), 62);
                    else Status.open(c, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                } catch (Exception e) {
                    Status.open(c, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                }
            }));
            b.addView(Ui.text(c, "Optional. If this box handles Bluetooth calls through Android, every call then uses Car Launcher's screen with mute, keypad and audio controls. If it doesn't, nothing changes and BT Phone keeps working.", 12, Ui.SUB, false), top(4));
            return Panels.panel("Calls", b, 640, 430);
        });
    }

    static View radio(Context c, String title, String sub, boolean on, Runnable click) {
        LinearLayout r = Ui.row(c);
        Ui.pad(r, 0, 10, 0, 10);
        View dot = new View(c);
        dot.setBackground(on ? Ui.outline(Ui.ACCENT, Ui.TEXT, 3, 11) : Ui.outline(0, Ui.MUTED, 2, 11));
        r.addView(dot, Ui.lpu(22, 22));
        LinearLayout t = Ui.col(c);
        t.addView(Ui.text(c, title, 15, Ui.TEXT, on));
        t.addView(Ui.text(c, sub, 12, Ui.SUB, false), top(3));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(14);
        r.addView(t, tl);
        r.setOnClickListener(v -> click.run());
        return r;
    }

    // ------------------------------------------------------------------ connections

    static void connections() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.switchRow(R.drawable.ic_wifi, Ui.ACCENT, "Join my phone's hotspot", "Android asks once to allow it", Prefs.flag(Prefs.CONN_PHONE, false), on -> {
                Prefs.put(Prefs.CONN_PHONE, on);
                if (on) Actions.joinPhoneHotspot(c);
                Panels.refresh();
            }));
            if (Prefs.flag(Prefs.CONN_PHONE, false)) {
                LinearLayout f = Ui.row(c);
                f.addView(Widgets.field("Hotspot name (SSID)", Prefs.str(Prefs.CONN_PHONE_SSID, ""), false, t -> Prefs.put(Prefs.CONN_PHONE_SSID, t)), Ui.weight(1, true));
                f.addView(Ui.gap(c, 10, 1));
                f.addView(Widgets.field("Password", Prefs.str(Prefs.CONN_PHONE_PASS, ""), true, t -> Prefs.put(Prefs.CONN_PHONE_PASS, t)), Ui.weight(1, true));
                b.addView(f, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            b.addView(Panels.switchRow(R.drawable.ic_hot, Ui.ACCENT, "Turn on the box hotspot", "Opens hotspot settings if it's off (Android 13 limit)",
                    Prefs.flag(Prefs.CONN_HOTSPOT, false), on -> Prefs.put(Prefs.CONN_HOTSPOT, on)));
            b.addView(Panels.switchRow(R.drawable.ic_vpnkey, Ui.GREEN, "Connect Tailscale at start",
                    Status.tailscaleInstalled() ? "Uses the Tailscale app on this box" : "Install Tailscale first", Prefs.flag(Prefs.CONN_TS, false), on -> {
                        Prefs.put(Prefs.CONN_TS, on);
                        if (on) Status.tailscale(true);
                        Panels.refresh();
                    }));
            if (Prefs.flag(Prefs.CONN_TS, false))
                b.addView(Panels.switchRow(R.drawable.ic_refresh, Ui.SUB, "Reconnect every minute if dropped", null, Prefs.flag(Prefs.CONN_TS_RETRY, true),
                        on -> Prefs.put(Prefs.CONN_TS_RETRY, on)));
            b.addView(Panels.item("Home Assistant over Tailscale", HA.configured() ? "Set up" : "Not set up", () -> {
                JSONArray s = TileDrawer.specs();
                for (int i = 0; i < s.length(); i++)
                    if ("ha".equals(s.optJSONObject(i).optString("id"))) {
                        HA.setup(i);
                        return;
                    }
                Panels.push(() -> Panels.panel("Home Assistant", Ui.text(c(), "Add the Home Assistant tile first: long-press a tile → ⋯ → Replace with another tile.", 14, Ui.SUB, false), 480, 220));
            }));
            return Panels.panel("Connections at start", b, 620, 440);
        });
    }

    // ------------------------------------------------------------------ splash screen

    static void splash() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.switchRow(0, 0, "Show a splash screen", "When the car starts", Prefs.flag(Prefs.SPLASH_ON, false), on -> {
                Prefs.put(Prefs.SPLASH_ON, on);
                Panels.refresh();
            }));
            String type = Prefs.str(Prefs.SPLASH_TYPE, "prayer");
            LinearLayout tiles = Ui.row(c);
            Prayers.P p = Prayers.get(Prefs.str(Prefs.PRAYER_ID, null));
            tiles.addView(typeTile(c, "Travel Prayer", p == null ? "Not chosen" : p.name, "prayer".equals(type), () -> {
                Prefs.put(Prefs.SPLASH_TYPE, "prayer");
                Panels.refresh();
            }), Ui.weight(1, true));
            tiles.addView(Ui.gap(c, 12, 1));
            String ct = Prefs.str(Prefs.CUSTOM_TEXT, "");
            tiles.addView(typeTile(c, "Custom", ct.isEmpty() ? "Your own text" : ct, "custom".equals(type), () -> {
                Prefs.put(Prefs.SPLASH_TYPE, "custom");
                Panels.refresh();
            }), Ui.weight(1, true));
            b.addView(tiles, Ui.lpu(Ui.MATCH, 76));
            int secs = Prefs.num(Prefs.SPLASH_SECS, 10);
            b.addView(Panels.row("Show for", segNum(c, Prefs.SPLASH_SECS, 5, "5 s", secs), segNum(c, Prefs.SPLASH_SECS, 10, "10 s", secs),
                    segNum(c, Prefs.SPLASH_SECS, 15, "15 s", secs), segNum(c, Prefs.SPLASH_SECS, 30, "30 s", secs), segNum(c, Prefs.SPLASH_SECS, 0, "Until tapped", secs)), top(8));
            String when = Prefs.str(Prefs.SPLASH_WHEN, "start");
            b.addView(Panels.row("When", segStr(c, Prefs.SPLASH_WHEN, "start", "Every start", when), segStr(c, Prefs.SPLASH_WHEN, "day", "Once a day", when),
                    segStr(c, Prefs.SPLASH_WHEN, "nav", "When navigation starts", when)));
            String mode = Splash.mode();
            b.addView(Panels.row("Show as", segStr(c, "splash_mode", "screen", "Screen", mode), segStr(c, "splash_mode", "both", "Screen + voice", mode),
                    segStr(c, "splash_mode", "voice", "Voice only", mode)), top(4));
            if (!"screen".equals(mode) && !"custom".equals(type)) {
                String lang = Prefs.str("splash_voice_lang", "orig");
                b.addView(Panels.row("Recite in", segStr(c, "splash_voice_lang", "orig", "Original language", lang),
                        segStr(c, "splash_voice_lang", "en", "English", lang)), top(4));
            }
            if (!"screen".equals(mode)) {
                String src = Prefs.str("splash_audio", "builtin");
                boolean has = !Prefs.str("splash_audio_uri", "").isEmpty();
                b.addView(Panels.row("Recording", segStr(c, "splash_audio", "builtin", "Built-in voice", src),
                        Ui.seg(c, "own".equals(src) && has ? "My recording ✓" : "My recording…", "own".equals(src), () -> {
                            if (MainActivity.I != null) MainActivity.I.pickAudio();
                        })), top(4));
            }
            if ("custom".equals(type)) {
                b.addView(Panels.section("Custom text"));
                b.addView(Widgets.field("Main text", ct, false, t -> Prefs.put(Prefs.CUSTOM_TEXT, t)), Ui.lp(Ui.MATCH, Ui.WRAP));
                b.addView(Widgets.field("Small text underneath (optional)", Prefs.str(Prefs.CUSTOM_SUB, ""), false, t -> Prefs.put(Prefs.CUSTOM_SUB, t)), top(8));
            } else {
                b.addView(Panels.item("Set up Travel Prayer", null, SettingsPanels::travelPrayer), top(4));
            }
            b.addView(Ui.button(c, "Preview", false, () -> {
                if (!"screen".equals(Splash.mode())) Splash.recite(c);
                if (!"voice".equals(Splash.mode())) preview();
            }), top(10));
            return Panels.panel("Splash screen", b, 680, 444);
        });
    }

    static void preview() {
        if (MainActivity.I == null) return;
        // come back to the same setup page when the preview ends
        final java.util.List<Panels.Page> pages = Panels.saveStack();
        Panels.close();
        Splash.show(MainActivity.I, () -> Panels.restoreStack(pages));
    }

    private static View typeTile(Context c, String title, String sub, boolean on, Runnable click) {
        LinearLayout l = Ui.col(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(on ? Ui.outline(0xFF1A2740, Ui.ACCENT, 2, 14) : Themes.inner(Ui.CARD2, 14));
        Ui.pad(l, 16, 10, 16, 10);
        l.addView(Ui.line(c, title, 15, Ui.TEXT, true));
        l.addView(Ui.line(c, sub, 12, Ui.SUB, false), top(4));
        l.setOnClickListener(v -> click.run());
        return l;
    }

    // ------------------------------------------------------------------ travel prayer

    static void travelPrayer() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            Prayers.P p = Prayers.get(Prefs.str(Prefs.PRAYER_ID, null));
            b.addView(Panels.section("Prayer"));
            if (p == null) {
                LinearLayout empty = Ui.row(c);
                empty.setBackground(Ui.dashed(Ui.MUTED, 14));
                Ui.pad(empty, 16, 14, 16, 14);
                empty.addView(Ui.text(c, "No prayer chosen", 15, Ui.SUB, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                empty.addView(Ui.text(c, "Choose ›", 14, Ui.ACCENT_SOFT, true));
                empty.setOnClickListener(v -> prayerPicker());
                b.addView(empty, Ui.lp(Ui.MATCH, Ui.WRAP));
            } else {
                LinearLayout sel = Ui.row(c);
                sel.setBackground(Themes.inner(Ui.CARD2, 14));
                Ui.pad(sel, 16, 12, 16, 12);
                LinearLayout t = Ui.col(c);
                t.addView(Ui.line(c, p.name, 15, Ui.TEXT, true));
                t.addView(Ui.line(c, p.faith + " · " + p.source, 12, Ui.SUB, false), top(3));
                sel.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                sel.addView(Ui.text(c, "Change ›", 14, Ui.ACCENT_SOFT, true));
                sel.setOnClickListener(v -> prayerPicker());
                b.addView(sel, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            b.addView(Panels.section("Layout"));
            String layout = Prefs.str(Prefs.PRAYER_LAYOUT, "stacked");
            LinearLayout lay = Ui.row(c);
            String[][] ls = {{"stacked", "Stacked"}, {"split", "Split"}, {"card", "Card"}};
            for (int i = 0; i < 3; i++) {
                final String id = ls[i][0];
                LinearLayout cell = Ui.col(c);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.addView(layoutThumb(c, id, id.equals(layout)), Ui.lpu(120, 72));
                cell.addView(Ui.text(c, ls[i][1], 12, id.equals(layout) ? Ui.TEXT : Ui.SUB, id.equals(layout)), top(5));
                cell.setOnClickListener(v -> {
                    Prefs.put(Prefs.PRAYER_LAYOUT, id);
                    Panels.refresh();
                });
                LinearLayout.LayoutParams cl = Ui.lp(Ui.WRAP, Ui.WRAP);
                cl.rightMargin = Ui.u(16);
                lay.addView(cell, cl);
            }
            b.addView(lay);
            if ("split".equals(layout) && p != null && p.orig == null)
                b.addView(Ui.text(c, "This prayer has no original-language text, so it uses Stacked.", 11, Ui.SUB, false), top(4));
            int show = Prefs.num(Prefs.PRAYER_SHOW, 7);
            b.addView(Panels.row("Show", showSeg(c, "Original script", 1, show), showSeg(c, "Transliteration", 2, show), showSeg(c, "Translation", 4, show)), top(6));
            b.addView(Ui.button(c, "Preview", true, SettingsPanels::preview), top(8));
            return Panels.panel("Travel prayer", b, 640, 444);
        });
    }

    private static TextView showSeg(Context c, String label, int bit, int cur) {
        return Ui.seg(c, label, (cur & bit) != 0, () -> {
            int v = cur ^ bit;
            if (v == 0) v = 4;
            Prefs.put(Prefs.PRAYER_SHOW, v);
            Panels.refresh();
        });
    }

    /** Little drawing of each splash layout. */
    private static View layoutThumb(Context c, String id, boolean on) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(on ? Ui.outline(0xFF05080C, Ui.ACCENT, 2, 8) : Ui.outline(0xFF05080C, Ui.LINE, 1, 8));
        Ui.pad(f, 6);
        if ("split".equals(id)) {
            LinearLayout r = Ui.row(c);
            View g = new View(c);
            g.setBackground(Ui.round(0xFF0F3D26, 4));
            r.addView(g, Ui.weight(1, true));
            LinearLayout lines = Ui.col(c);
            Ui.pad(lines, 6, 8, 2, 8);
            for (int i = 0; i < 3; i++) lines.addView(bar(c, i == 2 ? 26 : 40, Ui.MUTED), top(i == 0 ? 0 : 5));
            r.addView(lines, Ui.weight(1, true));
            f.addView(r, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        } else {
            FrameLayout inner = new FrameLayout(c);
            if ("card".equals(id)) {
                f.setBackground(on ? Ui.outline(0xFF1A2230, Ui.ACCENT, 2, 8) : Ui.outline(0xFF1A2230, Ui.LINE, 1, 8));
                inner.setBackground(Ui.round(Ui.CARD3, 5));
            }
            LinearLayout lines = Ui.col(c);
            lines.setGravity(Gravity.CENTER_HORIZONTAL);
            lines.addView(bar(c, 70, Ui.TEXT2));
            lines.addView(bar(c, 50, Ui.MUTED), top(6));
            lines.addView(bar(c, 60, Ui.MUTED), top(4));
            inner.addView(lines, Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH);
            if ("card".equals(id)) il.setMargins(Ui.u(12), Ui.u(8), Ui.u(12), Ui.u(8));
            f.addView(inner, il);
        }
        return f;
    }

    private static View bar(Context c, float w, int color) {
        View v = new View(c);
        v.setBackground(Ui.round(color, 2));
        v.setLayoutParams(Ui.lpu(w, 4));
        return v;
    }

    static void prayerPicker() {
        Panels.push(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            String cur = Prefs.str(Prefs.PRAYER_ID, null);
            for (String faith : Prayers.FAITHS) {
                if (!Faith.allows(faith)) continue; // only the selected faith add-on
                b.addView(Panels.section(faith));
                for (Prayers.P p : Prayers.ALL) {
                    if (!p.faith.equals(faith)) continue;
                    LinearLayout r = Ui.row(c);
                    r.setBackground(Ui.round(p.id.equals(cur) ? 0xFF1A2740 : Ui.CARD2, 12));
                    Ui.pad(r, 14, 10, 14, 10);
                    LinearLayout t = Ui.col(c);
                    t.addView(Ui.line(c, p.name, 15, Ui.TEXT, true));
                    t.addView(Ui.line(c, p.english, 12, Ui.SUB, false), top(3));
                    r.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                    if (p.id.equals(cur)) r.addView(Ui.icon(c, R.drawable.ic_check, Ui.ACCENT), Ui.lpu(20, 20));
                    r.setOnClickListener(v -> {
                        Prefs.put(Prefs.PRAYER_ID, p.id);
                        Prefs.put(Prefs.SPLASH_TYPE, "prayer");
                        Panels.back();
                        Panels.refresh();
                    });
                    LinearLayout.LayoutParams rl = Ui.lp(Ui.MATCH, Ui.WRAP);
                    rl.bottomMargin = Ui.u(8);
                    b.addView(r, rl);
                }
            }
            b.addView(Panels.section("My own"));
            final String[] own = {Prefs.str(Prefs.CUSTOM_TEXT, "")};
            b.addView(Widgets.field("Type your own prayer", own[0], false, t -> own[0] = t), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Ui.button(c, "Use my own", "own".equals(cur), () -> {
                if (own[0].isEmpty()) return;
                Prefs.put(Prefs.CUSTOM_TEXT, own[0]);
                Prefs.put(Prefs.PRAYER_ID, "own");
                Panels.back();
                Panels.refresh();
            }), top(8));
            return Panels.panel("Choose a prayer", b, 620, 444);
        });
    }

    // ------------------------------------------------------------------ calendar

    static void calendar() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.section("Second calendar"));
            List<View> chips = new ArrayList<>();
            for (String[] a : Cal.ALTS) if (Faith.calendarAllowed(a[0])) chips.add(segStr(c, Prefs.CAL_ALT, a[0], a[1], Cal.alt()));
            b.addView(AppPanels.grid(c, chips, 4, 8));
            if (Cal.alt().startsWith("islamic")) {
                int adj = Prefs.num(Prefs.CAL_ADJ, 0);
                b.addView(Panels.row("Hijri adjustment", segNum(c, Prefs.CAL_ADJ, -1, "−1 day", adj), segNum(c, Prefs.CAL_ADJ, 0, "0", adj),
                        segNum(c, Prefs.CAL_ADJ, 1, "+1 day", adj)), top(6));
                b.addView(Ui.text(c, "Use this if your local moon sighting differs by a day. Today: " + Cal.format(Cal.alt(), System.currentTimeMillis(), "d MMMM y"), 11, Ui.SUB, false));
            }
            b.addView(Panels.section("Special days"));
            List<String> sp = Cal.specials();
            String[][] kinds = {{"islamic", "Islamic"}, {"jewish", "Jewish"}, {"chinese", "Chinese"}};
            LinearLayout sr = Ui.row(c);
            for (String[] k : kinds) {
                sr.addView(Ui.seg(c, k[1], sp.contains(k[0]), () -> {
                    List<String> l = new ArrayList<>(Cal.specials());
                    if (!l.remove(k[0])) l.add(k[0]);
                    Prefs.putList(Prefs.CAL_SPECIAL, l);
                    Panels.refresh();
                }));
                sr.addView(Ui.gap(c, 8, 1));
            }
            b.addView(sr);
            b.addView(Panels.section("Google calendars to include"));
            List<String[]> cals = Cal.calendars(c);
            List<String> ids = Prefs.list(Prefs.CAL_IDS);
            if (!Cal.canReadCalendar(c)) {
                b.addView(Ui.button(c, "Allow calendar access", false, () -> {
                    if (MainActivity.I != null) MainActivity.I.requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 4);
                }));
            } else if (cals.isEmpty()) b.addView(Ui.text(c, "No calendars on this box. Add your Google account in Android settings.", 12, Ui.SUB, false));
            for (String[] k : cals) {
                boolean on = ids.isEmpty() || ids.contains(k[0]);
                b.addView(Panels.switchRow(R.drawable.ic_calendar, Ui.ACCENT, k[1], k[2], on, v -> {
                    List<String> l = new ArrayList<>(Prefs.list(Prefs.CAL_IDS));
                    if (l.isEmpty()) for (String[] x : cals) l.add(x[0]);
                    if (v) {
                        if (!l.contains(k[0])) l.add(k[0]);
                    } else l.remove(k[0]);
                    if (l.size() == cals.size()) l.clear();
                    Prefs.putList(Prefs.CAL_IDS, l);
                }));
            }
            boolean sun = Prefs.flag(Prefs.CAL_WEEK_SUN, false);
            b.addView(Panels.row("Week starts", Ui.seg(c, "Monday", !sun, () -> {
                Prefs.put(Prefs.CAL_WEEK_SUN, false);
                Panels.refresh();
            }), Ui.seg(c, "Sunday", sun, () -> {
                Prefs.put(Prefs.CAL_WEEK_SUN, true);
                Panels.refresh();
            })), top(6));
            return Panels.panel("Calendar", b, 660, 444);
        });
    }

    // ------------------------------------------------------------------ units

    static void units() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            boolean cel = Prefs.flag(Prefs.CELSIUS, true), h24 = Prefs.flag(Prefs.H24, true), mph = Prefs.flag("mph", false);
            b.addView(Panels.row("Temperature", flagSeg(c, Prefs.CELSIUS, true, "°C", cel), flagSeg(c, Prefs.CELSIUS, false, "°F", cel)));
            b.addView(Panels.row("Clock", flagSeg(c, Prefs.H24, true, "24 hour", h24), flagSeg(c, Prefs.H24, false, "12 hour", h24)));
            b.addView(Panels.row("Speed", flagSeg(c, "mph", false, "km/h", mph), flagSeg(c, "mph", true, "mph", mph)));
            b.addView(Ui.text(c, "The Rail Bar clock follows Android's 24-hour setting.", 12, Ui.SUB, false), top(6));
            b.addView(Panels.item("Date and time settings", null, () -> Status.open(c, Settings.ACTION_DATE_SETTINGS)), top(6));
            return Panels.panel("Units and clock", b, 520, 330);
        });
    }

    static TextView flagSeg(Context c, String key, boolean value, String label, boolean cur) {
        return Ui.seg(c, label, value == cur, () -> {
            Prefs.put(key, value);
            Panels.refresh();
            if (MainActivity.I != null) {
                MainActivity.I.rail.updateWeather();
                MainActivity.I.tiles.refresh();
            }
        });
    }

    // ------------------------------------------------------------------ prayer times

    static void prayerTimes() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.col(c);
            b.addView(Panels.section("Calculation method"));
            List<View> chips = new ArrayList<>();
            int m = Prefs.num(Prefs.PRAY_METHOD, 0);
            for (int i = 0; i < Prayer.METHODS.length; i++) chips.add(segNum(c, Prefs.PRAY_METHOD, i, Prayer.METHODS[i], m));
            b.addView(AppPanels.grid(c, chips, 3, 8));
            int asr = Prefs.num(Prefs.PRAY_ASR, 2);
            b.addView(Panels.row("Asr", segNum(c, Prefs.PRAY_ASR, 1, "Shafi'i, Maliki, Hanbali", asr), segNum(c, Prefs.PRAY_ASR, 2, "Hanafi", asr)), top(8));
            b.addView(Panels.section("Adjust by minutes"));
            JSONArray adj = Prefs.arr(Prefs.PRAY_ADJ);
            double[] t = Prayer.times(0);
            for (int i = 0; i < 6; i++) {
                final int k = i;
                int v = adj.optInt(i, 0);
                LinearLayout r = Ui.row(c);
                Ui.pad(r, 0, 4, 0, 4);
                r.addView(Ui.line(c, Prayer.NAMES[i], 15, Ui.TEXT, false), Ui.lpu(120, Ui.WRAP));
                r.addView(Ui.line(c, t == null ? "--:--" : InfoPanels.clock(t[i]), 14, Ui.SUB, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                r.addView(Ui.seg(c, "−", false, () -> setAdj(k, v - 1)));
                TextView val = Ui.text(c, (v > 0 ? "+" : "") + v, 14, Ui.TEXT, true);
                val.setGravity(Gravity.CENTER);
                r.addView(val, Ui.lpu(44, Ui.WRAP));
                r.addView(Ui.seg(c, "+", false, () -> setAdj(k, v + 1)));
                b.addView(r);
            }
            b.addView(Panels.section("At salaah times"));
            b.addView(Panels.switchRow(0, 0, "Pause playing audio", "Music or radio pauses at each salaah time", Prefs.flag(Salah.PAUSE, true), v -> Prefs.put(Salah.PAUSE, v)));
            b.addView(Panels.switchRow(0, 0, "Announce it", "Says \"It's time for Salaah\" and the prayer's name", Prefs.flag(Salah.ANNOUNCE, true), v -> Prefs.put(Salah.ANNOUNCE, v)));
            b.addView(Ui.button(c, "Test", false, () -> Salah.fire("Dhuhr")), top(8));
            return Panels.panel("Prayer times", b, 600, 444);
        });
    }

    private static void setAdj(int i, int v) {
        JSONArray a = Prefs.arr(Prefs.PRAY_ADJ);
        JSONArray out = new JSONArray();
        for (int k = 0; k < 6; k++) out.put(k == i ? v : a.optInt(k, 0));
        Prefs.put(Prefs.PRAY_ADJ, out);
        Panels.refresh();
    }

    // ------------------------------------------------------------------ wallpaper colour

    static void colourPicker() {
        open(() -> {
            Context c = c();
            LinearLayout b = Ui.row(c);
            int cur = Prefs.num(Prefs.WALL_COLOR, 0xFF1A2433);
            float[] hsv = new float[3];
            Color.colorToHSV(cur, hsv);
            View preview = new View(c);
            preview.setBackground(Ui.round(cur, 16));
            Wheel wheel = new Wheel(c, hsv, col -> {
                preview.setBackground(Ui.round(col, 16));
                Prefs.put(Prefs.WALL_COLOR, col);
                Prefs.put(Prefs.WALL, "color");
                if (MainActivity.I != null) MainActivity.I.applyWallpaper();
            });
            b.addView(wheel, Ui.lpu(240, 240));
            LinearLayout right = Ui.col(c);
            right.addView(preview, Ui.lpu(Ui.MATCH, 90));
            right.addView(Ui.text(c, "Brightness", 13, Ui.SUB, false), top(18));
            SeekBar sb = new SeekBar(c);
            sb.setMax(100);
            sb.setProgress(Math.round(hsv[2] * 100));
            sb.getProgressDrawable().setColorFilter(Ui.ACCENT, PorterDuff.Mode.SRC_IN);
            sb.getThumb().setColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int v, boolean user) {
                    if (user) wheel.setValue(v / 100f);
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            right.addView(sb, top(6));
            right.addView(Ui.text(c, "Darker colours are easier on the eyes at night.", 12, Ui.SUB, false), top(14));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            rl.leftMargin = Ui.u(28);
            b.addView(right, rl);
            return Panels.panel("Wallpaper colour", b, 600, 380);
        });
    }

    /** Hue/saturation colour wheel. */
    static final class Wheel extends View {
        private final float[] hsv;
        private final Ui.Cb<Integer> cb;
        private Bitmap bm;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Wheel(Context c, float[] hsv, Ui.Cb<Integer> cb) {
            super(c);
            this.hsv = hsv;
            this.cb = cb;
        }

        void setValue(float v) {
            hsv[2] = Math.max(0.02f, v);
            bm = null;
            invalidate();
            cb.on(Color.HSVToColor(hsv));
        }

        @Override
        protected void onDraw(Canvas cv) {
            int s = Math.min(getWidth(), getHeight());
            float r = s / 2f;
            if (bm == null || bm.getWidth() != s) {
                bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                Canvas bc = new Canvas(bm);
                int[] hues = new int[13];
                for (int i = 0; i < 13; i++) hues[i] = Color.HSVToColor(new float[]{(i * 30) % 360, 1, hsv[2]});
                Shader sweep = new SweepGradient(r, r, hues, null);
                int grey = Color.HSVToColor(new float[]{0, 0, hsv[2]});
                Shader radial = new RadialGradient(r, r, r, grey, grey & 0x00FFFFFF, Shader.TileMode.CLAMP);
                Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
                wp.setShader(new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER));
                bc.drawCircle(r, r, r, wp);
            }
            cv.drawBitmap(bm, 0, 0, null);
            double a = Math.toRadians(hsv[0]);
            float x = r + (float) Math.cos(a) * hsv[1] * r, y = r + (float) Math.sin(a) * hsv[1] * r;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.uf(3));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(x, y, Ui.uf(10), p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float r = Math.min(getWidth(), getHeight()) / 2f;
            float dx = e.getX() - r, dy = e.getY() - r;
            hsv[0] = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
            hsv[1] = (float) Math.min(1, Math.hypot(dx, dy) / r);
            invalidate();
            getParent().requestDisallowInterceptTouchEvent(true);
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_MOVE) cb.on(Color.HSVToColor(hsv));
            return true;
        }
    }

    private static void eqStyle(String st) {
        Prefs.put("eq_style", st);
        if (NowPlaying.I != null) NowPlaying.I.update();
        Panels.refresh();
    }

    /** Modern (solid cards) or Glass (see-through cards). */
    private static void theme(boolean glass) {
        Prefs.put(Themes.KEY, glass);
        // glass over plain black shows nothing: bring in the Road wallpaper
        if (glass && "black".equals(Prefs.str(Prefs.WALL, "road"))) Prefs.put(Prefs.WALL, "road");
        if (MainActivity.I != null) MainActivity.I.applyWallpaper(); // also redraws the cards
        else Themes.refresh();
        Panels.refresh();
    }
}
