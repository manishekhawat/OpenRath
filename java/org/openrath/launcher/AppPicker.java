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

    static void show(Context c, String title, final Picked done) {
        final List<ResolveInfo> list = apps(c);
        PackageManager pm = c.getPackageManager();
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).loadLabel(pm).toString();
        new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        done.picked(list.get(which).activityInfo.packageName);
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null)
                .show();
    }

    private AppPicker() {
    }
}
