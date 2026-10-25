package org.openrath.launcher;

import android.app.AlertDialog;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The things a long press or a tap on a heading does, which nothing else on screen shows: a small
 * mark in the corner of each control that has a long press, its tip the first time that control is
 * tapped, one tip a day on the dashboard while parked until each has been shown once, and all of
 * them in Settings > Help.
 */
final class Hints {

    /** {tip, the feature it needs ("" for always), its id (kept in the settings once the tip has been seen)}. */
    static final String[][] ALL = {
            {"Hold any box to change what it shows, or to move it", "", "box"},
            {"Hold a button in the bar to choose which app it opens", "", "bar"},
            {"Hold the Apps button to open Settings", "", "apps"},
            {"Hold the Navigation button for your saved places", "places", "navi"},
            {"Tap the music or radio box's name for sources, playlists and what's up next", "", "media"},
            {"Hold an app in the app list to pin it to the top or hide it", "appDrawer", "drawer"},
            {"Hold the next-turn card to hide it for this trip", "navTurns", "turn"},
            {"Say \"reply\" after a message is read out to answer it by voice", "voice", "reply"},
    };

    /** The tip for a tap on this dock button: what holding it does. Pure, for the tests. */
    static String dockTip(String button, boolean places) {
        if ("apps".equals(button)) return "apps";
        if ("navi".equals(button) && places) return "navi";
        return "bar";
    }

    /** Whether tip `id` is in the list of tips seen ("," around each id). Pure, for the tests. */
    static boolean seen(String list, String id) {
        return list != null && list.contains("," + id + ",");
    }

    /** The list of tips seen, with `id` added. Pure, for the tests. */
    static String markSeen(String list, String id) {
        if (seen(list, id)) return list;
        return (list == null || list.isEmpty() ? "," : list) + id + ",";
    }

    private static int index(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i][2].equals(id)) return i;
        return -1;
    }

    /**
     * The first tap on a control that has a long press: its tip, once (remembered), so the long press is found
     * without reading Help.
     */
    static void firstTap(android.content.Context c, Prefs p, String id) {
        int i = index(id);
        if (i < 0 || seen(p.str("hintsSeen", ""), id)) return;
        used(p, id);
        Toast.makeText(c, Hindi.t("Tip") + ": " + Hindi.t(ALL[i][0]), Toast.LENGTH_LONG).show();
    }

    /** The tip was shown, or the owner held the control: no need to tell them about it again. */
    static void used(Prefs p, String id) {
        String list = p.str("hintsSeen", "");
        if (!seen(list, id)) p.put("hintsSeen", markSeen(list, id));
    }

    /**
     * The small mark at the top-right corner of a control that does something when held: a dot in a soft
     * shade of the text colour, there to notice without drawing the eye. It sits at the corner of the
     * square picture centred in the control (`inset` is the control's padding), just outside a round icon.
     */
    static final class Mark extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int inset;

        Mark(int inset) {
            this.inset = inset;
            paint.setColor(Ui.SILVER_DIM);
            paint.setAlpha(140);
        }

        @Override
        public void draw(android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            float[] at = spot(b.width(), b.height(), inset, Math.max(2, Ui.px(3)));
            cv.drawCircle(b.left + at[0], b.top + at[1], at[2], paint);
        }

        @Override
        public void setAlpha(int a) {
            paint.setAlpha(a);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter f) {
            paint.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Where the mark goes in a w by h control: {x, y, radius}, at the top-right corner of the centred square
     * picture inside the padding. Pure, for the tests.
     */
    static float[] spot(int w, int h, int inset, float r) {
        float half = Math.max(0, Math.min(w, h) - 2 * inset) / 2f;
        float cx = w / 2f, cy = h / 2f, d = half * 0.82f;
        return new float[]{Math.min(w - r, cx + d), Math.max(r, cy - d), r};
    }

    /** Put the long-press mark on a control; `inset` is the padding around its picture. */
    static void mark(android.view.View v, int inset) {
        v.setForeground(new Mark(inset));
    }

    /** Which tip to show next, from the one after `next`, skipping tips for switched-off features; -1 when all are done. Pure, for the tests. */
    static int pick(int next, boolean[] on) {
        for (int i = Math.max(0, next); i < ALL.length; i++) if (on[i]) return i;
        return -1;
    }

    /** Tips for features that are on; with `unseen`, only those not already shown on a first tap. */
    private static boolean[] on(Prefs p, boolean unseen) {
        boolean[] on = new boolean[ALL.length];
        String seen = p.str("hintsSeen", "");
        for (int i = 0; i < ALL.length; i++) on[i] = (ALL[i][1].isEmpty() || Features.on(p, ALL[i][1])) && !(unseen && seen(seen, ALL[i][2]));
        return on;
    }

    /** One tip a day on the dashboard, while parked and nothing else is in front. */
    static void maybe(HomeActivity a) {
        Prefs p = a.prefs;
        if (ChildLock.moving() || Setup.showing() != null || a.settingsView != null) return;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (today.equals(p.str("hintDay", ""))) return;
        int i = pick(p.num("hintNext", 0), on(p, true));
        if (i < 0) return;
        p.put("hintDay", today);
        p.put("hintNext", String.valueOf(i + 1));
        used(p, ALL[i][2]);
        Toast.makeText(a, Hindi.t("Tip") + ": " + Hindi.t(ALL[i][0]), Toast.LENGTH_LONG).show();
    }

    /** Settings > Help: every tip for the features that are on. */
    static void list(HomeActivity a) {
        boolean[] on = on(a.prefs, false);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < ALL.length; i++) if (on[i]) rows.add("•  " + Hindi.t(ALL[i][0]));
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Hidden shortcuts"))
                .setMessage(android.text.TextUtils.join("\n\n", rows))
                .setPositiveButton(Hindi.t("OK"), null).show();
    }

    private Hints() {
    }
}
