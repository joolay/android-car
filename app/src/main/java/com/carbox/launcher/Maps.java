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
final class Maps {
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
    Maps(Context c) {
        this.c = c;
        view = new FrameLayout(c);
        view.setBackground(Themes.card(Ui.CARD, 16));
        clip(view, 16);

        mapLayer = new FrameLayout(c);
        view.addView(mapLayer, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        try {
            web = new WebView(c);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            web.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            web.setVerticalScrollBarEnabled(false);
            web.setHorizontalScrollBarEnabled(false);
            web.setWebViewClient(new WebViewClient() {
                // the offline map file: answered straight from the file the user picked (byte ranges)
                @Override
                public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest r) {
                    if (!"pmtiles.local".equals(r.getUrl().getHost())) return null;
                    return OfflineMap.serve(c, r);
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    pageReady = true;
                    String key = Prefs.str(Prefs.MAP_KEY, "").replace("'", "");
                    shownDark = darkNow();
                    web.evaluateJavascript(GoogleTraffic.setupJs() + "setZoomPref(" + Prefs.str("map_zoom", "15") + ");setDark(" + shownDark + ");setup('" + provider() + "','" + key + "','" + option() + "');setHeadingUp(" + headingUp() + ");setInteractive(" + (bigHolder != null) + ");setRound(" + (bigHolder != null) + ");setTraffic(" + GoogleTraffic.on() + ")", null);
                    push();
                }
            });
            web.addJavascriptInterface(new Object() {
                @android.webkit.JavascriptInterface
                public void mapState(boolean follow, int scalePx, String scaleLabel) {
                    Ui.ui.post(() -> {
                        following = follow;
                        scaleW = scalePx;
                        scaleText = scaleLabel;
                        updateControls();
                    });
                }

                @android.webkit.JavascriptInterface
                public void mapZoom(double z) { // remembered for next time
                    Prefs.put("map_zoom", String.format(java.util.Locale.US, "%.2f", z));
                }

                @android.webkit.JavascriptInterface
                public void mapNote(String msg) {
                    Ui.ui.post(() -> android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_LONG).show());
                }
            }, "Android");
            web.loadUrl("file:///android_asset/map.html");
            mapLayer.addView(web, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            // on the Centre Stage the map page crops itself to a round radar (setRound) and is see-through
            // around it, so the themed stage shows there; the buttons keep their corners
            if (GoogleTraffic.available()) { // traffic on/off on the small map too (the big map has its own)
                smallTraffic = Ui.icon(c, R.drawable.ic_traffic, Ui.TEXT);
                Ui.pad(smallTraffic, 9, 9, 9, 9);
                smallTraffic.setElevation(Ui.uf(3));
                smallTraffic.setOnClickListener(v -> toggleTraffic());
                Ui.press(smallTraffic);
                FrameLayout.LayoutParams tl = Ui.flpu(40, 40, Gravity.TOP | Gravity.END);
                tl.setMargins(0, Ui.u(10), Ui.u(10), 0); // same corner spot and size as Now playing's Lyrics button
                mapLayer.addView(smallTraffic, tl);
                trafficLook();
            }
        } catch (Throwable t) {
            web = null; // no WebView on this box
        }
        noLoc = Ui.text(c, "Waiting for location…", 12, Ui.SUB, false);
        noLoc.setGravity(Gravity.CENTER);
        mapLayer.addView(noLoc, Ui.flp(Ui.MATCH, Ui.MATCH, Gravity.CENTER));

        go = Ui.text(c, "Navigate", 14, Ui.TEXT, true);
        go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.round(Ui.ACCENT, 12));
        go.setCompoundDrawablePadding(Ui.u(6));
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
        // the small map: swiping it (with the Tile Drawer locked open) shows the alternate stage's widgets;
        // a tap still makes the map big, a hold opens map settings. Under the map's own buttons.
        catcher = new SwipeBox(c, this::swipeSmall);
        catcher.setOnClickListener(v -> expand(true));
        catcher.onHold(SettingsPanels::mapNav);
        mapLayer.addView(catcher, smallTraffic != null ? mapLayer.indexOfChild(smallTraffic) : mapLayer.getChildCount(),
                new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
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
        Ui.press(go);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(Ui.MATCH, Ui.u(36), Gravity.BOTTOM);
        gl.setMargins(Ui.u(10), 0, Ui.u(10), Ui.u(10));
        mapLayer.addView(go, gl);

        // turn-by-turn card
        navCard = Ui.col(c);
        navCard.setBackground(Ui.round(0xFF0F3D26, 16));
        Ui.pad(navCard, 14, 14, 14, 12);
        navCard.setVisibility(View.GONE);
        LinearLayout top = Ui.row(c);
        turnIcon = new ImageView(c);
        turnIcon.setColorFilter(Ui.TEXT);
        top.addView(turnIcon, Ui.lpu(52, 52));
        dist = Ui.text(c, "", 30, Ui.TEXT, true);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        dlp.leftMargin = Ui.u(10);
        top.addView(dist, dlp);
        navCard.addView(top);
        road = Ui.text(c, "", 17, Ui.TEXT, true);
        road.setMaxLines(3);
        road.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams rl = Ui.lp(Ui.MATCH, Ui.WRAP);
        rl.topMargin = Ui.u(10);
        navCard.addView(road, rl);
        navCard.addView(new View(c), Ui.weight(1, false));
        eta = Ui.text(c, "", 13, Ui.MINT, false);
        eta.setMaxLines(2);
        navCard.addView(eta);
        navApp = Ui.line(c, "", 11, 0xFF9FD9B9, false);
        LinearLayout.LayoutParams al = Ui.lp(Ui.MATCH, Ui.WRAP);
        al.topMargin = Ui.u(6);
        navCard.addView(navApp, al);
        navCard.setOnClickListener(v -> {
            if (!Apps.launchPkg(c, MediaListener.navPkg)) Actions.openNavigation(c);
        });
        view.addView(navCard, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        Gps.listen(this::push);
        MediaListener.listenNav(this::updateNav);
    }

    /** The map turns with your direction (heading up) unless set to north up. */
    static boolean headingUp() {
        return Prefs.flag("map_heading_up", true);
    }

    void applyHeading() {
        if (web != null && pageReady) web.evaluateJavascript("setHeadingUp(" + headingUp() + ")", null);
    }

    /** Which map: osm (Standard), ofm (OpenFreeMap), pmtiles (offline file) or google. */
    static String provider() {
        // OpenFreeMap is the default; boxes still on the old Standard default move to it once
        if (!Prefs.flag("map_ofm_default", false)) {
            Prefs.put("map_ofm_default", true);
            if ("osm".equals(Prefs.str(Prefs.MAP_PROVIDER, "osm"))) Prefs.put(Prefs.MAP_PROVIDER, "ofm");
        }
        String p = Prefs.str(Prefs.MAP_PROVIDER, "ofm");
        if (!Addons.mapUnlocked(p)) return "ofm"; // a provider add-on that isn't unlocked
        if ("google".equals(p) && Prefs.str(Prefs.MAP_KEY, "").isEmpty()) return "ofm";
        if ("pmtiles".equals(p) && Prefs.str(OfflineMap.URI, "").isEmpty()) return "ofm";
        return p;
    }

    /** OpenFreeMap style, or the offline map's colours (dark at night, your day style in daylight). */
    static String option() {
        String p = provider();
        boolean dark = darkNow();
        if ("ofm".equals(p)) {
            String day = Prefs.str("ofm_style", "liberty");
            return dark ? "dark" : "dark".equals(day) ? "liberty" : day;
        }
        if ("pmtiles".equals(p)) return dark ? "dark" : "light";
        return "";
    }

    /** "auto" (light by day, dark from sunset to sunrise), "light" or "dark". */
    static String colours() {
        return Prefs.str("map_colours", "auto");
    }

    static boolean darkNow() {
        String m = colours();
        if ("dark".equals(m)) return true;
        if ("light".equals(m)) return false;
        java.util.Calendar c = java.util.Calendar.getInstance();
        double h = c.get(java.util.Calendar.HOUR_OF_DAY) + c.get(java.util.Calendar.MINUTE) / 60.0;
        double[] t = Prayer.times(0); // sunrise and sunset where the car is
        if (t == null) return h < 6 || h >= 18;
        return h < t[1] || h >= t[4];
    }

    private boolean shownDark;
    private final Runnable dayNight = new Runnable() { // auto: switch at sunrise and sunset
        @Override
        public void run() {
            if (darkNow() != shownDark) reload();
            Ui.ui.postDelayed(this, 60000);
        }
    };

    /** Google Maps only when it's chosen and a key has been entered; otherwise OpenStreetMap. */
    static boolean google() {
        return "google".equals(provider());
    }

    /** Reload after the map setting changes. */
    void reload() {
        if (web == null) return;
        pageReady = false;
        web.loadUrl("file:///android_asset/map.html");
    }

    /**
     * The big map's rim, plus its corners painted over in the screen's background colour: the map page
     * doesn't always respect the rounded clip, which left little light quarter-circles at the corners.
     */
    static android.graphics.drawable.Drawable rim(float r, float w) {
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
                    line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            @Override
            public void draw(android.graphics.Canvas cv) {
                android.graphics.Rect b = getBounds();
                float rr = Ui.uf(r), sw = Ui.uf(w);
                android.graphics.Path p = new android.graphics.Path();
                p.setFillType(android.graphics.Path.FillType.EVEN_ODD);
                p.addRect(b.left - 2, b.top - 2, b.right + 2, b.bottom + 2, android.graphics.Path.Direction.CW);
                p.addRoundRect(b.left, b.top, b.right, b.bottom, rr, rr, android.graphics.Path.Direction.CW);
                String mode = Prefs.str(Prefs.WALL, "road");
                fill.setColor("color".equals(mode) ? Prefs.num(Prefs.WALL_COLOR, Ui.BG) : android.graphics.Color.BLACK);
                cv.drawPath(p, fill);
                line.setStyle(android.graphics.Paint.Style.STROKE);
                line.setStrokeWidth(sw);
                line.setColor(Ui.CARD);
                float h = sw / 2;
                cv.drawRoundRect(b.left + h, b.top + h, b.right - h, b.bottom - h, rr - h, rr - h, line);
            }

            @Override
            public void setAlpha(int a) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter f) {
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    /**
     * The map cropped to a circle, like a radar screen: outside it the stage shows (glass in the Glass theme,
     * solid in Modern), with a rim and two faint range rings. Painted over the map (the map page doesn't
     * always respect a clip). The buttons and labels keep their places in the corners.
     */
    private android.graphics.drawable.Drawable radar() {
        return new android.graphics.drawable.Drawable() {
            final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
                    line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            final android.graphics.Path out = new android.graphics.Path();
            android.graphics.drawable.Drawable glass;

            @Override
            public void draw(android.graphics.Canvas cv) {
                if (bigHolder == null) return; // small map: a square widget, no crop
                android.graphics.Rect b = getBounds();
                float cx = b.exactCenterX(), cy = b.exactCenterY(), r = Math.min(b.width(), b.height()) / 2f - Ui.uf(6);
                out.reset();
                out.setFillType(android.graphics.Path.FillType.INVERSE_WINDING);
                out.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
                cv.save();
                cv.clipPath(out);
                if (Themes.on()) {
                    if (glass == null) {
                        glass = Themes.card(Ui.CARD, 0);
                        glass.setCallback(getCallback());
                    }
                    glass.setBounds(b);
                    glass.draw(cv);
                } else {
                    fill.setColor(Ui.CARD);
                    cv.drawRect(b, fill);
                }
                cv.restore();
                line.setStyle(android.graphics.Paint.Style.STROKE);
                line.setStrokeWidth(Ui.uf(1));
                line.setColor(0x24FFFFFF); // range rings
                cv.drawCircle(cx, cy, r * 0.33f, line);
                cv.drawCircle(cx, cy, r * 0.66f, line);
                line.setStrokeWidth(Ui.uf(3));
                line.setColor(Themes.on() ? 0x66FFFFFF : 0xFF2A3344); // rim
                cv.drawCircle(cx, cy, r, line);
            }

            @Override
            public void setAlpha(int a) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter f) {
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    static void clip(View v, float r) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.uf(r));
            }
        });
        v.setClipToOutline(true);
    }

    void start() {
        running = true;
        Ui.ui.removeCallbacks(dayNight);
        Ui.ui.postDelayed(dayNight, 60000);
        if (web != null) web.onResume();
        push();
        updateNav();
    }

    void stop() {
        running = false;
        if (web != null) web.onPause();
    }

    private void push() {
        noLoc.setVisibility(Gps.has() ? View.GONE : View.VISIBLE);
        if (web == null) {
            if (Gps.has()) {
                noLoc.setVisibility(View.VISIBLE);
                noLoc.setText(Gps.town != null ? Gps.town : "Map needs Android System WebView");
            }
            return;
        }
        if (!pageReady || !running || !Gps.has()) return;
        float b = Float.isNaN(Gps.bearing) ? -1 : Gps.bearing;
        web.evaluateJavascript(String.format(Locale.US, "setPos(%.6f,%.6f,%.0f)", Gps.lat, Gps.lon, b), null);
    }

    private FrameLayout bigHolder;
    private SwipeBox mini;
    private boolean tapBig;
    private Widgets.W miniW;

    /** Tap on the map: it fills the 2×4 grid and the small panel shows the Go to or Speed tile. */
    void expand(boolean on) {
        if (on && LyricsView.isShown()) { // lyrics open: a tap on the map minimises them (the map comes back)
            LyricsView.hide();
            return;
        }
        if (on && Panels.inTiles()) { // an expanded card is on the Centre Stage: send it back where it came from
            Panels.close();
            return;
        }
        MainActivity m = MainActivity.I;
        if (on && m != null && m.drawerOpen) { // tiles are up: tapping the small map brings the big map back
            m.drawer(false);
            return;
        }
        tapBig = on;
        updateNav();
    }

    boolean expanded() {
        return tapBig;
    }

    /** The big map's holder in the tile area (slid aside when the tile drawer opens). */
    View bigView() {
        return bigHolder;
    }

    /** The Top Left Widget's current widget (the small panel while the map is big). */
    static String miniId() {
        return LeftStages.current();
    }

    private void showMini(boolean on) {
        if (on && mini == null) {
            mini = new SwipeBox(c, this::swapMini);
            mini.onHold(LeftStages::chooser); // hold: choose the alternate stage's widgets
            try {
                miniW = LeftStages.make(c, miniId()); // same settings as in the tiles, its own design
                if (miniW != null && miniW.v != null) mini.addView(miniW.v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            } catch (Exception ignored) {
            }
            view.addView(mini, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            Ui.ui.post(miniTick);
        } else if (!on && mini != null) {
            Ui.ui.removeCallbacks(miniTick);
            view.removeView(mini);
            mini = null;
            miniW = null;
        }
    }

    /** Rebuild the Top Left Widget (after choosing its widgets). */
    void remini() {
        if (mini == null) return;
        showMini(false);
        showMini(true);
    }

    /** Swipe the Top Left Widget: the next (or previous) chosen widget slides in. */
    private SwipeBox catcher;
    private View mask; // the round radar crop: only on the Centre Stage (the small map stays a square widget)
    private boolean miniOverMap; // the alternate widgets shown over the small map (Tile Drawer locked open)

    /** Swipe on the small map: with the Tile Drawer locked open, its widgets come in (swipe on through them, back to the map). */
    private void swipeSmall() {
        MainActivity a = MainActivity.I;
        if (a == null || !a.drawerOpen || !MainActivity.drawerLocked() || bigHolder != null) return;
        miniOverMap = true;
        LeftStages.step(0);
        showMini(true);
        if (mini != null && Ui.ms(1) > 0) {
            int dir = catcher.dir;
            mini.setTranslationX(dir * view.getWidth());
            mini.animate().translationX(0).setDuration(Ui.ms(260)).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        }
    }

    /** Back to the small map from the widgets swiped over it. */
    void mapBack() {
        if (!miniOverMap) return;
        miniOverMap = false;
        if (bigHolder == null) showMini(false);
    }

    private void swapMini() {
        if (mini == null) return;
        int dir = mini.dir;
        if (miniOverMap && bigHolder == null) { // over the small map: past the last (or first) widget, the map comes back
            java.util.List<String> l = LeftStages.list();
            int i = l.indexOf(LeftStages.current());
            if ((dir > 0 && i == l.size() - 1) || (dir < 0 && i == 0) || l.size() < 2) {
                final View old = mini;
                Ui.ui.removeCallbacks(miniTick);
                mini = null;
                miniW = null;
                miniOverMap = false;
                old.animate().translationX(-dir * view.getWidth()).setDuration(Ui.ms(260)).withEndAction(() -> view.removeView(old)).start();
                if (Ui.ms(260) == 0) view.removeView(old);
                return;
            }
        }
        if (LeftStages.list().size() < 2) return;
        LeftStages.step(dir);
        final View old = mini;
        float w = view.getWidth();
        Ui.ui.removeCallbacks(miniTick);
        mini = null;
        miniW = null;
        showMini(true);
        if (mini == null) return;
        mini.setTranslationX(dir * w);
        mini.animate().translationX(0).setDuration(Ui.ms(260)).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        old.animate().translationX(-dir * w).setDuration(Ui.ms(260)).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                .withEndAction(() -> view.removeView(old)).start();
        if (Ui.ms(260) == 0) view.removeView(old);
    }

    private final Runnable miniTick = new Runnable() {
        @Override
        public void run() {
            if (miniW == null) return;
            try {
                miniW.tick();
            } catch (Exception ignored) {
            }
            Ui.ui.postDelayed(this, 1000);
        }
    };

    /** Move the live map into the 2×4 tile area (and back). The same map moves, so nothing reloads. */
    private void bigMap(boolean on) {
        MainActivity a = MainActivity.I;
        if (a == null) return;
        if (on && bigHolder == null) {
            bigHolder = new FrameLayout(a);
            // no background: the round map sits straight on the home behind it, the corners around it see-through
            view.removeView(mapLayer);
            bigHolder.addView(mapLayer, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            if (catcher != null) catcher.setVisibility(View.GONE); // big: the map takes the touches
            js("setRound(true)"); // round on the Centre Stage
            mapLayer.setVisibility(View.VISIBLE);
            bigHolder.addView(controls(a), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            trafficLook(); // the big map has its own traffic button
            pill(true);
            js("setInteractive(true)");
            Ui.ui.post(controlTick);
            int at = a.tileArea.indexOfChild(a.sheetHost); // under panels and alerts
            a.tileArea.addView(bigHolder, Math.max(0, at), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            a.tilesBehind(false); // the tiles would otherwise show through the round map's corners
        } else if (!on && bigHolder != null) {
            Ui.ui.removeCallbacks(controlTick);
            js("setInteractive(false)");
            pill(false);
            bigHolder.removeView(mapLayer);
            ((android.view.ViewGroup) bigHolder.getParent()).removeView(bigHolder);
            bigHolder = null;
            view.addView(mapLayer, 0, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            if (catcher != null) catcher.setVisibility(View.VISIBLE);
            a.tilesBehind(true);
            js("setRound(false)"); // square again
            trafficLook();
        }
    }

    // ------------------------------------------------------------------ big map controls

    private boolean following = true;
    private int scaleW;
    private String scaleText = "";
    private TextView street, limit, spd, scaleLbl;
    private View recentre, scaleBar, zoomBox;
    private ImageView compass;

    private View smallTraffic;

    /** Live traffic on/off (official Google layer on the Google map, Google's tiles on the others). */
    private void toggleTraffic() {
        boolean on = !Prefs.flag("map_traffic", true);
        Prefs.put("map_traffic", on);
        js("setTraffic(" + on + ")");
        trafficLook();
        updateControls();
    }

    private void trafficLook() {
        if (smallTraffic == null) return;
        boolean on = Prefs.flag("map_traffic", true);
        smallTraffic.setBackground(on ? Ui.oval(Ui.ACCENT) : btn(20));
        smallTraffic.setVisibility(bigHolder == null ? View.VISIBLE : View.GONE);
    }

    private void js(String code) {
        if (web != null && pageReady) web.evaluateJavascript(code, null);
    }

    /** Navigate: a full-width bar on the small map, a centred pill on the big one. */
    private void pill(boolean big) {
        FrameLayout.LayoutParams gl = big ? new FrameLayout.LayoutParams(Ui.WRAP, Ui.u(42), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
                : new FrameLayout.LayoutParams(Ui.MATCH, Ui.u(36), Gravity.BOTTOM);
        gl.setMargins(Ui.u(10), 0, Ui.u(10), Ui.u(big ? 12 : 10));
        go.setLayoutParams(gl);
        Ui.pad(go, big ? 22 : 0, 0, big ? 22 : 0, 0);
        go.setBackground(Ui.round(Ui.ACCENT, big ? 21 : 12));
        go.setElevation(big ? Ui.uf(4) : 0);
    }

    private static final int BTN_BG = 0xEB141922, BTN_EDGE = 0x33FFFFFF;

    /** A map button's background: glass in the Glass theme (like the stage around the map), else dark. */
    private static android.graphics.drawable.Drawable btn(float r) {
        return Themes.on() ? Themes.card(Ui.CARD, r) : Ui.outline(BTN_BG, BTN_EDGE, 1, r);
    }

    private View round(Context a, int icon, boolean tint, Runnable r) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(btn(24));
        f.setElevation(Ui.uf(4));
        ImageView iv = tint ? Ui.icon(a, icon, Ui.TEXT) : new ImageView(a);
        if (!tint) iv.setImageResource(icon);
        f.addView(iv, Ui.flpu(25, 25, Gravity.CENTER));
        f.setOnClickListener(v -> r.run());
        Ui.press(f);
        return f;
    }

    private FrameLayout controls(Context a) {
        FrameLayout o = new FrameLayout(a);
        // street you're on
        street = Ui.line(a, "", 18, Ui.TEXT, true); // area / street name: easy to read at a glance
        street.setBackground(btn(14));
        Ui.pad(street, 14, 7, 14, 7);
        street.setElevation(Ui.uf(4));
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.START);
        rl.setMargins(Ui.u(10), Ui.u(10), Ui.u(60), 0);
        o.addView(street, rl);
        // right-hand column: close, compass, traffic
        LinearLayout col = Ui.col(a);
        if (!MainActivity.mapHome()) col.addView(round(a, R.drawable.ic_x, true, () -> expand(false)), Ui.lpu(48, 48)); // the map is home: no close
        View cb = round(a, R.drawable.ic_compass, false, () -> { // direction of travel ⇄ north up
            Prefs.put("map_heading_up", !headingUp());
            applyHeading();
            updateControls();
        });
        compass = (ImageView) ((FrameLayout) cb).getChildAt(0);
        LinearLayout.LayoutParams cbl = Ui.lpu(48, 48);
        cbl.topMargin = col.getChildCount() > 0 ? Ui.u(8) : 0;
        col.addView(cb, cbl);
        // day / night colours: tap goes Auto → Light → Dark (moon = dark, sun = light, blue = auto)
        View db = round(a, R.drawable.ic_sun, true, () -> {
            String m = colours();
            Prefs.put("map_colours", "auto".equals(m) ? "light" : "light".equals(m) ? "dark" : "auto");
            android.widget.Toast.makeText(a, "Map colours: " + ("auto".equals(colours()) ? "Auto (day / night)" : "light".equals(colours()) ? "Light" : "Dark"), android.widget.Toast.LENGTH_SHORT).show();
            styleDay();
            reload();
        });
        dayIcon = (ImageView) ((FrameLayout) db).getChildAt(0);
        LinearLayout.LayoutParams dbl = Ui.lpu(48, 48);
        dbl.topMargin = Ui.u(8);
        col.addView(db, dbl);
        styleDay();
        if (GoogleTraffic.available()) {
            View tb = round(a, R.drawable.ic_traffic, true, this::toggleTraffic);
            tb.setTag("traffic");
            LinearLayout.LayoutParams tl = Ui.lpu(48, 48);
            tl.topMargin = Ui.u(8);
            col.addView(tb, tl);
        }
        // one column down the right-hand side: these, then recentre and zoom at the bottom; spread evenly
        // over the stage's height (spread() below), whatever the size and whichever buttons are showing
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Ui.WRAP, Ui.MATCH, Gravity.END);
        cl.setMargins(0, Ui.u(10), Ui.u(10), Ui.u(12));
        o.addView(col, cl);
        // zoom
        LinearLayout zoom = Ui.col(a);
        zoom.setBackground(btn(24));
        zoom.setElevation(Ui.uf(4));
        View zin = Ui.icon(a, R.drawable.ic_plus, Ui.TEXT), zout = Ui.icon(a, R.drawable.ic_minus, Ui.TEXT);
        Ui.pad(zin, 11, 11, 11, 11);
        Ui.pad(zout, 11, 11, 11, 11);
        zin.setOnClickListener(v -> js("zoomBy(1)"));
        zout.setOnClickListener(v -> js("zoomBy(-1)"));
        zoom.addView(zin, Ui.lpu(48, 48));
        View sep = new View(a);
        sep.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams sl = Ui.lpu(24, 1);
        sl.gravity = Gravity.CENTER_HORIZONTAL;
        zoom.addView(sep, sl);
        zoom.addView(zout, Ui.lpu(48, 48));
        View zoomView = zoom;
        zoomBox = zoom;
        // back to my location (only once you've moved the map)
        recentre = round(a, R.drawable.ic_myloc, true, () -> js("recenter()"));
        recentre.setBackground(Ui.oval(Ui.ACCENT));
        col.addView(recentre, Ui.lpu(48, 48)); // above the zoom buttons
        col.addView(zoomView, Ui.lp(Ui.WRAP, Ui.WRAP));
        spread(col);
        // bottom left: limit sign, speed, scale
        LinearLayout bl = Ui.row(a);
        bl.setGravity(Gravity.CENTER_VERTICAL);
        limit = Ui.text(a, "", 17, 0xFF111111, true);
        limit.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable sign = Ui.oval(0xFFFFFFFF);
        sign.setStroke(Ui.u(3), 0xFFD33A3A);
        limit.setBackground(sign);
        bl.addView(limit, Ui.lpu(50, 50));
        spd = Ui.line(a, "", 20, Ui.TEXT, true); // your speed: big
        spd.setBackground(btn(14));
        Ui.pad(spd, 14, 6, 14, 6);
        spd.setElevation(Ui.uf(4));
        LinearLayout.LayoutParams pl = Ui.lp(Ui.WRAP, Ui.WRAP);
        pl.leftMargin = Ui.u(8);
        bl.addView(spd, pl);
        LinearLayout sc = Ui.col(a);
        scaleBar = new View(a);
        scaleBar.setBackground(Ui.outline(0x00000000, 0xFFC8D0DC, 1, 0));
        sc.addView(scaleBar, new LinearLayout.LayoutParams(Ui.u(50), Ui.u(5)));
        scaleLbl = Ui.line(a, "", 11, 0xFFC8D0DC, false);
        sc.addView(scaleLbl);
        LinearLayout.LayoutParams scl = Ui.lp(Ui.WRAP, Ui.WRAP);
        scl.leftMargin = Ui.u(10);
        bl.addView(sc, scl);
        FrameLayout.LayoutParams bll = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.START);
        bll.setMargins(Ui.u(10), 0, 0, Ui.u(13));
        o.addView(bl, bll);
        updateControls();
        return o;
    }

    private ImageView dayIcon;

    private void styleDay() {
        if (dayIcon == null) return;
        boolean dark = darkNow();
        dayIcon.setImageResource(dark ? R.drawable.ic_moon : R.drawable.ic_sun);
        dayIcon.setColorFilter("auto".equals(colours()) ? Ui.ACCENT_SOFT : Ui.TEXT);
    }

    private final Runnable controlTick = new Runnable() {
        @Override
        public void run() {
            if (bigHolder == null) return;
            updateControls();
            Ui.ui.postDelayed(this, 1000);
        }
    };

    /** Even gaps between a column's buttons, filling its height (a hidden button's gap closes up). */
    private static void spread(LinearLayout col) {
        java.util.List<View> kids = new java.util.ArrayList<>();
        for (int i = 0; i < col.getChildCount(); i++) kids.add(col.getChildAt(i));
        java.util.List<android.view.ViewGroup.LayoutParams> lps = new java.util.ArrayList<>();
        for (View k : kids) lps.add(k.getLayoutParams());
        col.removeAllViews();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int i = 0; i < kids.size(); i++) {
            if (i > 0) col.addView(new View(col.getContext()), new LinearLayout.LayoutParams(1, 0, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(lps.get(i));
            lp.topMargin = 0;
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            col.addView(kids.get(i), lp);
        }
    }

    private void updateControls() {
        if (street == null || bigHolder == null) return;
        String r = SpeedLimit.road != null && !SpeedLimit.road.isEmpty() ? SpeedLimit.road : Gps.town;
        street.setText(r == null ? "" : r);
        street.setVisibility(r == null || r.isEmpty() ? View.GONE : View.VISIBLE);
        boolean mph = Widgets.mph();
        int lim = SpeedLimit.kmh;
        limit.setText(lim > 0 ? String.valueOf(mph ? Math.round(lim / 1.609f) : lim) : "");
        limit.setVisibility(lim > 0 ? View.VISIBLE : View.GONE);
        if (Gps.speed >= 0 && Gps.fresh()) {
            float v = Gps.speed * 3.6f;
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
        boolean hu = headingUp() && following && !Float.isNaN(Gps.bearing);
        compass.animate().rotation(hu ? -Gps.bearing : 0).setDuration(Ui.ms(600)).start();
        View tb = bigHolder.findViewWithTag("traffic");
        if (tb != null) tb.setBackground(Prefs.flag("map_traffic", true) ? Ui.oval(Ui.ACCENT) : btn(19));
    }

    private void updateNav() {
        MediaListener.Nav n = MediaListener.nav;
        boolean navSwap = n != null && NavCard.swap();
        bigMap(tapBig || navSwap); // swap: map into the tile area, directions stay here
        if (n == null || NavCard.inTiles()) { // instructions shown in the tile area instead: keep the map
            navCard.setVisibility(View.GONE);
            mapLayer.setVisibility(View.VISIBLE);
            if (bigHolder != null) miniOverMap = false;
            showMini(bigHolder != null || miniOverMap); // map is big (or swiped aside): the stage shows its widgets
            return;
        }
        showMini(false);
        navCard.setVisibility(View.VISIBLE);
        if (bigHolder == null) mapLayer.setVisibility(View.INVISIBLE);
        Bitmap bm = n.icon;
        if (bm != null) turnIcon.setImageBitmap(bm);
        else turnIcon.setImageResource(R.drawable.ic_turn);
        dist.setText(n.distance != null ? n.distance : "");
        dist.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.uf(n.distance != null && n.distance.length() > 7 ? 22 : 30));
        road.setText(n.instruction != null ? n.instruction : "");
        eta.setText(n.eta != null ? n.eta : "");
        navApp.setText(Apps.label(MediaListener.navPkg));
    }
}
