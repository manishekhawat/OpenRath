package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Help > About and licences, Privacy, and Crash reports. */
final class About {

    private static final int DIALOG = android.R.style.Theme_Material_Dialog_Alert;

    static void show(HomeActivity a) {
        // the licence notice and the credits stay in English, as they are written; the rest is translated
        text(a, "OpenRath " + a.versionName(),
                Hindi.t("A free and open-source home screen for Android car stereos.") + "\n\n"
                        + "OpenRath by Manish Shekhawat, https://github.com/manishekhawat/OpenRath\n\n"
                        + "Copyright (C) 2026 Manish Shekhawat and the OpenRath contributors. This program is free software: you can redistribute it and/or "
                        + "modify it under the terms of the GNU General Public License as published by the Free Software Foundation, "
                        + "either version 3 of the License, or (at your option) any later version. It comes with ABSOLUTELY NO WARRANTY. "
                        + "The full licence is in the LICENSE file of the source code. Additional term under GPLv3 section 7(b): "
                        + "anyone who conveys OpenRath or a modified version must keep the credit \"Based on OpenRath by Manish Shekhawat\", "
                        + "with a link to https://github.com/manishekhawat/OpenRath, in its About or credits screen.\n\n"
                        + Hindi.t("Not affiliated with or endorsed by any car or stereo maker; brand names belong to their owners. "
                        + "Car names and logos are trademarks of their owners and are shown only to identify the owner's own car.") + "\n\n"
                        + Hindi.t("THIRD-PARTY PARTS") + "\n"
                        + "• Leaflet 1.9.4: BSD 2-Clause, (c) 2010-2023 Vladimir Agafonkin, (c) 2010-2011 CloudMade\n"
                        + "• leaflet-rotate: GPL-3.0, Iván Sánchez Ortega, Raruto and contributors\n"
                        + "• QR Code Generator for JavaScript: MIT, (c) 2009 Kazuhiko Arase\n"
                        + "• QR Code generator library (Java): MIT, (c) Project Nayuki\n"
                        + "• Inter font: SIL Open Font License 1.1, (c) The Inter Project Authors\n\n"
                        + Hindi.t("THANKS TO") + "\n"
                        + "• Ideas and know-how from fyt-dialer, honda-customizer, MTCD-E_Radio-app, MtcDialer, mtccanbus-decompiled, "
                        + "Femto Car Launcher, Car Nebula and Mini AA. The full list is in CREDITS.md in the source code.\n\n"
                        + Hindi.t("ONLINE SERVICES (free, no account)") + "\n"
                        + "• Map: © OpenStreetMap contributors (ODbL), tiles from tile.openstreetmap.org\n"
                        + "• Weather: Open-Meteo.com (CC BY 4.0)\n"
                        + "• Routing: OSRM project demo server\n"
                        + "• Speed limits, cameras, tolls, petrol pumps, service centres: OpenStreetMap via the Overpass API\n"
                        + "• Place search: Photon by Komoot\n"
                        + "• Place names: BigDataCloud (when the unit has no geocoder)\n"
                        + "• Online radio: radio-browser.info community directory");
    }

    static void privacy(HomeActivity a) {
        text(a, Hindi.t("Privacy"),
                Hindi.t("OpenRath has no account, no ads, no analytics and no tracking. It never sends anything to its makers.") + "\n\n"
                        + Hindi.t("Crash and usage reports (off unless you turn them on in Settings > Features) stay on the unit for you to share. "
                        + "On a fleet's unit, its managed settings can send them once a day to the fleet's own address.") + "\n\n"
                        + Hindi.t("STAYS ON THE UNIT") + "\n"
                        + Hindi.t("Trips, driving score, routes (GPX), fuel and toll logs, parking spot, service reminders, phone favourites, "
                        + "learnt car signals, settings, driver profiles, waterlogging spots and backups. Backups go to OpenRath/backup "
                        + "on the unit's own storage. Dashcam clips go to OpenRath/dashcam and are never uploaded. The parking QR code "
                        + "is made on the unit; only the phone that scans it opens the map link.") + "\n\n"
                        + Hindi.t("SENT ONLY WHEN THE FEATURE IS USED") + "\n"
                        + "• " + Hindi.t("Weather and air quality: your approximate position (3 decimals, about 100 m) to Open-Meteo.") + "\n"
                        + "• " + Hindi.t("QR codes (parking, trip sharing, UPI split, parking reminder) are made on the unit; nothing is sent until a phone scans one.") + "\n"
                        + "• " + Hindi.t("Speed limits, cameras, toll plazas, one-way streets, schools, hospitals, bends, rest stops, petrol pumps and service centres: the area around you to the Overpass API (OpenStreetMap).") + "\n"
                        + "• " + Hindi.t("Accident help: nothing is sent; the paired phone calls your emergency contact. Car documents stay inside the app.") + "\n"
                        + "• " + Hindi.t("Map: the map area you look at to the tile server.") + "\n"
                        + "• " + Hindi.t("Routes: start and destination to the OSRM routing server.") + "\n"
                        + "• " + Hindi.t("Search: what you type or say to Photon. Voice search uses the unit's speech recogniser (usually Google).") + "\n"
                        + "• " + Hindi.t("Online radio: the station list from radio-browser.info, and the stream from the station itself.") + "\n\n"
                        + Hindi.t("Each has an on/off switch at the top of its section in Settings. Messages shown by the ticker are read on the unit and never stored."));
    }

    static void crashes(final HomeActivity a) {
        final List<File> files = Crash.files();
        if (files.isEmpty()) {
            Toast.makeText(a, Hindi.t("No crash reports. Good."), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[files.size()];
        for (int i = 0; i < names.length; i++) names[i] = files.get(i).getName();
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("Crash reports")).setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                final String body = Crash.read(files.get(w));
                new AlertDialog.Builder(a, DIALOG).setTitle(files.get(w).getName()).setMessage(body)
                        .setPositiveButton(Hindi.t("Share"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d2, int x) {
                                Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, "OpenRath crash report").putExtra(Intent.EXTRA_TEXT, body);
                                try {
                                    a.startActivity(Intent.createChooser(s, Hindi.t("Share the report")));
                                } catch (Throwable e) {
                                    Toast.makeText(a, Hindi.t("No app to share with"), Toast.LENGTH_LONG).show();
                                }
                            }
                        }).setNegativeButton(Hindi.t("Close"), null).show();
            }
        }).setNegativeButton(Hindi.t("Close"), null).setNeutralButton(Hindi.t("Delete all"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                for (File f : files) f.delete();
            }
        }).show();
    }

    /** The opt-in usage report: what it holds, to read and share. */
    static void usage(final HomeActivity a) {
        final String body = Reports.text(a.versionName());
        new AlertDialog.Builder(a, DIALOG).setTitle(Hindi.t("Usage report")).setMessage(body)
                .setPositiveButton(Hindi.t("Share"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int x) {
                        Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_SUBJECT, "OpenRath usage report").putExtra(Intent.EXTRA_TEXT, body);
                        try {
                            a.startActivity(Intent.createChooser(s, Hindi.t("Share the report")));
                        } catch (Throwable e) {
                            Toast.makeText(a, Hindi.t("No app to share with"), Toast.LENGTH_LONG).show();
                        }
                    }
                }).setNeutralButton(Hindi.t("Start again"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int x) {
                        Reports.clear();
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    private static void text(HomeActivity a, String title, String body) {
        TextView t = Ui.text(a, 15, 0xFFDDDDDD, "sans-serif", android.view.Gravity.LEFT);
        t.setSingleLine(false);
        t.setText(body);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.px(24), Ui.px(12), Ui.px(24), Ui.px(12));
        ScrollView sv = new ScrollView(a);
        sv.addView(t);
        new AlertDialog.Builder(a, DIALOG).setTitle(title).setView(sv).setPositiveButton(Hindi.t("OK"), null).show();
    }

    private About() {
    }
}
