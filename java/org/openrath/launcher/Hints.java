package org.openrath.launcher;

import android.app.AlertDialog;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The things a long press or a tap on a heading does, which nothing on screen shows: one tip a day
 * on the dashboard while parked, until each has been shown once, and all of them in Settings > Help.
 */
final class Hints {

    /** {tip, the feature it needs ("" for always)}. */
    static final String[][] ALL = {
            {"Hold any box to change what it shows, or to move it", ""},
            {"Hold a button in the bar to choose which app it opens", ""},
            {"Hold the Apps button to open Settings", ""},
            {"Hold the Navigation button for your saved places", "places"},
            {"Tap the music or radio box's name for sources, playlists and what's up next", ""},
            {"Hold an app in the app list to pin it to the top or hide it", "appDrawer"},
            {"Hold the next-turn card to hide it for this trip", "navTurns"},
            {"Say \"reply\" after a message is read out to answer it by voice", "voice"},
    };

    /** Which tip to show next, from the one after `next`, skipping tips for switched-off features; -1 when all are done. Pure, for the tests. */
    static int pick(int next, boolean[] on) {
        for (int i = Math.max(0, next); i < ALL.length; i++) if (on[i]) return i;
        return -1;
    }

    private static boolean[] on(Prefs p) {
        boolean[] on = new boolean[ALL.length];
        for (int i = 0; i < ALL.length; i++) on[i] = ALL[i][1].isEmpty() || Features.on(p, ALL[i][1]);
        return on;
    }

    /** One tip a day on the dashboard, while parked and nothing else is in front. */
    static void maybe(HomeActivity a) {
        Prefs p = a.prefs;
        if (ChildLock.moving() || Setup.showing() != null || a.settingsView != null) return;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (today.equals(p.str("hintDay", ""))) return;
        int i = pick(p.num("hintNext", 0), on(p));
        if (i < 0) return;
        p.put("hintDay", today);
        p.put("hintNext", String.valueOf(i + 1));
        Toast.makeText(a, Hindi.t("Tip") + ": " + Hindi.t(ALL[i][0]), Toast.LENGTH_LONG).show();
    }

    /** Settings > Help: every tip for the features that are on. */
    static void list(HomeActivity a) {
        boolean[] on = on(a.prefs);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < ALL.length; i++) if (on[i]) rows.add("•  " + Hindi.t(ALL[i][0]));
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Hidden shortcuts"))
                .setMessage(android.text.TextUtils.join("\n\n", rows))
                .setPositiveButton(Hindi.t("OK"), null).show();
    }

    private Hints() {
    }
}
