package org.openrath.launcher;

import java.util.ArrayList;
import java.util.List;

/** The latest message for the ticker, and which apps it listens to. */
final class Ticker {

    static final String CHANGED = "org.openrath.launcher.TICKER";
    /** Messaging apps listened to until the owner picks their own. */
    static final String[] DEFAULT_APPS = {"com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
            "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging", "com.truecaller",
            "com.instagram.android", "com.facebook.orca", "com.google.android.gm", "in.org.npci.upiapp",
            "net.one97.paytm", "com.phonepe.app", "com.google.android.apps.nbu.paisa.user"};

    static volatile String app = "", title = "", text = "";
    static volatile long at;

    static List<String> apps(Prefs p) {
        List<String> out = new ArrayList<>();
        String s = p.str("tickerApps", "");
        if (s.isEmpty()) for (String a : DEFAULT_APPS) out.add(a);
        else for (String a : s.split(",")) if (!a.isEmpty()) out.add(a);
        return out;
    }

    static boolean watched(Prefs p, String pkg) {
        return apps(p).contains(pkg);
    }

    /** False for a repeat of the same message (apps often re-post when they update). */
    static synchronized boolean post(String a, String t, String x) {
        long now = System.currentTimeMillis();
        if (a.equals(app) && t.equals(title) && x.equals(text) && now - at < 60000) return false;
        app = a;
        title = t;
        text = x;
        at = now;
        return true;
    }

    /** What to say aloud: "names" = only who it's from, "all" = the message too. */
    static String spoken(String mode) {
        String who = title.isEmpty() ? app : title;
        if ("all".equals(mode) && !text.isEmpty()) return "Message from " + who + " on " + app + ". " + text;
        return "New message from " + who + " on " + app;
    }

    private Ticker() {
    }
}
