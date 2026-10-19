package org.openrath.launcher;

import java.util.ArrayDeque;

/**
 * The last few errors OpenRath caught and carried on from, for Settings > Help > Diagnostics. Kept in
 * memory only (lost when the app closes) and never sent anywhere. Not for vendor probes that are
 * expected to fail on most units, which would push the useful ones out.
 */
final class Diag {

    static final int KEEP = 30;
    private static final ArrayDeque<String> recent = new ArrayDeque<>();

    /** Note an error caught in `where` (a few words: "weather", "OBD read"). */
    static void note(String where, Throwable e) {
        String what = e == null ? "" : e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        String line = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + where + " " + what;
        if (line.length() > 200) line = line.substring(0, 200);
        synchronized (recent) {
            recent.addLast(line);
            while (recent.size() > KEEP) recent.removeFirst();
        }
    }

    /** The noted errors, newest first, one per line; "" when there are none. */
    static String recent() {
        StringBuilder b = new StringBuilder();
        synchronized (recent) {
            java.util.Iterator<String> it = recent.descendingIterator();
            while (it.hasNext()) b.append(it.next()).append('\n');
        }
        return b.toString();
    }

    static void clear() {
        synchronized (recent) {
            recent.clear();
        }
    }

    private Diag() {
    }
}
