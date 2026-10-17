package org.openrath.launcher;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Monsoon mode: you mark the spots that flood (a steering-wheel button, a voice command, or
 * Settings), and when it is raining, or rain is due within the hour, a voice warns as you
 * approach one: "Waterlogging reported ahead".
 */
final class Monsoon {

    static List<double[]> spots(Prefs p) {
        List<double[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.str("floodSpots", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray s = a.getJSONArray(i);
                out.add(new double[]{s.getDouble(0), s.getDouble(1), s.optLong(2)});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Remember the car's spot as one that floods (the last 200; one per 100 m). */
    static void mark(Context c, Prefs p) {
        if (Drive.fixAt == 0) {
            Toast.makeText(c, Hindi.t("No GPS position yet"), Toast.LENGTH_SHORT).show();
            return;
        }
        List<double[]> l = spots(p);
        for (double[] s : l)
            if (Geo.dist(s[0], s[1], Drive.latNow, Drive.lonNow) < 100) {
                Toast.makeText(c, Hindi.t("This spot is already marked"), Toast.LENGTH_SHORT).show();
                return;
            }
        l.add(new double[]{Drive.latNow, Drive.lonNow, System.currentTimeMillis()});
        JSONArray a = new JSONArray();
        for (int i = Math.max(0, l.size() - 200); i < l.size(); i++) {
            a.put(new JSONArray().put(String.valueOf(l.get(i)[0])).put(String.valueOf(l.get(i)[1])).put((long) l.get(i)[2]));
        }
        p.put("floodSpots", a.toString());
        Toast.makeText(c, Hindi.t("Marked as a waterlogging spot"), Toast.LENGTH_SHORT).show();
    }

    static void clear(Prefs p) {
        p.put("floodSpots", "[]");
    }

    private static final Set<String> told = new HashSet<>();

    /** What to say now, or null: only in rain, once per spot per drive, for spots within 700 m ahead. */
    static String check(Prefs p, boolean wet, double lat, double lon, double heading, float kmh) {
        if (!Features.on(p, "monsoon")) return null;
        return warning(spots(p), told, wet, lat, lon, heading, kmh);
    }

    static String warning(List<double[]> spots, Set<String> told, boolean wet, double lat, double lon, double heading, float kmh) {
        if (!wet || kmh < 10) return null;
        for (double[] s : spots) {
            String id = s[0] + "," + s[1];
            if (told.contains(id) || !Geo.ahead(lat, lon, heading, s[0], s[1], 700)) continue;
            told.add(id);
            long m = Math.round(Geo.dist(lat, lon, s[0], s[1]) / 50.0) * 50;
            return "Waterlogging reported ahead, in " + Math.max(50, m) + " metres. Go slow.";
        }
        return null;
    }

    /** A new drive: warn about every spot again. */
    static void reset() {
        told.clear();
    }

    private Monsoon() {
    }
}
