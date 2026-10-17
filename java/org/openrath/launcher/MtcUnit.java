package org.openrath.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;

/**
 * The MTCD / MTCE add-on (Microntek units, sold as Joying, Pumpkin, Xtrons, Dasaita and others): FM
 * through the system's CarManager (the radio keys the open-source MTCD radio apps use), and
 * Bluetooth calls through the stereo's Bluetooth service in android.microntek.mtcser (as the
 * open-source MtcDialer does). Reverse gear and the headlight wire aren't known on these units yet;
 * those stay with OpenRath's own signal setup.
 */
final class MtcUnit extends Unit {

    private static final String BT_PKG = "android.microntek.mtcser", BT_IFACE = "android.microntek.mtcser.BTServiceInf";
    // BTServiceInf transaction codes
    private static final int T_INIT = 1, T_DIAL_OUT_NUM = 4, T_CALL_IN_NUM = 5, T_DEV_NAME = 12,
            T_ANSWER = 19, T_HANGUP = 20, T_REJECT = 21, T_DIAL = 54;

    private final Hub h;
    private final Handler main = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message m) {
            if ("Radio".equals(m.obj)) radio(m.getData());
        }
    };
    private Object car;               // android.microntek.CarManager, when the system has it
    private IBinder bt;
    private boolean btBound, receiving;
    private volatile String freq = "", ps = "", devName = "", callNum = "";
    private volatile long callAt;
    private String callDir = "";
    private Boolean fmOn;

    MtcUnit(Context c, Hub hub) {
        super(c);
        h = hub;
    }

    @Override
    String name() {
        return "MTCD / MTCE";
    }

    @Override
    void start() {
        try {
            car = Class.forName("android.microntek.CarManager").newInstance();
            car.getClass().getMethod("attach", Handler.class, String.class).invoke(car, main, "Radio");
        } catch (Throwable e) {
            // no CarManager on this unit: the audio manager route below
        }
        for (String cls : new String[]{BT_PKG + ".BTSerialService", BT_PKG + ".BlueToothService"}) {
            try {
                btBound = ctx.bindService(new Intent().setComponent(new ComponentName(BT_PKG, cls)), conn, Context.BIND_AUTO_CREATE);
            } catch (Throwable e) {
                btBound = false;
            }
            if (btBound) break;
        }
        IntentFilter f = new IntentFilter("com.microntek.bootcheck");
        f.addAction("com.microntek.canbusdisplay");
        try {
            Compat.fromOthers(ctx, calls, f);
            receiving = true;
        } catch (Throwable ignored) {
        }
    }

    @Override
    void stop() {
        if (car != null) try {
            car.getClass().getMethod("detach").invoke(car);
        } catch (Throwable ignored) {
        }
        car = null;
        if (btBound) try {
            ctx.unbindService(conn);
        } catch (Throwable ignored) {
        }
        btBound = false;
        bt = null;
        if (receiving) try {
            ctx.unregisterReceiver(calls);
        } catch (Throwable ignored) {
        }
        receiving = false;
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            bt = b;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    transact(T_INIT, null, false);
                }
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            bt = null;
        }
    };

    /** Calls in and out, and the radio coming on and off, as the stock apps announce them. */
    private final BroadcastReceiver calls = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if ("com.microntek.canbusdisplay".equals(i.getAction())) {
                String t = i.getStringExtra("type");
                if ("radio-on".equals(t)) fmOn = true;
                else if ("radio-off".equals(t)) fmOn = false;
            } else {
                String cls = i.getStringExtra("class");
                if (!"phonecallin".equals(cls) && !"phonecallout".equals(cls)) return;
                callAt = System.currentTimeMillis();
                callDir = "phonecallin".equals(cls) ? "in" : "out";
            }
            h.notifyIfChanged(true);
        }
    };

    // ------------------------------------------------------------------ the Bluetooth service

    /** A call on BTServiceInf: the String it returns (when `answer`), or "" ; null when it failed. */
    private String transact(int code, String arg, boolean answer) {
        IBinder b = bt;
        if (b == null) return null;
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(BT_IFACE);
            if (arg != null) d.writeString(arg);
            b.transact(code, d, r, 0);
            r.readException();
            String s = answer ? r.readString() : "";
            return s == null ? "" : s;
        } catch (Throwable e) {
            return null;
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    // ------------------------------------------------------------------ CarManager

    private boolean set(String kv) {
        try {
            if (car != null) {
                car.getClass().getMethod("setParameters", String.class).invoke(car, kv);
                return true;
            }
            // older MTCB / MTCC units take the same keys through the audio manager
            ((android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE)).setParameters(kv);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private String get(String k) {
        try {
            Object v = car != null ? car.getClass().getMethod("getParameters", String.class).invoke(car, k)
                    : ((android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE)).getParameters(k);
            return v == null ? "" : v.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    private void radio(Bundle b) {
        if (b == null) return;
        String type = b.getString("type");
        Object v = b.get("value");
        if ("freq".equals(type) && v instanceof Number) {
            String f = mhzText(((Number) v).longValue());
            if (!f.isEmpty()) freq = f;
        } else if ("psn".equals(type)) {
            ps = text(v);
        }
        h.notifyIfChanged(true);
    }

    private static String text(Object v) {
        try {
            if (v instanceof byte[]) return new String((byte[]) v, "UTF-8").replace('\0', ' ').trim();
        } catch (Exception ignored) {
        }
        return v == null ? "" : v.toString().trim();
    }

    @Override
    String stamp() {
        return freq + ps + fmOn + devName + callNum + callAt;
    }

    // ------------------------------------------------------------------ phone

    @Override
    void tick(Prefs p, Hub hub) {
        if (bt == null) {
            super.tick(p, hub);
            return;
        }
        String dev = transact(T_DEV_NAME, null, true);
        devName = dev == null ? "" : dev.trim();
        hub.btConnected = !devName.isEmpty() || Hub.androidBt();
        boolean recent = System.currentTimeMillis() - callAt < 30000;
        hub.phoneStatus = inCall() || recent ? 1 : 0;
        if (recent) {
            String n = transact("in".equals(callDir) ? T_CALL_IN_NUM : T_DIAL_OUT_NUM, null, true);
            if (n != null && !n.trim().isEmpty()) {
                callNum = n.trim();
                Calls.note(p, callNum, "", callDir, callAt);
            }
        }
        hub.lastCall = Calls.last(ctx, p);
    }

    @Override
    String call(Context c, String n) {
        String num = n == null ? "" : n.replaceAll("[^0-9+*#]", "");
        if (bt == null || num.isEmpty()) return super.call(c, n);
        if (transact(T_DIAL, num, false) == null) return "the stereo's phone service didn't answer";
        callAt = System.currentTimeMillis();
        callDir = "out";
        return null;
    }

    @Override
    void answer(Context c) {
        if (transact(T_ANSWER, null, false) == null) super.answer(c);
    }

    @Override
    void hangUp(Context c) {
        boolean ringing = "in".equals(callDir) && System.currentTimeMillis() - callAt < 30000;
        if (transact(ringing ? T_REJECT : T_HANGUP, null, false) == null) super.hangUp(c);
        callAt = 0;
    }

    @Override
    String caller() {
        return System.currentTimeMillis() - callAt < 30000 ? callNum : "";
    }

    // ------------------------------------------------------------------ FM

    @Override
    boolean fm(Prefs p) {
        return car != null;
    }

    @Override
    boolean fmFirst(Prefs p) {
        return car != null;
    }

    @Override
    String fmPlay(Context c, Prefs p, boolean on) {
        boolean ok = on ? set("av_focus_gain=fm") & set("av_channel_enter=fm")
                : set("av_focus_loss=fm") & set("av_channel_exit=fm");
        if (ok) fmOn = on;
        if (ok && on && p != null && freq.isEmpty()) fmTune(p, p.fmFreq());
        return ok ? null : "the stereo's radio didn't answer";
    }

    @Override
    void fmTune(Prefs p, String f) {
        try {
            set("ctl_radio_frequency=" + Math.round(Float.parseFloat(f) * 1000));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    boolean fmSeek(Prefs p, boolean up) {
        return set("ctl_radio_seek=" + (up ? "up" : "down"));
    }

    @Override
    void fmQuiet() {
        if (fmPlaying()) fmPlay(ctx, null, false);
    }

    @Override
    Show fmShow(Prefs p, String note, boolean localOn) {
        Show s = new Show();
        if (!freq.isEmpty() && !freq.equals(p.fmFreq())) p.setFmFreq(freq);
        s.title = (freq.isEmpty() ? p.fmFreq() : freq) + "  FM";
        s.playing = fmPlaying() ? Boolean.TRUE : fmOn;
        boolean on = s.playing != null ? s.playing : localOn;
        s.sub = !note.isEmpty() && on ? note : !ps.isEmpty() ? ps : on ? "FM radio" : "Tap play to listen";
        return s;
    }

    @Override
    boolean fmPlaying() {
        String ch = get("av_channel=");
        return ch.contains("fm") || fmOn != null && fmOn;
    }
}
