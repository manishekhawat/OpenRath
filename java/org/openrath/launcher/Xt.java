package org.openrath.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Topway's radio service: com.tw.service.xt / CommandService. The stock radio, music and Bluetooth
 * apps all talk to it through an AIDL interface and get radio news back through
 * com.tw.service.xt.aidl.IRadioCallBack. Its interface isn't published, so this loads the
 * service's own interface classes from its APK, calls them by reflection, and decodes the
 * callbacks from the method list. Whatever it finds is listed in the unit report.
 */
final class Xt {

    static final String PKG = "com.tw.service.xt";
    static final String CALLBACK = "com.tw.service.xt.aidl.IRadioCallBack";

    interface Listener {
        void radioChanged();
    }

    private final Context ctx;
    private final Listener listener;
    private ClassLoader loader;
    private Object api;                       // the service's interface, via its own Stub.asInterface
    private Class<?> iface;
    String descriptor = "", error = "";
    final List<String> methods = new ArrayList<>(), callbacks = new ArrayList<>();
    final List<String> heard = new ArrayList<>();   // last callbacks, for the report
    boolean bound, registered;

    // what the callbacks told us
    int freq;            // raw value as sent (e.g. 9110 or 91100)
    String freqText = "", name = "";
    Boolean playing;

    Xt(Context c, Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder b) {
            if (!Crash.begin("talking to the Topway radio service")) {
                error = "skipped (it crashed before)";
                return;
            }
            try {
                connect(b);
            } catch (Throwable e) {
                error = String.valueOf(e);
            }
            Crash.end();
            listener.radioChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            api = null;
            registered = false;
        }
    };

    void bind() {
        if (bound) return;
        try {
            Intent i = new Intent().setComponent(new ComponentName(PKG, PKG + ".CommandService"));
            bound = ctx.bindService(i, conn, Context.BIND_AUTO_CREATE);
            if (!bound) error = "the service refused the connection";
        } catch (Throwable e) {
            error = String.valueOf(e);
        }
    }

    void unbind() {
        if (!bound) return;
        try {
            ctx.unbindService(conn);
        } catch (Throwable ignored) {
            // not bound
        }
        bound = false;
        api = null;
    }

    boolean ready() {
        return api != null;
    }

    private void connect(IBinder b) throws Exception {
        descriptor = b.getInterfaceDescriptor();
        // its code is loaded into OpenRath: only when it came with the firmware, never an app
        // someone installed under the same name
        if (!Compat.isSystemApp(ctx.getPackageManager(), PKG)) throw new SecurityException(PKG + " is not part of the firmware");
        loader = ctx.createPackageContext(PKG, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY).getClassLoader();
        iface = Class.forName(descriptor, false, loader);
        Class<?> stub = Class.forName(descriptor + "$Stub", true, loader);
        api = stub.getMethod("asInterface", IBinder.class).invoke(null, b);
        methods.clear();
        for (Method m : iface.getDeclaredMethods()) methods.add(signature(m));
        handler.post(new Runnable() {
            @Override
            public void run() {
                poll();
            }
        });
        // register for news from every callback the service offers: radio (frequency, station),
        // and the TW command callback, which relays the car's MCU messages (lights, indicators)
        callbacks.clear();
        for (Method m : iface.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            String n = m.getName().toLowerCase(Locale.US);
            if (p.length != 1 || !p[0].isInterface() || !n.startsWith("register")) continue;   // not unRegister...
            try {
                for (Method cm : p[0].getDeclaredMethods()) callbacks.add(p[0].getSimpleName() + "." + signature(cm));
                m.invoke(api, callbackObject(p[0]));
                registered = true;
            } catch (Throwable e) {
                callbacks.add(m.getName() + " failed: " + e);
            }
        }
    }

    static String signature(Method m) {
        StringBuilder s = new StringBuilder(m.getName()).append("(");
        Class<?>[] p = m.getParameterTypes();
        for (int i = 0; i < p.length; i++) s.append(i > 0 ? "," : "").append(p[i].getSimpleName());
        return s.append(")").toString();
    }

    // ------------------------------------------------------------------ the callback, decoded by hand

    /** A binder that answers as IRadioCallBack, wrapped in a proxy object of that interface. */
    private Object callbackObject(final Class<?> cb) throws Exception {
        final Map<Integer, Method> byCode = new HashMap<>();
        try {
            Class<?> stub = Class.forName(cb.getName() + "$Stub", false, loader);
            for (Field f : stub.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || !f.getName().startsWith("TRANSACTION_")) continue;
                f.setAccessible(true);
                String name = f.getName().substring("TRANSACTION_".length());
                for (Method m : cb.getDeclaredMethods()) if (m.getName().equals(name)) byCode.put(f.getInt(null), m);
            }
        } catch (Throwable ignored) {
            // not on this unit
        }
        final Binder binder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) {
                    reply.writeString(cb.getName());
                    return true;
                }
                Method m = byCode.get(code);
                List<Object> args = new ArrayList<>();
                try {
                    data.enforceInterface(cb.getName());
                    if (m != null) for (Class<?> t : m.getParameterTypes()) args.add(read(data, t));
                } catch (Throwable ignored) {
                    // a call this unit sends differently
                }
                final String name = cb.getSimpleName() + "." + (m == null ? "#" + code : m.getName());
                final List<Object> a = args;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        heard(name, a);
                    }
                });
                if (reply != null) reply.writeNoException();
                return true;
            }
        };
        binder.attachInterface(null, cb.getName());
        return Proxy.newProxyInstance(loader, new Class<?>[]{cb}, new InvocationHandler() {
            @Override
            public Object invoke(Object p, Method m, Object[] a) {
                if (m.getName().equals("asBinder")) return binder;
                if (m.getName().equals("hashCode")) return binder.hashCode();
                if (m.getName().equals("equals")) return a != null && a[0] == p;
                if (m.getName().equals("toString")) return "OpenRathRadioCallBack";
                return null;
            }
        });
    }

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    private static Object read(Parcel d, Class<?> t) {
        if (t == int.class) return d.readInt();
        if (t == long.class) return d.readLong();
        if (t == boolean.class) return d.readInt() != 0;
        if (t == float.class) return d.readFloat();
        if (t == double.class) return d.readDouble();
        if (t == String.class) return d.readString();
        if (t == byte.class) return d.readByte();
        if (t == int[].class) return d.createIntArray();
        if (t == byte[].class) return d.createByteArray();
        if (t == android.os.Bundle.class) return d.readInt() != 0 ? android.os.Bundle.CREATOR.createFromParcel(d) : null;
        return null;   // anything else: stop decoding this call
    }

    private void heard(String method, List<Object> args) {
        String line = method + args;
        Keys.heardXt(line);   // a steering-wheel button may arrive this way
        heard.add(line);
        if (heard.size() > 30) heard.remove(0);
        // everything heard is also a possible headlight / indicator signal for the setups
        synchronized (Lights.live) {
            String base = "xt:" + method.replaceAll("\\(.*", "");
            boolean twCmd = method.toLowerCase(Locale.US).contains("command") && args.size() >= 2 && args.get(0) instanceof Integer;
            if (twCmd) base += ":" + args.get(0) + (args.get(1) instanceof Integer && args.size() > 2 ? ":" + args.get(1) : "");
            for (int i = twCmd ? 1 : 0; i < args.size(); i++) {
                Object o = args.get(i);
                if (o instanceof android.os.Bundle) {
                    android.os.Bundle bd = (android.os.Bundle) o;
                    try {
                        for (String k : bd.keySet()) Lights.live.put(base + "|" + k, String.valueOf(bd.get(k)));
                    } catch (Throwable ignored) {
                        // another app's class we can't read: skip it
                    }
                } else if (o instanceof int[]) {
                    Lights.live.put(base + "#" + i, java.util.Arrays.toString((int[]) o));
                } else if (o instanceof byte[]) {
                    Lights.live.put(base + "#" + i, java.util.Arrays.toString((byte[]) o));
                } else {
                    Lights.live.put(base + "#" + i, String.valueOf(o));
                }
            }
        }
        String lm = method.toLowerCase(Locale.US);
        boolean radioInfo = false;
        for (Object o : args) {
            if (o instanceof android.os.Bundle) {
                try {
                    radioInfo |= "radioinfo".equals(((android.os.Bundle) o).getString("action")) && ((android.os.Bundle) o).containsKey("freq");
                } catch (Throwable ignored) {
                    // not readable on this unit
                }
            }
        }
        if (radioInfo) radioAt = System.currentTimeMillis();
        if (radioInfo || method.startsWith("IRadioCallBack") || lm.matches(".*(radio|fm|freq|channel|tuner).*")) decode(method, args);
        // Topway's MCU radio message relayed as a TW command: (1025, 2, frequency in 10 kHz steps)
        if (lm.contains("command") && args.size() >= 3 && Integer.valueOf(Tw.RADIO).equals(args.get(0))
                && Integer.valueOf(2).equals(args.get(1)) && args.get(2) instanceof Integer) {
            int v = (Integer) args.get(2);
            if (v >= 8750 && v <= 10800) {
                setFreq(v, v / 100f);
                listener.radioChanged();
            }
        }
        // phone events come as IBTCallBack.* on some units, ITWCommandCallbackAidl.onBt*(…) on others
        if (method.startsWith("IBTCallBack") || method.contains(".onBt")) {
            btEvent = method.substring(method.indexOf('.') + 1);
            btAt = System.currentTimeMillis();
            String ev = btEvent.toLowerCase(Locale.US);
            if (ev.contains("disconnect")) btLink = 0;
            else if (ev.contains("connect")) {
                // e.g. onConnectStatus(int) or onConnected(String): a value of 0 or false means it dropped
                btLink = 1;
                for (Object o : args) if (o instanceof Integer || o instanceof Boolean) btLink = truthy(o) ? 1 : 0;
            }
            String num = "", who = "";
            for (Object o : args) {
                if (!(o instanceof String)) continue;
                String t = ((String) o).trim();
                if (t.isEmpty() || t.length() >= 40) continue;
                btText = t;
                if (isNumber(t)) num = t;
                else if (!t.contains(":") && !t.contains("/")) who = t;   // skip MAC addresses and paths
            }
            if (!num.isEmpty()) {
                // a number in a Bluetooth event is a call: ringing, dialling or in the call log
                if (!num.equals(callNumber)) callName = "";
                callNumber = num;
                callAt = btAt;
                if (ev.contains("in") || ev.contains("ring")) callDir = "in";
                else if (ev.contains("out") || ev.contains("dial")) callDir = "out";
            }
            if (!who.isEmpty() && btAt - callAt < 5000) callName = who;
        }
    }

    private void decode(String method, List<Object> args) {
        String lm = method.toLowerCase(Locale.US);
        for (Object o : args) {
            if (o instanceof Integer) {
                int v = (Integer) o;
                // FM frequencies as the tuner sends them: 8750..10800, 87500..108000 or 875..1080
                if (lm.contains("freq") || v >= 8750 && v <= 10800 || v >= 87500 && v <= 108000) {
                    if (v >= 87500 && v <= 108000) setFreq(v, v / 1000f);
                    else if (v >= 8750 && v <= 10800) setFreq(v, v / 100f);
                    else if (v >= 875 && v <= 1080) setFreq(v, v / 10f);
                }
            } else if (o instanceof String && mhz((String) o) > 0) {
                float f = mhz((String) o);   // some services send the frequency as text, "91.10" or "91.1 MHz"
                setFreq(Math.round(f * 100), f);
            } else if (o instanceof String && (lm.contains("name") || lm.contains("ps") || lm.contains("rds"))) {
                name = ((String) o).trim();
            } else if (o instanceof int[]) {
                for (int v : (int[]) o) {
                    if (v >= 87500 && v <= 108000) setFreq(v, v / 1000f);
                    else if (v >= 8750 && v <= 10800) setFreq(v, v / 100f);
                    else continue;
                    break;
                }
            } else if (o instanceof android.os.Bundle) {
                android.os.Bundle bd = (android.os.Bundle) o;
                try {
                    for (String k : bd.keySet()) {
                        if (!k.toLowerCase(Locale.US).contains("freq")) continue;
                        Object v = bd.get(k);
                        float f = v instanceof Integer ? fmMhz((Integer) v) : v == null ? 0 : textMhz(String.valueOf(v));
                        if (f > 0) setFreq(Math.round(f * 100), f);
                    }
                } catch (Throwable ignored) {
                    // not readable on this unit
                }
            } else if (o instanceof Boolean && (lm.contains("play") || lm.contains("state") || lm.contains("mute"))) {
                playing = lm.contains("mute") ? !(Boolean) o : (Boolean) o;
            }
        }
        listener.radioChanged();
    }

    private void setFreq(int raw, float mhz) {
        freq = raw;
        freqText = String.format(Locale.US, "%.1f", mhz);
        freqAt = System.currentTimeMillis();
    }

    /** An FM frequency as a tuner number: 8750..10800 (10 kHz steps), 87500..108000 (kHz) or 875..1080; 0 if not one. */
    static float fmMhz(int v) {
        if (v >= 87500 && v <= 108000) return v / 1000f;
        if (v >= 8750 && v <= 10800) return v / 100f;
        if (v >= 875 && v <= 1080) return v / 10f;
        return 0;
    }

    /** A frequency sent as text, either "98.3"-style MHz or a tuner number such as "9830" (the stock radio's radioinfo). */
    static float textMhz(String s) {
        float f = mhz(s);
        if (f > 0) return f;
        String t = s.trim();
        if (!t.matches("\\d{3,6}")) return 0;
        return fmMhz(Integer.parseInt(t));
    }

    /**
     * Ask the stock radio app where it is tuned. It answers through the service's extended interface
     * with a Bundle {action: "radioinfo", dateType: "send", freq: "9830"}, which reaches us as
     * ITWCommandCallbackAidl.onExtendedInterface. (Found in com.tw.radio: the stock launcher's widget asks the same way.)
     */
    long askedAt;
    /** When the stock radio app last answered: it is running, with the tuner powered up. */
    long radioAt;

    boolean radioAlive() {
        return radioAt > 0 && System.currentTimeMillis() - radioAt < 60000;
    }

    void askRadio() {
        if (api == null) return;
        askedAt = System.currentTimeMillis();
        try {
            android.os.Bundle b = new android.os.Bundle();
            b.putString("project", "radio");
            b.putString("type", "radioinfo");
            b.putString("dateType", "get");
            for (Method m : iface.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == android.os.Bundle.class && m.getName().toLowerCase(Locale.US).startsWith("extended")) {
                    m.invoke(api, b);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // not on this unit
        }
    }

    /** An FM frequency written as text ("91.10", "91.1 MHz", "91,1"), in MHz; 0 when it isn't one. */
    static float mhz(String s) {
        String t = s.trim().toLowerCase(Locale.US).replace(',', '.').replace("mhz", "").replace("fm", "").trim();
        if (!t.matches("\\d{2,3}\\.\\d{1,2}")) return 0;
        float f = Float.parseFloat(t);
        return f >= 87.5f && f <= 108f ? f : 0;
    }

    long freqAt, seekAt;

    /**
     * After next or previous: ask the service again over the next few seconds, since many units only
     * report the new station when asked, and redraw the box when the seek is over.
     */
    private void seeking() {
        seekAt = System.currentTimeMillis();
        name = "";   // the old station's name would be wrong now
        for (int d : new int[]{500, 1200, 2500, 4000, 4700}) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    poll();
                    listener.radioChanged();
                }
            }, d);
        }
    }

    /**
     * The frequency for the box: the last one the radio reported, else `saved` (the last one the app knew).
     */
    String display(String saved) {
        // no "Seeking…" swap: the old number stays until the radio says the new one (about half a second)
        return freqText.isEmpty() ? saved : freqText;
    }

    // ------------------------------------------------------------------ controls

    /**
     * Call the first no-argument method whose name matches, e.g. "next" finds radioNext() or
     * seekUp(). Only names that clearly mean the action are tried. Returns the method used, or null.
     */
    String call(String... patterns) {
        if (api == null) return null;
        for (String pat : patterns) {
            for (Method m : iface.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (!m.getName().toLowerCase(Locale.US).matches(pat)) continue;
                try {
                    m.invoke(api);
                    return m.getName();
                } catch (Throwable ignored) {
                    // not on this unit: try the next
                }
            }
        }
        return null;
    }

    String next() {
        String done = null;
        for (String c : new String[]{"radioNext", "radioSearchNext"}) if (has(c) && run(c) == null) { done = c; break; }
        if (done == null) done = call(".*radio.*next.*", ".*(seek|search|scan)_?up.*");
        if (done != null) seeking();
        return done;
    }

    String prev() {
        String done = null;
        for (String c : new String[]{"radioPre", "radioSearchPre"}) if (has(c) && run(c) == null) { done = c; break; }
        if (done == null) done = call(".*radio.*(prev|pre|last).*", ".*(seek|search|scan)_?down.*");
        if (done != null) seeking();
        return done;
    }

    /**
     * Run one command written as "name", "name:s:text", "name:i:number" or "name:b:true".
     * Returns null on success, otherwise why it failed.
     */
    String run(String cmd) {
        if (api == null) return "radio service not connected";
        String[] c = cmd.split(":", 3);
        for (Method m : iface.getDeclaredMethods()) {
            if (!m.getName().equals(c[0])) continue;
            Class<?>[] p = m.getParameterTypes();
            try {
                if (c.length < 3 && p.length == 0) m.invoke(api);
                else if (c.length == 3 && p.length == 1 && "s".equals(c[1]) && p[0] == String.class) m.invoke(api, c[2]);
                else if (c.length == 3 && p.length == 1 && "i".equals(c[1]) && p[0] == int.class) m.invoke(api, Integer.parseInt(c[2]));
                else if (c.length == 3 && p.length == 1 && "b".equals(c[1]) && p[0] == boolean.class) m.invoke(api, Boolean.parseBoolean(c[2]));
                else continue;
                return null;
            } catch (Throwable e) {
                Throwable t = e.getCause() != null ? e.getCause() : e;
                return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
            }
        }
        return "no " + c[0] + " on this unit";
    }

    /** The result of a no-argument getter such as getReverseStatus(), or null. */
    Object value(String name) {
        if (api == null) return null;
        for (Method m : iface.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != 0) continue;
            try {
                return m.invoke(api);
            } catch (Throwable e) {
                return null;
            }
        }
        return null;
    }

    /**
     * The service's own light and night state: every no-argument getter about lights, night or
     * illumination (lightValue(), isLightFromMCU(), nightMode…), as name → value. The service sets
     * the screen's night brightness from the MCU's illumination wire, so these follow the headlights.
     */
    java.util.Map<String, String> lightGetters() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String sig : methods) {
            if (!sig.endsWith("()")) continue;
            String n = sig.substring(0, sig.length() - 2), l = n.toLowerCase(Locale.US);
            if (!(l.contains("light") || l.contains("night") || l.contains("illum") || l.contains("daynight") || l.contains("brightness"))) continue;
            // strictly getters: on Topway units lightUp/Down/Max/Min() and lightValue(int) change the screen
            if (!(l.startsWith("get") || l.startsWith("is"))) continue;
            Object v = value(n);
            if (v != null) m.put(n, String.valueOf(v));
        }
        return m;
    }

    /** A getter's answer as on/off: true, a number other than 0, or text such as "1" or "connected". */
    boolean on(String name) {
        return truthy(value(name));
    }

    static boolean truthy(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        if (v instanceof String) {
            String s = ((String) v).trim().toLowerCase(Locale.US);
            return !s.isEmpty() && !s.equals("0") && !s.equals("false") && !s.contains("disconnect") && !s.equals("null");
        }
        return false;
    }

    /** The last connect / disconnect the Bluetooth callback reported: 1 connected, 0 disconnected, -1 none yet. */
    int btLink = -1;

    // what the Bluetooth callback said last: a caller's name or number, and when
    String btText = "", btEvent = "";
    long btAt;

    // the last call seen in a Bluetooth event: number, name (when sent), "in"/"out"/"" and when
    String callNumber = "", callName = "", callDir = "";
    long callAt;

    /** A phone number as the Bluetooth service sends it: digits with an optional + and separators. */
    static boolean isNumber(String t) {
        String d = t.replaceAll("[\\s()-]", "");
        return d.matches("\\+?[0-9*#]{3,16}");
    }

    /** Caller number or name from a getter the unit may have, e.g. btGetCallNumber(); empty if none. */
    String[] caller() {
        String num = "", name = "";
        for (String m : methods) {
            String n = m.replaceAll("\\(.*", "");
            if (!m.endsWith("()")) continue;
            String l = n.toLowerCase(Locale.US);
            if (!l.startsWith("btget") || !(l.contains("number") || l.contains("name") || l.contains("call"))) continue;
            if (l.contains("device") || l.contains("local") || l.contains("pin") || l.contains("status")) continue;
            Object v = value(n);
            if (!(v instanceof String) || ((String) v).trim().isEmpty()) continue;
            String t = ((String) v).trim();
            if (isNumber(t)) num = t;
            else if (l.contains("name")) name = t;
        }
        return new String[]{num, name};
    }

    boolean has(String name) {
        for (String m : methods) if (m.startsWith(name + "(")) return true;
        return false;
    }

    String play(boolean on) {
        String used = on ? call(".*radio.*(play|open|start|on|resume|enter)\\b.*", ".*(open|start|enter|play|resume).*radio.*",
                ".*(fm|tuner).*(play|open|start|on).*", ".*(open|start|play).*(fm|tuner).*")
                : call(".*radio.*(pause|close|stop|off|exit)\\b.*", ".*(close|stop|exit|pause).*radio.*",
                ".*(fm|tuner).*(pause|close|stop|off).*", ".*(close|stop|pause).*(fm|tuner).*");
        if (used == null) used = call1(on, ".*radio.*(power|enable|switch|state|on|play|mute|source|mode).*",
                ".*(power|enable|switch|play|mute|set).*(radio|fm|tuner).*");
        return used;
    }

    /** Like call(), for methods taking one boolean or int: true/1 for on (inverted for "mute"). */
    String call1(boolean on, String... patterns) {
        if (api == null) return null;
        for (String pat : patterns) {
            for (Method m : iface.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 1 || p[0] != boolean.class && p[0] != int.class) continue;
                String n = m.getName().toLowerCase(Locale.US);
                if (!n.matches(pat) || n.matches(".*(get|is|has|register|unregister|callback).*")) continue;
                boolean v = n.contains("mute") ? !on : on;
                try {
                    m.invoke(api, p[0] == boolean.class ? (Object) v : (Object) (v ? 1 : 0));
                    return m.getName() + "(" + v + ")";
                } catch (Throwable ignored) {
                    // not on this unit: try the next
                }
            }
        }
        return null;
    }

    /** Ask the service for the current radio state with any getter that sounds like it. */
    void poll() {
        if (api == null) return;
        askRadio();
        for (Method m : iface.getDeclaredMethods()) {
            if (m.getParameterTypes().length != 0) continue;
            String n = m.getName().toLowerCase(Locale.US);
            if (!n.matches("(get|is|request|query|refresh|update).*(freq|radio|fm|station|band|ps|rds|state|play).*")) continue;
            Class<?> r = m.getReturnType();
            try {
                Object v = m.invoke(api);
                if (r == void.class) continue;   // a request: the answer comes back through the callback
                List<Object> a = new ArrayList<>();
                a.add(r == boolean.class || r == Boolean.class ? v : r == int.class || r == Integer.class || r == String.class ? v : null);
                if (a.get(0) != null) decode(m.getName(), a);
            } catch (Throwable ignored) {
                // not on this unit: try the next
            }
        }
    }
}
