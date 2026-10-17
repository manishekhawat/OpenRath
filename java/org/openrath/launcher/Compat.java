package org.openrath.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * What changes with the Android version the stereo runs: receiver flags (Android 13+), the
 * permissions to ask for at first start, and all-files access for the OpenRath folder (Android 11+).
 * OpenRath targets Android 14 and runs on Android 10 and newer (MIN_SDK in build.py).
 */
final class Compat {

    /** Context.RECEIVER_EXPORTED and RECEIVER_NOT_EXPORTED (API 33), spelt out for older SDKs. */
    static final int EXPORTED = 0x2, NOT_EXPORTED = 0x4;

    /** A receiver for broadcasts from the stereo's own apps and services (other packages). */
    static Intent fromOthers(Context c, BroadcastReceiver r, IntentFilter f) {
        return register(c, r, f, EXPORTED);
    }

    /** A receiver for OpenRath's own broadcasts and the system's; other apps can't reach it. */
    static Intent ownOnly(Context c, BroadcastReceiver r, IntentFilter f) {
        return register(c, r, f, NOT_EXPORTED);
    }

    private static Intent register(Context c, BroadcastReceiver r, IntentFilter f, int flag) {
        if (Build.VERSION.SDK_INT >= 33) return c.registerReceiver(r, f, flag);
        return c.registerReceiver(r, f);
    }

    /** The runtime permissions to ask for at first start on this Android version. */
    static String[] startPermissions(int sdk) {
        List<String> l = new ArrayList<>();
        l.add("android.permission.ACCESS_FINE_LOCATION");
        if (sdk >= 33) {
            l.add("android.permission.READ_MEDIA_AUDIO");
            l.add("android.permission.READ_MEDIA_IMAGES");
            l.add("android.permission.POST_NOTIFICATIONS");
        } else {
            l.add("android.permission.READ_EXTERNAL_STORAGE");
            if (sdk < 30) l.add("android.permission.WRITE_EXTERNAL_STORAGE");   // Android 10 with legacy storage still needs it
        }
        if (sdk >= 31) {
            l.add("android.permission.BLUETOOTH_CONNECT");
            l.add("android.permission.BLUETOOTH_SCAN");
        }
        l.add("android.permission.READ_CALL_LOG");
        l.add("android.permission.CALL_PHONE");
        l.add("android.permission.READ_PHONE_STATE");
        l.add("android.permission.ANSWER_PHONE_CALLS");
        return l.toArray(new String[0]);
    }

    /** The ones of those not granted yet. */
    static String[] missing(Context c) {
        List<String> l = new ArrayList<>();
        for (String p : startPermissions(Build.VERSION.SDK_INT)) {
            if (c.checkSelfPermission(p) != android.content.pm.PackageManager.PERMISSION_GRANTED) l.add(p);
        }
        return l.toArray(new String[0]);
    }

    /** Android 11+: the OpenRath folder (themes, backups, music, dashcam) needs all-files access. */
    static boolean needsAllFiles() {
        if (Build.VERSION.SDK_INT < 30) return false;
        try {
            return !(Boolean) android.os.Environment.class.getMethod("isExternalStorageManager").invoke(null);
        } catch (Exception e) {
            return false;
        }
    }

    static void askAllFiles(Context c) {
        try {
            c.startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
                    android.net.Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                c.startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    private Compat() {
    }
}
