package org.openrath.launcher;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tyre pressure from Bluetooth (BLE) valve-cap sensors of the widespread "TPMS1_xxxxxx" kind,
 * which broadcast pressure, temperature and battery in their advertisement; no pairing needed.
 * Wired TPMS kits sold with head units talk a closed USB protocol and are not supported.
 *
 * Advertisement layout (manufacturer data, 18 bytes): 00 01, sensor address (6, the first byte
 * 0x80..0x83 is the wheel), pressure in Pa (4, little-endian), temperature in 0.01 °C (4,
 * little-endian), battery % (1), alarm flag (1).
 */
final class Tpms {

    static final class Tyre {
        String id;            // sensor address, hex
        float kpa = -1, tempC = Float.NaN;
        int battery = -1;
        long at;
    }

    /** Wheels in order: front left, front right, rear left, rear right. */
    static final String[] WHEELS = {"FL", "FR", "RL", "RR"};
    static final Tyre[] tyres = new Tyre[4];
    /** Every sensor heard recently, by address, for the setup screen. */
    static final Map<String, Tyre> heard = new LinkedHashMap<>();
    static volatile String status = Hindi.t("Off");

    private static ScanCallback cb;

    static void start(final Context c, final Prefs p) {
        if (cb != null) return;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) {
                status = Hindi.t("Bluetooth is off");
                return;
            }
            BluetoothLeScanner sc = a.getBluetoothLeScanner();
            if (sc == null) {
                status = Hindi.t("This unit can't scan for Bluetooth LE");
                return;
            }
            final Context app = c.getApplicationContext();
            cb = new ScanCallback() {
                @Override
                public void onScanResult(int type, ScanResult r) {
                    if (r.getScanRecord() == null) return;
                    Tyre t = parse(r.getScanRecord().getBytes());
                    if (t == null) return;
                    t.at = System.currentTimeMillis();
                    synchronized (heard) {
                        heard.put(t.id, t);
                    }
                    for (int w = 0; w < 4; w++) {
                        if (t.id.equals(p.str("tpms" + w, ""))) {
                            tyres[w] = t;
                            app.sendBroadcast(new Intent(Drive.CHANGED).setPackage(app.getPackageName()));
                        }
                    }
                }

                @Override
                public void onScanFailed(int code) {
                    status = Hindi.f("Scan failed (%1$s)", code);
                }
            };
            sc.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), cb);
            status = Hindi.t("Listening");
        } catch (Throwable e) {
            status = Hindi.f("Can't scan: %1$s", e.getMessage());
            cb = null;
        }
    }

    static void stop() {
        if (cb == null) return;
        try {
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner().stopScan(cb);
        } catch (Throwable ignored) {
        }
        cb = null;
        status = Hindi.t("Off");
    }

    /** One sensor's reading from a raw advertisement, or null if it isn't a TPMS cap. */
    static Tyre parse(byte[] adv) {
        if (adv == null) return null;
        int i = 0;
        while (i < adv.length) {
            int len = adv[i] & 0xFF;
            if (len == 0 || i + len > adv.length - 1) break;   // structure runs past the end
            int type = adv[i + 1] & 0xFF;
            if (type == 0xFF && len - 1 == 18 && adv[i + 2] == 0x00 && adv[i + 3] == 0x01) {
                int d = i + 2;
                Tyre t = new Tyre();
                StringBuilder id = new StringBuilder();
                for (int k = 2; k < 8; k++) id.append(String.format(Locale.US, "%02X", adv[d + k] & 0xFF));
                t.id = id.toString();
                long pa = le32(adv, d + 8);
                long temp = le32(adv, d + 12);
                if (temp > 0x7FFFFFFFL) temp -= 0x100000000L;
                t.kpa = pa / 1000f;
                t.tempC = temp / 100f;
                t.battery = adv[d + 16] & 0xFF;
                if (t.kpa < 0 || t.kpa > 1000) return null;
                return t;
            }
            i += len + 1;
        }
        return null;
    }

    /** The wheel a sensor says it belongs on (its first address byte 0x80..0x83), or -1. */
    static int wheelOf(String id) {
        try {
            int b = Integer.parseInt(id.substring(0, 2), 16);
            return b >= 0x80 && b <= 0x83 ? b - 0x80 : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static long le32(byte[] b, int o) {
        return (b[o] & 0xFFL) | (b[o + 1] & 0xFFL) << 8 | (b[o + 2] & 0xFFL) << 16 | (b[o + 3] & 0xFFL) << 24;
    }

    static float psi(float kpa) {
        return kpa * 0.145038f;
    }

    /** Pressure as the owner chose to see it. */
    static String show(Prefs p, float kpa) {
        if (kpa < 0) return "--";
        return "bar".equals(p.str("tpmsUnit", "psi")) ? String.format(Locale.US, "%.1f", kpa / 100f)
                : String.valueOf(Math.round(psi(kpa)));
    }

    /** A warning for tyre w, or "" when it looks fine. */
    static String warning(Prefs p, int w) {
        Tyre t = tyres[w];
        if (t == null || t.kpa < 0) return "";
        if (System.currentTimeMillis() - t.at > 15 * 60 * 1000) return "";
        if (psi(t.kpa) < p.num("tpmsLow", 28)) return Hindi.f("%1$s tyre low", WHEELS[w]);
        if (t.tempC > p.num("tpmsHot", 75)) return Hindi.f("%1$s tyre hot", WHEELS[w]);
        if (t.battery >= 0 && t.battery < 10) return Hindi.f("%1$s sensor battery low", WHEELS[w]);
        return "";
    }

    private Tpms() {
    }
}
