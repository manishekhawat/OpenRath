package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
        Managed.apply(this, prefs);   // a fleet's managed settings, if the unit has any
        Reports.install(this, prefs);
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
        // location, storage, Bluetooth, notifications and the plain Android phone (Bluetooth hands-free
        // through the system phone), as this Android version names them
        String[] missing = Compat.missing(this);
        if (missing.length > 0) requestPermissions(missing, 1);
        HomeDialogs.findRadio(this);
        Drive.start(this);
        Keys.init(this);
        widgets = new android.appwidget.AppWidgetHost(this, 2041);
        android.content.IntentFilter f = new android.content.IntentFilter(Keys.ACTION);
        f.addAction(Ticker.CHANGED);
        f.addAction(Drive.REST);
        f.addAction(Drive.FUEL_LOW);
        f.addAction(Drive.CRASH);
        f.addAction(Drive.HIGHWAY);
        f.addAction(android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);
        Compat.ownOnly(this, events, f);
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
            else if (Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED.equals(i.getAction())) {
                Managed.apply(HomeActivity.this, prefs);
                Extras.featuresChanged(HomeActivity.this);
            }
            else if (Drive.CRASH.equals(i.getAction()))
                Rescue.show(HomeActivity.this, prefs, i.getDoubleExtra("lat", 0), i.getDoubleExtra("lon", 0), false);
            else if (Drive.FUEL_LOW.equals(i.getAction())) {
                MapCard m = mapCard();
                if (m != null) m.js("restStop('fuel')");
            }
        }
    };

    /** The map box on this page, or null. */
    MapCard mapCard() {
        if (dash == null) return null;
        for (View v : dash.slots) if (v instanceof MapCard) return (MapCard) v;
        return null;
    }

    /** Time for a break: show the stops ahead on the map (or say where to find them). */
    void restStop() {
        MapCard m = mapCard();
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
        } else if ("docs".equals(a)) HomeVoice.run(this, new Voice.Command("docs", ""));
        else if ("news".equals(a)) HomeVoice.run(this, new Voice.Command("news", ""));
        else if ("cab".equals(a) || "sos".equals(a)) HomeVoice.run(this, new Voice.Command(a, ""));
        else if ("meter".equals(a)) HomeVoice.run(this, new Voice.Command("meter", prefs.longNum("cabMeterAt", 0) > 0 ? "stop" : "start"));
        else if ("kids".equals(a)) HomeVoice.run(this, new Voice.Command("kids", ""));
        else if ("fog".equals(a)) HomeVoice.run(this, new Voice.Command("fog", ""));
        else if ("share".equals(a) || "split".equals(a) || "parkTimer".equals(a) || "hotspot".equals(a)) HomeVoice.run(this, new Voice.Command(a, ""));
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
        dash.dock = dash.wide ? HomeDock.wideBar(this) : HomeDock.dock(this);
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
    boolean fogDismissed;

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

    private MapCard listening;

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

    void parkHere() {
        if (hub.fix == null) {
            Toast.makeText(this, "No GPS position yet", Toast.LENGTH_LONG).show();
            return;
        }
        Drive.setPark(this, hub.fix.getLatitude(), hub.fix.getLongitude());
        Toast.makeText(this, "Parking spot saved", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void voiceSearch(MapCard map) {
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
        MapCard m = listening;
        listening = null;
        listeningAny = false;
        HomeVoice.heard(this, said, m);
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

    String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void editSlot(int slot) {
        HomeDialogs.settings(this, slot);
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
                return SettingsRows.settingsRows(HomeActivity.this);
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

    /** The picture layout changed: redraw it, or rebuild the screen when it was switched. */
    void layoutChanged() {
        if (layoutView != null && layoutView.lay.id.equals(Layouts.current(prefs))) layoutView.rebuild();
        else build();
    }

    int devTaps;
    long devTapAt;

    // ------------------------------------------------------------------ moving boxes

    private View moveBar;

    /** Drag-and-drop mode: drag a box onto another to swap them; Done (or back) ends it. */
    void startMoving() {
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
        TextView done = PhoneCard.pill(this, "Done", 0xFF2A2D32);
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

    static final String[] PICTURES = {"car_off", "car_on", "logo"};
    /** Every picture the owner can replace; the request code is 61 + its index. */
    static final String[] ALL_PICTURES = {"car_off", "car_on", "logo", "dock_navi", "dock_music", "dock_radio",
            "dock_bt", "dock_phone", "dock_apps", "dock_settings"};

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

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        OverMic.hide(getApplicationContext());
        hub.start();
        Extras.resumed(this);
        Reports.maybeSend(versionName());
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
                    HomeSettings.askLevel(HomeActivity.this, true);
                    return;   // one question at a time
                }
                if (HomeDialogs.askVoice(HomeActivity.this)) return;
                if (HomeDialogs.askAllFiles(HomeActivity.this)) return;
                HomeDialogs.askForTrackInfo(HomeActivity.this);
                HomeDialogs.serviceBooking(HomeActivity.this);
                HomeDialogs.askOverlay(HomeActivity.this);
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
    void turn() {
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
