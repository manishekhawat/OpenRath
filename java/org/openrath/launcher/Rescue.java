package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Accident help: after a very hard stop (from 40 km/h or more to nearly nothing within about a
 * second and a half), the screen asks "Are you OK?" with a 30-second countdown. Unless someone
 * taps "I'm OK", it phones the emergency contact through the paired phone and shows where the car
 * is, in words and as a QR code, so it can be read out or scanned by whoever helps.
 */
final class Rescue {

    static final int SECONDS = 30;

    /** A crash-like stop: at least 40 km/h before, under 8 now, at more than 25 km/h lost per second. */
    static boolean crash(float before, float now, long ms) {
        if (ms <= 0 || ms > 2000 || before < 40 || now > 8) return false;
        return (before - now) / (ms / 1000f) > 25;
    }

    static String contact(Prefs p) {
        return p.str("sosNumber", "");
    }

    static String contactName(Prefs p) {
        String n = p.str("sosName", "");
        return n.isEmpty() ? contact(p) : n;
    }

    /** Settings: who to call. */
    static void edit(final HomeActivity a, final Prefs p, final Runnable done) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.px(20), Ui.px(8), Ui.px(20), 0);
        final EditText name = new EditText(a), num = new EditText(a);
        name.setHint("Name (e.g. Papa)");
        name.setText(p.str("sosName", ""));
        num.setHint("Phone number");
        num.setInputType(InputType.TYPE_CLASS_PHONE);
        num.setText(contact(p));
        box.addView(name);
        box.addView(num);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Emergency contact")
                .setMessage("Called after a crash if nobody taps \"I'm OK\" within 30 seconds. The phone must be paired by Bluetooth.")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        p.put("sosName", name.getText().toString().trim());
                        p.put("sosNumber", num.getText().toString().replaceAll("[^0-9+]", ""));
                        done.run();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    /** "28.45950, 77.02660" and a Maps link for the QR code. */
    static String where(double lat, double lon) {
        return String.format(Locale.US, "%.5f, %.5f", lat, lon);
    }

    // ------------------------------------------------------------------ the screen

    private static View shown;

    static boolean showing() {
        return shown != null;
    }

    /** Cover the home screen with the countdown; a test run (from Settings) never calls. */
    static void show(final HomeActivity a, final Prefs p, final double lat, final double lon, final boolean test) {
        if (shown != null) return;
        final FrameLayout cover = new FrameLayout(a);
        cover.setBackgroundColor(0xF2120808);
        cover.setClickable(true);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        cover.addView(col, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        TextView title = Ui.text(a, 34, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        title.setText(test ? "Are you OK?  (test)" : "Are you OK?");
        col.addView(title);
        final TextView info = Ui.text(a, 18, 0xFFE8C8C8, "sans-serif", Gravity.CENTER);
        final String who = contactName(p);
        info.setText(contact(p).isEmpty() ? "A very hard stop was detected. Set an emergency contact in Settings > Driving to be called."
                : "A very hard stop was detected. Calling " + who + " in " + SECONDS + " seconds.");
        info.setPadding(Ui.px(40), Ui.px(10), Ui.px(40), Ui.px(18));
        col.addView(info);
        final TextView count = Ui.text(a, 96, 0xFFFF6A5A, "sans-serif-light", Gravity.CENTER);
        count.setText(String.valueOf(SECONDS));
        col.addView(count);

        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        final TextView ok = button(a, "I'm OK", 0xFF2E7D4F);
        final TextView now = button(a, contact(p).isEmpty() ? "Call 112" : "Call now", 0xFFB3261E);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.px(240), Ui.px(76));
        bp.leftMargin = bp.rightMargin = Ui.px(14);
        row.addView(ok, bp);
        row.addView(now, bp);
        col.addView(row);

        LinearLayout place = new LinearLayout(a);
        place.setGravity(Gravity.CENTER_VERTICAL);
        place.setPadding(0, Ui.px(22), 0, 0);
        if (lat != 0 || lon != 0) {
            ImageView qr = Qr.view(a, "Where the car is", Geo.mapsLink(lat, lon), "Scan to open the spot in Google Maps.", Ui.px(170));
            place.addView(qr, new LinearLayout.LayoutParams(Ui.px(170), Ui.px(170)));
            TextView where = Ui.text(a, 18, 0xFFFFFFFF, "sans-serif", Gravity.LEFT);
            where.setText("The car is at\n" + where(lat, lon) + "\nScan for the spot in Google Maps");
            where.setPadding(Ui.px(18), 0, 0, 0);
            place.addView(where);
        }
        col.addView(place);

        a.root.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        shown = cover;
        final int[] left = {SECONDS};
        final Runnable[] tick = new Runnable[1];
        final Runnable close = new Runnable() {
            @Override
            public void run() {
                cover.removeCallbacks(tick[0]);
                a.root.removeView(cover);
                shown = null;
            }
        };
        final Runnable dial = new Runnable() {
            @Override
            public void run() {
                cover.removeCallbacks(tick[0]);
                count.setText("");
                String n = contact(p).isEmpty() ? "112" : contact(p);
                info.setText((test ? "Test: would call " : "Calling ") + (contact(p).isEmpty() ? "112" : who)
                        + ". Read out the place below. Tap \"I'm OK\" to close.");
                if (!test) a.call(n);
                now.setVisibility(View.GONE);
            }
        };
        tick[0] = new Runnable() {
            @Override
            public void run() {
                left[0]--;
                count.setText(String.valueOf(left[0]));
                if (left[0] <= 0) {
                    if (contact(p).isEmpty()) {
                        count.setText("");
                        info.setText("No emergency contact is set. Tap \"Call 112\" if you need help.");
                        return;
                    }
                    dial.run();
                } else cover.postDelayed(this, 1000);
            }
        };
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                close.run();
            }
        });
        now.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dial.run();
            }
        });
        a.speak(test ? "Accident help test. Are you OK?" : "Are you OK? Tap I'm OK, or I will call " + (contact(p).isEmpty() ? "no one, no contact is set" : who) + " in thirty seconds.");
        cover.postDelayed(tick[0], 1000);
    }

    private static TextView button(HomeActivity a, String s, int colour) {
        TextView b = Ui.text(a, 22, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        b.setText(s);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(16));
        g.setColor(colour);
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }

    private Rescue() {
    }
}
