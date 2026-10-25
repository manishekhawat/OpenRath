package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Child-lock for apps: chosen apps (video, browser, games) can't be opened while the car is
 * moving. The app drawer and the dashboard refuse them, and with the steering-wheel accessibility
 * service on, the lock also sends the screen home if one is opened any other way. Unlike valet
 * mode (Valet) it needs no PIN and leaves the volume, speed and Settings alone.
 */
final class ChildLock {

    static Set<String> apps(Prefs p) {
        Set<String> s = new HashSet<>(Arrays.asList(p.str("lockedApps", "").split(",")));
        s.remove("");
        return s;
    }

    /** When a fresh reading last said the car was moving. */
    private static volatile long movingSeen;

    /** How long a car last seen moving still counts as moving with no fresh speed (a tunnel, GPS lost). */
    static final long LOST_MS = 10 * 60 * 1000;

    static boolean moving() {
        long now = System.currentTimeMillis();
        int obd = Obd.speedNow();
        boolean m = moving(Drive.kmhNow, Drive.fixAt, obd, now, movingSeen);
        if (m && (obd >= 0 || now - Drive.fixAt < 10000)) movingSeen = now;
        return m;
    }

    /**
     * Whether the car moves: the engine's own speed when an OBD-II adapter reports it, else a GPS fix
     * under 10 seconds old. With neither, a car seen moving in the last 10 minutes still counts as
     * moving, so losing GPS in a tunnel or a garage doesn't unlock the locks. Pure, for the tests.
     */
    static boolean moving(float gpsKmh, long fixAt, int obdKmh, long now, long seenMoving) {
        if (obdKmh >= 0) return obdKmh > 8;
        if (now - fixAt < 10000) return gpsKmh > 8;
        return seenMoving > 0 && now - seenMoving < LOST_MS;
    }

    static boolean blocked(Prefs p, String pkg) {
        return pkg != null && Features.on(p, "childLock") && moving() && apps(p).contains(pkg);
    }

    static final String SORRY = "This app is locked while the car is moving";

    static void choose(Activity a, final Prefs p) {
        PackageManager pm = a.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<String> pkgs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<ResolveInfo> l = pm.queryIntentActivities(main, 0);
        Collections.sort(l, new ResolveInfo.DisplayNameComparator(pm));
        for (ResolveInfo ri : l) {
            String pkg = ri.activityInfo.packageName;
            if (pkgs.contains(pkg) || pkg.equals(a.getPackageName())) continue;
            pkgs.add(pkg);
            names.add(String.valueOf(ri.loadLabel(pm)));
        }
        Set<String> chosen = apps(p);
        final boolean[] on = new boolean[pkgs.size()];
        for (int i = 0; i < on.length; i++) on[i] = chosen.contains(pkgs.get(i));
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Lock these apps while moving"))
                .setMultiChoiceItems(names.toArray(new String[0]), on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w, boolean v) {
                        on[w] = v;
                    }
                }).setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < on.length; i++) if (on[i]) sb.append(pkgs.get(i)).append(',');
                p.put("lockedApps", sb.toString());
            }
        }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private ChildLock() {
    }
}
