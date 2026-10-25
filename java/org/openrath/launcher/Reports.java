package org.openrath.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Opt-in crash and usage reports (the "reports" switch, off by default). While it's on, OpenRath
 * counts which settings get opened and how often it starts, on the unit only. The owner shares the
 * report from Settings, or a fleet sets its own https address (managed settings) and the unit sends
 * it there once a day. Nothing goes to OpenRath's makers, and nothing is counted while it's off.
 * A report holds the counts, the app and Android versions, the unit's model and a random id made
 * when reports are turned on: no position, no names, no numbers.
 */
final class Reports {

    private static SharedPreferences sp;
    private static Prefs prefs;

    static void install(Context c, Prefs p) {
        sp = c.getApplicationContext().getSharedPreferences("reports", Context.MODE_PRIVATE);
        prefs = p;
        count("app start");
    }

    static boolean on() {
        return prefs != null && Features.on(prefs, "reports");
    }

    /** One more use of `what`, if reports are on. */
    static void count(String what) {
        if (!on() || sp == null) return;
        if (sp.getString("id", null) == null)
            sp.edit().putString("id", java.util.UUID.randomUUID().toString()).putLong("since", System.currentTimeMillis()).apply();
        sp.edit().putInt("n:" + what, sp.getInt("n:" + what, 0) + 1).apply();
    }

    /** The counts, most used first. */
    static Map<String, Integer> counts() {
        Map<String, Integer> out = new TreeMap<>();
        if (sp == null) return out;
        for (Map.Entry<String, ?> e : sp.getAll().entrySet())
            if (e.getKey().startsWith("n:") && e.getValue() instanceof Integer) out.put(e.getKey().substring(2), (Integer) e.getValue());
        return out;
    }

    /** The report as plain text, for Share and for a fleet's server. */
    static String text(String version) {
        StringBuilder b = new StringBuilder();
        b.append("OpenRath ").append(version).append(" usage report\n")
                .append("Unit: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                .append(", Android ").append(android.os.Build.VERSION.RELEASE).append('\n')
                .append("Report id: ").append(sp == null ? "-" : sp.getString("id", "-")).append('\n')
                .append("Counted since: ").append(sp == null ? "-" : new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(new java.util.Date(sp.getLong("since", System.currentTimeMillis())))).append("\n\n");
        b.append(format(counts()));
        java.util.List<File> crashes = Crash.files();
        b.append("\nCrash reports on the unit: ").append(crashes.size()).append('\n');
        for (File f : crashes) b.append("\n---- ").append(f.getName()).append('\n').append(Crash.read(f)).append('\n');
        return b.toString();
    }

    /** "what: count" lines, most used first. Kept apart from Android for the tests. */
    static String format(Map<String, Integer> counts) {
        java.util.List<Map.Entry<String, Integer>> l = new java.util.ArrayList<>(counts.entrySet());
        java.util.Collections.sort(l, new java.util.Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                int c = b.getValue().compareTo(a.getValue());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            }
        });
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : l) b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        return b.toString();
    }

    /** Whether `url` is somewhere reports may go: a well-formed https address with a host, and no name or password in it. */
    static boolean sendable(String url) {
        if (url == null) return false;
        try {
            // URI, not URL: URL takes "https:// bad" with a host of " bad" on some Java versions
            java.net.URI u = new java.net.URI(url.trim());
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && !u.getHost().isEmpty()
                    && u.getRawUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Once a day, when reports are on and a fleet gave an address: send the report there. */
    static void maybeSend(final String version) {
        if (!on()) return;
        final String url = prefs.str("reportUrl", "");
        if (!sendable(url) || System.currentTimeMillis() - sp.getLong("sent", 0) < 24 * 3600_000L) return;
        Bg.net(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection c = Compat.https(url.trim());
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    c.setDoOutput(true);
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                    OutputStream o = c.getOutputStream();
                    o.write(text(version).getBytes("UTF-8"));
                    o.close();
                    if (c.getResponseCode() / 100 == 2) sp.edit().putLong("sent", System.currentTimeMillis()).apply();
                    c.disconnect();
                } catch (Throwable e) {
                    Diag.note("report send", e);
                }
            }
        });
    }

    /** Turning reports off forgets everything counted. */
    static void clear() {
        if (sp != null) sp.edit().clear().apply();
    }

    private Reports() {
    }
}
