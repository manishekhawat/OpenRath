package org.openrath.launcher;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Hindi for the Settings screens, from assets/hi.tsv (English, a tab, then Hindi). Anything not in the
 * file stays in English, so a new setting never shows blank. The owner picks the language in Settings.
 */
final class Hindi {

    private static Map<String, String> words;
    static boolean on;

    static Map<String, String> parse(String text) {
        Map<String, String> m = new HashMap<>();
        for (String line : text.split("\n")) {
            if (line.startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String hi = line.substring(tab + 1).trim();
            if (!hi.isEmpty()) m.put(line.substring(0, tab), hi);
        }
        return m;
    }

    static String lang(Prefs p) {
        return p.str("lang", "hi".equals(java.util.Locale.getDefault().getLanguage()) ? "hi" : "en");
    }

    /** Called when Settings opens: reads the words once, when Hindi is chosen. */
    static void load(Context c, Prefs p) {
        on = "hi".equals(lang(p));
        if (!on || words != null) return;
        try (java.io.InputStream in = c.getAssets().open("hi.tsv")) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            words = parse(b.toString("UTF-8"));
        } catch (Exception e) {
            words = new HashMap<>();
        }
    }

    static String t(String s) {
        return t(words, on, s);
    }

    static String t(Map<String, String> m, boolean on, String s) {
        if (!on || m == null || s == null) return s;
        String h = m.get(s);
        return h == null ? s : h;
    }

    private Hindi() {
    }
}
