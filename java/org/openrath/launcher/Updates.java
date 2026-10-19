package org.openrath.launcher;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * New versions from the project's GitHub releases. Once a day it reads a small update.json from the
 * latest release ({"versionCode", "versionName", "apk", "sha256"}), downloads a newer APK, checks
 * its SHA-256, and offers to install it only while the car is parked. Before installing it checks
 * the APK is OpenRath, newer, and signed with the same key (Android refuses another key too).
 * Nothing is sent: it only downloads.
 */
final class Updates {

    static final String URL_DEFAULT = "https://github.com/manishekhawat/OpenRath/releases/latest/download/update.json";
    static final long DAY = 24 * 3600 * 1000L;

    /** Whether a daily check is due. */
    static boolean due(long last, long now) {
        return last <= 0 || now < last || now - last >= DAY;
    }

    /** Only https links are followed. */
    static boolean safe(String url) {
        return url != null && url.startsWith("https://") && url.length() > 12;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xFF));
        return s.toString();
    }

    /** Whether the file's SHA-256 is the one the update file names (any case, spaces ignored). */
    static boolean matches(String got, String want) {
        return got != null && want != null && got.equalsIgnoreCase(want.trim()) && got.length() == 64;
    }

    private static int ours(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static File apk(Context c) {
        return new File(c.getCacheDir(), "update.apk");
    }

    /** From the dashboard each time it comes back: check once a day, and offer a ready update when parked. */
    static void resumed(final HomeActivity a) {
        if (!Features.on(a.prefs, "updates")) return;
        offer(a);
        long now = System.currentTimeMillis();
        if (!due(a.prefs.longNum("updateCheckedAt", 0), now)) return;
        a.prefs.putLong("updateCheckedAt", now);
        check(a, false);
    }

    /** Look now. `told`: say so when there is nothing new (from the settings row). */
    static void check(final HomeActivity a, final boolean told) {
        final Context app = a.getApplicationContext();
        final Prefs p = a.prefs;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    org.json.JSONObject j = new org.json.JSONObject(fetch(p.str("updateUrl", URL_DEFAULT)));
                    int code = j.getInt("versionCode");
                    String name = j.optString("versionName", String.valueOf(code));
                    if (code <= ours(app)) {
                        msg = told ? Hindi.t("OpenRath is up to date") : null;
                    } else {
                        String url = j.getString("apk");
                        if (!safe(url)) throw new Exception("not https");
                        File f = apk(app);
                        download(url, f);
                        if (!matches(sha256(f), j.getString("sha256"))) {
                            f.delete();
                            throw new Exception("the download doesn't match its SHA-256");
                        }
                        p.put("updateReady", code + "|" + name);
                        msg = null;
                    }
                } catch (Exception e) {
                    msg = told ? Hindi.f("Couldn't check for updates: %1$s", String.valueOf(e.getMessage())) : null;
                }
                final String say = msg;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (say != null) Toast.makeText(a, say, Toast.LENGTH_LONG).show();
                        offer(a);
                    }
                });
            }
        }).start();
    }

    /** A downloaded update, offered while parked; while moving it waits for the next stop. */
    static void offer(final HomeActivity a) {
        String ready = a.prefs.str("updateReady", "");
        int bar = ready.indexOf('|');
        if (bar < 0) return;
        int code;
        try {
            code = Integer.parseInt(ready.substring(0, bar));
        } catch (NumberFormatException e) {
            code = 0;
        }
        if (code <= ours(a) || !apk(a).exists()) {
            a.prefs.put("updateReady", "");
            apk(a).delete();
            return;
        }
        if (ChildLock.moving() || Drive.kmhNow > Fixes.STILL_KMH) return;
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.f("OpenRath %1$s is ready", ready.substring(bar + 1)))
                .setMessage(Hindi.t("It was downloaded and checked. Install it now? Your settings stay."))
                .setPositiveButton(Hindi.t("Install"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        install(a);
                    }
                })
                .setNegativeButton(Hindi.t("Later"), null).show();
    }

    /**
     * Why a downloaded APK may not be installed, or null when it may: it must be OpenRath, newer than
     * the installed version, and signed with the same key. Kept apart from Android for the tests.
     */
    static String refusal(String pkg, String ours, long code, long installed, java.util.Set<String> signers, java.util.Set<String> ourSigners) {
        if (pkg == null || !pkg.equals(ours)) return Hindi.t("it is not OpenRath");
        if (code <= installed) return Hindi.t("it is not newer than this version");
        if (signers == null || signers.isEmpty() || ourSigners == null || !signers.equals(ourSigners))
            return Hindi.t("it is signed with a different key");
        return null;
    }

    /** The signing certificates of a package, installed (`path` null) or in an APK file, as hex. */
    @SuppressWarnings("deprecation")
    private static java.util.Set<String> signers(Context c, String path) {
        java.util.Set<String> out = new java.util.HashSet<>();
        android.content.pm.PackageManager pm = c.getPackageManager();
        android.content.pm.Signature[] sigs = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                // GET_SIGNING_CERTIFICATES and PackageInfo.signingInfo (Android 9), by reflection: the build's android.jar predates them
                int flag = 0x08000000;
                android.content.pm.PackageInfo pi = path == null ? pm.getPackageInfo(c.getPackageName(), flag) : pm.getPackageArchiveInfo(path, flag);
                Object si = pi == null ? null : android.content.pm.PackageInfo.class.getField("signingInfo").get(pi);
                if (si != null) sigs = (android.content.pm.Signature[]) si.getClass().getMethod("getApkContentsSigners").invoke(si);
            } else {
                int flag = android.content.pm.PackageManager.GET_SIGNATURES;
                android.content.pm.PackageInfo pi = path == null ? pm.getPackageInfo(c.getPackageName(), flag) : pm.getPackageArchiveInfo(path, flag);
                if (pi != null) sigs = pi.signatures;
            }
        } catch (Exception ignored) {
        }
        if (sigs != null) for (android.content.pm.Signature s : sigs) out.add(s.toCharsString());
        return out;
    }

    /** Checks the downloaded APK against the installed app; null when it may be installed. */
    @SuppressWarnings("deprecation")
    private static String check(Context c, File f) {
        android.content.pm.PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
        if (pi == null) return Hindi.t("it is not OpenRath");
        return refusal(pi.packageName, c.getPackageName(), pi.versionCode, ours(c), signers(c, f.getPath()), signers(c, null));
    }

    private static void install(Context c) {
        File apk = apk(c);
        String why = check(c, apk);
        if (why != null) {
            apk.delete();
            new Prefs(c).put("updateReady", "");
            Toast.makeText(c, Hindi.f("Couldn't install the update: %1$s", why), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            PackageInstaller pi = c.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(c.getPackageName());
            int id = pi.createSession(sp);
            PackageInstaller.Session s = pi.openSession(id);
            File f = apk(c);
            InputStream in = new FileInputStream(f);
            OutputStream out = s.openWrite("openrath.apk", 0, f.length());
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            s.fsync(out);
            out.close();
            in.close();
            // FLAG_MUTABLE (Android 12+): the installer fills in the result
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0);
            PendingIntent done = PendingIntent.getBroadcast(c, 46, new Intent(c, Done.class), flags);
            s.commit(done.getIntentSender());
            s.close();
        } catch (Exception e) {
            Toast.makeText(c, Hindi.f("Couldn't install the update: %1$s", String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
        }
    }

    /** The installer's answer: Android asks the owner to confirm, then the new version starts. */
    public static final class Done extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String why = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(c, Hindi.f("Couldn't install the update: %1$s", why == null ? "" : why), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static HttpURLConnection open(String u) throws Exception {
        if (!safe(u)) throw new Exception("not https");
        HttpURLConnection c = Compat.https(u);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "OpenRath");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        return c;
    }

    private static String fetch(String u) throws Exception {
        HttpURLConnection c = open(u);
        InputStream in = c.getInputStream();
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0 && b.size() < 65536; ) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    private static void download(String u, File to) throws Exception {
        HttpURLConnection c = open(u);
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(to);
        byte[] buf = new byte[65536];
        long total = 0;
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
            if ((total += n) > 200L * 1024 * 1024) throw new Exception("too big");
        }
        out.close();
        in.close();
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        in.close();
        return hex(md.digest());
    }

    private Updates() {
    }
}
