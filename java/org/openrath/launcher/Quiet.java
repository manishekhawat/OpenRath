package org.openrath.launcher;

import android.content.Context;
import android.media.AudioManager;

import java.util.Calendar;

/**
 * Quiet hours: late at night (10 pm to 6 am unless changed) the music is turned down once when
 * the quiet time starts, or when the unit switches on in it, and alert beeps are left out.
 * Spoken safety warnings still play.
 */
final class Quiet {

    private static boolean wasQuiet;

    static int from(Prefs p) {
        return p.num("quietFrom", 22);
    }

    static int to(Prefs p) {
        return p.num("quietTo", 6);
    }

    /** Whether hour `h` (0–23) is inside from..to, which may run past midnight. */
    static boolean inside(int h, int from, int to) {
        if (from == to) return false;
        return from < to ? h >= from && h < to : h >= from || h < to;
    }

    static boolean now(Prefs p) {
        return Features.on(p, "quiet") && inside(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), from(p), to(p));
    }

    /** Turn the music down once as quiet hours begin. */
    static void apply(Context c, Prefs p) {
        boolean q = now(p);
        if (q && !wasQuiet) {
            try {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int cap = Math.max(1, Math.round(max * p.num("quietVol", 40) / 100f));
                if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0);
            } catch (Throwable e) {
                Diag.note("quiet hours volume", e);
            }
        }
        wasQuiet = q;
    }

    static String hour(int h) {
        if (h == 0) return Hindi.t("midnight");
        if (h == 12) return Hindi.t("noon");
        // Hindi says the part of the day ("रात 2 बजे"), which am/pm alone doesn't tell
        if (Hindi.on) return Hindi.f(h < 4 || h >= 20 ? "%1$s at night" : h < 12 ? "%1$s in the morning" : h < 16 ? "%1$s in the afternoon" : "%1$s in the evening", h % 12);
        return String.format(java.util.Locale.US, h < 12 ? "%1$s am" : "%1$s pm", h % 12);
    }

    private Quiet() {
    }
}
