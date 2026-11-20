package com.carbox.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/** Buttons for the floating back button's hold menu. */
final class QuickActions {
    private QuickActions() {
    }

    /** id, label, colour */
    static final Object[][] ALL = {
            {"home", "Home", R.drawable.ic_home, 0xFF3D8BFD},
            {"split", "Split screen", R.drawable.ic_split, 0xFF5A3A9A},
            {"dial", "Dialler", R.drawable.ic_call, 0xFF34C759},
            {"assistant", "Voice assistant", R.drawable.ic_mic, 0xFF1F4E9A},
            {"playpause", "Play / pause", R.drawable.ic_playpause, 0xFFE5484D},
            {"next", "Next track", R.drawable.ic_next, 0xFFE5484D},
            {"prev", "Previous track", R.drawable.ic_prev, 0xFFE5484D},
            {"volup", "Volume up", R.drawable.ic_volup, 0xFFB7791F},
            {"voldown", "Volume down", R.drawable.ic_voldown, 0xFFB7791F},
            {"mute", "Mute", R.drawable.ic_mute, 0xFFB7791F},
            {"nav", "Navigation app", R.drawable.ic_navarrow, 0xFF0F6E56},
            {"music", "Music app", R.drawable.ic_music, 0xFF1DB954},
            {"connect", "Car Connect", R.drawable.ic_cast, 0xFF1F4E9A},
            {"lastapp", "Last app", R.drawable.ic_swap, 0xFF2E3747},
            {"recents", "Recent apps", R.drawable.ic_recents, 0xFF2E3747},
            {"notifications", "Notifications", R.drawable.ic_bell, 0xFF2E3747},
            {"screenshot", "Screenshot", R.drawable.ic_screenshot, 0xFF2E3747},
            {"screenoff", "Screen off", R.drawable.ic_power, 0xFF2E3747}};

    static final String[] DEFAULT = {"home", "split", "dial", "assistant", "playpause", "next"};
    static final int MIN = 3, MAX = 8;

    static Object[] find(String id) {
        for (Object[] a : ALL) if (a[0].equals(id)) return a;
        return null;
    }

    static List<String> chosen() {
        List<String> l = Prefs.has("fb_menu") ? Prefs.list("fb_menu") : new ArrayList<>(java.util.Arrays.asList(DEFAULT));
        List<String> ok = new ArrayList<>();
        for (String s : l) if (find(s) != null && !ok.contains(s)) ok.add(s);
        if (ok.size() < MIN) for (String d : DEFAULT) if (ok.size() < MIN && !ok.contains(d)) ok.add(d);
        while (ok.size() > MAX) ok.remove(ok.size() - 1);
        return ok;
    }

    static void run(AccessibilityService s, String id) {
        Context c = s;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        switch (id) {
            case "home":
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                break;
            case "split":
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
                break;
            case "recents":
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                break;
            case "lastapp":
                // double "recents" switches to the previous app on standard Android
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                Ui.ui.postDelayed(() -> s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS), 250);
                break;
            case "notifications":
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
                break;
            case "screenshot":
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
                break;
            case "screenoff":
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                break;
            case "dial":
                // Car Launcher's own phone screen (contacts, keypad)
                c.startActivity(new android.content.Intent(c, MainActivity.class).putExtra("open", "phone")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                break;
            case "assistant":
                Actions.assistant(c);
                break;
            case "playpause":
                key(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            case "next":
                key(am, KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case "prev":
                key(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case "volup":
                if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                break;
            case "voldown":
                if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                break;
            case "mute":
                if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                break;
            case "nav":
                Actions.openNavigation(c);
                break;
            case "music":
                if (!Music.open(c)) key(am, KeyEvent.KEYCODE_MEDIA_PLAY);
                break;
            case "connect": {
                Apps.App a = Actions.connectApp("carplay");
                if (a != null) Apps.launch(c, a);
                break;
            }
        }
    }

    private static void key(AudioManager am, int code) {
        if (am == null) return;
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }
}
