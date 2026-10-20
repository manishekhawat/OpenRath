package org.openrath.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What's on the road ahead, from OpenStreetMap through the Overpass API: toll plazas (a
 * countdown with the expected toll), one-way streets (a warning when driving against them),
 * schools and hospitals (slow down, no horn), and in fog the next bend on this road. The trip
 * service feeds every GPS fix to feed(); the map data is fetched again every few hundred metres.
 * The geometry functions have no Android types, so they are unit-tested.
 */
final class Ahead {

    /** A place ahead to speak about once: a toll plaza, school or hospital. */
    static final class Spot {
        final String kind, name;
        final double lat, lon;

        Spot(String kind, String name, double lat, double lon) {
            this.kind = kind;
            this.name = name == null ? "" : name;
            this.lat = lat;
            this.lon = lon;
        }

        String id() {
            return kind + String.format(Locale.US, "%.5f,%.5f", lat, lon);
        }
    }

    /** A road: its points in drawing order, and whether it is one-way (1), one-way backwards (-1) or not (0). */
    static final class Road {
        final List<double[]> pts = new ArrayList<>();
        int oneway;
        int lanes;   // 0 when the map doesn't say
        String name = "";
    }

    // ------------------------------------------------------------------ geometry

    /**
     * The nearest point on a road to (lat, lon): {metres away, bearing of the road's direction
     * there in degrees, segment index, fraction along the segment}, or null for an empty road.
     */
    static double[] nearest(List<double[]> pts, double lat, double lon) {
        double best = Double.MAX_VALUE, bearing = 0, seg = -1, frac = 0;
        double kx = Math.cos(Math.toRadians(lat)) * 111320, ky = 110540;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double ax = (pts.get(i)[1] - lon) * kx, ay = (pts.get(i)[0] - lat) * ky;
            double bx = (pts.get(i + 1)[1] - lon) * kx, by = (pts.get(i + 1)[0] - lat) * ky;
            double dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
            double px = ax + t * dx, py = ay + t * dy, d = Math.sqrt(px * px + py * py);
            if (d < best) {
                best = d;
                bearing = (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360;
                seg = i;
                frac = t;
            }
        }
        return seg < 0 ? null : new double[]{best, bearing, seg, frac};
    }

    /** The difference between two bearings, 0–180 degrees. */
    static double turn(double a, double b) {
        return Math.abs(((a - b) % 360 + 540) % 360 - 180);
    }

    /**
     * The next bend on this road in the direction of travel, within maxM metres:
     * {metres to it, +1 right or -1 left, how sharp in degrees}, or null when it runs straight.
     * A bend is the road turning 30 degrees or more within 80 metres.
     */
    static double[] nextBend(List<double[]> pts, double lat, double lon, double heading, double maxM) {
        double[] n = nearest(pts, lat, lon);
        if (n == null || heading < 0) return null;
        boolean forward = turn(n[1], heading) < 90;
        // the points ahead, in travel order, starting from where the car is
        List<double[]> ahead = new ArrayList<>();
        int seg = (int) n[2];
        ahead.add(new double[]{lat, lon});
        if (forward) for (int i = seg + 1; i < pts.size(); i++) ahead.add(pts.get(i));
        else for (int i = seg; i >= 0; i--) ahead.add(pts.get(i));
        double travelled = 0;
        double prevBearing = heading;
        for (int i = 0; i + 1 < ahead.size(); i++) {
            double[] a = ahead.get(i), b = ahead.get(i + 1);
            double len = Geo.dist(a[0], a[1], b[0], b[1]);
            if (len < 3) continue;
            double br = Geo.bearing(a[0], a[1], b[0], b[1]);
            if (i > 0) {
                // look over the next 80 m: how far the road swings from the bearing before this point
                double swing = 0, look = 0, dir = 0;
                for (int j = i; j + 1 < ahead.size() && look < 80; j++) {
                    double[] p = ahead.get(j), q = ahead.get(j + 1);
                    double l = Geo.dist(p[0], p[1], q[0], q[1]);
                    if (l < 3) continue;
                    double bj = Geo.bearing(p[0], p[1], q[0], q[1]);
                    double t = turn(prevBearing, bj);
                    if (t > swing) {
                        swing = t;
                        dir = ((bj - prevBearing + 540) % 360 - 180) > 0 ? 1 : -1;
                    }
                    look += l;
                }
                if (swing >= 30) return new double[]{travelled, dir, swing};
            }
            travelled += len;
            if (travelled > maxM) return null;
            prevBearing = br;
        }
        return null;
    }

    /** School hours on a weekday or Saturday: 7–9:30 in the morning and 12:30–3:30 in the afternoon. */
    static boolean schoolHours(Calendar c) {
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) return false;
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        return m >= 7 * 60 && m <= 9 * 60 + 30 || m >= 12 * 60 + 30 && m <= 15 * 60 + 30;
    }

    /** "Toll plaza in 2 kilometres, about 65 rupees. Keep to the FASTag lane." (the FASTag lane only in India) */
    static String tollText(double m, int rupees, String name) {
        String where = m >= 950 ? String.format(Locale.US, "%d kilometre%s", Math.round(m / 1000), Math.round(m / 1000) == 1 ? "" : "s")
                : (Math.round(m / 100) * 100) + " metres";
        return "Toll plaza" + (name.isEmpty() ? "" : ", " + name + ",") + " in " + where
                + (rupees > 0 ? ", about " + rupees + " " + Money.spoken() : "") + (Money.india() ? ". Keep to the FASTag lane." : ".");
    }

    // ------------------------------------------------------------------ the fetched map data

    final List<Spot> spots = new ArrayList<>();
    final List<Road> roads = new ArrayList<>();
    private final Set<String> told = new HashSet<>();
    private volatile double qLat, qLon;
    private volatile long qAt;
    private volatile boolean busy;
    private int againstFixes;
    private long wrongAt;

    /** The next bend for the fog screen: metres (or -1 when none), and +1 right / -1 left. */
    static volatile double bendM = -1;
    static volatile int bendDir;

    interface Fetch {
        String post(String query) throws Exception;
    }

    /**
     * One GPS fix. Returns sentences to speak (possibly none). wantToll, wantZones and fog
     * say which warnings are switched on; the map data is fetched in the background when needed.
     */
    List<String> feed(final double lat, final double lon, double heading, float kmh, boolean wantToll,
                      boolean wantZones, boolean fog, int tollRate, final Fetch net, Calendar now) {
        List<String> say = new ArrayList<>();
        if (!(wantToll || wantZones || fog)) {
            bendM = -1;
            return say;
        }
        long t = System.currentTimeMillis();
        boolean far = qAt == 0 || Geo.dist(qLat, qLon, lat, lon) > 350;
        // the gate spaces out questions to Overpass, shared with the speed alerts, and waits longer after errors
        if (!busy && kmh > 3 && (far || t - qAt > 180000) && t - qAt > 10000 && OverpassGate.SHARED.take(t))
            fetch(lat, lon, wantToll || wantZones, net);

        synchronized (this) {
            for (Spot s : spots) {
                double d = Geo.dist(lat, lon, s.lat, s.lon);
                boolean ahead = Geo.ahead(lat, lon, heading, s.lat, s.lon, 2500);
                if ("toll".equals(s.kind) && wantToll && ahead && d < 2200 && d > 250 && kmh > 15 && told.add(s.id()))
                    say.add(tollText(d, tollRate, s.name));
                if ("school".equals(s.kind) && wantZones && ahead && d < 250 && kmh > 25 && schoolHours(now) && told.add(s.id()))
                    say.add("School ahead. Please slow down.");
                if ("hospital".equals(s.kind) && wantZones && ahead && d < 250 && told.add(s.id()))
                    say.add("Hospital ahead. Please don't use the horn.");
            }
            // the road under the car: the nearest mapped road within 12 m
            Road on = null;
            double onD = 12, onBearing = 0;
            for (Road r : roads) {
                double[] n = nearest(r.pts, lat, lon);
                if (n != null && n[0] < onD) {
                    onD = n[0];
                    on = r;
                    onBearing = n[1];
                }
            }
            if (fog && on != null) {
                double[] b = nextBend(on.pts, lat, lon, heading, 400);
                bendM = b == null ? -1 : b[0];
                bendDir = b == null ? 0 : (int) b[1];
                if (b != null && b[0] < 250 && b[0] > 40 && kmh > 10 && told.add("bend" + Math.round(lat * 2000) + "," + Math.round(lon * 2000)))
                    say.add((b[2] > 60 ? "Sharp bend " : "Bend ") + (b[1] > 0 ? "to the right" : "to the left") + " ahead.");
            } else bendM = -1;
        }
        return say;
    }

    /** A new drive: places can be announced again. */
    synchronized void reset() {
        told.clear();
        againstFixes = 0;
    }

    private void fetch(final double lat, final double lon, final boolean places, final Fetch net) {
        busy = true;
        qAt = System.currentTimeMillis();
        qLat = lat;
        qLon = lon;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    // roads with their shape; places with a centre point
                    String q = String.format(Locale.US, "[out:json][timeout:15];"
                            + "way(around:400,%1$.6f,%2$.6f)[highway~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street)(_link)?$\"];"
                            + "out tags geom qt;"
                            + (places ? "(node(around:3000,%1$.6f,%2$.6f)[barrier=toll_booth];"
                            + "nwr(around:600,%1$.6f,%2$.6f)[amenity~\"^(school|kindergarten|hospital)$\"];);out tags center qt;" : ""), lat, lon);
                    JSONArray els = new JSONObject(net.post(q)).getJSONArray("elements");
                    List<Spot> sp = new ArrayList<>();
                    List<Road> rd = new ArrayList<>();
                    for (int i = 0; i < els.length(); i++) {
                        JSONObject e = els.getJSONObject(i);
                        JSONObject tags = e.optJSONObject("tags");
                        if (tags == null) continue;
                        String amenity = tags.optString("amenity");
                        if ("toll_booth".equals(tags.optString("barrier"))) {
                            sp.add(new Spot("toll", tags.optString("name"), e.optDouble("lat"), e.optDouble("lon")));
                        } else if (!amenity.isEmpty()) {
                            JSONObject c = e.optJSONObject("center");
                            double la = c != null ? c.optDouble("lat") : e.optDouble("lat"), lo = c != null ? c.optDouble("lon") : e.optDouble("lon");
                            if (Double.isNaN(la) || Double.isNaN(lo)) continue;
                            sp.add(new Spot("hospital".equals(amenity) ? "hospital" : "school", tags.optString("name"), la, lo));
                        } else if ("way".equals(e.optString("type")) && e.has("geometry")) {
                            Road r = new Road();
                            String ow = tags.optString("oneway");
                            r.oneway = "yes".equals(ow) || "1".equals(ow) || "true".equals(ow) ? 1 : "-1".equals(ow) ? -1 : 0;
                            if (r.oneway == 0 && ("motorway".equals(tags.optString("highway")) || tags.optString("junction").equals("roundabout")))
                                r.oneway = "no".equals(ow) ? 0 : 1;
                            r.name = tags.optString("name");
                            r.lanes = tags.optInt("lanes", 0);
                            JSONArray g = e.getJSONArray("geometry");
                            for (int k = 0; k < g.length(); k++)
                                r.pts.add(new double[]{g.getJSONObject(k).optDouble("lat"), g.getJSONObject(k).optDouble("lon")});
                            rd.add(r);
                        }
                    }
                    synchronized (Ahead.this) {
                        if (places) {
                            spots.clear();
                            spots.addAll(sp);
                        }
                        roads.clear();
                        roads.addAll(rd);
                    }
                    OverpassGate.SHARED.worked();
                } catch (Throwable e) {
                    // try again in ten seconds, or later when the gate says so (it waits longer after each failure)
                    OverpassGate.SHARED.failed(System.currentTimeMillis(), OverpassGate.code(e));
                    qAt = System.currentTimeMillis() - 170000;
                } finally {
                    busy = false;
                }
            }
        });
    }
}
