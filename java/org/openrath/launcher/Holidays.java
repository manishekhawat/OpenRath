package org.openrath.launcher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Holidays when the highways out of town fill up: India's national and main festival holidays
 * (the central government's gazetted list), and the long weekends they make. The fixed-date ones and
 * Good Friday (worked out from Easter) work for any year. Festivals that move with the moon are listed
 * by date up to the end of 2035. Eid is listed only for the years whose date is announced, since it
 * waits for the moon to be seen, and a festival whose day is in doubt in a year is left out of that year.
 */
final class Holidays {

    /** {yyyy-mm-dd, name, days of heavy traffic before it}. */
    static final String[][] MOVING = {
            {"2026-03-03", "Holi", "1"}, {"2026-03-21", "Eid", "1"},
            {"2026-09-04", "Janmashtami", "1"}, {"2026-10-20", "Dussehra", "1"}, {"2026-11-08", "Diwali", "2"},
            {"2026-11-24", "Guru Nanak Jayanti", "1"},
            {"2027-03-10", "Eid", "1"}, {"2027-03-23", "Holi", "1"},
            {"2027-08-25", "Janmashtami", "1"}, {"2027-10-09", "Dussehra", "1"}, {"2027-10-29", "Diwali", "2"},
            {"2027-11-14", "Guru Nanak Jayanti", "1"},
            {"2028-03-11", "Holi", "1"}, {"2028-08-13", "Janmashtami", "1"}, {"2028-09-27", "Dussehra", "1"},
            {"2028-10-17", "Diwali", "2"}, {"2028-11-02", "Guru Nanak Jayanti", "1"},
            {"2029-03-01", "Holi", "1"}, {"2029-09-01", "Janmashtami", "1"}, {"2029-10-16", "Dussehra", "1"},
            {"2029-11-05", "Diwali", "2"}, {"2029-11-21", "Guru Nanak Jayanti", "1"},
            {"2030-03-20", "Holi", "1"}, {"2030-08-21", "Janmashtami", "1"}, {"2030-10-06", "Dussehra", "1"},
            {"2030-10-26", "Diwali", "2"}, {"2030-11-10", "Guru Nanak Jayanti", "1"},
            {"2031-08-10", "Janmashtami", "1"}, {"2031-10-25", "Dussehra", "1"}, {"2031-11-14", "Diwali", "2"},
            {"2031-11-28", "Guru Nanak Jayanti", "1"},
            {"2032-08-28", "Janmashtami", "1"}, {"2032-10-14", "Dussehra", "1"}, {"2032-11-02", "Diwali", "2"},
            {"2032-11-17", "Guru Nanak Jayanti", "1"},
            {"2033-08-17", "Janmashtami", "1"}, {"2033-10-03", "Dussehra", "1"}, {"2033-10-22", "Diwali", "2"},
            {"2033-11-06", "Guru Nanak Jayanti", "1"},
            {"2034-10-22", "Dussehra", "1"}, {"2034-11-10", "Diwali", "2"},
            {"2035-03-24", "Holi", "1"}, {"2035-08-26", "Janmashtami", "1"}, {"2035-10-11", "Dussehra", "1"},
            {"2035-10-30", "Diwali", "2"}, {"2035-11-15", "Guru Nanak Jayanti", "1"},
    };

    /** {month, day, name}: the same date every year. */
    static final Object[][] FIXED = {
            {1, 1, "New Year"}, {1, 26, "Republic Day"}, {8, 15, "Independence Day"}, {10, 2, "Gandhi Jayanti"},
            {12, 25, "Christmas"},
    };

    /** A holiday: its day and name, and how many days before it the rush starts. */
    static final class Day {
        final Calendar date;
        final String name;
        final int lead;

        Day(Calendar date, String name, int lead) {
            this.date = date;
            this.name = name;
            this.lead = lead;
        }
    }

    private static Calendar day(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d);
        return c;
    }

    /** Easter Sunday in the Gregorian calendar (the anonymous Gregorian method). Pure, for the tests. */
    static Calendar easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451, n = h + l - 7 * m + 114;
        return day(y, n / 31, n % 31 + 1);
    }

    /** The holidays in a year: the fixed ones and Good Friday in any year, the moon's ones where listed. */
    static List<Day> of(int year) {
        List<Day> out = new ArrayList<>();
        for (Object[] f : FIXED) out.add(new Day(day(year, (Integer) f[0], (Integer) f[1]), (String) f[2], 1));
        Calendar gf = easter(year);
        gf.add(Calendar.DAY_OF_MONTH, -2);
        out.add(new Day(gf, "Good Friday", 1));
        for (String[] m : MOVING) {
            String[] d = m[0].split("-");
            if (Integer.parseInt(d[0]) == year)
                out.add(new Day(day(year, Integer.parseInt(d[1]), Integer.parseInt(d[2])), m[1], Integer.parseInt(m[2])));
        }
        return out;
    }

    private static int dow(Calendar c) {
        return c.get(Calendar.DAY_OF_WEEK);
    }

    /** The next holidays from today, for Settings: "Diwali, Sun 8 Nov". None outside India. */
    static List<String> upcoming(Calendar today, int n) {
        if (!Money.india()) return new ArrayList<>();   // India's list: no use elsewhere
        Calendar t = day(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH));
        List<Day> all = new ArrayList<>(of(t.get(Calendar.YEAR)));
        all.addAll(of(t.get(Calendar.YEAR) + 1));
        java.util.Collections.sort(all, new java.util.Comparator<Day>() {
            @Override
            public int compare(Day a, Day b) {
                return a.date.compareTo(b.date);
            }
        });
        List<String> out = new ArrayList<>();
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EEE d MMM", Locale.US);
        for (Day h : all) {
            if (h.date.before(t) || out.size() >= n) continue;
            out.add(h.name + ", " + f.format(h.date.getTime()));
        }
        return out;
    }

    private Holidays() {
    }
}
