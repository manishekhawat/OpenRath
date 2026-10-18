package org.openrath.launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * On-device tests: the parts the plain-Java tests (tests/) can't reach because they need a real
 * Android, such as settings storage, resources, managed settings and the home screen starting up.
 * No test library: run with
 *   adb shell am instrument -w org.openrath.launcher.test/org.openrath.launcher.Smoke
 * and the last line says "N checks, M failed". Settings it changes are put back afterwards. With
 * -e fleet set or -e fleet clear it checks only the managed settings that the fleet test app
 * (androidTest/fleet) has just set or cleared.
 */
public final class Smoke extends Instrumentation {

    private final StringBuilder out = new StringBuilder();
    private int run, failed;
    private String fleet;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        fleet = arguments == null ? null : arguments.getString("fleet");
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        Context c = getTargetContext();
        try {
            if (fleet != null) {
                fleet(c);
            } else {
                features(c);
                managed(c);
                hindi(c);
                reports(c);
                compat(c);
                home(c);
                backup(c);
                resources(c);
            }
        } catch (Throwable e) {
            check("no exception: " + e, false);
        }
        String summary = run + " checks, " + failed + " failed";
        out.append(summary).append('\n');
        Bundle b = new Bundle();
        b.putString(Instrumentation.REPORT_KEY_STREAMRESULT, out.toString());
        b.putInt("failed", failed);
        finish(failed == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, b);
    }

    private void check(String name, boolean ok) {
        run++;
        if (!ok) failed++;
        out.append(ok ? "ok    " : "FAIL  ").append(name).append('\n');
    }

    private void features(Context c) {
        Prefs p = new Prefs(c);
        boolean before = Features.on(p, "kids");
        Features.set(p, "kids", false);
        check("features: switch off is kept", !Features.on(new Prefs(c), "kids"));
        Features.set(p, "kids", true);
        check("features: switch on is kept", Features.on(new Prefs(c), "kids"));
        Features.set(p, "kids", before);
        boolean parking = Features.on(p, "parking");
        Features.set(p, "parkPhoto", false);   // an old id: switches the Parking switch it became part of
        check("features: old id follows its new switch", !Features.on(p, "parking") && !Features.on(p, "parkLink"));
        Features.set(p, "parking", parking);
        String mic = p.str("micPlace", "");
        boolean voice = Features.on(p, "voice");
        Features.set(p, "voice", true);
        p.put("micPlace", "map");
        check("features: mic on the map only", !Features.on(p, "floatMic") && !Features.on(p, "micOverApps"));
        p.put("micPlace", "apps");
        check("features: mic over other apps", Features.on(p, "floatMic") && Features.on(p, "micOverApps"));
        p.put("micPlace", mic);
        Features.set(p, "voice", voice);
    }

    private void managed(Context c) {
        Prefs p = new Prefs(c);
        boolean news = Features.on(p, "news");
        Bundle b = new Bundle();
        b.putString("featuresOff", "news");
        b.putBoolean("devMode", false);
        b.putString("reportUrl", "http://not-https.example");
        Managed.apply(p, b);
        check("managed: locked off", !Features.on(p, "news"));
        Features.set(p, "news", true);
        check("managed: owner can't change a locked one", !Features.on(p, "news"));
        check("managed: developer mode hidden", Managed.devBlocked(p) && !Features.dev(p));
        check("managed: plain http address refused", p.str("reportUrl", "").isEmpty());
        Managed.apply(p, new Bundle());
        check("managed: released", Managed.lockedTo(p, "news") == null && !Managed.devBlocked(p));
        Features.set(p, "news", news);
        Managed.apply(c, p);   // back to whatever the device really has
    }

    private void hindi(Context c) {
        Prefs p = new Prefs(c);
        String before = p.str("lang", "");
        p.put("lang", "hi");
        Hindi.load(c, p);
        check("hindi: from the resources", !"Search settings".equals(Hindi.t("Search settings")));
        p.put("lang", "en");
        Hindi.load(c, p);
        check("hindi: English when off", "Search settings".equals(Hindi.t("Search settings")));
        if (before.isEmpty()) before = Hindi.lang(p);
        p.put("lang", before);
        Hindi.load(c, p);
    }

    private void reports(Context c) {
        Prefs p = new Prefs(c);
        boolean was = Features.on(p, "reports");
        Reports.install(c, p);
        Features.set(p, "reports", false);
        Reports.clear();
        Reports.count("smoke");
        check("reports: nothing counted while off", !Reports.counts().containsKey("smoke"));
        Features.set(p, "reports", true);
        Reports.count("smoke");
        check("reports: counted when on", Reports.counts().containsKey("smoke"));
        check("reports: text has the counts", Reports.text("test").contains("smoke: 1"));
        Reports.clear();
        Features.set(p, "reports", was);
    }

    private void compat(Context c) {
        String[] all = Compat.startPermissions(android.os.Build.VERSION.SDK_INT);
        check("compat: asks for location", java.util.Arrays.asList(all).contains("android.permission.ACCESS_FINE_LOCATION"));
        check("compat: missing is a subset", Compat.missing(c).length <= all.length);
    }

    /** What a real device-management app set (androidTest/fleet), read the way OpenRath reads it. */
    private void fleet(Context c) {
        Prefs p = new Prefs(c);
        Managed.apply(c, p);
        if ("set".equals(fleet)) {
            check("fleet: level set to Full", Features.level(p) == Features.FULL);
            check("fleet: level locked", Managed.levelLocked(p));
            check("fleet: news locked off", "off".equals(Managed.lockedTo(p, "news")) && !Features.on(p, "news"));
            check("fleet: kids locked off", "off".equals(Managed.lockedTo(p, "kids")));
            check("fleet: reports locked on", "on".equals(Managed.lockedTo(p, "reports")) && Features.on(p, "reports"));
            Features.set(p, "news", true);
            check("fleet: driver can't turn news back on", !Features.on(p, "news"));
            check("fleet: developer mode hidden", Managed.devBlocked(p) && !Features.dev(p));
            check("fleet: report address kept", "https://fleet.example/openrath".equals(p.str("reportUrl", "")));
        } else {
            check("fleet: locks released", Managed.lockedTo(p, "news") == null && Managed.lockedTo(p, "reports") == null);
            check("fleet: level unlocked", !Managed.levelLocked(p));
            check("fleet: developer mode allowed", !Managed.devBlocked(p));
            check("fleet: report address cleared", p.str("reportUrl", "").isEmpty());
        }
    }

    /** A backup is written and read back, with a setting in it. */
    private void backup(Context c) {
        Prefs p = new Prefs(c);
        String before = p.str("smokeMark", "");
        p.put("smokeMark", "before");
        try {
            java.io.File f = Backup.make(c, "test");
            check("backup: written", f.isFile() && f.length() > 0);
            p.put("smokeMark", "after");
            Backup.restore(c, f);
            check("backup: setting restored", "before".equals(new Prefs(c).str("smokeMark", "")));
            f.delete();
        } catch (Throwable e) {
            check("backup: " + e, false);
        }
        p.put("smokeMark", before);
    }

    /** Every translation list that has a file is in the APK, and the managed settings are declared. */
    private void resources(Context c) {
        for (String name : Hindi.ARRAYS) {
            int id = c.getResources().getIdentifier(name, "array", c.getPackageName());
            if (id == 0) continue;   // a list no file has yet
            check("resources: " + name + " has text", c.getResources().getStringArray(id).length > 0);
        }
        check("resources: Settings list present", c.getResources().getIdentifier("ui_strings", "array", c.getPackageName()) != 0);
        check("resources: managed settings declared", c.getResources().getIdentifier("app_restrictions", "xml", c.getPackageName()) != 0);
    }

    /** Runs r on the main thread; true if it threw nothing. */
    private boolean onMain(final Runnable r) {
        final Throwable[] err = {null};
        runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable e) {
                    err[0] = e;
                }
            }
        });
        waitForIdleSync();
        if (err[0] != null) out.append("      ").append(err[0]).append('\n');
        return err[0] == null;
    }

    private void home(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN).setClassName(c, HomeActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity a = startActivitySync(i);
        waitForIdleSync();
        check("home: the dashboard starts", a instanceof HomeActivity && !a.isFinishing());
        check("home: it has a screen", a.getWindow() != null && a.getWindow().getDecorView().getWidth() > 0);
        final HomeActivity h = (HomeActivity) a;
        final Prefs p = h.prefs;
        // every layout builds
        String layout = Layouts.current(p);
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add(Layouts.current(p));
        for (Layouts.Layout l : Layouts.all(c)) ids.add(l.id);
        for (final String id : ids) {
            check("home: layout " + id + " builds", onMain(new Runnable() {
                @Override
                public void run() {
                    p.put("layout", id);
                    h.build();
                }
            }));
        }
        p.put("layout", layout);
        // Settings opens and closes, in English and in Hindi
        String lang = p.str("lang", "");
        for (final String l : new String[]{"en", "hi"}) {
            check("home: settings open (" + l + ")", onMain(new Runnable() {
                @Override
                public void run() {
                    p.put("lang", l);
                    Hindi.load(h, p);
                    h.build();
                    h.openSettings();
                    if (h.settingsView == null) throw new IllegalStateException("no settings view");
                    h.closeSettings();
                }
            }));
        }
        check("home: Hindi words loaded", Hindi.on && !"Search settings".equals(Hindi.t("Search settings")));
        p.put("lang", lang.isEmpty() ? "en" : lang);
        Hindi.load(h, p);
        onMain(new Runnable() {
            @Override
            public void run() {
                h.build();
            }
        });
        a.finish();
        waitForIdleSync();
    }
}
