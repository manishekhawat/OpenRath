package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.DatePicker;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Looking after the car: service reminders (by distance from the odometer, or by date), toll
 * spending, the trip history and sunset times for night dimming.
 */
final class Care {

    // ------------------------------------------------------------------ odometer

    /** The car's odometer: what was entered once (or at the last fill-up) plus what the GPS has counted since. */
    static long odo(Prefs p) {
        String off = p.str("odoOffset", "");
        if (!off.isEmpty()) {
            try {
                return Math.round(Drive.odoKm + Double.parseDouble(off));
            } catch (Exception ignored) {
            }
        }
        return Math.round(Fuel.suggestOdo(p));
    }

    static void setOdo(Prefs p, double km) {
        p.put("odoOffset", String.valueOf(km - Drive.odoKm));
    }

    static void editOdo(final Activity a, final Prefs p, final Runnable done) {
        number(a, "Odometer reading now (km)", String.valueOf(odo(p)), new Num() {
            @Override
            public void got(double v) {
                setOdo(p, v);
                done.run();
            }
        });
    }

    // ------------------------------------------------------------------ service reminders

    static final String[] ITEMS = {"oil", "tyres", "alignment", "service", "insurance", "puc"};
    static final String[] NAMES = {"Oil change", "Tyre rotation", "Wheel alignment and balancing", "General service",
            "Insurance renewal", "PUC certificate"};
    private static final int[] EVERY_KM = {10000, 10000, 5000, 10000, 0, 0};

    static boolean byDate(int i) {
        return EVERY_KM[i] == 0;
    }

    static int every(Prefs p, int i) {
        return p.num("care:" + ITEMS[i] + ":every", EVERY_KM[i]);
    }

    /** Km left to the next one (negative = overdue), or Long.MIN_VALUE if never set. */
    static long kmLeft(Prefs p, int i) {
        String last = p.str("care:" + ITEMS[i] + ":last", "");
        if (last.isEmpty()) return Long.MIN_VALUE;
        return Long.parseLong(last) + every(p, i) - odo(p);
    }

    /** Days left to a dated item (negative = overdue), or Long.MIN_VALUE if not set. */
    static long daysLeft(Prefs p, int i) {
        String due = p.str("care:" + ITEMS[i] + ":due", "");
        if (due.isEmpty()) return Long.MIN_VALUE;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(due);
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            return Math.round((d.getTime() - today.getTimeInMillis()) / 86400000.0);
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    static String describe(Prefs p, int i) {
        if (byDate(i)) {
            long d = daysLeft(p, i);
            if (d == Long.MIN_VALUE) return Hindi.t("due date not set");
            String due = p.str("care:" + ITEMS[i] + ":due", "");
            return d < 0 ? Hindi.f("overdue since %1$s", due) : d == 0 ? Hindi.t("due today") : Hindi.f("due %1$s (%2$s days)", due, d);
        }
        long k = kmLeft(p, i);
        if (k == Long.MIN_VALUE) return Hindi.f("every %1$s km; last one not set", every(p, i));
        return k < 0 ? Hindi.f("overdue by %1$s km", -k) : Hindi.f("in %1$s km (every %2$s km)", k, every(p, i));
    }

    /** The most urgent reminder for the car box, or "" when nothing is close. */
    static String alert(Prefs p) {
        String best = "";
        long bestScore = Long.MAX_VALUE;
        for (int i = 0; i < ITEMS.length; i++) {
            long score;
            String text;
            if (byDate(i)) {
                long d = daysLeft(p, i);
                if (d == Long.MIN_VALUE || d > 15) continue;
                score = d * 40;   // 15 days ≈ 600 km of urgency
                text = NAMES[i].toUpperCase(Locale.US) + (d < 0 ? " OVERDUE" : d == 0 ? " DUE TODAY" : " DUE IN " + d + (d == 1 ? " DAY" : " DAYS"));
            } else {
                long k = kmLeft(p, i);
                if (k == Long.MIN_VALUE || k > 500) continue;
                score = k;
                text = NAMES[i].toUpperCase(Locale.US) + (k < 0 ? " OVERDUE" : " DUE IN " + k + " KM");
            }
            if (score < bestScore) {
                bestScore = score;
                best = text;
            }
        }
        return best;
    }

    /** A km-based service due within 300 km (or overdue), for the booking reminder: its index, or -1. */
    static int bookingDue(Prefs p) {
        int best = -1;
        long bestKm = Long.MAX_VALUE;
        for (int i = 0; i < ITEMS.length; i++) {
            if (byDate(i) || "tyres".equals(ITEMS[i]) || "alignment".equals(ITEMS[i])) continue;
            long k = kmLeft(p, i);
            if (k == Long.MIN_VALUE || k > 300) continue;
            if (k < bestKm) {
                bestKm = k;
                best = i;
            }
        }
        return best;
    }

    /** Tapping a reminder in the settings: mark it done, or change when it's due. */
    static void edit(final Activity a, final Prefs p, final int i, final Runnable done) {
        final String key = "care:" + ITEMS[i];
        if (byDate(i)) {
            Calendar c = Calendar.getInstance();
            try {
                String due = p.str(key + ":due", "");
                if (!due.isEmpty()) c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(due));
                else c.add(Calendar.MONTH, 6);
            } catch (Exception ignored) {
            }
            DatePickerDialog d = new DatePickerDialog(a, android.R.style.Theme_Material_Dialog_Alert, new DatePickerDialog.OnDateSetListener() {
                @Override
                public void onDateSet(DatePicker v, int y, int m, int day) {
                    p.put(key + ":due", String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day));
                    done.run();
                }
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            d.setTitle(Hindi.f("%1$s: next due date", Hindi.t(NAMES[i])));
            d.show();
            return;
        }
        String[] opts = {Hindi.f("Done today (at %1$s km)", odo(p)), Hindi.t("Last done at a different km"), Hindi.f("Change how often (now every %1$s km)", every(p, i))};
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t(NAMES[i]))
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dlg, int w) {
                        if (w == 0) {
                            p.put(key + ":last", String.valueOf(odo(p)));
                            done.run();
                        } else if (w == 1) {
                            number(a, "Odometer when it was last done (km)", String.valueOf(odo(p)), new Num() {
                                @Override
                                public void got(double v) {
                                    p.put(key + ":last", String.valueOf(Math.round(v)));
                                    done.run();
                                }
                            });
                        } else {
                            number(a, "Every how many km?", String.valueOf(every(p, i)), new Num() {
                                @Override
                                public void got(double v) {
                                    if (v >= 500) p.put(key + ":every", String.valueOf(Math.round(v)));
                                    done.run();
                                }
                            });
                        }
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    // ------------------------------------------------------------------ tolls

    static JSONArray tolls(Prefs p) {
        try {
            return new JSONArray(p.str("tolls", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void addToll(final Activity a, final Prefs p, final Runnable done) {
        number(a, "Toll paid (₹)", "", new Num() {
            @Override
            public void got(double v) {
                try {
                    JSONArray l = tolls(p);
                    l.put(new JSONObject().put("t", System.currentTimeMillis()).put("rs", v));
                    p.put("tolls", l.toString());
                } catch (Exception ignored) {
                }
                done.run();
            }
        });
    }

    /** Rupees spent on tolls (or fuel) between two times. */
    static double spent(JSONArray l, long from, long to) {
        double s = 0;
        for (int i = 0; i < l.length(); i++) {
            JSONObject e = l.optJSONObject(i);
            long t = e.optLong("t");
            if (t >= from && t < to) s += e.optDouble("rs");
        }
        return s;
    }

    // ------------------------------------------------------------------ trip history

    /** Weekly and monthly totals from the day-by-day log kept by the trip service. */
    static void history(Activity a, Prefs p) {
        JSONObject days = Drive.history(a);
        JSONArray fuel = Fuel.log(p), toll = tolls(p);
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Calendar weekStart = (Calendar) c.clone();
        weekStart.set(Calendar.DAY_OF_WEEK, weekStart.getFirstDayOfWeek());
        if (weekStart.after(c)) weekStart.add(Calendar.DAY_OF_MONTH, -7);
        Calendar lastWeek = (Calendar) weekStart.clone();
        lastWeek.add(Calendar.DAY_OF_MONTH, -7);
        Calendar monthStart = (Calendar) c.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        Calendar lastMonth = (Calendar) monthStart.clone();
        lastMonth.add(Calendar.MONTH, -1);
        Calendar tomorrow = (Calendar) c.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        StringBuilder b = new StringBuilder();
        period(b, "This week", weekStart, tomorrow, days, fuel, toll);
        period(b, "Last week", lastWeek, weekStart, days, fuel, toll);
        period(b, "This month", monthStart, tomorrow, days, fuel, toll);
        period(b, "Last month", lastMonth, monthStart, days, fuel, toll);
        b.append(Hindi.f("Today's driving score: %1$s / 100 (%2$s harsh brakes, %3$s hard starts, %4$s min over the limit)",
                Drive.score(), Drive.todayBrakes, Drive.todayAccels, Drive.todaySpeedSec / 60));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Trip history")).setMessage(b.toString()).setPositiveButton(Hindi.t("OK"), null).show();
    }

    private static void period(StringBuilder b, String name, Calendar from, Calendar to, JSONObject days, JSONArray fuel, JSONArray toll) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        double km = 0;
        long sec = 0;
        int scoreSum = 0, scored = 0;
        Calendar d = (Calendar) from.clone();
        while (d.before(to)) {
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
        long t0 = from.getTimeInMillis(), t1 = to.getTimeInMillis();
        double rsFuel = spent(fuel, t0, t1), rsToll = spent(toll, t0, t1);
        b.append(Hindi.t(name)).append(": ").append(Hindi.f("%1$s km, %2$s h %3$s min", Fuel.fmt(km), sec / 3600, sec % 3600 / 60));
        if (rsFuel > 0) b.append(Hindi.f(", fuel ₹%1$s", Math.round(rsFuel)));
        if (rsToll > 0) b.append(Hindi.f(", tolls ₹%1$s", Math.round(rsToll)));
        if (scored > 0) b.append(Hindi.f(", score %1$s", scoreSum / scored));
        b.append("\n\n");
    }

    // ------------------------------------------------------------------ sunset

    /** True between sunset and sunrise at this place (NOAA's approximate formula, good to a few minutes). */
    static boolean dark(double lat, double lon, long now) {
        Calendar c = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(now);
        int day = c.get(Calendar.DAY_OF_YEAR);
        double hour = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0;
        double g = 2 * Math.PI / 365 * (day - 1 + (hour - 12) / 24);
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g)
                - 0.040849 * Math.sin(2 * g));
        double la = Math.toRadians(lat);
        double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
        if (cosHa > 1) return true;    // polar night
        if (cosHa < -1) return false;  // midnight sun
        double ha = Math.toDegrees(Math.acos(cosHa));
        double sunrise = 720 - 4 * (lon + ha) - eqt, sunset = 720 - 4 * (lon - ha) - eqt;   // minutes, UTC
        double m = hour * 60;
        return m < sunrise || m > sunset;
    }

    // ------------------------------------------------------------------ a number prompt

    interface Num {
        void got(double v);
    }

    static void number(Activity a, String title, String value, final Num n) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(value);
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t(title)).setView(e)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            n.got(Double.parseDouble(e.getText().toString().trim()));
                        } catch (Exception ignored) {
                        }
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private Care() {
    }
}
