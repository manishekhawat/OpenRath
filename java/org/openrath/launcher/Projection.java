package org.openrath.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * The phone-projection app (wireless CarPlay and Android Auto: ZLink, AutoKit, CarbitLink, Headunit Revived and
 * others). The owner can choose it; otherwise it is found by its package name or label, once a
 * minute at most. Many of these apps have no launcher icon, so it opens through Compat.launch.
 * With the androidAuto switch on and no app chosen, it is OpenRath's own Android Auto screen.
 */
final class Projection {

    /** Words in the package name or label of projection apps. */
    static final String[] WORDS = {"zlink", "autokit", "carplay", "carbit", "phonelink", "phone link", "easyconn",
            "androidauto", "android auto", "auto link", "autolink", "carlink", "headunit"};

    private static String found;
    private static long foundAt = -60000;

    /** True when this package name or label looks like a projection app. */
    static boolean looksLike(String pkg, String label) {
        String s = ((pkg == null ? "" : pkg) + " " + (label == null ? "" : label)).toLowerCase(Locale.US);
        // the phone's own Android Auto app, if it's here, can't receive a phone
        if (s.contains("com.google.android.projection.gearhead")) return false;
        for (String w : WORDS) if (s.contains(w)) return true;
        return false;
    }

    /** The projection app to open, or null when there's none. */
    static String app(Context c, Prefs p) {
        PackageManager pm = c.getPackageManager();
        String chosen = p.app("projection");
        if (chosen != null && Compat.launch(pm, chosen) != null) return chosen;
        // OpenRath's own Android Auto, when it is switched on and no other app is chosen
        if (Features.on(p, "androidAuto")) return c.getPackageName();
        long now = SystemClock.elapsedRealtime();
        if (now - foundAt < 60000) return found;
        foundAt = now;
        found = null;
        try {
            List<ApplicationInfo> all = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : all) {
                if (ai.packageName.equals(c.getPackageName())) continue;
                String label;
                try {
                    label = String.valueOf(pm.getApplicationLabel(ai));
                } catch (Exception e) {
                    label = "";
                }
                if (looksLike(ai.packageName, label) && Compat.launch(pm, ai.packageName) != null) {
                    found = ai.packageName;
                    break;
                }
            }
        } catch (Exception e) {
            Diag.note("projection app search", e);
        }
        return found;
    }

    /** Opens the projection app; false when there's none. */
    static boolean open(Context c, Prefs p) {
        String pkg = app(c, p);
        if (c.getPackageName().equals(pkg)) {
            AaActivity.open(c);
            return true;
        }
        Intent i = Compat.launch(c.getPackageManager(), pkg);
        if (i == null) {
            Toast.makeText(c, Hindi.t("No phone projection app found; choose one in Settings, Radio and music"), Toast.LENGTH_LONG).show();
            return false;
        }
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            Toast.makeText(c, Hindi.t("That app couldn't be opened"), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** Forget the found app, after the owner chooses one or apps change. */
    static void forget() {
        found = null;
        foundAt = -60000;
    }

    private Projection() {
    }
}
