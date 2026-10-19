package org.openrath.launcher;

import android.os.SystemClock;

/**
 * Many stereos don't shut down with the ignition: they go into deep sleep with OpenRath still on
 * screen, and often without telling apps the screen went off. On waking, the GPS listener, the
 * music apps' link and the map can be stale. A check every 15 seconds spots the sleep (the boot
 * clock moved on while the awake clock didn't) and starts them all again.
 */
final class Wake {

    static final long CHECK_MS = 15000, SLEPT_MS = 60000;

    private static long lastElapsed, lastUptime;

    /** How long the unit was in deep sleep between two readings of the clocks, in ms. */
    static long slept(long elapsedBefore, long uptimeBefore, long elapsedNow, long uptimeNow) {
        if (elapsedBefore <= 0) return 0;
        return Math.max(0, (elapsedNow - elapsedBefore) - (uptimeNow - uptimeBefore));
    }

    private static Runnable check;

    /** Started from onResume; stops by itself when the dashboard is not on screen. */
    static void watch(final HomeActivity a) {
        lastElapsed = SystemClock.elapsedRealtime();
        lastUptime = SystemClock.uptimeMillis();
        if (check != null) a.dash.removeCallbacks(check);
        check = new Runnable() {
            @Override
            public void run() {
                if (!HomeActivity.visible || check != this) return;
                long e = SystemClock.elapsedRealtime(), u = SystemClock.uptimeMillis();
                long s = slept(lastElapsed, lastUptime, e, u);
                lastElapsed = e;
                lastUptime = u;
                if (s > SLEPT_MS && Features.on(a.prefs, "wake")) woke(a);
                a.dash.postDelayed(this, CHECK_MS);
            }
        };
        a.dash.postDelayed(check, CHECK_MS);
    }

    /** Everything that goes stale in deep sleep, started again. */
    static void woke(HomeActivity a) {
        a.prefs.put("sleptAt", String.valueOf(System.currentTimeMillis() - 2 * SLEPT_MS));   // so Resume carries on the music
        Drive.start(a);
        a.hub.stop();
        a.hub.start();
        a.build();
        Extras.resumed(a);
    }

    private Wake() {
    }
}
