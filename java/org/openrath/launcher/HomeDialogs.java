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
                .setTitle("Mic over other apps")
                .setMessage("To keep the floating mic on top of maps and music, Android needs you to allow "
                        + "\"Display over other apps\" for OpenRath on the next page. You can turn the mic over other apps "
                        + "off in Settings > Features.")
                .setPositiveButton("Allow", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        OverMic.ask(a);
                    }
                }).setNegativeButton("Not now", null).show();
    }

    /** First start: pick the unit's own radio app, so the radio box and dock button just work. */
    static void findRadio(final HomeActivity a) {
        if (a.prefs.app("media:radio") != null) return;
        PackageManager pm = a.getPackageManager();
        String[] known = {"com.tw.radio", "com.tw.radioapp", "com.tw.fmradio", "com.ts.radio", "com.syu.radio",
                "com.qf.radio", "com.microntek.radio", "com.hct.radio", "com.android.fmradio",
                "com.mediatek.fmradio", "com.caf.fmradio", "com.spreadtrum.fmradio", "com.sprd.fmradio"};
        for (String k : known) {
            if (pm.getLaunchIntentForPackage(k) != null) {
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
        String when = k < 0 ? "is overdue by " + (-k) + " km" : "is due in " + k + " km";
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Book the service?")
                .setMessage(Care.NAMES[i] + " " + when + "." + (have ? " Call " + mine[0] + " to book a slot?"
                        : " Find your service centre on the map and tap Mine next to it, so next time it's one tap to call."))
                .setPositiveButton(have ? "Call" : "Find one", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (have) a.call(mine[1]);
                        else HomeVoice.run(a, new Voice.Command("service", ""));
                    }
                })
                .setNegativeButton("Later", null).show();
    }

    /** Once: ask for notification access, which is what lets the boxes see the track and play state. */
    static void askForTrackInfo(final HomeActivity a) {
        android.content.SharedPreferences sp = a.getSharedPreferences("once", android.content.Context.MODE_PRIVATE);
        if (a.hub.mediaAccess || sp.getBoolean("askedTrackInfo", false)) return;
        sp.edit().putBoolean("askedTrackInfo", true).apply();
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Show what's playing")
                .setMessage("To show the station, the track and the right play or pause button, OpenRath needs "
                        + "notification access. On the next screen, turn on OpenRath.")
                .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            a.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        } catch (Exception e) {
                            Toast.makeText(a, "This unit has no notification access screen", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Not now", null).show();
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
        items[0] = "      Choose a widget from another app…";
        for (int i = 0; i < Prefs.CONTENTS.length; i++) {
            items[i + 1] = (Prefs.CONTENTS[i].equals(cur) ? "✓  " : "      ") + "Show " + Prefs.CONTENT_NAMES[i].toLowerCase() + " here";
        }
        items[items.length - 1] = "      All settings…";
        items = java.util.Arrays.copyOf(items, items.length + 1);
        items[items.length - 1] = "      Move boxes (drag and drop)…";
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(slotName(a, slot))
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
                .setNegativeButton("Close", null)
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
        String side = left ? "left" : "right";
        if (slot == 0) return "Big box, " + side;
        if (slot == 1) return "Middle box, " + side;
        if (slot == 2) return "Bottom box, " + side;
        if (slot == 3) return "Big box, " + side;
        return "Bottom box, " + side;
    }

    /** Android 11+, once: the OpenRath folder (themes, backups, music, dashcam) needs all-files access. */
    static boolean askAllFiles(final HomeActivity a) {
        if (!a.prefs.str("askedAllFiles", "").isEmpty() || !Compat.needsAllFiles()) return false;
        a.prefs.put("askedAllFiles", "yes");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle("Allow the OpenRath folder")
                .setMessage("On this Android version OpenRath needs \"Allow access to manage all files\" to keep its backups, "
                        + "themes, dashcam clips and documents in the OpenRath folder, and to find music on USB sticks.")
                .setPositiveButton("Open the setting", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Compat.askAllFiles(a);
                    }
                }).setNegativeButton("Not now", null).show();
        return true;
    }

    /** Once: no voice engine on the unit, so offer Google's from the Play Store. */
    static boolean askVoice(final HomeActivity a) {
        if (!a.prefs.str("askedVoice", "").isEmpty() || !Speak.engines(a).isEmpty()) return false;
        a.prefs.put("askedVoice", "yes");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle("Get a spoken voice")
                .setMessage("This stereo has no text-to-speech voice, so OpenRath can't speak alerts, turns or the news. "
                        + "Get \"Speech Services by Google\" (free) from the Play Store? Later: Settings > Map and navigation > Spoken voice.")
                .setPositiveButton("Open the Play Store", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Speak.getVoice(a);
                    }
                }).setNegativeButton("Not now", null).show();
        return true;
    }

    private HomeDialogs() {
    }
}
