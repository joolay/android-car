package com.carbox.launcher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Startup optimiser: finds apps that clash with Car Launcher or slow the box down at start-up.
 * Android doesn't let a normal app switch other apps off, so each row opens that app's App info
 * page, where Disable / Force stop is one tap. "Keep" hides an app from the list for good.
 */
final class Optimiser {
    private static final String IGNORED = "opt_ignored";

    static final class Item {
        String pkg, label, why;
        Drawable icon;
        int rank; // 0 = conflict, 1 = slows start-up
    }

    private Optimiser() {
    }

    static List<Item> scan(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> ignored = new HashSet<>(Store.list(IGNORED));
        Set<String> autostart = new HashSet<>();
        for (int i = 0; i < Actions.autostart().length(); i++) {
            String k = Actions.autostart().optString(i);
            autostart.add(k.contains("/") ? k.substring(0, k.indexOf('/')) : k);
        }
        String me = c.getPackageName();
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. other home-screen apps: they can take over the Home button or start on top of us
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : pm.queryIntentActivities(home, 0)) {
            String p = r.activityInfo.packageName;
            if (p.equals(me) || p.equals("com.android.settings") || p.contains("fallbackhome") || ignored.contains(p) || !seen.add(p)) continue;
            add(out, pm, p, "Another launcher. It can take over the Home button", 0);
        }

        // 2. user-installed apps that draw over the screen or start themselves when the car starts
        for (Apps.App a : Apps.all) {
            String p = a.pkg;
            if (p.equals(me) || ignored.contains(p) || seen.contains(p) || autostart.contains(p) || a.system) continue;
            PackageInfo pi;
            try {
                pi = pm.getPackageInfo(p, PackageManager.GET_PERMISSIONS);
            } catch (Exception e) {
                continue;
            }
            if (pi.requestedPermissions == null) continue;
            boolean overlay = false, boot = false;
            for (String perm : pi.requestedPermissions) {
                if (Manifest.permission.SYSTEM_ALERT_WINDOW.equals(perm)) overlay = true;
                if (Manifest.permission.RECEIVE_BOOT_COMPLETED.equals(perm)) boot = true;
            }
            // music and navigation apps are expected to run in a car
            if (a.category == ApplicationInfo.CATEGORY_AUDIO || a.category == ApplicationInfo.CATEGORY_MAPS || java.util.Arrays.asList(Actions.MUSIC).contains(p)) continue;
            if (overlay && boot) {
                seen.add(p);
                add(out, pm, p, "Starts with the car and can float over the screen", 0);
            } else if (boot) {
                seen.add(p);
                add(out, pm, p, "Starts itself when the car starts", 1);
            }
        }
        out.sort((x, y) -> x.rank != y.rank ? x.rank - y.rank : x.label.compareToIgnoreCase(y.label));
        return out;
    }

    private static void add(List<Item> out, PackageManager pm, String pkg, String why, int rank) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (!ai.enabled) return; // already off
            Item it = new Item();
            it.pkg = pkg;
            it.label = pm.getApplicationLabel(ai).toString();
            it.icon = pm.getApplicationIcon(ai);
            it.why = why;
            it.rank = rank;
            out.add(it);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ page

    static Sheets.Page page;

    static void show() {
        Sheets.Page p = page = () -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            List<Item> items = scan(c);
            b.addView(U.text(c, items.isEmpty()
                    ? "Nothing to tidy up. No other launchers or self-starting apps were found."
                    : "These may clash with Car Launcher or slow the box down when the car starts. Tap Disable to open the app's page, then tap Disable or Force stop there.",
                    13, U.SUB, false));
            for (Item it : items) {
                LinearLayout r = U.row(c);
                r.setMinimumHeight(U.u(54));
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(it.icon);
                r.addView(iv, U.lpu(34, 34));
                LinearLayout t = U.col(c);
                t.addView(U.line(c, it.label, 15, U.TEXT, false));
                t.addView(U.line(c, it.why, 12, it.rank == 0 ? U.AMBER : U.SUB, false), Widgets.top(2));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
                tl.setMargins(U.u(12), 0, U.u(10), 0);
                r.addView(t, tl);
                r.addView(U.button(c, "Keep", false, () -> {
                    List<String> l = new ArrayList<>(Store.list(IGNORED));
                    l.add(it.pkg);
                    Store.putList(IGNORED, l);
                    Sheets.refresh();
                }));
                r.addView(U.gap(c, 8, 1));
                r.addView(U.button(c, "Disable", true, () -> Apps.appInfo(c, it.pkg)));
                b.addView(U.hr(c), Widgets.top(6));
                b.addView(r);
            }
            if (!Store.list(IGNORED).isEmpty()) {
                TextView reset = U.text(c, "Show kept apps again", 12, U.ACCENT_SOFT, false);
                reset.setGravity(Gravity.CENTER);
                reset.setOnClickListener(v -> {
                    Store.putList(IGNORED, new ArrayList<>());
                    Sheets.refresh();
                });
                b.addView(reset, Widgets.top(12));
            }
            Store.put("opt_seen", true);
            return Sheets.panel("Startup optimiser", b, 680, 440);
        };
        if (Sheets.open()) Sheets.push(p);
        else Sheets.show(p);
    }

    static int count(Context c) {
        return scan(c).size();
    }
}
