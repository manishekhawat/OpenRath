package org.openrath.launcher;

/**
 * Sanity rules for GPS fixes and the speed shown. Android can hand back an old cached fix when
 * location updates restart; it is older on the boot clock than the last one used, so it is skipped
 * instead of adding a jump to the trip or a spike to the speed.
 */
final class Fixes {

    /** Below this the car is standing still: GPS drift at a red light reads 1–2 km/h. */
    static final float STILL_KMH = 2f;
    /** Below this the speed shows 0: the owner's speed filter (Settings > Driving), for GPS that drifts when parked. */
    static float stillKmh = STILL_KMH;

    /** True when a fix can be used: real coordinates, and not older than the last fix used (0 = none yet). */
    static boolean usable(double lat, double lon, long fixNanos, long lastNanos) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return false;
        if (lat == 0 && lon == 0) return false;
        return lastNanos == 0 || fixNanos == 0 || fixNanos >= lastNanos;
    }

    static boolean usable(android.location.Location l, long lastNanos) {
        return l != null && usable(l.getLatitude(), l.getLongitude(), l.getElapsedRealtimeNanos(), lastNanos);
    }

    /**
     * The speed to show: eased towards the new reading over about a third of a second, so it doesn't
     * jump digit to digit, and 0 below walking pace so it doesn't crawl at a red light.
     */
    static float smooth(float shown, float raw, long dtMs) {
        if (raw < stillKmh) return 0;
        if (shown <= 0 || dtMs <= 0 || dtMs > 5000) return raw;
        double a = 1 - Math.exp(-dtMs / 350.0);
        return (float) (shown + a * (raw - shown));
    }

    private Fixes() {
    }
}
