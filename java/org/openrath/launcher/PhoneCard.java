package org.openrath.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Bluetooth phone through Topway's service: who is calling (from its Bluetooth callback),
 * answer and hang up, and up to three favourite numbers to call with one tap.
 */
final class PhoneCard extends Cards.Card {
    final TextView head = Ui.label(getContext(), Hindi.t("PHONE"));
    final TextView title = Ui.text(getContext(), 24, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
    final TextView sub = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
    final LinearLayout row = new LinearLayout(getContext());
    final TextView answer, end;
    final TextView[] favs = new TextView[3];
    /** The last calls, while a phone is connected; tap one to call back. */
    final LinearLayout calls = new LinearLayout(getContext());
    final TextView[] callRows = new TextView[5];
    java.util.List<String[]> recentCalls = new java.util.ArrayList<>();
    int callRoom = 5;
    String callsKey = "";
    long callsAt;
    Unit unit;

    PhoneCard(Context c) {
        super(c);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        answer = pill(c, Hindi.t("Answer"), 0xFF1F6F45);
        end = pill(c, Hindi.t("End"), 0xFF7A2328);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(answer);
        row.addView(end);
        for (int i = 0; i < 3; i++) {
            favs[i] = pill(c, "", 0);
            row.addView(favs[i]);
            final int n = i;
            clickable(favs[i], new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String[] f = fav(host.prefs(), n);
                    if (f == null) return;
                    String err = unit == null ? "the stereo isn't ready" : unit.call(getContext(), f[1]);
                    sub.setText(err == null ? Hindi.f("Calling %1$s…", f[0]) : Hindi.f("Couldn't call: %1$s", Hindi.t(err)));
                }
            });
        }
        addView(head);
        addView(title);
        addView(sub);
        addView(row);
        calls.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < callRows.length; i++) {
            callRows[i] = Ui.text(c, 13, Ui.SILVER, "sans-serif", Gravity.LEFT);
            callRows[i].setSingleLine(true);
            callRows[i].setEllipsize(android.text.TextUtils.TruncateAt.END);
            final int n = i;
            clickable(callRows[i], new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (n >= recentCalls.size()) return;
                    String[] f = recentCalls.get(n);
                    String err = unit == null ? "the stereo isn't ready" : unit.call(getContext(), f[0]);
                    android.widget.Toast.makeText(getContext(), err == null ? Hindi.f("Calling %1$s…", f[1].isEmpty() ? f[0] : f[1])
                            : Hindi.f("Couldn't call: %1$s", Hindi.t(err)), android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            calls.addView(callRows[i]);
        }
        calls.setVisibility(GONE);
        addView(calls);
        clickable(answer, new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (unit != null) unit.answer(getContext());
            }
        });
        clickable(end, new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (unit != null) unit.hangUp(getContext());
            }
        });
        OnClickListener back = new OnClickListener() {
            @Override
            public void onClick(View v) {
                // tap the last caller to ring them back; with no call yet, open the phone app
                if (last == null || unit == null || host.prefs() == null) {
                    host.openApp("dock:phone", "Phone");
                    return;
                }
                String err = unit.call(getContext(), last[0]);
                sub.setText(err == null ? Hindi.f("Calling %1$s…", last[1].isEmpty() ? last[0] : last[1]) : Hindi.f("Couldn't call: %1$s", Hindi.t(err)));
            }
        };
        clickable(title, back);
        clickable(sub, back);
    }

    String[] last;

    static String ago(long at) {
        long m = (System.currentTimeMillis() - at) / 60000;
        if (m < 1) return Hindi.t("just now");
        if (m < 60) return Hindi.f("%1$s min ago", m);
        if (m < 24 * 60) return Hindi.f("%1$s h ago", m / 60);
        return new java.text.SimpleDateFormat("d MMM", java.util.Locale.US).format(new java.util.Date(at));
    }

    static TextView pill(Context c, String text, int fill) {
        TextView t = Ui.text(c, 14, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
        t.setText(text);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(10 * Ui.k);
        g.setColor(fill != 0 ? fill : Ui.PANEL_TOP);
        g.setStroke(Math.max(1, Ui.px(1.2f)), fill != 0 ? fill : Ui.EDGE);
        t.setBackground(g);
        return t;
    }

    /** Favourite i as {name, number}, or null. Stored as "name|number". */
    static String[] fav(Prefs p, int i) {
        String v = p.str("fav" + (i + 1), "");
        int bar = v.indexOf('|');
        if (bar <= 0 || bar == v.length() - 1) return null;
        return new String[]{v.substring(0, bar), v.substring(bar + 1)};
    }

    @Override
    void fit(int w, int h) {
        float s = Math.min(h / (134f * Ui.k), w / (330f * Ui.k));
        int m = Ui.px(18 * s);
        head.setTextSize(0, Math.max(Ui.MIN_TEXT, 11 * s) * Ui.k);
        head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(12 * s), 0, 0));
        title.setTextSize(0, 24 * Ui.k * s);
        title.setMaxWidth(w - 2 * m);
        title.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(30 * s), m, 0));
        sub.setTextSize(0, 12 * Ui.k * s);
        sub.setMaxWidth(w - 2 * m);
        sub.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(60 * s), m, 0));
        int bh = Ui.px(34 * s);
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView t = (TextView) row.getChildAt(i);
            t.setTextSize(0, 13 * Ui.k * s);
            t.setPadding(Ui.px(12 * s), 0, Ui.px(12 * s), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, bh);
            lp.rightMargin = Ui.px(8 * s);
            t.setLayoutParams(lp);
        }
        row.setLayoutParams(at(-1, -2, Gravity.BOTTOM | Gravity.LEFT, m, 0, m, Ui.px(12 * s)));
        // the call list: from under the heading down to the favourites, as many rows as fit (up to 5)
        int top = Ui.px(32 * s), rowH = Ui.px(19 * s);
        int bottomRoom = Ui.px(12 * s) + bh + Ui.px(6 * s);
        callRoom = Math.max(1, Math.min(callRows.length, (h - top - bottomRoom) / rowH));
        for (TextView t : callRows) {
            t.setTextSize(0, 13 * Ui.k * s);
            t.setLayoutParams(new LinearLayout.LayoutParams(-1, rowH));
            t.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        }
        calls.setLayoutParams(at(-1, -2, Gravity.TOP | Gravity.LEFT, m, top, m, 0));
    }

    @Override
    public void changed(Hub h) {
        unit = h.unit;
        Prefs p = host.prefs();
        for (int i = 0; i < 3; i++) {
            String[] f = fav(p, i);
            favs[i].setText(f == null ? "" : f[0]);
            favs[i].setVisibility(f == null ? GONE : VISIBLE);
        }
        String down = unit.phoneDown();
        if (down != null) {
            head.setText(Hindi.t("PHONE"));
            title.setText(Hindi.t("Phone"));
            sub.setText(Hindi.t(down));
            return;
        }
        String said = unit.caller();
        head.setText(h.btConnected ? Hindi.t("PHONE  ·  CONNECTED") : Hindi.t("PHONE  ·  NOT CONNECTED"));
        boolean call = h.phoneStatus != 0;
        last = h.lastCall;
        if (call) {
            String who = last != null && System.currentTimeMillis() - Long.parseLong(last[3]) < 120000
                    ? (last[1].isEmpty() ? last[0] : last[1]) : !said.isEmpty() ? said : Hindi.t("Call");
            title.setText(who);
            sub.setText(Hindi.t("Call in progress or ringing"));
        } else if (last != null) {
            title.setText((last[1].isEmpty() ? last[0] : last[1]));
            sub.setText(Hindi.f("out".equals(last[2]) ? "Called %1$s" : "in".equals(last[2]) ? "Called you %1$s" : "Last call %1$s", ago(Long.parseLong(last[3])))
                    + (h.btConnected ? "  ·  " + Hindi.t("tap to call back") : ""));
        } else {
            title.setText(h.btConnected ? Hindi.t("Phone connected") : Hindi.t("No phone connected"));
            sub.setText(h.btConnected ? Hindi.t("Your last call shows here") : Hindi.t("Pair your phone in the Bluetooth app"));
        }
        answer.setVisibility(call ? VISIBLE : GONE);
        end.setVisibility(call ? VISIBLE : GONE);
        // connected and no call on: the last five calls instead of just the last one
        String key = p.str("callHistory", "").hashCode() + "/" + h.btConnected + call;
        if (!key.equals(callsKey) || System.currentTimeMillis() - callsAt > 15000) {
            callsKey = key;
            callsAt = System.currentTimeMillis();
            recentCalls = h.btConnected && !call ? Calls.recent(getContext(), p, callRows.length) : new java.util.ArrayList<String[]>();
        }
        boolean list = !recentCalls.isEmpty();
        calls.setVisibility(list ? VISIBLE : GONE);
        title.setVisibility(list ? GONE : VISIBLE);
        sub.setVisibility(list ? GONE : VISIBLE);
        for (int i = 0; i < callRows.length; i++) {
            boolean show = list && i < recentCalls.size() && i < callRoom;
            callRows[i].setVisibility(show ? VISIBLE : GONE);
            if (!show) continue;
            String[] f = recentCalls.get(i);
            String arrow = "out".equals(f[2]) ? "↗  " : "missed".equals(f[2]) ? "✕  " : "↙  ";
            callRows[i].setText(arrow + (f[1].isEmpty() ? f[0] : f[1]) + "   ·   " + ago(Long.parseLong(f[3])));
            callRows[i].setTextColor("missed".equals(f[2]) ? 0xFFE0796F : Ui.SILVER);
        }
    }
}
