package com.carbox.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Live dark map with a Navigate button. Turns into a turn-by-turn card while Google Maps or Waze is navigating. */
final class MapPanel {
    private final Context c;
    final FrameLayout view;
    private WebView web;
    private final FrameLayout mapLayer;
    private final LinearLayout navCard;
    private final ImageView turnIcon;
    private final TextView dist, road, eta, navApp;
    private final TextView noLoc;
    private TextView go;
    private boolean pageReady, running;

    @SuppressLint("SetJavaScriptEnabled")
    MapPanel(Context c) {
        this.c = c;
        view = new FrameLayout(c);
        view.setBackground(U.round(U.CARD, 16));
        clip(view, 16);

        mapLayer = new FrameLayout(c);
        view.addView(mapLayer, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        try {
            web = new WebView(c);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            web.setBackgroundColor(U.BG);
            web.setVerticalScrollBarEnabled(false);
            web.setHorizontalScrollBarEnabled(false);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    pageReady = true;
                    String key = Store.str(Store.MAP_KEY, "").replace("'", "");
                    web.evaluateJavascript("setup('" + (google() ? "google" : "osm") + "','" + key + "');setHeadingUp(" + headingUp() + ");setInteractive(" + (bigHolder != null) + ");setTraffic(" + Store.flag("map_traffic", false) + ")", null);
                    push();
                }
            });
            web.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void mapState(boolean follow, int scalePx, String scaleLabel) {
                    U.ui.post(() -> {
                        following = follow;
                        scaleW = scalePx;
                        scaleText = scaleLabel;
                        updateControls();
                    });
                }

                @android.webkit.JavascriptInterface
                public void mapNote(String msg) {
                    U.ui.post(() -> android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_LONG).show());
                }
            }, "Android");
            web.loadUrl("file:///android_asset/map.html");
            mapLayer.addView(web, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        } catch (Throwable t) {
            web = null; // no WebView on this box
        }
        noLoc = U.text(c, "Waiting for location…", 12, U.SUB, false);
        noLoc.setGravity(Gravity.CENTER);
        mapLayer.addView(noLoc, U.flp(U.MATCH, U.MATCH, Gravity.CENTER));

        go = U.text(c, "Navigate", 14, U.TEXT, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(U.round(U.ACCENT, 12));
        go.setCompoundDrawablePadding(U.u(6));
        go.setOnClickListener(v -> Actions.openNavigation(c));
        go.setOnLongClickListener(v -> {
            SettingsPanels.mapNav();
            return true;
        });
        // hold anywhere on the map for map and navigation settings
        View.OnLongClickListener hold = v -> {
            SettingsPanels.mapNav();
            return true;
        };
        mapLayer.setOnLongClickListener(hold);
        mapLayer.setOnClickListener(v -> expand(true));
        if (web != null) {
            web.setLongClickable(true);
            web.setOnLongClickListener(hold);
            // a tap on the small map makes it big (once big, taps belong to the map)
            android.view.GestureDetector tap = new android.view.GestureDetector(c, new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                    if (bigHolder == null) expand(true);
                    return false;
                }
            });
            web.setOnTouchListener((v, e) -> {
                tap.onTouchEvent(e);
                return false;
            });
        }
        U.press(go);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(U.MATCH, U.u(36), Gravity.BOTTOM);
        gl.setMargins(U.u(10), 0, U.u(10), U.u(10));
        mapLayer.addView(go, gl);

        // turn-by-turn card
        navCard = U.col(c);
        navCard.setBackground(U.round(0xFF0F3D26, 16));
        U.pad(navCard, 14, 14, 14, 12);
        navCard.setVisibility(View.GONE);
        LinearLayout top = U.row(c);
        turnIcon = new ImageView(c);
        turnIcon.setColorFilter(U.TEXT);
        top.addView(turnIcon, U.lpu(52, 52));
        dist = U.text(c, "", 30, U.TEXT, true);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, U.WRAP, 1);
        dlp.leftMargin = U.u(10);
        top.addView(dist, dlp);
        navCard.addView(top);
        road = U.text(c, "", 17, U.TEXT, true);
        road.setMaxLines(3);
        road.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams rl = U.lp(U.MATCH, U.WRAP);
        rl.topMargin = U.u(10);
        navCard.addView(road, rl);
        navCard.addView(new View(c), U.weight(1, false));
        eta = U.text(c, "", 13, U.MINT, false);
        eta.setMaxLines(2);
        navCard.addView(eta);
        navApp = U.line(c, "", 11, 0xFF9FD9B9, false);
        LinearLayout.LayoutParams al = U.lp(U.MATCH, U.WRAP);
        al.topMargin = U.u(6);
        navCard.addView(navApp, al);
        navCard.setOnClickListener(v -> {
            if (!Apps.launchPkg(c, MediaListener.navPkg)) Actions.openNavigation(c);
        });
        view.addView(navCard, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        Loc.listen(this::push);
        MediaListener.listenNav(this::updateNav);
    }

    /** The map turns with your direction (heading up) unless set to north up. */
    static boolean headingUp() {
        return Store.flag("map_heading_up", true);
    }

    void applyHeading() {
        if (web != null && pageReady) web.evaluateJavascript("setHeadingUp(" + headingUp() + ")", null);
    }

    /** Google Maps only when it's chosen and a key has been entered; otherwise OpenStreetMap. */
    static boolean google() {
        return "google".equals(Store.str(Store.MAP_PROVIDER, "osm")) && !Store.str(Store.MAP_KEY, "").isEmpty();
    }

    /** Reload after the map setting changes. */
    void reload() {
        if (web == null) return;
        pageReady = false;
        web.loadUrl("file:///android_asset/map.html");
    }

    static void clip(View v, float r) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), U.uf(r));
            }
        });
        v.setClipToOutline(true);
    }

    void start() {
        running = true;
        if (web != null) web.onResume();
        push();
        updateNav();
    }

    void stop() {
        running = false;
        if (web != null) web.onPause();
    }

    private void push() {
        noLoc.setVisibility(Loc.has() ? View.GONE : View.VISIBLE);
        if (web == null) {
            if (Loc.has()) {
                noLoc.setVisibility(View.VISIBLE);
                noLoc.setText(Loc.town != null ? Loc.town : "Map needs Android System WebView");
            }
            return;
        }
        if (!pageReady || !running || !Loc.has()) return;
        float b = Float.isNaN(Loc.bearing) ? -1 : Loc.bearing;
        web.evaluateJavascript(String.format(Locale.US, "setPos(%.6f,%.6f,%.0f)", Loc.lat, Loc.lon, b), null);
    }

    private FrameLayout bigHolder, mini;
    private boolean tapBig;
    private Widgets.W miniW;

    /** Tap on the map: it fills the 2×4 grid and the small panel shows the Go to or Speed tile. */
    void expand(boolean on) {
        tapBig = on;
        updateNav();
    }

    boolean expanded() {
        return tapBig;
    }

    /** "goto" or "speed": the tile shown in the small panel while the map is big. */
    static String miniId() {
        return Store.str("map_mini", "goto");
    }

    private void showMini(boolean on) {
        if (on && mini == null) {
            mini = new SwipeBox(c, this::swapMini);
            String id = miniId();
            int idx = -1;
            org.json.JSONArray s = Tiles.specs();
            for (int i = 0; i < s.length(); i++) {
                org.json.JSONObject o = s.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) idx = i; // same tile (its places / settings)
            }
            org.json.JSONObject spec = idx >= 0 ? s.optJSONObject(idx) : Tiles.widget(id);
            try {
                miniW = Widgets.make(c, idx, spec);
                if (miniW != null && miniW.v != null) mini.addView(miniW.v, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            } catch (Exception ignored) {
            }
            view.addView(mini, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            U.ui.post(miniTick);
        } else if (!on && mini != null) {
            U.ui.removeCallbacks(miniTick);
            view.removeView(mini);
            mini = null;
            miniW = null;
        }
    }

    /** Swipe the small panel (any direction) to switch between Go to and Speed. */
    private void swapMini() {
        Store.put("map_mini", "goto".equals(miniId()) ? "speed" : "goto");
        showMini(false);
        showMini(true);
        if (mini != null) {
            mini.setAlpha(0f);
            mini.animate().alpha(1f).setDuration(200).start();
        }
    }

    /** Holds the small tile and catches a swipe over it (taps still reach the tile). */
    private static final class SwipeBox extends FrameLayout {
        private final Runnable swiped;
        private final int slop;
        private float dx0, dy0;
        private boolean done;

        SwipeBox(Context c, Runnable swiped) {
            super(c);
            this.swiped = swiped;
            slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop() * 3;
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            return check(e);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            check(e);
            return true;
        }

        private boolean check(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    dx0 = e.getX();
                    dy0 = e.getY();
                    done = false;
                    return false;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!done && Math.hypot(e.getX() - dx0, e.getY() - dy0) > slop) {
                        done = true;
                        post(swiped);
                        return true;
                    }
                    return done;
            }
            return false;
        }
    }

    private final Runnable miniTick = new Runnable() {
        @Override
        public void run() {
            if (miniW == null) return;
            try {
                miniW.tick();
            } catch (Exception ignored) {
            }
            U.ui.postDelayed(this, 1000);
        }
    };

    /** Move the live map into the 2×4 tile area (and back). The same map moves, so nothing reloads. */
    private void bigMap(boolean on) {
        MainActivity a = MainActivity.I;
        if (a == null) return;
        if (on && bigHolder == null) {
            bigHolder = new FrameLayout(a);
            MapPanel.clip(bigHolder, 16);
            view.removeView(mapLayer);
            bigHolder.addView(mapLayer, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            mapLayer.setVisibility(View.VISIBLE);
            bigHolder.addView(controls(a), new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            pill(true);
            js("setInteractive(true)");
            U.ui.post(controlTick);
            int at = a.tileArea.indexOfChild(a.sheetHost); // under panels and alerts
            a.tileArea.addView(bigHolder, Math.max(0, at), new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        } else if (!on && bigHolder != null) {
            U.ui.removeCallbacks(controlTick);
            js("setInteractive(false)");
            pill(false);
            bigHolder.removeView(mapLayer);
            ((android.view.ViewGroup) bigHolder.getParent()).removeView(bigHolder);
            bigHolder = null;
            view.addView(mapLayer, 0, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        }
    }

    // ------------------------------------------------------------------ big map controls

    private boolean following = true;
    private int scaleW;
    private String scaleText = "";
    private TextView street, limit, spd, scaleLbl;
    private View recentre, scaleBar;
    private ImageView compass;

    private void js(String code) {
        if (web != null && pageReady) web.evaluateJavascript(code, null);
    }

    /** Navigate: a full-width bar on the small map, a centred pill on the big one. */
    private void pill(boolean big) {
        FrameLayout.LayoutParams gl = big ? new FrameLayout.LayoutParams(U.WRAP, U.u(38), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
                : new FrameLayout.LayoutParams(U.MATCH, U.u(36), Gravity.BOTTOM);
        gl.setMargins(U.u(10), 0, U.u(10), U.u(big ? 12 : 10));
        go.setLayoutParams(gl);
        U.pad(go, big ? 18 : 0, 0, big ? 18 : 0, 0);
        go.setBackground(U.round(U.ACCENT, big ? 19 : 12));
    }

    private View round(Context a, int icon, boolean tint, Runnable r) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(U.outline(0xD10B0E13, 0x2EFFFFFF, 1, 19));
        ImageView iv = tint ? U.icon(a, icon, U.TEXT) : new ImageView(a);
        if (!tint) iv.setImageResource(icon);
        f.addView(iv, U.flpu(20, 20, Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        U.press(f);
        return f;
    }

    private FrameLayout controls(Context a) {
        FrameLayout o = new FrameLayout(a);
        // street you're on
        street = U.line(a, "", 12, U.TEXT, false);
        street.setBackground(U.outline(0xD10B0E13, 0x2EFFFFFF, 1, 10));
        U.pad(street, 10, 4, 10, 4);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.START);
        rl.setMargins(U.u(10), U.u(10), U.u(60), 0);
        o.addView(street, rl);
        // right-hand column: close, compass, traffic
        LinearLayout col = U.col(a);
        col.addView(round(a, R.drawable.ic_x, true, () -> expand(false)), U.lpu(38, 38));
        View cb = round(a, R.drawable.ic_compass, false, () -> { // direction of travel ⇄ north up
            Store.put("map_heading_up", !headingUp());
            applyHeading();
            updateControls();
        });
        compass = (ImageView) ((FrameLayout) cb).getChildAt(0);
        LinearLayout.LayoutParams cbl = U.lpu(38, 38);
        cbl.topMargin = U.u(8);
        col.addView(cb, cbl);
        if (google()) {
            View tb = round(a, R.drawable.ic_traffic, true, () -> {
                boolean on = !Store.flag("map_traffic", false);
                Store.put("map_traffic", on);
                js("setTraffic(" + on + ")");
                updateControls();
            });
            tb.setTag("traffic");
            LinearLayout.LayoutParams tl = U.lpu(38, 38);
            tl.topMargin = U.u(8);
            col.addView(tb, tl);
        }
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.END);
        cl.setMargins(0, U.u(10), U.u(10), 0);
        o.addView(col, cl);
        // zoom
        LinearLayout zoom = U.col(a);
        zoom.setBackground(U.outline(0xD10B0E13, 0x2EFFFFFF, 1, 19));
        View zin = U.icon(a, R.drawable.ic_plus, U.TEXT), zout = U.icon(a, R.drawable.ic_minus, U.TEXT);
        U.pad(zin, 9, 9, 9, 9);
        U.pad(zout, 9, 9, 9, 9);
        zin.setOnClickListener(v -> js("zoomBy(1)"));
        zout.setOnClickListener(v -> js("zoomBy(-1)"));
        zoom.addView(zin, U.lpu(38, 38));
        View sep = new View(a);
        sep.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams sl = U.lpu(20, 1);
        sl.gravity = Gravity.CENTER_HORIZONTAL;
        zoom.addView(sep, sl);
        zoom.addView(zout, U.lpu(38, 38));
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.BOTTOM | Gravity.END);
        zl.setMargins(0, 0, U.u(10), U.u(58));
        o.addView(zoom, zl);
        // back to my location (only once you've moved the map)
        recentre = round(a, R.drawable.ic_myloc, true, () -> js("recenter()"));
        recentre.setBackground(U.oval(U.ACCENT));
        FrameLayout.LayoutParams ml = U.flpu(38, 38, Gravity.BOTTOM | Gravity.END);
        ml.setMargins(0, 0, U.u(10), U.u(12));
        o.addView(recentre, ml);
        // bottom left: limit sign, speed, scale
        LinearLayout bl = U.row(a);
        bl.setGravity(Gravity.CENTER_VERTICAL);
        limit = U.text(a, "", 13, 0xFF111111, true);
        limit.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable sign = U.oval(0xFFFFFFFF);
        sign.setStroke(U.u(3), 0xFFD33A3A);
        limit.setBackground(sign);
        bl.addView(limit, U.lpu(36, 36));
        spd = U.line(a, "", 13, U.TEXT, true);
        spd.setBackground(U.outline(0xD10B0E13, 0x2EFFFFFF, 1, 10));
        U.pad(spd, 10, 4, 10, 4);
        LinearLayout.LayoutParams pl = U.lp(U.WRAP, U.WRAP);
        pl.leftMargin = U.u(8);
        bl.addView(spd, pl);
        LinearLayout sc = U.col(a);
        scaleBar = new View(a);
        scaleBar.setBackground(U.outline(0x00000000, 0xFFC8D0DC, 1, 0));
        sc.addView(scaleBar, new LinearLayout.LayoutParams(U.u(50), U.u(5)));
        scaleLbl = U.line(a, "", 11, 0xFFC8D0DC, false);
        sc.addView(scaleLbl);
        LinearLayout.LayoutParams scl = U.lp(U.WRAP, U.WRAP);
        scl.leftMargin = U.u(10);
        bl.addView(sc, scl);
        FrameLayout.LayoutParams bll = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.BOTTOM | Gravity.START);
        bll.setMargins(U.u(10), 0, 0, U.u(13));
        o.addView(bl, bll);
        updateControls();
        return o;
    }

    private final Runnable controlTick = new Runnable() {
        @Override
        public void run() {
            if (bigHolder == null) return;
            updateControls();
            U.ui.postDelayed(this, 1000);
        }
    };

    private void updateControls() {
        if (street == null || bigHolder == null) return;
        String r = SpeedLimit.road != null && !SpeedLimit.road.isEmpty() ? SpeedLimit.road : Loc.town;
        street.setText(r == null ? "" : r);
        street.setVisibility(r == null || r.isEmpty() ? View.GONE : View.VISIBLE);
        boolean mph = Widgets.mph();
        int lim = SpeedLimit.kmh;
        limit.setText(lim > 0 ? String.valueOf(mph ? Math.round(lim / 1.609f) : lim) : "");
        limit.setVisibility(lim > 0 ? View.VISIBLE : View.GONE);
        if (Loc.speed >= 0 && Loc.fresh()) {
            float v = Loc.speed * 3.6f;
            spd.setText(Math.round(mph ? v / 1.609f : v) + (mph ? " mph" : " km/h"));
            spd.setVisibility(View.VISIBLE);
        } else spd.setVisibility(View.GONE);
        if (scaleW > 0) {
            scaleBar.getLayoutParams().width = scaleW;
            scaleBar.requestLayout();
            scaleLbl.setText(scaleText);
        }
        recentre.setVisibility(following ? View.GONE : View.VISIBLE);
        // compass needle points north on screen
        boolean hu = headingUp() && following && !Float.isNaN(Loc.bearing);
        compass.animate().rotation(hu ? -Loc.bearing : 0).setDuration(600).start();
        View tb = bigHolder.findViewWithTag("traffic");
        if (tb != null) tb.setBackground(Store.flag("map_traffic", false) ? U.oval(U.ACCENT) : U.outline(0xD10B0E13, 0x2EFFFFFF, 1, 19));
    }

    private void updateNav() {
        MediaListener.Nav n = MediaListener.nav;
        boolean navSwap = n != null && NavCard.swap();
        bigMap(tapBig || navSwap); // swap: map into the tile area, directions stay here
        if (n == null || NavCard.inTiles()) { // instructions shown in the tile area instead: keep the map
            navCard.setVisibility(View.GONE);
            mapLayer.setVisibility(View.VISIBLE);
            showMini(bigHolder != null); // map is big: the small panel shows Go to / Speed
            return;
        }
        showMini(false);
        navCard.setVisibility(View.VISIBLE);
        if (bigHolder == null) mapLayer.setVisibility(View.INVISIBLE);
        Bitmap bm = n.icon;
        if (bm != null) turnIcon.setImageBitmap(bm);
        else turnIcon.setImageResource(R.drawable.ic_turn);
        dist.setText(n.distance != null ? n.distance : "");
        dist.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, U.uf(n.distance != null && n.distance.length() > 7 ? 22 : 30));
        road.setText(n.instruction != null ? n.instruction : "");
        eta.setText(n.eta != null ? n.eta : "");
        navApp.setText(Apps.label(MediaListener.navPkg));
    }
}
