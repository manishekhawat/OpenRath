package org.openrath.launcher;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Small helpers with no Android types (so they are unit-tested): the car wash reminder, the UPI
 * link for splitting a trip cost, the phone reminder link for paid parking, and the start-up
 * summary.
 */
final class Errands {

    // ------------------------------------------------------------------ car wash

    /**
     * Whether to suggest a wash: it has been at least `every` days since the last one and no rain
     * is expected in the next two days (a wash just before rain is wasted).
     */
    static boolean washDue(long lastWash, long now, int every, List<String[]> forecast) {
        if (lastWash <= 0) return false;   // never marked: don't nag until the owner starts it
        long days = (now - lastWash) / 86400000L;
        if (days < every) return false;
        if (forecast != null) {
            for (int i = 0; i < forecast.size() && i < 3; i++) {
                String icon = forecast.get(i)[1];
                if ("w_rain".equals(icon) || "w_storm".equals(icon)) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ splitting the cost

    /** Each person's share in rupees, rounded up to the next rupee. */
    static int share(double total, int people) {
        return people <= 0 ? 0 : (int) Math.ceil(total / people - 1e-9);
    }

    /** A UPI payment link any UPI app opens with the amount filled in. */
    static String upiLink(String vpa, String name, int amount, String note) {
        return "upi://pay?pa=" + enc(vpa.trim()) + "&pn=" + enc(name.trim().isEmpty() ? vpa.trim() : name.trim())
                + "&am=" + amount + ".00&cu=INR&tn=" + enc(note);
    }

    /** A UPI id looks like name@bank. */
    static boolean validVpa(String v) {
        return v != null && v.trim().matches("[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,64}");
    }

    private static String enc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c) && c < 128 || c == '.' || c == '-' || c == '_' || c == '@') b.append(c);
            else if (c == ' ') b.append("%20");
            else {
                byte[] bytes = String.valueOf(c).getBytes(java.nio.charset.Charset.forName("UTF-8"));
                for (byte x : bytes) b.append(String.format(Locale.US, "%%%02X", x & 0xFF));
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ paid parking

    /**
     * A Google Calendar link for the phone: an event that ends when the parking does, with a
     * reminder before it. Times in UTC as the link needs them.
     */
    static String parkingLink(long start, int minutes) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        long end = start + minutes * 60000L;
        long remind = Math.max(start, end - 10 * 60000L);
        return "https://calendar.google.com/calendar/render?action=TEMPLATE&text=" + enc("Parking ends")
                + "&dates=" + f.format(new java.util.Date(remind)) + "/" + f.format(new java.util.Date(end))
                + "&details=" + enc("Back to the car by " + clock(end));
    }

    static String clock(long t) {
        return new java.text.SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date(t));
    }

    // ------------------------------------------------------------------ start-up summary

    static String greeting(Calendar c) {
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h < 5 ? "Hello" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
    }

    /**
     * One short paragraph for the first start of the day, from whatever is known; parts that
     * aren't known are left out.
     */
    static String summary(Calendar now, String name, String temp, String weather, String rain, int aqi,
                          double rangeKm, String care, String holiday) {
        StringBuilder b = new StringBuilder(greeting(now));
        if (name != null && !name.isEmpty()) b.append(", ").append(name);
        b.append(".");
        if (temp != null && !temp.isEmpty() && !"--".equals(temp))
            b.append(" It's ").append(temp.replace("°", " degrees")).append(weather == null || weather.isEmpty() ? "" : " and " + weather.toLowerCase(Locale.US)).append(".");
        if (rain != null && !rain.isEmpty()) b.append(" ").append(rain).append(".");
        if (aqi > 200) b.append(" The air is ").append(Air.band(aqi).toLowerCase(Locale.US)).append(", A Q I ").append(aqi).append(".");
        if (rangeKm >= 0) b.append(" Petrol for about ").append(Math.round(rangeKm / 10) * 10).append(" kilometres.");
        if (care != null && !care.isEmpty()) b.append(" ").append(care.substring(0, 1)).append(care.substring(1).toLowerCase(Locale.US)).append(".");
        if (holiday != null && !holiday.isEmpty()) b.append(" Coming up: ").append(holiday).append(".");
        return b.toString();
    }

    // ------------------------------------------------------------------ fuel theft or leak

    /**
     * Km per litre for each stretch between full tanks, oldest first: from the trip odometer at
     * each fill-up, the litres put in after the earlier full tank, and which were full.
     */
    static List<Double> fullTankKmpl(double[] km, double[] litres, boolean[] full) {
        List<Double> out = new java.util.ArrayList<>();
        int last = -1;
        double l = 0;
        for (int i = 0; i < km.length; i++) {
            if (last >= 0) l += litres[i];
            if (!full[i]) continue;
            if (last >= 0 && l > 0 && km[i] > km[last]) out.add((km[i] - km[last]) / l);
            last = i;
            l = 0;
        }
        return out;
    }

    /**
     * A warning when the latest full-tank mileage is under 75% of the usual (the median of up to
     * six earlier ones, at least three), or null.
     */
    static String fuelDrop(List<Double> kmpl) {
        if (kmpl.size() < 4) return null;
        double now = kmpl.get(kmpl.size() - 1);
        List<Double> before = new java.util.ArrayList<>(kmpl.subList(Math.max(0, kmpl.size() - 7), kmpl.size() - 1));
        java.util.Collections.sort(before);
        double usual = before.get(before.size() / 2);
        if (now >= usual * 0.75) return null;
        return String.format(Locale.US, "The last tank gave %.1f km per litre, well below your usual %.1f. "
                + "If you didn't drive differently, check for a fuel leak, or that the pump filled what it showed.", now, usual);
    }

    // ------------------------------------------------------------------ stops

    /** "At the stop ahead you usually wait about 2 and a half minutes." */
    static String waitText(long sec) {
        if (sec < 90) return "At the stop ahead you usually wait about a minute.";
        long halves = Math.round(sec / 30.0);
        String m = halves / 2 + (halves % 2 == 1 ? " and a half" : "");
        return "At the stop ahead you usually wait about " + m + " minutes.";
    }

    // ------------------------------------------------------------------ headlights

    /** Remind once a drive: it's dark, the car is moving, and the headlights are known to be off (or not known at all). */
    static String lightsReminder(boolean dark, float kmh, boolean known, boolean on, boolean told) {
        if (told || !dark || kmh < 20 || known && on) return null;
        return known ? "It's dark and your headlights look off. Please switch them on."
                : "It's getting dark. Remember your headlights.";
    }

    // ------------------------------------------------------------------ sound profiles

    static final String[] SOUND = {"city", "highway", "night"};

    /** Which sound profile fits now: highway above all, then night, else city. */
    static String soundProfile(boolean highway, boolean dark) {
        return highway ? "highway" : dark ? "night" : "city";
    }

    // ------------------------------------------------------------------ weekly report

    /** The week's report as lines: {km, hours, fuel ₹, tolls ₹, score, best, worst}; empty parts are skipped. */
    static String weekly(double km, long seconds, double fuelRs, double tollRs, int score, String best, String worst) {
        StringBuilder b = new StringBuilder();
        b.append(Hindi.f("Driven: %1$s km in %2$s h %3$s min", String.format(Locale.US, "%.0f", km), seconds / 3600,
                String.format(Locale.US, "%02d", seconds % 3600 / 60)));
        if (km > 0 && seconds > 0) b.append(Hindi.f(" (average %1$s km/h)", String.format(Locale.US, "%.0f", km / (seconds / 3600.0))));
        b.append('\n');
        if (fuelRs > 0) b.append(Hindi.f("Petrol: ₹%1$s", String.format(Locale.US, "%,.0f", fuelRs)))
                .append(km > 0 ? Hindi.f(" (₹%1$s a km)", String.format(Locale.US, "%.1f", fuelRs / km)) : "").append('\n');
        if (tollRs > 0) b.append(Hindi.f("Tolls: ₹%1$s", String.format(Locale.US, "%,.0f", tollRs))).append('\n');
        if (score >= 0) b.append(Hindi.f("Driving score: %1$s out of 100", score)).append('\n');
        if (best != null && !best.isEmpty()) b.append(Hindi.f("Best drive: %1$s", best)).append('\n');
        if (worst != null && !worst.isEmpty()) b.append(Hindi.f("Needs work: %1$s", worst)).append('\n');
        return b.toString().trim();
    }

    // ------------------------------------------------------------------ guest Wi-Fi

    /** The text a phone camera reads to join a Wi-Fi network. */
    static String wifiQr(String ssid, String password) {
        String s = wifiEsc(ssid), p = wifiEsc(password);
        return password.isEmpty() ? "WIFI:T:nopass;S:" + s + ";;" : "WIFI:T:WPA;S:" + s + ";P:" + p + ";;";
    }

    private static String wifiEsc(String v) {
        return v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"");
    }

    private Errands() {
    }
}
