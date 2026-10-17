package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Commute insights: from the recorded drives, the trip you make most (say home to office) and
 * how long it takes depending on when you leave, so you can pick the quickest time. Places are
 * named from the map's saved Home and Office when they're within 400 m.
 */
final class Commute {

    static final double SAME_PLACE_M = 500;

    static final class Route {
        String from, to;
        int trips;
        double km;
        /** Leaving time (minutes after midnight, in half hours) → {total minutes, trips}. */
        final TreeMap<Integer, long[]> slots = new TreeMap<>();
        int best = -1;
    }

    /** The two most-made routes with 3 or more drives, most made first. `places` maps a name to {lat, lon}. */
    static List<Route> learn(List<Coach.Trip> all, Map<String, double[]> places, java.util.TimeZone tz) {
        List<double[]> centres = new ArrayList<>();
        List<int[]> pairs = new ArrayList<>();
        List<Coach.Trip> used = new ArrayList<>();
        for (Coach.Trip t : all) {
            if (t.sLat == 0 || t.eLat == 0 || t.km < 2) continue;
            int a = cluster(centres, t.sLat, t.sLon), b = cluster(centres, t.eLat, t.eLon);
            if (a == b) continue;   // a round trip
            pairs.add(new int[]{a, b});
            used.add(t);
        }
        Map<String, Route> routes = new TreeMap<>();
        for (int i = 0; i < used.size(); i++) {
            Coach.Trip t = used.get(i);
            int[] ab = pairs.get(i);
            String key = ab[0] + ">" + ab[1];
            Route r = routes.get(key);
            if (r == null) {
                r = new Route();
                r.from = name(centres.get(ab[0]), places, ab[0]);
                r.to = name(centres.get(ab[1]), places, ab[1]);
                routes.put(key, r);
            }
            r.trips++;
            r.km += t.km;
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(t.start);
            int slot = c.get(Calendar.HOUR_OF_DAY) * 60 + (c.get(Calendar.MINUTE) / 30) * 30;
            long[] s = r.slots.get(slot);
            if (s == null) r.slots.put(slot, s = new long[2]);
            s[0] += t.minutes();
            s[1]++;
        }
        List<Route> out = new ArrayList<>();
        for (Route r : routes.values()) if (r.trips >= 3) out.add(r);
        java.util.Collections.sort(out, new java.util.Comparator<Route>() {
            @Override
            public int compare(Route x, Route y) {
                return y.trips - x.trips;
            }
        });
        for (Route r : out) {
            r.km /= r.trips;
            double bestAvg = Double.MAX_VALUE;
            for (Map.Entry<Integer, long[]> e : r.slots.entrySet()) {
                double avg = (double) e.getValue()[0] / e.getValue()[1];
                if (e.getValue()[1] >= 2 && avg < bestAvg) {
                    bestAvg = avg;
                    r.best = e.getKey();
                }
            }
        }
        return out.size() > 2 ? out.subList(0, 2) : out;
    }

    private static int cluster(List<double[]> centres, double lat, double lon) {
        for (int i = 0; i < centres.size(); i++)
            if (Geo.dist(centres.get(i)[0], centres.get(i)[1], lat, lon) < SAME_PLACE_M) return i;
        centres.add(new double[]{lat, lon});
        return centres.size() - 1;
    }

    private static String name(double[] c, Map<String, double[]> places, int i) {
        for (Map.Entry<String, double[]> e : places.entrySet())
            if (Geo.dist(c[0], c[1], e.getValue()[0], e.getValue()[1]) < 400) return e.getKey();
        return "place " + (char) ('A' + Math.min(25, i));
    }

    static String time(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return String.format(Locale.US, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "am" : "pm");
    }

    /** Home and Office from the map's saved places. */
    static Map<String, double[]> places(Prefs p) {
        Map<String, double[]> m = new TreeMap<>();
        for (String n : new String[]{"Home", "Office"}) {
            String[] v = p.str("place:" + n, "").split(",");
            if (v.length == 2) {
                try {
                    m.put(n, new double[]{Double.parseDouble(v[0]), Double.parseDouble(v[1])});
                } catch (Exception ignored) {
                }
            }
        }
        return m;
    }

    static String describe(List<Route> routes) {
        if (routes.isEmpty()) return "Not enough drives yet. After three or more drives between the same two places, "
                + "this shows how long the drive takes depending on when you leave.";
        StringBuilder s = new StringBuilder();
        for (Route r : routes) {
            if (s.length() > 0) s.append("\n\n");
            s.append(String.format(Locale.US, "%s to %s: %d drives, about %.0f km", r.from, r.to, r.trips, r.km));
            for (Map.Entry<Integer, long[]> e : r.slots.entrySet()) {
                long[] v = e.getValue();
                s.append(String.format(Locale.US, "\n• Leaving %s–%s: %d min on average (%d %s)", time(e.getKey()),
                        time(e.getKey() + 30), Math.round((double) v[0] / v[1]), v[1], v[1] == 1 ? "drive" : "drives"));
            }
            if (r.best >= 0) s.append("\nQuickest: leave between ").append(time(r.best)).append(" and ").append(time(r.best + 30)).append('.');
        }
        return s.toString();
    }

    static void show(Activity a, Prefs p) {
        String text = describe(learn(Coach.trips(p), places(p), java.util.TimeZone.getDefault()));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle("Commute insights")
                .setMessage(text + "\n\nSave Home and Office on the map (the star) to see them named here.")
                .setPositiveButton("OK", null).show();
    }

    private Commute() {
    }
}
