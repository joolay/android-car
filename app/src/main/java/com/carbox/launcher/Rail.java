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
    static final String[] IDS = {"weather", "cell", "wifi", "gps", "hotspot", "tailscale", "data"};
    static final String[] NAMES = {"Weather", "Mobile signal", "Wi-Fi", "GPS", "Hotspot", "Tailscale", "Data meter"};

    private final Context c;
    final LinearLayout view;
    private final LinearLayout items;
    private ImageView wIcon, hotIcon, tsIcon;
    private TextView wHi, wLo, clock, day, date;

    private final Runnable clockTick = this::tickClock;

    private void tickClock() {
        if (clock == null) return;
        java.util.Calendar k = java.util.Calendar.getInstance();
        int h = k.get(java.util.Calendar.HOUR_OF_DAY), m = k.get(java.util.Calendar.MINUTE);
        String hs = Store.flag(Store.H24, true) ? U.two(h) : String.valueOf(h % 12 == 0 ? 12 : h % 12);
        android.text.SpannableString s = new android.text.SpannableString(hs + ":" + U.two(m));
        int st = hs.length();
        s.setSpan(new android.text.style.AbsoluteSizeSpan(U.u(14)), st, s.length(), 0);
        s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, st, 0);
        s.setSpan(new android.text.style.ForegroundColorSpan(U.TEXT2), st, s.length(), 0);
        clock.setText(s);
        java.util.Date now = k.getTime();
        if (day != null) day.setText(new java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(now));
        if (date != null) date.setText(new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(now));
        U.ui.removeCallbacks(clockTick);
        U.ui.postDelayed(clockTick, 60000 - (k.get(java.util.Calendar.SECOND) * 1000 + k.get(java.util.Calendar.MILLISECOND)) + 50);
    }
    private StatusIcons.CellIcon cell;
    private StatusIcons.WifiIcon wifi;
    private StatusIcons.GpsIcon gps;
    private final List<StatusIcons.AnimatedIcon> animated = new ArrayList<>();

    Rail(Context c) {
        this.c = c;
        view = U.col(c);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setBackground(U.round(U.CARD, 16));
        U.pad(view, 0, 14, 0, 12);

        // fixed: clock and date at the top
        view.addView(item("clock"), U.lp(U.MATCH, U.WRAP));
        View hr = new View(c);
        hr.setBackgroundColor(U.LINE);
        LinearLayout.LayoutParams hlp = U.lpu(40, 1);
        hlp.topMargin = U.u(8);
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        view.addView(hr, hlp);

        // the switched-on icons share the space between the clock and the mic
        items = U.col(c);
        items.setGravity(Gravity.CENTER_HORIZONTAL);
        view.addView(items, new LinearLayout.LayoutParams(U.MATCH, 0, 1));

        // Settings now lives in All apps; the rail keeps just the voice button at the bottom
        View mic = circle(R.drawable.ic_mic, U.RED, 48, 26);
        mic.setOnClickListener(v -> Actions.assistant(c));
        mic.setOnLongClickListener(v -> { // hold: pick another assistant
            InfoPanels.assistants();
            return true;
        });
        LinearLayout.LayoutParams mlp = U.lpu(48, 48);
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        view.addView(mic, mlp);

        build();
        Status.listen(this::updateStatus);
        Weather.listen(this::updateWeather);
        Loc.listen(this::updateGps);
    }

    private View circle(int icon, int bg, float size, float iconSize) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        f.setBackground(U.oval(bg));
        ImageView iv = U.icon(c, icon, U.TEXT);
        f.addView(iv, U.flpu(iconSize, iconSize, Gravity.CENTER));
        U.press(f);
        return f;
    }

    static List<String[]> order() {
        JSONArray a = Store.arr(Store.RAIL);
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
        Store.put(Store.RAIL, a);
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
        items.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1));
        for (String[] e : order()) {
            if (!"1".equals(e[1])) continue;
            View v = item(e[0]);
            if (v == null) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
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
                LinearLayout l = U.col(c);
                l.setGravity(Gravity.CENTER_HORIZONTAL);
                clock = U.text(c, "", 20, U.TEXT, false);
                clock.setSingleLine(true);
                clock.setGravity(Gravity.CENTER);
                l.addView(clock, U.lp(U.MATCH, U.WRAP));
                day = U.text(c, "", 12, U.TEXT, true);
                day.setSingleLine(true);
                day.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams dl = U.lp(U.MATCH, U.WRAP);
                dl.topMargin = U.u(3);
                l.addView(day, dl);
                date = U.text(c, "", 11, U.SUB, false);
                date.setSingleLine(true);
                date.setGravity(Gravity.CENTER);
                l.addView(date, U.lp(U.MATCH, U.WRAP));
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
                LinearLayout l = U.col(c);
                l.setGravity(Gravity.CENTER_HORIZONTAL);
                wIcon = U.icon(c, R.drawable.ic_w_partly, 0);
                l.addView(wIcon, U.lpu(26, 26));
                wHi = U.text(c, "--°", 16, U.TEXT, true);
                LinearLayout.LayoutParams hl = U.lp(U.WRAP, U.WRAP);
                hl.topMargin = U.u(2);
                l.addView(wHi, hl);
                wLo = U.text(c, "--°", 13, U.SUB, false);
                l.addView(wLo);
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
                cell.setLayoutParams(new LinearLayout.LayoutParams(U.WRAP, U.u(22)));
                v = cell;
                v.setOnClickListener(x -> InfoPanels.quick("cell"));
                v.setOnLongClickListener(x -> {
                    Status.open(c, Settings.ACTION_DATA_ROAMING_SETTINGS);
                    return true;
                });
                return wrap(v, 22, true);
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
                hotIcon = U.icon(c, R.drawable.ic_hot, U.MUTED);
                v = hotIcon;
                v.setOnClickListener(x -> InfoPanels.quick("hotspot"));
                v.setOnLongClickListener(x -> {
                    Status.tetherSettings(c);
                    return true;
                });
                return wrap(v, 22, false);
            }
            case "tailscale": {
                tsIcon = U.icon(c, R.drawable.ic_vpnkey, U.MUTED);
                v = tsIcon;
                v.setOnClickListener(x -> InfoPanels.quick("tailscale"));
                v.setOnLongClickListener(x -> {
                    Apps.launchPkg(c, Status.TS_PKG);
                    return true;
                });
                return wrap(v, 22, false);
            }
            case "data": { // live data speed gauge
                v = new DataMeter(c);
                v.setOnClickListener(x -> InfoPanels.quick("data"));
                v.setOnLongClickListener(x -> {
                    Status.open(c, android.provider.Settings.ACTION_DATA_USAGE_SETTINGS);
                    return true;
                });
                U.press(v);
                android.widget.FrameLayout f = new android.widget.FrameLayout(c);
                f.addView(v, new android.widget.FrameLayout.LayoutParams(U.u(46), U.u(40), Gravity.CENTER));
                return f;
            }
            default:
                return null;
        }
        return v;
    }

    /** Give small icons a bigger tap area. */
    private View wrap(View v, float h, boolean wrapWidth) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        f.addView(v, new android.widget.FrameLayout.LayoutParams(wrapWidth ? U.WRAP : U.u(h), U.u(h), Gravity.CENTER));
        f.setPadding(U.u(10), U.u(2), U.u(10), U.u(2));
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
        if (hotIcon != null) hotIcon.setColorFilter(Status.hotspotOn ? U.ACCENT : U.MUTED);
        if (tsIcon != null) tsIcon.setColorFilter(Status.vpnOn && Status.tailscaleInstalled() ? U.GREEN : U.MUTED);
        updateGps();
    }

    void updateGps() {
        if (gps == null) return;
        if (!Loc.hasPermission() || !Loc.gpsEnabled()) gps.setState(StatusIcons.GpsIcon.OFF, -1);
        else if (Loc.fresh()) gps.setState(StatusIcons.GpsIcon.FIX, Loc.satsUsed);
        else gps.setState(StatusIcons.GpsIcon.SEARCHING, Loc.satsVisible);
    }

    private android.animation.ObjectAnimator wPulse;

    void updateWeather() {
        if (wIcon == null) return;
        // the icon breathes while a new forecast is being fetched
        if (Weather.loading() && wPulse == null && U.ms(1) > 0) {
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
