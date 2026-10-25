package org.openrath.launcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Engine data from an ELM327 OBD-II adapter (the common plug under the dashboard), over Bluetooth or
 * Wi-Fi: RPM, speed, coolant and intake temperature, engine load, fuel level, battery voltage and
 * fault codes. A Bluetooth adapter is paired in the unit's Bluetooth settings first; a Wi-Fi one is
 * joined in the unit's Wi-Fi settings. Either is then chosen in Settings > Engine (OBD-II).
 * Runs on its own thread inside the trip service; the boxes read the static fields.
 */
final class Obd {

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // latest readings; -1 = not known
    static volatile int rpm = -1, kmh = -1, coolant = -1, load = -1, fuel = -1, intake = -999;
    /** The address most Wi-Fi ELM327 adapters use. */
    static final String WIFI_DEFAULT = "192.168.0.10:35000";
    static volatile float volts = -1;
    /** Air flow (g/s), manifold pressure (kPa), throttle (%) and fuel trims (%), read when the car answers them. */
    static volatile float maf = -1;
    static volatile int manifold = -1, throttle = -1, shortTrim = -999, longTrim = -999;
    /** Codes the engine computer has seen once but not confirmed yet (mode 07): the early warning. */
    static final List<String> pendingCodes = new ArrayList<>();
    static volatile long readAt;
    static volatile String status = Hindi.t("Not set up"), protocol = "";
    static volatile boolean connected;
    static final List<String> codes = new ArrayList<>();
    static volatile long codesAt;

    /**
     * One connection attempt loop: its own stop switch, so a stop() followed quickly by start() can't
     * let the old loop carry on, and its socket, which stop() closes so a waiting read ends at once.
     */
    private static final class Run {
        final java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        volatile java.io.Closeable socket;
        Thread thread;

        void end() {
            stop.set(true);
            java.io.Closeable s = socket;
            if (s != null) {
                try {
                    s.close();
                } catch (Exception ignored) {
                    // already closed
                }
            }
            if (thread != null) thread.interrupt();
        }
    }

    private static Run run;
    private static volatile String pending;   // "03" read codes or "04" clear codes, asked from Settings

    /** Speed from the car itself, when the adapter answered in the last 3 seconds; else -1. */
    static int speedNow() {
        return connected && kmh >= 0 && System.currentTimeMillis() - readAt < 3000 ? kmh : -1;
    }

    static void start(final Context c, final Prefs p) {
        final String mac = p.str("obdMac", "");
        mine = mine(p.str("obdMine", ""));
        if (mac.isEmpty()) {
            status = Hindi.t("Not set up");
            return;
        }
        synchronized (Obd.class) {
            if (run != null && !run.stop.get() && run.thread.isAlive()) return;
            final Run r = new Run();
            // its own thread: the connection loop runs, blocked on the adapter, for the whole drive
            r.thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    loop(c.getApplicationContext(), mac, r);
                }
            }, "obd");
            r.thread.setDaemon(true);
            run = r;
            r.thread.start();
        }
    }

    static void stop() {
        synchronized (Obd.class) {
            if (run != null) run.end();
            run = null;
        }
        connected = false;
    }

    static void readCodes() {
        pending = "03";
    }

    static void clearCodes() {
        pending = "04";
    }

    /** Paired Bluetooth devices as "name|mac", adapters with OBD-like names first. */
    static List<String> paired() {
        List<String> out = new ArrayList<>(), rest = new ArrayList<>();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return out;
            for (BluetoothDevice d : a.getBondedDevices()) {
                String n = d.getName() == null ? d.getAddress() : d.getName();
                String u = n.toUpperCase(Locale.US);
                (u.contains("OBD") || u.contains("ELM") || u.contains("V-LINK") || u.contains("VLINK") || u.contains("KONNWEI")
                        ? out : rest).add(n + "|" + d.getAddress());
            }
        } catch (Throwable ignored) {
            // no Bluetooth permission or adapter
        }
        out.addAll(rest);
        return out;
    }

    /** {host, port} for a Wi-Fi adapter saved as "wifi:host:port", or null for a Bluetooth one. */
    static String[] wifiTarget(String saved) {
        if (saved == null || !saved.startsWith("wifi:")) return null;
        String t = saved.substring(5).trim();
        int colon = t.lastIndexOf(':');
        String host = colon > 0 ? t.substring(0, colon) : t, port = colon > 0 ? t.substring(colon + 1) : "35000";
        try {
            int n = Integer.parseInt(port);
            if (n <= 0 || n > 65535 || host.isEmpty()) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        return new String[]{host, port};
    }

    /** A connection to a Wi-Fi adapter, through the Wi-Fi even when another network carries the internet. */
    private static java.net.Socket wifiSocket(Context c, String host, int port) throws Exception {
        javax.net.SocketFactory f = javax.net.SocketFactory.getDefault();
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (android.net.Network n : cm.getAllNetworks()) {
                android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                    f = n.getSocketFactory();
                    break;
                }
            }
        } catch (Throwable e) {
            Diag.note("OBD Wi-Fi network", e);
        }
        java.net.Socket s = f.createSocket();
        s.connect(new java.net.InetSocketAddress(host, port), 8000);
        return s;
    }

    private static BluetoothSocket bluetooth(String mac) throws Exception {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null || !a.isEnabled()) throw new IllegalStateException(Hindi.t("Bluetooth is off"));
        BluetoothDevice d = a.getRemoteDevice(mac);
        a.cancelDiscovery();
        BluetoothSocket s = null;
        try {
            s = d.createRfcommSocketToServiceRecord(SPP);
            s.connect();
        } catch (Exception first) {
            // many cheap adapters only answer on channel 1
            try {
                if (s != null) s.close();
            } catch (Exception ignored) {
                // closing anyway
            }
            s = (BluetoothSocket) d.getClass().getMethod("createRfcommSocket", int.class).invoke(d, 1);
            s.connect();
        }
        return s;
    }

    private static void loop(Context c, String mac, Run run) {
        long backoff = 5000;
        String[] wifi = wifiTarget(mac);
        java.util.concurrent.atomic.AtomicBoolean stop = run.stop;
        while (!stop.get()) {
            java.io.Closeable s = null;
            try {
                status = Hindi.t("Connecting…");
                changed(c);
                InputStream in;
                OutputStream out;
                java.net.Socket ws = null;
                if (wifi != null) {
                    ws = wifiSocket(c, wifi[0], Integer.parseInt(wifi[1]));
                    s = ws;
                    in = ws.getInputStream();
                    out = ws.getOutputStream();
                } else {
                    BluetoothSocket bs = bluetooth(mac);
                    s = bs;
                    in = bs.getInputStream();
                    out = bs.getOutputStream();
                }
                run.socket = s;
                if (stop.get()) break;   // stopped while connecting: the finally closes it
                for (String cmd : new String[]{"ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0"}) ask(in, out, ws, cmd, cmd.equals("ATZ") ? 3000 : 1500);
                String first = ask(in, out, ws, "0100", 10000);   // the adapter searches for the car's protocol here
                if (first.contains("UNABLE") || first.contains("ERROR") || first.contains("NODATA") || first.contains("CANTCONNECT"))
                    throw new IllegalStateException(Hindi.t("adapter can't reach the car (ignition on?)"));
                protocol = clean(ask(in, out, ws, "ATDP", 1500));
                if (stop.get()) break;
                connected = true;
                status = Hindi.t("Connected");
                backoff = 5000;
                changed(c);
                int round = 0;
                int[] misses = new int[EXTRA.length];
                java.util.Map<String, Integer> mineMisses = new java.util.HashMap<>();
                while (!stop.get()) {
                    String job = pending;
                    if (job != null) {
                        pending = null;
                        String r = ask(in, out, ws, job, 5000);
                        if ("03".equals(job)) {
                            boolean can = protocol.contains("CAN") || protocol.contains("15765");
                            List<String> found = dtcs(r, can);
                            List<String> soon = dtcs(ask(in, out, ws, "07", 5000), can, "47");
                            synchronized (codes) {
                                codes.clear();
                                codes.addAll(found);
                                pendingCodes.clear();
                                for (String k : soon) if (!found.contains(k)) pendingCodes.add(k);
                            }
                            codesAt = System.currentTimeMillis();
                        } else {
                            synchronized (codes) {
                                codes.clear();
                                pendingCodes.clear();
                            }
                            codesAt = System.currentTimeMillis();
                        }
                    }
                    kmh = speed(ask(in, out, ws, "010D", 1500));
                    rpm = rpm(ask(in, out, ws, "010C", 1500));
                    if (round % 5 == 0) {
                        coolant = coolant(ask(in, out, ws, "0105", 1500));
                        load = load(ask(in, out, ws, "0104", 1500));
                        fuel = fuel(ask(in, out, ws, "012F", 1500));
                        intake = intake(ask(in, out, ws, "010F", 1500));
                        volts = volts(ask(in, out, ws, "ATRV", 1500));
                    }
                    // the extra readings, each in turn, and dropped for this connection when the car never answers them
                    int x = round % EXTRA.length;
                    if (misses[x] < 3) {
                        String r = ask(in, out, ws, EXTRA[x], 1500);
                        float v = extra(EXTRA[x], r);
                        misses[x] = v == NONE ? misses[x] + 1 : 0;
                        if (v != NONE || misses[x] >= 3) store(EXTRA[x], v);
                    }
                    // the owner's own readings, one a round, dropped the same way
                    String[] own = mine;
                    if (own.length > 0) {
                        String id = own[round % own.length];
                        Integer m = mineMisses.get(id);
                        if (m == null || m < 3) {
                            float v = custom(id, ask(in, out, ws, id, 1500));
                            int miss = v == NONE ? (m == null ? 1 : m + 1) : 0;
                            mineMisses.put(id, miss);
                            if (v != NONE || miss >= 3) mineNow.put(id, v);
                        }
                    }
                    readAt = System.currentTimeMillis();
                    round++;
                    changed(c);
                    Thread.sleep(250);
                }
            } catch (Throwable e) {
                if (stop.get()) break;   // stop() closed the socket: not a fault to show
                connected = false;
                status = Hindi.f("Not connected: %1$s", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                changed(c);
            } finally {
                run.socket = null;
                try {
                    if (s != null) s.close();
                } catch (Exception ignored) {
                    // closing anyway
                }
            }
            if (stop.get()) break;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                return;
            }
            backoff = Math.min(backoff * 2, 60000);
        }
    }

    private static long changedAt;

    private static void changed(Context c) {
        long now = System.currentTimeMillis();
        if (now - changedAt < 900) return;   // the boxes redraw once a second anyway
        changedAt = now;
        Compat.send(c, new Intent(Drive.CHANGED));
    }

    /**
     * Send one command and read up to the '>' prompt. A Wi-Fi adapter's socket (ws) waits for the
     * answer itself, with a time limit; a Bluetooth socket has none, so it is checked every 40 ms.
     */
    private static String ask(InputStream in, OutputStream out, java.net.Socket ws, String cmd, long timeout) throws Exception {
        byte[] buf = new byte[64];
        int left;
        while ((left = in.available()) > 0) in.read(buf, 0, Math.min(left, buf.length));   // anything stale
        out.write((cmd + "\r").getBytes("US-ASCII"));
        out.flush();
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + timeout;
        while (true) {
            long wait = end - System.currentTimeMillis();
            if (wait <= 0) break;
            int n;
            if (ws != null) {
                ws.setSoTimeout((int) Math.max(1, wait));
                try {
                    n = in.read(buf);
                } catch (java.net.SocketTimeoutException timedOut) {
                    break;
                }
            } else {
                int av = in.available();
                if (av <= 0) {
                    Thread.sleep(40);
                    continue;
                }
                n = in.read(buf, 0, Math.min(av, buf.length));
            }
            if (n < 0) throw new IllegalStateException(Hindi.t("adapter disconnected"));
            int prompt = -1;
            for (int i = 0; i < n && prompt < 0; i++) if (buf[i] == '>') prompt = i;
            for (int i = 0; i < (prompt < 0 ? n : prompt); i++) sb.append((char) buf[i]);
            if (prompt >= 0) break;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ parsing (unit-tested)

    /** Upper-case, no spaces, no "SEARCHING..." or echo noise. */
    static String clean(String r) {
        return r.toUpperCase(Locale.US).replace("SEARCHING...", "").replace("BUSINIT:...OK", "").replaceAll("[\\s>]", "");
    }

    /** Data bytes after the mode+PID reply header (e.g. "410D" for 010D), or null. */
    static int[] bytes(String reply, String header) {
        String r = clean(reply);
        int i = r.indexOf(header);
        if (i < 0) return null;
        String hex = r.substring(i + header.length());
        int n = hex.length() / 2;
        int[] out = new int[n];
        try {
            for (int k = 0; k < n; k++) out[k] = Integer.parseInt(hex.substring(2 * k, 2 * k + 2), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    static int speed(String r) {
        int[] b = bytes(r, "410D");
        return b == null || b.length < 1 ? -1 : b[0];
    }

    static int rpm(String r) {
        int[] b = bytes(r, "410C");
        return b == null || b.length < 2 ? -1 : (b[0] * 256 + b[1]) / 4;
    }

    static int coolant(String r) {
        int[] b = bytes(r, "4105");
        return b == null || b.length < 1 ? -1 : b[0] - 40;
    }

    static int load(String r) {
        int[] b = bytes(r, "4104");
        return b == null || b.length < 1 ? -1 : Math.round(b[0] * 100 / 255f);
    }

    /** Fuel tank level in percent (PID 2F); many cars don't report it. */
    static int fuel(String r) {
        int[] b = bytes(r, "412F");
        return b == null || b.length < 1 ? -1 : Math.round(b[0] * 100 / 255f);
    }

    /** Intake air temperature in °C (PID 0F), or -999. */
    static int intake(String r) {
        int[] b = bytes(r, "410F");
        return b == null || b.length < 1 ? -999 : b[0] - 40;
    }

    /** The extra readings, asked one per round. */
    static final String[] EXTRA = {"0110", "010B", "0111", "0106", "0107"};
    static final float NONE = -9999;

    /** An extra reading's value, or NONE when the car didn't answer it. Pure, for the tests. */
    static float extra(String pid, String r) {
        int[] b = bytes(r, "41" + pid.substring(2));
        if (b == null || b.length < 1) return NONE;
        switch (pid) {
            case "0110": return b.length < 2 ? NONE : (b[0] * 256 + b[1]) / 100f;
            case "010B": return b[0];
            case "0111": return Math.round(b[0] * 100 / 255f);
            default: return Math.round((b[0] - 128) * 100 / 128f);   // fuel trims, 0106 and 0107
        }
    }

    private static void store(String pid, float v) {
        boolean none = v == NONE;
        switch (pid) {
            case "0110": maf = none ? -1 : v; break;
            case "010B": manifold = none ? -1 : Math.round(v); break;
            case "0111": throttle = none ? -1 : Math.round(v); break;
            case "0106": shortTrim = none ? -999 : Math.round(v); break;
            default: longTrim = none ? -999 : Math.round(v);
        }
    }

    /** Petrol used right now, in litres an hour, from the air flow (14.7 g of air a gram of petrol, 745 g a litre); -1 without it. */
    static float litresAnHour(float maf) {
        return maf < 0 ? -1 : maf * 3600f / (14.7f * 745f);
    }

    // ------------------------------------------------------------------ the owner's own readings

    /**
     * Common mode 01 readings from the SAE J1979 standard, for the owner to add to the live readings:
     * {PID, name, unit, formula}. A and B are the first two data bytes of the answer; the formulas:
     * "pct" A*100/255, "temp" A-40, "trim" (A-128)*100/128, "a" A, "a3" 3A, "ab" 256A+B, "rpm" (256A+B)/4,
     * "maf" (256A+B)/100, "adv" A/2-64, "rail" (256A+B)*0.079, "ab10" (256A+B)*10, "cat" (256A+B)/10-40,
     * "volt" (256A+B)/1000, "abpct" (256A+B)*100/255, "lambda" (256A+B)*2/65536, "o2" A/200,
     * "inj" (256A+B)/128-210, "rate" (256A+B)/20, "torque" A-125.
     */
    static final String[][] PIDS = {
            {"0104", "Engine load", "%", "pct"},
            {"0105", "Coolant temperature", "°C", "temp"},
            {"0106", "Fuel trim now (bank 1)", "%", "trim"},
            {"0107", "Fuel trim learnt (bank 1)", "%", "trim"},
            {"0108", "Fuel trim now (bank 2)", "%", "trim"},
            {"0109", "Fuel trim learnt (bank 2)", "%", "trim"},
            {"010A", "Fuel pressure", "kPa", "a3"},
            {"010B", "Manifold pressure", "kPa", "a"},
            {"010C", "Engine speed", "rpm", "rpm"},
            {"010D", "Speed", "km/h", "a"},
            {"010E", "Ignition timing", "°", "adv"},
            {"010F", "Intake air temperature", "°C", "temp"},
            {"0110", "Air flow", "g/s", "maf"},
            {"0111", "Throttle", "%", "pct"},
            {"0114", "Oxygen sensor 1 (bank 1)", "V", "o2"},
            {"0115", "Oxygen sensor 2 (bank 1)", "V", "o2"},
            {"011F", "Time since the engine started", "s", "ab"},
            {"0121", "Distance driven with the engine light on", "km", "ab"},
            {"0122", "Fuel rail pressure (to the manifold)", "kPa", "rail"},
            {"0123", "Fuel rail pressure", "kPa", "ab10"},
            {"012C", "EGR asked for", "%", "pct"},
            {"012D", "EGR error", "%", "trim"},
            {"012E", "Fuel vapour purge", "%", "pct"},
            {"012F", "Fuel tank level", "%", "pct"},
            {"0130", "Warm-ups since the codes were cleared", "", "a"},
            {"0131", "Distance since the codes were cleared", "km", "ab"},
            {"0133", "Air pressure outside", "kPa", "a"},
            {"013C", "Catalytic converter temperature", "°C", "cat"},
            {"0142", "Engine computer voltage", "V", "volt"},
            {"0143", "Absolute engine load", "%", "abpct"},
            {"0144", "Air-fuel ratio asked for (lambda)", "", "lambda"},
            {"0145", "Throttle (relative)", "%", "pct"},
            {"0146", "Outside air temperature", "°C", "temp"},
            {"0147", "Throttle (sensor B)", "%", "pct"},
            {"0149", "Accelerator pedal (sensor D)", "%", "pct"},
            {"014A", "Accelerator pedal (sensor E)", "%", "pct"},
            {"014C", "Throttle motor asked for", "%", "pct"},
            {"014D", "Time run with the engine light on", "min", "ab"},
            {"014E", "Time since the codes were cleared", "min", "ab"},
            {"0152", "Ethanol in the fuel", "%", "pct"},
            {"0159", "Fuel rail pressure (absolute)", "kPa", "ab10"},
            {"015A", "Accelerator pedal (relative)", "%", "pct"},
            {"015B", "Hybrid battery life left", "%", "pct"},
            {"015C", "Engine oil temperature", "°C", "temp"},
            {"015D", "Fuel injection timing", "°", "inj"},
            {"015E", "Fuel used", "L/h", "rate"},
            {"0161", "Engine torque asked for", "%", "torque"},
            {"0162", "Engine torque", "%", "torque"},
            {"0163", "Engine torque at 100%", "Nm", "ab"},
    };

    /** Readings the live list always shows, so they aren't offered again. */
    private static final String BUILT_IN = ",0104,0105,0106,0107,010B,010C,010D,010F,0110,0111,012F,";

    /** The table's row for a PID ("0146"), or null. */
    static String[] pid(String id) {
        for (String[] r : PIDS) if (r[0].equals(id)) return r;
        return null;
    }

    /** The PIDs the owner can add: the table less the built-in readings. */
    static List<String[]> offered() {
        List<String[]> out = new ArrayList<>();
        for (String[] r : PIDS) if (!BUILT_IN.contains("," + r[0] + ",")) out.add(r);
        return out;
    }

    /** A formula applied to the data bytes; NONE when the answer is too short. Pure, for the tests. */
    static float formula(String kind, int[] b) {
        if (b == null || b.length < 1) return NONE;
        int a = b[0];
        if ("pct".equals(kind)) return a * 100 / 255f;
        if ("temp".equals(kind)) return a - 40;
        if ("trim".equals(kind)) return (a - 128) * 100 / 128f;
        if ("a".equals(kind)) return a;
        if ("a3".equals(kind)) return 3 * a;
        if ("adv".equals(kind)) return a / 2f - 64;
        if ("o2".equals(kind)) return a / 200f;
        if ("torque".equals(kind)) return a - 125;
        if (b.length < 2) return NONE;
        int ab = a * 256 + b[1];
        switch (kind) {
            case "ab": return ab;
            case "rpm": return ab / 4f;
            case "maf": return ab / 100f;
            case "rail": return ab * 0.079f;
            case "ab10": return ab * 10f;
            case "cat": return ab / 10f - 40;
            case "volt": return ab / 1000f;
            case "abpct": return ab * 100 / 255f;
            case "lambda": return ab * 2 / 65536f;
            case "inj": return ab / 128f - 210;
            case "rate": return ab / 20f;
            default: return NONE;
        }
    }

    /** One of the table's readings from the car's answer, or NONE. Pure, for the tests. */
    static float custom(String id, String reply) {
        String[] row = pid(id);
        return row == null ? NONE : formula(row[3], bytes(reply, "41" + id.substring(2)));
    }

    /** "Outside air temperature: 31 °C", with the decimals the reading needs. Pure, for the tests. */
    static String customLine(String[] row, float v) {
        String k = row[3];
        int dec = "volt".equals(k) || "o2".equals(k) || "lambda".equals(k) || "maf".equals(k) ? 2
                : "adv".equals(k) || "rail".equals(k) || "cat".equals(k) || "inj".equals(k) || "rate".equals(k) ? 1 : 0;
        String n = String.format(Locale.US, "%." + dec + "f", v);
        if (n.equals("-0")) n = "0";
        return Hindi.t(row[1]) + ": " + n + (row[2].isEmpty() ? "" : row[2].startsWith("°") || row[2].equals("%") ? row[2] : " " + row[2]);
    }

    /** The owner's own readings ("0146,015C"), asked in turn with the extra ones. */
    static volatile String[] mine = new String[0];
    /** Their latest values; NONE when the car didn't answer. */
    static final java.util.Map<String, Float> mineNow = new java.util.concurrent.ConcurrentHashMap<>();

    /** The owner's list from a setting: known PIDs only, each once. Pure, for the tests. */
    static String[] mine(String setting) {
        List<String> out = new ArrayList<>();
        for (String s : (setting == null ? "" : setting).split(",")) {
            String id = s.trim().toUpperCase(Locale.US);
            if (pid(id) != null && !out.contains(id)) out.add(id);
        }
        return out.toArray(new String[0]);
    }

    /** Settings > Your own engine readings: tick the readings to add. */
    static void chooseMine(final android.app.Activity a, final Prefs p, final Runnable done) {
        final List<String[]> rows = offered();
        String[] names = new String[rows.size()];
        final boolean[] on = new boolean[rows.size()];
        List<String> now = java.util.Arrays.asList(mine(p.str("obdMine", "")));
        for (int i = 0; i < rows.size(); i++) {
            names[i] = Hindi.t(rows.get(i)[1]) + (rows.get(i)[2].isEmpty() ? "" : "  (" + rows.get(i)[2] + ")");
            on[i] = now.contains(rows.get(i)[0]);
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Your own engine readings"))
                .setMultiChoiceItems(names, on, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w, boolean checked) {
                        on[w] = checked;
                    }
                })
                .setPositiveButton(Hindi.t("Save"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        StringBuilder b = new StringBuilder();
                        for (int i = 0; i < rows.size(); i++) if (on[i]) b.append(b.length() == 0 ? "" : ",").append(rows.get(i)[0]);
                        p.put("obdMine", b.toString());
                        mine = mine(b.toString());
                        mineNow.clear();
                        if (done != null) done.run();
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    /** The live readings, as lines for Settings. */
    static String readings() {
        StringBuilder b = new StringBuilder();
        if (rpm >= 0) b.append(Hindi.f("Engine speed: %1$s rpm", rpm)).append('\n');
        if (coolant > -40) b.append(Hindi.f("Coolant: %1$s°C", coolant)).append('\n');
        if (intake > -999) b.append(Hindi.f("Intake air: %1$s°C", intake)).append('\n');
        if (load >= 0) b.append(Hindi.f("Engine load: %1$s%%", load)).append('\n');
        if (throttle >= 0) b.append(Hindi.f("Throttle: %1$s%%", throttle)).append('\n');
        if (manifold >= 0) b.append(Hindi.f("Manifold pressure: %1$s kPa", manifold)).append('\n');
        if (maf >= 0) b.append(Hindi.f("Air flow: %1$s g/s (about %2$s litres an hour)", Fuel.fmt(maf), Fuel.fmt(litresAnHour(maf)))).append('\n');
        if (shortTrim > -999) b.append(Hindi.f("Fuel trim now: %1$s%%", shortTrim)).append('\n');
        if (longTrim > -999) b.append(Hindi.f("Fuel trim learnt: %1$s%%", longTrim)).append('\n');
        if (fuel >= 0) b.append(Hindi.f("Fuel tank: %1$s%%", fuel)).append('\n');
        if (volts > 0) b.append(Hindi.f("Battery: %1$s V", Fuel.fmt(volts))).append('\n');
        for (String id : mine) {
            String[] row = pid(id);
            Float v = mineNow.get(id);
            if (row == null || v == null) continue;   // not asked yet
            b.append(v == NONE ? Hindi.f("%1$s: the car doesn't give this", Hindi.t(row[1])) : customLine(row, v)).append('\n');
        }
        return b.toString().trim();
    }

    /** ATRV answers like "12.6V". */
    static float volts(String r) {
        String s = clean(r).replace("V", "");
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Fault codes from a mode 03 reply. Each code is two bytes: the top two bits pick P, C, B or U,
     * the rest is four hex digits. On CAN cars the first byte after 43 is the number of codes and
     * long answers come as numbered frames ("0:4302...", "1:...").
     */
    static List<String> dtcs(String reply, boolean can) {
        return dtcs(reply, can, "43");
    }

    /** Codes from a mode 03 (stored, "43") or mode 07 (pending, "47") reply. */
    static List<String> dtcs(String reply, boolean can, String head) {
        List<String> out = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        StringBuilder cur = null;
        for (String raw : reply.toUpperCase(Locale.US).split("[\\r\\n]+")) {
            String line = raw.replaceAll("[\\s>]", "");
            if (line.isEmpty() || line.startsWith("SEARCHING")) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([0-9A-F]):(.*)$").matcher(line);
            if (m.matches()) {
                if (m.group(1).equals("0") || cur == null) {
                    cur = new StringBuilder();
                    blocks.add(null);
                }
                cur.append(m.group(2));
                blocks.set(blocks.size() - 1, cur.toString());
            } else if (line.startsWith(head)) {
                cur = new StringBuilder(line);
                blocks.add(line);
            } else if (line.length() <= 3) {
                cur = null;   // a CAN byte count such as "00A"
            }
        }
        for (String block : blocks) {
            if (block == null || !block.startsWith(head)) continue;
            String hex = block.substring(2).replaceAll("[^0-9A-F]", "");
            int n = hex.length() / 2, k = 0, limit = n;
            if (can && n > 0) {
                limit = Math.min(n, 1 + 2 * Integer.parseInt(hex.substring(0, 2), 16));
                k = 1;
            }
            for (; k + 1 < limit; k += 2) {
                int a = Integer.parseInt(hex.substring(2 * k, 2 * k + 2), 16);
                int b = Integer.parseInt(hex.substring(2 * k + 2, 2 * k + 4), 16);
                if (a == 0 && b == 0) continue;
                String code = "PCBU".charAt(a >> 6) + Integer.toString((a >> 4) & 3) + String.format(Locale.US, "%X%02X", a & 15, b);
                if (!out.contains(code)) out.add(code);
            }
        }
        return out;
    }

    /** A code in plain words (FaultCodes). */
    static String meaning(String code) {
        return Hindi.t((String) FaultCodes.look(code)[0]);
    }

    private Obd() {
    }
}
