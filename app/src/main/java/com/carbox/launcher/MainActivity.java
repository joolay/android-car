package com.carbox.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The home screen. Everything is built in code and sized for an 800×480 design, scaled to the real screen. */
public class MainActivity extends Activity {
    static MainActivity I;
    static final int REQ_WALLPAPER = 41;

    FrameLayout root;
    private FrameLayout overlay;
    FrameLayout tileArea, sheetHost;
    private LinearLayout middle;
    private FrameLayout tickerSlot;
    Maps map;
    NowPlaying nowPlaying;
    TileDrawer tiles;
    NewsTicker ticker;
    Dock dock;
    Rail rail;
    private boolean resumed;
    boolean pendingAutostart;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        I = this;
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        w.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        Ui.init(this);
        Prefs.init(this);
        Trace.atStart(this); // troubleshooting mode: log from here on
        About.catchCrashes(this);
        Apps.init(this);
        Status.init(this);
        Gps.init(this);
        Rest.init(this);
        Salah.start(this);
        HfpCalls.start(this);
        NavCard.start();
        SpeedLimit.start();
        Logbook.start();
        SpeedCard.start();
        CarHA.start();
        HomeApproach.start();
        Ui.ui.postDelayed(HomeApproach::fromGoTo, 8000);
        build();
        immersive();
        askPermissions();
        Actions.connectionsAtStart(this);
        Ui.ui.postDelayed(Actions.tsWatch, 60000);
        // music starts first: at a car start the music app is opened (so it wakes and plays), then
        // Home comes back with the splash; hotspot settings and auto-start apps once it has closed
        Runnable splash = () -> {
            if (!Splash.maybeShow(this, "start", () -> Actions.afterSplash(this))) Actions.afterSplash(this);
            Ui.ui.post(() -> { // no splash voice this time: the music starts now
                if (!Splash.voiceStarted) Music.go("no splash voice");
            });
        };
        if (Music.atStart(this) && Music.open(this)) Ui.ui.postDelayed(() -> {
            NowPlaying.pressHome();
            // the splash once Home is really back in front: shown straight away, the Home press
            // arriving a moment later closed it again (only its voice was left)
            final long until = android.os.SystemClock.elapsedRealtime() + 4000;
            Ui.ui.post(new Runnable() {
                @Override
                public void run() {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if ((frontAt != 0 && now - frontAt > 600) || now > until) splash.run();
                    else Ui.ui.postDelayed(this, 150);
                }
            });
        }, 2500);
        else splash.run();
        Ui.ui.post(() -> handleOpen(getIntent()));
        Ui.ui.postDelayed(this::afterUpdate, 2500);
        Wizard.resume(); // the wizard restarted the home for a new theme: carry on
        Ui.ui.postDelayed(About::offerReport, 4000); // crashed last time? offer the report
        Updater.start(this); // new versions from GitHub (needs the key in Settings → System → Updates)
        // start with the big map in the tile area and Go to in the small map panel (setting)
        // the home view: the big map in the tile area, Go to in the small map panel, Now playing
        // below; the tiles are a drawer that slides in from the Rail Bar (All apps button)
        if (mapHome()) Ui.ui.post(() -> { // already open when the home screen first shows
            if (map != null && !map.expanded()) {
                Prefs.put("map_mini", LeftStages.list().get(0)); // the Top Left Widget starts on its first widget
                map.expand(true);
            }
        });
        // traffic for the drive, once per car start (after the splash and auto-start apps settle)
        long bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        if (Math.abs(Prefs.lng("traffic_boot", 0) - bootAt) > 60000) {
            Prefs.put("traffic_boot", bootAt);
            Ui.ui.postDelayed(() -> ticker.trafficAtStart(0), 12000);
        }
        // the launcher can be restarted when it becomes the Home app: carry on with the checklist
        Ui.ui.postDelayed(() -> {
            if (SettingsPanels.backFromChecklist()) SettingsPanels.reopenChecklist();
        }, 400);
    }

    private void build() {
        root = new FrameLayout(this);
        liveWall = new LiveWallpaper(this); // the animated wallpaper and the lighting / weather overlays
        root.addView(liveWall, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        applyWallpaper();
        Ui.ui.postDelayed(skyTick, 60000);
        Weather.listen(() -> { if (liveWall != null) liveWall.update(Themes.Sky.now()); });

        LinearLayout main = Ui.row(this);
        main.setGravity(0);
        Ui.pad(main, 8);
        root.addView(main, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // left column: the two left stages. Now Playing (a fixture) on one, the alternate stage on the other
        // (its widgets, and Maps when it minimises out of the Centre Stage)
        leftCol = Ui.col(this);
        map = new Maps(this);
        nowPlaying = new NowPlaying(this);
        bottomSlot = new FrameLayout(this); // Now Playing's stage
        bottomLeft();
        placeStages();
        main.addView(leftCol, Ui.lpu(200, Ui.MATCH));

        // middle: tiles, ticker, dock
        middle = Ui.col(this);
        tiles = new TileDrawer(this);
        // the tile area also hosts panels (All apps, settings, phone...) so they stay inside the 2×4 grid
        tileArea = new FrameLayout(this);
        tilesClip = new FrameLayout(this); // the tiles stay inside the tile area as they rise and sink
        tilesClip.addView(tiles.view, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        tileArea.addView(tilesClip, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        sheetHost = new FrameLayout(this);
        tileArea.addView(sheetHost, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        tickerSlot = new FrameLayout(this);
        ticker = new NewsTicker(this);
        tickerSlot.addView(ticker.view, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        LinearLayout tickerRow = Ui.row(this); // ticker, then "Tiles" + lock while the drawer is open
        tickerRow.addView(tickerSlot, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        boolean tickerOn = Prefs.flag("ticker_on", true);
        if (tickerOn) buildChip(tickerRow);
        // the ticker sits at the top of the middle column, above the tiles / big map
        LinearLayout.LayoutParams tl = Ui.lpu(Ui.MATCH, 36); // a little taller, easier to read
        tl.bottomMargin = Ui.u(8);
        middle.addView(tickerRow, tl);
        this.tickerRow = tickerRow;
        tickerRow.setVisibility(tickerOn ? View.VISIBLE : View.GONE); // off: the map / tiles take its space (still shown while editing tiles)
        middle.addView(tileArea, Ui.weight(1, false));
        tilePad(tickerOn);
        dock = new Dock(this);
        LinearLayout dockRow = Ui.row(this);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.addView(dock.view, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        if (!tickerOn) buildChip(dockRow); // "Tiles" + lock at the end of the dock instead
        LinearLayout.LayoutParams dl = Ui.lpu(Ui.MATCH, 64); // same height with or without the ticker (the map / tiles give way)
        dl.topMargin = Ui.u(8);
        middle.addView(dockRow, dl);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, Ui.MATCH, 1);
        ml.leftMargin = Ui.u(8);
        ml.rightMargin = Ui.u(8);
        main.addView(middle, ml);
        main.setClipChildren(false); // the big map shrinks across into the small map panel
        middle.setClipChildren(false);
        tileArea.setClipChildren(false);

        // right rail
        rail = new Rail(this);
        main.addView(rail.view, Ui.lpu(72, Ui.MATCH));
        // a video's picture-in-picture window keeps off the map, Now playing, ticker, dock and Rail Bar,
        // so it settles over the tiles (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            for (View k : new View[]{leftCol, tickerRow, dockRow, rail.view}) k.setPreferKeepClear(true);
        }

        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        Panels.attach(sheetHost, overlay);
        setContentView(root);
        dock.render();
    }

    /** Swap the ticker for the edit bar while tiles are being rearranged. */
    private LinearLayout tickerRow;

    private FrameLayout tilesClip;

    // ---- the Bottom Left Widget
    private FrameLayout bottomSlot;
    private Widgets.W bottomW;
    private final Runnable bottomTick = new Runnable() {
        @Override
        public void run() {
            if (bottomW == null) return;
            try {
                bottomW.tick();
            } catch (Exception ignored) {
            }
            Ui.ui.postDelayed(this, 1000);
        }
    };

    private LinearLayout leftCol;

    /** Now Playing's stage always holds the one Now Playing. */
    void bottomLeft() {
        if (bottomSlot == null) return;
        Ui.ui.removeCallbacks(bottomTick);
        bottomW = null;
        View np = nowPlaying.view;
        if (np.getParent() == bottomSlot) return;
        if (np.getParent() != null) ((android.view.ViewGroup) np.getParent()).removeView(np);
        bottomSlot.addView(np, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    /** Order the left stages: Now Playing on top or below, the alternate stage (with Maps when minimised) on the other. */
    void placeStages() {
        if (leftCol == null) return;
        leftCol.removeAllViews();
        boolean npTop = LeftStages.nowPlayingTop();
        View first = npTop ? bottomSlot : map.view, second = npTop ? map.view : bottomSlot;
        leftCol.addView(first, Ui.weight(1, false));
        LinearLayout.LayoutParams lp = Ui.weight(1, false);
        lp.topMargin = Ui.u(8);
        leftCol.addView(second, lp);
    }

    /**
     * Without the ticker the tile area is taller: the big map simply grows, and the tiles keep their
     * size with the extra height as padding above and below the grid.
     */
    private void tilePad(boolean tickerShown) {
        // ticker hidden: wider, even gaps (12 between tiles, and 12 down to the dock), the tops lined up
        // with the map and the Rail Bar, and the tiles taller with what's left
        TileDrawer.gapDu = tickerShown ? 8 : 12;
        tilesClip.setPadding(0, 0, 0, tickerShown ? 0 : Ui.u(4));
        if (tiles != null) tiles.view.requestLayout();
    }

    // ---- a tile-area page's title bar sits in the ticker's place while it's open
    private View title;

    boolean canHoldTitle() {
        return tickerRow != null && tickerRow.getVisibility() == View.VISIBLE && !tiles.editing();
    }

    /**
     * Expanded windows on the Centre Stage put their title and buttons in the News Ticker's place (themed bar),
     * so the window itself gets all the room. Returns false when the ticker is hidden (keep the header inside).
     */
    boolean titleToTicker(View head) {
        if (!canHoldTitle() || head == null) return false;
        head.setBackground(Themes.card(Ui.CARD, 12));
        Ui.pad(head, 10, 0, 10, 0);
        tickerTitle(head);
        return true;
    }

    void tickerTitle(View head) {
        if (tickerSlot == null || tiles.editing()) return;
        if (head == title) return;
        title = head;
        tickerSlot.removeAllViews();
        if (head != null) {
            if (head.getParent() != null) ((android.view.ViewGroup) head.getParent()).removeView(head);
            tickerSlot.addView(head, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            head.setAlpha(0f);
            head.animate().alpha(1f).setDuration(Ui.ms(200)).start();
        } else tickerSlot.addView(ticker.view, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    void editMode(boolean on) {
        title = null;
        if (tickerRow != null && !Prefs.flag("ticker_on", true)) {
            tickerRow.setVisibility(on ? View.VISIBLE : View.GONE);
            tilePad(on); // the edit bar takes the padding's place while editing
        }
        tickerSlot.removeAllViews();
        if (on) tickerSlot.addView(NewsTicker.editBar(this, () -> tiles.setEditing(false)), new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        else tickerSlot.addView(ticker.view, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    // ------------------------------------------------------------------ wallpaper

    LiveWallpaper liveWall;

    /** The lighting follows the time of day: nudge the wallpaper every minute so it keeps up. */
    private final Runnable skyTick = new Runnable() {
        @Override
        public void run() {
            if (liveWall != null) liveWall.update(Themes.Sky.now());
            Ui.ui.postDelayed(this, 60000);
        }
    };

    void applyWallpaper() {
        String mode = Themes.simple() ? "black" : Prefs.str(Prefs.WALL, "road"); // Simple theme: always plain black
        if ("color".equals(mode)) {
            root.setBackground(new ColorDrawable(Prefs.num(Prefs.WALL_COLOR, Ui.BG)));
        } else if ("image".equals(mode)) {
            String uri = Prefs.str("wall_uri", null);
            Bitmap bm = null;
            if (uri != null) {
                try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;
                    bm = BitmapFactory.decodeStream(in, null, o);
                } catch (Exception ignored) {
                }
            }
            if (bm != null) {
                BitmapDrawable d = new BitmapDrawable(getResources(), bm);
                root.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                        new CenterCrop(d), new ColorDrawable(0x66000000)}));
            } else root.setBackground(new ColorDrawable(0xFF000000));
        } else if (LiveWallpaper.animated(mode)) { // an animated scene (road, fish tank, engine, flux, pistons, cogs)
            root.setBackground(new ColorDrawable(LiveWallpaper.base(mode)));
        } else root.setBackground(new ColorDrawable(0xFF000000));
        if (liveWall != null) {
            liveWall.setScene(mode);
            liveWall.update(Themes.Sky.now());
        }
        Themes.refresh(); // glass cards show the new wallpaper
        applyInvert();
    }

    private static final android.graphics.Paint INVERT = new android.graphics.Paint();
    static {
        INVERT.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{
                -1, 0, 0, 0, 255, 0, -1, 0, 0, 255, 0, 0, -1, 0, 255, 0, 0, 0, 1, 0}));
    }

    /** Simple Inverted: invert the whole home (white background, black text, colours flipped); the map is
     *  inverted a second time so it stays normal and readable. */
    void applyInvert() {
        if (root == null) return;
        boolean inv = Themes.inverted();
        root.setLayerType(inv ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, inv ? INVERT : null);
        if (map != null && map.view != null)
            map.view.setLayerType(inv ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, inv ? INVERT : null);
    }

    /** Draws a bitmap like ImageView's centerCrop. */
    static final class CenterCrop extends android.graphics.drawable.Drawable {
        private final BitmapDrawable d;

        CenterCrop(BitmapDrawable d) {
            this.d = d;
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            Bitmap bm = d.getBitmap();
            float s = Math.max(b.width() / (float) bm.getWidth(), b.height() / (float) bm.getHeight());
            float w = bm.getWidth() * s, h = bm.getHeight() * s;
            android.graphics.RectF dst = new android.graphics.RectF(b.left + (b.width() - w) / 2, b.top + (b.height() - h) / 2,
                    b.left + (b.width() + w) / 2, b.top + (b.height() + h) / 2);
            c.drawBitmap(bm, null, dst, d.getPaint());
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    static final int REQ_AUDIO = 71;

    /** Pick an audio file (e.g. a recitation recording) to play instead of the built-in voice. */
    void pickAudio() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), REQ_AUDIO);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "No file picker on this box", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    void pickWallpaperImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try {
            startActivityForResult(i, REQ_WALLPAPER);
        } catch (Exception e) {
            try {
                startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_WALLPAPER);
            } catch (Exception ignored) {
                android.widget.Toast.makeText(this, "No file picker on this box", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == InAppKeyboard.REQ_VOICE && res == RESULT_OK) InAppKeyboard.voiceResult(data);
        if (req == 62) Panels.refresh(); // back from "make Car Launcher the Phone app"
        if (req == REQ_AUDIO && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            Prefs.put("splash_audio_uri", data.getData().toString());
            Prefs.put("splash_audio", "own");
            Panels.refresh();
        }
        if (req == OfflineMap.REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            OfflineMap.picked(this, data.getData());
            if (map != null) map.reload();
            Panels.refresh();
        }
        if (req == REQ_WALLPAPER && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            Prefs.put("wall_uri", u.toString());
            Prefs.put(Prefs.WALL, "image");
            applyWallpaper();
            Panels.refresh();
        }
    }

    // ------------------------------------------------------------------ night (dims this screen without needing extra permission)

    void applyNight() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; // night dimming removed: the box controls brightness
        getWindow().setAttributes(lp);
    }

    // ------------------------------------------------------------------ full screen

    void immersive() {
        Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    // ------------------------------------------------------------------ permissions

    private void askPermissions() {
        String[] want = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALENDAR, Manifest.permission.READ_CONTACTS,
                android.os.Build.VERSION.SDK_INT >= 31 ? Manifest.permission.BLUETOOTH_CONNECT : Manifest.permission.READ_CONTACTS}; // Bluetooth: to see BT calls
        List<String> need = new ArrayList<>();
        for (String p : want) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (!need.isEmpty() && !Prefs.flag("asked_perms2", false)) {
            Prefs.put("asked_perms2", true); // asked again once, for the new Bluetooth permission
            requestPermissions(need.toArray(new String[0]), 1); // the set-up checklist follows when this closes
        } else Ui.ui.postDelayed(this::firstRunChecklist, 1500);
    }

    /** First run: the checklist for permissions Android only grants in its own settings screens. */
    /** After installing a new version: offer to run the set-up checklist again (new steps may have been added). */
    /** Close Car Launcher and open it again a moment later (a fresh start). */
    void restartApp() {
        Prefs.flush();
        Trace.event(this, "Restart Car Launcher (from Settings)");
        android.content.Intent i = new android.content.Intent(this, MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 7, i, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_CANCEL_CURRENT);
        android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (am != null) am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 800, pi);
        finishAffinity();
        android.os.Process.killProcess(android.os.Process.myPid()); // as Home, Android brings it straight back anyway
    }

    /** Android won't let an app restart the box itself: open the power menu (Restart is in it) through the Call helper. */
    void restartBox() {
        if (BackButtonService.I != null && BackButtonService.I.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)) {
            android.widget.Toast.makeText(this, "Tap Restart", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        android.widget.Toast.makeText(this, "Switch the car off and on again to restart the box (or turn on the Call helper so this button can open the power menu)", android.widget.Toast.LENGTH_LONG).show();
    }

    private void afterUpdate() {
        String ver;
        long code;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
            code = pi.getLongVersionCode();
        } catch (Exception e) {
            return;
        }
        long last = Prefs.lng("last_version", -1);
        Prefs.put("last_version", code);
        // no record yet: an older version that didn't keep one counts as an update (it had shown the checklist)
        boolean updated = last < 0 ? Prefs.flag("setup_shown", false) : last != code;
        if (!updated || isFinishing()) return; // first install (the checklist runs anyway) or no change
        if (Panels.open() || CallState.state != CallState.NONE) return; // don't interrupt
        final String[] news = changes(ver);
        Panels.show(() -> {
            android.content.Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Now on " + ver + ". Restart the box so everything starts fresh?", 17, Ui.TEXT, true));
            if (news.length > 0) { // a very short outline of what's new
                LinearLayout list = Ui.col(c);
                for (String n : news) {
                    if (n.trim().isEmpty()) continue;
                    android.widget.TextView t = Ui.text(c, "•  " + n.trim(), 16, Ui.TEXT2, false);
                    list.addView(t, Widgets.top(5));
                }
                b.addView(list, Widgets.top(8));
            }
            LinearLayout btns = Ui.row(c);
            btns.addView(Ui.button(c, "Restart box", true, () -> {
                Panels.close();
                restartBox();
            }));
            btns.addView(Ui.gap(c, 10, 1));
            btns.addView(Ui.button(c, "Run set-up", false, () -> {
                Panels.close();
                SettingsPanels.setupChecklist();
            }));
            btns.addView(Ui.gap(c, 10, 1));
            btns.addView(Ui.button(c, "Not now", false, Panels::close));
            b.addView(btns, Widgets.top(18));
            b.addView(Ui.text(c, "Closes by itself in 15 s · set-up ticks stay ticked", 13, Ui.SUB, false), Widgets.top(8));
            // closes by itself after 15 s, if it's still the page showing
            b.postDelayed(() -> {
                if (b.isAttachedToWindow()) Panels.close();
            }, 15000);
            return Panels.panel("Updated", b, 560, 400);
        });
    }

    /** What's new in this version, from assets/changes.txt ("0.29: one; two; three"). */
    private String[] changes(String ver) {
        String v = ver == null ? "" : ver.replaceAll("^(\\d+\\.\\d+).*", "$1");
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open("changes.txt")))) {
            for (String l; (l = r.readLine()) != null; )
                if (l.startsWith(v + ":")) return l.substring(v.length() + 1).split(";");
        } catch (Exception ignored) {
        }
        return new String[0];
    }

    private void firstRunChecklist() {
        if (isFinishing()) return;
        if (!Wizard.done()) { // the welcome wizard first; the set-up checklist follows it
            Prefs.put("setup_shown", true);
            Wizard.show();
            return;
        }
        if (Prefs.flag("setup_shown", false)) return;
        Prefs.put("setup_shown", true);
        if (!MediaListener.hasAccess(this) || !SettingsPanels.isDefaultHome(this)) SettingsPanels.setupChecklist();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        if (req == 61) { // contacts / call history for the phone screen
            PhoneScreen.refresh();
            return;
        }
        if (req == 54) { // microphone for the Car keyboard
            Panels.refresh();
            return;
        }
        if (req == InAppKeyboard.REQ_MIC) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) InAppKeyboard.micGranted();
            return;
        }
        Gps.stop();
        Gps.start();
        Status.refreshAndNotify();
        tiles.refresh();
        if (req == 1) Ui.ui.postDelayed(this::firstRunChecklist, 600);
    }

    // ------------------------------------------------------------------ lifecycle

    static long frontAt; // when the home screen last came to the front (0 = not in front)

    @Override
    protected void onResume() {
        frontAt = android.os.SystemClock.elapsedRealtime();
        Trace.event(this, "Home screen shown");
        super.onResume();
        resumed = true;
        immersive();
        applyNight();
        Gps.start();
        Status.start();
        if (CallState.state != CallState.NONE) CallCard.show(); // back home during a call
        NavCard.update();
        Weather.maybeFetch();
        ticker.start();
        tiles.start();
        nowPlaying.start();
        map.start();
        rail.paused(false);
        if (liveWall != null) liveWall.run(true);
        dock.render();
        // back from Android's keyboard settings: show the updated steps
        if (Panels.open() && Panels.top() != null && (Panels.top() == SettingsPanels.keyboardPage || Panels.top() == SettingsPanels.setupPage || Panels.top() == SettingsPanels.floatPage || Panels.top() == Optimiser.page)) {
            Panels.refresh();
        }
        if (pendingAutostart) {
            // back from hotspot settings: now open the auto-start apps
            pendingAutostart = false;
            Ui.ui.postDelayed(() -> Actions.runAutostart(this), 1200);
        }
    }

    @Override
    protected void onPause() {
        frontAt = 0;
        Trace.event(this, "Home screen left (another app in front)");
        super.onPause();
        resumed = false;
        if (liveWall != null) liveWall.run(false); // no wallpaper animation behind other apps
        ticker.stop();
        tiles.stop();
        nowPlaying.stop();
        map.stop();
        rail.paused(true);
        Status.stop();
        // keep GPS running in the background so trip/speed stay accurate while another app is open
    }

    // ---- idle: panels and pop-ups in the tile area minimise 15 s after the last touch anywhere.
    // Calls and turn-by-turn stay; full-screen pages (settings, widgets, All apps) stay.
    private final Runnable idle = () -> {
        if (WhatsWeb.isOpen() || Panels.sticky) { /* WhatsApp, news and traffic stay open until closed by hand */ } else if (Panels.minimisable() || (AppPanels.galleryRef != null && Panels.top() == AppPanels.galleryRef)) Panels.close();
        PhoneScreen.hide();
        SalahCard.hide();
        SpeedCard.hide();
        Alerts.hideNotice();
        if (this.drawerOpen && !tiles.editing() && !drawerLocked()) drawer(false); // the map comes back (unless locked open)
        if (this.peeking) drawer(false);
        hideAlertTile();
    };

    void resetIdle() {
        Ui.ui.removeCallbacks(idle);
        Ui.ui.postDelayed(idle, Prefs.num("card_secs", 15) > 0 ? Math.max(15, Prefs.num("card_secs", 15)) * 1000L : 15000);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN || ev.getActionMasked() == android.view.MotionEvent.ACTION_UP) resetIdle();
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && tiles != null) noteTile(ev);
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && rail != null) noteRail(ev);
        // editing tiles: a touch outside the grid (and its edit bar) finishes editing, and does nothing else
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && tiles != null && tiles.editing() && !Panels.open()
                && !inside(tileArea, ev) && !inside(tickerSlot, ev)) {
            tiles.setEditing(false);
            swallow = true;
            return true;
        }
        if (swallow) {
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_UP || ev.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) swallow = false;
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean swallow;

    // ---- things that open in the tile area grow out of the tile they came from
    private final android.graphics.Rect touchTile = new android.graphics.Rect();
    private long touchAt;

    /** Remember which tile a touch landed on, so what it opens can grow out of it. */
    private void noteTile(android.view.MotionEvent ev) {
        android.graphics.Rect r = new android.graphics.Rect();
        for (int i = 0; i < tiles.view.getChildCount(); i++) {
            View t = tiles.view.getChildAt(i);
            if (t.getGlobalVisibleRect(r) && r.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                touchTile.set(r);
                touchAt = android.os.SystemClock.elapsedRealtime();
                return;
            }
        }
        // the Now playing tile and the Top Left Widget: what they open grows out of them too
        for (View t : new View[]{nowPlaying != null ? nowPlaying.view : null, map != null ? map.view : null}) {
            if (t != null && t.getGlobalVisibleRect(r) && r.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                touchTile.set(r);
                touchAt = android.os.SystemClock.elapsedRealtime();
                return;
            }
        }
    }

    // ---- rail bar pop-up cards pop out of the icon that opened them
    private final android.graphics.Rect railIcon = new android.graphics.Rect();
    private long railAt;

    /** Remember which Rail Bar icon a touch landed on (the smallest clickable view under the finger). */
    private void noteRail(android.view.MotionEvent ev) {
        android.graphics.Rect best = null, r = new android.graphics.Rect();
        java.util.ArrayDeque<View> q = new java.util.ArrayDeque<>();
        q.add(rail.view);
        while (!q.isEmpty()) {
            View v = q.poll();
            if (!v.isShown() || !v.getGlobalVisibleRect(r) || !r.contains((int) ev.getRawX(), (int) ev.getRawY())) continue;
            if (v.isClickable() && (best == null || r.width() * r.height() < best.width() * best.height())) best = new android.graphics.Rect(r);
            if (v instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) q.add(g.getChildAt(i));
            }
        }
        if (best == null) return;
        railIcon.set(best);
        railAt = android.os.SystemClock.elapsedRealtime();
    }

    /** A pop-up card grows out of the Rail Bar icon just tapped (and fades in). */
    void popFromRail(View v) {
        if (android.os.SystemClock.elapsedRealtime() - railAt > 1500 || railIcon.isEmpty()) return;
        final android.graphics.Rect src = new android.graphics.Rect(railIcon);
        v.setAlpha(0f);
        v.post(() -> {
            if (v.getWidth() == 0 || v.getHeight() == 0) {
                v.setAlpha(1f);
                return;
            }
            int[] at = new int[2];
            v.getLocationOnScreen(at);
            // shrink towards the icon's centre: the card starts icon-sized on the icon and grows out of it
            v.setPivotX(src.exactCenterX() - at[0]);
            v.setPivotY(src.exactCenterY() - at[1]);
            float sx = Math.max(0.05f, src.width() / (float) v.getWidth()), sy = Math.max(0.05f, src.height() / (float) v.getHeight());
            v.setScaleX(sx);
            v.setScaleY(sy);
            v.setAlpha(0.4f);
            v.animate().scaleX(1).scaleY(1).alpha(1f).setDuration(Ui.ms(300))
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        });
    }

    /** Where an alert's tile is on screen (or the first spot, over the map). */
    android.graphics.Rect alertRect(String kind) {
        android.graphics.Rect r = new android.graphics.Rect();
        org.json.JSONArray s = TileDrawer.specs();
        for (int i = 0; i < s.length() && i < tiles.view.getChildCount(); i++) {
            org.json.JSONObject o = s.optJSONObject(i);
            if (o != null && kind.equals(Alerts.kindOf(o.optString("id"))) && tiles.view.getChildAt(i).getGlobalVisibleRect(r) && r.width() > Ui.u(40)) return r;
        }
        int[] ta = new int[2];
        tileArea.getLocationOnScreen(ta);
        int cw = (tileArea.getWidth() - Ui.u(8) * 3) / 4, ch = (tileArea.getHeight() - Ui.u(8)) / 2;
        return new android.graphics.Rect(ta[0], ta[1], ta[0] + cw, ta[1] + ch);
    }

    /** Grow v (filling the tile area) out of a tile: `from` on screen, or the tile just touched. */
    void growIn(View v, android.graphics.Rect from) {
        android.graphics.Rect src = from != null ? from : android.os.SystemClock.elapsedRealtime() - touchAt < 1500 ? new android.graphics.Rect(touchTile) : null;
        if (src == null || src.isEmpty()) return;
        v.setAlpha(0f);
        v.post(() -> {
            if (v.getWidth() == 0 || v.getHeight() == 0) {
                v.setAlpha(1f);
                return;
            }
            int[] ta = new int[2];
            tileArea.getLocationOnScreen(ta);
            v.setPivotX(0);
            v.setPivotY(0);
            v.setTranslationX(src.left - ta[0]);
            v.setTranslationY(src.top - ta[1]);
            v.setScaleX(src.width() / (float) v.getWidth());
            v.setScaleY(src.height() / (float) v.getHeight());
            v.setAlpha(0.7f);
            v.animate().translationX(0).translationY(0).scaleX(1).scaleY(1).alpha(1f).setDuration(Ui.ms(320))
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        });
    }

    private static boolean inside(View v, android.view.MotionEvent ev) {
        if (v == null || !v.isShown()) return false;
        android.graphics.Rect r = new android.graphics.Rect();
        v.getGlobalVisibleRect(r);
        return r.contains((int) ev.getRawX(), (int) ev.getRawY());
    }

    boolean isResumed2() {
        return resumed;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Home pressed while already home: close any panel and leave edit mode
        InAppKeyboard.hide();
        if (handleOpen(i)) return;
        // Android sends Home after you pick Car Launcher as the Home app: go back to the checklist
        if (SettingsPanels.backFromChecklist()) {
            Ui.ui.post(SettingsPanels::reopenChecklist);
            return;
        }
        PhoneScreen.hide();
        if (Panels.open()) Panels.close();
        if (tiles.editing()) tiles.setEditing(false);
        if (drawerOpen || peeking) drawer(false);
        Splash.cancel();
    }

    // ---- tile drawer (when the map is the home view)
    boolean drawerOpen;

    /** Maps always lives on the Centre Stage (a fixture); it only minimises into the alternate stage. */
    static boolean mapHome() {
        return true;
    }

    /** All apps button: the tiles rise out of the news ticker, the map lifts away. */
    void toggleDrawer() {
        drawer(!drawerOpen);
    }

    // "Tiles" + a lock at the right of the ticker while the drawer is open: locked, the tiles stay
    // open (no slide-back after 15 s); the ticker shrinks to make room
    private LinearLayout tilesChip;
    private LockView lock;

    static boolean drawerLocked() {
        return Prefs.flag("drawer_lock", false);
    }

    private void buildChip(LinearLayout row) {
        tilesChip = Ui.row(this);
        tilesChip.setGravity(Gravity.CENTER_VERTICAL);
        TextView w = Ui.line(this, "Tiles", 13, Ui.TEXT2, true);
        if (!Prefs.flag("ticker_on", true)) w.setVisibility(View.GONE); // in the dock: just the lock
        tilesChip.addView(w);
        lock = new LockView(this);
        lock.set(drawerLocked(), false);
        lock.setOnClickListener(v -> {
            Prefs.put("drawer_lock", !drawerLocked());
            lock.set(drawerLocked(), true);
            resetIdle();
            Ui.ui.removeCallbacks(lockAway); // pressed: it goes 2 s later
            Ui.ui.postDelayed(lockAway, 2000);
        });
        LinearLayout.LayoutParams ll = Ui.lpu(30, 30);
        ll.leftMargin = Ui.u(8);
        tilesChip.addView(lock, ll);
        tilesChip.setVisibility(View.GONE);
        LinearLayout.LayoutParams cl = Ui.lp(Ui.WRAP, Ui.MATCH);
        cl.leftMargin = Ui.u(8);
        row.addView(tilesChip, cl);
    }

    private final Runnable lockAway = () -> lockButton(false);

    private void lockButton(boolean show) {
        if (tilesChip == null) return;
        if (!show) Ui.ui.removeCallbacks(lockAway);
        tilesChip.animate().cancel();
        if (show) {
            lock.set(drawerLocked(), false);
            tilesChip.setVisibility(View.VISIBLE);
            tilesChip.setAlpha(0f);
            tilesChip.setTranslationX(Ui.u(40));
            tilesChip.animate().alpha(1f).translationX(0).setDuration(Ui.ms(300)).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            // ticker hidden: the lock sits in the dock for 5 s, then goes so the dock has its room back
            Ui.ui.removeCallbacks(lockAway);
            Ui.ui.postDelayed(lockAway, 4000); // the lock (and "Tiles") goes after a few seconds
        } else tilesChip.animate().alpha(0f).translationX(Ui.u(40)).setDuration(Ui.ms(220)).withEndAction(() -> tilesChip.setVisibility(View.GONE)).start();
    }

    // ---- an alert's tile shown over the map in the first spot, with a highlight border
    private FrameLayout alertTile;
    private Widgets.W alertW;
    private final Runnable alertTick = new Runnable() {
        @Override
        public void run() {
            if (alertW == null) return;
            try {
                alertW.tick();
            } catch (Exception ignored) {
            }
            Ui.ui.postDelayed(this, 1000);
        }
    };

    void alertTile(int index) {
        hideAlertTile();
        org.json.JSONObject spec = TileDrawer.specs().optJSONObject(index);
        if (spec == null || !"w".equals(spec.optString("t"))) return;
        try {
            alertW = Widgets.make(this, index, spec);
        } catch (Exception e) {
            return;
        }
        if (alertW == null || alertW.v == null) return;
        float gap = Ui.u(8), cw = (tileArea.getWidth() - gap * (TileDrawer.COLS - 1)) / TileDrawer.COLS, ch = (tileArea.getHeight() - gap) / TileDrawer.ROWS;
        alertTile = new FrameLayout(this);
        alertTile.addView(alertW.v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        Maps.clip(alertTile, 16);
        View ring = new View(this);
        ring.setBackground(Ui.outline(0, Ui.ACCENT, 3, 16));
        alertTile.addView(ring, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        alertTile.setElevation(Ui.uf(8));
        alertTile.setOnClickListener(v -> { // tap: open the tiles
            hideAlertTile();
            drawer(true);
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(cw), Math.round(ch), Gravity.TOP | Gravity.START);
        tileArea.addView(alertTile, tileArea.indexOfChild(sheetHost), lp); // over the map, under panels
        alertTile.setScaleX(0.85f);
        alertTile.setScaleY(0.85f);
        alertTile.setAlpha(0f);
        alertTile.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(Ui.ms(260)).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
        Ui.ui.post(alertTick);
    }

    void hideAlertTile() {
        Ui.ui.removeCallbacks(alertTick);
        alertW = null;
        if (alertTile != null) {
            final View t = alertTile;
            alertTile = null;
            t.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(Ui.ms(200)).withEndAction(() -> {
                if (t.getParent() != null) ((android.view.ViewGroup) t.getParent()).removeView(t);
            }).start();
        }
    }

    /** Alert: slide the map only part way, showing just the first column (the alert tile) at the right. */
    boolean peeking;

    void peek() {
        View big = map != null ? map.bigView() : null;
        if (big == null || drawerOpen) return;
        peeking = true;
        // the top row of tiles (the alert tile is first) rises from the ticker; the map lifts by a row
        float h = tileArea.getHeight() + Ui.u(12), row = tileArea.getHeight() / 2f + Ui.u(4);
        android.view.animation.Interpolator ease = new android.view.animation.DecelerateInterpolator(1.8f);
        tiles.view.setTranslationX(0);
        big.setTranslationX(0);
        tiles.view.setVisibility(View.VISIBLE);
        if (tiles.view.getTranslationY() == 0) tiles.view.setTranslationY(h);
        tiles.view.animate().translationY(h - row).setDuration(Ui.ms(340)).setInterpolator(ease).start();
        big.animate().translationY(-row).setDuration(Ui.ms(340)).setInterpolator(ease).start();
    }

    // ---- the big map steps aside (into the small map panel) while lyrics, a dialler or a panel use the tile area
    private boolean mapAside;
    private long createdAt = android.os.SystemClock.elapsedRealtime();

    void mapAside(boolean on) {
        if (map == null || !mapHome() || drawerOpen) {
            if (!on) mapAside = false;
            return;
        }
        View big = map.bigView();
        // the first 30 s after start (splash, prayer card...): no shrink and grow, the map simply waits
        boolean quiet = android.os.SystemClock.elapsedRealtime() - createdAt < 30000;
        if (on) {
            if (mapAside || !map.expanded() || big == null) return;
            mapAside = true;
            if (quiet) {
                map.expand(false);
                return;
            }
            morph(big, true, () -> {
                if (mapAside) map.expand(false);
            });
        } else {
            if (!mapAside) return;
            Ui.ui.post(() -> { // a moment later, so one thing replacing another doesn't bounce the map
                if (!mapAside || drawerOpen) return;
                if (Panels.inTiles() || LyricsView.isShown() || PhoneScreen.isShown()) return; // something still there
                mapAside = false;
                if (quiet) map.expand(true); // already open, no grow animation
                else growMap();
            });
        }
    }

    /** The big map grows back out of the small map panel. */
    private void growMap() {
        if (map == null) return;
        if (!map.expanded()) map.expand(true);
        View big = map.bigView();
        if (big == null) return;
        big.post(() -> morph(big, false, null));
    }

    void drawer(boolean open) {
        if (open == drawerOpen && !peeking) return;
        mapAside = false;
        boolean fromPeek = peeking;
        peeking = false;
        if (open) hideAlertTile();
        if (!open && map != null) map.mapBack(); // widgets swiped over the small map go back to the map
        drawerOpen = open;
        lockButton(open);
        // the tiles rise up out of the news ticker, while the big map shrinks into the small map
        // panel on the left (and grows back out of it when the tiles go down)
        float h = tileArea.getHeight() + Ui.u(12);
        android.view.animation.Interpolator ease = new android.view.animation.DecelerateInterpolator(1.8f);
        tiles.view.setTranslationX(0);
        if (open) {
            if (Panels.open()) Panels.close();
            tiles.view.setVisibility(View.VISIBLE);
            if (!fromPeek) tiles.view.setTranslationY(h); // from a peek it carries on from where it is
            tiles.view.animate().translationY(0).setDuration(Ui.ms(340)).setInterpolator(ease).start();
            View big = map != null ? map.bigView() : null;
            if (big == null) return;
            morph(big, true, () -> {
                if (drawerOpen) map.expand(false); // the live map is now the small map
            });
        } else {
            if (tiles.editing()) tiles.setEditing(false);
            // once down, hide them so no bits of tiles show between the ticker and the dock
            tiles.view.animate().translationY(h).setDuration(Ui.ms(300)).setInterpolator(ease)
                    .withEndAction(() -> { if (!drawerOpen && !peeking) tiles.view.setVisibility(View.INVISIBLE); }).start();
            if (map == null) return;
            if (!map.expanded()) map.expand(true);
            View big = map.bigView();
            if (big == null) return;
            big.post(() -> morph(big, false, null)); // once it's laid out at full size: start small, grow back
        }
    }

    /** Move and scale that make the big map sit exactly on the small map panel: {dx, dy, sx, sy}. */
    private android.animation.ValueAnimator morphAnim;

    /**
     * The big map shrinks into the small map panel (or grows out of it) without squashing: it scales
     * evenly and its edges are trimmed to the panel's shape, so the middle of the map lands exactly on
     * the panel. When shrinking, `end` swaps the live map into the panel before the big one is reset
     * (no flash of the big map at full size).
     */
    private void morph(View big, boolean shrink, Runnable end) {
        if (morphAnim != null) morphAnim.cancel();
        big.animate().cancel();
        float bw = big.getWidth(), bh = big.getHeight(), mw = map.view.getWidth(), mh = map.view.getHeight();
        if (bw <= 0 || bh <= 0 || mw <= 0 || mh <= 0) {
            if (end != null) end.run();
            resetMorph(big);
            return;
        }
        int[] a = new int[2], b = new int[2];
        map.view.getLocationOnScreen(a);
        tileArea.getLocationOnScreen(b);
        float s, cl = 0, ct = 0;
        if (mw / mh < bw / bh) { // panel narrower than the map: trim the sides
            s = mh / bh;
            cl = (bw - mw / s) / 2f;
        } else { // trim top and bottom
            s = mw / bw;
            ct = (bh - mh / s) / 2f;
        }
        final float tx = a[0] - b[0] - cl * s, ty = a[1] - b[1] - ct * s, sc = s, fcl = cl, fct = ct;
        big.setPivotX(0);
        big.setPivotY(0);
        android.graphics.Rect clip = new android.graphics.Rect();
        android.animation.ValueAnimator.AnimatorUpdateListener step = va -> {
            float v = (float) va.getAnimatedValue(), f = shrink ? v : 1 - v;
            big.setScaleX(1 + (sc - 1) * f);
            big.setScaleY(1 + (sc - 1) * f);
            big.setTranslationX(tx * f);
            big.setTranslationY(ty * f);
            clip.set(Math.round(fcl * f), Math.round(fct * f), Math.round(bw - fcl * f), Math.round(bh - fct * f));
            big.setClipBounds(clip);
        };
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(Ui.ms(360));
        va.setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f));
        va.addUpdateListener(step);
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator x) {
                cancelled = true; // a new move took over
            }

            @Override
            public void onAnimationEnd(android.animation.Animator x) {
                if (cancelled) return;
                morphAnim = null;
                if (!shrink) {
                    resetMorph(big);
                    return;
                }
                if (end != null) end.run(); // the live map moves into the panel first…
                big.post(() -> resetMorph(big)); // …then the (now empty) big holder is put back
            }
        });
        if (!shrink) step.onAnimationUpdate(va); // start small, on the panel
        morphAnim = va;
        va.start();
    }

    private static void resetMorph(View v) {
        resetTransform(v);
        v.setClipBounds(null);
    }

    private float[] toSmall(View big) {
        int[] a = new int[2], b = new int[2];
        map.view.getLocationOnScreen(a);
        tileArea.getLocationOnScreen(b);
        big.setPivotX(0);
        big.setPivotY(0);
        float sx = big.getWidth() > 0 ? map.view.getWidth() / (float) big.getWidth() : 0.3f;
        float sy = big.getHeight() > 0 ? map.view.getHeight() / (float) big.getHeight() : 0.5f;
        return new float[]{a[0] - b[0], a[1] - b[1], sx, sy};
    }

    private static void resetTransform(View v) {
        v.animate().cancel();
        v.setTranslationX(0);
        v.setTranslationY(0);
        v.setScaleX(1);
        v.setScaleY(1);
    }

    /** "Open the phone" from the hold menu, or a dial request from another app (tel: number). */
    private boolean handleOpen(Intent i) {
        if (i == null) return false;
        if ("phone".equals(i.getStringExtra("open"))) {
            PhoneScreen.show(null);
            return true;
        }
        if (Intent.ACTION_DIAL.equals(i.getAction()) || Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null && "tel".equals(i.getData().getScheme())) {
            PhoneScreen.show(i.getData() != null ? i.getData().getSchemeSpecificPart() : "");
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (InAppKeyboard.hide()) return;
        if (PhoneScreen.hide()) return;
        if (Splash.dismiss()) return;
        if (Panels.open()) {
            Panels.back();
            return;
        }
        if (tiles.editing()) tiles.setEditing(false);
        else if (drawerOpen || peeking) drawer(false);
        // a launcher never finishes on back
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (I == this) I = null;
    }
}
