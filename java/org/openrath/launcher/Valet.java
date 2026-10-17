package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Kids or valet mode: while someone else drives (or the children are in charge of the music), the
 * volume can't go above a set level, there's a voice warning above a set speed, and Settings and
 * the box menus need the owner's PIN.
 */
final class Valet {

    static boolean on(Prefs p) {
        return "on".equals(p.str("valet", "off"));
    }

    static String pin(Prefs p) {
        return p.str("valetPin", "");
    }

    /** Highest music volume allowed, as a share of the maximum (percent). */
    static int volumeCap(Prefs p) {
        return p.num("valetVol", 50);
    }

    /** Speed above which the voice warns, km/h. */
    static int speed(Prefs p) {
        return p.num("valetSpeed", 80);
    }

    /** Keep the music volume at or below the cap; called by the trip service on every GPS fix. */
    static void enforce(Context c, Prefs p) {
        if (!on(p)) return;
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cap = Math.max(1, Math.round(max * volumeCap(p) / 100f));
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0);
        } catch (Throwable ignored) {
        }
    }

    /** Run `ok` straight away when valet mode is off, else after the right PIN. */
    static void unlock(final Activity a, final Prefs p, final Runnable ok) {
        if (!on(p)) {
            ok.run();
            return;
        }
        askPin(a, Hindi.t("Valet mode is on. Enter the PIN"), new Pin() {
            @Override
            public void entered(String pin) {
                if (pin.equals(pin(p))) ok.run();
                else Toast.makeText(a, Hindi.t("Wrong PIN"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Settings row: switch valet mode on (choosing a PIN the first time) or off (with the PIN). */
    static void toggle(final Activity a, final Prefs p, final Runnable changed) {
        if (on(p)) {
            unlock(a, p, new Runnable() {
                @Override
                public void run() {
                    p.put("valet", "off");
                    Toast.makeText(a, Hindi.t("Valet mode is off"), Toast.LENGTH_SHORT).show();
                    changed.run();
                }
            });
            return;
        }
        if (pin(p).length() < 4) {
            choosePin(a, p, new Runnable() {
                @Override
                public void run() {
                    toggle(a, p, changed);
                }
            });
            return;
        }
        p.put("valet", "on");
        Toast.makeText(a, Hindi.f("Valet mode is on: volume up to %1$s%%, warning above %2$s km/h, Settings locked", volumeCap(p), speed(p)), Toast.LENGTH_LONG).show();
        changed.run();
    }

    static void choosePin(final Activity a, final Prefs p, final Runnable done) {
        askPin(a, Hindi.t("Choose a 4-digit PIN for valet mode"), new Pin() {
            @Override
            public void entered(String pin) {
                if (!pin.matches("\\d{4,8}")) {
                    Toast.makeText(a, Hindi.t("Use 4 to 8 digits"), Toast.LENGTH_SHORT).show();
                    return;
                }
                p.put("valetPin", pin);
                done.run();
            }
        });
    }

    interface Pin {
        void entered(String pin);
    }

    static void askPin(Activity a, String title, final Pin then) {
        final EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint("PIN");
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setView(box)
                .setPositiveButton(Hindi.t("OK"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        then.entered(e.getText().toString().trim());
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private Valet() {
    }
}
