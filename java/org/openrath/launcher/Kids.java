package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Trip games for children: spot a car of the colour shown (two players keep score), spot number
 * plates from other states, and a big "how far to go" countdown to where the map is taking you,
 * Home or Office. Scores and the states found are kept for the day.
 */
final class Kids {

    static final String[][] COLOURS = {{"White", "#F4F4F4"}, {"Silver", "#B8BEC6"}, {"Black", "#1A1A1A"}, {"Red", "#D32F2F"},
            {"Blue", "#1E63C8"}, {"Grey", "#6B7078"}, {"Brown", "#7A4E2D"}, {"Yellow", "#F2C230"}, {"Green", "#2E8B57"},
            {"Orange", "#F07A20"}};

    /** Number-plate codes and the state or territory they come from. */
    static final String[][] STATES = {{"DL", "Delhi"}, {"HR", "Haryana"}, {"UP", "Uttar Pradesh"}, {"RJ", "Rajasthan"},
            {"PB", "Punjab"}, {"CH", "Chandigarh"}, {"HP", "Himachal"}, {"UK", "Uttarakhand"}, {"JK", "Jammu & Kashmir"},
            {"MH", "Maharashtra"}, {"GJ", "Gujarat"}, {"MP", "Madhya Pradesh"}, {"KA", "Karnataka"}, {"TN", "Tamil Nadu"},
            {"KL", "Kerala"}, {"AP", "Andhra Pradesh"}, {"TS", "Telangana"}, {"WB", "West Bengal"}, {"BR", "Bihar"},
            {"OD", "Odisha"}, {"GA", "Goa"}, {"AS", "Assam"}, {"JH", "Jharkhand"}, {"CG", "Chhattisgarh"}, {"BH", "Bharat series"}};

    /** Straight-line km from here to the target, rounded for children ("48", "4.5"). */
    static String km(double metres) {
        if (metres < 0) return "--";
        double k = metres / 1000;
        return k >= 10 ? String.valueOf(Math.round(k)) : String.format(Locale.US, "%.1f", k);
    }

    /** Today's key, so scores start fresh each day. */
    private static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
    }

    private static int score(Prefs p, int player) {
        String[] s = p.str("kidsScore", "").split("\\|");
        if (s.length < 3 || !s[0].equals(today())) return 0;
        try {
            return Integer.parseInt(s[player + 1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void setScore(Prefs p, int a, int b) {
        p.put("kidsScore", today() + "|" + a + "|" + b);
    }

    private static List<String> found(Prefs p) {
        List<String> out = new ArrayList<>();
        String[] s = p.str("kidsStates", "").split("\\|");
        if (s.length < 2 || !s[0].equals(today())) return out;
        for (String x : s[1].split(",")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    private static void setFound(Prefs p, List<String> f) {
        p.put("kidsStates", today() + "|" + android.text.TextUtils.join(",", f));
    }

    static String player(Prefs p, int i) {
        return p.str("kid" + (i + 1), Hindi.t(i == 0 ? "Left seat" : "Right seat"));
    }

    // ------------------------------------------------------------------ the screen

    static void show(final Activity a, final Prefs p) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF1B2330);
        LinearLayout tabs = new LinearLayout(a);
        tabs.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(8));
        final FrameLayout body = new FrameLayout(a);
        final String[] names = {"Spot the colour", "Number plates", "How far?", "Close"};
        final TextView[] tab = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            tab[i] = pill(a, Hindi.t(names[i]), 0xFF2C3A50, 18);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(54), i == 3 ? 0.6f : 1f);
            lp.rightMargin = Ui.px(10);
            tabs.addView(tab[i], lp);
            tab[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (k == 3) {
                        d.dismiss();
                        return;
                    }
                    for (int j = 0; j < 3; j++) tint(tab[j], j == k ? 0xFF3E7BD8 : 0xFF2C3A50);
                    body.removeAllViews();
                    body.addView(k == 0 ? colours(a, p) : k == 1 ? plates(a, p) : far(a, p, d));
                }
            });
        }
        page.addView(tabs);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        tint(tab[0], 0xFF3E7BD8);
        body.addView(colours(a, p));
        Edge.fit(page);
        d.setContentView(page);
        d.show();
    }

    private static TextView pill(Activity a, String s, int colour, float size) {
        TextView t = Ui.text(a, size, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        t.setText(s);
        tint(t, colour);
        t.setClickable(true);
        return t;
    }

    private static void tint(TextView t, int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(16));
        g.setColor(colour);
        t.setBackground(g);
    }

    private static View colours(final Activity a, final Prefs p) {
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(Ui.px(24), Ui.px(8), Ui.px(24), Ui.px(16));
        final Random rnd = new Random();
        final int[] cur = {rnd.nextInt(COLOURS.length)};
        final TextView card = Ui.text(a, 64, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        final TextView hint = Ui.text(a, 18, 0xFFC8D2E0, "sans-serif", Gravity.CENTER);
        hint.setText(Hindi.t("Who sees a car this colour first? Tap your name."));
        final Runnable paint = new Runnable() {
            @Override
            public void run() {
                String[] c = COLOURS[cur[0]];
                int col = 0xFF000000 | Integer.parseInt(c[1].substring(1), 16);
                card.setText(Hindi.f("%1$s car", Hindi.t(c[0])));
                boolean light = "White".equals(c[0]) || "Silver".equals(c[0]) || "Yellow".equals(c[0]);
                card.setTextColor(light ? 0xFF1B2330 : 0xFFFFFFFF);
                tint(card, col);
            }
        };
        paint.run();
        v.addView(card, new LinearLayout.LayoutParams(-1, 0, 1f));
        v.addView(hint, new LinearLayout.LayoutParams(-1, Ui.px(44)));
        LinearLayout row = new LinearLayout(a);
        final TextView[] btn = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int pl = i;
            btn[i] = pill(a, "", i == 0 ? 0xFF2E7D4F : 0xFF8A4FBF, 24);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(96), 1f);
            if (i == 0) lp.rightMargin = Ui.px(16);
            row.addView(btn[i], lp);
            btn[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    int s0 = score(p, 0) + (pl == 0 ? 1 : 0), s1 = score(p, 1) + (pl == 1 ? 1 : 0);
                    setScore(p, s0, s1);
                    int next;
                    do next = rnd.nextInt(COLOURS.length); while (next == cur[0]);
                    cur[0] = next;
                    paint.run();
                    label(p, btn);
                }
            });
            btn[i].setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    rename(a, p, pl, new Runnable() {
                        @Override
                        public void run() {
                            label(p, btn);
                        }
                    });
                    return true;
                }
            });
        }
        label(p, btn);
        v.addView(row);
        TextView foot = Ui.text(a, 13, 0xFF8FA0B6, "sans-serif", Gravity.CENTER);
        foot.setText(Hindi.t("Long-press a name to change it. Scores start again each day."));
        foot.setPadding(0, Ui.px(8), 0, 0);
        v.addView(foot);
        return v;
    }

    private static void label(Prefs p, TextView[] btn) {
        for (int i = 0; i < 2; i++) btn[i].setText(player(p, i) + "   " + score(p, i));
    }

    private static void rename(Activity a, final Prefs p, final int i, final Runnable done) {
        final EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setText(player(p, i));
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Player's name")).setView(e)
                .setPositiveButton(Hindi.t("OK"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = e.getText().toString().trim();
                        if (!n.isEmpty()) p.put("kid" + (i + 1), n);
                        done.run();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static View plates(final Activity a, final Prefs p) {
        ScrollView sv = new ScrollView(a);
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(Ui.px(24), Ui.px(4), Ui.px(24), Ui.px(16));
        final TextView head = Ui.text(a, 20, 0xFFFFFFFF, "sans-serif-medium", Gravity.LEFT);
        v.addView(head);
        GridLayout g = new GridLayout(a);
        g.setColumnCount(5);
        final List<String> got = found(p);
        head.setText(Hindi.f("Number plates from other states: %1$s of %2$s found today", got.size(), STATES.length));
        for (final String[] st : STATES) {
            final TextView t = pill(a, st[0] + "\n" + Hindi.t(st[1]), got.contains(st[0]) ? 0xFF2E7D4F : 0xFF2C3A50, 15);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    if (got.contains(st[0])) got.remove(st[0]);
                    else got.add(st[0]);
                    setFound(p, got);
                    tint(t, got.contains(st[0]) ? 0xFF2E7D4F : 0xFF2C3A50);
                    head.setText(Hindi.f("Number plates from other states: %1$s of %2$s found today", got.size(), STATES.length));
                }
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.px(176);
            lp.height = Ui.px(72);
            lp.setMargins(Ui.px(5), Ui.px(5), Ui.px(5), Ui.px(5));
            g.addView(t, lp);
        }
        v.addView(g);
        sv.addView(v);
        return sv;
    }

    /** {name, lat, lon} of the places to count down to: the map's destination, Home and Office. */
    static List<String[]> targets(Prefs p) {
        List<String[]> out = new ArrayList<>();
        String[] d = p.str("dest", "").split("\\|");
        if (d.length == 3) out.add(new String[]{p.str("destNick", "").isEmpty() ? (d[0].isEmpty() ? "Where we're going" : d[0]) : p.str("destNick", ""), d[1], d[2]});
        for (String n : new String[]{"Home", "Office"}) {
            String[] s = p.str("place:" + n, "").split(",");
            if (s.length == 2) out.add(new String[]{n, s[0], s[1]});
        }
        return out;
    }

    private static View far(final Activity a, final Prefs p, final Dialog dlg) {
        final LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        final List<String[]> ts = targets(p);
        if (ts.isEmpty()) {
            TextView t = Ui.text(a, 22, 0xFFFFFFFF, "sans-serif", Gravity.CENTER);
            t.setText(Hindi.t("Search for a place on the map first,\nor save Home or Office there."));
            v.addView(t);
            return v;
        }
        final int[] at = {Math.min(p.num("kidsTarget", 0), ts.size() - 1)};
        final TextView big = Ui.text(a, 150, 0xFFFFFFFF, "sans-serif-light", Gravity.CENTER);
        final TextView to = Ui.text(a, 30, 0xFFC8D2E0, "sans-serif", Gravity.CENTER);
        TextView note = Ui.text(a, 14, 0xFF8FA0B6, "sans-serif", Gravity.CENTER);
        note.setText(Hindi.t("Straight-line distance; the road is a little longer.  Tap the place to change it, long-press to give it a name."));
        v.addView(big);
        v.addView(to);
        v.addView(note);
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                String[] t = ts.get(at[0]);
                double m = Drive.fixAt == 0 ? -1 : Geo.dist(Drive.latNow, Drive.lonNow, Double.parseDouble(t[1]), Double.parseDouble(t[2]));
                big.setText(km(m));
                to.setText(Hindi.f("km to %1$s", Hindi.t(t[0])));
                v.removeCallbacks(this);
                if (dlg.isShowing() && v.isAttachedToWindow()) v.postDelayed(this, 2000);
            }
        };
        tick.run();
        to.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                at[0] = (at[0] + 1) % ts.size();
                p.put("kidsTarget", String.valueOf(at[0]));
                tick.run();
            }
        });
        to.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View x) {
                if (at[0] != 0 || p.str("dest", "").isEmpty()) return false;
                final EditText e = new EditText(a);
                e.setSingleLine(true);
                e.setHint(Hindi.t("e.g. Nani's house"));
                e.setText(p.str("destNick", ""));
                new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(Hindi.t("Call this place")).setView(e)
                        .setPositiveButton(Hindi.t("OK"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                p.put("destNick", e.getText().toString().trim());
                                ts.get(0)[0] = p.str("destNick", "").isEmpty() ? ts.get(0)[0] : p.str("destNick", "");
                                tick.run();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
                return true;
            }
        });
        v.post(tick);
        return v;
    }

    private Kids() {
    }
}
