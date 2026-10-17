package org.openrath.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.Locale;

/**
 * Turn-by-turn without internet: the map in the box needs the internet for routes, so with no
 * connection Go hands the trip to an app that keeps its own maps on the unit. Organic Maps (free and
 * open source, offline maps of India) is preferred, then OsmAnd, then Google Maps (with an offline
 * area downloaded). Settings > Map and navigation can pick another.
 */
final class OfflineNav {

    static final String[] KNOWN = {"app.organicmaps", "net.osmand.plus", "net.osmand", "com.google.android.apps.maps"};

    /** The app to use: the owner's pick, else the first known one installed; null if none. */
    static String app(Context c, Prefs p) {
        String mine = p.app("offlineNav");
        if (mine != null && installed(c, mine)) return mine;
        for (String k : KNOWN) if (installed(c, k)) return k;
        return null;
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    /** The address that starts guidance (Google Maps) or shows the place to route to (the others). */
    static String uri(String pkg, double lat, double lon, String name) {
        if ("com.google.android.apps.maps".equals(pkg)) return String.format(Locale.US, "google.navigation:q=%.6f,%.6f", lat, lon);
        return String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(%s)", lat, lon, lat, lon, Uri.encode(name == null ? "" : name));
    }

    static boolean go(Context c, Prefs p, double lat, double lon, String name) {
        String pkg = app(c, p);
        if (pkg == null) return false;
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri(pkg, lat, lon, name))).setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private OfflineNav() {
    }
}
