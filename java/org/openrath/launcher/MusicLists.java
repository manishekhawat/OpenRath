package org.openrath.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The sums behind the radio and music screen (MusicScreen): FM presets, tuning a step, favourite
 * stations first, songs grouped by artist or folder, and the search. No Android types, so it is
 * unit-tested.
 */
final class MusicLists {

    /** The FM band, in tenths of a MHz. */
    static final int LOW = 875, HIGH = 1080;

    /**
     * One preset as "98.3", from whatever a list or a radio holds ("98.3 FM", "98,3", "983" or
     * "98300" kHz); "" when it isn't an FM station.
     */
    static String freq(String s) {
        if (s == null) return "";
        String t = s.toUpperCase(Locale.US).replace("MHZ", "").replace("FM", "").replace(',', '.').trim();
        if (t.isEmpty()) return "";
        float f;
        try {
            f = Float.parseFloat(t);
        } catch (NumberFormatException e) {
            return "";
        }
        if (t.indexOf('.') < 0) {
            // whole numbers as the radios write them: 983 (tenths), 9830 (hundredths), 98300 (kHz)
            if (f >= 87500 && f <= 108000) f /= 1000;
            else if (f >= 8750 && f <= 10800) f /= 100;
            else if (f >= 875 && f <= 1080) f /= 10;
        }
        int tenths = Math.round(f * 10);
        if (tenths < LOW || tenths > HIGH) return "";
        return text(tenths);
    }

    private static String text(int tenths) {
        return (tenths / 10) + "." + (tenths % 10);
    }

    /**
     * The presets to show: the ones saved by hand (a comma list) and the ones a scan found, each once,
     * low to high, anything that isn't an FM station left out.
     */
    static List<String> presets(String saved, List<String> scanned) {
        Set<String> all = new LinkedHashSet<>();
        if (saved != null) for (String s : saved.split(",")) {
            String f = freq(s);
            if (!f.isEmpty()) all.add(f);
        }
        if (scanned != null) for (String s : scanned) {
            String f = freq(s);
            if (!f.isEmpty()) all.add(f);
        }
        List<String> out = new ArrayList<>(all);
        Collections.sort(out, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Float.compare(Float.parseFloat(a), Float.parseFloat(b));
            }
        });
        return out;
    }

    /** `steps` tenths up (or down) from a station, going round the band at either end. */
    static String step(String from, int steps) {
        String f = freq(from);
        int t = f.isEmpty() ? 983 : Math.round(Float.parseFloat(f) * 10);
        int span = HIGH - LOW + 1;
        t = LOW + ((t - LOW + steps) % span + span) % span;
        return text(t);
    }

    /** A list kept as text, one item a line or after a comma: `item` added when missing, else taken out. */
    static String toggle(String list, String item, String sep) {
        List<String> l = items(list, sep);
        if (!l.remove(item)) l.add(item);
        return join(l, sep);
    }

    /** The list without `item`. */
    static String without(String list, String item, String sep) {
        List<String> l = items(list, sep);
        l.remove(item);
        return join(l, sep);
    }

    static List<String> items(String list, String sep) {
        List<String> l = new ArrayList<>();
        if (list == null) return l;
        for (String s : list.split(java.util.regex.Pattern.quote(sep))) {
            String t = s.trim();
            if (!t.isEmpty() && !l.contains(t)) l.add(t);
        }
        return l;
    }

    private static String join(List<String> l, String sep) {
        StringBuilder b = new StringBuilder();
        for (String s : l) b.append(b.length() == 0 ? "" : sep).append(s);
        return b.toString();
    }

    /** The order to list stations in, as indexes into `names`: favourites first, otherwise as given. */
    static List<Integer> favFirst(List<String> names, Set<String> favs) {
        List<Integer> top = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) (favs.contains(names.get(i)) ? top : rest).add(i);
        top.addAll(rest);
        return top;
    }

    /** The folder a file is in, by name: "/storage/usb/Old Hindi/x.mp3" is "Old Hindi". */
    static String folder(String path) {
        if (path == null) return "";
        int end = path.lastIndexOf('/');
        if (end <= 0) return "";
        int start = path.lastIndexOf('/', end - 1);
        return path.substring(start + 1, end);
    }

    /**
     * A song's artist: what the music list says, except for files found by walking the USB stick,
     * whose second line is their folder's name (then the artist isn't known).
     */
    static String artist(String sub, String path) {
        if (sub == null) return "";
        String s = sub.trim();
        return s.equals(folder(path)) ? "" : s;
    }

    /**
     * Songs grouped under a name (an artist or a folder), as indexes, the names A to Z without regard
     * to case; songs without one go under "".
     */
    static Map<String, List<Integer>> group(List<String> keys) {
        Map<String, List<Integer>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i) == null ? "" : keys.get(i).trim();
            List<Integer> l = out.get(k);
            if (l == null) out.put(k, l = new ArrayList<>());
            l.add(i);
        }
        return out;
    }

    /** The songs whose name or second line has every word typed, as indexes; all of them for an empty search. */
    static List<Integer> search(List<String> names, List<String> subs, String typed) {
        List<Integer> out = new ArrayList<>();
        String q = typed == null ? "" : typed.toLowerCase(Locale.US).trim();
        String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
        for (int i = 0; i < names.size(); i++) {
            String hay = (names.get(i) + " " + (i < subs.size() ? subs.get(i) : "")).toLowerCase(Locale.US);
            boolean all = true;
            for (String w : words) if (!hay.contains(w)) all = false;
            if (all) out.add(i);
        }
        return out;
    }

    /** "8 of 15" volume as a share of the bar, 0 to 1. */
    static float share(int now, int max) {
        return max <= 0 ? 0 : Math.max(0, Math.min(1, now / (float) max));
    }

    private MusicLists() {
    }
}
