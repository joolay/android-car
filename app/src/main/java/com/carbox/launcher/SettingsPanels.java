package com.carbox.launcher;

import android.content.Context;
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
        Sheets.show(SettingsPanels::mainPage);
    }

    private static View mainPage() {
        Context c = c();
        LinearLayout b = U.col(c);

        // quick toggles
        LinearLayout q = U.row(c);
        q.addView(quick(c, R.drawable.ic_wifi, "Wi-Fi", Status.wifiOn, U.ACCENT, () -> Status.wifiPanel(c)), U.weight(1, true));
        q.addView(quick(c, R.drawable.ic_bt, "Bluetooth", bluetoothOn(), U.ACCENT, () -> Status.open(c, Settings.ACTION_BLUETOOTH_SETTINGS)), margin());
        q.addView(quick(c, R.drawable.ic_hot, "Hotspot", Status.hotspotOn, U.ACCENT, () -> Status.tetherSettings(c)), margin());
        q.addView(quick(c, R.drawable.ic_vpnkey, "Tailscale", Status.vpnOn, U.GREEN, () -> {
            if (!Status.tailscaleInstalled()) InfoPanels.quick("tailscale");
            else {
                Status.tailscale(!Status.vpnOn);
                U.ui.postDelayed(Sheets::refresh, 2500);
            }
        }), margin());
        q.addView(quick(c, R.drawable.ic_moon, "Night", Store.flag(Store.NIGHT, false), U.ACCENT, () -> {
            Store.put(Store.NIGHT, !Store.flag(Store.NIGHT, false));
            if (MainActivity.I != null) MainActivity.I.applyNight();
            Sheets.refresh();
        }), margin());
        b.addView(q, U.lpu(U.MATCH, 66));

        // brightness
        LinearLayout br = U.row(c);
        br.addView(U.icon(c, R.drawable.ic_sun, U.SUB), U.lpu(20, 20));
        SeekBar sb = new SeekBar(c);
        sb.setMax(255);
        sb.setProgress(Actions.brightness(c));
        sb.getProgressDrawable().setColorFilter(U.ACCENT, PorterDuff.Mode.SRC_IN);
        sb.getThumb().setColorFilter(U.TEXT, PorterDuff.Mode.SRC_IN);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user && Actions.canWriteSettings(c) && MainActivity.I != null) Actions.setBrightness(MainActivity.I, v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                if (!Actions.canWriteSettings(c) && MainActivity.I != null) Actions.setBrightness(MainActivity.I, s.getProgress());
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        sl.leftMargin = U.u(8);
        br.addView(sb, sl);
        if (!Actions.canWriteSettings(c)) br.addView(U.text(c, "Tap to allow", 11, U.SUB, false));
        b.addView(br, top(14));

        // assistant
        String as = Store.str(Store.ASSIST, "gemini");
        String ext = Store.str(Store.ASSIST_PKG, null);
        b.addView(Sheets.row("Assistant",
                U.seg(c, "Gemini", "gemini".equals(as), () -> {
                    Store.put(Store.ASSIST, "gemini");
                    Sheets.refresh();
                }),
                U.seg(c, "external".equals(as) && ext != null ? Apps.label(ext) + " ›" : "External ›", "external".equals(as), SettingsPanels::assistantPicker)), top(6));

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

        // two-column list
        String[][] items = {
                {"Navigation app", Actions.navPkg() == null ? "Choose" : Apps.label(Actions.navPkg())},
                {"Right bar order", ""},
                {"Hidden apps", Apps.hidden().isEmpty() ? "None" : String.valueOf(Apps.hidden().size())},
                {"News and traffic", tickerLabel()},
                {"Calls", Widgets.callsLabel()},
                {"Connections at start", connLabel()},
                {"Splash screen", Store.flag(Store.SPLASH_ON, false) ? "On" : "Off"},
                {"Calendar", Cal.altLabel()},
                {"Units and clock", (Store.flag(Store.CELSIUS, true) ? "°C" : "°F") + " · " + (Store.flag(Store.H24, true) ? "24 h" : "12 h")},
                {"Prayer times", Prayer.METHODS[Store.num(Store.PRAY_METHOD, 0)]},
                {"Auto start", Actions.autostart().length() + " apps"},
                {"Lyrics over album art", Store.flag(Store.LYRICS, true) ? "On" : "Off"},
                {"Android settings", ""},
                {"About", "Car Launcher"}};
        Runnable[] actions = {SettingsPanels::navApp, SettingsPanels::railOrder, SettingsPanels::hiddenApps, SettingsPanels::news,
                SettingsPanels::calls, SettingsPanels::connections, SettingsPanels::splash, SettingsPanels::calendar, SettingsPanels::units,
                SettingsPanels::prayerTimes, AppPanels::autostart, () -> {
            Store.put(Store.LYRICS, !Store.flag(Store.LYRICS, true));
            Sheets.refresh();
            if (MainActivity.I != null) MainActivity.I.nowPlaying.update();
        }, () -> {
            Status.open(c, Settings.ACTION_SETTINGS);
            Sheets.close();
        }, SettingsPanels::about};
        LinearLayout cols = U.row(c);
        cols.setGravity(Gravity.TOP);
        LinearLayout l1 = U.col(c), l2 = U.col(c);
        for (int i = 0; i < items.length; i++) {
            LinearLayout it = Sheets.item(items[i][0], items[i][1].isEmpty() ? null : items[i][1], actions[i]);
            (i % 2 == 0 ? l1 : l2).addView(it);
            View hr = U.hr(c);
            (i % 2 == 0 ? l1 : l2).addView(hr);
        }
        cols.addView(l1, U.weight(1, true));
        cols.addView(U.gap(c, 28, 1));
        cols.addView(l2, U.weight(1, true));
        b.addView(cols, top(10));
        return Sheets.panel("Settings", b, 700, 444);
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

    static void navApp() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
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
            return Sheets.panel("Navigation app", b, 520, 400);
        });
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
            b.addView(U.text(c, "Settings and the mic always stay at the bottom.", 12, U.SUB, false), top(8));
            return Sheets.panel("Right bar order", b, 480, 440);
        });
    }

    private static void rebuildRail() {
        if (MainActivity.I != null) MainActivity.I.rail.build();
    }

    // ------------------------------------------------------------------ hidden apps

    static void hiddenApps() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            if (Apps.hidden().isEmpty()) b.addView(U.text(c, "No hidden apps. Long-press an app and choose Hide app.", 14, U.SUB, false));
            for (String k : new ArrayList<>(Apps.hidden())) {
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
                    Apps.setHidden(k, false);
                    Sheets.refresh();
                }));
                b.addView(r);
            }
            return Sheets.panel("Hidden apps", b, 520, 400);
        });
    }

    // ------------------------------------------------------------------ news & traffic

    static void news() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
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

    static void calls() {
        open(() -> {
            Context c = c();
            LinearLayout b = U.col(c);
            String m = Store.str(Store.CALLS, "box");
            b.addView(radio(c, "Box dialler", "Calls from this box's own phone (needs a SIM)", "box".equals(m), () -> {
                Store.put(Store.CALLS, "box");
                Sheets.refresh();
            }));
            String bt = Store.str(Store.CALLS_BT_PKG, null);
            b.addView(radio(c, "Bluetooth phone app", bt == null ? "Choose the app that calls through your phone" : Apps.label(bt), "bt".equals(m), () -> {
                Store.put(Store.CALLS, "bt");
                if (Store.str(Store.CALLS_BT_PKG, null) == null) AppPanels.appPicker("Bluetooth phone app", a -> {
                    Store.put(Store.CALLS_BT_PKG, a.pkg);
                    Sheets.back();
                    Sheets.refresh();
                });
                else Sheets.refresh();
            }));
            b.addView(radio(c, "Through Car Connect", "Opens CarPlay / Android Auto to call from your phone", "connect".equals(m), () -> {
                Store.put(Store.CALLS, "connect");
                Sheets.refresh();
            }));
            if ("bt".equals(m)) {
                b.addView(Sheets.item("Change Bluetooth phone app", bt == null ? "Choose" : Apps.label(bt), () -> AppPanels.appPicker("Bluetooth phone app", a -> {
                    Store.put(Store.CALLS_BT_PKG, a.pkg);
                    Sheets.back();
                    Sheets.refresh();
                })), top(6));
                String fb = Store.str(Store.CALLS_FALLBACK, "ask");
                b.addView(Sheets.row("If the phone isn't connected", segStr(c, Store.CALLS_FALLBACK, "ask", "Ask", fb),
                        segStr(c, Store.CALLS_FALLBACK, "box", "Use box dialler", fb), segStr(c, Store.CALLS_FALLBACK, "none", "Do nothing", fb)));
            }
            return Sheets.panel("Calls", b, 600, 420);
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
                Sheets.push(() -> Sheets.panel("Home Assistant", U.text(c(), "Add the Home Assistant widget to a tile first: long-press a tile → ⋯ → Replace with widget.", 14, U.SUB, false), 480, 220));
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
            if ("custom".equals(type)) {
                b.addView(Sheets.section("Custom text"));
                b.addView(Widgets.field("Main text", ct, false, t -> Store.put(Store.CUSTOM_TEXT, t)), U.lp(U.MATCH, U.WRAP));
                b.addView(Widgets.field("Small text underneath (optional)", Store.str(Store.CUSTOM_SUB, ""), false, t -> Store.put(Store.CUSTOM_SUB, t)), top(8));
            } else {
                b.addView(Sheets.item("Set up Travel Prayer", null, SettingsPanels::travelPrayer), top(4));
            }
            b.addView(U.button(c, "Preview", false, SettingsPanels::preview), top(10));
            return Sheets.panel("Splash screen", b, 680, 444);
        });
    }

    static void preview() {
        if (MainActivity.I == null) return;
        Sheets.close();
        Splash.show(MainActivity.I);
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
            for (String[] a : Cal.ALTS) chips.add(segStr(c, Store.CAL_ALT, a[0], a[1], Cal.alt()));
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
            b.addView(U.text(c, "The right bar clock follows Android's 24-hour setting.", 12, U.SUB, false), top(6));
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
