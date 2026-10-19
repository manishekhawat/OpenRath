package org.openrath.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the stock radio app (com.tw.radio) writes for itself: /data/tw/radio_freq, the station it is
 * on (first line), and /data/tw/radio_freqs, the stations its own scan found (one a line). On units
 * where the radio service never answers, these are the only way to see the frequency.
 */
final class StockRadio {

    static final String NOW = "/data/tw/radio_freq", LIST = "/data/tw/radio_freqs";
    private static long readAt, seenModified;
    private static String cached = "";

    /** "91.1", or "" when the file can't be read. Read at most every 700 ms. */
    static String now() {
        long t = System.currentTimeMillis();
        if (t - readAt < 700) return cached;
        readAt = t;
        File f = new File(NOW);
        if (!f.canRead()) return cached = "";
        if (f.lastModified() == seenModified && !cached.isEmpty()) return cached;
        seenModified = f.lastModified();
        List<String> l = lines(f);
        return cached = l.isEmpty() ? "" : text(l.get(0));
    }

    /** The stations the stock radio found, low to high, as "91.1". */
    static List<String> stations() {
        List<String> out = new ArrayList<>();
        for (String s : lines(new File(LIST))) {
            String t = text(s);
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return Scan.stations(out);
    }

    /** One number from the radio's files, in whatever unit it uses, as "91.1"; "" if it isn't FM. */
    static String text(String line) {
        try {
            float f = Xt.fmMhz(Integer.parseInt(line.trim()));
            return f >= 87.5f && f <= 108f ? String.format(Locale.US, "%.1f", f) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> lines(File f) {
        List<String> l = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            for (String s; (s = r.readLine()) != null && l.size() < 200; ) if (!s.trim().isEmpty()) l.add(s.trim());
        } catch (Exception ignored) {
            // not on this unit
        }
        return l;
    }

    private StockRadio() {
    }
}
