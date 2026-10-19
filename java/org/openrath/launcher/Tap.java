package org.openrath.launcher;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** A short tick and a buzz on each tap of a box or dock button, so a tap is felt without looking. */
final class Tap {

    private static ToneGenerator tone;

    static void feedback(View v, Prefs p) {
        if (p == null || !Features.on(p, "tapFeedback")) return;
        try {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Throwable ignored) {
            // on every tap: no haptics on this unit
        }
        try {
            // the system stream, not music: it isn't lost under loud music and doesn't pause it
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_SYSTEM, 45);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 25);
        } catch (Throwable ignored) {
            // on every tap: the tap still works without the tone
        }
    }

    private Tap() {
    }
}
