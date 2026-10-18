package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Cab driver page in Settings: the daily target and break reminder, fare meter rates, UPI
 * id, quiet ride, SOS contacts, vehicle number, papers, the phrase language and clearing the
 * earnings log.
 */
final class CabSettings {

    static void rows(final HomeActivity a, List<SettingsView.Row> r) {
        final Prefs p = a.prefs;
        final String g = "Cab";
        if (!Features.on(p, "cab")) {
            r.add(new SettingsView.Row(g, "Cab driver tools are off", "switch them on above", null));
            return;
        }
        final Runnable redo = new Runnable() {
            @Override
            public void run() {
                if (a.settingsView != null) a.settingsView.refresh();
            }
        };
        if (Features.on(p, "cabTarget")) {
            r.add(new SettingsView.Row(g, "Daily target", Hindi.f("%1$s net", Cab.rs(p.num("cabTargetRs", 2000))), new Runnable() {
                @Override
                public void run() {
                    ask(a, "Daily target (₹ net)", String.valueOf(p.num("cabTargetRs", 2000)), "cabTargetRs", redo);
                }
            }));
            r.add(new SettingsView.Row(g, "Break reminder after", Hindi.f("%1$s hours on duty", p.num("cabDutyH", 10)), new Runnable() {
                @Override
                public void run() {
                    int[] steps = {8, 9, 10, 11, 12, 0};
                    int cur = p.num("cabDutyH", 10), next = 10;
                    for (int k = 0; k < steps.length; k++) if (steps[k] == cur) next = steps[(k + 1) % steps.length];
                    p.put("cabDutyH", String.valueOf(next));
                    redo.run();
                }
            }));
        }
        if (Features.on(p, "cabMeter")) {
            final Cab.Rates rt = CabMeter.rates(p);
            r.add(new SettingsView.Row(g, "Fare meter rates", Hindi.f("%1$s for %2$s km, %3$s/km, waiting %4$s/min after %5$s min, night +%6$s%%",
                    Cab.rs(rt.base), Fuel.fmt(rt.baseKm), Cab.rs(rt.perKm), Cab.rs(rt.waitPerMin), Math.round(rt.freeWait), Math.round(rt.nightPct)), new Runnable() {
                @Override
                public void run() {
                    meterRates(a, redo);
                }
            }));
        }
        if (Features.on(p, "cabMeter") || Features.on(p, "cabEarnings")) {
            String upi = p.str("upiId", "");
            r.add(new SettingsView.Row(g, "UPI id for fares", upi.isEmpty() ? "not set: the meter can show a QR code to pay you" : upi, new Runnable() {
                @Override
                public void run() {
                    final EditText e = new EditText(a);
                    e.setHint("name@bank");
                    e.setText(p.str("upiId", ""));
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle(Hindi.t("Your UPI id")).setView(e)
                            .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    String v = e.getText().toString().trim();
                                    if (!v.isEmpty() && !Errands.validVpa(v)) {
                                        Toast.makeText(a, Hindi.t("That doesn't look like a UPI id (name@bank)"), Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    p.put("upiId", v);
                                    redo.run();
                                }
                            }).setNegativeButton(Hindi.t("Cancel"), null).show();
                }
            }));
        }
        if (Features.on(p, "cabQuiet")) {
            r.add(new SettingsView.Row(g, "Quiet ride volume", Hindi.f("%1$s%% of full", p.num("cabQuietPct", 30)), new Runnable() {
                @Override
                public void run() {
                    int v = p.num("cabQuietPct", 30);
                    p.put("cabQuietPct", String.valueOf(v >= 50 ? 10 : v + 10));
                    redo.run();
                }
            }));
            r.add(new SettingsView.Row(g, "Quiet ride with the meter", "on".equals(p.str("cabQuietWithMeter", "on"))
                    ? "starts and ends with the fare meter" : "only from the Cab menu", new Runnable() {
                @Override
                public void run() {
                    p.put("cabQuietWithMeter", "on".equals(p.str("cabQuietWithMeter", "on")) ? "off" : "on");
                    redo.run();
                }
            }));
        }
        if (Features.on(p, "cabSos")) {
            String[] c1 = CabSos.contact(p, 1), c2 = CabSos.contact(p, 2);
            r.add(new SettingsView.Row(g, "SOS contacts", c1 == null && c2 == null ? "not set"
                    : (c1 != null ? c1[0] : "") + (c1 != null && c2 != null ? ", " : "") + (c2 != null ? c2[0] : ""), new Runnable() {
                @Override
                public void run() {
                    CabSos.editContacts(a, redo);
                }
            }));
        }
        if (Features.on(p, "cabSos") || Features.on(p, "cabMeter")) {
            r.add(new SettingsView.Row(g, "Vehicle number", p.str("cabVehicle", "").isEmpty() ? "not set (goes in the SOS text)" : p.str("cabVehicle", ""), new Runnable() {
                @Override
                public void run() {
                    final EditText e = new EditText(a);
                    e.setHint(Hindi.t("e.g. DL 1R AB 1234"));
                    e.setText(p.str("cabVehicle", ""));
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle(Hindi.t("Vehicle number")).setView(e)
                            .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    p.put("cabVehicle", e.getText().toString().trim().toUpperCase(Locale.US));
                                    redo.run();
                                }
                            }).setNegativeButton(Hindi.t("Cancel"), null).show();
                }
            }));
        }
        if (Features.on(p, "cabPapers")) {
            long now = System.currentTimeMillis();
            int set = 0;
            String soonest = "";
            long best = Long.MAX_VALUE;
            for (int i = 0; i < Cab.PAPERS.length; i++) {
                long d = Cab.daysTo(p.str(Cab.paperKey(Cab.PAPERS[i]), ""), now);
                if (d == Long.MIN_VALUE) continue;
                set++;
                if (d < best) {
                    best = d;
                    soonest = Cab.dueShow(Cab.PAPER_NAMES[i], d);
                }
            }
            r.add(new SettingsView.Row(g, "Papers due dates", set == 0 ? "permit, fitness, badge, road tax, insurance, PUC: not set" : soonest + Hindi.f("  (%1$s of 6 set)", set),
                    new Runnable() {
                        @Override
                        public void run() {
                            Cab.papers(a, redo);
                        }
                    }));
        }
        if (Features.on(p, "cabPhrases")) {
            int li = java.util.Arrays.asList(Cab.LANGS).indexOf(p.str("cabLang", "hi"));
            r.add(new SettingsView.Row(g, "Phrases in", Cab.LANG_NAMES[Math.max(0, li)], new Runnable() {
                @Override
                public void run() {
                    int i = java.util.Arrays.asList(Cab.LANGS).indexOf(p.str("cabLang", "hi"));
                    p.put("cabLang", Cab.LANGS[(Math.max(0, i) + 1) % Cab.LANGS.length]);
                    redo.run();
                }
            }));
        }
        if (Features.on(p, "cabEarnings")) {
            r.add(new SettingsView.Row(g, "Clear the earnings log", Hindi.f("%1$s entries", Cab.log(p).size()), new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setMessage(Hindi.t("Delete every ride and cost in the log?"))
                            .setPositiveButton(Hindi.t("Delete all"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    Cab.save(p, new ArrayList<Cab.Entry>());
                                    redo.run();
                                }
                            }).setNegativeButton(Hindi.t("Keep"), null).show();
                }
            }));
        }
    }

    private static void ask(final HomeActivity a, String title, String value, final String key, final Runnable done) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(value);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t(title)).setView(e)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            a.prefs.put(key, String.valueOf(Integer.parseInt(e.getText().toString().trim())));
                            done.run();
                        } catch (Exception ex) {
                            Toast.makeText(a, Hindi.t("Enter a number"), Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static void meterRates(final HomeActivity a, final Runnable done) {
        final Prefs p = a.prefs;
        Cab.Rates r = CabMeter.rates(p);
        LinearLayout box = Cab.box(a);
        final String[] keys = {"cabBase", "cabBaseKm", "cabPerKm", "cabWaitRs", "cabFreeWait", "cabNightPct"};
        String[] labels = {"Base fare (₹)", "Km included in the base fare", "₹ a km after that", "Waiting ₹ a minute",
                "Free waiting minutes", "Night charge, 11 pm to 5 am (%)"};
        double[] vals = {r.base, r.baseKm, r.perKm, r.waitPerMin, r.freeWait, r.nightPct};
        final EditText[] f = new EditText[keys.length];
        for (int i = 0; i < keys.length; i++) {
            TextView t = new TextView(a);
            t.setText(Hindi.t(labels[i]));
            box.addView(t);
            f[i] = Cab.number(box, "", Fuel.fmt(vals[i]).replace(".0", ""));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Fare meter rates")).setView(sv)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (int i = 0; i < keys.length; i++) {
                            double v = Cab.num(f[i]);
                            if (v >= 0) p.put(keys[i], String.valueOf(v));
                        }
                        done.run();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private CabSettings() {
    }
}
