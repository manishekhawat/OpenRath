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
 * reinstall. A daily copy is made automatically (the last seven are kept). Secrets (the valet PIN,
 * the hotspot password) are left out, and a restore never changes them, the update and report
 * addresses or a fleet's managed settings.
 * <p>
 * Moving to another stereo: the settings alone as a QR code when they are small enough, else the
 * whole backup file shared to another app (Bluetooth, a messaging app, a file manager). The other
 * stereo takes the shared file or the code's text (see {@link Receive}), or opens a file it already has.
 */
final class Backup {

    /** Settings files that are worth keeping; "crash" is left out on purpose. */
    private static final String[] PREFS = {"home", "drive", "player"};
    private static final int KEEP = 7;
    /** Largest backup.json or picture read back from a backup. */
    static final long MAX_ENTRY = 8L * 1024 * 1024;
    /** Largest total of pictures read back from one backup. */
    static final long MAX_PICTURES = 64L * 1024 * 1024;

    /**
     * Secrets never written to a backup (backups sit on shared storage, where other apps can read
     * them): the valet PIN, the hotspot password and the key-action token.
     */
    private static final Set<String> SECRET = new HashSet<>(Arrays.asList("x:valetPin", "x:apPass", "x:keyToken"));
    /**
     * Never taken from a backup, whatever it holds: the secrets, valet mode (it goes with its PIN),
     * the addresses updates come from and reports go to, and a fleet's managed settings. What the
     * unit has for these stays as it is.
     */
    private static final Set<String> KEPT = new HashSet<>(Arrays.asList("x:valetPin", "x:apPass", "x:keyToken", "x:valet",
            "x:updateUrl", "x:reportUrl"));

    /** Whether a setting goes into backups. */
    static boolean backedUp(String key) {
        return key != null && !SECRET.contains(key);
    }

    /** Whether a setting may be restored from a backup. */
    static boolean restorable(String key) {
        return key != null && !KEPT.contains(key) && !key.startsWith("x:m:");
    }

    /** "openrath-manual-2024-05-01-0930.zip, 1 May 2024 09:30": the file and when it was made, for the confirm dialogs. */
    static String label(File f) {
        return f.getName() + ", " + new SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(new Date(f.lastModified()));
    }

    static File dir() {
        return Storage.dir("backup");
    }

    /** Write a backup now; returns the file. */
    static File make(Context c, String kind) throws Exception {
        File d = dir();
        if (!d.isDirectory() && !d.mkdirs()) throw new IllegalStateException(Hindi.f("can't create %1$s (allow storage access?)", d));
        String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date());
        File f = new File(d, "openrath-" + kind + "-" + stamp + ".zip");
        write(c, f);
        prune(kind);
        return f;
    }

    /** Everything a backup holds, into the zip file f. */
    private static void write(Context c, File f) throws Exception {
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
            if (je.getSize() > MAX_ENTRY) throw new IllegalStateException(Hindi.t("not an OpenRath backup"));
            JSONObject all = new JSONObject(read(z.getInputStream(je)));
            apply(c, all, false);
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
            long pictures = 0;
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!n.startsWith("custom/") || e.isDirectory()) continue;
                String file = n.substring("custom/".length());
                if (file.contains("/") || file.contains("..") || !file.endsWith(".png")) continue;   // pictures only, no paths
                if (e.getSize() > MAX_ENTRY || pictures > MAX_PICTURES) continue;
                File to = new File(custom, file);
                FileOutputStream o = new FileOutputStream(to);
                long got;
                try {
                    got = copy(z.getInputStream(e), o, MAX_ENTRY);
                } finally {
                    o.close();
                }
                if (got < 0) to.delete();   // bigger than it said: not kept
                else pictures += got;
            }
        } finally {
            z.close();
        }
    }

    /** The settings files in a backup's JSON, put back; `merge` only sets what it holds and keeps the rest. */
    private static void apply(Context c, JSONObject all, boolean merge) throws Exception {
        JSONObject prefs = all.getJSONObject("prefs");
        Set<String> allowed = new HashSet<>(Arrays.asList(PREFS));
        java.util.Iterator<String> names = prefs.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) continue;
            load(c.getSharedPreferences(name, Context.MODE_PRIVATE), prefs.getJSONObject(name), merge);
        }
    }

    private static JSONObject dump(SharedPreferences sp) throws Exception {
        return dump(sp, Integer.MAX_VALUE);
    }

    // values keep their type: {"key": ["s", "text"]}, ["i", 3], ["l", 9], ["f", 1.5], ["b", true], ["ss", [..]]
    private static JSONObject dump(SharedPreferences sp, int longest) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (!backedUp(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof String && ((String) v).length() > longest) continue;   // a log or a list, not a setting
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

    private static void load(SharedPreferences sp, JSONObject o, boolean merge) throws Exception {
        Map<String, ?> before = sp.getAll();
        SharedPreferences.Editor ed = merge ? sp.edit() : sp.edit().clear();
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (!restorable(k)) continue;
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
        // what a backup can't change stays as the unit has it
        for (Map.Entry<String, ?> e : before.entrySet()) {
            if (restorable(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof String) ed.putString(e.getKey(), (String) v);
            else if (v instanceof Integer) ed.putInt(e.getKey(), (Integer) v);
            else if (v instanceof Long) ed.putLong(e.getKey(), (Long) v);
            else if (v instanceof Float) ed.putFloat(e.getKey(), (Float) v);
            else if (v instanceof Boolean) ed.putBoolean(e.getKey(), (Boolean) v);
            else if (v instanceof Set) {
                Set<String> x = new HashSet<>();
                for (Object y : (Set<?>) v) x.add(String.valueOf(y));
                ed.putStringSet(e.getKey(), x);
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

    /** Copies at most `max` bytes; -1 when there was more (the rest isn't read). */
    static long copy(InputStream in, java.io.OutputStream o, long max) throws Exception {
        byte[] b = new byte[65536];
        long total = 0;
        int n;
        try {
            while ((n = in.read(b)) > 0) {
                if ((total += n) > max) return -1;
                o.write(b, 0, n);
            }
        } finally {
            in.close();
        }
        return total;
    }

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        if (copy(in, o, MAX_ENTRY) < 0) throw new IllegalStateException(Hindi.t("not an OpenRath backup"));
        return o.toString("UTF-8");
    }

    // ------------------------------------------------------------------ moving to another stereo

    /** What starts the settings' text in a QR code; the rest is the compressed settings in base64. */
    static final String CODE = "OPENRATH1:";
    /** The biggest QR code offered (125 modules a side): bigger ones get modules too small for a phone to read off a car screen. */
    static final int QR_VERSION = 27;
    /** Longer text values (logs, lists) stay out of the QR code; the shared file has them. */
    private static final int QR_LONGEST = 400;
    /** Largest file or text taken from another app. */
    static final long MAX_IN = MAX_PICTURES + 2 * MAX_ENTRY;

    /** Text as a QR code's settings text: compressed, then base64 for the code. Pure, for the tests. */
    static String pack(String json) throws Exception {
        java.util.zip.Deflater z = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION);
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        java.util.zip.DeflaterOutputStream d = new java.util.zip.DeflaterOutputStream(o, z);
        d.write(json.getBytes("UTF-8"));
        d.close();
        z.end();
        return CODE + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(o.toByteArray());
    }

    /** The settings text found in some text (a scanned code, a shared message), or null. Pure, for the tests. */
    static String unpack(String text) {
        if (text == null) return null;
        int at = text.indexOf(CODE);
        if (at < 0) return null;
        int end = at + CODE.length();
        while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '-' || text.charAt(end) == '_')) end++;
        try {
            byte[] packed = java.util.Base64.getUrlDecoder().decode(text.substring(at + CODE.length(), end));
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            if (copy(new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(packed)), o, MAX_ENTRY) < 0) return null;
            String json = o.toString("UTF-8");
            return new JSONObject(json).has("prefs") ? json : null;
        } catch (Exception e) {
            return null;   // cut short or not ours
        }
    }

    /** The dashboard's settings for a QR code: no secrets, no logs, lists or pictures. */
    static String qrText(Context c) throws Exception {
        JSONObject all = new JSONObject();
        all.put("app", "OpenRath");
        all.put("format", 1);
        all.put("made", System.currentTimeMillis());
        JSONObject prefs = new JSONObject();
        prefs.put("home", dump(c.getSharedPreferences("home", Context.MODE_PRIVATE), QR_LONGEST));
        all.put("prefs", prefs);
        return pack(all.toString());
    }

    /** Settings > Move to another stereo: the QR code when the settings fit one, and sharing the file. */
    static void move(final android.app.Activity a) {
        String code;
        try {
            code = qrText(a);
        } catch (Exception e) {
            code = null;
        }
        final String qr = code != null && Qr.fits(code, QR_VERSION) ? code : null;
        String size = code == null ? "" : Hindi.f("The settings come to %1$s characters; a code this screen can show well holds about 1,400.", code.length());
        android.app.AlertDialog.Builder b = new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Move to another stereo"));
        if (qr != null) {
            b.setMessage(Hindi.t("Share the file to take everything: the settings, fuel log, trips and your pictures. Send it by Bluetooth or a "
                    + "messaging app, or copy it with a file manager, then open it on the other stereo with OpenRath.") + "\n\n"
                    + Hindi.t("The QR code holds the settings only. This stereo can't read a QR code itself, so scan it with a phone and "
                    + "share the text it shows to the other stereo. PINs and passwords are never included.") + "\n\n" + size)
                    .setNeutralButton(Hindi.t("Show the QR code"), new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            Qr.show(a, "Settings for another stereo", qr, "Scan with a phone, then share the text to the other stereo.");
                        }
                    });
        } else {
            b.setMessage(Hindi.t("Share the file to take everything: the settings, fuel log, trips and your pictures. Send it by Bluetooth or a "
                    + "messaging app, or copy it with a file manager, then open it on the other stereo with OpenRath. "
                    + "PINs and passwords are never included.") + "\n\n" + Hindi.t("Too many settings for one QR code.") + (size.isEmpty() ? "" : " " + size));
        }
        b.setPositiveButton(Hindi.t("Share the file"), new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int w) {
                share(a);
            }
        }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    /** The whole backup to another app, from OpenRath's own folder (no storage permission needed). */
    private static void share(android.app.Activity a) {
        try {
            File dir = new File(a.getCacheDir(), "move");
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException(Hindi.t("no room on the unit"));
            String name = "openrath-move-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + ".zip";
            write(a, new File(dir, name));
            android.net.Uri u = android.net.Uri.parse("content://" + Files.AUTHORITY + "/" + name);
            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip")
                    .putExtra(android.content.Intent.EXTRA_STREAM, u).putExtra(android.content.Intent.EXTRA_SUBJECT, name)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(android.content.ClipData.newRawUri(name, u));
            a.startActivity(android.content.Intent.createChooser(send, Hindi.t("Send the settings with")));
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(a, Hindi.t("No app on this stereo can send files. Use Back up now and copy the file from OpenRath/backup with a USB stick."),
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            android.widget.Toast.makeText(a, Hindi.f("Couldn't back up: %1$s", e.getMessage()), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Settings > Read settings from another stereo: pick the file it sent. */
    static void pickIn(android.app.Activity a) {
        a.startActivity(new android.content.Intent(a, Receive.class).putExtra("pick", true));
    }

    /** Hands the files made for sharing to the app the owner picked, read-only, one at a time. */
    public static final class Files extends android.content.ContentProvider {
        static final String AUTHORITY = "org.openrath.launcher.move";

        @Override
        public boolean onCreate() {
            return true;
        }

        private File file(android.net.Uri u) throws java.io.FileNotFoundException {
            String n = u == null ? null : u.getLastPathSegment();
            if (n == null || !n.matches("openrath-move-[0-9-]+\\.zip")) throw new java.io.FileNotFoundException();
            File f = new File(new File(getContext().getCacheDir(), "move"), n);
            if (!f.isFile()) throw new java.io.FileNotFoundException();
            return f;
        }

        @Override
        public android.os.ParcelFileDescriptor openFile(android.net.Uri u, String mode) throws java.io.FileNotFoundException {
            if (!"r".equals(mode)) throw new java.io.FileNotFoundException();
            return android.os.ParcelFileDescriptor.open(file(u), android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        }

        @Override
        public android.database.Cursor query(android.net.Uri u, String[] cols, String sel, String[] args, String order) {
            try {
                File f = file(u);
                android.database.MatrixCursor c = new android.database.MatrixCursor(new String[]{
                        android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE});
                c.addRow(new Object[]{f.getName(), f.length()});
                return c;
            } catch (java.io.FileNotFoundException e) {
                return null;
            }
        }

        @Override
        public String getType(android.net.Uri u) {
            return "application/zip";
        }

        @Override
        public android.net.Uri insert(android.net.Uri u, android.content.ContentValues v) {
            return null;
        }

        @Override
        public int delete(android.net.Uri u, String s, String[] a) {
            return 0;
        }

        @Override
        public int update(android.net.Uri u, android.content.ContentValues v, String s, String[] a) {
            return 0;
        }
    }

    /**
     * Takes settings from another stereo: a backup file shared or opened with OpenRath, the text of
     * a settings QR code shared to it, or a file the owner picks. Asks before anything changes.
     */
    public static final class Receive extends android.app.Activity {
        private static final int PICK = 1;

        @Override
        protected void onCreate(android.os.Bundle b) {
            super.onCreate(b);
            Prefs p = new Prefs(this);
            Hindi.load(this, p);
            if (Valet.on(p)) {
                android.widget.Toast.makeText(this, Hindi.t("Settings can't be changed in valet mode."), android.widget.Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            android.content.Intent i = getIntent();
            if (b == null && i != null && i.getBooleanExtra("pick", false)) {
                try {
                    startActivityForResult(new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("*/*")
                            .addCategory(android.content.Intent.CATEGORY_OPENABLE), PICK);
                } catch (android.content.ActivityNotFoundException e) {
                    android.widget.Toast.makeText(this, Hindi.t("No file picker on this stereo. Copy the file into its Download folder and use Restore from a backup."),
                            android.widget.Toast.LENGTH_LONG).show();
                    finish();
                }
                return;
            }
            if (b == null) take(i);
        }

        @Override
        protected void onActivityResult(int code, int result, android.content.Intent data) {
            super.onActivityResult(code, result, data);
            if (code != PICK || result != RESULT_OK || data == null) {
                finish();
                return;
            }
            take(data);
        }

        /** The file or text in an intent: a zip is a backup, text may hold a settings code. */
        private void take(android.content.Intent i) {
            String text = null;
            android.net.Uri u = null;
            if (i != null) {
                u = i.getData();
                if (u == null && android.content.Intent.ACTION_SEND.equals(i.getAction())) {
                    Object s = i.getParcelableExtra(android.content.Intent.EXTRA_STREAM);
                    if (s instanceof android.net.Uri) u = (android.net.Uri) s;
                    CharSequence t = i.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT);
                    if (t != null) text = t.toString();
                }
            }
            try {
                if (u != null) {
                    File in = new File(getCacheDir(), "incoming.zip");
                    java.io.InputStream s = getContentResolver().openInputStream(u);
                    if (s == null) throw new IllegalStateException(Hindi.t("not an OpenRath backup"));
                    FileOutputStream o = new FileOutputStream(in);
                    long got;
                    try {
                        got = copy(s, o, MAX_IN);
                    } finally {
                        o.close();
                    }
                    if (got < 0) throw new IllegalStateException(Hindi.t("the file is too big to be a backup"));
                    if (isZip(in)) {
                        confirm(in, null);
                        return;
                    }
                    text = new String(java.nio.file.Files.readAllBytes(in.toPath()), "UTF-8");
                    in.delete();
                }
                String json = unpack(text);
                if (json == null) throw new IllegalStateException(Hindi.t("not an OpenRath backup"));
                confirm(null, json);
            } catch (Exception e) {
                android.widget.Toast.makeText(this, Hindi.f("Couldn't restore: %1$s", e.getMessage()), android.widget.Toast.LENGTH_LONG).show();
                finish();
            }
        }

        private static boolean isZip(File f) {
            try {
                java.io.InputStream s = new FileInputStream(f);
                try {
                    return s.read() == 'P' && s.read() == 'K';
                } finally {
                    s.close();
                }
            } catch (Exception e) {
                return false;
            }
        }

        private void confirm(final File zip, final String json) {
            new Glass.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Settings from another stereo"))
                    .setMessage(zip != null ? Hindi.t("Replace every setting, the fuel log and trip history on this stereo with the ones sent? "
                            + "A copy of what's here now is saved first, when storage is allowed. PINs and passwords stay as they are.")
                            : Hindi.t("Put the dashboard settings from the other stereo on this one? The fuel log, trips and pictures here "
                            + "stay. PINs and passwords stay as they are."))
                    .setPositiveButton(Hindi.t("Restore"), new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            try {
                                try {
                                    make(Receive.this, "before-restore");
                                } catch (Exception ignored) {
                                    // no storage permission: go on without the copy, as the owner was told
                                }
                                if (zip != null) restore(Receive.this, zip);
                                else apply(Receive.this, new JSONObject(json), true);
                                if (zip != null) zip.delete();
                                restart();
                            } catch (Exception e) {
                                android.widget.Toast.makeText(Receive.this, Hindi.f("Couldn't restore: %1$s", e.getMessage()), android.widget.Toast.LENGTH_LONG).show();
                                finish();
                            }
                        }
                    })
                    .setNegativeButton(Hindi.t("Cancel"), new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            if (zip != null) zip.delete();
                            finish();
                        }
                    })
                    .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(android.content.DialogInterface d) {
                            finish();
                        }
                    }).show();
        }

        /** Start the dashboard again in a moment, reading the new settings. */
        private void restart() {
            android.widget.Toast.makeText(this, Hindi.t("Restored; restarting"), android.widget.Toast.LENGTH_SHORT).show();
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0,
                    new android.content.Intent(this, HomeActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    android.app.PendingIntent.FLAG_CANCEL_CURRENT | 0x04000000);   // FLAG_IMMUTABLE, needed from Android 12
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null) am.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 1500, pi);
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }, 1200);
        }
    }

    private Backup() {
    }
}
