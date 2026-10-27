package com.carbox.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Floating back button over other apps (hidden on the home screen). Tap = Back, which also returns
 * home from an app's first page; hold = Home; drag to move. Uses an accessibility service because
 * that is the only way Android lets an app press Back for another app.
 */
public class BackButtonService extends AccessibilityService {
    static BackButtonService I;
    private WindowManager wm;
    private FrameLayout button;
    private WindowManager.LayoutParams lp;
    private boolean visible;

    @Override
    protected void onServiceConnected() {
        I = this;
        U.init((Context) this);
        Store.init(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        build();
    }

    static int sizeDu() {
        String s = Store.str("fb_size", "m");
        return "s".equals(s) ? 48 : "l".equals(s) ? 72 : 60;
    }

    private void build() {
        if (button != null) {
            try {
                wm.removeView(button);
            } catch (Exception ignored) {
            }
        }
        int size = U.u(sizeDu());
        button = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable bg = U.oval(0xD1171C24);
        bg.setStroke(U.u(2), 0x40FFFFFF);
        button.setBackground(bg);
        button.setElevation(U.uf(6));
        ImageView iv = U.icon(this, R.drawable.ic_back, U.TEXT);
        button.addView(iv, new FrameLayout.LayoutParams(size / 2, size / 2, Gravity.CENTER));
        lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Store.num("fb_x", U.u(14));
        lp.y = Store.num("fb_y", U.u(206));
        button.setOnTouchListener(new Touch());
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        try {
            wm.addView(button, lp);
        } catch (Exception e) {
            button = null;
        }
    }

    /** Called from Settings when size or on/off changes. */
    static void refresh() {
        if (I != null) {
            I.build();
            I.setVisible(I.visible);
        }
    }

    private void setVisible(boolean v) {
        visible = v;
        if (button != null) button.setVisibility(v && Store.flag("fb_on", true) ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName())) {
            setVisible(false); // the home screen (and our own panels)
            return;
        }
        // ignore pop-ups that don't change the app on screen: system UI, keyboards
        if (pkg.equals("com.android.systemui") || isKeyboard(pkg)) return;
        if (isOtherLauncher(pkg)) {
            setVisible(false);
            return;
        }
        setVisible(true);
    }

    private boolean isKeyboard(String pkg) {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        for (android.view.inputmethod.InputMethodInfo i : imm.getEnabledInputMethodList()) if (i.getPackageName().equals(pkg)) return true;
        return false;
    }

    private boolean isOtherLauncher(String pkg) {
        android.content.Intent h = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME);
        for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(h, 0))
            if (r.activityInfo != null && pkg.equals(r.activityInfo.packageName)) return true;
        return false;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (button != null) {
            try {
                wm.removeView(button);
            } catch (Exception ignored) {
            }
        }
        if (I == this) I = null;
        super.onDestroy();
    }

    /** Tap = Back, hold = Home, drag = move (position remembered). */
    private final class Touch implements View.OnTouchListener {
        private float downX, downY;
        private int startX, startY;
        private boolean moved, held;
        private final int slop = ViewConfiguration.get(BackButtonService.this).getScaledTouchSlop();
        private final Runnable hold = () -> {
            held = true;
            button.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            performGlobalAction(GLOBAL_ACTION_HOME);
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    moved = false;
                    held = false;
                    v.setAlpha(0.7f);
                    U.ui.postDelayed(hold, 600);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!moved && Math.hypot(dx, dy) > slop * 2) {
                        moved = true;
                        U.ui.removeCallbacks(hold);
                    }
                    if (moved) {
                        lp.x = Math.max(0, startX + (int) dx);
                        lp.y = Math.max(0, startY + (int) dy);
                        wm.updateViewLayout(button, lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    U.ui.removeCallbacks(hold);
                    if (moved) {
                        Store.put("fb_x", lp.x);
                        Store.put("fb_y", lp.y);
                    } else if (!held && e.getActionMasked() == MotionEvent.ACTION_UP) {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }
                    return true;
            }
            return false;
        }
    }

    static boolean enabled(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }
}
