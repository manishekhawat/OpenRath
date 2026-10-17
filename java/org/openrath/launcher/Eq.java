package org.openrath.launcher;

import android.content.Context;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;

import java.util.Locale;

/**
 * A simple equalizer: presets plus bass and treble, applied to the app's own player and, where
 * the unit allows it, to all sound (audio session 0). Many head units have their own DSP
 * equalizer in the stock EQ app; this one adds to it rather than replacing it.
 */
final class Eq {

    static final String[] PRESETS = {"Flat", "Bass", "Vocal", "Rock", "Pop", "Classical", "Custom"};
    // gain in dB for five bands (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz)
    private static final int[][] GAINS = {{0, 0, 0, 0, 0}, {6, 4, 0, 0, 1}, {-2, 0, 3, 4, 2}, {5, 3, -1, 3, 5},
            {-1, 2, 4, 2, -1}, {4, 2, -1, 2, 4}};

    private static Equalizer global, own;
    private static BassBoost bass;
    private static int ownSession;
    static String status = "";

    /** The five band gains in dB for the chosen preset or the custom settings. */
    static int[] gains(Prefs p) {
        String preset = p.str("eqPreset", "Flat");
        String now = Drive.soundNow;
        if (!now.isEmpty() && Features.on(p, "soundProfiles") && !Sound.eq(p, now).isEmpty()) preset = Sound.eq(p, now);
        int i = java.util.Arrays.asList(PRESETS).indexOf(preset);
        if (i >= 0 && i < GAINS.length) return GAINS[i].clone();
        int[] g = new int[5];
        String[] f = p.str("eqCustom", "0,0,0,0,0").split(",");
        for (int k = 0; k < 5 && k < f.length; k++) {
            try {
                g[k] = Math.max(-12, Math.min(12, Integer.parseInt(f[k].trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        return g;
    }

    /** Apply to audio session `session` (0 = everything, where allowed). Safe to call often. */
    static synchronized void apply(Context c, Prefs p, int session) {
        boolean on = Features.on(p, "eq") || Features.on(p, "soundProfiles") && !Drive.soundNow.isEmpty();
        try {
            Equalizer e = session == 0 ? global : own;
            if (!on) {
                if (e != null) e.setEnabled(false);
                if (bass != null && session == 0) bass.setEnabled(false);
                status = "Off";
                return;
            }
            if (session != 0 && e != null && ownSession != session) {
                e.release();   // a new player has a new audio session
                e = own = null;
            }
            if (e == null) {
                e = new Equalizer(0, session);
                if (session != 0) ownSession = session;
                if (session == 0) global = e;
                else own = e;
            }
            int[] g = gains(p);
            short bands = e.getNumberOfBands();
            short[] range = e.getBandLevelRange();
            for (short b = 0; b < bands; b++) {
                int hz = e.getCenterFreq(b) / 1000;
                int idx = hz < 150 ? 0 : hz < 500 ? 1 : hz < 2000 ? 2 : hz < 7000 ? 3 : 4;
                short mb = (short) Math.max(range[0], Math.min(range[1], g[idx] * 100));
                e.setBandLevel(b, mb);
            }
            e.setEnabled(true);
            if (session == 0) {
                if (bass == null) bass = new BassBoost(0, 0);
                if (bass.getStrengthSupported()) bass.setStrength((short) Math.max(0, Math.min(1000, p.num("eqBass", 0) * 100)));
                bass.setEnabled(p.num("eqBass", 0) > 0);
            }
            status = String.format(Locale.US, "On (%d bands)", bands);
        } catch (Throwable t) {
            status = session == 0 ? "This unit doesn't allow an equalizer on all sound; it works on the app's own player"
                    : "Not available: " + t.getMessage();
        }
    }

    static synchronized void release() {
        if (own != null) {
            own.release();
            own = null;
        }
    }

    private Eq() {
    }
}
