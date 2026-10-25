package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Saved places for any navigation app: Home, Office, where the car is parked and your own spots,
 * each handed over as a geo: link. Typing an address is blocked while the car is moving. Opened with
 * a long press on the dock's Navigation button.
 */
final class Places {

    static final int MAX = 12;

    /** A geo: link any navigation app understands, with the name shown as the label. */
    static String geo(double lat, double lon, String name) {
        String n = name == null ? "" : name.replace("(", "").replace(")", "").trim();
        String at = String.format(Locale.US, "%.6f,%.6f", lat, lon);
        return "geo:" + at + "?q=" + at + (n.isEmpty() ? "" : "(" + Uri.encode(n) + ")");
    }

    /** A geo: search link for a typed address. */
    static String search(String address) {
        return "geo:0,0?q=" + Uri.encode(address.trim());
    }

    /** "lat,lon" from a saved value, or null. */
    static double[] point(String v) {
        if (v == null) return null;
        String[] s = v.split(",");
        if (s.length != 2) return null;
        try {
            double lat = Double.parseDouble(s[0].trim()), lon = Double.parseDouble(s[1].trim());
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180 || (lat == 0 && lon == 0)) return null;
            return new double[]{lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The owner's own places, saved as "name|lat,lon" separated by ";". */
    static List<String[]> own(String saved) {
        List<String[]> out = new ArrayList<>();
        if (saved == null) return out;
        for (String e : saved.split(";")) {
            int bar = e.indexOf('|');
            if (bar <= 0 || point(e.substring(bar + 1)) == null) continue;
            out.add(new String[]{e.substring(0, bar), e.substring(bar + 1)});
        }
        return out;
    }

    /** `saved` with a place added at the front (one with the same name is replaced), at most MAX. */
    static String add(String saved, String name, double lat, double lon) {
        String clean = name.replace(";", " ").replace("|", " ").trim();
        StringBuilder b = new StringBuilder(clean).append('|').append(String.format(Locale.US, "%.6f,%.6f", lat, lon));
        int n = 1;
        for (String[] p : own(saved)) {
            if (p[0].equalsIgnoreCase(clean) || n >= MAX) continue;
            b.append(';').append(p[0]).append('|').append(p[1]);
            n++;
        }
        return b.toString();
    }

    static String drop(String saved, String name) {
        StringBuilder b = new StringBuilder();
        for (String[] p : own(saved)) {
            if (p[0].equals(name)) continue;
            if (b.length() > 0) b.append(';');
            b.append(p[0]).append('|').append(p[1]);
        }
        return b.toString();
    }

    static void show(final HomeActivity a) {
        final Prefs p = a.prefs;
        final List<String> names = new ArrayList<>();
        final List<String> links = new ArrayList<>();
        for (String n : new String[]{"Home", "Office"}) {
            double[] at = point(p.str("place:" + n, ""));
            if (at != null) {
                names.add(Hindi.t(n));
                links.add(geo(at[0], at[1], n));
            }
        }
        if (Drive.parkLat != 0 || Drive.parkLon != 0) {
            names.add(Hindi.t("Where the car is parked"));
            links.add(geo(Drive.parkLat, Drive.parkLon, "Parked car"));
        }
        final List<String[]> mine = own(p.str("places", ""));
        for (String[] m : mine) {
            double[] at = point(m[1]);
            names.add(m[0]);
            links.add(geo(at[0], at[1], m[0]));
        }
        final int fixed = names.size();
        names.add(Hindi.t("Type an address…"));
        names.add(Hindi.t("Save where I am now…"));
        names.add(Hindi.f("Open %1$s", Hindi.t("Navigation")));
        names.add(Hindi.t("Change the navigation app"));
        AlertDialog d = new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Saved places"))
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        if (i < fixed) go(a, links.get(i));
                        else if (i == fixed) type(a, false);
                        else if (i == fixed + 1) type(a, true);
                        else if (i == fixed + 2) a.openApp("dock:navi", "Navigation");
                        else AppPicker.show(a, Hindi.f("App for %1$s", Hindi.t("Navigation")), new AppPicker.Picked() {
                                @Override
                                public void picked(String pkg) {
                                    p.setApp("dock:navi", pkg);
                                }
                            });
                    }
                }).create();
        // a long press on one of your own places forgets it
        d.getListView().setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> v, android.view.View row, int i, long id) {
                int own = i - (fixed - mine.size());
                if (own < 0 || own >= mine.size()) return false;
                p.put("places", drop(p.str("places", ""), mine.get(own)[0]));
                Toast.makeText(a, Hindi.f("Forgot %1$s", mine.get(own)[0]), Toast.LENGTH_SHORT).show();
                d.dismiss();
                return true;
            }
        });
        d.show();
    }

    /** Hand the place to the navigation app the owner chose, else to whichever app takes geo: links. */
    static void go(HomeActivity a, String link) {
        Intent in = new Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String pkg = a.prefs.app("dock:navi");
        if (pkg != null) {
            try {
                a.startActivity(new Intent(in).setPackage(pkg));
                return;
            } catch (Exception ignored) {
                // that app doesn't take geo: links
            }
        }
        try {
            a.startActivity(in);
        } catch (Exception e) {
            Toast.makeText(a, Hindi.t("No navigation app on this unit takes places"), Toast.LENGTH_LONG).show();
        }
    }

    /** Type an address, or a name for the spot the car is at. Not while moving. */
    private static void type(final HomeActivity a, final boolean saveHere) {
        if (ChildLock.moving()) {
            Toast.makeText(a, Hindi.t("Typing is blocked while the car is moving"), Toast.LENGTH_LONG).show();
            return;
        }
        final android.location.Location at = a.hub == null ? null : a.hub.liveFix();
        if (saveHere && at == null) {
            Toast.makeText(a, Hindi.t("Waiting for GPS"), Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setHint(Hindi.t(saveHere ? "Name, like Gym" : "Address or place"));
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t(saveHere ? "Save where I am now…" : "Type an address…"))
                .setView(e)
                .setPositiveButton(Hindi.t(saveHere ? "Save" : "Go"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String t = e.getText().toString().trim();
                        if (t.isEmpty()) return;
                        if (saveHere) a.prefs.put("places", add(a.prefs.str("places", ""), t, at.getLatitude(), at.getLongitude()));
                        else go(a, search(t));
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null)
                .show();
    }

    private Places() {
    }
}
