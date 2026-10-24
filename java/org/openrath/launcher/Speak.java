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
    /** A sentence was heard to the end; the engine started but couldn't speak; it had no voice data. */
    private static boolean heard, spokeFail, noVoiceData;
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
            tell(noVoiceData ? NO_DATA : NO_VOICE);
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

    /** Engines already tried since the last time the voice worked or was retried. */
    private static final List<String> tried = new ArrayList<>();

    private static void start() {
        start(null);
    }

    /**
     * Start the unit's chosen engine (null), or a named one. When an engine doesn't start, the next
     * installed one is tried before giving up: a stereo whose Google voice is disabled or broken may
     * still have its own, or eSpeak NG.
     */
    private static void start(final String engine) {
        if (engine == null) {
            tried.clear();
            heard = spokeFail = noVoiceData = false;
        }
        else tried.add(engine);
        TextToSpeech.OnInitListener l = new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                synchronized (Speak.class) {
                    ready = status == TextToSpeech.SUCCESS;
                    if (!ready) {
                        String next = nextEngine(engines(app), tried, engine == null ? defaultEngine() : null);
                        if (next != null) {
                            try {
                                tts.shutdown();
                            } catch (Throwable ignored) {
                                // that engine already failed
                            }
                            start(next);
                            return;
                        }
                        failed = true;
                        failedAt = System.currentTimeMillis();
                        if (!waiting.isEmpty()) tell(NO_VOICE);
                        try {
                            tts.shutdown();
                        } catch (Throwable ignored) {
                            // the voice already failed
                        }
                        waiting.clear();
                        return;
                    }
                    if (!language()) {
                        // the engine starts but has no voice for English or the unit's language: it
                        // stays silent, so try the next engine, else say so
                        String next = nextEngine(engines(app), tried, engine == null ? defaultEngine() : null);
                        try {
                            tts.shutdown();
                        } catch (Throwable ignored) {
                            // that engine already failed
                        }
                        ready = false;
                        if (next != null) {
                            start(next);
                            return;
                        }
                        failed = true;
                        noVoiceData = true;
                        failedAt = System.currentTimeMillis();
                        if (!waiting.isEmpty()) tell(NO_DATA);
                        waiting.clear();
                        return;
                    }
                    final Prefs prefs = new Prefs(app);
                    // one listener: Android keeps only the last one set, so the ducking lives here too
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                            if (!id.startsWith("f") && Features.on(prefs, "duck")) Duck.start(app);
                        }

                        @Override
                        public void onDone(String id) {
                            if (id.startsWith("f")) {
                                made(id, true);
                                return;
                            }
                            heard = true;
                            spokeFail = false;
                            Duck.end(app);
                            if (id.equals(lastId) || ("f" + id).equals(lastId)) finishLong();
                        }

                        @Override
                        public void onStop(String id, boolean interrupted) {
                            if (!id.startsWith("f")) Duck.end(app);
                        }

                        @Override
                        @SuppressWarnings("deprecation")
                        public void onError(String id) {
                            onError(id, -1);
                        }

                        @Override
                        public void onError(String id, int code) {
                            if (id.startsWith("f")) made(id, false);
                            else {
                                Duck.end(app);
                                finishLong();
                            }
                            if (heard) return;
                            spokeFail = true;
                            Diag.note("voice", new IllegalStateException("speak error " + code));
                            tell(SPEAK_FAILED);
                        }
                    });
                    for (String[] t : waiting) speak(t[0], false, !t[1].isEmpty());
                    waiting.clear();
                }
            }
        };
        tts = engine == null ? new TextToSpeech(app, l) : new TextToSpeech(app, l, engine);
    }

    /** English (India, then any), else the unit's language. False when the engine has none of them. */
    private static boolean language() {
        try {
            if (tts.setLanguage(new Locale("en", "IN")) >= 0) return true;
            if (tts.setLanguage(Locale.UK) >= 0 || tts.setLanguage(Locale.US) >= 0) return true;
            if (tts.setLanguage(Locale.getDefault()) >= 0) return true;
            // a Hindi voice still reads English words, better than silence
            return tts.setLanguage(new Locale("hi", "IN")) >= 0;
        } catch (Throwable e) {
            return false;
        }
    }

    static final String NO_DATA = "The voice engine has no English voice installed. Settings > Map and navigation > Spoken voice > Android's voice settings downloads one, or get eSpeak NG.";
    static final String SPEAK_FAILED = "The voice engine started but couldn't speak. Settings > Map and navigation > Spoken voice > Repair it, or get eSpeak NG.";

    /** The engine Android uses by default, or null. */
    private static String defaultEngine() {
        try {
            return android.provider.Settings.Secure.getString(app.getContentResolver(), "tts_default_synth");
        } catch (Throwable e) {
            return null;
        }
    }

    /** The next engine to try after the default one (`failedDefault`) and the ones in `tried` didn't start, or null. Pure, for the tests. */
    static String nextEngine(List<String> installed, List<String> tried, String failedDefault) {
        for (String e : installed) if (!tried.contains(e) && !e.equals(failedDefault)) return e;
        return null;
    }

    /**
     * Speech is made into sound files and played by OpenRath's own player, the way the online radio
     * plays: on some stereos the voice engine's own sound never reaches the speakers (the engine
     * says it spoke, nothing is heard), while the app's music does. Clips are {id, file, long, made}.
     */
    private static final List<Object[]> clips = new ArrayList<>();
    private static android.media.MediaPlayer mp;
    private static boolean ducked;

    private static void speak(String text, boolean flush, boolean longOne) {
        if (flush) {
            stopClips();
            finishLong();
            try {
                tts.stop();
            } catch (Throwable ignored) {
                // nothing was being made
            }
        }
        if (longOne) fmAside();
        List<String> parts = sentences(text, 380);
        for (int i = 0; i < parts.size(); i++) {
            String id = "f" + (longOne ? "long" : "say") + (++seq);
            if (longOne && i == parts.size() - 1) lastId = id;
            java.io.File f = new java.io.File(app.getCacheDir(), "say-" + seq + ".wav");
            clips.add(new Object[]{id, f, longOne, false});
            int r;
            try {
                r = tts.synthesizeToFile(parts.get(i), null, f, id);
            } catch (Throwable e) {
                r = TextToSpeech.ERROR;
            }
            if (r != TextToSpeech.SUCCESS) {
                // this engine can't make files: let it speak by itself
                clips.remove(clips.size() - 1);
                tts.setAudioAttributes(attrs(longOne));
                tts.speak(parts.get(i), TextToSpeech.QUEUE_ADD, null, id.substring(1));
            }
        }
    }

    private static android.media.AudioAttributes attrs(boolean longOne) {
        return new android.media.AudioAttributes.Builder()
                .setUsage(longOne ? android.media.AudioAttributes.USAGE_MEDIA : android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    /** A clip was made (ok) or failed: play the next ones in order. */
    private static void made(final String id, final boolean ok) {
        main().post(new Runnable() {
            @Override
            public void run() {
                synchronized (Speak.class) {
                    for (int i = 0; i < clips.size(); i++) {
                        Object[] c = clips.get(i);
                        if (!c[0].equals(id)) continue;
                        if (ok) c[3] = true;
                        else {
                            clips.remove(i);
                            ((java.io.File) c[1]).delete();
                        }
                        break;
                    }
                    playNext();
                }
            }
        });
    }

    private static void playNext() {
        if (mp != null) return;
        if (clips.isEmpty() || !(Boolean) clips.get(0)[3]) {
            if (clips.isEmpty()) {
                if (ducked) Duck.end(app);
                ducked = false;
            }
            return;
        }
        final Object[] c = clips.remove(0);
        final java.io.File f = (java.io.File) c[1];
        try {
            mp = new android.media.MediaPlayer();
            mp.setAudioAttributes(attrs((Boolean) c[2]));
            mp.setDataSource(f.getPath());
            mp.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(android.media.MediaPlayer p) {
                    synchronized (Speak.class) {
                        heard = true;
                        spokeFail = false;
                        done(p, f, c[0]);
                    }
                }
            });
            mp.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(android.media.MediaPlayer p, int what, int extra) {
                    synchronized (Speak.class) {
                        Diag.note("voice", new IllegalStateException("clip play error " + what + "/" + extra));
                        done(p, f, c[0]);
                    }
                    return true;
                }
            });
            mp.prepare();
            if (!ducked && Features.on(new Prefs(app), "duck")) {
                Duck.start(app);
                ducked = true;
            }
            mp.start();
        } catch (Throwable e) {
            Diag.note("voice clip", e);
            if (mp != null) mp.release();
            mp = null;
            f.delete();
            playNext();
        }
    }

    private static void done(android.media.MediaPlayer p, java.io.File f, Object id) {
        try {
            p.release();
        } catch (Throwable ignored) {
            // already released
        }
        if (p == mp) mp = null;
        f.delete();
        if (String.valueOf(id).substring(1).equals(lastId) || String.valueOf(id).equals(lastId)) finishLong();
        playNext();
    }

    private static void stopClips() {
        for (Object[] c : clips) ((java.io.File) c[1]).delete();
        clips.clear();
        if (mp != null) {
            try {
                mp.stop();
                mp.release();
            } catch (Throwable ignored) {
                // already done
            }
            mp = null;
        }
        if (ducked) Duck.end(app);
        ducked = false;
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
        } catch (Exception e) {
            Diag.note("voice list", e);
        }
        return out;
    }

    /** How the voice is doing, for Settings: working, not starting, or not tried yet. Pure, for the tests. */
    static String state(boolean installed, boolean ready, boolean failed) {
        return state(installed, ready, failed, false, false, false);
    }

    static String state(boolean installed, boolean ready, boolean failed, boolean heard, boolean spokeFail, boolean noData) {
        if (!installed) return "No voice engine is installed, so OpenRath can't speak.";
        if (noData) return "The voice engine has no English voice installed. Download one in Android's voice settings, or get eSpeak NG.";
        if (ready && spokeFail) return "The voice engine starts but couldn't speak. Repairing it (clear its storage), or eSpeak NG, usually fixes this.";
        if (ready && heard) return "The voice is working.";
        if (ready) return "The voice engine has started. Tap Test the voice and listen.";
        if (failed) return "The voice engine is installed but doesn't start. Updating it, or clearing its storage, usually fixes this.";
        return "The voice hasn't been tried yet: tap Test the voice.";
    }

    /**
     * Get a working voice. Android doesn't let an app install an engine by itself, and Google's can't be
     * shipped inside OpenRath, so this offers the ways to fix it: test it, update or repair Speech
     * Services by Google, get eSpeak NG (free, works without Google), or Android's voice settings. Some
     * stereos' voice settings screen closes by itself when the engine is broken, so it comes last.
     */
    static synchronized void getVoice(final Context c) {
        if (!(c instanceof android.app.Activity)) {
            open(c, store(c, GOOGLE_TTS));
            return;
        }
        final boolean google = engines(c).contains(GOOGLE_TTS);
        java.util.List<String> names = new ArrayList<>();
        final java.util.List<Runnable> acts = new ArrayList<>();
        names.add(Hindi.t("Test the voice"));
        acts.add(new Runnable() {
            @Override
            public void run() {
                failed = false;
                tts = null;
                ready = false;
                sayLong(c, Hindi.t("Hello. The OpenRath voice is working."));
            }
        });
        if (google) {
            // the voice's own download screen: the stereo's Android voice settings screen may crash
            names.add(Hindi.t("Download the English voice (opens Google's voice download)"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    android.content.Intent i = new android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(GOOGLE_TTS);
                    if (c.getPackageManager().resolveActivity(i, 0) == null) i = new android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    open(c, i);
                    noVoiceData = false;
                }
            });
        }
        names.add(Hindi.t(google ? "Update Speech Services by Google" : "Get Speech Services by Google (speaks Hindi)"));
        acts.add(new Runnable() {
            @Override
            public void run() {
                open(c, store(c, GOOGLE_TTS));
            }
        });
        if (google) {
            names.add(Hindi.t("Repair it: open its app info to enable it or clear its storage"));
            acts.add(new Runnable() {
                @Override
                public void run() {
                    open(c, new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + GOOGLE_TTS)));
                }
            });
        }
        names.add(Hindi.t("Get eSpeak NG instead (free, no Google needed)"));
        acts.add(new Runnable() {
            @Override
            public void run() {
                open(c, store(c, ESPEAK));
            }
        });
        names.add(Hindi.t("Android's voice settings"));
        acts.add(new Runnable() {
            @Override
            public void run() {
                open(c, new android.content.Intent("com.android.settings.TTS_SETTINGS"));
            }
        });
        new android.app.AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
                // a dialog with a message hides its list, so the state goes under the title
                .setCustomTitle(title(c, Hindi.t("Spoken voice"), Hindi.t(state(!engines(c).isEmpty(), ready, failed, heard, spokeFail, noVoiceData))))
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        acts.get(w).run();
                    }
                })
                .setNegativeButton(Hindi.t("Close"), null).show();
    }

    private static android.view.View title(Context c, String title, String text) {
        float d = c.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(c);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding((int) (24 * d), (int) (20 * d), (int) (24 * d), (int) (8 * d));
        android.widget.TextView t = new android.widget.TextView(c);
        t.setText(title);
        t.setTextSize(22);
        t.setTextColor(0xFFFFFFFF);
        box.addView(t);
        android.widget.TextView m = new android.widget.TextView(c);
        m.setText(text);
        m.setTextSize(17);
        m.setTextColor(0xCCFFFFFF);
        m.setPadding(0, (int) (10 * d), 0, 0);
        box.addView(m);
        return box;
    }

    /** eSpeak NG's package: a free, open-source voice that works without Google's services. */
    static final String ESPEAK = "com.reecedunn.espeak";

    /** An app's page in the Play Store, else on the web (F-Droid for eSpeak NG, which isn't on every store). */
    private static android.content.Intent store(Context c, String pkg) {
        // eSpeak NG isn't on the Play Store: always its F-Droid page, which has the APK to download
        if (ESPEAK.equals(pkg)) return new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://f-droid.org/packages/" + pkg + "/"));
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg));
        if (c.getPackageManager().resolveActivity(i, 0) != null) return i;
        return new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ESPEAK.equals(pkg)
                ? "https://f-droid.org/packages/" + pkg + "/" : "https://play.google.com/store/apps/details?id=" + pkg));
    }

    private static void open(Context c, android.content.Intent i) {
        try {
            c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                c.startActivity(new android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ex) {
                Diag.note("voice settings", ex);
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
        language();
        return true;
    }

    /** True while something is being said (or waits to be). */
    static synchronized boolean speaking() {
        return !waiting.isEmpty() || mp != null || !clips.isEmpty() || tts != null && ready && tts.isSpeaking();
    }

    /** Stop talking now (the news box's Stop). */
    static synchronized void stop() {
        waiting.clear();
        stopClips();
        if (tts != null && ready) tts.stop();
        finishLong();
    }

    private Speak() {
    }
}
