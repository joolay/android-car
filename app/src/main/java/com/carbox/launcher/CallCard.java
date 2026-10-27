package com.carbox.launcher;

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The call, shown inside the 2×4 tile area while Car Launcher is on screen (incoming, dialling,
 * in a call). Map, music, rail and dock stay visible. When another app is in front, the
 * full-screen CallScreen is used instead.
 */
final class CallCard {
    private static FrameLayout shown;
    private static TextView status;
    private static boolean keypad;
    private static final Runnable update = CallCard::render;

    private CallCard() {
    }

    static void show() {
        MainActivity a = MainActivity.I;
        if (a == null || CallState.state == CallState.NONE) return;
        if (shown == null) {
            shown = new FrameLayout(a);
            shown.setClickable(true);
            a.tileArea.addView(shown, new FrameLayout.LayoutParams(U.MATCH, U.MATCH)); // on top of tiles and panels
            CallState.listen(update);
            U.ui.post(tick);
        }
        render();
    }

    static void hide() {
        if (shown == null) return;
        CallState.unlisten(update);
        U.ui.removeCallbacks(tick);
        if (shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
        keypad = false;
    }

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (status != null && CallState.state == CallState.ACTIVE && CallState.activeSince > 0) status.setText(elapsed() + " · " + CallState.via);
            U.ui.postDelayed(this, 1000);
        }
    };

    private static String elapsed() {
        long s = (SystemClock.elapsedRealtime() - CallState.activeSince) / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private static void render() {
        if (CallState.state == CallState.NONE) {
            hide();
            return;
        }
        Context c = shown.getContext();
        shown.removeAllViews();
        boolean ringing = CallState.state == CallState.RINGING;
        shown.setBackground(U.round(ringing ? 0xFF16324F : 0xFF123D2A, 16));

        LinearLayout col = U.col(c);
        col.setGravity(Gravity.CENTER);
        U.pad(col, 16, 12, 16, 12);
        Widgets.Person p = new Widgets.Person();
        p.name = CallState.name == null ? "Unknown" : CallState.name;
        p.number = CallState.number;
        if (CallState.photo != null) {
            android.widget.ImageView iv = new android.widget.ImageView(c);
            iv.setImageBitmap(CallState.photo);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setBackground(U.oval(U.CARD3));
            col.addView(iv, U.lpu(72, 72));
        } else col.addView(Widgets.avatar(c, p, 72), U.lpu(72, 72));
        TextView nm = U.line(c, p.name, 24, U.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        col.addView(nm, Widgets.top(10));
        if (CallState.number != null && !CallState.number.isEmpty() && !CallState.number.equals(p.name)) {
            TextView num = U.line(c, CallState.number, 14, U.TEXT2, false);
            num.setGravity(Gravity.CENTER);
            col.addView(num, Widgets.top(2));
        }
        status = U.line(c, ringing ? "Incoming call · " + CallState.via
                : CallState.state == CallState.DIALING ? "Calling… · " + CallState.via
                : CallState.state == CallState.HOLDING ? "On hold" : elapsed() + " · " + CallState.via, 13, ringing ? U.SUB : U.MINT, false);
        status.setGravity(Gravity.CENTER);
        col.addView(status, Widgets.top(4));

        LinearLayout btns = U.row(c);
        btns.setGravity(Gravity.CENTER);
        if (ringing) {
            btns.addView(round(c, U.RED, R.drawable.ic_call, 135, CallState::decline), U.lpu(64, 64));
            btns.addView(U.gap(c, 90, 1));
            btns.addView(round(c, U.GREEN, R.drawable.ic_call, 0, CallState::answer), U.lpu(64, 64));
        } else {
            if (CallState.full()) {
                btns.addView(chip(c, R.drawable.ic_mute, CallState.muted, () -> CallState.setMuted(!CallState.muted)), U.lpu(48, 48));
                btns.addView(U.gap(c, 14, 1));
                btns.addView(chip(c, R.drawable.ic_volup, false, CallState::toggleAudio), U.lpu(48, 48));
                btns.addView(U.gap(c, 14, 1));
            } else {
                // a BT Phone call we can't fully control: one tap to its own screen
                btns.addView(chip(c, R.drawable.ic_phone, false, CallState::openBtPhone), U.lpu(48, 48));
                btns.addView(U.gap(c, 14, 1));
            }
            btns.addView(round(c, U.RED, R.drawable.ic_call, 135, CallState::hangup), U.lpu(64, 64));
        }
        col.addView(btns, Widgets.top(14));
        shown.addView(col, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
    }

    private static View round(Context c, int color, int icon, float rotate, Runnable r) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(U.oval(color));
        View iv = U.icon(c, icon, U.TEXT);
        iv.setRotation(rotate);
        f.addView(iv, U.flpu(28, 28, Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        U.press(f);
        return f;
    }

    private static View chip(Context c, int icon, boolean on, Runnable r) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(U.oval(on ? U.TEXT : 0x33FFFFFF));
        f.addView(U.icon(c, icon, on ? U.BG : U.TEXT), U.flpu(22, 22, Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        U.press(f);
        return f;
    }
}
