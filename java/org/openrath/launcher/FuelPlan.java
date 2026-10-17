package org.openrath.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fuel-low planner: from the last full tank in the fuel log, the litres added since, the GPS
 * distance driven since and your average mileage, it works out how far the petrol will go. Below
 * the set range it says so and lists the petrol pumps ahead on the map.
 */
final class FuelPlan {

    /** The tank, litres, from the car setup or Settings; 40 until it's set. */
    static int tank(Prefs p) {
        return p.num("tankL", 40);
    }

    static int warnKm(Prefs p) {
        return p.num("fuelWarnKm", 60);
    }

    /**
     * Litres left: a full tank at the last full fill-up, plus part fills since (never above the
     * tank), less the distance driven since at `kmpl`. -1 when there's no full fill-up to start from.
     */
    static double litresLeft(double tank, double[] gps, double[] litres, boolean[] full, double gpsNow, double kmpl) {
        int f = -1;
        for (int i = 0; i < full.length; i++) if (full[i]) f = i;
        if (f < 0 || kmpl <= 0) return -1;
        double l = tank, at = gps[f];
        for (int i = f + 1; i < gps.length; i++) {
            l -= Math.max(0, gps[i] - at) / kmpl;
            l = Math.min(tank, Math.max(0, l) + litres[i]);
            at = gps[i];
        }
        return Math.max(0, l - Math.max(0, gpsNow - at) / kmpl);
    }

    static double kmpl(Prefs p) {
        double k = Fuel.stats(p).avgKmpl;
        return k > 0 ? k : Coach.typical > 0 ? Coach.typical : Coach.DEFAULT_KMPL;
    }

    /** Kilometres the petrol will last, or -1 when not known. */
    static double rangeKm(Prefs p) {
        JSONArray a = Fuel.log(p);
        int n = a.length();
        double[] gps = new double[n], litres = new double[n];
        boolean[] full = new boolean[n];
        for (int i = 0; i < n; i++) {
            JSONObject e = a.optJSONObject(i);
            gps[i] = e.optDouble("gps", 0);
            litres[i] = e.optDouble("l", 0);
            full[i] = e.optBoolean("full", true);
        }
        double kmpl = kmpl(p), l = litresLeft(tank(p), gps, litres, full, Drive.odoKm, kmpl);
        return l < 0 ? -1 : l * kmpl;
    }

    static String text(Prefs p) {
        double r = rangeKm(p);
        if (r < 0) return "not known yet: add a fill-up with \"filled to full\" ticked";
        return "about " + Math.round(r / 10) * 10 + " km of petrol left";
    }

    private static int told = Integer.MAX_VALUE;   // the last warning level spoken, km

    /** What to say, or null: once below the set range, and again below half of it. */
    static String check(Prefs p) {
        if (!Features.on(p, "fuelPlan")) return null;
        double r = rangeKm(p);
        if (r < 0) return null;
        int warn = warnKm(p);
        int level = r < warn / 2.0 ? warn / 2 : r < warn ? warn : Integer.MAX_VALUE;
        if (level == Integer.MAX_VALUE) {
            told = Integer.MAX_VALUE;   // filled up since
            return null;
        }
        if (level >= told) return null;
        told = level;
        return "About " + Math.max(10, Math.round(r / 10) * 10) + " kilometres of " + Car.fuelWord + " left. Showing " + Car.stationWord + " ahead.";
    }

    private FuelPlan() {
    }
}
