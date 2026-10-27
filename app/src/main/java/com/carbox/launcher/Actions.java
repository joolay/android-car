package com.carbox.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Things the launcher does on your behalf: assistant, calls, navigation, connections, auto start. */
final class Actions {
    private Actions() {
    }

    static final String[] NAV_APPS = {"com.google.android.apps.maps", "com.waze", "com.here.app.maps", "com.sygic.aura",
            "net.osmand", "net.osmand.plus", "ru.yandex.yandexnavi", "com.google.android.apps.mapslite"};
    static final String GEMINI = "com.google.android.apps.bard";
    static final String[][] ASSISTANTS = {
            {"com.anthropic.claude", "Claude"}, {"com.openai.chatgpt", "ChatGPT"}, {"com.microsoft.copilot", "Microsoft Copilot"},
            {"com.amazon.dee.app", "Amazon Alexa"}, {"ai.perplexity.app.android", "Perplexity"}, {"com.facebook.stella", "Meta AI"},
            {"com.google.android.apps.bard", "Gemini"}};
    static final String[][] BRIDGES = {
            {"com.speedplay", "Speed Play"}, {"com.zjinnova.zlink", "ZLink"}, {"com.autokit.carlink", "AutoKit"},
            {"com.carbit.carlink", "CarbitLink"}, {"com.tlink.carlink", "TLink"}};
    static final String[] BRIDGE_WORDS = {"speed play", "speedplay", "zlink", "autokit", "carlink", "carbit", "tlink", "carplay", "android auto"};

    /** First installed app that looks like a CarPlay / Android Auto bridge. */
    static Apps.App findBridge() {
        for (String[] b : BRIDGES) {
            Apps.App a = Apps.byPkg(b[0]);
            if (a != null) return a;
        }
        for (Apps.App a : Apps.all) {
            String l = a.label.toLowerCase(java.util.Locale.ROOT);
            for (String w : BRIDGE_WORDS) if (l.contains(w)) return a;
        }
        return null;
    }

    static boolean looksLikeBridge(Apps.App a) {
        String l = a.label.toLowerCase(java.util.Locale.ROOT);
        for (String w : BRIDGE_WORDS) if (l.contains(w)) return true;
        for (String[] b : BRIDGES) if (b[0].equals(a.pkg)) return true;
        return false;
    }

    static final String[] MUSIC = {"com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app",
            "com.apple.android.music", "com.amazon.mp3", "com.google.android.youtube"};

    // ------------------------------------------------------------------ assistant

    static void assistant(Context c) {
        if ("external".equals(Store.str(Store.ASSIST, "gemini"))) {
            String pkg = Store.str(Store.ASSIST_PKG, null);
            if (pkg != null && Apps.launchPkg(c, pkg)) return;
            SettingsPanels.assistantPicker();
            return;
        }
        // Gemini / default assistant: start listening straight away
        for (String action : new String[]{Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST, RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE}) {
            try {
                c.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        }
        if (!Apps.launchPkg(c, GEMINI)) Toast.makeText(c, "Gemini isn't installed on this box", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ navigation

    static String navPkg() {
        String p = Store.str(Store.NAV_PKG, null);
        if (p != null && Apps.installed(p)) return p;
        for (String n : NAV_APPS) if (Apps.byPkg(n) != null) return n;
        return null;
    }

    static void openNavigation(Context c) {
        String p = navPkg();
        if (p == null || !Apps.launchPkg(c, p)) AppPanels.appPicker("Choose navigation app", a -> {
            Store.put(Store.NAV_PKG, a.pkg);
            Apps.launch(c, a);
        });
    }

    /** Start turn-by-turn to an address in the chosen navigation app. */
    static void navigateTo(Context c, String address) {
        String p = navPkg();
        Intent i;
        if ("com.google.android.apps.maps".equals(p)) i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(address)));
        else if ("com.waze".equals(p)) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=" + Uri.encode(address) + "&navigate=yes"));
        else i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)));
        if (p != null) i.setPackage(p);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Toast.makeText(c, "No navigation app found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ------------------------------------------------------------------ calls

    static void call(Activity a, String number) {
        String mode = Store.str(Store.CALLS, "box");
        if ("connect".equals(mode)) {
            if (!Apps.launchPkg(a, Store.str(Store.BRIDGE_PKG, null))) AppPanels.connect();
            return;
        }
        if ("bt".equals(mode)) {
            String pkg = Store.str(Store.CALLS_BT_PKG, null);
            if (pkg != null) {
                Intent d = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    a.startActivity(d);
                    return;
                } catch (Exception ignored) {
                }
                if (Apps.launchPkg(a, pkg)) return;
            }
            String fb = Store.str(Store.CALLS_FALLBACK, "ask");
            if ("none".equals(fb)) return;
            if ("ask".equals(fb)) {
                Toast.makeText(a, "Bluetooth phone isn't available, using the box dialler", Toast.LENGTH_SHORT).show();
            }
        }
        if (a.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                a.startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        } else {
            a.requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 7);
        }
        try {
            a.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(a, "No dialler on this box", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ car connect

    static void carConnect(Context c, String which) {
        String key = "bridge".equals(which) ? Store.BRIDGE_PKG : "mirror".equals(which) ? Store.MIRROR_PKG : Store.NATIVE_PKG;
        String pkg = Store.str(key, null);
        if ("bridge".equals(which) && pkg == null) {
            Apps.App b = findBridge();
            if (b != null) pkg = b.pkg;
        }
        if (pkg != null && Apps.launchPkg(c, pkg)) {
            Sheets.close();
            return;
        }
        String title = "bridge".equals(which) ? "CarPlay / Android Auto app" : "mirror".equals(which) ? "Mirror link app" : "Native car shortcut";
        AppPanels.bridgePicker(title, key);
    }

    // ------------------------------------------------------------------ brightness & night

    static boolean canWriteSettings(Context c) {
        return Settings.System.canWrite(c);
    }

    static int brightness(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            return 180;
        }
    }

    static void setBrightness(Activity a, int v) {
        if (!Settings.System.canWrite(a)) {
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + a.getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            Settings.System.putInt(a.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(a.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(5, Math.min(255, v)));
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ connections at start

    static void connectionsAtStart(Activity a) {
        if (Store.flag(Store.CONN_TS, false) && Status.tailscaleInstalled()) Status.tailscale(true);
        if (Store.flag(Store.CONN_PHONE, false)) joinPhoneHotspot(a);
        if (Store.flag(Store.CONN_HOTSPOT, false)) {
            Status.refresh();
            if (!Status.hotspotOn) U.ui.postDelayed(() -> Status.tetherSettings(a), 4000);
        }
    }

    static void joinPhoneHotspot(Context c) {
        String ssid = Store.str(Store.CONN_PHONE_SSID, null);
        if (ssid == null || Build.VERSION.SDK_INT < 29) return;
        try {
            WifiNetworkSuggestion.Builder b = new WifiNetworkSuggestion.Builder().setSsid(ssid).setIsAppInteractionRequired(false);
            String pass = Store.str(Store.CONN_PHONE_PASS, null);
            if (pass != null && !pass.isEmpty()) b.setWpa2Passphrase(pass);
            List<WifiNetworkSuggestion> l = new ArrayList<>();
            l.add(b.build());
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wm.removeNetworkSuggestions(l);
            wm.addNetworkSuggestions(l);
        } catch (Exception ignored) {
        }
    }

    /** Keep Tailscale connected while the car is on. */
    static final Runnable tsWatch = new Runnable() {
        @Override
        public void run() {
            if (Store.flag(Store.CONN_TS, false) && Store.flag(Store.CONN_TS_RETRY, true) && Status.tailscaleInstalled() && !Status.vpnOn)
                Status.tailscale(true);
            U.ui.postDelayed(this, 60000);
        }
    };

    // ------------------------------------------------------------------ auto start

    static JSONArray autostart() {
        return Store.arr(Store.AUTOSTART);
    }

    static void addAutostart(String key) {
        JSONArray a = autostart();
        for (int i = 0; i < a.length(); i++) if (key.equals(a.optJSONObject(i).optString("key"))) return;
        JSONObject o = new JSONObject();
        try {
            o.put("key", key);
            o.put("wait", 3);
            o.put("mode", "bg");
        } catch (Exception ignored) {
        }
        a.put(o);
        Store.put(Store.AUTOSTART, a);
    }

    static boolean isAutostart(String key) {
        JSONArray a = autostart();
        for (int i = 0; i < a.length(); i++) if (key.equals(a.optJSONObject(i).optString("key"))) return true;
        return false;
    }

    /** Runs once per boot: opens each app in order, then returns home. */
    static void runAutostart(Activity act) {
        if (!Store.flag(Store.AUTOSTART_ON, true)) return;
        long bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (Math.abs(Store.lng("autostart_boot", 0) - bootAt) < 60000) return; // already ran this boot
        if (SystemClock.elapsedRealtime() > 10 * 60 * 1000L) {
            Store.put("autostart_boot", bootAt);
            return; // launcher restarted long after boot; don't surprise the driver
        }
        Store.put("autostart_boot", bootAt);
        JSONArray a = autostart();
        long delay = 1500;
        String last = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            final Apps.App app = Apps.get(o.optString("key"));
            if (app == null) continue;
            delay += o.optInt("wait", 3) * 1000L;
            U.ui.postDelayed(() -> Apps.launch(act, app), delay);
            if ("stay".equals(o.optString("mode"))) last = app.key;
            delay += 1500;
        }
        if (a.length() > 0 && "home".equals(Store.str(Store.AUTOSTART_SHOW, "home")) && last == null) {
            U.ui.postDelayed(() -> act.startActivity(new Intent(act, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)), delay + 2000);
        }
    }
}
