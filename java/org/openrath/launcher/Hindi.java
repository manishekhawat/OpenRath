package org.openrath.launcher;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hindi for everything OpenRath shows, from Android's string resources: res/values/ui*.xml hold the
 * English the code uses, res/values-hi/ui*.xml the Hindi under the same names, and each file's list
 * (ARRAYS) pairs them. Code passes its English through t(), or f() for text with numbers or names
 * in it ("%1$s km"). Anything not translated stays in English, so new text never shows blank. The
 * owner picks the language in Settings; it starts as the stereo's own language.
 */
final class Hindi {

    /** One list per resource file: Settings, then the dashboard and the rest of the app. */
    static final String[] ARRAYS = {"ui_strings", "ui_home", "ui_cards", "ui_cab", "ui_driving", "ui_extras", "ui_more"};

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
        Money.load(c, p);
        on = "hi".equals(lang(p));
        if (!on || words != null) return;
        words = new HashMap<>();
        android.content.res.Resources en = in(c, Locale.ENGLISH), hi = in(c, new Locale("hi"));
        for (String name : ARRAYS) {
            try {
                int id = c.getResources().getIdentifier(name, "array", c.getPackageName());
                if (id != 0) words.putAll(pair(en.getStringArray(id), hi.getStringArray(id)));
            } catch (Exception e) {
                Diag.note("Hindi words", e);
            }
        }
    }

    private static android.content.res.Resources in(Context c, Locale l) {
        android.content.res.Configuration conf = new android.content.res.Configuration(c.getResources().getConfiguration());
        conf.setLocale(l);
        return c.createConfigurationContext(conf).getResources();
    }

    static String t(String s) {
        return Money.shown(t(words, on, s));
    }

    /** Text with numbers or names in it: the English format ("Toll in %1$s km") is what gets translated. */
    static String f(String format, Object... args) {
        try {
            return Money.shown(String.format(Locale.US, t(words, on, format), args));
        } catch (Exception e) {
            return Money.shown(String.format(Locale.US, format, args));
        }
    }

    /** t() for a CharSequence, for code that sets text either way. */
    static CharSequence t(CharSequence s) {
        return s instanceof String ? t((String) s) : s;
    }

    static String t(Map<String, String> m, boolean on, String s) {
        if (!on || m == null || s == null) return s;
        String h = m.get(s);
        return h == null ? s : h;
    }

    private Hindi() {
    }
}
