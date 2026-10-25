package org.openrath.launcher;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * OpenRath's own keypad, for a number that isn't in the contacts: big keys, the number as it is
 * typed (with the contact's name when it is one), a backspace and Call, which rings it through the
 * stereo's Bluetooth phone like the rest of the phone box. Typing a number is blocked while the car
 * is moving, as typing is elsewhere: it says to say "call" and a name instead, and an open keypad
 * closes when the car moves off.
 */
final class Keypad {

    /** The longest number the keypad takes. */
    static final int MAX = 18;

    /** The number after a key: digits, * and #, and + only at the start; at most MAX long. */
    static String press(String now, char key) {
        String n = now == null ? "" : now;
        if (n.length() >= MAX) return n;
        if (key == '+') return n.isEmpty() ? "+" : n;
        if ((key >= '0' && key <= '9') || key == '*' || key == '#') return n + key;
        return n;
    }

    /** The number with its last key taken off. */
    static String back(String now) {
        return now == null || now.isEmpty() ? "" : now.substring(0, now.length() - 1);
    }

    /**
     * The number as shown while typing: Indian mobiles as 98765 43210 (+91 98765 43210, 0 98765
     * 43210), other numbers with a + in threes after it, short numbers and codes with * or # as typed.
     */
    static String show(String n) {
        if (n == null || n.isEmpty()) return "";
        if (n.indexOf('*') >= 0 || n.indexOf('#') >= 0) return n;
        if (n.startsWith("+91")) return n.length() == 3 ? n : "+91 " + fives(n.substring(3));
        if (n.startsWith("+")) return "+" + threes(n.substring(1));
        if (n.startsWith("0") && n.length() > 1) return "0 " + fives(n.substring(1));
        return fives(n);
    }

    private static String fives(String d) {
        if (d.length() <= 5) return d;
        if (d.length() <= 10) return d.substring(0, 5) + " " + d.substring(5);
        return d.substring(0, 5) + " " + d.substring(5, 10) + " " + d.substring(10);
    }

    private static String threes(String d) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            if (i > 0 && i % 3 == 0) b.append(' ');
            b.append(d.charAt(i));
        }
        return b.toString();
    }

    private static final String KEYS = "123456789*0#";

    /** Open the keypad over the dashboard, or say why not (moving). */
    static void show(final HomeActivity a, final Unit unit) {
        if (ChildLock.moving()) {
            moving(a);
            return;
        }
        final Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        final String[] num = {""};
        int sw = a.getResources().getDisplayMetrics().widthPixels, sh = a.getResources().getDisplayMetrics().heightPixels;
        boolean tall = Ui.portrait(sw, sh);
        final int keyH = Ui.px(HomeDock.TARGET), gap = Ui.px(10);
        int keyW = Ui.px(tall ? 120 : 112);

        final TextView shown = Ui.text(a, 40, Ui.SILVER, "sans-serif-light", Gravity.CENTER);
        shown.setEllipsize(android.text.TextUtils.TruncateAt.START);
        final TextView name = Ui.text(a, 20, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        final Runnable redraw = new Runnable() {
            @Override
            public void run() {
                boolean none = num[0].isEmpty();
                shown.setText(none ? Hindi.t("Type a number") : show(num[0]));
                shown.setTextColor(none ? Ui.SILVER_DIM : Ui.SILVER);
                shown.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, (none ? 26 : show(num[0]).length() > 11 ? 30 : 40) * Ui.k);
                String who = num[0].length() >= 6 ? Contacts.nameFor(a, num[0]) : "";
                name.setText(who);
            }
        };
        redraw.run();

        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(a);
            for (int c = 0; c < 3; c++) {
                final char ch = KEYS.charAt(r * 3 + c);
                TextView k = key(a, String.valueOf(ch), 0, 34);
                k.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        num[0] = press(num[0], ch);
                        redraw.run();
                    }
                });
                if (ch == '0') k.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        num[0] = press(num[0], '+');   // hold 0 for +, as on a phone
                        redraw.run();
                        return true;
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(keyW, keyH);
                if (c > 0) lp.leftMargin = gap;
                row.addView(k, lp);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
            if (r > 0) rp.topMargin = gap;
            grid.addView(row, rp);
        }

        TextView del = key(a, "⌫", 0, 30);
        del.setContentDescription(Hindi.t("Delete"));
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                num[0] = back(num[0]);
                redraw.run();
            }
        });
        del.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                num[0] = "";
                redraw.run();
                return true;
            }
        });
        TextView call = key(a, Hindi.t("Call"), 0xFF1F6F45, 26);
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                if (num[0].isEmpty()) return;
                if (ChildLock.moving()) {
                    d.dismiss();
                    moving(a);
                    return;
                }
                if (unit == null) {
                    Toast.makeText(a, Hindi.f("Couldn't call: %1$s", Hindi.t("the stereo isn't ready")), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!unit.dialsItself(a)) {
                    // without the phone permission Android's dialer would open: ask for it instead
                    a.requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE}, 1);
                    Toast.makeText(a, Hindi.t("Allow phone calls, then tap Call again"), Toast.LENGTH_LONG).show();
                    return;
                }
                String n = num[0], who = Contacts.nameFor(a, n);
                String err = unit.call(a, n);
                Toast.makeText(a, err == null ? Hindi.f("Calling %1$s…", who.isEmpty() ? show(n) : who) : Hindi.f("Couldn't call: %1$s", err),
                        Toast.LENGTH_SHORT).show();
                if (err == null) d.dismiss();
            }
        });
        TextView close = key(a, Hindi.t("Close"), 0, 24);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        // the number and its buttons: beside the keys on a wide screen, above and below them on a tall one
        LinearLayout panel = new LinearLayout(a);
        int pad = Ui.px(20);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.RADIUS * Ui.k);
        g.setColor(Ui.solidPanel());
        g.setStroke(Math.max(1, Ui.px(1.2f)), Ui.EDGE);
        panel.setBackground(g);
        panel.setClickable(true);   // a tap on the panel doesn't close it
        int gridW = 3 * keyW + 2 * gap;
        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(a);
        top.addView(shown, new LinearLayout.LayoutParams(0, keyH, 1f));
        side.addView(top, new LinearLayout.LayoutParams(-1, keyH));
        side.addView(name, new LinearLayout.LayoutParams(-1, -2));
        if (tall) {
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.addView(side, new LinearLayout.LayoutParams(gridW, -2));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
            gp.topMargin = gap;
            panel.addView(grid, gp);
            LinearLayout bottom = new LinearLayout(a);
            bottom.addView(del, new LinearLayout.LayoutParams(keyW, keyH));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(keyW, keyH);
            cp.leftMargin = gap;
            bottom.addView(call, cp);
            bottom.addView(close, new LinearLayout.LayoutParams(cp));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
            bp.topMargin = gap;
            panel.addView(bottom, bp);
        } else {
            int sideW = Ui.px(360);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.px(96), keyH);
            dp.leftMargin = gap;
            top.addView(del, dp);
            side.addView(new View(a), new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, keyH);
            cp.topMargin = gap;
            side.addView(call, cp);
            side.addView(close, new LinearLayout.LayoutParams(cp));
            panel.addView(side, new LinearLayout.LayoutParams(sideW, 4 * keyH + 3 * gap));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-2, -2);
            gp.leftMargin = Ui.px(20);
            panel.addView(grid, gp);
        }
        FrameLayout dim = new FrameLayout(a);
        dim.setBackgroundColor(0xB0000000);
        dim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        dim.addView(panel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        d.setContentView(dim);

        // the car moves off: the keypad goes, and says what to do instead
        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable watch = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) return;
                if (ChildLock.moving()) {
                    d.dismiss();
                    moving(a);
                    return;
                }
                h.postDelayed(this, 1000);
            }
        };
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface di) {
                h.removeCallbacks(watch);
            }
        });
        d.show();
        h.postDelayed(watch, 1000);
    }

    /** While moving: no typing a number; a voice command instead (with a button for it when voice is on). */
    private static void moving(final HomeActivity a) {
        Glass.Builder b = new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert);
        b.setTitle(Hindi.t("Dialing a number is blocked while the car is moving"))
                .setMessage(Hindi.t("Say \"call\" and a name instead, or stop the car to type a number."))
                .setNegativeButton(Hindi.t("OK"), null);
        if (Features.on(a.prefs, "voice")) b.setPositiveButton(Hindi.t("Speak"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface di, int w) {
                a.voiceSearch(a.mapCard());
            }
        });
        b.show();
    }

    private static TextView key(android.content.Context c, String text, int fill, float size) {
        TextView t = Ui.text(c, size, fill != 0 ? 0xFFFFFFFF : Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
        t.setText(text);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * Ui.k);
        g.setColor(fill != 0 ? fill : Ui.PANEL_TOP);
        g.setStroke(Math.max(1, Ui.px(1.2f)), fill != 0 ? fill : Ui.EDGE);
        t.setBackground(g);
        t.setClickable(true);
        return t;
    }

    private Keypad() {
    }
}
