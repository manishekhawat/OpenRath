package org.openrath.launcher;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Dates and times as text, without making a new formatter each time: one formatter per pattern,
 * and each pattern's text worked out at most once a second. Follows a change of the unit's time
 * zone. No Android types, so it is unit-tested.
 */
final class Times {

    private static final class Kept {
        final SimpleDateFormat f;
        String zone;
        long second = Long.MIN_VALUE;
        String text;

        Kept(String pattern) {
            f = new SimpleDateFormat(pattern, Locale.US);
        }
    }

    private static final Map<String, Kept> KEPT = new HashMap<>();

    /** Now, in a SimpleDateFormat pattern (English names; Hindi.t translates them). */
    static String now(String pattern) {
        return at(pattern, System.currentTimeMillis());
    }

    /** The time ms in the pattern; the same second gives the text worked out before. */
    static synchronized String at(String pattern, long ms) {
        Kept k = KEPT.get(pattern);
        if (k == null) {
            k = new Kept(pattern);
            KEPT.put(pattern, k);
        }
        long second = Math.floorDiv(ms, 1000L);
        if (second != k.second || k.text == null) {
            String zone = TimeZone.getDefault().getID();
            if (!zone.equals(k.zone)) {
                k.f.setTimeZone(TimeZone.getTimeZone(zone));
                k.zone = zone;
            }
            k.second = second;
            k.text = k.f.format(new Date(ms));
        }
        return k.text;
    }

    /** Today as "2026-10-12". */
    static String today() {
        return now("yyyy-MM-dd");
    }

    private Times() {
    }
}
