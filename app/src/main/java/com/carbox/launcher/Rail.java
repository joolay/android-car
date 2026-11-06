package com.carbox.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** CarPlay-style status rail on the right edge. */
final class Rail {
    // the clock (top) and the voice button (bottom) are fixed; these sit between them, evenly spaced
    static final String[] IDS = {"weather", "cell", "wifi", "bluetooth", "gps", "hotspot", "tailscale", "data"};
    static final String[] NAMES = {"Weather", "Mobile signal", "Wi-Fi", "Bluetooth", "GPS", "Hotspot", "Tailscale", "Data meter"};

    private final Context c;
    final LinearLayout view;
    private final LinearLayout items;
    private ImageView wIcon, hotIcon, tsIcon, btIcon;
    private TextView wHi, wLo, clock, day, date;

    private final Runnable clockTick = this::tickClock;

    private void tickClock() {
        if (clock == null) return;
        java.util.Calendar k = java.util.Calendar.getInstance();
        int h = k.get(java.util.Calendar.HOUR_OF_DAY), m = k.get(java.util.Calendar.MINUTE);
        String hs = Prefs.flag(Prefs.H24, true) ? Ui.two(h) : String.valueOf(h % 12 == 0 ? 12 : h % 12);
        android.text.SpannableString s = new android.text.SpannableString(hs + ":" + Ui.two(m));
        int st = hs.length();
        s.setSpan(new android.text.style.AbsoluteSizeSpan(Ui.u(14)), st, s.length(), 0);
        s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, st, 0);
        s.setSpan(new android.text.style.ForegroundColorSpan(Ui.TEXT2), st, s.length(), 0);
        clock.setText(s);
        java.util.Date now = k.getTime();
        if (day != null) day.setText(new java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(now));
        if (date != null) date.setText(new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(now));
        Ui.ui.removeCallbacks(clockTick);
        Ui.ui.postDelayed(clockTick, 60000 - (k.get(java.util.Calendar.SECOND) * 1000 + k.get(java.util.Calendar.MILLISECOND)) + 50);
    }
    private StatusIcons.CellIcon cell;
    private StatusIcons.WifiIcon wifi;
    private StatusIcons.GpsIcon gps;
    private final List<StatusIcons.AnimatedIcon> animated = new ArrayList<>();

    Rail(Context c) {
        this.c = c;
        view = Ui.col(c);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setBackground(Themes.card(Ui.CARD, 16));
        Ui.pad(view, 0, 14, 0, 12);

        // fixed: clock and date at the top
        view.addView(item("clock"), Ui.lp(Ui.MATCH, Ui.WRAP));
        View hr = new View(c);
        hr.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams hlp = Ui.lpu(40, 1);
        hlp.topMargin = Ui.u(8);
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        view.addView(hr, hlp);

        // the switched-on icons share the space between the clock and the mic
        items = Ui.col(c);
        items.setGravity(Gravity.CENTER_HORIZONTAL);
        view.addView(items, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

        // Settings now lives in All apps; the rail keeps just the voice button at the bottom
        View mic = circle(R.drawable.ic_mic, Ui.RED, 48, 26);
        mic.setOnClickListener(v -> Actions.assistant(c));
        mic.setOnLongClickListener(v -> { // hold: pick another assistant
            InfoPanels.assistants();
            return true;
        });
        LinearLayout.LayoutParams mlp = Ui.lpu(48, 48);
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        view.addView(mic, mlp);

        build();
        Status.listen(this::updateStatus);
        Weather.listen(this::updateWeather);
        Gps.listen(this::updateGps);
    }

    private View circle(int icon, int bg, float size, float iconSize) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        f.setBackground(Ui.oval(bg));
        ImageView iv = Ui.icon(c, icon, Ui.TEXT);
        f.addView(iv, Ui.flpu(iconSize, iconSize, Gravity.CENTER));
        Ui.press(f);
        return f;
    }

    static List<String[]> order() {
        JSONArray a = Prefs.arr(Prefs.RAIL);
        List<String[]> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id");
            if (indexOf(id) < 0 || seen.contains(id)) continue;
            seen.add(id);
            out.add(new String[]{id, o.optBoolean("on", true) ? "1" : "0"});
        }
        for (String id : IDS) if (!seen.contains(id)) out.add(new String[]{id, "1"});
        return out;
    }

    static void saveOrder(List<String[]> l) {
        JSONArray a = new JSONArray();
        for (String[] s : l) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", s[0]);
                o.put("on", "1".equals(s[1]));
            } catch (Exception ignored) {
            }
            a.put(o);
        }
        Prefs.put(Prefs.RAIL, a);
    }

    static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    /** (Re)build the middle icons in the saved order, spread evenly with equal gaps. */
    void build() {
        items.removeAllViews();
        animated.clear();
        cell = null;
        wifi = null;
        gps = null;
        wIcon = null;
        hotIcon = null;
        tsIcon = null;
        btIcon = null;
        items.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1));
        for (String[] e : order()) {
            if (!"1".equals(e[1])) continue;
            View v = item(e[0]);
            if (v == null) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            items.addView(v, lp);
            items.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1));
        }
        updateStatus();
        updateWeather();
        updateGps();
    }

    private View item(String id) {
        View v;
        switch (id) {
            case "clock": {
                // Full rail width with centred text: on the box the wrap-sized column was measured
                // before the time was filled in, which clipped "10:45" to "10" and "27 Oct" to "27".
                LinearLayout l = Ui.col(c);
                l.setGravity(Gravity.CENTER_HORIZONTAL);
                clock = Ui.text(c, "", 20, Ui.TEXT, false);
                clock.setSingleLine(true);
                clock.setGravity(Gravity.CENTER);
                l.addView(clock, Ui.lp(Ui.MATCH, Ui.WRAP));
                day = Ui.text(c, "", 12, Ui.TEXT, true);
                day.setSingleLine(true);
                day.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams dl = Ui.lp(Ui.MATCH, Ui.WRAP);
                dl.topMargin = Ui.u(3);
                l.addView(day, dl);
                date = Ui.text(c, "", 11, Ui.SUB, false);
                date.setSingleLine(true);
                date.setGravity(Gravity.CENTER);
                l.addView(date, Ui.lp(Ui.MATCH, Ui.WRAP));
                tickClock();
                v = l;
                v.setOnClickListener(x -> InfoPanels.calendar());
                v.setOnLongClickListener(x -> {
                    Status.open(c, Settings.ACTION_DATE_SETTINGS);
                    return true;
                });
                break;
            }
            case "weather": {
                LinearLayout l = Ui.row(c); // a smaller icon with the high and low to its right
                l.setGravity(Gravity.CENTER);
                wIcon = Ui.icon(c, R.drawable.ic_w_partly, 0);
                l.addView(wIcon, Ui.lpu(20, 20));
                LinearLayout range = Ui.col(c);
                wHi = Ui.text(c, "--°", 14, Ui.TEXT, true);
                range.addView(wHi);
                wLo = Ui.text(c, "--°", 12, Ui.SUB, false);
                range.addView(wLo);
                LinearLayout.LayoutParams rl = Ui.lp(Ui.WRAP, Ui.WRAP);
                rl.leftMargin = Ui.u(5);
                l.addView(range, rl);
                v = l;
                v.setOnClickListener(x -> InfoPanels.weather(0));
                v.setOnLongClickListener(x -> {
                    SettingsPanels.units();
                    return true;
                });
                break;
            }
            case "cell": {
                cell = new StatusIcons.CellIcon(c);
                animated.add(cell);
                cell.setLayoutParams(new LinearLayout.LayoutParams(Ui.WRAP, Ui.u(22)));
                v = cell;
                v.setOnClickListener(x -> InfoPanels.quick("cell"));
                v.setOnLongClickListener(x -> {
                    Status.open(c, Settings.ACTION_DATA_ROAMING_SETTINGS);
                    return true;
                });
                return withSpeed(wrap(v, 22, true), DataMeter.LTE);
            }
            case "wifi": {
                wifi = new StatusIcons.WifiIcon(c);
                animated.add(wifi);
                v = wifi;
                v.setOnClickListener(x -> InfoPanels.quick("wifi"));
                v.setOnLongClickListener(x -> {
                    Status.open(c, Settings.ACTION_WIFI_SETTINGS);
                    return true;
                });
                return withSpeed(wrap(v, 22, false), DataMeter.WIFI);
            }
            case "bluetooth": { // off: dim, on: white, connected to the phone or a speaker: blue
                btIcon = Ui.icon(c, R.drawable.ic_bt, Ui.MUTED);
                v = btIcon;
                v.setOnClickListener(x -> InfoPanels.quick("bluetooth"));
                v.setOnLongClickListener(x -> {
                    Status.open(c, Settings.ACTION_BLUETOOTH_SETTINGS);
                    return true;
                });
                return wrap(v, 22, false);
            }
            case "gps": {
                gps = new StatusIcons.GpsIcon(c);
                animated.add(gps);
                v = gps;
                v.setOnClickListener(x -> InfoPanels.quick("gps"));
                v.setOnLongClickListener(x -> {
                    Status.open(c, Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    return true;
                });
                return wrap(v, 22, true);
            }
            case "hotspot": {
                hotIcon = Ui.icon(c, R.drawable.ic_hot, Ui.MUTED);
                v = hotIcon;
                v.setOnClickListener(x -> InfoPanels.quick("hotspot"));
                v.setOnLongClickListener(x -> {
                    Status.tetherSettings(c);
                    return true;
                });
                return withSpeed(wrap(v, 22, false), DataMeter.HOTSPOT);
            }
            case "tailscale": {
                tsIcon = Ui.icon(c, R.drawable.ic_vpnkey, Ui.MUTED);
                v = tsIcon;
                v.setOnClickListener(x -> InfoPanels.quick("tailscale"));
                v.setOnLongClickListener(x -> {
                    Apps.launchPkg(c, Status.TS_PKG);
                    return true;
                });
                return wrap(v, 22, false);
            }
            case "data": { // this month's mobile data (tap: the full breakdown)
                v = new DataMeter(c);
                v.setOnClickListener(x -> InfoPanels.quick("data"));
                v.setOnLongClickListener(x -> {
                    Status.open(c, android.provider.Settings.ACTION_DATA_USAGE_SETTINGS);
                    return true;
                });
                Ui.press(v);
                android.widget.FrameLayout f = new android.widget.FrameLayout(c);
                f.addView(v, new android.widget.FrameLayout.LayoutParams(Ui.u(50), Ui.u(46), Gravity.CENTER));
                return f;
            }
            default:
                return null;
        }
        return v;
    }

    /** A connection's icon with its live speed bar beside it. */
    private View withSpeed(View icon, int kind) {
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(icon);
        LinearLayout.LayoutParams bl = Ui.lpu(4, 20);
        bl.leftMargin = Ui.u(4);
        r.addView(new DataMeter.SpeedBar(c, kind), bl);
        return r;
    }

    /** Give small icons a bigger tap area. */
    private View wrap(View v, float h, boolean wrapWidth) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        f.addView(v, new android.widget.FrameLayout.LayoutParams(wrapWidth ? Ui.WRAP : Ui.u(h), Ui.u(h), Gravity.CENTER));
        f.setPadding(Ui.u(10), Ui.u(2), Ui.u(10), Ui.u(2));
        f.setOnClickListener(x -> v.performClick());
        f.setOnLongClickListener(x -> v.performLongClick());
        return f;
    }

    void updateStatus() {
        if (cell != null) cell.setState(!Status.sim, Status.cellLevel, Status.netType, Status.dataOn);
        if (wifi != null) {
            int st = !Status.wifiOn ? StatusIcons.WifiIcon.OFF : Status.wifiConnected ? StatusIcons.WifiIcon.CONNECTED : StatusIcons.WifiIcon.SCANNING;
            wifi.setState(st, Status.wifiLevel);
        }
        if (btIcon != null) btIcon.setColorFilter(!Status.btOn ? Ui.MUTED : Status.btConnected ? Ui.ACCENT : Ui.TEXT);
        if (hotIcon != null) hotIcon.setColorFilter(Status.hotspotOn ? Ui.ACCENT : Ui.MUTED);
        if (tsIcon != null) tsIcon.setColorFilter(Status.vpnOn && Status.tailscaleInstalled() ? Ui.GREEN : Ui.MUTED);
        updateGps();
    }

    void updateGps() {
        if (gps == null) return;
        if (!Gps.hasPermission() || !Gps.gpsEnabled()) gps.setState(StatusIcons.GpsIcon.OFF, -1);
        else if (Gps.fresh()) gps.setState(StatusIcons.GpsIcon.FIX, Gps.satsUsed);
        else gps.setState(StatusIcons.GpsIcon.SEARCHING, Gps.satsVisible);
    }

    private android.animation.ObjectAnimator wPulse;

    void updateWeather() {
        if (wIcon == null) return;
        // the icon breathes while a new forecast is being fetched
        if (Weather.loading() && wPulse == null && Ui.ms(1) > 0) {
            wPulse = android.animation.ObjectAnimator.ofFloat(wIcon, View.ALPHA, 1f, 0.3f);
            wPulse.setDuration(600);
            wPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            wPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            wPulse.start();
        } else if (!Weather.loading() && wPulse != null) {
            wPulse.cancel();
            wPulse = null;
            wIcon.setAlpha(1f);
        }
        if (!Weather.ok) return;
        wIcon.setImageResource(Weather.icon(Weather.code, Weather.isDay));
        Weather.Day d = Weather.today();
        if (d != null) {
            wHi.setText(Weather.t(d.hi));
            wLo.setText(Weather.t(d.lo));
        }
    }

    void paused(boolean p) {
        if (!p) tickClock(); // catch up straight away after the screen was off
        for (StatusIcons.AnimatedIcon a : animated) a.setPaused(p);
    }
}
