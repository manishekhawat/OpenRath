package org.openrath.launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Screenshots of the main screens for the README and for checking a change by eye: the opening
 * logo, each setup step, the dashboard in several layouts, and Settings. Run on a fresh install with
 *   adb shell am instrument -w org.openrath.launcher.test/org.openrath.launcher.Shots
 * and pull Android/data/org.openrath.launcher/files/shots.
 */
public final class Shots extends Instrumentation {

    private final StringBuilder out = new StringBuilder();
    private File dir;
    private int taken;

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
            dir = c.getExternalFilesDir("shots");
            run(c);
        } catch (Throwable e) {
            out.append("error: ").append(e).append('\n');
        }
        out.append(taken).append(" screenshots\n");
        Bundle b = new Bundle();
        b.putString(Instrumentation.REPORT_KEY_STREAMRESULT, out.toString());
        finish(Activity.RESULT_OK, b);
    }

    private void run(Context c) {
        final Prefs p = new Prefs(c);
        p.put("setupDone", "");
        p.put("setupStep", "");
        p.put("lang", "en");
        Intent i = new Intent(Intent.ACTION_MAIN).setClassName(c, HomeActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        final HomeActivity h = (HomeActivity) startActivitySync(i);
        // full screen, as most stereos run: no status bar, navigation bar or taskbar in the pictures
        runOnMainSync(new Runnable() {
            @Override
            @SuppressWarnings("deprecation")
            public void run() {
                h.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
                h.getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        });
        SystemClock.sleep(3500);   // the opening logo (a moving picture; see docs/opening-logo.gif)
        if (Setup.showing == null) {
            out.append("no setup screen\n");
        } else {
            shot("02_setup_welcome");
            String[] names = {"", "03_setup_permissions", "04_setup_car", "05_setup_look", "06_setup_level", "07_setup_apps", "08_setup_phone_home"};
            for (int s = Setup.ACCESS; s <= Setup.FINISH; s++) {
                final int to = s;
                main(new Runnable() {
                    @Override
                    public void run() {
                        Setup.showing.step(to);
                    }
                });
                shot(names[s]);
            }
            main(new Runnable() {
                @Override
                public void run() {
                    p.put("lang", "hi");
                    Hindi.load(h, p);
                    Setup.showing.step(Setup.WELCOME);
                }
            });
            shot("09_setup_hindi");
            main(new Runnable() {
                @Override
                public void run() {
                    p.put("lang", "en");
                    Hindi.load(h, p);
                    Setup.showing.close();
                }
            });
            SystemClock.sleep(4000);   // the "All set" note goes
        }
        String[][] layouts = {{Layouts.BOXES, "10_dashboard_boxes"}, {HomeActivity.WIDE, "11_dashboard_wide_map"},
                {"scenery", "12_dashboard_scenery"}, {"columns", "13_dashboard_columns"}, {"strip", "14_dashboard_strip"},
                {"twin_dials", "15_dashboard_twin_dials"}};
        for (final String[] l : layouts) {
            main(new Runnable() {
                @Override
                public void run() {
                    p.put("layout", l[0]);
                    h.build();
                }
            });
            SystemClock.sleep(2500);
            shot(l[1]);
        }
        main(new Runnable() {
            @Override
            public void run() {
                p.put("layout", Layouts.BOXES);
                h.build();
                h.openSettings();
            }
        });
        SystemClock.sleep(1500);
        shot("16_settings");
        main(new Runnable() {
            @Override
            public void run() {
                h.closeSettings();
            }
        });
        // the three looks: each theme on the boxes, then its picture layout
        String[][] looks = {{"mission", "17_mission"}, {"electric", "18_electric"}, {"roadster", "19_roadster"}};
        for (final String[] l : looks) {
            for (final String layout : new String[]{Layouts.BOXES, l[0]}) {
                main(new Runnable() {
                    @Override
                    public void run() {
                        p.put("themePack", "@" + l[0]);
                        p.put("layout", layout);
                        h.build();
                    }
                });
                SystemClock.sleep(2500);
                shot(l[1] + (layout.equals(Layouts.BOXES) ? "_theme" : "_layout"));
            }
        }
    }

    private void main(Runnable r) {
        runOnMainSync(r);
        waitForIdleSync();
        SystemClock.sleep(700);
    }

    private void shot(String name) {
        try {
            Bitmap b = getUiAutomation().takeScreenshot();
            if (b == null) {
                out.append("no picture for ").append(name).append('\n');
                return;
            }
            FileOutputStream f = new FileOutputStream(new File(dir, name + ".png"));
            b.compress(Bitmap.CompressFormat.PNG, 100, f);
            f.close();
            taken++;
            out.append("shot ").append(name).append('\n');
        } catch (Exception e) {
            out.append("couldn't save ").append(name).append(": ").append(e).append('\n');
        }
    }
}
