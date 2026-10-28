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

    SwipeBox(Context c, Runnable swiped) {
        super(c);
        this.swiped = swiped;
        slop = ViewConfiguration.get(c).getScaledTouchSlop() * 3;
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
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!done && Math.hypot(e.getX() - x0, e.getY() - y0) > slop) {
                    done = true;
                    post(swiped);
                    return true;
                }
                return done;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return done;
        }
        return false;
    }
}
