package org.openrath.launcher;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Today's and the next few days' events from the calendars on the unit (a signed-in Google account or the phone's). */
final class Agenda {

    static final int DAYS = 4, MAX = 6;

    static final class Event {
        final String title;
        final long start, end;
        final boolean allDay;

        Event(String title, long start, long end, boolean allDay) {
            this.title = title;
            this.start = start;
            this.end = end;
            this.allDay = allDay;
        }
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Events still to come or going on now, from now until the end of DAYS days, soonest first. */
    static List<Event> read(Context c, long now) {
        List<Event> out = new ArrayList<>();
        if (!allowed(c)) return out;
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(b, now - 24 * 3600000L);
        android.content.ContentUris.appendId(b, now + DAYS * 24 * 3600000L);
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(b.build(), new String[]{CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY},
                    null, null, CalendarContract.Instances.BEGIN + " ASC");
            while (cur != null && cur.moveToNext() && out.size() < MAX) {
                boolean allDay = cur.getInt(3) != 0;
                long start = cur.getLong(1), end = cur.getLong(2);
                // all-day events are stored at midnight UTC: move them to the local day
                if (allDay) {
                    int off = TimeZone.getDefault().getOffset(start);
                    start -= off;
                    end -= off;
                }
                if (end <= now) continue;
                String t = cur.getString(0);
                out.add(new Event(t == null || t.trim().isEmpty() ? "(no title)" : t.trim(), start, end, allDay));
            }
        } catch (Exception e) {
            Diag.note("calendar read", e);
        } finally {
            if (cur != null) cur.close();
        }
        return out;
    }

    /** {day, time} for an event: "Today", "Tomorrow" or "Wed", and "14:30" ("" for all day, "now" while it runs). */
    static String[] when(long start, long end, boolean allDay, long now, TimeZone tz) {
        Calendar n = Calendar.getInstance(tz, Locale.US), s = Calendar.getInstance(tz, Locale.US);
        n.setTimeInMillis(now);
        s.setTimeInMillis(Math.max(start, now));
        int days = dayNumber(s) - dayNumber(n);
        String day = days <= 0 ? "Today" : days == 1 ? "Tomorrow"
                : new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}[s.get(Calendar.DAY_OF_WEEK) - 1];
        String time = allDay ? "" : start <= now && end > now ? "now"
                : String.format(Locale.US, "%02d:%02d", s.get(Calendar.HOUR_OF_DAY), s.get(Calendar.MINUTE));
        return new String[]{day, time};
    }

    private static int dayNumber(Calendar c) {
        return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR);
    }

    private Agenda() {
    }
}
