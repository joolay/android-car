package com.carbox.launcher;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.VideoProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The call on screen. Two sources:
 * 1) Android's calling system, when Car Launcher is the box's Phone app (full control), or
 * 2) BT Phone's call notification, whose own Answer / Decline / Hang up buttons we press.
 */
final class CallState {
    private CallState() {
    }

    static final int NONE = 0, RINGING = 1, DIALING = 2, ACTIVE = 3, HOLDING = 4;

    static int state = NONE;
    static String name, number;
    static Bitmap photo;
    static long activeSince;
    static boolean muted;
    static String via = "Bluetooth";

    // source 1
    static Call call;
    static InCallService inCall;
    // source 2
    static String notifKey;
    static PendingIntent answerPI, declinePI, hangupPI, openPI;
    // source 3: the Bluetooth hands-free stack (HfpCalls)
    static boolean hfp;

    private static final List<Runnable> listeners = new ArrayList<>();

    static void listen(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
    }

    static void unlisten(Runnable r) {
        listeners.remove(r);
    }

    static void changed() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    static boolean full() {
        return call != null;
    }

    // ------------------------------------------------------------------ actions

    // source 4: BT Phone's own screen, read by the accessibility service (BtAuto)
    static boolean screen;

    static void fromScreen(Context c, String who, boolean ringing) {
        if (call != null || (notifKey != null && !screen)) return; // a better source has it
        int before = state;
        screen = true;
        String contact = PhoneScreen.lookup(c, who);
        boolean isNumber = who.replaceAll("[^0-9]", "").length() >= 6;
        name = contact != null ? contact : who;
        number = isNumber ? who : "";
        via = "Bluetooth";
        state = ringing ? RINGING : ACTIVE;
        if (state == ACTIVE && before != ACTIVE) activeSince = android.os.SystemClock.elapsedRealtime();
        if (before == state && name.equals(who)) return;
        changed();
        if (before == NONE || (before == RINGING) != ringing) CallScreen.open(c);
    }

    static void screenGone() {
        if (screen && call == null) {
            screen = false;
            clear();
        }
    }

    static void answer() {
        if (screen && BtAuto.press("answer")) return;
        if (call != null) call.answer(VideoProfile.STATE_AUDIO_ONLY);
        else if (answerPI != null) send(answerPI);
        else openBtPhone(); // no button we can press: open BT Phone to answer there
    }

    /** Open the box's BT Phone app (its call screen), for calls we can see but not control. */
    static void openBtPhone() {
        if (openPI != null) {
            send(openPI);
            return;
        }
        String bt = Actions.btDialler();
        if (bt != null && MainActivity.I != null) Apps.launchPkg(MainActivity.I, bt);
    }

    static void decline() {
        if (screen && BtAuto.press("decline")) return;
        if (call != null) {
            if (call.getState() == Call.STATE_RINGING) call.reject(false, null);
            else call.disconnect();
        } else if (declinePI != null || hangupPI != null) send(declinePI != null ? declinePI : hangupPI);
        else openBtPhone();
    }

    static void hangup() {
        if (screen && BtAuto.press("hangup")) return;
        if (call != null) call.disconnect();
        else if (hangupPI != null || declinePI != null) send(hangupPI != null ? hangupPI : declinePI);
        else openBtPhone();
    }

    static void setMuted(boolean m) {
        if (inCall != null) inCall.setMuted(m);
        muted = m;
        changed();
    }

    static void setHold(boolean h) {
        if (call == null) return;
        if (h) call.hold();
        else call.unhold();
    }

    static void dtmf(char c) {
        if (call == null) return;
        call.playDtmfTone(c);
        U.ui.postDelayed(call::stopDtmfTone, 150);
    }

    /** Switch the call's sound between the car (box) and the phone itself. */
    static void toggleAudio() {
        if (inCall == null) return;
        CallAudioState a = inCall.getCallAudioState();
        if (a == null) return;
        int route = a.getRoute();
        int supported = a.getSupportedRouteMask();
        int next;
        if (route == CallAudioState.ROUTE_BLUETOOTH) next = (supported & CallAudioState.ROUTE_SPEAKER) != 0 ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_EARPIECE;
        else next = (supported & CallAudioState.ROUTE_BLUETOOTH) != 0 ? CallAudioState.ROUTE_BLUETOOTH : CallAudioState.ROUTE_SPEAKER;
        inCall.setAudioRoute(next);
    }

    static String audioLabel() {
        if (inCall == null || inCall.getCallAudioState() == null) return "Audio";
        int r = inCall.getCallAudioState().getRoute();
        return r == CallAudioState.ROUTE_BLUETOOTH ? "Bluetooth" : r == CallAudioState.ROUTE_SPEAKER ? "Car speaker" : r == CallAudioState.ROUTE_EARPIECE ? "Phone" : "Audio";
    }

    private static void send(PendingIntent pi) {
        if (pi == null) return;
        try {
            pi.send();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ source 1: Android calling system

    static void fromTelecom(InCallService svc, Call c) {
        inCall = svc;
        call = c;
        notifKey = null;
        String num = c.getDetails().getHandle() != null ? c.getDetails().getHandle().getSchemeSpecificPart() : "";
        number = num;
        String disp = c.getDetails().getCallerDisplayName();
        String contact = MainActivity.I != null ? PhoneScreen.lookup(svc, num) : null;
        name = contact != null ? contact : disp != null && !disp.isEmpty() ? disp : num.isEmpty() ? "Unknown" : num;
        photo = Widgets.photo(svc, PhoneScreen.photoFor(svc, num));
        updateTelecom();
    }

    static void updateTelecom() {
        if (call == null) return;
        int s = call.getState();
        int before = state;
        state = s == Call.STATE_RINGING ? RINGING : s == Call.STATE_DIALING || s == Call.STATE_CONNECTING ? DIALING
                : s == Call.STATE_ACTIVE ? ACTIVE : s == Call.STATE_HOLDING ? HOLDING : s == Call.STATE_DISCONNECTED ? NONE : state;
        if (state == ACTIVE && before != ACTIVE && activeSince == 0) activeSince = SystemClock.elapsedRealtime();
        if (state == NONE) clear();
        else changed();
    }

    // ------------------------------------------------------------------ source 2: BT Phone's notification

    private static final String[] ANSWER = {"answer", "accept", "pick up", "接听", "接聽"};
    private static final String[] DECLINE = {"decline", "reject", "dismiss", "ignore", "拒绝", "拒接"};
    private static final String[] HANGUP = {"hang up", "hangup", "end", "挂断", "掛斷"};

    /** True if this notification looks like a call from the box's Bluetooth phone app. */
    static boolean isCallNotification(Context c, String pkg, Notification n) {
        String bt = Actions.btDialler();
        if (pkg.equals(bt)) log(pkg, n); // keep a record, to see what this box's BT Phone sends
        if (Notification.CATEGORY_CALL.equals(n.category)) return true;
        if (pkg.equals(bt)) {
            if (n.actions != null)
                for (Notification.Action a : n.actions) if (a.title != null && (match(a.title, ANSWER) || match(a.title, HANGUP) || match(a.title, DECLINE))) return true;
            // some BT Phone apps post a plain "Incoming call" / caller notification with no buttons
            CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE), x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            String all = ((t == null ? "" : t) + " " + (x == null ? "" : x)).toLowerCase(Locale.ROOT);
            if (looksLikeStatus(all) || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0 && all.replaceAll("[^0-9]", "").length() >= 6) return true;
        }
        return false;
    }

    /** The last few notifications from BT Phone (Settings → Calls shows them). */
    private static void log(String pkg, Notification n) {
        try {
            CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE), x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            StringBuilder a = new StringBuilder();
            if (n.actions != null) for (Notification.Action ac : n.actions) a.append(a.length() > 0 ? ", " : "").append(ac.title);
            String line = new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date()) + "  " + (n.category == null ? "-" : n.category)
                    + " · " + t + " · " + x + (a.length() > 0 ? " · buttons: " + a : " · no buttons");
            java.util.List<String> l = new ArrayList<>(Store.list("call_log"));
            l.add(0, line);
            while (l.size() > 6) l.remove(l.size() - 1);
            Store.putList("call_log", l);
        } catch (Exception ignored) {
        }
    }

    /** Calls seen by the Bluetooth hands-free stack. HFP states: 0 active, 1 held, 2 dialling, 3 alerting, 4 incoming, 5 waiting, 6 held, 7 ended. */
    static void fromHfp(Context c, int hfpState, String num) {
        if (call != null || hfpState < 0) return; // the calling system has it
        if (hfpState == 7) {
            if (hfp && notifKey == null) clear();
            hfp = false;
            return;
        }
        hfp = true;
        int before = state;
        state = hfpState == 4 || hfpState == 5 ? RINGING : hfpState == 2 || hfpState == 3 ? DIALING : hfpState == 0 ? ACTIVE : HOLDING;
        if (notifKey == null) { // a notification, if any, already filled in the caller
            number = num == null ? "" : num;
            String contact = number.isEmpty() ? null : PhoneScreen.lookup(c, number);
            name = contact != null ? contact : number.isEmpty() ? "Unknown" : number;
            photo = Widgets.photo(c, PhoneScreen.photoFor(c, number));
            via = "Bluetooth";
        }
        if (state == ACTIVE && before != ACTIVE) activeSince = android.os.SystemClock.elapsedRealtime();
        changed();
        if (before == NONE || (before == RINGING) != (state == RINGING)) CallScreen.open(c);
    }

    static void fromNotification(Context c, String key, String pkg, Notification n) {
        if (call != null) return; // the calling system already gave us this call
        PendingIntent ans = null, dec = null, hang = null;
        if (n.actions != null) for (Notification.Action a : n.actions) {
            if (a.title == null) continue;
            if (ans == null && match(a.title, ANSWER)) ans = a.actionIntent;
            else if (dec == null && match(a.title, DECLINE)) dec = a.actionIntent;
            else if (hang == null && match(a.title, HANGUP)) hang = a.actionIntent;
        }
        notifKey = key;
        openPI = n.contentIntent; // tapping it opens BT Phone's own call screen
        answerPI = ans;
        declinePI = dec;
        hangupPI = hang;
        CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE), x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        String title = t == null ? "" : t.toString(), text = x == null ? "" : x.toString();
        // BT Phone usually puts the caller in the title and "Incoming call" / a number in the text (or the other way round)
        String who = looksLikeStatus(title) ? text : title;
        String other = who.equals(title) ? text : title;
        String num = who.replaceAll("[^0-9+]", "").length() >= 6 ? who : other.replaceAll("[^0-9+]", "").length() >= 6 ? other : "";
        number = num.replaceAll("[^0-9+ ]", "").trim();
        String contact = PhoneScreen.lookup(c, number);
        name = contact != null ? contact : who.isEmpty() ? "Unknown" : who;
        photo = Widgets.photo(c, PhoneScreen.photoFor(c, number));
        if (photo == null && n.getLargeIcon() != null) {
            try {
                android.graphics.drawable.Drawable d = n.getLargeIcon().loadDrawable(c);
                if (d instanceof android.graphics.drawable.BitmapDrawable) photo = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            } catch (Exception ignored) {
            }
        }
        via = Apps.label(pkg);
        int before = state;
        String all = (title + " " + text).toLowerCase(Locale.ROOT);
        state = ans != null || all.contains("incoming") || all.contains("来电") ? RINGING : ACTIVE;
        if (state == ACTIVE && before != ACTIVE) activeSince = SystemClock.elapsedRealtime();
        changed();
        if (before == NONE || (before == RINGING) != (state == RINGING)) CallScreen.open(c);
    }

    static void notificationRemoved(String key) {
        if (call == null && key.equals(notifKey) && !hfp) clear();
        else if (key.equals(notifKey)) notifKey = null;
    }

    private static boolean looksLikeStatus(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return l.contains("incoming") || l.contains("call") || l.contains("来电") || l.contains("calling") || l.contains("ringing");
    }

    private static boolean match(CharSequence title, String[] words) {
        String l = title.toString().toLowerCase(Locale.ROOT);
        for (String w : words) if (l.contains(w)) return true;
        return false;
    }

    static void clear() {
        state = NONE;
        call = null;
        notifKey = null;
        answerPI = declinePI = hangupPI = openPI = null;
        hfp = false;
        screen = false;
        activeSince = 0;
        muted = false;
        changed();
    }

    static Intent screenIntent(Context c) {
        return new Intent(c, CallScreen.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }
}
