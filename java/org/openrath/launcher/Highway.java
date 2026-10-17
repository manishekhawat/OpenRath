package org.openrath.launcher;

/**
 * Highway mode: after two minutes above 75 km/h the car counts as on the highway until it stays
 * under 50 km/h for two minutes. While it lasts, message pop-ups and read-aloud wait (they are
 * counted and mentioned when the highway ends), and the dashboard shows a slim highway strip
 * with the average speed and the time since the last break. No Android types, so it is tested.
 */
final class Highway {

    static final float FAST = 75, SLOW = 50;
    static final long HOLD_MS = 120000;

    static volatile boolean on;
    static volatile int held;   // messages that waited

    private long fastSince = -1, slowSince = -1;

    /** One speed reading; returns +1 when highway mode starts, -1 when it ends, else 0. */
    int feed(float kmh, long now) {
        if (kmh >= FAST) {
            if (fastSince < 0) fastSince = now;
        } else fastSince = -1;
        if (kmh < SLOW) {
            if (slowSince < 0) slowSince = now;
        } else slowSince = -1;
        if (!on && fastSince >= 0 && now - fastSince >= HOLD_MS) {
            on = true;
            held = 0;
            return 1;
        }
        if (on && slowSince >= 0 && now - slowSince >= HOLD_MS) {
            on = false;
            return -1;
        }
        return 0;
    }

    static String endText(int held) {
        if (held <= 0) return "Highway mode off.";
        return "Highway mode off. " + held + (held == 1 ? " message" : " messages") + " came in while you were on the highway.";
    }

    void reset() {
        fastSince = slowSince = -1;
        on = false;
    }
}
