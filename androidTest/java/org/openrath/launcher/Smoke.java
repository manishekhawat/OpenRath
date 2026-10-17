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
 * and the last line says "N checks, M failed". Settings it changes are put back afterwards.
 */
public final class Smoke extends Instrumentation {

    private final StringBuilder out = new StringBuilder();
    private int run, failed;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        Context c = getTargetContext();
        try {
            features(c);
            managed(c);
            hindi(c);
            reports(c);
            compat(c);
            home(c);
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

    private void home(Context c) {
        Intent i = new Intent(Intent.ACTION_MAIN).setClassName(c, HomeActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity a = startActivitySync(i);
        waitForIdleSync();
        check("home: the dashboard starts", a instanceof HomeActivity && !a.isFinishing());
        check("home: it has a screen", a.getWindow() != null && a.getWindow().getDecorView().getWidth() > 0);
        a.finish();
        waitForIdleSync();
    }
}
