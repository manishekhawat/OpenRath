package org.openrath.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;

/**
 * Carries on after the unit switches on: the same source (FM station, online station, music file
 * or music app) at the same volume. What was playing is remembered whenever a box starts or stops
 * something; the dashboard calls due() at start-up and after the screen wakes from sleep.
 */
final class Resume {

    /** Called by the boxes. what: "fm", "web", "files", "app:<package>" or "" for stopped. */
    static void remember(Prefs p, String what) {
        p.put("resume", what);
    }

    /** Volume, saved every few seconds by the trip service. */
    static void saveVolume(Context c, Prefs p) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            p.put("resumeVol", String.valueOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)));
        } catch (Throwable ignored) {
            // every few seconds: too often to note; the next one tries again
        }
    }

    /** True once per switch-on: a fresh boot, or the screen back after more than a minute asleep. */
    static boolean due(Context c, Prefs p) {
        if (!Features.on(p, "resume")) return false;
        long bootAt = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60000;   // minutes, stable for this boot
        String key = String.valueOf(bootAt);
        if (!key.equals(p.str("resumeBoot", ""))) {
            p.put("resumeBoot", key);
            return true;
        }
        long sleptAt = Long.parseLong(p.str("sleptAt", "0"));
        if (sleptAt > 0 && System.currentTimeMillis() - sleptAt > 60000) {
            p.put("sleptAt", "0");
            return true;
        }
        p.put("sleptAt", "0");
        return false;
    }

    static void restoreVolume(Context c, Prefs p) {
        int v = p.num("resumeVol", -1);
        if (v < 0) return;
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(v, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
        } catch (Throwable e) {
            Diag.note("resume volume", e);
        }
    }

    /** Screen off and on: the unit's sleep with the ignition, on Topway units. */
    static final class Screen extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            Prefs p = new Prefs(c);
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) p.put("sleptAt", String.valueOf(System.currentTimeMillis()));
        }
    }

    /** The boot broadcasts the manifest lists for Boot; anything else sent to it is ignored. */
    static boolean bootAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action);
    }

    /** Starts the trip service when the unit boots, before anyone opens the dashboard. */
    public static final class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i == null || !bootAction(i.getAction())) return;
            Drive.start(c);
        }
    }

    private Resume() {
    }
}
