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
        if (mac.isEmpty()) {
            status = Hindi.t("Not set up");
            return;
        }
        synchronized (Obd.class) {
            if (run != null && !run.stop.get() && run.thread.isAlive()) return;
            final Run r = new Run();
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
        } catch (Throwable ignored) {
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
                while (!stop.get()) {
                    String job = pending;
                    if (job != null) {
                        pending = null;
                        String r = ask(in, out, ws, job, 5000);
                        if ("03".equals(job)) {
                            List<String> found = dtcs(r, protocol.contains("CAN") || protocol.contains("15765"));
                            synchronized (codes) {
                                codes.clear();
                                codes.addAll(found);
                            }
                            codesAt = System.currentTimeMillis();
                        } else {
                            synchronized (codes) {
                                codes.clear();
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
        c.sendBroadcast(new Intent(Drive.CHANGED).setPackage(c.getPackageName()));
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
            } else if (line.startsWith("43")) {
                cur = new StringBuilder(line);
                blocks.add(line);
            } else if (line.length() <= 3) {
                cur = null;   // a CAN byte count such as "00A"
            }
        }
        for (String block : blocks) {
            if (block == null || !block.startsWith("43")) continue;
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
