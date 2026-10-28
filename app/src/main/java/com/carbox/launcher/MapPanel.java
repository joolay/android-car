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
                    web.evaluateJavascript("setup('" + (google() ? "google" : "osm") + "','" + key + "')", null);
                    push();
                }
            });
            web.addJavascriptInterface(new Object() {
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

        TextView go = U.text(c, "Navigate", 14, U.TEXT, true);
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
        if (web != null) {
            web.setLongClickable(true);
            web.setOnLongClickListener(hold);
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

    private FrameLayout bigHolder;

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
            int at = a.tileArea.indexOfChild(a.sheetHost); // under panels and alerts
            a.tileArea.addView(bigHolder, Math.max(0, at), new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        } else if (!on && bigHolder != null) {
            bigHolder.removeView(mapLayer);
            ((android.view.ViewGroup) bigHolder.getParent()).removeView(bigHolder);
            bigHolder = null;
            view.addView(mapLayer, 0, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        }
    }

    private void updateNav() {
        MediaListener.Nav n = MediaListener.nav;
        bigMap(n != null && NavCard.swap()); // swap: map into the tile area, directions stay here
        if (n == null || NavCard.inTiles()) { // instructions shown in the tile area instead: keep the map
            navCard.setVisibility(View.GONE);
            mapLayer.setVisibility(View.VISIBLE);
            return;
        }
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
