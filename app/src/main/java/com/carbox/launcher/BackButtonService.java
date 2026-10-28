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
    private boolean visible;      // an app (not home) is on screen
    private boolean awake;        // shown because the screen was touched recently
    static final long SHOW_MS = 4000;
    private final Runnable sleep = () -> setAwake(false);

    @Override
    protected void onServiceConnected() {
        I = this;
        // settings first: when the box starts, this service starts before the launcher has opened
        // them, and saving anything earlier crashed the service (so the button never appeared)
        Store.init(this);
        U.init((Context) this);
        Store.put("fb_connected_at", System.currentTimeMillis()); // shows in Settings that it really started
        try {
            About.catchCrashes(this);
            Apps.init(this);
        } catch (Exception ignored) {
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        build();
        // Android's own floating accessibility button (the blue tab on the screen edge): tap it for
        // the shortcut circle with Back in the middle, so it works even when our round button doesn't show
        try {
            getAccessibilityButtonController().registerAccessibilityButtonCallback(new android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                @Override
                public void onClicked(android.accessibilityservice.AccessibilityButtonController controller) {
                    if (menu != null) closeMenu();
                    else openMenu(true);
                }
            });
        } catch (Exception ignored) {
        }
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
        // FLAG_WATCH_OUTSIDE_TOUCH tells us when the screen is touched anywhere (the touch still goes to the app)
        lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // keep it on screen: a position saved on another screen size could put it out of sight
        android.graphics.Point scr = new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(scr);
        lp.x = Math.max(0, Math.min(scr.x - size, Store.num("fb_x", U.u(14))));
        lp.y = Math.max(0, Math.min(scr.y - size, Store.num("fb_y", U.u(206))));
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
        if (!v) {
            awake = false;
            if (menu != null) closeMenu();
        }
        apply();
    }

    /** Wake the button for a few seconds after any touch. */
    private void setAwake(boolean a) {
        awake = a;
        U.ui.removeCallbacks(sleep);
        if (a) U.ui.postDelayed(sleep, SHOW_MS);
        apply();
    }

    static boolean onlyOnTouch() {
        return Store.flag("fb_touch", false);
    }

    /**
     * Shown: full size and opaque. Asleep: a 1-pixel invisible window that only listens for touches,
     * so nothing is blocked and the next tap on the app wakes the button.
     */
    private void apply() {
        if (button == null) return;
        boolean on = visible && Store.flag("fb_on", true);
        button.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) return;
        boolean show = awake || !onlyOnTouch();
        int size = U.u(sizeDu());
        int w = show ? size : 1;
        if (lp.width != w) {
            lp.width = w;
            lp.height = w;
            try {
                wm.updateViewLayout(button, lp);
            } catch (Exception ignored) {
            }
        }
        button.animate().cancel();
        if (show) {
            button.setAlpha(button.getAlpha() < 0.05f ? 0f : button.getAlpha());
            button.animate().alpha(1f).setDuration(150).start();
        } else button.setAlpha(0f);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        int t = e.getEventType();
        try {
            BtAuto.onEvent(this, e); // BT Phone only: calls in, calls out
        } catch (Exception ignored) {
        }
        if (t == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || t == AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        if ((t == AccessibilityEvent.TYPE_VIEW_CLICKED || t == AccessibilityEvent.TYPE_VIEW_SCROLLED) && visible && onlyOnTouch()) {
            setAwake(true);
            return;
        }
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
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

    private java.util.Set<String> keyboards;
    private long keyboardsAt;

    /** Is this a keyboard app? (the list is read at most once a minute) */
    private boolean isKeyboard(String pkg) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (keyboards == null || now - keyboardsAt > 60000) {
            keyboards = new java.util.HashSet<>();
            keyboardsAt = now;
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) for (android.view.inputmethod.InputMethodInfo i : imm.getEnabledInputMethodList()) keyboards.add(i.getPackageName());
        }
        return keyboards.contains(pkg);
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

    // ------------------------------------------------------------------ hold menu

    private FrameLayout menu;
    private final java.util.List<View> menuItems = new java.util.ArrayList<>();
    private final java.util.List<String> menuIds = new java.util.ArrayList<>();
    private String hot;

    /** 3–8 buttons in a circle around the back button. */
    private void openMenu() {
        openMenu(false);
    }

    private boolean fromTab;

    /** fromTab: opened by Android's accessibility tab, so centre on the screen and put Back in the middle. */
    private void openMenu(boolean tab) {
        closeMenu();
        fromTab = tab;
        U.ui.removeCallbacks(sleep); // keep the back button up while the menu is open
        android.graphics.Point screen = new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(screen);
        int item = U.u(64), radius = U.u(118), reach = radius + item / 2 + U.u(26);
        int size = U.u(sizeDu());
        // centre on the back button, pulled in from the edges so the whole circle fits
        int cx = Math.max(reach, Math.min(screen.x - reach, tab ? screen.x / 2 : lp.x + size / 2));
        int cy = Math.max(reach, Math.min(screen.y - reach, tab ? screen.y / 2 : lp.y + size / 2));

        menu = new FrameLayout(this);
        menu.setBackgroundColor(0x8C000000);
        menu.setOnClickListener(v -> closeMenu());
        java.util.List<String> ids = QuickActions.chosen();
        menuItems.clear();
        menuIds.clear();
        for (int i = 0; i < ids.size(); i++) {
            Object[] a = QuickActions.find(ids.get(i));
            double ang = -Math.PI / 2 + i * 2 * Math.PI / ids.size();
            int x = (int) (cx + radius * Math.cos(ang)), y = (int) (cy + radius * Math.sin(ang));
            FrameLayout b = new FrameLayout(this);
            b.setBackground(U.oval((Integer) a[3]));
            b.setElevation(U.uf(6));
            b.addView(U.icon(this, (Integer) a[2], U.TEXT), new FrameLayout.LayoutParams(U.u(28), U.u(28), Gravity.CENTER));
            final String id = (String) a[0];
            b.setOnClickListener(v -> {
                closeMenu();
                QuickActions.run(this, id);
            });
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(item, item, Gravity.TOP | Gravity.START);
            bl.leftMargin = x - item / 2;
            bl.topMargin = y - item / 2;
            menu.addView(b, bl);
            menuItems.add(b);
            menuIds.add(id);
            android.widget.TextView t = U.text(this, (String) a[1], 12, U.TEXT, false);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(U.uf(3), 0, U.uf(1), 0xFF000000);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(U.u(110), android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            tl.leftMargin = x - U.u(55);
            tl.topMargin = y + item / 2 + U.u(3);
            menu.addView(t, tl);
        }
        // centre: close
        FrameLayout close = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable cbg = U.oval(0xFF171C24);
        cbg.setStroke(U.u(2), 0x59FFFFFF);
        close.setBackground(cbg);
        close.addView(U.icon(this, tab ? R.drawable.ic_back : R.drawable.ic_x, U.TEXT), new FrameLayout.LayoutParams(U.u(26), U.u(26), Gravity.CENTER));
        close.setOnClickListener(v -> {
            closeMenu();
            if (fromTab) performGlobalAction(GLOBAL_ACTION_BACK); // middle = Back when opened from the tab
        });
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(U.u(60), U.u(60), Gravity.TOP | Gravity.START);
        cl.leftMargin = cx - U.u(30);
        cl.topMargin = cy - U.u(30);
        menu.addView(close, cl);

        WindowManager.LayoutParams mlp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        mlp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(menu, mlp);
            menu.setAlpha(0f);
            menu.animate().alpha(1f).setDuration(120).start();
        } catch (Exception e) {
            menu = null;
        }
    }

    /** While still holding: light up the button under the finger. */
    private void highlight(float rx, float ry) {
        hot = null;
        for (int i = 0; i < menuItems.size(); i++) {
            View v = menuItems.get(i);
            int[] p = new int[2];
            v.getLocationOnScreen(p);
            float cx = p[0] + v.getWidth() / 2f, cy = p[1] + v.getHeight() / 2f;
            boolean on = Math.hypot(rx - cx, ry - cy) < v.getWidth() * 0.75f;
            v.setScaleX(on ? 1.18f : 1f);
            v.setScaleY(on ? 1.18f : 1f);
            if (on) hot = menuIds.get(i);
        }
    }

    private void closeMenu() {
        hot = null;
        if (menu != null) {
            try {
                wm.removeView(menu);
            } catch (Exception ignored) {
            }
            menu = null;
        }
        if (onlyOnTouch() && awake) setAwake(true); // restart the fade timer
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
            openMenu();
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                if (onlyOnTouch()) setAwake(true);
                return false;
            }
            if (onlyOnTouch() && !awake) return false;
            if (onlyOnTouch()) setAwake(true); // keep it up while you use it
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
                    if (held) {
                        highlight(e.getRawX(), e.getRawY());
                        return true;
                    }
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
                    if (held) {
                        // slid onto a menu button and let go: run it; otherwise the menu stays open for a tap
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && hot != null) {
                            String id = hot;
                            closeMenu();
                            QuickActions.run(BackButtonService.this, id);
                        }
                        return true;
                    }
                    if (moved) {
                        Store.put("fb_x", lp.x);
                        Store.put("fb_y", lp.y);
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }
                    return true;
            }
            return false;
        }
    }

    /** "Display over other apps" granted. */
    static boolean overlay(Context c) {
        return android.provider.Settings.canDrawOverlays(c);
    }

    static boolean enabled(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }
}
