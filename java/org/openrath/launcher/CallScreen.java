package org.openrath.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The call screen over any app: when a call rings, a bar along the top shows who is calling (their
 * contact name when the unit has it) with big Answer and End buttons, whatever app is in front, and
 * stays as a slim bar with End during the call. Follows Android's phone state, which Bluetooth
 * hands-free calls report on most units. Needs "Display over other apps".
 */
final class CallScreen {

    private static View shown;
    private static TextView who, what, answer;

    static boolean wanted(Prefs p) {
        return Features.on(p, "callScreen");
    }

    /** Android's phone state changes, heard by the trip service (it runs all the time). */
    static final class Watch extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            try {
                Prefs p = new Prefs(c);
                if (!wanted(p)) return;
                String state = i.getStringExtra(TelephonyManager.EXTRA_STATE);
                String number = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                Context app = c.getApplicationContext();
                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) show(app, number, true);
                else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    if (shown != null) show(app, number, false);
                } else hide(app);
            } catch (Throwable e) {
                Diag.note("call screen", e);
            }
        }
    }

    /** The name to show: the contact, else an OpenRath favourite, else the number, else "Call". */
    static String caller(Context c, Prefs p, String number) {
        if (number == null || number.isEmpty()) return Hindi.t("Call");
        String n = Contacts.nameFor(c, number);
        if (n.isEmpty()) n = Calls.nameFor(p, number);
        return n.isEmpty() ? number : n;
    }

    static void show(final Context app, String number, boolean ringing) {
        if (!OverMic.allowed(app)) return;
        final Prefs p = new Prefs(app);
        if (shown == null) {
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            int sw = app.getResources().getDisplayMetrics().widthPixels;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(Math.min(sw - Ui.px(40), Ui.px(760)), -2,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = Ui.px(16);
            LinearLayout s = new LinearLayout(app);
            s.setGravity(Gravity.CENTER_VERTICAL);
            s.setPadding(Ui.px(22), Ui.px(12), Ui.px(12), Ui.px(12));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(Ui.RADIUS * Ui.k);
            g.setColor(Ui.solidPanel());
            g.setStroke(Math.max(1, Ui.px(2)), 0xFF2E9E62);
            s.setBackground(g);
            LinearLayout words = new LinearLayout(app);
            words.setOrientation(LinearLayout.VERTICAL);
            who = Ui.text(app, 30, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
            who.setEllipsize(android.text.TextUtils.TruncateAt.END);
            what = Ui.text(app, 17, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
            words.addView(who);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
            wp.topMargin = Ui.px(6);
            words.addView(what, wp);
            s.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
            answer = button(app, Hindi.t("Answer"), 0xFF1F6F45);
            TextView end = button(app, Hindi.t("End"), 0xFF7A2328);
            answer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    telecom(app, "acceptRingingCall");
                }
            });
            end.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    telecom(app, "endCall");
                    hide(app);
                }
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.px(150), Ui.px(HomeDock.TARGET));
            bp.leftMargin = Ui.px(12);
            s.addView(answer, bp);
            s.addView(end, new LinearLayout.LayoutParams(bp));
            try {
                wm.addView(s, lp);
                shown = s;
            } catch (Exception e) {
                return;   // the permission was taken back
            }
        }
        if (number != null && !number.isEmpty()) who.setText(caller(app, p, number));
        else if (who.getText().length() == 0) who.setText(Hindi.t("Call"));
        what.setText(ringing ? Hindi.t("Calling you") : Hindi.t("On a call"));
        answer.setVisibility(ringing ? View.VISIBLE : View.GONE);
        if (number != null && !number.isEmpty() && ringing) Calls.note(p, number, "", "in", System.currentTimeMillis());
    }

    private static TextView button(Context c, String text, int fill) {
        TextView t = Ui.text(c, 22, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        t.setText(text);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.RADIUS * Ui.k);
        g.setColor(fill);
        t.setBackground(g);
        return t;
    }

    /** A TelecomManager call by name (answering needs Android 8, ending Android 9; the unit may not allow it). */
    private static void telecom(Context c, String method) {
        try {
            Object t = c.getSystemService("telecom");
            if (t != null) t.getClass().getMethod(method).invoke(t);
        } catch (Throwable e) {
            Diag.note("call " + method, e);
        }
    }

    static void hide(Context app) {
        if (shown == null) return;
        try {
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(shown);
        } catch (Exception ignored) {
            // not shown any more
        }
        shown = null;
        who = what = answer = null;
    }

    private CallScreen() {
    }
}
