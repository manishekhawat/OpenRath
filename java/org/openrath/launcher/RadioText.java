package org.openrath.launcher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the band, frequency and station name out of a radio app's track text ("FM1 98.30",
 * "Radio Mirchi - 98.3 MHz", "AM 1040"), so the radio box shows the same big frequency on every
 * unit.
 */
final class RadioText {

    private static final Pattern FREQ = Pattern.compile(
            "(?:\\b(FM\\s?\\d?|AM|MW)\\b)?\\s*(\\d{2,4}(?:[.,]\\d{1,2})?)\\s*(MHz|kHz)?",
            Pattern.CASE_INSENSITIVE);

    /** What was found: band ("FM" or "AM"), the frequency as shown, and the station name, or "". */
    static final class Found {
        final String band, freq, station;

        Found(String band, String freq, String station) {
            this.band = band;
            this.freq = freq;
            this.station = station;
        }

        String big() {
            return band + " " + freq;
        }
    }

    /** The radio's text in, or null when it holds no frequency. */
    static Found parse(String title, String sub) {
        String all = ((title == null ? "" : title) + "  " + (sub == null ? "" : sub)).trim();
        if (all.isEmpty()) return null;
        Matcher m = FREQ.matcher(all);
        while (m.find()) {
            String num = m.group(2);
            if (num == null) continue;
            String bandText = m.group(1), unit = m.group(3);
            double f;
            try {
                f = Double.parseDouble(num.replace(',', '.'));
            } catch (NumberFormatException e) {
                continue;
            }
            String band;
            if (bandText != null) band = bandText.toUpperCase(Locale.US).startsWith("FM") ? "FM" : "AM";
            else if (unit != null) band = unit.equalsIgnoreCase("kHz") ? "AM" : "FM";
            else if (f >= 87.5 && f <= 108.0) band = "FM";
            else if (f >= 520 && f <= 1710 && num.indexOf('.') < 0 && num.indexOf(',') < 0) band = "AM";
            else continue;   // a song title's number, a year, not a frequency
            if ("FM".equals(band) && (f < 64 || f > 108.1)) continue;
            if ("AM".equals(band) && (f < 150 || f > 1800)) continue;
            String freq = "FM".equals(band) ? String.format(Locale.US, "%.1f", f) : String.format(Locale.US, "%.0f", f);
            String rest = (all.substring(0, m.start()) + " " + all.substring(m.end())).replaceAll("\\s+", " ");
            String station = trim(rest);
            return new Found(band, freq, station);
        }
        return null;
    }

    private static String trim(String s) {
        int a = 0, b = s.length();
        while (a < b && " -|/•:·".indexOf(s.charAt(a)) >= 0) a++;
        while (b > a && " -|/•:·".indexOf(s.charAt(b - 1)) >= 0) b--;
        return s.substring(a, b);
    }

    private RadioText() {
    }
}
