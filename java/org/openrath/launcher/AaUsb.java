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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.SystemClock;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Android Auto over the USB cable. A phone plugged into the unit's USB port is asked to switch
 * into Android Open Accessory mode (it comes back as Google's accessory device, 18d1:2d00), then
 * opened as one: its two bulk endpoints are the wire AaSession talks over. Only while the
 * androidAuto switch is on; a phone the owner said no to is left alone until it is unplugged.
 */
final class AaUsb {

    static final int GOOGLE = 0x18D1, APPLE = 0x05AC;
    private static final String PERMISSION = "org.openrath.launcher.AA_USB_PERMISSION";
    // Android Open Accessory control requests and the strings that make the phone start Android Auto
    private static final int GET_PROTOCOL = 51, SEND_STRING = 52, START = 53;
    private static final String[] STRINGS = {"Android", "Android Auto", "Android Auto", "2.0.1",
            "https://developer.android.com/auto/index.html", "HU-AAAAAA001"};
    /** USB-to-serial chips (OBD-II adapters and the like): vendor-class devices that are never phones. */
    private static final int[] SERIAL_CHIPS = {0x0403, 0x1A86, 0x067B, 0x10C4};

    private static BroadcastReceiver listener;
    /** Phones asked to switch, by device name, and when: one ask per phone in 15 seconds. */
    private static final Map<String, Long> switched = new HashMap<>();
    /**
     * Phones left alone until they are unplugged: the owner refused the permission, a session
     * with it ended, or it was told about a missing certificate. The Android Auto screen tries
     * them again (the owner asked for it there).
     */
    private static final Set<String> resting = new HashSet<>();
    /** Phones whose permission question is on screen. */
    private static final Set<String> asked = new HashSet<>();
    /** A switch or a start is under way on a background thread. */
    private static boolean busy;

    /** Whether a device is already in accessory mode (Google's accessory ids). */
    static boolean accessory(int vendor, int product) {
        return vendor == GOOGLE && (product == 0x2D00 || product == 0x2D01 || product == 0x2D04 || product == 0x2D05);
    }

    /**
     * Whether a device looks like a phone that could run Android Auto, from its ids, its device
     * class and its interfaces' {class, subclass, protocol}: in accessory mode, or with ADB, PTP,
     * tethering or the vendor-class interface Android uses for file transfer. Not Apple, hubs,
     * memory sticks, sound cards or USB-serial adapters. Pure, for the tests.
     */
    static boolean looksLikePhone(int vendor, int product, int deviceClass, int[][] interfaces) {
        if (vendor == APPLE) return false;
        if (accessory(vendor, product)) return true;
        if (deviceClass == 1 || deviceClass == 7 || deviceClass == 8 || deviceClass == 9 || deviceClass == 0x0E
                || deviceClass == 0x11 || deviceClass == 0xFF) return false;
        for (int v : SERIAL_CHIPS) if (vendor == v) return false;
        for (int[] i : interfaces) {
            if (i[0] == 0xFF && i[1] == 0x42 && i[2] == 0x01) return true;   // ADB
            if (i[0] == 0x06 && i[1] == 0x01 && i[2] == 0x01) return true;   // PTP
            if (i[0] == 0xE0 && i[1] == 0x01 && i[2] == 0x03) return true;   // tethering
            if (i[0] == 0xFF && i[1] == 0xFF && i[2] == 0x00) return true;   // file transfer (MTP)
        }
        return false;
    }

    /** Watch for phones plugged in and out; once, with the app's context. */
    static synchronized void listen(Context c) {
        if (listener != null) return;
        final Context app = c.getApplicationContext();
        listener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context x, Intent i) {
                UsbDevice d = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                String a = i.getAction();
                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(a)) {
                    if (d != null) gone(d.getDeviceName());
                    return;
                }
                if (PERMISSION.equals(a) && d != null) {
                    synchronized (AaUsb.class) {
                        asked.remove(d.getDeviceName());
                        if (!i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            resting.add(d.getDeviceName());
                            return;
                        }
                    }
                }
                check(app, false);
            }
        };
        try {
            IntentFilter f = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            Compat.fromSystem(app, listener, f);
            Compat.fromSystem(app, listener, new IntentFilter(PERMISSION));
        } catch (Throwable e) {
            listener = null;
            Diag.note("android auto usb watch", e);
        }
    }

    /** A device left: the session ends if it was the phone, and the phone may start afresh next time. */
    static void gone(String name) {
        AaSession s = AaSession.current();
        if (s != null && !s.link.wireless() && s.link.name().equals(name)) s.stop(Hindi.t("The phone was unplugged"));
        synchronized (AaUsb.class) {
            resting.remove(name);
            switched.remove(name);
            asked.remove(name);
        }
    }

    /** A session on this phone ended: it isn't started again by itself until it is plugged in afresh. */
    static synchronized void ended(String name) {
        resting.add(name);
    }

    /**
     * Look at what is plugged in: start Android Auto with a phone already in accessory mode, ask
     * another phone to switch, or ask the owner for permission first. Nothing while the switch is
     * off or a session runs. `screen` is true from the Android Auto screen: phones left alone are
     * tried again, and a missing certificate is left for the screen to explain; otherwise the
     * screen is opened to explain it, once per phone.
     */
    static void check(Context c, boolean screen) {
        try {
            final Context app = c.getApplicationContext();
            boolean on = Features.on(new Prefs(app), "androidAuto");
            plugStart(app, on);
            if (!on || AaSession.current() != null) return;
            final UsbManager m = (UsbManager) app.getSystemService(Context.USB_SERVICE);
            if (m == null) return;
            for (final UsbDevice d : m.getDeviceList().values()) {
                if (!looksLikePhone(d.getVendorId(), d.getProductId(), d.getDeviceClass(), interfaces(d))) continue;
                String name = d.getDeviceName();
                synchronized (AaUsb.class) {
                    if (screen) resting.remove(name);
                    if (busy || resting.contains(name) || asked.contains(name)) continue;
                }
                if (AaCert.from(app).isEmpty()) {
                    if (!screen) {
                        ended(name);
                        AaActivity.open(app);   // it says what's needed
                    }
                    return;
                }
                if (!m.hasPermission(d)) {
                    synchronized (AaUsb.class) {
                        asked.add(name);
                    }
                    ask(app, m, d);
                    return;
                }
                final boolean acc = accessory(d.getVendorId(), d.getProductId());
                if (!acc) {
                    synchronized (AaUsb.class) {
                        Long at = switched.get(name);
                        if (at != null && SystemClock.elapsedRealtime() - at < 15000) continue;
                        switched.put(name, SystemClock.elapsedRealtime());
                    }
                }
                synchronized (AaUsb.class) {
                    busy = true;
                }
                Bg.run(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (acc) start(app, m, d);
                            else switchPhone(m, d);
                        } finally {
                            synchronized (AaUsb.class) {
                                busy = false;
                            }
                        }
                    }
                });
                return;
            }
        } catch (Throwable e) {
            Diag.note("android auto usb check", e);
        }
    }

    private static Boolean plugStartOn;

    /**
     * Turns the "open OpenRath when a phone is plugged in" entry (AaUsbStart in the manifest) on
     * with the switch, so Android doesn't offer OpenRath for every phone while it is off.
     */
    private static void plugStart(Context app, boolean on) {
        if (plugStartOn != null && plugStartOn == on) return;
        try {
            app.getPackageManager().setComponentEnabledSetting(
                    new android.content.ComponentName(app, "org.openrath.launcher.AaUsbStart"),
                    on ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            plugStartOn = on;
        } catch (Throwable e) {
            Diag.note("android auto plug-in entry", e);
        }
    }

    private static int[][] interfaces(UsbDevice d) {
        int n = d.getInterfaceCount();
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            UsbInterface f = d.getInterface(i);
            out[i] = new int[]{f.getInterfaceClass(), f.getInterfaceSubclass(), f.getInterfaceProtocol()};
        }
        return out;
    }

    private static void ask(Context app, UsbManager m, UsbDevice d) {
        Intent i = new Intent(PERMISSION).setPackage(app.getPackageName());
        // Android 12 and newer need it mutable, so the system can add whether it was allowed
        int flags = Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0;   // PendingIntent.FLAG_MUTABLE
        m.requestPermission(d, PendingIntent.getBroadcast(app, 0, i, flags));
    }

    /** Asks the phone to switch to accessory mode; it then leaves the bus and comes back as Google's accessory. */
    private static void switchPhone(UsbManager m, UsbDevice d) {
        UsbDeviceConnection c = null;
        try {
            c = m.openDevice(d);
            if (c == null) return;
            byte[] v = new byte[2];
            int n = c.controlTransfer(UsbConstants.USB_DIR_IN | UsbConstants.USB_TYPE_VENDOR, GET_PROTOCOL, 0, 0, v, 2, 1000);
            if (n != 2 || ((v[0] & 0xFF) | ((v[1] & 0xFF) << 8)) < 1) {
                Diag.note("android auto: no accessory mode on " + d.getProductName(), null);
                return;
            }
            for (int i = 0; i < STRINGS.length; i++) {
                byte[] s = STRINGS[i].getBytes("UTF-8");
                if (c.controlTransfer(UsbConstants.USB_TYPE_VENDOR, SEND_STRING, 0, i, s, s.length, 1000) < 0) {
                    Diag.note("android auto: the phone refused accessory string " + i, null);
                    return;
                }
            }
            c.controlTransfer(UsbConstants.USB_TYPE_VENDOR, START, 0, 0, new byte[0], 0, 1000);
        } catch (Throwable e) {
            Diag.note("android auto usb switch", e);
        } finally {
            if (c != null) c.close();
        }
    }

    /** Opens the phone in accessory mode and starts Android Auto on it. */
    private static void start(Context app, UsbManager m, UsbDevice d) {
        Link l = null;
        try {
            l = Link.open(m, d);
            if (AaSession.start(app, l) != null) {
                AaActivity.open(app);
                return;
            }
            ended(d.getDeviceName());   // no certificate after all: the screen explains, once
        } catch (Throwable e) {
            Diag.note("android auto usb open", e);
        }
        if (l != null) l.close();
    }

    /** The phone in accessory mode: its first interface's bulk endpoints, read 16 KB at a time. */
    static final class Link implements AaLink {
        private final UsbDeviceConnection c;
        private final UsbInterface face;
        private final UsbEndpoint in, out;
        private final String name;
        private final byte[] buf = new byte[16384];
        private int pos, have;
        private volatile boolean closed;

        private Link(UsbDeviceConnection c, UsbInterface face, UsbEndpoint in, UsbEndpoint out, String name) {
            this.c = c;
            this.face = face;
            this.in = in;
            this.out = out;
            this.name = name;
        }

        static Link open(UsbManager m, UsbDevice d) throws IOException {
            UsbDeviceConnection c = m.openDevice(d);
            if (c == null) throw new IOException("no permission for the phone");
            try {
                if (d.getInterfaceCount() == 0) throw new IOException("the phone has no interface");
                UsbInterface f = d.getInterface(0);
                if (!c.claimInterface(f, true)) throw new IOException("the phone's interface is busy");
                UsbEndpoint in = null, out = null;
                for (int i = 0; i < f.getEndpointCount(); i++) {
                    UsbEndpoint e = f.getEndpoint(i);
                    if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                    if (e.getDirection() == UsbConstants.USB_DIR_IN && in == null) in = e;
                    else if (e.getDirection() == UsbConstants.USB_DIR_OUT && out == null) out = e;
                }
                if (in == null || out == null) throw new IOException("the phone has no bulk endpoints");
                return new Link(c, f, in, out, d.getDeviceName());
            } catch (IOException e) {
                c.close();
                throw e;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (have == 0) {
                int quick = 0;
                while (true) {
                    if (closed) throw new IOException("closed");
                    long t = SystemClock.elapsedRealtime();
                    // Android gives -1 for a timeout and for an error alike: a second's wait is a
                    // timeout, many instant failures in a row are the phone gone
                    int n = c.bulkTransfer(in, buf, buf.length, 1000);
                    if (n > 0) {
                        pos = 0;
                        have = n;
                        break;
                    }
                    if (SystemClock.elapsedRealtime() - t < 50 && ++quick > 20) throw new IOException("the phone stopped answering");
                }
            }
            int n = Math.min(len, have);
            System.arraycopy(buf, pos, b, off, n);
            pos += n;
            have -= n;
            return n;
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (closed) throw new IOException("closed");
                int n = Math.min(len, 16384);
                int sent = c.bulkTransfer(out, b, off, n, 2000);
                if (sent <= 0) throw new IOException("USB write failed");
                off += sent;
                len -= sent;
            }
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                c.releaseInterface(face);
            } catch (Throwable e) {
                Diag.note("android auto usb release", e);
            }
            c.close();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean wireless() {
            return false;
        }
    }

    private AaUsb() {
    }
}
