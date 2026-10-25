package org.openrath.launcher;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * What the settings rows open: the OpenRath level, developer mode (seven taps on the version),
 * diagnostics, the built-in FM tuner test, the owner's own pictures, favourite numbers, the name
 * under the car, a box's source, and picking apps, stations and songs; and Settings reopened in
 * a new language.
 */
final class HomeSettings {

    /** Settings: try OpenRath's own FM tuner, and keep it if it plays. */
    static void tryTuner(final HomeActivity a) {
        if (Tuner.chosen(a.prefs)) {
            new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(Hindi.t("Built-in FM tuner is on"))
                    .setPositiveButton(Hindi.t("Keep it"), null)
                    .setNegativeButton(Hindi.t("Use the stock radio again"), new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            Tuner.stop(a);
                            a.prefs.put("fmTuner", "");
                            if (a.settingsView != null) a.settingsView.refresh();
                        }
                    }).show();
            return;
        }
        Toast.makeText(a, Hindi.t("Trying the FM chip…"), Toast.LENGTH_SHORT).show();
        String err = Tuner.play(a, a.prefs, true);
        String report = android.text.TextUtils.join("\n", Tuner.steps);
        if (err != null) {
            Tuner.stop(a);
            a.prefs.put("fmTuner", "no");
            new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(Hindi.t("The built-in tuner can't play on this unit"))
                    .setMessage(report + "\n\n" + Hindi.t("OpenRath keeps using the stock radio app for FM. Please send a photo of this."))
                    .setPositiveButton(Hindi.t("OK"), null).show();
            if (a.settingsView != null) a.settingsView.refresh();
            return;
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.f("Can you hear FM %1$s?", Tuner.freqText()))
                .setMessage(report + "\n\n" + Hindi.t("Turn the volume up if it's quiet. If it plays, OpenRath will play FM by itself from now on: "
                        + "no stock radio app, no screen opening."))
                .setPositiveButton(Hindi.t("Yes, use it"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        a.prefs.put("fmTuner", "own");
                        MediaCard.owner = "radio";
                        if (a.settingsView != null) a.settingsView.refresh();
                        a.hub.notifyIfChanged(true);
                    }
                })
                .setNegativeButton(Hindi.t("No sound"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Tuner.stop(a);
                        a.prefs.put("fmTuner", "no");
                        if (a.settingsView != null) a.settingsView.refresh();
                    }
                }).setCancelable(false).show();
    }

    static void diagnostics(final HomeActivity a) {
        StringBuilder b = new StringBuilder();
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        b.append("OpenRath ").append(a.versionName()).append(", Android ").append(Build.VERSION.RELEASE)
                .append(", screen ").append(dm.widthPixels).append("x").append(dm.heightPixels).append("\n\n");
        String[] sig = a.prefs.lightSignal();
        b.append("Headlights: ").append(a.prefs.lights());
        if (sig != null) {
            b.append(", follows ").append(sig[0]).append(" (off=").append(sig[1]).append(", on=").append(sig[2])
                    .append(", now=").append(Lights.read(a, sig[0])).append(")");
        } else {
            b.append(", not set up; night mode now ").append(Lights.read(a, "uimode:night"));
        }
        b.append(" → car shows ").append(a.hub.lightsOn() ? "ON" : "OFF").append("\n");
        String radio = a.prefs.app("media:radio");
        b.append("Radio app: ").append(radio == null ? "none found" : radio + (Compat.launch(a.getPackageManager(), radio) == null ? " (not installed)" : "")).append("\n");
        String music = a.prefs.app("media:music");
        b.append("Music app: ").append(music == null ? "whatever is playing" : music).append("\n");
        b.append("Navigation app: ").append(a.prefs.app("dock:navi")).append("\n");
        b.append("Track info access: ").append(a.hub.mediaAccess ? "yes" : "no").append(", players seen: ")
                .append(a.hub.sessionPkgs.isEmpty() ? "none" : android.text.TextUtils.join(", ", a.hub.sessionPkgs))
                .append("\n");
        b.append("Audio playing: ").append(a.hub.audioActive ? "yes" : "no").append("\n");
        long age = a.hub.fixAge();
        b.append("GPS: ").append(a.hub.fix == null ? "no position yet" : age < 0 ? "last known position only"
                : "fix " + age + " s ago").append(", speed ").append(a.hub.speed())
                .append(Drive.fixAt == 0 ? ", trip service no fix" : ", trip service fix " + (System.currentTimeMillis() - Drive.fixAt) / 1000 + " s ago").append("\n");
        String[] ls = a.prefs.signal("left"), rs = a.prefs.signal("right");
        b.append("Indicators: ").append(ls == null && rs == null ? "not set up" : "left " + (ls == null ? "-" : ls[0] + "=" + Lights.read(a, ls[0]))
                + ", right " + (rs == null ? "-" : rs[0] + "=" + Lights.read(a, rs[0]))).append("\n");
        b.append("Sources: radio ").append(a.prefs.source("radio")).append(", music ").append(a.prefs.source("music"))
                .append(", navigation ").append(a.prefs.source("navi")).append(", internet ").append(a.hub.online ? "yes" : "no").append("\n");
        b.append("Player: ").append(Player.mode == null ? "idle" : Player.mode + (Player.playing ? " playing" : " stopped")
                + (Player.status.isEmpty() ? "" : " (" + Player.status + ")")).append("\n");
        b.append("Stereo: ").append(Stereo.name(a)).append(", using ").append(a.hub.unit.name()).append("\n");
        if (a.hub.xt != null) b.append("Topway radio service: ").append(a.hub.xt.ready() ? "connected" + (a.hub.xt.registered ? ", listening" : "")
                + (a.hub.xt.freqText.isEmpty() ? "" : ", FM " + a.hub.xt.freqText) : "not connected " + a.hub.xt.error).append("\n");
        b.append("FM commands: start ").append(a.prefs.fmCmd("play").isEmpty() ? "not found yet" : Fm.label(a.prefs.fmCmd("play")))
                .append(", stop ").append(a.prefs.fmCmd("stop").isEmpty() ? "not found yet" : Fm.label(a.prefs.fmCmd("stop")))
                .append(", callbacks ").append(a.hub.xt == null ? "-" : a.hub.xt.callbacks.size()).append("\n");
        if (a.hub.xt != null && !a.hub.xt.heard.isEmpty()) b.append("Radio service said: ")
                .append(android.text.TextUtils.join("  ", a.hub.xt.heard.subList(Math.max(0, a.hub.xt.heard.size() - 8), a.hub.xt.heard.size()))).append("\n");
        if (false) {
            b.append("Radio service commands: ").append(android.text.TextUtils.join(" ", a.hub.xt.methods)).append("\n");
            if (!a.hub.xt.heard.isEmpty()) b.append("Radio service said: ")
                    .append(android.text.TextUtils.join(" ", a.hub.xt.heard.subList(Math.max(0, a.hub.xt.heard.size() - 6), a.hub.xt.heard.size()))).append("\n");
        }
        b.append("Phone: ").append(a.hub.btConnected ? "connected" : "not connected").append(" (radio service says ")
                .append(a.hub.xt == null ? "-" : String.valueOf(a.hub.btRaw)).append(", last Bluetooth message ")
                .append(a.hub.xt == null || a.hub.xt.btEvent.isEmpty() ? "none" : a.hub.xt.btEvent).append(", Android Bluetooth ")
                .append(Hub.androidBt() ? "connected" : "not connected").append(")\n");
        b.append("Lights, as the unit's service sees them: ");
        try {
            b.append(a.hub.xt == null || !a.hub.xt.ready() ? "service not connected" : a.hub.xt.lightGetters().toString());
            android.content.ContentResolver cr = a.getContentResolver();
            for (String k : new String[]{"day_night_mode", "nightMode", "night_mode", "screen_brightness"}) {
                String v = android.provider.Settings.System.getString(cr, k);
                if (v != null) b.append(", ").append(k).append("=").append(v);
            }
        } catch (Throwable e) {
            b.append(e.getClass().getSimpleName());
        }
        b.append("\n");
        b.append("Last call: ").append(a.hub.lastCall == null ? "none seen yet" : a.hub.lastCall[1] + " " + a.hub.lastCall[0] + " " + a.hub.lastCall[2])
                .append(a.hub.xt == null ? "" : ", last Bluetooth text \"" + a.hub.xt.btText + "\"")
                .append(a.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED ? "" : ", call log not allowed").append("\n");
        b.append("Topway radio control: ").append(!Tw.available() ? "not on this unit"
                : a.hub.fm == null ? "present" : a.hub.fm.seen ? "FM " + a.hub.fm.frequency() + (a.hub.fm.on ? " (playing)" : "")
                + (a.hub.fm.name.isEmpty() ? "" : " " + a.hub.fm.name) : "present, no answer from the tuner yet").append("\n");
        b.append("Trip: today ").append(String.format(java.util.Locale.US, "%.1f km, %d min", Drive.todayKm, Drive.todaySec / 60))
                .append(", speed limit ").append(Drive.limit > 0 ? Drive.limit + " km/h" : "not known here")
                .append(Drive.cameraM >= 0 ? ", camera " + Drive.cameraM + " m" : "")
                .append(Drive.alertError.isEmpty() ? "" : " (" + Drive.alertError + ")").append("\n");
        if (Crash.last() != null) b.append("Last problem: ").append(Crash.last()).append("\n");
        String caught = Diag.recent();
        if (!caught.isEmpty()) b.append("Errors caught since the app started (newest first):\n").append(caught);
        String exit = Crash.lastExit(a);
        if (exit != null) b.append("Last closed because ").append(exit).append("\n");
        b.append("Weather: ").append(a.hub.temp).append(" ").append(a.hub.weather).append(" ").append(a.hub.place);
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Diagnostics")).setMessage(b.toString()).setPositiveButton(Hindi.t("OK"), null).show();
    }

    /** Settings again from scratch (a new language), without asking for the valet PIN again. */
    static void closeAndReopenSettings(final HomeActivity a) {
        boolean unlocked = a.ownerUnlocked;
        a.closeSettings();
        a.ownerUnlocked = unlocked;
        a.openSettings();
    }

    /** Seven taps on the version turn developer mode on, as on Android itself. */
    static void versionTapped(final HomeActivity a) {
        if (Managed.devBlocked(a.prefs)) {
            Toast.makeText(a, Hindi.f("Developer mode: %1$s", Hindi.t(Managed.NOTE).toLowerCase(java.util.Locale.US)), Toast.LENGTH_SHORT).show();
            return;
        }
        if (Features.dev(a.prefs)) {
            Toast.makeText(a, Hindi.t("Developer mode is already on"), Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        a.devTaps = now - a.devTapAt > 3000 ? 1 : a.devTaps + 1;
        a.devTapAt = now;
        if (a.devTaps >= 7) {
            a.prefs.put("devMode", "on");
            Toast.makeText(a, Hindi.t("Developer mode is on: every switch, diagnostics and tests are in Settings"), Toast.LENGTH_LONG).show();
            if (a.settingsView != null) a.settingsView.refresh();
        } else if (a.devTaps >= 3) {
            Toast.makeText(a, Hindi.f("%1$s more taps for developer mode", String.valueOf(7 - a.devTaps)), Toast.LENGTH_SHORT).show();
        }
    }

    /** Simple, Full or Custom. At first start Simple is ticked; "Not now" keeps everything as it is. */
    static void askLevel(final HomeActivity a, final boolean first) {
        if (Managed.levelLocked(a.prefs)) {
            if (!first) Toast.makeText(a, Hindi.f("OpenRath level: %1$s", Hindi.t(Managed.NOTE).toLowerCase(java.util.Locale.US)), Toast.LENGTH_SHORT).show();
            return;
        }
        final int[] pick = {first ? Features.SIMPLE : Features.level(a.prefs)};
        String[] items = new String[Features.LEVELS.length];
        for (int i = 0; i < items.length; i++) items[i] = Hindi.t(Features.LEVEL_NAMES[i]) + "\n" + Hindi.t(Features.LEVEL_ABOUT[i]);
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(first ? Hindi.t("How much should OpenRath show?") : Hindi.t("OpenRath level"))
                .setSingleChoiceItems(items, pick[0], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        pick[0] = w;
                    }
                })
                .setPositiveButton(Hindi.t("Use this"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Features.setLevel(a.prefs, pick[0]);
                        Toast.makeText(a, Hindi.f("%1$s. Change it any time in Settings > OpenRath level", Hindi.t(Features.LEVEL_NAMES[pick[0]])),
                                Toast.LENGTH_LONG).show();
                        Extras.featuresChanged(a);
                        if (a.settingsView != null) a.settingsView.refresh();
                    }
                })
                .setNegativeButton(first ? Hindi.t("Not now") : Hindi.t("Cancel"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (first) a.prefs.put("level", "custom");   // asked once; everything stays as it was
                    }
                }).show();
    }

    static void pickPicture(final HomeActivity a, final int which) {
        final java.io.File mine = Ui.customFile(a, HomeActivity.ALL_PICTURES[which]);
        if (mine.exists()) {
            new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setItems(new String[]{Hindi.t("Choose another picture"), Hindi.t("Use the built-in one")}, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            if (w == 0) choosePicture(a, which);
                            else {
                                mine.delete();
                                a.restyle();
                            }
                        }
                    }).setNegativeButton(Hindi.t("Cancel"), null).show();
            return;
        }
        choosePicture(a, which);
    }

    private static void choosePicture(final HomeActivity a, int which) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {
            a.startActivityForResult(Intent.createChooser(i, Hindi.t("Choose a picture")), 61 + which);
        } catch (Exception e) {
            Toast.makeText(a, Hindi.t("No app on the unit can pick pictures. Put a PNG in a theme pack instead (Make a theme to edit)."), Toast.LENGTH_LONG).show();
        }
    }

    static void editFavourite(final HomeActivity a, final int i) {
        String[] f = PhoneCard.fav(a.prefs, i);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.px(20);
        box.setPadding(pad, pad / 2, pad, 0);
        final android.widget.EditText name = new android.widget.EditText(a), num = new android.widget.EditText(a);
        name.setHint(Hindi.t("Name"));
        num.setHint(Hindi.t("Phone number"));
        num.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        if (f != null) {
            name.setText(f[0]);
            num.setText(f[1]);
        }
        box.addView(name);
        box.addView(num);
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.f("Favourite %1$s (empty to remove)", String.valueOf(i + 1)))
                .setView(box)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim().replace("|", " "), p = num.getText().toString().trim();
                        a.prefs.put("fav" + (i + 1), n.isEmpty() || p.isEmpty() ? "" : n + "|" + p);
                        a.hub.notifyIfChanged(true);
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    static void cycle(final HomeActivity a, String what, String[] all) {
        String cur = a.prefs.source(what);
        int i = java.util.Arrays.asList(all).indexOf(cur);
        a.prefs.setSource(what, all[(i + 1) % all.length]);
        a.hub.notifyIfChanged(true);
    }

    static void editWelcome(final HomeActivity a) {
        final android.widget.EditText e = new android.widget.EditText(a);
        e.setText(a.prefs.welcome());
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Your name, on the home screen (empty to hide)"))
                .setView(e)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        a.prefs.setWelcome(e.getText().toString().trim());
                        a.prefs.put("layUser", "");   // the picture layouts' own name, from before there was one
                        a.layoutChanged();
                        a.hub.notifyIfChanged(true);
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    /** Pick a station or song from the player's list, loading the list first if needed. */
    static void pickTrack(final HomeActivity a, final boolean web) {
        final java.util.List<Player.Track> list = web ? Player.stations : Player.songs;
        if (list.isEmpty()) {
            final android.app.ProgressDialog wait = android.app.ProgressDialog.show(a, null,
                    web ? Hindi.t("Finding stations…") : Hindi.t("Looking for music…"), true);
            Bg.run(new Runnable() {
                @Override
                public void run() {
                    final java.util.List<Player.Track> l = web ? Player.fetchStations(a)
                            : Player.scanSongs(a);
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            wait.dismiss();
                            if (l.isEmpty()) {
                                Toast.makeText(a, web ? Hindi.t("Web radio unreachable") : Hindi.t("No music files found"),
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            list.clear();
                            list.addAll(l);
                            pickTrack(a, web);
                        }
                    });
                }
            });
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            Player.Track t = list.get(i);
            names[i] = t.sub.isEmpty() ? t.name : t.name + "  ·  " + t.sub;
        }
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(web ? Hindi.t("Online radio") : Hindi.t("Songs"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        a.prefs.setSource(web ? "radio" : "music", web ? "web" : "files");
                        Player.send(a, Player.PICK, web ? Player.WEB : Player.FILES, which);
                    }
                })
                .setNegativeButton(Hindi.t("Close"), null).show();
    }

    static Runnable pick(final HomeActivity a, final String key, final String name) {
        return new Runnable() {
            @Override
            public void run() {
                AppPicker.show(a, Hindi.f("Choose the app for %1$s", Hindi.t(name)), new AppPicker.Picked() {
                    @Override
                    public void picked(String p) {
                        a.prefs.setApp(key, p);
                    }
                });
            }
        };
    }

    private HomeSettings() {
    }
}
