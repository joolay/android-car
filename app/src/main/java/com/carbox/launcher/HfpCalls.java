package com.carbox.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Third way to see Bluetooth calls: the box's own Bluetooth hands-free stack (the car side of a
 * phone link) announces every call change. This catches calls even when the BT Phone app shows
 * no notification. Needs the "Nearby devices" (Bluetooth) permission.
 */
final class HfpCalls {
    private static final String ACTION = "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED";
    private static final String EXTRA = "android.bluetooth.headsetclient.extra.CALL";
    private static boolean on;

    private HfpCalls() {
    }

    static void start(Context c) {
        if (on) return;
        on = true;
        try {
            c.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent i) {
                    try {
                        Object call = i.getParcelableExtra(EXTRA);
                        if (call == null) return;
                        int state = -1;
                        String number = null;
                        try {
                            state = (Integer) call.getClass().getMethod("getState").invoke(call);
                            number = (String) call.getClass().getMethod("getNumber").invoke(call);
                        } catch (Exception e) {
                            // hidden methods blocked: read them from its description instead
                            String s = call.toString();
                            Matcher m = Pattern.compile("mState: ?(\\w+)").matcher(s);
                            if (m.find()) state = stateFromName(m.group(1));
                            Matcher n = Pattern.compile("mNumber: ?([+0-9*#]+)").matcher(s);
                            if (n.find()) number = n.group(1);
                        }
                        final int st = state;
                        final String num = number;
                        U.ui.post(() -> CallState.fromHfp(ctx, st, num));
                    } catch (Exception ignored) {
                    }
                }
            }, new IntentFilter(ACTION));
        } catch (Exception ignored) {
        }
    }

    private static int stateFromName(String s) {
        switch (s.toUpperCase(java.util.Locale.ROOT)) {
            case "0": case "ACTIVE": return 0;
            case "1": case "HELD": return 1;
            case "2": case "DIALING": return 2;
            case "3": case "ALERTING": return 3;
            case "4": case "INCOMING": return 4;
            case "5": case "WAITING": return 5;
            case "6": case "HELD_BY_RESPONSE_AND_HOLD": return 6;
            case "7": case "TERMINATED": return 7;
            default: return -1;
        }
    }
}
