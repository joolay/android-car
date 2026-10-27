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
    private LinearLayout middle;
    private FrameLayout tickerSlot;
    MapPanel map;
    NowPlaying nowPlaying;
    Tiles tiles;
    Ticker ticker;
    Dock dock;
    Rail rail;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        I = this;
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        w.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        U.init(this);
        Store.init(this);
        Apps.init(this);
        Status.init(this);
        Loc.init(this);
        build();
        immersive();
        askPermissions();
        Actions.connectionsAtStart(this);
        U.ui.postDelayed(Actions.tsWatch, 60000);
        U.ui.postDelayed(() -> Actions.runAutostart(this), 1200);
        Splash.maybeShow(this, "start");
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
        middle.addView(tiles.view, U.weight(1, false));
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
        Sheets.attach(overlay);
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
        lp.screenBrightness = Store.flag(Store.NIGHT, false) ? 0.08f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
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
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALENDAR, Manifest.permission.READ_CONTACTS};
        List<String> need = new ArrayList<>();
        for (String p : want) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (!need.isEmpty() && !Store.flag("asked_perms", false)) {
            Store.put("asked_perms", true);
            requestPermissions(need.toArray(new String[0]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        Loc.stop();
        Loc.start();
        Status.refreshAndNotify();
        tiles.refresh();
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
        Weather.maybeFetch();
        ticker.start();
        tiles.start();
        nowPlaying.start();
        map.start();
        rail.paused(false);
        dock.render();
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

    boolean isResumed2() {
        return resumed;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        // Home pressed while already home: close any panel and leave edit mode
        if (Sheets.open()) Sheets.close();
        if (tiles.editing()) tiles.setEditing(false);
        Splash.dismiss();
    }

    @Override
    public void onBackPressed() {
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
