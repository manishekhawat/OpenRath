package org.openrath.launcher;

/**
 * Air quality alerts: the Indian AQI (CPCB scale) from Open-Meteo's PM2.5 and PM10 readings,
 * fetched with the weather. When the air turns Poor or worse the car says so and suggests
 * switching the AC to recirculate, at most once every three hours.
 */
final class Air {

    static volatile int aqi = -1;

    private static final double[][] PM25 = {{0, 30, 0, 50}, {30, 60, 51, 100}, {60, 90, 101, 200}, {90, 120, 201, 300},
            {120, 250, 301, 400}, {250, 500, 401, 500}};
    private static final double[][] PM10 = {{0, 50, 0, 50}, {50, 100, 51, 100}, {100, 250, 101, 200}, {250, 350, 201, 300},
            {350, 430, 301, 400}, {430, 600, 401, 500}};

    private static int sub(double c, double[][] t) {
        if (c < 0 || Double.isNaN(c)) return -1;
        for (double[] r : t) {
            if (c <= r[1]) return (int) Math.round(r[2] + (r[3] - r[2]) * (c - r[0]) / (r[1] - r[0]));
        }
        return 500;
    }

    /** The AQI from PM2.5 and PM10 in µg/m³ (either may be NaN), or -1. */
    static int index(double pm25, double pm10) {
        return Math.max(sub(pm25, PM25), sub(pm10, PM10));
    }

    static String band(int aqi) {
        if (aqi < 0) return "unknown";
        if (aqi <= 50) return "Good";
        if (aqi <= 100) return "Satisfactory";
        if (aqi <= 200) return "Moderate";
        if (aqi <= 300) return "Poor";
        if (aqi <= 400) return "Very poor";
        return "Severe";
    }

    /** What to say for a new reading, or null: only for Poor or worse, and not again within three hours. */
    static String alert(int aqi, long now, long lastAt) {
        if (aqi <= 200 || now - lastAt < 3 * 3600000L) return null;
        return "The air outside is " + band(aqi).toLowerCase(java.util.Locale.US) + ", A Q I " + aqi
                + ". Close the windows and set the AC to recirculate.";
    }

    static String url(double lat, double lon) {
        return String.format(java.util.Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.3f&longitude=%.3f&current=pm2_5,pm10", lat, lon);
    }

    private Air() {
    }
}
