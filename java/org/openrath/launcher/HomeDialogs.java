package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

/**
 * The home screen's questions and menus: the long-press menu on a box (what it shows, a widget,
 * all settings), the questions asked once (notification access, the mic over other apps,
 * all-files access, a spoken voice), finding the unit's radio app at first start, and the offer
 * to book a service that is due.
 */
final class HomeDialogs {

    /** Once: the floating mic can stay over other apps if Android allows it. */
    static void askOverlay(final HomeActivity a) {
        if (!HomeActivity.visible || !OverMic.wanted(a.prefs) || OverMic.allowed(a) || "yes".equals(a.prefs.str("overlayAsked", ""))) return;
        a.prefs.put("overlayAsked", "yes");
        new android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Mic over other apps"))
                .setMessage(Hindi.t("To keep the floating mic on top of maps and music, Android needs you to allow "
                        + "\"Display over other apps\" for OpenRath on the next page. You can turn the mic over other apps "
                        + "off in Settings > Features."))
                .setPositiveButton(Hindi.t("Allow"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        OverMic.ask(a);
                    }
                }).setNegativeButton(Hindi.t("Not now"), null).show();
    }

    /**
     * First start: says why each permission is wanted before Android asks, so owners don't refuse
     * prompts they don't understand. Shown once; later starts ask for what is still missing.
     */
    static void explainPermissions(final HomeActivity a, final String[] missing) {
        StringBuilder b = new StringBuilder(Hindi.t("Android will now ask for a few permissions. Here is what each one is for:"));
        boolean loc = false, files = false, phone = false, bt = false, notes = false;
        for (String m : missing) {
            if (m.contains("LOCATION")) loc = true;
            else if (m.contains("STORAGE") || m.contains("MEDIA")) files = true;
            else if (m.contains("BLUETOOTH")) bt = true;
            else if (m.contains("NOTIFICATIONS")) notes = true;
            else phone = true;
        }
        if (loc) b.append("\n\n• ").append(Hindi.t("Location: your speed, the map, speed-camera alerts and trip records. It stays on the unit."));
        if (files) b.append("\n\n• ").append(Hindi.t("Music and photos: plays music from a USB stick, and your own pictures for the car and backgrounds."));
        if (bt) b.append("\n\n• ").append(Hindi.t("Nearby devices: your phone's calls over Bluetooth, and OBD or tyre sensors if you have them."));
        if (phone) b.append("\n\n• ").append(Hindi.t("Phone and calls: shows who is calling, and lets you answer and call back from the home screen."));
        if (notes) b.append("\n\n• ").append(Hindi.t("Notifications: reminders such as a break after long driving."));
        b.append("\n\n").append(Hindi.t("You can refuse any of them; only that part stops working. Nothing is sent online."));
        final boolean[] asked = {false};
        final Runnable ask = new Runnable() {
            @Override
            public void run() {
                if (asked[0]) return;
                asked[0] = true;
                a.prefs.put("permsExplained", "yes");
                a.requestPermissions(missing, 1);
            }
        };
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Before you start"))
                .setMessage(b.toString())
                .setPositiveButton(Hindi.t("Continue"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        ask.run();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface d) {
                        ask.run();
                    }
                }).show();
    }

    /** First start: pick the unit's own radio app, so the radio box and dock button just work. */
    static void findRadio(final HomeActivity a) {
        if (a.prefs.app("media:radio") != null) return;
        // none found today: listing every app again on each start is slow on these units; try again tomorrow
        String today = Times.today();
        if (today.equals(a.prefs.str("radioNotFound", ""))) return;
        PackageManager pm = a.getPackageManager();
        String[] known = {"com.tw.radio", "com.tw.radioapp", "com.tw.fmradio", "com.ts.radio", "com.syu.radio",
                "com.qf.radio", "com.microntek.radio", "com.hct.radio", "com.android.fmradio",
                "com.mediatek.fmradio", "com.caf.fmradio", "com.spreadtrum.fmradio", "com.sprd.fmradio"};
        for (String k : known) {
            if (Compat.launch(pm, k) != null) {
                a.prefs.setApp("media:radio", k);
                return;
            }
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(main, 0)) {
            String label = String.valueOf(r.loadLabel(pm)).toLowerCase(java.util.Locale.US);
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(a.getPackageName())) continue;
            if (label.contains("radio") || label.equals("fm") || label.contains("收音") || Hub.looksLikeRadio(pkg)) {
                a.prefs.setApp("media:radio", pkg);
                return;
            }
        }
        a.prefs.put("radioNotFound", today);
    }

    /** When a service is near and the car is parked: offer to call the saved service centre (once a day). */
    static void serviceBooking(final HomeActivity a) {
        if (!Features.on(a.prefs, "serviceBooking") || !Features.on(a.prefs, "care") || Drive.kmhNow > 3) return;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (today.equals(a.prefs.str("bookAsked", ""))) return;
        int i = Care.bookingDue(a.prefs);
        if (i < 0) return;
        a.prefs.put("bookAsked", today);
        final String[] mine = a.prefs.str("myService", "").split("\\|", -1);
        final boolean have = mine.length == 2 && !mine[1].isEmpty();
        long k = Care.kmLeft(a.prefs, i);
        String what = Hindi.t(Care.NAMES[i]);
        String when = k < 0 ? Hindi.f("%1$s is overdue by %2$s km.", what, String.valueOf(-k)) : Hindi.f("%1$s is due in %2$s km.", what, String.valueOf(k));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Book the service?"))
                .setMessage(when + (have ? Hindi.f(" Call %1$s to book a slot?", mine[0])
                        : Hindi.t(" Find your service centre on the map and tap Mine next to it, so next time it's one tap to call.")))
                .setPositiveButton(have ? Hindi.t("Call") : Hindi.t("Find one"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (have) a.call(mine[1]);
                        else HomeVoice.run(a, new Voice.Command("service", ""));
                    }
                })
                .setNegativeButton(Hindi.t("Later"), null).show();
    }

    /** Once: ask for notification access, which is what lets the boxes see the track and play state. */
    static void askForTrackInfo(final HomeActivity a) {
        android.content.SharedPreferences sp = a.getSharedPreferences("once", android.content.Context.MODE_PRIVATE);
        if (a.hub.mediaAccess || sp.getBoolean("askedTrackInfo", false)) return;
        sp.edit().putBoolean("askedTrackInfo", true).apply();
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Show what's playing"))
                .setMessage(Hindi.t("To show the station, the track and the right play or pause button, OpenRath needs "
                        + "notification access. On the next screen, turn on OpenRath."))
                .setPositiveButton(Hindi.t("Open"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            a.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        } catch (Exception e) {
                            Toast.makeText(a, Hindi.t("This unit has no notification access screen"), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(Hindi.t("Not now"), null).show();
    }

    /** Long-press on a box: what it shows, and the way to all settings. */
    static void settings(final HomeActivity a, final int slot) {
        if (Valet.on(a.prefs) && !a.ownerUnlocked) {
            Valet.unlock(a, a.prefs, new Runnable() {
                @Override
                public void run() {
                    a.ownerUnlocked = true;
                    settings(a, slot);
                }
            });
            return;
        }
        if (slot < 0) {
            a.openSettings();
            return;
        }
        String cur = a.prefs.slot(slot);
        String[] items = new String[Prefs.CONTENTS.length + 2];
        items[0] = "      " + Hindi.t("Choose a widget from another app…");
        for (int i = 0; i < Prefs.CONTENTS.length; i++) {
            items[i + 1] = (Prefs.CONTENTS[i].equals(cur) ? "✓  " : "      ") + Hindi.f("Show %1$s here", Hindi.t(Prefs.CONTENT_NAMES[i]).toLowerCase());
        }
        items[items.length - 1] = "      " + Hindi.t("All settings…");
        items = java.util.Arrays.copyOf(items, items.length + 1);
        items[items.length - 1] = "      " + Hindi.t("Move boxes (drag and drop)…");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t(slotName(a, slot)))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == Prefs.CONTENTS.length + 2) a.startMoving();
                        else if (which == Prefs.CONTENTS.length + 1) a.openSettings();
                        else if (which == 0) widgetHere(a, slot);
                        else {
                            String c = Prefs.CONTENTS[which - 1];
                            a.prefs.setSlot(slot, c);
                            a.build();
                            // a widget box with nothing in it yet: go straight to the list of widgets
                            if (c.startsWith("widget") && a.prefs.num("widgetId:" + c, -1) < 0) a.pickWidget(c);
                        }
                    }
                })
                .setNegativeButton(Hindi.t("Close"), null)
                .show();
    }

    /** Put a widget box here (reusing it if this box already is one) and open the list of widgets. */
    private static void widgetHere(final HomeActivity a, int slot) {
        if (!Features.on(a.prefs, "widgets")) Features.set(a.prefs, "widgets", true);
        String cur = a.prefs.slot(slot);
        String c = cur.startsWith("widget") ? cur : Prefs.WIDGET1;
        if (!cur.startsWith("widget")) {
            // use a widget content no other box on this page shows, so nothing else moves
            boolean oneUsed = false;
            for (int i = 0; i < Prefs.SLOTS; i++) if (Prefs.WIDGET1.equals(a.prefs.slot(i))) oneUsed = true;
            c = oneUsed ? Prefs.WIDGET2 : Prefs.WIDGET1;
            a.prefs.setSlot(slot, c);
            a.build();
        }
        a.pickWidget(c);
    }

    static String slotName(final HomeActivity a, int slot) {
        boolean left = (slot < 3) != a.prefs.swapped();
        // whole names, so Settings and the box menu can look each one up in the translations
        if (slot == 0 || slot == 3) return left ? "Big box, left" : "Big box, right";
        if (slot == 1) return left ? "Middle box, left" : "Middle box, right";
        return left ? "Bottom box, left" : "Bottom box, right";
    }

    /** Android 11+, once: the OpenRath folder (themes, backups, music, dashcam) needs all-files access. */
    static boolean askAllFiles(final HomeActivity a) {
        if (!a.prefs.str("askedAllFiles", "").isEmpty() || !Compat.needsAllFiles()) return false;
        a.prefs.put("askedAllFiles", "yes");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Allow the OpenRath folder"))
                .setMessage(Hindi.t("On this Android version OpenRath needs \"Allow access to manage all files\" to keep its backups, "
                        + "themes, dashcam clips and documents in the OpenRath folder, and to find music on USB sticks."))
                .setPositiveButton(Hindi.t("Open the setting"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Compat.askAllFiles(a);
                    }
                }).setNegativeButton(Hindi.t("Not now"), null).show();
        return true;
    }

    /** Once: no voice engine on the unit, so offer Google's from the Play Store. */
    static boolean askVoice(final HomeActivity a) {
        if (!a.prefs.str("askedVoice", "").isEmpty() || !Speak.engines(a).isEmpty()) return false;
        a.prefs.put("askedVoice", "yes");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Get a spoken voice"))
                .setMessage(Hindi.t("This stereo has no text-to-speech voice, so OpenRath can't speak alerts, turns or the news. "
                        + "Get \"Speech Services by Google\" (free) from the Play Store? Later: Settings > Map and navigation > Spoken voice."))
                .setPositiveButton(Hindi.t("Open the Play Store"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Speak.getVoice(a);
                    }
                }).setNegativeButton(Hindi.t("Not now"), null).show();
        return true;
    }

    private HomeDialogs() {
    }
}
