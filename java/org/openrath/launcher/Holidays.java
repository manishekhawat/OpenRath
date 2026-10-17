package org.openrath.launcher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Holidays when the highways out of town fill up: India's national and main festival holidays
 * (the central government's gazetted list), and the long weekends they make. Festivals that move
 * with the moon are listed by date up to the end of 2027; the fixed ones work for any year.
 */
final class Holidays {

    /** {yyyy-mm-dd, name, days of heavy traffic before it}. */
    static final String[][] MOVING = {
            {"2026-03-03", "Holi", "1"}, {"2026-03-21", "Eid", "1"}, {"2026-04-03", "Good Friday", "1"},
            {"2026-09-04", "Janmashtami", "1"}, {"2026-10-20", "Dussehra", "1"}, {"2026-11-08", "Diwali", "2"},
            {"2026-11-24", "Guru Nanak Jayanti", "1"},
            {"2027-03-10", "Eid", "1"}, {"2027-03-23", "Holi", "1"}, {"2027-03-26", "Good Friday", "1"},
            {"2027-08-25", "Janmashtami", "1"}, {"2027-10-09", "Dussehra", "1"}, {"2027-10-29", "Diwali", "2"},
            {"2027-11-14", "Guru Nanak Jayanti", "1"},
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

    /** The holidays in a year. */
    static List<Day> of(int year) {
        List<Day> out = new ArrayList<>();
        for (Object[] f : FIXED) out.add(new Day(day(year, (Integer) f[0], (Integer) f[1]), (String) f[2], 1));
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

    /**
     * The holiday rush on this day, as "the Diwali long weekend" or "Holi", or null. The rush is the
     * holiday's lead days before it and the day itself; a holiday on a Friday or Monday (or Thursday
     * or Tuesday, when people take the day between) makes a long weekend, and the whole weekend counts.
     */
    static String rush(Calendar today) {
        Calendar t = day(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH));
        List<Day> all = new ArrayList<>(of(t.get(Calendar.YEAR)));
        all.addAll(of(t.get(Calendar.YEAR) + 1));   // New Year's rush starts in December
        for (Day h : all) {
            int wd = dow(h.date);
            boolean longWeekend = wd == Calendar.FRIDAY || wd == Calendar.MONDAY || wd == Calendar.THURSDAY || wd == Calendar.TUESDAY;
            Calendar from = (Calendar) h.date.clone(), to = (Calendar) h.date.clone();
            from.add(Calendar.DAY_OF_MONTH, -h.lead);
            if (longWeekend) {
                // stretch over the weekend next to it
                if (wd == Calendar.FRIDAY) to.add(Calendar.DAY_OF_MONTH, 2);
                else if (wd == Calendar.THURSDAY) to.add(Calendar.DAY_OF_MONTH, 3);
                else if (wd == Calendar.MONDAY) from.add(Calendar.DAY_OF_MONTH, -3);
                else from.add(Calendar.DAY_OF_MONTH, -4);
            }
            if (!t.before(from) && !t.after(to)) return longWeekend ? "the " + h.name + " long weekend" : h.name;
        }
        return null;
    }

    /** The next holidays from today, for Settings: "Diwali, Sun 8 Nov". */
    static List<String> upcoming(Calendar today, int n) {
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
