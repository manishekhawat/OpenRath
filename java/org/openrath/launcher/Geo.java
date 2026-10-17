package org.openrath.launcher;

/** Distances and directions on the earth, with no Android types, so they can be unit-tested. */
final class Geo {

    /** Metres between two points. */
    static double dist(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000, p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Compass direction from the first point to the second, 0–360. */
    static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2), x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    /** Whether a point is within `m` metres and roughly ahead (±50°); heading < 0 means unknown, so anything near counts. */
    static boolean ahead(double lat, double lon, double heading, double pLat, double pLon, double m) {
        if (dist(lat, lon, pLat, pLon) > m) return false;
        if (heading < 0) return true;
        double diff = Math.abs(((bearing(lat, lon, pLat, pLon) - heading) % 360 + 540) % 360 - 180);
        return diff < 50;
    }

    /** A Google Maps link to a point, which opens on any phone. */
    static String mapsLink(double lat, double lon) {
        return String.format(java.util.Locale.US, "https://maps.google.com/?q=%.5f,%.5f", lat, lon);
    }

    /** Google Maps directions from one spot to another: family see the route and time on their phone. */
    static String routeLink(double lat, double lon, double toLat, double toLon) {
        return String.format(java.util.Locale.US,
                "https://www.google.com/maps/dir/?api=1&origin=%.5f,%.5f&destination=%.5f,%.5f&travelmode=driving",
                lat, lon, toLat, toLon);
    }

    private Geo() {
    }
}
