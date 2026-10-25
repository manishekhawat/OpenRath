package org.openrath.launcher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Listening for a voice command inside OpenRath: a small pill at the top shows "Listening…" and
 * the words as they are heard, and the dashboard stays in view, instead of the speech app's full
 * screen. Needs the microphone permission and a speech service on the unit; without either, the
 * speech app's own screen is used as before (HomeActivity.voiceSearch).
 */
final class Listen {

    private static SpeechRecognizer rec;
    private static TextView pill;

    /** Whether OpenRath can listen by itself here. */
    static boolean can(HomeActivity a) {
        return a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && SpeechRecognizer.isRecognitionAvailable(a);
    }

    /** Start listening with the same settings as the speech app's screen (`i`); the words go to HomeVoice. */
    static void start(final HomeActivity a, Intent i, final MapCard map) {
        stop(a);
        try {
            rec = SpeechRecognizer.createSpeechRecognizer(a);
        } catch (Throwable e) {
            Diag.note("speech recognizer", e);
            rec = null;
            return;
        }
        show(a, Hindi.t("Listening…"));
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        rec.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float db) {}
            @Override public void onBufferReceived(byte[] buf) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int type, Bundle b) {}

            @Override
            public void onPartialResults(Bundle b) {
                ArrayList<String> got = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (got != null && !got.isEmpty() && pill != null) pill.setText(got.get(0));
            }

            @Override
            public void onResults(Bundle b) {
                ArrayList<String> got = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                stop(a);
                HomeVoice.heard(a, got, map);
            }

            @Override
            public void onError(int error) {
                boolean none = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                show(a, none ? Hindi.t("Didn't catch that") : Hindi.t("Voice isn't available right now"));
                if (pill != null) pill.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stop(a);
                    }
                }, 1500);
                if (map != null) map.voiceResult(null);
            }
        });
        try {
            rec.startListening(i);
        } catch (Throwable e) {
            Diag.note("speech listen", e);
            stop(a);
        }
    }

    private static void show(HomeActivity a, String s) {
        if (pill == null) {
            pill = Ui.text(a, 22, Ui.SILVER, "sans-serif", Gravity.CENTER);
            pill.setPadding(Ui.px(28), Ui.px(16), Ui.px(28), Ui.px(16));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(999);
            g.setColor(Ui.solidPanel());
            g.setStroke(Math.max(1, Ui.px(2)), Ui.ACCENT);
            pill.setBackground(g);
            pill.setElevation(Ui.px(12));
            pill.setEllipsize(android.text.TextUtils.TruncateAt.START);
            pill.setOnClickListener(new android.view.View.OnClickListener() {
                @Override
                public void onClick(android.view.View v) {
                    stop((HomeActivity) v.getContext());   // a tap cancels
                }
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = Ui.px(16);
            a.root.addView(pill, lp);
        }
        pill.setText(s);
    }

    static void stop(HomeActivity a) {
        if (rec != null) {
            try {
                rec.cancel();
                rec.destroy();
            } catch (Throwable ignored) {
                // already gone
            }
            rec = null;
        }
        if (pill != null) {
            a.root.removeView(pill);
            pill = null;
        }
    }

    private Listen() {
    }
}
