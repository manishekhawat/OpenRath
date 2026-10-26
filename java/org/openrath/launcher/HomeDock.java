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
        bar.setPadding(Ui.px(24), 0, Ui.px(18), Ui.px(2));   // the buttons get (nearly) the bar's 84: over HomeDock.TARGET
        android.widget.TextClock clock = new android.widget.TextClock(a);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Ui.SILVER);
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30 * Ui.k);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif", 0));
        bar.addView(clock, new LinearLayout.LayoutParams(0, -1, 1f));
        clock.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        if (Features.on(a.prefs, "dockStatus")) bar.addView(new StatusIcons(a, true), new LinearLayout.LayoutParams(Ui.px(170), -1));
        final java.util.List<String> apps = wideApps(a);
        for (int i = 0; i < 6; i++) {
            final int slot = i;
            final String pkg = i < apps.size() ? apps.get(i) : null;
            if (pkg == null && i > apps.size()) break;   // one "add" button after the apps
            ImageView v = new ImageView(a);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v.setPadding(Ui.px(8), Ui.px(8), Ui.px(8), Ui.px(8));
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
                        Hints.firstTap(a, a.prefs, "bar");   // once: holding it changes the app
                        if (ChildLock.blocked(a.prefs, pkg)) {
                            Toast.makeText(a, Hindi.t(ChildLock.SORRY), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent in = Compat.launch(a.getPackageManager(), pkg);
                        if (in != null) a.startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    Hints.used(a.prefs, "bar");
                    pickWideApp(a, slot);
                    return true;
                }
            });
            if (pkg != null) Hints.mark(v, Ui.px(6));   // a hold changes the app
            // each takes touches across a full target, the bar's whole height
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(TARGET), -1);
            lp.leftMargin = lp.rightMargin = Ui.px(2);
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
            bar.addView(ic, new LinearLayout.LayoutParams(Ui.px(TARGET), -1));
        }
        if (!Glass.on) return bar;
        // Glass: the bar floats over the wallpaper as a glass pill
        bar.setBackground(Glass.pane(32));
        bar.setPadding(Ui.px(20), 0, Ui.px(10), 0);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(a);
        wrap.setPadding(Ui.px(18), 0, Ui.px(18), Ui.px(4));
        wrap.addView(bar, new android.widget.FrameLayout.LayoutParams(-1, -1));
        return wrap;
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
            LinearLayout.LayoutParams p = across ? new LinearLayout.LayoutParams(0, Ui.px(TARGET), 1f)
                    : new LinearLayout.LayoutParams(Ui.px(TARGET), 0, 1f);
            v.setLayoutParams(p);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            gap(v, across);
            Hints.mark(v, Ui.px((TARGET - 62) / 2f));   // each dock button does something when held
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    Tap.feedback(x, a.prefs);
                    Hints.firstTap(a, a.prefs, Hints.dockTip(name, Features.on(a.prefs, "places")));   // once each: what holding it does
                    if ("apps".equals(name)) a.startActivity(new Intent(a, AppsActivity.class));
                    else if (("music".equals(name) || "radio".equals(name)) && a.openMusic(name)) return;
                    else a.openApp(dockKey(name), label);
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    Hints.used(a.prefs, Hints.dockTip(name, Features.on(a.prefs, "places")));
                    if ("apps".equals(name)) {
                        HomeDialogs.settings(a, -1);
                        return true;
                    }
                    if ("navi".equals(name) && Features.on(a.prefs, "places")) {
                        Places.show(a);
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
        if ("dock".equals(a.statusPlace())) {
            d.addView(new StatusIcons(a, across), across ? new LinearLayout.LayoutParams(0, Ui.px(62), 1.5f)
                    : new LinearLayout.LayoutParams(Ui.px(62), 0, 1.5f));
        }
        ImageView gear = new ImageView(a);
        gear.setImageBitmap(Ui.img(a, "dock_settings"));
        gear.setScaleType(ImageView.ScaleType.FIT_CENTER);
        gap(gear, across);
        gear.setLayoutParams(across ? new LinearLayout.LayoutParams(0, Ui.px(TARGET), 1f) : new LinearLayout.LayoutParams(Ui.px(TARGET), 0, 1f));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                a.openSettings();
            }
        });
        d.addView(gear);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(a);
        wrap.addView(d, new android.widget.FrameLayout.LayoutParams(-1, -1));
        String at = a.dash.dockAt;
        if (Glass.on) {
            // Glass: a floating glass bar, lined up with the boxes, instead of the edge and its rule
            d.setBackground(Glass.pane(28));
            int edge = Ui.px(10), in = Ui.px(4), along = Ui.px(18);
            if (across) wrap.setPadding(along, "top".equals(at) ? edge : in, along, "top".equals(at) ? in : edge);
            else wrap.setPadding("right".equals(at) ? in : edge, along, "right".equals(at) ? edge : in, along);
            if (across) d.setPadding(Ui.px(10), 0, Ui.px(10), 0);
            else d.setPadding(0, Ui.px(8), 0, Ui.px(8));
            return wrap;
        }
        // the thin rule sits on the edge facing the boxes
        int thin = Math.max(1, Ui.px(1.2f));
        android.widget.FrameLayout.LayoutParams lp = across
                ? new android.widget.FrameLayout.LayoutParams(-1, thin, "top".equals(at) ? Gravity.BOTTOM : Gravity.TOP)
                : new android.widget.FrameLayout.LayoutParams(thin, -1, "right".equals(at) ? Gravity.LEFT : Gravity.RIGHT);
        wrap.addView(line, lp);
        return wrap;
    }

    /**
     * A little room either side of a ring along the dock, so on a short screen (a stereo's own status
     * bar, the status icons) the rings shrink a touch and keep apart, the same gap between each,
     * instead of touching. Where there's room the ring is already narrower than its space, so it
     * doesn't change.
     */
    /**
     * Each dock button takes touches across 76 (the size Android's car guidelines ask for while
     * driving); its picture stays 62 wide, with the rest as padding.
     */
    static final int TARGET = 76;

    private static void gap(ImageView v, boolean across) {
        int g = Ui.px(5), side = Ui.px((TARGET - 62) / 2f);
        if (across) v.setPadding(g, side, g, side);
        else v.setPadding(side, g, side, g);
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
