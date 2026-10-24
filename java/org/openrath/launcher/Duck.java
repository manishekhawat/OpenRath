package org.openrath.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/**
 * Music dips while the car speaks (a turn, a warning): the voice asks Android for a short turn
 * with the sound ("duck"), and gives it back when it is done. Players that follow Android's rules
 * (this app's own, most music apps) lower themselves; the unit's own radio may not.
 */
final class Duck {

    private static int speaking;
    private static final AudioManager.OnAudioFocusChangeListener NONE = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int change) {
        }
    };

    static void attach(final Context c, final Prefs p, TextToSpeech tts) {
        final Context app = c.getApplicationContext();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String id) {
                if (Features.on(p, "duck")) start(app);
            }

            @Override
            public void onDone(String id) {
                end(app);
            }

            @Override
            public void onError(String id) {
                end(app);
            }

            @Override
            public void onStop(String id, boolean interrupted) {
                end(app);   // cut off by newer speech
            }
        });
    }

    static synchronized void start(Context c) {
        if (speaking++ > 0) return;
        try {
            ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).requestAudioFocus(NONE, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        } catch (Throwable e) {
            Diag.note("audio duck", e);
        }
    }

    static synchronized void end(Context c) {
        if (speaking == 0) return;
        if (--speaking > 0) return;
        try {
            ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocus(NONE);
        } catch (Throwable e) {
            Diag.note("audio unduck", e);
        }
    }

    private Duck() {
    }
}
