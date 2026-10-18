package org.openrath.launcher;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Which make of stereo this is, from the system apps it carries. Features that only work through
 * one maker's own services (Topway's radio service and MCU link: FM in the box, reverse gear,
 * Bluetooth calls) show their settings only on that make; everything else works on any Android.
 */
final class Stereo {

    static final String TOPWAY = "topway", FYT = "fyt", MTC = "mtc", SZ = "szchoiceway", ANDROID = "android";

    private static String kind;

    static String kind(Context c) {
        if (kind == null) kind = detect(c);
        return kind;
    }

    static boolean topway(Context c) {
        return TOPWAY.equals(kind(c));
    }

    static String name(Context c) {
        String k = kind(c);
        if (TOPWAY.equals(k)) return "Topway";
        if (FYT.equals(k)) return "FYT";
        if (MTC.equals(k)) return "MTCD / MTCE (Joying and others)";
        if (SZ.equals(k)) return "szchoiceway";
        return "Android (no maker-specific extras)";
    }

    private static String detect(Context c) {
        PackageManager pm = c.getPackageManager();
        if (has(pm, Xt.PKG) || has(pm, "com.tw.radio") || cls("android.tw.john.TWUtil")) return TOPWAY;
        if (has(pm, "com.syu.ms") || has(pm, "com.syu.canbus") || has(pm, "com.syu.radio")) return FYT;
        if (has(pm, "android.microntek.service") || has(pm, "com.microntek.radio") || cls("android.microntek.CarManager")) return MTC;
        if (has(pm, "com.szchoiceway.radio") || has(pm, "com.szchoiceway.eventcenter") || hasJson(c)) return SZ;
        return ANDROID;
    }

    /** Pure part, for the tests: the make from the package names present. */
    static String from(java.util.Collection<String> pkgs) {
        if (pkgs.contains("com.tw.service.xt") || pkgs.contains("com.tw.radio")) return TOPWAY;
        if (pkgs.contains("com.syu.ms") || pkgs.contains("com.syu.canbus") || pkgs.contains("com.syu.radio")) return FYT;
        if (pkgs.contains("android.microntek.service") || pkgs.contains("com.microntek.radio")) return MTC;
        if (pkgs.contains("com.szchoiceway.radio") || pkgs.contains("com.szchoiceway.eventcenter")) return SZ;
        return ANDROID;
    }

    private static boolean hasJson(Context c) {
        try {
            return android.provider.Settings.Global.getString(c.getContentResolver(), SzUnit.JSON) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean has(PackageManager pm, String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean cls(String name) {
        try {
            Class.forName(name, false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private Stereo() {
    }
}
