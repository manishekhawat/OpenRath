package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The setup screens on the first start, after the logo: language, permissions (and a backup to bring
 * back), the car, the look, how much OpenRath shows, the apps, then the phone and the home app.
 * Every step can be skipped and everything changed later in Settings; Back goes a step back. The
 * step is remembered, so turning the screen or leaving for Android's settings comes back to it.
 */
final class Setup extends FrameLayout {

    static final int WELCOME = 0, ACCESS = 1, CAR = 2, LOOK = 3, LEVEL = 4, APPS = 5, FINISH = 6;
    static final String[] STEPS = {"Welcome", "Permissions", "Your car", "Look", "How much", "Apps", "Phone and home"};

    /** On screen now; held weakly so it never keeps an old dashboard alive. */
    private static WeakReference<Setup> showing = new WeakReference<>(null);

    /** The setup on screen now, or null. */
    static Setup showing() {
        return showing.get();
    }

    /** The dashboard is going: forget its setup screen. */
    static void forget() {
        showing = new WeakReference<>(null);
    }

    /** Whether to show it: switched on, not finished, and either under way or a fresh install. */
    static boolean needed(boolean on, boolean done, boolean started, boolean carReady, boolean levelPicked) {
        if (!on || done) return false;
        return started || (!carReady && !levelPicked);
    }

    /** The step after `step`; How much is left out when a fleet sets the level. */
    static int next(int step, boolean levelLocked) {
        int n = step + 1;
        if (n == LEVEL && levelLocked) n++;
        return Math.min(n, FINISH);
    }

    static int back(int step, boolean levelLocked) {
        int n = step - 1;
        if (n == LEVEL && levelLocked) n--;
        return Math.max(n, WELCOME);
    }

    /** What a permission is for, as the group shown on the permissions step. */
    static String kind(String perm) {
        if (perm.contains("LOCATION")) return "location";
        if (perm.contains("STORAGE") || perm.contains("MEDIA")) return "files";
        if (perm.contains("BLUETOOTH")) return "bluetooth";
        if (perm.contains("NOTIFICATIONS")) return "notifications";
        return "phone";
    }

    static final String[][] KINDS = {
            {"location", "Location", "Your speed, the map, speed-camera alerts and trip records. It stays on the unit."},
            {"files", "Music and photos", "Music from a USB stick, and your own pictures for the car and backgrounds."},
            {"bluetooth", "Nearby devices", "Your phone's calls over Bluetooth, and OBD or tyre sensors if you have them."},
            {"phone", "Phone and calls", "Shows who is calling, and lets you answer and call back from the home screen."},
            {"notifications", "Notifications", "Reminders such as a break after long driving."},
    };

    static void show(HomeActivity a) {
        if (showing.get() != null) return;
        Setup s = new Setup(a);
        a.root.addView(s, new LayoutParams(-1, -1));
        showing = new WeakReference<>(s);
        s.render();
    }

    private final HomeActivity a;
    private final Prefs p;
    private int step;
    private int level = Features.SIMPLE;
    private boolean asked;
    private EditText tank, name;

    private Setup(HomeActivity a) {
        super(a);
        this.a = a;
        this.p = a.prefs;
        step = Math.max(WELCOME, Math.min(FINISH, p.num("setupStep", WELCOME)));
        if (Features.levelPicked(p)) level = Features.level(p);
        setBackgroundColor(Ui.BACKGROUND);
        setClickable(true);   // nothing reaches the dashboard underneath
        setElevation(Ui.px(20));   // above the floating mic, which has its own elevation
        p.put("setupStep", String.valueOf(step));
    }

    // ------------------------------------------------------------------ frame

    /** Builds the whole screen again for the current step, language and permissions. */
    void render() {
        save();
        removeAllViews();
        boolean portrait = Ui.portrait(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        LinearLayout frame = new LinearLayout(a);
        frame.setOrientation(portrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        addView(frame, new LayoutParams(-1, -1));
        if (portrait) frame.addView(progress(), new LinearLayout.LayoutParams(-1, -2));
        else frame.addView(rail(), new LinearLayout.LayoutParams(Ui.px(250), -1));

        LinearLayout main = new LinearLayout(a);
        main.setOrientation(LinearLayout.VERTICAL);
        frame.addView(main, portrait ? new LinearLayout.LayoutParams(-1, 0, 1) : new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.px(portrait ? 22 : 36);
        body.setPadding(pad, Ui.px(28), pad, Ui.px(20));
        sv.addView(body, new ScrollView.LayoutParams(-1, -2));
        main.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        main.addView(bar(), new LinearLayout.LayoutParams(-1, -2));

        switch (step) {
            case WELCOME: welcome(body); break;
            case ACCESS: access(body); break;
            case CAR: car(body); break;
            case LOOK: look(body); break;
            case LEVEL: level(body); break;
            case APPS: apps(body); break;
            default: finish(body);
        }
    }

    /** The step list down the left: done ones ticked, this one lit. */
    private View rail() {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(Ui.PANEL);
        r.setPadding(Ui.px(24), Ui.px(28), Ui.px(16), Ui.px(20));
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = wheel(30);
        if (logo != null) head.addView(logo);
        TextView t = text(19, Ui.SILVER, "sans-serif-light");
        t.setText("OpenRath");
        t.setPadding(Ui.px(10), 0, 0, 0);
        head.addView(t);
        r.addView(head);
        space(r, 26);
        boolean locked = Managed.levelLocked(p);
        int n = 1;
        for (int i = 0; i < STEPS.length; i++) {
            if (i == LEVEL && locked) continue;
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.px(9), 0, Ui.px(9));
            boolean done = i < step, now = i == step;
            TextView dot = text(13, now ? 0xFF000000 : done ? Ui.ACCENT : Ui.SILVER_DIM, "sans-serif-medium");
            dot.setGravity(Gravity.CENTER);
            dot.setText(done ? "✓" : String.valueOf(n));
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            if (now) g.setColor(Ui.ACCENT);
            else g.setStroke(Math.max(1, Ui.px(1.5f)), done ? Ui.ACCENT : Ui.EDGE);
            dot.setBackground(g);
            row.addView(dot, new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
            TextView name = text(16, now ? Ui.SILVER : done ? Ui.SILVER_DIM : Ui.mix(Ui.SILVER_DIM, Ui.PANEL, 0.6f), now ? "sans-serif-medium" : "sans-serif");
            name.setText(Hindi.t(STEPS[i]));
            name.setPadding(Ui.px(12), 0, 0, 0);
            row.addView(name);
            if (done) {
                final int to = i;
                row.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        go(to);
                    }
                });
            }
            r.addView(row);
            n++;
        }
        return r;
    }

    /** Portrait: "Step 3 of 7" and a bar across the top. */
    private View progress() {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(6));
        boolean locked = Managed.levelLocked(p);
        int total = STEPS.length - (locked ? 1 : 0), at = step + 1 - (locked && step > LEVEL ? 1 : 0);
        TextView t = text(13, Ui.ACCENT, "sans-serif-medium");
        t.setText(Hindi.f("Step %1$s of %2$s", at, total) + "  ·  " + Hindi.t(STEPS[step]));
        r.addView(t);
        space(r, 8);
        LinearLayout track = new LinearLayout(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.PANEL_TOP);
        bg.setCornerRadius(Ui.px(3));
        track.setBackground(bg);
        View fill = new View(a);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(Ui.ACCENT);
        fg.setCornerRadius(Ui.px(3));
        fill.setBackground(fg);
        track.addView(fill, new LinearLayout.LayoutParams(0, Ui.px(5), at));
        track.addView(new View(a), new LinearLayout.LayoutParams(0, Ui.px(5), Math.max(0, total - at)));
        r.addView(track, new LinearLayout.LayoutParams(-1, Ui.px(5)));
        return r;
    }

    /** Back, Skip setup, and the big Next button. */
    private View bar() {
        LinearLayout b = new LinearLayout(a);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.px(28), Ui.px(12), Ui.px(28), Ui.px(18));
        if (step > WELCOME) {
            TextView back = button(Hindi.t("Back"), false);
            back.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    back();
                }
            });
            b.addView(back);
        }
        b.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
        if (step < FINISH) {
            TextView skip = text(15, Ui.SILVER_DIM, "sans-serif");
            skip.setText(Hindi.t("Skip setup"));
            skip.setPadding(Ui.px(16), Ui.px(14), Ui.px(20), Ui.px(14));
            skip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    skipAll();
                }
            });
            b.addView(skip);
        }
        TextView next = button(nextLabel(), true);
        next.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                forward();
            }
        });
        b.addView(next);
        return b;
    }

    private String nextLabel() {
        if (step == WELCOME) return Hindi.t("Let's start");
        if (step == ACCESS && Compat.missing(a).length > 0 && !asked) return Hindi.t("Allow and continue");
        if (step == FINISH) return Hindi.t("Start driving");
        return Hindi.t("Next");
    }

    // ------------------------------------------------------------------ moving between steps

    private void go(int to) {
        leave();
        step = to;
        p.put("setupStep", String.valueOf(step));
        render();
    }

    /** Straight to a step (the device tests and screenshots). */
    void step(int to) {
        go(Math.max(WELCOME, Math.min(FINISH, to)));
    }

    /** Finish as if Start driving was tapped. */
    void close() {
        done();
    }

    void back() {
        if (step == WELCOME) return;
        go(back(step, Managed.levelLocked(p)));
    }

    private void forward() {
        if (step == ACCESS && !asked) {
            String[] missing = Compat.missing(a);
            asked = true;
            p.put("permsExplained", "yes");
            if (missing.length > 0) a.requestPermissions(missing, 1);
        }
        if (step == FINISH) {
            done();
            return;
        }
        go(next(step, Managed.levelLocked(p)));
    }

    /** What a step keeps when it is left: the typed tank size and name, the level. */
    private void leave() {
        save();
        if (step == LEVEL && !Managed.levelLocked(p)) {
            Features.setLevel(p, level);
            Extras.featuresChanged(a);
        }
        if (step == CAR) {
            p.put("carSetup", "yes");
            Car.apply(p);
            Ui.clearImages();
            a.restyle();
        }
    }

    private void save() {
        if (tank != null && tank.getParent() != null) {
            try {
                int l = Math.round(Float.parseFloat(tank.getText().toString().trim()));
                if (l > 0) p.put("tankL", String.valueOf(Math.max(5, Math.min(120, l))));
            } catch (NumberFormatException ignored) {
                // not a number: nothing to save
            }
        }
        if (name != null && name.getParent() != null) p.setWelcome(name.getText().toString().trim());
        tank = name = null;
    }

    private void skipAll() {
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Skip setup?"))
                .setMessage(Hindi.t("OpenRath starts with its usual settings. You can set everything up later in Settings, or run this setup again from Settings, Help."))
                .setPositiveButton(Hindi.t("Skip"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        save();
                        if (!Features.levelPicked(p)) p.put("level", "custom");
                        done();
                    }
                })
                .setNegativeButton(Hindi.t("Keep going"), null).show();
    }

    /** Finished or skipped: the start-up questions it covered aren't asked again. */
    private void done() {
        leave();
        p.put("setupDone", "yes");
        p.put("setupStep", "");
        p.put("carSetup", "yes");
        p.put("permsExplained", "yes");
        p.put("restoreAsked", "yes");
        p.put("askedVoice", "yes");
        p.put("askedAllFiles", "yes");
        a.getSharedPreferences("once", Context.MODE_PRIVATE).edit().putBoolean("askedTrackInfo", true).apply();
        forget();
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.removeView(this);
        a.build();
        Toast.makeText(a, Hindi.t("All set. Long-press any box, or open Settings, to change things."), Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ the steps

    private void welcome(LinearLayout c) {
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = wheel(84);
        if (logo != null) c.addView(logo);
        space(c, 18);
        TextView t = title(Hindi.t("Welcome to OpenRath"));
        t.setGravity(Gravity.CENTER);
        c.addView(t);
        space(c, 8);
        TextView b = body(Hindi.t("Let's get your stereo ready in a few quick steps. You can change all of it later in Settings."));
        b.setGravity(Gravity.CENTER);
        c.addView(b, new LinearLayout.LayoutParams(-1, -2));
        space(c, 26);
        c.addView(label(Hindi.t("Language")));
        space(c, 10);
        LinearLayout row = new LinearLayout(a);
        String now = Hindi.lang(p);
        row.addView(choice("English", "Menus and voice in English", !"hi".equals(now), new Runnable() {
            @Override
            public void run() {
                language("en");
            }
        }), new LinearLayout.LayoutParams(Ui.px(240), -2));
        View gap = new View(a);
        row.addView(gap, new LinearLayout.LayoutParams(Ui.px(14), 1));
        row.addView(choice("हिन्दी", "मेन्यू और आवाज़ हिन्दी में", "hi".equals(now), new Runnable() {
            @Override
            public void run() {
                language("hi");
            }
        }), new LinearLayout.LayoutParams(Ui.px(240), -2));
        c.addView(row);
    }

    private void language(String l) {
        p.put("lang", l);
        Hindi.load(a, p);
        a.build();
        render();
    }

    private void access(LinearLayout c) {
        c.addView(title(Hindi.t("Allow what OpenRath needs")));
        space(c, 8);
        c.addView(body(Hindi.t("Android asks about each one. Refuse any you like: only that part stops working. Nothing is sent online.")));
        space(c, 20);
        List<String> wanted = new ArrayList<>(), missing = new ArrayList<>();
        for (String s : Compat.startPermissions(android.os.Build.VERSION.SDK_INT)) if (!wanted.contains(kind(s))) wanted.add(kind(s));
        for (String s : Compat.missing(a)) if (!missing.contains(kind(s))) missing.add(kind(s));
        for (String[] k : KINDS) {
            if (!wanted.contains(k[0])) continue;
            boolean ok = !missing.contains(k[0]);
            c.addView(row(Hindi.t(k[1]), Hindi.t(k[2]), ok ? "✓ " + Hindi.t("Allowed") : Hindi.t(asked ? "Not allowed" : "Not yet"), ok, null));
            space(c, 10);
        }
        if (asked && !missing.isEmpty()) {
            TextView again = button(Hindi.t("Ask again"), false);
            again.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    a.requestPermissions(Compat.missing(a), 1);
                }
            });
            c.addView(again, new LinearLayout.LayoutParams(-2, -2));
            space(c, 10);
        }
        // the special ones Android keeps on its own pages
        space(c, 10);
        c.addView(label(Hindi.t("Also useful")));
        space(c, 10);
        boolean media = a.hub != null && a.hub.mediaAccess;
        c.addView(row(Hindi.t("Show what's playing"), Hindi.t("The station, the song and the right play or pause button, from notification access."),
                media ? "✓ " + Hindi.t("Allowed") : Hindi.t("Open"), media, media ? null : new Runnable() {
                    @Override
                    public void run() {
                        try {
                            a.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        } catch (Exception e) {
                            Toast.makeText(a, Hindi.t("This unit has no notification access screen"), Toast.LENGTH_LONG).show();
                        }
                    }
                }));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            space(c, 10);
            boolean files = !Compat.needsAllFiles();
            c.addView(row(Hindi.t("The OpenRath folder"), Hindi.t("Backups, themes, dashcam clips and music on USB sticks."),
                    files ? "✓ " + Hindi.t("Allowed") : Hindi.t("Open"), files, files ? null : new Runnable() {
                        @Override
                        public void run() {
                            Compat.askAllFiles(a);
                        }
                    }));
        }
        backup(c);
    }

    /** A backup on the unit (OpenRath's or Dzire Home's): bring it back instead of setting up. */
    private void backup(LinearLayout c) {
        if (!p.str("restoreAsked", "").isEmpty()) return;
        List<File> found;
        try {
            Storage.migrate();
            found = Backup.found();
        } catch (Throwable t) {
            return;
        }
        if (found.isEmpty()) return;
        final File f = found.get(0);
        String when = new java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new java.util.Date(f.lastModified()));
        space(c, 20);
        c.addView(label(Hindi.t("Used OpenRath before?")));
        space(c, 10);
        c.addView(row(Hindi.f("A backup from %1$s", when),
                Hindi.t("Bring back those settings, the fuel log and trips. OpenRath restarts with them, and the rest of setup isn't needed.")
                        + "\n" + Backup.label(f),
                Hindi.t("Bring it back"), false, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Backup.restore(a, f);
                            p.put("setupDone", "yes");
                            p.put("restoreAsked", "yes");
                            Toast.makeText(a, Hindi.t("Restored; restarting"), Toast.LENGTH_SHORT).show();
                            postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    android.os.Process.killProcess(android.os.Process.myPid());
                                }
                            }, 1200);
                        } catch (Exception e) {
                            Toast.makeText(a, Hindi.f("Couldn't restore: %1$s", String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
                        }
                    }
                }));
    }

    private void car(LinearLayout c) {
        c.addView(title(Hindi.t("Your car")));
        space(c, 8);
        c.addView(body(Hindi.t("It picks the car picture, the name beside the logo, fuel words and service reminders.")));
        space(c, 20);
        String what = (Car.brandName(p) + " " + Car.model(p)).trim();
        c.addView(label(Hindi.t("Brand and model")));
        space(c, 10);
        c.addView(row(what.isEmpty() ? Hindi.t("Choose your car") : what, null, Hindi.t(what.isEmpty() ? "Choose" : "Change"), !what.isEmpty(), new Runnable() {
            @Override
            public void run() {
                pickBrand();
            }
        }));
        space(c, 20);
        c.addView(label(Hindi.t("Shape")));
        space(c, 10);
        Flow shapes = new Flow(a);
        String shape = p.str("carShape", "");
        for (int i = 0; i < Car3D.SHAPES.length; i++) {
            final int w = i;
            shapes.addView(chip(Hindi.t(Car3D.SHAPE_NAMES[i]), Car3D.SHAPES[i].equals(shape), new Runnable() {
                @Override
                public void run() {
                    p.put("carShape", Car3D.SHAPES[w]);
                    p.put("carBody", w >= 2 ? "suv" : "car");
                    render();
                }
            }));
        }
        c.addView(shapes);
        space(c, 20);
        c.addView(label(Hindi.t("Runs on")));
        space(c, 10);
        Flow fuels = new Flow(a);
        final String fuel = Car.fuel(p);
        for (int i = 0; i < Car.FUELS.length; i++) {
            final int w = i;
            fuels.addView(chip(Hindi.t(Car.FUEL_NAMES[i]), Car.FUELS[i].equals(fuel), new Runnable() {
                @Override
                public void run() {
                    p.put("fuel", Car.FUELS[w]);
                    for (String id : Car.FUEL_FEATURES) Features.set(p, id, !"ev".equals(Car.FUELS[w]));
                    render();
                }
            }));
        }
        c.addView(fuels);
        if (!"ev".equals(fuel)) {
            space(c, 20);
            c.addView(label(Hindi.t("cng".equals(fuel) ? "CNG cylinder, kg" : "Fuel tank, litres")));
            space(c, 10);
            tank = field(p.str("tankL", ""), Hindi.t("Leave empty if unsure"), InputType.TYPE_CLASS_NUMBER);
            c.addView(tank, new LinearLayout.LayoutParams(Ui.px(240), -2));
        }
        space(c, 20);
        c.addView(label(Hindi.t("Your name, for the greeting under the car")));
        space(c, 10);
        name = field(p.welcome(), Hindi.t("Leave empty for no greeting"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        c.addView(name, new LinearLayout.LayoutParams(Ui.px(420), -2));
    }

    private void pickBrand() {
        save();
        final List<Car.Brand> all = Car.brands(a);
        String[] names = new String[all.size() + 1];
        for (int i = 0; i < all.size(); i++) names[i] = all.get(i).name;
        names[all.size()] = Hindi.t("Another brand…");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Which brand?"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w < all.size()) pickModel(all.get(w));
                        else Chores.text(a, Hindi.t("Brand"), Hindi.t("e.g. Ford"), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, new Extras.Num() {
                            @Override
                            public void set(String v) {
                                if (!v.trim().isEmpty()) pickModel(new Car.Brand(Car.idFor(v.trim()), v.trim()));
                            }
                        });
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private void pickModel(final Car.Brand b) {
        final Extras.Num then = new Extras.Num() {
            @Override
            public void set(String v) {
                p.put("carBrand", b.id);
                p.put("carBrandName", b.name);
                p.put("carModel", v.trim());
                if (!v.trim().isEmpty()) p.put("carName", Car.nameFor(v.trim()));
                render();
            }
        };
        if (b.models.isEmpty()) {
            Chores.text(a, Hindi.t("Model"), Hindi.t("e.g. Ecosport"), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, then);
            return;
        }
        String[] names = b.models.toArray(new String[b.models.size() + 1]);
        names[b.models.size()] = Hindi.t("Another model…");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.f("%1$s: which model?", b.name))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w < b.models.size()) then.set(b.models.get(w));
                        else Chores.text(a, Hindi.t("Model"), "", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, then);
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private void look(LinearLayout c) {
        c.addView(title(Hindi.t("Choose the look")));
        space(c, 8);
        c.addView(body(Hindi.t("Your car's colour for the highlights, and a screen layout. Themes and more are in Settings.")));
        space(c, 20);
        c.addView(label(Hindi.t("Colour")));
        space(c, 12);
        Flow swatches = new Flow(a);
        String now = p.str("theme", Ui.THEMES[0]);
        for (int i = 0; i < Ui.THEMES.length; i++) {
            final String t = Ui.THEMES[i];
            swatches.addView(swatch(i, t.equals(now), new Runnable() {
                @Override
                public void run() {
                    p.put("theme", t);
                    a.restyle();
                    render();
                }
            }));
        }
        c.addView(swatches);
        TextView picked = text(14, Ui.SILVER_DIM, "sans-serif");
        picked.setText(now);
        picked.setPadding(Ui.px(4), Ui.px(6), 0, 0);
        c.addView(picked);
        space(c, 22);
        c.addView(label(Hindi.t("Screen layout")));
        space(c, 12);
        c.addView(new LayoutGallery(a, Layouts.current(p), new LayoutGallery.Picked() {
            @Override
            public void picked(String id) {
                p.put("layout", id);
                a.build();
                render();
            }
        }));
    }

    private void level(LinearLayout c) {
        c.addView(title(Hindi.t("How much should OpenRath show?")));
        space(c, 8);
        c.addView(body(Hindi.t("Start simple if you like: you can add more any time in Settings, OpenRath level, or switch single features on.")));
        space(c, 20);
        for (int i = 0; i < Features.LEVELS.length; i++) {
            final int w = i;
            c.addView(choice(Hindi.t(Features.LEVEL_NAMES[i]), Hindi.t(Features.LEVEL_ABOUT[i]), level == i, new Runnable() {
                @Override
                public void run() {
                    level = w;
                    render();
                }
            }), new LinearLayout.LayoutParams(-1, -2));
            space(c, 12);
        }
    }

    private void apps(LinearLayout c) {
        c.addView(title(Hindi.t("Your apps")));
        space(c, 8);
        c.addView(body(Hindi.t("The buttons and boxes open these. OpenRath found what it could; tap one to change it.")));
        space(c, 20);
        String[][] apps = {{"media:radio", "Radio", "FM and AM, from the stereo's radio app"}, {"media:music", "Music", "Songs from USB, Bluetooth or a streaming app"},
                {"dock:navi", "Navigation", "Opens from the dock and from saved places"}, {"dock:phone", "Phone", "Calls and contacts"}};
        PackageManager pm = a.getPackageManager();
        for (final String[] ap : apps) {
            String saved = p.app(ap[0]);
            final String pkg = saved != null && Compat.launch(pm, saved) != null ? saved : null;   // a default that isn't installed counts as not chosen
            String label = pkg == null ? Hindi.t("Not chosen") : a.appLabel(pkg);
            View r = row(Hindi.t(ap[1]) + "  ·  " + label, Hindi.t(ap[2]), Hindi.t(pkg == null ? "Choose" : "Change"), pkg != null, new Runnable() {
                @Override
                public void run() {
                    AppPicker.show(a, Hindi.f("App for %1$s", Hindi.t(ap[1])), new AppPicker.Picked() {
                        @Override
                        public void picked(String chosen) {
                            p.setApp(ap[0], chosen);
                            render();
                        }
                    });
                }
            });
            if (pkg != null) {
                try {
                    ImageView icon = new ImageView(a);
                    icon.setImageDrawable(pm.getApplicationIcon(pkg));
                    int s = Ui.px(40);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
                    lp.rightMargin = Ui.px(14);
                    ((LinearLayout) r).addView(icon, 0, lp);
                } catch (Exception ignored) {
                    // app gone: no icon
                }
            }
            c.addView(r);
            space(c, 10);
        }
    }

    private void finish(LinearLayout c) {
        c.addView(title(Hindi.t("Almost done")));
        space(c, 8);
        c.addView(body(Hindi.t("Two things make OpenRath feel like part of the car.")));
        space(c, 20);
        String phone = pairedPhone();
        c.addView(row(Hindi.t("Connect your phone"), phone != null ? Hindi.f("Paired with %1$s. Calls and music come through the stereo.", phone)
                        : Hindi.t("Pair it in Bluetooth settings for calls and music through the stereo."),
                phone != null ? "✓ " + Hindi.t("Paired") : Hindi.t("Bluetooth settings"), phone != null, phone != null ? null : new Runnable() {
                    @Override
                    public void run() {
                        try {
                            a.startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                        } catch (Exception e) {
                            Toast.makeText(a, Hindi.t("This unit has no Bluetooth settings screen"), Toast.LENGTH_LONG).show();
                        }
                    }
                }));
        space(c, 10);
        boolean home = Compat.isHome(a);
        c.addView(row(Hindi.t("Make OpenRath the home app"), Hindi.t("The stereo starts in OpenRath, and the Home button comes back to it."),
                home ? "✓ " + Hindi.t("Done") : Hindi.t("Set"), home, home ? null : new Runnable() {
                    @Override
                    public void run() {
                        if (!Compat.askHome(a))
                            Toast.makeText(a, Hindi.t("This unit has no screen to choose the home app; press Home and choose OpenRath, Always"), Toast.LENGTH_LONG).show();
                    }
                }));
        if (Speak.engines(a).isEmpty()) {
            space(c, 10);
            c.addView(row(Hindi.t("Get a spoken voice"), Hindi.t("This stereo has no voice, so OpenRath can't speak alerts or turns. Speech Services by Google is free."),
                    Hindi.t("Get it"), false, new Runnable() {
                        @Override
                        public void run() {
                            Speak.getVoice(a);
                        }
                    }));
        }
        space(c, 24);
        c.addView(body(Hindi.t("Tip: long-press any box to change what it shows. Settings is the gear in the buttons bar.")));
    }

    /** A paired phone's name, or null. */
    private static String pairedPhone() {
        try {
            android.bluetooth.BluetoothAdapter ad = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (ad == null) return null;
            for (android.bluetooth.BluetoothDevice d : ad.getBondedDevices()) {
                android.bluetooth.BluetoothClass k = d.getBluetoothClass();
                if (k != null && k.getMajorDeviceClass() == android.bluetooth.BluetoothClass.Device.Major.PHONE) return d.getName();
            }
        } catch (Throwable ignored) {
            // no Bluetooth permission or adapter
        }
        return null;
    }

    // ------------------------------------------------------------------ pieces

    private TextView text(float size, int colour, String family) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * Ui.k);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    private TextView title(String s) {
        TextView t = text(32, Ui.SILVER, "sans-serif-light");
        t.setText(s);
        return t;
    }

    private TextView body(String s) {
        TextView t = text(17, Ui.SILVER_DIM, "sans-serif");
        t.setText(s);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(12, Ui.ACCENT, "sans-serif-medium");
        t.setLetterSpacing(0.16f);
        t.setText(s.toUpperCase(Locale.getDefault()));
        return t;
    }

    private void space(LinearLayout c, float h) {
        c.addView(new View(a), new LinearLayout.LayoutParams(1, Ui.px(h)));
    }

    private TextView button(String s, boolean main) {
        TextView b = text(17, main ? 0xFF000000 : Ui.SILVER, "sans-serif-medium");
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(Ui.px(main ? 170 : 110));
        b.setMinHeight(Ui.px(54));
        b.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(27));
        if (main) g.setColor(Ui.mix(Ui.ACCENT, 0xFFFFFFFF, 0.85f));
        else g.setStroke(Math.max(1, Ui.px(1.5f)), Ui.EDGE);
        b.setBackground(g);
        return b;
    }

    private GradientDrawable card(boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(14));
        g.setColor(on ? Ui.PANEL_TOP : Ui.PANEL);
        g.setStroke(Math.max(1, Ui.px(on ? 2.5f : 1.2f)), on ? Ui.ACCENT : Ui.mix(Ui.EDGE, Ui.PANEL, 0.6f));
        return g;
    }

    /** A card to pick: a name, a line under it, lit when chosen. */
    private View choice(String name, String about, boolean on, final Runnable tap) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.px(20), Ui.px(16), Ui.px(20), Ui.px(16));
        c.setBackground(card(on));
        TextView n = text(19, Ui.SILVER, "sans-serif-medium");
        n.setText((on ? "● " : "○ ") + name);
        c.addView(n);
        if (about != null) {
            TextView ab = text(14, Ui.SILVER_DIM, "sans-serif");
            ab.setText(about);
            ab.setPadding(0, Ui.px(6), 0, 0);
            c.addView(ab);
        }
        c.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                tap.run();
            }
        });
        return c;
    }

    /** A line with a name, an explanation and a status or action on the right. */
    private View row(String name, String about, String action, boolean ok, final Runnable tap) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.px(18), Ui.px(14), Ui.px(18), Ui.px(14));
        r.setBackground(card(false));
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(17, Ui.SILVER, "sans-serif-medium");
        n.setText(name);
        words.addView(n);
        if (about != null) {
            TextView ab = text(14, Ui.SILVER_DIM, "sans-serif");
            ab.setText(about);
            ab.setPadding(0, Ui.px(4), 0, 0);
            words.addView(ab);
        }
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView act;
        if (tap != null) {
            act = button(action, false);
            act.setMinHeight(Ui.px(46));
            act.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    tap.run();
                }
            });
            r.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    tap.run();
                }
            });
        } else {
            act = text(15, ok ? Ui.ACCENT : Ui.SILVER_DIM, "sans-serif-medium");
            act.setText(action);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.px(16);
        r.addView(act, lp);
        return r;
    }

    private View chip(String s, boolean on, final Runnable tap) {
        TextView t = text(16, on ? 0xFF000000 : Ui.SILVER, on ? "sans-serif-medium" : "sans-serif");
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(Ui.px(48));
        t.setPadding(Ui.px(20), 0, Ui.px(20), 0);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.px(24));
        if (on) g.setColor(Ui.mix(Ui.ACCENT, 0xFFFFFFFF, 0.85f));
        else {
            g.setColor(Ui.PANEL);
            g.setStroke(Math.max(1, Ui.px(1.2f)), Ui.EDGE);
        }
        t.setBackground(g);
        t.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                tap.run();
            }
        });
        return t;
    }

    private View swatch(int i, boolean on, final Runnable tap) {
        View v = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Ui.colour(i));
        g.setStroke(Ui.px(on ? 4 : 1.5f), on ? Ui.SILVER : Ui.EDGE);
        v.setBackground(g);
        v.setLayoutParams(new ViewGroup.LayoutParams(Ui.px(52), Ui.px(52)));
        v.setContentDescription(Ui.THEMES[i]);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View x) {
                tap.run();
            }
        });
        return v;
    }

    private EditText field(String now, String hint, int type) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setText(now);
        e.setHint(hint);
        e.setTextColor(Ui.SILVER);
        e.setHintTextColor(Ui.mix(Ui.SILVER_DIM, Ui.PANEL, 0.6f));
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 18 * Ui.k);
        e.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(12));
        e.setBackground(card(false));
        return e;
    }

    private ImageView wheel(float size) {
        Bitmap b = Layouts.asset(a, "logo_s");
        if (b == null) return null;
        ImageView v = new ImageView(a);
        v.setImageBitmap(b);
        v.setColorFilter(Ui.SILVER);
        int s = Ui.px(size);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return v;
    }

    /** Lays chips and swatches out in rows that wrap. */
    static final class Flow extends ViewGroup {
        Flow(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int max = MeasureSpec.getSize(ws), gap = Ui.px(10), x = 0, y = 0, line = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                v.measure(lp != null && lp.width > 0 ? MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST),
                        lp != null && lp.height > 0 ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (x > 0 && x + v.getMeasuredWidth() > max) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                x += v.getMeasuredWidth() + gap;
                line = Math.max(line, v.getMeasuredHeight());
            }
            setMeasuredDimension(max, y + line);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int max = r - l, gap = Ui.px(10), x = 0, y = 0, line = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (x > 0 && x + v.getMeasuredWidth() > max) {
                    x = 0;
                    y += line + gap;
                    line = 0;
                }
                v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
                x += v.getMeasuredWidth() + gap;
                line = Math.max(line, v.getMeasuredHeight());
            }
        }
    }
}
