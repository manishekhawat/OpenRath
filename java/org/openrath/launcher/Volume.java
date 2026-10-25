package org.openrath.launcher;

import android.content.Context;
import android.media.AudioManager;

import java.util.List;
import java.util.Locale;

/**
 * The volume up and down buttons on OpenRath's screens. On Topway units the speakers' volume is
 * the MCU's, not Android's music volume, so a plain change of the music stream does nothing: the
 * radio service's own volume command is used when it has one, else the change goes the way the
 * stereo's volume keys do (the suggested stream, with the unit's volume panel), which the unit
 * passes on to its MCU.
 */
final class Volume {

    static void step(Context c, boolean up) {
        Xt xt = Hub.xtLink;
        if (xt != null && xt.ready()) {
            String m = command(xt.methods, up);
            if (m != null && xt.run(m) == null) return;
        }
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            am.adjustSuggestedStreamVolume(up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
        } catch (Throwable e) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        }
    }

    /** The radio service's volume up or down command with no arguments (volumeUp(), setVolUp()…), or null. Pure, for the tests. */
    static String command(List<String> sigs, boolean up) {
        for (String sig : sigs) {
            if (!sig.endsWith("()")) continue;
            String n = sig.substring(0, sig.length() - 2), l = n.toLowerCase(Locale.US);
            if (!l.contains("vol") || l.contains("mute") || l.startsWith("get") || l.startsWith("is")) continue;
            if (up ? l.endsWith("up") || l.endsWith("add") || l.endsWith("inc") : l.endsWith("down") || l.endsWith("dec") || l.endsWith("sub")) return n;
        }
        return null;
    }

    private Volume() {
    }
}
