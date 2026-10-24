package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * "How the stock launcher does it": the names inside the stock launcher's and radio's code that are about
 * the radio and the lights (broadcast actions, service commands, settings keys). They show which
 * route the stock radio widget and the headlight picture use, so OpenRath can use the same one.
 * Preinstalled apps on Android 8 keep their code in oat/…/*.vdex next to the APK; both are read.
 */
final class Peek {

    static final String[] PKGS = {"com.tw.radio", "com.tw.service.xt", "com.tw.core", "com.tw.bt"};
    private static final Pattern WANT = Pattern.compile(
            "(?i)(radio|\\bfm|fm_|freq|tuner|light|lamp|illum|headl|ill_|_ill|night|turn_?l|turn_?r|indicat|"
                    + "openproject|channel|twutil|tw\\.john|mcu|backcar|reverse|calllog|call_log|phonebook|contact|btcall|lastcall|callnumber|phonenumber|callname)");
    private static final Pattern SKIP = Pattern.compile("^(android|java|javax|kotlin|dalvik|org|com\\.google|androidx)[./].*|.*\\s.*");

    /**
     * Copies the stock launcher's and radio service's own files (the APK, and on preinstalled apps the
     * oat code next to it) to Download/OpenRath stock apps and to any USB stick, so they can be sent for
     * a closer look. A theme plugin only holds pictures; the logic is in these.
     */
    static void save(final Activity a) {
        final ProgressDialog wait = ProgressDialog.show(a, null, Hindi.t("Copying the stock launcher…"), true);
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final StringBuilder out = new StringBuilder();
                List<File> dirs = new ArrayList<>();
                dirs.add(new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "OpenRath stock apps"));
                for (File s : BootArt.sticks()) dirs.add(new File(s, "OpenRath stock apps"));
                PackageManager pm = a.getPackageManager();
                Set<String> pkgs = new LinkedHashSet<>(java.util.Arrays.asList(PKGS));
                try {
                    for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN)
                            .addCategory(android.content.Intent.CATEGORY_HOME), 0)) {
                        if (!r.activityInfo.packageName.equals(a.getPackageName())) pkgs.add(r.activityInfo.packageName);
                    }
                } catch (Throwable ignored) {
                    // not readable on this unit
                }
                // theme apps and other launchers (iLauncher, the stock theme app): they know how the
                // stock unit reads the headlights for day and night
                try {
                    for (ApplicationInfo ai : pm.getInstalledApplications(0))
                        if (themeLike(ai.packageName) && !ai.packageName.equals(a.getPackageName())) pkgs.add(ai.packageName);
                } catch (Throwable ignored) {
                    // not readable on this unit
                }
                List<String> saved = new ArrayList<>();
                for (String pkg : pkgs) {
                    ApplicationInfo ai;
                    try {
                        ai = pm.getApplicationInfo(pkg, 0);
                    } catch (Exception e) {
                        continue;
                    }
                    List<File[]> files = new ArrayList<>();
                    files.add(new File[]{new File(ai.sourceDir), null});
                    File[] arches = new File(new File(ai.sourceDir).getParentFile(), "oat").listFiles();
                    if (arches != null) for (File arch : arches) {
                        File[] fs = arch.listFiles();
                        if (fs != null) for (File f : fs) files.add(new File[]{f, arch});
                    }
                    for (File[] f : files) {
                        String name = pkg + (f[1] == null ? ".apk" : "-" + f[1].getName() + "-" + f[0].getName());
                        boolean ok = false;
                        for (File d : dirs) {
                            try {
                                if (!d.isDirectory() && !d.mkdirs()) continue;
                                BootArt.copy(f[0], new File(d, name));
                                ok = true;
                            } catch (Throwable ignored) {
                                // not readable on this unit: try the next
                            }
                        }
                        if (ok) saved.add(name + " (" + f[0].length() / 1024 + " KB)");
                    }
                }
                if (saved.isEmpty()) out.append(Hindi.t("Nothing could be copied. Android may not let apps read the stock apps on this unit."));
                else {
                    out.append(Hindi.t("Copied to:")).append("\n");
                    for (File d : dirs) if (d.isDirectory()) out.append("  ").append(d.getPath()).append('\n');
                    out.append("\n");
                    for (String s : saved) out.append(s).append('\n');
                    out.append("\n").append(Hindi.t("Send the files ending in .apk and .vdex (or .odex) for the launcher."));
                }
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wait.dismiss();
                        } catch (Throwable ignored) {
                            // already gone
                        }
                        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle(Hindi.t("Stock launcher copied")).setMessage(out.toString())
                                .setPositiveButton(Hindi.t("OK"), null).show();
                    }
                });
            }
        });
    }

    /** Apps worth copying for how the stock unit switches day and night: themes and launchers. */
    static boolean themeLike(String pkg) {
        String l = pkg.toLowerCase(Locale.US);
        if (l.startsWith("com.android.") || l.startsWith("android.") || l.startsWith("com.google.")) return false;
        return l.contains("theme") || l.contains("launcher") || l.contains("skin");
    }

    private static final String SKIPPED = "Skipped: reading the stock apps stopped OpenRath before.\nUse Help > Clear the crash note to try again.";

    static void run(final Activity a) {
        if (!Crash.begin("reading the stock launcher")) {
            show(a, SKIPPED);
            return;
        }
        final ProgressDialog wait = ProgressDialog.show(a, null, Hindi.t("Reading the stock launcher and radio…"), true);
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final StringBuilder out = new StringBuilder();
                try {
                    collect(a, out);
                } catch (Throwable e) {
                    out.append("\nStopped: ").append(e);
                }
                Crash.end();
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            wait.dismiss();
                        } catch (Throwable ignored) {
                            // already gone
                        }
                        show(a, out.toString());
                    }
                });
            }
        });
    }

    private static void collect(Activity a, StringBuilder out) {
        PackageManager pm = a.getPackageManager();
        Set<String> pkgs = new LinkedHashSet<>(java.util.Arrays.asList(PKGS));
        // every other home-screen app too: including the stock launcher
        try {
            for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME), 0)) {
                if (!r.activityInfo.packageName.equals(a.getPackageName())) pkgs.add(r.activityInfo.packageName);
            }
        } catch (Throwable ignored) {
            // not readable on this unit
        }
        for (String pkg : pkgs) {
            ApplicationInfo ai;
            try {
                ai = pm.getApplicationInfo(pkg, 0);
            } catch (Exception e) {
                continue;
            }
            Set<String> found = new LinkedHashSet<>();
            int sources = 0;
            // code inside the APK
            try {
                ZipFile z = new ZipFile(ai.sourceDir);
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (!(n.startsWith("classes") && n.endsWith(".dex")) || e.getSize() > 16L * 1024 * 1024) continue;
                    scan(read(z.getInputStream(e)), found);
                    sources++;
                }
                z.close();
            } catch (Throwable ignored) {
                // not readable on this unit: try the next
            }
            // preinstalled: the dex sits in oat/<arch>/<name>.vdex or .odex
            File dir = new File(ai.sourceDir).getParentFile();
            File oat = new File(dir, "oat");
            File[] arches = oat.listFiles();
            if (arches != null) for (File arch : arches) {
                File[] fs = arch.listFiles();
                if (fs == null) continue;
                for (File f : fs) {
                    if (!(f.getName().endsWith(".vdex") || f.getName().endsWith(".odex")) || f.length() > 24L * 1024 * 1024) continue;
                    try {
                        scan(read(new FileInputStream(f)), found);
                        sources++;
                    } catch (Throwable ignored) {
                        // not readable on this unit: try the next
                    }
                }
            }
            out.append("■ ").append(pkg).append(" (").append(sources).append(sources == 1 ? " code file, " : " code files, ")
                    .append(found.size()).append(" names)\n");
            int shown = 0;
            StringBuilder line = new StringBuilder();
            for (String s : found) {
                if (shown++ >= 160) {
                    line.append(" …");
                    break;
                }
                line.append(s).append("   ");
            }
            out.append(line).append("\n");
            out.append(components(pm, pkg)).append("\n");
        }
    }

    /** The app's services, receivers and content providers: how other apps reach it. */
    private static String components(PackageManager pm, String pkg) {
        StringBuilder b = new StringBuilder();
        try {
            android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES
                    | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS);
            if (pi.services != null) for (android.content.pm.ServiceInfo x : pi.services)
                b.append("service ").append(x.name.replace(pkg, "")).append(x.exported ? "" : " (private)").append("   ");
            if (pi.receivers != null) for (android.content.pm.ActivityInfo x : pi.receivers)
                b.append("receiver ").append(x.name.replace(pkg, "")).append(x.exported ? "" : " (private)").append("   ");
            if (pi.providers != null) for (android.content.pm.ProviderInfo x : pi.providers)
                b.append("provider ").append(x.authority).append(x.exported ? "" : " (private)").append("   ");
        } catch (Throwable ignored) {
            // not on this unit
        }
        return b.toString();
    }

    /** Every dex inside these bytes (a vdex holds them one after another). */
    private static void scan(byte[] b, Set<String> found) {
        for (int i = 0; i + 8 < b.length; i++) {
            if (b[i] != 'd' || b[i + 1] != 'e' || b[i + 2] != 'x' || b[i + 3] != '\n' || b[i + 7] != 0) continue;
            int size = (b[i + 32] & 0xFF) | (b[i + 33] & 0xFF) << 8 | (b[i + 34] & 0xFF) << 16 | (b[i + 35] & 0xFF) << 24;
            if (size < 0x70 || i + size > b.length) continue;
            byte[] d = new byte[size];
            System.arraycopy(b, i, d, 0, size);
            for (String s : Strings.dex(d)) {
                if (s.length() > 90 || SKIP.matcher(s).matches() || !WANT.matcher(s).find()) continue;
                if (s.startsWith("L") && s.endsWith(";")) continue;   // class type names: too many
                found.add(s);
            }
            i += size - 1;
        }
    }

    private static byte[] read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        return o.toByteArray();
    }

    private static void show(Activity a, String text) {
        String saved = "";
        try {
            File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "openrath-launcher-report.txt");
            FileWriter w = new FileWriter(f);
            w.write(text);
            w.close();
            saved = Hindi.f("Saved to %1$s", f.getPath()) + "\n\n";
        } catch (Throwable ignored) {
            // can't save here: the text is still shown
        }
        TextView t = new TextView(a);
        t.setText(saved + Hindi.t(text));   // the report itself stays in English; only the note above is translated
        t.setTextSize(10);
        t.setTextColor(0xFFE0E0E0);
        t.setTextIsSelectable(true);
        int pad = (int) (12 * a.getResources().getDisplayMetrics().density);
        t.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(a);
        sv.addView(t);
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("How the stock launcher does it (photograph each screen)"))
                .setView(sv)
                .setPositiveButton(Hindi.t("OK"), null).show();
    }

    private Peek() {
    }
}
