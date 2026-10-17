package org.openrath.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Places the car learns from your own drives, kept as plain text in the settings:
 * speed breakers (a sharp slow-down at the same spot, in the same direction, on three different
 * drives) and stops (how long you usually wait at a signal or toll plaza, by the time of day).
 * No Android types, so it is unit-tested.
 */
final class Spots {

    static final class Spot {
        double lat, lon;
        int dir;                                   // heading in eighths (0 = north), -1 for any
        final List<long[]> seen = new ArrayList<>();   // {drive or time, value}
    }

    private final Map<String, Spot> all = new LinkedHashMap<>();
    private final double grid;
    private final int keep, max;

    /** grid: size of a spot in degrees; keep: readings kept per spot; max: spots kept in all. */
    Spots(double grid, int keep, int max) {
        this.grid = grid;
        this.keep = keep;
        this.max = max;
    }

    static int eighth(double heading) {
        return heading < 0 ? -1 : (int) Math.round(((heading % 360) + 360) % 360 / 45) % 8;
    }

    private String key(double lat, double lon, int dir) {
        return Math.round(lat / grid) + "," + Math.round(lon / grid) + "," + dir;
    }

    /** Add a reading at a place: `id` is the drive (for breakers) or the time (for waits). */
    Spot add(double lat, double lon, double heading, long id, long value) {
        int d = eighth(heading);
        String k = key(lat, lon, d);
        Spot s = all.get(k);
        if (s == null) {
            s = new Spot();
            s.lat = lat;
            s.lon = lon;
            s.dir = d;
            all.put(k, s);
            while (all.size() > max) all.remove(all.keySet().iterator().next());
        }
        s.seen.add(new long[]{id, value});
        while (s.seen.size() > keep) s.seen.remove(0);
        return s;
    }

    /** How many different drives (ids) a spot was seen on. */
    static int drives(Spot s) {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (long[] r : s.seen) ids.add(r[0]);
        return ids.size();
    }

    /** The median of the values, or -1 for none. */
    static long median(List<Long> v) {
        if (v.isEmpty()) return -1;
        List<Long> c = new ArrayList<>(v);
        Collections.sort(c);
        return c.get(c.size() / 2);
    }

    /** Waits at a spot within two hours of `hour` (wrapping round midnight); all of them when fewer than three. */
    static List<Long> waitsNear(Spot s, int hour) {
        List<Long> near = new ArrayList<>(), any = new ArrayList<>();
        for (long[] r : s.seen) {
            any.add(r[1]);
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(r[0]);
            int dh = Math.abs(c.get(java.util.Calendar.HOUR_OF_DAY) - hour);
            if (Math.min(dh, 24 - dh) <= 2) near.add(r[1]);
        }
        return near.size() >= 3 ? near : any;
    }

    /** The nearest learnt spot ahead in the car's direction within `metres`, matching `ok`, or null. */
    Spot ahead(double lat, double lon, double heading, double metres, Test ok) {
        Spot best = null;
        double bestD = metres;
        int d = eighth(heading);
        for (Spot s : all.values()) {
            if (s.dir >= 0 && d >= 0 && Math.min(Math.abs(s.dir - d), 8 - Math.abs(s.dir - d)) > 1) continue;
            double m = Geo.dist(lat, lon, s.lat, s.lon);
            if (m < bestD && m > 15 && Geo.ahead(lat, lon, heading, s.lat, s.lon, metres) && ok.ok(s)) {
                bestD = m;
                best = s;
            }
        }
        return best;
    }

    interface Test {
        boolean ok(Spot s);
    }

    static String id(Spot s) {
        return String.format(Locale.US, "%.4f,%.4f,%d", s.lat, s.lon, s.dir);
    }

    // ------------------------------------------------------------------ saving as text

    String save() {
        StringBuilder b = new StringBuilder();
        for (Spot s : all.values()) {
            b.append(String.format(Locale.US, "%.5f|%.5f|%d|", s.lat, s.lon, s.dir));
            for (int i = 0; i < s.seen.size(); i++) {
                if (i > 0) b.append(',');
                b.append(s.seen.get(i)[0]).append(':').append(s.seen.get(i)[1]);
            }
            b.append('\n');
        }
        return b.toString();
    }

    void load(String text) {
        all.clear();
        if (text == null) return;
        for (String line : text.split("\n")) {
            String[] f = line.split("\\|");
            if (f.length < 4) continue;
            try {
                double la = Double.parseDouble(f[0]), lo = Double.parseDouble(f[1]);
                int d = Integer.parseInt(f[2]);
                Spot s = new Spot();
                s.lat = la;
                s.lon = lo;
                s.dir = d;
                for (String r : f[3].split(",")) {
                    String[] p = r.split(":");
                    if (p.length == 2) s.seen.add(new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])});
                }
                all.put(key(la, lo, d), s);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    int size() {
        return all.size();
    }

    int count(Test t) {
        int n = 0;
        for (Spot s : all.values()) if (t.ok(s)) n++;
        return n;
    }

    void clear() {
        all.clear();
    }
}
