package org.openrath.launcher;

import android.app.Notification;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

/**
 * The next turn from the navigation app the driver uses (Google Maps, Waze, OsmAnd, Organic Maps and
 * others). While they guide, those apps keep a notification up with the distance, the instruction and
 * an arrow; MediaListener hands it here, and the dashboard shows it in a card (NavCard), and over other
 * apps in the mini strip (OverStrip). The last app that guided is remembered, so it can be opened
 * again after the stereo restarts mid-trip (Extras.resumeNav).
 */
final class NavTurn {

    static final String CHANGED = "org.openrath.launcher.NAV_TURN";

    /** Navigation apps whose guidance notification is read; any other app's must say CATEGORY_NAVIGATION. */
    static final String[] APPS = {"com.google.android.apps.maps", "com.google.android.apps.mapslite", "com.waze",
            "net.osmand", "net.osmand.plus", "app.organicmaps", "com.mapswithme.maps.pro", "com.here.app.maps",
            "com.sygic.aura", "ru.yandex.yandexnavi", "com.mmi.maps", "com.tomtom.gplay.navapp", "cz.seznam.mapy",
            "com.magicearth"};

    static volatile String pkg = "", instruction = "", distance = "", eta = "";
    static volatile Bitmap arrow;
    static volatile long at;

    /** Guidance older than this (the app stopped updating it) is not shown. */
    static final long STALE_MS = 3 * 60 * 1000;

    static boolean isNavApp(String p) {
        if (p == null) return false;
        for (String a : APPS) if (a.equals(p)) return true;
        return false;
    }

    /** Whether there is guidance to show now. */
    static boolean active() {
        return !instruction.isEmpty() && System.currentTimeMillis() - at < STALE_MS;
    }

    /**
     * Whether a notification is a navigation app's guidance: an ongoing one from a known navigation
     * app, or any app's that says it is navigation.
     */
    static boolean guidance(String p, String category, boolean ongoing) {
        if (Notification.CATEGORY_NAVIGATION.equals(category)) return true;
        return ongoing && isNavApp(p) && !Notification.CATEGORY_TRANSPORT.equals(category);
    }

    /**
     * Split a guidance notification's title and text into (distance, instruction). Google Maps puts
     * "300 m" in the title and "Turn left onto MG Road" in the text; Waze and OsmAnd often put the
     * instruction first. The part that starts with a number and a unit is the distance.
     */
    static String[] split(String title, String text) {
        String t = title == null ? "" : title.trim(), x = text == null ? "" : text.trim();
        if (looksLikeDistance(t)) return new String[]{t, x};
        if (looksLikeDistance(x)) return new String[]{x, t};
        // "In 300 m, turn left": the distance at the front of one line
        java.util.regex.Matcher m = LEAD.matcher(t);
        if (m.find()) return new String[]{m.group(1).trim(), (t.substring(m.end()).trim() + (x.isEmpty() ? "" : "  ·  " + x)).trim()};
        return new String[]{"", x.isEmpty() ? t : t.isEmpty() ? x : t + "  ·  " + x};
    }

    private static final java.util.regex.Pattern DIST = java.util.regex.Pattern.compile(
            "^(in\\s+)?[0-9]+([.,][0-9]+)?\\s*(m|km|ft|mi|yd|metres|meters|miles|feet|मी|किमी)\\.?$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern LEAD = java.util.regex.Pattern.compile(
            "^(?:in\\s+)?([0-9]+(?:[.,][0-9]+)?\\s*(?:m|km|ft|mi|yd))\\b[,:]?", java.util.regex.Pattern.CASE_INSENSITIVE);

    static boolean looksLikeDistance(String s) {
        return s != null && s.length() <= 24 && DIST.matcher(s.trim()).matches();
    }

    /** Take a posted notification; true when it was guidance (and the card should change). */
    static boolean take(StatusBarNotification sbn, Prefs p) {
        Notification n = sbn.getNotification();
        if (n == null) return false;
        String from = sbn.getPackageName();
        if (!guidance(from, n.category, (n.flags & Notification.FLAG_ONGOING_EVENT) != 0)) return false;
        Bundle x = n.extras;
        CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE), text = x.getCharSequence(Notification.EXTRA_TEXT),
                sub = x.getCharSequence(Notification.EXTRA_SUB_TEXT);
        if (title == null && text == null) return false;
        String[] dt = split(title == null ? null : title.toString(), text == null ? null : text.toString());
        Bitmap b = null;
        try {
            Object big = x.get(Notification.EXTRA_LARGE_ICON);
            if (big instanceof Bitmap) b = (Bitmap) big;
        } catch (Throwable ignored) {
            // an icon that isn't a picture: the card draws without an arrow
        }
        synchronized (NavTurn.class) {
            pkg = from;
            distance = dt[0];
            instruction = dt[1];
            eta = sub == null ? "" : sub.toString();
            arrow = b;
            at = System.currentTimeMillis();
        }
        // remembered for a restart mid-trip (once a minute is enough)
        if (p != null && (!from.equals(p.str("navApp", "")) || System.currentTimeMillis() - p.longNum("navAt", 0L) > 60000)) {
            p.put("navApp", from);
            p.putLong("navAt", System.currentTimeMillis());
        }
        return true;
    }

    /** The guidance notification went away: the trip ended. False when it wasn't the one shown. */
    static boolean removed(StatusBarNotification sbn, Prefs p) {
        if (!sbn.getPackageName().equals(pkg)) return false;
        Notification n = sbn.getNotification();
        if (n != null && !guidance(sbn.getPackageName(), n.category, (n.flags & Notification.FLAG_ONGOING_EVENT) != 0)) return false;
        clear();
        if (p != null) p.putLong("navAt", 0L);   // arrived or stopped: nothing to open again after a restart
        return true;
    }

    static synchronized void clear() {
        pkg = "";
        instruction = distance = eta = "";
        arrow = null;
        at = 0;
    }

    private NavTurn() {
    }
}
