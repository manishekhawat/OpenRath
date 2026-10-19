package org.openrath.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;

/**
 * Small Wi-Fi, mobile signal, Bluetooth, GPS and battery icons for the dock, because many stereos
 * hide Android's status bar. Bright means working, dim means on but not connected; an icon is left
 * out when the unit has no such thing (no SIM, no battery). Checked every 5 seconds.
 */
final class StatusIcons extends View {

    static final int OFF = 0, SEARCHING = 1, ON = 2;

    /** What the GPS icon shows: a fix in the last 10 seconds, searching, or location switched off. */
    static int gps(long fixAgeSec, boolean enabled) {
        if (fixAgeSec >= 0 && fixAgeSec <= 10) return ON;
        return enabled ? SEARCHING : OFF;
    }

    /** Battery percent from the system's level and scale, or -1 when the unit reports no battery. */
    static int percent(boolean present, int level, int scale) {
        if (!present || level < 0 || scale <= 0) return -1;
        return Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
    }

    /** "4G", "5G", "H+"… for TelephonyManager's network type, or "" when unknown. */
    static String label(int type) {
        switch (type) {
            case 20: return "5G";
            case 13: case 19: return "4G";
            case 15: return "H+";
            case 8: case 9: case 10: return "H";
            case 3: case 5: case 6: case 12: case 14: case 17: return "3G";
            case 2: return "E";
            case 1: case 16: return "G";
            default: return "";
        }
    }

    /** Signal bars, 0 to 4, from a Wi-Fi strength in dBm. */
    static int wifiBars(int rssi) {
        if (rssi <= -100 || rssi == Integer.MAX_VALUE) return 0;
        if (rssi >= -55) return 4;
        if (rssi >= -67) return 3;
        if (rssi >= -78) return 2;
        return 1;
    }

    private final HomeActivity a;
    private final boolean across;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    // -1 = hide this icon
    private int wifi = -1, mobile = -1, bt = -1, gps = -1, battery = -1;
    private String net = "";
    private boolean wifiOn, charging;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            // behind another app, or the screen off: no checks until the dashboard shows again
            if (getWindowVisibility() != VISIBLE) return;
            long before = shownState();
            read();
            if (shownState() != before) invalidate();
            postDelayed(this, 5000);
        }
    };

    /** What the icons show, to redraw only when it changes. */
    private long shownState() {
        return new Stamp().add(wifi).add(mobile).add(bt).add(gps).add(battery).add(net).add(wifiOn).add(charging).value();
    }

    StatusIcons(HomeActivity a, boolean across) {
        super(a);
        this.a = a;
        this.across = across;
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        removeCallbacks(tick);
        if (v == VISIBLE && isAttachedToWindow()) post(tick);
    }

    private void read() {
        Context c = a;
        // Wi-Fi
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiOn = wm != null && wm.isWifiEnabled();
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm == null ? null : cm.getNetworkInfo(android.net.ConnectivityManager.TYPE_WIFI);
            boolean joined = ni != null && ni.isConnected();
            wifi = !wifiOn ? -1 : joined ? Math.max(1, wifiBars(wm.getConnectionInfo().getRssi())) : 0;
        } catch (Exception e) {
            wifi = -1;
        }
        // mobile signal, only with a SIM
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null || tm.getSimState() != android.telephony.TelephonyManager.SIM_STATE_READY) mobile = -1;
            else {
                mobile = level(tm);
                net = label(tm.getNetworkType());
            }
        } catch (Exception e) {
            mobile = -1;
        }
        // Bluetooth: on, and whether a phone or speaker is connected
        try {
            android.bluetooth.BluetoothAdapter ba = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (ba == null || !ba.isEnabled()) bt = -1;
            else {
                boolean linked = false;
                for (int p : new int[]{android.bluetooth.BluetoothProfile.A2DP, android.bluetooth.BluetoothProfile.HEADSET}) {
                    try {
                        if (ba.getProfileConnectionState(p) == android.bluetooth.BluetoothProfile.STATE_CONNECTED) linked = true;
                    } catch (Exception ignored) {
                        // no Bluetooth permission: show not linked
                    }
                }
                bt = linked ? ON : SEARCHING;
            }
        } catch (Exception e) {
            bt = -1;
        }
        // GPS
        boolean enabled = false;
        try {
            android.location.LocationManager lm = (android.location.LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            enabled = lm != null && lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {
            // refreshed often: unknown counts as off
        }
        gps = gps(a.hub == null ? -1 : a.hub.fixAge(), enabled);
        // battery, if the unit has one
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            battery = b == null ? -1 : percent(b.getBooleanExtra("present", false),
                    b.getIntExtra("level", -1), b.getIntExtra("scale", -1));
            charging = b != null && b.getIntExtra("plugged", 0) != 0;
        } catch (Exception e) {
            battery = -1;
        }
        String d = "";
        if (wifi >= 0) d += Hindi.t("Wi-Fi") + " " + (wifi > 0 ? Hindi.t("connected") : Hindi.t("not connected")) + ". ";
        if (bt >= 0) d += "Bluetooth " + (bt == ON ? Hindi.t("connected") : Hindi.t("not connected")) + ". ";
        d += "GPS " + (gps == ON ? Hindi.t("connected") : Hindi.t("not connected")) + ".";
        setContentDescription(d);
    }

    /** Bars 0 to 4 from TelephonyManager.getSignalStrength() (Android 9+, by reflection), else 2. */
    private static int level(android.telephony.TelephonyManager tm) {
        if (Build.VERSION.SDK_INT < 28) return 2;
        try {
            Object s = tm.getClass().getMethod("getSignalStrength").invoke(tm);
            return s == null ? 0 : (Integer) s.getClass().getMethod("getLevel").invoke(s);
        } catch (Throwable t) {
            return 2;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = 0;
        for (int v : new int[]{wifi, mobile, bt, gps, battery}) if (v >= 0) n++;
        if (n == 0) return;
        float s = across ? Math.min(getHeight() * 0.6f, getWidth() / (n * 1.4f)) : Math.min(getWidth() * 0.42f, getHeight() / (n * 1.4f));
        float step = s * 1.4f;
        float x = across ? (getWidth() - step * n) / 2 + (step - s) / 2 : (getWidth() - s) / 2;
        float y = across ? (getHeight() - s) / 2 : (getHeight() - step * n) / 2 + (step - s) / 2;
        ink.setStrokeWidth(Math.max(1.5f, s * 0.1f));
        if (wifi >= 0) { wifi(c, x, y, s); if (across) x += step; else y += step; }
        if (mobile >= 0) { bars(c, x, y, s); if (across) x += step; else y += step; }
        if (bt >= 0) { bt(c, x, y, s); if (across) x += step; else y += step; }
        if (gps >= 0) { pin(c, x, y, s); if (across) x += step; else y += step; }
        if (battery >= 0) cell(c, x, y, s);
    }

    private void colour(boolean bright) {
        ink.setColor(bright ? Ui.SILVER : Ui.mix(Ui.SILVER_DIM, Ui.BACKGROUND, 0.35f));
    }

    private void wifi(Canvas c, float x, float y, float s) {
        ink.setStyle(Paint.Style.STROKE);
        float cx = x + s / 2, by = y + s * 0.9f;
        for (int i = 1; i <= 3; i++) {
            colour(wifi >= i);
            float rr = s * 0.28f * i;
            r.set(cx - rr, by - rr, cx + rr, by + rr);
            c.drawArc(r, 225, 90, false, ink);
        }
        colour(wifi > 0);
        ink.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, by - s * 0.04f, s * 0.07f, ink);
    }

    private void bars(Canvas c, float x, float y, float s) {
        ink.setStyle(Paint.Style.FILL);
        if (!net.isEmpty()) {
            // the network type, small over the left of the bars, dim when there's no data link
            colour(mobile > 0);
            ink.setTextSize(s * 0.42f);
            ink.setTextAlign(Paint.Align.LEFT);
            ink.setFakeBoldText(true);
            c.drawText(net, x, y + s * 0.38f, ink);
            ink.setFakeBoldText(false);
        }
        float w = s / 5.5f;
        for (int i = 0; i < 4; i++) {
            colour(mobile > i);
            float h = s * (0.3f + 0.23f * i);
            r.set(x + i * w * 1.45f, y + s - h, x + i * w * 1.45f + w, y + s);
            c.drawRect(r, ink);
        }
    }

    private void bt(Canvas c, float x, float y, float s) {
        colour(bt == ON);
        ink.setStyle(Paint.Style.STROKE);
        float l = x + s * 0.28f, m = x + s * 0.5f, rr = x + s * 0.72f;
        path.reset();
        path.moveTo(l, y + s * 0.3f);
        path.lineTo(rr, y + s * 0.7f);
        path.lineTo(m, y + s * 0.92f);
        path.lineTo(m, y + s * 0.08f);
        path.lineTo(rr, y + s * 0.3f);
        path.lineTo(l, y + s * 0.7f);
        c.drawPath(path, ink);
    }

    private void pin(Canvas c, float x, float y, float s) {
        colour(gps == ON);
        ink.setStyle(gps == OFF ? Paint.Style.STROKE : Paint.Style.FILL);
        float cx = x + s / 2, rr = s * 0.3f, cy = y + s * 0.36f;
        path.reset();
        r.set(cx - rr, cy - rr, cx + rr, cy + rr);
        path.arcTo(r, 150, 240);
        path.lineTo(cx, y + s);
        path.close();
        c.drawPath(path, ink);
        ink.setColor(Ui.BACKGROUND);
        ink.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, rr * 0.4f, ink);
    }

    private void cell(Canvas c, float x, float y, float s) {
        colour(true);
        if (battery <= 15 && !charging) ink.setColor(0xFFD5525A);
        ink.setStyle(Paint.Style.STROKE);
        float t = y + s * 0.18f, b = y + s * 0.95f, l = x + s * 0.12f, rr = x + s * 0.88f;
        r.set(l, t, rr, b);
        c.drawRoundRect(r, s * 0.08f, s * 0.08f, ink);
        ink.setStyle(Paint.Style.FILL);
        c.drawRect(x + s * 0.38f, y + s * 0.05f, x + s * 0.62f, t, ink);
        float inner = s * 0.08f;
        float top = b - inner - (b - t - 2 * inner) * battery / 100f;
        c.drawRect(l + inner, top, rr - inner, b - inner, ink);
    }
}
