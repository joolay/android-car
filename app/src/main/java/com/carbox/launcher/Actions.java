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

    /** What the voice button did last (shown in Settings → Assistant, to see why it didn't work). */
    static void assistLog(String s) {
        Trace.event("Voice button: " + s.replace("\n", " / "));
        Prefs.put("assist_log", new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + "\n" + s);
    }

    private static void tryVoice(Context c, String[][] tries, int k, StringBuilder log) {
        for (int i = k; i < tries.length; i++) {
            String[] t = tries[i];
            if ("assist".equals(t[0])) { // Android's own "start the assistant" (not an app screen)
                if (!launchAssist(c)) {
                    log.append("Android's assistant pop-up: not available\n");
                    continue;
                }
                log.append("Asked Android for the assistant pop-up\n");
                final int next = i + 1;
                Ui.ui.postDelayed(() -> { // the Call helper can see whether the pop-up is on screen
                    Boolean shown = assistShown();
                    if (Boolean.FALSE.equals(shown)) {
                        log.append("  …nothing appeared\n");
                        tryVoice(c, tries, next, log);
                    } else assistLog(log.append(shown == null ? "(can't check without the Call helper)" : "It's on screen").toString());
                }, 1500);
                return;
            }
            String name = t[0].substring(t[0].lastIndexOf('.') + 1) + " in " + (t[1] == null ? "the default assistant" : Apps.label(t[1]));
            try {
                Intent in = new Intent(t[0]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (t[1] != null) in.setPackage(t[1]);
                c.startActivity(in);
                log.append("Opened: ").append(name).append("\n");
                final int next = i + 1;
                Ui.ui.postDelayed(() -> { // came straight back to Home: that way doesn't work here
                    if (MainActivity.frontAt != 0 && android.os.SystemClock.elapsedRealtime() - MainActivity.frontAt < 1400) {
                        log.append("  …it closed at once\n");
                        tryVoice(c, tries, next, log);
                    } else assistLog(log.append("Stayed open").toString());
                }, 1600);
                return;
            } catch (Exception e) {
                log.append(name).append(": not available\n");
            }
        }
        boolean ok = Apps.launchPkg(c, GEMINI);
        assistLog(log.append(ok ? "Opened the Gemini app (tap its mic)" : "Couldn't open Gemini").toString());
    }

    /** Start Android's assistant the way the system's own button does (hidden in Android, so asked by name). */
    private static boolean launchAssist(Context c) {
        try {
            Object sm = c.getSystemService(Context.SEARCH_SERVICE);
            java.lang.reflect.Method m = sm.getClass().getMethod("launchAssist", android.os.Bundle.class);
            m.invoke(sm, new android.os.Bundle());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Is an assistant (Google / Gemini) on screen? null when it can't be told (no Call helper). */
    private static Boolean assistShown() {
        BackButtonService s = BackButtonService.I;
        if (s == null) return null;
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : s.getWindows()) {
                android.view.accessibility.AccessibilityNodeInfo r = w.getRoot();
                if (r == null || r.getPackageName() == null) continue;
                String p = r.getPackageName().toString();
                if (p.equals(GEMINI) || p.equals("com.google.android.googlequicksearchbox") || p.contains("assistant")) return true;
            }
            return false;
        } catch (Exception e) {
            return null;
        }
    }

    static void assistant(Context c) {
        if ("external".equals(Prefs.str(Prefs.ASSIST, "gemini"))) {
            String pkg = Prefs.str(Prefs.ASSIST_PKG, null);
            if (pkg != null && Apps.launchPkg(c, pkg)) {
                assistLog("Set to External: opened " + Apps.label(pkg) + " (choose Gemini in Settings → Assistant for Gemini)");
                return;
            }
            SettingsPanels.assistantPicker();
            return;
        }
        // Gemini, step by step: each way is tried, and if it comes straight back to the home screen
        // (the log showed Google's voice screen closing at once) the next one is tried, ending with
        // simply opening the Gemini app
        if (Apps.installed(GEMINI)) {
            // first the same thing the box's own voice button does: Android's assistant (the one set as
            // Digital assistant app in Android settings, e.g. Google / Gemini), as a pop-up
            String[][] tries = {{"assist", null}, {Intent.ACTION_VOICE_COMMAND, GEMINI}, {Intent.ACTION_ASSIST, GEMINI},
                    {Intent.ACTION_VOICE_COMMAND, "com.google.android.googlequicksearchbox"},
                    {"android.intent.action.VOICE_ASSIST", "com.google.android.googlequicksearchbox"}};
            tryVoice(c, tries, 0, new StringBuilder());
            return;
        } else assistLog("Gemini isn't installed on this box: tried the box's own assistant");
        // no Gemini: the box's default assistant, listening straight away
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
        String p = Prefs.str(Prefs.NAV_PKG, null);
        if (p != null && Apps.installed(p)) return p;
        for (String n : NAV_APPS) if (Apps.byPkg(n) != null) return n;
        return null;
    }

    static void openNavigation(Context c) {
        String p = navPkg();
        if (p == null || !Apps.launchPkg(c, p)) AppPanels.appPicker("Choose navigation app", a -> {
            Prefs.put(Prefs.NAV_PKG, a.pkg);
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

    /** Car-box phone apps read the number from different places: give it in all the usual ones. */
    static Intent withNumber(Intent i, String number) {
        String n = number.replaceAll("[^0-9+*#]", "");
        return i.putExtra("number", n).putExtra("phone_number", n).putExtra("phoneNumber", n).putExtra("tel", n)
                .putExtra("android.intent.extra.PHONE_NUMBER", n).putExtra("dial_number", n).putExtra("num", n);
    }

    static void call(Activity a, String number) {
        String mode = Prefs.str(Prefs.CALLS, "bt");
        if ("connect".equals(mode)) {
            Apps.App app = connectApp("carplay");
            if (app == null || !Apps.launch(a, app)) AppPanels.connect();
            return;
        }
        if ("bt".equals(mode)) {
            // Car Launcher is the box's Phone app and Android has a Bluetooth calling account: call directly
            if (placeOverBluetooth(a, number)) return;
            String pkg = btDialler();
            if (pkg == null && !Prefs.flag("bt_asked", false)) {
                // first call and no BT phone app recognised: ask once which app it is, then dial
                Prefs.put("bt_asked", true);
                AppPanels.btPhonePicker(chosen -> {
                    Prefs.put(Prefs.CALLS_BT_PKG, chosen.pkg);
                    btReset();
                    Panels.close();
                    call(a, number);
                });
                return;
            }
            if (pkg != null && BtAuto.active() && Prefs.flag("bt_type", true)) {
                // BT Phone ignores numbers sent to it: open it and type the number in for you
                BtAuto.dial(a, number, pkg);
                return;
            }
            if (pkg != null) {
                // ask BT Phone to call straight away; if it only accepts "dial", it opens with the number filled in
                if (a.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        a.startActivity(withNumber(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))), number).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        return;
                    } catch (Exception ignored) {
                    }
                }
                Intent d = withNumber(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))), number).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    a.startActivity(d);
                    return;
                } catch (Exception ignored) {
                }
                if (Apps.launchPkg(a, pkg)) return;
            }
            String fb = Prefs.str(Prefs.CALLS_FALLBACK, "ask");
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

    static boolean isDefaultDialer(Context c) {
        android.telecom.TelecomManager tm = (android.telecom.TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
        return tm != null && c.getPackageName().equals(tm.getDefaultDialerPackage());
    }

    /** A Bluetooth (hands-free) calling account in Android's calling system, if the box has one. */
    static android.telecom.PhoneAccountHandle bluetoothAccount(Context c) {
        if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null;
        android.telecom.TelecomManager tm = (android.telecom.TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
        if (tm == null) return null;
        try {
            for (android.telecom.PhoneAccountHandle h : tm.getCallCapablePhoneAccounts()) {
                String p = h.getComponentName().getPackageName().toLowerCase(java.util.Locale.ROOT);
                String cls = h.getComponentName().getClassName().toLowerCase(java.util.Locale.ROOT);
                if (p.contains("bluetooth") || cls.contains("hfp") || cls.contains("bluetooth")) return h;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean placeOverBluetooth(Activity a, String number) {
        if (!isDefaultDialer(a) || a.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false;
        android.telecom.PhoneAccountHandle h = bluetoothAccount(a);
        if (h == null) return false;
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putParcelable(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, h);
            ((android.telecom.TelecomManager) a.getSystemService(Context.TELECOM_SERVICE)).placeCall(Uri.fromParts("tel", number, null), extras);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ car connect

    /** which: carplay | aa | mirror | native */
    static String connectKey(String which) {
        switch (which) {
            case "carplay":
                return Prefs.CARPLAY_PKG;
            case "aa":
                return Prefs.AA_PKG;
            case "mirror":
                return Prefs.MIRROR_PKG;
            default:
                return Prefs.NATIVE_PKG;
        }
    }

    static String connectTitle(String which) {
        switch (which) {
            case "carplay":
                return "Apple CarPlay";
            case "aa":
                return "Android Auto";
            case "mirror":
                return "Mirror Screen";
            default:
                return "Native Car";
        }
    }

    /** The app a Car Connect line opens: the one you chose, otherwise a good guess by name. */
    static Apps.App connectApp(String which) {
        Apps.App a = Apps.byPkg(Prefs.str(connectKey(which), null));
        if (a != null) return a;
        if ("carplay".equals(which) || "aa".equals(which)) {
            a = Apps.byPkg(Prefs.str(Prefs.BRIDGE_PKG, null));
            return a != null ? a : findBridge();
        }
        if ("mirror".equals(which)) return byLabel("mirror", false);
        return byLabel("car", true);
    }

    static Apps.App byLabel(String word, boolean exact) {
        for (Apps.App a : Apps.all) {
            String l = a.label.trim().toLowerCase(java.util.Locale.ROOT);
            if (exact ? l.equals(word) : l.equals(word)) return a;
        }
        if (!exact) for (Apps.App a : Apps.all) if (a.label.toLowerCase(java.util.Locale.ROOT).contains(word)) return a;
        return null;
    }

    /** The Bluetooth phone app: the one chosen in Calls, otherwise the box's own BT phone app found by name. */
    private static String btCache;
    private static long btCacheAt;

    /** BT Phone's package. Asked on every screen change by the accessibility service, so kept for 30 s. */
    static String btDialler() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (btCacheAt > 0 && now - btCacheAt < 30000) return btCache;
        btCache = findBtDialler();
        btCacheAt = now;
        return btCache;
    }

    static void btReset() {
        btCacheAt = 0;
    }

    private static String findBtDialler() {
        String p = Prefs.str(Prefs.CALLS_BT_PKG, null);
        if (p != null && Apps.installed(p)) return p;
        Apps.App a = guessBtPhone();
        return a == null ? null : a.pkg;
    }

    /** Car boxes name it "BT Phone", "Bluetooth", "Phone Link"… — but never the BT music player. */
    static Apps.App guessBtPhone() {
        Apps.App best = null;
        int bestScore = 0;
        for (Apps.App a : Apps.all) {
            String l = a.label.toLowerCase(java.util.Locale.ROOT), k = a.pkg.toLowerCase(java.util.Locale.ROOT);
            if (l.contains("music") || l.contains("audio") || l.contains("player") || k.contains("music")) continue;
            int score = 0;
            if (l.contains("bt phone") || l.contains("btphone") || l.contains("bluetooth phone") || l.contains("bt dial")) score = 5;
            else if (l.equals("bluetooth") || l.equals("bt") || l.contains("phone link")) score = 4;
            else if (k.contains("bluetooth") || k.contains(".bt.") || k.endsWith(".bt") || k.contains("btphone")) score = 3;
            else if (l.contains("bluetooth")) score = 2;
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    static boolean looksLikeBtPhone(Apps.App a) {
        String l = a.label.toLowerCase(java.util.Locale.ROOT), k = a.pkg.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("music") || l.contains("audio")) return false;
        return l.contains("bluetooth") || l.startsWith("bt") || l.contains("phone") || l.contains("dial") || k.contains("bluetooth") || k.contains(".bt");
    }

    static void carConnect(Context c, String which) {
        Apps.App a = connectApp(which);
        if (a != null && Apps.launch(c, a)) {
            Panels.close();
            return;
        }
        AppPanels.bridgePicker(connectTitle(which) + " app", connectKey(which));
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

    /** Connections that happen quietly in the background: Tailscale and joining the phone's hotspot. */
    static void connectionsAtStart(Activity a) {
        if (Prefs.flag(Prefs.CONN_TS, false) && Status.tailscaleInstalled()) Status.tailscale(true);
        if (Prefs.flag(Prefs.CONN_PHONE, false)) joinPhoneHotspot(a);
    }

    /**
     * Things that open other screens (hotspot settings, auto-start apps). Run only after the splash
     * screen has finished, once per car start, so they never cover the splash.
     */
    static void afterSplash(MainActivity a) {
        long bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        boolean firstThisBoot = Math.abs(Prefs.lng("hotspot_boot", 0) - bootAt) >= 60000;
        if (firstThisBoot && Prefs.flag(Prefs.CONN_HOTSPOT, false)) {
            Prefs.put("hotspot_boot", bootAt);
            Status.refresh();
            if (!Status.hotspotOn) {
                // Android 13 only lets Settings turn the hotspot on; auto start waits until you come back
                a.pendingAutostart = true;
                Ui.ui.postDelayed(() -> Status.tetherSettings(a), 1500);
                return;
            }
        }
        // auto-start apps wait for the music to get going (up to 15 s), so they don't interrupt it
        Ui.ui.postDelayed(() -> Music.whenStarted(a, () -> runAutostart(a), 15000), 1200);
    }

    static void joinPhoneHotspot(Context c) {
        String ssid = Prefs.str(Prefs.CONN_PHONE_SSID, null);
        if (ssid == null || Build.VERSION.SDK_INT < 29) return;
        try {
            WifiNetworkSuggestion.Builder b = new WifiNetworkSuggestion.Builder().setSsid(ssid).setIsAppInteractionRequired(false);
            String pass = Prefs.str(Prefs.CONN_PHONE_PASS, null);
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
            if (Prefs.flag(Prefs.CONN_TS, false) && Prefs.flag(Prefs.CONN_TS_RETRY, true) && Status.tailscaleInstalled() && !Status.vpnOn)
                Status.tailscale(true);
            Ui.ui.postDelayed(this, 60000);
        }
    };

    // ------------------------------------------------------------------ auto start

    static JSONArray autostart() {
        return Prefs.arr(Prefs.AUTOSTART);
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
        Prefs.put(Prefs.AUTOSTART, a);
    }

    static boolean isAutostart(String key) {
        JSONArray a = autostart();
        for (int i = 0; i < a.length(); i++) if (key.equals(a.optJSONObject(i).optString("key"))) return true;
        return false;
    }

    /** Runs once per boot: opens each app in order, then returns home. */
    static void runAutostart(Activity act) {
        if (!Prefs.flag(Prefs.AUTOSTART_ON, true)) return;
        long bootAt = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (Math.abs(Prefs.lng("autostart_boot", 0) - bootAt) < 60000) return; // already ran this boot
        if (SystemClock.elapsedRealtime() > 10 * 60 * 1000L) {
            Prefs.put("autostart_boot", bootAt);
            return; // launcher restarted long after boot; don't surprise the driver
        }
        Prefs.put("autostart_boot", bootAt);
        JSONArray a = autostart();
        long delay = 1500;
        String last = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            final Apps.App app = Apps.get(o.optString("key"));
            if (app == null) continue;
            delay += o.optInt("wait", 3) * 1000L;
            Ui.ui.postDelayed(() -> Apps.launch(act, app), delay);
            if ("stay".equals(o.optString("mode"))) last = app.key;
            delay += 1500;
        }
        if (a.length() > 0 && "home".equals(Prefs.str(Prefs.AUTOSTART_SHOW, "home")) && last == null) {
            Ui.ui.postDelayed(() -> act.startActivity(new Intent(act, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)), delay + 2000);
        }
    }
}
