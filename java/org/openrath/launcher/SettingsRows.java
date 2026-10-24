package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

/**
 * Everything on the settings screen, by group: the level, the dashboard's boxes, the screen
 * layout and each layout's parts, themes, the car, the dock, radio and music, the map, driving,
 * car care, phone, car signals and help. Developer mode adds the fault-finding rows.
 */
final class SettingsRows {

    private static String contentName(String c) {
        int i = java.util.Arrays.asList(Prefs.CONTENTS).indexOf(c);
        return i >= 0 ? Prefs.CONTENT_NAMES[i] : c.startsWith("widget") ? "Widget from another app" : c;
    }

    /** A list's names in the owner's language, for a dialog. */
    private static String[] tr(String[] en) {
        String[] out = new String[en.length];
        for (int i = 0; i < en.length; i++) out[i] = Hindi.t(en[i]);
        return out;
    }

    private static String appName(android.content.pm.PackageManager pm, String pkg) {
        if (pkg == null) return "not chosen";
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return Hindi.f("%1$s (not installed)", pkg);
        }
    }

    private static void layoutRows(final HomeActivity a, java.util.List<SettingsView.Row> r) {
        final String g = "Layout";
        final java.util.List<Layouts.Layout> all = Layouts.all(a);
        final Layouts.Layout cur = Layouts.get(a, Layouts.current(a.prefs));
        if (cur == null) return;
        final java.util.List<String> opts = Layouts.opts(cur);
        if (!opts.isEmpty()) {
            int shown = 0;
            for (String o : opts) if (Layouts.shown(a.prefs, cur.id, o)) shown++;
            r.add(new SettingsView.Row(g, "Show on this layout", Hindi.f("%1$s of %2$s parts", String.valueOf(shown), String.valueOf(opts.size())), new Runnable() {
                @Override
                public void run() {
                    final String[] names = new String[opts.size()];
                    final boolean[] on = new boolean[opts.size()];
                    for (int i = 0; i < names.length; i++) {
                        String n = Layouts.OPT_NAMES.get(opts.get(i));
                        names[i] = n == null ? opts.get(i) : Hindi.t(n);
                        on[i] = Layouts.shown(a.prefs, cur.id, opts.get(i));
                    }
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle(Hindi.f("Show on %1$s", Hindi.t(cur.name)))
                            .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int i, boolean b) {
                                    on[i] = b;
                                }
                            })
                            .setPositiveButton(Hindi.t("OK"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    for (int i = 0; i < on.length; i++) Layouts.setShown(a.prefs, cur.id, opts.get(i), on[i]);
                                    a.layoutChanged();
                                    if (a.settingsView != null) a.settingsView.refresh();
                                }
                            }).setNegativeButton(Hindi.t("Cancel"), null).show();
                }
            }));
        }
        final Layouts.Part stats = Layouts.part(cur, "stats");
        if (stats != null) {
            java.util.List<String> now = Layouts.statItems(a.prefs, cur.id, stats);
            StringBuilder v = new StringBuilder();
            for (String it : now) v.append(v.length() == 0 ? "" : ", ").append(Hindi.t(Layouts.statName(it)).toLowerCase(java.util.Locale.US));
            r.add(new SettingsView.Row(g, "Details in the list", v.length() == 0 ? "none" : v.toString(), new Runnable() {
                @Override
                public void run() {
                    final java.util.List<String> order = Layouts.statOrder(a.prefs, cur.id, stats);
                    final java.util.List<String> showing = Layouts.statItems(a.prefs, cur.id, stats);
                    final String[] names = new String[order.size()];
                    final boolean[] on = new boolean[order.size()];
                    for (int i = 0; i < names.length; i++) {
                        names[i] = Hindi.t(Layouts.statName(order.get(i)));
                        on[i] = showing.contains(order.get(i));
                    }
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle(Hindi.f("Details on %1$s", Hindi.t(cur.name)))
                            .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int i, boolean b) {
                                    on[i] = b;
                                }
                            })
                            .setPositiveButton(Hindi.t("OK"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    for (int i = 0; i < on.length; i++) Layouts.setStat(a.prefs, cur.id, stats, order.get(i), on[i]);
                                    a.layoutChanged();
                                    if (a.settingsView != null) a.settingsView.refresh();
                                }
                            })
                            .setNeutralButton(Hindi.t("Change the order"), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    for (int i = 0; i < on.length; i++) Layouts.setStat(a.prefs, cur.id, stats, order.get(i), on[i]);
                                    statOrder(a, cur, stats);
                                }
                            }).setNegativeButton(Hindi.t("Cancel"), null).show();
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Phone mirroring button", Layouts.mirrorOn(a.prefs) ? "on the layout's buttons" : "off", new Runnable() {
            @Override
            public void run() {
                a.prefs.put("layMirror", Layouts.mirrorOn(a.prefs) ? "off" : "on");
                a.layoutChanged();
                if (Layouts.mirrorOn(a.prefs))
                    Toast.makeText(a, Hindi.t("Tap the new button to choose your mirroring app"), Toast.LENGTH_LONG).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Background movement", Layouts.animOn(a.prefs) ? "gentle, while driving" : "off", new Runnable() {
            @Override
            public void run() {
                a.prefs.put("layAnim", Layouts.animOn(a.prefs) ? "off" : "on");
                a.layoutChanged();
            }
        }));
        final String[] walls = {"layout", "mine", "cycle"};
        final String[] wallNames = {"the layout's own", "your picture", "take turns every hour"};
        int wi = Math.max(0, java.util.Arrays.asList(walls).indexOf(Layouts.wall(a.prefs)));
        r.add(new SettingsView.Row(g, "Background picture", wallNames[wi], new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(walls).indexOf(Layouts.wall(a.prefs));
                String next = walls[(i + 1) % walls.length];
                a.prefs.put("layWall", next);
                if (!"layout".equals(next) && Layouts.myWalls().isEmpty())
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle(Hindi.t("Add your pictures"))
                            .setMessage(Hindi.f("Copy pictures (JPG or PNG) to %1$s on the unit, with a USB stick or a file manager. Until then the layout's own picture shows.",
                                    Layouts.wallDir().getPath()))
                            .setPositiveButton(Hindi.t("OK"), null).show();
                a.layoutChanged();
            }
        }));
        if (Layouts.part(cur, "favourites") != null) {
            r.add(new SettingsView.Row(g, "Clear the favourite apps", "tap a tile on the layout to choose, long-press to change", new Runnable() {
                @Override
                public void run() {
                    a.prefs.put("layApps", "");
                    a.layoutChanged();
                    Toast.makeText(a, Hindi.t("Cleared. Tap a tile on the layout to choose its app."), Toast.LENGTH_LONG).show();
                }
            }));
        }
    }

    /** The times the car picture's headlights go on and off. */
    private static void lightTimes(final HomeActivity a) {
        final int from = a.prefs.num("lightsFrom", 18 * 60 + 30), to = a.prefs.num("lightsTo", 6 * 60 + 30);
        new android.app.TimePickerDialog(a, android.R.style.Theme_Material_Dialog_Alert, new android.app.TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(android.widget.TimePicker v, int h, int m) {
                a.prefs.put("lightsFrom", String.valueOf(h * 60 + m));
                new android.app.TimePickerDialog(a, android.R.style.Theme_Material_Dialog_Alert, new android.app.TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(android.widget.TimePicker v2, int h2, int m2) {
                        a.prefs.put("lightsTo", String.valueOf(h2 * 60 + m2));
                        a.hub.notifyIfChanged(true);
                        a.openSettings();
                    }
                }, to / 60, to % 60, true).show();
                Toast.makeText(a, Hindi.t("Now the time they go off"), Toast.LENGTH_SHORT).show();
            }
        }, from / 60, from % 60, true).show();
        Toast.makeText(a, Hindi.t("The time the headlights go on"), Toast.LENGTH_SHORT).show();
    }

    /** Put one of the layout's details first. */
    private static void statOrder(final HomeActivity a, final Layouts.Layout cur, final Layouts.Part stats) {
        final java.util.List<String> order = Layouts.statItems(a.prefs, cur.id, stats);
        if (order.size() < 2) {
            Toast.makeText(a, Hindi.t("Show two or more details first"), Toast.LENGTH_SHORT).show();
            a.layoutChanged();
            return;
        }
        String[] names = new String[order.size()];
        for (int i = 0; i < names.length; i++) names[i] = (i + 1) + ".  " + Hindi.t(Layouts.statName(order.get(i)));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Put first"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Layouts.toTop(a.prefs, cur.id, stats, order.get(w));
                        a.layoutChanged();
                    }
                }).setNegativeButton(Hindi.t("Done"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        a.layoutChanged();
                    }
                }).show();
    }

    /** The boxes on this page with what each shows; picking one asks what it should show. */
    private static void boxes(final HomeActivity a) {
        String[] names = new String[Prefs.SLOTS];
        for (int i = 0; i < Prefs.SLOTS; i++) {
            names[i] = Hindi.t(HomeDialogs.slotName(a, i)) + ":  " + Hindi.t(contentName(a.prefs.slot(i)));
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("What the boxes show"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, final int slot) {
                        final int[] at = HomeDialogs.offered(a.prefs.slot(slot), Features.on(a.prefs, "soundboard"));
                        String[] shown = new String[at.length];
                        int now = -1;
                        for (int i = 0; i < at.length; i++) {
                            shown[i] = Hindi.t(Prefs.CONTENT_NAMES[at[i]]);
                            if (Prefs.CONTENTS[at[i]].equals(a.prefs.slot(slot))) now = i;
                        }
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle(Hindi.f("%1$s shows", Hindi.t(HomeDialogs.slotName(a, slot))))
                                .setSingleChoiceItems(shown, now,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d2, int which) {
                                                a.prefs.setSlot(slot, Prefs.CONTENTS[at[which]]);
                                                a.build();
                                                d2.dismiss();
                                            }
                                        })
                                .setNegativeButton(Hindi.t("Cancel"), null).show();
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    /** Everything on the settings screen, by group. */
    static java.util.List<SettingsView.Row> settingsRows(final HomeActivity a) {
        final java.util.List<SettingsView.Row> r = new java.util.ArrayList<>();
        final android.content.pm.PackageManager pm = a.getPackageManager();

        // ---- Level
        levelRows(a, r);

        // ---- Dashboard
        String g = "Dashboard";
        // one row for all the boxes, not one each: a long-press on a box does the same
        r.add(new SettingsView.Row(g, "What the boxes show", "or long-press a box", new Runnable() {
            @Override
            public void run() {
                boxes(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Move boxes", "drag and drop on the dashboard", new Runnable() {
            @Override
            public void run() {
                a.startMoving();
            }
        }));
        final int[] sizes = {80, 90, 100, 110, 120, 130};
        r.add(new SettingsView.Row("General", "Size of text and boxes", a.prefs.num("uiScale", 100) + "%", new Runnable() {
            @Override
            public void run() {
                String[] names = new String[sizes.length];
                for (int i = 0; i < sizes.length; i++) names[i] = sizes[i] + "%" + (sizes[i] == 100 ? Hindi.t(" (as designed)") : "");
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Size of text and boxes"))
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.prefs.put("uiScale", String.valueOf(sizes[w]));
                                a.restyle();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        r.add(new SettingsView.Row("General", "Units", (Units.mph ? "mph" : "km/h") + ", " + (Units.fahrenheit ? "°F" : "°C"), new Runnable() {
            @Override
            public void run() {
                final String[] names = {"km/h and °C", "mph and °F", "km/h and °F", "mph and °C"};
                final String[][] vals = {{"kmh", "c"}, {"mph", "f"}, {"kmh", "f"}, {"mph", "c"}};
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Units for speed and temperature"))
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.prefs.put("speedUnit", vals[w][0]);
                                a.prefs.put("tempUnit", vals[w][1]);
                                a.build();
                                a.hub.refreshWeather();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        r.add(new SettingsView.Row("General", "Country, for money and holidays", Money.name(Money.country) + "  ·  " + Money.symbol(Money.country).trim(), new Runnable() {
            @Override
            public void run() {
                final java.util.List<String> codes = new java.util.ArrayList<>(java.util.Arrays.asList(Money.COUNTRIES));
                if (!codes.contains(Money.country)) codes.add(0, Money.country);
                String[] names = new String[codes.size()];
                for (int i = 0; i < names.length; i++)
                    names[i] = (codes.get(i).equals(Money.country) ? "✓  " : "     ") + Money.name(codes.get(i)) + "  ·  " + Money.symbol(codes.get(i)).trim();
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Country"))
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.prefs.put("country", codes.get(w));
                                Money.load(a.prefs);
                                a.build();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        r.add(new SettingsView.Row("General", "Less movement", Hindi.t(Motion.NAMES[java.util.Arrays.asList(Motion.LEVELS).indexOf(Motion.pick(a.prefs.str("motion", "standard")))]), new Runnable() {
            @Override
            public void run() {
                String[] names = new String[Motion.NAMES.length];
                for (int i = 0; i < names.length; i++) names[i] = Hindi.t(Motion.NAMES[i]);
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Animations"))
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.prefs.put("motion", Motion.LEVELS[w]);
                                a.build();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        final String[] turns = {"auto", "landscape", "portrait"};
        r.add(new SettingsView.Row("General", "Screen way up", Ui.portrait(a.dash.getWidth(), a.dash.getHeight()) ? Hindi.f("%1$s, boxes stacked", Hindi.t(a.prefs.str("turn", "auto"))) : a.prefs.str("turn", "auto"), new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(turns).indexOf(a.prefs.str("turn", "auto"));
                a.prefs.put("turn", turns[(i + 1) % turns.length]);
                a.turn();
                a.restyle();
            }
        }));
        if (Features.on(a.prefs, "news")) {
            r.add(new SettingsView.Row(g, "News box shows", News.topicName(a.prefs.str("newsTopic", "top")), new Runnable() {
                @Override
                public void run() {
                    int i = java.util.Arrays.asList(News.TOPICS).indexOf(a.prefs.str("newsTopic", "top"));
                    a.prefs.put("newsTopic", News.TOPICS[(i + 1) % News.TOPICS.length]);
                    News.at = 0;
                    News.refresh(a, a.prefs, true);
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Columns", a.prefs.swapped() ? "map on the left" : "car on the left", new Runnable() {
            @Override
            public void run() {
                a.prefs.setSwapped(!a.prefs.swapped());
                a.build();
            }
        }));
        r.add(new SettingsView.Row("General", "Your name", a.prefs.welcome().isEmpty() ? "hidden" : a.prefs.welcome(), new Runnable() {
            @Override
            public void run() {
                HomeSettings.editWelcome(a);
            }
        }));
        final String[] dimModes = {"off", "sunset", "lights"};
        final String[] dimNames = {"off", "after sunset", "with the headlights"};
        final int[] dimSteps = {20, 30, 40, 50, 60};
        String dimNow = a.prefs.str("dim", "off");
        r.add(new SettingsView.Row(g, "Night dimming", "off".equals(dimNow) ? Hindi.t("off")
                : Hindi.f("%1$s, %2$s%% darker", Hindi.t(dimNames[Math.max(0, java.util.Arrays.asList(dimModes).indexOf(dimNow))]), String.valueOf(a.prefs.num("dimLevel", 40))),
                new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Night dimming"))
                                .setSingleChoiceItems(tr(dimNames), Math.max(0, java.util.Arrays.asList(dimModes).indexOf(a.prefs.str("dim", "off"))),
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int w) {
                                                a.prefs.put("dim", dimModes[w]);
                                                d.dismiss();
                                                a.hub.notifyIfChanged(true);
                                                if (w == 0) {
                                                    if (a.settingsView != null) a.settingsView.refresh();
                                                    return;
                                                }
                                                String[] names = new String[dimSteps.length];
                                                for (int i = 0; i < names.length; i++) names[i] = Hindi.f("%1$s%% darker", String.valueOf(dimSteps[i]));
                                                int at = 0;
                                                while (at < dimSteps.length && dimSteps[at] != a.prefs.num("dimLevel", 40)) at++;
                                                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("How dark at night"))
                                                        .setSingleChoiceItems(names, at < dimSteps.length ? at : -1, new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface d2, int w2) {
                                                                a.prefs.put("dimLevel", String.valueOf(dimSteps[w2]));
                                                                d2.dismiss();
                                                                a.hub.notifyIfChanged(true);
                                                                if (a.settingsView != null) a.settingsView.refresh();
                                                            }
                                                        }).show();
                                            }
                                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
                    }
                }));
        r.add(new SettingsView.Row(g, "Reset the layout", "boxes and apps back to the start", new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage(Hindi.t("Put the boxes and chosen apps back as they were at the start? Your name, sources and learnt car signals stay."))
                        .setPositiveButton(Hindi.t("Reset"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.prefs.reset();
                                a.build();
                            }
                        })
                        .setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));

        // ---- Screen layout: the boxes or a picture layout, and each layout's parts
        layoutRows(a, r);

        // ---- Themes: the gallery, then ways to make and share themes
        g = "Themes";
        r.add(new SettingsView.Row(g, "", null, null));
        r.add(new SettingsView.Row(g, "Accent colour (car paint)", Theme.json().has("colours") && Theme.json().optJSONObject("colours").has("accent") ? Hindi.f("set by %1$s", Theme.name) : a.prefs.str("theme", Ui.THEMES[0]), new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(Hindi.t("Match the car's colour"))
                        .setSingleChoiceItems(Ui.THEMES, Math.max(0, java.util.Arrays.asList(Ui.THEMES).indexOf(a.prefs.str("theme", Ui.THEMES[0]))),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        a.prefs.put("theme", Ui.THEMES[w]);
                                        d.dismiss();
                                        a.restyle();
                                    }
                                }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Night and phone themes", "choose the themes", new Runnable() {
            @Override
            public void run() {
                ThemeAuto.menu(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Make the boot logo", "with your car's badge and name", new Runnable() {
            @Override
            public void run() {
                BootArt.menu(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Make your own theme", "start from the current theme", new Runnable() {
            @Override
            public void run() {
                try {
                    String folder = "my-theme";
                    for (int i = 2; new java.io.File(Theme.root(), folder).exists(); i++) folder = "my-theme-" + i;
                    java.io.File d = Theme.starter(a, folder);
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle(Hindi.t("Theme created"))
                            .setMessage(Hindi.f("Edit the files in %1$s (on a PC over USB, or with a file manager). "
                                    + "HOW-TO.txt in that folder explains each file. It is now in the list above; tap it to use it, and again after each edit.", d.getPath()))
                            .setPositiveButton(Hindi.t("OK"), null).show();
                } catch (Exception e) {
                    Toast.makeText(a, Hindi.f("Couldn't create it: %1$s (allow storage access?)", e.getMessage()), Toast.LENGTH_LONG).show();
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Import a theme from Download", "a theme .zip someone shared", new Runnable() {
            @Override
            public void run() {
                final java.util.List<java.io.File> zips = Theme.zipsInDownload();
                if (zips.isEmpty()) {
                    Toast.makeText(a, Hindi.t("No .zip files in Download"), Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[zips.size()];
                for (int i = 0; i < names.length; i++) names[i] = zips.get(i).getName();
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(Hindi.t("Import which theme?"))
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    String folder = Theme.importZip(zips.get(w));
                                    a.prefs.put("themePack", folder);
                                    a.restyle();
                                    Toast.makeText(a, Hindi.f("Theme \"%1$s\" is on", folder), Toast.LENGTH_LONG).show();
                                } catch (Exception e) {
                                    Toast.makeText(a, Hindi.f("Not a theme: %1$s", e.getMessage()), Toast.LENGTH_LONG).show();
                                }
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));

        // ---- Car
        g = "Car";
        r.add(new SettingsView.Row(g, "Brand, model and fuel", Car.summary(a.prefs), new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                Car.setup(a);
            }
        }));
        if (Features.on(a.prefs, "view360")) {
            r.add(new SettingsView.Row(g, "360 view of your car", Car3D.SHAPE_NAMES[java.util.Arrays.asList(Car3D.SHAPES).indexOf(Car3D.shape(a.prefs))]
                    + (Car3D.plate(a.prefs).isEmpty() ? "" : ", " + Car3D.plate(a.prefs)), new Runnable() {
                @Override
                public void run() {
                    View360.show(a);
                }
            }));
            String cam = View360.camApp(a, a.prefs);
            r.add(new SettingsView.Row(g, "Stereo's 360 camera app", cam == null ? "none found (pick one)" : a.appLabel(cam), new Runnable() {
                @Override
                public void run() {
                    AppPicker.show(a, Hindi.t("The 360 camera app"), new AppPicker.Picked() {
                        @Override
                        public void picked(String pkg) {
                            a.prefs.setApp("cam360", pkg);
                            if (a.settingsView != null) a.settingsView.refresh();
                        }
                    });
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Car name", Car.name(a.prefs), new Runnable() {
            @Override
            public void run() {
                final android.widget.EditText e = new android.widget.EditText(a);
                e.setSingleLine(true);
                e.setText(Car.name(a.prefs));
                e.setSelectAllOnFocus(true);
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(Hindi.t("Name beside the logo (e.g. SWIFT, BALENO)"))
                        .setView(e)
                        .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.prefs.put("carName", e.getText().toString().trim());
                                a.restyle();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        String[] picNames = {"Car picture, lights off", "Car picture, lights on", "Brand logo"};
        for (int i = 0; i < HomeActivity.PICTURES.length; i++) {
            final int n = i;
            r.add(new SettingsView.Row(g, picNames[i], Ui.customFile(a, HomeActivity.PICTURES[i]).exists() ? "your picture"
                    : "built-in; tap to use your own", new Runnable() {
                @Override
                public void run() {
                    HomeSettings.pickPicture(a, n);
                }
            }));
        }

        // ---- Dock
        g = "Buttons bar";
        final String[] places = {"left", "right", "top", "bottom"};
        r.add(new SettingsView.Row(g, "Position", Hindi.f("%1$s; long-press a button on the bar to choose its app", Hindi.t(a.prefs.str("dockAt", "left"))), new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(places).indexOf(a.prefs.str("dockAt", "left"));
                a.prefs.put("dockAt", places[(i + 1) % places.length]);
                a.restyle();
            }
        }));
        r.add(new SettingsView.Row(g, "Button pictures", "built-in, or your own", new Runnable() {
            @Override
            public void run() {
                int n = Prefs.DOCK.length + 1;
                String[] names = new String[n];
                Runnable[] acts = new Runnable[n];
                for (int i = 0; i < n; i++) {
                    final String key = i < Prefs.DOCK.length ? Prefs.DOCK[i] : "settings";
                    names[i] = Hindi.t(i < Prefs.DOCK.length ? Prefs.DOCK_NAMES[i] : "Settings") + "  ·  "
                            + Hindi.t(Ui.customFile(a, "dock_" + key).exists() ? "your picture" : "built-in");
                    final int code = java.util.Arrays.asList(HomeActivity.ALL_PICTURES).indexOf("dock_" + key);
                    acts[i] = new Runnable() {
                        @Override
                        public void run() {
                            HomeSettings.pickPicture(a, code);
                        }
                    };
                }
                menu(a, "Button pictures", names, acts);
            }
        }));

        // ---- Radio and music
        g = "Radio and music";
        final String[] radioSrc = {"auto: online with internet, else FM", "online stations", "FM radio"};
        final String[] musicSrc = {"auto: music app with internet, else files", "music app", "music files"};
        r.add(new SettingsView.Row(g, "Radio plays", radioSrc[Math.max(0, java.util.Arrays.asList(Prefs.RADIO_SRC).indexOf(a.prefs.source("radio")))],
                new Runnable() {
                    @Override
                    public void run() {
                        HomeSettings.cycle(a, "radio", Prefs.RADIO_SRC);
                    }
                }));
        r.add(new SettingsView.Row(g, "Online radio station", Player.stations.isEmpty() || Player.station >= Player.stations.size() ? null : Player.stations.get(Player.station).name, new Runnable() {
            @Override
            public void run() {
                HomeSettings.pickTrack(a, true);
            }
        }));
        r.add(new SettingsView.Row(g, "FM radio app", appName(pm, a.prefs.app("media:radio")), HomeSettings.pick(a, "media:radio", "Radio")));
        String fp = a.prefs.fmCmd("play"), fs0 = a.prefs.fmCmd("stop");
        if (a.topway()) r.add(new SettingsView.Row(g, "FM radio test (find how this unit starts FM)",
                fp.isEmpty() ? "not done yet" : fs0.isEmpty() ? Hindi.f("start: %1$s", Fm.label(fp)) : Hindi.f("start: %1$s, stop: %2$s", Fm.label(fp), Fm.label(fs0)), new Runnable() {
            @Override
            public void run() {
                if (a.hub.xt == null || !a.hub.xt.ready()) {
                    Toast.makeText(a, Hindi.t("Topway's radio service isn't connected; only the straight link to the radio chip can be tried"), Toast.LENGTH_LONG).show();
                }
                Fm.test(a, a.prefs, a.hub.xt != null && a.hub.xt.ready() ? a.hub.xt : null, a.hub.fm, false);
            }
        }));
        if (a.topway()) {
            String ft = a.prefs.str("fmTuner", "");
            r.add(new SettingsView.Row(g, "Built-in FM tuner (without the stock radio app)",
                    "own".equals(ft) ? "on: OpenRath plays FM itself" : "no".equals(ft) ? "didn't work on this unit; tap to try again" : "not tried yet: tap to try",
                    new Runnable() {
                        @Override
                        public void run() {
                            HomeSettings.tryTuner(a);
                        }
                    }));
        }
        if (a.topway()) r.add(new SettingsView.Row(g, "FM frequency to start on", a.prefs.fmFreq() + " MHz", new Runnable() {
            @Override
            public void run() {
                final android.widget.EditText e = new android.widget.EditText(a);
                e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                e.setText(a.prefs.fmFreq());
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(Hindi.t("FM frequency (MHz)"))
                        .setView(e)
                        .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    float f = Float.parseFloat(e.getText().toString().trim());
                                    if (f >= 87.5f && f <= 108f) a.prefs.setFmFreq(Fm.freqOf(f));
                                } catch (Exception ignored) {
                                    // not a number: nothing to save
                                }
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Music plays", musicSrc[Math.max(0, java.util.Arrays.asList(Prefs.MUSIC_SRC).indexOf(a.prefs.source("music")))],
                new Runnable() {
                    @Override
                    public void run() {
                        HomeSettings.cycle(a, "music", Prefs.MUSIC_SRC);
                    }
                }));
        r.add(new SettingsView.Row(g, "Music files", Player.songs.isEmpty() ? "choose a song, or look again after plugging in a USB stick"
                : Hindi.f("%1$s songs found; choose one, or look again", String.valueOf(Player.songs.size())), new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setItems(new String[]{Hindi.t("Choose a song"), Hindi.t("Look for music files again")}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                if (w == 0) HomeSettings.pickTrack(a, false);
                                else {
                                    Player.songs.clear();
                                    Toast.makeText(a, Hindi.t("Music files are found again on the next play"), Toast.LENGTH_LONG).show();
                                }
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Music app", appName(pm, a.prefs.app("media:music")), HomeSettings.pick(a, "media:music", "Music")));
        if (Features.on(a.prefs, "projection")) {
            String proj = Projection.app(a, a.prefs);
            r.add(new SettingsView.Row(g, "Phone projection app", proj == null ? "none found (pick one)"
                    : a.prefs.app("projection") == null ? Hindi.f("%1$s (found)", a.appLabel(proj)) : a.appLabel(proj), new Runnable() {
                @Override
                public void run() {
                    AppPicker.show(a, Hindi.t("Your CarPlay or Android Auto app"), new AppPicker.Picked() {
                        @Override
                        public void picked(String pkg) {
                            a.prefs.setApp("projection", pkg);
                            Projection.forget();
                            a.refresh();
                            if (a.settingsView != null) a.settingsView.refresh();
                        }
                    });
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Track and station info", a.hub.mediaAccess ? "allowed" : "not allowed", new Runnable() {
            @Override
            public void run() {
                try {
                    a.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    Toast.makeText(a, Hindi.t("Turn on OpenRath in this list"), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(a, Hindi.t("This unit has no notification access screen"), Toast.LENGTH_LONG).show();
                }
            }
        }));

        // ---- Map and navigation
        g = "Map and navigation";
        final String[] looks = {"auto", "day", "night"};
        final String[] lookNames = {"light by day, dark at night", "always light", "always dark"};
        r.add(new SettingsView.Row(g, "Map look", lookNames[Math.max(0, java.util.Arrays.asList(looks).indexOf(a.prefs.str("mapStyle", "auto")))], new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(looks).indexOf(a.prefs.str("mapStyle", "auto"));
                a.prefs.put("mapStyle", looks[(i + 1) % looks.length]);
                a.hub.notifyIfChanged(true);
            }
        }));
        if (Features.on(a.prefs, "offlineMap")) r.add(new SettingsView.Row(g, "Map kept for no internet", Hindi.f("about %1$s MB of places you've seen; tap to clear",
                String.valueOf(Tiles.sizeMb(a))), new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage(Hindi.t("Delete the kept map? Places will show again once seen with internet."))
                        .setPositiveButton(Hindi.t("Delete"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Tiles.clear(a);
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        String offNav = OfflineNav.app(a, a.prefs);
        final java.util.List<String> engines = Speak.engines(a);
        r.add(new SettingsView.Row(g, "Spoken voice", engines.contains(Speak.GOOGLE_TTS) ? "Speech Services by Google; tap to test or fix it"
                : engines.isEmpty() ? "none installed: tap to get Speech Services by Google" : "another engine (tap to get Google's, which speaks Hindi)", new Runnable() {
            @Override
            public void run() {
                Speak.getVoice(a);
            }
        }));
        if (Features.on(a.prefs, "offlineVoice")) r.add(new SettingsView.Row(g, "Voice without internet", "get Google's offline speech pack (English and Hindi)", new Runnable() {
            @Override
            public void run() {
                Intent[] tries = {
                        new Intent().setClassName("com.google.android.googlequicksearchbox",
                                "com.google.android.voicesearch.greco3.languagepack.InstallActivity"),
                        new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
                        new Intent(android.provider.Settings.ACTION_SETTINGS)};
                for (Intent t : tries) {
                    try {
                        a.startActivity(t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        Toast.makeText(a, Hindi.t("Download English (India) and Hindi, on Wi-Fi"), Toast.LENGTH_LONG).show();
                        return;
                    } catch (Exception ignored) {
                        // not on this unit: try the next screen
                    }
                }
            }
        }));
        final String offName = offNav == null ? Hindi.t("none: install Organic Maps") : OfflineNav.label(a, offNav);
        r.add(new SettingsView.Row(g, "Navigation app", Prefs.sourceName(a.prefs.source("navi")), new Runnable() {
            @Override
            public void run() {
                menu(a, "Navigation app", new String[]{
                        Hindi.t("Guide routes in") + ":  " + Hindi.t(Prefs.sourceName(a.prefs.source("navi"))),
                        Hindi.t("Navigation button opens") + ":  " + appName(pm, a.prefs.app("dock:navi")),
                        Hindi.t("Without internet") + ":  " + offName}, new Runnable[]{
                        new Runnable() {
                            @Override
                            public void run() {
                                HomeSettings.cycle(a, "navi", Prefs.NAV_SRC);
                            }
                        },
                        HomeSettings.pick(a, "dock:navi", "Navigation"),
                        HomeSettings.pick(a, "offlineNav", "Offline map app")});
            }
        }));

        // ---- Driving
        g = "Driving";
        if (Features.on(a.prefs, "achievements")) r.add(new SettingsView.Row(g, "Badges", Hindi.f("%1$s of %2$s earned", Achievements.count(a), Achievements.BADGES.length), new Runnable() {
            @Override
            public void run() {
                Achievements.show(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Your own speed limit", a.prefs.warnSpeed() == 0 ? "off" : Hindi.f("%1$s km/h", String.valueOf(a.prefs.warnSpeed())), new Runnable() {
            @Override
            public void run() {
                int[] steps = {0, 80, 90, 100, 110, 120};
                int i = 0;
                while (i < steps.length && steps[i] != a.prefs.warnSpeed()) i++;
                a.prefs.setWarnSpeed(steps[(i + 1) % steps.length]);
            }
        }));
        final int fix = a.prefs.num("speedFix", 0);
        r.add(new SettingsView.Row(g, "Speed correction", fix == 0 ? "off" : Hindi.f("GPS speed %1$s%%", (fix > 0 ? "+" : "") + fix), new Runnable() {
            @Override
            public void run() {
                final int[] steps = {-5, -3, -2, 0, 2, 3, 4, 5, 6, 8, 10};
                String[] names = new String[steps.length];
                int cur = 3;
                for (int i = 0; i < steps.length; i++) {
                    names[i] = steps[i] == 0 ? Hindi.t("off") : (steps[i] > 0 ? "+" : "") + steps[i] + "%";
                    if (steps[i] == fix) cur = i;
                }
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(Hindi.t("Add to the GPS speed, so it matches your car's speedometer"))
                        .setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.prefs.put("speedFix", String.valueOf(steps[w]));
                                d.dismiss();
                                if (a.settingsView != null) a.settingsView.refresh();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        final int filter = a.prefs.num("speedFilter", 2);
        r.add(new SettingsView.Row(g, "Speed filter", filter == 0 ? "off" : Hindi.f("0 below %1$s km/h", filter), new Runnable() {
            @Override
            public void run() {
                final int[] steps = {0, 2, 3, 4, 5, 6, 8};
                String[] names = new String[steps.length];
                int cur = 1;
                for (int i = 0; i < steps.length; i++) {
                    names[i] = steps[i] == 0 ? Hindi.t("off") : steps[i] + " km/h";
                    if (steps[i] == filter) cur = i;
                }
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(Hindi.t("Show 0 below this speed, for GPS that drifts when parked"))
                        .setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                a.prefs.put("speedFilter", String.valueOf(steps[w]));
                                Fixes.stillKmh = steps[w];
                                d.dismiss();
                                if (a.settingsView != null) a.settingsView.refresh();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        Fuel.Stats fs = Fuel.stats(a.prefs);
        r.add(new SettingsView.Row(g, "Trips, fuel and tolls", Hindi.f("today %1$s km, score %2$s; %3$s", String.format(java.util.Locale.US, "%.1f", Drive.todayKm),
                String.valueOf(Drive.score()), fs.fills == 0 ? Hindi.t("no fill-ups yet") : Hindi.f("%1$s fill-ups", String.valueOf(fs.fills))), new Runnable() {
            @Override
            public void run() {
                a.tripMenu();
            }
        }));
        if (Features.on(a.prefs, "tolls")) r.add(new SettingsView.Row(g, "Toll estimate per plaza", Hindi.f("₹%1$s (for route estimates)", String.valueOf(a.prefs.num("tollRate", 100))), new Runnable() {
            @Override
            public void run() {
                Care.number(a, Hindi.t("Typical car toll per plaza (₹)"), String.valueOf(a.prefs.num("tollRate", 100)), new Care.Num() {
                    @Override
                    public void got(double v) {
                        a.prefs.put("tollRate", String.valueOf(Math.round(v)));
                    }
                });
            }
        }));

        // ---- Car care
        g = "Car care";
        final Runnable careChanged = new Runnable() {
            @Override
            public void run() {
                a.hub.notifyIfChanged(true);
                if (a.settingsView != null) a.settingsView.refresh();
            }
        };
        int due = 0;
        for (int i = 0; i < Care.ITEMS.length; i++) {
            long left = Care.byDate(i) ? Care.daysLeft(a.prefs, i) : Care.kmLeft(a.prefs, i);
            if (left != Long.MIN_VALUE && left <= 0) due++;
        }
        r.add(new SettingsView.Row(g, "Service reminders", due == 0 ? Hindi.f("%1$s km", String.valueOf(Care.odo(a.prefs)))
                : Hindi.f("%1$s km, %2$s due", String.valueOf(Care.odo(a.prefs)), String.valueOf(due)), new Runnable() {
            @Override
            public void run() {
                String[] names = new String[Care.ITEMS.length + 1];
                Runnable[] acts = new Runnable[names.length];
                names[0] = Hindi.t("Odometer") + ":  " + Hindi.f("%1$s km", String.valueOf(Care.odo(a.prefs)));
                acts[0] = new Runnable() {
                    @Override
                    public void run() {
                        Care.editOdo(a, a.prefs, careChanged);
                    }
                };
                for (int i = 0; i < Care.ITEMS.length; i++) {
                    final int n = i;
                    names[i + 1] = Hindi.t(Care.NAMES[i]) + ":  " + Care.describe(a.prefs, i);
                    acts[i + 1] = new Runnable() {
                        @Override
                        public void run() {
                            Care.edit(a, a.prefs, n, careChanged);
                        }
                    };
                }
                menu(a, "Service reminders", names, acts);
            }
        }));

        // ---- Phone
        g = "Phone";
        int favs = 0;
        for (int i = 0; i < 3; i++) if (PhoneCard.fav(a.prefs, i) != null) favs++;
        r.add(new SettingsView.Row(g, "Favourite contacts", Hindi.f("%1$s of 3 set", String.valueOf(favs)), new Runnable() {
            @Override
            public void run() {
                String[] names = new String[3];
                Runnable[] acts = new Runnable[3];
                for (int i = 0; i < 3; i++) {
                    final int n = i;
                    String[] f = PhoneCard.fav(a.prefs, i);
                    names[i] = (i + 1) + ".  " + (f == null ? Hindi.t("not set") : f[0] + "  " + f[1]);
                    acts[i] = new Runnable() {
                        @Override
                        public void run() {
                            HomeSettings.editFavourite(a, n);
                        }
                    };
                }
                menu(a, "Favourite contacts", names, acts);
            }
        }));
        r.add(new SettingsView.Row(g, "Phone app", appName(pm, a.prefs.app("dock:phone")), HomeSettings.pick(a, "dock:phone", "Phone")));

        // ---- Car signals
        g = "Car signals";
        String l = a.prefs.lights();
        final String[] lightModes = {"auto", "times", "on", "off"};
        final String[] lightNames = {"follow the car (else sunset to sunrise)", "between set times", "always on", "always off"};
        r.add(new SettingsView.Row(g, "Car picture headlights", "times".equals(l) ? Hindi.f("between %1$s and %2$s", Lights.hhmm(a.prefs.num("lightsFrom", 18 * 60 + 30)),
                Lights.hhmm(a.prefs.num("lightsTo", 6 * 60 + 30))) : Hindi.t(lightNames[Math.max(0, java.util.Arrays.asList(lightModes).indexOf(l))]),
                new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Car picture headlights"))
                                .setSingleChoiceItems(tr(lightNames), java.util.Arrays.asList(lightModes).indexOf(a.prefs.lights()), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        a.prefs.setLights(lightModes[w]);
                                        d.dismiss();
                                        a.hub.notifyIfChanged(true);
                                        if (w == 1) lightTimes(a);
                                        else if (a.settingsView != null) a.settingsView.refresh();
                                    }
                                }).setNegativeButton(Hindi.t("Cancel"), null).show();
                    }
                }));
        final Lights.Done relisten = new Lights.Done() {
            @Override
            public void done() {
                a.hub.stop();
                a.hub.start();
                a.hub.notifyIfChanged(true);
            }
        };
        final String[] revModes = {"lines", "app", "off"};
        final String[] revNames = {"show parking guide lines", "open the camera app", "do nothing"};
        r.add(new SettingsView.Row(g, "In reverse", Hindi.t(revNames[Math.max(0, java.util.Arrays.asList(revModes).indexOf(a.prefs.str("reverse", "lines")))])
                + (a.hub.xt != null && a.hub.xt.has("getReverseStatus") ? "" : Hindi.t(" (needs Topway's service)")), new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(revModes).indexOf(a.prefs.str("reverse", "lines"));
                a.prefs.put("reverse", revModes[(i + 1) % revModes.length]);
            }
        }));
        r.add(new SettingsView.Row(g, "Rear camera app", appName(pm, a.prefs.app("reverse:cam")), HomeSettings.pick(a, "reverse:cam", "Rear camera")));
        r.add(new SettingsView.Row(g, "Preview the reverse guide lines", null, new Runnable() {
            @Override
            public void run() {
                final View v = new ReverseView(a);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        a.root.removeView(v);
                    }
                });
                a.root.addView(v, new android.widget.FrameLayout.LayoutParams(-1, -1));
                Toast.makeText(a, Hindi.t("Tap to close"), Toast.LENGTH_SHORT).show();
            }
        }));
        int learnt = (a.prefs.lightSignal() != null ? 1 : 0) + (a.prefs.signal("left") != null || a.prefs.signal("right") != null ? 1 : 0)
                + (a.prefs.signal("door") != null ? 1 : 0) + (a.prefs.signal("belt") != null ? 1 : 0);
        r.add(new SettingsView.Row(g, "Car signals setup", Hindi.f("%1$s of 4 set up", String.valueOf(learnt)), new Runnable() {
            @Override
            public void run() {
                String yes = Hindi.t("set up"), no = Hindi.t("not set up");
                menu(a, "Car signals setup", new String[]{
                        Hindi.t("Headlights") + ":  " + (a.prefs.lightSignal() == null ? no : yes),
                        Hindi.t("Indicators") + ":  " + (a.prefs.signal("left") == null && a.prefs.signal("right") == null ? no : yes),
                        Hindi.t("Doors") + ":  " + (a.prefs.signal("door") == null ? no : yes),
                        Hindi.t("Seat belt") + ":  " + (a.prefs.signal("belt") == null ? no : yes)}, new Runnable[]{
                        new Runnable() {
                            @Override
                            public void run() {
                                Lights.deep = false;
                                Lights.setup(a, a.prefs, relisten);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                Lights.deep = false;
                                Lights.indicatorSetup(a, a.prefs, relisten);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                Lights.deep = false;
                                Lights.learn(a, a.prefs, "door", relisten);
                            }
                        },
                        new Runnable() {
                            @Override
                            public void run() {
                                Lights.deep = false;
                                Lights.learn(a, a.prefs, "belt", relisten);
                            }
                        }});
            }
        }));
        r.add(new SettingsView.Row(g, "Headlight setup, deep search", "slower; also reads the system apps", new Runnable() {
            @Override
            public void run() {
                Lights.deep = true;
                a.setupLights();
            }
        }));

        // ---- Help
        g = "Help";
        r.add(new SettingsView.Row(g, "Hidden shortcuts", "what holding a box or a button does", new Runnable() {
            @Override
            public void run() {
                Hints.list(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Reset GPS", "if it takes long to find where you are", new Runnable() {
            @Override
            public void run() {
                Toast.makeText(a, Drive.resetGps(a) ? Hindi.t("GPS data cleared; it can take a few minutes outdoors to find you again")
                        : Hindi.t("This unit's GPS doesn't allow a reset"), Toast.LENGTH_LONG).show();
            }
        }));
        if (Features.on(a.prefs, "updates")) {
            r.add(new SettingsView.Row(g, "Check for updates now", "looks for a newer OpenRath", new Runnable() {
                @Override
                public void run() {
                    Updates.check(a, true);
                }
            }));
        }
        final boolean home = Compat.isHome(a);
        r.add(new SettingsView.Row(g, "Home app", home ? "OpenRath; the unit starts in it" : "not OpenRath; tap to make the unit start in it", new Runnable() {
            @Override
            public void run() {
                if (home) Toast.makeText(a, Hindi.t("OpenRath is already the home app"), Toast.LENGTH_SHORT).show();
                else if (!Compat.askHome(a)) Toast.makeText(a, Hindi.t("This unit has no screen to choose the home app; press Home and choose OpenRath, Always"), Toast.LENGTH_LONG).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Run setup again", "language, car, look, apps and the home app, step by step", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                a.prefs.put("setupDone", "");
                Setup.show(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Diagnostics", "send a photo if something doesn't work", new Runnable() {
            @Override
            public void run() {
                HomeSettings.diagnostics(a);
            }
        }));
        if (a.topway()) r.add(new SettingsView.Row(g, "How the stock launcher does it", "the radio and light names inside the stock apps", new Runnable() {
            @Override
            public void run() {
                Peek.run(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Copy the stock launcher", "to Download and USB, to send for a closer look", new Runnable() {
            @Override
            public void run() {
                Peek.save(a);
            }
        }));
        if (a.topway()) r.add(new SettingsView.Row(g, "Unit report", "how the stock radio and launcher work", new Runnable() {
            @Override
            public void run() {
                Probe.run(a, a.prefs.app("media:radio"), a.hub.xt);
            }
        }));
        if (Crash.last() != null) {
            r.add(new SettingsView.Row(g, "Clear the crash note", "and retry skipped steps", new Runnable() {
                @Override
                public void run() {
                    Crash.forgive();
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Android settings", null, new Runnable() {
            @Override
            public void run() {
                a.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }));
        r.add(new SettingsView.Row(g, "Version", "OpenRath " + a.versionName() + (Features.dev(a.prefs) ? Hindi.t(", developer mode") : ""), new Runnable() {
            @Override
            public void run() {
                HomeSettings.versionTapped(a);
            }
        }));
        Extras.rows(a, r);
        if (!Features.dev(a.prefs)) {
            for (java.util.Iterator<SettingsView.Row> it = r.iterator(); it.hasNext(); ) {
                if (DEV_ROWS.contains(it.next().title)) it.remove();
            }
        }
        return r;
    }

    /** Fault-finding and test rows that only developer mode shows. */
    private static final java.util.Set<String> DEV_ROWS = new java.util.HashSet<>(java.util.Arrays.asList(
            "Diagnostics", "How the stock launcher does it", "Unit report", "Clear the crash note", "Copy the stock launcher", "Speed filter",
            "Headlight setup, deep search", "FM radio test (find how this unit starts FM)",
            "Built-in FM tuner (without the stock radio app)", "FM frequency to start on"));

    /** A list of choices, each opening its own setting. */
    private static void menu(HomeActivity a, String title, String[] names, final Runnable[] acts) {
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t(title))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        acts[w].run();
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    private static void levelRows(final HomeActivity a, java.util.List<SettingsView.Row> r) {
        final String g = "Level";
        int lv = Features.level(a.prefs);
        r.add(new SettingsView.Row(g, "OpenRath level", Hindi.t(Features.LEVEL_NAMES[lv]) + ": " + Hindi.t(Features.LEVEL_ABOUT[lv]), new Runnable() {
            @Override
            public void run() {
                HomeSettings.askLevel(a, false);
            }
        }));
        r.add(new SettingsView.Row(g, "Language", "hi".equals(Hindi.lang(a.prefs)) ? "हिन्दी" : "English", new Runnable() {
            @Override
            public void run() {
                a.prefs.put("lang", "hi".equals(Hindi.lang(a.prefs)) ? "en" : "hi");
                Hindi.load(a, a.prefs);
                a.build();   // the dashboard in the new language too
                if (a.settingsView != null) HomeSettings.closeAndReopenSettings(a);
            }
        }));
        if (Features.dev(a.prefs)) {
            r.add(new SettingsView.Row(g, "Developer mode", "on: every switch, diagnostics and tests. Tap to turn off", new Runnable() {
                @Override
                public void run() {
                    a.prefs.put("devMode", "off");
                    a.devTaps = 0;
                    Toast.makeText(a, Hindi.t("Developer mode is off"), Toast.LENGTH_SHORT).show();
                    if (a.settingsView != null) a.settingsView.refresh();
                }
            }));
        }
    }

    private SettingsRows() {
    }
}
