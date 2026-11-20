package com.carbox.launcher;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The alternate stage: Go To at start, which turns (cube) to Drive after 15 s; swipe turns between them.
 * Next Prayer, Speed and Park here turn in from the other side for 5 s when needed.
 */
final class DriveStage extends FrameLayout {
    private static long born;
    private static boolean intro = true; // Go To shows first, once per run
    private final Context c;
    private final Widgets.W[] faces = new Widgets.W[2]; // 0 Go To, 1 Drive
    private int cur;
    private Widgets.W temp;
    private boolean busy;
    private long stoppedAt;
    private boolean parkShown, speedWas;
    private int prayerKey = -1, prayerStage;

    DriveStage(Context c) {
        super(c);
        this.c = c;
        if (born == 0) born = System.currentTimeMillis();
        cur = intro ? 0 : 1;
        for (int i = 0; i < 2; i++) {
            faces[i] = LeftStages.make(c, i == 0 ? "goto" : "drive");
            if (faces[i] != null && faces[i].v != null) addView(faces[i].v, new LayoutParams(Ui.MATCH, Ui.MATCH));
            if (i != cur && faces[i] != null) faces[i].v.setVisibility(GONE);
        }
    }

    /** 0 = Go To, 1 = Drive. */
    int index() {
        return cur;
    }

    /** Swipe: turn to the other face (dir 1 = next, -1 = previous). */
    void turn(int dir) {
        if (busy || temp != null) return;
        int to = 1 - cur;
        if (faces[cur] == null || faces[to] == null) return;
        busy = true;
        intro = false;
        final int from = cur;
        cur = to;
        Cube.turn(faces[from].v, faces[to].v, dir, () -> busy = false);
    }

    private void show(String id) {
        if (busy || temp != null || faces[cur] == null) return;
        try {
            temp = LeftStages.make(c, id);
        } catch (Exception e) {
            temp = null;
        }
        if (temp == null || temp.v == null) {
            temp = null;
            return;
        }
        busy = true;
        temp.v.setVisibility(GONE);
        addView(temp.v, new LayoutParams(Ui.MATCH, Ui.MATCH));
        final View old = faces[cur].v;
        final Widgets.W t = temp;
        Trace.event("stage " + id);
        Cube.turn(old, t.v, -1, () -> { // from the other side than a swipe
            busy = false;
            postDelayed(() -> back(t, old), 5000);
        });
    }

    private void back(final Widgets.W t, final View old) {
        if (temp != t) return;
        busy = true;
        Cube.turn(t.v, old, 1, () -> {
            removeView(t.v);
            temp = null;
            busy = false;
        });
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                step();
            } catch (Exception ignored) {
            }
            postDelayed(this, 1000);
        }
    };

    private void step() {
        for (Widgets.W f : faces) if (f != null) f.tick();
        if (temp != null) temp.tick();
        if (intro && System.currentTimeMillis() - born >= 15000) {
            intro = false;
            turn(1);
        }
        if (busy || temp != null) return;
        boolean over = SpeedLimit.over();
        if (over && !speedWas) show("speed");
        speedWas = over;
        if (Gps.moving()) {
            stoppedAt = 0;
            parkShown = false;
        } else {
            if (stoppedAt == 0) stoppedAt = System.currentTimeMillis();
            if (!parkShown && System.currentTimeMillis() - stoppedAt >= 120000) {
                parkShown = true;
                show("park");
                return;
            }
        }
        if (Faith.islam()) {
            if (Alerts.salahNow()) {
                if (prayerStage < 2) {
                    prayerStage = 2;
                    show("nextprayer");
                }
                return;
            }
            double[] n = Prayer.next();
            if (n != null) {
                int key = (int) n[0] + 10 * (int) n[1];
                if (key != prayerKey) {
                    prayerKey = key;
                    prayerStage = 0;
                }
                if (prayerStage < 1 && Prayer.msUntil(n) <= 15 * 60000L) {
                    prayerStage = 1;
                    show("nextprayer");
                }
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(tick, 1000);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
    }
}
