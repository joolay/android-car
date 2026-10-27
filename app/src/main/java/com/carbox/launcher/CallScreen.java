package com.carbox.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Full-screen incoming / in-call screen. */
public class CallScreen extends Activity {
    private FrameLayout root;
    private TextView timer;
    private boolean keypad;
    private final Runnable update = this::render;

    /** Bring the call screen up from anywhere (calling service, accessibility service or the launcher). */
    static void open(Context c) {
        if (MainActivity.I != null && MainActivity.I.isResumed2()) {
            CallCard.show(); // Car Launcher is in front: show the call inside the tile area
            return;
        }
        Context from = BackButtonService.I != null ? BackButtonService.I : MainActivity.I != null && MainActivity.I.isResumed2() ? MainActivity.I : c;
        try {
            from.startActivity(CallState.screenIntent(from));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        Store.init(this);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        setContentView(root);
        immersive();
        CallState.listen(update);
        render();
        U.ui.post(tick);
    }

    @Override
    protected void onDestroy() {
        CallState.unlisten(update);
        U.ui.removeCallbacks(tick);
        super.onDestroy();
    }

    private void immersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController ic = getWindow().getInsetsController();
            if (ic != null) {
                ic.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                ic.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (timer != null && CallState.activeSince > 0) timer.setText(elapsed() + " · " + CallState.via);
            U.ui.postDelayed(this, 1000);
        }
    };

    private static String elapsed() {
        long s = (SystemClock.elapsedRealtime() - CallState.activeSince) / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private void render() {
        if (CallState.state == CallState.NONE) {
            finish();
            return;
        }
        Context c = this;
        root.removeAllViews();
        timer = null;
        boolean ringing = CallState.state == CallState.RINGING;
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{ringing ? 0xFF16324F : 0xFF123D2A, U.BG, U.BG}));
        Widgets.Person p = new Widgets.Person();
        p.name = CallState.name == null ? "?" : CallState.name;
        p.number = CallState.number;

        if (ringing) {
            LinearLayout top = U.col(c);
            top.setGravity(Gravity.CENTER_HORIZONTAL);
            top.addView(avatar(c, p, 112), U.lpu(112, 112));
            top.addView(U.line(c, p.name, 34, U.TEXT, true), Widgets.top(16));
            if (CallState.number != null && !CallState.number.isEmpty() && !CallState.number.equals(p.name))
                top.addView(U.line(c, CallState.number, 16, U.TEXT2, false), Widgets.top(4));
            top.addView(U.line(c, "Incoming call · " + CallState.via, 13, U.SUB, false), Widgets.top(8));
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            tl.topMargin = U.u(44);
            root.addView(top, tl);
            LinearLayout btns = U.row(c);
            btns.addView(big(c, U.RED, "Decline", 135, CallState::decline));
            btns.addView(U.gap(c, 200, 1));
            btns.addView(big(c, U.GREEN, "Answer", 0, CallState::answer));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            bl.bottomMargin = U.u(36);
            root.addView(btns, bl);
            return;
        }

        // in call
        LinearLayout head = U.row(c);
        head.addView(avatar(c, p, 84), U.lpu(84, 84));
        LinearLayout t = U.col(c);
        t.addView(U.line(c, p.name, 30, U.TEXT, true));
        timer = U.line(c, CallState.state == CallState.DIALING ? "Calling… · " + CallState.via
                : CallState.state == CallState.HOLDING ? "On hold" : (CallState.activeSince > 0 ? elapsed() : "") + " · " + CallState.via, 16, U.MINT, false);
        t.addView(timer, Widgets.top(4));
        LinearLayout.LayoutParams ttl = U.lp(U.WRAP, U.WRAP);
        ttl.leftMargin = U.u(20);
        head.addView(t, ttl);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.START);
        hl.setMargins(U.u(40), U.u(40), 0, 0);
        root.addView(head, hl);

        if (keypad && CallState.full()) {
            LinearLayout pad = U.col(c);
            String keys = "123456789*0#";
            for (int r = 0; r < 4; r++) {
                LinearLayout kr = U.row(c);
                for (int k = 0; k < 3; k++) {
                    char ch = keys.charAt(r * 3 + k);
                    TextView b = U.text(c, String.valueOf(ch), 24, U.TEXT, false);
                    b.setGravity(Gravity.CENTER);
                    b.setBackground(U.round(U.CARD3, 12));
                    b.setOnClickListener(v -> CallState.dtmf(ch));
                    LinearLayout.LayoutParams kl = U.lpu(84, 52);
                    kl.setMargins(U.u(4), U.u(4), U.u(4), U.u(4));
                    kr.addView(b, kl);
                }
                pad.addView(kr);
            }
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.END | Gravity.TOP);
            pl.setMargins(0, U.u(24), U.u(30), 0);
            root.addView(pad, pl);
        }

        LinearLayout ctl = U.row(c);
        if (CallState.full()) {
            ctl.addView(small(c, "Mute", R.drawable.ic_mute, CallState.muted, () -> CallState.setMuted(!CallState.muted)));
            ctl.addView(small(c, "Keypad", R.drawable.ic_apps, keypad, () -> {
                keypad = !keypad;
                render();
            }));
            ctl.addView(small(c, CallState.audioLabel(), R.drawable.ic_volup, false, CallState::toggleAudio));
            ctl.addView(small(c, CallState.state == CallState.HOLDING ? "Resume" : "Hold", R.drawable.ic_pause, CallState.state == CallState.HOLDING,
                    () -> CallState.setHold(CallState.state != CallState.HOLDING)));
        }
        ctl.addView(small(c, "Home", R.drawable.ic_home, false, this::goHome));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        cl.topMargin = U.u(keypad ? 300 : 190);
        if (keypad) {
            cl.gravity = Gravity.START | Gravity.TOP;
            cl.leftMargin = U.u(30);
            cl.topMargin = U.u(160);
        }
        root.addView(ctl, cl);

        View end = big(c, U.RED, "End call", 135, CallState::hangup);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, keypad ? Gravity.BOTTOM | Gravity.START : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        el.bottomMargin = U.u(28);
        if (keypad) el.leftMargin = U.u(180);
        root.addView(end, el);
    }

    private void goHome() {
        startActivity(new android.content.Intent(this, MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        moveTaskToBack(true);
    }

    @Override
    public void onBackPressed() {
        if (CallState.state == CallState.RINGING) return; // answer or decline
        goHome();
    }

    private static View avatar(Context c, Widgets.Person p, float size) {
        if (CallState.photo != null) {
            FrameLayout f = new FrameLayout(c);
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(CallState.photo);
            f.addView(iv, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            f.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline o) {
                    o.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            f.setClipToOutline(true);
            return f;
        }
        return Widgets.avatar(c, p, size);
    }

    private static View big(Context c, int color, String label, float rotate, Runnable r) {
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout b = new FrameLayout(c);
        b.setBackground(U.oval(color));
        b.setElevation(U.uf(6));
        ImageView iv = U.icon(c, R.drawable.ic_call, U.TEXT);
        iv.setRotation(rotate);
        b.addView(iv, U.flpu(40, 40, Gravity.CENTER));
        b.setOnClickListener(v -> r.run());
        U.press(b);
        l.addView(b, U.lpu(92, 92));
        l.addView(U.text(c, label, 15, U.TEXT, false), Widgets.top(8));
        return l;
    }

    private static View small(Context c, String label, int icon, boolean on, Runnable r) {
        LinearLayout l = U.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout b = new FrameLayout(c);
        b.setBackground(U.oval(on ? U.TEXT : U.CARD3));
        b.addView(U.icon(c, icon, on ? 0xFF000000 : U.TEXT), U.flpu(26, 26, Gravity.CENTER));
        b.setOnClickListener(v -> r.run());
        l.addView(b, U.lpu(66, 66));
        l.addView(U.line(c, label, 13, U.TEXT2, false), Widgets.top(6));
        LinearLayout.LayoutParams lp = U.lpu(110, U.WRAP);
        l.setLayoutParams(lp);
        return l;
    }
}
