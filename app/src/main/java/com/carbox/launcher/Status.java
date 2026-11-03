package com.carbox.launcher;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Mobile, Wi-Fi, hotspot and Tailscale state, refreshed every few seconds. */
final class Status {
    static boolean sim, dataOn;
    static int cellLevel;
    static String netType, operator;
    static boolean wifiOn, wifiConnected;
    static int wifiLevel;
    static String ssid;
    static boolean hotspotOn, vpnOn;

    static final String TS_PKG = "com.tailscale.ipn";

    private static Context ctx;
    private static boolean running;
    private static final List<Runnable> listeners = new ArrayList<>();

    private Status() {
    }

    static void init(Context c) {
        ctx = c.getApplicationContext();
    }

    static void listen(Runnable r) {
        listeners.add(r);
    }

    static void start() {
        if (running) return;
        running = true;
        Ui.ui.post(tick);
    }

    static void stop() {
        running = false;
        Ui.ui.removeCallbacks(tick);
    }

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            for (Runnable r : new ArrayList<>(listeners)) r.run();
            if (running) Ui.ui.postDelayed(this, 3000);
        }
    };

    @SuppressWarnings("deprecation")
    static void refresh() {
        // mobile
        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        sim = tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY;
        cellLevel = 0;
        netType = null;
        operator = sim ? tm.getNetworkOperatorName() : null;
        if (sim) {
            try {
                SignalStrength ss = tm.getSignalStrength();
                cellLevel = ss == null ? 0 : ss.getLevel();
            } catch (Exception ignored) {
            }
            if (ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    netType = label(tm.getDataNetworkType());
                } catch (SecurityException ignored) {
                }
            }
        }
        // transports
        dataOn = false;
        wifiConnected = false;
        vpnOn = false;
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null) continue;
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) dataOn = true;
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifiConnected = true;
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpnOn = true;
            }
        }
        // wifi
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiOn = wm != null && wm.isWifiEnabled();
        wifiLevel = 0;
        ssid = null;
        if (wifiOn && wifiConnected) {
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null) {
                wifiLevel = WifiManager.calculateSignalLevel(wi.getRssi(), 5);
                String s = wi.getSSID();
                if (s != null && !s.contains("unknown ssid")) ssid = s.replace("\"", "");
            }
        }
        hotspotOn = apEnabled(wm);
    }

    private static boolean apEnabled(WifiManager wm) {
        if (wm == null) return false;
        try {
            Method m = wm.getClass().getMethod("isWifiApEnabled");
            Object r = m.invoke(wm);
            return r instanceof Boolean && (Boolean) r;
        } catch (Exception e) {
            return false;
        }
    }

    static String label(int t) {
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

    // ------------------------------------------------------------------ actions

    static boolean tailscaleInstalled() {
        return Apps.installed(TS_PKG);
    }

    /** Tailscale's own automation receiver (used by Tasker etc.). */
    static void tailscale(boolean connect) {
        try {
            Intent i = new Intent(connect ? "com.tailscale.ipn.CONNECT_VPN" : "com.tailscale.ipn.DISCONNECT_VPN");
            i.setComponent(new ComponentName(TS_PKG, "com.tailscale.ipn.IPNReceiver"));
            ctx.sendBroadcast(i);
        } catch (Exception ignored) {
        }
        Ui.ui.postDelayed(Status::refreshAndNotify, 2500);
    }

    static void refreshAndNotify() {
        refresh();
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    static void open(Context c, String action) {
        try {
            c.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    static void wifiPanel(Context c) {
        open(c, Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS);
    }

    static void dataPanel(Context c) {
        open(c, Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY : Settings.ACTION_DATA_ROAMING_SETTINGS);
    }

    static void tetherSettings(Context c) {
        try {
            Intent i = new Intent();
            i.setClassName("com.android.settings", "com.android.settings.TetherSettings");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            open(c, Settings.ACTION_WIRELESS_SETTINGS);
        }
    }

    static String bars() {
        return TextUtils.isEmpty(operator) ? "Mobile" : operator;
    }
}
