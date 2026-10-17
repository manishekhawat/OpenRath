package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The headlight signal. Head units don't agree on where they publish the illumination wire, so
 * the setup takes three snapshots of the unit's settings and system properties (lights off, on,
 * off again) and keeps whatever followed the lights. Besides settings and properties it listens
 * for broadcasts that the unit's own apps (launcher, car settings, MCU service) know about with
 * names like ILL or NIGHT, found by reading the strings in those apps, and reads the backlight
 * files. Keys look like "system:ill_state", "prop:sys.tw.ill", "bcast:com.x.ILL|state",
 * "bcast:last", "file:/sys/class/backlight/x/brightness" or "uimode:night".
 */
final class Lights {

    private static final Uri[] TABLES = {Settings.System.CONTENT_URI, Settings.Global.CONTENT_URI, Settings.Secure.CONTENT_URI};
    private static final String[] TABLE_NAMES = {"system", "global", "secure"};
    private static final Pattern PROP = Pattern.compile("^\\[(.+?)\\]: \\[(.*)\\]$");
    // things that change on their own and would look like a match
    private static final String[] NOISE = {"time", "date", "uptime", "count", "boot", "rescue", "tombstone",
            "anr", "battery", "_ms", ".ms", "clock", "random", "seed", "session", "token", "location", "gps"};
    // names that hint at the headlight signal; these go to the top of the list
    private static final String[] HINTS = {"ill", "light", "lamp", "night", "dim", "dark", "backlight", "brightness", "headl"};

    // package names worth reading for broadcast names
    private static final Pattern PKG_HINT = Pattern.compile(
            "topway|variety|\\.tw\\.|^com\\.tw|mcu|canbus|carsetting|car\\.setting|carservice|\\.car\\b|launcher|syu|fyt|hct|microntek|backlight|systemui|\\.ts\\.|^com\\.ts");
    private static final Pattern ACTION = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$");
    private static final Pattern ACTION_HINT = Pattern.compile(
            "(?i)((^|[._])ill([._]|on|off|state|umin|$))|illum|night|headl|head_l|lamp|backlight|dimm|day_?mode|daynight|day_night|carlight|car_light|turn_?(light|signal|lamp|left|right|state)|turnsignal|indicat|blinker|winker|hazard|canbus|can_?info|carinfo|car_?state|cardata");

    private static Method sysGet;

    /** Also read the system apps for broadcast names and listen to the MCU (slower, riskier). */
    static boolean deep;

    /** Latest broadcast state, as keys "bcast:ACTION|extra" and "bcast:last". */
    static final Map<String, String> live = new HashMap<>();
    /** Topway's radio service, when connected: its light getters are read directly. */
    static volatile Xt xt;
    static String stats = "";

    // ------------------------------------------------------------------ reading one key

    /** Current value of a learnt key, or null if it can't be read. */
    static String read(Context c, String key) {
        int i = key.indexOf(':');
        String src = key.substring(0, i), name = key.substring(i + 1);
        try {
            if ("uimode".equals(src)) return night(c);
            if ("prop".equals(src)) return prop(name);
            if ("bcast".equals(src) || "tw".equals(src) || "xt".equals(src)) {
                synchronized (live) {
                    return live.get(key);
                }
            }
            if ("file".equals(src)) return file(name);
            if ("xtget".equals(src)) {
                Xt x = xt;
                Object v = x == null ? null : x.value(name);
                return v == null ? null : String.valueOf(v);
            }
            ContentResolver r = c.getContentResolver();
            if ("system".equals(src)) return Settings.System.getString(r, name);
            if ("global".equals(src)) return Settings.Global.getString(r, name);
            if ("secure".equals(src)) return Settings.Secure.getString(r, name);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String night(Context c) {
        int n = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return n == Configuration.UI_MODE_NIGHT_YES ? "yes" : "no";
    }

    private static String prop(String name) throws Exception {
        if (sysGet == null) sysGet = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
        return (String) sysGet.invoke(null, name);
    }

    private static String file(String path) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(path));
            String s = r.readLine();
            r.close();
            return s == null ? "" : s.trim();
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ broadcasts

    /** Listen for these actions and keep their latest extras in `live`. Unregister the result when done. */
    static BroadcastReceiver listen(Context c, Collection<String> actions) {
        if (actions.isEmpty()) return null;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context x, Intent i) {
                // other apps' extras can hold their own classes, which can't be read here and
                // used to take the app down during the deep search
                try {
                    record(i);
                } catch (Throwable ignored) {
                }
            }
        };
        IntentFilter f = new IntentFilter();
        for (String a : actions) f.addAction(a);
        try {
            Intent sticky = Compat.fromOthers(c.getApplicationContext(), r, f);
            if (sticky != null) record(sticky);
        } catch (Throwable e) {
            return null;
        }
        return r;
    }

    private static void record(Intent i) {
        String a = i.getAction();
        if (a == null) return;
        synchronized (live) {
            live.put("bcast:last", a);
            Bundle b = i.getExtras();
            if (b != null) {
                b.setClassLoader(Lights.class.getClassLoader());
                for (String k : b.keySet()) {
                    try {
                        live.put("bcast:" + a + "|" + k, String.valueOf(b.get(k)));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    /** The MCU message code a learnt "tw:WHAT:ARG1" key needs, or -1. */
    static int twCode(String[] sig) {
        if (sig == null || !sig[0].startsWith("tw:")) return -1;
        try {
            return Integer.parseInt(sig[0].split(":")[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    /** The actions a learnt "bcast:" key needs to listen for. */
    static List<String> actionsFor(String[] sig) {
        List<String> out = new ArrayList<>();
        if (sig == null || !sig[0].startsWith("bcast:")) return out;
        if ("bcast:last".equals(sig[0])) {
            if (!sig[1].isEmpty()) out.add(sig[1]);
            for (String v : sig[2].split("\n")) if (!v.isEmpty()) out.add(v);
        } else {
            String a = sig[0].substring(6);
            out.add(a.substring(0, a.indexOf('|')));
        }
        return out;
    }

    /** Broadcast names that the unit's own system apps mention and that sound like the headlights. */
    static Set<String> scanActions(Context c) {
        Set<String> found = new LinkedHashSet<>();
        if (!Crash.begin("reading system apps")) {
            stats = Hindi.t("skipped reading system apps (it crashed before)");
            return found;
        }
        try {
            scanInto(c, found);
        } catch (Throwable e) {
            stats = Hindi.f("reading system apps failed: %1$s", e);
        }
        Crash.end();
        return found;
    }

    private static void scanInto(Context c, Set<String> found) {
        PackageManager pm = c.getPackageManager();
        int apps = 0;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || ai.sourceDir == null) continue;
            if (!PKG_HINT.matcher(ai.packageName.toLowerCase(Locale.US)).find()) continue;
            apps++;
            ZipFile z = null;
            try {
                z = new ZipFile(ai.sourceDir);
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName();
                    boolean dex = n.startsWith("classes") && n.endsWith(".dex");
                    if (!dex && !n.equals("AndroidManifest.xml")) continue;
                    if (e.getSize() > 12L * 1024 * 1024 || e.getSize() < 0) continue;
                    byte[] b = bytes(z.getInputStream(e));
                    for (String str : dex ? Strings.dex(b) : Strings.axml(b)) {
                        if (str.length() < 6 || str.length() > 120) continue;
                        if (ACTION.matcher(str).matches() && ACTION_HINT.matcher(str).find()) found.add(str);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                try {
                    if (z != null) z.close();
                } catch (Exception ignored) {
                }
            }
            // odex'd system apps keep their code next to the apk; the manifest still helps
            if (found.size() > 400) break;
        }
        stats = Hindi.f("listening for %1$s broadcasts from %2$s system apps", found.size(), apps);
    }

    private static byte[] bytes(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        return o.toByteArray();
    }

    // ------------------------------------------------------------------ snapshots

    private static String lastCounts = "";

    static Map<String, String> snapshot(Context c) {
        Map<String, String> m = new HashMap<>();
        for (int t = 0; t < TABLES.length; t++) {
            Cursor cur = null;
            try {
                cur = c.getContentResolver().query(TABLES[t], new String[]{"name", "value"}, null, null, null);
                while (cur != null && cur.moveToNext()) {
                    m.put(TABLE_NAMES[t] + ":" + cur.getString(0), String.valueOf(cur.getString(1)));
                }
            } catch (Exception ignored) {
                // some tables can't be listed on newer Android
            } finally {
                if (cur != null) cur.close();
            }
        }
        try {
            Process p = Runtime.getRuntime().exec("getprop");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                Matcher x = PROP.matcher(line.trim());
                if (x.matches()) m.put("prop:" + x.group(1), x.group(2));
            }
            r.close();
        } catch (Exception ignored) {
        }
        for (String dir : new String[]{"/sys/class/backlight", "/sys/class/leds"}) {
            File[] l = new File(dir).listFiles();
            if (l == null) continue;
            for (File f : l) {
                for (String n : new String[]{"brightness", "actual_brightness"}) {
                    String v = file(f.getPath() + "/" + n);
                    if (v != null) m.put("file:" + f.getPath() + "/" + n, v);
                }
            }
        }
        synchronized (live) {
            m.putAll(live);
        }
        Xt x = xt;
        if (x != null && x.ready()) {
            try {
                for (Map.Entry<String, String> e : x.lightGetters().entrySet()) m.put("xtget:" + e.getKey(), e.getValue());
            } catch (Throwable ignored) {
            }
        }
        m.put("uimode:night", night(c));
        int[] n = new int[5];
        for (String k : m.keySet()) {
            if (k.startsWith("prop:")) n[0]++;
            else if (k.startsWith("bcast:")) n[1]++;
            else if (k.startsWith("tw:")) n[4]++;
            else if (k.startsWith("file:")) n[2]++;
            else if (k.startsWith("xtget:")) n[1]++;
            else if (!k.startsWith("uimode") && !k.startsWith("tw:")) n[3]++;
        }
        lastCounts = Hindi.f("%1$s settings, %2$s system properties, %3$s backlight files, %4$s broadcast values, %5$s received",
                n[3], n[0], n[2], n[1], setupSpy == null ? Hindi.f("no Topway MCU link (%1$s)", Tw.spyError)
                : Hindi.f("%1$s Topway MCU values from %2$s codes", n[4], Tw.spyCodesOpen));
        return m;
    }

    static final class Candidate {
        String key, off, on;
        int score;
    }

    /** Keys that were the same with the lights off both times, and different with them on. */
    static List<Candidate> diff(Map<String, String> off1, Map<String, String> on, Map<String, String> off2) {
        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, String> e : on.entrySet()) {
            String k = e.getKey(), a = off1.get(k), b = off2.get(k), v = e.getValue();
            if (a == null || !a.equals(b) || a.equals(v)) continue;
            String lk = k.toLowerCase(Locale.US);
            boolean noise = false;
            for (String n : NOISE) if (lk.contains(n)) noise = true;
            if (noise) continue;
            Candidate c = new Candidate();
            c.key = k;
            c.off = a;
            c.on = v;
            for (String h : HINTS) if (lk.contains(h)) c.score += 2;
            if (k.startsWith("uimode")) c.score += 1;
            if (k.startsWith("tw:")) c.score += 3;   // straight from the car's wiring
            if (k.startsWith("xtget:")) c.score += 4;   // the unit's own service, as the stock launcher reads it
            if ("door".equals(kind) && lk.contains("door")) c.score += 4;
            if ("belt".equals(kind) && (lk.contains("belt") || lk.contains("seat"))) c.score += 4;
            if (!"lights".equals(kind) && (lk.contains("brightness") || k.startsWith("uimode"))) continue;
            if (dims(k, a, v)) c.score += 3;   // the screen dims with the headlights, as on many units
            else if (lk.contains("brightness")) c.score -= 1;   // brightness can also change for other reasons
            out.add(c);
        }
        Collections.sort(out, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate x, Candidate y) {
                return y.score != x.score ? y.score - x.score : x.key.compareTo(y.key);
            }
        });
        return out;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /** A screen brightness that went down with the lights on: the unit dimming for the night. */
    static boolean dims(String key, String off, String on) {
        String lk = key.toLowerCase(Locale.US);
        if (!(lk.contains("brightness") || lk.contains("backlight") || lk.contains("dim"))) return false;
        double a = num(off), b = num(on);
        return !Double.isNaN(a) && !Double.isNaN(b) && b < a;
    }

    /**
     * Whether a reading of the learnt signal means the lights are on. A number (a screen
     * brightness) counts as on when it is nearer the "on" reading than the "off" one, so a dimming
     * that lands a step off, or a fade still under way, is still read right. Anything else counts
     * as on whenever it isn't the "lights off" value.
     */
    static boolean isOn(String v, String[] sig) {
        if (v == null || sig == null) return false;
        double x = num(v), off = num(sig[1]), on = num(sig[2]);
        if (!Double.isNaN(x) && !Double.isNaN(off) && !Double.isNaN(on) && off != on)
            return Math.abs(x - on) < Math.abs(x - off);
        return !v.equals(sig[1]);
    }

    /** How a learnt key reads in the setup's list. */
    static String label(Candidate c) {
        return (dims(c.key, c.off, c.on) ? Hindi.t("Screen dimming:") + " " : "") + c.key;
    }

    // ------------------------------------------------------------------ the setup dialogs

    interface Done {
        void done();
    }

    /** True when minute-of-day `now` is inside from..to; the window may run past midnight (18:30 to 06:30). */
    static boolean inWindow(int now, int from, int to) {
        if (from == to) return false;
        return from < to ? now >= from && now < to : now >= from || now < to;
    }

    static String hhmm(int m) {
        return String.format(java.util.Locale.US, "%02d:%02d", m / 60 % 24, m % 60);
    }

    /** What the setup is learning: "lights", or another on/off signal such as "door" or "belt". */
    private static String kind = "lights";

    static void setup(final Activity a, final Prefs prefs, final Done done) {
        learn(a, prefs, "lights", done);
    }

    /** The same three-step setup for any on/off signal the unit may pass on: a door, the seat belt. */
    static void learn(final Activity a, final Prefs prefs, String what, final Done done) {
        kind = what;
        final ProgressDialog wait = ProgressDialog.show(a, null, Hindi.f("Looking for the %1$s signal…", Hindi.t(noun())), true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Set<String> actions = deep ? scanActions(a) : new LinkedHashSet<String>();
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wait.dismiss();
                        listenWide(a, actions);
                        start(a, prefs, done);
                    }
                });
            }
        }).start();
    }

    private static BroadcastReceiver setupReceiver;
    private static Tw setupSpy;

    private static boolean wideRunning;

    /**
     * Start listening for the setup. The deep search's wide listening stays marked as a risky step
     * until the setup ends, so if the app dies while it runs (even in the unit's own code), the
     * next deep search leaves that part out instead of crashing again.
     */
    private static void listenWide(Context c, Set<String> actions) {
        if (deep) {
            if (!Crash.begin("deep search listening")) {
                stats = Hindi.t("deep search listening skipped (it stopped the app before)");
                return;
            }
            wideRunning = true;
        }
        setupReceiver = listen(c, actions);
        if (deep) {
            setupSpy = Tw.guardedSpy(Tw.spyCodes());
            Crash.begin("deep search listening");   // guardedSpy ended its own step; mark ours again
        }
    }

    private static void stopListening(Context c) {
        if (wideRunning) {
            wideRunning = false;
            Crash.end();
        }
        if (setupSpy != null) {
            setupSpy.close();
            setupSpy = null;
        }
        if (setupReceiver != null) {
            try {
                c.getApplicationContext().unregisterReceiver(setupReceiver);
            } catch (Exception ignored) {
            }
            setupReceiver = null;
        }
    }

    private static String noun() {
        return "door".equals(kind) ? "door" : "belt".equals(kind) ? "seat-belt" : "headlight";
    }

    /** The three steps for each kind of signal. */
    static String[] steps(String what) {
        if ("door".equals(what)) return new String[]{
                "Close all the doors. Then tap Next.",
                "Now open the driver's door and keep it open. Tap Next.",
                "Close the door again, then tap Next."};
        if ("belt".equals(what)) return new String[]{
                "Sit in the driver's seat with the seat belt OFF. Then tap Next.",
                "Now fasten the seat belt. Tap Next.",
                "Unfasten it again, then tap Next."};
        return new String[]{
                "Turn the car's headlights ON, then OFF again, and leave them off. Then tap Next.",
                "Now turn the headlights ON. Wait until the screen settles, then tap Next.",
                "Turn the headlights OFF again, wait a moment, then tap Next."};
    }

    private static void start(final Activity a, final Prefs prefs, final Done done) {
        final List<Map<String, String>> shots = new ArrayList<>();
        step(a, prefs, done, shots, steps(kind), 0);
    }

    private static void step(final Activity a, final Prefs prefs, final Done done,
                             final List<Map<String, String>> shots, final String[] steps, final int i) {
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.f("%1$s setup  %2$s/3", Hindi.t(noun()).substring(0, 1).toUpperCase(Locale.US) + Hindi.t(noun()).substring(1), i + 1))
                .setMessage(Hindi.t(steps[i]))
                .setCancelable(false)
                .setNegativeButton(Hindi.t("Cancel"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        stopListening(a);
                    }
                })
                .setPositiveButton(Hindi.t("Next"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final ProgressDialog wait = ProgressDialog.show(a, null, Hindi.t("Reading the unit…"), true);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    shots.add(snapshot(a));
                                } catch (Throwable e) {
                                    shots.add(new HashMap<String, String>());
                                }
                                a.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        wait.dismiss();
                                        if (i + 1 < steps.length) step(a, prefs, done, shots, steps, i + 1);
                                        else finish(a, prefs, done, diff(shots.get(0), shots.get(1), shots.get(2)));
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .show();
    }

    private static void finish(final Activity a, final Prefs prefs, final Done done, final List<Candidate> found) {
        stopListening(a);
        AlertDialog.Builder b = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert);
        if (found.isEmpty()) {
            b.setTitle(Hindi.f("No %1$s signal found", Hindi.t(noun())))
                    .setMessage(("lights".equals(kind) ? Hindi.t("Nothing this app can read changed with the headlights, so the unit may not pass the "
                            + "signal on to apps. Check that the unit's illumination wire is connected, or set the "
                            + "car's lights to always on or always off from this menu.")
                            : Hindi.t("Nothing this app can read changed. Most units only learn about doors and seat belts through a "
                            + "CAN bus decoder box; without one, the unit never hears about them."))
                            + "\n\n" + Hindi.f("What it checked: %1$s, %2$s.", lastCounts, stats))
                    .setPositiveButton(Hindi.t("OK"), null).show();
            return;
        }
        if (found.size() == 1) {
            save(a, prefs, found.get(0), done);
            return;
        }
        final List<Candidate> list = found.size() > 12 ? found.subList(0, 12) : found;
        String[] items = new String[list.size()];
        for (int i = 0; i < items.length; i++) {
            Candidate c = list.get(i);
            items[i] = (i == 0 ? "★ " : "") + label(c) + "   (" + shorten(c.off) + " → " + shorten(c.on) + ")";
        }
        b.setTitle(Hindi.f("These followed the %1$s; pick one (★ = best guess)", Hindi.t(noun())))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        save(a, prefs, list.get(which), done);
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static String shorten(String s) {
        return s.length() > 14 ? s.substring(0, 13) + "…" : s;
    }

    private static void save(Activity a, Prefs prefs, Candidate c, Done done) {
        if (!"lights".equals(kind)) {
            prefs.setSignal(kind, c.key, c.off, c.on);
            Toast.makeText(a, Hindi.f("The %1$s will follow %2$s", Hindi.t(noun()), c.key), Toast.LENGTH_LONG).show();
            done.done();
            return;
        }
        prefs.setLightSignal(c.key, c.off, c.on);
        prefs.setLights("auto");
        Toast.makeText(a, dims(c.key, c.off, c.on) ? Hindi.f("Headlights will follow the screen dimming (%1$s)", c.key)
                : Hindi.f("Headlights will follow %1$s", c.key), Toast.LENGTH_LONG).show();
        done.done();
    }

    // ------------------------------------------------------------------ turn indicators

    /** Whether a learnt indicator signal reads "on" right now (the blink's lit half, for most cars). */
    static boolean matches(Context c, String[] sig) {
        if (sig == null) return false;
        String v = read(c, sig[0]);
        if (v == null) return false;
        if (sig[2].isEmpty()) return !v.equals(sig[1]);
        for (String on : sig[2].split("\n")) if (on.equals(v)) return true;
        return false;
    }

    private static final String NONE = "\u0000none";

    /** Every value each key had while snapshots were taken for `ms` milliseconds. */
    static Map<String, Set<String>> sample(Context c, long ms) {
        Map<String, Set<String>> out = new HashMap<>();
        long end = System.currentTimeMillis() + ms;
        int n = 0;
        do {
            Map<String, String> snap = snapshot(c);
            for (Map.Entry<String, String> e : snap.entrySet()) {
                Set<String> v = out.get(e.getKey());
                if (v == null) {
                    v = new LinkedHashSet<>();
                    if (n > 0) v.add(NONE);   // the key wasn't there in earlier snapshots
                    out.put(e.getKey(), v);
                }
                v.add(e.getValue());
            }
            for (Map.Entry<String, Set<String>> e : out.entrySet()) {
                if (!snap.containsKey(e.getKey())) e.getValue().add(NONE);
            }
            n++;
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
            }
        } while (System.currentTimeMillis() < end);
        return out;
    }

    static final class Side {
        String key, off;
        Set<String> on = new LinkedHashSet<>();
        int score;
    }

    /** The key that changed most clearly while one side blinked, and not (or differently) for the other. */
    static Side bestSide(Map<String, Set<String>> idle, Map<String, Set<String>> mine, Map<String, Set<String>> other,
                         String word) {
        Side best = null;
        for (Map.Entry<String, Set<String>> e : mine.entrySet()) {
            String k = e.getKey();
            Set<String> base = idle.get(k);
            if (base == null) base = java.util.Collections.singleton(NONE);
            if (base.size() != 1) continue;   // changes by itself
            String lk = k.toLowerCase(Locale.US);
            boolean noise = false;
            for (String n : NOISE) if (lk.contains(n)) noise = true;
            if (noise || k.startsWith("uimode")) continue;
            Side s = new Side();
            s.key = k;
            s.off = base.iterator().next();
            for (String v : e.getValue()) if (!base.contains(v) && !NONE.equals(v)) s.on.add(v);
            if (s.on.isEmpty()) continue;
            Set<String> others = new LinkedHashSet<>();
            if (other.get(k) != null) for (String v : other.get(k)) if (!base.contains(v)) others.add(v);
            boolean shared = false;
            for (String v : s.on) if (others.contains(v)) shared = true;
            s.score = shared ? 1 : 5;
            if (e.getValue().size() > 1 && e.getValue().contains(s.off)) s.score += 1;   // it blinked
            if (lk.contains(word)) s.score += 3;
            for (String h : new String[]{"turn", "indicat", "blink", "signal", "winker", "lamp"}) if (lk.contains(h)) s.score += 2;
            if (lk.contains("brightness") || k.startsWith("file:")) s.score -= 3;
            if (best == null || s.score > best.score) best = s;
        }
        if (best != null && NONE.equals(best.off)) best.off = "";
        return best;
    }

    static void indicatorSetup(final Activity a, final Prefs prefs, final Done done) {
        final ProgressDialog wait = ProgressDialog.show(a, null, Hindi.t("Looking for the indicator signal…"), true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Set<String> actions = deep ? scanActions(a) : new LinkedHashSet<String>();
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wait.dismiss();
                        listenWide(a, actions);
                        indicatorStep(a, prefs, done, new ArrayList<Map<String, Set<String>>>(), 0);
                    }
                });
            }
        }).start();
    }

    private static void indicatorStep(final Activity a, final Prefs prefs, final Done done,
                                      final List<Map<String, Set<String>>> runs, final int i) {
        final String[] steps = {
                "Switch both indicators OFF (and the hazard lights). Tap Next and wait 4 seconds.",
                "Switch the LEFT indicator on. Tap Next and leave it blinking for 4 seconds.",
                "Switch the left one off and the RIGHT indicator on. Tap Next and leave it blinking for 4 seconds."};
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.f("Indicator setup  %1$s/3", i + 1))
                .setMessage(Hindi.t(steps[i]))
                .setCancelable(false)
                .setNegativeButton(Hindi.t("Cancel"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        stopListening(a);
                    }
                })
                .setPositiveButton(Hindi.t("Next"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final ProgressDialog wait = ProgressDialog.show(a, null, Hindi.t("Watching for 4 seconds…"), true);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    runs.add(sample(a, 4000));
                                } catch (Throwable e) {
                                    runs.add(new HashMap<String, Set<String>>());
                                }
                                a.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        wait.dismiss();
                                        if (i < 2) indicatorStep(a, prefs, done, runs, i + 1);
                                        else indicatorDone(a, prefs, done, runs);
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .show();
    }

    private static void indicatorDone(final Activity a, final Prefs prefs, final Done done,
                                      List<Map<String, Set<String>>> runs) {
        stopListening(a);
        final Side l = bestSide(runs.get(0), runs.get(1), runs.get(2), "left");
        final Side r = bestSide(runs.get(0), runs.get(2), runs.get(1), "right");
        AlertDialog.Builder b = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert);
        if (l == null && r == null) {
            b.setTitle(Hindi.t("No indicator signal found"))
                    .setMessage(Hindi.t("Nothing this app can read changed with the indicators. Many units only get the "
                            + "indicators through a CAN bus decoder box; without one, the unit never learns about them.")
                            + "\n\n" + Hindi.f("What it checked: %1$s, %2$s.", lastCounts, stats))
                    .setPositiveButton(Hindi.t("OK"), null).show();
            return;
        }
        StringBuilder m = new StringBuilder();
        m.append(Hindi.t("Left:")).append(' ').append(l == null ? Hindi.t("not found") : l.key + "  (" + shorten(l.off) + " → " + shorten(join(l.on)) + ")");
        m.append("\n\n").append(Hindi.t("Right:")).append(' ').append(r == null ? Hindi.t("not found") : r.key + "  (" + shorten(r.off) + " → " + shorten(join(r.on)) + ")");
        if (l != null && r != null && l.key.equals(r.key) && l.on.equals(r.on)) {
            m.append("\n\n").append(Hindi.t("Both sides look the same, so the car will show both arrows for either indicator."));
        }
        b.setTitle(Hindi.t("Indicators found"))
                .setMessage(m.toString())
                .setNegativeButton(Hindi.t("Cancel"), null)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        prefs.setSignal("left", l == null ? null : l.key, l == null ? "" : l.off, l == null ? "" : join(l.on));
                        prefs.setSignal("right", r == null ? null : r.key, r == null ? "" : r.off, r == null ? "" : join(r.on));
                        done.done();
                    }
                }).show();
    }

    private static String join(Set<String> s) {
        StringBuilder b = new StringBuilder();
        for (String v : s) {
            if (b.length() > 0) b.append("\n");
            b.append(v);
        }
        return b.toString();
    }

    private Lights() {
    }
}
