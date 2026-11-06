package com.carbox.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.TrafficStats;
import android.os.SystemClock;
import android.view.View;

/**
 * Rail Bar data meter: this month's mobile (LTE) data, down and up, counting up smoothly. Tap it for
 * the full breakdown (LTE, hotspot, Wi-Fi). Also keeps the live speeds for the small speed bars beside
 * the signal, Wi-Fi and hotspot icons ({@link SpeedBar}).
 *
 * The box counts all data and mobile data; what isn't mobile went over Wi-Fi, or to hotspot devices
 * when the hotspot is on and Wi-Fi isn't connected (a close estimate when both are on).
 */
final class DataMeter extends View {

    // ------------------------------------------------------------------ counting

    static final int LTE = 0, WIFI = 1, HOTSPOT = 2;
    /** Bytes per second right now, per connection: {down, up}. */
    static final long[][] speed = new long[3][2];
    /** This month, per connection: {down, up}. */
    private static final long[][] month = new long[3][2];
    private static boolean loaded;
    private static long seenAll = -1, seenAllTx, seenMob, seenMobTx, lastAt, savedAt;

    private static String monthKey() {
        return new java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(new java.util.Date());
    }

    private static final String[] NAMES = {"lte", "wifi", "hot"};

    private static void load(long boot) {
        loaded = true;
        for (int k = 0; k < 3; k++) {
            month[k][0] = Prefs.lng("du_" + NAMES[k] + "_d", 0);
            month[k][1] = Prefs.lng("du_" + NAMES[k] + "_u", 0);
        }
        if (Math.abs(Prefs.lng("du_boot", 0) - boot) < 60000) { // same start: carry on from the last look
            seenAll = Prefs.lng("du_seen_rx", 0);
            seenAllTx = Prefs.lng("du_seen_tx", 0);
            seenMob = Prefs.lng("du_seen_mrx", 0);
            seenMobTx = Prefs.lng("du_seen_mtx", 0);
        } else { // after a restart the box's counters began again at 0
            seenAll = seenAllTx = seenMob = seenMobTx = 0;
        }
    }

    private static void save(long boot) {
        for (int k = 0; k < 3; k++) {
            Prefs.put("du_" + NAMES[k] + "_d", month[k][0]);
            Prefs.put("du_" + NAMES[k] + "_u", month[k][1]);
        }
        Prefs.put("du_seen_rx", seenAll);
        Prefs.put("du_seen_tx", seenAllTx);
        Prefs.put("du_seen_mrx", seenMob);
        Prefs.put("du_seen_mtx", seenMobTx);
        Prefs.put("du_boot", boot);
    }

    /** Read the counters; at most about once a second whoever asks. */
    static void sample() {
        long now = SystemClock.elapsedRealtime();
        if (loaded && now - lastAt < 900) return;
        long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes();
        long mrx = Math.max(0, TrafficStats.getMobileRxBytes()), mtx = Math.max(0, TrafficStats.getMobileTxBytes());
        if (rx < 0 || tx < 0) return; // not supported on this box
        long boot = System.currentTimeMillis() - now;
        if (!loaded) load(boot);
        if (!monthKey().equals(Prefs.str("du_month", ""))) { // a new month: start again
            Prefs.put("du_month", monthKey());
            for (long[] m : month) m[0] = m[1] = 0;
        }
        if (rx < seenAll || tx < seenAllTx || mrx < seenMob || mtx < seenMobTx) seenAll = seenAllTx = seenMob = seenMobTx = 0;
        long dAll = rx - seenAll, uAll = tx - seenAllTx, dMob = mrx - seenMob, uMob = mtx - seenMobTx;
        long dLoc = Math.max(0, dAll - dMob), uLoc = Math.max(0, uAll - uMob);
        // what isn't mobile: Wi-Fi when connected, else the hotspot's own devices
        int local = Status.hotspotOn && !Status.wifiConnected ? HOTSPOT : WIFI;
        month[LTE][0] += dMob;
        month[LTE][1] += uMob;
        month[local][0] += dLoc;
        month[local][1] += uLoc;
        float s = lastAt > 0 && now > lastAt ? (now - lastAt) / 1000f : 0;
        for (long[] sp : speed) sp[0] = sp[1] = 0;
        if (s > 0 && seenAll > 0) {
            speed[LTE][0] = Math.round(dMob / s);
            speed[LTE][1] = Math.round(uMob / s);
            speed[local][0] = Math.round(dLoc / s);
            speed[local][1] = Math.round(uLoc / s);
        }
        seenAll = rx;
        seenAllTx = tx;
        seenMob = mrx;
        seenMobTx = mtx;
        lastAt = now;
        if (savedAt == 0 || now - savedAt > 30000) { // saved every 30 s
            save(boot);
            savedAt = now;
        }
    }

    private static long[] nsm;
    private static long nsmAt;

    /** This month on a connection: {down, up}. Mobile uses Android's own count when usage access is on (the whole month). */
    static long[] used(Context c, int k) {
        sample();
        if (k == LTE && nsmAt != 0 && SystemClock.elapsedRealtime() - nsmAt < 30000) { // asked at most every 30 s
            if (nsm != null) return nsm;
        } else if (k == LTE && Apps.hasUsageAccess()) {
            nsmAt = SystemClock.elapsedRealtime();
            nsm = null;
            try {
                android.app.usage.NetworkStatsManager sm = (android.app.usage.NetworkStatsManager) c.getSystemService(Context.NETWORK_STATS_SERVICE);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                android.app.usage.NetworkStats.Bucket b = sm.querySummaryForDevice(android.net.ConnectivityManager.TYPE_MOBILE, null,
                        cal.getTimeInMillis(), System.currentTimeMillis());
                if (b != null && b.getRxBytes() + b.getTxBytes() > 0) return nsm = new long[]{b.getRxBytes(), b.getTxBytes()};
            } catch (Exception ignored) {
            }
        }
        return new long[]{month[k][0], month[k][1]};
    }

    /** Short amount for the rail: 850K, 12M, 1.4G. */
    static String shortSize(long b) {
        if (b < 1_000_000) return Math.round(b / 1000f) + "K";
        if (b < 1_000_000_000) return Math.round(b / 1_000_000f) + "M";
        return String.format(java.util.Locale.US, "%.1fG", b / 1_000_000_000f);
    }

    static String size(long b) {
        if (b < 1_000_000) return Math.round(b / 1000f) + " KB";
        if (b < 1_000_000_000) return Math.round(b / 1_000_000f) + " MB";
        return String.format(java.util.Locale.US, "%.2f GB", b / 1_000_000_000f);
    }

    /** 1.2 MB/s, 340 KB/s. */
    static String rate(long b) {
        if (b < 1000) return b + " B/s";
        if (b < 1_000_000) return Math.round(b / 1000f) + " KB/s";
        return String.format(java.util.Locale.US, "%.1f MB/s", b / 1_000_000f);
    }

    /** Where a speed sits on a bar: log scale, 2 KB/s to 10 MB/s fills it. */
    static float level(long b) {
        if (b <= 2000) return 0f;
        return (float) Math.max(0, Math.min(1, (Math.log10(b) - 3.3) / 3.7));
    }

    // ------------------------------------------------------------------ the meter

    /** What the rail meter counts (quick card): "lte" (default), "wifi", "hotspot" or "total"; and "down" (default) or "both". */
    static final String SRC = "data_src", SHOW = "data_show";

    /** This month's {down, up} for a source; "total" is mobile plus Wi-Fi (hotspot data is already in those). */
    static long[] usedFor(Context c, String src) {
        if ("wifi".equals(src)) return used(c, WIFI);
        if ("hotspot".equals(src)) return used(c, HOTSPOT);
        if ("total".equals(src)) {
            long[] a = used(c, LTE), b = used(c, WIFI);
            return new long[]{a[0] + b[0], a[1] + b[1]};
        }
        return used(c, LTE);
    }

    private final Paint cap = new Paint(Paint.ANTI_ALIAS_FLAG), dn = new Paint(Paint.ANTI_ALIAS_FLAG), up = new Paint(Paint.ANTI_ALIAS_FLAG),
            ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private double shownD = -1, shownU = -1; // count up smoothly to the real amounts
    private boolean running;

    DataMeter(Context c) {
        super(c);
        cap.setColor(Ui.SUB);
        cap.setFakeBoldText(true);
        cap.setTextAlign(Paint.Align.CENTER);
        cap.setLetterSpacing(0.08f);
        dn.setColor(Ui.TEXT); // the amount stays white; only the arrow turns green while data is coming down
        dn.setFakeBoldText(true);
        dn.setTextAlign(Paint.Align.LEFT);
        arrow.setFakeBoldText(true);
        arrow.setTextAlign(Paint.Align.LEFT);
        up.setColor(Ui.AMBER);
        up.setFakeBoldText(true);
        up.setTextAlign(Paint.Align.CENTER);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Ui.LINE);
        setContentDescription("Mobile data used this month");
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            sample(); // keeps the live speeds fresh, for the arrow
            invalidate();
            if (running) postDelayed(this, 1000);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        long[] m = usedFor(getContext(), Prefs.str(SRC, "lte"));
        boolean both = "both".equals(Prefs.str(SHOW, "down"));
        boolean anim = Ui.ms(1) > 0;
        if (shownD < 0 || !anim) {
            shownD = m[0];
            shownU = m[1];
        } else {
            shownD += (m[0] - shownD) * 0.2;
            shownU += (m[1] - shownU) * 0.2;
        }
        float w = getWidth(), h = getHeight();
        float cx = w / 2f;
        // no box: this month's data downloaded in green (and uploaded in amber below it if chosen); tap for the full breakdown
        if (both) {
            dn.setTextSize(Ui.uf(11));
            up.setTextSize(Ui.uf(11));
            float mid = h / 2f - (dn.descent() + dn.ascent()) / 2f, gap = Ui.uf(7);
            drawDown(cv, shortSize(Math.round(shownD)), cx, mid - gap);
            cv.drawText("↑" + shortSize(Math.round(shownU)), cx, mid + gap, up);
        } else {
            dn.setTextSize(Ui.uf(13));
            float y = h / 2f - (dn.descent() + dn.ascent()) / 2f;
            drawDown(cv, shortSize(Math.round(shownD)), cx, y);
        }
        if (anim && (Math.abs(m[0] - shownD) > 500 || Math.abs(m[1] - shownU) > 500)) postInvalidateOnAnimation();
    }

    /** Data is coming down on the connection(s) this meter counts. */
    private static boolean downloading() {
        String src = Prefs.str(SRC, "lte");
        long d;
        if ("wifi".equals(src)) d = speed[WIFI][0];
        else if ("hotspot".equals(src)) d = speed[HOTSPOT][0];
        else if ("total".equals(src)) d = speed[LTE][0] + speed[WIFI][0];
        else d = speed[LTE][0];
        return d > 2000;
    }

    /** The amount in white, with its ↓ arrow green while there's download activity (white otherwise). */
    private void drawDown(Canvas cv, String text, float cx, float y) {
        arrow.setTextSize(dn.getTextSize());
        arrow.setColor(downloading() ? Ui.GREEN : Ui.TEXT);
        String a = "↓";
        float aw = arrow.measureText(a), tw = dn.measureText(text), x0 = cx - (aw + tw) / 2f;
        cv.drawText(a, x0, y, arrow);
        cv.drawText(text, x0 + aw, y, dn);
    }

    // ------------------------------------------------------------------ speed bar

    /** A thin upright bar beside a connection's icon: fills with that connection's live speed (down and up together). */
    static final class SpeedBar extends View {
        private final int kind;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float shown;
        private boolean running;
        private long activeAt = -10000;

        SpeedBar(Context c, int kind) {
            super(c);
            this.kind = kind;
            track.setColor(Ui.LINE);
            fill.setColor(kind == LTE ? Ui.ACCENT : kind == WIFI ? Ui.TEAL : Ui.AMBER);
            if (kind == LTE) setVisibility(GONE); // until there's mobile data moving
        }

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                sample();
                // Wi-Fi and hotspot bars only while that connection is up (mobile always)
                // the signal's bar only while mobile data is moving (and 3 s after, so it doesn't flicker)
                long now = SystemClock.elapsedRealtime();
                if (speed[kind][0] + speed[kind][1] > 2000) activeAt = now;
                boolean on = kind == LTE ? now - activeAt < 3000 : kind == WIFI ? Status.wifiConnected : Status.hotspotOn;
                setVisibility(on ? VISIBLE : GONE);
                invalidate();
                if (running) postDelayed(this, 1000);
            }
        };

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            running = true;
            post(tick);
        }

        @Override
        protected void onDetachedFromWindow() {
            running = false;
            removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), rad = w / 2f;
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, rad, rad, track);
            float t = level(speed[kind][0] + speed[kind][1]);
            boolean anim = Ui.ms(1) > 0;
            shown = anim ? shown + (t - shown) * 0.25f : t;
            if (shown > 0.02f) {
                r.set(0, h - Math.max(w, h * shown), w, h);
                cv.drawRoundRect(r, rad, rad, fill);
            }
            if (anim && Math.abs(t - shown) > 0.01f) postInvalidateOnAnimation();
        }
    }
}
