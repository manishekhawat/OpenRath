package org.openrath.launcher;

import android.app.AlertDialog;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small badges for good driving, worked out from the day-by-day driving log (Drive.history): smooth
 * streaks, weeks within the limit, calm long drives and distance. Kept on the unit; a new one is
 * shown once with a toast.
 */
final class Achievements {

    /** {id, name, how to earn it}. */
    static final String[][] BADGES = {
            {"smooth3", "Smooth three", "Three driving days in a row with a score of 90 or more"},
            {"smooth7", "Smooth week", "Seven driving days in a row with a score of 90 or more"},
            {"smooth30", "Smooth month", "Thirty driving days in a row with a score of 90 or more"},
            {"limit7", "A week within the limit", "Seven driving days in a row without speeding"},
            {"perfect", "Perfect day", "A day of 20 km or more with a score of 100"},
            {"calm", "Calm long drive", "A day of 100 km or more with no harsh braking or hard starts"},
            {"km1000", "1,000 km", "1,000 km driven with OpenRath"},
            {"km10000", "10,000 km", "10,000 km driven with OpenRath"},
    };
    /** A day counts as a driving day from this distance. */
    static final double DRIVEN_KM = 5;

    /**
     * Badges the log has earned. `days` is in date order, each {km, seconds, harsh brakes, hard
     * starts, seconds over the limit, score}; days with almost no driving don't break a streak.
     */
    static List<String> earned(List<double[]> days) {
        List<String> out = new ArrayList<>();
        int smooth = 0, bestSmooth = 0, within = 0, bestWithin = 0;
        double km = 0;
        boolean perfect = false, calm = false;
        for (double[] d : days) {
            if (d == null || d.length < 6) continue;
            km += d[0];
            if (d[0] < DRIVEN_KM) continue;
            smooth = d[5] >= 90 ? smooth + 1 : 0;
            within = d[4] == 0 ? within + 1 : 0;
            bestSmooth = Math.max(bestSmooth, smooth);
            bestWithin = Math.max(bestWithin, within);
            if (d[0] >= 20 && d[5] >= 100) perfect = true;
            if (d[0] >= 100 && d[2] == 0 && d[3] == 0) calm = true;
        }
        if (bestSmooth >= 3) out.add("smooth3");
        if (bestSmooth >= 7) out.add("smooth7");
        if (bestSmooth >= 30) out.add("smooth30");
        if (bestWithin >= 7) out.add("limit7");
        if (perfect) out.add("perfect");
        if (calm) out.add("calm");
        if (km >= 1000) out.add("km1000");
        if (km >= 10000) out.add("km10000");
        return out;
    }

    /** The ones in `now` that aren't in the saved comma list `had`. */
    static List<String> fresh(String had, List<String> now) {
        List<String> out = new ArrayList<>();
        String h = "," + (had == null ? "" : had) + ",";
        for (String id : now) if (!h.contains("," + id + ",")) out.add(id);
        return out;
    }

    static String name(String id) {
        for (String[] b : BADGES) if (b[0].equals(id)) return b[1];
        return id;
    }

    /** The driving log in date order. */
    static List<double[]> days(JSONObject hist) {
        List<String> keys = new ArrayList<>();
        java.util.Iterator<String> it = hist.keys();
        while (it.hasNext()) keys.add(it.next());
        Collections.sort(keys);
        List<double[]> out = new ArrayList<>();
        for (String k : keys) {
            JSONArray j = hist.optJSONArray(k);
            if (j == null || j.length() < 6) continue;
            double[] d = new double[6];
            for (int i = 0; i < 6; i++) d[i] = j.optDouble(i, 0);
            out.add(d);
        }
        return out;
    }

    /** From the dashboard's regular check, at most once an hour: a toast for each new badge. */
    static void check(HomeActivity a) {
        if (!Features.on(a.prefs, "achievements")) return;
        long now = System.currentTimeMillis();
        if (Math.abs(now - a.prefs.longNum("badgesAt", 0)) < 3600 * 1000L) return;
        a.prefs.putLong("badgesAt", now);
        List<String> got = earned(days(Drive.history(a)));
        List<String> news = fresh(a.prefs.str("badges", ""), got);
        if (news.isEmpty()) return;
        a.prefs.put("badges", android.text.TextUtils.join(",", got));
        for (String id : news) Toast.makeText(a, Hindi.f("New badge: %1$s", Hindi.t(name(id))), Toast.LENGTH_LONG).show();
    }

    static int count(HomeActivity a) {
        return earned(days(Drive.history(a))).size();
    }

    static void show(HomeActivity a) {
        List<String> got = earned(days(Drive.history(a)));
        a.prefs.put("badges", android.text.TextUtils.join(",", got));
        String[] rows = new String[BADGES.length];
        for (int i = 0; i < BADGES.length; i++) {
            rows[i] = (got.contains(BADGES[i][0]) ? "★  " : "☆  ") + Hindi.t(BADGES[i][1]) + "\n     " + Hindi.t(BADGES[i][2]);
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.f("Badges: %1$s of %2$s", got.size(), BADGES.length))
                .setItems(rows, null)
                .setPositiveButton(Hindi.t("Close"), null).show();
    }

    private Achievements() {
    }
}
