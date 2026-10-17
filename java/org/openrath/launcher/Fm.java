package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Starting and stopping FM from the radio box on Topway units. The unit's radio service
 * (com.tw.service.xt) has no plain "play radio" command, only things like radioOpenChannel,
 * radioSetChannel and OpenProject, so which one switches the sound to FM is found once with the
 * FM radio test in Settings and remembered. "mcu" means Topway's MCU link instead.
 */
final class Fm {

    static List<String> playCandidates(Prefs p) {
        String f = p.fmFreq();
        int fi;
        try {
            fi = Math.round(Float.parseFloat(f) * 100);
        } catch (Exception e) {
            fi = 9830;
        }
        List<String> l = new ArrayList<>();
        // the stock radio app is what plays FM; the service's OpenProject starts it, and the
        // dashboard comes straight back to the front while it keeps playing (as the stock launcher does)
        l.add("OpenProject:s:com.tw.radio");
        l.add("OpenProject:s:radio");
        l.add("OpenProject:s:Radio");
        l.add("launch:" + RADIO);
        for (String svc : radioServices()) l.add("svc:" + svc);
        l.add("radioOpenChannel:s:" + f);
        l.add("radioOpenChannel:s:" + fi);
        l.add("radioSetChannel:i:" + fi);
        l.add("radioSetChannel:i:" + fi * 10);
        l.add("radioSetChannel:i:1");
        l.add("OpenProject:s:fm");
        l.add("radioNext");
        l.add("radioBand:i:0");
        l.add("mediaPlay");
        l.add("mcu");
        return l;
    }

    static List<String> stopCandidates() {
        List<String> l = new ArrayList<>();
        l.add("killProject:s:com.tw.radio");
        l.add("mediaPause");
        for (String svc : radioServices()) l.add("svc:" + svc);
        l.add("killProject:s:radio");
        l.add("setVolumeMute:b:true");
        l.add("mcu");
        return l;
    }

    static final String RADIO = "com.tw.radio";
    /** Set by the dashboard; used to start the radio app's service and to come back to the front. */
    static Context app;

    /** The stock radio app's services, e.g. a background FM service, by class name. */
    static List<String> radioServices() {
        List<String> out = new ArrayList<>();
        try {
            PackageInfo pi = app.getPackageManager().getPackageInfo(RADIO, PackageManager.GET_SERVICES);
            if (pi.services != null) for (ServiceInfo si : pi.services) out.add(si.name);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Commands that bring the radio app's screen up, so the dashboard has to come back after. */
    static boolean opensApp(String cmd) {
        return cmd.startsWith("OpenProject:") || cmd.startsWith("launch:");
    }

    /**
     * Bring the dashboard back in front of the radio app, which keeps playing behind it. Tried a
     * few times, as the radio app can take a moment to open.
     */
    static void comeBack() {
        if (app == null) return;
        final Handler h = new Handler(android.os.Looper.getMainLooper());
        for (long ms : new long[]{300, 700, 1500, 2500}) {
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (HomeActivity.visible) return;
                    try {
                        app.startActivity(new Intent(app, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    } catch (Throwable ignored) {
                    }
                }
            }, ms);
        }
    }

    static String label(String cmd) {
        if ("mcu".equals(cmd)) return Hindi.t("Topway MCU link (no radio service)");
        if (cmd.startsWith("launch:")) return Hindi.t("Open the radio app, then back to the dashboard");
        if (cmd.startsWith("svc:")) return Hindi.f("Radio app's service %1$s", cmd.substring(cmd.lastIndexOf('.') + 1));
        if (opensApp(cmd)) return Hindi.f("%1$s, then back to the dashboard", label0(cmd));
        return label0(cmd);
    }

    private static String label0(String cmd) {
        String[] c = cmd.split(":", 3);
        if (c.length < 3) return c[0] + "()";
        return c[0] + "(" + ("s".equals(c[1]) ? "\"" + c[2] + "\"" : c[2]) + ")";
    }

    /** Run one command; null when it ran. */
    static String run(String cmd, Xt xt, Tw.Radio fm, boolean on) {
        if ("mcu".equals(cmd)) {
            if (fm == null) return "no MCU link on this unit";
            fm.play(on);
            return null;
        }
        boolean front = HomeActivity.visible;
        String err;
        if (cmd.startsWith("launch:")) {
            Intent i = app == null ? null : app.getPackageManager().getLaunchIntentForPackage(cmd.substring(7));
            if (i == null) return "the radio app isn't installed";
            try {
                app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                err = null;
            } catch (Throwable e) {
                err = e.getClass().getSimpleName();
            }
        } else if (cmd.startsWith("svc:")) {
            if (app == null) return "not ready";
            try {
                Intent i = new Intent().setClassName(RADIO, cmd.substring(4));
                if (!on) return app.stopService(i) ? null : "service wasn't running";
                return app.startService(i) == null ? "the radio app refused" : null;
            } catch (Throwable e) {
                return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            }
        } else {
            err = xt == null ? "radio service not connected" : xt.run(cmd);
        }
        // a command that opened the radio app's screen: the radio keeps playing, the dashboard returns
        if (err == null && on && front && opensApp(cmd)) comeBack();
        return err;
    }

    /** Start or stop FM with the command found by the test (or a best guess before that). */
    /** OpenRath started the stock radio app since it came up, and when the last screen-free play was. */
    static boolean stockStarted;
    private static long quietPlayAt;

    static String play(Prefs p, Xt xt, Tw.Radio fm, boolean on) {
        // OpenRath's own tuner, when the test in Settings found it plays on this unit
        if (Tuner.chosen(p) && app != null) return Tuner.play(app, p, on);
        // the stock radio app is already running (it answered lately): just move the sound to it, or
        // away from it, the way its own screen does. No screen opens, so no flash, and it stays tuned.
        // Also once OpenRath has started the radio app since it came up: the app keeps running behind
        // the dashboard (stop no longer closes it), so later plays only move the sound. Pressing play
        // again within 15 s of such a play (it was silent: the radio app had gone) opens it again.
        long now = System.currentTimeMillis();
        boolean retry = on && now - quietPlayAt < 15000;
        if (xt != null && fm != null && (xt.radioAlive() || stockStarted) && !retry) {
            fm.play(on);
            xt.askRadio();
            if (on) quietPlayAt = now;
            return null;
        }
        if (xt != null) xt.askRadio();   // so the next press knows
        String cmd = p.fmCmd(on ? "play" : "stop");
        if (!cmd.isEmpty()) {
            // a learnt "mute" stop needs undoing before the next start
            if (on && p.fmCmd("stop").startsWith("setVolumeMute") && xt != null) xt.run("setVolumeMute:b:false");
            String err = run(cmd, xt, fm, on);
            if (on && err == null && opensApp(cmd)) stockStarted = true;
            quietPlayAt = 0;
            return err;
        }
        // not tested yet: start the stock radio app behind the dashboard, which is what plays FM.
        // Stopping only takes the sound away, so the next play needs no screen.
        String err = on ? (xt != null && xt.has("OpenProject") ? run("OpenProject:s:" + RADIO, xt, fm, true)
                : run("radioOpenChannel:s:" + p.fmFreq(), xt, fm, true))
                : fm != null ? null
                : xt != null && xt.has("killProject") ? run("killProject:s:" + RADIO, xt, fm, false) : run("mediaPause", xt, fm, false);
        if (fm != null) fm.play(on);
        if (on && err == null) stockStarted = true;
        quietPlayAt = 0;
        return err;
    }

    /** Settings > FM radio test: try each command until one starts (or stops) the radio. */
    static void test(final Activity a, final Prefs p, final Xt xt, final Tw.Radio fm, final boolean stop) {
        final List<String> all = stop ? stopCandidates() : playCandidates(p);
        String[] names = new String[all.size()];
        String cur = p.fmCmd(stop ? "stop" : "play");
        for (int i = 0; i < names.length; i++) names[i] = (all.get(i).equals(cur) ? "✓  " : "") + label(all.get(i));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(stop ? Hindi.t("Start FM first, then try each until FM stops") : Hindi.t("Try each until FM starts playing"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        final String cmd = all.get(which);
                        String err = run(cmd, xt, fm, !stop);
                        if (err != null) {
                            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                    .setMessage(Hindi.f("%1$s didn't run: %2$s", label(cmd), Hindi.t(err)))
                                    .setPositiveButton(Hindi.t("Try another"), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d2, int w) {
                                            test(a, p, xt, fm, stop);
                                        }
                                    }).setNegativeButton(Hindi.t("Close"), null).show();
                            return;
                        }
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (a.isFinishing()) return;
                                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                        .setTitle(label(cmd))
                                        .setMessage(stop ? Hindi.t("Did the FM radio stop?") : Hindi.t("Is the FM radio playing now, with the dashboard still in front?"))
                                        .setPositiveButton(Hindi.t("Yes, use this"), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d2, int w) {
                                                p.setFmCmd(stop ? "stop" : "play", cmd);
                                                if (!stop) test(a, p, xt, fm, true);
                                            }
                                        })
                                        .setNegativeButton(Hindi.t("No, try another"), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d2, int w) {
                                                test(a, p, xt, fm, stop);
                                            }
                                        }).show();
                            }
                        }, 2500);
                    }
                })
                .setNegativeButton(Hindi.t("Close"), null).show();
    }

    static String freqOf(float mhz) {
        return String.format(Locale.US, "%.1f", mhz);
    }

    private Fm() {
    }
}
