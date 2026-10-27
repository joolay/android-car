package com.carbox.launcher;

import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Renders the real launcher views to PNG on an 800×480 screen (drawn at 2×), so the screenshots
 * show exactly what the code lays out. Not a correctness test: run by the Screenshots workflow.
 * App icons are stand-ins (coloured letters) and the map is blank, because there is no real phone
 * or internet here.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w800dp-h480dp-land-xhdpi")
public class ScreensTest {
    private static final String[][] APPS = {
            {"com.spotify.music", "Spotify", "#1DB954"},
            {"com.google.android.apps.maps", "Maps", "#4285F4"},
            {"com.waze", "Waze", "#33CCFF"},
            {"com.whatsapp", "WhatsApp", "#25D366"},
            {"com.google.android.youtube", "YouTube", "#FF0000"},
            {"com.google.android.apps.youtube.music", "YT Music", "#E52D27"},
            {"com.android.chrome", "Chrome", "#F4B400"},
            {"com.google.android.gm", "Gmail", "#EA4335"},
            {"com.google.android.calendar", "Calendar", "#1A73E8"},
            {"com.android.bluetooth.phone", "BT Phone", "#5E5CE6"},
            {"com.google.android.apps.photos", "Photos", "#FBBC04"},
            {"com.google.android.dialer", "Phone", "#34A853"},
            {"com.android.camera", "Camera", "#8E8E93"},
            {"com.tailscale.ipn", "Tailscale", "#3D3D3D"},
            {"com.netflix.mediaclient", "Netflix", "#B20710"},
            {"com.google.android.apps.messaging", "Messages", "#1A73E8"},
            {"io.homeassistant.companion.android", "Home Assistant", "#18BCF2"},
            {"com.android.settings", "Android settings", "#607D8B"},
            {"com.android.vending", "Play Store", "#01875F"},
            {"com.google.android.googlequicksearchbox", "Google", "#4285F4"},
            {"com.audible.application", "Audible", "#F7991C"}};

    private final File out = new File(System.getProperty("screens.dir", "build/screens"));
    private ActivityController<MainActivity> ctl;
    private MainActivity a;

    @Test
    public void render() throws Exception {
        out.mkdirs();
        Context c = RuntimeEnvironment.getApplication();
        installApps(c);
        Store.init(c);
        Store.put(Store.H24, true);
        Store.put("splash_on", false);

        ctl = Robolectric.buildActivity(MainActivity.class).setup();
        a = ctl.get();
        waitForApps();
        fakeData();

        // 1. home: default tiles with the Driving timer in place of Speed
        Tiles.setSpec(4, Tiles.widget("rest"));
        Rest.driveMs = 84 * 60000L;
        Rest.meters = 142000;
        shot("1-home");

        // 2. the same widget when a break is due
        Rest.driveMs = 123 * 60000L;
        Rest.meters = 211000;
        shot("2-home-break-due");
        Rest.driveMs = 84 * 60000L;
        Rest.meters = 142000;

        // 3. twelve tiles: first screen (with the position bar between the rows), then swiped to the end
        Tiles.setCount(12);
        JSONArraySet(8, "trip");
        JSONArraySet(9, "weather");
        JSONArraySet(10, "heading");
        JSONArraySet(11, "prayertimes");
        shot("3-tiles-12-first-screen");
        a.tiles.view.scrollTo(100000, 0);
        a.tiles.view.clampScroll();
        shot("4-tiles-12-swiped-right");
        a.tiles.view.scrollTo(0, 0);
        Tiles.setCount(8);

        // 5. All apps with Settings first
        AppPanels.allApps(false);
        shot("5-all-apps");
        Sheets.close();

        // 6. Driving timer settings
        Rest.setup();
        shot("6-driving-timer-settings");
        Sheets.close();

        // 7. Home tiles setting
        SettingsPanels.tileCount();
        shot("7-home-tiles-setting");
        Sheets.close();

        // 8. Settings main page
        SettingsPanels.main();
        shot("8-settings");
        Sheets.close();
    }

    private static void JSONArraySet(int i, String id) {
        Tiles.setSpec(i, Tiles.widget(id));
    }

    // ------------------------------------------------------------------ set-up

    private void installApps(Context c) {
        ShadowPackageManager spm = shadowOf(c.getPackageManager());
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (String[] app : APPS) {
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = app[0];
            ai.name = app[1];
            ai.nonLocalizedLabel = app[1];
            ActivityInfo act = new ActivityInfo();
            act.packageName = app[0];
            act.name = app[0] + ".Main";
            act.nonLocalizedLabel = app[1];
            act.applicationInfo = ai;
            act.exported = true;
            ResolveInfo ri = new ResolveInfo();
            ri.activityInfo = act;
            ri.nonLocalizedLabel = app[1];
            spm.addResolveInfoForIntent(launcher, ri);
            android.content.pm.PackageInfo pi = new android.content.pm.PackageInfo();
            pi.packageName = app[0];
            pi.applicationInfo = ai;
            spm.installPackage(pi);
            spm.addActivityIfNotPresent(new ComponentName(app[0], act.name));
        }
    }

    private void waitForApps() throws Exception {
        for (int i = 0; i < 50 && Apps.all.size() < APPS.length; i++) {
            Thread.sleep(100);
            shadowOf(Looper.getMainLooper()).idle();
        }
        // stand-in icons: a coloured circle with the first letter
        for (Apps.App app : Apps.all) {
            String color = "#3D8BFD";
            for (String[] s : APPS) if (s[0].equals(app.pkg)) color = s[2];
            java.lang.reflect.Field f = Apps.App.class.getDeclaredField("icon");
            f.setAccessible(true);
            f.set(app, letterIcon(app.label, Color.parseColor(color)));
        }
        // pin two, so the dock shows pinned apps then recent ones
        Apps.setPinned(Apps.byPkg("com.spotify.music").key, true);
        Apps.setPinned(Apps.byPkg("com.google.android.apps.maps").key, true);
        // a re-render now that icons and pins are in place
        Tiles.save(new org.json.JSONArray());
        a.tiles.refresh();
        a.dock.render();
        settle();
    }

    private Drawable letterIcon(String label, int color) {
        int s = 96;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        cv.drawCircle(s / 2f, s / 2f, s / 2f, p);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(44);
        cv.drawText(label.substring(0, 1), s / 2f, s / 2f + 16, p);
        return new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), b);
    }

    /** Weather and now-playing values so the rail and cards aren't empty. */
    private void fakeData() {
        Weather.ok = true;
        Weather.code = 2;
        Weather.isDay = true;
        Weather.temp = 24;
        Weather.Day d = new Weather.Day();
        d.date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        d.code = 2;
        d.hi = 27;
        d.lo = 16;
        d.sunrise = "05:12";
        d.sunset = "18:21";
        Weather.days.clear();
        Weather.days.add(d);
        a.rail.updateWeather();
        a.rail.build();
        settle();
    }

    // ------------------------------------------------------------------ capture

    private void settle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void shot(String name) throws Exception {
        settle();
        View root = a.getWindow().getDecorView().getRootView();
        int w = 1600, h = 960;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        settle();
        // let widgets show their current values
        a.tiles.start();
        settle();
        a.tiles.stop();
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(U.BG);
        root.draw(cv);
        try (FileOutputStream fo = new FileOutputStream(new File(out, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, fo);
        }
    }
}
