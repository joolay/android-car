package com.carbox.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * A panel that notices a swipe in any direction over it (even over its buttons) and runs an
 * action; taps, holds and the buttons inside still work as usual.
 */
class SwipeBox extends FrameLayout {
    private final Runnable swiped;
    private final int slop;
    private float x0, y0;
    private boolean done;
    /** Direction of the last swipe: 1 = to the left / up (next), -1 = to the right / down (previous). */
    int dir = 1;
    private Runnable held; // optional: hold anywhere on the panel, even over its buttons
    private final Runnable holdFire = () -> {
        if (done || held == null) return;
        done = true; // what's under the finger gets a cancel, no tap
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        held.run();
    };

    SwipeBox(Context c, Runnable swiped) {
        super(c);
        this.swiped = swiped;
        slop = ViewConfiguration.get(c).getScaledTouchSlop() * 3;
    }

    void onHold(Runnable r) {
        held = r;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return check(e) || super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (check(e)) {
            MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel); // no tap or hold once it's a swipe
            cancel.recycle();
            return true;
        }
        return super.onTouchEvent(e) || true;
    }

    private boolean check(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = e.getX();
                y0 = e.getY();
                done = false;
                removeCallbacks(holdFire);
                if (held != null) postDelayed(holdFire, ViewConfiguration.getLongPressTimeout());
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!done && Math.hypot(e.getX() - x0, e.getY() - y0) > slop) {
                    done = true;
                    removeCallbacks(holdFire);
                    float dx = e.getX() - x0, dy = e.getY() - y0;
                    dir = Math.abs(dx) >= Math.abs(dy) ? (dx < 0 ? 1 : -1) : (dy < 0 ? 1 : -1);
                    post(swiped);
                    return true;
                }
                return done;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(holdFire);
                return done;
        }
        return false;
    }
}
