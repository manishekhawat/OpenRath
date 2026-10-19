package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/**
 * The screens for the everyday helpers: splitting a trip's cost by UPI QR code, a paid-parking
 * reminder on the phone, the car wash reminder and the start-up summary. The sums and links are
 * in Errands, where they are tested.
 */
final class Chores {

    private static final int DIALOG = android.R.style.Theme_Material_Dialog_Alert;

    static void text(HomeActivity a, String title, String hint, String cur, int type, final Extras.Num then) {
        final EditText e = new EditText(a);
        e.setInputType(type);
        e.setHint(hint);
        e.setText(cur);
        int pad = Ui.px(20);
        FrameLayout box = new FrameLayout(a);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        new AlertDialog.Builder(a, DIALOG).setTitle(title).setView(box)
                .setPositiveButton(Hindi.t("OK"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        then.set(e.getText().toString().trim());
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    // ------------------------------------------------------------------ splitting the cost

    /** The last fill-up's rupees, as the usual thing to split; 0 when there is none. */
    private static double lastFill(Prefs p) {
        JSONArray l = Fuel.log(p);
        JSONObject e = l.length() == 0 ? null : l.optJSONObject(l.length() - 1);
        return e == null ? 0 : e.optDouble("rs", 0);
    }

    static void split(final HomeActivity a) {
        final Prefs p = a.prefs;
        if (!Features.on(p, "split")) {
            Toast.makeText(a, Hindi.t("Turn on \"Split the cost by UPI\" in Settings > Features"), Toast.LENGTH_LONG).show();
            return;
        }
        if (!Errands.validVpa(p.str("upiId", ""))) {
            text(a, Hindi.t("Your UPI id (like name@okbank)"), "name@bank", p.str("upiId", ""), InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, new Extras.Num() {
                @Override
                public void set(String v) {
                    if (!Errands.validVpa(v)) {
                        Toast.makeText(a, Hindi.t("That doesn't look like a UPI id (name@bank)"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    p.put("upiId", v);
                    split(a);
                }
            });
            return;
        }
        double fill = lastFill(p);
        Extras.number(a, Hindi.t(fill > 0 ? "Total to split, ₹ (last fill-up shown)" : "Total to split, ₹"), fill > 0 ? String.valueOf(Math.round(fill)) : "",
                new Extras.Num() {
                    @Override
                    public void set(String v) {
                        if (v.isEmpty()) return;
                        final double total = Double.parseDouble(v);
                        Extras.number(a, Hindi.t("How many people, you included?"), p.str("splitPeople", "2"), new Extras.Num() {
                            @Override
                            public void set(String n) {
                                int people = n.isEmpty() ? 2 : (int) Double.parseDouble(n);
                                if (people < 2) {
                                    Toast.makeText(a, Hindi.t("At least 2 people"), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                p.put("splitPeople", String.valueOf(people));
                                int each = Errands.share(total, people);
                                Qr.show(a, Hindi.f("Pay ₹%1$s each", each), Errands.upiLink(p.str("upiId", ""), p.str("upiName", ""), each, "Trip share"),
                                        Hindi.f("₹%1$s split %2$s ways. Each person scans this with any UPI app "
                                                + "(GPay, PhonePe, Paytm, BHIM); the amount is filled in. Pays %3$s.", Math.round(total), people, p.str("upiId", "")));
                            }
                        });
                    }
                });
    }

    // ------------------------------------------------------------------ paid parking

    static void parkingTimer(final HomeActivity a) {
        final Prefs p = a.prefs;
        if (!Features.on(p, "parkTimer")) {
            Toast.makeText(a, Hindi.t("Turn on \"Paid parking reminder\" in Settings > Features"), Toast.LENGTH_LONG).show();
            return;
        }
        final int[] mins = {30, 60, 120, 180, 240};
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("Parking paid for"))
                .setItems(new String[]{Hindi.t("30 minutes"), Hindi.t("1 hour"), Hindi.t("2 hours"), Hindi.t("3 hours"), Hindi.t("4 hours")}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        long now = System.currentTimeMillis();
                        long end = now + mins[w] * 60000L;
                        p.putLong("parkEnds", end);
                        Qr.show(a, Hindi.f("Parking ends at %1$s", Errands.clock(end)), Errands.parkingLink(now, mins[w]),
                                Hindi.t("Scan with your phone to add a reminder to its calendar, 10 minutes before the parking runs out. "
                                        + "The car's screen switches off with the engine, so the phone does the reminding."));
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    // ------------------------------------------------------------------ weekly report

    /** Sundays (or the first start after a missed one): the last seven days in one card. */
    static void weekly(final HomeActivity a, boolean now) {
        Prefs p = a.prefs;
        if (!now && (!Features.on(p, "weekly") || Drive.kmhNow > 3)) return;
        long t = System.currentTimeMillis(), last = p.longNum("weeklyAt", 0);
        if (!now) {
            if (last == 0) {
                p.putLong("weeklyAt", t);
                return;
            }
            boolean sunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
            if (!(sunday && t - last > 6 * 86400000L || t - last > 8 * 86400000L)) return;
            p.putLong("weeklyAt", t);
        }
        long from = t - 7 * 86400000L;
        JSONObject days = Drive.history(a);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        double km = 0;
        long sec = 0;
        int scoreSum = 0, scored = 0;
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(from);
        for (int i = 0; i < 8; i++) {
            JSONArray r = days.optJSONArray(f.format(d.getTime()));
            if (r != null) {
                km += r.optDouble(0);
                sec += r.optLong(1);
                if (r.optDouble(0) > 1 && r.length() > 5) {
                    scoreSum += r.optInt(5);
                    scored++;
                }
            }
            d.add(Calendar.DAY_OF_MONTH, 1);
        }
        // the smoothest and the most wasteful drive, by fuel lost to habits per km
        double kmpl = FuelPlan.kmpl(p);
        Coach.Trip best = null, worst = null;
        double bestW = Double.MAX_VALUE, worstW = -1;
        for (Coach.Trip tr : Coach.trips(p)) {
            if (tr.start < from || tr.km < 3) continue;
            double w = Coach.total(Coach.waste(tr, kmpl)) / tr.km;
            if (w < bestW) {
                bestW = w;
                best = tr;
            }
            if (w > worstW) {
                worstW = w;
                worst = tr;
            }
        }
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("EEE", java.util.Locale.US);
        String b = best == null ? "" : df.format(new java.util.Date(best.start)) + ", " + Math.round(best.km) + " km: "
                + Coach.reasons(best, Coach.waste(best, kmpl));
        String w = worst == null || worst == best ? "" : df.format(new java.util.Date(worst.start)) + ", " + Math.round(worst.km) + " km: "
                + Coach.reasons(worst, Coach.waste(worst, kmpl));
        String text = Errands.weekly(km, sec, Care.spent(Fuel.log(p), from, t), Care.spent(Care.tolls(p), from, t),
                scored > 0 ? scoreSum / scored : -1, b, w);
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("Your week in the car")).setMessage(text).setPositiveButton(Hindi.t("OK"), null).show();
    }

    // ------------------------------------------------------------------ parking photo

    static void parkPhoto(HomeActivity a) {
        java.io.File f = Snap.file();
        if (!f.exists()) {
            Toast.makeText(a, Hindi.t(Dashcam.cameras() == 0 ? "No camera on this unit, so no parking photo"
                    : "No parking photo yet: one is taken when you park"), Toast.LENGTH_LONG).show();
            return;
        }
        final android.app.Dialog d = new android.app.Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        android.widget.ImageView img = new android.widget.ImageView(a);
        img.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        img.setImageBitmap(android.graphics.BitmapFactory.decodeFile(f.getPath()));
        img.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                d.dismiss();
            }
        });
        d.setContentView(img);
        d.show();
        Toast.makeText(a, Hindi.f("Taken %1$s. Tap to close.", new java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.US).format(new java.util.Date(f.lastModified()))), Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ guest Wi-Fi

    /** Show a QR code passengers scan to join the unit's hotspot, after turning the hotspot on. */
    static void hotspot(final HomeActivity a) {
        final Prefs p = a.prefs;
        if (!Features.on(p, "hotspot")) {
            Toast.makeText(a, Hindi.t("Turn on \"Guest Wi-Fi\" in Settings > Features"), Toast.LENGTH_LONG).show();
            return;
        }
        if (p.str("apName", "").isEmpty()) {
            text(a, Hindi.t("Hotspot name (as set on the unit)"), Hindi.t("e.g. MyCar"), "", InputType.TYPE_CLASS_TEXT, new Extras.Num() {
                @Override
                public void set(final String name) {
                    if (name.isEmpty()) return;
                    text(a, Hindi.t("Hotspot password"), "", "", InputType.TYPE_CLASS_TEXT, new Extras.Num() {
                        @Override
                        public void set(String pass) {
                            p.put("apName", name);
                            p.put("apPass", pass);
                            hotspot(a);
                        }
                    });
                }
            });
            return;
        }
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("Guest Wi-Fi"))
                .setMessage(Hindi.t("Android only lets the unit's own settings switch the hotspot on. Turn it on there, come back, "
                        + "then show the code for passengers to join."))
                .setPositiveButton(Hindi.t("Show the code"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Qr.show(a, Hindi.t("Join the car's Wi-Fi"), Errands.wifiQr(p.str("apName", ""), p.str("apPass", "")),
                                Hindi.f("Point a phone camera at this to join \"%1$s\". It shares the unit's mobile data.", p.str("apName", "")));
                    }
                })
                .setNeutralButton(Hindi.t("Hotspot settings"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String[][] tries = {{"com.android.settings", "com.android.settings.TetherSettings"},
                                {"com.android.settings", "com.android.settings.Settings$TetherSettingsActivity"}};
                        for (String[] t : tries) {
                            try {
                                a.startActivity(new android.content.Intent().setClassName(t[0], t[1]));
                                return;
                            } catch (Throwable ignored) {
                            }
                        }
                        try {
                            a.startActivity(new android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
                        } catch (Throwable e) {
                            Toast.makeText(a, Hindi.t("Open the unit's Settings > Network > Hotspot"), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(Hindi.t("Close"), null).show();
    }

    // ------------------------------------------------------------------ start-up summary

    static void summary(HomeActivity a) {
        Prefs p = a.prefs;
        if (!Features.on(p, "startSummary")) return;
        if (Setup.showing != null) return;   // not over the setup screens; it comes once they're done
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (today.equals(p.str("summaryDay", ""))) return;
        p.put("summaryDay", today);
        // the next holiday, if it falls within a week (the first one on or after a week from now differs)
        List<String> next = Holidays.upcoming(Calendar.getInstance(), 1);
        Calendar wk = Calendar.getInstance();
        wk.add(Calendar.DAY_OF_MONTH, 7);
        List<String> later = Holidays.upcoming(wk, 1);
        String h = !next.isEmpty() && (later.isEmpty() || !later.get(0).equals(next.get(0))) ? next.get(0) : "";
        String text = Errands.summary(Calendar.getInstance(), Profiles.current(p), a.hub.temp, a.hub.weather,
                Features.on(p, "rain") ? a.hub.rainSoon : "", Features.on(p, "aqi") ? Air.aqi : -1,
                Features.on(p, "fuelPlan") ? FuelPlan.rangeKm(p) : -1, Features.on(p, "care") ? Care.alert(p) : "", h);
        Toast.makeText(a, text, Toast.LENGTH_LONG).show();
        if (!"text".equals(p.str("summaryHow", "voice"))) a.speak(text);
    }

    private Chores() {
    }
}
