package org.openrath.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The home screen's voice: one text-to-speech engine for the whole app. Anything said before the
 * engine is ready is kept and said once it is (the first greeting used to be lost that way). It
 * speaks Indian English, else the unit's own language when that voice isn't installed, and plays as
 * navigation guidance so car units mix it over the radio and music. Long passages (the news) are
 * said a sentence at a time, as music, with the FM tuner's sound handed back to the unit while
 * they play: on Topway units the MCU keeps app sound silent while FM has the speakers.
 */
final class Speak {

    private static TextToSpeech tts;
    private static boolean ready, failed;
    private static long failedAt;
    private static Context app;
    private static final List<String[]> waiting = new ArrayList<>();   // {text, "long" or ""}
    private static Handler main;
    /** Runs when a long passage ends (gives FM its sound back). */
    private static Runnable after;
    private static int seq;
    private static String lastId = "";

    static synchronized void say(Context c, String text, boolean flush) {
        say(c, text, flush, false);
    }

    /**
     * A long passage, such as the news: as music rather than a prompt, and with FM paused for it.
     * Tells the driver when the unit has no working voice instead of staying silent.
     */
    static synchronized void sayLong(Context c, String text) {
        say(c, text, true, true);
    }

    private static void say(Context c, String text, boolean flush, boolean longOne) {
        if (text == null || text.trim().isEmpty()) return;
        app = c.getApplicationContext();
        if (failed && System.currentTimeMillis() - failedAt > 60000) {
            // a voice may have been installed since: start the engine again
            failed = false;
            tts = null;
        }
        if (failed) {
            tell(NO_VOICE);
            return;
        }
        if (tts == null) start();
        if (!ready) {
            if (flush) waiting.clear();
            waiting.add(new String[]{text, longOne ? "long" : ""});
            return;
        }
        speak(text, flush, longOne);
    }

    private static void start() {
        tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                synchronized (Speak.class) {
                    ready = status == TextToSpeech.SUCCESS;
                    if (!ready) {
                        failed = true;
                        failedAt = System.currentTimeMillis();
                        if (!waiting.isEmpty()) tell(NO_VOICE);
                        try {
                            tts.shutdown();
                        } catch (Throwable ignored) {
                        }
                        waiting.clear();
                        return;
                    }
                    if (tts.setLanguage(new Locale("en", "IN")) < 0) tts.setLanguage(Locale.getDefault());
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                        }

                        @Override
                        public void onDone(String id) {
                            if (id.equals(lastId)) finishLong();
                        }

                        @Override
                        @SuppressWarnings("deprecation")
                        public void onError(String id) {
                            finishLong();
                            if (id.startsWith("long")) tell("The voice couldn't read this aloud. Check the stereo's text-to-speech in Android Settings.");
                        }
                    });
                    Duck.attach(app, new Prefs(app), tts);
                    for (String[] t : waiting) speak(t[0], false, !t[1].isEmpty());
                    waiting.clear();
                }
            }
        });
    }

    private static void speak(String text, boolean flush, boolean longOne) {
        tts.setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(longOne ? android.media.AudioAttributes.USAGE_MEDIA : android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
        if (flush) finishLong();
        if (longOne) fmAside();
        List<String> parts = sentences(text, 380);
        for (int i = 0; i < parts.size(); i++) {
            String id = (longOne ? "long" : "say") + (++seq);
            if (longOne && i == parts.size() - 1) lastId = id;
            tts.speak(parts.get(i), flush && i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, id);
        }
    }

    /** Text in pieces of at most `max` characters, broken after sentences where possible. */
    static List<String> sentences(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String s : text.trim().split("(?<=[.!?।])\\s+")) {
            while (s.length() > max) {
                int cut = s.lastIndexOf(' ', max);
                if (cut <= 0) cut = max;
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.add(s.substring(0, cut).trim());
                s = s.substring(cut).trim();
            }
            if (cur.length() > 0 && cur.length() + 1 + s.length() > max) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (!s.isEmpty()) cur.append(cur.length() == 0 ? "" : " ").append(s);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** FM (through the MCU) keeps app sound off the speakers: give it to the app while reading. */
    private static void fmAside() {
        if (after != null) return;
        final Tw.Radio r = Hub.radioLink;
        if (r == null || !r.on || Tuner.on) return;
        r.play(false);
        after = new Runnable() {
            @Override
            public void run() {
                r.play(true);
            }
        };
    }

    private static void finishLong() {
        final Runnable a = after;
        after = null;
        if (a != null) main().post(a);
    }

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    static final String NO_VOICE = "The stereo has no spoken voice yet. Settings > Map and navigation > Spoken voice gets one in two taps.";
    static final String GOOGLE_TTS = "com.google.android.tts";

    /** The voice engines installed (Google's, Samsung's, eSpeak and so on). */
    static List<String> engines(Context c) {
        List<String> out = new ArrayList<>();
        try {
            for (android.content.pm.ResolveInfo r : c.getPackageManager().queryIntentServices(
                    new android.content.Intent("android.intent.action.TTS_SERVICE"), 0)) {
                out.add(r.serviceInfo.packageName);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * Get a voice: Google's Speech Services from the Play Store when there's no engine (Android doesn't let
     * an app install another one by itself, and Google's can't be shipped inside OpenRath), else the
     * voice settings to pick it and download the Hindi and English voices.
     */
    static synchronized void getVoice(Context c) {
        android.content.Intent i;
        if (!engines(c).contains(GOOGLE_TTS)) {
            i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + GOOGLE_TTS));
            if (c.getPackageManager().resolveActivity(i, 0) == null) {
                i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + GOOGLE_TTS));
            }
        } else {
            i = new android.content.Intent("com.android.settings.TTS_SETTINGS");
        }
        try {
            c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                c.startActivity(new android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
        if (failed) {   // try again on the next sentence
            failed = false;
            tts = null;
        }
    }

    private static void tell(final String s) {
        main().post(new Runnable() {
            @Override
            public void run() {
                if (app != null) android.widget.Toast.makeText(app, Hindi.t(s), android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Say something in another language (the cab's passenger phrases), then go back to Indian English.
     * False when that language's voice isn't installed (or the engine isn't ready yet).
     */
    static synchronized boolean sayIn(Context c, String text, Locale lang) {
        if (tts == null || !ready) {
            say(c, text, true);
            return tts != null;
        }
        if (tts.isLanguageAvailable(lang) < 0) return false;
        tts.setLanguage(lang);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "phrase");
        if (tts.setLanguage(new Locale("en", "IN")) < 0) tts.setLanguage(Locale.getDefault());
        return true;
    }

    /** True while something is being said (or waits to be). */
    static synchronized boolean speaking() {
        return !waiting.isEmpty() || tts != null && ready && tts.isSpeaking();
    }

    /** Stop talking now (the news box's Stop). */
    static synchronized void stop() {
        waiting.clear();
        if (tts != null && ready) tts.stop();
        finishLong();
    }

    private Speak() {
    }
}
