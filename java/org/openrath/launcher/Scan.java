package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FM station scan for the radio box. The tuner lives inside the stock radio app, so this steps it with
 * the radio service's "search next" (it stops at the next frequency with a signal) and reads where it
 * stopped from the radio's own answer (Xt.askRadio), or from the file the stock radio writes
 * (StockRadio) where the service never answers. Going once round the band gives the list of
 * stations; picking one steps along until the radio is there. The list is kept for next time.
 */
final class Scan {

    static final int MAX_STATIONS = 40;
    private static final long STEP_MS = 600, STEP_TIMEOUT_MS = 12000;

    /** The stations from a list of stops in the order found: duplicates out, low to high. */
    static List<String> stations(List<String> stops) {
        List<String> out = new ArrayList<>();
        for (String s : stops) if (!s.isEmpty() && !out.contains(s)) out.add(s);
        Collections.sort(out, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Float.compare(Float.parseFloat(a), Float.parseFloat(b));
            }
        });
        return out;
    }

    /** True once the scan is back where it started, or has wrapped past it. */
    static boolean round(List<String> stops) {
        int n = stops.size();
        if (n < 2) return false;
        String last = stops.get(n - 1);
        return stops.subList(0, n - 1).contains(last);
    }

    /** Where the radio is: its service's answer, else the file the stock radio writes. */
    static String freq(Xt xt) {
        String f = xt == null ? "" : xt.freqText;
        return f.isEmpty() ? StockRadio.now() : f;
    }

    static List<String> saved(Prefs p) {
        List<String> l = new ArrayList<>();
        for (String s : p.str("fmStations", "").split(",")) if (!s.trim().isEmpty()) l.add(s.trim());
        return l;
    }

    /** Tap on SCAN: scan the band, or tune one of the stations found last time. */
    static void menu(final Activity a, final Prefs p, final Xt xt, final Runnable changed) {
        if (Tuner.chosen(p)) {
            own(a, p, changed);
            return;
        }
        if (xt == null) {
            android.widget.Toast.makeText(a, "The radio service isn't connected", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> found = saved(p);
        if (found.isEmpty()) found.addAll(StockRadio.stations());
        String[] items = new String[found.size() + 1];
        items[0] = found.isEmpty() ? "Scan for stations" : "Scan again";
        String now = freq(xt);
        for (int i = 0; i < found.size(); i++) items[i + 1] = found.get(i) + " FM" + (found.get(i).equals(now) ? "   (playing)" : "");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(found.isEmpty() ? "FM stations" : found.size() + " FM stations")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) all(a, p, xt, changed);
                        else tune(a, xt, found.get(w - 1), changed);
                    }
                }).setNegativeButton("Close", null).show();
    }

    /** With OpenRath's own tuner: the chip scans the whole band itself. */
    private static void own(final Activity a, final Prefs p, final Runnable changed) {
        final List<String> found = saved(p);
        String[] items = new String[found.size() + 1];
        items[0] = found.isEmpty() ? "Scan for stations" : "Scan again";
        for (int i = 0; i < found.size(); i++) items[i + 1] = found.get(i) + " FM" + (found.get(i).equals(Tuner.freqText()) ? "   (playing)" : "");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(found.isEmpty() ? "FM stations" : found.size() + " FM stations")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (!Tuner.on) Tuner.play(a, p, true);
                        if (w > 0) {
                            Tuner.tune(p, Tuner.parse(found.get(w - 1)));
                            changed.run();
                            return;
                        }
                        android.widget.Toast.makeText(a, "Scanning the FM band…", android.widget.Toast.LENGTH_SHORT).show();
                        List<String> st = Tuner.scan();
                        finish(a, p, st, changed);
                    }
                }).setNegativeButton("Close", null).show();
    }

    /** Step round the band once and keep every stop. */
    static void all(final Activity a, final Prefs p, final Xt xt, final Runnable changed) {
        final ProgressDialog wait = new ProgressDialog(a, android.R.style.Theme_Material_Dialog_Alert);
        wait.setMessage("Scanning for FM stations…");
        wait.setCancelable(true);
        wait.show();
        final List<String> stops = new ArrayList<>();
        final Handler h = new Handler(Looper.getMainLooper());
        final String[] last = {freq(xt)};
        final long[] stepAt = {0};
        final Runnable[] step = new Runnable[1];
        step[0] = new Runnable() {
            @Override
            public void run() {
                if (!wait.isShowing()) {
                    finish(a, p, stops, changed);
                    return;
                }
                long now = System.currentTimeMillis();
                if (stepAt[0] == 0) {
                    stepAt[0] = now;
                    if (xt.run("radioSearchNext") != null) xt.next();
                } else if (!freq(xt).isEmpty() && !freq(xt).equals(last[0])) {
                    // the radio stopped on a new station
                    last[0] = freq(xt);
                    stops.add(last[0]);
                    wait.setMessage("Scanning for FM stations…\n" + stations(stops).size() + " found, now on " + last[0]);
                    if (round(stops) || stops.size() >= MAX_STATIONS) {
                        wait.dismiss();
                        finish(a, p, stops, changed);
                        return;
                    }
                    stepAt[0] = 0;
                } else if (now - stepAt[0] > STEP_TIMEOUT_MS) {
                    wait.dismiss();
                    finish(a, p, stops, changed);
                    return;
                }
                xt.askRadio();
                h.postDelayed(this, STEP_MS);
            }
        };
        h.post(step[0]);
    }

    private static void finish(Activity a, Prefs p, List<String> stops, Runnable changed) {
        List<String> st = stations(stops);
        // the stepping saw nothing (the radio service doesn't say where it is): use the stock radio's own scan
        if (st.size() < 2) st = stations(merge(st, StockRadio.stations()));
        if (st.isEmpty()) {
            android.widget.Toast.makeText(a, "No stations found. Open the stock radio once and run its scan (or AS), then try again.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder b = new StringBuilder();
        for (String s : st) b.append(b.length() == 0 ? "" : ",").append(s);
        p.put("fmStations", b.toString());
        changed.run();
        android.widget.Toast.makeText(a, st.size() + " stations found. Tap SCAN to pick one.", android.widget.Toast.LENGTH_LONG).show();
    }

    private static List<String> merge(List<String> a, List<String> b) {
        List<String> l = new ArrayList<>(a);
        l.addAll(b);
        return l;
    }

    /** Step along until the radio is on `target` (at most once round the band). */
    static void tune(final Activity a, final Xt xt, final String target, final Runnable changed) {
        if (target.equals(freq(xt))) return;
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] steps = {0};
        final String[] last = {freq(xt)};
        final long[] stepAt = {0}, direct = {0};
        h.post(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                if (target.equals(freq(xt))) {
                    changed.run();
                    return;
                }
                if (direct[0] == 0) {
                    // ask for the station outright first; step along only if that does nothing
                    direct[0] = now;
                    int fi = Math.round(Float.parseFloat(target) * 100);
                    if (xt.has("radioOpenChannel")) xt.run("radioOpenChannel:s:" + target);
                    else if (xt.has("radioSetChannel")) xt.run("radioSetChannel:i:" + fi);
                    else direct[0] = 1;
                } else if (now - direct[0] < 2500) {
                    // waiting to see whether the radio went there
                } else if (direct[0] > 1 && freq(xt).isEmpty()) {
                    // this unit never says where the radio is: trust the direct request, don't step blind
                    changed.run();
                    return;
                } else if (stepAt[0] == 0 || !freq(xt).equals(last[0])) {
                    last[0] = freq(xt);
                    if (++steps[0] > MAX_STATIONS) return;
                    stepAt[0] = now;
                    if (xt.run("radioSearchNext") != null) xt.next();
                } else if (now - stepAt[0] > STEP_TIMEOUT_MS) {
                    android.widget.Toast.makeText(a, "Couldn't reach " + target + " FM", android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                xt.askRadio();
                changed.run();
                h.postDelayed(this, STEP_MS);
            }
        });
    }

    private Scan() {
    }
}
