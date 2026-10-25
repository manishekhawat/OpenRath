/*
 * OpenRath's built-in Android Auto receiver.
 *
 * Derived from Headunit Revived / Open Headunit by Andre Rinas
 * (github.com/andreknieriem/headunit-revived), which is based on headunit by Michael Reid
 * (github.com/mikereidis/headunit, Copyright 2011-2015 Michael A. Reid). Credit to Michael Reid
 * (headunit) and Andre Rinas (Headunit Revived / Open Headunit).
 * Copyright (C) Manish Shekhawat and the OpenRath contributors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. It is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See LICENSES/AGPL-3.0.txt for the full text.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package org.openrath.launcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Looper;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.UUID;

/**
 * Wireless Android Auto: the phone is started over Bluetooth and then talks over Wi-Fi. OpenRath
 * makes a Wi-Fi Direct group (its own small Wi-Fi network), listens for the phone on Android
 * Auto's Bluetooth service (and knocks on paired phones' one), and tells the phone over that
 * Bluetooth channel the network's name, password and where to connect (port 5288). The phone
 * joins, connects over TCP, and an ordinary AaSession runs on that socket. One thread of its own
 * does it all in turn, only while the androidAutoWireless switch (and androidAuto) is on.
 */
final class AaWireless {

    /** Android Auto's Bluetooth service, the one a phone looks for in a car. */
    static final UUID AA_UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66");
    static final int PORT = 5288;
    // the Bluetooth messages (the reference's WppMessageType)
    static final int START_REQUEST = 1, INFO_REQUEST = 2, INFO_RESPONSE = 3, VERSION_REQUEST = 4, VERSION_RESPONSE = 5,
            CONNECT_STATUS = 6, START_RESPONSE = 7, PING_REQUEST = 8, PING_RESPONSE = 9;
    private static final int WPA2_PERSONAL = 8, DYNAMIC = 1;

    private static volatile boolean running;
    private static volatile Thread thread;
    private static volatile String ssid = "", key = "", ip = "";
    private static WifiP2pManager.Channel channel;
    private static volatile BluetoothServerSocket listening;
    private static volatile ServerSocket server;

    // ---- the messages (pure, for the tests)

    /** A Bluetooth message: payload length and type (two bytes each, high first), then the payload. */
    static byte[] frame(int type, byte[] payload) {
        byte[] f = new byte[4 + payload.length];
        f[0] = (byte) (payload.length >> 8);
        f[1] = (byte) payload.length;
        f[2] = (byte) (type >> 8);
        f[3] = (byte) type;
        System.arraycopy(payload, 0, f, 4, payload.length);
        return f;
    }

    /** Where the phone connects after joining the network (WifiStartRequest). */
    static byte[] startRequest(String ip, int port) {
        return new AaProto.Writer().text(1, ip).num(2, port).num(3, 0).bytes();
    }

    /** The network: name, password, its address if known ("" if not), WPA2, and that it may change (WifiInfoResponse). */
    static byte[] infoResponse(String ssid, String key, String bssid) {
        return new AaProto.Writer().text(1, ssid).text(2, key).text(3, bssid == null ? "" : bssid)
                .num(4, WPA2_PERSONAL).num(5, DYNAMIC).bytes();
    }

    /** OpenRath's wireless version (4.2, as the reference) and the car's names (WifiVersionRequest). */
    static byte[] versionRequest(AaMessages.Car car) {
        return new AaProto.Writer().num(1, 4).num(2, 2)
                .msg(5, new AaProto.Writer().text(1, car.make).text(2, car.model).text(3, car.year).text(4, car.id)
                        .text(5, car.huMake).text(6, car.huModel).text(7, car.version).text(8, car.version))
                .bytes();
    }

    // ---- starting and stopping

    /** Starts or stops wireless Android Auto to match the switches; cheap, called on each return to the dashboard. */
    static synchronized void check(Context c) {
        Prefs p = new Prefs(c.getApplicationContext());
        boolean on = Features.on(p, "androidAuto") && Features.on(p, "androidAutoWireless");
        if (on && !running) start(c.getApplicationContext());
        else if (!on && running) stop(c.getApplicationContext());
    }

    private static void start(final Context app) {
        running = true;
        group(app);
        // wireless Android Auto's own thread: it waits on Bluetooth and the network for as long as the switch is on
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                loop(app);
            }
        }, "android-auto-wireless");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    private static void stop(Context app) {
        running = false;
        closeQuietly(listening);
        closeQuietly(server);
        Thread t = thread;
        if (t != null) t.interrupt();
        thread = null;
        try {
            WifiP2pManager m = (WifiP2pManager) app.getSystemService(Context.WIFI_P2P_SERVICE);
            if (m != null && channel != null) m.removeGroup(channel, null);
        } catch (Throwable e) {
            Diag.note("android auto wifi group off", e);
        }
    }

    /** Makes the Wi-Fi Direct group and notes its name, password and address. */
    private static void group(Context app) {
        try {
            final WifiP2pManager m = (WifiP2pManager) app.getSystemService(Context.WIFI_P2P_SERVICE);
            if (m == null) return;
            if (channel == null) channel = m.initialize(app, Looper.getMainLooper(), null);
            m.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    readGroup(m);
                }

                @Override
                public void onFailure(int reason) {
                    readGroup(m);   // often "busy": a group from last time is still up
                }
            });
        } catch (Throwable e) {
            Diag.note("android auto wifi group", e);
        }
    }

    private static void readGroup(final WifiP2pManager m) {
        try {
            m.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup g) {
                    if (g == null) return;
                    ssid = g.getNetworkName() == null ? "" : g.getNetworkName();
                    key = g.getPassphrase() == null ? "" : g.getPassphrase();
                }
            });
            m.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                @Override
                public void onConnectionInfoAvailable(WifiP2pInfo info) {
                    ip = info != null && info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : "192.168.49.1";
                }
            });
        } catch (Throwable e) {
            Diag.note("android auto wifi group details", e);
        }
    }

    // ---- the thread

    private static void loop(Context app) {
        while (running) {
            BluetoothSocket bt = null;
            try {
                if (AaSession.current() != null || ssid.isEmpty()) {
                    Thread.sleep(3000);   // a session runs, or the network isn't up yet
                    if (ssid.isEmpty()) group(app);
                    continue;
                }
                bt = phone();
                if (bt == null) continue;
                Socket s = handshake(app, bt);
                if (s == null) continue;
                Link l = new Link(s, bt);
                bt = null;   // the link owns it now
                if (AaSession.start(app, l) != null) AaActivity.open(app);
                else l.close();
            } catch (InterruptedException e) {
                return;   // switched off
            } catch (Throwable e) {
                if (running) Diag.note("android auto wireless", e);
                try {
                    Thread.sleep(15000);   // Bluetooth off or not allowed: look again now and then
                } catch (InterruptedException x) {
                    return;   // switched off
                }
            } finally {
                closeQuietly(bt);
            }
        }
    }

    /** A phone on Android Auto's Bluetooth service: one that connects to us within 20 seconds, else a paired phone we knock on. */
    private static BluetoothSocket phone() throws IOException {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null || !a.isEnabled()) throw new IOException("Bluetooth is off or missing");
        if (listening == null) listening = a.listenUsingRfcommWithServiceRecord("Android Auto", AA_UUID);
        try {
            return listening.accept(20000);
        } catch (IOException timeout) {
            // nobody came: knock on the paired phones
            if (!running) return null;
        }
        for (BluetoothDevice d : a.getBondedDevices()) {
            BluetoothClass k = d.getBluetoothClass();
            if (k != null && k.getMajorDeviceClass() != BluetoothClass.Device.Major.PHONE) continue;
            BluetoothSocket s = null;
            try {
                s = d.createRfcommSocketToServiceRecord(AA_UUID);
                s.connect();
                return s;
            } catch (IOException e) {
                closeQuietly(s);   // not in range, or no Android Auto there
            }
        }
        return null;
    }

    /** Over Bluetooth: who we are, where to connect, the network; then wait for the phone on TCP. Null when it doesn't come. */
    private static Socket handshake(Context app, BluetoothSocket bt) throws IOException {
        AaMessages.Car car = AaSession.car(app, true);
        InputStream in = bt.getInputStream();
        OutputStream out = bt.getOutputStream();
        if (server == null) {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(PORT));
            server = ss;
        }
        out.write(frame(VERSION_REQUEST, versionRequest(car)));
        out.write(frame(START_REQUEST, startRequest(ip, PORT)));
        out.flush();
        DataInputStream din = new DataInputStream(in);
        long until = System.currentTimeMillis() + 30000;
        boolean joined = false;
        while (running && System.currentTimeMillis() < until && !joined) {
            byte[] h = new byte[4];
            din.readFully(h);
            int len = ((h[0] & 0xFF) << 8) | (h[1] & 0xFF), type = ((h[2] & 0xFF) << 8) | (h[3] & 0xFF);
            byte[] payload = new byte[len];
            din.readFully(payload);
            if (type == INFO_REQUEST) {
                out.write(frame(INFO_RESPONSE, infoResponse(ssid, key, "")));
                out.flush();
            } else if (type == PING_REQUEST) {
                out.write(frame(PING_RESPONSE, payload));
                out.flush();
            } else if (type == CONNECT_STATUS || type == START_RESPONSE) {
                joined = AaProto.find(payload, 0, payload.length, type == CONNECT_STATUS ? 1 : 3, 0) == 0;
            }
        }
        if (!joined) return null;
        server.setSoTimeout(30000);
        try {
            Socket s = server.accept();
            s.setTcpNoDelay(true);
            return s;
        } catch (SocketTimeoutException e) {
            return null;   // the phone joined but never connected
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException e) {
            Diag.note("android auto wireless close", e);
        }
    }

    /** The phone over Wi-Fi: a TCP socket. The Bluetooth channel stays open beside it until the session ends. */
    static final class Link implements AaLink {
        private final Socket s;
        private final BluetoothSocket bt;
        private final InputStream in;
        private final OutputStream out;

        Link(Socket s, BluetoothSocket bt) throws IOException {
            this.s = s;
            this.bt = bt;
            in = s.getInputStream();
            out = s.getOutputStream();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return in.read(b, off, len);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            out.flush();
        }

        @Override
        public void close() {
            closeQuietly(s);
            closeQuietly(bt);
        }

        @Override
        public String name() {
            return "wifi:" + s.getInetAddress();
        }

        @Override
        public boolean wireless() {
            return true;
        }
    }

    private AaWireless() {
    }
}
