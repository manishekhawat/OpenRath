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

    /** Tries at carrying on: 2 seconds apart, so up to 10 seconds for a slow music app or radio. */
    static final int TRIES = 5;

    /** Navigation is opened again when it was guiding up to this long before the switch-off. */
    static final long NAV_MS = 30 * 60 * 1000;

    /** Whether to open the navigation app again: it guided within NAV_MS. Pure, for the tests. */
    static boolean navDue(String pkg, long guidedAt, long now) {
        return pkg != null && !pkg.isEmpty() && guidedAt > 0 && now - guidedAt >= 0 && now - guidedAt < NAV_MS;
    }

    /**
     * Play the music app that was playing: through its media session when it has one up, else with
     * a play button press sent to that app alone. False while it has no session yet (try again),
     * unless this is the last try.
     */
    static boolean playApp(Context c, String pkg, boolean last) {
        try {
            android.media.session.MediaSessionManager m = (android.media.session.MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (android.media.session.MediaController mc : m.getActiveSessions(new android.content.ComponentName(c, MediaListener.class))) {
                if (pkg.equals(mc.getPackageName())) {
                    mc.getTransportControls().play();
                    return true;
                }
            }
        } catch (Throwable e) {
            // no notification access: the button press below
        }
        if (!last) return false;
        long t = SystemClock.uptimeMillis();
        for (int action : new int[]{android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP}) {
            Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                    .putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(t, t, action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY, 0));
            c.sendBroadcast(i);
        }
        return true;
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
