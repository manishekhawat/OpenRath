package org.openrath.launcher;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Routes driven, one small file per day in the app's own storage (kept 60 days, never sent
 * anywhere), exported on request as GPX to OpenRath/gpx for Google Earth, OsmAnd or Strava.
 * Also the 0–60 and 0–100 km/h timer, and volume that rises with speed.
 */
final class Tracks {

    private static final int KEEP_DAYS = 60;

    static File dir(Context c) {
        return new File(c.getFilesDir(), "tracks");
    }

    // ------------------------------------------------------------------ recording

    private static double lastLat, lastLon;
    private static long lastAt;

    /** From the trip service on each GPS fix; keeps a point every 5 s or 30 m while moving. */
    static void point(Context c, double lat, double lon, double alt, float kmh, long now) {
        if (kmh < 4) return;
        float[] d = new float[1];
        if (lastAt != 0) android.location.Location.distanceBetween(lastLat, lastLon, lat, lon, d);
        if (lastAt != 0 && now - lastAt < 5000 && d[0] < 30) return;
        lastLat = lat;
        lastLon = lon;
        lastAt = now;
        File f = new File(dir(c), day(now) + ".csv");
        try {
            f.getParentFile().mkdirs();
            Writer w = new FileWriter(f, true);
            w.write(String.format(Locale.US, "%d,%.6f,%.6f,%.0f,%.1f\n", now, lat, lon, alt, kmh));
            w.close();
        } catch (Exception ignored) {
        }
    }

    static String day(long t) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t));
    }

    /** Days with a recorded route, newest first. */
    static List<String> days(Context c) {
        List<String> out = new ArrayList<>();
        String[] l = dir(c).list();
        if (l == null) return out;
        Arrays.sort(l);
        for (int i = l.length - 1; i >= 0; i--) if (l[i].endsWith(".csv")) out.add(l[i].substring(0, l[i].length() - 4));
        return out;
    }

    static void prune(Context c) {
        List<String> d = days(c);
        for (int i = KEEP_DAYS; i < d.size(); i++) new File(dir(c), d.get(i) + ".csv").delete();
    }

    static void deleteAll(Context c) {
        for (String d : days(c)) new File(dir(c), d + ".csv").delete();
    }

    /** Write one day as GPX in OpenRath/gpx; a new segment starts after a 5-minute gap. */
    static File export(Context c, String day) throws Exception {
        File out = Storage.dir("gpx");
        if (!out.isDirectory() && !out.mkdirs()) throw new IllegalStateException("can't create " + out + " (allow storage access?)");
        File f = new File(out, "openrath-" + day + ".gpx");
        BufferedReader r = new BufferedReader(new FileReader(new File(dir(c), day + ".csv")));
        Writer w = new FileWriter(f);
        try {
            w.write(gpxHead("Drives on " + day));
            String line;
            long prev = 0;
            boolean open = false;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 5) continue;
                long t = Long.parseLong(p[0]);
                if (!open || t - prev > 5 * 60000) {
                    if (open) w.write("    </trkseg>\n");
                    w.write("    <trkseg>\n");
                    open = true;
                }
                w.write(gpxPoint(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), t));
                prev = t;
            }
            if (open) w.write("    </trkseg>\n");
            w.write("  </trk>\n</gpx>\n");
        } finally {
            r.close();
            w.close();
        }
        return f;
    }

    static String gpxHead(String name) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<gpx version=\"1.1\" creator=\"OpenRath\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
                + "  <trk>\n    <name>" + name.replace("&", "&amp;").replace("<", "&lt;") + "</name>\n";
    }

    static String gpxPoint(double lat, double lon, double ele, long t) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        return String.format(Locale.US, "      <trkpt lat=\"%.6f\" lon=\"%.6f\"><ele>%.0f</ele><time>%s</time></trkpt>\n",
                lat, lon, ele, iso.format(new Date(t)));
    }

    // ------------------------------------------------------------------ 0–100 timer

    /**
     * Times standing starts: armed after a second at a standstill, starts when the car moves,
     * and records the moment 60 and 100 km/h are passed (between two readings, the crossing
     * time is interpolated). A run that slows down or takes over 40 s is dropped.
     */
    static final class Timer {
        long stillSince, startAt, prevAt;
        float prevKmh;
        boolean armed, running, done60;
        float result60 = -1, result100 = -1;   // seconds, from the latest run

        /** Feed a speed reading; returns a finished 0–100 time in seconds, or -1. */
        float feed(float kmh, long now) {
            float out = -1;
            if (kmh < 2) {
                if (stillSince == 0) stillSince = now;
                if (now - stillSince >= 1000) armed = true;
                running = false;
            } else {
                stillSince = 0;
                if (armed && !running) {
                    running = true;
                    armed = false;
                    // the car pulled away somewhere between the last still reading and this one
                    startAt = prevAt != 0 && now - prevAt < 3000 ? prevAt + (now - prevAt) / 2 : now;
                    done60 = false;
                    result60 = result100 = -1;
                }
                if (running) {
                    if (kmh + 3 < prevKmh || now - startAt > 40000) running = false;   // backed off: not a full run
                    else {
                        if (!done60 && kmh >= 60) {
                            result60 = cross(60, kmh, now);
                            done60 = true;
                        }
                        if (kmh >= 100) {
                            result100 = cross(100, kmh, now);
                            out = result100;
                            running = false;
                        }
                    }
                }
            }
            prevKmh = kmh;
            prevAt = now;
            return out;
        }

        private float cross(float target, float kmh, long now) {
            float f = kmh <= prevKmh ? 1 : (target - prevKmh) / (kmh - prevKmh);
            long at = prevAt + (long) ((now - prevAt) * Math.min(1, Math.max(0, f)));
            return (at - startAt) / 1000f;
        }
    }

    // ------------------------------------------------------------------ volume with speed

    /** Extra volume steps for a speed: none below 40 km/h, then one more every `per` km/h, at most 6. */
    static int extraSteps(float kmh, int per) {
        if (per <= 0 || kmh < 40) return 0;
        return Math.min(6, 1 + (int) ((kmh - 40) / per));
    }

    private Tracks() {
    }
}
