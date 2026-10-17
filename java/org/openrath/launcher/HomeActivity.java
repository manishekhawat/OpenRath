package org.openrath.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** The home screen: silver dock on the left, five boxes you can rearrange with a long-press. */
public class HomeActivity extends Activity implements Cards.Host {

    Prefs prefs;
    Hub hub;
    Dashboard dash;
    android.widget.FrameLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Fm.app = getApplicationContext();
        Crash.install(this);
        prefs = new Prefs(this);
        // a new version: steps skipped after a crash in the old one get another try
        try {
            int v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            if (prefs.num("crashForgiven", 0) != v) {
                Crash.forgive();
                prefs.put("crashForgiven", String.valueOf(v));
            }
        } catch (Exception ignored) {
        }
        turn();
        hub = new Hub(this, prefs);
        dash = new Dashboard(this);
        root = new android.widget.FrameLayout(this);
        root.addView(dash, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 23
                && (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission("android.permission.CALL_PHONE") != PackageManager.PERMISSION_GRANTED)) {
            // calls and call state: the plain Android phone (Bluetooth hands-free through the system phone)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_CALL_LOG, "android.permission.CALL_PHONE", "android.permission.READ_PHONE_STATE",
                    "android.permission.ANSWER_PHONE_CALLS"}, 1);
        }
        findRadio();
        Drive.start(this);
        Keys.init(this);
        widgets = new android.appwidget.AppWidgetHost(this, 2041);
        android.content.IntentFilter f = new android.content.IntentFilter(Keys.ACTION);
        f.addAction(Ticker.CHANGED);
        f.addAction(Drive.REST);
        f.addAction(Drive.FUEL_LOW);
        f.addAction(Drive.CRASH);
        f.addAction(Drive.HIGHWAY);
        registerReceiver(events, f);
        build();
        handleIntent(getIntent());
    }

    /** True while the dashboard is on screen (the key service leaves keys to it then). */
    static volatile boolean visible;
    android.appwidget.AppWidgetHost widgets;

    private final android.content.BroadcastReceiver events = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            if (Keys.ACTION.equals(i.getAction())) keyAction(i.getStringExtra("action"));
            else if (Ticker.CHANGED.equals(i.getAction())) Extras.ticker(HomeActivity.this);
            else if (Drive.REST.equals(i.getAction())) restStop();
            else if (Drive.HIGHWAY.equals(i.getAction())) highwayStrip();
            else if (Drive.CRASH.equals(i.getAction()))
                Rescue.show(HomeActivity.this, prefs, i.getDoubleExtra("lat", 0), i.getDoubleExtra("lon", 0), false);
            else if (Drive.FUEL_LOW.equals(i.getAction())) {
                Cards.MapCard m = mapCard();
                if (m != null) m.js("restStop('fuel')");
            }
        }
    };

    /** The map box on this page, or null. */
    Cards.MapCard mapCard() {
        if (dash == null) return null;
        for (View v : dash.slots) if (v instanceof Cards.MapCard) return (Cards.MapCard) v;
        return null;
    }

    /** Time for a break: show the stops ahead on the map (or say where to find them). */
    void restStop() {
        Cards.MapCard m = mapCard();
        if (m != null) m.js("restStop()");
        else Toast.makeText(this, "Time for a break. Put the map on the dashboard to see stops ahead.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    private void handleIntent(Intent i) {
        if (i != null && Keys.ACTION.equals(i.getAction())) keyAction(i.getStringExtra("action"));
        if (i != null && Drive.CRASH.equals(i.getAction())) {
            i.setAction(null);   // not again when the screen is recreated
            Rescue.show(this, prefs, i.getDoubleExtra("lat", 0), i.getDoubleExtra("lon", 0), false);
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (Keys.onKey(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        Extras.touched(this);
        return super.dispatchTouchEvent(e);
    }

    private boolean indicating;
    private long turnCamAt;

    /** Slow with an indicator on: the stereo's 360 cameras, as factory around-view systems do. */
    private void turnCam(Hub h) {
        boolean on = h.leftOn || h.rightOn;
        String sp = h.speed();
        if (on && !indicating && Features.on(prefs, "cam360Turn") && !sp.equals("--") && Integer.parseInt(sp) < 15
                && System.currentTimeMillis() - turnCamAt > 20000) {
            turnCamAt = System.currentTimeMillis();
            View360.openCam(this, prefs);
        }
        indicating = on;
    }

    /** An app's name, for settings rows. */
    String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    /** The Topway add-on is in use (a Topway unit, with "Stereo maker's services" on). */
    boolean topway() {
        return hub.unit instanceof TopwayUnit;
    }

    /** A steering-wheel button's action. */
    void keyAction(String a) {
        if (a == null) return;
        Extras.wake(this);
        if ("toggle".equals(a) || "next".equals(a) || "prev".equals(a)) Extras.media(this, a);
        else if ("answer".equals(a)) hub.unit.answer(this);
        else if ("hangup".equals(a)) hub.unit.hangUp(this);
        else if ("navi".equals(a)) openApp("dock:navi", "Navigation");
        else if ("radio".equals(a)) openApp("media:radio", "Radio");
        else if ("music".equals(a)) openApp("media:music", "Music");
        else if ("dash".equals(a)) closeSettings();
        else if ("screen".equals(a)) Extras.screenOff(this);
        else if ("page".equals(a)) turnPage(1);
        else if ("voice".equals(a)) voiceSearch(mapCard());
        else if ("driver".equals(a)) {
            if (Features.on(prefs, "profiles")) Profiles.next(this, prefs);
        } else if ("docs".equals(a)) run(new Voice.Command("docs", ""));
        else if ("news".equals(a)) run(new Voice.Command("news", ""));
        else if ("cab".equals(a) || "sos".equals(a)) run(new Voice.Command(a, ""));
        else if ("meter".equals(a)) run(new Voice.Command("meter", prefs.longNum("cabMeterAt", 0) > 0 ? "stop" : "start"));
        else if ("kids".equals(a)) run(new Voice.Command("kids", ""));
        else if ("fog".equals(a)) run(new Voice.Command("fog", ""));
        else if ("share".equals(a) || "split".equals(a) || "parkTimer".equals(a) || "hotspot".equals(a)) run(new Voice.Command(a, ""));
    }

    /**
     * Share the trip as a QR code, with nothing sent over the internet: scanning it opens Google
     * Maps on the phone with the route from where the car is now to where it's going, so family
     * can see the way and the time left. Without a destination it is just the car's spot.
     */
    void shareTrip() {
        if (!Features.on(prefs, "tripShare")) {
            Toast.makeText(this, "Turn on \"Share the trip by QR code\" in Settings > Features", Toast.LENGTH_LONG).show();
            return;
        }
        double lat = Drive.latNow, lon = Drive.lonNow;
        if (lat == 0 && lon == 0 && hub.fix != null) {
            lat = hub.fix.getLatitude();
            lon = hub.fix.getLongitude();
        }
        if (lat == 0 && lon == 0) {
            Toast.makeText(this, "Waiting for the GPS to find the car", Toast.LENGTH_LONG).show();
            return;
        }
        String[] d = prefs.str("dest", "").split("\\|");
        if (d.length == 3) {
            try {
                double tl = Double.parseDouble(d[1]), tn = Double.parseDouble(d[2]);
                double km = Geo.dist(lat, lon, tl, tn) / 1000;
                Qr.show(this, "Our trip to " + d[0], Geo.routeLink(lat, lon, tl, tn),
                        String.format(java.util.Locale.US, "About %.0f km to go in a straight line. ", km)
                                + "Scan with a phone to see the route and time from where the car is now. "
                                + "Show a new code later for a fresh position.");
                return;
            } catch (Exception ignored) {
            }
        }
        Qr.show(this, "Where we are now", Geo.mapsLink(lat, lon),
                "Scan with a phone to see where the car is now. Pick a destination on the map to share the route too.");
    }

    /** Once: the floating mic can stay over other apps if Android allows it. */
    private void askOverlay() {
        if (!visible || !OverMic.wanted(prefs) || OverMic.allowed(this) || "yes".equals(prefs.str("overlayAsked", ""))) return;
        prefs.put("overlayAsked", "yes");
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Mic over other apps")
                .setMessage("To keep the floating mic on top of maps and music, Android needs you to allow "
                        + "\"Display over other apps\" for OpenRath on the next page. You can turn the mic over other apps "
                        + "off in Settings > Features.")
                .setPositiveButton("Allow", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        OverMic.ask(HomeActivity.this);
                    }
                }).setNegativeButton("Not now", null).show();
    }

    /** The parking spot as a QR code that opens it in Google Maps on a phone. */
    @Override
    public void parkQr() {
        if (!Features.on(prefs, "parkLink")) {
            Toast.makeText(this, "Turn on \"Parking spot to your phone\" in Settings > Driving", Toast.LENGTH_LONG).show();
            return;
        }
        if (Drive.parkAt == 0) {
            Toast.makeText(this, "No parking spot saved yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Qr.show(this, "Where the car is parked", Geo.mapsLink(Drive.parkLat, Drive.parkLon),
                "Scan with your phone to open the spot in Google Maps and walk back to the car.");
    }

    /** Call a number through the unit's Bluetooth phone (the map's service centre list). */
    @Override
    public void call(String number) {
        String n = number == null ? "" : number.replaceAll("[^0-9+]", "");
        if (n.isEmpty()) return;
        String err = hub.unit.call(this, n);
        Toast.makeText(this, err == null ? "Calling " + n + "…" : "Couldn't call: " + err, Toast.LENGTH_SHORT).show();
    }

    /** Swipe to the next or previous dashboard page. */
    void turnPage(int dir) {
        int n = prefs.pages();
        if (n < 2) return;
        Prefs.page = (Prefs.page + dir + n) % n;
        build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            widgets.startListening();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onStop() {
        OverMic.show(getApplicationContext(), prefs);
        try {
            widgets.stopListening();
        } catch (Throwable ignored) {
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(events);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public android.appwidget.AppWidgetHost widgetHost() {
        return widgets;
    }

    @Override
    public void pickWidget(String content) {
        Extras.pickWidget(this, content);
    }

    @Override
    public void obdMenu() {
        Extras.obdMenu(this);
    }

    @Override
    public void tyresMenu() {
        Extras.tyresMenu(this);
    }

    /** First start: pick the unit's own radio app, so the radio box and dock button just work. */
    private void findRadio() {
        if (prefs.app("media:radio") != null) return;
        PackageManager pm = getPackageManager();
        String[] known = {"com.tw.radio", "com.tw.radioapp", "com.tw.fmradio", "com.ts.radio", "com.syu.radio",
                "com.qf.radio", "com.microntek.radio", "com.hct.radio", "com.android.fmradio",
                "com.mediatek.fmradio", "com.caf.fmradio", "com.spreadtrum.fmradio", "com.sprd.fmradio"};
        for (String k : known) {
            if (pm.getLaunchIntentForPackage(k) != null) {
                prefs.setApp("media:radio", k);
                return;
            }
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(main, 0)) {
            String label = String.valueOf(r.loadLabel(pm)).toLowerCase(java.util.Locale.US);
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            if (label.contains("radio") || label.equals("fm") || label.contains("收音") || Hub.looksLikeRadio(pkg)) {
                prefs.setApp("media:radio", pkg);
                return;
            }
        }
    }

    /** The picture layout on screen, or null when the boxes are. */
    LayoutView layoutView;

    /** (Re)create the dock and the boxes (or the picture layout) from the saved settings. */
    void build() {
        hub.clearListeners();
        dash.removeAllViews();
        dash.swapped = prefs.swapped();
        dash.dockAt = prefs.str("dockAt", "left");
        dash.wide = false;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        Ui.user = Math.max(50, Math.min(200, prefs.num("uiScale", 100))) / 100f;
        Ui.k = Ui.scale(dm.widthPixels, dm.heightPixels, Ui.user);
        if (Ui.portrait(dm.widthPixels, dm.heightPixels)) dash.dockAt = "bottom";   // stacked boxes, apps below
        Car.apply(prefs);
        lastW = dm.widthPixels;
        lastH = dm.heightPixels;
        Ui.theme(prefs);
        Theme.load(this, prefs);
        // a picture layout instead of the boxes
        if (layoutView != null) {
            root.removeView(layoutView);
            layoutView = null;
        }
        java.util.Arrays.fill(dash.slots, null);
        Layouts.Layout lay = Layouts.get(this, Layouts.current(prefs));
        dash.setVisibility(lay == null ? View.VISIBLE : View.GONE);
        if (lay != null) {
            layoutView = new LayoutView(this, lay);
            root.addView(layoutView, root.indexOfChild(dash) + 1, new android.widget.FrameLayout.LayoutParams(-1, -1));
            hub.add(layoutView);
            hub.add(overlays);
            hub.onReverse = new Runnable() {
                @Override
                public void run() {
                    reverseChanged();
                }
            };
            floaters();
            return;
        }
        dash.wide = WIDE.equals(Layouts.current(prefs));
        dash.setBackgroundColor(Ui.BACKGROUND);
        if (Theme.gradient() != null)
            dash.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, Theme.gradient()));
        android.graphics.Bitmap wall = Theme.background();
        if (wall != null) {
            android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(getResources(), wall);
            dash.setBackground(new CenterCrop(bd));
        }
        if (Prefs.page >= prefs.pages()) Prefs.page = 0;
        dash.pages = prefs.pages();
        dash.page = Prefs.page;
        dash.pager = new Dashboard.Pager() {
            @Override
            public void turn(int dir) {
                turnPage(dir);
            }
        };
        dash.setWillNotDraw(false);
        dash.dock = dash.wide ? wideBar() : dock();
        dash.addView(dash.dock);
        for (int i = 0; i < Prefs.SLOTS; i++) {
            Cards.Card c = Cards.make(prefs.slot(i), this, this, i);
            dash.slots[i] = c;
            dash.addView(c);
            hub.add(c);
        }
        dash.requestLayout();
        hub.add(overlays);
        dash.mover = new Dashboard.Mover() {
            @Override
            public void swap(int from, int to) {
                prefs.swapSlots(from, to);
                build();
                dash.setEditing(true);
            }
        };
        hub.onReverse = new Runnable() {
            @Override
            public void run() {
                reverseChanged();
            }
        };
        floaters();
    }

    /** The Wide map arrangement's id in the layout setting. */
    static final String WIDE = "wide";

    private View micView, perfView;

    /** The draggable mic and the performance box, above the dashboard and below everything else. */
    void floaters() {
        if (micView != null) root.removeView(micView);
        if (perfView != null) root.removeView(perfView);
        micView = perfView = null;
        int at = root.indexOfChild(layoutView != null ? layoutView : dash) + 1;
        if (Features.on(prefs, "perf")) {
            perfView = new Floaters.Perf(this);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(Ui.px(230), Ui.px(84),
                    Gravity.TOP | Gravity.LEFT);
            lp.leftMargin = Ui.px(dash.wide ? 26 : 8);
            lp.topMargin = Ui.px(dash.wide ? 26 : 8);
            root.addView(perfView, at, lp);
        }
        if (Features.on(prefs, "floatMic")) {
            final Floaters.Mic mic = new Floaters.Mic(this, prefs, new Runnable() {
                @Override
                public void run() {
                    voiceSearch(mapCard());
                }
            });
            micView = mic;
            root.addView(mic, at, new android.widget.FrameLayout.LayoutParams(Ui.px(64), Ui.px(64)));
            root.post(new Runnable() {
                @Override
                public void run() {
                    mic.restore(root.getWidth(), root.getHeight());
                }
            });
        }
    }

    /** The bottom bar of the Wide map: the clock, your apps, the mic, all apps and settings. */
    private View wideBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(24), Ui.px(4), Ui.px(18), Ui.px(8));
        android.widget.TextClock clock = new android.widget.TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Ui.SILVER);
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30 * Ui.k);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif", 0));
        bar.addView(clock, new LinearLayout.LayoutParams(0, -1, 1f));
        clock.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        final java.util.List<String> apps = wideApps();
        for (int i = 0; i < 6; i++) {
            final int slot = i;
            final String pkg = i < apps.size() ? apps.get(i) : null;
            if (pkg == null && i > apps.size()) break;   // one "add" button after the apps
            ImageView v = new ImageView(this);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v.setPadding(Ui.px(6), Ui.px(6), Ui.px(6), Ui.px(6));
            if (pkg != null) {
                try {
                    v.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
                } catch (Exception e) {
                    continue;
                }
            } else {
                v.setImageBitmap(Ui.img(this, "dock_apps"));
                v.setAlpha(0.45f);
            }
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    if (pkg == null) pickWideApp(slot);
                    else {
                        if (ChildLock.blocked(prefs, pkg)) {
                            Toast.makeText(HomeActivity.this, ChildLock.SORRY, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Intent in = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (in != null) startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    pickWideApp(slot);
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(72), Ui.px(72));
            lp.leftMargin = lp.rightMargin = Ui.px(6);
            bar.addView(v, lp);
        }
        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(0, 1, 1f));
        String[] icons = {"mic", "apps", "settings"};
        for (final String n : icons) {
            Floaters.Icon ic = new Floaters.Icon(this, n, Ui.SILVER);
            ic.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    if ("mic".equals(n)) voiceSearch(mapCard());
                    else if ("apps".equals(n)) startActivity(new Intent(HomeActivity.this, AppsActivity.class));
                    else openSettings();
                }
            });
            bar.addView(ic, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        }
        return bar;
    }

    /** The Wide map bar's apps: chosen ones, else the navigation, music, phone and radio apps. */
    private java.util.List<String> wideApps() {
        java.util.List<String> out = new java.util.ArrayList<>();
        String saved = prefs.str("wideApps", "");
        if (!saved.isEmpty()) {
            for (String s : saved.split(",")) if (!s.isEmpty() && !out.contains(s)) out.add(s);
            return out;
        }
        for (String k : new String[]{"dock:navi", "media:music", "dock:phone", "media:radio"}) {
            String p = prefs.app(k);
            if (p != null && !out.contains(p)) out.add(p);
        }
        return out;
    }

    private void pickWideApp(final int slot) {
        AppPicker.show(this, "App for the bar", new AppPicker.Picked() {
            @Override
            public void picked(String pkg) {
                java.util.List<String> l = wideApps();
                if (slot < l.size()) l.set(slot, pkg);
                else l.add(pkg);
                prefs.put("wideApps", android.text.TextUtils.join(",", l));
                build();
            }
        });
    }

    // ------------------------------------------------------------------ night dimming and reverse

    private View dimmer, reverseView;

    /** Dims the dashboard at night (after sunset, or with the headlights) with a dark layer on top. */
    private final Hub.Listener overlays = new Hub.Listener() {
        @Override
        public void changed(Hub h) {
            turnCam(h);
            String mode = prefs.str("dim", "off");
            boolean night = false;
            if ("lights".equals(mode)) night = h.lightsOn();
            else if ("sunset".equals(mode) && h.fix != null) night = Care.dark(h.fix.getLatitude(), h.fix.getLongitude(), System.currentTimeMillis());
            if (dimmer == null) {
                dimmer = new View(HomeActivity.this);
                dimmer.setBackgroundColor(0xFF000000);
                dimmer.setClickable(false);
                dimmer.setFocusable(false);
                root.addView(dimmer, root.indexOfChild(layoutView != null ? layoutView : dash) + 1,
                        new android.widget.FrameLayout.LayoutParams(-1, -1));
            }
            dimmer.setAlpha(night ? prefs.num("dimLevel", 40) / 100f : 0f);
            dimmer.setVisibility(night ? View.VISIBLE : View.GONE);
            nightDrive(h);
            fogMode(h);
            valetBadge();
        }
    };

    // ------------------------------------------------------------------ night-drive mode and valet badge

    private NightDrive night;
    private boolean nightDismissed;
    long nightPreviewUntil;

    /** After sunset during a drive: black screen, red-tinted map, big red speed. */
    void nightDrive(Hub h) {
        long now = System.currentTimeMillis();
        boolean driving = Drive.drives.cur != null;
        if (!driving) nightDismissed = false;   // next drive, night mode again
        boolean dark = h.fix != null && Care.dark(h.fix.getLatitude(), h.fix.getLongitude(), now);
        boolean want = now < nightPreviewUntil
                || Features.on(prefs, "nightDrive") && dark && driving && !nightDismissed && settingsView == null && !hub.reverse;
        if (want && night == null) {
            night = new NightDrive(this, new NightDrive.Closed() {
                @Override
                public void closed() {
                    nightDismissed = true;
                    nightPreviewUntil = 0;
                    nightDrive(hub);
                }
            });
            root.addView(night, new android.widget.FrameLayout.LayoutParams(-1, -1));
        } else if (!want && night != null) {
            root.removeView(night);
            night = null;
        }
        if (night != null) {
            night.setSpeed(h.speed());
            night.setMap(mapCard());
        }
    }

    private Fog fog;
    private boolean fogDismissed;

    /** In fog while driving: big speed, slow guide speed and the next bend (see Fog). */
    void fogMode(Hub h) {
        boolean driving = Drive.drives.cur != null;
        if (!driving) fogDismissed = false;
        boolean want = Fog.now(prefs) && driving && !fogDismissed && settingsView == null && !hub.reverse && !Rescue.showing();
        if (want && fog == null) {
            fog = new Fog(this, prefs, new Fog.Closed() {
                @Override
                public void closed() {
                    fogDismissed = true;
                    fogMode(hub);
                }
            });
            root.addView(fog, new android.widget.FrameLayout.LayoutParams(-1, -1));
        } else if (!want && fog != null) {
            root.removeView(fog);
            fog = null;
        }
        if (fog != null) fog.setSpeed(h.speed());
    }

    /** When a service is near and the car is parked: offer to call the saved service centre (once a day). */
    void serviceBooking() {
        if (!Features.on(prefs, "serviceBooking") || !Features.on(prefs, "care") || Drive.kmhNow > 3) return;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        if (today.equals(prefs.str("bookAsked", ""))) return;
        int i = Care.bookingDue(prefs);
        if (i < 0) return;
        prefs.put("bookAsked", today);
        final String[] mine = prefs.str("myService", "").split("\\|", -1);
        final boolean have = mine.length == 2 && !mine[1].isEmpty();
        long k = Care.kmLeft(prefs, i);
        String when = k < 0 ? "is overdue by " + (-k) + " km" : "is due in " + k + " km";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Book the service?")
                .setMessage(Care.NAMES[i] + " " + when + "." + (have ? " Call " + mine[0] + " to book a slot?"
                        : " Find your service centre on the map and tap Mine next to it, so next time it's one tap to call."))
                .setPositiveButton(have ? "Call" : "Find one", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (have) call(mine[1]);
                        else run(new Voice.Command("service", ""));
                    }
                })
                .setNegativeButton("Later", null).show();
    }

    private android.widget.TextView highwayView;

    /** The slim highway strip: average speed, time since a break, messages waiting. */
    void highwayStrip() {
        boolean on = Highway.on && Features.on(prefs, "highway");
        if (!on) {
            if (highwayView != null) root.removeView(highwayView);
            highwayView = null;
            return;
        }
        if (highwayView == null) {
            highwayView = Ui.text(this, 14, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
            highwayView.setLetterSpacing(0.06f);
            highwayView.setPadding(Ui.px(18), Ui.px(6), Ui.px(18), Ui.px(6));
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(Ui.px(14));
            g.setColor(0xCC1F4E8C);
            highwayView.setBackground(g);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = Ui.px(valetView != null ? 40 : 6);
            root.addView(highwayView, lp);
        }
        long mins = Drive.drives.drivingMinutes(System.currentTimeMillis());
        String t = "HIGHWAY  ·  avg " + Math.round(Drive.avgKmh()) + " km/h  ·  driving " + (mins >= 60 ? mins / 60 + " h " + mins % 60 + " min" : mins + " min")
                + (Highway.held > 0 ? "  ·  " + Highway.held + " waiting" : "");
        highwayView.setText(t);
        root.removeCallbacks(highwayTick);
        root.postDelayed(highwayTick, 30000);
    }

    private final Runnable highwayTick = new Runnable() {
        @Override
        public void run() {
            if (highwayView != null) highwayStrip();
        }
    };

    private android.widget.TextView valetView;

    /** A small "Valet mode" badge at the top while it's on; tap to end it with the PIN. */
    void valetBadge() {
        boolean on = Valet.on(prefs);
        if (on && valetView == null) {
            valetView = Ui.text(this, 13, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
            valetView.setText("VALET MODE  ·  tap to end");
            valetView.setLetterSpacing(0.1f);
            valetView.setPadding(Ui.px(16), Ui.px(6), Ui.px(16), Ui.px(6));
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(Ui.px(14));
            g.setColor(0xCC7A2328);
            valetView.setBackground(g);
            valetView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Valet.toggle(HomeActivity.this, prefs, new Runnable() {
                        @Override
                        public void run() {
                            valetBadge();
                        }
                    });
                }
            });
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = Ui.px(6);
            root.addView(valetView, lp);
        } else if (!on && valetView != null) {
            root.removeView(valetView);
            valetView = null;
        }
    }

    private void reverseChanged() {
        String mode = prefs.str("reverse", "lines");
        if (hub.reverse && "lines".equals(mode)) {
            if (reverseView == null) {
                reverseView = new ReverseView(this);
                root.addView(reverseView, new android.widget.FrameLayout.LayoutParams(-1, -1));
            }
        } else if (reverseView != null) {
            root.removeView(reverseView);
            reverseView = null;
        }
        if (hub.reverse && "app".equals(mode)) openApp("reverse:cam", "Rear camera");
    }

    private View dock() {
        final boolean across = dash.dockAcross();
        LinearLayout d = new LinearLayout(this);
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
        View line = new View(this);
        line.setBackground(rule);
        for (int i = 0; i < Prefs.DOCK.length; i++) {
            final String name = Prefs.DOCK[i];
            final String label = Prefs.DOCK_NAMES[i];
            ImageView v = new ImageView(this);
            v.setImageBitmap(Ui.img(this, "dock_" + name));
            LinearLayout.LayoutParams p = across ? new LinearLayout.LayoutParams(0, Ui.px(62), 1f)
                    : new LinearLayout.LayoutParams(Ui.px(62), 0, 1f);
            v.setLayoutParams(p);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    if ("apps".equals(name)) startActivity(new Intent(HomeActivity.this, AppsActivity.class));
                    else openApp(dockKey(name), label);
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    if ("apps".equals(name)) {
                        settings(-1);
                        return true;
                    }
                    final String key = dockKey(name);
                    AppPicker.show(HomeActivity.this, "App for " + label, new AppPicker.Picked() {
                        @Override
                        public void picked(String pkg) {
                            prefs.setApp(key, pkg);
                        }
                    });
                    return true;
                }
            });
            d.addView(v);
        }
        ImageView gear = new ImageView(this);
        gear.setImageBitmap(Ui.img(this, "dock_settings"));
        gear.setScaleType(ImageView.ScaleType.FIT_CENTER);
        gear.setLayoutParams(across ? new LinearLayout.LayoutParams(0, Ui.px(62), 1f) : new LinearLayout.LayoutParams(Ui.px(62), 0, 1f));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                openSettings();
            }
        });
        d.addView(gear);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.addView(d, new android.widget.FrameLayout.LayoutParams(-1, -1));
        // the thin rule sits on the edge facing the boxes
        String at = dash.dockAt;
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

    @Override
    public void openApp(final String key, final String name) {
        String pkg = prefs.app(key);
        if (ChildLock.blocked(prefs, pkg)) {
            Toast.makeText(this, ChildLock.SORRY, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null && "dock:phone".equals(key)) i = new Intent(Intent.ACTION_DIAL);
        if (i != null) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        }
        AppPicker.show(this, "Choose the app for " + name, new AppPicker.Picked() {
            @Override
            public void picked(String p) {
                prefs.setApp(key, p);
                openApp(key, name);
            }
        });
    }

    @Override
    public Prefs prefs() {
        return prefs;
    }

    @Override
    public void refresh() {
        hub.notifyIfChanged(true);
    }

    @Override
    public void setupLights() {
        Lights.setup(this, prefs, new Lights.Done() {
            @Override
            public void done() {
                hub.stop();
                hub.start();
                hub.notifyIfChanged(true);
            }
        });
    }

    @Override
    public void speak(final String text) {
        Speak.say(this, text, true);   // one engine for the app; waits for it to be ready
    }

    private Cards.MapCard listening;

    @Override
    public void fuel() {
        Fuel.history(this, prefs, new Runnable() {
            @Override
            public void run() {
                hub.notifyIfChanged(true);
            }
        });
    }

    @Override
    public void tripMenu() {
        String[] items = {"Start today's trip again", "Save the parking spot here", "Fuel log"};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(String.format(java.util.Locale.US, "Today %.1f km  ·  GPS odometer %d km", Drive.todayKm, Math.round(Drive.odoKm)))
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) {
                            Drive.resetToday(HomeActivity.this);
                            hub.notifyIfChanged(true);
                        } else if (w == 1) parkHere();
                        else fuel();
                    }
                }).show();
    }

    /** Settings: try OpenRath's own FM tuner, and keep it if it plays. */
    private void tryTuner() {
        if (Tuner.chosen(prefs)) {
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Built-in FM tuner is on")
                    .setPositiveButton("Keep it", null)
                    .setNegativeButton("Use the stock radio again", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            Tuner.stop(HomeActivity.this);
                            prefs.put("fmTuner", "");
                            if (settingsView != null) settingsView.refresh();
                        }
                    }).show();
            return;
        }
        Toast.makeText(this, "Trying the FM chip…", Toast.LENGTH_SHORT).show();
        String err = Tuner.play(this, prefs, true);
        String report = android.text.TextUtils.join("\n", Tuner.steps);
        if (err != null) {
            Tuner.stop(this);
            prefs.put("fmTuner", "no");
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("The built-in tuner can't play on this unit")
                    .setMessage(report + "\n\nOpenRath keeps using the stock radio app for FM. Please send a photo of this.")
                    .setPositiveButton("OK", null).show();
            if (settingsView != null) settingsView.refresh();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Can you hear FM " + Tuner.freqText() + "?")
                .setMessage(report + "\n\nTurn the volume up if it's quiet. If it plays, OpenRath will play FM by itself from now on: "
                        + "no stock radio app, no screen opening.")
                .setPositiveButton("Yes, use it", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        prefs.put("fmTuner", "own");
                        Cards.MediaCard.owner = "radio";
                        if (settingsView != null) settingsView.refresh();
                        hub.notifyIfChanged(true);
                    }
                })
                .setNegativeButton("No sound", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Tuner.stop(HomeActivity.this);
                        prefs.put("fmTuner", "no");
                        if (settingsView != null) settingsView.refresh();
                    }
                }).setCancelable(false).show();
    }

    private void parkHere() {
        if (hub.fix == null) {
            Toast.makeText(this, "No GPS position yet", Toast.LENGTH_LONG).show();
            return;
        }
        Drive.setPark(this, hub.fix.getLatitude(), hub.fix.getLongitude());
        Toast.makeText(this, "Parking spot saved", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void voiceSearch(Cards.MapCard map) {
        boolean commands = Features.on(prefs, "voice");
        String lang = prefs.str("voiceLang", "en-IN");
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, commands ? "Say a command or a place" : "Where to?")
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // no internet: Google's offline speech pack (Settings > Map and navigation to get it)
                .putExtra("android.speech.extra.PREFER_OFFLINE", !Player.online(this) && Features.on(prefs, "offlineVoice"))
                // understand both: Hindi with English words, or English with Hindi words
                .putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"hi-IN".equals(lang) ? "en-IN" : "hi-IN"});
        try {
            listening = map;
            listeningAny = true;
            startActivityForResult(i, 42);
        } catch (Exception e) {
            listening = null;
            listeningAny = false;
            Toast.makeText(this, "Voice needs the Google app on the unit", Toast.LENGTH_LONG).show();
            if (map != null) map.voiceResult(null);
        }
    }

    private boolean listeningAny;

    /** Act on what was heard: a command (FM, call, music, volume, places), else a map search. */
    private void heard(java.util.List<String> said, Cards.MapCard map) {
        if (said == null || said.isEmpty()) {
            if (map != null) map.voiceResult(null);
            return;
        }
        Voice.Command cmd = null;
        if (Features.on(prefs, "voice")) {
            // the recogniser's best guess first; a later guess wins only if it is a real command
            for (String h : said) {
                Voice.Command c = Voice.parse(h);
                if (c == null) continue;
                if (cmd == null) cmd = c;
                if (!"search".equals(c.kind)) {
                    cmd = c;
                    break;
                }
            }
        }
        if (cmd == null || "search".equals(cmd.kind)) {
            if (map != null) map.voiceResult(cmd == null ? said.get(0) : cmd.arg);
            else Toast.makeText(this, "Heard \"" + said.get(0) + "\". Put the map on the dashboard to search for places.", Toast.LENGTH_LONG).show();
            return;
        }
        if (map != null) map.voiceResult(null);
        run(cmd);
    }

    /** Read the top headlines aloud; if there are none yet, fetch them and read them when they come. */
    void readNews() {
        if (!Features.on(prefs, "news")) {
            speak("The news is switched off in Settings, Features.");
            return;
        }
        if (Speak.speaking()) Speak.stop();
        final String topic = News.topicName(prefs.str("newsTopic", "top"));
        if (!News.items.isEmpty() && (System.currentTimeMillis() - News.at < News.FRESH_MS || !Player.online(this))) {
            Speak.sayLong(HomeActivity.this, News.read(News.items, topic));
            return;
        }
        final int v = News.version;
        News.refresh(this, prefs, true);
        if (!Player.online(this)) {
            speak("There's no internet, and no headlines were saved yet.");
            return;
        }
        final android.os.Handler h = new android.os.Handler();
        h.postDelayed(new Runnable() {
            int tries;

            @Override
            public void run() {
                if (News.version != v || ++tries > 12) Speak.sayLong(HomeActivity.this, News.read(News.items, topic));
                else h.postDelayed(this, 500);
            }
        }, 500);
    }

    private void run(Voice.Command cmd) {
        String k = cmd.kind;
        final Unit unit = hub.unit;
        if ("fm".equals(k)) {
            if (!cmd.arg.isEmpty()) prefs.setFmFreq(cmd.arg);
            Cards.MediaCard.owner = "radio";
            String err;
            if (unit.fm(prefs)) err = unit.fmPlay(this, prefs, true);
            else {
                // plain Android: the radio app plays it
                openApp("media:radio", "Radio");
                err = null;
            }
            Resume.remember(prefs, "fm");
            if (!cmd.arg.isEmpty() && unit.fm(prefs)) {
                final String f = cmd.arg;
                // the radio app takes a moment to open; then tune it
                root.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        unit.fmTune(prefs, f);
                    }
                }, 2500);
            }
            Toast.makeText(this, err == null ? "FM " + (cmd.arg.isEmpty() ? "radio" : cmd.arg) : "FM didn't start: " + err, Toast.LENGTH_SHORT).show();
        } else if ("call".equals(k)) {
            String num = Voice.number(cmd.arg), who = cmd.arg;
            if (num.isEmpty()) {
                for (int i = 0; i < 3; i++) {
                    String[] f = Cards.PhoneCard.fav(prefs, i);
                    if (f != null && Voice.sameName(f[0], cmd.arg)) {
                        num = f[1];
                        who = f[0];
                        break;
                    }
                }
            }
            if (num.isEmpty()) {
                speak("I couldn't find " + cmd.arg + " in your favourites");
                Toast.makeText(this, "No favourite called \"" + cmd.arg + "\". Add one in Settings > Phone and messages.", Toast.LENGTH_LONG).show();
            } else {
                String err = unit.call(this, num);
                Toast.makeText(this, err == null ? "Calling " + who + "…" : "Couldn't call: " + err, Toast.LENGTH_SHORT).show();
            }
        } else if ("park".equals(k)) {
            parkQr();
        } else if ("clip".equals(k)) {
            if (!Features.on(prefs, "dashcam")) Toast.makeText(this, "Turn on the dashcam in Settings > Driving", Toast.LENGTH_LONG).show();
            else {
                Dashcam.protect();
                speak("Clip saved");
            }
        } else if ("flood".equals(k)) {
            Monsoon.mark(this, prefs);
        } else if ("docs".equals(k)) {
            if (Features.on(prefs, "wallet")) Wallet.show(this, 0);
            else Toast.makeText(this, "Turn on the documents wallet in Settings > Car", Toast.LENGTH_LONG).show();
        } else if ("kids".equals(k)) {
            if (Features.on(prefs, "kids")) Kids.show(this, prefs);
            else Toast.makeText(this, "Turn on trip games in Settings > Family", Toast.LENGTH_LONG).show();
        } else if ("fog".equals(k)) {
            if (!Features.on(prefs, "fog")) Toast.makeText(this, "Turn on fog mode in Settings > Driving", Toast.LENGTH_LONG).show();
            else {
                boolean on = !"on".equals(prefs.str("fogNow", "off"));
                prefs.put("fogNow", on ? "on" : "off");
                fogDismissed = false;
                speak(on ? "Fog mode on" : "Fog mode off");
                fogMode(hub);
            }
        } else if ("share".equals(k)) {
            shareTrip();
        } else if ("hotspot".equals(k)) {
            Chores.hotspot(this);
        } else if ("weekly".equals(k)) {
            Chores.weekly(this, true);
        } else if ("parkPhoto".equals(k)) {
            Chores.parkPhoto(this);
        } else if ("split".equals(k)) {
            Chores.split(this);
        } else if ("parkTimer".equals(k)) {
            Chores.parkingTimer(this);
        } else if ("news".equals(k)) {
            readNews();
        } else if ("cab".equals(k)) {
            Cab.menu(this);
        } else if ("sos".equals(k)) {
            if (Cab.on(prefs, "cabSos")) Cab.sos(this);
            else Toast.makeText(this, "Turn on One-tap SOS in Settings > Cab driver", Toast.LENGTH_LONG).show();
        } else if ("meter".equals(k)) {
            if (!Cab.on(prefs, "cabMeter")) Toast.makeText(this, "Turn on the fare meter in Settings > Cab driver", Toast.LENGTH_LONG).show();
            else if (prefs.longNum("cabMeterAt", 0) > 0 && "stop".equals(cmd.arg)) Cab.stopMeter(this);
            else if ("stop".equals(cmd.arg)) Toast.makeText(this, "The fare meter isn't running", Toast.LENGTH_SHORT).show();
            else {
                if (prefs.longNum("cabMeterAt", 0) == 0) Cab.startMeter(this);
                Cab.showMeter(this);
            }
            hub.notifyIfChanged(true);
        } else if ("view360".equals(k)) {
            // the stereo's 360 cameras when it has them, else OpenRath's 3D car
            if (!View360.openCam(this, prefs)) View360.show(this);
        } else if ("earnings".equals(k)) {
            String t = Cab.on(prefs, "cabEarnings") ? Cab.spoken(prefs) : "Turn on the earnings log in Settings > Cab driver.";
            Toast.makeText(this, t, Toast.LENGTH_LONG).show();
            speak(t);
        } else if ("aqi".equals(k)) {
            String t = Air.aqi < 0 ? "The air quality isn't known yet." : "The air quality is " + Air.band(Air.aqi).toLowerCase(java.util.Locale.US)
                    + ", A Q I " + Air.aqi + ".";
            Toast.makeText(this, t, Toast.LENGTH_LONG).show();
            speak(t);
        } else if ("music".equals(k)) {
            Cards.MediaCard.owner = "music";
            if (!(Player.playing || hub.music != null && hub.music.playing)) Extras.media(this, "toggle");
        } else if ("next".equals(k) || "prev".equals(k)) {
            Extras.media(this, k);
        } else if ("pause".equals(k)) {
            if (Player.playing || hub.music != null && hub.music.playing || unit.fmPlaying())
                Extras.media(this, "toggle");
        } else if ("volUp".equals(k) || "volDown".equals(k) || "mute".equals(k)) {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            int dir = "volUp".equals(k) ? android.media.AudioManager.ADJUST_RAISE
                    : "volDown".equals(k) ? android.media.AudioManager.ADJUST_LOWER : android.media.AudioManager.ADJUST_TOGGLE_MUTE;
            for (int i = 0; i < ("mute".equals(k) ? 1 : 2); i++)
                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI);
        } else {
            Cards.MapCard m = mapCard();
            if (m == null) {
                Toast.makeText(this, "Put the map on the dashboard for directions", Toast.LENGTH_LONG).show();
                return;
            }
            if ("home".equals(k)) m.js("goFav('Home')");
            else if ("office".equals(k)) m.js("goFav('Office')");
            else if ("fuel".equals(k)) m.js("goFuel()");
            else if ("rest".equals(k)) m.js("restStop()");
            else if ("service".equals(k)) m.js("serviceCentres()");
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == Extras.WIDGET_BIND || code == Extras.WIDGET_CONFIGURE) {
            Extras.widgetResult(this, code, result);
            return;
        }
        if (code >= Wallet.CODE && code < Wallet.CODE + Wallet.DOCS.length) {
            if (result == RESULT_OK && data != null) Wallet.picked(this, code, data.getData());
            return;
        }
        if (code >= 61 && code < 61 + ALL_PICTURES.length) {
            if (result == RESULT_OK && data != null && data.getData() != null) savePicture(code, data.getData());
            return;
        }
        if (code != 42 || !listeningAny) return;
        java.util.ArrayList<String> said = data == null ? null
                : data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        Cards.MapCard m = listening;
        listening = null;
        listeningAny = false;
        heard(said, m);
    }

    @Override
    public void navigateOffline(double lat, double lon, String name) {
        if (!OfflineNav.go(this, prefs, lat, lon, name))
            Toast.makeText(this, "No internet, and no offline map app. Install Organic Maps (free, with offline maps of India) "
                    + "and download your state's map once on Wi-Fi.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void navigate(double lat, double lon, String name) {
        String pkg = prefs.app("dock:navi");
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                String.format(java.util.Locale.US, "google.navigation:q=%.6f,%.6f", lat, lon)));
        if (pkg != null) i.setPackage(pkg);
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (Exception ignored) {
        }
        // not Google Maps: any app that takes a map location
        Intent geo = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(String.format(java.util.Locale.US,
                "geo:%.6f,%.6f?q=%.6f,%.6f(%s)", lat, lon, lat, lon, android.net.Uri.encode(name))));
        try {
            startActivity(geo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "No navigation app found", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void startRadioService() {
        String pkg = prefs.app("media:radio");
        if (pkg == null) return;
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(pkg, PackageManager.GET_SERVICES);
            if (pi.services == null) return;
            for (android.content.pm.ServiceInfo si : pi.services) {
                if (!si.exported) continue;
                try {
                    startService(new Intent().setClassName(pkg, si.name));
                } catch (Throwable ignored) {
                    // background start not allowed, or the service needs more than a plain start
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void startRadio() {
        String pkg = prefs.app("media:radio");
        Intent i = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            openApp("media:radio", "Radio");
            return;
        }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            return;
        }
        // the radio keeps playing in the background; bring the dashboard back once it has tuned in
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(HomeActivity.this, HomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
        }, 2500);
    }

    private void diagnostics() {
        StringBuilder b = new StringBuilder();
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        b.append("OpenRath ").append(versionName()).append(", Android ").append(Build.VERSION.RELEASE)
                .append(", screen ").append(dm.widthPixels).append("x").append(dm.heightPixels).append("\n\n");
        String[] sig = prefs.lightSignal();
        b.append("Headlights: ").append(prefs.lights());
        if (sig != null) {
            b.append(", follows ").append(sig[0]).append(" (off=").append(sig[1]).append(", on=").append(sig[2])
                    .append(", now=").append(Lights.read(this, sig[0])).append(")");
        } else {
            b.append(", not set up; night mode now ").append(Lights.read(this, "uimode:night"));
        }
        b.append(" → car shows ").append(hub.lightsOn() ? "ON" : "OFF").append("\n");
        String radio = prefs.app("media:radio");
        b.append("Radio app: ").append(radio == null ? "none found" : radio + (getPackageManager()
                .getLaunchIntentForPackage(radio) == null ? " (not installed)" : "")).append("\n");
        String music = prefs.app("media:music");
        b.append("Music app: ").append(music == null ? "whatever is playing" : music).append("\n");
        b.append("Navigation app: ").append(prefs.app("dock:navi")).append("\n");
        b.append("Track info access: ").append(hub.mediaAccess ? "yes" : "no").append(", players seen: ")
                .append(hub.sessionPkgs.isEmpty() ? "none" : android.text.TextUtils.join(", ", hub.sessionPkgs))
                .append("\n");
        b.append("Audio playing: ").append(hub.audioActive ? "yes" : "no").append("\n");
        long age = hub.fixAge();
        b.append("GPS: ").append(hub.fix == null ? "no position yet" : age < 0 ? "last known position only"
                : "fix " + age + " s ago").append(", speed ").append(hub.speed())
                .append(Drive.fixAt == 0 ? ", trip service no fix" : ", trip service fix " + (System.currentTimeMillis() - Drive.fixAt) / 1000 + " s ago").append("\n");
        String[] ls = prefs.signal("left"), rs = prefs.signal("right");
        b.append("Indicators: ").append(ls == null && rs == null ? "not set up" : "left " + (ls == null ? "-" : ls[0] + "=" + Lights.read(this, ls[0]))
                + ", right " + (rs == null ? "-" : rs[0] + "=" + Lights.read(this, rs[0]))).append("\n");
        b.append("Sources: radio ").append(prefs.source("radio")).append(", music ").append(prefs.source("music"))
                .append(", navigation ").append(prefs.source("navi")).append(", internet ").append(hub.online ? "yes" : "no").append("\n");
        b.append("Player: ").append(Player.mode == null ? "idle" : Player.mode + (Player.playing ? " playing" : " stopped")
                + (Player.status.isEmpty() ? "" : " (" + Player.status + ")")).append("\n");
        b.append("Stereo: ").append(Stereo.name(this)).append(", using ").append(hub.unit.name()).append("\n");
        if (hub.xt != null) b.append("Topway radio service: ").append(hub.xt.ready() ? "connected" + (hub.xt.registered ? ", listening" : "")
                + (hub.xt.freqText.isEmpty() ? "" : ", FM " + hub.xt.freqText) : "not connected " + hub.xt.error).append("\n");
        b.append("FM commands: start ").append(prefs.fmCmd("play").isEmpty() ? "not found yet" : Fm.label(prefs.fmCmd("play")))
                .append(", stop ").append(prefs.fmCmd("stop").isEmpty() ? "not found yet" : Fm.label(prefs.fmCmd("stop")))
                .append(", callbacks ").append(hub.xt == null ? "-" : hub.xt.callbacks.size()).append("\n");
        if (hub.xt != null && !hub.xt.heard.isEmpty()) b.append("Radio service said: ")
                .append(android.text.TextUtils.join("  ", hub.xt.heard.subList(Math.max(0, hub.xt.heard.size() - 8), hub.xt.heard.size()))).append("\n");
        if (false) {
            b.append("Radio service commands: ").append(android.text.TextUtils.join(" ", hub.xt.methods)).append("\n");
            if (!hub.xt.heard.isEmpty()) b.append("Radio service said: ")
                    .append(android.text.TextUtils.join(" ", hub.xt.heard.subList(Math.max(0, hub.xt.heard.size() - 6), hub.xt.heard.size()))).append("\n");
        }
        b.append("Phone: ").append(hub.btConnected ? "connected" : "not connected").append(" (radio service says ")
                .append(hub.xt == null ? "-" : String.valueOf(hub.btRaw)).append(", last Bluetooth message ")
                .append(hub.xt == null || hub.xt.btEvent.isEmpty() ? "none" : hub.xt.btEvent).append(", Android Bluetooth ")
                .append(Hub.androidBt() ? "connected" : "not connected").append(")\n");
        b.append("Lights, as the unit's service sees them: ");
        try {
            b.append(hub.xt == null || !hub.xt.ready() ? "service not connected" : hub.xt.lightGetters().toString());
            android.content.ContentResolver cr = getContentResolver();
            for (String k : new String[]{"day_night_mode", "nightMode", "night_mode", "screen_brightness"}) {
                String v = android.provider.Settings.System.getString(cr, k);
                if (v != null) b.append(", ").append(k).append("=").append(v);
            }
        } catch (Throwable e) {
            b.append(e.getClass().getSimpleName());
        }
        b.append("\n");
        b.append("Last call: ").append(hub.lastCall == null ? "none seen yet" : hub.lastCall[1] + " " + hub.lastCall[0] + " " + hub.lastCall[2])
                .append(hub.xt == null ? "" : ", last Bluetooth text \"" + hub.xt.btText + "\"")
                .append(Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED ? "" : ", call log not allowed").append("\n");
        b.append("Topway MCU link: ").append(!Tw.available() ? "not on this unit"
                : hub.fm == null ? "present" : hub.fm.seen ? "FM " + hub.fm.frequency() + (hub.fm.on ? " (playing)" : "")
                + (hub.fm.name.isEmpty() ? "" : " " + hub.fm.name) : "present, no answer from the tuner yet").append("\n");
        b.append("Trip: today ").append(String.format(java.util.Locale.US, "%.1f km, %d min", Drive.todayKm, Drive.todaySec / 60))
                .append(", speed limit ").append(Drive.limit > 0 ? Drive.limit + " km/h" : "not known here")
                .append(Drive.cameraM >= 0 ? ", camera " + Drive.cameraM + " m" : "")
                .append(Drive.alertError.isEmpty() ? "" : " (" + Drive.alertError + ")").append("\n");
        if (Crash.last() != null) b.append("Last problem: ").append(Crash.last()).append("\n");
        b.append("Weather: ").append(hub.temp).append(" ").append(hub.weather).append(" ").append(hub.place);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Diagnostics").setMessage(b.toString()).setPositiveButton("OK", null).show();
    }

    String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** Once: ask for notification access, which is what lets the boxes see the track and play state. */
    private void askForTrackInfo() {
        android.content.SharedPreferences sp = getSharedPreferences("once", MODE_PRIVATE);
        if (hub.mediaAccess || sp.getBoolean("askedTrackInfo", false)) return;
        sp.edit().putBoolean("askedTrackInfo", true).apply();
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Show what's playing")
                .setMessage("To show the station, the track and the right play or pause button, OpenRath needs "
                        + "notification access. On the next screen, turn on OpenRath.")
                .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                        } catch (Exception e) {
                            Toast.makeText(HomeActivity.this, "This unit has no notification access screen", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Not now", null).show();
    }

    @Override
    public void editSlot(int slot) {
        settings(slot);
    }

    /** Long-press on a box: what it shows, and the way to all settings. */
    private void settings(final int slot) {
        if (Valet.on(prefs) && !ownerUnlocked) {
            Valet.unlock(this, prefs, new Runnable() {
                @Override
                public void run() {
                    ownerUnlocked = true;
                    settings(slot);
                }
            });
            return;
        }
        if (slot < 0) {
            openSettings();
            return;
        }
        String cur = prefs.slot(slot);
        String[] items = new String[Prefs.CONTENTS.length + 2];
        items[0] = "      Choose a widget from another app…";
        for (int i = 0; i < Prefs.CONTENTS.length; i++) {
            items[i + 1] = (Prefs.CONTENTS[i].equals(cur) ? "✓  " : "      ") + "Show " + Prefs.CONTENT_NAMES[i].toLowerCase() + " here";
        }
        items[items.length - 1] = "      All settings…";
        items = java.util.Arrays.copyOf(items, items.length + 1);
        items[items.length - 1] = "      Move boxes (drag and drop)…";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(slotName(slot))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == Prefs.CONTENTS.length + 2) startMoving();
                        else if (which == Prefs.CONTENTS.length + 1) openSettings();
                        else if (which == 0) widgetHere(slot);
                        else {
                            String c = Prefs.CONTENTS[which - 1];
                            prefs.setSlot(slot, c);
                            build();
                            // a widget box with nothing in it yet: go straight to the list of widgets
                            if (c.startsWith("widget") && prefs.num("widgetId:" + c, -1) < 0) pickWidget(c);
                        }
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /** Put a widget box here (reusing it if this box already is one) and open the list of widgets. */
    private void widgetHere(int slot) {
        if (!Features.on(prefs, "widgets")) Features.set(prefs, "widgets", true);
        String cur = prefs.slot(slot);
        String c = cur.startsWith("widget") ? cur : Prefs.WIDGET1;
        if (!cur.startsWith("widget")) {
            // use a widget content no other box on this page shows, so nothing else moves
            boolean oneUsed = false;
            for (int i = 0; i < Prefs.SLOTS; i++) if (Prefs.WIDGET1.equals(prefs.slot(i))) oneUsed = true;
            c = oneUsed ? Prefs.WIDGET2 : Prefs.WIDGET1;
            prefs.setSlot(slot, c);
            build();
        }
        pickWidget(c);
    }

    private String slotName(int slot) {
        boolean left = (slot < 3) != prefs.swapped();
        String side = left ? "left" : "right";
        if (slot == 0) return "Big box, " + side;
        if (slot == 1) return "Middle box, " + side;
        if (slot == 2) return "Bottom box, " + side;
        if (slot == 3) return "Big box, " + side;
        return "Bottom box, " + side;
    }

    private static String contentName(String c) {
        int i = java.util.Arrays.asList(Prefs.CONTENTS).indexOf(c);
        return i < 0 ? c : Prefs.CONTENT_NAMES[i];
    }

    SettingsView settingsView;

    /** The owner entered the valet PIN: Settings and box menus open until Settings is closed. */
    boolean ownerUnlocked;

    void openSettings() {
        if (settingsView != null) return;
        if (Valet.on(prefs) && !ownerUnlocked) {
            Valet.unlock(this, prefs, new Runnable() {
                @Override
                public void run() {
                    ownerUnlocked = true;
                    openSettings();
                }
            });
            return;
        }
        Hindi.load(this, prefs);
        settingsView = new SettingsView(this, new SettingsView.Source() {
            @Override
            public java.util.List<SettingsView.Row> rows() {
                return settingsRows();
            }

            @Override
            public View page(String group) {
                if ("Layout".equals(group)) {
                    return new LayoutGallery(HomeActivity.this, Layouts.current(prefs), new LayoutGallery.Picked() {
                        @Override
                        public void picked(String id) {
                            prefs.put("layout", id);
                            build();
                            if (settingsView != null) settingsView.refresh();
                        }
                    });
                }
                if (!"Themes".equals(group)) return null;
                return new ThemeGallery(HomeActivity.this, Theme.available(HomeActivity.this), Theme.id(),
                        Theme.paintAccent, Theme.paintEdge, new ThemeGallery.Picked() {
                    @Override
                    public void picked(Theme.Pack p) {
                        prefs.put("themePack", p.id);
                        restyle();   // also reloads a folder theme that was edited
                        if (!Theme.error.isEmpty())
                            Toast.makeText(HomeActivity.this, Theme.error, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void closeSettings() {
                HomeActivity.this.closeSettings();
            }
        });
        root.addView(settingsView, new android.widget.FrameLayout.LayoutParams(-1, -1));
        settingsView.refresh();
    }

    void closeSettings() {
        if (settingsView == null) return;
        root.removeView(settingsView);
        settingsView = null;
        ownerUnlocked = false;
        hub.notifyIfChanged(true);
    }

    private static String appName(android.content.pm.PackageManager pm, String pkg) {
        if (pkg == null) return "not chosen";
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg + " (not installed)";
        }
    }

    /** The picture layout changed: redraw it, or rebuild the screen when it was switched. */
    void layoutChanged() {
        if (layoutView != null && layoutView.lay.id.equals(Layouts.current(prefs))) layoutView.rebuild();
        else build();
    }

    private void layoutRows(java.util.List<SettingsView.Row> r) {
        final String g = "Layout";
        final java.util.List<Layouts.Layout> all = Layouts.all(this);
        final Layouts.Layout cur = Layouts.get(this, Layouts.current(prefs));
        if (cur == null) return;
        final java.util.List<String> opts = Layouts.opts(cur);
        if (!opts.isEmpty()) {
            int shown = 0;
            for (String o : opts) if (Layouts.shown(prefs, cur.id, o)) shown++;
            r.add(new SettingsView.Row(g, "Show on this layout", shown + " of " + opts.size() + " parts", new Runnable() {
                @Override
                public void run() {
                    final String[] names = new String[opts.size()];
                    final boolean[] on = new boolean[opts.size()];
                    for (int i = 0; i < names.length; i++) {
                        String n = Layouts.OPT_NAMES.get(opts.get(i));
                        names[i] = n == null ? opts.get(i) : n;
                        on[i] = Layouts.shown(prefs, cur.id, opts.get(i));
                    }
                    new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Show on " + cur.name)
                            .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int i, boolean b) {
                                    on[i] = b;
                                }
                            })
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    for (int i = 0; i < on.length; i++) Layouts.setShown(prefs, cur.id, opts.get(i), on[i]);
                                    layoutChanged();
                                    if (settingsView != null) settingsView.refresh();
                                }
                            }).setNegativeButton("Cancel", null).show();
                }
            }));
        }
        final Layouts.Part stats = Layouts.part(cur, "stats");
        if (stats != null) {
            java.util.List<String> now = Layouts.statItems(prefs, cur.id, stats);
            StringBuilder v = new StringBuilder();
            for (String it : now) v.append(v.length() == 0 ? "" : ", ").append(Layouts.statName(it).toLowerCase(java.util.Locale.US));
            r.add(new SettingsView.Row(g, "Details in the list", v.length() == 0 ? "none" : v.toString(), new Runnable() {
                @Override
                public void run() {
                    final java.util.List<String> order = Layouts.statOrder(prefs, cur.id, stats);
                    final java.util.List<String> showing = Layouts.statItems(prefs, cur.id, stats);
                    final String[] names = new String[order.size()];
                    final boolean[] on = new boolean[order.size()];
                    for (int i = 0; i < names.length; i++) {
                        names[i] = Layouts.statName(order.get(i));
                        on[i] = showing.contains(order.get(i));
                    }
                    new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Details on " + cur.name)
                            .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int i, boolean b) {
                                    on[i] = b;
                                }
                            })
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    for (int i = 0; i < on.length; i++) Layouts.setStat(prefs, cur.id, stats, order.get(i), on[i]);
                                    layoutChanged();
                                    if (settingsView != null) settingsView.refresh();
                                }
                            }).setNegativeButton("Cancel", null).show();
                }
            }));
            r.add(new SettingsView.Row(g, "Order of the details", "tap one to put it first", new Runnable() {
                @Override
                public void run() {
                    final java.util.List<String> order = Layouts.statItems(prefs, cur.id, stats);
                    if (order.size() < 2) {
                        Toast.makeText(HomeActivity.this, "Show two or more details first", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String[] names = new String[order.size()];
                    for (int i = 0; i < names.length; i++) names[i] = (i + 1) + ".  " + Layouts.statName(order.get(i));
                    new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Put first")
                            .setItems(names, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    Layouts.toTop(prefs, cur.id, stats, order.get(w));
                                    layoutChanged();
                                }
                            }).setNegativeButton("Done", null).show();
                }
            }));
        }
        if (Layouts.part(cur, "user") != null) {
            String n = prefs.str("layUser", "");
            r.add(new SettingsView.Row(g, "Driver's name", n.isEmpty() ? (Profiles.current(prefs).isEmpty() ? "not set" : Profiles.current(prefs) + " (driver profile)") : n,
                    new Runnable() {
                        @Override
                        public void run() {
                            final android.widget.EditText e = new android.widget.EditText(HomeActivity.this);
                            e.setSingleLine(true);
                            e.setText(prefs.str("layUser", ""));
                            new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                                    .setTitle("Driver's name").setView(e)
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            prefs.put("layUser", e.getText().toString().trim());
                                            layoutChanged();
                                            if (settingsView != null) settingsView.refresh();
                                        }
                                    }).setNegativeButton("Cancel", null).show();
                        }
                    }));
        }
        if (cur.roadHorizon > 0) {
            r.add(new SettingsView.Row(g, "Moving road lines", Layouts.roadOn(prefs) ? "on, while driving" : "off", new Runnable() {
                @Override
                public void run() {
                    prefs.put("layRoad", Layouts.roadOn(prefs) ? "off" : "on");
                    layoutChanged();
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Phone mirroring button", Layouts.mirrorOn(prefs) ? "on the layout's buttons" : "off", new Runnable() {
            @Override
            public void run() {
                prefs.put("layMirror", Layouts.mirrorOn(prefs) ? "off" : "on");
                layoutChanged();
                if (Layouts.mirrorOn(prefs))
                    Toast.makeText(HomeActivity.this, "Tap the new button to choose your mirroring app", Toast.LENGTH_LONG).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Background movement", Layouts.animOn(prefs) ? "gentle, while driving" : "off", new Runnable() {
            @Override
            public void run() {
                prefs.put("layAnim", Layouts.animOn(prefs) ? "off" : "on");
                layoutChanged();
            }
        }));
        final String[] walls = {"layout", "mine", "cycle"};
        final String[] wallNames = {"the layout's own", "your picture", "take turns every hour"};
        int wi = Math.max(0, java.util.Arrays.asList(walls).indexOf(Layouts.wall(prefs)));
        r.add(new SettingsView.Row(g, "Background picture", wallNames[wi], new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(walls).indexOf(Layouts.wall(prefs));
                String next = walls[(i + 1) % walls.length];
                prefs.put("layWall", next);
                if (!"layout".equals(next) && Layouts.myWalls().isEmpty())
                    new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Add your pictures")
                            .setMessage("Copy pictures (JPG or PNG) to " + Layouts.wallDir().getPath()
                                    + " on the unit, with a USB stick or a file manager. Until then the layout's own picture shows.")
                            .setPositiveButton("OK", null).show();
                layoutChanged();
            }
        }));
        if (Layouts.part(cur, "favourites") != null) {
            r.add(new SettingsView.Row(g, "Clear the favourite apps", "tap a tile on the layout to choose, long-press to change", new Runnable() {
                @Override
                public void run() {
                    prefs.put("layApps", "");
                    layoutChanged();
                    Toast.makeText(HomeActivity.this, "Cleared. Tap a tile on the layout to choose its app.", Toast.LENGTH_LONG).show();
                }
            }));
        }
    }

    /** Everything on the settings screen, by group. */
    private java.util.List<SettingsView.Row> settingsRows() {
        final java.util.List<SettingsView.Row> r = new java.util.ArrayList<>();
        final android.content.pm.PackageManager pm = getPackageManager();

        // ---- Level
        levelRows(r);

        // ---- Dashboard
        String g = "Dashboard";
        for (int i = 0; i < Prefs.SLOTS; i++) {
            final int slot = i;
            r.add(new SettingsView.Row(g, slotName(i), contentName(prefs.slot(i)), new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle(slotName(slot) + " shows")
                            .setSingleChoiceItems(Prefs.CONTENT_NAMES, java.util.Arrays.asList(Prefs.CONTENTS).indexOf(prefs.slot(slot)),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int which) {
                                            prefs.setSlot(slot, Prefs.CONTENTS[which]);
                                            build();
                                            d.dismiss();
                                        }
                                    })
                            .setNegativeButton("Cancel", null).show();
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Move boxes", "drag and drop on the dashboard", new Runnable() {
            @Override
            public void run() {
                startMoving();
            }
        }));
        final int[] sizes = {80, 90, 100, 110, 120, 130};
        r.add(new SettingsView.Row(g, "Size of text and boxes", prefs.num("uiScale", 100) + "%", new Runnable() {
            @Override
            public void run() {
                String[] names = new String[sizes.length];
                for (int i = 0; i < sizes.length; i++) names[i] = sizes[i] + "%" + (sizes[i] == 100 ? " (as designed)" : "");
                new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Size of text and boxes")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                prefs.put("uiScale", String.valueOf(sizes[w]));
                                restyle();
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));
        final String[] turns = {"auto", "landscape", "portrait"};
        r.add(new SettingsView.Row(g, "Screen way up", prefs.str("turn", "auto") + (Ui.portrait(dash.getWidth(), dash.getHeight()) ? ", boxes stacked" : ""), new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(turns).indexOf(prefs.str("turn", "auto"));
                prefs.put("turn", turns[(i + 1) % turns.length]);
                turn();
                restyle();
            }
        }));
        if (Features.on(prefs, "news")) {
            r.add(new SettingsView.Row(g, "News box shows", News.topicName(prefs.str("newsTopic", "top")), new Runnable() {
                @Override
                public void run() {
                    int i = java.util.Arrays.asList(News.TOPICS).indexOf(prefs.str("newsTopic", "top"));
                    prefs.put("newsTopic", News.TOPICS[(i + 1) % News.TOPICS.length]);
                    News.at = 0;
                    News.refresh(HomeActivity.this, prefs, true);
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Columns", prefs.swapped() ? "map on the left" : "car on the left", new Runnable() {
            @Override
            public void run() {
                prefs.setSwapped(!prefs.swapped());
                build();
            }
        }));
        r.add(new SettingsView.Row(g, "Welcome name", prefs.welcome().isEmpty() ? "hidden" : prefs.welcome(), new Runnable() {
            @Override
            public void run() {
                editWelcome();
            }
        }));
        final String[] dimModes = {"off", "sunset", "lights"};
        final String[] dimNames = {"off", "after sunset", "with the headlights"};
        r.add(new SettingsView.Row(g, "Night dimming", dimNames[Math.max(0, java.util.Arrays.asList(dimModes).indexOf(prefs.str("dim", "off")))],
                new Runnable() {
                    @Override
                    public void run() {
                        int i = java.util.Arrays.asList(dimModes).indexOf(prefs.str("dim", "off"));
                        prefs.put("dim", dimModes[(i + 1) % dimModes.length]);
                        hub.notifyIfChanged(true);
                    }
                }));
        r.add(new SettingsView.Row(g, "How dark at night", prefs.num("dimLevel", 40) + "%", new Runnable() {
            @Override
            public void run() {
                int[] steps = {20, 30, 40, 50, 60};
                int i = 0;
                while (i < steps.length && steps[i] != prefs.num("dimLevel", 40)) i++;
                prefs.put("dimLevel", String.valueOf(steps[(i + 1) % steps.length]));
                hub.notifyIfChanged(true);
            }
        }));
        r.add(new SettingsView.Row(g, "Reset the layout", "boxes and apps back to the start", new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage("Put the boxes and chosen apps back as they were at the start? Your name, sources and learnt car signals stay.")
                        .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                prefs.reset();
                                build();
                            }
                        })
                        .setNegativeButton("Cancel", null).show();
            }
        }));

        // ---- Screen layout: the boxes or a picture layout, and each layout's parts
        layoutRows(r);

        // ---- Themes: the gallery, then ways to make and share themes
        g = "Themes";
        r.add(new SettingsView.Row(g, "", null, null));
        r.add(new SettingsView.Row(g, "Accent colour (car paint)", Theme.json().has("colours") && Theme.json().optJSONObject("colours").has("accent") ? "set by " + Theme.name : prefs.str("theme", Ui.THEMES[0]), new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Match the car's colour")
                        .setSingleChoiceItems(Ui.THEMES, Math.max(0, java.util.Arrays.asList(Ui.THEMES).indexOf(prefs.str("theme", Ui.THEMES[0]))),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        prefs.put("theme", Ui.THEMES[w]);
                                        d.dismiss();
                                        restyle();
                                    }
                                }).setNegativeButton("Cancel", null).show();
            }
        }));
        if (Features.on(prefs, "bootArt")) r.add(new SettingsView.Row(g, "Boot logo and animation", "make them with your car's badge and name", new Runnable() {
            @Override
            public void run() {
                BootArt.menu(HomeActivity.this);
            }
        }));
        r.add(new SettingsView.Row(g, "Make your own theme", "copies " + Theme.name + " to OpenRath/themes to edit", new Runnable() {
            @Override
            public void run() {
                try {
                    String folder = "my-theme";
                    for (int i = 2; new java.io.File(Theme.root(), folder).exists(); i++) folder = "my-theme-" + i;
                    java.io.File d = Theme.starter(HomeActivity.this, folder);
                    new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Theme created")
                            .setMessage("Edit the files in " + d.getPath() + " (on a PC over USB, or with a file manager). "
                                    + "HOW-TO.txt in that folder explains each file. It is now in the list above; tap it to use it, and again after each edit.")
                            .setPositiveButton("OK", null).show();
                } catch (Exception e) {
                    Toast.makeText(HomeActivity.this, "Couldn't create it: " + e.getMessage() + " (allow storage access?)", Toast.LENGTH_LONG).show();
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Import a theme from Download", "a theme .zip someone shared", new Runnable() {
            @Override
            public void run() {
                final java.util.List<java.io.File> zips = Theme.zipsInDownload();
                if (zips.isEmpty()) {
                    Toast.makeText(HomeActivity.this, "No .zip files in Download", Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[zips.size()];
                for (int i = 0; i < names.length; i++) names[i] = zips.get(i).getName();
                new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Import which theme?")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    String folder = Theme.importZip(zips.get(w));
                                    prefs.put("themePack", folder);
                                    restyle();
                                    Toast.makeText(HomeActivity.this, "Theme \"" + folder + "\" is on", Toast.LENGTH_LONG).show();
                                } catch (Exception e) {
                                    Toast.makeText(HomeActivity.this, "Not a theme: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));

        // ---- Car
        g = "Car";
        r.add(new SettingsView.Row(g, "Brand, model and fuel", Car.summary(prefs), new Runnable() {
            @Override
            public void run() {
                closeSettings();
                Car.setup(HomeActivity.this);
            }
        }));
        if (Features.on(prefs, "view360")) {
            r.add(new SettingsView.Row(g, "360 view of your car", Car3D.SHAPE_NAMES[java.util.Arrays.asList(Car3D.SHAPES).indexOf(Car3D.shape(prefs))]
                    + (Car3D.plate(prefs).isEmpty() ? "" : ", " + Car3D.plate(prefs)), new Runnable() {
                @Override
                public void run() {
                    View360.show(HomeActivity.this);
                }
            }));
            String cam = View360.camApp(this, prefs);
            r.add(new SettingsView.Row(g, "Stereo's 360 camera app", cam == null ? "none found (pick one)" : appLabel(cam), new Runnable() {
                @Override
                public void run() {
                    AppPicker.show(HomeActivity.this, "The 360 camera app", new AppPicker.Picked() {
                        @Override
                        public void picked(String pkg) {
                            prefs.setApp("cam360", pkg);
                            if (settingsView != null) settingsView.refresh();
                        }
                    });
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Car name", Car.name(prefs), new Runnable() {
            @Override
            public void run() {
                final android.widget.EditText e = new android.widget.EditText(HomeActivity.this);
                e.setSingleLine(true);
                e.setText(Car.name(prefs));
                e.setSelectAllOnFocus(true);
                new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Name beside the logo (e.g. SWIFT, BALENO)")
                        .setView(e)
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                prefs.put("carName", e.getText().toString().trim());
                                restyle();
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));
        String[] picNames = {"Car picture, lights off", "Car picture, lights on", "Brand logo"};
        for (int i = 0; i < PICTURES.length; i++) {
            final int n = i;
            r.add(new SettingsView.Row(g, picNames[i], Ui.customFile(this, PICTURES[i]).exists() ? "your picture" : "built-in", new Runnable() {
                @Override
                public void run() {
                    pickPicture(n);
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Use the built-in pictures again", null, new Runnable() {
            @Override
            public void run() {
                for (String n : PICTURES) Ui.customFile(HomeActivity.this, n).delete();
                restyle();
            }
        }));
        r.add(new SettingsView.Row(g, "Tip", "a PNG with a transparent background looks best; the car facing you, about 820 x 480", new Runnable() {
            @Override
            public void run() {
            }
        }));

        // ---- Dock
        g = "Buttons bar";
        final String[] places = {"left", "right", "top", "bottom"};
        r.add(new SettingsView.Row(g, "Position", prefs.str("dockAt", "left"), new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(places).indexOf(prefs.str("dockAt", "left"));
                prefs.put("dockAt", places[(i + 1) % places.length]);
                restyle();
            }
        }));
        for (int i = 0; i <= Prefs.DOCK.length; i++) {
            final String key = i < Prefs.DOCK.length ? Prefs.DOCK[i] : "settings";
            final String label = i < Prefs.DOCK.length ? Prefs.DOCK_NAMES[i] : "Settings";
            final int code = java.util.Arrays.asList(ALL_PICTURES).indexOf("dock_" + key);
            r.add(new SettingsView.Row(g, "Icon: " + label, Ui.customFile(this, "dock_" + key).exists() ? "your picture" : "built-in", new Runnable() {
                @Override
                public void run() {
                    pickPicture(code);
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Use the built-in icons again", null, new Runnable() {
            @Override
            public void run() {
                for (String n : ALL_PICTURES) if (n.startsWith("dock_")) Ui.customFile(HomeActivity.this, n).delete();
                restyle();
            }
        }));
        r.add(new SettingsView.Row(g, "What each button opens", "long-press a button on the bar to choose its app", new Runnable() {
            @Override
            public void run() {
            }
        }));

        // ---- Radio and music
        g = "Radio and music";
        final String[] radioSrc = {"auto: online with internet, else FM", "online stations", "FM radio"};
        final String[] musicSrc = {"auto: music app with internet, else files", "music app", "music files"};
        r.add(new SettingsView.Row(g, "Radio plays", radioSrc[Math.max(0, java.util.Arrays.asList(Prefs.RADIO_SRC).indexOf(prefs.source("radio")))],
                new Runnable() {
                    @Override
                    public void run() {
                        cycle("radio", Prefs.RADIO_SRC);
                    }
                }));
        r.add(new SettingsView.Row(g, "Online radio station", Player.stations.isEmpty() || Player.station >= Player.stations.size() ? null : Player.stations.get(Player.station).name, new Runnable() {
            @Override
            public void run() {
                pickTrack(true);
            }
        }));
        r.add(new SettingsView.Row(g, "FM radio app", appName(pm, prefs.app("media:radio")), pick("media:radio", "Radio")));
        String fp = prefs.fmCmd("play"), fs0 = prefs.fmCmd("stop");
        if (topway()) r.add(new SettingsView.Row(g, "FM radio test (find how this unit starts FM)",
                fp.isEmpty() ? "not done yet" : "start: " + Fm.label(fp) + (fs0.isEmpty() ? "" : ", stop: " + Fm.label(fs0)), new Runnable() {
            @Override
            public void run() {
                if (hub.xt == null || !hub.xt.ready()) {
                    Toast.makeText(HomeActivity.this, "Topway's radio service isn't connected; only the MCU link can be tried", Toast.LENGTH_LONG).show();
                }
                Fm.test(HomeActivity.this, prefs, hub.xt != null && hub.xt.ready() ? hub.xt : null, hub.fm, false);
            }
        }));
        if (topway()) {
            String ft = prefs.str("fmTuner", "");
            r.add(new SettingsView.Row(g, "Built-in FM tuner (without the stock radio app)",
                    "own".equals(ft) ? "on: OpenRath plays FM itself" : "no".equals(ft) ? "didn't work on this unit; tap to try again" : "not tried yet: tap to try",
                    new Runnable() {
                        @Override
                        public void run() {
                            tryTuner();
                        }
                    }));
        }
        if (topway()) r.add(new SettingsView.Row(g, "FM frequency to start on", prefs.fmFreq() + " MHz", new Runnable() {
            @Override
            public void run() {
                final android.widget.EditText e = new android.widget.EditText(HomeActivity.this);
                e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
                e.setText(prefs.fmFreq());
                new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("FM frequency (MHz)")
                        .setView(e)
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    float f = Float.parseFloat(e.getText().toString().trim());
                                    if (f >= 87.5f && f <= 108f) prefs.setFmFreq(Fm.freqOf(f));
                                } catch (Exception ignored) {
                                }
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Music plays", musicSrc[Math.max(0, java.util.Arrays.asList(Prefs.MUSIC_SRC).indexOf(prefs.source("music")))],
                new Runnable() {
                    @Override
                    public void run() {
                        cycle("music", Prefs.MUSIC_SRC);
                    }
                }));
        r.add(new SettingsView.Row(g, "Choose a song", null, new Runnable() {
            @Override
            public void run() {
                pickTrack(false);
            }
        }));
        r.add(new SettingsView.Row(g, "Look for music files again", "after plugging in a USB stick", new Runnable() {
            @Override
            public void run() {
                Player.songs.clear();
                Toast.makeText(HomeActivity.this, "Music files are found again on the next play", Toast.LENGTH_LONG).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Music app", appName(pm, prefs.app("media:music")), pick("media:music", "Music")));
        r.add(new SettingsView.Row(g, "Track and station info", hub.mediaAccess ? "allowed" : "not allowed", new Runnable() {
            @Override
            public void run() {
                try {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                    Toast.makeText(HomeActivity.this, "Turn on OpenRath in this list", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(HomeActivity.this, "This unit has no notification access screen", Toast.LENGTH_LONG).show();
                }
            }
        }));

        // ---- Map and navigation
        g = "Map and navigation";
        if (Features.on(prefs, "offlineMap")) r.add(new SettingsView.Row(g, "Map kept for no internet", "about " + Tiles.sizeMb()
                + " MB of places you've seen; tap to clear", new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setMessage("Delete the kept map? Places will show again once seen with internet.")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Tiles.clear();
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));
        String offNav = OfflineNav.app(this, prefs);
        r.add(new SettingsView.Row(g, "Guide without internet in", offNav == null ? "no offline map app: install Organic Maps" : OfflineNav.label(this, offNav),
                pick("offlineNav", "Offline map app")));
        final java.util.List<String> engines = Speak.engines(this);
        r.add(new SettingsView.Row(g, "Spoken voice", engines.contains(Speak.GOOGLE_TTS) ? "Speech Services by Google (tap to pick it, or get the Hindi voice)"
                : engines.isEmpty() ? "none installed: tap to get Speech Services by Google" : "another engine (tap to get Google's, which speaks Hindi)", new Runnable() {
            @Override
            public void run() {
                Speak.getVoice(HomeActivity.this);
            }
        }));
        if (Features.on(prefs, "offlineVoice")) r.add(new SettingsView.Row(g, "Voice without internet", "get Google's offline speech pack (English and Hindi)", new Runnable() {
            @Override
            public void run() {
                Intent[] tries = {
                        new Intent().setClassName("com.google.android.googlequicksearchbox",
                                "com.google.android.voicesearch.greco3.languagepack.InstallActivity"),
                        new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
                        new Intent(android.provider.Settings.ACTION_SETTINGS)};
                for (Intent t : tries) {
                    try {
                        startActivity(t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        Toast.makeText(HomeActivity.this, "Download English (India) and Hindi, on Wi-Fi", Toast.LENGTH_LONG).show();
                        return;
                    } catch (Exception ignored) {
                    }
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Guide routes in", Prefs.sourceName(prefs.source("navi")), new Runnable() {
            @Override
            public void run() {
                cycle("navi", Prefs.NAV_SRC);
            }
        }));
        r.add(new SettingsView.Row(g, "Navigation button opens", appName(pm, prefs.app("dock:navi")), pick("dock:navi", "Navigation")));
        r.add(new SettingsView.Row(g, "Save the parking spot here", Drive.parkAt == 0 ? "none saved"
                : "saved " + Cards.TripCard.ago(Drive.parkAt), new Runnable() {
            @Override
            public void run() {
                parkHere();
            }
        }));

        // ---- Driving
        g = "Driving";
        r.add(new SettingsView.Row(g, "Speed limit and camera alerts", prefs.alertsOn() ? "beep and voice" : "silent", new Runnable() {
            @Override
            public void run() {
                prefs.setAlertsOn(!prefs.alertsOn());
            }
        }));
        r.add(new SettingsView.Row(g, "Warn above a speed anywhere", prefs.warnSpeed() == 0 ? "off" : prefs.warnSpeed() + " km/h", new Runnable() {
            @Override
            public void run() {
                int[] steps = {0, 80, 90, 100, 110, 120};
                int i = 0;
                while (i < steps.length && steps[i] != prefs.warnSpeed()) i++;
                prefs.setWarnSpeed(steps[(i + 1) % steps.length]);
            }
        }));
        r.add(new SettingsView.Row(g, "Start today's trip again", String.format(java.util.Locale.US, "today %.1f km", Drive.todayKm), new Runnable() {
            @Override
            public void run() {
                Drive.resetToday(HomeActivity.this);
                hub.notifyIfChanged(true);
            }
        }));
        Fuel.Stats fs = Fuel.stats(prefs);
        r.add(new SettingsView.Row(g, "Fuel log", fs.fills == 0 ? "no fill-ups yet" : fs.fills + " fill-ups"
                + (fs.avgKmpl > 0 ? ", " + Fuel.fmt(fs.avgKmpl) + " km/l" : ""), new Runnable() {
            @Override
            public void run() {
                fuel();
            }
        }));
        r.add(new SettingsView.Row(g, "Add a fill-up", null, new Runnable() {
            @Override
            public void run() {
                Fuel.add(HomeActivity.this, prefs, new Runnable() {
                    @Override
                    public void run() {
                        hub.notifyIfChanged(true);
                    }
                });
            }
        }));

        r.add(new SettingsView.Row(g, "Trip history", "weekly and monthly distance, time and costs", new Runnable() {
            @Override
            public void run() {
                Care.history(HomeActivity.this, prefs);
            }
        }));
        r.add(new SettingsView.Row(g, "Driving score today", Drive.score() + " / 100", new Runnable() {
            @Override
            public void run() {
                Care.history(HomeActivity.this, prefs);
            }
        }));
        r.add(new SettingsView.Row(g, "Add a toll payment", Cards.tollsThisMonth(prefs).replace("  ·  ", ""), new Runnable() {
            @Override
            public void run() {
                Care.addToll(HomeActivity.this, prefs, new Runnable() {
                    @Override
                    public void run() {
                        hub.notifyIfChanged(true);
                    }
                });
            }
        }));
        r.add(new SettingsView.Row(g, "Toll estimate per plaza", "₹" + prefs.num("tollRate", 100) + " (for route estimates)", new Runnable() {
            @Override
            public void run() {
                Care.number(HomeActivity.this, "Typical car toll per plaza (₹)", String.valueOf(prefs.num("tollRate", 100)), new Care.Num() {
                    @Override
                    public void got(double v) {
                        prefs.put("tollRate", String.valueOf(Math.round(v)));
                    }
                });
            }
        }));

        // ---- Car care
        g = "Car care";
        final Runnable careChanged = new Runnable() {
            @Override
            public void run() {
                hub.notifyIfChanged(true);
                if (settingsView != null) settingsView.refresh();
            }
        };
        r.add(new SettingsView.Row(g, "Odometer", Care.odo(prefs) + " km (tap to correct)", new Runnable() {
            @Override
            public void run() {
                Care.editOdo(HomeActivity.this, prefs, careChanged);
            }
        }));
        for (int i = 0; i < Care.ITEMS.length; i++) {
            final int n = i;
            r.add(new SettingsView.Row(g, Care.NAMES[i], Care.describe(prefs, i), new Runnable() {
                @Override
                public void run() {
                    Care.edit(HomeActivity.this, prefs, n, careChanged);
                }
            }));
        }

        // ---- Phone
        g = "Phone";
        for (int i = 0; i < 3; i++) {
            final int n = i;
            String[] f = Cards.PhoneCard.fav(prefs, i);
            r.add(new SettingsView.Row(g, "Favourite " + (i + 1), f == null ? "not set" : f[0] + "  " + f[1], new Runnable() {
                @Override
                public void run() {
                    editFavourite(n);
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Phone app", appName(pm, prefs.app("dock:phone")), pick("dock:phone", "Phone")));

        // ---- Car signals
        g = "Car signals";
        String l = prefs.lights();
        r.add(new SettingsView.Row(g, "Car picture headlights", "auto".equals(l) ? "follow the car (else sunset to sunrise)"
                : "times".equals(l) ? "between set times" : "on".equals(l) ? "always on" : "always off",
                new Runnable() {
                    @Override
                    public void run() {
                        String cur = prefs.lights();
                        prefs.setLights("auto".equals(cur) ? "times" : "times".equals(cur) ? "on" : "on".equals(cur) ? "off" : "auto");
                        hub.notifyIfChanged(true);
                    }
                }));
        if ("times".equals(l)) {
            r.add(new SettingsView.Row(g, "Headlight times", Lights.hhmm(prefs.num("lightsFrom", 18 * 60 + 30)) + " to "
                    + Lights.hhmm(prefs.num("lightsTo", 6 * 60 + 30)), new Runnable() {
                @Override
                public void run() {
                    final int from = prefs.num("lightsFrom", 18 * 60 + 30), to = prefs.num("lightsTo", 6 * 60 + 30);
                    new android.app.TimePickerDialog(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert, new android.app.TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(android.widget.TimePicker v, int h, int m) {
                            prefs.put("lightsFrom", String.valueOf(h * 60 + m));
                            new android.app.TimePickerDialog(HomeActivity.this, android.R.style.Theme_Material_Dialog_Alert, new android.app.TimePickerDialog.OnTimeSetListener() {
                                @Override
                                public void onTimeSet(android.widget.TimePicker v2, int h2, int m2) {
                                    prefs.put("lightsTo", String.valueOf(h2 * 60 + m2));
                                    hub.notifyIfChanged(true);
                                    openSettings();
                                }
                            }, to / 60, to % 60, true).show();
                            Toast.makeText(HomeActivity.this, "Now the time they go off", Toast.LENGTH_SHORT).show();
                        }
                    }, from / 60, from % 60, true).show();
                    Toast.makeText(HomeActivity.this, "The time the headlights go on", Toast.LENGTH_SHORT).show();
                }
            }));
        }
        final Lights.Done relisten = new Lights.Done() {
            @Override
            public void done() {
                hub.stop();
                hub.start();
                hub.notifyIfChanged(true);
            }
        };
        final String[] revModes = {"lines", "app", "off"};
        final String[] revNames = {"show parking guide lines", "open the camera app", "do nothing"};
        r.add(new SettingsView.Row(g, "In reverse", revNames[Math.max(0, java.util.Arrays.asList(revModes).indexOf(prefs.str("reverse", "lines")))]
                + (hub.xt != null && hub.xt.has("getReverseStatus") ? "" : " (needs Topway's service)"), new Runnable() {
            @Override
            public void run() {
                int i = java.util.Arrays.asList(revModes).indexOf(prefs.str("reverse", "lines"));
                prefs.put("reverse", revModes[(i + 1) % revModes.length]);
            }
        }));
        r.add(new SettingsView.Row(g, "Rear camera app", appName(pm, prefs.app("reverse:cam")), pick("reverse:cam", "Rear camera")));
        r.add(new SettingsView.Row(g, "Preview the reverse guide lines", null, new Runnable() {
            @Override
            public void run() {
                final View v = new ReverseView(HomeActivity.this);
                v.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View x) {
                        root.removeView(v);
                    }
                });
                root.addView(v, new android.widget.FrameLayout.LayoutParams(-1, -1));
                Toast.makeText(HomeActivity.this, "Tap to close", Toast.LENGTH_SHORT).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Headlight setup", prefs.lightSignal() == null ? "not set up" : "set up", new Runnable() {
            @Override
            public void run() {
                Lights.deep = false;
                Lights.setup(HomeActivity.this, prefs, relisten);
            }
        }));
        r.add(new SettingsView.Row(g, "Headlight setup, deep search", "slower; also reads the system apps", new Runnable() {
            @Override
            public void run() {
                Lights.deep = true;
                setupLights();
            }
        }));
        r.add(new SettingsView.Row(g, "Indicator setup", prefs.signal("left") == null && prefs.signal("right") == null ? "not set up" : "set up",
                new Runnable() {
                    @Override
                    public void run() {
                        Lights.deep = false;
                        Lights.indicatorSetup(HomeActivity.this, prefs, relisten);
                    }
                }));
        for (final String[] w : new String[][]{{"door", "Door setup"}, {"belt", "Seat-belt setup"}}) {
            r.add(new SettingsView.Row(g, w[1], prefs.signal(w[0]) == null ? "not set up; for the voice reminders" : "set up", new Runnable() {
                @Override
                public void run() {
                    Lights.deep = false;
                    Lights.learn(HomeActivity.this, prefs, w[0], relisten);
                }
            }));
        }

        // ---- Help
        g = "Help";
        r.add(new SettingsView.Row(g, "Diagnostics", "send a photo if something doesn't work", new Runnable() {
            @Override
            public void run() {
                diagnostics();
            }
        }));
        if (topway()) r.add(new SettingsView.Row(g, "How the stock launcher does it", "the radio and light names inside the stock apps", new Runnable() {
            @Override
            public void run() {
                Peek.run(HomeActivity.this);
            }
        }));
        r.add(new SettingsView.Row(g, "Copy the stock launcher", "to Download and USB, to send for a closer look", new Runnable() {
            @Override
            public void run() {
                Peek.save(HomeActivity.this);
            }
        }));
        if (topway()) r.add(new SettingsView.Row(g, "Unit report", "how the stock radio and launcher work", new Runnable() {
            @Override
            public void run() {
                Probe.run(HomeActivity.this, prefs.app("media:radio"), hub.xt);
            }
        }));
        if (Crash.last() != null) {
            r.add(new SettingsView.Row(g, "Clear the crash note", "and retry skipped steps", new Runnable() {
                @Override
                public void run() {
                    Crash.forgive();
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Android settings", null, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }));
        r.add(new SettingsView.Row(g, "Version", "OpenRath " + versionName() + (Features.dev(prefs) ? ", developer mode" : ""), new Runnable() {
            @Override
            public void run() {
                versionTapped();
            }
        }));
        Extras.rows(this, r);
        if (!Features.dev(prefs)) {
            for (java.util.Iterator<SettingsView.Row> it = r.iterator(); it.hasNext(); ) {
                if (DEV_ROWS.contains(it.next().title)) it.remove();
            }
        }
        return r;
    }

    /** Fault-finding and test rows that only developer mode shows. */
    private static final java.util.Set<String> DEV_ROWS = new java.util.HashSet<>(java.util.Arrays.asList(
            "Diagnostics", "How the stock launcher does it", "Unit report", "Clear the crash note", "Crash reports", "Copy the stock launcher",
            "Headlight setup, deep search", "FM radio test (find how this unit starts FM)",
            "Built-in FM tuner (without the stock radio app)", "FM frequency to start on"));

    private void levelRows(java.util.List<SettingsView.Row> r) {
        final String g = "Level";
        int lv = Features.level(prefs);
        r.add(new SettingsView.Row(g, "OpenRath level", Features.LEVEL_NAMES[lv] + ": " + Features.LEVEL_ABOUT[lv], new Runnable() {
            @Override
            public void run() {
                askLevel(false);
            }
        }));
        r.add(new SettingsView.Row(g, "Every switch", "each level's features can still be turned on or off one by one, in their own section", new Runnable() {
            @Override
            public void run() {
                Toast.makeText(HomeActivity.this, "Look under \"Turn on or off\" at the top of each section", Toast.LENGTH_LONG).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Language", "hi".equals(Hindi.lang(prefs)) ? "हिन्दी (Settings screens)" : "English", new Runnable() {
            @Override
            public void run() {
                prefs.put("lang", "hi".equals(Hindi.lang(prefs)) ? "en" : "hi");
                Hindi.load(HomeActivity.this, prefs);
                if (settingsView != null) closeAndReopenSettings();
            }
        }));
        if (Features.dev(prefs)) {
            r.add(new SettingsView.Row(g, "Developer mode", "on: every switch, diagnostics and tests. Tap to turn off", new Runnable() {
                @Override
                public void run() {
                    prefs.put("devMode", "off");
                    devTaps = 0;
                    Toast.makeText(HomeActivity.this, "Developer mode is off", Toast.LENGTH_SHORT).show();
                    if (settingsView != null) settingsView.refresh();
                }
            }));
        }
    }

    private int devTaps;
    private long devTapAt;

    /** Once: no voice engine on the unit, so offer Google's from the Play Store. */
    private boolean askVoice() {
        if (!prefs.str("askedVoice", "").isEmpty() || !Speak.engines(this).isEmpty()) return false;
        prefs.put("askedVoice", "yes");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Get a spoken voice")
                .setMessage("This stereo has no text-to-speech voice, so OpenRath can't speak alerts, turns or the news. "
                        + "Get \"Speech Services by Google\" (free) from the Play Store? Later: Settings > Map and navigation > Spoken voice.")
                .setPositiveButton("Open the Play Store", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Speak.getVoice(HomeActivity.this);
                    }
                }).setNegativeButton("Not now", null).show();
        return true;
    }

    /** Settings again from scratch (a new language), without asking for the valet PIN again. */
    private void closeAndReopenSettings() {
        boolean unlocked = ownerUnlocked;
        closeSettings();
        ownerUnlocked = unlocked;
        openSettings();
    }

    /** Seven taps on the version turn developer mode on, as on Android itself. */
    private void versionTapped() {
        if (Features.dev(prefs)) {
            Toast.makeText(this, "Developer mode is already on", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        devTaps = now - devTapAt > 3000 ? 1 : devTaps + 1;
        devTapAt = now;
        if (devTaps >= 7) {
            prefs.put("devMode", "on");
            Toast.makeText(this, "Developer mode is on: every switch, diagnostics and tests are in Settings", Toast.LENGTH_LONG).show();
            if (settingsView != null) settingsView.refresh();
        } else if (devTaps >= 3) {
            Toast.makeText(this, (7 - devTaps) + " more taps for developer mode", Toast.LENGTH_SHORT).show();
        }
    }

    /** Simple, Full or Custom. At first start Simple is ticked; "Not now" keeps everything as it is. */
    void askLevel(final boolean first) {
        final int[] pick = {first ? Features.SIMPLE : Features.level(prefs)};
        String[] items = new String[Features.LEVELS.length];
        for (int i = 0; i < items.length; i++) items[i] = Features.LEVEL_NAMES[i] + "\n" + Features.LEVEL_ABOUT[i];
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(first ? "How much should OpenRath show?" : "OpenRath level")
                .setSingleChoiceItems(items, pick[0], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        pick[0] = w;
                    }
                })
                .setPositiveButton("Use this", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Features.setLevel(prefs, pick[0]);
                        Toast.makeText(HomeActivity.this, Features.LEVEL_NAMES[pick[0]] + ". Change it any time in Settings > OpenRath level",
                                Toast.LENGTH_LONG).show();
                        Extras.featuresChanged(HomeActivity.this);
                        if (settingsView != null) settingsView.refresh();
                    }
                })
                .setNegativeButton(first ? "Not now" : "Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (first) prefs.put("level", "custom");   // asked once; everything stays as it was
                    }
                }).show();
    }

    // ------------------------------------------------------------------ moving boxes

    private View moveBar;

    /** Drag-and-drop mode: drag a box onto another to swap them; Done (or back) ends it. */
    private void startMoving() {
        closeSettings();
        dash.setEditing(true);
        if (moveBar != null) return;
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(18), Ui.px(8), Ui.px(8), Ui.px(8));
        bar.setBackground(Ui.panel());
        TextView t = Ui.text(this, 16, Ui.SILVER, "sans-serif", Gravity.LEFT);
        t.setText("Drag a box onto another to swap them");
        bar.addView(t);
        TextView done = Cards.PhoneCard.pill(this, "Done", 0xFF2A2D32);
        done.setTextSize(0, 16 * Ui.k);
        done.setPadding(Ui.px(20), 0, Ui.px(20), 0);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, Ui.px(44));
        dp.leftMargin = Ui.px(18);
        bar.addView(done, dp);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMoving();
            }
        });
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.px(24);
        moveBar = bar;
        root.addView(bar, lp);
    }

    private void stopMoving() {
        dash.setEditing(false);
        if (moveBar != null) root.removeView(moveBar);
        moveBar = null;
    }

    // ------------------------------------------------------------------ the owner's own pictures

    private static final String[] PICTURES = {"car_off", "car_on", "logo"};
    /** Every picture the owner can replace; the request code is 61 + its index. */
    private static final String[] ALL_PICTURES = {"car_off", "car_on", "logo", "dock_navi", "dock_music", "dock_radio",
            "dock_bt", "dock_phone", "dock_apps", "dock_settings"};

    private void pickPicture(int which) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choose a picture"), 61 + which);
        } catch (Exception e) {
            Toast.makeText(this, "No app on the unit can pick pictures. Put a PNG in a theme pack instead (Make a theme to edit).", Toast.LENGTH_LONG).show();
        }
    }

    /** Scale the picked picture to a sensible size and keep it as custom/<name>.png. */
    private void savePicture(int code, android.net.Uri uri) {
        String name = ALL_PICTURES[code - 61];
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > 1600) sample *= 2;
            o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (b == null) throw new IllegalStateException("not a picture");
            java.io.File f = Ui.customFile(this, name);
            f.getParentFile().mkdirs();
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            // one picture for both, until a separate "lights on" picture is chosen
            if ("car_off".equals(name) && !Ui.customFile(this, "car_on").exists()) {
                java.io.FileOutputStream o2 = new java.io.FileOutputStream(Ui.customFile(this, "car_on_auto"));
                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o2);
                o2.close();
                Ui.customFile(this, "car_on_auto").renameTo(Ui.customFile(this, "car_on"));
            }
            restyle();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't use that picture: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Rebuild everything in the new look, keeping the settings screen open. */
    void restyle() {
        boolean open = settingsView != null;
        closeSettings();
        build();
        if (open) openSettings();
    }

    /** A wallpaper scaled to fill the screen without stretching. */
    static final class CenterCrop extends android.graphics.drawable.Drawable {
        private final android.graphics.drawable.BitmapDrawable b;

        CenterCrop(android.graphics.drawable.BitmapDrawable b) {
            this.b = b;
        }

        @Override
        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect r = getBounds();
            android.graphics.Bitmap bm = b.getBitmap();
            float sc = Math.max(r.width() / (float) bm.getWidth(), r.height() / (float) bm.getHeight());
            float w = bm.getWidth() * sc, h = bm.getHeight() * sc;
            c.drawColor(Ui.BACKGROUND);
            c.drawBitmap(bm, null, new android.graphics.RectF(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2), null);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(android.graphics.ColorFilter f) {}
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
    }

    private void editFavourite(final int i) {
        String[] f = Cards.PhoneCard.fav(prefs, i);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.px(20);
        box.setPadding(pad, pad / 2, pad, 0);
        final android.widget.EditText name = new android.widget.EditText(this), num = new android.widget.EditText(this);
        name.setHint("Name");
        num.setHint("Phone number");
        num.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        if (f != null) {
            name.setText(f[0]);
            num.setText(f[1]);
        }
        box.addView(name);
        box.addView(num);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Favourite " + (i + 1) + " (empty to remove)")
                .setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String n = name.getText().toString().trim().replace("|", " "), p = num.getText().toString().trim();
                        prefs.put("fav" + (i + 1), n.isEmpty() || p.isEmpty() ? "" : n + "|" + p);
                        hub.notifyIfChanged(true);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void cycle(String what, String[] all) {
        String cur = prefs.source(what);
        int i = java.util.Arrays.asList(all).indexOf(cur);
        prefs.setSource(what, all[(i + 1) % all.length]);
        hub.notifyIfChanged(true);
    }

    private void editWelcome() {
        final android.widget.EditText e = new android.widget.EditText(this);
        e.setText(prefs.welcome());
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Name under the car (empty to hide)")
                .setView(e)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        prefs.setWelcome(e.getText().toString().trim());
                        hub.notifyIfChanged(true);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Pick a station or song from the player's list, loading the list first if needed. */
    private void pickTrack(final boolean web) {
        final java.util.List<Player.Track> list = web ? Player.stations : Player.songs;
        if (list.isEmpty()) {
            final android.app.ProgressDialog wait = android.app.ProgressDialog.show(this, null,
                    web ? "Finding stations…" : "Looking for music…", true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final java.util.List<Player.Track> l = web ? Player.fetchStations(HomeActivity.this)
                            : Player.scanSongs(HomeActivity.this);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            wait.dismiss();
                            if (l.isEmpty()) {
                                Toast.makeText(HomeActivity.this, web ? "Web radio unreachable" : "No music files found",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            list.clear();
                            list.addAll(l);
                            pickTrack(web);
                        }
                    });
                }
            }).start();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            Player.Track t = list.get(i);
            names[i] = t.sub.isEmpty() ? t.name : t.name + "  ·  " + t.sub;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(web ? "Online radio" : "Songs")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        prefs.setSource(web ? "radio" : "music", web ? "web" : "files");
                        Player.send(HomeActivity.this, Player.PICK, web ? Player.WEB : Player.FILES, which);
                    }
                })
                .setNegativeButton("Close", null).show();
    }

    private Runnable pick(final String key, final String name) {
        return new Runnable() {
            @Override
            public void run() {
                AppPicker.show(HomeActivity.this, "Choose the app for " + name, new AppPicker.Picked() {
                    @Override
                    public void picked(String p) {
                        prefs.setApp(key, p);
                    }
                });
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        OverMic.hide(getApplicationContext());
        hub.start();
        Extras.resumed(this);
        highwayStrip();
        dash.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (visible) Chores.summary(HomeActivity.this);   // after the weather has had time to arrive
            }
        }, 15000);
        dash.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!visible || Car.firstRun(HomeActivity.this)) return;   // the car setup first; the rest next time
                if (!Features.levelPicked(prefs)) {
                    askLevel(true);
                    return;   // one question at a time
                }
                if (askVoice()) return;
                askForTrackInfo();
                serviceBooking();
                askOverlay();
                Chores.washCheck(HomeActivity.this);
                Chores.weekly(HomeActivity.this, false);
            }
        }, 3000);
    }

    @Override
    protected void onPause() {
        visible = false;
        hub.stop();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        Drive.start(this);
        hub.stop();
        hub.start();
    }

    /**
     * Which way up the screen is: "auto" keeps a landscape stereo landscape (either way up) and a
     * portrait one portrait; or the owner's choice from Settings > Dashboard.
     */
    private void turn() {
        String t = prefs.str("turn", "auto");
        boolean portrait = "portrait".equals(t);
        if ("auto".equals(t)) {
            android.graphics.Point s = new android.graphics.Point();
            android.view.Display d = getWindowManager().getDefaultDisplay();
            d.getRealSize(s);
            int rot = d.getRotation();
            boolean sideways = rot == android.view.Surface.ROTATION_90 || rot == android.view.Surface.ROTATION_270;
            int naturalW = sideways ? s.y : s.x, naturalH = sideways ? s.x : s.y;
            portrait = naturalH > naturalW;
        }
        setRequestedOrientation(portrait ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private int lastW, lastH;

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        if ((dm.widthPixels != lastW || dm.heightPixels != lastH) && lastW != 0) restyle();   // turned or resized
        lastW = dm.widthPixels;
        lastH = dm.heightPixels;
        hub.notifyIfChanged(true);   // night mode follows the headlight wire
    }

    @Override
    public void onBackPressed() {
        // this is the home screen: back only closes the settings or the box mover
        if (Extras.wake(this)) return;
        if (dash.editing()) stopMoving();
        else closeSettings();
    }
}
