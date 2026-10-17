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
 * Engine data from a Bluetooth ELM327 OBD-II adapter (the common blue plug under the dashboard):
 * RPM, speed, coolant temperature, engine load, battery voltage and fault codes. The adapter is
 * paired in the unit's Bluetooth settings first, then chosen in Settings > Engine (OBD-II).
 * Runs on its own thread inside the trip service; the boxes read the static fields.
 */
final class Obd {

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // latest readings; -1 = not known
    static volatile int rpm = -1, kmh = -1, coolant = -1, load = -1;
    static volatile float volts = -1;
    static volatile long readAt;
    static volatile String status = "Not set up", protocol = "";
    static volatile boolean connected;
    static final List<String> codes = new ArrayList<>();
    static volatile long codesAt;

    private static Thread thread;
    private static volatile boolean stop;
    private static volatile String pending;   // "03" read codes or "04" clear codes, asked from Settings

    /** Speed from the car itself, when the adapter answered in the last 3 seconds; else -1. */
    static int speedNow() {
        return connected && kmh >= 0 && System.currentTimeMillis() - readAt < 3000 ? kmh : -1;
    }

    static void start(final Context c, final Prefs p) {
        final String mac = p.str("obdMac", "");
        if (mac.isEmpty()) {
            status = "Not set up";
            return;
        }
        if (thread != null && thread.isAlive()) return;
        stop = false;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop(c.getApplicationContext(), mac);
            }
        }, "obd");
        thread.setDaemon(true);
        thread.start();
    }

    static void stop() {
        stop = true;
        if (thread != null) thread.interrupt();
        thread = null;
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

    private static void loop(Context c, String mac) {
        long backoff = 5000;
        while (!stop) {
            BluetoothSocket s = null;
            try {
                status = "Connecting…";
                changed(c);
                BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
                if (a == null || !a.isEnabled()) throw new IllegalStateException("Bluetooth is off");
                BluetoothDevice d = a.getRemoteDevice(mac);
                a.cancelDiscovery();
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
                InputStream in = s.getInputStream();
                OutputStream out = s.getOutputStream();
                for (String cmd : new String[]{"ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0"}) ask(in, out, cmd, cmd.equals("ATZ") ? 3000 : 1500);
                String first = ask(in, out, "0100", 10000);   // the adapter searches for the car's protocol here
                if (first.contains("UNABLE") || first.contains("ERROR") || first.contains("NODATA") || first.contains("CANTCONNECT"))
                    throw new IllegalStateException("adapter can't reach the car (ignition on?)");
                protocol = clean(ask(in, out, "ATDP", 1500));
                connected = true;
                status = "Connected";
                backoff = 5000;
                changed(c);
                int round = 0;
                while (!stop) {
                    String job = pending;
                    if (job != null) {
                        pending = null;
                        String r = ask(in, out, job, 5000);
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
                    kmh = speed(ask(in, out, "010D", 1500));
                    rpm = rpm(ask(in, out, "010C", 1500));
                    if (round % 5 == 0) {
                        coolant = coolant(ask(in, out, "0105", 1500));
                        load = load(ask(in, out, "0104", 1500));
                        volts = volts(ask(in, out, "ATRV", 1500));
                    }
                    readAt = System.currentTimeMillis();
                    round++;
                    changed(c);
                    Thread.sleep(250);
                }
            } catch (Throwable e) {
                connected = false;
                status = "Not connected: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                changed(c);
            } finally {
                try {
                    if (s != null) s.close();
                } catch (Exception ignored) {
                }
            }
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

    /** Send one command and read up to the '>' prompt. */
    private static String ask(InputStream in, OutputStream out, String cmd, long timeout) throws Exception {
        while (in.available() > 0) in.read();
        out.write((cmd + "\r").getBytes("US-ASCII"));
        out.flush();
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < end) {
            if (in.available() > 0) {
                int b = in.read();
                if (b < 0) throw new IllegalStateException("adapter disconnected");
                if (b == '>') break;
                sb.append((char) b);
            } else Thread.sleep(10);
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

    /** A few common codes, in words; others link to a search. */
    static String meaning(String code) {
        String[][] known = {
                {"P0101", "Air flow sensor reading out of range"}, {"P0115", "Coolant temperature sensor"},
                {"P0120", "Throttle position sensor"}, {"P0130", "Oxygen sensor (front)"}, {"P0133", "Oxygen sensor slow (front)"},
                {"P0171", "Engine running lean"}, {"P0172", "Engine running rich"}, {"P0300", "Random misfire"},
                {"P0301", "Misfire, cylinder 1"}, {"P0302", "Misfire, cylinder 2"}, {"P0303", "Misfire, cylinder 3"},
                {"P0304", "Misfire, cylinder 4"}, {"P0335", "Crankshaft position sensor"}, {"P0340", "Camshaft position sensor"},
                {"P0420", "Catalytic converter efficiency low"}, {"P0442", "Small fuel vapour (EVAP) leak"},
                {"P0455", "Large fuel vapour (EVAP) leak; check the fuel cap"}, {"P0500", "Vehicle speed sensor"},
                {"P0505", "Idle air control"}, {"P0562", "Battery voltage low"}, {"P0563", "Battery voltage high"},
                {"P0700", "Gearbox control fault"}};
        for (String[] k : known) if (k[0].equals(code)) return k[1];
        return "Look this code up for your car";
    }

    private Obd() {
    }
}
