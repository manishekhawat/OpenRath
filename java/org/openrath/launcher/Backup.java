package org.openrath.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Backup and restore to a .zip in OpenRath/backup: every setting, the fuel and toll logs, trip
 * history, learnt car signals and the owner's own pictures. Survives a firmware reflash or a
 * reinstall. A daily copy is made automatically (the last seven are kept).
 */
final class Backup {

    /** Settings files that are worth keeping; "crash" is left out on purpose. */
    private static final String[] PREFS = {"home", "drive", "player"};
    private static final int KEEP = 7;

    static File dir() {
        return Storage.dir("backup");
    }

    /** Write a backup now; returns the file. */
    static File make(Context c, String kind) throws Exception {
        File d = dir();
        if (!d.isDirectory() && !d.mkdirs()) throw new IllegalStateException(Hindi.f("can't create %1$s (allow storage access?)", d));
        String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date());
        File f = new File(d, "openrath-" + kind + "-" + stamp + ".zip");
        JSONObject all = new JSONObject();
        all.put("app", "OpenRath");
        all.put("format", 1);
        all.put("made", System.currentTimeMillis());
        JSONObject prefs = new JSONObject();
        for (String name : PREFS) prefs.put(name, dump(c.getSharedPreferences(name, Context.MODE_PRIVATE)));
        all.put("prefs", prefs);
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f));
        try {
            z.putNextEntry(new ZipEntry("backup.json"));
            z.write(all.toString(1).getBytes("UTF-8"));
            z.closeEntry();
            File[] pics = new File(c.getFilesDir(), "custom").listFiles();
            if (pics != null) for (File p : pics) {
                z.putNextEntry(new ZipEntry("custom/" + p.getName()));
                copy(new FileInputStream(p), z);
                z.closeEntry();
            }
        } finally {
            z.close();
        }
        prune(kind);
        return f;
    }

    /** Once a day, from the trip service. */
    static void daily(Context c, Prefs p) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (today.equals(p.str("backupDay", ""))) return;
        try {
            make(c, "auto");
            p.put("backupDay", today);
            p.put("backupLast", String.valueOf(System.currentTimeMillis()));
        } catch (Throwable ignored) {
            // no storage permission yet: try again tomorrow
        }
    }

    private static void prune(String kind) {
        List<File> mine = new ArrayList<>();
        File[] l = dir().listFiles();
        if (l == null) return;
        for (File f : l) if (f.getName().startsWith("openrath-" + kind + "-")) mine.add(f);
        java.util.Collections.sort(mine);
        for (int i = 0; i < mine.size() - KEEP; i++) mine.get(i).delete();
    }

    /** Backups found, newest first: OpenRath/backup, the old DzireHome/backup and Download. */
    static List<File> found() {
        List<File> out = new ArrayList<>();
        for (File d : new File[]{dir(), new File(new File(Environment.getExternalStorageDirectory(), Storage.OLD), "backup"), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}) {
            File[] l = d.listFiles();
            if (l != null) for (File f : l) {
                String n = f.getName();   // dzire-home-: made by the app before it was renamed
                if ((n.startsWith("openrath-") || n.startsWith("dzire-home-")) && n.endsWith(".zip")) out.add(f);
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return out;
    }

    /** Put everything back from a backup. The app should restart afterwards. */
    static void restore(Context c, File f) throws Exception {
        ZipFile z = new ZipFile(f);
        try {
            ZipEntry je = z.getEntry("backup.json");
            if (je == null) throw new IllegalStateException(Hindi.t("not an OpenRath backup"));
            JSONObject all = new JSONObject(read(z.getInputStream(je)));
            JSONObject prefs = all.getJSONObject("prefs");
            Set<String> allowed = new HashSet<>(Arrays.asList(PREFS));
            java.util.Iterator<String> names = prefs.keys();
            while (names.hasNext()) {
                String name = names.next();
                if (!allowed.contains(name)) continue;
                load(c.getSharedPreferences(name, Context.MODE_PRIVATE), prefs.getJSONObject(name));
            }
            SharedPreferences home = c.getSharedPreferences("home", Context.MODE_PRIVATE);
            if ("Dzire Home".equals(all.optString("app")) && !home.contains("carBrand")) {
                // made before the rename, when the app was only for the Dzire: that is the car
                home.edit().putString("carBrand", "maruti-suzuki").putString("carBrandName", "Maruti Suzuki")
                        .putString("carModel", "Dzire").putString("fuel", "petrol").putString("carSetup", "yes").apply();
                if (!home.contains("tankL")) home.edit().putString("tankL", "37").apply();   // the Dzire's tank, the old default
            }
            File custom = new File(c.getFilesDir(), "custom");
            custom.mkdirs();
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!n.startsWith("custom/") || e.isDirectory()) continue;
                String file = n.substring("custom/".length());
                if (file.contains("/") || file.contains("..") || !file.endsWith(".png")) continue;   // pictures only, no paths
                FileOutputStream o = new FileOutputStream(new File(custom, file));
                copy(z.getInputStream(e), o);
                o.close();
            }
        } finally {
            z.close();
        }
    }

    // values keep their type: {"key": ["s", "text"]}, ["i", 3], ["l", 9], ["f", 1.5], ["b", true], ["ss", [..]]
    private static JSONObject dump(SharedPreferences sp) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            Object v = e.getValue();
            JSONArray a = new JSONArray();
            if (v instanceof String) a.put("s").put(v);
            else if (v instanceof Integer) a.put("i").put(v);
            else if (v instanceof Long) a.put("l").put(v);
            else if (v instanceof Float) a.put("f").put(((Float) v).doubleValue());
            else if (v instanceof Boolean) a.put("b").put(v);
            else if (v instanceof Set) {
                JSONArray s = new JSONArray();
                for (Object x : (Set<?>) v) s.put(String.valueOf(x));
                a.put("ss").put(s);
            } else continue;
            o.put(e.getKey(), a);
        }
        return o;
    }

    private static void load(SharedPreferences sp, JSONObject o) throws Exception {
        SharedPreferences.Editor ed = sp.edit().clear();
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONArray a = o.getJSONArray(k);
            String t = a.getString(0);
            if ("s".equals(t)) ed.putString(k, a.getString(1));
            else if ("i".equals(t)) ed.putInt(k, a.getInt(1));
            else if ("l".equals(t)) ed.putLong(k, a.getLong(1));
            else if ("f".equals(t)) ed.putFloat(k, (float) a.getDouble(1));
            else if ("b".equals(t)) ed.putBoolean(k, a.getBoolean(1));
            else if ("ss".equals(t)) {
                Set<String> s = new HashSet<>();
                JSONArray x = a.getJSONArray(1);
                for (int i = 0; i < x.length(); i++) s.add(x.getString(i));
                ed.putStringSet(k, s);
            }
        }
        ed.commit();
    }

    private static void copy(InputStream in, java.io.OutputStream o) throws Exception {
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
    }

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        copy(in, o);
        return o.toString("UTF-8");
    }

    private Backup() {
    }
}
