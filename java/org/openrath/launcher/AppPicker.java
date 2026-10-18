package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Lists the installed apps and hands back the chosen package. */
final class AppPicker {

    interface Picked {
        void picked(String pkg);
    }

    static List<ResolveInfo> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = new ArrayList<>(pm.queryIntentActivities(i, 0));
        final PackageManager fpm = pm;
        Collections.sort(list, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(fpm).toString().compareToIgnoreCase(b.loadLabel(fpm).toString());
            }
        });
        for (int j = list.size() - 1; j >= 0; j--) {
            if (list.get(j).activityInfo.packageName.equals(c.getPackageName())) list.remove(j);
        }
        return list;
    }

    /**
     * Apps with a main screen but no launcher icon, such as some phone-projection, camera and radio
     * apps. One entry per app, sorted by name.
     */
    static List<ResolveInfo> noIcon(Context c) {
        PackageManager pm = c.getPackageManager();
        java.util.Set<String> shown = new java.util.HashSet<>();
        for (ResolveInfo r : apps(c)) shown.add(r.activityInfo.packageName);
        shown.add(c.getPackageName());
        List<ResolveInfo> out = new ArrayList<>();
        List<ResolveInfo> all;
        try {
            all = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN), 0);
        } catch (Exception e) {
            return out;
        }
        for (ResolveInfo r : all) {
            if (r.activityInfo == null || !r.activityInfo.exported) continue;
            if (shown.add(r.activityInfo.packageName)) out.add(r);
        }
        final PackageManager fpm = pm;
        Collections.sort(out, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return label(fpm, a).compareToIgnoreCase(label(fpm, b));
            }
        });
        return out;
    }

    /** The app's name, not the name of the screen that was found. */
    static String label(PackageManager pm, ResolveInfo r) {
        try {
            if (r.activityInfo.applicationInfo != null) return r.activityInfo.applicationInfo.loadLabel(pm).toString();
        } catch (Exception ignored) {
        }
        return r.loadLabel(pm).toString();
    }

    static void show(Context c, String title, Picked done) {
        list(c, title, apps(c), true, done);
    }

    private static void list(final Context c, final String title, final List<ResolveInfo> list, final boolean more,
                             final Picked done) {
        PackageManager pm = c.getPackageManager();
        final int n = list.size();
        String[] names = new String[more ? n + 1 : n];
        for (int i = 0; i < n; i++) names[i] = more ? list.get(i).loadLabel(pm).toString() : label(pm, list.get(i));
        if (more) names[n] = Hindi.t("More apps (without an icon)…");
        new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which < n) done.picked(list.get(which).activityInfo.packageName);
                        else list(c, title, noIcon(c), false, done);
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null)
                .show();
    }

    private AppPicker() {
    }
}
