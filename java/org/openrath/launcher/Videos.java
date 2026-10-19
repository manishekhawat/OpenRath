package org.openrath.launcher;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Videos on the unit, USB stick or SD card, for the video box. The picture shows only while GPS
 * says the car is stopped; the sound keeps going.
 */
final class Videos {

    static boolean isVideo(String name) {
        String n = name.toLowerCase(Locale.US);
        for (String e : new String[]{".mp4", ".mkv", ".webm", ".3gp", ".mov", ".avi", ".m4v", ".ts"}) if (n.endsWith(e)) return true;
        return false;
    }

    /** Whether the picture may show: a GPS fix in the last 10 seconds that says the car is standing still. */
    static boolean parked(float kmh, long fixAgeMs) {
        return fixAgeMs >= 0 && fixAgeMs < 10000 && kmh < Fixes.STILL_KMH;
    }

    static List<String> find(Context c) {
        List<String> out = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Video.Media.DATA}, null, null, MediaStore.Video.Media.DISPLAY_NAME + " COLLATE NOCASE");
            while (cur != null && cur.moveToNext()) {
                String p = cur.getString(0);
                if (p != null && new File(p).canRead()) out.add(p);
            }
        } catch (Exception e) {
            Diag.note("video scan", e);
        } finally {
            if (cur != null) cur.close();
        }
        List<String> walked = new ArrayList<>();
        for (String root : new String[]{"/storage", "/mnt/media_rw", "/mnt/usb_storage", "/mnt/udisk"}) walk(new File(root), 0, walked, out);
        Collections.sort(walked, String.CASE_INSENSITIVE_ORDER);
        out.addAll(walked);
        return out;
    }

    private static void walk(File dir, int depth, List<String> out, List<String> seen) {
        if (depth > 5 || out.size() > 500) return;
        File[] l = dir.listFiles();
        if (l == null) return;
        for (File f : l) {
            String n = f.getName();
            if (n.startsWith(".") || n.equals("Android") || n.equals("self") || n.equals("emulated") && depth > 0) continue;
            if (f.isDirectory()) walk(f, depth + 1, out, seen);
            else if (isVideo(n) && !seen.contains(f.getPath()) && !out.contains(f.getPath())) out.add(f.getPath());
        }
    }

    private Videos() {
    }
}
