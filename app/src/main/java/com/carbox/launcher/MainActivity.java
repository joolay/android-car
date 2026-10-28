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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The home screen. Everything is built in code and sized for an 800×480 design, scaled to the real screen. */
public class MainActivity extends Activity {
    static MainActivity I;
    static final int REQ_WALLPAPER = 41;

    private FrameLayout root, overlay;
    FrameLayout tileArea, sheetHost;
    private LinearLayout middle;
    private FrameLayout tickerSlot;
    MapPanel map;
    NowPlaying nowPlaying;
    Tiles tiles;
    Ticker ticker;
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
        U.init(this);
        Store.init(this);
        About.catchCrashes(this);
        Apps.init(this);
        Status.init(this);
        Loc.init(this);
        Rest.init(this);
        Salah.start(this);
        HfpCalls.start(this);
        NavCard.start();
        SpeedLimit.start();
        Logbook.start();
        SpeedCard.start();
        CarHA.start();
        HomeApproach.start();
        U.ui.postDelayed(HomeApproach::fromGoTo, 8000);
        build();
        immersive();
        askPermissions();
        Actions.connectionsAtStart(this);
        U.ui.postDelayed(Actions.tsWatch, 60000);
        // music starts first, even before the splash
        Music.atStart(this);
        // splash next; hotspot settings and auto-start apps only once it has closed
        if (!Splash.maybeShow(this, "start", () -> Actions.afterSplash(this))) Actions.afterSplash(this);
        U.ui.post(() -> handleOpen(getIntent()));
        U.ui.postDelayed(this::afterUpdate, 2500);
        U.ui.postDelayed(About::offerReport, 4000); // crashed last time? offer the report
        // traffic for the drive, once per car start (after the splash and auto-start apps settle)
        long bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        if (Math.abs(Store.lng("traffic_boot", 0) - bootAt) > 60000) {
            Store.put("traffic_boot", bootAt);
            U.ui.postDelayed(() -> ticker.trafficAtStart(0), 12000);
        }
        // the launcher can be restarted when it becomes the Home app: carry on with the checklist
        U.ui.postDelayed(() -> {
            if (SettingsPanels.backFromChecklist()) SettingsPanels.reopenChecklist();
        }, 400);
    }

    private void build() {
        root = new FrameLayout(this);
        applyWallpaper();

        LinearLayout main = U.row(this);
        main.setGravity(0);
        U.pad(main, 8);
        root.addView(main, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        // left column: map over now playing
        LinearLayout left = U.col(this);
        map = new MapPanel(this);
        left.addView(map.view, U.weight(1, false));
        nowPlaying = new NowPlaying(this);
        LinearLayout.LayoutParams np = U.weight(1, false);
        np.topMargin = U.u(8);
        left.addView(nowPlaying.view, np);
        main.addView(left, U.lpu(200, U.MATCH));

        // middle: tiles, ticker, dock
        middle = U.col(this);
        tiles = new Tiles(this);
        // the tile area also hosts panels (All apps, settings, phone...) so they stay inside the 2×4 grid
        tileArea = new FrameLayout(this);
        tileArea.addView(tiles.view, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        sheetHost = new FrameLayout(this);
        tileArea.addView(sheetHost, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        middle.addView(tileArea, U.weight(1, false));
        tickerSlot = new FrameLayout(this);
        ticker = new Ticker(this);
        tickerSlot.addView(ticker.view, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        LinearLayout.LayoutParams tl = U.lpu(U.MATCH, 30);
        tl.topMargin = U.u(8);
        middle.addView(tickerSlot, tl);
        dock = new Dock(this);
        LinearLayout.LayoutParams dl = U.lpu(U.MATCH, 56);
        dl.topMargin = U.u(8);
        middle.addView(dock.view, dl);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(0, U.MATCH, 1);
        ml.leftMargin = U.u(8);
        ml.rightMargin = U.u(8);
        main.addView(middle, ml);

        // right rail
        rail = new Rail(this);
        main.addView(rail.view, U.lpu(72, U.MATCH));

        overlay = new FrameLayout(this);
        root.addView(overlay, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        Sheets.attach(sheetHost, overlay);
        setContentView(root);
        dock.render();
    }

    /** Swap the ticker for the edit bar while tiles are being rearranged. */
    void editMode(boolean on) {
        tickerSlot.removeAllViews();
        if (on) tickerSlot.addView(Ticker.editBar(this, () -> tiles.setEditing(false)), new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        else tickerSlot.addView(ticker.view, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
    }

    // ------------------------------------------------------------------ wallpaper

    void applyWallpaper() {
        String mode = Store.str(Store.WALL, "black");
        if ("color".equals(mode)) {
            root.setBackground(new ColorDrawable(Store.num(Store.WALL_COLOR, U.BG)));
        } else if ("image".equals(mode)) {
            String uri = Store.str("wall_uri", null);
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
        } else root.setBackground(new ColorDrawable(0xFF000000));
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
        if (req == Keyboard.REQ_VOICE && res == RESULT_OK) Keyboard.voiceResult(data);
        if (req == 62) Sheets.refresh(); // back from "make Car Launcher the Phone app"
        if (req == REQ_AUDIO && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                getContentResolver().takePersistableUriPermission(data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            Store.put("splash_audio_uri", data.getData().toString());
            Store.put("splash_audio", "own");
            Sheets.refresh();
        }
        if (req == REQ_WALLPAPER && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            Store.put("wall_uri", u.toString());
            Store.put(Store.WALL, "image");
            applyWallpaper();
            Sheets.refresh();
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
        if (!need.isEmpty() && !Store.flag("asked_perms2", false)) {
            Store.put("asked_perms2", true); // asked again once, for the new Bluetooth permission
            requestPermissions(need.toArray(new String[0]), 1); // the set-up checklist follows when this closes
        } else U.ui.postDelayed(this::firstRunChecklist, 1500);
    }

    /** First run: the checklist for permissions Android only grants in its own settings screens. */
    /** After installing a new version: offer to run the set-up checklist again (new steps may have been added). */
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
        long last = Store.lng("last_version", -1);
        Store.put("last_version", code);
        // no record yet: an older version that didn't keep one counts as an update (it had shown the checklist)
        boolean updated = last < 0 ? Store.flag("setup_shown", false) : last != code;
        if (!updated || isFinishing()) return; // first install (the checklist runs anyway) or no change
        if (Sheets.open() || CallState.state != CallState.NONE) return; // don't interrupt
        Sheets.show(() -> {
            android.content.Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            b.addView(U.text(c, "Car Launcher was updated to " + ver + ". Run the set-up checklist again? New steps may have been added, and anything already done stays ticked.", 14, U.TEXT2, false));
            LinearLayout btns = U.row(c);
            btns.addView(U.button(c, "Run set-up", true, () -> {
                Sheets.close();
                SettingsPanels.setupChecklist();
            }));
            btns.addView(U.gap(c, 10, 1));
            btns.addView(U.button(c, "Not now", false, Sheets::close));
            b.addView(btns, Widgets.top(16));
            return Sheets.panel("Updated", b, 460, 260);
        });
    }

    private void firstRunChecklist() {
        if (Store.flag("setup_shown", false) || isFinishing()) return;
        Store.put("setup_shown", true);
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
            Sheets.refresh();
            return;
        }
        if (req == Keyboard.REQ_MIC) {
            if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) Keyboard.micGranted();
            return;
        }
        Loc.stop();
        Loc.start();
        Status.refreshAndNotify();
        tiles.refresh();
        if (req == 1) U.ui.postDelayed(this::firstRunChecklist, 600);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        immersive();
        applyNight();
        Loc.start();
        Status.start();
        if (CallState.state != CallState.NONE) CallCard.show(); // back home during a call
        NavCard.update();
        Weather.maybeFetch();
        ticker.start();
        tiles.start();
        nowPlaying.start();
        map.start();
        rail.paused(false);
        dock.render();
        // back from Android's keyboard settings: show the updated steps
        if (Sheets.open() && Sheets.top() != null && (Sheets.top() == SettingsPanels.keyboardPage || Sheets.top() == SettingsPanels.setupPage || Sheets.top() == SettingsPanels.floatPage || Sheets.top() == Optimiser.page)) {
            Sheets.refresh();
        }
        if (pendingAutostart) {
            // back from hotspot settings: now open the auto-start apps
            pendingAutostart = false;
            U.ui.postDelayed(() -> Actions.runAutostart(this), 1200);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
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
        if (Sheets.minimisable() || (AppPanels.galleryRef != null && Sheets.top() == AppPanels.galleryRef)) Sheets.close();
        PhoneScreen.hide();
        SalahCard.hide();
        SpeedCard.hide();
        Alerts.hideNotice();
    };

    void resetIdle() {
        U.ui.removeCallbacks(idle);
        U.ui.postDelayed(idle, Store.num("card_secs", 15) > 0 ? Math.max(15, Store.num("card_secs", 15)) * 1000L : 15000);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN || ev.getActionMasked() == android.view.MotionEvent.ACTION_UP) resetIdle();
        return super.dispatchTouchEvent(ev);
    }

    boolean isResumed2() {
        return resumed;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Home pressed while already home: close any panel and leave edit mode
        Keyboard.hide();
        if (handleOpen(i)) return;
        // Android sends Home after you pick Car Launcher as the Home app: go back to the checklist
        if (SettingsPanels.backFromChecklist()) {
            U.ui.post(SettingsPanels::reopenChecklist);
            return;
        }
        PhoneScreen.hide();
        if (Sheets.open()) Sheets.close();
        if (tiles.editing()) tiles.setEditing(false);
        Splash.cancel();
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
        if (Keyboard.hide()) return;
        if (PhoneScreen.hide()) return;
        if (Splash.dismiss()) return;
        if (Sheets.open()) {
            Sheets.back();
            return;
        }
        if (tiles.editing()) tiles.setEditing(false);
        // a launcher never finishes on back
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (I == this) I = null;
    }
}
