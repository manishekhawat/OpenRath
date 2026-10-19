package org.openrath.launcher;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Cheap Bluetooth LED strips (the ELK-BLEDOM kind, run by the "duoCo Strip" or "Lotus Lantern"
 * apps) as ambient light: the colour follows the theme's accent, dims at night and turns red for a
 * warning (engine hot, over the limit). Commands are 9 bytes, 7E … EF, written to characteristic
 * FFF3. Only the strip the owner picks is used; nothing else is sent.
 */
final class LedStrip {

    static final UUID SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    static final UUID WRITE = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb");
    static final int RED = 0xFF0000;
    static final long TICK_MS = 3000;

    /** Set colour: 7E 00 05 03 RR GG BB 00 EF. */
    static byte[] rgb(int c) {
        return new byte[]{0x7E, 0x00, 0x05, 0x03, (byte) (c >> 16), (byte) (c >> 8), (byte) c, 0x00, (byte) 0xEF};
    }

    /** Switch on or off. */
    static byte[] power(boolean on) {
        return new byte[]{0x7E, 0x00, 0x04, (byte) (on ? 0xF0 : 0x00), 0x00, (byte) (on ? 0x01 : 0x00), (byte) 0xFF, 0x00, (byte) 0xEF};
    }

    /** Brightness 0 to 100. */
    static byte[] brightness(int pct) {
        return new byte[]{0x7E, 0x00, 0x01, (byte) Math.max(0, Math.min(100, pct)), 0x00, 0x00, 0x00, 0x00, (byte) 0xEF};
    }

    /** The colour to show: red for a warning, else the accent, at a third of its strength at night. */
    static int colour(int accent, boolean night, boolean warn) {
        if (warn) return RED;
        int r = (accent >> 16) & 0xFF, g = (accent >> 8) & 0xFF, b = accent & 0xFF;
        if (night) {
            r /= 3;
            g /= 3;
            b /= 3;
        }
        return (r << 16) | (g << 8) | b;
    }

    /** Whether a Bluetooth name looks like one of these strips. */
    static boolean looksLike(String name) {
        if (name == null) return false;
        String n = name.toUpperCase(Locale.US);
        return n.startsWith("ELK") || n.contains("BLEDOM") || n.startsWith("MELK") || n.startsWith("LEDBLE") || n.startsWith("DUOCO");
    }

    private static Handler main;
    private static BluetoothGatt gatt;
    private static BluetoothGattCharacteristic out;
    private static String mac;
    private static int shown = -1;
    private static long triedAt;
    private static Runnable tick;

    /** From the dashboard's regular check: start or stop following, as the switch says. */
    static void check(HomeActivity a) {
        String want = a.prefs.str("ledMac", "");
        if (!Features.on(a.prefs, "ledStrip") || want.isEmpty()) {
            stop();
            return;
        }
        if (!want.equals(mac)) stop();
        mac = want;
        // the newest dashboard, without keeping an old one alive after Android recreates it
        dash = new java.lang.ref.WeakReference<>(a);
        if (tick != null) return;
        final Context app = a.getApplicationContext();
        final Prefs prefs = new Prefs(app);
        if (main == null) main = new Handler(Looper.getMainLooper());
        tick = new Runnable() {
            @Override
            public void run() {
                if (tick != this) return;
                follow(prefs, app);
                main.postDelayed(this, TICK_MS);
            }
        };
        main.post(tick);
    }

    private static java.lang.ref.WeakReference<HomeActivity> dash;

    /** The dashboard was closed for good: stop following until the next one checks again. */
    static void closed(HomeActivity a) {
        HomeActivity d = dash == null ? null : dash.get();
        if (d == null || d == a) {
            tick = null;
            dash = null;
        }
    }

    /** Night here: from the dashboard's position while it is open, else the trip service's. */
    private static boolean night(long now) {
        HomeActivity a = dash == null ? null : dash.get();
        if (a != null && a.hub != null && a.hub.fix != null) return ThemeAuto.night(a, now);
        if (Drive.fixAt > 0) return Care.dark(Drive.latNow, Drive.lonNow, now);
        return a != null ? ThemeAuto.night(a, now) : ThemeAuto.night(null, now);
    }

    private static void follow(Prefs prefs, Context app) {
        if (out == null) {
            long now = System.currentTimeMillis();
            if (now - triedAt > 30000) {
                triedAt = now;
                connect(app);
            }
            return;
        }
        boolean warn = (Obd.connected && Obd.coolant >= prefs.num("obdHot", 105))
                || (Drive.limit > 0 && Drive.kmhNow > Drive.limit + 5);
        int c = colour(Ui.ACCENT, night(System.currentTimeMillis()), warn);
        if (c != shown && send(rgb(c))) shown = c;
    }

    private static void connect(Context app) {
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad == null || !ad.isEnabled() || mac == null) return;
            if (gatt != null) gatt.close();
            BluetoothDevice d = ad.getRemoteDevice(mac);
            gatt = d.connectGatt(app, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
                    if (state == BluetoothProfile.STATE_CONNECTED) g.discoverServices();
                    else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        out = null;
                        shown = -1;
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt g, int status) {
                    BluetoothGattCharacteristic ch = null;
                    BluetoothGattService s = g.getService(SERVICE);
                    if (s != null) ch = s.getCharacteristic(WRITE);
                    // some strips use another service: take its first writable characteristic
                    if (ch == null) {
                        for (BluetoothGattService any : g.getServices()) {
                            for (BluetoothGattCharacteristic c : any.getCharacteristics()) {
                                if (ch == null && (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) ch = c;
                            }
                        }
                    }
                    out = ch;
                    if (ch != null) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                send(power(true));
                            }
                        });
                    }
                }
            });
        } catch (Throwable ignored) {
            // no permission, or Bluetooth off: try again later
        }
    }

    private static boolean send(byte[] b) {
        try {
            BluetoothGattCharacteristic ch = out;
            if (ch == null || gatt == null) return false;
            ch.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            ch.setValue(b);
            return gatt.writeCharacteristic(ch);
        } catch (Throwable t) {
            return false;
        }
    }

    static void stop() {
        tick = null;
        out = null;
        shown = -1;
        mac = null;
        try {
            if (gatt != null) gatt.close();
        } catch (Throwable ignored) {
        }
        gatt = null;
    }

    /** Look for strips nearby for 8 seconds, then let the owner pick one. */
    static void choose(final HomeActivity a) {
        final BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null || !ad.isEnabled() || ad.getBluetoothLeScanner() == null) {
            Toast.makeText(a, Hindi.t("Turn on Bluetooth first"), Toast.LENGTH_LONG).show();
            return;
        }
        if (main == null) main = new Handler(Looper.getMainLooper());
        final List<String> found = new ArrayList<>();
        final ScanCallback cb = new ScanCallback() {
            @Override
            public void onScanResult(int type, ScanResult r) {
                String n = r.getDevice().getName();
                if (n == null && r.getScanRecord() != null) n = r.getScanRecord().getDeviceName();
                String e = n + "|" + r.getDevice().getAddress();
                if (looksLike(n) && !found.contains(e)) found.add(e);
            }
        };
        try {
            ad.getBluetoothLeScanner().startScan(cb);
        } catch (Throwable t) {
            Toast.makeText(a, Hindi.f("Can't scan: %1$s", String.valueOf(t.getMessage())), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(a, Hindi.t("Looking for light strips; keep the strip switched on"), Toast.LENGTH_LONG).show();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    ad.getBluetoothLeScanner().stopScan(cb);
                } catch (Throwable ignored) {
                }
                if (found.isEmpty()) {
                    Toast.makeText(a, Hindi.t("No light strip found nearby"), Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < names.length; i++) names[i] = found.get(i).replace("|", "  ");
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Which light strip?"))
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                String[] p = found.get(w).split("\\|");
                                a.prefs.put("ledName", p[0]);
                                a.prefs.put("ledMac", p[1]);
                                Features.set(a.prefs, "ledStrip", true);
                                stop();
                                check(a);
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }, 8000);
    }

    private LedStrip() {
    }
}
