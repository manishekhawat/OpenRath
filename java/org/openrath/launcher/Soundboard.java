package org.openrath.launcher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

/**
 * The soundboard box: six pads of fun sounds (horn, fart, applause and others) for when parked
 * with friends. The sounds are made in code, so no recordings ship with the app. Pads stay quiet
 * while the car moves, so they can't distract the driver.
 */
final class Soundboard {

    static final int RATE = 22050;
    static final String[] PADS = {"Horn", "Fart", "Applause", "Ba-dum-tss", "Bell", "Wrong answer"};

    /** Above this speed the pads don't play. */
    static final float PARKED_KMH = 5;

    private static AudioTrack playing;

    /** The sound for pad i as 16-bit mono samples at RATE. */
    static short[] make(int pad) {
        Random r = new Random(41 + pad);
        switch (pad) {
            case 0: {   // two-tone car horn
                int n = (int) (RATE * 0.9);
                short[] s = new short[n];
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    double v = Math.signum(Math.sin(2 * Math.PI * 420 * t)) * 0.5 + Math.signum(Math.sin(2 * Math.PI * 500 * t)) * 0.5;
                    s[i] = (short) (v * env(i, n, 0.02, 0.05) * 9000);
                }
                return s;
            }
            case 1: {   // a low, wobbling buzz that falls away
                int n = (int) (RATE * 0.8);
                short[] s = new short[n];
                double ph = 0;
                for (int i = 0; i < n; i++) {
                    double t = (double) i / n;
                    double f = 95 - 45 * t + 12 * Math.sin(2 * Math.PI * 7 * t * 3) + r.nextGaussian() * 6;
                    ph += 2 * Math.PI * Math.max(30, f) / RATE;
                    double v = (ph % (2 * Math.PI)) / Math.PI - 1;   // sawtooth
                    v = v * 0.8 + r.nextGaussian() * 0.12;
                    s[i] = (short) (clip(v) * env(i, n, 0.03, 0.25) * 14000);
                }
                return s;
            }
            case 2: {   // applause: many short claps of noise
                int n = RATE * 2;
                double[] d = new double[n];
                for (int c = 0; c < 140; c++) {
                    int at = r.nextInt(n - 2000);
                    double g = 0.3 + r.nextDouble() * 0.7;
                    for (int k = 0; k < 1500; k++) d[at + k] += r.nextGaussian() * g * Math.exp(-k / 220.0);
                }
                short[] s = new short[n];
                for (int i = 0; i < n; i++) s[i] = (short) (clip(d[i] * 0.35) * env(i, n, 0.1, 0.3) * 16000);
                return s;
            }
            case 3: {   // two drum hits and a cymbal
                int n = (int) (RATE * 1.4);
                double[] d = new double[n];
                int[] at = {0, (int) (RATE * 0.18), (int) (RATE * 0.42)};
                for (int h = 0; h < 3; h++) {
                    for (int k = 0; at[h] + k < n; k++) {
                        double t = (double) k / RATE;
                        if (h < 2) {
                            if (k > RATE * 0.2) break;
                            d[at[h] + k] += (Math.sin(2 * Math.PI * (h == 0 ? 110 : 150) * t) * 0.8 + r.nextGaussian() * 0.35) * Math.exp(-t * 22);
                        } else {
                            d[at[h] + k] += r.nextGaussian() * 0.45 * Math.exp(-t * 3.2);
                        }
                    }
                }
                short[] s = new short[n];
                for (int i = 0; i < n; i++) s[i] = (short) (clip(d[i]) * Math.min(1, i / 60.0) * 16000);
                return s;
            }
            case 4: {   // a bell
                int n = (int) (RATE * 1.6);
                short[] s = new short[n];
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    double v = Math.sin(2 * Math.PI * 880 * t) * 0.6 + Math.sin(2 * Math.PI * 1760 * t) * 0.25
                            + Math.sin(2 * Math.PI * 2640 * t) * 0.15;
                    s[i] = (short) (v * Math.exp(-t * 2.5) * 14000);
                }
                return s;
            }
            default: {   // a quiz-show buzzer
                int n = (int) (RATE * 0.7);
                short[] s = new short[n];
                for (int i = 0; i < n; i++) {
                    double t = (double) i / RATE;
                    double v = Math.signum(Math.sin(2 * Math.PI * 140 * t)) * 0.7 + Math.signum(Math.sin(2 * Math.PI * 147 * t)) * 0.3;
                    s[i] = (short) (v * env(i, n, 0.01, 0.05) * 8000);
                }
                return s;
            }
        }
    }

    /** A fade in over `in` and out over `out` (fractions of the sound), so it doesn't click. */
    private static double env(int i, int n, double in, double out) {
        double t = (double) i / n;
        return Math.min(1, Math.min(t / in, (1 - t) / out));
    }

    private static double clip(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    /** Plays pad i; false (with a note) while the car moves. */
    static boolean play(Context c, int pad) {
        if (Drive.kmhNow > PARKED_KMH) {
            Toast.makeText(c, Hindi.t("The soundboard plays only when the car is stopped"), Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            if (playing != null) playing.release();
            short[] s = make(pad);
            AudioTrack t = new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    s.length * 2, AudioTrack.MODE_STATIC, 0);
            t.write(s, 0, s.length);
            t.play();
            playing = t;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Soundboard() {
    }
}
