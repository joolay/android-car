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
        return Sheets.ctx();
    }

    static void open(Sheets.Page p) {
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }

    // ------------------------------------------------------------------ main

    static void main() {
        Sheets.showFull(SettingsPanels::mainPage); // full screen; its pages open on top of it
    }

    private static int tab; // the settings category showing (kept while Settings is reopened)

    /** A settings row for a tab: coloured icon, name with its current value underneath, and a switch or ›. */
    private static View bigRow(Context c, int icon, int color, String title, String value, Runnable open, String key, boolean def) {
        LinearLayout r = U.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(U.round(U.CARD2, 12));
        U.pad(r, 10, 6, 10, 6);
        r.setMinimumHeight(U.u(58));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(U.oval(color));
        ic.addView(U.icon(c, icon, U.TEXT), U.flpu(17, 17, Gravity.CENTER));
        r.addView(ic, U.lpu(32, 32));
        LinearLayout tx = U.col(c);
        tx.addView(U.line(c, title, 15, U.TEXT, false));
        if (value != null && !value.isEmpty()) tx.addView(U.line(c, value, 12, U.SUB, false), top(2));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.setMargins(U.u(10), 0, U.u(6), 0);
        r.addView(tx, tl);
        if (key != null) r.addView(new U.Toggle(c, Store.flag(key, def), on -> {
            Store.put(key, on);
            if (Store.SPLASH_ON.equals(key) || Alerts.GLOBAL.equals(key)) Sheets.refresh();
        }));
        else r.addView(U.text(c, "›", 17, U.MUTED, false));
        if (open != null) r.setOnClickListener(v -> open.run());
        U.press(r);
        return r;
    }

    /** A compact settings row: name, current value, and either a switch (on/off settings) or ›. */
    private static View textRow(Context c, String title, String value, Runnable open, String key, boolean def) {
        LinearLayout r = U.row(c);
        r.setBackground(U.round(U.CARD2, 10));
        U.pad(r, 10, 0, 8, 0);
        r.setMinimumHeight(U.u(38));
        r.addView(U.line(c, title, 13, U.TEXT, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
        if (value != null && !value.isEmpty()) {
            TextView v = U.line(c, value, 11, U.SUB, false);
            v.setMaxWidth(U.u(80));
            LinearLayout.LayoutParams vl = U.lp(U.WRAP, U.WRAP);
            vl.rightMargin = U.u(6);
            r.addView(v, vl);
        }
        if (key != null) r.addView(new U.Toggle(c, Store.flag(key, def), on -> {
            Store.put(key, on);
            if (Store.SPLASH_ON.equals(key) || Alerts.GLOBAL.equals(key)) Sheets.refresh();
        }));
        else r.addView(U.text(c, "›", 15, U.MUTED, false));
        if (open != null) r.setOnClickListener(v -> open.run()); // tap the name for the full page
        U.press(r);
        return r;
    }

    /** One setting: icon, name and current value, all the same size. */
    private static View tile(Context c, int icon, int color, String title, String value, Runnable click) {
        LinearLayout r = U.row(c);
        r.setBackground(U.round(U.CARD2, 14));
        U.pad(r, 8, 5, 6, 5);
        r.setMinimumHeight(U.u(40)); // compact
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(U.oval(color));
        ic.addView(U.icon(c, icon, U.TEXT), U.flpu(12, 12, Gravity.CENTER));
        r.addView(ic, U.lpu(22, 22));
        LinearLayout t = U.col(c);
        t.addView(U.line(c, title, 12, U.TEXT, true));
        if (value != null && !value.isEmpty()) t.addView(U.line(c, value, 10, U.SUB, false), top(0));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.leftMargin = U.u(6);
        r.addView(t, tl);
        r.addView(U.text(c, "›", 13, U.MUTED, false));
        r.setOnClickListener(v -> click.run());
        U.press(r);
        return r;
    }

    private static View mainPage() {
        Context c = c();
        LinearLayout card = U.col(c);
        card.setBackground(U.round(U.CARD, 18));
        U.pad(card, 20, 14, 20, 14);
        card.setLayoutParams(Sheets.centered(784, 464));
        LinearLayout head = U.row(c);
        head.addView(U.line(c, "Settings", 20, U.TEXT, true), new LinearLayout.LayoutParams(0, U.WRAP, 1));
        View x = U.icon(c, R.drawable.ic_x, U.SUB);
        x.setOnClickListener(v -> Sheets.close());
        head.addView(x, U.lpu(26, 26));
        card.addView(head);

        int railOn = 0;
        for (String[] e : Rail.order()) if ("1".equals(e[1])) railOn++;
        String w = Store.str(Store.WALL, "black");
        String as = Store.str(Store.ASSIST, "gemini"), ext = Store.str(Store.ASSIST_PKG, null);
        final int BLUE = 0xFF1F4E9A, GREEN = 0xFF1F7A46, AMBER = 0xFF8A5A10, PURPLE = 0xFF5A3A9A, GREY = U.CARD3;
        Object[][][] groups = {
                {{"Home screen"},
                        {R.drawable.ic_apps, PURPLE, "Left Widgets", "Top: " + TopLeft.list().size() + " · Bottom: " + Widgets.name(TopLeft.bottom()), (Runnable) () -> TopLeft.chooser(false)},
                        {R.drawable.ic_photo, PURPLE, "Wallpaper", "image".equals(w) ? "Image" : "color".equals(w) ? "Colour" : "Black", (Runnable) SettingsPanels::wallpaper},
                        {R.drawable.ic_drag, PURPLE, "Rail Bar", railOn + " of " + Rail.IDS.length + " shown", (Runnable) SettingsPanels::railOrder},
                        {R.drawable.ic_apps, PURPLE, "Dock", Dock.icons() + " icons" + (Dock.dialler() ? " · dialler" : ""), (Runnable) SettingsPanels::dock},
                        {R.drawable.ic_hide, PURPLE, "Hidden from All apps", Apps.hidden().isEmpty() ? "None" : Apps.hidden().size() + " hidden", (Runnable) () -> hiddenApps(false)},
                        {R.drawable.ic_hide, PURPLE, "Hidden from Dock", Apps.dockHidden().isEmpty() ? "None" : Apps.dockHidden().size() + " hidden", (Runnable) () -> hiddenApps(true)},
                        {R.drawable.ic_sun, PURPLE, "Splash screen", null, (Runnable) SettingsPanels::splash, Store.SPLASH_ON, false},
                        {R.drawable.ic_bolt, PURPLE, "Animations", null, null, "anim_on", true}},
                {{"Driving"},
                        {R.drawable.ic_car, GREEN, "Driving timer", U.dur(Rest.limitMin() * 60000L), (Runnable) Rest::setup, Rest.ON, true},
                        {R.drawable.ic_pin, GREEN, "Car location", "Home Assistant", (Runnable) CarHA::settings, CarHA.ON, false},
                        {R.drawable.ic_bell, PURPLE, "Alerts expand", null, (Runnable) Alerts::settings, Alerts.GLOBAL, false}},
                {{"News ticker"},
                        {R.drawable.ic_bell, GREEN, "News and traffic", tickerLabel(), (Runnable) SettingsPanels::news},
                        {R.drawable.ic_bell, GREEN, "Show the ticker", Store.flag("ticker_on", true) ? "On · tap to hide" : "Off · tap to show", (Runnable) () -> {
                            Store.put("ticker_on", !Store.flag("ticker_on", true));
                            Sheets.close();
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
                        {R.drawable.ic_calendar, GREY, "Units and clock", (Store.flag(Store.CELSIUS, true) ? "°C" : "°F") + " · " + (Store.flag(Store.H24, true) ? "24 h" : "12 h"), (Runnable) SettingsPanels::units},
                        {R.drawable.ic_calendar, GREY, "Calendar", Cal.altLabel(), (Runnable) SettingsPanels::calendar},
                        {R.drawable.ic_moon, GREY, "Faith add-ons", Faith.label(), (Runnable) Faith::picker},
                        Faith.islam() ? new Object[]{R.drawable.ic_kaaba, GREY, "Prayer times", Prayer.METHODS[Store.num(Store.PRAY_METHOD, 0)], (Runnable) SettingsPanels::prayerTimes} : null},
                {{"System"},
                        {R.drawable.ic_check, GREY, "Set-up checklist", null, (Runnable) SettingsPanels::setupChecklist},
                        {R.drawable.ic_bolt, GREY, "Startup optimiser", null, (Runnable) Optimiser::show},
                        {R.drawable.ic_call, GREY, "Call helper", BackButtonService.enabled(c) ? "On" : "Off", (Runnable) SettingsPanels::floatingBack},
                        {R.drawable.ic_gear, GREY, "Android settings", null, (Runnable) SettingsPanels::androidSettings},
                        {R.drawable.ic_shield, GREY, "About", About.version(c), (Runnable) About::show}}};

        // categories as tabs down the left; the chosen one's settings fill the rest, two big rows across
        LinearLayout body = U.row(c);
        body.setGravity(Gravity.TOP);
        LinearLayout tabs = U.col(c);
        if (tab >= groups.length) tab = 0;
        for (int gi = 0; gi < groups.length; gi++) {
            final int g = gi;
            Object[] first = null;
            for (int k = 1; k < groups[gi].length && first == null; k++) first = groups[gi][k];
            LinearLayout t = U.row(c);
            t.setGravity(Gravity.CENTER_VERTICAL);
            U.pad(t, 10, 0, 8, 0);
            t.setMinimumHeight(U.u(44));
            boolean on = gi == tab;
            t.setBackground(on ? U.round(U.CARD2, 12) : null);
            if (first != null) {
                FrameLayout ic = new FrameLayout(c);
                ic.setBackground(U.oval((Integer) first[1]));
                ic.addView(U.icon(c, (Integer) first[0], U.TEXT), U.flpu(15, 15, Gravity.CENTER));
                t.addView(ic, U.lpu(26, 26));
            }
            TextView tn = U.line(c, (String) groups[gi][0][0], 13, on ? U.TEXT : U.SUB, on);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            tl.leftMargin = U.u(10);
            t.addView(tn, tl);
            if (on) {
                View bar = new View(c);
                bar.setBackground(U.round(U.ACCENT, 2));
                t.addView(bar, U.lpu(3, 20));
            }
            t.setOnClickListener(v -> {
                tab = g;
                Sheets.refresh();
            });
            U.press(t);
            tabs.addView(t, top(gi == 0 ? 0 : 4));
        }
        android.widget.ScrollView ts = new android.widget.ScrollView(c);
        ts.setVerticalScrollBarEnabled(false);
        ts.addView(tabs);
        body.addView(ts, U.lpu(196, U.MATCH));
        View div = new View(c);
        div.setBackgroundColor(U.LINE);
        LinearLayout.LayoutParams dl = U.lpu(1, U.MATCH);
        dl.setMargins(U.u(12), 0, U.u(14), 0);
        body.addView(div, dl);

        LinearLayout list = U.col(c);
        Object[][] g = groups[tab];
        TextView h = U.line(c, (String) g[0][0], 17, U.TEXT, true);
        list.addView(h);
        List<View> cells = new ArrayList<>();
        for (int k = 1; k < g.length; k++) {
            Object[] it = g[k];
            if (it == null) continue;
            cells.add(bigRow(c, (Integer) it[0], (Integer) it[1], (String) it[2], (String) it[3], (Runnable) it[4],
                    it.length > 5 ? (String) it[5] : null, it.length > 6 && (Boolean) it[6]));
        }
        list.addView(AppPanels.grid(c, cells, 2, 8), top(10));
        TextView hint = U.text(c, "More settings: hold the map for map and navigation, hold Now playing for music. Hold a tile for its own settings.", 12, U.SUB, false);
        list.addView(hint, top(16));
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        body.addView(sv, new LinearLayout.LayoutParams(0, U.MATCH, 1));
        LinearLayout.LayoutParams bl = U.weight(1, false);
        bl.topMargin = U.u(12);
        card.addView(body, bl);
        return card;
    }

    /** Assistant for the voice button. */
    static void assistant() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
        // assistant
        String as = Store.str(Store.ASSIST, "gemini");
        String ext = Store.str(Store.ASSIST_PKG, null);
        b.addView(Sheets.row("Assistant",
                U.seg(c, "Gemini", "gemini".equals(as), () -> {
                    Store.put(Store.ASSIST, "gemini");
                    Sheets.refresh();
                }),
                U.seg(c, "external".equals(as) && ext != null ? "External · " + Apps.label(ext) + " ›" : "External ›", "external".equals(as), SettingsPanels::assistantPicker)));

            b.addView(U.text(c, "Tip: hold the voice button on the Rail Bar to pick any installed assistant for one go.", 12, U.SUB, false), top(10));
            String al = Store.str("assist_log", "");
            b.addView(Sheets.section("Last time the voice button was pressed"));
            b.addView(U.text(c, al.isEmpty() ? "Not pressed yet." : al, 11, U.SUB, false));
            return Sheets.panel("Assistant", b, 560, 340);
        });
    }

    /** Wallpaper: black, a colour or an image. */
    static void wallpaper() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
        // wallpaper
        String w = Store.str(Store.WALL, "black");
        View swatch = new View(c);
        swatch.setBackground(U.oval(Store.num(Store.WALL_COLOR, 0xFF1A2433)));
        LinearLayout colour = U.row(c);
        colour.setBackground(U.round("color".equals(w) ? U.ACCENT : U.CARD2, 16));
        U.pad(colour, 10, 6, 14, 6);
        colour.addView(swatch, U.lpu(18, 18));
        colour.addView(U.gap(c, 8, 1));
        colour.addView(U.text(c, "Colour", 14, U.TEXT, false));
        colour.setOnClickListener(v -> colourPicker());
        b.addView(Sheets.row("Wallpaper",
                U.seg(c, "Black", "black".equals(w), () -> {
                    Store.put(Store.WALL, "black");
                    if (MainActivity.I != null) MainActivity.I.applyWallpaper();
                    Sheets.refresh();
                }), colour,
                U.seg(c, "image".equals(w) ? "Image ✓" : "Image…", "image".equals(w), () -> {
                    if (MainActivity.I != null) MainActivity.I.pickWallpaperImage();
                })));

            return Sheets.panel("Wallpaper", b, 560, 240);
        });
    }

    private static LinearLayout.LayoutParams margin() {
        LinearLayout.LayoutParams lp = U.weight(1, true);
        lp.leftMargin = U.u(10);
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
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER);
        l.setBackground(U.round(on ? onColor : U.CARD2, 14));
        l.addView(U.icon(c, icon, U.TEXT), U.lpu(22, 22));
        TextView t = U.line(c, label, 12, U.TEXT, on);
        l.addView(t, top(5));
        l.setOnClickListener(v -> click.run());
        U.press(l);
        return l;
    }

    static String tickerLabel() {
        String m = Store.str(Store.TICKER_MODE, "alt");
        return "news".equals(m) ? "News only" : "traffic".equals(m) ? "Traffic only" : "Alternate";
    }

    static String connLabel() {
        int n = 0;
        if (Store.flag(Store.CONN_PHONE, false)) n++;
        if (Store.flag(Store.CONN_HOTSPOT, false)) n++;
        if (Store.flag(Store.CONN_TS, false)) n++;
        return n == 0 ? "Off" : n + " on";
    }

    static void about() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            String v = "";
            try {
                v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            b.addView(U.text(c, "Car Launcher " + v, 16, U.TEXT, true));
            b.addView(U.text(c, "Built for 800×480 car boxes. Map © OpenStreetMap contributors © CARTO. Weather by Open-Meteo. Lyrics by LRCLIB. Headlines from Google News.", 13, U.SUB, false), top(8));
            b.addView(U.text(c, "Make this your Home app: Android settings → Apps → Default apps → Home app.", 13, U.TEXT2, false), top(12));
            b.addView(U.button(c, "Default apps", false, () -> Status.open(c, Settings.ACTION_HOME_SETTINGS)), top(12));
            return Sheets.panel("About", b, 520, 320);
        });
    }

    // ------------------------------------------------------------------ assistant

    static void assistantPicker() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "The mic button opens this app. Claude and ChatGPT open their app; tap their voice button to talk.", 12, U.SUB, false));
            boolean any = false;
            String cur = Store.str(Store.ASSIST_PKG, null);
            for (String[] a : Actions.ASSISTANTS) {
                if (Actions.GEMINI.equals(a[0])) continue;
                Apps.App app = Apps.byPkg(a[0]);
                if (app == null) continue;
                any = true;
                b.addView(appRow(c, app, a[0].equals(cur), () -> {
                    Store.put(Store.ASSIST, "external");
                    Store.put(Store.ASSIST_PKG, a[0]);
                    Sheets.back();
                    Sheets.refresh();
                }));
            }
            if (!any) b.addView(U.text(c, "None of Claude, ChatGPT, Copilot, Alexa, Perplexity or Meta AI are installed.", 13, U.TEXT2, false), top(10));
            b.addView(Sheets.item("Other app…", null, () -> AppPanels.appPicker("Assistant app", app -> {
                Store.put(Store.ASSIST, "external");
                Store.put(Store.ASSIST_PKG, app.pkg);
                Sheets.close();
            })), top(4));
            return Sheets.panel("External assistant", b, 520, 400);
        });
    }

    static View appRow(Context c, Apps.App a, boolean selected, Runnable click) {
        LinearLayout r = U.row(c);
        r.setMinimumHeight(U.u(50));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(a.icon);
        r.addView(iv, U.lpu(32, 32));
        TextView t = U.line(c, a.label, 15, U.TEXT, selected);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.leftMargin = U.u(12);
        r.addView(t, tl);
        if (selected) r.addView(U.icon(c, R.drawable.ic_check, U.ACCENT), U.lpu(20, 20));
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
            LinearLayout b = U.col(c);
            b.addView(Sheets.section("Navigation app"));
            navRows(c, b);
            b.addView(Sheets.section("Map"));
            mapRows(c, b);
            NavBanner.rows(c, b);
            return Sheets.panel("Map and navigation", b, 660, 440);
        });
    }

    private static void navRows(Context c, LinearLayout b) {
        {
            String cur = Actions.navPkg();
            for (String p : Actions.NAV_APPS) {
                Apps.App a = Apps.byPkg(p);
                if (a == null) continue;
                b.addView(appRow(c, a, p.equals(cur), () -> {
                    Store.put(Store.NAV_PKG, p);
                    Sheets.refresh();
                }));
            }
            b.addView(Sheets.item("Other app…", null, () -> AppPanels.appPicker("Navigation app", a -> {
                Store.put(Store.NAV_PKG, a.pkg);
                Sheets.back();
                Sheets.refresh();
            })));
            b.addView(U.text(c, "Turn-by-turn on the map tile works with Google Maps and Waze.", 12, U.SUB, false), top(8));
        }
    }

    // ------------------------------------------------------------------ music

    static void music() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            LinearLayout app = U.row(c);
            U.pad(app, 0, 10, 0, 10);
            LinearLayout t = U.col(c);
            t.addView(U.text(c, "Default music app", 15, U.TEXT, false));
            t.addView(U.text(c, "Used by auto play, the Now playing card and its play button", 12, U.SUB, false), top(3));
            app.addView(t, new LinearLayout.LayoutParams(0, U.WRAP, 1));
            String p = Music.pkg();
            if (p != null && Apps.iconOf(p) != null) {
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(Apps.iconOf(p));
                app.addView(iv, U.lpu(28, 28));
                app.addView(U.gap(c, 8, 1));
            }
            app.addView(U.text(c, Music.label() + " ›", 14, U.ACCENT_SOFT, true));
            app.setOnClickListener(v -> musicAppPicker());
            b.addView(app);
            b.addView(Sheets.item("Bottom Left Widget", Widgets.name(TopLeft.bottom()) + " · change it", () -> TopLeft.chooser(true)));
            b.addView(U.hr(c));
            b.addView(Sheets.switchRow(0, 0, "Play music when the car starts", "Starts straight away, before the splash screen",
                    Store.flag(Store.MUSIC_AUTOPLAY, false), on -> Store.put(Store.MUSIC_AUTOPLAY, on)));
            b.addView(U.hr(c));
            String sw = Music.startWith();
            b.addView(Sheets.row("At start, play", U.seg(c, "My playlist", "playlist".equals(sw), () -> {
                Store.put("music_start", "playlist");
                Sheets.refresh();
            }), U.seg(c, "Whatever played last", "last".equals(sw), () -> {
                Store.put("music_start", "last");
                Sheets.refresh();
            })), top(8));
            b.addView(U.text(c, "My playlist", 15, U.TEXT, false), top(12));
            b.addView(U.text(c, "\"Liked Songs\", a playlist name, or a playlist link shared from the app. Also used if nothing is loaded.", 12, U.SUB, false), top(3));
            LinearLayout pr = U.row(c);
            final String[] pl = {Store.str(Store.MUSIC_PLAYLIST, "")};
            pr.addView(Widgets.field("e.g. Liked Songs", pl[0], false, v -> {
                pl[0] = v;
                Store.put(Store.MUSIC_PLAYLIST, v);
            }), new LinearLayout.LayoutParams(0, U.WRAP, 1));
            pr.addView(U.gap(c, 10, 1));
            pr.addView(U.button(c, "Test", false, () -> {
                Sheets.close();
                Music.playPlaylist(c, true);
            }));
            b.addView(pr, top(10));

            java.util.List<String> ml = Store.list("music_log");
            b.addView(Sheets.section("Last car start")); // always shown, so it's easy to find
            b.addView(U.text(c, ml.isEmpty() ? "Nothing logged yet: it fills in at the next car start." : String.join("\n", ml), 11, U.SUB, false));
            String eq = EqView.style();
            b.addView(Sheets.row("Music animation", U.seg(c, "Equaliser", "bars".equals(eq), () -> { Store.put("eq_style", "bars"); Sheets.refresh(); }),
                    U.seg(c, "Wave", "wave".equals(eq), () -> { Store.put("eq_style", "wave"); Sheets.refresh(); }),
                    U.seg(c, "Pulse", "pulse".equals(eq), () -> { Store.put("eq_style", "pulse"); Sheets.refresh(); })), top(12));
            return Sheets.panel("Music", b, 640, 432);
        });
    }

    static void musicAppPicker() {
        Sheets.push(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Music apps on this box are listed first", 12, U.SUB, false));
            String cur = Store.str(Store.MUSIC_PKG, null), now = Music.pkg();
            for (Apps.App a : Music.players(c)) {
                b.addView(appRow(c, a, !Music.LAST.equals(cur) && a.pkg.equals(now), () -> {
                    Store.put(Store.MUSIC_PKG, a.pkg);
                    Sheets.back();
                    Sheets.refresh();
                }));
            }
            b.addView(radio(c, "Last app that played", "Whatever played last time", Music.LAST.equals(cur), () -> {
                Store.put(Store.MUSIC_PKG, Music.LAST);
                Sheets.back();
                Sheets.refresh();
            }));
            b.addView(Sheets.item("Other app…", null, () -> AppPanels.appPicker("Default music app", a -> {
                Store.put(Store.MUSIC_PKG, a.pkg);
                Sheets.back();
                Sheets.back();
                Sheets.refresh();
            })));
            return Sheets.panel("Default music app", b, 560, 420);
        });
    }

    // ------------------------------------------------------------------ keyboard & Android settings

    // ------------------------------------------------------------------ set-up checklist (first run)

    static Sheets.Page setupPage;

    static boolean isDefaultHome(Context c) {
        android.content.pm.ResolveInfo r = c.getPackageManager().resolveActivity(
                new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && c.getPackageName().equals(r.activityInfo.packageName);
    }

    static void setupChecklist() {
        open(setupPage = () -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "A few Android permissions make everything work. Each opens the right Android screen; press Back to return here.", 13, U.SUB, false));
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
            b.addView(check(c, R.drawable.ic_home, U.CARD3, "Make Car Launcher your Home app", "So the Home button always comes back here",
                    home ? "Done" : "Choose", home, () -> Status.open(c, Settings.ACTION_HOME_SETTINGS)));
            b.addView(check(c, R.drawable.ic_apps, U.CARD3, "Usage access (optional)", "Shows the apps you really use in the dock",
                    usage ? "Done" : "Allow", usage, () -> Status.open(c, Settings.ACTION_USAGE_ACCESS_SETTINGS)));
            b.addView(check(c, R.drawable.ic_mic, U.CARD3, "Car keyboard (optional)", "Big keys and voice typing in every app",
                    kb ? "Done" : "Set up", kb, SettingsPanels::keyboard));
            boolean fb = BackButtonService.enabled(c);
            b.addView(check(c, R.drawable.ic_back, U.CARD3, "Call helper (optional)", "Shows and answers calls from the box's BT Phone app",
                    fb ? "Done" : "Set up", fb, SettingsPanels::floatingBack));
            boolean wg = Store.flag("widgets_seen", false);
            b.addView(check(c, R.drawable.ic_apps, U.CARD3, "Choose your tiles", "Pick what goes on your home screen",
                    wg ? "Done" : "Choose", wg, () -> {
                        Store.put("widgets_seen", true);
                        AppPanels.gallery(-1);
                    }));
            boolean fa = Store.flag("faith_seen", false);
            b.addView(check(c, R.drawable.ic_moon, U.CARD3, "Faith add-ons", "Prayer times, qibla, travel prayers: " + Faith.label(),
                    fa ? "Done" : "Choose", fa, Faith::picker));
            boolean opt = Store.flag("opt_seen", false);
            b.addView(check(c, R.drawable.ic_bolt, U.CARD3, "Startup optimiser (recommended)", "Find apps that clash with Car Launcher or slow the start-up",
                    opt ? "Done" : "Check", opt, Optimiser::show));
            b.addView(U.text(c, "Find this again any time in Settings → Android settings → Set-up checklist.", 12, U.SUB, false), top(10));
            return Sheets.panel("Welcome to Car Launcher", b, 640, 440);
        });
    }

    private static View check(Context c, int icon, int color, String title, String sub, String btn, boolean done, Runnable click) {
        LinearLayout r = U.row(c);
        r.setMinimumHeight(U.u(54));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(U.round(done ? U.DGREEN : color, 10));
        ic.addView(U.icon(c, done ? R.drawable.ic_check : icon, U.TEXT), U.flpu(20, 20, Gravity.CENTER));
        r.addView(ic, U.lpu(36, 36));
        LinearLayout t = U.col(c);
        t.addView(U.text(c, title, 15, U.TEXT, false));
        t.addView(U.text(c, sub, 12, U.SUB, false), top(3));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.setMargins(U.u(14), 0, U.u(12), 0);
        r.addView(t, tl);
        r.addView(U.button(c, btn, !done, () -> {
            Store.put("setup_return", System.currentTimeMillis()); // coming back reopens the checklist
            click.run();
        }));
        LinearLayout w = U.col(c);
        w.addView(U.hr(c));
        w.addView(r);
        return w;
    }

    static Sheets.Page floatPage;

    static void floatingBack() {
        open(floatPage = () -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Lets Car Launcher show incoming calls and press Answer, Decline and the keypad in the box's BT Phone app. It reads only BT Phone's own screens.", 13, U.SUB, false));
            boolean on = BackButtonService.enabled(c);
            if (!on) b.addView(step(c, 1, "Allow restricted settings", "Tap Turn on below first (Android will block it), then come back here: App info → ⋮ (top right) → Allow restricted settings",
                    "App info", false, () -> Apps.appInfo(c, c.getPackageName())), top(10));
            b.addView(step(c, on ? 1 : 2, "Turn on \"Car Launcher call helper\"", "In Android's Accessibility list",
                    on ? "Done" : "Turn on", on, () -> Status.open(c, Settings.ACTION_ACCESSIBILITY_SETTINGS)), on ? top(10) : U.lp(U.MATCH, U.WRAP));
            String st = BackButtonService.I != null ? "Running" : on ? "Switched on but not running: switch it off and on again in Accessibility" : "Off";
            b.addView(U.text(c, "Status: " + st, 13, BackButtonService.I != null ? U.GREEN : U.AMBER, true), top(8));
            return Sheets.panel("Call helper", b, 640, 432);
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
        Sheets.push(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            java.util.List<String> chosen = QuickActions.chosen();
            b.addView(U.text(c, "Hold the back button to open these in a circle around it. Tap to add or remove (3 to 8). Numbers show the order, starting at the top.",
                    12, U.SUB, false));
            java.util.List<View> cells = new java.util.ArrayList<>();
            for (Object[] a : QuickActions.ALL) {
                final String id = (String) a[0];
                int pos = chosen.indexOf(id);
                boolean on = pos >= 0;
                LinearLayout r = U.row(c);
                r.setBackground(on ? U.outline(0xFF1A2740, U.ACCENT, 2, 10) : U.round(U.CARD2, 10));
                U.pad(r, 10, 0, 10, 0);
                FrameLayout ic = new FrameLayout(c);
                ic.setBackground(U.oval(on ? U.ACCENT : U.CARD3));
                ic.addView(U.icon(c, (Integer) a[2], U.TEXT), U.flpu(16, 16, Gravity.CENTER));
                r.addView(ic, U.lpu(28, 28));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                tl.leftMargin = U.u(10);
                r.addView(U.line(c, (String) a[1], 13, U.TEXT, false), tl);
                if (on) r.addView(U.text(c, String.valueOf(pos + 1), 12, U.ACCENT_SOFT, true));
                r.setMinimumHeight(U.u(40));
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
                    Store.putList("fb_menu", l);
                    Sheets.refresh();
                });
                cells.add(r);
            }
            b.addView(AppPanels.grid(c, cells, 3, 8), top(10));
            return Sheets.panel("Hold menu", b, 700, 440);
        });
    }

    private static TextView sizeSeg(Context c, String v, String label, String cur) {
        return U.seg(c, label, v.equals(cur), () -> {
            Store.put("fb_size", v);
            BackButtonService.refresh();
            Sheets.refresh();
        });
    }

    static Sheets.Page keyboardPage;

    static void keyboard() {
        open(keyboardPage = () -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Use the big Car keyboard (with voice typing) in every app on the box, not just the launcher.", 13, U.SUB, false));
            boolean en = CarIME.enabled(c), def = CarIME.isDefault(c);
            boolean mic = c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            b.addView(step(c, 1, "Turn on Car keyboard", "In Android's keyboard list. Android shows a standard warning for any keyboard; tap OK.", en ? "Done" : "Turn on", en, () ->
                    Status.open(c, Settings.ACTION_INPUT_METHOD_SETTINGS)), top(10));
            b.addView(step(c, 2, "Make it the default", "Pick \"Car keyboard\" from the list Android shows", def ? "Done" : "Choose", def, () -> {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
                U.ui.postDelayed(Sheets::refresh, 4000);
            }));
            b.addView(step(c, 3, "Allow the microphone", "For the Voice button", mic ? "Done" : "Allow", mic, () -> {
                if (MainActivity.I != null) MainActivity.I.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 54);
            }));
            b.addView(U.text(c, "To switch back later, choose your old keyboard the same way.", 12, U.SUB, false), top(12));
            return Sheets.panel("Keyboard", b, 640, 432);
        });
    }

    private static View step(Context c, int n, String title, String sub, String btn, boolean done, Runnable click) {
        LinearLayout r = U.row(c);
        r.setMinimumHeight(U.u(64));
        TextView num = U.text(c, done ? "✓" : String.valueOf(n), 14, U.TEXT, true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(U.oval(done ? U.DGREEN : U.CARD3));
        r.addView(num, U.lpu(30, 30));
        LinearLayout t = U.col(c);
        t.addView(U.text(c, title, 15, U.TEXT, false));
        t.addView(U.text(c, sub, 12, U.SUB, false), top(3));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.setMargins(U.u(14), 0, U.u(12), 0);
        r.addView(t, tl);
        r.addView(U.button(c, btn, !done, click));
        LinearLayout w = U.col(c);
        w.addView(U.hr(c));
        w.addView(r);
        return w;
    }

    /** True once if we've just come back from an Android page opened by the checklist (within 10 min). */
    static boolean backFromChecklist() {
        long at = Store.lng("setup_return", 0);
        if (at == 0) return false;
        Store.put("setup_return", 0L);
        return System.currentTimeMillis() - at < 600000;
    }

    /** Show the checklist again (or refresh it if it's already up). */
    static void reopenChecklist() {
        if (Sheets.open() && Sheets.top() == setupPage) Sheets.refresh();
        else setupChecklist();
    }

    static void androidSettings() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(Sheets.item("Set-up checklist", null, SettingsPanels::setupChecklist));
            b.addView(U.hr(c));
            b.addView(Sheets.item("Call helper", BackButtonService.enabled(c) ? "On" : "Off", SettingsPanels::floatingBack));
            b.addView(U.hr(c));
            b.addView(Sheets.item("Startup optimiser", null, Optimiser::show));
            b.addView(U.hr(c));
            String[][] items = {{"All Android settings", Settings.ACTION_SETTINGS}, {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS},
                    {"Wi-Fi", Settings.ACTION_WIFI_SETTINGS}, {"Sound", Settings.ACTION_SOUND_SETTINGS}, {"Display", Settings.ACTION_DISPLAY_SETTINGS},
                    {"Home app", Settings.ACTION_HOME_SETTINGS}, {"Apps", Settings.ACTION_APPLICATION_SETTINGS}};
            for (String[] it : items) {
                b.addView(Sheets.item(it[0], null, () -> {
                    Status.open(c, it[1]);
                    Sheets.close();
                }));
                b.addView(U.hr(c));
            }
            return Sheets.panel("Android settings", b, 520, 432);
        });
    }

    // ------------------------------------------------------------------ map

    private static void mapRows(Context c, LinearLayout b) {
        {
            String prov = MapPanel.provider();
            b.addView(radio(c, "Standard", "OpenStreetMap. Free, no account. Dark style.", "osm".equals(prov), () -> setMap("osm")));
            b.addView(radio(c, "OpenFreeMap", "Free vector map with sharp labels that stay upright as it turns.", "ofm".equals(prov), () -> setMap("ofm")));
            if ("ofm".equals(prov)) {
                String st = Store.str("ofm_style", "liberty");
                if ("dark".equals(st)) st = "liberty";
                b.addView(Sheets.row("Day style", U.seg(c, "Liberty", "liberty".equals(st), () -> { Store.put("ofm_style", "liberty"); setMap("ofm"); }),
                        U.seg(c, "Positron", "positron".equals(st), () -> { Store.put("ofm_style", "positron"); setMap("ofm"); }),
                        U.seg(c, "Bright", "bright".equals(st), () -> { Store.put("ofm_style", "bright"); setMap("ofm"); })), top(4));
            }
            b.addView(radio(c, "Offline map", "Download the area around you (or use a .pmtiles file). Works with no internet.", "pmtiles".equals(prov), () -> {
                if (Store.str(OfflineMap.URI, "").isEmpty()) MapDownload.panel();
                else setMap("pmtiles");
            }));
            {
                LinearLayout r = U.row(c);
                String nm = Store.str(OfflineMap.NAME, "");
                r.addView(U.line(c, nm.isEmpty() ? "No map file chosen" : nm, 13, nm.isEmpty() ? U.AMBER : U.TEXT2, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                r.addView(U.button(c, nm.isEmpty() ? "Choose file" : "Change file", false, () -> {
                    if (MainActivity.I != null) OfflineMap.pick(MainActivity.I);
                }));
                r.addView(U.gap(c, 8, 1));
                r.addView(U.button(c, "Download map here", true, MapDownload::panel));
                b.addView(r, top(4));
                b.addView(U.text(c, "Download map here fetches just the area around the car (choose how far and how detailed). Or choose a .pmtiles file you copied onto the box. Street-name lettering needs internet the first time; the map itself works offline.",
                        12, U.SUB, false), top(6));
            }
            b.addView(radio(c, "Google Maps", "Needs your own Google Maps API key. Live traffic.", "google".equals(prov), () -> setMap("google")));
            String col = MapPanel.colours();
            b.addView(Sheets.row("Colours", U.seg(c, "Auto (day / night)", "auto".equals(col), () -> { Store.put("map_colours", "auto"); setMap(prov); }),
                    U.seg(c, "Light", "light".equals(col), () -> { Store.put("map_colours", "light"); setMap(prov); }),
                    U.seg(c, "Dark", "dark".equals(col), () -> { Store.put("map_colours", "dark"); setMap(prov); })), top(10));
            b.addView(U.text(c, "Auto: light from sunrise, dark from sunset where the car is (switches by itself).", 12, U.SUB, false), top(4));
            if ("google".equals(prov)) {
                b.addView(Sheets.section("Google Maps API key"));
                final String[] key = {Store.str(Store.MAP_KEY, "")};
                LinearLayout r = U.row(c);
                r.addView(Widgets.field("Paste your key (starts with AIza…)", key[0], true, t -> key[0] = t), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                r.addView(U.gap(c, 10, 1));
                r.addView(U.button(c, "Save", true, () -> {
                    Store.put(Store.MAP_KEY, key[0]);
                    reloadMap();
                    Sheets.refresh();
                }));
                b.addView(r, U.lp(U.MATCH, U.WRAP));
                b.addView(U.text(c, Store.str(Store.MAP_KEY, "").isEmpty() ? "No key yet, so the map is using Standard." : "Key saved. If Google refuses it, the map goes back to Standard and tells you.",
                        12, Store.str(Store.MAP_KEY, "").isEmpty() ? U.AMBER : U.GREEN, false), top(8));
                b.addView(U.text(c, "Get a key at console.cloud.google.com: create a project, turn on \"Maps JavaScript API\", then Credentials → Create API key. Restrict the key to the Maps JavaScript API only. Google asks for a card, but light use like this should stay within its free monthly allowance.",
                        12, U.SUB, false), top(10));
            }
            String nw = Store.str(NavCard.WHERE, "tiles");
            b.addView(Sheets.row("Turn-by-turn", U.seg(c, "In the tile area", "tiles".equals(nw), () -> { Store.put(NavCard.WHERE, "tiles"); NavCard.update(); reloadMap(); Sheets.refresh(); }),
                    U.seg(c, "In the map panel", "map".equals(nw), () -> { Store.put(NavCard.WHERE, "map"); NavCard.update(); reloadMap(); Sheets.refresh(); }),
        U.seg(c, "Swap: big map", "swap".equals(nw), () -> { Store.put(NavCard.WHERE, "swap"); NavCard.update(); reloadMap(); Sheets.refresh(); })), top(8));
            boolean hu = MapPanel.headingUp();
            b.addView(Sheets.row("Map faces", U.seg(c, "Direction of travel", hu, () -> { Store.put("map_heading_up", true); if (MainActivity.I != null) MainActivity.I.map.applyHeading(); Sheets.refresh(); }),
                    U.seg(c, "North up", !hu, () -> { Store.put("map_heading_up", false); if (MainActivity.I != null) MainActivity.I.map.applyHeading(); Sheets.refresh(); })), top(8));
            b.addView(Sheets.switchRow(0, 0, "Map is the home view", "The big map with Go to beside it; the All apps button slides your tiles in",
                    Store.flag("map_start_big", true), on -> Store.put("map_start_big", on)), top(8));
            b.addView(Sheets.item("Top Left Widget", TopLeft.list().size() + " widgets · swipe it to go through them", TopLeft::chooser), top(8));
            b.addView(U.text(c, "Tap the map to make it fill the tiles; ✕ on the big map makes it small again. The Top Left Widget shows above the Bottom Left one: swipe it for the next widget, hold it to choose them.", 12, U.SUB, false), top(6));
        }
    }

    private static void remini() {
        if (MainActivity.I != null && MainActivity.I.map.expanded()) {
            MainActivity.I.map.expand(false);
            MainActivity.I.map.expand(true);
        }
    }

    private static void setMap(String p) {
        Store.put(Store.MAP_PROVIDER, p);
        reloadMap();
        Sheets.refresh();
    }

    private static void reloadMap() {
        if (MainActivity.I != null) MainActivity.I.map.reload();
    }

    // ------------------------------------------------------------------ right bar order

    static void railOrder() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            List<String[]> order = Rail.order();
            for (int i = 0; i < order.size(); i++) {
                final int idx = i;
                String[] e = order.get(i);
                LinearLayout r = U.row(c);
                U.pad(r, 0, 4, 0, 4);
                TextView up = U.text(c, "▲", 15, i == 0 ? U.MUTED : U.TEXT2, false);
                U.pad(up, 8, 6, 8, 6);
                up.setOnClickListener(v -> {
                    if (idx == 0) return;
                    order.add(idx - 1, order.remove(idx));
                    Rail.saveOrder(order);
                    rebuildRail();
                    Sheets.refresh();
                });
                r.addView(up);
                TextView dn = U.text(c, "▼", 15, i == order.size() - 1 ? U.MUTED : U.TEXT2, false);
                U.pad(dn, 8, 6, 12, 6);
                dn.setOnClickListener(v -> {
                    if (idx == order.size() - 1) return;
                    order.add(idx + 1, order.remove(idx));
                    Rail.saveOrder(order);
                    rebuildRail();
                    Sheets.refresh();
                });
                r.addView(dn);
                r.addView(U.line(c, Rail.NAMES[Rail.indexOf(e[0])], 15, U.TEXT, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                r.addView(new U.Toggle(c, "1".equals(e[1]), on -> {
                    e[1] = on ? "1" : "0";
                    Rail.saveOrder(order);
                    rebuildRail();
                }));
                b.addView(r);
            }
            b.addView(U.text(c, "Switch an icon off to remove it from the Rail Bar; the others spread out to fill the space. The clock stays at the top and the mic at the bottom.", 12, U.SUB, false), top(8));
            int cs = Store.num("card_secs", 15);
            b.addView(Sheets.row("Pop-ups close after", segNum(c, "card_secs", 5, "5 s", cs), segNum(c, "card_secs", 8, "8 s", cs),
                    segNum(c, "card_secs", 15, "15 s", cs), segNum(c, "card_secs", 30, "30 s", cs), segNum(c, "card_secs", 0, "Never", cs)), top(10));
            return Sheets.panel("Rail Bar", b, 480, 440);
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
            LinearLayout b = U.col(c);
            java.util.Set<String> set = dock ? Apps.dockHidden() : Apps.hidden();
            b.addView(U.text(c, dock ? "These never show in the dock (pinned or recent) but stay in All apps." : "These don't show in All apps or the dock.", 13, U.SUB, false));
            if (set.isEmpty()) b.addView(U.text(c, "None hidden.", 14, U.SUB, false), top(8));
            for (String k : new ArrayList<>(set)) {
                Apps.App a = Apps.byKey.get(k);
                if (a == null) continue;
                LinearLayout r = U.row(c);
                r.setMinimumHeight(U.u(50));
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(a.icon);
                r.addView(iv, U.lpu(32, 32));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                tl.leftMargin = U.u(12);
                r.addView(U.line(c, a.label, 15, U.TEXT, false), tl);
                r.addView(U.seg(c, "Unhide", false, () -> {
                    if (dock) Apps.setDockHidden(k, false);
                    else Apps.setHidden(k, false);
                    if (MainActivity.I != null) MainActivity.I.dock.render();
                    Sheets.refresh();
                }));
                b.addView(r);
            }
            b.addView(U.button(c, "+ Hide an app", false, () -> AppPanels.appPicker(dock ? "Hide from Dock" : "Hide from All apps", ap -> {
                if (dock) Apps.setDockHidden(ap.key, true);
                else Apps.setHidden(ap.key, true);
                if (MainActivity.I != null) MainActivity.I.dock.render();
                Sheets.back();
            })), top(10));
            return Sheets.panel(dock ? "Hidden from Dock" : "Hidden from All apps", b, 520, 420);
        });
    }

    /** Dock: the dialler button and how many icons (5–7, not counting All apps). */
    static void dock() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(Sheets.switchRow(0, 0, "Dialler button", "The green phone button at the start of the dock", Dock.dialler(), v -> {
                Store.put("dock_dialer", v);
                if (MainActivity.I != null) MainActivity.I.dock.render();
            }));
            // also here (not only in News and traffic): hiding the ticker makes the dock bigger
            b.addView(Sheets.switchRow(0, 0, "Show the news ticker", "Off: the map and tiles get its space", Store.flag("ticker_on", true), on -> {
                Store.put("ticker_on", on);
                if (MainActivity.I != null) MainActivity.I.recreate();
            }), top(4));
            int n = Dock.icons();
            List<View> segs = new ArrayList<>();
            for (int k = 5; k <= 8; k++) {
                final int v = k;
                segs.add(U.seg(c, String.valueOf(k), k == n, () -> {
                    Store.put("dock_icons", v);
                    if (MainActivity.I != null) MainActivity.I.dock.render();
                    Sheets.refresh();
                }));
            }
            b.addView(Sheets.row("Icons", segs.toArray(new View[0])), top(8));
            b.addView(U.text(c, "Not counting All apps. Pinned apps come first, then the ones you used last.", 12, U.SUB, false), top(4));
            b.addView(Sheets.item("Hidden from Dock", Apps.dockHidden().size() + " hidden", () -> hiddenApps(true)), top(8));
            return Sheets.panel("Dock", b, 520, 360);
        });
    }

    // ------------------------------------------------------------------ news & traffic

    static void news() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(Sheets.switchRow(0, 0, "Show the news ticker", "Off: the map and tiles get its space (restarts the home screen)", Store.flag("ticker_on", true), on -> {
                Store.put("ticker_on", on);
                if (MainActivity.I != null) MainActivity.I.recreate();
            }));
            String bp = Ticker.browser();
            b.addView(Sheets.item("Open stories in", bp != null ? Apps.label(bp) : "Android's default browser", Ticker::browserPicker), top(8));
            String m = Store.str(Store.TICKER_MODE, "alt");
            b.addView(Sheets.row("Show", segStr(c, Store.TICKER_MODE, "alt", "Alternate", m), segStr(c, Store.TICKER_MODE, "news", "News only", m),
                    segStr(c, Store.TICKER_MODE, "traffic", "Traffic only", m)));
            int every = Store.num(Store.TICKER_EVERY, 60);
            if ("alt".equals(m)) b.addView(Sheets.row("Switch every", segNum(c, Store.TICKER_EVERY, 30, "30 s", every),
                    segNum(c, Store.TICKER_EVERY, 60, "1 min", every), segNum(c, Store.TICKER_EVERY, 120, "2 min", every), segNum(c, Store.TICKER_EVERY, 300, "5 min", every)));
            int back = Store.num(Store.TICKER_BACK, 60);
            b.addView(Sheets.row("After a tap, go back", segNum(c, Store.TICKER_BACK, 30, "30 s", back), segNum(c, Store.TICKER_BACK, 60, "1 min", back),
                    segNum(c, Store.TICKER_BACK, 300, "5 min", back), segNum(c, Store.TICKER_BACK, 0, "Never", back)));
            b.addView(Sheets.switchRow(0, 0, "Scrolling headlines", "Off shows one headline at a time", Store.flag(Store.TICKER_SCROLL, true),
                    on -> Store.put(Store.TICKER_SCROLL, on)));
            b.addView(Sheets.section("News feed (RSS)"));
            b.addView(Widgets.field("Blank = Google News for " + (Loc.town != null ? Loc.town : "your area"), Store.str(Store.NEWS_URL, ""), false,
                    t -> Store.put(Store.NEWS_URL, t)), U.lp(U.MATCH, U.WRAP));
            b.addView(Sheets.section("Traffic feed (RSS)"));
            b.addView(Widgets.field("Blank = \"traffic\" news for your area", Store.str(Store.TRAFFIC_URL, ""), false,
                    t -> Store.put(Store.TRAFFIC_URL, t)), U.lp(U.MATCH, U.WRAP));
            b.addView(Sheets.section("TomTom key (optional, for live incidents)"));
            b.addView(Widgets.field("Free key from developer.tomtom.com", Store.str(Store.TOMTOM_KEY, ""), true,
                    t -> Store.put(Store.TOMTOM_KEY, t)), U.lp(U.MATCH, U.WRAP));
            b.addView(U.button(c, "Reload headlines now", false, () -> {
                if (MainActivity.I != null) MainActivity.I.ticker.fetch();
            }), top(14));
            b.addView(Sheets.switchRow(0, 0, "Show traffic when the car starts", "Opens the traffic list in the tile area for 15 seconds", Store.flag("traffic_start", true), v -> Store.put("traffic_start", v)), top(8));
            return Sheets.panel("News and traffic", b, 660, 440);
        });
    }

    static TextView segStr(Context c, String key, String value, String label, String cur) {
        return U.seg(c, label, value.equals(cur), () -> {
            Store.put(key, value);
            Sheets.refresh();
        });
    }

    static TextView segNum(Context c, String key, int value, String label, int cur) {
        return U.seg(c, label, value == cur, () -> {
            Store.put(key, value);
            Sheets.refresh();
        });
    }

    // ------------------------------------------------------------------ calls

    static void callLog() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            java.util.List<String> cl = Store.list("call_log");
            if (cl.isEmpty()) b.addView(U.text(c, "Nothing yet. Make or receive a call, then come back. If this stays empty, BT Phone isn't posting notifications at all.", 13, U.SUB, false));
            for (String s : cl) {
                TextView t = U.text(c, s, 12, U.TEXT2, false);
                t.setBackground(U.round(U.CARD2, 10));
                U.pad(t, 10, 8, 10, 8);
                b.addView(t, top(6));
            }
            b.addView(U.text(c, "Bluetooth permission (Nearby devices): " + (c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == android.content.pm.PackageManager.PERMISSION_GRANTED ? "allowed" : "not allowed")
                    + " · Notification access: " + (MediaListener.hasAccess(c) ? "on" : "off") + " · BT Phone app: " + (Actions.btDialler() == null ? "not found" : Apps.label(Actions.btDialler())), 12, U.SUB, false), top(12));
            return Sheets.panel("Last BT Phone notifications", b, 640, 430);
        });
    }

    static void calls() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            // first, so it's easy to find: what BT Phone has sent (helps when calls aren't picked up)
            b.addView(Sheets.item("Last BT Phone notifications", Store.list("call_log").size() + " recorded", SettingsPanels::callLog));
            b.addView(U.hr(c));
            b.addView(U.text(c, "Quick dial, Contact tiles and the assistant all call through this.", 12, U.SUB, false));
            String m = Store.str(Store.CALLS, "bt");
            String bt = Actions.btDialler();
            b.addView(radio(c, "Bluetooth dialler (default)", "Calls go through your paired phone · " + (bt == null ? "choose the app ›" : "app: " + Apps.label(bt) + " ›"),
                    "bt".equals(m), () -> {
                        Store.put(Store.CALLS, "bt");
                        if (Actions.btDialler() == null) AppPanels.btPhonePicker(a -> {
                            Store.put(Store.CALLS_BT_PKG, a.pkg); Actions.btReset();
                            Sheets.back();
                            Sheets.refresh();
                        });
                        else Sheets.refresh();
                    }), top(6));
            b.addView(radio(c, "Box dialler", "Calls go out over the SIM card in the car box", "box".equals(m), () -> {
                Store.put(Store.CALLS, "box");
                Sheets.refresh();
            }));
            b.addView(radio(c, "Through Car Connect", "Opens CarPlay / Android Auto to call from your phone", "connect".equals(m), () -> {
                Store.put(Store.CALLS, "connect");
                Sheets.refresh();
            }));
            if ("bt".equals(m)) {
                b.addView(Sheets.item("Bluetooth dialler app", bt == null ? "Choose" : Apps.label(bt), () -> AppPanels.btPhonePicker(a -> {
                    Store.put(Store.CALLS_BT_PKG, a.pkg); Actions.btReset();
                    Sheets.back();
                    Sheets.refresh();
                })), top(6));
                String fb = Store.str(Store.CALLS_FALLBACK, "ask");
                b.addView(Sheets.row("If Bluetooth isn't connected", segStr(c, Store.CALLS_FALLBACK, "ask", "Ask", fb),
                        segStr(c, Store.CALLS_FALLBACK, "box", "Use box dialler", fb), segStr(c, Store.CALLS_FALLBACK, "none", "Do nothing", fb)));
            }
            b.addView(Sheets.section("Call screen"), top(4));
            b.addView(Sheets.switchRow(R.drawable.ic_call, U.GREEN, "Car Launcher call screen", "Big full-screen Answer / Decline for BT Phone calls (uses notification access)",
                    Store.flag("call_screen", true), v -> Store.put("call_screen", v)));
            boolean dialer = Actions.isDefaultDialer(c);
            b.addView(Sheets.item("Make Car Launcher the box's Phone app", dialer ? "On" : "Off", () -> {
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
            b.addView(U.text(c, "Optional. If this box handles Bluetooth calls through Android, every call then uses Car Launcher's screen with mute, keypad and audio controls. If it doesn't, nothing changes and BT Phone keeps working.", 12, U.SUB, false), top(4));
            return Sheets.panel("Calls", b, 640, 430);
        });
    }

    static View radio(Context c, String title, String sub, boolean on, Runnable click) {
        LinearLayout r = U.row(c);
        U.pad(r, 0, 10, 0, 10);
        View dot = new View(c);
        dot.setBackground(on ? U.outline(U.ACCENT, U.TEXT, 3, 11) : U.outline(0, U.MUTED, 2, 11));
        r.addView(dot, U.lpu(22, 22));
        LinearLayout t = U.col(c);
        t.addView(U.text(c, title, 15, U.TEXT, on));
        t.addView(U.text(c, sub, 12, U.SUB, false), top(3));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        tl.leftMargin = U.u(14);
        r.addView(t, tl);
        r.setOnClickListener(v -> click.run());
        return r;
    }

    // ------------------------------------------------------------------ connections

    static void connections() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(Sheets.switchRow(R.drawable.ic_wifi, U.ACCENT, "Join my phone's hotspot", "Android asks once to allow it", Store.flag(Store.CONN_PHONE, false), on -> {
                Store.put(Store.CONN_PHONE, on);
                if (on) Actions.joinPhoneHotspot(c);
                Sheets.refresh();
            }));
            if (Store.flag(Store.CONN_PHONE, false)) {
                LinearLayout f = U.row(c);
                f.addView(Widgets.field("Hotspot name (SSID)", Store.str(Store.CONN_PHONE_SSID, ""), false, t -> Store.put(Store.CONN_PHONE_SSID, t)), U.weight(1, true));
                f.addView(U.gap(c, 10, 1));
                f.addView(Widgets.field("Password", Store.str(Store.CONN_PHONE_PASS, ""), true, t -> Store.put(Store.CONN_PHONE_PASS, t)), U.weight(1, true));
                b.addView(f, U.lp(U.MATCH, U.WRAP));
            }
            b.addView(Sheets.switchRow(R.drawable.ic_hot, U.ACCENT, "Turn on the box hotspot", "Opens hotspot settings if it's off (Android 13 limit)",
                    Store.flag(Store.CONN_HOTSPOT, false), on -> Store.put(Store.CONN_HOTSPOT, on)));
            b.addView(Sheets.switchRow(R.drawable.ic_vpnkey, U.GREEN, "Connect Tailscale at start",
                    Status.tailscaleInstalled() ? "Uses the Tailscale app on this box" : "Install Tailscale first", Store.flag(Store.CONN_TS, false), on -> {
                        Store.put(Store.CONN_TS, on);
                        if (on) Status.tailscale(true);
                        Sheets.refresh();
                    }));
            if (Store.flag(Store.CONN_TS, false))
                b.addView(Sheets.switchRow(R.drawable.ic_refresh, U.SUB, "Reconnect every minute if dropped", null, Store.flag(Store.CONN_TS_RETRY, true),
                        on -> Store.put(Store.CONN_TS_RETRY, on)));
            b.addView(Sheets.item("Home Assistant over Tailscale", HA.configured() ? "Set up" : "Not set up", () -> {
                JSONArray s = Tiles.specs();
                for (int i = 0; i < s.length(); i++)
                    if ("ha".equals(s.optJSONObject(i).optString("id"))) {
                        HA.setup(i);
                        return;
                    }
                Sheets.push(() -> Sheets.panel("Home Assistant", U.text(c(), "Add the Home Assistant tile first: long-press a tile → ⋯ → Replace with another tile.", 14, U.SUB, false), 480, 220));
            }));
            return Sheets.panel("Connections at start", b, 620, 440);
        });
    }

    // ------------------------------------------------------------------ splash screen

    static void splash() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(Sheets.switchRow(0, 0, "Show a splash screen", "When the car starts", Store.flag(Store.SPLASH_ON, false), on -> {
                Store.put(Store.SPLASH_ON, on);
                Sheets.refresh();
            }));
            String type = Store.str(Store.SPLASH_TYPE, "prayer");
            LinearLayout tiles = U.row(c);
            Prayers.P p = Prayers.get(Store.str(Store.PRAYER_ID, null));
            tiles.addView(typeTile(c, "Travel Prayer", p == null ? "Not chosen" : p.name, "prayer".equals(type), () -> {
                Store.put(Store.SPLASH_TYPE, "prayer");
                Sheets.refresh();
            }), U.weight(1, true));
            tiles.addView(U.gap(c, 12, 1));
            String ct = Store.str(Store.CUSTOM_TEXT, "");
            tiles.addView(typeTile(c, "Custom", ct.isEmpty() ? "Your own text" : ct, "custom".equals(type), () -> {
                Store.put(Store.SPLASH_TYPE, "custom");
                Sheets.refresh();
            }), U.weight(1, true));
            b.addView(tiles, U.lpu(U.MATCH, 76));
            int secs = Store.num(Store.SPLASH_SECS, 10);
            b.addView(Sheets.row("Show for", segNum(c, Store.SPLASH_SECS, 5, "5 s", secs), segNum(c, Store.SPLASH_SECS, 10, "10 s", secs),
                    segNum(c, Store.SPLASH_SECS, 15, "15 s", secs), segNum(c, Store.SPLASH_SECS, 30, "30 s", secs), segNum(c, Store.SPLASH_SECS, 0, "Until tapped", secs)), top(8));
            String when = Store.str(Store.SPLASH_WHEN, "start");
            b.addView(Sheets.row("When", segStr(c, Store.SPLASH_WHEN, "start", "Every start", when), segStr(c, Store.SPLASH_WHEN, "day", "Once a day", when),
                    segStr(c, Store.SPLASH_WHEN, "nav", "When navigation starts", when)));
            String mode = Splash.mode();
            b.addView(Sheets.row("Show as", segStr(c, "splash_mode", "screen", "Screen", mode), segStr(c, "splash_mode", "both", "Screen + voice", mode),
                    segStr(c, "splash_mode", "voice", "Voice only", mode)), top(4));
            if (!"screen".equals(mode) && !"custom".equals(type)) {
                String lang = Store.str("splash_voice_lang", "orig");
                b.addView(Sheets.row("Recite in", segStr(c, "splash_voice_lang", "orig", "Original language", lang),
                        segStr(c, "splash_voice_lang", "en", "English", lang)), top(4));
            }
            if (!"screen".equals(mode)) {
                String src = Store.str("splash_audio", "builtin");
                boolean has = !Store.str("splash_audio_uri", "").isEmpty();
                b.addView(Sheets.row("Recording", segStr(c, "splash_audio", "builtin", "Built-in voice", src),
                        U.seg(c, "own".equals(src) && has ? "My recording ✓" : "My recording…", "own".equals(src), () -> {
                            if (MainActivity.I != null) MainActivity.I.pickAudio();
                        })), top(4));
            }
            if ("custom".equals(type)) {
                b.addView(Sheets.section("Custom text"));
                b.addView(Widgets.field("Main text", ct, false, t -> Store.put(Store.CUSTOM_TEXT, t)), U.lp(U.MATCH, U.WRAP));
                b.addView(Widgets.field("Small text underneath (optional)", Store.str(Store.CUSTOM_SUB, ""), false, t -> Store.put(Store.CUSTOM_SUB, t)), top(8));
            } else {
                b.addView(Sheets.item("Set up Travel Prayer", null, SettingsPanels::travelPrayer), top(4));
            }
            b.addView(U.button(c, "Preview", false, () -> {
                if (!"screen".equals(Splash.mode())) Splash.recite(c);
                if (!"voice".equals(Splash.mode())) preview();
            }), top(10));
            return Sheets.panel("Splash screen", b, 680, 444);
        });
    }

    static void preview() {
        if (MainActivity.I == null) return;
        // come back to the same setup page when the preview ends
        final java.util.List<Sheets.Page> pages = Sheets.saveStack();
        Sheets.close();
        Splash.show(MainActivity.I, () -> Sheets.restoreStack(pages));
    }

    private static View typeTile(Context c, String title, String sub, boolean on, Runnable click) {
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(on ? U.outline(0xFF1A2740, U.ACCENT, 2, 14) : U.round(U.CARD2, 14));
        U.pad(l, 16, 10, 16, 10);
        l.addView(U.line(c, title, 15, U.TEXT, true));
        l.addView(U.line(c, sub, 12, U.SUB, false), top(4));
        l.setOnClickListener(v -> click.run());
        return l;
    }

    // ------------------------------------------------------------------ travel prayer

    static void travelPrayer() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            Prayers.P p = Prayers.get(Store.str(Store.PRAYER_ID, null));
            b.addView(Sheets.section("Prayer"));
            if (p == null) {
                LinearLayout empty = U.row(c);
                empty.setBackground(U.dashed(U.MUTED, 14));
                U.pad(empty, 16, 14, 16, 14);
                empty.addView(U.text(c, "No prayer chosen", 15, U.SUB, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                empty.addView(U.text(c, "Choose ›", 14, U.ACCENT_SOFT, true));
                empty.setOnClickListener(v -> prayerPicker());
                b.addView(empty, U.lp(U.MATCH, U.WRAP));
            } else {
                LinearLayout sel = U.row(c);
                sel.setBackground(U.round(U.CARD2, 14));
                U.pad(sel, 16, 12, 16, 12);
                LinearLayout t = U.col(c);
                t.addView(U.line(c, p.name, 15, U.TEXT, true));
                t.addView(U.line(c, p.faith + " · " + p.source, 12, U.SUB, false), top(3));
                sel.addView(t, new LinearLayout.LayoutParams(0, U.WRAP, 1));
                sel.addView(U.text(c, "Change ›", 14, U.ACCENT_SOFT, true));
                sel.setOnClickListener(v -> prayerPicker());
                b.addView(sel, U.lp(U.MATCH, U.WRAP));
            }
            b.addView(Sheets.section("Layout"));
            String layout = Store.str(Store.PRAYER_LAYOUT, "stacked");
            LinearLayout lay = U.row(c);
            String[][] ls = {{"stacked", "Stacked"}, {"split", "Split"}, {"card", "Card"}};
            for (int i = 0; i < 3; i++) {
                final String id = ls[i][0];
                LinearLayout cell = U.col(c);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.addView(layoutThumb(c, id, id.equals(layout)), U.lpu(120, 72));
                cell.addView(U.text(c, ls[i][1], 12, id.equals(layout) ? U.TEXT : U.SUB, id.equals(layout)), top(5));
                cell.setOnClickListener(v -> {
                    Store.put(Store.PRAYER_LAYOUT, id);
                    Sheets.refresh();
                });
                LinearLayout.LayoutParams cl = U.lp(U.WRAP, U.WRAP);
                cl.rightMargin = U.u(16);
                lay.addView(cell, cl);
            }
            b.addView(lay);
            if ("split".equals(layout) && p != null && p.orig == null)
                b.addView(U.text(c, "This prayer has no original-language text, so it uses Stacked.", 11, U.SUB, false), top(4));
            int show = Store.num(Store.PRAYER_SHOW, 7);
            b.addView(Sheets.row("Show", showSeg(c, "Original script", 1, show), showSeg(c, "Transliteration", 2, show), showSeg(c, "Translation", 4, show)), top(6));
            b.addView(U.button(c, "Preview", true, SettingsPanels::preview), top(8));
            return Sheets.panel("Travel prayer", b, 640, 444);
        });
    }

    private static TextView showSeg(Context c, String label, int bit, int cur) {
        return U.seg(c, label, (cur & bit) != 0, () -> {
            int v = cur ^ bit;
            if (v == 0) v = 4;
            Store.put(Store.PRAYER_SHOW, v);
            Sheets.refresh();
        });
    }

    /** Little drawing of each splash layout. */
    private static View layoutThumb(Context c, String id, boolean on) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(on ? U.outline(0xFF05080C, U.ACCENT, 2, 8) : U.outline(0xFF05080C, U.LINE, 1, 8));
        U.pad(f, 6);
        if ("split".equals(id)) {
            LinearLayout r = U.row(c);
            View g = new View(c);
            g.setBackground(U.round(0xFF0F3D26, 4));
            r.addView(g, U.weight(1, true));
            LinearLayout lines = U.col(c);
            U.pad(lines, 6, 8, 2, 8);
            for (int i = 0; i < 3; i++) lines.addView(bar(c, i == 2 ? 26 : 40, U.MUTED), top(i == 0 ? 0 : 5));
            r.addView(lines, U.weight(1, true));
            f.addView(r, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        } else {
            FrameLayout inner = new FrameLayout(c);
            if ("card".equals(id)) {
                f.setBackground(on ? U.outline(0xFF1A2230, U.ACCENT, 2, 8) : U.outline(0xFF1A2230, U.LINE, 1, 8));
                inner.setBackground(U.round(U.CARD3, 5));
            }
            LinearLayout lines = U.col(c);
            lines.setGravity(Gravity.CENTER_HORIZONTAL);
            lines.addView(bar(c, 70, U.TEXT2));
            lines.addView(bar(c, 50, U.MUTED), top(6));
            lines.addView(bar(c, 60, U.MUTED), top(4));
            inner.addView(lines, U.flp(U.WRAP, U.WRAP, Gravity.CENTER));
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(U.MATCH, U.MATCH);
            if ("card".equals(id)) il.setMargins(U.u(12), U.u(8), U.u(12), U.u(8));
            f.addView(inner, il);
        }
        return f;
    }

    private static View bar(Context c, float w, int color) {
        View v = new View(c);
        v.setBackground(U.round(color, 2));
        v.setLayoutParams(U.lpu(w, 4));
        return v;
    }

    static void prayerPicker() {
        Sheets.push(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            String cur = Store.str(Store.PRAYER_ID, null);
            for (String faith : Prayers.FAITHS) {
                if (!Faith.allows(faith)) continue; // only the selected faith add-on
                b.addView(Sheets.section(faith));
                for (Prayers.P p : Prayers.ALL) {
                    if (!p.faith.equals(faith)) continue;
                    LinearLayout r = U.row(c);
                    r.setBackground(U.round(p.id.equals(cur) ? 0xFF1A2740 : U.CARD2, 12));
                    U.pad(r, 14, 10, 14, 10);
                    LinearLayout t = U.col(c);
                    t.addView(U.line(c, p.name, 15, U.TEXT, true));
                    t.addView(U.line(c, p.english, 12, U.SUB, false), top(3));
                    r.addView(t, new LinearLayout.LayoutParams(0, U.WRAP, 1));
                    if (p.id.equals(cur)) r.addView(U.icon(c, R.drawable.ic_check, U.ACCENT), U.lpu(20, 20));
                    r.setOnClickListener(v -> {
                        Store.put(Store.PRAYER_ID, p.id);
                        Store.put(Store.SPLASH_TYPE, "prayer");
                        Sheets.back();
                        Sheets.refresh();
                    });
                    LinearLayout.LayoutParams rl = U.lp(U.MATCH, U.WRAP);
                    rl.bottomMargin = U.u(8);
                    b.addView(r, rl);
                }
            }
            b.addView(Sheets.section("My own"));
            final String[] own = {Store.str(Store.CUSTOM_TEXT, "")};
            b.addView(Widgets.field("Type your own prayer", own[0], false, t -> own[0] = t), U.lp(U.MATCH, U.WRAP));
            b.addView(U.button(c, "Use my own", "own".equals(cur), () -> {
                if (own[0].isEmpty()) return;
                Store.put(Store.CUSTOM_TEXT, own[0]);
                Store.put(Store.PRAYER_ID, "own");
                Sheets.back();
                Sheets.refresh();
            }), top(8));
            return Sheets.panel("Choose a prayer", b, 620, 444);
        });
    }

    // ------------------------------------------------------------------ calendar

    static void calendar() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            b.addView(Sheets.section("Second calendar"));
            List<View> chips = new ArrayList<>();
            for (String[] a : Cal.ALTS) if (Faith.calendarAllowed(a[0])) chips.add(segStr(c, Store.CAL_ALT, a[0], a[1], Cal.alt()));
            b.addView(AppPanels.grid(c, chips, 4, 8));
            if (Cal.alt().startsWith("islamic")) {
                int adj = Store.num(Store.CAL_ADJ, 0);
                b.addView(Sheets.row("Hijri adjustment", segNum(c, Store.CAL_ADJ, -1, "−1 day", adj), segNum(c, Store.CAL_ADJ, 0, "0", adj),
                        segNum(c, Store.CAL_ADJ, 1, "+1 day", adj)), top(6));
                b.addView(U.text(c, "Use this if your local moon sighting differs by a day. Today: " + Cal.format(Cal.alt(), System.currentTimeMillis(), "d MMMM y"), 11, U.SUB, false));
            }
            b.addView(Sheets.section("Special days"));
            List<String> sp = Cal.specials();
            String[][] kinds = {{"islamic", "Islamic"}, {"jewish", "Jewish"}, {"chinese", "Chinese"}};
            LinearLayout sr = U.row(c);
            for (String[] k : kinds) {
                sr.addView(U.seg(c, k[1], sp.contains(k[0]), () -> {
                    List<String> l = new ArrayList<>(Cal.specials());
                    if (!l.remove(k[0])) l.add(k[0]);
                    Store.putList(Store.CAL_SPECIAL, l);
                    Sheets.refresh();
                }));
                sr.addView(U.gap(c, 8, 1));
            }
            b.addView(sr);
            b.addView(Sheets.section("Google calendars to include"));
            List<String[]> cals = Cal.calendars(c);
            List<String> ids = Store.list(Store.CAL_IDS);
            if (!Cal.canReadCalendar(c)) {
                b.addView(U.button(c, "Allow calendar access", false, () -> {
                    if (MainActivity.I != null) MainActivity.I.requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 4);
                }));
            } else if (cals.isEmpty()) b.addView(U.text(c, "No calendars on this box. Add your Google account in Android settings.", 12, U.SUB, false));
            for (String[] k : cals) {
                boolean on = ids.isEmpty() || ids.contains(k[0]);
                b.addView(Sheets.switchRow(R.drawable.ic_calendar, U.ACCENT, k[1], k[2], on, v -> {
                    List<String> l = new ArrayList<>(Store.list(Store.CAL_IDS));
                    if (l.isEmpty()) for (String[] x : cals) l.add(x[0]);
                    if (v) {
                        if (!l.contains(k[0])) l.add(k[0]);
                    } else l.remove(k[0]);
                    if (l.size() == cals.size()) l.clear();
                    Store.putList(Store.CAL_IDS, l);
                }));
            }
            boolean sun = Store.flag(Store.CAL_WEEK_SUN, false);
            b.addView(Sheets.row("Week starts", U.seg(c, "Monday", !sun, () -> {
                Store.put(Store.CAL_WEEK_SUN, false);
                Sheets.refresh();
            }), U.seg(c, "Sunday", sun, () -> {
                Store.put(Store.CAL_WEEK_SUN, true);
                Sheets.refresh();
            })), top(6));
            return Sheets.panel("Calendar", b, 660, 444);
        });
    }

    // ------------------------------------------------------------------ units

    static void units() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            boolean cel = Store.flag(Store.CELSIUS, true), h24 = Store.flag(Store.H24, true), mph = Store.flag("mph", false);
            b.addView(Sheets.row("Temperature", flagSeg(c, Store.CELSIUS, true, "°C", cel), flagSeg(c, Store.CELSIUS, false, "°F", cel)));
            b.addView(Sheets.row("Clock", flagSeg(c, Store.H24, true, "24 hour", h24), flagSeg(c, Store.H24, false, "12 hour", h24)));
            b.addView(Sheets.row("Speed", flagSeg(c, "mph", false, "km/h", mph), flagSeg(c, "mph", true, "mph", mph)));
            b.addView(U.text(c, "The Rail Bar clock follows Android's 24-hour setting.", 12, U.SUB, false), top(6));
            b.addView(Sheets.item("Date and time settings", null, () -> Status.open(c, Settings.ACTION_DATE_SETTINGS)), top(6));
            return Sheets.panel("Units and clock", b, 520, 330);
        });
    }

    static TextView flagSeg(Context c, String key, boolean value, String label, boolean cur) {
        return U.seg(c, label, value == cur, () -> {
            Store.put(key, value);
            Sheets.refresh();
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
            LinearLayout b = U.col(c);
            b.addView(Sheets.section("Calculation method"));
            List<View> chips = new ArrayList<>();
            int m = Store.num(Store.PRAY_METHOD, 0);
            for (int i = 0; i < Prayer.METHODS.length; i++) chips.add(segNum(c, Store.PRAY_METHOD, i, Prayer.METHODS[i], m));
            b.addView(AppPanels.grid(c, chips, 3, 8));
            int asr = Store.num(Store.PRAY_ASR, 2);
            b.addView(Sheets.row("Asr", segNum(c, Store.PRAY_ASR, 1, "Shafi'i, Maliki, Hanbali", asr), segNum(c, Store.PRAY_ASR, 2, "Hanafi", asr)), top(8));
            b.addView(Sheets.section("Adjust by minutes"));
            JSONArray adj = Store.arr(Store.PRAY_ADJ);
            double[] t = Prayer.times(0);
            for (int i = 0; i < 6; i++) {
                final int k = i;
                int v = adj.optInt(i, 0);
                LinearLayout r = U.row(c);
                U.pad(r, 0, 4, 0, 4);
                r.addView(U.line(c, Prayer.NAMES[i], 15, U.TEXT, false), U.lpu(120, U.WRAP));
                r.addView(U.line(c, t == null ? "--:--" : InfoPanels.clock(t[i]), 14, U.SUB, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                r.addView(U.seg(c, "−", false, () -> setAdj(k, v - 1)));
                TextView val = U.text(c, (v > 0 ? "+" : "") + v, 14, U.TEXT, true);
                val.setGravity(Gravity.CENTER);
                r.addView(val, U.lpu(44, U.WRAP));
                r.addView(U.seg(c, "+", false, () -> setAdj(k, v + 1)));
                b.addView(r);
            }
            b.addView(Sheets.section("At salaah times"));
            b.addView(Sheets.switchRow(0, 0, "Pause playing audio", "Music or radio pauses at each salaah time", Store.flag(Salah.PAUSE, true), v -> Store.put(Salah.PAUSE, v)));
            b.addView(Sheets.switchRow(0, 0, "Announce it", "Says \"It's time for Salaah\" and the prayer's name", Store.flag(Salah.ANNOUNCE, true), v -> Store.put(Salah.ANNOUNCE, v)));
            b.addView(U.button(c, "Test", false, () -> Salah.fire("Dhuhr")), top(8));
            return Sheets.panel("Prayer times", b, 600, 444);
        });
    }

    private static void setAdj(int i, int v) {
        JSONArray a = Store.arr(Store.PRAY_ADJ);
        JSONArray out = new JSONArray();
        for (int k = 0; k < 6; k++) out.put(k == i ? v : a.optInt(k, 0));
        Store.put(Store.PRAY_ADJ, out);
        Sheets.refresh();
    }

    // ------------------------------------------------------------------ wallpaper colour

    static void colourPicker() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.row(c);
            int cur = Store.num(Store.WALL_COLOR, 0xFF1A2433);
            float[] hsv = new float[3];
            Color.colorToHSV(cur, hsv);
            View preview = new View(c);
            preview.setBackground(U.round(cur, 16));
            Wheel wheel = new Wheel(c, hsv, col -> {
                preview.setBackground(U.round(col, 16));
                Store.put(Store.WALL_COLOR, col);
                Store.put(Store.WALL, "color");
                if (MainActivity.I != null) MainActivity.I.applyWallpaper();
            });
            b.addView(wheel, U.lpu(240, 240));
            LinearLayout right = U.col(c);
            right.addView(preview, U.lpu(U.MATCH, 90));
            right.addView(U.text(c, "Brightness", 13, U.SUB, false), top(18));
            SeekBar sb = new SeekBar(c);
            sb.setMax(100);
            sb.setProgress(Math.round(hsv[2] * 100));
            sb.getProgressDrawable().setColorFilter(U.ACCENT, PorterDuff.Mode.SRC_IN);
            sb.getThumb().setColorFilter(U.TEXT, PorterDuff.Mode.SRC_IN);
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
            right.addView(U.text(c, "Darker colours are easier on the eyes at night.", 12, U.SUB, false), top(14));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            rl.leftMargin = U.u(28);
            b.addView(right, rl);
            return Sheets.panel("Wallpaper colour", b, 600, 380);
        });
    }

    /** Hue/saturation colour wheel. */
    static final class Wheel extends View {
        private final float[] hsv;
        private final U.Cb<Integer> cb;
        private Bitmap bm;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Wheel(Context c, float[] hsv, U.Cb<Integer> cb) {
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
            p.setStrokeWidth(U.uf(3));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(x, y, U.uf(10), p);
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
}
