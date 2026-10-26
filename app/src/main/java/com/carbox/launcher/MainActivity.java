package com.carbox.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Car-style home screen:
 *  - left quarter: live map with a Navigate button
 *  - right three quarters: clock, shortcut folders, favourites dock
 */
public class MainActivity extends Activity {

    // Colours
    private static final int C_BG = 0xFF0B0E13;
    private static final int C_CARD = 0xFF171C24;
    private static final int C_CARD2 = 0xFF222A37;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_SUB = 0xFF9AA4B2;
    private static final int C_ACCENT = 0xFF3D8BFD;

    private static final int DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert;
    private static final int REQ_LOCATION = 41;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final String[] NAV_APPS = {
            "com.google.android.apps.maps", "com.waze", "com.here.app.maps",
            "com.sygic.aura", "net.osmand", "net.osmand.plus", "ru.yandex.yandexnavi",
            "com.google.android.apps.mapslite"
    };
    private static final String[] MUSIC_HINTS = {"music", "spotify", "radio", "podcast", "audio", "deezer", "tidal", "pandora", "shazam", "soundcloud"};
    private static final String[] VIDEO_HINTS = {"youtube", "netflix", "video", "player", "tv", "primevideo", "disney", "showmax", "kodi"};
    private static final String[] SOCIAL_HINTS = {"whatsapp", "telegram", "facebook", "messenger", "instagram", "signal", "twitter", "snapchat", "viber", "skype"};

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final List<AppInfo> allApps = new ArrayList<>();
    private final Map<String, AppInfo> appsByKey = new HashMap<>();
    private final List<Dialog> openDialogs = new ArrayList<>();

    private Store store;
    private float density;
    private GridView folderGrid;
    private FolderAdapter folderAdapter;
    private LinearLayout dockRow;
    private WebView mapView;
    private LocationManager locationManager;
    private boolean locationRunning;
    private boolean receiverRegistered;
    private Location lastLocation;
    private StatusController status;
    private NowPlaying nowPlaying;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        store = new Store(this);
        store.load();
        status = new StatusController(this);
        nowPlaying = new NowPlaying(this, this::openMusicApp);
        setContentView(buildRoot());
        registerPackageReceiver();
        reloadApps();
        if (hasLocationPermission()) {
            startLocation();
        }
        // Location for the map, GPS and weather; phone state to show LTE / 4G / 5G
        List<String> needed = new ArrayList<>();
        for (String perm : new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE}) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) needed.add(perm);
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQ_LOCATION);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasLocationPermission()) startLocation();
        status.start();
        nowPlaying.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopLocation();
        status.stop();
        nowPlaying.stop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home button pressed while we're already on top: close any open panels
        dismissAllDialogs();
        if (folderGrid != null) folderGrid.smoothScrollToPosition(0);
    }

    @Override
    public void onBackPressed() {
        // A home screen has nowhere to go back to
    }

    @Override
    protected void onDestroy() {
        stopLocation();
        status.shutdown();
        nowPlaying.stop();
        if (receiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (mapView != null) mapView.destroy();
        bg.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        if (hasLocationPermission()) {
            startLocation();
            status.start();
        } else if (mapView != null) {
            mapView.evaluateJavascript("noPermission()", null);
        }
    }

    // ---------------------------------------------------------------- layout

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(C_BG);
        int p = dp(12);
        root.setPadding(p, p, p, p);

        // Left quarter: map on top, now playing below
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(buildMapPanel(), new LinearLayout.LayoutParams(MATCH, 0, 1f));
        LinearLayout.LayoutParams npLp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        npLp.topMargin = dp(12);
        left.addView(nowPlaying.getView(), npLp);
        root.addView(left, new LinearLayout.LayoutParams(0, MATCH, 1f));

        // Right three quarters
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, MATCH, 3f);
        rightLp.leftMargin = dp(12);
        root.addView(right, rightLp);

        // Right edge: CarPlay-style status rail (time, date, weather, signals)
        LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(dp(92), MATCH);
        railLp.leftMargin = dp(12);
        root.addView(buildStatusRail(), railLp);


        folderGrid = new GridView(this);
        folderGrid.setNumColumns(4);
        folderGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        folderGrid.setHorizontalSpacing(dp(12));
        folderGrid.setVerticalSpacing(dp(12));
        folderGrid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        folderGrid.setVerticalScrollBarEnabled(false);
        folderGrid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        folderAdapter = new FolderAdapter();
        folderGrid.setAdapter(folderAdapter);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        gridLp.topMargin = 0;
        gridLp.bottomMargin = dp(12);
        right.addView(folderGrid, gridLp);

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.setBackground(round(C_CARD, 22));
        dockRow.setPadding(dp(8), dp(6), dp(8), dp(6));
        right.addView(dockRow, new LinearLayout.LayoutParams(MATCH, dp(84)));

        return root;
    }

    private View buildMapPanel() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(round(C_CARD, 22));
        panel.setClipToOutline(true);

        mapView = new WebView(this);
        WebSettings s = mapView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " CarBoxLauncher/1.0");
        mapView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Re-send the latest fix once the map page is ready
                if (lastLocation != null) pushLocation(lastLocation);
                else if (!hasLocationPermission()) view.evaluateJavascript("noPermission()", null);
            }
        });
        mapView.setBackgroundColor(C_CARD);
        mapView.loadUrl("file:///android_asset/map.html");
        panel.addView(mapView, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView nav = new TextView(this);
        nav.setText("Navigate");
        nav.setTextColor(C_TEXT);
        nav.setTextSize(16);
        nav.setTypeface(Typeface.DEFAULT_BOLD);
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(round(C_ACCENT, 18));
        nav.setOnClickListener(v -> openNavigation());
        nav.setOnLongClickListener(v -> {
            pickApp("Choose navigation app", this::setNavApp);
            return true;
        });
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(MATCH, dp(46), Gravity.BOTTOM);
        navLp.setMargins(dp(10), dp(10), dp(10), dp(10));
        panel.addView(nav, navLp);
        return panel;
    }

    private View buildStatusRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setBackground(round(C_CARD, 22));
        rail.setPadding(dp(6), dp(14), dp(6), dp(14));

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextColor(C_TEXT);
        clock.setTextSize(26);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        rail.addView(clock);

        TextClock day = new TextClock(this);
        day.setFormat24Hour("EEE");
        day.setFormat12Hour("EEE");
        day.setTextColor(C_TEXT);
        day.setTextSize(11);
        day.setTypeface(Typeface.DEFAULT_BOLD);
        rail.addView(day);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("d MMM");
        date.setFormat12Hour("d MMM");
        date.setTextColor(C_SUB);
        date.setTextSize(10);
        rail.addView(date);

        View divider = new View(this);
        divider.setBackgroundColor(C_CARD2);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(40), dp(1));
        dlp.topMargin = dp(14);
        dlp.bottomMargin = dp(14);
        rail.addView(divider, dlp);

        // Weather low/high, mobile signal, Wi-Fi and GPS
        rail.addView(status.getView(), new LinearLayout.LayoutParams(MATCH, WRAP));
        return rail;
    }

    // ---------------------------------------------------------------- apps

    private void reloadApps() {
        bg.execute(() -> {
            List<AppInfo> loaded = loadApps();
            ui.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                allApps.clear();
                allApps.addAll(loaded);
                appsByKey.clear();
                for (AppInfo a : loaded) appsByKey.put(a.key, a);
                if (store.isFirstRun()) seedDefaults();
                folderAdapter.notifyDataSetChanged();
                renderDock();
            });
        });
    }

    private List<AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        List<AppInfo> out = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || getPackageName().equals(ai.packageName)) continue;
            CharSequence label = ri.loadLabel(pm);
            ApplicationInfo app = ai.applicationInfo;
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            out.add(new AppInfo(new ComponentName(ai.packageName, ai.name),
                    label == null ? ai.packageName : label.toString(),
                    ri.loadIcon(pm), app.category, system));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    /** First launch: sort installed apps into starter folders and fill the dock. */
    private void seedDefaults() {
        LinkedHashMap<String, Store.Folder> groups = new LinkedHashMap<>();
        for (String name : new String[]{"Navigation", "Music", "Video", "Social", "Apps", "System"}) {
            groups.put(name, new Store.Folder(name));
        }
        for (AppInfo a : allApps) {
            String p = a.pkg.toLowerCase(Locale.ROOT);
            String group;
            if (a.category == ApplicationInfo.CATEGORY_MAPS || contains(NAV_APPS, a.pkg)) group = "Navigation";
            else if (a.category == ApplicationInfo.CATEGORY_AUDIO || hasHint(p, MUSIC_HINTS)) group = "Music";
            else if (a.category == ApplicationInfo.CATEGORY_VIDEO || hasHint(p, VIDEO_HINTS)) group = "Video";
            else if (a.category == ApplicationInfo.CATEGORY_SOCIAL || hasHint(p, SOCIAL_HINTS)) group = "Social";
            else if (a.system) group = "System";
            else group = "Apps";
            groups.get(group).apps.add(a.key);
        }
        store.folders.clear();
        for (Store.Folder f : groups.values()) {
            if (!f.apps.isEmpty()) store.folders.add(f);
        }

        // Dock: navigation, music, then common car apps
        String[][] dockCandidates = {
                NAV_APPS,
                {"com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music", "deezer.android.app"},
                {"com.whatsapp"},
                {"com.google.android.dialer", "com.android.dialer"},
                {"com.google.android.youtube"},
                {"com.android.settings"}
        };
        int slot = 0;
        for (String[] options : dockCandidates) {
            if (slot >= Store.DOCK_SIZE) break;
            AppInfo found = null;
            for (String pkg : options) {
                found = firstActivityOf(pkg);
                if (found != null) break;
            }
            if (found != null) store.dock.set(slot++, found.key);
        }
        store.save();
    }

    private AppInfo firstActivityOf(String pkg) {
        for (AppInfo a : allApps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private List<AppInfo> resolve(List<String> keys) {
        List<AppInfo> out = new ArrayList<>();
        for (String k : keys) {
            AppInfo a = appsByKey.get(k);
            if (a != null) out.add(a);
        }
        return out;
    }

    private void launch(AppInfo a) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(a.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Couldn't open " + a.label);
        }
    }

    private void openAppInfo(AppInfo a) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("App info isn't available on this device");
        }
    }

    // ---------------------------------------------------------------- navigation

    private void openNavigation() {
        String pkg = store.navPkg;
        if (pkg == null || firstActivityOf(pkg) == null) {
            pkg = null;
            for (String p : NAV_APPS) {
                if (firstActivityOf(p) != null) {
                    pkg = p;
                    break;
                }
            }
        }
        Intent i = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            pickApp("Choose navigation app", this::setNavApp);
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Couldn't open navigation");
        }
    }

    /** Fallback for the Now Playing card when nothing is playing. */
    private void openMusicApp() {
        String[] music = {"com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app",
                "com.apple.android.music", "com.amazon.mp3", "com.google.android.youtube"};
        for (String p : music) {
            AppInfo a = firstActivityOf(p);
            if (a != null) {
                launch(a);
                return;
            }
        }
        for (AppInfo a : allApps) {
            if (a.category == ApplicationInfo.CATEGORY_AUDIO) {
                launch(a);
                return;
            }
        }
        pickApp("Choose a music app", this::launch);
    }

    private void setNavApp(AppInfo a) {
        store.navPkg = a.pkg;
        store.save();
        toast("Navigate now opens " + a.label);
        launch(a);
    }

    // ---------------------------------------------------------------- dock

    private void renderDock() {
        dockRow.removeAllViews();
        for (int i = 0; i < Store.DOCK_SIZE; i++) {
            final int slot = i;
            final AppInfo a = appsByKey.get(store.dock.get(i));
            FrameLayout cell = new FrameLayout(this);
            ImageView iv = new ImageView(this);
            if (a != null) {
                iv.setImageDrawable(a.icon);
                cell.setOnClickListener(v -> launch(a));
                cell.setOnLongClickListener(v -> {
                    dockSlotMenu(slot, a);
                    return true;
                });
                cell.setContentDescription(a.label);
            } else {
                iv.setImageResource(R.drawable.ic_add);
                iv.setColorFilter(C_SUB);
                int pad = dp(16);
                iv.setPadding(pad, pad, pad, pad);
                iv.setBackground(round(C_CARD2, 16));
                cell.setOnClickListener(v -> pickApp("Add to dock", app -> {
                    store.dock.set(slot, app.key);
                    store.save();
                    renderDock();
                }));
                cell.setContentDescription("Empty dock slot");
            }
            cell.addView(iv, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
            dockRow.addView(cell, new LinearLayout.LayoutParams(0, MATCH, 1f));
        }

        View divider = new View(this);
        divider.setBackgroundColor(C_CARD2);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(2), dp(44));
        dlp.setMargins(dp(6), 0, dp(6), 0);
        dockRow.addView(divider, dlp);

        FrameLayout allCell = new FrameLayout(this);
        ImageView allIcon = new ImageView(this);
        allIcon.setImageResource(R.drawable.ic_apps);
        int pad = dp(15);
        allIcon.setPadding(pad, pad, pad, pad);
        allIcon.setBackground(round(C_ACCENT, 16));
        allCell.addView(allIcon, new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
        allCell.setOnClickListener(v -> showAllApps());
        allCell.setContentDescription("All apps");
        dockRow.addView(allCell, new LinearLayout.LayoutParams(dp(76), MATCH));
    }

    private void dockSlotMenu(int slot, AppInfo current) {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(current.label)
                .setItems(new String[]{"Replace", "Remove from dock"}, (d, which) -> {
                    if (which == 0) {
                        pickApp("Replace with", app -> {
                            store.dock.set(slot, app.key);
                            store.save();
                            renderDock();
                        });
                    } else {
                        store.dock.set(slot, "");
                        store.save();
                        renderDock();
                    }
                })
                .show();
    }

    private void addToDock(AppInfo a) {
        if (store.dock.contains(a.key)) {
            toast(a.label + " is already in the dock");
            return;
        }
        int free = store.dock.indexOf("");
        if (free < 0) {
            toast("Dock is full – long-press a dock icon to replace it");
            return;
        }
        store.dock.set(free, a.key);
        store.save();
        renderDock();
    }

    // ---------------------------------------------------------------- folders

    private void openFolder(int index) {
        if (index < 0 || index >= store.folders.size()) return;
        final Store.Folder f = store.folders.get(index);
        final Dialog d = panelDialog(0.78f, 0.86f);

        LinearLayout box = panelBox();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(f.name);
        title.setTextColor(C_TEXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        header.addView(pill("Edit apps", () -> {
            d.dismiss();
            editFolderApps(index);
        }));
        header.addView(spaceH(8));
        header.addView(pill("Rename", () -> {
            d.dismiss();
            renameFolder(index);
        }));
        header.addView(spaceH(8));
        header.addView(pill("Close", d::dismiss));
        box.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        List<AppInfo> apps = resolve(f.apps);
        if (apps.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("This folder is empty.\nTap “Edit apps” to add some.");
            empty.setTextColor(C_SUB);
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            box.addView(empty, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        } else {
            box.addView(appGrid(apps, d), gridParams());
        }
        d.setContentView(box);
        showDialog(d, 0.78f, 0.86f);
    }

    private void folderMenu(int index) {
        Store.Folder f = store.folders.get(index);
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(f.name)
                .setItems(new String[]{"Edit apps", "Rename", "Move to front", "Delete folder"}, (d, which) -> {
                    if (which == 0) editFolderApps(index);
                    else if (which == 1) renameFolder(index);
                    else if (which == 2) {
                        store.folders.add(0, store.folders.remove(index));
                        store.save();
                        folderAdapter.notifyDataSetChanged();
                    } else confirmDeleteFolder(index);
                })
                .show();
    }

    private void newFolder() {
        askText("New folder", "", name -> {
            store.folders.add(new Store.Folder(name));
            store.save();
            folderAdapter.notifyDataSetChanged();
            editFolderApps(store.folders.size() - 1);
        });
    }

    private void renameFolder(int index) {
        Store.Folder f = store.folders.get(index);
        askText("Rename folder", f.name, name -> {
            f.name = name;
            store.save();
            folderAdapter.notifyDataSetChanged();
        });
    }

    private void confirmDeleteFolder(int index) {
        Store.Folder f = store.folders.get(index);
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Delete “" + f.name + "”?")
                .setMessage("The apps stay installed – only the shortcut folder is removed.")
                .setPositiveButton("Delete", (d, w) -> {
                    store.folders.remove(index);
                    store.save();
                    folderAdapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void editFolderApps(int index) {
        if (allApps.isEmpty()) {
            toast("Still loading apps…");
            return;
        }
        final Store.Folder f = store.folders.get(index);
        final String[] labels = new String[allApps.size()];
        final boolean[] checked = new boolean[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) {
            labels[i] = allApps.get(i).label;
            checked[i] = f.apps.contains(allApps.get(i).key);
        }
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Apps in “" + f.name + "”")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (d, w) -> {
                    // Keep the existing order, drop unticked apps, append newly ticked ones
                    ArrayList<String> next = new ArrayList<>();
                    for (String k : f.apps) {
                        int pos = indexOfKey(k);
                        if (pos < 0 || checked[pos]) next.add(k);
                    }
                    for (int i = 0; i < allApps.size(); i++) {
                        String k = allApps.get(i).key;
                        if (checked[i] && !next.contains(k)) next.add(k);
                    }
                    f.apps.clear();
                    f.apps.addAll(next);
                    store.save();
                    folderAdapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int indexOfKey(String key) {
        for (int i = 0; i < allApps.size(); i++) if (allApps.get(i).key.equals(key)) return i;
        return -1;
    }

    private void chooseFolderFor(AppInfo a) {
        String[] names = new String[store.folders.size() + 1];
        for (int i = 0; i < store.folders.size(); i++) names[i] = store.folders.get(i).name;
        names[names.length - 1] = "+ New folder…";
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("Add " + a.label + " to")
                .setItems(names, (d, which) -> {
                    if (which == store.folders.size()) {
                        askText("New folder", "", name -> {
                            Store.Folder f = new Store.Folder(name);
                            f.apps.add(a.key);
                            store.folders.add(f);
                            store.save();
                            folderAdapter.notifyDataSetChanged();
                        });
                    } else {
                        Store.Folder f = store.folders.get(which);
                        if (!f.apps.contains(a.key)) f.apps.add(a.key);
                        store.save();
                        folderAdapter.notifyDataSetChanged();
                        toast("Added to " + f.name);
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------- all apps

    private void showAllApps() {
        final Dialog d = panelDialog(0.92f, 0.92f);
        LinearLayout box = panelBox();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("All apps");
        title.setTextColor(C_TEXT);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView hint = new TextView(this);
        hint.setText("Long-press an app to add it to the dock or a folder");
        hint.setTextColor(C_SUB);
        hint.setTextSize(13);
        header.addView(hint);
        header.addView(spaceH(12));
        header.addView(pill("Close", d::dismiss));
        box.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));
        box.addView(appGrid(allApps, d), gridParams());
        d.setContentView(box);
        showDialog(d, 0.92f, 0.92f);
    }

    private void appMenu(AppInfo a) {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(a.label)
                .setItems(new String[]{"Add to dock", "Add to folder…", "Set as Navigate app", "App info"}, (d, which) -> {
                    if (which == 0) addToDock(a);
                    else if (which == 1) chooseFolderFor(a);
                    else if (which == 2) {
                        store.navPkg = a.pkg;
                        store.save();
                        toast("Navigate now opens " + a.label);
                    } else openAppInfo(a);
                })
                .show();
    }

    // ---------------------------------------------------------------- pickers & dialogs

    interface AppCallback {
        void on(AppInfo app);
    }

    interface TextCallback {
        void on(String text);
    }

    private void pickApp(String title, AppCallback cb) {
        if (allApps.isEmpty()) {
            toast("Still loading apps…");
            return;
        }
        final List<AppInfo> list = new ArrayList<>(allApps);
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(title)
                .setAdapter(new AppRowAdapter(list), (d, which) -> cb.on(list.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void askText(String title, String initial, TextCallback cb) {
        final EditText input = new EditText(this);
        input.setText(initial);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setTextColor(C_TEXT);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(22), dp(8), dp(22), 0);
        wrap.addView(input, new FrameLayout.LayoutParams(MATCH, WRAP));
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("OK", (d, w) -> {
                    String s = input.getText().toString().trim();
                    if (!s.isEmpty()) cb.on(s);
                })
                .setNegativeButton("Cancel", null)
                .show();
        input.requestFocus();
    }

    private Dialog panelDialog(float wFrac, float hFrac) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private void showDialog(Dialog d, float wFrac, float hFrac) {
        openDialogs.add(d);
        d.setOnDismissListener(x -> openDialogs.remove(d));
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics dm = getResources().getDisplayMetrics();
            w.setLayout((int) (dm.widthPixels * wFrac), (int) (dm.heightPixels * hFrac));
        }
    }

    private void dismissAllDialogs() {
        for (Dialog d : new ArrayList<>(openDialogs)) {
            try {
                d.dismiss();
            } catch (Exception ignored) {
            }
        }
        openDialogs.clear();
    }

    private LinearLayout panelBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(C_CARD, 24));
        box.setPadding(dp(20), dp(16), dp(20), dp(16));
        return box;
    }

    private GridView appGrid(List<AppInfo> apps, Dialog owner) {
        GridView g = new GridView(this);
        g.setNumColumns(GridView.AUTO_FIT);
        g.setColumnWidth(dp(104));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        g.setVerticalSpacing(dp(10));
        g.setSelector(new ColorDrawable(Color.TRANSPARENT));
        g.setAdapter(new AppGridAdapter(apps, owner));
        return g;
    }

    private LinearLayout.LayoutParams gridParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        lp.topMargin = dp(14);
        return lp;
    }

    // ---------------------------------------------------------------- adapters

    /** Folder tiles on the home screen, plus a "New folder" tile at the end. */
    private final class FolderAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return store.folders.size() + 1;
        }

        @Override
        public Object getItem(int position) {
            return position < store.folders.size() ? store.folders.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackground(round(C_CARD, 20));
            card.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.setLayoutParams(new AbsListView.LayoutParams(MATCH, dp(136)));

            if (position == store.folders.size()) {
                ImageView plus = new ImageView(MainActivity.this);
                plus.setImageResource(R.drawable.ic_add);
                plus.setColorFilter(C_SUB);
                card.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(40)));
                TextView t = label("New folder", 15, C_SUB, false);
                card.addView(t);
                card.setOnClickListener(v -> newFolder());
                return card;
            }

            final int index = position;
            Store.Folder f = store.folders.get(position);
            List<AppInfo> apps = resolve(f.apps);

            GridLayout preview = new GridLayout(MainActivity.this);
            preview.setColumnCount(2);
            int shown = Math.min(4, apps.size());
            for (int i = 0; i < shown; i++) {
                ImageView iv = new ImageView(MainActivity.this);
                iv.setImageDrawable(apps.get(i).icon);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = dp(30);
                lp.height = dp(30);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                preview.addView(iv, lp);
            }
            if (shown == 0) {
                ImageView iv = new ImageView(MainActivity.this);
                iv.setImageResource(R.drawable.ic_folder);
                iv.setColorFilter(C_CARD2);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = dp(48);
                lp.height = dp(48);
                preview.addView(iv, lp);
            }
            card.addView(preview, new LinearLayout.LayoutParams(WRAP, WRAP));

            TextView name = label(f.name, 15, C_TEXT, true);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(WRAP, WRAP);
            nlp.topMargin = dp(6);
            card.addView(name, nlp);
            card.addView(label(apps.size() == 1 ? "1 app" : apps.size() + " apps", 12, C_SUB, false));

            card.setOnClickListener(v -> openFolder(index));
            card.setOnLongClickListener(v -> {
                folderMenu(index);
                return true;
            });
            return card;
        }
    }

    /** Icon + label grid used inside folders and the all-apps panel. */
    private final class AppGridAdapter extends BaseAdapter {
        private final List<AppInfo> apps;
        private final Dialog owner;

        AppGridAdapter(List<AppInfo> apps, Dialog owner) {
            this.apps = apps;
            this.owner = owner;
        }

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final AppInfo a = apps.get(position);
            LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(4), dp(8), dp(4), dp(8));
            cell.setLayoutParams(new AbsListView.LayoutParams(MATCH, WRAP));
            ImageView iv = new ImageView(MainActivity.this);
            iv.setImageDrawable(a.icon);
            cell.addView(iv, new LinearLayout.LayoutParams(dp(60), dp(60)));
            TextView t = label(a.label, 13, C_TEXT, false);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(MATCH, WRAP);
            tlp.topMargin = dp(6);
            cell.addView(t, tlp);
            cell.setOnClickListener(v -> {
                owner.dismiss();
                launch(a);
            });
            cell.setOnLongClickListener(v -> {
                appMenu(a);
                return true;
            });
            return cell;
        }
    }

    /** Rows with icon + name, used by the app picker. */
    private final class AppRowAdapter extends BaseAdapter {
        private final List<AppInfo> apps;

        AppRowAdapter(List<AppInfo> apps) {
            this.apps = apps;
        }

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppInfo a = apps.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(22), dp(8), dp(22), dp(8));
            ImageView iv = new ImageView(MainActivity.this);
            iv.setImageDrawable(a.icon);
            row.addView(iv, new LinearLayout.LayoutParams(dp(38), dp(38)));
            TextView t = label(a.label, 16, C_TEXT, false);
            t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            tlp.leftMargin = dp(16);
            row.addView(t, tlp);
            return row;
        }
    }

    // ---------------------------------------------------------------- location

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            pushLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    private void startLocation() {
        if (locationRunning) return;
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        Location best = null;
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (!locationManager.getAllProviders().contains(provider)) continue;
                locationManager.requestLocationUpdates(provider, 2000L, 3f, locationListener, Looper.getMainLooper());
                locationRunning = true;
                Location last = locationManager.getLastKnownLocation(provider);
                if (last != null && (best == null || last.getTime() > best.getTime())) best = last;
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        if (best != null) pushLocation(best);
    }

    private void stopLocation() {
        if (locationManager != null && locationRunning) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {
            }
        }
        locationRunning = false;
    }

    private void pushLocation(Location l) {
        if (l == null) return;
        lastLocation = l;
        if (status != null) status.onLocation(l);
        if (mapView == null) return;
        mapView.evaluateJavascript("setPos(" + l.getLatitude() + "," + l.getLongitude() + ")", null);
    }

    // ---------------------------------------------------------------- package changes

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    private void registerPackageReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
        receiverRegistered = true;
    }

    // ---------------------------------------------------------------- small helpers

    private int dp(int v) {
        return Math.round(v * density);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView pill(String text, Runnable action) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(C_TEXT);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(C_CARD2, 18));
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private View spaceH(int dpWidth) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dpWidth), 1));
        return v;
    }

    private static boolean contains(String[] arr, String s) {
        for (String x : arr) if (x.equals(s)) return true;
        return false;
    }

    private static boolean hasHint(String pkg, String[] hints) {
        for (String h : hints) if (pkg.contains(h)) return true;
        return false;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
