package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The mileage coach: each drive is recorded (distance, idling, hard starts, time above 90 km/h),
 * and the coach estimates how much extra fuel each habit cost, so the worst drives and their
 * reasons can be shown. The estimates are for a 1.2 litre petrol hatchback or small saloon.
 */
final class Coach {

    /** Petrol burnt standing still with the engine running, litres per hour. */
    static final double IDLE_LPH = 0.7;
    /** Extra petrol for each hard start, litres. */
    static final double START_L = 0.015;
    /** Extra share of fuel per km driven above 90 km/h. */
    static final double FAST_SHARE = 0.2;
    /** Mileage to assume before the fuel log has one, km/l. */
    static final double DEFAULT_KMPL = 18;
    /** The mileage to expect before the fuel log knows the real one: from the car profile (Car.apply). */
    static double typical = DEFAULT_KMPL;
    /** A stop longer than this ends the drive (it wasn't idling in traffic). */
    static final long STOP_MS = 10 * 60000;

    /** One drive: from moving off to a stop of ten minutes or more. */
    static final class Trip {
        long start, end;
        double km;
        long movingSec, idleSec, fastSec;
        int starts, brakes;
        // where it started and ended (0 when not known)
        double sLat, sLon, eLat, eLon;

        JSONArray json() {
            return new JSONArray().put(start).put(end).put(String.valueOf(Math.round(km * 10) / 10.0)).put(movingSec)
                    .put(idleSec).put(fastSec).put(starts).put(brakes)
                    .put(String.valueOf(sLat)).put(String.valueOf(sLon)).put(String.valueOf(eLat)).put(String.valueOf(eLon));
        }

        static Trip of(JSONArray a) {
            Trip t = new Trip();
            t.start = a.optLong(0);
            t.end = a.optLong(1);
            t.km = a.optDouble(2);
            t.movingSec = a.optLong(3);
            t.idleSec = a.optLong(4);
            t.fastSec = a.optLong(5);
            t.starts = a.optInt(6);
            t.brakes = a.optInt(7);
            t.sLat = a.optDouble(8, 0);
            t.sLon = a.optDouble(9, 0);
            t.eLat = a.optDouble(10, 0);
            t.eLon = a.optDouble(11, 0);
            return t;
        }

        long minutes() {
            return Math.max(1, (end - start) / 60000);
        }
    }

    /** Builds drives from the trip service's readings. Feed it every GPS fix. */
    static final class Tracker {
        Trip cur;
        long prevAt, stillSince, pendingIdle;

        /** Feed with the position too, so the drive knows where it started and ended. */
        Trip feed(long now, float kmh, double dKm, boolean hardStart, boolean brake, double lat, double lon) {
            boolean moving = kmh >= 2;
            Trip done = feed(now, kmh, dKm, hardStart, brake);
            if (cur != null) {
                if (cur.sLat == 0 && cur.start == now) {
                    cur.sLat = lat;
                    cur.sLon = lon;
                }
                if (moving || cur.eLat == 0) {
                    cur.eLat = lat;
                    cur.eLon = lon;
                }
            }
            return done;
        }

        /** Returns a finished drive when a long stop ends one, else null. */
        Trip feed(long now, float kmh, double dKm, boolean hardStart, boolean brake) {
            Trip done = null;
            if (cur == null) {
                if (kmh > 8) {
                    cur = new Trip();
                    cur.start = now;
                    prevAt = now;
                    stillSince = 0;
                    pendingIdle = 0;
                }
                return null;
            }
            long dt = Math.min(5000, Math.max(0, now - prevAt));
            prevAt = now;
            cur.km += dKm;
            if (hardStart) cur.starts++;
            if (brake) cur.brakes++;
            if (kmh < 2) {
                if (stillSince == 0) stillSince = now;
                pendingIdle += dt;
                if (now - stillSince >= STOP_MS) {
                    cur.end = stillSince;
                    done = cur;
                    cur = null;
                }
            } else {
                // the stop was short: it was idling in traffic or at a signal
                cur.idleSec += pendingIdle / 1000;
                pendingIdle = 0;
                stillSince = 0;
                cur.movingSec += dt / 1000;
                if (kmh > 90) cur.fastSec += dt / 1000;
            }
            return done;
        }

        /** Close the drive now (the unit switched off); null if there was none worth keeping. */
        Trip close(long now) {
            Trip t = cur;
            cur = null;
            if (t == null) return null;
            t.end = stillSince > 0 ? stillSince : now;
            return t;
        }

        /** Minutes driven since the last long stop, or 0 when not driving. */
        long drivingMinutes(long now) {
            return cur == null ? 0 : (now - cur.start) / 60000;
        }
    }

    /** What a drive's habits cost: {idle litres, hard-start litres, speeding litres}. */
    static double[] waste(Trip t, double kmpl) {
        if (kmpl <= 0) kmpl = typical > 0 ? typical : (typical > 0 ? typical : DEFAULT_KMPL);
        double idle = t.idleSec / 3600.0 * IDLE_LPH;
        double starts = t.starts * START_L;
        double fastKm = t.fastSec / 3600.0 * 100;   // about 100 km/h while above 90
        double fast = Math.min(fastKm, t.km) / kmpl * FAST_SHARE;
        return new double[]{idle, starts, fast};
    }

    static double total(double[] w) {
        return w[0] + w[1] + w[2];
    }

    /** "14 min idling, 5 hard starts" — the habits that cost most first, small ones left out. */
    static String reasons(Trip t, double[] w) {
        List<double[]> r = new ArrayList<>();
        for (int i = 0; i < 3; i++) r.add(new double[]{i, w[i]});
        Collections.sort(r, new Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(b[1], a[1]);
            }
        });
        StringBuilder s = new StringBuilder();
        for (double[] x : r) {
            if (x[1] < 0.02) continue;
            if (s.length() > 0) s.append(", ");
            int i = (int) x[0];
            if (i == 0) s.append(Hindi.f("%1$s min idling", t.idleSec / 60));
            else if (i == 1) s.append(Hindi.f(t.starts == 1 ? "%1$s hard start" : "%1$s hard starts", t.starts));
            else s.append(Hindi.f("%1$s min above 90 km/h", Math.max(1, t.fastSec / 60)));
        }
        return s.length() == 0 ? Hindi.t("smooth drive") : s.toString();
    }

    /** The one tip for the habit that cost most overall. */
    static String tip(double[] sum) {
        int worst = sum[0] >= sum[1] && sum[0] >= sum[2] ? 0 : sum[1] >= sum[2] ? 1 : 2;
        if (total(sum) < 0.05) return Hindi.t("Your driving is already easy on fuel.");
        if (worst == 0) return Hindi.t("Idling costs the most. Switch off for waits longer than a minute, like level crossings.");
        if (worst == 1) return Hindi.t("Hard starts cost the most. Pull away gently and change up by 2,000 rpm.");
        return Hindi.t("Speed costs the most. Holding 80 instead of 100 km/h saves about a fifth of the fuel on the highway.");
    }

    static List<Trip> trips(Prefs p) {
        List<Trip> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.str("drives", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Trip.of(a.getJSONArray(i)));
        } catch (Exception e) {
            Diag.note("drive list read", e);
        }
        return out;
    }

    /** Keep a finished drive (the last 120); drives under a kilometre aren't worth coaching. */
    static void save(Prefs p, Trip t) {
        if (t == null || t.km < 1) return;
        try {
            JSONArray a = new JSONArray(p.str("drives", "[]"));
            a.put(t.json());
            JSONArray keep = new JSONArray();
            for (int i = Math.max(0, a.length() - 120); i < a.length(); i++) keep.put(a.get(i));
            p.put("drives", keep.toString());
        } catch (Exception e) {
            Diag.note("drive list save", e);
        }
    }

    /** Settings > Driving > Mileage coach: the drives that wasted most, and why. */
    static void show(Activity a, Prefs p) {
        List<Trip> all = trips(p);
        if (all.isEmpty()) {
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Mileage coach"))
                    .setMessage(Hindi.t("No drives recorded yet. After your next drive of a kilometre or more, this shows "
                            + "which drives used the most extra fuel, and why."))
                    .setPositiveButton(Hindi.t("OK"), null).show();
            return;
        }
        final double kmpl = Fuel.stats(p).avgKmpl > 0 ? Fuel.stats(p).avgKmpl : DEFAULT_KMPL;
        final double price = Fuel.price(p);
        double[] sum = new double[3];
        for (Trip t : all) {
            double[] w = waste(t, kmpl);
            for (int i = 0; i < 3; i++) sum[i] += w[i];
        }
        List<Trip> sorted = new ArrayList<>(all);
        Collections.sort(sorted, new Comparator<Trip>() {
            @Override
            public int compare(Trip x, Trip y) {
                return Double.compare(total(waste(y, kmpl)), total(waste(x, kmpl)));
            }
        });
        StringBuilder s = new StringBuilder();
        s.append(Hindi.f("Your last %1$s drives used about %2$s litres more than they needed",
                all.size(), String.format(Locale.US, "%.1f", total(sum))));
        if (price > 0) s.append(' ').append(Hindi.f("(about ₹%1$s)", Math.round(total(sum) * price)));
        s.append(".\n\n").append(tip(sum)).append("\n\n").append(Hindi.t("The drives that used the most extra fuel:")).append('\n');
        SimpleDateFormat f = new SimpleDateFormat("EEE d MMM, h:mm a", Locale.US);
        for (int i = 0; i < Math.min(8, sorted.size()); i++) {
            Trip t = sorted.get(i);
            double[] w = waste(t, kmpl);
            s.append("\n• ").append(f.format(new Date(t.start))).append("  ·  ").append(Hindi.f("%1$s km, %2$s min", String.format(Locale.US, "%.1f", t.km), t.minutes()));
            s.append("\n   ").append(reasons(t, w));
            if (total(w) >= 0.02) {
                String l = String.format(Locale.US, "%.2f", total(w));
                s.append("  (").append(price > 0 ? Hindi.f("about %1$s l, ₹%2$s extra", l, Math.round(total(w) * price))
                        : Hindi.f("about %1$s l extra", l)).append(')');
            }
        }
        s.append("\n\n").append(Hindi.f("Estimates for a 1.2 litre petrol engine, using %1$s km/l.", Fuel.fmt(kmpl)));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Mileage coach"))
                .setMessage(s.toString()).setPositiveButton(Hindi.t("OK"), null).show();
    }

    private Coach() {
    }
}
