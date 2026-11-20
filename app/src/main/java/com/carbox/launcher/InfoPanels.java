package com.carbox.launcher;

import android.content.Context;
import android.content.Intent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Panels that show information: calendar, weather, status quick cards, prayer times, Qibla. */
final class InfoPanels {
    private InfoPanels() {
    }

    static String clock(double hours) {
        if (Prefs.flag(Prefs.H24, true)) return Ui.hm(hours);
        int mins = (int) Math.round(((hours % 24) + 24) % 24 * 60) % 1440;
        int h = mins / 60, m = mins % 60;
        return (h % 12 == 0 ? 12 : h % 12) + ":" + Ui.two(m) + (h < 12 ? " am" : " pm");
    }

    static LinearLayout.LayoutParams top(float m) {
        return Widgets.top(m);
    }

    // ------------------------------------------------------------------ calendar

    private static final int[] month = new int[1]; // months offset from current, while the card is open

    static void calendar() {
        month[0] = 0;
        Panels.showCard(InfoPanels::calendarPage, true); // a quick card over the grid
    }

    private static View calendarPage() {
        Context c = Panels.ctx();
        boolean altView = Prefs.flag(Prefs.CAL_VIEW_ALT, false) && Cal.hasAlt();
        String primary = altView ? Cal.alt() : "gregorian";

        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD, 16));
        Ui.pad(card, 18, 14, 18, 14);
        card.setLayoutParams(Panels.centered(570, 360));

        LinearLayout body = Ui.row(c);
        body.setGravity(Gravity.TOP);
        card.addView(body, Ui.weight(1, false));

        // ---- month grid
        LinearLayout left = Ui.col(c);
        body.addView(left, new LinearLayout.LayoutParams(0, Ui.MATCH, 1.15f)); // month grid shares the width

        android.icu.util.Calendar cal = Cal.at(primary, System.currentTimeMillis());
        cal.add(android.icu.util.Calendar.MONTH, month[0]);
        cal.set(android.icu.util.Calendar.DAY_OF_MONTH, 1);
        cal.set(android.icu.util.Calendar.HOUR_OF_DAY, 12);
        long adj = primary.startsWith("islamic") ? Prefs.num(Prefs.CAL_ADJ, 0) * 86400000L : 0;
        long firstMs = cal.getTimeInMillis() - adj;

        LinearLayout head = Ui.row(c);
        View prev = Ui.icon(c, R.drawable.ic_back, Ui.SUB);
        prev.setOnClickListener(v -> {
            month[0]--;
            Panels.refresh();
        });
        head.addView(prev, Ui.lpu(20, 20));
        TextView title = Ui.line(c, Cal.format(primary, firstMs, "MMMM y"), 17, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        View next = Ui.icon(c, R.drawable.ic_fwd, Ui.SUB);
        next.setOnClickListener(v -> {
            month[0]++;
            Panels.refresh();
        });
        head.addView(next, Ui.lpu(20, 20));
        left.addView(head, Ui.lp(Ui.MATCH, Ui.WRAP));

        if (Cal.hasAlt()) {
            LinearLayout sw = Ui.row(c);
            sw.setGravity(Gravity.CENTER);
            sw.addView(small(c, "Gregorian", !altView, () -> {
                Prefs.put(Prefs.CAL_VIEW_ALT, false);
                month[0] = 0;
                Panels.refresh();
            }));
            sw.addView(Ui.gap(c, 6, 1));
            sw.addView(small(c, Cal.shortLabel(), altView, () -> {
                Prefs.put(Prefs.CAL_VIEW_ALT, true);
                month[0] = 0;
                Panels.refresh();
            }));
            left.addView(sw, top(8));
        }

        boolean sun = Prefs.flag(Prefs.CAL_WEEK_SUN, false);
        LinearLayout dow = Ui.row(c);
        String[] names = sun ? new String[]{"S", "M", "T", "W", "T", "F", "S"} : new String[]{"M", "T", "W", "T", "F", "S", "S"};
        for (String n : names) {
            TextView t = Ui.text(c, n, 11, Ui.MUTED, true);
            t.setGravity(Gravity.CENTER);
            dow.addView(t, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        }
        left.addView(dow, top(10));

        java.util.Calendar g = java.util.Calendar.getInstance();
        g.setTimeInMillis(firstMs);
        int wd = g.get(java.util.Calendar.DAY_OF_WEEK); // 1 = Sunday
        int lead = sun ? wd - 1 : (wd + 5) % 7;
        g.add(java.util.Calendar.DAY_OF_YEAR, -lead);
        java.util.Calendar today = java.util.Calendar.getInstance();
        int curMonth = cal.get(android.icu.util.Calendar.MONTH);
        String secondary = altView ? "gregorian" : Cal.hasAlt() ? Cal.alt() : null;
        for (int w = 0; w < 6; w++) {
            LinearLayout row = Ui.row(c);
            boolean any = false;
            for (int d = 0; d < 7; d++) {
                long ms = g.getTimeInMillis();
                android.icu.util.Calendar pc = Cal.at(primary, ms);
                boolean inMonth = pc.get(android.icu.util.Calendar.MONTH) == curMonth;
                if (inMonth) any = true;
                boolean isToday = g.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
                        && g.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR);
                LinearLayout cell = Ui.col(c);
                cell.setGravity(Gravity.CENTER);
                if (isToday) cell.setBackground(Ui.round(Ui.ACCENT, 9));
                TextView n = Ui.text(c, String.valueOf(pc.get(android.icu.util.Calendar.DAY_OF_MONTH)), 13, inMonth ? Ui.TEXT : Ui.MUTED, isToday);
                n.setGravity(Gravity.CENTER);
                cell.addView(n);
                if (secondary != null) {
                    android.icu.util.Calendar sc = Cal.at(secondary, ms);
                    TextView s = Ui.text(c, String.valueOf(sc.get(android.icu.util.Calendar.DAY_OF_MONTH)), 8, isToday ? 0xCCFFFFFF : inMonth ? Ui.TEAL : 0xFF3A4452, false);
                    s.setGravity(Gravity.CENTER);
                    cell.addView(s);
                }
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, Ui.u(secondary != null ? 34 : 28), 1);
                cl.setMargins(Ui.u(2), Ui.u(1), Ui.u(2), Ui.u(1));
                row.addView(cell, cl);
                g.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }
            if (!any) break;
            left.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
        }

        // ---- upcoming
        View div = new View(c);
        div.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams dl = Ui.lpu(1, Ui.MATCH);
        dl.setMargins(Ui.u(14), Ui.u(4), Ui.u(14), Ui.u(4));
        body.addView(div, dl);
        LinearLayout right = Ui.col(c);
        body.addView(right, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        LinearLayout rh = Ui.row(c);
        rh.addView(Ui.line(c, "Upcoming", 15, Ui.TEXT, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        View x = Ui.icon(c, R.drawable.ic_x, Ui.SUB);
        x.setOnClickListener(v -> Panels.close());
        rh.addView(x, Ui.lpu(26, 26));
        right.addView(rh);
        if (Cal.hasAlt()) {
            TextView today2 = Ui.line(c, Cal.format(Cal.alt(), System.currentTimeMillis(), "d MMMM y"), 11, Ui.TEAL, false);
            right.addView(today2, top(3));
        }
        List<Cal.Event> up = Cal.upcoming(c, 5);
        LinearLayout list = Ui.col(c);
        android.widget.ScrollView ls = new android.widget.ScrollView(c); // never clipped in the tile area
        ls.setVerticalScrollBarEnabled(false);
        ls.addView(list);
        LinearLayout.LayoutParams lsl = new LinearLayout.LayoutParams(Ui.MATCH, 0, 1);
        lsl.topMargin = Ui.u(10);
        right.addView(ls, lsl);
        if (up.isEmpty()) list.addView(Ui.text(c, "Nothing coming up", 12, Ui.SUB, false));
        // explain why Google Calendar events are missing, with a button that fixes it
        String problem = null, fix = null;
        Runnable fixIt = null;
        if (!Cal.canReadCalendar(c)) {
            problem = "Calendar access is off, so Google events can't show.";
            fix = "Allow";
            fixIt = () -> {
                if (MainActivity.I == null) return;
                if (MainActivity.I.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CALENDAR) || !Prefs.flag("asked_cal", false)) {
                    Prefs.put("asked_cal", true);
                    MainActivity.I.requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 4);
                } else Apps.appInfo(c, c.getPackageName()); // denied for good: turn it on under Permissions
                Panels.close();
            };
        } else if (Cal.calendars(c).isEmpty()) {
            problem = "No Google calendars on this box. Add your Google account and turn on Calendar sync.";
            fix = "Accounts";
            fixIt = () -> {
                Status.open(c, android.provider.Settings.ACTION_SYNC_SETTINGS);
                Panels.close();
            };
        } else if (Cal.events(c, 60).isEmpty()) {
            problem = "No Google events in the next 60 days. If that's wrong, check Calendar sync is on.";
            fix = "Sync";
            fixIt = () -> {
                Status.open(c, android.provider.Settings.ACTION_SYNC_SETTINGS);
                Panels.close();
            };
        }
        if (problem != null) {
            LinearLayout pr = Ui.row(c);
            pr.setBackground(Ui.round(0xFF2A2414, 10));
            Ui.pad(pr, 10, 8, 8, 8);
            pr.addView(Ui.text(c, problem, 11, 0xFFE8D5A8, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            final Runnable fr = fixIt;
            TextView fb = Ui.text(c, fix + " ›", 12, Ui.AMBER, true);
            Ui.pad(fb, 8, 4, 2, 4);
            fb.setOnClickListener(v -> fr.run());
            pr.addView(fb);
            list.addView(pr, top(4));
        }
        for (Cal.Event e : up) {
            LinearLayout r = Ui.row(c);
            r.setGravity(Gravity.TOP);
            r.setBackground(Themes.inner(Ui.CARD2, 12)); // each event on its own card
            Ui.pad(r, 12, 10, 12, 10);
            View dot = new View(c);
            dot.setBackground(Ui.oval(e.special ? Ui.GREEN : Ui.ACCENT));
            LinearLayout.LayoutParams dotl = Ui.lpu(7, 7);
            dotl.topMargin = Ui.u(5);
            r.addView(dot, dotl);
            LinearLayout t = Ui.col(c);
            t.addView(Ui.line(c, e.title, 14, Ui.TEXT, false));
            String when = Cal.dayLabel(e.at) + (e.allDay ? "" : " · " + clock(hours(e.at)));
            t.addView(Ui.line(c, when, 11, Ui.SUB, false), top(2));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            tl.leftMargin = Ui.u(10);
            r.addView(t, tl);
            LinearLayout.LayoutParams rl = Ui.lp(Ui.MATCH, Ui.WRAP);
            rl.bottomMargin = Ui.u(8);
            list.addView(r, rl);
        }
        TextView open = Ui.text(c, "Open calendar ›", 13, Ui.ACCENT_SOFT, true);
        open.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                Panels.close();
            } catch (Exception e) {
                Apps.launchPkg(c, "com.google.android.calendar");
            }
        });
        right.addView(open, top(8));
        return card;
    }

    static double hours(long ms) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(ms);
        return k.get(Calendar.HOUR_OF_DAY) + k.get(Calendar.MINUTE) / 60.0;
    }

    private static TextView small(Context c, String s, boolean on, Runnable click) {
        TextView t = Ui.text(c, s, 12, on ? Ui.TEXT : Ui.SUB, on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(on ? Ui.round(Ui.ACCENT, 12) : Themes.inner(Ui.CARD2, 12));
        Ui.pad(t, 12, 5, 12, 5);
        t.setOnClickListener(v -> click.run());
        return t;
    }

    // ------------------------------------------------------------------ weather

    private static int weatherTab;
    private static boolean weatherWatching;
    private static final Panels.Page WEATHER_PAGE = InfoPanels::weatherPage;

    static void weather(int tab) {
        weatherTab = tab;
        if (!weatherWatching) { // when a fresh forecast lands while the card is open, redraw it (hours filled in at once)
            weatherWatching = true;
            Weather.listen(() -> { if (Panels.isTop(WEATHER_PAGE)) Panels.refresh(); });
        }
        // a centred card (not squeezed into the short tile area, where the forecast was getting cut off)
        Panels.showCard(WEATHER_PAGE, false);
        Weather.maybeFetch();
    }

    private static View weatherPage() {
        Context c = Panels.ctx();
        LinearLayout b = Ui.col(c);
        LinearLayout tabs = Ui.row(c);
        String[] names = {"Today", "Tomorrow", "3 days", "7 days"};
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            tabs.addView(Ui.seg(c, names[i], weatherTab == i, () -> {
                weatherTab = k;
                Panels.refresh();
            }));
            tabs.addView(Ui.gap(c, 8, 1));
        }
        b.addView(tabs);
        // places: where you are, then your saved places; + adds one, hold a place to remove it
        java.util.List<String[]> places = Weather.places();
        if (weatherPlace > places.size()) weatherPlace = 0;
        LinearLayout pr = Ui.row(c);
        pr.addView(Ui.seg(c, "Here", weatherPlace == 0, () -> { weatherPlace = 0; Panels.refresh(); }));
        for (int i = 0; i < places.size(); i++) {
            final int k = i + 1;
            pr.addView(Ui.gap(c, 6, 1));
            TextView ch = Ui.seg(c, places.get(i)[0].split(",")[0], weatherPlace == k, () -> { weatherPlace = k; Panels.refresh(); });
            ch.setOnLongClickListener(v -> { Weather.removePlace(k - 1); weatherPlace = 0; Panels.refresh(); return true; });
            pr.addView(ch);
        }
        pr.addView(Ui.gap(c, 6, 1));
        pr.addView(Ui.iconButton(c, R.drawable.ic_plus, "Add a place", () -> addPlace()));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(pr);
        b.addView(hs, top(8));
        String title = Gps.town != null ? "Weather · " + Gps.town : "Weather";
        if (weatherPlace > 0) { // another place: its own forecast
            String[] p = places.get(weatherPlace - 1);
            title = "Weather · " + p[0].split(",")[0];
            Weather.fetchPlace(p, Panels::refresh);
            final LinearLayout fb = b;
            if (!Weather.withPlace(p, () -> {
                if (weatherTab == 0) today(c, fb);
                else if (weatherTab == 1) tomorrow(c, fb);
                else if (weatherTab == 2) threeDays(c, fb);
                else sevenDays(c, fb);
            })) b.addView(Ui.text(c, "Loading the forecast for " + p[0] + "…", 14, Ui.SUB, false), top(24));
            return Panels.panel(title, b, 660, 440);
        }
        if (!Weather.ok) {
            b.addView(Ui.text(c, Gps.has() ? "Loading the forecast…" : "Waiting for a location to get the forecast.", 14, Ui.SUB, false), top(24));
            return Panels.panel(title, b, 660, 440);
        }
        if (weatherTab == 0) today(c, b);
        else if (weatherTab == 1) tomorrow(c, b);
        else if (weatherTab == 2) threeDays(c, b);
        else sevenDays(c, b);
        return Panels.panel(title, b, 660, 440);
    }

    private static int weatherPlace; // 0 here, then the saved places

    private static void addPlace() {
        Panels.push(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            final String[] name = {""};
            b.addView(Widgets.field("Town or city, e.g. Pietermaritzburg", "", false, t -> name[0] = t), Ui.lp(Ui.MATCH, Ui.WRAP));
            b.addView(Ui.button(c, "Done", true, () -> {
                if (name[0].trim().isEmpty()) return;
                Weather.addPlace(name[0], () -> {
                    weatherPlace = Weather.places().size();
                    Panels.back();
                });
            }), top(12));
            return Panels.panel("Add a place", b, 480, 220);
        });
    }

    private static void today(Context c, LinearLayout b) {
        LinearLayout r = Ui.row(c);
        r.addView(Ui.icon(c, Weather.icon(Weather.code, Weather.isDay), 0), Ui.lpu(64, 64));
        LinearLayout t = Ui.col(c);
        t.addView(Ui.text(c, Weather.t(Weather.temp), 40, Ui.TEXT, true));
        t.addView(Ui.text(c, Weather.describe(Weather.code), 14, Ui.TEXT2, false), top(2));
        LinearLayout.LayoutParams tl = Ui.lp(Ui.WRAP, Ui.WRAP);
        tl.leftMargin = Ui.u(14);
        r.addView(t, tl);
        r.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        // facts, 2 columns × 3
        Weather.Day d = Weather.today();
        String[][] facts = {
                {"Feels like", Weather.t(Weather.feels)},
                {"Humidity", Weather.humidity + "%"},
                {"Wind", Math.round(Weather.windKmh) + " km/h" + (d != null ? " " + Weather.compass(d.windDir) : "")},
                {"Rain", d != null ? d.rainProb + "%" : "--"},
                {"UV index", d != null ? String.valueOf(Math.round(d.uv)) : "--"},
                {"Sun", d != null ? d.sunrise + " – " + d.sunset : "--"}};
        LinearLayout grid = Ui.col(c);
        for (int row = 0; row < 3; row++) {
            LinearLayout gr = Ui.row(c);
            for (int col = 0; col < 2; col++) {
                String[] f = facts[row * 2 + col];
                LinearLayout cell = Ui.col(c);
                cell.addView(Ui.line(c, f[0], 11, Ui.SUB, false));
                cell.addView(Ui.line(c, f[1], 14, Ui.TEXT, true), top(2));
                gr.addView(cell, Ui.lpu(130, Ui.WRAP));
            }
            LinearLayout.LayoutParams gl = Ui.lp(Ui.WRAP, Ui.WRAP);
            if (row > 0) gl.topMargin = Ui.u(8);
            grid.addView(gr, gl);
        }
        r.addView(grid);
        b.addView(r, top(18));
        hourly(c, b, -1); // today's hours, scrolling on into tomorrow
    }

    /**
     * The scrolling hourly strip. onlyDay -1: from now, carrying on past midnight into tomorrow (with a
     * "Tomorrow" divider); 0 or 1: just that day's hours.
     */
    private static void hourly(Context c, LinearLayout b, int onlyDay) {
        LinearLayout hours = Ui.row(c);
        int shown = 0;
        boolean dividerDone = false;
        for (int i = 0; i < Weather.hours.size() && shown < 24; i++) {
            double[] h = Weather.hours.get(i);
            int off = h.length > 5 ? (int) h[5] : 0;
            if (onlyDay >= 0 && off != onlyDay) continue;
            if (onlyDay < 0 && off >= 1 && !dividerDone) { // mark where tomorrow begins
                dividerDone = true;
                LinearLayout div = Ui.col(c);
                div.setGravity(Gravity.CENTER);
                View bar = new View(c); // a slim line marks midnight
                bar.setBackgroundColor(Ui.ACCENT_SOFT);
                div.addView(bar, Ui.lpu(2, 40));
                TextView dl = Ui.line(c, "Tomorrow", 11, Ui.ACCENT_SOFT, true);
                div.addView(dl, top(6));
                LinearLayout.LayoutParams dvl = Ui.lpu(72, Ui.MATCH);
                if (shown > 0) dvl.leftMargin = Ui.u(8);
                hours.addView(div, dvl);
            }
            boolean now = onlyDay < 0 && shown == 0;
            LinearLayout cell = Ui.col(c);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setBackground(Themes.inner(now ? Ui.CARD3 : Ui.CARD2, 12));
            Ui.pad(cell, 8, 10, 8, 10);
            cell.addView(Ui.text(c, now ? "Now" : clockShort((int) h[0]), 11, Ui.SUB, false));
            LinearLayout.LayoutParams il = Ui.lpu(28, 28);
            il.topMargin = Ui.u(6);
            cell.addView(Ui.icon(c, Weather.icon((int) h[2], h[4] > 0), 0), il);
            cell.addView(Ui.text(c, Weather.t(h[1]), 14, Ui.TEXT, true), top(6));
            cell.addView(Ui.text(c, (int) h[3] + "%", 10, (int) h[3] >= 40 ? Ui.ACCENT_SOFT : Ui.MUTED, false), top(3));
            LinearLayout.LayoutParams cl = Ui.lpu(62, Ui.WRAP); // same width each, never squashed
            if (shown > 0 || dividerDone) cl.leftMargin = Ui.u(8);
            hours.addView(cell, cl);
            shown++;
        }
        if (shown == 0) b.addView(Ui.text(c, "No hourly forecast.", 13, Ui.SUB, false), top(18));
        else {
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(c); // swipe for later hours
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.addView(hours);
            b.addView(hsv, top(18));
        }
    }

    /** Tomorrow: the day's headline figures, then tomorrow's hours. */
    private static void tomorrow(Context c, LinearLayout b) {
        Weather.Day d = Weather.days.size() > 1 ? Weather.days.get(1) : null;
        if (d == null) {
            b.addView(Ui.text(c, "Tomorrow's forecast isn't in yet.", 14, Ui.SUB, false), top(24));
            return;
        }
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(Ui.icon(c, Weather.icon(d.code, true), 0), Ui.lpu(64, 64));
        LinearLayout t = Ui.col(c);
        t.addView(Ui.text(c, "H " + Weather.t(d.hi) + "   L " + Weather.t(d.lo), 24, Ui.TEXT, true));
        t.addView(Ui.text(c, Weather.describe(d.code), 14, Ui.TEXT2, false), top(2));
        LinearLayout.LayoutParams tl = Ui.lp(Ui.WRAP, Ui.WRAP);
        tl.leftMargin = Ui.u(14);
        r.addView(t, tl);
        b.addView(r, top(8));
        b.addView(Ui.line(c, "Rain " + d.rainProb + "%   ·   Wind " + Math.round(d.windKmh) + " km/h " + Weather.compass(d.windDir)
                + "   ·   UV " + Math.round(d.uv), 13, Ui.SUB, false), top(10));
        hourly(c, b, 1); // tomorrow's hours
    }

    static String clockShort(int hour) {
        if (Prefs.flag(Prefs.H24, true)) return Ui.two(hour) + ":00";
        return (hour % 12 == 0 ? 12 : hour % 12) + (hour < 12 ? " am" : " pm");
    }

    static String dayName(String date, int index) {
        if (index == 0) return "Today";
        if (index == 1) return "Tomorrow";
        try {
            java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            return new java.text.SimpleDateFormat("EEEE", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return date;
        }
    }

    private static void threeDays(Context c, LinearLayout b) {
        LinearLayout r = Ui.row(c);
        for (int i = 0; i < Math.min(3, Weather.days.size()); i++) {
            Weather.Day d = Weather.days.get(i);
            LinearLayout card = Ui.col(c);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackground(Themes.card(Ui.CARD2, 16)); // pop-up card: glass in the Glass theme
            Ui.pad(card, 14, 16, 14, 16);
            card.addView(Ui.text(c, dayName(d.date, i), 15, Ui.TEXT, true));
            LinearLayout.LayoutParams il = Ui.lpu(56, 56);
            il.topMargin = Ui.u(12);
            card.addView(Ui.icon(c, Weather.icon(d.code, true), 0), il);
            card.addView(Ui.text(c, Weather.describe(d.code), 13, Ui.TEXT2, false), top(8));
            LinearLayout hl = Ui.row(c);
            hl.addView(Ui.text(c, Weather.t(d.hi), 22, Ui.TEXT, true));
            hl.addView(Ui.text(c, "  " + Weather.t(d.lo), 16, Ui.SUB, false));
            card.addView(hl, top(10));
            card.addView(Ui.text(c, "Rain " + d.rainProb + "%" + (d.rainMm >= 0.5 ? " · " + Math.round(d.rainMm) + " mm" : ""), 12, Ui.SUB, false), top(10));
            card.addView(Ui.text(c, "Wind " + Math.round(d.windKmh) + " km/h " + Weather.compass(d.windDir), 12, Ui.SUB, false), top(4));
            card.addView(Ui.text(c, "Sun " + d.sunrise + " – " + d.sunset, 12, Ui.SUB, false), top(4));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            if (i > 0) cl.leftMargin = Ui.u(10);
            r.addView(card, cl);
        }
        b.addView(r, top(18));
    }

    private static void sevenDays(Context c, LinearLayout b) {
        double min = 99, max = -99;
        for (Weather.Day d : Weather.days) {
            min = Math.min(min, d.lo);
            max = Math.max(max, d.hi);
        }
        LinearLayout list = Ui.col(c);
        for (int i = 0; i < Weather.days.size(); i++) {
            Weather.Day d = Weather.days.get(i);
            LinearLayout r = Ui.row(c);
            r.addView(Ui.line(c, dayName(d.date, i), 14, Ui.TEXT, i == 0), Ui.lpu(100, Ui.WRAP));
            r.addView(Ui.icon(c, Weather.icon(d.code, true), 0), Ui.lpu(26, 26));
            TextView rp = Ui.text(c, d.rainProb >= 20 ? d.rainProb + "%" : "", 11, Ui.ACCENT_SOFT, false);
            r.addView(rp, Ui.lpu(44, Ui.WRAP));
            rp.setGravity(Gravity.CENTER);
            TextView lo = Ui.text(c, Weather.t(d.lo), 13, Ui.SUB, false);
            lo.setGravity(Gravity.END);
            r.addView(lo, Ui.lpu(40, Ui.WRAP));
            Bar bar = new Bar(c, (float) ((d.lo - min) / Math.max(1, max - min)), (float) ((d.hi - min) / Math.max(1, max - min)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.u(6), 1);
            bl.setMargins(Ui.u(12), 0, Ui.u(12), 0);
            r.addView(bar, bl);
            r.addView(Ui.text(c, Weather.t(d.hi), 13, Ui.TEXT, true), Ui.lpu(40, Ui.WRAP));
            LinearLayout.LayoutParams rl = Ui.lpu(Ui.MATCH, 40);
            list.addView(r, rl);
        }
        b.addView(list, top(12));
    }

    static final class Bar extends View {
        private final float from, to;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c, float from, float to) {
            super(c);
            this.from = from;
            this.to = to;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float h = getHeight(), w = getWidth();
            p.setColor(Ui.CARD3);
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, h / 2, h / 2, p);
            p.setShader(new android.graphics.LinearGradient(0, 0, w, 0, Ui.TEAL, Ui.AMBER, android.graphics.Shader.TileMode.CLAMP));
            r.set(w * from, 0, Math.max(w * from + h, w * to), h);
            cv.drawRoundRect(r, h / 2, h / 2, p);
            p.setShader(null);
        }
    }

    // ------------------------------------------------------------------ status quick cards

    static void quick(String id) {
        Panels.showCard(() -> quickCard(id));
    }

    /** Pop-up beside the voice button: the box's assistant plus any installed ones (Claude, ChatGPT, Alexa…). */
    static void assistants() {
        Panels.showCard(() -> {
            Context c = Panels.ctx();
            LinearLayout card = Ui.col(c);
            card.setBackground(Themes.card(Ui.CARD2, 16)); // pop-up card: glass in the Glass theme
            Ui.pad(card, 14, 12, 14, 12);
            card.setLayoutParams(Panels.anchored(280, -2, 88, 40));
            card.addView(Ui.line(c, "Assistants", 15, Ui.TEXT, true));
            card.addView(Ui.line(c, "Tap to talk · hold to make it the voice button's", 11, Ui.SUB, false), top(2));
            boolean ext = "external".equals(Prefs.str(Prefs.ASSIST, "gemini"));
            String cur = ext ? Prefs.str(Prefs.ASSIST_PKG, "") : "";
            // everything on the box that can answer by voice; then the ones worth having that aren't here yet
            java.util.List<String[]> on = new java.util.ArrayList<>();
            on.add(new String[]{"", "Default assistant"});
            on.addAll(Actions.installedAssistants(c));
            java.util.List<String[]> missing = new java.util.ArrayList<>();
            for (String[] w : Actions.WANTED) {
                boolean have = false;
                for (String[] a : on) {
                    if (w[0].isEmpty() ? a[1].toLowerCase(java.util.Locale.ROOT).contains(w[1].toLowerCase(java.util.Locale.ROOT)) : w[0].equals(a[0])) have = true;
                }
                if (!have) missing.add(w);
            }
            LinearLayout rows = Ui.col(c);
            for (String[] a : on) {
                boolean gem = a[0].isEmpty() || Actions.GEMINI.equals(a[0]); // the box's own way (Gemini / Google)
                boolean chosen = ext ? a[0].equals(cur) : a[0].isEmpty();
                LinearLayout r = assistRow(c, a[0], a[1], chosen ? "Voice button" : null);
                r.setOnClickListener(v -> { // for this one go
                    Panels.close();
                    if (gem) Actions.boxVoice(c);
                    else Actions.voiceStart(c, a[0]);
                });
                r.setOnLongClickListener(v -> { // from now on
                    if (gem) Prefs.put(Prefs.ASSIST, "gemini");
                    else {
                        Prefs.put(Prefs.ASSIST, "external");
                        Prefs.put(Prefs.ASSIST_PKG, a[0]);
                    }
                    android.widget.Toast.makeText(c, "The voice button now uses " + a[1], android.widget.Toast.LENGTH_SHORT).show();
                    Panels.refresh();
                    return true;
                });
                Ui.press(r);
                rows.addView(Ui.hr(c), top(4));
                rows.addView(r);
            }
            for (String[] w : missing) {
                LinearLayout r = assistRow(c, null, w[1], null);
                r.setAlpha(0.6f);
                TextView get = Ui.text(c, "Get", 12, Ui.TEXT, true);
                get.setBackground(Ui.round(Ui.ACCENT, 10));
                Ui.pad(get, 10, 4, 10, 4);
                r.addView(get);
                r.setOnClickListener(v -> { // its Play Store page (or a search there)
                    Panels.close();
                    try {
                        c.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(w[2])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        android.widget.Toast.makeText(c, "No app store on this box", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
                Ui.press(r);
                rows.addView(Ui.hr(c), top(4));
                rows.addView(r);
            }
            int n = on.size() + missing.size();
            android.widget.ScrollView sv = new android.widget.ScrollView(c); // a long list scrolls instead of running off the screen
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(rows);
            card.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, n > 6 ? Ui.u(6 * 53) : Ui.WRAP));
            return card;
        });
    }

    /** One assistant in the voice button's list: its icon (or a mic), its name, and an optional tag on the right. */
    private static LinearLayout assistRow(Context c, String pkg, String name, String tag) {
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.u(48));
        android.graphics.drawable.Drawable icon = null;
        try {
            if (pkg != null && !pkg.isEmpty()) icon = c.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) {
        }
        if (icon != null) {
            ImageView iv = new ImageView(c);
            iv.setImageDrawable(icon);
            r.addView(iv, Ui.lpu(30, 30));
        } else {
            FrameLayout f = new FrameLayout(c);
            f.setBackground(Ui.oval(pkg == null ? Ui.CARD3 : Ui.RED)); // grey: not on the box yet
            f.addView(Ui.icon(c, R.drawable.ic_mic, Ui.TEXT), Ui.flpu(16, 16, Gravity.CENTER));
            r.addView(f, Ui.lpu(30, 30));
        }
        TextView t = Ui.line(c, name, 14, Ui.TEXT, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(12);
        r.addView(t, tl);
        if (tag != null) r.addView(Ui.line(c, tag, 11, Ui.ACCENT_SOFT, true));
        return r;
    }

    private static View quickCard(String id) {
        Context c = Panels.ctx();
        LinearLayout card = Ui.col(c);
        card.setBackground(Themes.card(Ui.CARD2, 16)); // pop-up card: glass in the Glass theme
        Ui.pad(card, 16, 14, 16, 14);
        card.setLayoutParams(Panels.anchored(260, -2, 88, 110));
        int icon, tint = Ui.TEXT;
        String title, line1, line2 = null, hint;
        Boolean on = null;           // null = no toggle
        Ui.Cb<Boolean> toggle = null;
        switch (id) {
            case "data": {
                icon = R.drawable.ic_bolt;
                title = "Data this month";
                long[] lte = DataMeter.used(c, DataMeter.LTE), wf = DataMeter.used(c, DataMeter.WIFI), hs = DataMeter.used(c, DataMeter.HOTSPOT);
                line1 = "LTE  ↓ " + DataMeter.size(lte[0]) + "  ↑ " + DataMeter.size(lte[1])
                        + "\nHotspot  ↓ " + DataMeter.size(hs[0]) + "  ↑ " + DataMeter.size(hs[1])
                        + "\nWi-Fi  ↓ " + DataMeter.size(wf[0]) + "  ↑ " + DataMeter.size(wf[1]);
                long[][] sp = DataMeter.speed;
                line2 = "Now: LTE " + DataMeter.rate(sp[0][0] + sp[0][1]) + " · Wi-Fi " + DataMeter.rate(sp[1][0] + sp[1][1])
                        + " · Hotspot " + DataMeter.rate(sp[2][0] + sp[2][1]);
                hint = "Hotspot data also counts in Mobile (it comes over mobile); Total is Mobile plus Wi-Fi. Starts again on the 1st. Hold for Android's data usage";
                break;
            }
            case "cell":
                icon = R.drawable.ic_bolt;
                title = "Mobile data";
                line1 = !Status.sim ? "No SIM card" : Status.dataOn ? "Connected" : "Not connected";
                if (Status.sim) line2 = (Status.operator == null || Status.operator.isEmpty() ? "" : Status.operator + " · ")
                        + (Status.netType != null ? Status.netType + " · " : "") + signal(Status.cellLevel);
                hint = "Hold the icon for mobile network settings";
                on = Status.dataOn;
                toggle = v -> Status.dataPanel(c);
                break;
            case "wifi":
                icon = R.drawable.ic_wifi;
                title = "Wi-Fi";
                line1 = !Status.wifiOn ? "Off" : Status.wifiConnected ? (Status.ssid == null ? "Connected" : Status.ssid) : "Searching";
                if (Status.wifiConnected) line2 = "Connected · " + signal(Status.wifiLevel);
                hint = "Hold the icon for full Wi-Fi settings";
                on = Status.wifiOn;
                toggle = v -> Status.wifiPanel(c); // Android 13 only lets Settings switch Wi-Fi
                break;
            case "gps":
                icon = R.drawable.ic_navarrow;
                title = "GPS";
                line1 = !Gps.hasPermission() ? "No permission" : !Gps.gpsEnabled() ? "Location is off" : Gps.fresh() ? "Fix" : "Searching";
                line2 = (Gps.satsUsed < 0 ? "" : Gps.satsUsed + " satellites used") + (Gps.satsVisible > 0 ? (Gps.satsUsed < 0 ? "" : " · ") + Gps.satsVisible + " in view" : "")
                        + (Gps.town != null ? "\nNear " + Gps.town : "");
                hint = "Hold the icon for location settings";
                break;
            case "bluetooth":
                icon = R.drawable.ic_bt;
                title = "Bluetooth";
                Boolean lk = BtAuto.linked(); // the box's BT Phone link when it was last seen, else Android's Bluetooth
                boolean linked = lk != null ? lk : Status.btConnected;
                line1 = lk != null ? (lk ? "Phone connected" : "Phone not connected") : !Status.btOn ? "Off" : Status.btConnected ? "Connected" : "On, not connected";
                line2 = lk != null ? "Seen on BT Phone's screen" : "Open BT Phone once so this can follow it";
                hint = "Hold the icon to open BT Phone";
                tint = linked ? Ui.ACCENT : Ui.TEXT;
                break;
            case "hotspot":
                icon = R.drawable.ic_hot;
                title = "Hotspot";
                line1 = Status.hotspotOn ? "On" : "Off";
                line2 = "Shares this box's mobile data";
                hint = "Hold the icon for hotspot settings";
                on = Status.hotspotOn;
                toggle = v -> Status.tetherSettings(c);
                break;
            default:
                icon = R.drawable.ic_vpnkey;
                title = "Tailscale";
                if (!Status.tailscaleInstalled()) {
                    line1 = "Not installed";
                    line2 = "Install Tailscale from the Play Store";
                } else {
                    line1 = Status.vpnOn ? "Connected" : "Not connected";
                    line2 = HA.configured() ? (Status.vpnOn ? "Home Assistant reachable" : "Home Assistant needs Tailscale") : "Your devices over a private network";
                    on = Status.vpnOn;
                    toggle = v -> {
                        Status.tailscale(v);
                        Ui.ui.postDelayed(Panels::refresh, 2500);
                    };
                }
                tint = Status.vpnOn ? Ui.GREEN : Ui.TEXT;
                hint = "Hold the icon to open Tailscale";
        }
        LinearLayout head = Ui.row(c);
        head.addView(Ui.icon(c, icon, tint), Ui.lpu(22, 22));
        TextView t = Ui.line(c, title, 16, Ui.TEXT, true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        tl.leftMargin = Ui.u(10);
        head.addView(t, tl);
        if (on != null) {
            final Ui.Cb<Boolean> fcb = toggle;
            Ui.Toggle tg = new Ui.Toggle(c, on, v -> {
                if (fcb != null) fcb.on(v);
                if (!"tailscale".equals(id)) Panels.close();
            });
            tg.setLayoutParams(Ui.lpu(34, 20));
            head.addView(tg);
        }
        card.addView(head);
        card.addView(Ui.text(c, line1, 14, Ui.TEXT, false), top(10));
        if (line2 != null && !line2.isEmpty()) card.addView(Ui.text(c, line2, 12, Ui.SUB, false), top(2));
        View hr = new View(c);
        hr.setBackgroundColor(Ui.CARD3);
        LinearLayout.LayoutParams hl = Ui.lp(Ui.MATCH, Math.max(1, Ui.u(1)));
        hl.topMargin = Ui.u(12);
        card.addView(hr, hl);
        card.addView(Ui.text(c, hint, 12, Ui.MUTED, false), top(8));
        return card;
    }

    static String signal(int level) {
        return level >= 4 ? "strong signal" : level >= 2 ? "fair signal" : "weak signal";
    }

    private static void fact(Context c, LinearLayout b, String k, String v) {
        LinearLayout r = Ui.row(c);
        r.addView(Ui.line(c, k, 13, Ui.SUB, false), Ui.lpu(96, Ui.WRAP));
        r.addView(Ui.line(c, v, 13, Ui.TEXT, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        LinearLayout.LayoutParams lp = Ui.lp(Ui.MATCH, Ui.WRAP);
        lp.topMargin = Ui.u(6);
        b.addView(r, lp);
    }

    // ------------------------------------------------------------------ prayer

    static void prayer() {
        Panels.show(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.col(c);
            double[] today = Prayer.times(0), tom = Prayer.times(1);
            if (today == null) {
                b.addView(Ui.text(c, "Prayer times need a GPS location.", 14, Ui.SUB, false));
                return Panels.panel("Prayer times", b, 520, 300);
            }
            double[] n = Prayer.next();
            LinearLayout head = Ui.row(c);
            head.addView(Ui.text(c, "", 13, Ui.SUB, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            head.addView(colHead(c, "Today"));
            head.addView(colHead(c, "Tomorrow"));
            b.addView(head);
            for (int i = 0; i < 6; i++) {
                boolean next0 = n != null && (int) n[0] == i && n[1] == 0, next1 = n != null && (int) n[0] == i && n[1] == 1;
                LinearLayout r = Ui.row(c);
                Ui.pad(r, 10, 0, 0, 0);
                r.setMinimumHeight(Ui.u(34));
                r.addView(Ui.line(c, Prayer.NAMES[i], 15, i == 1 ? Ui.SUB : Ui.TEXT, false), new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                r.addView(cell(c, clock(today[i]), next0));
                r.addView(cell(c, clock(tom[i]), next1));
                b.addView(r);
            }
            String asr = Prefs.num(Prefs.PRAY_ASR, 2) == 2 ? "Hanafi" : "Shafi'i";
            TextView m = Ui.text(c, Prayer.METHODS[Prefs.num(Prefs.PRAY_METHOD, 0)] + " · Asr " + asr + (Gps.town != null ? " · " + Gps.town : ""), 12, Ui.SUB, false);
            b.addView(m, top(12));
            b.addView(Panels.item("Prayer time settings", null, SettingsPanels::prayerTimes), top(4));
            return Panels.panel("Prayer times", b, 560, 420);
        });
    }

    private static TextView colHead(Context c, String s) {
        TextView t = Ui.text(c, s, 12, Ui.SUB, true);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(Ui.lpu(110, Ui.WRAP));
        return t;
    }

    private static TextView cell(Context c, String s, boolean hi) {
        TextView t = Ui.text(c, s, 15, hi ? Ui.TEXT : Ui.TEXT2, hi);
        t.setGravity(Gravity.CENTER);
        if (hi) t.setBackground(Ui.round(Ui.DGREEN, 8));
        Ui.pad(t, 0, 5, 0, 5);
        LinearLayout.LayoutParams lp = Ui.lpu(100, Ui.WRAP);
        lp.leftMargin = Ui.u(10);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ qibla

    static void qibla() {
        live(true);
    }

    static void heading() {
        live(false);
    }

    /** Big live compass (qibla or heading): turns with the car, holds the last direction while parked. */
    private static void live(boolean qibla) {
        Panels.show(() -> {
            Context c = Panels.ctx();
            LinearLayout b = Ui.row(c);
            b.setGravity(android.view.Gravity.CENTER_VERTICAL);
            Widgets.Compass comp = new Widgets.Compass(c);
            if (qibla) comp.markerIcon = c.getDrawable(R.drawable.ic_kaaba);
            b.addView(comp, Ui.lpu(230, 230));
            LinearLayout t = Ui.col(c);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
            tl.leftMargin = Ui.u(28);
            TextView big = Ui.text(c, "", 44, Ui.TEXT, true), mid = Ui.text(c, "", 15, Ui.TEXT2, false),
                    info = Ui.text(c, "", 14, Ui.TEXT2, false), note = Ui.text(c, "", 12, Ui.SUB, false);
            t.addView(big);
            t.addView(mid, top(4));
            t.addView(info, top(16));
            t.addView(note, top(16));
            b.addView(t, tl);
            Runnable upd = new Runnable() {
                @Override
                public void run() {
                    if (!comp.isAttachedToWindow() && comp.getWidth() > 0) return; // panel closed
                    float h = Gps.heading();
                    boolean known = !Float.isNaN(h), parked = !Gps.moving();
                    comp.rotation = known ? h : 0;
                    comp.arrow = known;
                    if (qibla) {
                        if (!Gps.has()) {
                            comp.marker = Float.NaN;
                            big.setText("--°");
                            mid.setText("Waiting for GPS");
                            info.setText("");
                        } else {
                            float q = (float) Prayer.qibla();
                            comp.marker = q;
                            if (known) {
                                float rel = ((q - h) % 360 + 540) % 360 - 180;
                                big.setText(Math.abs(Math.round(rel)) + "°");
                                mid.setText(Math.abs(rel) < 5 ? "Straight ahead" : rel < 0 ? "To your left" : "To your right");
                            } else {
                                big.setText(Math.round(q) + "°");
                                mid.setText("from North (" + Weather.compass(Math.round(q)) + ")");
                            }
                            info.setText(String.format(Locale.US, "%d° from North · %,d km to the Kaaba", Math.round(q), Math.round(Prayer.kmToKaaba())));
                        }
                    } else {
                        big.setText(known ? Math.round(h) + "°" : "--°");
                        mid.setText(known ? Weather.compass(Math.round(h)) : Gps.fresh() ? "Drive to set the direction" : "No GPS");
                        boolean m = Widgets.mph();
                        String sp = Gps.fresh() && Gps.speed >= 0 ? Math.round(Gps.speed * 3.6f / (m ? 1.609f : 1)) + (m ? " mph" : " km/h") : "--";
                        String al = Double.isNaN(Gps.alt) ? "--" : Math.round(m ? Gps.alt * 3.28084 : Gps.alt) + (m ? " ft" : " m");
                        info.setText("Speed " + sp + " · Altitude " + al);
                    }
                    note.setText(known && parked ? "Parked: showing the direction the car was last driving." : known ? "Turns with your GPS heading." : "");
                    comp.invalidate();
                    Ui.ui.postDelayed(this, 500);
                }
            };
            Ui.ui.post(upd);
            return Panels.panel(qibla ? "Qibla" : "Heading", b, 600, 360);
        });
    }
}
