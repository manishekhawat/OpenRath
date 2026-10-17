package org.openrath.launcher;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * The dock along one edge of the dashboard, with the main apps, all apps and Settings, and the
 * Wide map's bar along the bottom: the clock, chosen apps, the mic, all apps and Settings.
 */
final class HomeDock {

    /** The bottom bar of the Wide map: the clock, your apps, the mic, all apps and settings. */
    static View wideBar(final HomeActivity a) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(24), Ui.px(4), Ui.px(18), Ui.px(8));
        android.widget.TextClock clock = new android.widget.TextClock(a);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Ui.SILVER);
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30 * Ui.k);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif", 0));
        bar.addView(clock, new LinearLayout.LayoutParams(0, -1, 1f));
        clock.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        final java.util.List<String> apps = wideApps(a);
        for (int i = 0; i < 6; i++) {
            final int slot = i;
            final String pkg = i < apps.size() ? apps.get(i) : null;
            if (pkg == null && i > apps.size()) break;   // one "add" button after the apps
            ImageView v = new ImageView(a);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v.setPadding(Ui.px(6), Ui.px(6), Ui.px(6), Ui.px(6));
            if (pkg != null) {
                try {
                    v.setImageDrawable(a.getPackageManager().getApplicationIcon(pkg));
                } catch (Exception e) {
                    continue;
                }
            } else {
                v.setImageBitmap(Ui.img(a, "dock_apps"));
                v.setAlpha(0.45f);
            }
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    if (pkg == null) pickWideApp(a, slot);
                    else {
                        if (ChildLock.blocked(a.prefs, pkg)) {
                            Toast.makeText(a, Hindi.t(ChildLock.SORRY), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent in = a.getPackageManager().getLaunchIntentForPackage(pkg);
                        if (in != null) a.startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    pickWideApp(a, slot);
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(72), Ui.px(72));
            lp.leftMargin = lp.rightMargin = Ui.px(6);
            bar.addView(v, lp);
        }
        View gap = new View(a);
        bar.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        String[] icons = {"mic", "apps", "settings"};
        for (final String n : icons) {
            Floaters.Icon ic = new Floaters.Icon(a, n, Ui.SILVER);
            ic.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    if ("mic".equals(n)) a.voiceSearch(a.mapCard());
                    else if ("apps".equals(n)) a.startActivity(new Intent(a, AppsActivity.class));
                    else a.openSettings();
                }
            });
            bar.addView(ic, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        }
        return bar;
    }

    /** The Wide map bar's apps: chosen ones, else the navigation, music, phone and radio apps. */
    private static java.util.List<String> wideApps(final HomeActivity a) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String saved = a.prefs.str("wideApps", "");
        if (!saved.isEmpty()) {
            for (String s : saved.split(",")) if (!s.isEmpty() && !out.contains(s)) out.add(s);
            return out;
        }
        for (String k : new String[]{"dock:navi", "media:music", "dock:phone", "media:radio"}) {
            String p = a.prefs.app(k);
            if (p != null && !out.contains(p)) out.add(p);
        }
        return out;
    }

    private static void pickWideApp(final HomeActivity a, final int slot) {
        AppPicker.show(a, Hindi.t("App for the bar"), new AppPicker.Picked() {
            @Override
            public void picked(String pkg) {
                java.util.List<String> l = wideApps(a);
                if (slot < l.size()) l.set(slot, pkg);
                else l.add(pkg);
                a.prefs.put("wideApps", android.text.TextUtils.join(",", l));
                a.build();
            }
        });
    }

    static View dock(final HomeActivity a) {
        final boolean across = a.dash.dockAcross();
        LinearLayout d = new LinearLayout(a);
        d.setOrientation(across ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        d.setGravity(across ? Gravity.CENTER_VERTICAL : Gravity.CENTER_HORIZONTAL);
        int pad = Ui.px(18);
        if (across) d.setPadding(pad, Ui.px(8), pad, Ui.px(8));
        else d.setPadding(0, pad, 0, pad);
        // thin silver rule down the right edge
        android.graphics.drawable.GradientDrawable rule = new android.graphics.drawable.GradientDrawable(
                across ? android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                        : android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.SILVER_DIM & 0x00FFFFFF, Ui.mix(Ui.SILVER_DIM, Ui.BACKGROUND, 0.7f), Ui.SILVER_DIM & 0x00FFFFFF});
        View line = new View(a);
        line.setBackground(rule);
        for (int i = 0; i < Prefs.DOCK.length; i++) {
            final String name = Prefs.DOCK[i];
            final String label = Prefs.DOCK_NAMES[i];
            ImageView v = new ImageView(a);
            v.setImageBitmap(Ui.img(a, "dock_" + name));
            LinearLayout.LayoutParams p = across ? new LinearLayout.LayoutParams(0, Ui.px(62), 1f)
                    : new LinearLayout.LayoutParams(Ui.px(62), 0, 1f);
            v.setLayoutParams(p);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    if ("apps".equals(name)) a.startActivity(new Intent(a, AppsActivity.class));
                    else a.openApp(dockKey(name), label);
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    if ("apps".equals(name)) {
                        HomeDialogs.settings(a, -1);
                        return true;
                    }
                    final String key = dockKey(name);
                    AppPicker.show(a, Hindi.f("App for %1$s", Hindi.t(label)), new AppPicker.Picked() {
                        @Override
                        public void picked(String pkg) {
                            a.prefs.setApp(key, pkg);
                        }
                    });
                    return true;
                }
            });
            d.addView(v);
        }
        ImageView gear = new ImageView(a);
        gear.setImageBitmap(Ui.img(a, "dock_settings"));
        gear.setScaleType(ImageView.ScaleType.FIT_CENTER);
        gear.setLayoutParams(across ? new LinearLayout.LayoutParams(0, Ui.px(62), 1f) : new LinearLayout.LayoutParams(Ui.px(62), 0, 1f));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                a.openSettings();
            }
        });
        d.addView(gear);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(a);
        wrap.addView(d, new android.widget.FrameLayout.LayoutParams(-1, -1));
        // the thin rule sits on the edge facing the boxes
        String at = a.dash.dockAt;
        int thin = Math.max(1, Ui.px(1.2f));
        android.widget.FrameLayout.LayoutParams lp = across
                ? new android.widget.FrameLayout.LayoutParams(-1, thin, "top".equals(at) ? Gravity.BOTTOM : Gravity.TOP)
                : new android.widget.FrameLayout.LayoutParams(thin, -1, "right".equals(at) ? Gravity.LEFT : Gravity.RIGHT);
        wrap.addView(line, lp);
        return wrap;
    }

    /** Music and radio share their app with the music and radio boxes. */
    private static String dockKey(String name) {
        if ("music".equals(name)) return "media:music";
        if ("radio".equals(name)) return "media:radio";
        return "dock:" + name;
    }

    private HomeDock() {
    }
}
