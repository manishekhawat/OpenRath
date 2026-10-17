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
        return i < 0 ? c : Prefs.CONTENT_NAMES[i];
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
                            }).setNegativeButton(Hindi.t("Cancel"), null).show();
                }
            }));
            r.add(new SettingsView.Row(g, "Order of the details", "tap one to put it first", new Runnable() {
                @Override
                public void run() {
                    final java.util.List<String> order = Layouts.statItems(a.prefs, cur.id, stats);
                    if (order.size() < 2) {
                        Toast.makeText(a, Hindi.t("Show two or more details first"), Toast.LENGTH_SHORT).show();
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
                            }).setNegativeButton(Hindi.t("Done"), null).show();
                }
            }));
        }
        if (Layouts.part(cur, "user") != null) {
            String n = a.prefs.str("layUser", "");
            r.add(new SettingsView.Row(g, "Driver's name", n.isEmpty() ? (Profiles.current(a.prefs).isEmpty() ? "not set" : Hindi.f("%1$s (driver profile)", Profiles.current(a.prefs))) : n,
                    new Runnable() {
                        @Override
                        public void run() {
                            final android.widget.EditText e = new android.widget.EditText(a);
                            e.setSingleLine(true);
                            e.setText(a.prefs.str("layUser", ""));
                            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                    .setTitle(Hindi.t("Driver's name")).setView(e)
                                    .setPositiveButton(Hindi.t("OK"), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            a.prefs.put("layUser", e.getText().toString().trim());
                                            a.layoutChanged();
                                            if (a.settingsView != null) a.settingsView.refresh();
                                        }
                                    }).setNegativeButton(Hindi.t("Cancel"), null).show();
                        }
                    }));
        }
        if (cur.roadHorizon > 0) {
            r.add(new SettingsView.Row(g, "Moving road lines", Layouts.roadOn(a.prefs) ? "on, while driving" : "off", new Runnable() {
                @Override
                public void run() {
                    a.prefs.put("layRoad", Layouts.roadOn(a.prefs) ? "off" : "on");
                    a.layoutChanged();
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

    /** Everything on the settings screen, by group. */
    static java.util.List<SettingsView.Row> settingsRows(final HomeActivity a) {
        final java.util.List<SettingsView.Row> r = new java.util.ArrayList<>();
        final android.content.pm.PackageManager pm = a.getPackageManager();

        // ---- Level
        levelRows(a, r);

        // ---- Dashboard
        String g = "Dashboard";
        for (int i = 0; i < Prefs.SLOTS; i++) {
            final int slot = i;
            r.add(new SettingsView.Row(g, HomeDialogs.slotName(a, i), Hindi.t(contentName(a.prefs.slot(i))), new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle(Hindi.f("%1$s shows", Hindi.t(HomeDialogs.slotName(a, slot))))
                            .setSingleChoiceItems(tr(Prefs.CONTENT_NAMES), java.util.Arrays.asList(Prefs.CONTENTS).indexOf(a.prefs.slot(slot)),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int which) {
                                            a.prefs.setSlot(slot, Prefs.CONTENTS[which]);
                                            a.build();
                                            d.dismiss();
                                        }
                                    })
                            .setNegativeButton(Hindi.t("Cancel"), null).show();
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Move boxes", "drag and drop on the dashboard", new Runnable() {
            @Override
            public void run() {
                a.startMoving();
            }
        }));
        final int[] sizes = {80, 90, 100, 110, 120, 130};
        r.add(new SettingsView.Row(g, "Size of text and boxes", a.prefs.num("uiScale", 100) + "%", new Runnable() {
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
        final String[] turns = {"auto", "landscape", "portrait"};
        r.add(new SettingsView.Row(g, "Screen way up", Ui.portrait(a.dash.getWidth(), a.dash.getHeight()) ? Hindi.f("%1$s, boxes stacked", Hindi.t(a.prefs.str("turn", "auto"))) : a.prefs.str("turn", "auto"), new Runnable() {
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
        r.add(new SettingsView.Row(g, "Welcome name", a.prefs.welcome().isEmpty() ? "hidden" : a.prefs.welcome(), new Runnable() {
            @Override
            public void run() {
                HomeSettings.editWelcome(a);
            }
        }));
        final String[] dimModes = {"off", "sunset", "lights"};
        final String[] dimNames = {"off", "after sunset", "with the headlights"};
        r.add(new SettingsView.Row(g, "Night dimming", dimNames[Math.max(0, java.util.Arrays.asList(dimModes).indexOf(a.prefs.str("dim", "off")))],
                new Runnable() {
                    @Override
                    public void run() {
                        int i = java.util.Arrays.asList(dimModes).indexOf(a.prefs.str("dim", "off"));
                        a.prefs.put("dim", dimModes[(i + 1) % dimModes.length]);
                        a.hub.notifyIfChanged(true);
                    }
                }));
        r.add(new SettingsView.Row(g, "How dark at night", a.prefs.num("dimLevel", 40) + "%", new Runnable() {
            @Override
            public void run() {
                int[] steps = {20, 30, 40, 50, 60};
                int i = 0;
                while (i < steps.length && steps[i] != a.prefs.num("dimLevel", 40)) i++;
                a.prefs.put("dimLevel", String.valueOf(steps[(i + 1) % steps.length]));
                a.hub.notifyIfChanged(true);
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
        if (Features.on(a.prefs, "bootArt")) r.add(new SettingsView.Row(g, "Boot logo and animation", "make them with your car's badge and name", new Runnable() {
            @Override
            public void run() {
                BootArt.menu(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Make your own theme", Hindi.f("copies %1$s to OpenRath/themes to edit", Theme.name), new Runnable() {
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
            r.add(new SettingsView.Row(g, picNames[i], Ui.customFile(a, HomeActivity.PICTURES[i]).exists() ? "your picture" : "built-in", new Runnable() {
                @Override
                public void run() {
                    HomeSettings.pickPicture(a, n);
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Use the built-in pictures again", null, new Runnable() {
            @Override
            public void run() {
                for (String n : HomeActivity.PICTURES) Ui.customFile(a, n).delete();
                a.restyle();
            }
        }));
        r.add(new SettingsView.Row(g, "Tip", "a PNG with a transparent background looks best; the car facing you, about 820 x 480", new Runnable() {
            @Override
            public void run() {
            }
        }));

        // ---- Dock
        g = "Buttons bar";
        final String[] places = {"left", "right", "top", "bottom"};
        r.add(new SettingsView.Row(g, "Position", a.prefs.str("dockAt", "left"), new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(places).indexOf(a.prefs.str("dockAt", "left"));
                a.prefs.put("dockAt", places[(i + 1) % places.length]);
                a.restyle();
            }
        }));
        for (int i = 0; i <= Prefs.DOCK.length; i++) {
            final String key = i < Prefs.DOCK.length ? Prefs.DOCK[i] : "settings";
            final String label = i < Prefs.DOCK.length ? Prefs.DOCK_NAMES[i] : "Settings";
            final int code = java.util.Arrays.asList(HomeActivity.ALL_PICTURES).indexOf("dock_" + key);
            r.add(new SettingsView.Row(g, "Icon: " + label, Ui.customFile(a, "dock_" + key).exists() ? "your picture" : "built-in", new Runnable() {
                @Override
                public void run() {
                    HomeSettings.pickPicture(a, code);
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Use the built-in icons again", null, new Runnable() {
            @Override
            public void run() {
                for (String n : HomeActivity.ALL_PICTURES) if (n.startsWith("dock_")) Ui.customFile(a, n).delete();
                a.restyle();
            }
        }));
        r.add(new SettingsView.Row(g, "What each button opens", "long-press a button on the bar to choose its app", new Runnable() {
            @Override
            public void run() {
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
                    Toast.makeText(a, Hindi.t("Topway's radio service isn't connected; only the MCU link can be tried"), Toast.LENGTH_LONG).show();
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
        r.add(new SettingsView.Row(g, "Choose a song", null, new Runnable() {
            @Override
            public void run() {
                HomeSettings.pickTrack(a, false);
            }
        }));
        r.add(new SettingsView.Row(g, "Look for music files again", "after plugging in a USB stick", new Runnable() {
            @Override
            public void run() {
                Player.songs.clear();
                Toast.makeText(a, Hindi.t("Music files are found again on the next play"), Toast.LENGTH_LONG).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Music app", appName(pm, a.prefs.app("media:music")), HomeSettings.pick(a, "media:music", "Music")));
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
        if (Features.on(a.prefs, "offlineMap")) r.add(new SettingsView.Row(g, "Map kept for no internet", Hindi.f("about %1$s MB of places you've seen; tap to clear",
                String.valueOf(Tiles.sizeMb())), new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage(Hindi.t("Delete the kept map? Places will show again once seen with internet."))
                        .setPositiveButton(Hindi.t("Delete"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Tiles.clear();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        String offNav = OfflineNav.app(a, a.prefs);
        r.add(new SettingsView.Row(g, "Guide without internet in", offNav == null ? "no offline map app: install Organic Maps" : OfflineNav.label(a, offNav),
                HomeSettings.pick(a, "offlineNav", "Offline map app")));
        final java.util.List<String> engines = Speak.engines(a);
        r.add(new SettingsView.Row(g, "Spoken voice", engines.contains(Speak.GOOGLE_TTS) ? "Speech Services by Google (tap to pick it, or get the Hindi voice)"
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
                    }
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Guide routes in", Prefs.sourceName(a.prefs.source("navi")), new Runnable() {
            @Override
            public void run() {
                HomeSettings.cycle(a, "navi", Prefs.NAV_SRC);
            }
        }));
        r.add(new SettingsView.Row(g, "Navigation button opens", appName(pm, a.prefs.app("dock:navi")), HomeSettings.pick(a, "dock:navi", "Navigation")));
        r.add(new SettingsView.Row(g, "Save the parking spot here", Drive.parkAt == 0 ? "none saved"
                : Hindi.f("saved %1$s", Cards.TripCard.ago(Drive.parkAt)), new Runnable() {
            @Override
            public void run() {
                a.parkHere();
            }
        }));

        // ---- Driving
        g = "Driving";
        r.add(new SettingsView.Row(g, "Speed limit and camera alerts", a.prefs.alertsOn() ? "beep and voice" : "silent", new Runnable() {
            @Override
            public void run() {
                a.prefs.setAlertsOn(!a.prefs.alertsOn());
            }
        }));
        r.add(new SettingsView.Row(g, "Warn above a speed anywhere", a.prefs.warnSpeed() == 0 ? "off" : Hindi.f("%1$s km/h", String.valueOf(a.prefs.warnSpeed())), new Runnable() {
            @Override
            public void run() {
                int[] steps = {0, 80, 90, 100, 110, 120};
                int i = 0;
                while (i < steps.length && steps[i] != a.prefs.warnSpeed()) i++;
                a.prefs.setWarnSpeed(steps[(i + 1) % steps.length]);
            }
        }));
        r.add(new SettingsView.Row(g, "Start today's trip again", Hindi.f("today %1$s km", String.format(java.util.Locale.US, "%.1f", Drive.todayKm)), new Runnable() {
            @Override
            public void run() {
                Drive.resetToday(a);
                a.hub.notifyIfChanged(true);
            }
        }));
        Fuel.Stats fs = Fuel.stats(a.prefs);
        r.add(new SettingsView.Row(g, "Fuel log", fs.fills == 0 ? "no fill-ups yet" : fs.avgKmpl > 0
                ? Hindi.f("%1$s fill-ups, %2$s km/l", String.valueOf(fs.fills), Fuel.fmt(fs.avgKmpl)) : Hindi.f("%1$s fill-ups", String.valueOf(fs.fills)), new Runnable() {
            @Override
            public void run() {
                a.fuel();
            }
        }));
        r.add(new SettingsView.Row(g, "Add a fill-up", null, new Runnable() {
            @Override
            public void run() {
                Fuel.add(a, a.prefs, new Runnable() {
                    @Override
                    public void run() {
                        a.hub.notifyIfChanged(true);
                    }
                });
            }
        }));

        r.add(new SettingsView.Row(g, "Trip history", "weekly and monthly distance, time and costs", new Runnable() {
            @Override
            public void run() {
                Care.history(a, a.prefs);
            }
        }));
        r.add(new SettingsView.Row(g, "Driving score today", Drive.score() + " / 100", new Runnable() {
            @Override
            public void run() {
                Care.history(a, a.prefs);
            }
        }));
        r.add(new SettingsView.Row(g, "Add a toll payment", Cards.tollsThisMonth(a.prefs).replace("  ·  ", ""), new Runnable() {
            @Override
            public void run() {
                Care.addToll(a, a.prefs, new Runnable() {
                    @Override
                    public void run() {
                        a.hub.notifyIfChanged(true);
                    }
                });
            }
        }));
        r.add(new SettingsView.Row(g, "Toll estimate per plaza", Hindi.f("₹%1$s (for route estimates)", String.valueOf(a.prefs.num("tollRate", 100))), new Runnable() {
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
        r.add(new SettingsView.Row(g, "Odometer", Hindi.f("%1$s km (tap to correct)", String.valueOf(Care.odo(a.prefs))), new Runnable() {
            @Override
            public void run() {
                Care.editOdo(a, a.prefs, careChanged);
            }
        }));
        for (int i = 0; i < Care.ITEMS.length; i++) {
            final int n = i;
            r.add(new SettingsView.Row(g, Care.NAMES[i], Care.describe(a.prefs, i), new Runnable() {
                @Override
                public void run() {
                    Care.edit(a, a.prefs, n, careChanged);
                }
            }));
        }

        // ---- Phone
        g = "Phone";
        for (int i = 0; i < 3; i++) {
            final int n = i;
            String[] f = PhoneCard.fav(a.prefs, i);
            r.add(new SettingsView.Row(g, "Favourite " + (i + 1), f == null ? "not set" : f[0] + "  " + f[1], new Runnable() {
                @Override
                public void run() {
                    HomeSettings.editFavourite(a, n);
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Phone app", appName(pm, a.prefs.app("dock:phone")), HomeSettings.pick(a, "dock:phone", "Phone")));

        // ---- Car signals
        g = "Car signals";
        String l = a.prefs.lights();
        r.add(new SettingsView.Row(g, "Car picture headlights", "auto".equals(l) ? "follow the car (else sunset to sunrise)"
                : "times".equals(l) ? "between set times" : "on".equals(l) ? "always on" : "always off",
                new Runnable() {
                    @Override
                    public void run() {
                        String cur = a.prefs.lights();
                        a.prefs.setLights("auto".equals(cur) ? "times" : "times".equals(cur) ? "on" : "on".equals(cur) ? "off" : "auto");
                        a.hub.notifyIfChanged(true);
                    }
                }));
        if ("times".equals(l)) {
            r.add(new SettingsView.Row(g, "Headlight times", Hindi.f("%1$s to %2$s", Lights.hhmm(a.prefs.num("lightsFrom", 18 * 60 + 30)),
                    Lights.hhmm(a.prefs.num("lightsTo", 6 * 60 + 30))), new Runnable() {
                @Override
                public void run() {
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
            }));
        }
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
        r.add(new SettingsView.Row(g, "Headlight setup", a.prefs.lightSignal() == null ? "not set up" : "set up", new Runnable() {
            @Override
            public void run() {
                Lights.deep = false;
                Lights.setup(a, a.prefs, relisten);
            }
        }));
        r.add(new SettingsView.Row(g, "Headlight setup, deep search", "slower; also reads the system apps", new Runnable() {
            @Override
            public void run() {
                Lights.deep = true;
                a.setupLights();
            }
        }));
        r.add(new SettingsView.Row(g, "Indicator setup", a.prefs.signal("left") == null && a.prefs.signal("right") == null ? "not set up" : "set up",
                new Runnable() {
                    @Override
                    public void run() {
                        Lights.deep = false;
                        Lights.indicatorSetup(a, a.prefs, relisten);
                    }
                }));
        for (final String[] w : new String[][]{{"door", "Door setup"}, {"belt", "Seat-belt setup"}}) {
            r.add(new SettingsView.Row(g, w[1], a.prefs.signal(w[0]) == null ? "not set up; for the voice reminders" : "set up", new Runnable() {
                @Override
                public void run() {
                    Lights.deep = false;
                    Lights.learn(a, a.prefs, w[0], relisten);
                }
            }));
        }

        // ---- Help
        g = "Help";
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
            "Diagnostics", "How the stock launcher does it", "Unit report", "Clear the crash note", "Crash reports", "Copy the stock launcher",
            "Headlight setup, deep search", "FM radio test (find how this unit starts FM)",
            "Built-in FM tuner (without the stock radio app)", "FM frequency to start on"));

    private static void levelRows(final HomeActivity a, java.util.List<SettingsView.Row> r) {
        final String g = "Level";
        int lv = Features.level(a.prefs);
        r.add(new SettingsView.Row(g, "OpenRath level", Hindi.t(Features.LEVEL_NAMES[lv]) + ": " + Hindi.t(Features.LEVEL_ABOUT[lv]), new Runnable() {
            @Override
            public void run() {
                HomeSettings.askLevel(a, false);
            }
        }));
        r.add(new SettingsView.Row(g, "Every switch", "each level's features can still be turned on or off one by one, in their own section", new Runnable() {
            @Override
            public void run() {
                Toast.makeText(a, Hindi.t("Look under \"Turn on or off\" at the top of each section"), Toast.LENGTH_LONG).show();
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
