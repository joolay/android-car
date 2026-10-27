package com.carbox.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.text.SimpleDateFormat;
import android.icu.util.ULocale;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Second calendars (Hijri, Hebrew, …), special days, and Google Calendar events. */
final class Cal {
    private Cal() {
    }

    /** id, label, ICU calendar keyword */
    static final String[][] ALTS = {
            {"islamic-umalqura", "Islamic (Hijri)"}, {"hebrew", "Hebrew"}, {"chinese", "Chinese lunar"},
            {"indian", "Indian (Saka)"}, {"persian", "Persian"}, {"ethiopic", "Ethiopian"}, {"buddhist", "Buddhist"}, {"none", "None"}};

    static String alt() {
        String a = Store.str(Store.CAL_ALT, "islamic-umalqura");
        return Faith.calendarAllowed(a) ? a : "none"; // Hijri / Hebrew only with that faith add-on
    }

    static String altLabel() {
        for (String[] a : ALTS) if (a[0].equals(alt())) return a[1];
        return "None";
    }

    static String shortLabel() {
        String a = alt();
        if (a.startsWith("islamic")) return "Hijri";
        if ("hebrew".equals(a)) return "Hebrew";
        if ("chinese".equals(a)) return "Lunar";
        if ("indian".equals(a)) return "Saka";
        if ("persian".equals(a)) return "Persian";
        if ("ethiopic".equals(a)) return "Ethiopian";
        if ("buddhist".equals(a)) return "Buddhist";
        return "";
    }

    static boolean hasAlt() {
        return !"none".equals(alt());
    }

    static ULocale locale(String cal) {
        return new ULocale("en@calendar=" + cal);
    }

    /** An ICU calendar of the given type set to a day (ms), applying the Hijri adjustment. */
    static android.icu.util.Calendar at(String cal, long ms) {
        android.icu.util.Calendar c = android.icu.util.Calendar.getInstance(locale(cal));
        long adj = cal.startsWith("islamic") ? Store.num(Store.CAL_ADJ, 0) * 86400000L : 0;
        c.setTimeInMillis(ms + adj);
        return c;
    }

    static String format(String cal, long ms, String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, locale(cal));
        long adj = cal.startsWith("islamic") ? Store.num(Store.CAL_ADJ, 0) * 86400000L : 0;
        return f.format(new Date(ms + adj));
    }

    // ------------------------------------------------------------------ upcoming

    static final class Event implements Comparable<Event> {
        long at;
        String title;
        boolean special; // Islamic / Jewish / Chinese day rather than a Google event
        boolean allDay;

        @Override
        public int compareTo(Event o) {
            return Long.compare(at, o.at);
        }
    }

    // month index (0-based ICU), day, name
    private static final Object[][] ISLAMIC = {
            {0, 1, "Islamic New Year"}, {0, 10, "Day of Ashura"}, {2, 12, "Mawlid an-Nabi"}, {6, 27, "Isra and Mi'raj"},
            {7, 15, "Laylat al-Bara'ah"}, {8, 1, "First day of Ramadan"}, {8, 27, "Laylat al-Qadr"}, {9, 1, "Eid al-Fitr"},
            {11, 9, "Day of Arafah"}, {11, 10, "Eid al-Adha"}};
    // Hebrew: TISHRI=0 … ADAR_1=5 ADAR=6 NISAN=7 IYAR=8 SIVAN=9 AV=11
    private static final Object[][] JEWISH = {
            {0, 1, "Rosh Hashanah"}, {0, 10, "Yom Kippur"}, {0, 15, "Sukkot"}, {2, 25, "Hanukkah"}, {6, 14, "Purim"},
            {7, 15, "Passover"}, {9, 6, "Shavuot"}, {11, 9, "Tisha B'Av"}};
    private static final Object[][] CHINESE = {
            {0, 1, "Lunar New Year"}, {0, 15, "Lantern Festival"}, {4, 5, "Dragon Boat Festival"}, {7, 15, "Mid-Autumn Festival"}};

    static List<String> specials() {
        List<String> l = Store.list(Store.CAL_SPECIAL);
        if (!Store.has(Store.CAL_SPECIAL)) {
            l = new ArrayList<>();
            l.add("islamic");
        }
        return l;
    }

    /** Special days in the next `days` days. */
    static List<Event> specialDays(int days) {
        List<Event> out = new ArrayList<>();
        List<String> want = specials();
        java.util.Calendar g = java.util.Calendar.getInstance();
        g.set(java.util.Calendar.HOUR_OF_DAY, 12);
        g.set(java.util.Calendar.MINUTE, 0);
        for (int i = 0; i < days; i++) {
            long ms = g.getTimeInMillis();
            if (want.contains("islamic")) match(out, ms, "islamic-umalqura", ISLAMIC);
            if (want.contains("jewish")) match(out, ms, "hebrew", JEWISH);
            if (want.contains("chinese")) match(out, ms, "chinese", CHINESE);
            g.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }

    private static void match(List<Event> out, long ms, String cal, Object[][] table) {
        android.icu.util.Calendar c = at(cal, ms);
        int m = c.get(android.icu.util.Calendar.MONTH), d = c.get(android.icu.util.Calendar.DAY_OF_MONTH);
        if ("chinese".equals(cal) && c.get(android.icu.util.Calendar.IS_LEAP_MONTH) == 1) return;
        for (Object[] row : table) {
            if ((Integer) row[0] == m && (Integer) row[1] == d) {
                Event e = new Event();
                e.at = ms;
                e.title = (String) row[2];
                e.special = true;
                e.allDay = true;
                out.add(e);
            }
        }
        // Purim falls in Adar II in leap years: also accept Adar I? No — only ADAR (6) which ICU uses for Adar II in leap years.
    }

    static boolean canReadCalendar(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /** Google (and other synced) calendar events in the next `days` days. */
    static List<Event> events(Context c, int days) {
        List<Event> out = new ArrayList<>();
        if (!canReadCalendar(c)) return out;
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 3600000L);
        ContentUris.appendId(b, now + days * 86400000L);
        List<String> ids = Store.list(Store.CAL_IDS);
        try (Cursor cur = c.getContentResolver().query(b.build(), new String[]{CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.END},
                CalendarContract.Instances.VISIBLE + "=1", null, CalendarContract.Instances.BEGIN + " ASC")) {
            while (cur != null && cur.moveToNext() && out.size() < 40) {
                if (!ids.isEmpty() && !ids.contains(String.valueOf(cur.getLong(3)))) continue;
                if (cur.getLong(4) < now) continue;
                Event e = new Event();
                e.at = cur.getLong(0);
                e.title = cur.getString(1) == null ? "(No title)" : cur.getString(1);
                e.allDay = cur.getInt(2) == 1;
                if (e.allDay) {
                    // all-day events are stored in UTC midnight
                    e.at = e.at - java.util.TimeZone.getDefault().getOffset(e.at) + 12 * 3600000L;
                }
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static List<Event> upcoming(Context c, int max) {
        List<Event> l = events(c, 60);
        l.addAll(specialDays(120));
        Collections.sort(l);
        return l.size() > max ? new ArrayList<>(l.subList(0, max)) : l;
    }

    /** Calendars on the box: {id, name, account}. */
    static List<String[]> calendars(Context c) {
        List<String[]> out = new ArrayList<>();
        if (!canReadCalendar(c)) return out;
        try (Cursor cur = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME},
                null, null, null)) {
            while (cur != null && cur.moveToNext())
                out.add(new String[]{String.valueOf(cur.getLong(0)), cur.getString(1), cur.getString(2)});
        } catch (Exception ignored) {
        }
        return out;
    }

    static String dayLabel(long ms) {
        java.util.Calendar t = java.util.Calendar.getInstance();
        java.util.Calendar e = java.util.Calendar.getInstance();
        e.setTimeInMillis(ms);
        if (t.get(java.util.Calendar.YEAR) == e.get(java.util.Calendar.YEAR) && t.get(java.util.Calendar.DAY_OF_YEAR) == e.get(java.util.Calendar.DAY_OF_YEAR))
            return "Today";
        t.add(java.util.Calendar.DAY_OF_YEAR, 1);
        if (t.get(java.util.Calendar.YEAR) == e.get(java.util.Calendar.YEAR) && t.get(java.util.Calendar.DAY_OF_YEAR) == e.get(java.util.Calendar.DAY_OF_YEAR))
            return "Tomorrow";
        return new java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(new Date(ms));
    }
}
