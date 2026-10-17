package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The cab earnings log's screens: adding a ride (app, how it was paid, fare and km) or a cost,
 * the day's and week's totals by app and by payment, and the list of entries to correct or
 * delete. The log itself and its sums are in {@link Cab}.
 */
final class CabEarnings {

    static final String[] APPS = {"Uber", "Ola", "Rapido", "inDrive", "Namma Yatri", "Street", "Other"};
    static final String[] PAYS = {"Cash", "UPI", "In the app"};
    static final String[] COSTS = {"Fuel or CNG", "Toll", "Parking", "Food", "Commission", "Repair", "Other"};

    /** A line that steps through choices on each tap: "App: Uber  ▸". */
    private static TextView chooser(LinearLayout box, final String label, final String[] choices, final String[] chosen) {
        final TextView t = new TextView(box.getContext());
        t.setTextSize(18);
        t.setPadding(0, Ui.px(12), 0, Ui.px(12));
        t.setText(label + ": " + chosen[0] + "   ▸");
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = java.util.Arrays.asList(choices).indexOf(chosen[0]);
                chosen[0] = choices[(i + 1) % choices.length];
                t.setText(label + ": " + chosen[0] + "   ▸");
            }
        });
        box.addView(t);
        return t;
    }

    static void rideDialog(final HomeActivity a, long fare, final double km, final double lat, final double lon, String pay) {
        final Prefs p = a.prefs;
        LinearLayout box = Cab.box(a);
        final EditText amount = Cab.number(box, "Fare (₹)", fare > 0 ? String.valueOf(fare) : null);
        final String[] app = {p.str("cabLastApp", "Uber")};
        final String[] how = {pay != null ? pay : p.str("cabLastPay", "Cash")};
        chooser(box, "App", APPS, app);
        chooser(box, "Paid by", PAYS, how);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add a ride").setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double v = Cab.num(amount);
                        if (v <= 0) {
                            Toast.makeText(a, "Enter the fare in rupees", Toast.LENGTH_LONG).show();
                            return;
                        }
                        p.put("cabLastApp", app[0]);
                        p.put("cabLastPay", how[0]);
                        Cab.addRide(a, v, km, app[0], how[0], lat, lon);
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static void costDialog(final HomeActivity a) {
        final Prefs p = a.prefs;
        LinearLayout box = Cab.box(a);
        final EditText amount = Cab.number(box, "Amount (₹)", null);
        final String[] what = {p.str("cabLastCost", COSTS[0])};
        chooser(box, "For", COSTS, what);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add a cost").setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        double v = Cab.num(amount);
                        if (v <= 0) {
                            Toast.makeText(a, "Enter the amount in rupees", Toast.LENGTH_LONG).show();
                            return;
                        }
                        p.put("cabLastCost", what[0]);
                        Cab.addCost(p, v, what[0]);
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static String report(Prefs p, long now) {
        List<Cab.Entry> l = Cab.log(p);
        long today = Cab.dayStart(now), week = Cab.weekStart(now);
        StringBuilder b = new StringBuilder();
        Object[][] spans = {{"Today", today, now + 1}, {"Yesterday", Cab.dayStart(today - 3600000), today},
                {"This week (from Monday)", week, now + 1}, {"Last 30 days", today - 29 * 86400000L, now + 1}};
        for (Object[] s : spans) {
            Cab.Sum x = Cab.sum(l, (Long) s[1], (Long) s[2]);
            b.append(s[0]).append(": ").append(Cab.rs(x.net())).append(" net\n   ").append(x.rides).append(x.rides == 1 ? " ride " : " rides ")
                    .append(Cab.rs(x.in)).append(",  costs ").append(Cab.rs(x.out)).append("\n");
        }
        List<Object[]> apps = Cab.by(l, week, now + 1, false), pays = Cab.by(l, today, now + 1, true);
        if (!apps.isEmpty()) {
            b.append("\nThis week by app: ");
            for (int i = 0; i < apps.size(); i++) b.append(i == 0 ? "" : ",  ").append(apps.get(i)[0]).append(" ").append(Cab.rs((Double) apps.get(i)[1]));
        }
        if (!pays.isEmpty()) {
            b.append("\nToday by payment: ");
            for (int i = 0; i < pays.size(); i++) b.append(i == 0 ? "" : ",  ").append(pays.get(i)[0]).append(" ").append(Cab.rs((Double) pays.get(i)[1]));
        }
        int target = p.num("cabTargetRs", 2000);
        if (Cab.on(p, "cabTarget") && target > 0) {
            double net = Cab.sum(l, today, now + 1).net();
            b.append("\n\nDaily target ").append(Cab.rs(target)).append(": ")
                    .append(net >= target ? "reached" : Cab.rs(target - net) + " to go");
        }
        return b.toString().trim();
    }

    /** What "how much did I earn" says. */
    static String spoken(Prefs p) {
        long now = System.currentTimeMillis();
        Cab.Sum s = Cab.sum(Cab.log(p), Cab.dayStart(now), now + 1);
        if (s.rides == 0 && s.out == 0) return "No rides logged today yet.";
        return "Today, " + s.rides + (s.rides == 1 ? " ride, " : " rides, ") + Math.round(s.in) + " rupees in, "
                + Math.round(s.out) + " rupees of costs, " + Math.round(s.net()) + " rupees net.";
    }

    static void earnings(final HomeActivity a) {
        final Prefs p = a.prefs;
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Earnings").setMessage(report(p, System.currentTimeMillis()))
                .setPositiveButton("Add a ride", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        rideDialog(a, 0, 0, 0, 0, null);
                    }
                })
                .setNeutralButton("All entries", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        entries(a);
                    }
                })
                .setNegativeButton("Close", null).show();
    }

    static void entries(final HomeActivity a) {
        final Prefs p = a.prefs;
        final List<Cab.Entry> l = Cab.log(p);
        int n = Math.min(l.size(), 200);
        String[] rows = new String[n];
        SimpleDateFormat f = new SimpleDateFormat("d MMM  HH:mm", Locale.getDefault());
        for (int i = 0; i < n; i++) {
            Cab.Entry e = l.get(l.size() - 1 - i);
            rows[i] = f.format(new Date(e.t)) + "   " + (e.ride ? "+" + Cab.rs(e.rs) + "  " + e.app + ", " + e.pay
                    + (e.km > 0 ? ", " + Fuel.fmt(e.km) + " km" : "") : "−" + Cab.rs(e.rs) + "  " + e.what);
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(n == 0 ? "Nothing logged yet" : "Rides and costs (tap one to delete it)")
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, final int which) {
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setMessage("Delete this entry?")
                                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int w) {
                                        List<Cab.Entry> out = new ArrayList<>(l);
                                        out.remove(l.size() - 1 - which);
                                        Cab.save(p, out);
                                        a.hub.notifyIfChanged(true);
                                    }
                                }).setNegativeButton("Keep", null).show();
                    }
                }).setNegativeButton("Close", null).show();
    }

    private CabEarnings() {
    }
}
