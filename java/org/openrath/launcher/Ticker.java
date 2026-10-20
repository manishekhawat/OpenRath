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
    /** The message's own reply button (from its app), when it has one that takes typed text. */
    static volatile android.app.Notification.Action reply;
    static volatile android.app.RemoteInput replyInput;

    /** Keep the message's reply button, if it has one that takes words (WhatsApp, Messages, Telegram and most others). */
    static void replyFrom(android.app.Notification n) {
        reply = null;
        replyInput = null;
        if (n == null || n.actions == null) return;
        for (android.app.Notification.Action a : n.actions) {
            android.app.RemoteInput[] in = a.getRemoteInputs();
            if (in == null) continue;
            for (android.app.RemoteInput r : in) {
                if (r.getAllowFreeFormInput()) {
                    reply = a;
                    replyInput = r;
                    return;
                }
            }
        }
    }

    /** Whether the last message can be answered (within 10 minutes of it). */
    static boolean canReply() {
        return reply != null && replyInput != null && System.currentTimeMillis() - at < 10 * 60000;
    }

    /** Send `words` through the message's own reply button; false when it couldn't. */
    static boolean send(android.content.Context c, String words) {
        android.app.Notification.Action a = reply;
        android.app.RemoteInput r = replyInput;
        if (a == null || r == null || words == null || words.trim().isEmpty()) return false;
        android.content.Intent i = new android.content.Intent();
        android.os.Bundle b = new android.os.Bundle();
        b.putCharSequence(r.getResultKey(), words.trim());
        android.app.RemoteInput.addResultsToIntent(new android.app.RemoteInput[]{r}, i, b);
        try {
            a.actionIntent.send(c, 0, i);
            reply = null;
            replyInput = null;
            return true;
        } catch (Exception e) {
            Diag.note("message reply", e);
            return false;
        }
    }

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
