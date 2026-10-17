package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fuel log: each fill-up has litres, amount paid, odometer and whether the tank was filled.
 * Mileage per tank is the distance between two full fill-ups over the litres put in since the
 * first of them (the standard "full to full" method).
 */
final class Fuel {

    static final class Stats {
        double lastKmpl, avgKmpl, rupeesPerKm;
        int fills;
        long lastAt;
    }

    /** Km per litre between full tanks, by the odometer typed at each fill-up. */
    static java.util.List<Double> fullTankKmpl(JSONArray l) {
        int n = l.length();
        double[] km = new double[n], litres = new double[n];
        boolean[] full = new boolean[n];
        for (int i = 0; i < n; i++) {
            JSONObject e = l.optJSONObject(i);
            km[i] = e.optDouble("odo", e.optDouble("gps", 0));
            litres[i] = e.optDouble("l", 0);
            full[i] = e.optBoolean("full", true);
        }
        return Errands.fullTankKmpl(km, litres, full);
    }

    static JSONArray log(Prefs p) {
        try {
            return new JSONArray(p.fuelLog());
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static Stats stats(Prefs p) {
        JSONArray a = log(p);
        int n = a.length();
        double[] odo = new double[n], litres = new double[n], rupees = new double[n];
        boolean[] full = new boolean[n];
        long[] at = new long[n];
        for (int i = 0; i < n; i++) {
            JSONObject e = a.optJSONObject(i);
            odo[i] = e.optDouble("odo");
            litres[i] = e.optDouble("l");
            rupees[i] = e.optDouble("rs");
            full[i] = e.optBoolean("full", true);
            at[i] = e.optLong("t");
        }
        return compute(odo, litres, rupees, full, at);
    }

    /** Full-to-full mileage from fill-ups in order (no Android types, so it is unit-tested). */
    static Stats compute(double[] odo, double[] litres, double[] rupees, boolean[] full, long[] at) {
        Stats s = new Stats();
        s.fills = odo.length;
        double totalKm = 0, totalL = 0, totalRs = 0;
        int lastFull = -1;
        double litresSince = 0, rupeesSince = 0;
        for (int i = 0; i < odo.length; i++) {
            s.lastAt = at[i];
            if (lastFull >= 0) {
                litresSince += litres[i];
                rupeesSince += rupees[i];
            }
            if (full[i]) {
                if (lastFull >= 0 && litresSince > 0) {
                    double km = odo[i] - odo[lastFull];
                    if (km > 0) {
                        s.lastKmpl = km / litresSince;
                        totalKm += km;
                        totalL += litresSince;
                        totalRs += rupeesSince;
                    }
                }
                lastFull = i;
                litresSince = 0;
                rupeesSince = 0;
            }
        }
        if (totalL > 0) s.avgKmpl = totalKm / totalL;
        if (totalKm > 0) s.rupeesPerKm = totalRs / totalKm;
        return s;
    }

    /**
     * Petrol price per litre: the owner's own figure if set, else the latest fill-up in this city
     * (from the weather's place name), else the latest fill-up anywhere; 0 when unknown.
     */
    static double price(Prefs p) {
        try {
            double own = Double.parseDouble(p.str("fuelPrice", "0"));
            if (own > 0) return own;
        } catch (Exception ignored) {
        }
        JSONArray a = log(p);
        String city = p.str("city", "");
        double any = 0;
        for (int i = a.length() - 1; i >= 0; i--) {
            JSONObject e = a.optJSONObject(i);
            double l = e.optDouble("l", 0), rs = e.optDouble("rs", 0);
            if (l <= 0 || rs <= 0) continue;
            if (!city.isEmpty() && city.equals(e.optString("city"))) return rs / l;
            if (any == 0) any = rs / l;
        }
        return any;
    }

    /** Where the price comes from, for Settings. */
    static String priceText(Prefs p) {
        double v = price(p);
        if (v <= 0) return Hindi.t("not known yet: add a fill-up with the amount paid, or tap to enter it");
        boolean own = !"0".equals(p.str("fuelPrice", "0")) && !p.str("fuelPrice", "0").isEmpty();
        String city = p.str("city", "");
        return own ? Hindi.f("₹%1$s a litre (your figure)", fmt(v)) : city.isEmpty() ? Hindi.f("₹%1$s a litre (from your fill-ups)", fmt(v))
                : Hindi.f("₹%1$s a litre (from your fill-ups, %2$s)", fmt(v), city);
    }

    /** Petrol cost of driving this far, at the average mileage and today's price; 0 when unknown. */
    static double cost(Prefs p, double km) {
        double price = price(p);
        if (price <= 0 || km <= 0) return 0;
        double kmpl = stats(p).avgKmpl;
        return km / (kmpl > 0 ? kmpl : Coach.typical > 0 ? Coach.typical : Coach.DEFAULT_KMPL) * price;
    }

    /** The odometer to suggest: the last entry plus what the GPS has counted since. */
    static double suggestOdo(Prefs p) {
        JSONArray a = log(p);
        if (a.length() == 0) return Math.round(Drive.odoKm);
        JSONObject last = a.optJSONObject(a.length() - 1);
        return Math.round(last.optDouble("odo") + Math.max(0, Drive.odoKm - last.optDouble("gps", Drive.odoKm)));
    }

    static void add(final Activity a, final Prefs p, final Runnable done) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText litres = field(a, box, Hindi.t("Litres"), "");
        double pr = price(p);
        final EditText rupees = field(a, box, pr > 0 ? Hindi.f("Amount paid (₹), or leave empty for ₹%1$s a litre", fmt(pr)) : Hindi.t("Amount paid (₹)"), "");
        final EditText odo = field(a, box, Hindi.t("Odometer (km)"), String.valueOf((long) suggestOdo(p)));
        final CheckBox full = new CheckBox(a);
        full.setText(Hindi.t("Filled the tank to full"));
        full.setChecked(true);
        box.addView(full);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Add a fill-up"))
                .setView(box)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            JSONObject e = new JSONObject();
                            e.put("t", System.currentTimeMillis());
                            e.put("l", Double.parseDouble(litres.getText().toString().trim()));
                            String rs = rupees.getText().toString().trim();
                            // left empty: work it out from the litres and the known price
                            e.put("rs", rs.isEmpty() ? Math.round(e.getDouble("l") * price(p)) : Double.parseDouble(rs));
                            e.put("city", p.str("city", ""));
                            e.put("odo", Double.parseDouble(odo.getText().toString().trim()));
                            e.put("gps", Drive.odoKm);
                            e.put("full", full.isChecked());
                            JSONArray l = log(p);
                            l.put(e);
                            p.setFuelLog(l.toString());
                            done.run();
                            if (full.isChecked() && Features.on(p, "fuelCheck")) {
                                String warn = Errands.fuelDrop(fullTankKmpl(l));
                                if (warn != null) new android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                        .setTitle(Hindi.t("Mileage dropped")).setMessage(warn).setPositiveButton(Hindi.t("OK"), null).show();
                            }
                        } catch (Exception ex) {
                            android.widget.Toast.makeText(a, Hindi.t("Enter the litres and the odometer as numbers"), android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static EditText field(Activity a, LinearLayout box, String label, String value) {
        TextView t = new TextView(a);
        t.setText(label);
        box.addView(t);
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(value);
        box.addView(e);
        return e;
    }

    static void history(final Activity a, final Prefs p, final Runnable changed) {
        final JSONArray l = log(p);
        String[] rows = new String[l.length()];
        SimpleDateFormat f = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        for (int i = 0; i < rows.length; i++) {
            JSONObject e = l.optJSONObject(l.length() - 1 - i);
            rows[i] = f.format(new Date(e.optLong("t"))) + "   " + fmt(e.optDouble("l")) + " L   ₹" + Math.round(e.optDouble("rs"))
                    + "   " + Math.round(e.optDouble("odo")) + " km" + (e.optBoolean("full", true) ? "" : "   " + Hindi.t("(part fill)"));
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(rows.length == 0 ? Hindi.t("No fill-ups yet") : Hindi.t("Fill-ups (tap one to delete it)"))
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, final int which) {
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setMessage(Hindi.t("Delete this fill-up?"))
                                .setPositiveButton(Hindi.t("Delete"), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d2, int w) {
                                        JSONArray out = new JSONArray();
                                        int skip = l.length() - 1 - which;
                                        for (int i = 0; i < l.length(); i++) if (i != skip) out.put(l.opt(i));
                                        p.setFuelLog(out.toString());
                                        changed.run();
                                    }
                                })
                                .setNegativeButton(Hindi.t("Keep"), null).show();
                    }
                })
                .setPositiveButton(Hindi.t("Add a fill-up"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        add(a, p, changed);
                    }
                })
                .setNegativeButton(Hindi.t("Close"), null).show();
    }

    static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private Fuel() {
    }
}
