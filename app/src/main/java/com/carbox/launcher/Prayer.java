package com.carbox.launcher;

import org.json.JSONArray;

import java.util.Calendar;
import java.util.TimeZone;

/** Prayer times worked out on the box (no internet), plus Qibla direction. */
final class Prayer {
    static final String[] NAMES = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    static final String[] METHODS = {"Karachi", "Muslim World League", "ISNA", "Egypt", "Umm al-Qura"};
    private static final double[][] ANGLES = {{18, 18}, {18, 17}, {15, 15}, {19.5, 17.5}, {18.5, -90}}; // -90 = 90 min after maghrib

    private Prayer() {
    }

    /** Six times in local hours for the given day offset from today (0 = today). */
    static double[] times(int dayOffset) {
        if (!Loc.has()) return null;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, dayOffset);
        double tz = TimeZone.getDefault().getOffset(c.getTimeInMillis()) / 3600000.0;
        int method = Store.num(Store.PRAY_METHOD, 0);
        int asrF = Store.num(Store.PRAY_ASR, 2);
        double[] t = compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), Loc.lat, Loc.lon, tz,
                ANGLES[method][0], ANGLES[method][1], asrF);
        JSONArray adj = Store.arr(Store.PRAY_ADJ);
        for (int i = 0; i < 6; i++) t[i] += adj.optInt(i, 0) / 60.0;
        return t;
    }

    static double[] compute(int y, int m, int d, double lat, double lon, double tz, double fajrA, double ishaA, int asrFactor) {
        double jd = julian(y, m, d) - lon / (15 * 24);
        double[] sp = sun(jd + 0.5);
        double decl = sp[0], eqt = sp[1];
        double noon = 12 + tz - lon / 15 - eqt;
        double fajr = noon - hourAngle(fajrA, lat, decl);
        double sunrise = noon - hourAngle(0.833, lat, decl);
        double dhuhr = noon + 1 / 60.0;
        double asrAngle = -Math.toDegrees(Math.atan(1 / (asrFactor + Math.tan(Math.abs(Math.toRadians(lat) - decl)))));
        double asr = noon + hourAngle(asrAngle, lat, decl);
        double maghrib = noon + hourAngle(0.833, lat, decl);
        double isha = ishaA < 0 ? maghrib + 1.5 : noon + hourAngle(ishaA, lat, decl);
        return new double[]{fajr, sunrise, dhuhr, asr, maghrib, isha};
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    private static double[] sun(double jd) {
        double D = jd - 2451545.0;
        double g = Math.toRadians((357.529 + 0.98560028 * D) % 360);
        double q = (280.459 + 0.98564736 * D) % 360;
        double L = Math.toRadians((q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g)) % 360);
        double e = Math.toRadians(23.439 - 0.00000036 * D);
        double ra = Math.toDegrees(Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L))) / 15;
        double decl = Math.asin(Math.sin(e) * Math.sin(L));
        double eqt = q / 15 - ((ra % 24) + 24) % 24;
        if (eqt > 12) eqt -= 24;
        if (eqt < -12) eqt += 24;
        return new double[]{decl, eqt};
    }

    private static double hourAngle(double angle, double lat, double decl) {
        double p = Math.toRadians(lat);
        double v = (-Math.sin(Math.toRadians(angle)) - Math.sin(p) * Math.sin(decl)) / (Math.cos(p) * Math.cos(decl));
        if (v > 1 || v < -1) return Double.NaN;
        return Math.toDegrees(Math.acos(v)) / 15;
    }

    /** The next of the five prayers: {index into NAMES, dayOffset, hours}. */
    static double[] next() {
        double[] today = times(0);
        if (today == null) return null;
        Calendar c = Calendar.getInstance();
        double now = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0 + c.get(Calendar.SECOND) / 3600.0;
        int[] five = {0, 2, 3, 4, 5};
        for (int i : five) if (today[i] > now) return new double[]{i, 0, today[i]};
        double[] tom = times(1);
        return new double[]{0, 1, tom[0]};
    }

    static long msUntil(double[] next) {
        Calendar c = Calendar.getInstance();
        double now = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0;
        double h = next[2] + next[1] * 24 - now;
        return (long) (h * 3600000);
    }

    // ------------------------------------------------------------------ qibla

    static final double KAABA_LAT = 21.4225, KAABA_LON = 39.8262;

    static double qibla() {
        double la1 = Math.toRadians(Loc.lat), la2 = Math.toRadians(KAABA_LAT), dl = Math.toRadians(KAABA_LON - Loc.lon);
        double y = Math.sin(dl) * Math.cos(la2);
        double x = Math.cos(la1) * Math.sin(la2) - Math.sin(la1) * Math.cos(la2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    static double kmToKaaba() {
        double la1 = Math.toRadians(Loc.lat), la2 = Math.toRadians(KAABA_LAT);
        double dla = la2 - la1, dlo = Math.toRadians(KAABA_LON - Loc.lon);
        double a = Math.sin(dla / 2) * Math.sin(dla / 2) + Math.cos(la1) * Math.cos(la2) * Math.sin(dlo / 2) * Math.sin(dlo / 2);
        return 2 * 6371 * Math.asin(Math.sqrt(a));
    }
}
