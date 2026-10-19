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
 * OpenRath targets Android 14 and runs on Android 8.0 and newer (MIN_SDK in build.py).
 */
final class Compat {

    /** Context.RECEIVER_EXPORTED and RECEIVER_NOT_EXPORTED (API 33), spelt out for older SDKs. */
    static final int EXPORTED = 0x2, NOT_EXPORTED = 0x4;

    /**
     * The permission OpenRath's own broadcasts are sent with and received under. It is declared in
     * AndroidManifest.xml at signature level, so only apps signed with OpenRath's key (OpenRath
     * itself) hold it.
     */
    static final String OWN = "org.openrath.launcher.permission.OWN_BROADCAST";

    /** A receiver for broadcasts from the stereo's own apps and services (other packages). */
    static Intent fromOthers(Context c, BroadcastReceiver r, IntentFilter f) {
        return register(c, r, f, EXPORTED, null);
    }

    /**
     * A receiver for OpenRath's own broadcasts. Before Android 13 a receiver registered in code can
     * be reached by any app, so the sender must also hold OpenRath's own permission (see send()).
     */
    static Intent ownOnly(Context c, BroadcastReceiver r, IntentFilter f) {
        return register(c, r, f, NOT_EXPORTED, OWN);
    }

    /**
     * A receiver for the system's protected broadcasts (screen off, managed settings changed). Only
     * the system can send those, so no permission is asked of the sender.
     */
    static Intent fromSystem(Context c, BroadcastReceiver r, IntentFilter f) {
        return register(c, r, f, NOT_EXPORTED, null);
    }

    private static Intent register(Context c, BroadcastReceiver r, IntentFilter f, int flag, String permission) {
        if (Build.VERSION.SDK_INT >= 33) return c.registerReceiver(r, f, permission, null, flag);
        return c.registerReceiver(r, f, permission, null);
    }

    /** Send one of OpenRath's own broadcasts: only to OpenRath, and only receivers holding its permission hear it. */
    static void send(Context c, Intent i) {
        c.sendBroadcast(i.setPackage(c.getPackageName()), OWN);
    }

    /** Whether OpenRath may fetch `url` itself: https only (plain http is left to radio streams). */
    static boolean httpsOnly(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, 8) && url.length() > 8;
    }

    /** A connection for OpenRath's own downloads; refuses anything but https. */
    static java.net.HttpURLConnection https(String url) throws java.io.IOException {
        if (!httpsOnly(url)) throw new java.io.IOException("not https");
        java.net.URLConnection c = new java.net.URL(url).openConnection();
        if (!(c instanceof javax.net.ssl.HttpsURLConnection)) throw new java.io.IOException("not https");
        return (java.net.HttpURLConnection) c;
    }

    /** FLAG_SYSTEM or FLAG_UPDATED_SYSTEM_APP: the app came with the firmware. */
    static boolean systemFlags(int flags) {
        return (flags & (android.content.pm.ApplicationInfo.FLAG_SYSTEM
                | android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    /**
     * True when `pkg` is installed as part of the firmware. An app the owner installed under the same
     * package name is not, so its code is never loaded into OpenRath.
     */
    static boolean isSystemApp(android.content.pm.PackageManager pm, String pkg) {
        if (pm == null || pkg == null) return false;
        try {
            return systemFlags(pm.getApplicationInfo(pkg, 0).flags);
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * The intent that opens an app: its launcher icon, or for apps with no icon (some projection,
     * camera and radio apps) its first main screen. Null when the app isn't installed.
     */
    static Intent launch(android.content.pm.PackageManager pm, String pkg) {
        if (pm == null || pkg == null) return null;
        Intent i = null;
        try {
            i = pm.getLaunchIntentForPackage(pkg);
        } catch (Exception ignored) {
            // package gone: try the search below
        }
        if (i != null) return i;
        try {
            Intent m = new Intent(Intent.ACTION_MAIN).setPackage(pkg);
            List<android.content.pm.ResolveInfo> l = pm.queryIntentActivities(m, 0);
            for (android.content.pm.ResolveInfo r : l) {
                if (r.activityInfo == null || !r.activityInfo.exported) continue;
                return new Intent(Intent.ACTION_MAIN)
                        .setClassName(r.activityInfo.packageName, r.activityInfo.name);
            }
        } catch (Exception ignored) {
            // nothing found on this unit
        }
        return null;
    }

    /** android.app.role.RoleManager (Android 10+), by reflection: the SDK OpenRath builds with predates it. */
    private static Object roles(Context c) {
        if (Build.VERSION.SDK_INT < 29) return null;
        try {
            Object rm = c.getSystemService("role");
            if (rm == null) return null;
            Object avail = rm.getClass().getMethod("isRoleAvailable", String.class).invoke(rm, "android.app.role.HOME");
            return Boolean.TRUE.equals(avail) ? rm : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean roleHeld(Object rm) {
        try {
            return Boolean.TRUE.equals(rm.getClass().getMethod("isRoleHeld", String.class).invoke(rm, "android.app.role.HOME"));
        } catch (Throwable e) {
            return false;
        }
    }

    /** True when OpenRath is the home app, so the unit starts in it. */
    static boolean isHome(Context c) {
        Object rm = roles(c);
        if (rm != null) return roleHeld(rm);
        try {
            Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo r = c.getPackageManager()
                    .resolveActivity(h, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return r != null && r.activityInfo != null && c.getPackageName().equals(r.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Asks to become the home app: Android's own dialog (Android 10+), else the home-app screen,
     * else the default-apps screen. False when the unit has none of them.
     */
    static boolean askHome(android.app.Activity a) {
        Object rm = roles(a);
        if (rm != null && !roleHeld(rm)) {
            try {
                Intent i = (Intent) rm.getClass().getMethod("createRequestRoleIntent", String.class).invoke(rm, "android.app.role.HOME");
                a.startActivityForResult(i, 7);
                return true;
            } catch (Throwable ignored) {
                // no role manager on this unit: try the settings screens below
            }
        }
        for (String action : new String[]{android.provider.Settings.ACTION_HOME_SETTINGS,
                "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"}) {
            try {
                a.startActivity(new Intent(action));
                return true;
            } catch (Exception ignored) {
                // not on this unit: try the next screen
            }
        }
        return false;
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
            if (sdk < 30) l.add("android.permission.WRITE_EXTERNAL_STORAGE");   // up to Android 10 (legacy storage) it is needed to write
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
                // no such settings screen on this unit
            }
        }
    }

    private Compat() {
    }
}
