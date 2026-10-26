package com.carbox.launcher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** One launchable app (an activity with a launcher icon). */
final class AppInfo {
    final ComponentName component;
    final String key;      // "package/activity" – what we save in folders and the dock
    final String pkg;
    final String label;
    final Drawable icon;
    final int category;    // ApplicationInfo.CATEGORY_*
    final boolean system;

    AppInfo(ComponentName component, String label, Drawable icon, int category, boolean system) {
        this.component = component;
        this.key = component.flattenToString();
        this.pkg = component.getPackageName();
        this.label = label;
        this.icon = icon;
        this.category = category;
        this.system = system;
    }
}
