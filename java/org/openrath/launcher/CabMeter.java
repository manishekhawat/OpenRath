package org.openrath.launcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

/**
 * The cab fare meter: its rates from the settings, the distance, waiting time and fare of the
 * running ride, starting and stopping it, and the full-screen fare shown to the passenger, paid
 * in cash or by UPI and saved to the earnings log. The fare sum itself is {@link Cab#fare}.
 */
final class CabMeter {

    static Cab.Rates rates(Prefs p) {
        Cab.Rates r = new Cab.Rates();
        r.base = dbl(p, "cabBase", r.base);
        r.baseKm = dbl(p, "cabBaseKm", r.baseKm);
        r.perKm = dbl(p, "cabPerKm", r.perKm);
        r.waitPerMin = dbl(p, "cabWaitRs", r.waitPerMin);
        r.freeWait = dbl(p, "cabFreeWait", r.freeWait);
        r.nightPct = dbl(p, "cabNightPct", r.nightPct);
        return r;
    }

    private static double dbl(Prefs p, String k, double def) {
        try {
            return Double.parseDouble(p.str(k, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    static double meterKm(Prefs p) {
        return Math.max(0, Drive.odoKm - dbl(p, "cabMeterOdo", Drive.odoKm));
    }

    static long meterWaitMs(Prefs p) {
        if (Cab.waitMs < 0) Cab.waitMs = p.longNum("cabMeterWait", 0);
        return Cab.waitMs;
    }

    static boolean meterNight(Prefs p) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(p.longNum("cabMeterAt", System.currentTimeMillis()));
        return Cab.night(c.get(Calendar.HOUR_OF_DAY)) || Cab.night(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    static long fareNow(Prefs p) {
        return Cab.fare(rates(p), meterKm(p), meterWaitMs(p) / 60000.0, meterNight(p));
    }

    static void startMeter(HomeActivity a) {
        Prefs p = a.prefs;
        long now = System.currentTimeMillis();
        p.putLong("cabMeterAt", now);
        p.put("cabMeterOdo", String.valueOf(Drive.odoKm));
        p.putLong("cabMeterWait", 0);
        p.put("cabMeterLat", String.valueOf(Drive.latNow));
        p.put("cabMeterLon", String.valueOf(Drive.lonNow));
        Cab.waitMs = 0;
        Cab.startDuty(p, now);
        if (Cab.on(p, "cabQuiet") && "on".equals(p.str("cabQuietWithMeter", "on")) && !Cab.quiet) Cab.setQuiet(a, true);
        Cab.version++;
    }

    /** The meter, big, for the passenger to see. */
    static void showMeter(final HomeActivity a) {
        final Prefs p = a.prefs;
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xFF050607);
        TextView head = Ui.text(a, 22, Ui.SILVER_DIM, "sans-serif-medium", Gravity.CENTER);
        head.setText("FARE  ·  किराया");
        col.addView(head);
        final TextView fare = Ui.text(a, 120, 0xFFFFFFFF, "sans-serif-light", Gravity.CENTER);
        col.addView(fare);
        final TextView info = Ui.text(a, 24, Ui.SILVER, "sans-serif", Gravity.CENTER);
        info.setPadding(0, 0, 0, Ui.px(26));
        col.addView(info);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        TextView hide = button(a, "Hide", 0xFF33363C), stop = button(a, "Stop and charge", 0xFF1F6F45);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.px(260), Ui.px(76));
        bp.leftMargin = bp.rightMargin = Ui.px(14);
        row.addView(hide, bp);
        row.addView(stop, bp);
        col.addView(row);
        d.setContentView(col);
        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            @Override
            public void run() {
                if (p.longNum("cabMeterAt", 0) == 0) {
                    d.dismiss();
                    return;
                }
                Cab.Rates r = rates(p);
                fare.setText(Cab.rs(fareNow(p)));
                long w = meterWaitMs(p) / 60000;
                info.setText(Fuel.fmt(meterKm(p)) + " km   ·   " + w + " min waiting   ·   " + Cab.hm(System.currentTimeMillis() - p.longNum("cabMeterAt", 0))
                        + (meterNight(p) ? "\nNight charge +" + Math.round(r.nightPct) + "%" : "")
                        + "\n" + Cab.rs(r.base) + " for the first " + Fuel.fmt(r.baseKm) + " km, then " + Cab.rs(r.perKm) + " a km");
                fare.postDelayed(this, 1000);
            }
        };
        hide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                stopMeter(a);
            }
        });
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface di) {
                fare.removeCallbacks(tick[0]);
            }
        });
        d.show();
        tick[0].run();
    }

    static void stopMeter(final HomeActivity a) {
        final Prefs p = a.prefs;
        if (p.longNum("cabMeterAt", 0) == 0) return;
        final long f = fareNow(p);
        final double km = meterKm(p);
        final double lat = dbl(p, "cabMeterLat", 0), lon = dbl(p, "cabMeterLon", 0);
        long w = meterWaitMs(p) / 60000;
        String said = Fuel.fmt(km) + " km, " + w + " min waiting" + (meterNight(p) ? ", night charge" : "");
        p.putLong("cabMeterAt", 0);
        p.putLong("cabMeterWait", 0);
        Cab.waitMs = 0;
        if (Cab.quiet && "on".equals(p.str("cabQuietWithMeter", "on"))) Cab.setQuiet(a, false);
        Cab.version++;
        final boolean log = Cab.on(p, "cabEarnings");
        final String upi = p.str("upiId", "");
        AlertDialog.Builder b = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Fare " + Cab.rs(f)).setMessage(said + (log ? "\n\nSave it to the earnings log?" : ""))
                .setPositiveButton(log ? "Paid in cash" : "OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (log) Cab.addRide(a, f, km, "Meter", "Cash", lat, lon);
                        a.hub.notifyIfChanged(true);
                    }
                })
                .setNeutralButton(Errands.validVpa(upi) ? "UPI QR" : "Paid by UPI", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (log) Cab.addRide(a, f, km, "Meter", "UPI", lat, lon);
                        if (Errands.validVpa(upi)) Qr.show(a, "Pay " + Cab.rs(f), Errands.upiLink(upi, p.str("upiName", ""), (int) f, "Cab fare"),
                                "Scan with any UPI app (GPay, PhonePe, Paytm, BHIM); the fare is filled in. Pays " + upi + ".");
                        else Toast.makeText(a, "Set your UPI id in Settings > Cab driver to show a QR code", Toast.LENGTH_LONG).show();
                        a.hub.notifyIfChanged(true);
                    }
                });
        if (log) b.setNegativeButton("Don't save", null);
        b.show();
    }

    static TextView button(Context a, String s, int colour) {
        TextView b = Ui.text(a, 22, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        b.setText(s);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(16));
        g.setColor(colour);
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    private CabMeter() {
    }
}
