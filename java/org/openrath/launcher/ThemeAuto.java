package org.openrath.launcher;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.List;

/**
 * The theme changes by itself: a night theme from sunset to sunrise, and a theme of its own when a
 * chosen phone connects by Bluetooth (each driver's look). The phone wins over the night theme.
 * Checked with the deep-sleep check, every 15 seconds.
 */
final class ThemeAuto {

    /** The phone connected now, by Bluetooth name, or "". */
    static volatile String phone = "";
    private static BroadcastReceiver listener;

    /** Which theme to show: the phone's, else the night one at night, else the owner's own (null). */
    static String pick(String nightTheme, String phoneName, String phoneTheme, boolean night, String connected) {
        if (!phoneTheme.isEmpty() && !phoneName.isEmpty() && phoneName.equalsIgnoreCase(connected)) return phoneTheme;
        if (night && !nightTheme.isEmpty()) return nightTheme;
        return null;
    }

    /** Night at the dashboard's position, or by the clock (a may be null). */
    static boolean night(HomeActivity a, long now) {
        android.location.Location f = a == null || a.hub == null ? null : a.hub.fix;
        if (f != null) return Care.dark(f.getLatitude(), f.getLongitude(), now);
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 19 || h < 6;
    }

    /** Listen for phones connecting and leaving. */
    static void listen(Context c) {
        if (listener != null) return;
        listener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String n = "";
                try {
                    n = d == null || d.getName() == null ? "" : d.getName();
                } catch (SecurityException ignored) {
                    // no Bluetooth permission: no name
                }
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) phone = n;
                else if (n.equals(phone)) phone = "";
            }
        };
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        try {
            c.getApplicationContext().registerReceiver(listener, f);
        } catch (Throwable e) {
            Diag.note("theme Bluetooth watch", e);
        }
    }

    /** Switch the theme when it should change; called every 15 seconds while the dashboard shows. */
    static void check(HomeActivity a) {
        Prefs p = a.prefs;
        String want = null;
        if (Features.on(p, "autoTheme")) {
            listen(a);
            want = pick(p.str("nightTheme", ""), p.str("phoneThemeName", ""), p.str("phoneTheme", ""),
                    night(a, System.currentTimeMillis()), phone);
        }
        String now = Theme.override;
        if (want == null ? now == null : want.equals(now)) return;
        Theme.override = want;
        if (a.settingsView == null) a.restyle();
    }

    /** Settings: the night theme, and a theme for one phone. */
    static void menu(final HomeActivity a) {
        final List<Theme.Pack> packs = Theme.available(a);
        final String[] names = new String[packs.size() + 1];
        names[0] = Hindi.t("None (keep my theme)");
        for (int i = 0; i < packs.size(); i++) names[i + 1] = packs.get(i).name;
        String[] items = {Hindi.f("At night: %1$s", nameOf(packs, a.prefs.str("nightTheme", ""))),
                Hindi.f("When %1$s connects: %2$s", a.prefs.str("phoneThemeName", "").isEmpty() ? Hindi.t("a phone") : a.prefs.str("phoneThemeName", ""),
                        nameOf(packs, a.prefs.str("phoneTheme", "")))};
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Theme changes by itself"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) choose(a, "nightTheme", packs, names);
                        else choosePhone(a, packs, names);
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    private static String nameOf(List<Theme.Pack> packs, String id) {
        for (Theme.Pack k : packs) if (k.id.equals(id)) return k.name;
        return Hindi.t("none");
    }

    private static void choose(final HomeActivity a, final String key, final List<Theme.Pack> packs, String[] names) {
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Which theme?"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        a.prefs.put(key, w == 0 ? "" : packs.get(w - 1).id);
                        Features.set(a.prefs, "autoTheme", true);
                        check(a);
                    }
                }).show();
    }

    private static void choosePhone(final HomeActivity a, final List<Theme.Pack> packs, final String[] names) {
        final List<String> devs = Obd.paired();
        if (devs.isEmpty()) {
            android.widget.Toast.makeText(a, Hindi.t("Pair the phone with the unit first"), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String[] shown = new String[devs.size()];
        for (int i = 0; i < shown.length; i++) shown[i] = devs.get(i).split("\\|")[0];
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Which phone?"))
                .setItems(shown, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        a.prefs.put("phoneThemeName", devs.get(w).split("\\|")[0]);
                        choose(a, "phoneTheme", packs, names);
                    }
                }).show();
    }

    private ThemeAuto() {
    }
}
