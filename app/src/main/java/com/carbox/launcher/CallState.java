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
    static PendingIntent answerPI, declinePI, hangupPI;

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

    static void answer() {
        if (call != null) call.answer(VideoProfile.STATE_AUDIO_ONLY);
        else send(answerPI);
    }

    static void decline() {
        if (call != null) {
            if (call.getState() == Call.STATE_RINGING) call.reject(false, null);
            else call.disconnect();
        } else send(declinePI != null ? declinePI : hangupPI);
    }

    static void hangup() {
        if (call != null) call.disconnect();
        else send(hangupPI != null ? hangupPI : declinePI);
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
        if (Notification.CATEGORY_CALL.equals(n.category)) return true;
        String bt = Actions.btDialler();
        if (pkg.equals(bt) && n.actions != null) {
            for (Notification.Action a : n.actions) if (a.title != null && match(a.title, ANSWER) || a.title != null && match(a.title, HANGUP)) return true;
        }
        return false;
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
        if (ans == null && dec == null && hang == null) return; // nothing we can press
        notifKey = key;
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
        state = ans != null ? RINGING : ACTIVE;
        if (state == ACTIVE && before != ACTIVE) activeSince = SystemClock.elapsedRealtime();
        changed();
        if (before == NONE || (before == RINGING) != (state == RINGING)) CallScreen.open(c);
    }

    static void notificationRemoved(String key) {
        if (call == null && key.equals(notifKey)) clear();
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
        answerPI = declinePI = hangupPI = null;
        activeSince = 0;
        muted = false;
        changed();
    }

    static Intent screenIntent(Context c) {
        return new Intent(c, CallScreen.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }
}
