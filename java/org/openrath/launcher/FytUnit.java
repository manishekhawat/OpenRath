package org.openrath.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

/**
 * The FYT (SYU) add-on: FM, Bluetooth calls, reverse gear and the headlight wire through the
 * stereo's "toolkit" service in com.syu.ms, the way the stock radio and phone apps use it. The
 * service hands out one binder per module (main 0, radio 1, Bluetooth 2); each takes commands, and
 * pushes updates to a callback registered per update code. Codes from the stock apps' own constant
 * classes (FinalMain, FinalRadio, FinalBt), as read by the open-source fyt-dialer and
 * honda-customizer projects. Anything the service doesn't answer falls back to plain Android.
 */
final class FytUnit extends Unit {

    static final String PKG = "com.syu.ms";
    private static final String TOOLKIT = "com.syu.ipc.IRemoteToolkit", MODULE = "com.syu.ipc.IRemoteModule",
            CALLBACK = "com.syu.ipc.IModuleCallback";
    static final int MAIN = 0, RADIO = 1, BT = 2;
    // main module
    static final int U_APP_ID = 0, U_LAMPLET = 4, U_BACKCAR = 12, C_APP_ID = 0, APP_RADIO = 1;
    // radio module
    static final int U_BAND = 0, U_FREQ = 1, U_PS_TEXT = 26, C_SEEK_UP = 5, C_SEEK_DOWN = 6, C_FREQ = 13, FREQ_DIRECT = 3;
    // Bluetooth module
    static final int U_PHONE_NUMBER = 8, U_PHONE_STATE = 9, C_DIAL = 7, C_PICKUP = 9, C_HANG = 10, C_REJECT = 11;
    // phone states
    static final int PH_CONNECTED = 2, PH_DIALING = 3, PH_RINGING = 4, PH_TALKING = 5, PH_PAIRING = 6;

    private final Hub h;
    private final Handler main = new Handler(Looper.getMainLooper());
    private IBinder mainM, radioM, btM;
    private boolean bound;
    private volatile int appId = -1, band = -1, freq, phone = -1, backcar = -1, lamplet = -1;
    private volatile String ps = "", number = "";
    private long callStart;
    private final java.util.List<Binder> callbacks = new java.util.ArrayList<>();

    FytUnit(Context c, Hub hub) {
        super(c);
        h = hub;
    }

    @Override
    String name() {
        return "FYT";
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, final IBinder toolkit) {
            Bg.run(new Runnable() {
                @Override
                public void run() {
                    mainM = module(toolkit, MAIN);
                    radioM = module(toolkit, RADIO);
                    btM = module(toolkit, BT);
                    listen(mainM, MAIN, U_APP_ID, U_LAMPLET, U_BACKCAR);
                    listen(radioM, RADIO, U_BAND, U_FREQ, U_PS_TEXT);
                    listen(btM, BT, U_PHONE_NUMBER, U_PHONE_STATE);
                    changed();
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            mainM = radioM = btM = null;
            changed();
        }
    };

    @Override
    void start() {
        try {
            Intent i = new Intent("com.syu.ms.toolkit").setPackage(PKG);
            bound = ctx.bindService(i, conn, Context.BIND_AUTO_CREATE);
            if (!bound) bound = ctx.bindService(new Intent().setComponent(new ComponentName(PKG, "app.ToolkitService")), conn, Context.BIND_AUTO_CREATE);
        } catch (Throwable e) {
            bound = false;
        }
    }

    @Override
    void stop() {
        if (bound) try {
            ctx.unbindService(conn);
        } catch (Throwable ignored) {
            // not bound
        }
        bound = false;
        mainM = radioM = btM = null;
    }

    private void changed() {
        main.post(new Runnable() {
            @Override
            public void run() {
                h.notifyIfChanged(true);
            }
        });
    }

    // ------------------------------------------------------------------ the toolkit's binder calls

    private static IBinder module(IBinder toolkit, int id) {
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(TOOLKIT);
            d.writeInt(id);
            toolkit.transact(1, d, r, 0);
            r.readException();
            return r.readStrongBinder();
        } catch (Throwable e) {
            return null;
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    /** A command to a module: cmd(code, ints, floats, strings), one way. */
    private static boolean cmd(IBinder m, int code, int[] ints, String[] strs) {
        if (m == null) return false;
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(MODULE);
            d.writeInt(code);
            d.writeIntArray(ints);
            d.writeFloatArray(null);
            d.writeStringArray(strs);
            m.transact(1, d, null, IBinder.FLAG_ONEWAY);
            return true;
        } catch (Throwable e) {
            return false;
        } finally {
            d.recycle();
        }
    }

    /** Updates the module pushes to us; notify=1 sends the current value straight away. */
    private void listen(IBinder m, int mod, int... codes) {
        if (m == null) return;
        Binder callback = new Callback(mod);
        callbacks.add(callback);   // kept, so the service's link to it stays alive
        for (int code : codes) {
            Parcel d = Parcel.obtain(), r = Parcel.obtain();
            try {
                d.writeInterfaceToken(MODULE);
                d.writeStrongBinder(callback);
                d.writeInt(code);
                d.writeInt(1);
                m.transact(3, d, r, 0);
                r.readException();
            } catch (Throwable ignored) {
                // not on this unit
            } finally {
                d.recycle();
                r.recycle();
            }
        }
    }

    /** A module's updates (the same code means different things in different modules). */
    private final class Callback extends Binder {
        final int mod;

        Callback(int m) {
            mod = m;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(CALLBACK);
                return true;
            }
            if (code != 1) return false;
            try {
                data.enforceInterface(CALLBACK);
                int u = data.readInt();
                int[] ints = data.createIntArray();
                data.createFloatArray();
                String[] strs = data.createStringArray();
                update(mod, u, ints, strs);
                if (reply != null) reply.writeNoException();
            } catch (Throwable ignored) {
                // a message this unit sends differently
            }
            return true;
        }
    }

    private int i0(int[] a) {
        return a != null && a.length > 0 ? a[0] : -1;
    }

    private String s0(String[] a) {
        return a != null && a.length > 0 && a[0] != null ? a[0].trim() : "";
    }

    private void update(int mod, int u, int[] ints, String[] strs) {
        int v = i0(ints);
        if (mod == MAIN) {
            if (u == U_APP_ID) appId = v;
            else if (u == U_BACKCAR) backcar = v;
            else if (u == U_LAMPLET) lamplet = v;
        } else if (mod == RADIO) {
            if (u == U_BAND) band = v;
            else if (u == U_FREQ && v > 0) freq = v;
            else if (u == U_PS_TEXT) ps = s0(strs);
        } else if (mod == BT) {
            if (u == U_PHONE_STATE && v >= 0 && v <= 9) {
                if ((v == PH_DIALING || v == PH_RINGING) && phone != v) callStart = System.currentTimeMillis();
                phone = v;
            } else if (u == U_PHONE_NUMBER) number = s0(strs);
        }
        changed();
    }

    @Override
    String stamp() {
        return appId + "/" + band + "/" + freq + ps + "/" + phone + number + "/" + backcar + lamplet;
    }

    @Override
    Boolean reverse() {
        return mainM == null || backcar < 0 ? null : backcar != 0;
    }

    @Override
    Boolean lights() {
        return mainM == null || lamplet < 0 ? null : lamplet != 0;
    }

    // ------------------------------------------------------------------ phone

    @Override
    void tick(Prefs p, Hub hub) {
        if (btM == null || phone < 0) {
            super.tick(p, hub);
            return;
        }
        int st = phone;
        hub.btConnected = st >= PH_CONNECTED && st != PH_PAIRING || Hub.androidBt();
        hub.phoneStatus = st == PH_DIALING || st == PH_RINGING || st == PH_TALKING ? st : 0;
        if (hub.phoneStatus != 0 && !number.isEmpty())
            Calls.note(p, number, "", st == PH_DIALING ? "out" : st == PH_RINGING ? "in" : "", callStart > 0 ? callStart : System.currentTimeMillis());
        hub.lastCall = Calls.last(ctx, p);
    }

    @Override
    String call(Context c, String n) {
        String num = n == null ? "" : n.replaceAll("[^0-9+*#]", "");
        if (btM == null || num.isEmpty()) return super.call(c, n);
        if (phone >= 0 && phone < PH_CONNECTED) return Hindi.t("no phone connected");
        return cmd(btM, C_DIAL, null, new String[]{num}) ? null : Hindi.t("the stereo's phone service didn't answer");
    }

    @Override
    void answer(Context c) {
        if (!cmd(btM, C_PICKUP, null, null)) super.answer(c);
    }

    @Override
    void hangUp(Context c) {
        if (!cmd(btM, phone == PH_RINGING ? C_REJECT : C_HANG, null, null)) super.hangUp(c);
    }

    @Override
    boolean dialsItself(Context c) {
        return btM != null || super.dialsItself(c);
    }

    @Override
    int callState() {
        return callState(btM != null, phone);
    }

    /** The call screen's state from the Bluetooth module's phone state (-1 before it has said). */
    static int callState(boolean linked, int phone) {
        if (!linked || phone < 0) return CALL_UNKNOWN;
        if (phone == PH_RINGING) return CALL_RINGING;
        return phone == PH_DIALING || phone == PH_TALKING ? CALL_ON : CALL_NONE;
    }

    @Override
    String caller() {
        return phone == PH_DIALING || phone == PH_RINGING || phone == PH_TALKING ? number : "";
    }

    // ------------------------------------------------------------------ FM

    @Override
    boolean fm(Prefs p) {
        return radioM != null;
    }

    @Override
    boolean fmFirst(Prefs p) {
        return radioM != null;
    }

    @Override
    String fmPlay(Context c, Prefs p, boolean on) {
        if (mainM == null) return Hindi.t("the stereo's radio service isn't connected");
        // the MCU's sound source: 1 is the radio, 0 lets it go
        boolean ok = cmd(mainM, C_APP_ID, new int[]{on ? APP_RADIO : 0}, null);
        if (ok) appId = on ? APP_RADIO : 0;
        return ok ? null : Hindi.t("the stereo's radio service didn't answer");
    }

    @Override
    void fmTune(Prefs p, String f) {
        try {
            cmd(radioM, C_FREQ, new int[]{FREQ_DIRECT, Math.round(Float.parseFloat(f) * 100)}, null);
        } catch (NumberFormatException ignored) {
            // not a number: leave the tuner
        }
    }

    @Override
    boolean fmSeek(Prefs p, boolean up) {
        return cmd(radioM, up ? C_SEEK_UP : C_SEEK_DOWN, null, null);
    }

    @Override
    void fmQuiet() {
        if (appId == APP_RADIO) fmPlay(ctx, null, false);
    }

    @Override
    Show fmShow(Prefs p, String note, boolean localOn) {
        Show s = new Show();
        boolean am = band >= 0 && band < 65536;
        String f = am ? (freq > 0 ? freq + "  AM" : "") : mhzText(freq);
        if (!am && !f.isEmpty()) {
            if (!f.equals(p.fmFreq())) p.setFmFreq(f);
            f += "  FM";
        }
        s.title = f.isEmpty() ? Hindi.t("FM radio") : f;
        s.playing = appId < 0 ? null : appId == APP_RADIO;
        boolean on = s.playing != null ? s.playing : localOn;
        s.sub = !note.isEmpty() && on ? note : !ps.isEmpty() ? ps : on ? Hindi.t("FM radio") : Hindi.t("Tap play to listen");
        return s;
    }

    @Override
    boolean fmPlaying() {
        return appId == APP_RADIO;
    }
}
