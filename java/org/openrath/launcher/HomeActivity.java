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
        prefs = new Prefs(this);
        Managed.apply(this, prefs);   // a fleet's managed settings, if the unit has any
        Reports.install(this, prefs);
        Hindi.load(this, prefs);   // before the dashboard is built, so it starts in the owner's language
        // a new version: steps skipped after a crash in the old one get another try
        try {
            int v = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            if (prefs.num("crashForgiven", 0) != v) {
                Crash.forgive();
                prefs.put("crashForgiven", String.valueOf(v));
            }
        } catch (Exception e) {
            Diag.note("version check", e);
        }
        turn();
        hub = new Hub(this, prefs);
        dash = new Dashboard(this);
        root = new android.widget.FrameLayout(this) {
            // a full-screen page (Settings, a menu) coming or going: the floating mic hides or comes back
            @Override
            public void onViewAdded(View v) {
                super.onViewAdded(v);
                micShown();
            }

            @Override
            public void onViewRemoved(View v) {
                super.onViewRemoved(v);
                micShown();
            }
        };
        root.addView(dash, new android.widget.FrameLayout.LayoutParams(-1, -1));
        Edge.fit(root);
        setContentView(root);
        // location, storage, Bluetooth, notifications and the plain Android phone (Bluetooth hands-free
        // through the system phone), as this Android version names them
        final boolean setup = Setup.needed(Features.on(prefs, "setup"), "yes".equals(prefs.str("setupDone", "")),
                !prefs.str("setupStep", "").isEmpty(), Car.ready(prefs), Features.levelPicked(prefs));
        final String[] missing = Compat.missing(this);
        if (setup) {
            // the setup screens explain and ask
        } else if (missing.length > 0 && !"yes".equals(prefs.str("permsExplained", ""))) HomeDialogs.explainPermissions(this, missing);
        else if (missing.length > 0) requestPermissions(missing, 1);
        HomeDialogs.findRadio(this);
        Drive.start(this);
        Keys.init(this);
        widgets = new android.appwidget.AppWidgetHost(this, 2041);
        android.content.IntentFilter f = new android.content.IntentFilter(Keys.ACTION);
        f.addAction(Ticker.CHANGED);
        f.addAction(NavTurn.CHANGED);
        f.addAction(Drive.REST);
        f.addAction(Drive.FUEL_LOW);
        f.addAction(Drive.HIGHWAY);
        Compat.ownOnly(this, events, f);
        // managed settings changed: sent by the system, which needs no permission of OpenRath's
        android.content.IntentFilter sys = new android.content.IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);
        sys.addAction(Intent.ACTION_SCREEN_ON);   // back from the stereo's sleep: check at once (Wake)
        Compat.fromSystem(this, events, sys);
        PhoneLink.listen(this);
        AaUsb.listen(this);   // a phone plugged in starts Android Auto (when switched on)
        build();
        root.post(new Runnable() {
            @Override
            public void run() {
                firstFrame = true;   // drawn once: the map's web page can load now (see build)
            }
        });
        if (setup) Setup.show(this);
        if (Intro.show(Features.on(prefs, "intro"), Motion.pick(prefs.str("motion", "standard")), b == null)) Intro.play(this, root);
        handleIntent(getIntent());
    }

    /** True while the dashboard is on screen (the key service leaves keys to it then). */
    static volatile boolean visible;
    android.appwidget.AppWidgetHost widgets;

    private final android.content.BroadcastReceiver events = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            if (Keys.ACTION.equals(i.getAction())) {
                if (ownKey(i)) keyAction(i.getStringExtra("action"));
            } else if (Ticker.CHANGED.equals(i.getAction())) Extras.ticker(HomeActivity.this);
            else if (NavTurn.CHANGED.equals(i.getAction())) NavCard.update(HomeActivity.this);
            else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                if (visible) Wake.sleptNow(HomeActivity.this);
            }
            else if (Drive.REST.equals(i.getAction())) restStop();
            else if (Drive.HIGHWAY.equals(i.getAction())) highwayStrip();
            else if (Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED.equals(i.getAction())) {
                Managed.apply(HomeActivity.this, prefs);
                Extras.featuresChanged(HomeActivity.this);
            }
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
        else Toast.makeText(this, Hindi.t("Time for a break. Put the map on the dashboard to see stops ahead."), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    private void handleIntent(Intent i) {
        if (i != null && Keys.ACTION.equals(i.getAction()) && ownKey(i)) keyAction(i.getStringExtra("action"));
    }

    /** A key action from OpenRath itself (it carries this install's token); other apps' are ignored. */
    private boolean ownKey(Intent i) {
        String got;
        try {
            got = i.getStringExtra(Keys.TOKEN);
        } catch (Throwable e) {   // extras another app made unreadable
            return false;
        }
        return Keys.tokenOk(Keys.token(this), got);
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
        else if ("radio".equals(a)) {
            if (!openMusic(MusicScreen.RADIO)) openApp("media:radio", "Radio");
        } else if ("music".equals(a)) {
            if (!openMusic(MusicScreen.MUSIC)) openApp("media:music", "Music");
        } else if ("dash".equals(a)) {
            closeMusic();
            closeSettings();
        }
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
            Toast.makeText(this, Hindi.t("Turn on \"Share the trip by QR code\" in Settings > Features"), Toast.LENGTH_LONG).show();
            return;
        }
        double lat = Drive.latNow, lon = Drive.lonNow;
        if (lat == 0 && lon == 0 && hub.fix != null) {
            lat = hub.fix.getLatitude();
            lon = hub.fix.getLongitude();
        }
        if (lat == 0 && lon == 0) {
            Toast.makeText(this, Hindi.t("Waiting for the GPS to find the car"), Toast.LENGTH_LONG).show();
            return;
        }
        String[] d = prefs.str("dest", "").split("\\|");
        if (d.length == 3) {
            try {
                double tl = Double.parseDouble(d[1]), tn = Double.parseDouble(d[2]);
                double km = Geo.dist(lat, lon, tl, tn) / 1000;
                Qr.show(this, Hindi.f("Our trip to %1$s", d[0]), Geo.routeLink(lat, lon, tl, tn),
                        Hindi.f("About %1$s km to go in a straight line.", String.format(java.util.Locale.US, "%.0f", km)) + " "
                                + Hindi.t("Scan with a phone to see the route and time from where the car is now. "
                                + "Show a new code later for a fresh position."));
                return;
            } catch (Exception ignored) {
                // a bad saved destination: share where we are instead
            }
        }
        Qr.show(this, Hindi.t("Where we are now"), Geo.mapsLink(lat, lon),
                Hindi.t("Scan with a phone to see where the car is now. Pick a destination on the map to share the route too."));
    }

    /** The parking spot as a QR code that opens it in Google Maps on a phone. */
    @Override
    public void parkQr() {
        if (!Features.on(prefs, "parkLink")) {
            Toast.makeText(this, Hindi.t("Turn on \"Parking spot to your phone\" in Settings > Driving"), Toast.LENGTH_LONG).show();
            return;
        }
        if (Drive.parkAt == 0) {
            Toast.makeText(this, Hindi.t("No parking spot saved yet"), Toast.LENGTH_SHORT).show();
            return;
        }
        Qr.show(this, Hindi.t("Where the car is parked"), Geo.walkLink(Drive.parkLat, Drive.parkLon),
                Hindi.t("Scan with your phone to open the spot in Google Maps and walk back to the car."));
    }

    /** Call a number through the unit's Bluetooth phone (the map's service centre list). */
    @Override
    public void call(String number) {
        String n = number == null ? "" : number.replaceAll("[^0-9+]", "");
        if (n.isEmpty()) return;
        String err = hub.unit.call(this, n);
        Toast.makeText(this, err == null ? Hindi.f("Calling %1$s…", n) : Hindi.f("Couldn't call: %1$s", err), Toast.LENGTH_SHORT).show();
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
        MapCard map = mapCard();
        if (map != null) map.resumeMap();
        try {
            widgets.startListening();
        } catch (Throwable e) {
            Diag.note("widget host start", e);
        }
    }

    @Override
    protected void onStop() {
        OverMic.show(getApplicationContext(), prefs);
        OverStrip.show(getApplicationContext(), prefs);
        // another app is in front: the map's page stops drawing and running its timers until we're back
        MapCard map = mapCard();
        if (map != null) map.pauseMap();
        try {
            widgets.stopListening();
        } catch (Throwable e) {
            Diag.note("widget host stop", e);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Setup.forget();
        PhoneLink.stop(this);
        try {
            unregisterReceiver(events);
        } catch (Throwable ignored) {
            // was not registered
        }
        // let go of everything that would keep this screen alive after Android drops it
        dash.removeCallbacks(chores);
        dash.removeCallbacks(questions);
        Wake.stop(this);
        Extras.closed(this);
        LedStrip.closed(this);
        if (keptMap != null) keptMap.destroy();
        keptMap = null;
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
        Motion.load(prefs);
        Units.load(prefs);
        Fixes.stillKmh = prefs.num("speedFilter", 2);
        dash.swapped = prefs.swapped();
        dash.setSizes(prefs.str("boxSizes", null));
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
            keepMap(false);
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
        if (Glass.on) dash.setBackground(Glass.backdrop());   // the blurred wallpaper, under glass boxes
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
            String content = prefs.slot(i);
            // a new map page waits until the dashboard has been drawn once, so the first screen comes up sooner
            Cards.Card c = Prefs.MAP.equals(content) && !firstFrame && keptMap == null ? mapLater(i) : Cards.make(content, this, this, i);
            dash.slots[i] = c;
            dash.addView(c);
            hub.add(c);
        }
        keepMap(true);
        dash.requestLayout();
        hub.add(overlays);
        dash.mover = new Dashboard.Mover() {
            @Override
            public void swap(int from, int to) {
                prefs.swapSlots(from, to);
                build();
                dash.setEditing(true);
            }

            @Override
            public void resized(String sizes) {
                prefs.put("boxSizes", sizes);
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

    /** The map box, kept between rebuilds while a page shows the map (see Cards.map). */
    MapCard keptMap;
    private boolean firstFrame;

    /** An empty box where the map goes, swapped for the map once the first screen is drawn. */
    private Cards.Card mapLater(int slot) {
        final Cards.Card wait = new Cards.NoMapCard(this, "");
        wait.host = this;
        wait.slot = slot;
        wait.setOnLongClickListener(wait.edit);
        root.post(new Runnable() {
            @Override
            public void run() {
                firstFrame = true;
                int i = java.util.Arrays.asList(dash.slots).indexOf(wait);
                if (i < 0) return;   // rebuilt since: that build made its own
                Cards.Card m = Cards.make(Prefs.MAP, HomeActivity.this, HomeActivity.this, i);
                dash.removeView(wait);
                dash.slots[i] = m;
                dash.addView(m);
                hub.add(m);
                dash.requestLayout();
            }
        });
        return wait;
    }

    /** The next build starts the map page afresh (after deep sleep it can be stale). */
    void freshMap() {
        if (keptMap != null) keptMap.destroy();
        keptMap = null;
    }

    /**
     * After a rebuild: the kept map is on screen, paused for another page that has it, or let go
     * for good when no page shows it (or a picture layout replaced the boxes).
     */
    private void keepMap(boolean boxes) {
        MapCard m = keptMap;
        if (m == null) return;
        for (View v : dash.slots) {
            if (v == m) {
                m.resumeMap();
                return;
            }
        }
        if (boxes && m.alive() && prefs.mapOnAnyPage()) {
            m.pauseMap();
            return;
        }
        m.destroy();
        keptMap = null;
    }

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
            root.addView(mic, at, new android.widget.FrameLayout.LayoutParams(Ui.px(Floaters.MIC), Ui.px(Floaters.MIC)));
            root.post(new Runnable() {
                @Override
                public void run() {
                    mic.restore(root.getWidth(), root.getHeight());
                }
            });
        }
        micShown();
    }

    /** Whether Android's own status bar shows over the launcher: then OpenRath's status icons would only repeat it. */
    boolean systemBarShown() {
        try {
            android.view.WindowInsets in = getWindow().getDecorView().getRootWindowInsets();
            return in != null && in.getSystemWindowInsetTop() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Where OpenRath's Wi-Fi, Bluetooth, GPS and battery icons go: "" nowhere (switched off, or Android's
     * status bar already shows them), "clock" along the top of the clock box when this page has one, else
     * "dock" in the buttons bar. Pure, for the tests.
     */
    static String statusPlace(boolean on, boolean systemBar, boolean clockOnPage) {
        if (!on || systemBar) return "";
        return clockOnPage ? "clock" : "dock";
    }

    String statusPlace() {
        boolean clock = false;
        if (layoutView == null) for (int i = 0; i < Prefs.SLOTS; i++) if (Prefs.CLOCK.equals(prefs.slot(i))) clock = true;
        return statusPlace(Features.on(prefs, "dockStatus"), systemBarShown(), clock);
    }

    /**
     * The floating mic stays out of the way: hidden while this page shows the map (which has its own
     * mic) and while Settings or any other full-screen page or menu is open on top.
     */
    void micShown() {
        if (micView == null || root == null) return;
        boolean hide = settingsView != null || mapCard() != null;
        if (layoutView == null && dash != null) {
            for (int i = 0; i < Prefs.SLOTS; i++) if (Prefs.MAP.equals(prefs.slot(i))) hide = true;
        }
        for (int i = root.indexOfChild(micView) + 1; i > 0 && i < root.getChildCount() && !hide; i++) {
            View v = root.getChildAt(i);
            android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (v != dimmer && v.getVisibility() == View.VISIBLE && lp != null && lp.width == -1 && lp.height == -1) hide = true;
        }
        micView.setVisibility(hide ? View.GONE : View.VISIBLE);
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
            // Android's own brightness when the owner allowed it (holds over other apps too), switched by
            // the night dimming choice or, with that off, the headlights; else this window's backlight
            // where Android lets an app set it; else a dark layer on top
            boolean system = systemBrightness("off".equals(mode) ? h.lightsOn() : night);
            if (system) night = false;
            boolean backlit = backlight(night ? prefs.num("dimLevel", 40) : 0);
            dimmer.setAlpha(night && !backlit ? prefs.num("dimLevel", 40) / 100f : 0f);
            dimmer.setVisibility(night && !backlit ? View.VISIBLE : View.GONE);
            nightDrive(h);
            fogMode(h);
            valetBadge();
        }
    };

    private int backlitBy = -1;

    /**
     * Turn the screen's backlight down by `percent` from the brightness set in Android (0 = back to
     * Android's own), while the dashboard shows. False when it can't (Android's brightness unreadable).
     * The screensaver's own dark screen is left alone.
     */
    private boolean backlight(int percent) {
        if (percent <= 0 && backlitBy == 0) return true;
        if (Extras.darkScreen()) return true;
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (percent <= 0) {
            lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            int sys;
            try {
                sys = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
            } catch (Exception e) {
                return false;
            }
            lp.screenBrightness = Math.max(0.02f, sys / 255f * (100 - percent) / 100f);
        }
        getWindow().setAttributes(lp);
        backlitBy = Math.max(0, percent);
        return true;
    }

    // ------------------------------------------------------------------ brightness for all apps

    /** Android's brightness (1 to 255) for a level in percent. Pure, for the tests. */
    static int systemLevel(int percent) {
        return Math.max(1, Math.min(255, Math.round(percent * 255 / 100f)));
    }

    /** Whether Android lets OpenRath change its own settings (the owner allows it once). */
    static boolean canWriteSettings(android.content.Context c) {
        return android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.System.canWrite(c);
    }

    /** The level last written to Android's settings; -1 = none yet. */
    private int systemSet = -1;

    /**
     * Set Android's own screen brightness to the owner's day or night level, so it holds over the
     * other apps too. Written only when it changes, so the owner can still move it by hand in
     * between. False when it is off or not allowed: then the dashboard dims only itself, as before.
     */
    private boolean systemBrightness(boolean night) {
        if (!"on".equals(prefs.str("sysBright", "off")) || !canWriteSettings(this)) {
            systemSet = -1;
            return false;
        }
        int want = systemLevel(night ? prefs.num("brightNight", 25) : prefs.num("brightDay", 80));
        if (want == systemSet) return true;
        try {
            android.content.ContentResolver cr = getContentResolver();
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, want);
            systemSet = want;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** "day 80%, night 25%", "off", or that Android's permission is still missing; for Settings. */
    String brightnessState() {
        if (!"on".equals(prefs.str("sysBright", "off"))) return Hindi.t("off; only the dashboard dims at night");
        if (!canWriteSettings(this)) return Hindi.t("waiting for your OK in Android; the dashboard dims meanwhile");
        return Hindi.f("day %1$s%%, night %2$s%%", prefs.num("brightDay", 80), prefs.num("brightNight", 25));
    }

    /** Settings > Screen brightness for all apps: explain and ask once, then the day and night levels. */
    void brightnessSettings() {
        if (!"on".equals(prefs.str("sysBright", "off"))) {
            new Glass.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Screen brightness for all apps"))
                    .setMessage(Hindi.t("OpenRath can set the unit's own screen brightness: one level by day and a lower one at night, "
                            + "switched by the headlights (or at sunset, as chosen in Night dimming). Then the night level also holds "
                            + "over the map, music and camera apps, not only on the dashboard.") + "\n\n" + Hindi.t("For this, Android asks you once to "
                            + "allow OpenRath to change system settings. Without it, only the dashboard dims, as now."))
                    .setPositiveButton(Hindi.t("Turn on"), new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            rememberOwnBrightness();
                            prefs.put("sysBright", "on");
                            if (!canWriteSettings(HomeActivity.this)) askWriteSettings();
                            else brightnessLevels();
                            hub.notifyIfChanged(true);
                            if (settingsView != null) settingsView.refresh();
                        }
                    }).setNegativeButton(Hindi.t("Cancel"), null).show();
            return;
        }
        brightnessLevels();
    }

    private void brightnessLevels() {
        final boolean allowed = canWriteSettings(this);
        final String[] items = {Hindi.f("Day level: %1$s%%", prefs.num("brightDay", 80)), Hindi.f("Night level: %1$s%%", prefs.num("brightNight", 25)),
                allowed ? Hindi.t("Turn off") : Hindi.t("Allow it in Android"), Hindi.t("Turn off")};
        new Glass.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Screen brightness for all apps"))
                .setItems(allowed ? java.util.Arrays.copyOf(items, 3) : items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) brightnessStep("brightDay", 80, new int[]{40, 50, 60, 70, 80, 90, 100}, "How bright by day");
                        else if (w == 1) brightnessStep("brightNight", 25, new int[]{5, 10, 15, 20, 25, 30, 40, 50}, "How bright at night");
                        else if (w == 2 && !allowed) askWriteSettings();
                        else {
                            prefs.put("sysBright", "off");
                            giveBackOwnBrightness();
                            hub.notifyIfChanged(true);
                            if (settingsView != null) settingsView.refresh();
                        }
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    private void brightnessStep(final String key, int def, final int[] steps, String title) {
        String[] names = new String[steps.length];
        int at = -1;
        for (int i = 0; i < steps.length; i++) {
            names[i] = steps[i] + "%";
            if (steps[i] == prefs.num(key, def)) at = i;
        }
        new Glass.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t(title))
                .setSingleChoiceItems(names, at, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        prefs.put(key, String.valueOf(steps[w]));
                        d.dismiss();
                        systemSet = -1;   // write the new level now
                        hub.notifyIfChanged(true);
                        if (settingsView != null) settingsView.refresh();
                    }
                }).show();
    }

    /** Android's own screen for "Modify system settings", on OpenRath's page. */
    private void askWriteSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Toast.makeText(this, Hindi.t("Switch on \"Modify system settings\" for OpenRath, then come back."), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, Hindi.t("This unit has no screen to allow that, so only the dashboard dims at night."), Toast.LENGTH_LONG).show();
        }
    }

    /** The brightness the owner had before OpenRath took it over, to give back when it is turned off. */
    private void rememberOwnBrightness() {
        try {
            android.content.ContentResolver cr = getContentResolver();
            prefs.put("brightWas", String.valueOf(android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS)));
            prefs.put("brightWasMode", String.valueOf(android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0)));
        } catch (Exception ignored) {
            // unreadable: nothing to give back later
        }
    }

    private void giveBackOwnBrightness() {
        systemSet = -1;
        if (!canWriteSettings(this) || prefs.str("brightWas", "").isEmpty()) return;
        try {
            android.content.ContentResolver cr = getContentResolver();
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, prefs.num("brightWas", 200));
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, prefs.num("brightWasMode", 0));
        } catch (Exception ignored) {
            // left as it is
        }
    }

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
            night.setSpeed(h.speedShown());
            night.setMap(mapCard());
        }
    }

    private Fog fog;
    boolean fogDismissed;

    /** In fog while driving: big speed, slow guide speed and the next bend (see Fog). */
    void fogMode(Hub h) {
        boolean driving = Drive.drives.cur != null;
        if (!driving) fogDismissed = false;
        boolean want = Fog.now(prefs) && driving && !fogDismissed && settingsView == null && !hub.reverse;
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
        if (fog != null) fog.setSpeed(h.speedShown());
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
        String t = Hindi.f("HIGHWAY  ·  avg %1$s km/h  ·  driving %2$s", String.valueOf(Math.round(Drive.avgKmh())),
                mins >= 60 ? Hindi.f("%1$s h %2$s min", String.valueOf(mins / 60), String.valueOf(mins % 60)) : Hindi.f("%1$s min", String.valueOf(mins)))
                + (Highway.held > 0 ? "  ·  " + Hindi.f("%1$s waiting", String.valueOf(Highway.held)) : "");
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
            valetView.setText(Hindi.t("VALET MODE  ·  tap to end"));
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
            Toast.makeText(this, Hindi.t(ChildLock.SORRY), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = pkg == null ? null : Compat.launch(getPackageManager(), pkg);
        if (i == null && "dock:phone".equals(key)) i = new Intent(Intent.ACTION_DIAL);
        if (i != null) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
                // app gone: let them pick another
            }
        }
        AppPicker.show(this, Hindi.f("Choose the app for %1$s", Hindi.t(name)), new AppPicker.Picked() {
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
        String[] items = {Hindi.t("Start today's trip again"), Hindi.t("Save the parking spot here"), Hindi.t("Fuel log and fill-ups"),
                Hindi.t("Trip history and driving score"), Hindi.t("Add a toll payment")};
        new Glass.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.f("Today %1$s km  ·  GPS odometer %2$s km", String.format(java.util.Locale.US, "%.1f", Drive.todayKm), String.valueOf(Math.round(Drive.odoKm))))
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) {
                            Drive.resetToday(HomeActivity.this);
                            hub.notifyIfChanged(true);
                        } else if (w == 1) parkHere();
                        else if (w == 2) fuel();
                        else if (w == 3) Care.history(HomeActivity.this, prefs);
                        else Care.addToll(HomeActivity.this, prefs, new Runnable() {
                                @Override
                                public void run() {
                                    hub.notifyIfChanged(true);
                                }
                            });
                    }
                }).show();
    }

    void parkHere() {
        // only a live GPS fix: the network's rough position or an old one can be hundreds of metres off
        android.location.Location f = hub.liveFix();
        if (f == null) {
            Toast.makeText(this, Hindi.t("No GPS position yet"), Toast.LENGTH_LONG).show();
            return;
        }
        Drive.setPark(this, f.getLatitude(), f.getLongitude());
        Toast.makeText(this, Hindi.t("Parking spot saved"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void voiceSearch(MapCard map) {
        boolean commands = Features.on(prefs, "voice");
        String lang = prefs.str("voiceLang", "en-IN");
        Intent i = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, commands ? Hindi.t("Say a command or a place") : Hindi.t("Where to?"))
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                // no internet: Google's offline speech pack (Settings > Map and navigation to get it)
                .putExtra("android.speech.extra.PREFER_OFFLINE", !Player.online(this) && Features.on(prefs, "offlineVoice"))
                // understand both: Hindi with English words, or English with Hindi words
                .putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"hi-IN".equals(lang) ? "en-IN" : "hi-IN"});
        if (Listen.can(this)) {   // in a small pill, the dashboard staying in view
            Listen.start(this, i, map);
            return;
        }
        // the first time, ask for the microphone, then listen (in the pill if it was allowed)
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !"yes".equals(prefs.str("micAsked", ""))) {
            prefs.put("micAsked", "yes");
            micFor = map;
            micWaiting = true;
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC_ASK);
            return;
        }
        try {
            listening = map;
            listeningAny = true;
            startActivityForResult(i, 42);
        } catch (Exception e) {
            listening = null;
            listeningAny = false;
            Toast.makeText(this, Hindi.t("Voice needs the Google app on the unit"), Toast.LENGTH_LONG).show();
            if (map != null) map.voiceResult(null);
        }
    }

    private boolean listeningAny;
    /** The next words heard are a reply to the last message (HomeVoice.reply). */
    boolean replying;
    private static final int MIC_ASK = 5;
    /** Voice was asked for while Android asked about the microphone: listen once it answers. */
    private MapCard micFor;
    private boolean micWaiting;

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
            Toast.makeText(this, Hindi.t("No internet, and no offline map app. Install Organic Maps (free, with offline maps of India) "
                    + "and download your state's map once on Wi-Fi."), Toast.LENGTH_LONG).show();
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
            // no Google Maps: try any map app below
        }
        // not Google Maps: any app that takes a map location
        Intent geo = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(String.format(java.util.Locale.US,
                "geo:%.6f,%.6f?q=%.6f,%.6f(%s)", lat, lon, lat, lon, android.net.Uri.encode(name))));
        try {
            startActivity(geo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, Hindi.t("No navigation app found"), Toast.LENGTH_LONG).show();
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
        } catch (Throwable e) {
            Diag.note("radio app services", e);
        }
    }

    @Override
    public void startRadio() {
        String pkg = prefs.app("media:radio");
        Intent i = pkg == null ? null : Compat.launch(getPackageManager(), pkg);
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
    MusicScreen musicScreen;

    /**
     * The radio and music screen, on the Radio, Music or Apps list (null: the last one). False when
     * its switch in Settings > Features is off, so the caller does what it did before.
     */
    boolean openMusic(String tab) {
        if (!Features.on(prefs, "musicApp")) return false;
        if (musicScreen != null) {
            if (tab != null) MusicScreen.tab = tab;
            musicScreen.refresh(true);
            return true;
        }
        musicScreen = new MusicScreen(this, tab, new MusicScreen.Closed() {
            @Override
            public void closed() {
                closeMusic();
            }
        });
        root.addView(musicScreen, new android.widget.FrameLayout.LayoutParams(-1, -1));
        return true;
    }

    /** Back to the dashboard from the radio and music screen; false when it wasn't open. */
    boolean closeMusic() {
        if (musicScreen == null) return false;
        musicScreen.gone();
        root.removeView(musicScreen);
        musicScreen = null;
        hub.notifyIfChanged(true);
        return true;
    }

    /** The owner entered the valet PIN: Settings and box menus open until Settings is closed. */
    boolean ownerUnlocked;

    /** The owner said a passenger is using the screen: Settings open while moving until it is closed. */
    private boolean passenger;

    void openSettings() {
        if (settingsView != null) return;
        if (Features.on(prefs, "parkedSettings") && !passenger && ChildLock.moving()) {
            new Glass.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(Hindi.t("Settings open when the car is stopped"))
                    .setMessage(Hindi.t("Changing settings while driving takes your eyes off the road."))
                    .setPositiveButton(Hindi.t("OK"), null)
                    .setNegativeButton(Hindi.t("I'm a passenger"), new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            passenger = true;
                            openSettings();
                        }
                    }).show();
            return;
        }
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
        micShown();
        ownerUnlocked = false;
        passenger = false;
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
        android.graphics.drawable.GradientDrawable barBg = Ui.panel();
        if (Glass.on) barBg.setColors(new int[]{Glass.solid(), Glass.solid()});   // over the boxes: solid, to read
        bar.setBackground(barBg);
        TextView t = Ui.text(this, 16, Ui.SILVER, "sans-serif", Gravity.LEFT);
        t.setText(Hindi.t("Drag a box onto another to swap them, or drag a bar between boxes to resize them"));
        bar.addView(t);
        TextView done = PhoneCard.pill(this, Hindi.t("Done"), 0xFF2A2D32);
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
        TextView reset = PhoneCard.pill(this, Hindi.t("Usual sizes"), 0xFF2A2D32);
        reset.setTextSize(0, 16 * Ui.k);
        reset.setPadding(Ui.px(20), 0, Ui.px(20), 0);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, Ui.px(44));
        rp.leftMargin = Ui.px(10);
        bar.addView(reset, rp);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.put("boxSizes", "");
                dash.setSizes(null);
                dash.requestLayout();
                dash.invalidate();
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
            Toast.makeText(this, Hindi.f("Couldn't use that picture: %1$s", e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /** Rebuild everything in the new look, keeping the settings screen open. */
    void restyle() {
        boolean open = settingsView != null;
        closeSettings();
        Theme.reload();   // a new look: read the theme and its pictures again
        build();
        if (open) openSettings();
    }

    /** A wallpaper scaled to fill the screen without stretching. */
    static final class CenterCrop extends android.graphics.drawable.Drawable {
        private final android.graphics.drawable.BitmapDrawable b;
        private final android.graphics.RectF to = new android.graphics.RectF();

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
            to.set(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2);
            c.drawBitmap(bm, null, to, null);
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
        OverStrip.hide(getApplicationContext());
        NavCard.update(this);
        hub.start();
        Extras.resumed(this);
        Reports.maybeSend(versionName());
        Updates.resumed(this);
        Wake.watch(this);
        AaUsb.check(this, false);
        highwayStrip();
        if (Setup.showing() != null) Setup.showing().render();   // back from Android's settings: show what changed
        // once per return to the dashboard: a quick back-and-forth doesn't queue them up again
        dash.removeCallbacks(chores);
        dash.removeCallbacks(questions);
        dash.postDelayed(chores, 15000);
        dash.postDelayed(questions, 3000);
    }

    private final Runnable chores = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            Chores.summary(HomeActivity.this);   // after the weather has had time to arrive
            Hints.maybe(HomeActivity.this);
        }
    };

    private final Runnable questions = new Runnable() {
        @Override
        public void run() {
            if (!visible || Setup.showing() != null || Car.firstRun(HomeActivity.this)) return;   // the car setup first; the rest next time
            if (!Features.levelPicked(prefs)) {
                HomeSettings.askLevel(HomeActivity.this, true);
                return;   // one question at a time
            }
            if (HomeDialogs.askVoice(HomeActivity.this)) return;
            if (HomeDialogs.askAllFiles(HomeActivity.this)) return;
            HomeDialogs.askForTrackInfo(HomeActivity.this);
            HomeDialogs.serviceBooking(HomeActivity.this);
            HomeDialogs.askOverlay(HomeActivity.this);
            Chores.weekly(HomeActivity.this, false);
        }
    };

    @Override
    protected void onPause() {
        visible = false;
        Listen.stop(this);
        hub.stop();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == MIC_ASK) {
            if (micWaiting) {
                micWaiting = false;
                MapCard m = micFor;
                micFor = null;
                voiceSearch(m);
            }
            return;
        }
        Drive.start(this);
        hub.stop();
        hub.start();
        if (Setup.showing() != null) Setup.showing().render();
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
        Setup setup = Setup.showing();
        if (setup != null) {
            setup.back();
            return;
        }
        if (closeMusic()) return;
        if (dash.editing()) stopMoving();
        else closeSettings();
    }
}
