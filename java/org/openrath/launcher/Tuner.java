package org.openrath.launcher;

import android.content.Context;
import android.media.AudioManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OpenRath's own FM radio: drives the unit's FM chip directly through its driver library (the same
 * calls, in the same order, as the stock radio app), so FM plays without com.tw.radio and without
 * its screen. Works only where the firmware lets an ordinary app load the library and open the chip;
 * Settings > Radio and music > Built-in FM tuner tries it and remembers the answer.
 */
final class Tuner {

    /** Each step of the last try, for the test's report. */
    static final List<String> steps = new ArrayList<>();
    static volatile boolean on;
    static volatile float mhz;
    static volatile String name = "";
    private static boolean open;
    // the stock radio's "FM device" for the audio switch (AudioManagerEx.setDeviceConnectionStateForFM)
    private static final int FM_DEVICE = 0x10000000;

    /** Chosen in Settings after a test that played. */
    static boolean chosen(Prefs p) {
        return "own".equals(p.str("fmTuner", ""));
    }

    static String freqText() {
        return mhz <= 0 ? "" : String.format(Locale.US, "%.1f", mhz);
    }

    private static void step(String s) {
        steps.add(s);
    }

    /** Load the driver and open the chip; false (with the reason in steps) when the unit refuses. */
    private static boolean openChip(Prefs p) {
        if (open) return true;
        try {
            Class.forName("com.android.fmradio.FmNative");
            step("FM library loaded");
        } catch (Throwable e) {
            step("The unit won't let apps load its FM library (" + e.getClass().getSimpleName() + ")");
            return false;
        }
        // the chip takes one owner: ask the stock radio to close first
        Xt x = Hub.xtLink;
        if (x != null && x.ready() && x.radioAlive() && x.has("killProject")) {
            x.run("killProject:s:" + Fm.RADIO);
            step("Asked the stock radio to close");
            try {
                Thread.sleep(700);
            } catch (InterruptedException ignored) {
                // woken early: carry on
            }
        }
        try {
            if (!com.android.fmradio.FmNative.openDev()) {
                step("The FM chip didn't open (the stock radio may still have it, or apps aren't allowed)");
                return false;
            }
        } catch (Throwable e) {
            step("Opening the FM chip failed: " + e);
            return false;
        }
        step("FM chip opened");
        open = true;
        return true;
    }

    /** Play (or stop) FM at the saved frequency; null when it worked, else what went wrong. */
    static String play(Context c, Prefs p, boolean yes) {
        steps.clear();
        if (!yes) {
            stop(c);
            return null;
        }
        if (!openChip(p)) return steps.get(steps.size() - 1);
        float f = parse(p.fmFreq());
        try {
            if (!com.android.fmradio.FmNative.powerUp(f)) {
                step("The chip didn't power up at " + f + " MHz");
                return steps.get(steps.size() - 1);
            }
            step("Powered up at " + f + " MHz");
            mhz = f;
            audio(c, true);
            com.android.fmradio.FmNative.setMute(false);
            step("Sound unmuted");
            try {
                com.android.fmradio.FmNative.setRds(true);
            } catch (Throwable ignored) {
                // no RDS on this unit
            }
            on = true;
            return null;
        } catch (Throwable e) {
            step("Playing failed: " + e);
            return steps.get(steps.size() - 1);
        }
    }

    static void stop(Context c) {
        try {
            if (open) {
                com.android.fmradio.FmNative.setMute(true);
                audio(c, false);
                com.android.fmradio.FmNative.powerDown(0);
                com.android.fmradio.FmNative.closeDev();
            }
        } catch (Throwable ignored) {
            // not on this unit
        }
        open = false;
        on = false;
    }

    /** Next or previous station with a signal; the new frequency, or 0. */
    static float seek(Prefs p, boolean up) {
        if (!on) return 0;
        try {
            float f = com.android.fmradio.FmNative.seek(mhz, up);
            if (f >= 87.5f && f <= 108f) {
                com.android.fmradio.FmNative.tune(f);
                mhz = f;
                name = "";
                p.setFmFreq(freqText());
                return f;
            }
        } catch (Throwable ignored) {
            // not on this unit
        }
        return 0;
    }

    static boolean tune(Prefs p, float f) {
        if (!on) return false;
        try {
            if (com.android.fmradio.FmNative.tune(f)) {
                mhz = f;
                name = "";
                p.setFmFreq(freqText());
                return true;
            }
        } catch (Throwable ignored) {
            // not on this unit
        }
        return false;
    }

    /** Every station with a signal, low to high, as "91.1"; empty if the chip can't scan. */
    static List<String> scan() {
        List<String> out = new ArrayList<>();
        if (!on) return out;
        try {
            short[] s = com.android.fmradio.FmNative.autoScan(0);
            if (s != null) for (short v : s) {
                String t = stationText(v);
                if (!t.isEmpty() && !out.contains(t)) out.add(t);
            }
            com.android.fmradio.FmNative.tune(mhz);
        } catch (Throwable ignored) {
            // not on this unit
        }
        return out;
    }

    /** autoScan gives 10 kHz or 100 kHz units depending on the chip: 9110 or 911 both mean 91.1. */
    static String stationText(int v) {
        float f = v >= 8750 ? v / 100f : v >= 875 ? v / 10f : 0;
        return f >= 87.5f && f <= 108f ? String.format(Locale.US, "%.1f", f) : "";
    }

    static float parse(String s) {
        try {
            float f = Float.parseFloat(s);
            if (f >= 87.5f && f <= 108f) return f;
        } catch (Exception ignored) {
            // not a number: use the default
        }
        return 98.3f;
    }

    /** The radio's RDS station name, when the chip has one. */
    static void readName() {
        if (!on) return;
        try {
            byte[] ps = com.android.fmradio.FmNative.getPs();
            if (ps != null && ps.length > 0) name = new String(ps, "US-ASCII").trim();
        } catch (Throwable ignored) {
            // no RDS on this unit
        }
    }

    /** Move the unit's sound to the FM chip and back, like the stock radio's enableFmAudio. */
    private static void audio(Context c, boolean yes) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            am.getClass().getMethod("setDeviceConnectionStateForFM", int.class, int.class, String.class, String.class)
                    .invoke(am, FM_DEVICE, yes ? 1 : 0, "", "");
            step("FM sound path " + (yes ? "on" : "off"));
        } catch (Throwable e) {
            step("No FM sound path switch on this unit (" + e.getClass().getSimpleName() + ")");
        }
        try {
            am.setParameters("FM_mute=" + (yes ? 0 : 1));
            if (yes) am.setParameters("FM_Volume=" + am.getStreamVolume(AudioManager.STREAM_MUSIC));
        } catch (Throwable ignored) {
            // not on this unit
        }
        // and the MCU's source, as the radio's screen does
        Tw.Radio r = Hub.radioLink;
        if (r != null) r.play(yes);
    }

    private Tuner() {
    }
}
