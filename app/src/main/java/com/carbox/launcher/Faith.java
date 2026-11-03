package com.carbox.launcher;

import android.content.Context;
import android.widget.LinearLayout;

/**
 * Faith add-ons. Religious features (prayer times, qibla, salaah announcements, travel prayers,
 * Hijri/Hebrew calendars) only appear when that faith is selected; "None" hides them all.
 */
final class Faith {
    static final String KEY = "faith";
    static final String[][] ALL = {{"none", "None"}, {"Islam", "Islam"}, {"Christianity", "Christianity"}, {"Judaism", "Judaism"}};

    private Faith() {
    }

    /** "none", or a faith name as used by Prayers ("Islam", "Christianity", "Judaism"). */
    static String get() {
        String f = Prefs.str(KEY, "");
        if (!f.isEmpty()) return f;
        f = guess();
        Prefs.put(KEY, f); // remember, so this isn't worked out again on every call
        return f;
    }

    private static String guess() {
        // existing set-ups: carry on as before if Islamic features are in use
        String pid = Prefs.str(Prefs.PRAYER_ID, "");
        if (pid.startsWith("islam") || Prefs.arr(Prefs.TILES).toString().matches(".*\"(nextprayer|prayertimes|qibla)\".*")) return "Islam";
        if (pid.startsWith("christ")) return "Christianity";
        if (pid.startsWith("jud")) return "Judaism";
        return "none"; // new set-up: chosen in the welcome checklist
    }

    static boolean islam() {
        return "Islam".equals(get());
    }

    /** Is this faith's add-on switched on (for travel prayers listed by faith)? */
    static boolean allows(String faith) {
        return faith.equals(get());
    }

    /** Widgets that belong to a faith add-on. */
    static boolean widgetAllowed(String id) {
        boolean islamic = "nextprayer".equals(id) || "prayertimes".equals(id) || "qibla".equals(id);
        return !islamic || islam();
    }

    static boolean calendarAllowed(String alt) {
        if (alt.startsWith("islamic")) return islam();
        if ("hebrew".equals(alt)) return "Judaism".equals(get());
        return true;
    }

    static String label() {
        String f = get();
        return "none".equals(f) ? "None" : f;
    }

    static void picker() {
        Panels.Page p = () -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            b.addView(Ui.text(c, "Religious features are add-ons. Only the faith you pick shows its prayer times, tiles, travel prayers, announcements and calendar.", 13, Ui.SUB, false));
            String cur = get();
            java.util.List<android.view.View> segs = new java.util.ArrayList<>();
            for (String[] f : ALL) segs.add(Ui.seg(c, f[1], f[0].equals(cur), () -> {
                Prefs.put(KEY, f[0]);
                if ("hebrew".equals(Cal.alt()) || Cal.alt().startsWith("islamic")) {
                    if (!calendarAllowed(Cal.alt())) Prefs.put(Prefs.CAL_ALT, "none");
                }
                if ("Islam".equals(f[0]) && Prefs.str(Prefs.CAL_ALT, "").isEmpty()) Prefs.put(Prefs.CAL_ALT, "islamic-umalqura");
                if ("Judaism".equals(f[0]) && "none".equals(Cal.alt())) Prefs.put(Prefs.CAL_ALT, "hebrew");
                Prefs.put("faith_seen", true);
                Panels.refresh();
                if (MainActivity.I != null) MainActivity.I.rail.build();
            }));
            b.addView(AppPanels.grid(c, segs, 4, 10), Widgets.top(14));
            return Panels.panel("Faith add-ons", b, 600, 300);
        };
        if (Panels.open()) Panels.push(p);
        else Panels.show(p);
    }
}
