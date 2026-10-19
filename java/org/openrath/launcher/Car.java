package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The car profile: brand, model, fuel and tank size, set once on the first start (Settings > Car to
 * change). Everything that used to assume one particular car reads from here: the name beside the
 * logo, the brand's badge if one is built in, the service centre search, the fuel words and the
 * mileage to expect before the fuel log has learnt the real figure.
 */
final class Car {

    private static final int DIALOG = android.R.style.Theme_Material_Dialog_Alert;

    static final String[] FUELS = {"petrol", "diesel", "cng", "hybrid", "ev"};
    static final String[] FUEL_NAMES = {"Petrol", "Diesel", "CNG", "Petrol hybrid", "Electric"};

    /** Fuel features that mean nothing in an electric car; switched off when Electric is picked. */
    static final String[] FUEL_FEATURES = {"fuelPrice", "coach", "fuelPlan", "fuelCheck"};

    /** What the tank holds, in words for speech and labels ("petrol", "diesel", "CNG"). */
    static String fuelWord = "petrol";
    /** Where it's filled ("petrol pumps", "CNG stations", "charging stations"). */
    static String stationWord = "petrol pumps";

    static String brand(Prefs p) {
        return p.str("carBrand", "");
    }

    static String brandName(Prefs p) {
        return p.str("carBrandName", "");
    }

    static String model(Prefs p) {
        return p.str("carModel", "");
    }

    static String fuel(Prefs p) {
        return p.str("fuel", "petrol");
    }

    /** The name beside the logo: the owner's, else the theme's, else the model's. */
    static String name(Prefs p) {
        return p.str("carName", Theme.text("carName", nameFor(model(p))));
    }

    static boolean ready(Prefs p) {
        return "yes".equals(p.str("carSetup", ""));
    }

    /** Set the words and the expected mileage from the profile; call when the app or a service starts. */
    static void apply(Prefs p) {
        String f = fuel(p);
        fuelWord = word(f);
        stationWord = stations(f);
        Coach.typical = kmpl(f);
        Ui.brand = brand(p);
        Ui.body = p.str("carBody", "car");
    }

    // ------------------------------------------------------------------ pure helpers (tested)

    static String word(String fuel) {
        if ("diesel".equals(fuel)) return "diesel";
        if ("cng".equals(fuel)) return "CNG";
        if ("ev".equals(fuel)) return "charge";
        return "petrol";
    }

    static String stations(String fuel) {
        if ("diesel".equals(fuel)) return "fuel pumps";
        if ("cng".equals(fuel)) return "CNG stations";
        if ("ev".equals(fuel)) return "charging stations";
        return "petrol pumps";
    }

    /** A typical new car's km per litre (per kg for CNG) until the fuel log knows better; 0 for electric. */
    static double kmpl(String fuel) {
        if ("diesel".equals(fuel)) return 20;
        if ("cng".equals(fuel)) return 25;
        if ("hybrid".equals(fuel)) return 22;
        if ("ev".equals(fuel)) return 0;
        return 17;
    }

    /** The name beside the logo for a model: upper case, at most 14 letters. */
    static String nameFor(String model) {
        String n = model == null ? "" : model.trim().toUpperCase(Locale.US);
        return n.length() > 14 ? n.substring(0, 14).trim() : n;
    }

    /** What to search the map for when looking for a workshop. */
    static String serviceSearch(String brandName) {
        return brandName == null || brandName.trim().isEmpty() ? "car service centre" : brandName.trim() + " service centre";
    }

    /**
     * Words that a brand's workshops carry in OpenStreetMap, as a regular expression for the map's
     * search ("" for any workshop). Maruti's dealer chains have their own names.
     */
    static String searchWords(String id, String name) {
        if ("maruti-suzuki".equals(id)) return "maruti|suzuki|nexa|arena|true value";
        if ("mercedes-benz".equals(id)) return "mercedes|benz";
        if ("tata-motors".equals(id)) return "tata";
        if (name == null) return "";
        StringBuilder b = new StringBuilder();
        for (String w : name.toLowerCase(Locale.US).split("[^a-z0-9ëé]+")) {
            if (w.length() < 2 || w.equals("motors") || w.equals("motor") || w.equals("cars")) continue;
            if (b.length() > 0) b.append('|');
            b.append(w);
        }
        return b.toString();
    }

    /** A brand's id from its name, as in the catalogue ("Maruti Suzuki" -> "maruti-suzuki"). */
    static String idFor(String name) {
        StringBuilder b = new StringBuilder();
        for (char c : name.trim().toLowerCase(Locale.US).toCharArray()) {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') b.append(c);
            else if (b.length() > 0 && b.charAt(b.length() - 1) != '-') b.append('-');
        }
        while (b.length() > 0 && b.charAt(b.length() - 1) == '-') b.setLength(b.length() - 1);
        return b.toString();
    }

    // ------------------------------------------------------------------ the brands and models (assets/cars.json)

    static final class Brand {
        final String id, name;
        final List<String> models = new ArrayList<>();

        Brand(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static List<Brand> brands(android.content.Context c) {
        List<Brand> out = new ArrayList<>();
        try {
            InputStream in = c.getAssets().open("cars.json");
            byte[] b = new byte[in.available()];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            in.close();
            org.json.JSONArray a = new org.json.JSONArray(new String(b, 0, n, "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                Brand br = new Brand(o.getString("id"), o.getString("name"));
                org.json.JSONArray m = o.getJSONArray("models");
                for (int j = 0; j < m.length(); j++) br.models.add(m.getString(j));
                out.add(br);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ------------------------------------------------------------------ first start and setup

    /**
     * On the very first start: offer to bring back a backup (one made by Dzire Home counts), else run
     * the car setup. True when something was shown, so the other start-up questions wait their turn.
     */
    static boolean firstRun(final HomeActivity a) {
        final Prefs p = a.prefs;
        if (ready(p)) return false;
        if (a.checkSelfPermission(android.os.Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED && p.num("setupWaits", 0) < 2) {
            p.put("setupWaits", String.valueOf(p.num("setupWaits", 0) + 1));
            return true;   // the storage question is on screen; look for a backup once it's answered
        }
        Storage.migrate();
        if (p.str("restoreAsked", "").isEmpty()) {
            p.put("restoreAsked", "yes");
            final List<File> found = Backup.found();
            if (!found.isEmpty()) {
                final File f = found.get(0);
                String when = new java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new java.util.Date(f.lastModified()));
                new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("Welcome to OpenRath"))
                        .setMessage(Hindi.f(f.getName().startsWith("dzire-home-") ? "There's a backup here from %1$s, made by Dzire Home. Bring back those settings, the fuel log and trips?"
                                : "There's a backup here from %1$s. Bring back those settings, the fuel log and trips?", when)
                                + "\n\n" + Backup.label(f))
                        .setPositiveButton(Hindi.t("Bring them back"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    Backup.restore(a, f);
                                    Toast.makeText(a, Hindi.t("Restored; restarting"), Toast.LENGTH_SHORT).show();
                                    a.dash.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            android.os.Process.killProcess(android.os.Process.myPid());
                                        }
                                    }, 1200);
                                } catch (Exception e) {
                                    Toast.makeText(a, Hindi.f("Couldn't restore: %1$s", e.getMessage()), Toast.LENGTH_LONG).show();
                                    setup(a);
                                }
                            }
                        })
                        .setNegativeButton(Hindi.t("Start fresh"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                setup(a);
                            }
                        }).setCancelable(false).show();
                return true;
            }
        }
        setup(a);
        return true;
    }

    /** Brand, model, shape, fuel, tank: five short questions. */
    static void setup(final HomeActivity a) {
        final List<Brand> all = brands(a);
        String[] names = new String[all.size() + 1];
        for (int i = 0; i < all.size(); i++) names[i] = all.get(i).name;
        names[all.size()] = Hindi.t("Another brand…");
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("Which car is this? Pick the brand"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w < all.size()) model(a, all.get(w));
                        else Chores.text(a, Hindi.t("Brand"), Hindi.t("e.g. Ford"), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, new Extras.Num() {
                            @Override
                            public void set(String v) {
                                model(a, new Brand(idFor(v), v.isEmpty() ? "" : v));
                            }
                        });
                    }
                })
                .setNegativeButton(Hindi.t("Later"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        a.prefs.put("carSetup", "yes");   // ask again only from Settings > Car
                        Toast.makeText(a, Hindi.t("You can set up the car any time in Settings > Car"), Toast.LENGTH_LONG).show();
                    }
                }).setCancelable(false).show();
    }

    private static void model(final HomeActivity a, final Brand b) {
        final Extras.Num then = new Extras.Num() {
            @Override
            public void set(String v) {
                body(a, b, v);
            }
        };
        if (b.models.isEmpty()) {
            Chores.text(a, Hindi.t("Model"), Hindi.t("e.g. Ecosport"), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, then);
            return;
        }
        String[] names = b.models.toArray(new String[b.models.size() + 1]);
        names[b.models.size()] = Hindi.t("Another model…");
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.f("%1$s: which model?", b.name))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w < b.models.size()) then.set(b.models.get(w));
                        else Chores.text(a, Hindi.t("Model"), "", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, then);
                    }
                }).setCancelable(false).show();
    }

    /** Hatchback or saloon, or SUV or MUV: picks the car picture's shape. */
    private static void body(final HomeActivity a, final Brand b, final String model) {
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("What shape is it?"))
                .setItems(Cab.tr(Car3D.SHAPE_NAMES), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        // the 3D car takes the exact shape; the car picture has two
                        a.prefs.put("carShape", Car3D.SHAPES[w]);
                        a.prefs.put("carBody", w >= 2 ? "suv" : "car");
                        fuel(a, b, model);
                    }
                }).setCancelable(false).show();
    }

    private static void fuel(final HomeActivity a, final Brand b, final String model) {
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("What does it run on?"))
                .setItems(Cab.tr(FUEL_NAMES), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final String f = FUELS[w];
                        if ("ev".equals(f)) {
                            save(a, b, model, f, 0);
                            return;
                        }
                        Extras.number(a, Hindi.t("cng".equals(f) ? "CNG cylinder, kg (leave empty if unsure)" : "Fuel tank, litres (leave empty if unsure)"),
                                "", new Extras.Num() {
                                    @Override
                                    public void set(String v) {
                                        int l = 0;
                                        try {
                                            l = Math.round(Float.parseFloat(v));
                                        } catch (NumberFormatException ignored) {
                                        }
                                        save(a, b, model, f, l);
                                    }
                                });
                    }
                }).setCancelable(false).show();
    }

    private static void save(HomeActivity a, Brand b, String model, String fuel, int tank) {
        Prefs p = a.prefs;
        p.put("carBrand", b.id);
        p.put("carBrandName", b.name);
        p.put("carModel", model);
        p.put("fuel", fuel);
        if (!model.isEmpty()) p.put("carName", nameFor(model));
        if (tank > 0) p.put("tankL", String.valueOf(Math.max(5, Math.min(120, tank))));
        if ("ev".equals(fuel)) for (String id : FUEL_FEATURES) Features.set(p, id, false);
        p.put("carSetup", "yes");
        apply(p);
        Ui.clearImages();
        a.restyle();
        String what = (b.name + " " + model).trim();
        Toast.makeText(a, Hindi.f("%1$s set up. Change it any time in Settings > Car.", what.isEmpty() ? Hindi.t("Car") : what), Toast.LENGTH_LONG).show();
    }

    /** One line for Settings > Car. */
    static String summary(Prefs p) {
        String s = (brandName(p) + " " + model(p)).trim();
        String f = fuel(p);
        int i = java.util.Arrays.asList(FUELS).indexOf(f);
        return (s.isEmpty() ? Hindi.t("not set") : s) + ", " + (i < 0 ? f : Hindi.t(FUEL_NAMES[i])).toLowerCase(Locale.US);
    }

    private Car() {
    }
}
