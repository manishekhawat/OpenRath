package org.openrath.launcher;

import android.os.Handler;
import android.os.Message;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Topway's link to the unit's MCU, android.tw.john.TWUtil, built into the firmware of TW / Topway
 * head units. The stock radio and launcher talk to the FM tuner through it, and so do third-party
 * radio apps such as NavRadio+ (the codes below come from the open-source ivvlev/CarRadio).
 * It's reached by reflection because it's not part of normal Android; on other units it's absent.
 */
final class Tw {

    // radio messages
    static final int RADIO_KEYS = 513, RADIO = 1025, STATION = 1026, RDS = 1028, RDS_TEXT = 1029, REGION = 1030;
    static final int AUDIO = 769, ACTIVITY = 40448, FOCUS = 40465;
    static final int SRC_RADIO = 0x0A, SRC_RELEASE = 0x8A;
    // backlight messages, from the stock Settings app: 0x102 brightness mode (0 auto, 1 light, 2 dark), 0x114 level
    static final int BACKLIGHT_MODE = 0x102, BACKLIGHT = 0x114;

    private static Class<?> cls;
    private static boolean looked;

    static boolean available() {
        if (!looked) {
            looked = true;
            try {
                cls = Class.forName("android.tw.john.TWUtil");
            } catch (Throwable e) {
                cls = null;
            }
        }
        return cls != null;
    }

    private Object util;
    private Method write;

    /** Open a connection for these message codes; messages arrive on `h`. Throws if the unit has no TWUtil. */
    Tw(int type, int[] codes, Handler h, String tag) throws Exception {
        if (!available()) throw new IllegalStateException("no TWUtil");
        Constructor<?> c;
        try {
            c = cls.getConstructor(int.class);
            util = c.newInstance(type);
        } catch (NoSuchMethodException e) {
            util = cls.getConstructor().newInstance();
        }
        short[] s = new short[codes.length];
        for (int i = 0; i < codes.length; i++) s[i] = (short) codes[i];
        int r = (Integer) cls.getMethod("open", short[].class).invoke(util, (Object) s);
        if (r != 0) throw new IllegalStateException("TWUtil.open returned " + r);
        cls.getMethod("start").invoke(util);
        cls.getMethod("addHandler", String.class, Handler.class).invoke(util, tag, h);
        write = cls.getMethod("write", int.class, int.class, int.class);
    }

    int write(int what, int a1, int a2) {
        try {
            return (Integer) write.invoke(util, what, a1, a2);
        } catch (Throwable e) {
            return -1;
        }
    }

    /** The two-number write(what, value) the stock radio uses; falls back to write(what, value, 0). */
    int write2(int what, int a1) {
        try {
            return (Integer) cls.getMethod("write", int.class, int.class).invoke(util, what, a1);
        } catch (Throwable e) {
            return write(what, a1, 0);
        }
    }

    boolean closed;
    /** The other connections a wide spy() opened alongside this one. */
    private final java.util.List<Tw> more = new java.util.ArrayList<>();
    /** Why the last spy() opened nothing, for the setup's report. */
    static String spyError = "";

    void close() {
        closed = true;
        for (Tw t : more) t.close();
        try {
            cls.getMethod("stop").invoke(util);
            cls.getMethod("close").invoke(util);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ the FM tuner

    /** Live FM state for the radio box, fed by the tuner's own messages. */
    static final class Radio {
        int freq;            // FM in 10 kHz steps (9110 = 91.1 MHz), AM in kHz
        int band;            // 0, 1: FM; 2: AM
        String name = "";    // RDS station name
        boolean on;          // the tuner has the audio
        boolean seen;        // we've heard from the tuner
        private Tw tw;
        private final Handler handler;

        Radio(final Runnable changed) {
            handler = new Handler() {
                @Override
                public void handleMessage(Message m) {
                    seen = true;
                    if (m.what == RADIO && m.arg1 == 2) freq = m.arg2;
                    else if (m.what == RADIO && m.arg1 == 1) band = m.arg2;
                    else if (m.what == RDS && m.obj instanceof String) name = ((String) m.obj).trim();
                    else if (m.what == AUDIO || m.what == ACTIVITY) on = m.arg1 == 1;
                    changed.run();
                }
            };
        }

        boolean open() {
            if (tw != null) return true;
            if (!Crash.begin("opening the FM tuner")) return false;
            try {
                tw = new Tw(1, new int[]{RADIO_KEYS, RADIO, STATION, RDS, RDS_TEXT, REGION, AUDIO, ACTIVITY}, handler, "radio");
                tw.write(RADIO, 255, 0);   // ask for band, frequency and flags
                tw.write(RDS, 255, 0);
                tw.write(RDS_TEXT, 255, 0);
                Crash.end();
                return true;
            } catch (Throwable e) {
                tw = null;
                Crash.end();
                return false;
            }
        }

        void close() {
            if (tw != null) tw.close();
            tw = null;
        }

        String frequency() {
            if (freq <= 0) return "";
            if (band == 2) return freq + " kHz";
            return String.format(java.util.Locale.US, "%.1f", freq / 100f);
        }

        void next() {
            if (open()) tw.write(RADIO, 1, 0);   // seek up to the next station
        }

        void prev() {
            if (open()) tw.write(RADIO, 1, 1);
        }

        /**
         * Switch the unit's sound to the FM tuner, or let go of it, with the same two messages the stock
         * radio app (com.tw.radio) sends when its screen comes up or goes: "activity" 0x9E00 = 10 or 0x8A,
         * and "source" 0x9E11 = (0xC0, 10 or 0x8A). The tuner itself runs inside the radio app.
         */
        void play(boolean yes) {
            if (!open()) return;
            tw.write2(ACTIVITY, yes ? SRC_RADIO : SRC_RELEASE);
            tw.write(FOCUS, 0xC0, yes ? SRC_RADIO : SRC_RELEASE);
            on = yes;
        }
    }

    // ------------------------------------------------------------------ listening to everything (setup)

    /**
     * For the headlight and indicator setups: listen to a wide range of MCU message codes and keep
     * the latest value of each as "tw:WHAT:ARG1" = "ARG2" (plus the text, if any) in Lights.live.
     */
    static Tw spy(final int[] codes) {
        Handler h = new Handler() {
            @Override
            public void handleMessage(Message m) {
                String v = String.valueOf(m.arg2) + (m.obj instanceof String ? "/" + m.obj : "");
                synchronized (Lights.live) {
                    Lights.live.put("tw:" + m.what + ":" + m.arg1, v);
                    // the same message as one value too, so a change in arg1 (a state, not a sub-code) shows
                    Lights.live.put("tw:" + m.what, m.arg1 + "/" + v);
                }
            }
        };
        try {
            // in groups: the unit's link takes only so many codes per connection, and one bad code
            // shouldn't lose the rest
            Tw first = null;
            int opened = 0;
            spyError = "";
            for (int from = 0; from < codes.length; from += 10) {
                int[] part = java.util.Arrays.copyOfRange(codes, from, Math.min(codes.length, from + 10));
                try {
                    Tw t = new Tw(0, part, h, "openrath" + from);
                    if (first == null) first = t;
                    else first.more.add(t);
                    opened += part.length;
                } catch (Throwable e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    if (spyError.isEmpty()) spyError = String.valueOf(c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName());
                }
            }
            if (first == null) return null;
            spyCodesOpen = opened;
            final Tw t = first;
            // ask the MCU for its backlight state every 1.5 s, the way the stock Settings app does
            // (0x102 "brightness mode": Auto follows the headlights; 0x114 backlight level), so a
            // headlight switch that only shows in the answer is seen by the setup
            boolean wantsBacklight = false;
            for (int c : codes) wantsBacklight |= c == BACKLIGHT_MODE || c == BACKLIGHT;
            if (!wantsBacklight) return t;
            final Handler ask = new Handler(android.os.Looper.getMainLooper());
            ask.post(new Runnable() {
                @Override
                public void run() {
                    if (t.closed) return;
                    t.write(BACKLIGHT_MODE, 0xff, 0);
                    t.write(BACKLIGHT, 0xff, 0);
                    ask.postDelayed(this, 1500);
                }
            });
            return t;
        } catch (Throwable e) {
            spyError = String.valueOf(e);
            return null;
        }
    }

    /** How many codes the last spy() is listening to. */
    static int spyCodesOpen;

    /** spy(), skipped for good if it ever took the app down. */
    static Tw guardedSpy(int[] codes) {
        if (!available()) {
            spyError = "no TWUtil on this unit";
            return null;
        }
        if (!Crash.begin("listening to the Topway MCU")) {
            spyError = "skipped, it stopped the app before";
            return null;
        }
        Tw t = spy(codes);
        Crash.end();
        return t;
    }

    /** Codes the setup listens to: the low system ranges where key, light and car messages live. */
    static int[] spyCodes() {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int base : new int[]{0x0100, 0x0200, 0x0300, 0x0500, 0x0600, 0x0700}) {
            for (int i = 0; i < 12; i++) l.add(base + i);
        }
        // and every other code the unit's own command service (com.tw.service.xt) opens
        for (int c : new int[]{0x0210, 0x0112, 0x0114, 0x0603, 0x0605, 0x0404, 0x0505, 0x0506, 0x0507, 0x0508, 0x0509, 0x9E00, 0x9E11, 0x9F10, 0x9F1B, 0x9F1C, 0x9F1D})
            if (!l.contains(c)) l.add(c);
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }
}
