package com.carbox.launcher;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Installed launchable apps, launching, recents, pins and hidden apps. */
final class Apps {

    static final class App {
        final ComponentName cn;
        final String key, pkg, label;
        final Drawable icon;
        final int category;
        final boolean system;

        App(ComponentName cn, String label, Drawable icon, int category, boolean system) {
            this.cn = cn;
            this.key = cn.flattenToString();
            this.pkg = cn.getPackageName();
            this.label = label;
            this.icon = icon;
            this.category = category;
            this.system = system;
        }
    }

    static final List<App> all = new ArrayList<>();
    static final Map<String, App> byKey = new HashMap<>();
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final ExecutorService bg = Executors.newSingleThreadExecutor();
    private static Context ctx;

    private Apps() {
    }

    static void init(Context c) {
        boolean first = ctx == null;
        ctx = c.getApplicationContext();
        if (!first) return;
        reload();
        // keep the list fresh when apps are installed, updated or removed
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        ctx.registerReceiver(new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                reload();
            }
        }, f);
    }

    static void listen(Runnable r) {
        listeners.add(r);
    }

    static void reload() {
        bg.execute(() -> {
            List<App> loaded = load();
            Ui.ui.post(() -> {
                all.clear();
                all.addAll(loaded);
                byKey.clear();
                for (App a : loaded) byKey.put(a.key, a);
                for (Runnable r : new ArrayList<>(listeners)) r.run();
            });
        });
    }

    private static List<App> load() {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || ctx.getPackageName().equals(ai.packageName)) continue;
            CharSequence label = ri.loadLabel(pm);
            ApplicationInfo app = ai.applicationInfo;
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            out.add(new App(new ComponentName(ai.packageName, ai.name), label == null ? ai.packageName : label.toString(),
                    ri.loadIcon(pm), app.category, system));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    static App get(String key) {
        return key == null ? null : byKey.get(key);
    }

    static App byPkg(String pkg) {
        if (pkg == null) return null;
        for (App a : all) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    static boolean installed(String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static String label(String pkg) {
        App a = byPkg(pkg);
        if (a != null) return a.label;
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    static Drawable iconOf(String pkg) {
        App a = byPkg(pkg);
        if (a != null) return a.icon;
        try {
            return ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ launching

    static boolean launch(Context c, App a) {
        Trace.event("Open app: " + a.label + " (" + a.pkg + ")");
        Music.appOpened(a.pkg);
        if (a == null) return false;
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            noteLaunch(a.key);
            return true;
        } catch (Exception e) {
            Toast.makeText(c, "Couldn't open " + a.label, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    static boolean launchPkg(Context c, String pkg) {
        if (pkg == null) return false;
        App a = byPkg(pkg);
        if (a != null) return launch(c, a);
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void appInfo(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Car boxes often have their own cut-down settings app that answers this first and has no ⋮ menu
        // (where Android 13 keeps "Allow restricted settings"). Ask Android's own Settings app first.
        try {
            c.startActivity(new Intent(i).setPackage("com.android.settings"));
            return;
        } catch (Exception ignored) {
        }
        try {
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    static void noteLaunch(String key) {
        List<String> r = Prefs.list(Prefs.RECENTS);
        r.remove(key);
        r.add(0, key);
        while (r.size() > 30) r.remove(r.size() - 1);
        Prefs.putList(Prefs.RECENTS, r);
    }

    // ------------------------------------------------------------------ pins, hidden, recents

    static List<String> pins() {
        return Prefs.list(Prefs.PINS);
    }

    static boolean pinned(String key) {
        return pins().contains(key);
    }

    static void setPinned(String key, boolean on) {
        List<String> p = pins();
        p.remove(key);
        if (on) p.add(key);
        Prefs.putList(Prefs.PINS, p);
    }

    static Set<String> hidden() {
        return new HashSet<>(Prefs.list(Prefs.HIDDEN));
    }

    /** Apps kept out of the dock (pinned or recent) but still in All apps. */
    static Set<String> dockHidden() {
        return new HashSet<>(Prefs.list("dock_hidden"));
    }

    static void setDockHidden(String key, boolean on) {
        List<String> h = Prefs.list("dock_hidden");
        h.remove(key);
        if (on) h.add(key);
        Prefs.putList("dock_hidden", h);
    }

    static void setHidden(String key, boolean on) {
        List<String> h = Prefs.list(Prefs.HIDDEN);
        h.remove(key);
        if (on) h.add(key);
        Prefs.putList(Prefs.HIDDEN, h);
    }

    static List<App> visible() {
        Set<String> h = hidden();
        List<App> out = new ArrayList<>();
        for (App a : all) if (!h.contains(a.key)) out.add(a);
        return out;
    }

    static boolean hasUsageAccess() {
        try {
            AppOpsManager ops = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    /** Recently used apps, newest first: usage stats when allowed, otherwise our own launch history. */
    static List<App> recents(int max) {
        Set<String> h = hidden();
        h.addAll(dockHidden()); // the dock also leaves out apps hidden from it
        List<String> pins = pins();
        List<App> out = new ArrayList<>();
        if (hasUsageAccess()) {
            try {
                UsageStatsManager um = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                List<UsageStats> stats = um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7L * 86400000L, now);
                Collections.sort(stats, (a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
                for (UsageStats s : stats) {
                    if (s.getLastTimeUsed() <= 0) continue;
                    App a = byPkg(s.getPackageName());
                    if (a == null || h.contains(a.key) || pins.contains(a.key) || out.contains(a)) continue;
                    out.add(a);
                    if (out.size() >= max) return out;
                }
            } catch (Exception ignored) {
            }
        }
        for (String k : Prefs.list(Prefs.RECENTS)) {
            App a = byKey.get(k);
            if (a == null || h.contains(k) || pins.contains(k) || out.contains(a)) continue;
            out.add(a);
            if (out.size() >= max) break;
        }
        // Top up with common car apps so the dock is never empty on first run
        if (out.size() < max) {
            for (App a : all) {
                if (out.size() >= max) break;
                if (h.contains(a.key) || pins.contains(a.key) || out.contains(a) || a.system) continue;
                out.add(a);
            }
        }
        return out;
    }

    static List<App> search(String q) {
        String s = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<App> out = new ArrayList<>();
        for (App a : visible()) if (s.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(s)) out.add(a);
        return out;
    }
}
