package org.openrath.launcher;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hindi for the Settings screens, from Android's string resources: res/values/ui.xml holds the English
 * the code uses, res/values-hi/ui.xml the Hindi under the same names, and the ui_strings list pairs
 * them. Anything not translated stays in English, so a new setting never shows blank. The owner
 * picks the language in Settings; it starts as the stereo's own language.
 */
final class Hindi {

    private static Map<String, String> words;
    static boolean on;

    /** English to Hindi from the two resolutions of the ui_strings list; untranslated ones are left out. */
    static Map<String, String> pair(String[] en, String[] hi) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < Math.min(en.length, hi.length); i++)
            if (en[i] != null && hi[i] != null && !hi[i].isEmpty() && !hi[i].equals(en[i])) m.put(en[i], hi[i]);
        return m;
    }

    static String lang(Prefs p) {
        return p.str("lang", "hi".equals(Locale.getDefault().getLanguage()) ? "hi" : "en");
    }

    /** Called when Settings opens: reads the words once, when Hindi is chosen. */
    static void load(Context c, Prefs p) {
        on = "hi".equals(lang(p));
        if (!on || words != null) return;
        try {
            int id = c.getResources().getIdentifier("ui_strings", "array", c.getPackageName());
            words = pair(in(c, Locale.ENGLISH).getStringArray(id), in(c, new Locale("hi")).getStringArray(id));
        } catch (Exception e) {
            words = new HashMap<>();
        }
    }

    private static android.content.res.Resources in(Context c, Locale l) {
        android.content.res.Configuration conf = new android.content.res.Configuration(c.getResources().getConfiguration());
        conf.setLocale(l);
        return c.createConfigurationContext(conf).getResources();
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
