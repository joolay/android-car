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
            shown.setTag(CallCard.class); // so the navigation card stays underneath
            a.tileArea.addView(shown, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH)); // on top of tiles and panels
            CallState.listen(update);
            Ui.ui.post(tick);
        }
        render();
    }

    static void hide() {
        if (shown == null) return;
        CallState.unlisten(update);
        Ui.ui.removeCallbacks(tick);
        if (shown.getParent() != null) ((android.view.ViewGroup) shown.getParent()).removeView(shown);
        shown = null;
        keypad = false;
    }

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (status != null && CallState.state == CallState.ACTIVE && CallState.activeSince > 0) status.setText(elapsed() + " · " + CallState.via);
            Ui.ui.postDelayed(this, 1000);
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
        shown.setBackground(Ui.round(ringing ? 0xFF16324F : 0xFF123D2A, 16));

        LinearLayout col = Ui.col(c);
        col.setGravity(Gravity.CENTER);
        Ui.pad(col, 16, 12, 16, 12);
        Widgets.Person p = new Widgets.Person();
        p.name = CallState.name == null ? "Unknown" : CallState.name;
        p.number = CallState.number;
        if (CallState.photo != null) {
            android.widget.ImageView iv = new android.widget.ImageView(c);
            iv.setImageBitmap(CallState.photo);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setBackground(Ui.oval(Ui.CARD3));
            col.addView(iv, Ui.lpu(72, 72));
        } else col.addView(Widgets.avatar(c, p, 72), Ui.lpu(72, 72));
        TextView nm = Ui.line(c, p.name, 24, Ui.TEXT, true);
        nm.setGravity(Gravity.CENTER);
        col.addView(nm, Widgets.top(10));
        if (CallState.number != null && !CallState.number.isEmpty() && !CallState.number.equals(p.name)) {
            TextView num = Ui.line(c, CallState.number, 14, Ui.TEXT2, false);
            num.setGravity(Gravity.CENTER);
            col.addView(num, Widgets.top(2));
        }
        status = Ui.line(c, ringing ? "Incoming call · " + CallState.via
                : CallState.state == CallState.DIALING ? "Calling… · " + CallState.via
                : CallState.state == CallState.HOLDING ? "On hold" : elapsed() + " · " + CallState.via, 13, ringing ? Ui.SUB : Ui.MINT, false);
        status.setGravity(Gravity.CENTER);
        col.addView(status, Widgets.top(4));

        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.CENTER);
        if (ringing) {
            btns.addView(round(c, Ui.RED, R.drawable.ic_call, 135, CallState::decline), Ui.lpu(64, 64));
            btns.addView(Ui.gap(c, 90, 1));
            btns.addView(round(c, Ui.GREEN, R.drawable.ic_call, 0, CallState::answer), Ui.lpu(64, 64));
        } else {
            if (CallState.full()) {
                btns.addView(chip(c, R.drawable.ic_mute, CallState.muted, () -> CallState.setMuted(!CallState.muted)), Ui.lpu(48, 48));
                btns.addView(Ui.gap(c, 14, 1));
                btns.addView(chip(c, R.drawable.ic_volup, false, CallState::toggleAudio), Ui.lpu(48, 48));
                btns.addView(Ui.gap(c, 14, 1));
            } else {
                // a BT Phone call we can't fully control: one tap to its own screen
                btns.addView(chip(c, R.drawable.ic_phone, false, CallState::openBtPhone), Ui.lpu(48, 48));
                btns.addView(Ui.gap(c, 14, 1));
            }
            btns.addView(round(c, Ui.RED, R.drawable.ic_call, 135, CallState::hangup), Ui.lpu(64, 64));
        }
        col.addView(btns, Widgets.top(14));
        shown.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    private static View round(Context c, int color, int icon, float rotate, Runnable r) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Ui.oval(color));
        View iv = Ui.icon(c, icon, Ui.TEXT);
        iv.setRotation(rotate);
        f.addView(iv, Ui.flpu(28, 28, Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        Ui.press(f);
        return f;
    }

    private static View chip(Context c, int icon, boolean on, Runnable r) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Ui.oval(on ? Ui.TEXT : 0x33FFFFFF));
        f.addView(Ui.icon(c, icon, on ? Ui.BG : Ui.TEXT), Ui.flpu(22, 22, Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        Ui.press(f);
        return f;
    }
}
