package com.carbox.launcher;

import android.content.Context;
import android.content.Intent;
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
        if (Store.flag(Store.H24, true)) return U.hm(hours);
        int mins = (int) Math.round(((hours % 24) + 24) % 24 * 60) % 1440;
        int h = mins / 60, m = mins % 60;
        return (h % 12 == 0 ? 12 : h % 12) + ":" + U.two(m) + (h < 12 ? " am" : " pm");
    }

    static LinearLayout.LayoutParams top(float m) {
        return Widgets.top(m);
    }

    // ------------------------------------------------------------------ calendar

    private static final int[] month = new int[1]; // months offset from current, while the card is open

    static void calendar() {
        month[0] = 0;
        Sheets.show(InfoPanels::calendarPage);
    }

    private static View calendarPage() {
        Context c = Sheets.ctx();
        boolean altView = Store.flag(Store.CAL_VIEW_ALT, false) && Cal.hasAlt();
        String primary = altView ? Cal.alt() : "gregorian";

        LinearLayout card = U.col(c);
        card.setBackground(U.round(U.CARD, 22));
        U.pad(card, 26, 22, 26, 20);
        card.setLayoutParams(Sheets.centered(570, 360));

        LinearLayout body = U.row(c);
        body.setGravity(Gravity.TOP);
        card.addView(body, U.weight(1, false));

        // ---- month grid
        LinearLayout left = U.col(c);
        body.addView(left, U.lpu(290, U.MATCH));

        android.icu.util.Calendar cal = Cal.at(primary, System.currentTimeMillis());
        cal.add(android.icu.util.Calendar.MONTH, month[0]);
        cal.set(android.icu.util.Calendar.DAY_OF_MONTH, 1);
        cal.set(android.icu.util.Calendar.HOUR_OF_DAY, 12);
        long adj = primary.startsWith("islamic") ? Store.num(Store.CAL_ADJ, 0) * 86400000L : 0;
        long firstMs = cal.getTimeInMillis() - adj;

        LinearLayout head = U.row(c);
        View prev = U.icon(c, R.drawable.ic_back, U.SUB);
        prev.setOnClickListener(v -> {
            month[0]--;
            Sheets.refresh();
        });
        head.addView(prev, U.lpu(20, 20));
        TextView title = U.line(c, Cal.format(primary, firstMs, "MMMM y"), 17, U.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, U.WRAP, 1));
        View next = U.icon(c, R.drawable.ic_fwd, U.SUB);
        next.setOnClickListener(v -> {
            month[0]++;
            Sheets.refresh();
        });
        head.addView(next, U.lpu(20, 20));
        left.addView(head, U.lp(U.MATCH, U.WRAP));

        if (Cal.hasAlt()) {
            LinearLayout sw = U.row(c);
            sw.setGravity(Gravity.CENTER);
            sw.addView(small(c, "Gregorian", !altView, () -> {
                Store.put(Store.CAL_VIEW_ALT, false);
                month[0] = 0;
                Sheets.refresh();
            }));
            sw.addView(U.gap(c, 6, 1));
            sw.addView(small(c, Cal.shortLabel(), altView, () -> {
                Store.put(Store.CAL_VIEW_ALT, true);
                month[0] = 0;
                Sheets.refresh();
            }));
            left.addView(sw, top(8));
        }

        boolean sun = Store.flag(Store.CAL_WEEK_SUN, false);
        LinearLayout dow = U.row(c);
        String[] names = sun ? new String[]{"S", "M", "T", "W", "T", "F", "S"} : new String[]{"M", "T", "W", "T", "F", "S", "S"};
        for (String n : names) {
            TextView t = U.text(c, n, 11, U.MUTED, true);
            t.setGravity(Gravity.CENTER);
            dow.addView(t, new LinearLayout.LayoutParams(0, U.WRAP, 1));
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
            LinearLayout row = U.row(c);
            boolean any = false;
            for (int d = 0; d < 7; d++) {
                long ms = g.getTimeInMillis();
                android.icu.util.Calendar pc = Cal.at(primary, ms);
                boolean inMonth = pc.get(android.icu.util.Calendar.MONTH) == curMonth;
                if (inMonth) any = true;
                boolean isToday = g.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
                        && g.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR);
                LinearLayout cell = U.col(c);
                cell.setGravity(Gravity.CENTER);
                if (isToday) cell.setBackground(U.round(U.ACCENT, 9));
                TextView n = U.text(c, String.valueOf(pc.get(android.icu.util.Calendar.DAY_OF_MONTH)), 13, inMonth ? U.TEXT : U.MUTED, isToday);
                n.setGravity(Gravity.CENTER);
                cell.addView(n);
                if (secondary != null) {
                    android.icu.util.Calendar sc = Cal.at(secondary, ms);
                    TextView s = U.text(c, String.valueOf(sc.get(android.icu.util.Calendar.DAY_OF_MONTH)), 8, isToday ? 0xCCFFFFFF : inMonth ? U.TEAL : 0xFF3A4452, false);
                    s.setGravity(Gravity.CENTER);
                    cell.addView(s);
                }
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, U.u(secondary != null ? 34 : 28), 1);
                cl.setMargins(U.u(2), U.u(1), U.u(2), U.u(1));
                row.addView(cell, cl);
                g.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }
            if (!any) break;
            left.addView(row, U.lp(U.MATCH, U.WRAP));
        }

        // ---- upcoming
        View div = new View(c);
        div.setBackgroundColor(U.LINE);
        LinearLayout.LayoutParams dl = U.lpu(1, U.MATCH);
        dl.setMargins(U.u(22), 0, U.u(22), 0);
        body.addView(div, dl);
        LinearLayout right = U.col(c);
        body.addView(right, new LinearLayout.LayoutParams(0, U.MATCH, 1));
        LinearLayout rh = U.row(c);
        rh.addView(U.line(c, "Upcoming", 15, U.TEXT, true), new LinearLayout.LayoutParams(0, U.WRAP, 1));
        View x = U.icon(c, R.drawable.ic_x, U.SUB);
        x.setOnClickListener(v -> Sheets.close());
        rh.addView(x, U.lpu(22, 22));
        right.addView(rh);
        if (Cal.hasAlt()) {
            TextView today2 = U.line(c, Cal.format(Cal.alt(), System.currentTimeMillis(), "d MMMM y"), 11, U.TEAL, false);
            right.addView(today2, top(3));
        }
        List<Cal.Event> up = Cal.upcoming(c, 6);
        LinearLayout list = U.col(c);
        right.addView(list, top(10));
        if (up.isEmpty()) list.addView(U.text(c, Cal.canReadCalendar(c) ? "Nothing coming up" : "Allow calendar access to see your events", 12, U.SUB, false));
        for (Cal.Event e : up) {
            LinearLayout r = U.row(c);
            r.setGravity(Gravity.TOP);
            View dot = new View(c);
            dot.setBackground(U.oval(e.special ? U.GREEN : U.ACCENT));
            LinearLayout.LayoutParams dotl = U.lpu(7, 7);
            dotl.topMargin = U.u(5);
            r.addView(dot, dotl);
            LinearLayout t = U.col(c);
            t.addView(U.line(c, e.title, 13, U.TEXT2, false));
            String when = Cal.dayLabel(e.at) + (e.allDay ? "" : " · " + clock(hours(e.at)));
            t.addView(U.line(c, when, 11, U.SUB, false), top(2));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            tl.leftMargin = U.u(9);
            r.addView(t, tl);
            LinearLayout.LayoutParams rl = U.lp(U.MATCH, U.WRAP);
            rl.bottomMargin = U.u(9);
            list.addView(r, rl);
        }
        right.addView(new View(c), U.weight(1, false));
        TextView open = U.text(c, "Open calendar ›", 13, U.ACCENT_SOFT, true);
        open.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                Sheets.close();
            } catch (Exception e) {
                Apps.launchPkg(c, "com.google.android.calendar");
            }
        });
        right.addView(open);
        return card;
    }

    static double hours(long ms) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(ms);
        return k.get(Calendar.HOUR_OF_DAY) + k.get(Calendar.MINUTE) / 60.0;
    }

    private static TextView small(Context c, String s, boolean on, Runnable click) {
        TextView t = U.text(c, s, 12, on ? U.TEXT : U.SUB, on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(U.round(on ? U.ACCENT : U.CARD2, 12));
        U.pad(t, 12, 5, 12, 5);
        t.setOnClickListener(v -> click.run());
        return t;
    }

    // ------------------------------------------------------------------ weather

    private static int weatherTab;

    static void weather(int tab) {
        weatherTab = tab;
        Sheets.show(InfoPanels::weatherPage);
        Weather.maybeFetch();
    }

    private static View weatherPage() {
        Context c = Sheets.ctx();
        LinearLayout b = U.col(c);
        LinearLayout tabs = U.row(c);
        String[] names = {"Today", "3 days", "7 days"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            tabs.addView(U.seg(c, names[i], weatherTab == i, () -> {
                weatherTab = k;
                Sheets.refresh();
            }));
            tabs.addView(U.gap(c, 8, 1));
        }
        b.addView(tabs);
        if (!Weather.ok) {
            b.addView(U.text(c, Loc.has() ? "Loading the forecast…" : "Waiting for a location to get the forecast.", 14, U.SUB, false), top(24));
            return Sheets.panel(Loc.town != null ? "Weather · " + Loc.town : "Weather", b, 660, 440);
        }
        if (weatherTab == 0) today(c, b);
        else if (weatherTab == 1) threeDays(c, b);
        else sevenDays(c, b);
        return Sheets.panel(Loc.town != null ? "Weather · " + Loc.town : "Weather", b, 660, 440);
    }

    private static void today(Context c, LinearLayout b) {
        LinearLayout r = U.row(c);
        r.addView(U.icon(c, Weather.icon(Weather.code, Weather.isDay), 0), U.lpu(64, 64));
        LinearLayout t = U.col(c);
        t.addView(U.text(c, Weather.t(Weather.temp), 40, U.TEXT, true));
        t.addView(U.text(c, Weather.describe(Weather.code), 14, U.TEXT2, false), top(2));
        LinearLayout.LayoutParams tl = U.lp(U.WRAP, U.WRAP);
        tl.leftMargin = U.u(14);
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
        LinearLayout grid = U.col(c);
        for (int row = 0; row < 3; row++) {
            LinearLayout gr = U.row(c);
            for (int col = 0; col < 2; col++) {
                String[] f = facts[row * 2 + col];
                LinearLayout cell = U.col(c);
                cell.addView(U.line(c, f[0], 11, U.SUB, false));
                cell.addView(U.line(c, f[1], 14, U.TEXT, true), top(2));
                gr.addView(cell, U.lpu(130, U.WRAP));
            }
            LinearLayout.LayoutParams gl = U.lp(U.WRAP, U.WRAP);
            if (row > 0) gl.topMargin = U.u(8);
            grid.addView(gr, gl);
        }
        r.addView(grid);
        b.addView(r, top(18));

        LinearLayout hours = U.row(c);
        for (int i = 0; i < Math.min(8, Weather.hours.size()); i++) {
            double[] h = Weather.hours.get(i);
            LinearLayout cell = U.col(c);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setBackground(U.round(i == 0 ? U.CARD3 : U.CARD2, 12));
            U.pad(cell, 0, 10, 0, 10);
            cell.addView(U.text(c, i == 0 ? "Now" : clockShort((int) h[0]), 11, U.SUB, false));
            LinearLayout.LayoutParams il = U.lpu(28, 28);
            il.topMargin = U.u(6);
            cell.addView(U.icon(c, Weather.icon((int) h[2], h[4] > 0), 0), il);
            TextView tt = U.text(c, Weather.t(h[1]), 14, U.TEXT, true);
            cell.addView(tt, top(6));
            TextView rp = U.text(c, (int) h[3] + "%", 10, (int) h[3] >= 40 ? U.ACCENT_SOFT : U.MUTED, false);
            cell.addView(rp, top(3));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            if (i > 0) cl.leftMargin = U.u(6);
            hours.addView(cell, cl);
        }
        b.addView(hours, top(22));
    }

    static String clockShort(int hour) {
        if (Store.flag(Store.H24, true)) return U.two(hour) + ":00";
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
        LinearLayout r = U.row(c);
        for (int i = 0; i < Math.min(3, Weather.days.size()); i++) {
            Weather.Day d = Weather.days.get(i);
            LinearLayout card = U.col(c);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackground(U.round(U.CARD2, 16));
            U.pad(card, 14, 16, 14, 16);
            card.addView(U.text(c, dayName(d.date, i), 15, U.TEXT, true));
            LinearLayout.LayoutParams il = U.lpu(56, 56);
            il.topMargin = U.u(12);
            card.addView(U.icon(c, Weather.icon(d.code, true), 0), il);
            card.addView(U.text(c, Weather.describe(d.code), 13, U.TEXT2, false), top(8));
            LinearLayout hl = U.row(c);
            hl.addView(U.text(c, Weather.t(d.hi), 22, U.TEXT, true));
            hl.addView(U.text(c, "  " + Weather.t(d.lo), 16, U.SUB, false));
            card.addView(hl, top(10));
            card.addView(U.text(c, "Rain " + d.rainProb + "%" + (d.rainMm >= 0.5 ? " · " + Math.round(d.rainMm) + " mm" : ""), 12, U.SUB, false), top(10));
            card.addView(U.text(c, "Wind " + Math.round(d.windKmh) + " km/h " + Weather.compass(d.windDir), 12, U.SUB, false), top(4));
            card.addView(U.text(c, "Sun " + d.sunrise + " – " + d.sunset, 12, U.SUB, false), top(4));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            if (i > 0) cl.leftMargin = U.u(10);
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
        LinearLayout list = U.col(c);
        for (int i = 0; i < Weather.days.size(); i++) {
            Weather.Day d = Weather.days.get(i);
            LinearLayout r = U.row(c);
            r.addView(U.line(c, dayName(d.date, i), 14, U.TEXT, i == 0), U.lpu(100, U.WRAP));
            r.addView(U.icon(c, Weather.icon(d.code, true), 0), U.lpu(26, 26));
            TextView rp = U.text(c, d.rainProb >= 20 ? d.rainProb + "%" : "", 11, U.ACCENT_SOFT, false);
            r.addView(rp, U.lpu(44, U.WRAP));
            rp.setGravity(Gravity.CENTER);
            TextView lo = U.text(c, Weather.t(d.lo), 13, U.SUB, false);
            lo.setGravity(Gravity.END);
            r.addView(lo, U.lpu(40, U.WRAP));
            Bar bar = new Bar(c, (float) ((d.lo - min) / Math.max(1, max - min)), (float) ((d.hi - min) / Math.max(1, max - min)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, U.u(6), 1);
            bl.setMargins(U.u(12), 0, U.u(12), 0);
            r.addView(bar, bl);
            r.addView(U.text(c, Weather.t(d.hi), 13, U.TEXT, true), U.lpu(40, U.WRAP));
            LinearLayout.LayoutParams rl = U.lpu(U.MATCH, 40);
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
            p.setColor(U.CARD3);
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, h / 2, h / 2, p);
            p.setShader(new android.graphics.LinearGradient(0, 0, w, 0, U.TEAL, U.AMBER, android.graphics.Shader.TileMode.CLAMP));
            r.set(w * from, 0, Math.max(w * from + h, w * to), h);
            cv.drawRoundRect(r, h / 2, h / 2, p);
            p.setShader(null);
        }
    }

    // ------------------------------------------------------------------ status quick cards

    static void quick(String id) {
        Sheets.showCard(() -> quickCard(id));
    }

    private static View quickCard(String id) {
        Context c = Sheets.ctx();
        LinearLayout card = U.col(c);
        card.setBackground(U.round(U.CARD, 20));
        U.pad(card, 22, 18, 22, 18);
        card.setLayoutParams(Sheets.anchored(320, -2, 88, 100));
        String title;
        LinearLayout b = U.col(c);
        switch (id) {
            case "cell":
                title = "Mobile data";
                fact(c, b, "Status", !Status.sim ? "No SIM card" : Status.dataOn ? "Connected" : "Not connected");
                if (Status.sim) {
                    fact(c, b, "Network", (Status.operator == null || Status.operator.isEmpty() ? "—" : Status.operator) + (Status.netType != null ? " · " + Status.netType : ""));
                    fact(c, b, "Signal", Status.cellLevel + " of 4");
                }
                b.addView(U.button(c, "Mobile data settings", true, () -> {
                    Status.dataPanel(c);
                    Sheets.close();
                }), top(14));
                break;
            case "wifi":
                title = "Wi-Fi";
                fact(c, b, "Status", !Status.wifiOn ? "Off" : Status.wifiConnected ? "Connected" : "Searching");
                if (Status.wifiConnected) {
                    fact(c, b, "Network", Status.ssid == null ? "—" : Status.ssid);
                    fact(c, b, "Signal", Status.wifiLevel + " of 4");
                }
                LinearLayout wb = U.row(c);
                wb.addView(U.button(c, "Wi-Fi panel", true, () -> {
                    Status.wifiPanel(c);
                    Sheets.close();
                }));
                if (Store.str(Store.CONN_PHONE_SSID, null) != null) {
                    wb.addView(U.gap(c, 8, 1));
                    wb.addView(U.button(c, "Join phone", false, () -> {
                        Actions.joinPhoneHotspot(c);
                        Sheets.close();
                    }));
                }
                b.addView(wb, top(14));
                break;
            case "gps":
                title = "GPS";
                boolean fix = Loc.fresh();
                fact(c, b, "Status", !Loc.hasPermission() ? "No permission" : !Loc.gpsEnabled() ? "Location is off" : fix ? "Fix" : "Searching");
                fact(c, b, "Satellites", (Loc.satsUsed < 0 ? "—" : Loc.satsUsed + " used") + (Loc.satsVisible > 0 ? " of " + Loc.satsVisible + " in view" : ""));
                if (Loc.has()) fact(c, b, "Position", String.format(Locale.US, "%.5f, %.5f", Loc.lat, Loc.lon));
                if (Loc.town != null) fact(c, b, "Near", Loc.town);
                b.addView(U.button(c, "Location settings", true, () -> {
                    Status.open(c, Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    Sheets.close();
                }), top(14));
                break;
            case "hotspot":
                title = "Hotspot";
                fact(c, b, "Status", Status.hotspotOn ? "On" : "Off");
                b.addView(U.text(c, "Android 13 only lets the hotspot be switched in Settings.", 12, U.SUB, false), top(6));
                b.addView(U.button(c, "Hotspot settings", true, () -> {
                    Status.tetherSettings(c);
                    Sheets.close();
                }), top(14));
                break;
            default:
                title = "Tailscale";
                if (!Status.tailscaleInstalled()) {
                    b.addView(U.text(c, "Tailscale isn't installed on this box.", 13, U.SUB, false));
                    b.addView(U.button(c, "Get Tailscale", true, () -> {
                        try {
                            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Status.TS_PKG)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Exception ignored) {
                        }
                        Sheets.close();
                    }), top(14));
                } else {
                    b.addView(Sheets.switchRow(R.drawable.ic_vpnkey, Status.vpnOn ? U.GREEN : U.SUB, Status.vpnOn ? "Connected" : "Not connected",
                            "Home Assistant and your other devices", Status.vpnOn, on -> {
                                Status.tailscale(on);
                                U.ui.postDelayed(Sheets::refresh, 2500);
                            }));
                    b.addView(U.button(c, "Open Tailscale", false, () -> {
                        Apps.launchPkg(c, Status.TS_PKG);
                        Sheets.close();
                    }), top(10));
                }
        }
        LinearLayout head = U.row(c);
        head.addView(U.line(c, title, 17, U.TEXT, true), new LinearLayout.LayoutParams(0, U.WRAP, 1));
        View x = U.icon(c, R.drawable.ic_x, U.SUB);
        x.setOnClickListener(v -> Sheets.close());
        head.addView(x, U.lpu(22, 22));
        card.addView(head);
        card.addView(b, top(10));
        return card;
    }

    private static void fact(Context c, LinearLayout b, String k, String v) {
        LinearLayout r = U.row(c);
        r.addView(U.line(c, k, 13, U.SUB, false), U.lpu(96, U.WRAP));
        r.addView(U.line(c, v, 13, U.TEXT, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
        LinearLayout.LayoutParams lp = U.lp(U.MATCH, U.WRAP);
        lp.topMargin = U.u(6);
        b.addView(r, lp);
    }

    // ------------------------------------------------------------------ prayer

    static void prayer() {
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.col(c);
            double[] today = Prayer.times(0), tom = Prayer.times(1);
            if (today == null) {
                b.addView(U.text(c, "Prayer times need a GPS location.", 14, U.SUB, false));
                return Sheets.panel("Prayer times", b, 520, 300);
            }
            double[] n = Prayer.next();
            LinearLayout head = U.row(c);
            head.addView(U.text(c, "", 13, U.SUB, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
            head.addView(colHead(c, "Today"));
            head.addView(colHead(c, "Tomorrow"));
            b.addView(head);
            for (int i = 0; i < 6; i++) {
                boolean next0 = n != null && (int) n[0] == i && n[1] == 0, next1 = n != null && (int) n[0] == i && n[1] == 1;
                LinearLayout r = U.row(c);
                U.pad(r, 10, 0, 0, 0);
                r.setMinimumHeight(U.u(34));
                r.addView(U.line(c, Prayer.NAMES[i], 15, i == 1 ? U.SUB : U.TEXT, false), new LinearLayout.LayoutParams(0, U.WRAP, 1));
                r.addView(cell(c, clock(today[i]), next0));
                r.addView(cell(c, clock(tom[i]), next1));
                b.addView(r);
            }
            String asr = Store.num(Store.PRAY_ASR, 2) == 2 ? "Hanafi" : "Shafi'i";
            TextView m = U.text(c, Prayer.METHODS[Store.num(Store.PRAY_METHOD, 0)] + " · Asr " + asr + (Loc.town != null ? " · " + Loc.town : ""), 12, U.SUB, false);
            b.addView(m, top(12));
            b.addView(Sheets.item("Prayer time settings", null, SettingsPanels::prayerTimes), top(4));
            return Sheets.panel("Prayer times", b, 560, 420);
        });
    }

    private static TextView colHead(Context c, String s) {
        TextView t = U.text(c, s, 12, U.SUB, true);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(U.lpu(110, U.WRAP));
        return t;
    }

    private static TextView cell(Context c, String s, boolean hi) {
        TextView t = U.text(c, s, 15, hi ? U.TEXT : U.TEXT2, hi);
        t.setGravity(Gravity.CENTER);
        if (hi) t.setBackground(U.round(U.DGREEN, 8));
        U.pad(t, 0, 5, 0, 5);
        LinearLayout.LayoutParams lp = U.lpu(100, U.WRAP);
        lp.leftMargin = U.u(10);
        t.setLayoutParams(lp);
        return t;
    }

    // ------------------------------------------------------------------ qibla

    static void qibla() {
        Sheets.show(() -> {
            Context c = Sheets.ctx();
            LinearLayout b = U.row(c);
            Widgets.Compass comp = new Widgets.Compass(c);
            comp.arrow = false;
            comp.markerIcon = c.getDrawable(R.drawable.ic_kaaba);
            if (Loc.has()) comp.marker = (float) Prayer.qibla();
            b.addView(comp, U.lpu(230, 230));
            LinearLayout t = U.col(c);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, U.WRAP, 1);
            tl.leftMargin = U.u(28);
            if (Loc.has()) {
                t.addView(U.text(c, Math.round(Prayer.qibla()) + "°", 44, U.TEXT, true));
                t.addView(U.text(c, "from North (" + Weather.compass((int) Math.round(Prayer.qibla())) + ")", 14, U.SUB, false), top(4));
                t.addView(U.text(c, String.format(Locale.US, "%,d km to the Kaaba", Math.round(Prayer.kmToKaaba())), 14, U.TEXT2, false), top(18));
                t.addView(U.text(c, "Most car boxes have no compass, so while parked this shows the direction from North. While driving, the tile turns with your GPS heading.", 12, U.SUB, false), top(18));
            } else t.addView(U.text(c, "Waiting for a GPS location.", 14, U.SUB, false));
            b.addView(t, tl);
            return Sheets.panel("Qibla", b, 600, 360);
        });
    }
}
