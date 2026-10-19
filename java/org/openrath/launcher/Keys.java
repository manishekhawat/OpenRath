package org.openrath.launcher;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Steering-wheel buttons. A button reaches Android as a key press (seen by the dashboard, or by
 * the optional accessibility service when another app is on screen) or, on Topway units, as a
 * message from the radio service. Settings > Steering-wheel buttons learns a button by having the
 * owner press it, then lets them pick what it does.
 */
final class Keys {

    static final String[] ACTIONS = {"toggle", "next", "prev", "mute", "volUp", "volDown", "answer", "hangup",
            "voice", "navi", "radio", "music", "dash", "screen", "page", "clip", "flood", "driver", "docs", "fog", "kids", "share", "split", "parkTimer", "hotspot", "news", "cab", "meter", "sos"};
    static final String[] ACTION_NAMES = {"Play / pause", "Next track or station", "Previous track or station", "Mute",
            "Volume up", "Volume down", "Answer call", "End call", "Voice command", "Open navigation",
            "Open the radio", "Open music", "Show the dashboard", "Screen off / on", "Next dashboard page",
            "Save the dashcam clip", "Mark a waterlogging spot", "Next driver", "Show the car documents",
            "Fog mode on / off", "Trip games for children", "Share the trip (QR code)", "Split the cost (UPI QR)",
            "Paid parking reminder", "Guest Wi-Fi code", "Read the news aloud",
            "Cab menu", "Fare meter start / stop", "Cab SOS"};
    static final String ACTION = "org.openrath.launcher.KEY_ACTION";
    /** The extra on a key action that carries this install's token (see token()). */
    static final String TOKEN = "token";
    private static volatile String token;

    /**
     * A random code made once per install and kept in the settings (never in backups). OpenRath's
     * own key actions carry it; the dashboard ignores a key action without it, so another app
     * can't make it answer a call, start an SOS or switch the hotspot on.
     */
    static String token(Context c) {
        String t = token;
        if (t != null) return t;
        synchronized (Keys.class) {
            if (token != null) return token;
            Prefs p = new Prefs(c.getApplicationContext());
            t = p.str("keyToken", "");
            if (t.length() < 32) {
                byte[] b = new byte[16];
                new java.security.SecureRandom().nextBytes(b);
                StringBuilder h = new StringBuilder();
                for (byte x : b) h.append(String.format(java.util.Locale.US, "%02x", x & 0xFF));
                t = h.toString();
                p.put("keyToken", t);
            }
            token = t;
            return t;
        }
    }

    /** Whether the token on an intent is this install's. Kept apart from Android for the tests. */
    static boolean tokenOk(String want, String got) {
        if (want == null || got == null || want.length() < 32) return false;
        return java.security.MessageDigest.isEqual(want.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                got.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** The intent that hands a key action to the dashboard, with the token. */
    static Intent intent(Context c, String action) {
        return new Intent(c, HomeActivity.class).setAction(ACTION).putExtra("action", action).putExtra(TOKEN, token(c));
    }

    /** The main thread's handler, made when first needed (so the press logic can be tested off Android). */
    private static final class Main {
        static final Handler H = new Handler(Looper.getMainLooper());
    }
    private static Context app;
    private static Prefs prefs;

    // learning
    interface Learnt {
        void learnt(String signature, String label);
    }

    private static Learnt learning;
    private static long learnFrom;
    private static final List<String> recentXt = new ArrayList<>();
    private static final List<Long> recentXtAt = new ArrayList<>();

    static void init(Context c) {
        app = c.getApplicationContext();
        prefs = new Prefs(app);
    }

    /** Learnt buttons as {signature, label, action}. */
    static List<String[]> map(Prefs p) {
        List<String[]> out = new ArrayList<>();
        for (String line : p.str("keymap", "").split("\n")) {
            String[] f = line.split("\t");
            if (f.length == 3) out.add(f);
        }
        return out;
    }

    static void put(Prefs p, String sig, String label, String action) {
        StringBuilder sb = new StringBuilder();
        for (String[] f : map(p)) if (!f[0].equals(sig)) sb.append(f[0]).append('\t').append(f[1]).append('\t').append(f[2]).append('\n');
        if (action != null) sb.append(sig).append('\t').append(label.replace('\t', ' ')).append('\t').append(action).append('\n');
        p.put("keymap", sb.toString());
    }

    /** What a long press of a learnt button does, or null (then a long press does the short action once). */
    static String longAction(Prefs p, String sig) {
        for (String line : p.str("keymapLong", "").split("\n")) {
            String[] f = line.split("\t");
            if (f.length == 2 && f[0].equals(sig)) return f[1];
        }
        return null;
    }

    static void putLong(Prefs p, String sig, String action) {
        StringBuilder sb = new StringBuilder();
        for (String line : p.str("keymapLong", "").split("\n")) {
            String[] f = line.split("\t");
            if (f.length == 2 && !f[0].equals(sig)) sb.append(line).append('\n');
        }
        if (action != null) sb.append(sig).append('\t').append(action).append('\n');
        p.put("keymapLong", sb.toString());
    }

    /** How long a button must be held to count as a long press. */
    static final long LONG_MS = 700;

    /**
     * Short or long press, from the key's down time and whether the long action already ran.
     * Returns "long" once the hold passes LONG_MS (fire it then, while the button is still held),
     * "short" on release before that, or null for nothing to do yet.
     */
    static String press(boolean down, boolean firstDown, long heldMs, boolean longDone, boolean hasLong) {
        if (down) return hasLong && !firstDown && !longDone && heldMs >= LONG_MS ? "long" : null;
        if (longDone) return null;
        return "short";
    }

    private static final java.util.Map<String, Long> downAt = new java.util.HashMap<>();
    private static final java.util.Set<String> longDone = new java.util.HashSet<>();

    static String actionName(String a) {
        for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(a)) return ACTION_NAMES[i];
        return a;
    }

    static void learn(Learnt l) {
        learning = l;
        learnFrom = System.currentTimeMillis();
    }

    static void stopLearning() {
        learning = null;
    }

    /** A key press from the dashboard or the accessibility service; true if it was used. */
    static boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_UNKNOWN && e.getScanCode() == 0)
            return false;
        String sig = "k:" + code + (code == KeyEvent.KEYCODE_UNKNOWN ? ":" + e.getScanCode() : "");
        if (learning != null) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                final Learnt l = learning;
                learning = null;
                final String s = sig, label = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").replace('_', ' ').toLowerCase(java.util.Locale.US);
                Main.H.post(new Runnable() {
                    @Override
                    public void run() {
                        l.learnt(s, "button " + label);
                    }
                });
            }
            return true;
        }
        if (prefs == null || !Features.on(prefs, "keys")) return false;
        for (String[] f : map(prefs)) {
            if (f[0].equals(sig)) {
                String lng = Features.on(prefs, "longKeys") ? longAction(prefs, sig) : null;
                boolean down = e.getAction() == KeyEvent.ACTION_DOWN, first = down && e.getRepeatCount() == 0;
                if (first) {
                    downAt.put(sig, e.getEventTime());
                    longDone.remove(sig);
                }
                Long at = downAt.get(sig);
                long held = at == null ? 0 : e.getEventTime() - at;
                String what = press(down, first, held, longDone.contains(sig), lng != null);
                if ("long".equals(what)) {
                    longDone.add(sig);
                    perform(lng);
                } else if ("short".equals(what)) {
                    perform(f[2]);
                }
                if (!down) {
                    downAt.remove(sig);
                    longDone.remove(sig);
                }
                return true;
            }
        }
        return false;
    }

    /** Everything Topway's radio service says; a learnt button message triggers its action. */
    static void heardXt(String line) {
        long now = System.currentTimeMillis();
        String sig = "x:" + line;
        synchronized (recentXt) {
            if (learning != null && now - learnFrom > 300 && !recentXt.contains(sig)) {
                final Learnt l = learning;
                learning = null;
                final String s = sig;
                Main.H.post(new Runnable() {
                    @Override
                    public void run() {
                        l.learnt(s, "radio-service message");
                    }
                });
                return;
            }
            // remember what is said all the time, so learning ignores it
            recentXt.add(sig);
            recentXtAt.add(now);
            while (!recentXtAt.isEmpty() && now - recentXtAt.get(0) > 5000) {
                recentXt.remove(0);
                recentXtAt.remove(0);
            }
        }
        if (prefs == null || !Features.on(prefs, "keys")) return;
        for (String[] f : map(prefs)) if (f[0].equals(sig)) perform(f[2]);
    }

    /** Hand the action to the dashboard, which knows what's playing. */
    static void perform(final String action) {
        if (app == null) return;
        android.media.AudioManager am = (android.media.AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if ("clip".equals(action) || "flood".equals(action)) {
            Main.H.post(new Runnable() {
                @Override
                public void run() {
                    if ("flood".equals(action)) Monsoon.mark(app, prefs);
                    else {
                        Dashcam.protect();
                        android.widget.Toast.makeText(app, Hindi.t("Dashcam clip saved"), android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return;
        }
        if ("volUp".equals(action) || "volDown".equals(action)) {
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                    "volUp".equals(action) ? android.media.AudioManager.ADJUST_RAISE : android.media.AudioManager.ADJUST_LOWER,
                    android.media.AudioManager.FLAG_SHOW_UI);
            return;
        }
        if ("mute".equals(action)) {
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_TOGGLE_MUTE,
                    android.media.AudioManager.FLAG_SHOW_UI);
            return;
        }
        Intent i = intent(app, action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        boolean showsScreen = "dash".equals(action) || "voice".equals(action) || "screen".equals(action) || "page".equals(action)
                || "driver".equals(action) || "docs".equals(action) || "kids".equals(action) || "fog".equals(action)
                || "share".equals(action) || "split".equals(action) || "parkTimer".equals(action) || "hotspot".equals(action);
        if (showsScreen || HomeActivity.visible) app.startActivity(i);
        else Compat.send(app, new Intent(ACTION).putExtra("action", action).putExtra(TOKEN, token(app)));
    }

    private Keys() {
    }
}
