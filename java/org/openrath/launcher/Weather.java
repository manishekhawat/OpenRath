package org.openrath.launcher;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Offline weather: the last forecast is kept, and while there is no internet the box shows the hour
 * that is now from it. These helpers have no Android types, so they are unit-tested.
 */
final class Weather {

    /** "2026-10-14T15:00": the hour now at that place, as Open-Meteo writes its hourly times. */
    static String hourKey(long nowMs, int utcOffsetSec) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(nowMs + utcOffsetSec * 1000L));
    }

    /** Where `hour` is in the forecast's hours, or -1 when the forecast doesn't reach it. */
    static int slot(String[] times, String hour) {
        for (int i = 0; i < times.length; i++) if (hour.equals(times[i])) return i;
        return -1;
    }

    /** "Offline · forecast from 3 h ago", under the weather. */
    static String savedNote(long savedAt, long nowMs) {
        long m = Math.max(0, (nowMs - savedAt) / 60000);
        return m < 60 ? Hindi.f("Offline · forecast from %1$s min ago", m)
                : m < 48 * 60 ? Hindi.f("Offline · forecast from %1$s h ago", m / 60)
                : Hindi.f("Offline · forecast from %1$s days ago", m / 1440);
    }

    private Weather() {
    }
}
