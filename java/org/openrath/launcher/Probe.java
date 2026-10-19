package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * "Unit report": what the stock radio and launcher expose (services, broadcasts, content
 * providers, names inside their code that mention the radio or the lights), plus every message the
 * Topway MCU sends while it listens. A photo of it shows how the stock launcher does what it does.
 * It is also saved to Download/openrath-unit-report.txt.
 */
final class Probe {

    private static final Pattern INTERESTING = Pattern.compile(
            "(?i)(freq|radio|tuner|\\bfm\\b|_fm|fm_|ill|light|night|lamp|backlight|headl|turn_?sig|indicat|content://|tw\\.john|twutil|mcu)");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][\\w$]*([./][\\w$]+)+$|^content://\\S+$");

    static void run(final Activity a, final String radioPkg, final Xt xt) {
        final ProgressDialog wait = ProgressDialog.show(a, null,
                "Building the unit report. If the radio is on, change station once with the steering wheel…", true);
        final StringBuilder out = new StringBuilder();
        final Tw[] spy = {null};
        final Set<String> msgs = new LinkedHashSet<>();
        if (Tw.available() && Crash.begin("listening to the Topway MCU")) {
            Handler h = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message m) {
                    synchronized (msgs) {
                        if (msgs.size() < 60) msgs.add(m.what + "," + m.arg1 + "," + m.arg2
                                + (m.obj instanceof String ? " \"" + m.obj + "\"" : ""));
                    }
                }
            };
            int[] codes = new int[]{513, 769, 1025, 1026, 1028, 1029, 1030, 0x9E00};
            try {
                spy[0] = new Tw(0, codes, h, "probe");
            } catch (Throwable e) {
                out.append("MCU listen failed: ").append(e).append("\n");
            }
            Crash.end();
        }
        Bg.run(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = a.getPackageManager();
                Set<String> pkgs = new LinkedHashSet<>();
                if (radioPkg != null) pkgs.add(radioPkg);
                for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                    String p = ai.packageName.toLowerCase(Locale.US);
                    if (p.startsWith("com.tw.") || p.contains("variety") || p.contains("topway")) pkgs.add(ai.packageName);
                }
                if (xt != null) {
                    out.append("■ Radio service ").append(Xt.PKG).append(": ")
                            .append(xt.ready() ? xt.descriptor : "not connected " + xt.error).append("\n");
                    out.append("  methods: ").append(android.text.TextUtils.join("  ", xt.methods)).append("\n");
                    out.append("  callback: ").append(android.text.TextUtils.join("  ", xt.callbacks))
                            .append(xt.registered ? "  (registered)" : "  (not registered)").append("\n");
                    out.append("  heard: ").append(xt.heard.isEmpty() ? "nothing" : android.text.TextUtils.join("  ", xt.heard)).append("\n\n");
                }
                for (String p : new String[]{"com.tw.core", Xt.PKG}) {
                    if (!Crash.begin("reading " + p + " constants")) continue;
                    try {
                        constants(a, p, out);
                    } catch (Throwable e) {
                        out.append(p).append(" constants: ").append(e).append("\n");
                    }
                    Crash.end();
                }
                for (String p : pkgs) {
                    try {
                        describe(pm, p, out);
                    } catch (Throwable e) {
                        out.append(p).append(": ").append(e).append("\n");
                    }
                }
                try {
                    Thread.sleep(6000);   // time for MCU messages to arrive
                } catch (InterruptedException ignored) {
                    // woken early: carry on
                }
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (spy[0] != null) spy[0].close();
                        StringBuilder full = new StringBuilder();
                        full.append("MCU messages (what,arg1,arg2): ");
                        synchronized (msgs) {
                            full.append(msgs.isEmpty() ? "none" : android.text.TextUtils.join("  |  ", msgs));
                        }
                        full.append("\n\n").append(out);
                        wait.dismiss();
                        show(a, full.toString());
                    }
                });
            }
        });
    }

    private static void describe(PackageManager pm, String pkg, StringBuilder out) throws Exception {
        PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS
                | PackageManager.GET_PROVIDERS | PackageManager.GET_ACTIVITIES);
        out.append("■ ").append(pkg).append(" ").append(pi.versionName).append("\n");
        if (pi.services != null) for (ServiceInfo s : pi.services)
            out.append("  service ").append(s.name).append(s.exported ? " (open)" : "").append("\n");
        if (pi.receivers != null) for (ActivityInfo r : pi.receivers)
            out.append("  receiver ").append(r.name).append(r.exported ? " (open)" : "").append("\n");
        if (pi.providers != null) for (ProviderInfo p : pi.providers)
            out.append("  provider ").append(p.authority).append(p.exported ? " (open)" : "").append("\n");
        Set<String> names = new LinkedHashSet<>();
        ZipFile z = new ZipFile(pi.applicationInfo.sourceDir);
        try {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                boolean dex = n.startsWith("classes") && n.endsWith(".dex");
                if (!dex && !n.equals("AndroidManifest.xml")) continue;
                if (e.getSize() > 12L * 1024 * 1024 || e.getSize() < 0) continue;
                byte[] b = bytes(z.getInputStream(e));
                for (String s : dex ? Strings.dex(b) : Strings.axml(b)) {
                    if (s.length() > 90 || !NAME.matcher(s).matches() || !INTERESTING.matcher(s).find()) continue;
                    if (s.startsWith("Landroid/") || s.startsWith("Ljava/")) continue;
                    names.add(s);
                    if (names.size() >= 80) break;
                }
            }
        } finally {
            z.close();
        }
        if (!names.isEmpty()) out.append("  names: ").append(android.text.TextUtils.join("  ", names)).append("\n");
        out.append("\n");
    }

    private static final Pattern WANTED_CLASS = Pattern.compile(
            "(?i).*(messenger|msg|const|key|cmd|command|action|aidl|light|ill|night|radio|car(info|state|data)?).*");

    /** Interfaces and constants in a Topway system app: the vocabulary of its messages. */
    private static void constants(Activity a, String pkg, StringBuilder out) throws Exception {
        ApplicationInfo ai = a.getPackageManager().getApplicationInfo(pkg, 0);
        // reading a constant runs the class's code: only for the firmware's own apps
        if (!Compat.systemFlags(ai.flags)) {
            out.append(pkg).append(" constants: skipped, not part of the firmware\n\n");
            return;
        }
        ClassLoader cl = a.createPackageContext(pkg, android.content.Context.CONTEXT_INCLUDE_CODE
                | android.content.Context.CONTEXT_IGNORE_SECURITY).getClassLoader();
        out.append("■ ").append(pkg).append(" classes\n");
        ZipFile z = new ZipFile(ai.sourceDir);
        int shown = 0;
        try {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements() && shown < 40) {
                ZipEntry e = en.nextElement();
                if (!e.getName().startsWith("classes") || !e.getName().endsWith(".dex")) continue;
                if (e.getSize() > 12L * 1024 * 1024 || e.getSize() < 0) continue;
                for (String d : Strings.dex(bytes(z.getInputStream(e)))) {
                    if (!d.startsWith("Lcom/tw/") || !d.endsWith(";") || shown >= 40) continue;
                    String name = d.substring(1, d.length() - 1).replace('/', '.');
                    if (!WANTED_CLASS.matcher(name.substring(name.lastIndexOf('.') + 1)).matches()) continue;
                    Class<?> c;
                    try {
                        c = Class.forName(name, false, cl);
                    } catch (Throwable t) {
                        continue;
                    }
                    StringBuilder b = new StringBuilder();
                    if (c.isInterface()) {
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) b.append(" ").append(Xt.signature(m));
                    }
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        int mod = f.getModifiers();
                        if (!java.lang.reflect.Modifier.isStatic(mod) || !java.lang.reflect.Modifier.isFinal(mod)) continue;
                        Class<?> t = f.getType();
                        if (t != int.class && t != String.class && t != long.class && t != boolean.class) continue;
                        try {
                            f.setAccessible(true);
                            b.append(" ").append(f.getName()).append("=").append(f.get(null));
                        } catch (Throwable ignored) {
                            // not readable on this unit
                        }
                        if (b.length() > 900) break;
                    }
                    if (b.length() == 0) continue;
                    out.append("  ").append(name.substring(pkg.length())).append(":").append(b).append("\n");
                    shown++;
                }
            }
        } finally {
            z.close();
        }
        out.append("\n");
    }

    private static byte[] bytes(InputStream in) throws java.io.IOException {
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
            File d = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            File f = new File(d, "openrath-unit-report.txt");
            FileWriter w = new FileWriter(f);
            w.write(text);
            w.close();
            saved = "Saved to " + f.getPath() + "\n\n";
        } catch (Throwable ignored) {
            // can't save here: the text is still shown
        }
        TextView t = new TextView(a);
        t.setText(saved + text);
        t.setTextSize(11);
        t.setTextColor(0xFFE0E0E0);
        t.setTextIsSelectable(true);
        int pad = (int) (16 * a.getResources().getDisplayMetrics().density);
        t.setPadding(pad, pad, pad, pad);
        ScrollView sv = new ScrollView(a);
        sv.addView(t);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Unit report (photograph each screen)")
                .setView(sv)
                .setPositiveButton("OK", null).show();
    }

    private Probe() {
    }
}
