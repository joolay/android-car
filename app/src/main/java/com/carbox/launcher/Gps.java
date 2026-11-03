package com.carbox.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** One place for GPS: position, speed, heading, altitude, satellites, trip and the town name. */
final class Gps {
    static double lat = Double.NaN, lon = Double.NaN, alt = Double.NaN;
    static float speed = -1;          // m/s, -1 = unknown
    static float bearing = Float.NaN;  // degrees; the last direction driven (held while parked)
    private static float savedBearing = -999;

    /** The last known direction, from this drive or the last one. */
    static float heading() {
        if (!Float.isNaN(bearing)) return bearing;
        int b = Prefs.num("last_bearing", -1);
        return b < 0 ? Float.NaN : b;
    }

    static boolean moving() {
        return fresh() && speed > 2f;
    }
    static long gpsFixAt;              // elapsedRealtime of last GPS fix
    static int satsUsed = -1, satsVisible = -1;
    static String town;

    // trip
    static double tripMeters;
    static long tripMovingMs;
    static float topSpeedToday;

    private static Context ctx;
    private static LocationManager lm;
    private static boolean running, gnssOn;
    private static Location last;
    private static long lastAt;
    private static final List<Runnable> listeners = new ArrayList<>();
    private static double geocodedLat = Double.NaN, geocodedLon = Double.NaN;

    private Gps() {
    }

    static void init(Context c) {
        ctx = c.getApplicationContext();
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        String today = day();
        if (today.equals(Prefs.str(Prefs.TOP_SPEED_DAY, ""))) topSpeedToday = Prefs.num(Prefs.TOP_SPEED, 0);
    }

    static void listen(Runnable r) {
        listeners.add(r);
    }

    static boolean hasPermission() {
        return ctx != null && ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean has() {
        return !Double.isNaN(lat);
    }

    static boolean gpsEnabled() {
        try {
            return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean fresh() {
        return gpsFixAt > 0 && SystemClock.elapsedRealtime() - gpsFixAt < 15000;
    }

    private static final LocationListener ll = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            update(l);
        }

        @Override
        public void onStatusChanged(String p, int s, Bundle b) {
        }

        @Override
        public void onProviderEnabled(String p) {
        }

        @Override
        public void onProviderDisabled(String p) {
        }
    };

    private static final GnssStatus.Callback gnss = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(GnssStatus st) {
            int used = 0;
            for (int i = 0; i < st.getSatelliteCount(); i++) if (st.usedInFix(i)) used++;
            satsUsed = used;
            satsVisible = st.getSatelliteCount();
        }

        @Override
        public void onStopped() {
            satsUsed = -1;
            satsVisible = -1;
        }
    };

    static void start() {
        if (running || lm == null || !hasPermission()) return;
        try {
            Location best = null;
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (!lm.getAllProviders().contains(p)) continue;
                lm.requestLocationUpdates(p, 1000L, 0f, ll, Looper.getMainLooper());
                Location k = lm.getLastKnownLocation(p);
                if (k != null && (best == null || k.getTime() > best.getTime())) best = k;
            }
            running = true;
            if (best != null) update(best);
            gnssOn = lm.registerGnssStatusCallback(gnss, Ui.ui);
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    static void stop() {
        if (lm == null) return;
        try {
            lm.removeUpdates(ll);
            if (gnssOn) lm.unregisterGnssStatusCallback(gnss);
        } catch (Exception ignored) {
        }
        running = false;
        gnssOn = false;
    }

    private static void update(Location l) {
        boolean gps = LocationManager.GPS_PROVIDER.equals(l.getProvider());
        if (gps) gpsFixAt = SystemClock.elapsedRealtime();
        lat = l.getLatitude();
        lon = l.getLongitude();
        if (l.hasAltitude()) alt = l.getAltitude();
        if (gps) {
            speed = l.hasSpeed() ? l.getSpeed() : speed;
            if (l.hasBearing() && speed > 1.5f) {
                bearing = l.getBearing();
                if (Math.abs(bearing - savedBearing) > 5) { // kept for when the car is parked (and after a restart)
                    savedBearing = bearing;
                    Prefs.put("last_bearing", Math.round(bearing));
                }
            }
            long now = SystemClock.elapsedRealtime();
            if (last != null && LocationManager.GPS_PROVIDER.equals(last.getProvider())) {
                float d = last.distanceTo(l);
                long dt = now - lastAt;
                // ignore jumps (bad fixes) and standing still
                if (speed > 1.0f && d < 200 && dt < 10000) {
                    tripMeters += d;
                    tripMovingMs += dt;
                }
            }
            last = l;
            lastAt = now;
            float kmh = speed * 3.6f;
            if (kmh > topSpeedToday && kmh < 300) {
                topSpeedToday = kmh;
                Prefs.put(Prefs.TOP_SPEED, Math.round(kmh));
                Prefs.put(Prefs.TOP_SPEED_DAY, day());
            }
        } else if (last == null) {
            last = l;
        }
        geocode();
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    static void resetTrip() {
        tripMeters = 0;
        tripMovingMs = 0;
    }

    private static String day() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private static void geocode() {
        if (!Geocoder.isPresent()) return;
        if (!Double.isNaN(geocodedLat)) {
            float[] r = new float[1];
            Location.distanceBetween(lat, lon, geocodedLat, geocodedLon, r);
            if (r[0] < 3000) return;
        }
        geocodedLat = lat;
        geocodedLon = lon;
        final double la = lat, lo = lon;
        Net.bg(() -> {
            try {
                List<Address> a = new Geocoder(ctx, Locale.getDefault()).getFromLocation(la, lo, 1);
                if (a != null && !a.isEmpty()) {
                    Address ad = a.get(0);
                    String t = ad.getLocality() != null ? ad.getLocality() : ad.getSubAdminArea() != null ? ad.getSubAdminArea() : ad.getAdminArea();
                    if (t != null) Ui.ui.post(() -> town = t);
                }
            } catch (Exception ignored) {
            }
        });
    }
}
