package org.openrath.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import android.os.Environment;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.io.StringWriter;

/**
 * Keeps the last crash for the diagnostics screen, and guards risky steps (reading other apps,
 * talking to the Topway MCU): a step that brought the app down is skipped from then on, even when
 * the crash happened in native code where Java can't catch it.
 */
final class Crash {

    private static SharedPreferences sp;

    static void install(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("crash", Context.MODE_PRIVATE);
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        String step = sp.getString("running", null);
        if (step != null) {   // the app died in the middle of this step last time
            sp.edit().remove("running").putBoolean("skip:" + step, true)
                    .putString("last", "Stopped during \"" + step + "\"; that step is now skipped").apply();
        }
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                StringWriter w = new StringWriter();
                e.printStackTrace(new PrintWriter(w));
                String s = w.toString();
                String running = sp.getString("running", null);
                SharedPreferences.Editor ed = sp.edit().putString("last", s.length() > 700 ? s.substring(0, 700) : s);
                if (running != null) ed.remove("running").putBoolean("skip:" + running, true);
                ed.commit();
                save(s, t.getName());
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    static File dir() {
        return Storage.dir("logs");
    }

    /** A crash report file for Help > Crash reports; the last 10 are kept. Nothing is sent anywhere. */
    private static void save(String trace, String thread) {
        try {
            File d = dir();
            if (!d.isDirectory() && !d.mkdirs()) return;
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.US).format(new java.util.Date());
            java.io.Writer w = new java.io.FileWriter(new File(d, "crash-" + stamp + ".txt"));
            w.write("OpenRath " + version + "\nAndroid " + android.os.Build.VERSION.RELEASE + " (" + android.os.Build.MODEL + ")\n"
                    + "Thread: " + thread + "\n\n" + trace);
            w.close();
            List<File> all = files();
            for (int i = 10; i < all.size(); i++) all.get(i).delete();
        } catch (Throwable ignored) {
        }
    }

    /** Saved crash reports, newest first. */
    static List<File> files() {
        List<File> out = new ArrayList<>();
        File[] l = dir().listFiles();
        if (l != null) for (File f : l) if (f.getName().startsWith("crash-")) out.add(f);
        java.util.Collections.sort(out, java.util.Collections.reverseOrder());
        return out;
    }

    static String read(File f) {
        try {
            java.io.InputStream in = new java.io.FileInputStream(f);
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            in.close();
            String s = o.toString("UTF-8");
            return s.length() > 6000 ? s.substring(0, 6000) + "\n…" : s;
        } catch (Exception e) {
            return "Couldn't read: " + e.getMessage();
        }
    }

    private static String version = "";

    /** Start a risky step; false if it crashed before and is skipped. */
    static boolean begin(String step) {
        if (sp == null) return true;
        if (sp.getBoolean("skip:" + step, false)) return false;
        sp.edit().putString("running", step).commit();
        return true;
    }

    static void end() {
        if (sp != null) sp.edit().remove("running").commit();
    }

    static String last() {
        return sp == null ? null : sp.getString("last", null);
    }

    /** Try the skipped steps again (from the menu, after an update). */
    static void forgive() {
        if (sp != null) sp.edit().clear().apply();
    }

    /** Android's reason the app was last ended (ApplicationExitInfo.REASON_*), in plain words. */
    static String reason(int r) {
        switch (r) {
            case 1: return "it closed itself";
            case 2: return "it was stopped by a signal";
            case 3: return "the unit ran low on memory";
            case 4: return "it crashed";
            case 5: return "it crashed in native code";
            case 6: return "it stopped responding";
            case 7: return "it failed to start";
            case 8: return "a permission was changed";
            case 9: return "it used too much of the unit";
            case 10: return "someone closed it";
            case 11: return "someone force-stopped it";
            case 12: return "an app it depends on stopped";
            case 14: return "the unit froze it";
            case 15: case 16: return "it was updated or changed";
            default: return "an unknown reason";
        }
    }

    /**
     * Why Android last ended the app and when, or null (needs Android 11 or newer). Makes "OpenRath
     * keeps restarting" reports diagnosable: memory, a crash, or the stereo killing it.
     */
    static String lastExit(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 30) return null;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            // by reflection: the build's android.jar predates ApplicationExitInfo
            List<?> l = (List<?>) am.getClass().getMethod("getHistoricalProcessExitReasons", String.class, int.class, int.class)
                    .invoke(am, c.getPackageName(), 0, 1);
            if (l == null || l.isEmpty()) return null;
            Object e = l.get(0);
            long at = (Long) e.getClass().getMethod("getTimestamp").invoke(e);
            int r = (Integer) e.getClass().getMethod("getReason").invoke(e);
            String d = (String) e.getClass().getMethod("getDescription").invoke(e);
            String when = android.text.format.DateFormat.format("d MMM, HH:mm", at).toString();
            return reason(r) + ", " + when + (d == null || d.isEmpty() ? "" : " (" + d + ")");
        } catch (Throwable t) {
            return null;
        }
    }

    private Crash() {
    }
}
