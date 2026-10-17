package org.openrath.launcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The cab driver's one-tap SOS: two emergency contacts, and five seconds to cancel before it
 * texts them where the car is, or, without a SIM in the stereo, calls the first one through
 * the phone.
 */
final class CabSos {

    static String[] contact(Prefs p, int i) {
        String v = p.str("cabSos" + i, "");
        int bar = v.indexOf('|');
        if (bar < 0 || bar == v.length() - 1) return null;
        return new String[]{v.substring(0, bar), v.substring(bar + 1)};
    }

    static String sosText(Prefs p, double lat, double lon, long now) {
        String who = p.welcome().isEmpty() ? "your driver" : p.welcome();
        String car = p.str("cabVehicle", "");
        return "SOS from " + who + (car.isEmpty() ? "" : " (" + car + ")") + ": I need help. "
                + (lat == 0 && lon == 0 ? "Location not known yet." : "I am here: " + Geo.mapsLink(lat, lon))
                + " at " + new SimpleDateFormat("HH:mm, d MMM", Locale.US).format(new Date(now)) + ".";
    }

    static void editContacts(final HomeActivity a, final Runnable done) {
        final Prefs p = a.prefs;
        LinearLayout box = Cab.box(a);
        final EditText[] f = new EditText[4];
        for (int i = 0; i < 2; i++) {
            String[] c = contact(p, i + 1);
            f[i * 2] = new EditText(a);
            f[i * 2].setHint(Hindi.f("Contact %1$s: name", i + 1));
            f[i * 2].setText(c == null ? "" : c[0]);
            box.addView(f[i * 2]);
            f[i * 2 + 1] = new EditText(a);
            f[i * 2 + 1].setHint(Hindi.f("Contact %1$s: phone number", i + 1));
            f[i * 2 + 1].setInputType(InputType.TYPE_CLASS_PHONE);
            f[i * 2 + 1].setText(c == null ? "" : c[1]);
            box.addView(f[i * 2 + 1]);
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("SOS contacts"))
                .setMessage(Hindi.t("SOS texts them where the car is. Without a SIM in the stereo it calls the first one through your phone instead."))
                .setView(box)
                .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        for (int i = 0; i < 2; i++) {
                            String n = f[i * 2 + 1].getText().toString().replaceAll("[^0-9+]", "");
                            String name = f[i * 2].getText().toString().trim().replace('|', ' ');
                            p.put("cabSos" + (i + 1), n.isEmpty() ? "" : (name.isEmpty() ? n : name) + "|" + n);
                        }
                        if (!canText(a))
                            a.requestPermissions(new String[]{android.Manifest.permission.SEND_SMS}, 6);
                        if (done != null) done.run();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    static boolean canText(Context c) {
        return c.checkCallingOrSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean simReady(Context c) {
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            return tm != null && tm.getSimState() == android.telephony.TelephonyManager.SIM_STATE_READY;
        } catch (Exception e) {
            return false;
        }
    }

    /** Five seconds to cancel (a mistaken tap), then text both contacts, or call the first. */
    static void sos(final HomeActivity a) {
        final Prefs p = a.prefs;
        final String[] c1 = contact(p, 1), c2 = contact(p, 2);
        if (c1 == null && c2 == null) {
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(Hindi.t("No SOS contacts yet"))
                    .setMessage(Hindi.t("Add two people to text in an emergency."))
                    .setPositiveButton(Hindi.t("Add them"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            editContacts(a, null);
                        }
                    })
                    .setNeutralButton(Hindi.t("Call 112"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            a.call("112");
                        }
                    }).setNegativeButton(Hindi.t("Close"), null).show();
            return;
        }
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xF2120808);
        final TextView info = Ui.text(a, 26, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        info.setPadding(Ui.px(40), 0, Ui.px(40), Ui.px(10));
        col.addView(info);
        final TextView count = Ui.text(a, 96, 0xFFFF6A5A, "sans-serif-light", Gravity.CENTER);
        col.addView(count);
        final LinearLayout extra = new LinearLayout(a);
        extra.setGravity(Gravity.CENTER);
        col.addView(extra);
        final TextView cancel = CabMeter.button(a, Hindi.t("Cancel"), 0xFF33363C);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.px(260), Ui.px(76));
        bp.topMargin = Ui.px(20);
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(cancel, bp);
        d.setContentView(col);
        final String who = c1 != null && c2 != null ? Hindi.f("%1$s and %2$s", c1[0], c2[0]) : c1 != null ? c1[0] : c2 != null ? c2[0] : "";
        info.setText(Hindi.f("Sending SOS with your location to %1$s", who));
        final int[] left = {5};
        count.setText("5");
        final Runnable send = new Runnable() {
            @Override
            public void run() {
                count.setText("");
                cancel.setText(Hindi.t("Close"));
                double lat = Drive.latNow, lon = Drive.lonNow;
                final String msg = sosText(p, lat, lon, System.currentTimeMillis());
                List<String> to = new ArrayList<>();
                if (c1 != null) to.add(c1[1]);
                if (c2 != null) to.add(c2[1]);
                boolean sent = false;
                if (canText(a) && simReady(a)) {
                    try {
                        android.telephony.SmsManager sm = android.telephony.SmsManager.getDefault();
                        for (String n : to) sm.sendMultipartTextMessage(n, null, sm.divideMessage(msg), null, null);
                        sent = true;
                    } catch (Exception ignored) {
                    }
                }
                if (sent) {
                    info.setText(Hindi.f("SOS sent to %1$s.", who) + "\n\n" + msg);
                    return;
                }
                // no SIM in the stereo: call through the paired phone, and a code to send the text from it
                info.setText(Hindi.f("The stereo can't send texts (no SIM), so it's calling %1$s through your phone.\nScan the code with your phone to send the SOS text.",
                        c1 != null ? c1[0] : c2[0]));
                a.call(c1 != null ? c1[1] : c2[1]);
                ImageView qr = Qr.view(a, Hindi.t("SOS text"), "SMSTO:" + to.get(0) + ":" + msg, Hindi.t("Scan to send the SOS text from your phone."), Ui.px(220));
                extra.addView(qr, new LinearLayout.LayoutParams(Ui.px(220), Ui.px(220)));
            }
        };
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) return;
                left[0]--;
                if (left[0] <= 0) send.run();
                else {
                    count.setText(String.valueOf(left[0]));
                    count.postDelayed(this, 1000);
                }
            }
        };
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                count.removeCallbacks(tick);
                d.dismiss();
            }
        });
        d.show();
        count.postDelayed(tick, 1000);
    }

    private CabSos() {
    }
}
