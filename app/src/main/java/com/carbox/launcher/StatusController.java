package com.carbox.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Top-bar status chips: weather, mobile signal, Wi-Fi and GPS. */
final class StatusController {

    private static final int C_CARD = 0xFF171C24;
    private static final int C_CARD2 = 0xFF2A3342;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_SUB = 0xFF9AA4B2;
    private static final int C_GREEN = 0xFF34C759;
    private static final int C_AMBER = 0xFFFFB020;
    private static final int C_GRAY = 0xFF5C6675;

    private static final long TICK_MS = 3000;
    private static final long WEATHER_EVERY_MS = 20 * 60 * 1000L;
    private static final long GPS_FRESH_MS = 15000;

    private final Context ctx;
    private final float density;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();

    private final LinearLayout root;
    private final TextView weatherIcon, weatherLow, weatherHigh;
    private final StatusIcons.CellIcon cellIcon;
    private final StatusIcons.WifiIcon wifiIcon;
    private final StatusIcons.GpsIcon gpsIcon;

    private boolean running;
    private boolean gnssRegistered;
    private Location lastLocation;
    private long lastGpsFixAt;
    private long weatherFetchedAt;
    private boolean weatherLoading;
    private int satsUsed = -1;
    private int satsVisible = -1;

    StatusController(Context ctx) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;

        // Vertical stack for the right-hand status rail: weather, then signal icons
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        weatherIcon = text("·", 24, C_TEXT, false);
        weatherHigh = text("--°", 18, C_TEXT, true);
        weatherLow = text("--°", 14, C_SUB, false);
        root.addView(weatherIcon, centered(0));
        root.addView(weatherHigh, centered(2));
        root.addView(weatherLow, centered(0));

        cellIcon = new StatusIcons.CellIcon(ctx);
        cellIcon.setContentDescription("Mobile signal");
        wifiIcon = new StatusIcons.WifiIcon(ctx);
        wifiIcon.setContentDescription("Wi-Fi");
        gpsIcon = new StatusIcons.GpsIcon(ctx);
        gpsIcon.setContentDescription("GPS");
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24));
        clp.topMargin = dp(20);
        root.addView(cellIcon, clp);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(24), dp(24));
        wlp.topMargin = dp(14);
        root.addView(wifiIcon, wlp);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24));
        glp.topMargin = dp(14);
        root.addView(gpsIcon, glp);
    }

    private LinearLayout.LayoutParams centered(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    View getView() {
        return root;
    }

    void start() {
        registerGnss();
        if (running) return;
        running = true;
        setIconsPaused(false);
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    void stop() {
        running = false;
        ui.removeCallbacks(tick);
        unregisterGnss();
        setIconsPaused(true);
    }

    private void setIconsPaused(boolean paused) {
        cellIcon.setPaused(paused);
        wifiIcon.setPaused(paused);
        gpsIcon.setPaused(paused);
    }

    void shutdown() {
        stop();
        net.shutdownNow();
    }

    void onLocation(Location l) {
        if (l == null) return;
        lastLocation = l;
        if (LocationManager.GPS_PROVIDER.equals(l.getProvider())) lastGpsFixAt = SystemClock.elapsedRealtime();
        maybeFetchWeather();
        updateGps();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateCell();
            updateWifi();
            updateGps();
            maybeFetchWeather();
            if (running) ui.postDelayed(this, TICK_MS);
        }
    };

    // ---------------------------------------------------------------- mobile

    private void updateCell() {
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null || tm.getSimState() != TelephonyManager.SIM_STATE_READY) {
            cellIcon.setState(true, 0, null, false);
            return;
        }
        int level = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                SignalStrength ss = tm.getSignalStrength();
                level = ss == null ? 0 : ss.getLevel();
            } catch (SecurityException ignored) {
            }
        }
        String type = null;
        if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                type = networkLabel(tm.getDataNetworkType());
            } catch (SecurityException ignored) {
            }
        }
        cellIcon.setState(false, level, type, mobileDataConnected());
    }

    /** True when the box is actually online over mobile data (not just registered on the network). */
    private boolean mobileDataConnected() {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true;
        }
        return false;
    }

    private static String networkLabel(int t) {
        switch (t) {
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "H+";
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
                return "H";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "E";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "G";
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------- wifi

    @SuppressWarnings("deprecation")
    private void updateWifi() {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm == null || !wm.isWifiEnabled()) {
            wifiIcon.setState(StatusIcons.WifiIcon.OFF, 0);
            return;
        }
        boolean connected = false;
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    connected = true;
                    break;
                }
            }
        }
        if (!connected) {
            wifiIcon.setState(StatusIcons.WifiIcon.SCANNING, 0);
            return;
        }
        WifiInfo info = wm.getConnectionInfo();
        int level = info == null ? 0 : WifiManager.calculateSignalLevel(info.getRssi(), 5);
        wifiIcon.setState(StatusIcons.WifiIcon.CONNECTED, level);
    }

    // ---------------------------------------------------------------- gps

    private final GnssStatus.Callback gnssCallback = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            int used = 0;
            for (int i = 0; i < status.getSatelliteCount(); i++) if (status.usedInFix(i)) used++;
            satsUsed = used;
            satsVisible = status.getSatelliteCount();
            updateGps();
        }

        @Override
        public void onStopped() {
            satsUsed = -1;
            satsVisible = -1;
        }
    };

    private void registerGnss() {
        if (gnssRegistered || !hasFine()) return;
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;
        try {
            gnssRegistered = lm.registerGnssStatusCallback(gnssCallback, ui);
        } catch (SecurityException ignored) {
        }
    }

    private void unregisterGnss() {
        if (!gnssRegistered) return;
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) lm.unregisterGnssStatusCallback(gnssCallback);
        gnssRegistered = false;
    }

    /** Satellite icon: number = satellites used in the fix (or in view while searching). */
    private void updateGps() {
        if (!hasFine()) {
            gpsIcon.setState(StatusIcons.GpsIcon.OFF, -1);
            return;
        }
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        boolean enabled = false;
        try {
            enabled = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {
        }
        if (!enabled) {
            gpsIcon.setState(StatusIcons.GpsIcon.OFF, -1);
        } else if (lastGpsFixAt > 0 && SystemClock.elapsedRealtime() - lastGpsFixAt < GPS_FRESH_MS) {
            gpsIcon.setState(StatusIcons.GpsIcon.FIX, satsUsed);
        } else {
            gpsIcon.setState(StatusIcons.GpsIcon.SEARCHING, satsVisible);
        }
    }

    private boolean hasFine() {
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // ---------------------------------------------------------------- weather (Open-Meteo, no API key)

    private void maybeFetchWeather() {
        final Location l = lastLocation;
        if (l == null || weatherLoading) return;
        if (weatherFetchedAt != 0 && SystemClock.elapsedRealtime() - weatherFetchedAt < WEATHER_EVERY_MS) return;
        weatherLoading = true;
        net.execute(() -> {
            String icon = null;
            String low = null, high = null;
            HttpURLConnection c = null;
            try {
                String u = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                        l.getLatitude(), l.getLongitude());
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONObject json = new JSONObject(sb.toString());
                JSONObject cur = json.getJSONObject("current");
                JSONObject daily = json.getJSONObject("daily");
                low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)) + "°";
                high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)) + "°";
                icon = weatherIcon(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1);
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            final String fIcon = icon, fLow = low, fHigh = high;
            ui.post(() -> {
                weatherLoading = false;
                if (fHigh != null) {
                    weatherFetchedAt = SystemClock.elapsedRealtime();
                    weatherIcon.setText(fIcon);
                    weatherLow.setText(fLow);
                    weatherHigh.setText(fHigh);
                }
            });
        });
    }

    private static String weatherIcon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";          // sun / moon
        if (code <= 2) return day ? "⛅" : "☁️";               // partly cloudy
        if (code == 3) return "☁️";                                 // cloudy
        if (code == 45 || code == 48) return "🌫️";            // fog
        if (code >= 51 && code <= 57) return "🌦️";            // drizzle
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "🌧️"; // rain
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "🌨️";   // snow
        if (code >= 95) return "⛈️";                                // storm
        return "☁️";
    }

    // ---------------------------------------------------------------- view helpers

    private LinearLayout chip() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD);
        bg.setCornerRadius(dp(16));
        l.setBackground(bg);
        l.setPadding(dp(12), 0, dp(12), 0);
        l.setMinimumHeight(dp(40));
        return l;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.leftMargin = dp(8);
        return lp;
    }

    private LinearLayout.LayoutParams marginStart(int dpValue) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(dpValue);
        return lp;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
