package org.openrath.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Hill (ghat) mode: the slope of the road from the GPS height over the last few hundred metres.
 * On a long steep climb or descent a voice gives the hill-driving tips once: a low gear, engine
 * braking instead of riding the brakes going down, and that traffic climbing up has the right of
 * way. GPS height is rough, so it only speaks for a steady slope over 400 m or more.
 * No Android types, so it is unit-tested.
 */
final class Hill {

    static final double STEEP = 0.06;     // 6 %: a ghat road
    static final double GENTLE = 0.03;    // back under 3 %: the hill is over
    static final double SPAN_M = 500;     // the slope is measured over this much road

    private final List<double[]> pts = new ArrayList<>();   // {metres driven, height}
    private double grade = Double.NaN;
    private int told;          // 1 climbing, -1 descending, 0 none since the last flat stretch
    private double flatFrom = -1;

    /** The slope now (rise over run, + uphill), or NaN until there is enough road. */
    double grade() {
        return grade;
    }

    /** One fix: the metres driven so far this drive and the GPS height; returns tips to speak, or null. */
    String feed(double metres, double height) {
        if (!pts.isEmpty() && metres < pts.get(pts.size() - 1)[0]) reset();   // the count started again
        pts.add(new double[]{metres, height});
        while (pts.size() > 2 && metres - pts.get(1)[0] >= SPAN_M) pts.remove(0);
        double[] first = pts.get(0);
        double run = metres - first[0];
        if (run < SPAN_M * 0.8) {
            grade = Double.NaN;
            return null;
        }
        // average the ends a little: the first and last few points
        grade = (avg(pts.size() - 3, pts.size()) - avg(0, 3)) / run;
        double a = Math.abs(grade);
        if (a < GENTLE) {
            if (flatFrom < 0) flatFrom = metres;
            if (metres - flatFrom > 1000) told = 0;   // a kilometre of flat road: the next hill is new
            return null;
        }
        flatFrom = -1;
        if (a < STEEP) return null;
        int dir = grade > 0 ? 1 : -1;
        if (told == dir) return null;
        told = dir;
        return dir > 0
                ? "Steep climb. Stay in a lower gear, and keep to your side at the bends."
                : "Steep road going down. Use a lower gear and let the engine slow the car; don't ride the brakes. "
                + "Vehicles climbing up have the right of way.";
    }

    private double avg(int from, int to) {
        from = Math.max(0, from);
        to = Math.min(pts.size(), to);
        double s = 0;
        for (int i = from; i < to; i++) s += pts.get(i)[1];
        return s / Math.max(1, to - from);
    }

    void reset() {
        pts.clear();
        grade = Double.NaN;
        told = 0;
        flatFrom = -1;
    }
}
