package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Driver profiles: each driver keeps their own boxes, theme, volume, sound, alert speed, voice
 * language and message settings. Switching saves the current driver's settings and loads the
 * other's, by hand or when the driver's phone connects (PhoneLink). The fuel log, drives, car setup and service reminders are shared by everyone.
 */
final class Profiles {

    private static final Set<String> KEYS = new HashSet<>(Arrays.asList("theme", "themePack", "swapped", "alerts",
            "warnSpeed", "voiceLang", "eqPreset", "eqBass", "eqCustom", "speedVolPer", "tickerShow", "readAloudMode",
            "fav", "saverMin", "pages", "dim", "dimLevel", "welcome", "quietVol", "feat:speedVolume", "feat:eq",
            "feat:readAloud", "feat:nightDrive", "feat:ticker", "feat:voice", "feat:pages", "feat:saver", "feat:quiet",
            "layout", "layWall", "layAnim", "layRoad", "layUser", "layApps", "layMirror"));

    /** Whether a setting belongs to the driver rather than the car. */
    static boolean personal(String key) {
        return KEYS.contains(key) || key.matches("(p\\d)?slot\\d") || key.startsWith("src:") || key.startsWith("lay:");
    }

    static String current(Prefs p) {
        return p.str("profile", "");
    }

    static JSONObject all(Prefs p) {
        try {
            return new JSONObject(p.str("profiles", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static List<String> names(Prefs p) {
        List<String> out = new ArrayList<>();
        Iterator<String> it = all(p).keys();
        while (it.hasNext()) out.add(it.next());
        java.util.Collections.sort(out);
        return out;
    }

    /** Keep the current driver's settings under their name. */
    static void saveCurrent(Context c, Prefs p) {
        String name = current(p);
        if (name.isEmpty()) return;
        try {
            JSONObject snap = new JSONObject();
            for (Map.Entry<String, ?> e : p.all().entrySet()) if (personal(e.getKey())) snap.put(e.getKey(), e.getValue());
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            snap.put("~vol", am.getStreamVolume(AudioManager.STREAM_MUSIC));
            JSONObject a = all(p);
            a.put(name, snap);
            p.put("profiles", a.toString());
        } catch (Exception e) {
            Diag.note("profile save", e);
        }
    }

    /** Save the current driver and load `name`'s settings; a new driver starts from the current ones. */
    static void switchTo(Activity a, Prefs p, String name) {
        saveCurrent(a, p);
        JSONObject snap = all(p).optJSONObject(name);
        if (snap != null) {
            List<String> drop = new ArrayList<>();
            for (String k : p.all().keySet()) if (personal(k) && !snap.has(k)) drop.add(k);
            Map<String, Object> put = new java.util.HashMap<>();
            Iterator<String> it = snap.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (personal(k)) put.put(k, snap.opt(k));
            }
            p.replace(drop, put);
            int vol = snap.optInt("~vol", -1);
            if (vol >= 0) {
                try {
                    ((AudioManager) a.getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                } catch (Throwable e) {
                    Diag.note("profile volume", e);
                }
            }
        }
        p.put("profile", name);
        saveCurrent(a, p);
        Toast.makeText(a, Hindi.f("Hello %1$s", name), Toast.LENGTH_SHORT).show();
        a.recreate();
    }

    /** The next driver in the list (steering-wheel button). */
    static void next(Activity a, Prefs p) {
        List<String> n = names(p);
        if (n.size() < 2) {
            Toast.makeText(a, Hindi.t("Add drivers in Settings > Family first"), Toast.LENGTH_SHORT).show();
            return;
        }
        int i = n.indexOf(current(p));
        switchTo(a, p, n.get((i + 1) % n.size()));
    }

    static void menu(final Activity a, final Prefs p) {
        final List<String> n = names(p);
        List<String> rows = new ArrayList<>();
        for (String s : n) rows.add((s.equals(current(p)) ? "✓  " : "     ") + s);
        rows.add(Hindi.t("Add a driver…"));
        if (!n.isEmpty()) rows.add(Hindi.t("Remove a driver…"));
        final boolean phones = !current(p).isEmpty() && a instanceof HomeActivity;
        if (phones) rows.add(Hindi.f("%1$s's phone…", current(p)));
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Who's driving?"))
                .setItems(rows.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w < n.size()) {
                            if (!n.get(w).equals(current(p))) switchTo(a, p, n.get(w));
                        } else if (w == n.size()) add(a, p);
                        else if (w == n.size() + 1 && !n.isEmpty()) remove(a, p);
                        else if (phones) PhoneLink.choosePhone((HomeActivity) a);
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    private static void add(final Activity a, final Prefs p) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setHint(Hindi.t("Name"));
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Add a driver"))
                .setMessage(names(p).isEmpty() ? Hindi.t("Your current settings become this driver's. Add a second driver to switch between them.") : null)
                .setView(box).setPositiveButton(Hindi.t("Add"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String name = e.getText().toString().trim();
                        if (name.isEmpty() || names(p).contains(name)) return;
                        if (current(p).isEmpty()) {
                            p.put("profile", name);
                            saveCurrent(a, p);
                            Toast.makeText(a, Hindi.f("%1$s added", name), Toast.LENGTH_SHORT).show();
                        } else switchTo(a, p, name);
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static void remove(final Activity a, final Prefs p) {
        final List<String> n = names(p);
        n.remove(current(p));
        if (n.isEmpty()) {
            Toast.makeText(a, Hindi.f("Switch to another driver to remove %1$s", current(p)), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Remove a driver"))
                .setItems(n.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        JSONObject all = all(p);
                        all.remove(n.get(w));
                        p.put("profiles", all.toString());
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private Profiles() {
    }
}
