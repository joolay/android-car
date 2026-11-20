package com.carbox.launcher;

import android.content.Context;
import android.widget.LinearLayout;

/**
 * Faith add-ons, one per faith (Addons: faith_islam, faith_christianity, faith_judaism), each switched on or
 * off on its own. A faith's religious features (its widgets, travel prayers, announcements and calendar)
 * only appear when its add-on is on. Settings → Faith.
 */
final class Faith {
    static final String KEY = "faith"; // the old single choice, read once to carry set-ups over
    /** {faith (as Prayers names it), add-on id, what it brings}. */
    static final String[][] FAITHS = {
            {"Islam", "faith_islam", "Next prayer, Prayer times and Qibla widgets · salaah announcements · Hijri calendar · travel prayers"},
            {"Christianity", "faith_christianity", "Travel prayers"},
            {"Judaism", "faith_judaism", "Travel prayers · Hebrew calendar"}};

    private Faith() {
    }

    private static String addon(String faith) {
        for (String[] f : FAITHS) if (f[0].equals(faith)) return f[1];
        return null;
    }

    /** Is this faith's add-on there and switched on? */
    static boolean on(String faith) {
        migrate();
        String id = addon(faith);
        return id != null && Addons.unlocked(id) && Prefs.flag(id + "_on", false);
    }

    /** Switch a faith's add-on on or off (keeping the calendar in step). */
    static void set(String faith, boolean on) {
        String id = addon(faith);
        if (id == null) return;
        Prefs.put(id + "_on", on);
        Prefs.put("faith_seen", true);
        String raw = Prefs.str(Prefs.CAL_ALT, "");
        if (!raw.isEmpty() && !calendarAllowed(raw)) Prefs.put(Prefs.CAL_ALT, "none"); // its calendar went with it
        if (on && "Islam".equals(faith) && (Prefs.str(Prefs.CAL_ALT, "").isEmpty() || "none".equals(Cal.alt())))
            Prefs.put(Prefs.CAL_ALT, "islamic-umalqura");
        if (on && "Judaism".equals(faith) && "none".equals(Cal.alt())) Prefs.put(Prefs.CAL_ALT, "hebrew");
        if (MainActivity.I != null) MainActivity.I.rail.build();
    }

    /** Set-ups from before the add-ons: the faith that was chosen (or in use) stays on. */
    private static void migrate() {
        if (Prefs.flag("faith_addons_v1", false)) return;
        Prefs.put("faith_addons_v1", true);
        String old = Prefs.str(KEY, "");
        if (old.isEmpty()) old = guess();
        String id = addon(old);
        if (id != null) Prefs.put(id + "_on", true);
    }

    private static String guess() {
        // existing set-ups: carry on as before if Islamic features are in use
        String pid = Prefs.str(Prefs.PRAYER_ID, "");
        if (pid.startsWith("islam") || Prefs.arr(Prefs.TILES).toString().matches(".*\"(nextprayer|prayertimes|qibla)\".*")) return "Islam";
        if (pid.startsWith("christ")) return "Christianity";
        if (pid.startsWith("jud")) return "Judaism";
        return "none"; // new set-up: chosen in Settings → Faith
    }

    static boolean islam() {
        return on("Islam");
    }

    /** Is this faith's add-on on (for travel prayers listed by faith)? */
    static boolean allows(String faith) {
        return on(faith);
    }

    /** Widgets that belong to a faith add-on. */
    static boolean widgetAllowed(String id) {
        boolean islamic = "nextprayer".equals(id) || "prayertimes".equals(id) || "qibla".equals(id);
        return !islamic || islam();
    }

    static boolean calendarAllowed(String alt) {
        if (alt.startsWith("islamic")) return islam();
        if ("hebrew".equals(alt)) return on("Judaism");
        return true;
    }

    /** The faiths switched on, e.g. "Islam" or "Islam · Judaism"; "None". */
    static String label() {
        java.util.List<String> l = new java.util.ArrayList<>();
        for (String[] f : FAITHS) if (on(f[0])) l.add(f[0]);
        return l.isEmpty() ? "None" : android.text.TextUtils.join(" · ", l);
    }

    /** One switch per faith add-on, with what it brings. */
    static void addFaithSwitches(Context c, LinearLayout b) {
        for (String[] f : FAITHS) {
            if (!Addons.unlocked(f[1])) continue; // not bought (once add-ons are sold)
            b.addView(Panels.switchRow(R.drawable.ic_moon, Ui.ACCENT_SOFT, f[0], f[2], on(f[0]), v -> {
                set(f[0], v);
                Panels.refresh();
            }), Widgets.top(8));
        }
    }

    /** The faith add-ons on their own (from the set-up checklist). */
    static void picker() {
        Panels.Page p = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Each faith is an add-on: switch on the ones you want. Only those show their widgets, travel prayers, announcements and calendar.", 13, Ui.SUB, false));
            addFaithSwitches(c, b);
            return Panels.panel("Faith add-ons", b, 600, 360);
        };
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }
}
