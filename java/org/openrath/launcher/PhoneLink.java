package org.openrath.launcher;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * When a phone connects by Bluetooth: the driver whose phone it is is picked (driver profiles), and
 * the CarPlay or Android Auto app opens by itself (when switched on). Each driver's phone is chosen
 * from the paired phones in the driver menu.
 */
final class PhoneLink {

    /** The paired phone's name a driver uses, kept as "phoneDriver:<phone name>" = driver. */
    static String driverFor(Prefs p, String phone) {
        return phone == null || phone.isEmpty() ? "" : p.str("phoneDriver:" + phone, "");
    }

    /** Whether to switch driver: the phone belongs to a known driver who isn't the one driving. Pure, for the tests. */
    static boolean switchDriver(String driver, String current, List<String> drivers) {
        return driver != null && !driver.isEmpty() && !driver.equals(current) && drivers.contains(driver);
    }

    private static BroadcastReceiver listener;

    static void listen(final HomeActivity a) {
        if (listener != null) return;
        listener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) return;
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                connected(a, d);
            }
        };
        try {
            Compat.fromSystem(a, listener, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        } catch (Throwable e) {
            listener = null;
            Diag.note("phone connect watch", e);
        }
    }

    static void stop(Context c) {
        if (listener == null) return;
        try {
            c.unregisterReceiver(listener);
        } catch (Throwable ignored) {
            // not registered
        }
        listener = null;
    }

    private static long projectedAt;

    static void connected(HomeActivity a, BluetoothDevice d) {
        String name = "";
        boolean phone = true;
        try {
            if (d != null) {
                name = d.getName() == null ? "" : d.getName();
                android.bluetooth.BluetoothClass k = d.getBluetoothClass();
                phone = k == null || k.getMajorDeviceClass() == android.bluetooth.BluetoothClass.Device.Major.PHONE;
            }
        } catch (SecurityException ignored) {
            // no Bluetooth permission: no name; the projection app may still open
        }
        if (!phone) return;   // an OBD adapter, a light strip or tyre sensors
        Prefs p = a.prefs;
        // projection first: it's what the driver sees; a driver switch rebuilds the dashboard behind it
        if (Features.on(p, "projectionAuto") && System.currentTimeMillis() - projectedAt > 120000
                && Projection.app(a, p) != null) {
            projectedAt = System.currentTimeMillis();
            Projection.open(a, p);
        }
        if (Features.on(p, "profiles") && a.settingsView == null
                && switchDriver(driverFor(p, name), Profiles.current(p), Profiles.names(p))) {
            Profiles.switchTo(a, p, driverFor(p, name));
        }
    }

    /** Settings: which paired phone belongs to the current driver. */
    static void choosePhone(final HomeActivity a) {
        final Prefs p = a.prefs;
        final String driver = Profiles.current(p);
        if (driver.isEmpty()) {
            android.widget.Toast.makeText(a, Hindi.t("Add drivers in Settings > Family first"), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> names = new ArrayList<>();
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null) for (BluetoothDevice d : bt.getBondedDevices()) if (d.getName() != null) names.add(d.getName());
        } catch (SecurityException e) {
            Diag.note("paired phones", e);
        }
        if (names.isEmpty()) {
            android.widget.Toast.makeText(a, Hindi.t("No paired phones yet. Pair your phone in the Bluetooth app."), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String[] rows = new String[names.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = (driver.equals(driverFor(p, names.get(i))) ? "✓  " : "     ") + names.get(i);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.f("%1$s's phone", driver))
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        p.put("phoneDriver:" + names.get(w), driver);
                        android.widget.Toast.makeText(a, Hindi.f("%1$s's settings load when this phone connects", driver),
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private PhoneLink() {
    }
}
