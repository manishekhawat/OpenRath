package org.openrath.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Good manners towards the free Overpass API, shared by the speed alerts (Drive) and the road
 * ahead (Ahead): at least 8 seconds between any two questions, and after a failed one a wait that
 * doubles each time (a minute at least when the server says it is busy), up to 5 minutes, until a
 * question gets an answer again. Speed cameras are kept for each square of about 5 km, in memory
 * and in the app's cache folder for a week, so driving there again needn't ask for them again.
 * No Android types, so it is unit-tested.
 */
final class OverpassGate {

    static final long GAP_MS = 8000, FIRST_WAIT_MS = 10000, BUSY_WAIT_MS = 60000, MAX_WAIT_MS = 5 * 60000;

    /** The one the app uses. */
    static final OverpassGate SHARED = new OverpassGate();

    private long nextAt, wait;

    /** Whether a question may go out now. */
    synchronized boolean may(long now) {
        return now >= nextAt;
    }

    /** True, and counted as sent, when a question may go out now; false to wait. */
    synchronized boolean take(long now) {
        if (now < nextAt) return false;
        nextAt = now + GAP_MS;
        return true;
    }

    /** An answer came: no more waiting than the usual gap. */
    synchronized void worked() {
        wait = 0;
    }

    /** No answer: code is the server's HTTP status, or 0 when it couldn't be reached. */
    synchronized void failed(long now, int code) {
        boolean busy = code == 429 || code == 503 || code == 504;
        wait = wait == 0 ? FIRST_WAIT_MS : Math.min(MAX_WAIT_MS, wait * 2);
        if (busy) wait = Math.max(wait, BUSY_WAIT_MS);
        nextAt = Math.max(nextAt, now + wait);
    }

    /** How long the last failure made us wait, in ms (0 after an answer). */
    synchronized long waiting() {
        return wait;
    }

    /** An HTTP error from the server, so failed() can tell "too many requests" from the rest. */
    static final class HttpError extends java.io.IOException {
        final int code;

        HttpError(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    /** The HTTP status behind an error, or 0. */
    static int code(Throwable t) {
        return t instanceof HttpError ? ((HttpError) t).code : 0;
    }

    // ------------------------------------------------------------------ speed cameras by square

    /** A square's side in degrees: about 5.5 km. */
    static final double CELL = 0.05;
    /** How far past its square a square's cameras are fetched, in degrees (about 1 km). */
    static final double MARGIN = 0.01;
    static final long KEEP_MS = 7L * 24 * 3600 * 1000;

    /** The square a place is in, as "row_column". */
    static String cell(double lat, double lon) {
        return (long) Math.floor(lat / CELL) + "_" + (long) Math.floor(lon / CELL);
    }

    /** {south, west, north, east} of a square with the margin around it, for an Overpass bbox. */
    static double[] box(String cell) {
        String[] p = cell.split("_");
        double s = Long.parseLong(p[0]) * CELL, w = Long.parseLong(p[1]) * CELL;
        return new double[]{s - MARGIN, w - MARGIN, s + CELL + MARGIN, w + CELL + MARGIN};
    }

    /** Overpass's "(south,west,north,east)" for a square. */
    static String bbox(String cell) {
        double[] b = box(cell);
        return String.format(Locale.US, "(%.5f,%.5f,%.5f,%.5f)", b[0], b[1], b[2], b[3]);
    }

    /** {lat, lon, limit} for each camera as text: the time it was fetched, then a line each. */
    static String write(List<double[]> cams, long at) {
        StringBuilder b = new StringBuilder().append(at).append('\n');
        for (double[] c : cams) b.append(String.format(Locale.US, "%.6f,%.6f,%d\n", c[0], c[1], (int) c[2]));
        return b.toString();
    }

    /** The cameras in text from write(), or null when it is older than a week or unreadable. */
    static List<double[]> read(String text, long now) {
        if (text == null) return null;
        String[] lines = text.split("\n");
        try {
            long at = Long.parseLong(lines[0].trim());
            if (now - at > KEEP_MS || at > now + 3600000L) return null;
            List<double[]> out = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].trim().isEmpty()) continue;
                String[] f = lines[i].split(",");
                out.add(new double[]{Double.parseDouble(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2])});
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** Cameras kept in memory (the last 32 squares) and as files in a folder. */
    static final class Cameras {
        private final Map<String, Object[]> mem = new LinkedHashMap<String, Object[]>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Object[]> e) {
                return size() > 32;
            }
        };

        /** From memory only (cheap enough for the screen's thread), or null. */
        @SuppressWarnings("unchecked")
        synchronized List<double[]> remembered(String cell, long now) {
            Object[] o = mem.get(cell);
            if (o == null) return null;
            if (now - (Long) o[0] > KEEP_MS) {
                mem.remove(cell);
                return null;
            }
            return (List<double[]>) o[1];
        }

        /** From memory, else from the folder when the copy is less than a week old, or null. */
        List<double[]> kept(String cell, File dir, long now) {
            List<double[]> m = remembered(cell, now);
            if (m != null || dir == null) return m;
            File f = new File(dir, "cams-" + cell + ".txt");
            if (!f.exists()) return null;
            List<double[]> d = read(readFile(f), now);
            if (d == null) {
                f.delete();
                return null;
            }
            synchronized (this) {
                mem.put(cell, new Object[]{f.lastModified() > 0 ? Math.min(now, f.lastModified()) : now, d});
            }
            return d;
        }

        /** Keep a fresh answer, in memory and in the folder. */
        void keep(String cell, List<double[]> cams, File dir, long now) {
            synchronized (this) {
                mem.put(cell, new Object[]{now, cams});
            }
            if (dir == null) return;
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) return;
                java.io.FileOutputStream o = new java.io.FileOutputStream(new File(dir, "cams-" + cell + ".txt"));
                try {
                    o.write(write(cams, now).getBytes("UTF-8"));
                } finally {
                    o.close();
                }
                // copies older than a week go
                File[] all = dir.listFiles();
                if (all != null) for (File x : all) if (now - x.lastModified() > KEEP_MS) x.delete();
            } catch (Exception ignored) {
            }
        }

        synchronized int size() {
            return mem.size();
        }

        synchronized void forget() {
            mem.clear();
        }
    }

    private static String readFile(File f) {
        try {
            java.io.InputStream in = new java.io.FileInputStream(f);
            try {
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
                return o.toString("UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
