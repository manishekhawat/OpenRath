package org.openrath.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live data for the cards: GPS position and speed, weather (Open-Meteo, no key needed),
 * headlights (night mode or the manual setting) and the playing music / radio session.
 * Cards register as listeners and get `changed()` once a second while something changed.
 */
final class Hub implements LocationListener {

    interface Listener {
        void changed(Hub h);
    }

    static final class Media {
        String title, subtitle;
        boolean playing;
        android.graphics.Bitmap art;   // album cover, when the app gives one
        MediaController controller;
    }

    private static final long WEATHER_MS = 15 * 60 * 1000;
    private static final long FIX_TIMEOUT_MS = 5000;

    private final Context ctx;
    private final Prefs prefs;
    private final Handler handler = new Handler();
    private final List<Listener> listeners = new ArrayList<>();

    Location fix;
    private long fixAt;
    private volatile long weatherAt;
    String temp = "--°", weather = "Weather", place = "", weatherIcon = "w_cloud";
    /** Rain here in the next hour (Open-Meteo), and along the planned route (from the map). */
    String rainSoon = "";
    /** For the picture layouts: humidity, wind, air pressure, and {date, icon, max, min} for today and the next days. */
    String humidity = "", wind = "", pressure = "";
    List<String[]> forecast = new ArrayList<>();
    static String routeRain = "";
    /** Raining, or rain due within the hour, here or on the route (for monsoon mode). */
    static volatile boolean wet;
    /** Fog now, from the weather (for fog mode in the trip service). */
    static volatile boolean fog;
    Media music, radio;
    /** For OpenRath's own tuner, which has no hub of its own. */
    static Tw.Radio radioLink;
    static Xt xtLink;
    boolean mediaAccess = true;
    boolean audioActive;
    boolean online;
    boolean leftOn, rightOn;                   // turn indicators, when learnt
    private long leftAt, rightAt;
    private String[] leftSig, rightSig;                       // something is playing, even without a media session
    final List<String> sessionPkgs = new ArrayList<>();
    private final Stamp stamp = new Stamp();
    private long lastStamp;
    private boolean stamped;

    Hub(Context c, Prefs p) {
        ctx = c;
        prefs = p;
        unit = new Unit(c);
    }

    void add(Listener l) {
        listeners.add(l);
        l.changed(this);
    }

    void clearListeners() {
        listeners.clear();
    }

    void start() {
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l != null) fix = l;
        } catch (SecurityException | IllegalArgumentException e) {
            // no permission yet, or no GPS: speed shows "--"
        }
        leftSig = prefs.signal("left");
        rightSig = prefs.signal("right");
        List<String> actions = Lights.actionsFor(prefs.lightSignal());
        actions.addAll(Lights.actionsFor(leftSig));
        actions.addAll(Lights.actionsFor(rightSig));
        actions.addAll(Lights.actionsFor(prefs.signal("door")));
        actions.addAll(Lights.actionsFor(prefs.signal("belt")));
        lightsReceiver = Lights.listen(ctx, actions);
        java.util.Set<Integer> codes = new java.util.HashSet<>();
        for (String[] sig : new String[][]{prefs.lightSignal(), leftSig, rightSig, prefs.signal("door"), prefs.signal("belt")}) {
            int c = Lights.twCode(sig);
            if (c >= 0) codes.add(c);
        }
        // the stereo: a maker's add-on when there is one and it is switched on, else plain Android
        unit.stop();
        unit = Unit.pick(ctx, prefs, this);
        unit.start();
        boolean maker = unit instanceof TopwayUnit;
        if (!maker) {
            // switched off in Features: let go of the maker's services
            if (xt != null) xt.unbind();
            if (fm != null) fm.close();
            fm = null;
            xt = null;
        }
        if (maker && !codes.isEmpty()) {
            int[] a = new int[codes.size()];
            int i = 0;
            for (int c : codes) a[i++] = c;
            spy = Tw.guardedSpy(a);
        }
        if (maker && fm == null && Tw.available()) {
            fm = new Tw.Radio(new Runnable() {
                @Override
                public void run() {
                    notifyIfChanged(true);
                }
            });
        }
        if (fm != null) fm.open();
        if (maker && xt == null && Compat.isSystemApp(ctx.getPackageManager(), Xt.PKG)) {
            xt = new Xt(ctx, new Xt.Listener() {
                @Override
                public void radioChanged() {
                    notifyIfChanged(true);
                }
            });
        }
        if (xt != null) xt.bind();
        Lights.xt = xt;
        radioLink = fm;
        xtLink = xt;
        playerReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context c, android.content.Intent i) {
                // the trip service and the engine adapter send this every second: redraw only what changed
                notifyIfChanged(!Drive.CHANGED.equals(i.getAction()));
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter(Player.CHANGED);
        f.addAction(Drive.CHANGED);
        Compat.ownOnly(ctx, playerReceiver, f);
        handler.removeCallbacks(blink);
        if (leftSig != null || rightSig != null) handler.post(blink);
        handler.removeCallbacks(tick);
        mediaDirty = true;
        watchSessions();
        handler.post(tick);
        handler.removeCallbacks(reversePoll);
        handler.postDelayed(reversePoll, 1500);
    }

    private android.content.BroadcastReceiver lightsReceiver, playerReceiver;
    private Tw spy;
    /** The stereo's own parts (FM, phone, reverse): plain Android, or a maker's add-on. */
    Unit unit;
    /** The FM tuner, on Topway units (null elsewhere). */
    Tw.Radio fm;
    /** Topway's radio service (stock radio's own route), when the radio app is Topway's. */
    Xt xt;

    /** Indicators blink, so they are read five times a second and count as on for a while after. */
    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (Lights.matches(ctx, leftSig)) leftAt = now;
            if (Lights.matches(ctx, rightSig)) rightAt = now;
            boolean l = now - leftAt < 1300, r = now - rightAt < 1300;
            if (l != leftOn || r != rightOn) {
                leftOn = l;
                rightOn = r;
                notifyIfChanged(true);
            }
            handler.postDelayed(this, 200);
        }
    };

    void stop() {
        unit.stop();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(mediaNow);
        unwatchSessions();
        handler.removeCallbacks(blink);
        handler.removeCallbacks(reversePoll);
        if (spy != null) {
            spy.close();
            spy = null;
        }
        if (playerReceiver != null) {
            try {
                ctx.unregisterReceiver(playerReceiver);
            } catch (Exception ignored) {
                // was not registered
            }
            playerReceiver = null;
        }
        if (lightsReceiver != null) {
            try {
                ctx.getApplicationContext().unregisterReceiver(lightsReceiver);
            } catch (Exception ignored) {
                // was not registered
            }
            lightsReceiver = null;
        }
        try {
            ((LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE)).removeUpdates(this);
        } catch (SecurityException ignored) {
            // no location permission
        }
    }

    private long xtPolled, btPolled;
    /** What the radio service answered for btGetConnectedStatus, for Diagnostics. */
    Object btRaw;

    /** A phone connected through Android's own Bluetooth (units whose Bluetooth isn't Topway's module). */
    static boolean androidBt() {
        try {
            android.bluetooth.BluetoothAdapter a = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) return false;
            // A2DP, headset, and the car-side profiles: A2DP sink (11) and hands-free client (16)
            for (int profile : new int[]{2, 1, 11, 16})
                if (a.getProfileConnectionState(profile) == android.bluetooth.BluetoothProfile.STATE_CONNECTED) return true;
        } catch (Throwable ignored) {
            // no Bluetooth permission or adapter: say not connected
        }
        return false;
    }
    boolean reverse, btConnected;
    int phoneStatus;
    /** The last call made or received: {number, name, dir, at}, or null. */
    String[] lastCall;
    /** Called when reverse gear changes. */
    Runnable onReverse;

    /** Reverse gear, read from Topway's service a few times a second. */
    private final Runnable reversePoll = new Runnable() {
        @Override
        public void run() {
            Boolean rv = unit.reverse();
            if (rv != null) {
                boolean r = rv;
                if (r != reverse) {
                    reverse = r;
                    if (onReverse != null) onReverse.run();
                    notifyIfChanged(true);
                }
            }
            // a unit that can't tell (yet) is asked less often
            handler.postDelayed(this, rv == null ? 2000 : 400);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            readMedia();
            long now = System.currentTimeMillis();
            if (xt != null && xt.ready() && now - xtPolled > 3000) {
                xtPolled = now;
                xt.poll();
            }
            if (now - btPolled > 2000) {
                btPolled = now;
                unit.tick(prefs, Hub.this);
            }
            if (Features.on(prefs, "weather")) {
                if (fix != null && now - weatherAt > WEATHER_MS) {
                    weatherAt = now;
                    fetchWeather(fix.getLatitude(), fix.getLongitude());
                }
                showSavedWeather();
            }
            Cab.tick(ctx, prefs);
            notifyIfChanged(false);
            handler.postDelayed(this, 1000);
        }
    };

    void notifyIfChanged(boolean force) {
        // a fingerprint of everything the boxes show, instead of a long string built every second
        Stamp t = stamp.reset();
        t.add(Cab.stamp(prefs)).add(lightsOn()).add(speed()).add(temp).add(weather).add(place).add(weatherIcon).add(rainSoon).add(routeRain)
                .add(hours.length).add(hours.length > 0 ? hours[0] : 0);
        media(t, music);
        media(t, radio);
        t.add(audioActive).add(online).add(leftOn).add(rightOn).add(unit.stamp()).add(Player.mode).add(Player.playing).add(Player.busy)
                .add(Player.status).add(Player.station).add(Player.song);
        if (fix == null) t.add(false);
        else t.add(true).add(fix.getLatitude()).add(fix.getLongitude());
        t.add(Math.round(Drive.todayKm * 10)).add(Drive.todaySec / 60).add(Math.round(Drive.driveKm * 10)).add(Drive.limit).add(Drive.cameraM / 50)
                .add(Drive.over).add(Drive.parkAt).add(prefs.fuelLog().length()).add(reverse).add(btConnected).add(phoneStatus);
        if (lastCall != null) t.add(lastCall[0]).add(lastCall[1]);
        t.add(Care.alert(prefs)).add(Drive.score());
        // what the boxes read from the trip service, the engine adapter and the tyre sensors
        t.add(Math.round(Drive.kmhNow)).add(Math.round(Drive.headingNow)).add(Math.round(Drive.headingKept)).add(Math.round(Drive.altNow))
                .add(Drive.driveSec / 60).add(Math.round(Drive.driveTop)).add(Drive.alertError).add(Math.round(Drive.odoKm * 10))
                .add(Highway.on).add(Highway.held).add(Math.round(Ahead.bendM)).add(Ahead.bendDir);
        t.add(Obd.connected).add(Obd.status).add(Obd.rpm).add(Obd.kmh).add(Obd.coolant).add(Obd.load).add(Obd.fuel).add(Obd.intake)
                .add(Obd.volts).add(Obd.codesAt).add(Tpms.status);
        for (Tpms.Tyre y : Tpms.tyres) {
            if (y == null) t.add(false);
            else t.add(y.kpa).add(y.tempC).add(y.at);
        }
        long v = t.value();
        if (!force && stamped && v == lastStamp) return;
        stamped = true;
        lastStamp = v;
        for (Listener l : new ArrayList<>(listeners)) l.changed(this);
    }

    private static void media(Stamp t, Media m) {
        if (m == null) t.add(false);
        else t.add(true).add(m.title).add(m.subtitle).add(m.playing);
    }

    boolean lightsOn() {
        String mode = prefs.lights();
        if ("on".equals(mode)) return true;
        if ("off".equals(mode)) return false;
        if ("times".equals(mode)) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            return Lights.inWindow(c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE),
                    prefs.num("lightsFrom", 18 * 60 + 30), prefs.num("lightsTo", 6 * 60 + 30));
        }
        String[] sig = prefs.lightSignal();
        if (sig == null) {
            // no signal learnt: the stereo add-on may read the illumination wire itself
            Boolean u = unit.lights();
            if (u != null) return u;
        }
        if (sig != null) {
            return Lights.isOn(Lights.read(ctx, sig[0]), sig);
        }
        // no headlight wire reaches apps on this unit: like the stock launcher and weather app
        // (TwilightCalculator), count the lights as on from sunset to sunrise here
        int night = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (night == Configuration.UI_MODE_NIGHT_YES) return true;
        return fix != null && Care.dark(fix.getLatitude(), fix.getLongitude(), System.currentTimeMillis());
    }

    String speed() {
        long now = System.currentTimeMillis();
        boolean fresh = fix != null && now - fixAt < FIX_TIMEOUT_MS && fix.hasSpeed();
        float raw;
        // the trip service works the speed out even when the GPS doesn't report one
        if (now - Drive.fixAt < FIX_TIMEOUT_MS && (!fresh || Drive.fixAt > fixAt)) raw = Drive.kmhNow;
        else if (!fresh) {
            shownKmh = 0;
            // no new speed, but the GPS has a position and the car was last seen standing still
            // (parked, the GPS asks only every 15 s): 0, not "--", which means no GPS at all
            return Drive.standing(fix != null, Drive.fixAt, Drive.kmhNow) ? "0" : "--";
        } else raw = fix.getSpeed() * 3.6f;
        long t = android.os.SystemClock.elapsedRealtime();
        shownKmh = Features.on(prefs, "smoothSpeed") ? Fixes.smooth(shownKmh, raw, t - shownAt) : raw < Fixes.stillKmh ? 0 : raw;
        shownAt = t;
        return String.valueOf(Math.round(shownKmh));
    }

    /** The speed in the owner's unit (km/h or mph), or "--". */
    String speedShown() {
        String s = speed();
        return !Units.mph || s.equals("--") ? s : String.valueOf(Units.speed(Integer.parseInt(s), true));
    }

    /** Seconds since the last GPS fix, or -1. */
    long fixAge() {
        return fixAt == 0 ? -1 : (System.currentTimeMillis() - fixAt) / 1000;
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!Fixes.usable(l, fixNanos)) return;   // a replayed old fix or no real position
        fixNanos = l.getElapsedRealtimeNanos();
        fix = l;
        fixAt = System.currentTimeMillis();
    }

    private long fixNanos;
    private float shownKmh;
    private long shownAt;

    /** A live GPS fix from the last 30 seconds (not the network's rough position or an old one), or null. */
    Location liveFix() {
        return fix != null && fixAt > 0 && System.currentTimeMillis() - fixAt < 30000 ? fix : null;
    }

    @Override public void onStatusChanged(String p, int s, Bundle b) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}

    // ------------------------------------------------------------------ media

    /**
     * The music apps' sessions are read again when one of them says something changed (a new song,
     * play or pause, a session started or ended), and otherwise every 5 seconds in case an app
     * doesn't say; the internet and "something is playing" are checked every few seconds.
     */
    private static final long MEDIA_MS = 5000, AUDIO_MS = 2000, ONLINE_MS = 5000;
    private boolean mediaDirty = true;
    private long mediaAt, audioAt, onlineAt;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged;
    private final List<MediaController> watched = new ArrayList<>();

    private final MediaController.Callback mediaChanged = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            mediaSoon();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            mediaSoon();
        }

        @Override
        public void onSessionDestroyed() {
            mediaSoon();
        }
    };

    private final Runnable mediaNow = new Runnable() {
        @Override
        public void run() {
            readMedia();
            notifyIfChanged(false);
        }
    };

    /** A burst of changes is read once, a moment later. */
    private void mediaSoon() {
        mediaDirty = true;
        handler.removeCallbacks(mediaNow);
        handler.postDelayed(mediaNow, 100);
    }

    private void watchSessions() {
        if (sessionsChanged != null) return;
        try {
            MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            sessionsChanged = new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    mediaSoon();
                }
            };
            m.addOnActiveSessionsChangedListener(sessionsChanged, new ComponentName(ctx, MediaListener.class), handler);
        } catch (Throwable e) {
            sessionsChanged = null;   // no notification access yet: the 5-second check finds the sessions
        }
    }

    private void unwatchSessions() {
        if (sessionsChanged != null) {
            try {
                ((MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE)).removeOnActiveSessionsChangedListener(sessionsChanged);
            } catch (Throwable e) {
                Diag.note("media sessions unwatch", e);
            }
            sessionsChanged = null;
        }
        for (MediaController c : watched) {
            try {
                c.unregisterCallback(mediaChanged);
            } catch (Throwable e) {
                Diag.note("media session unwatch", e);
            }
        }
        watched.clear();
    }

    /** Follow the changes of these sessions (and stop following the ones that ended). */
    private void watch(List<MediaController> sessions) {
        boolean same = sessions.size() == watched.size();
        for (int i = 0; same && i < sessions.size(); i++) same = sessions.get(i).getSessionToken().equals(watched.get(i).getSessionToken());
        if (same) return;
        for (MediaController c : watched) {
            try {
                c.unregisterCallback(mediaChanged);
            } catch (Throwable e) {
                Diag.note("media session unwatch", e);
            }
        }
        watched.clear();
        for (MediaController c : sessions) {
            try {
                c.registerCallback(mediaChanged, handler);
                watched.add(c);
            } catch (Throwable e) {
                Diag.note("media session watch", e);
            }
        }
    }

    private void readMedia() {
        long now = System.currentTimeMillis();
        if (now - onlineAt >= ONLINE_MS || onlineAt > now) {
            onlineAt = now;
            online = Player.online(ctx);
        }
        if (now - audioAt >= AUDIO_MS || audioAt > now) {
            audioAt = now;
            try {
                audioActive = ((android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE)).isMusicActive();
            } catch (Exception ignored) {
                // polled often: keep the last answer
            }
        }
        if (!mediaDirty && now - mediaAt < MEDIA_MS && mediaAt <= now) return;
        mediaDirty = false;
        mediaAt = now;
        List<MediaController> sessions;
        try {
            MediaSessionManager m = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            sessions = m.getActiveSessions(new ComponentName(ctx, MediaListener.class));
            mediaAccess = true;
        } catch (SecurityException e) {
            mediaAccess = false;
            music = radio = null;
            return;
        }
        if (sessionsChanged == null) watchSessions();   // notification access was just given
        watch(sessions);
        String radioPkg = prefs.app("media:radio");
        String musicPkg = prefs.app("media:music");
        MediaController r = null, mu = null;
        sessionPkgs.clear();
        for (MediaController c : sessions) {
            String pkg = c.getPackageName();
            if (pkg.equals(ctx.getPackageName())) continue;   // our own player
            sessionPkgs.add(pkg);
            if (pkg.equals(radioPkg) || (radioPkg == null && looksLikeRadio(pkg))) {
                if (r == null) r = c;
            } else if (musicPkg == null ? mu == null || (!playing(mu) && playing(c)) : pkg.equals(musicPkg) && mu == null) {
                mu = c;   // the chosen music app, or else the app that's playing (the first one if none is)
            }
        }
        radio = info(r);
        music = info(mu);
    }

    static boolean looksLikeRadio(String pkg) {
        String p = pkg.toLowerCase(Locale.US);
        return p.contains("radio") || p.endsWith(".fm") || p.contains(".fm.") || p.contains("fmradio");
    }

    private static String artUri;
    private static android.graphics.Bitmap artFromUri;

    /**
     * Whether OpenRath opens an art address another app's media session gives: content: only, which
     * the other app must share on purpose. A file: address would be opened with OpenRath's own
     * storage access, so it isn't.
     */
    static boolean artUriOk(String u) {
        return u != null && u.regionMatches(true, 0, "content:", 0, 8);
    }

    /** The picture at the app's art address (content:); kept while the address stays the same. */
    private android.graphics.Bitmap artFrom(MediaMetadata md) {
        String u = md.getString(MediaMetadata.METADATA_KEY_ART_URI);
        if (u == null) u = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (u == null) u = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        if (!artUriOk(u)) return null;
        if (u.equals(artUri)) return artFromUri;
        artUri = u;
        artFromUri = null;
        try {
            android.net.Uri uri = android.net.Uri.parse(u);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            java.io.InputStream in = ctx.getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= 512) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            in = ctx.getContentResolver().openInputStream(uri);
            artFromUri = android.graphics.BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
        } catch (Throwable ignored) {   // no permission to read it, or gone
        }
        return artFromUri;
    }

    private static boolean playing(MediaController c) {
        PlaybackState st = c.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }

    private Media info(MediaController c) {
        if (c == null) return null;
        Media m = new Media();
        m.controller = c;
        MediaMetadata md = c.getMetadata();
        if (md != null) {
            m.title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (m.title == null) m.title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            m.subtitle = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (m.subtitle == null) m.subtitle = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (m.subtitle == null) m.subtitle = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            if (m.subtitle == null) m.subtitle = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
            // the biggest picture the app gives; many give a small one, a bigger one under another key, or only an address
            for (String k : new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON}) {
                android.graphics.Bitmap b = md.getBitmap(k);
                if (b != null && (m.art == null || b.getWidth() * b.getHeight() > m.art.getWidth() * m.art.getHeight())) m.art = b;
            }
            if (m.art == null || m.art.getWidth() < 200) {
                android.graphics.Bitmap b = artFrom(md);
                if (b != null && (m.art == null || b.getWidth() > m.art.getWidth())) m.art = b;
            }
        }
        PlaybackState st = c.getPlaybackState();
        m.playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        return m;
    }

    // ------------------------------------------------------------------ weather

    private static String get(String url) throws java.io.IOException {
        HttpURLConnection c = Compat.https(url);
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            try {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return sb.toString();
            } finally {
                r.close();
            }
        } finally {
            c.disconnect();
        }
    }

    /** One weather reading, from the live answer or picked out of the saved forecast. */
    static final class Wx {
        String temp, desc, icon, rain = "", hum = "", wind = "", pressure = "";
        List<String[]> days = new ArrayList<>();
        /** The next 24 hours' temperatures and rain (mm), from the hour that is now; empty if unknown. */
        float[] hours = new float[0], hoursRain = new float[0];
    }

    /**
     * Read Open-Meteo's answer. Live: its "current" block. From the saved copy (savedAt > 0): the hour
     * that is now in its hourly forecast, so the box keeps showing a sensible reading offline for the
     * next five days. Null when the saved copy doesn't reach now.
     */
    static Wx readWeather(JSONObject all, long nowMs, boolean saved) throws org.json.JSONException {
        Wx w = new Wx();
        int offset = all.optInt("utc_offset_seconds", 0);
        String today = Weather.hourKey(nowMs, offset).substring(0, 10);
        JSONObject daily = all.optJSONObject("daily");
        if (daily != null) {
            org.json.JSONArray dt = daily.optJSONArray("time"), dc = daily.optJSONArray("weather_code"),
                    dmax = daily.optJSONArray("temperature_2m_max"), dmin = daily.optJSONArray("temperature_2m_min");
            for (int k = 0; dt != null && k < dt.length(); k++) {
                if (dt.optString(k).compareTo(today) < 0) continue;   // a saved copy: days gone by are dropped
                w.days.add(new String[]{dt.optString(k), weatherIcon(dc.optInt(k), true),
                        Units.temp(dmax.optDouble(k)), Units.temp(dmin.optDouble(k))});
            }
        }
        JSONObject hr = all.optJSONObject("hourly");
        org.json.JSONArray hts = hr == null ? null : hr.optJSONArray("time");
        if (hts != null) {
            String[] times = new String[hts.length()];
            for (int k = 0; k < times.length; k++) times[k] = hts.optString(k);
            int from = Weather.slot(times, Weather.hourKey(nowMs, offset));
            w.hours = Weather.next(numbers(hr.optJSONArray("temperature_2m")), from, 24);
            w.hoursRain = Weather.next(numbers(hr.optJSONArray("precipitation")), from, 24);
        }
        JSONObject cur;
        if (!saved) {
            cur = all.getJSONObject("current");
            // rain in the next hour, from the 15-minute forecast (slot 0 = now)
            JSONObject m15 = all.optJSONObject("minutely_15");
            org.json.JSONArray mm = m15 == null ? null : m15.optJSONArray("precipitation");
            if (mm != null) {
                for (int k = 0; k < mm.length() && k < 5; k++) {
                    if (mm.optDouble(k) >= 0.3) {
                        w.rain = k == 0 ? "Raining now" : "Rain in about " + k * 15 + " min";
                        break;
                    }
                }
            }
        } else {
            JSONObject hourly = all.optJSONObject("hourly");
            org.json.JSONArray ht = hourly == null ? null : hourly.optJSONArray("time");
            if (ht == null) return null;
            String[] times = new String[ht.length()];
            for (int k = 0; k < times.length; k++) times[k] = ht.optString(k);
            int i = Weather.slot(times, Weather.hourKey(nowMs, offset));
            if (i < 0) return null;
            cur = new JSONObject();
            for (String key : new String[]{"temperature_2m", "weather_code", "is_day", "relative_humidity_2m", "wind_speed_10m",
                    "wind_direction_10m", "surface_pressure"}) {
                org.json.JSONArray a = hourly.optJSONArray(key);
                if (a != null && i < a.length() && !a.isNull(i)) cur.put(key, a.get(i));
            }
            org.json.JSONArray pr = hourly.optJSONArray("precipitation");
            if (pr != null) {
                if (i < pr.length() && pr.optDouble(i) >= 0.3) w.rain = "Rain likely this hour";
                else if (i + 1 < pr.length() && pr.optDouble(i + 1) >= 0.3) w.rain = "Rain likely within 2 hours";
            }
            if (!cur.has("temperature_2m") || !cur.has("weather_code")) return null;
        }
        int code = cur.getInt("weather_code");
        boolean day = cur.optInt("is_day", 1) == 1;
        w.temp = Units.temp(cur.getDouble("temperature_2m"));
        w.desc = describeWeather(code);
        w.icon = weatherIcon(code, day);
        if (cur.has("relative_humidity_2m")) w.hum = Math.round(cur.optDouble("relative_humidity_2m")) + "%";
        if (cur.has("wind_speed_10m")) w.wind = Math.round(cur.optDouble("wind_speed_10m")) + " km/h "
                + Layouts.compass(cur.optDouble("wind_direction_10m"));
        if (cur.has("surface_pressure")) w.pressure = Math.round(cur.optDouble("surface_pressure")) + " hPa";
        return w;
    }

    private static double[] numbers(org.json.JSONArray a) {
        if (a == null) return new double[0];
        double[] out = new double[a.length()];
        for (int k = 0; k < out.length; k++) out[k] = a.optDouble(k);
        return out;
    }

    /** The next 24 hours (Wx.hours), for the weather box's curve. */
    float[] hours = new float[0], hoursRain = new float[0];

    private void showWeather(Wx w) {
        hours = w.hours;
        hoursRain = w.hoursRain;
        temp = w.temp;
        weather = w.desc;
        rainSoon = w.rain;
        humidity = w.hum;
        wind = w.wind;
        pressure = w.pressure;
        forecast = w.days;
        wet = !w.rain.isEmpty();
        fog = "w_fog".equals(w.icon);
        weatherIcon = w.icon;
    }

    /** When the saved forecast is on screen: when it was fetched (0 = the weather is live). */
    long weatherSaved;
    private long savedShownAt;

    /** No internet (or no GPS yet): show the hour that is now from the last forecast kept. */
    /** Fetch the weather again on the next tick (the temperature unit changed). */
    void refreshWeather() {
        weatherAt = 0;
    }

    /** The saved forecast, read once rather than every minute while offline. */
    private String savedBody;
    private JSONObject savedJson;

    private void showSavedWeather() {
        long now = System.currentTimeMillis();
        if (weatherSaved == 0 && weatherLive > 0 && now - weatherLive < WEATHER_MS * 2) return;   // live is still fresh
        if (now - savedShownAt < 60000) return;
        savedShownAt = now;
        String body = prefs.str("wxSaved", "");
        if (body.isEmpty()) return;
        try {
            if (!body.equals(savedBody)) {
                savedJson = new JSONObject(body);
                savedBody = body;
            }
            Wx w = readWeather(savedJson, now, true);
            if (w == null) return;
            showWeather(w);
            weatherSaved = prefs.longNum("wxSavedAt", now);
            if (place.isEmpty()) place = prefs.str("city", "");
        } catch (Exception e) {
            Diag.note("saved weather", e);
        }
    }

    private long weatherLive;

    private void fetchWeather(final double lat, final double lon) {
        Bg.run(new Runnable() {
            @Override
            public void run() {
                try {
                    // the hourly part is for offline: the box picks the hour that is now from it
                    final String body = get(String.format(Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,is_day,relative_humidity_2m,wind_speed_10m,wind_direction_10m,surface_pressure"
                                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=5"
                                    + "&hourly=temperature_2m,weather_code,is_day,relative_humidity_2m,wind_speed_10m,wind_direction_10m,surface_pressure,precipitation"
                                    + "&minutely_15=precipitation&forecast_minutely_15=5&timezone=auto",
                            lat, lon));
                    final Wx wx = readWeather(new JSONObject(body), System.currentTimeMillis(), false);
                    // air quality, from the same free service
                    int air = -1;
                    if (Features.on(prefs, "aqi")) {
                        try {
                            JSONObject aq = new JSONObject(get(Air.url(lat, lon))).getJSONObject("current");
                            air = Air.index(aq.optDouble("pm2_5", Double.NaN), aq.optDouble("pm10", Double.NaN));
                        } catch (Exception e) {
                            Diag.note("air quality fetch", e);
                        }
                    }
                    final int aqiNow = air;
                    final String p = placeName(lat, lon);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Air.aqi = aqiNow;
                            long nowMs = System.currentTimeMillis();
                            String say = Air.alert(aqiNow, nowMs, prefs.longNum("aqiTold", 0));
                            if (say != null) {
                                prefs.putLong("aqiTold", nowMs);
                                android.widget.Toast.makeText(ctx, Hindi.f("Air quality: %1$s (AQI %2$s)", Hindi.t(Air.band(aqiNow)), aqiNow),
                                        android.widget.Toast.LENGTH_LONG).show();
                                if (ctx instanceof Cards.Host) ((Cards.Host) ctx).speak(say);
                            }
                            showWeather(wx);
                            weatherSaved = 0;
                            weatherLive = nowMs;
                            prefs.put("wxSaved", body);
                            prefs.putLong("wxSavedAt", nowMs);
                            if (p != null) {
                                place = p;
                                prefs.put("city", p);   // fill-ups remember the city's petrol price
                            }
                        }
                    });
                } catch (Exception e) {
                    weatherAt = System.currentTimeMillis() - WEATHER_MS + 60000;   // retry in a minute
                }
            }
        });
    }

    /** The last place name found, for about a kilometre around where it was asked. */
    private static String placeKey, placeKept;

    private String placeName(double lat, double lon) {
        String key = Math.round(lat * 100) + "," + Math.round(lon * 100);
        synchronized (Hub.class) {
            if (key.equals(placeKey)) return placeKept;
        }
        String p = findPlace(lat, lon);
        if (p != null) {
            synchronized (Hub.class) {
                placeKey = key;
                placeKept = p;
            }
        }
        return p;
    }

    private String findPlace(double lat, double lon) {
        try {
            List<Address> a = new Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (a != null && !a.isEmpty()) {
                String p = a.get(0).getLocality();
                return p != null ? p : a.get(0).getSubAdminArea();
            }
        } catch (Exception ignored) {
            // many units have no geocoder: ask the web below
        }
        // many head units have no geocoder; ask a free reverse-geocoding service instead
        try {
            URL u = new URL(String.format(Locale.US,
                    "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.4f&longitude=%.4f&localityLanguage=en",
                    lat, lon));
            JSONObject j = new JSONObject(get(u.toString()));
            String p = j.optString("city", "");
            if (p.isEmpty()) p = j.optString("locality", "");
            if (!p.isEmpty()) return p;
        } catch (Exception e) {
            Diag.note("place lookup", e);
        }
        return null;
    }

    /** The rain line for the screen, in the chosen language (rainSoon itself stays English for the voice). */
    static String showRain(String r) {
        if (r == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Rain in about (\\d+) min").matcher(r);
        return m.matches() ? Hindi.f("Rain in about %1$s min", m.group(1)) : Hindi.t(r);
    }

    // WMO weather codes, as used by Open-Meteo
    static String describeWeather(int c) {
        if (c == 0) return "Clear";
        if (c <= 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c <= 48) return "Fog";
        if (c <= 57) return "Drizzle";
        if (c <= 67) return "Rain";
        if (c <= 77) return "Snow";
        if (c <= 82) return "Showers";
        if (c <= 86) return "Snow showers";
        return "Thunderstorm";
    }

    static String weatherIcon(int c, boolean day) {
        if (c == 0) return day ? "w_sun" : "w_moon";
        if (c <= 2) return day ? "w_partly" : "w_moon";
        if (c == 3) return "w_cloud";
        if (c <= 48) return "w_fog";
        if (c <= 67 || (c >= 80 && c <= 82)) return "w_rain";
        if (c <= 86) return "w_snow";
        return "w_storm";
    }
}
