package org.openrath.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Runs while the unit is on, even behind other apps: the trip computer (today's distance, drive
 * time, average speed, and a GPS odometer for the fuel log), the parking spot, and speed limit and
 * speed camera alerts. Limits and cameras come from OpenStreetMap through the free Overpass API;
 * many Indian roads have no limit tagged, so the box shows one only where the map has it.
 */
public class Drive extends Service implements LocationListener, TextToSpeech.OnInitListener {

    static final String CHANGED = "org.openrath.launcher.DRIVE_CHANGED";
    /** Sent after the set time of driving without a break: the dashboard suggests a rest stop. */
    static final String REST = "org.openrath.launcher.REST_STOP";
    /** Sent when the petrol is running low: the dashboard lists the pumps ahead. */
    static final String FUEL_LOW = "org.openrath.launcher.FUEL_LOW";
    /** A crash-like stop: the dashboard shows the accident-help countdown. */
    static final String CRASH = "org.openrath.launcher.CRASH";
    static final String HIGHWAY = "org.openrath.launcher.HIGHWAY";
    // drives for the mileage coach, and the break reminder
    static final Coach.Tracker drives = new Coach.Tracker();
    private long restAt;

    // trip computer
    static double todayKm, driveKm, odoKm;
    static long todaySec, driveSec;
    // parking
    static double parkLat, parkLon;
    static long parkAt;
    // speed alerts
    static int limit;                 // km/h, 0 = not known here
    static int cameraM = -1;          // metres to a speed camera ahead, -1 = none near
    static boolean over;              // above the limit (or the warning speed)
    static String alertError = "";
    // the latest speed, for the dashboard (some units' GPS gives no speed, so it's worked out)
    static float kmhNow;
    static long fixAt;
    // where the car is, and which way it's heading (-1 = not known)
    static volatile double latNow, lonNow, headingNow = -1;
    private long fuelCheckAt, crashAt;
    private final Ahead ahead = new Ahead();
    private final Hill hill = new Hill();
    private final Highway highway = new Highway();
    // learnt from your drives: speed breakers and stops
    private final Spots bumps = new Spots(0.0003, 12, 400), waits = new Spots(0.0005, 10, 300);
    private boolean spotsLoaded;
    private long driveId, stopStart = -1, darkSince = -1, doorToldAt, beltToldAt;
    private double stopLat, stopLon, stopHeading = -1, lastHeading = -1;
    private int beltTold;
    private boolean lightsTold;
    private final java.util.Set<String> spotTold = new java.util.HashSet<>();
    static volatile String soundNow = "";
    private long stillSince = -1;
    private String musicDone = "";   // what the parked-music saver already did this stop
    private final Ahead.Fetch overpass = new Ahead.Fetch() {
        @Override
        public String post(String q) throws Exception {
            return Drive.post("https://overpass-api.de/api/interpreter", "data=" + URLEncoder.encode(q, "UTF-8"));
        }
    };
    // driving score for today
    static int todayBrakes, todayAccels;
    static long todaySpeedSec;

    /** 100 = smooth and within limits; harsh braking, hard starts and speeding take points off. */
    static int score() {
        return (int) Math.max(0, Math.min(100, 100 - 4 * todayBrakes - 2 * todayAccels - todaySpeedSec / 30));
    }

    /** Day by day: {"2026-10-12": [km, seconds, harsh brakes, hard starts, seconds over the limit, score]}. */
    static JSONObject history(Context c) {
        try {
            return new JSONObject(c.getSharedPreferences("drive", MODE_PRIVATE).getString("hist", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String day;
    private float prevKmh;
    private long prevAt, lastHarsh;

    static void start(Context c) {
        // Android 14 lets a location service start only once location is allowed; the permission
        // answer starts it again (HomeActivity.onRequestPermissionsResult)
        if (Build.VERSION.SDK_INT >= 34 && c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Intent i = new Intent(c, Drive.class);
        try {
            c.startForegroundService(i);
        } catch (Throwable ignored) {
        }
    }

    static double avgKmh() {
        return todaySec < 60 ? 0 : todayKm / (todaySec / 3600.0);
    }

    private SharedPreferences sp;
    private Prefs prefs;
    private final Handler handler = new Handler();
    private Location last, stopAt;
    private long lastAt, stoppedSince, savedAt;   // lastAt: the last fix, on the elapsedRealtime clock
    private boolean moved;
    private TextToSpeech tts;
    private boolean ttsReady;
    private ToneGenerator tone;

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("drive", MODE_PRIVATE);
        prefs = new Prefs(this);
        Car.apply(prefs);
        odoKm = sp.getFloat("odo", 0);
        parkLat = sp.getFloat("parkLat", 0);
        parkLon = sp.getFloat("parkLon", 0);
        parkAt = sp.getLong("parkAt", 0);
        // the unit switched off with the ignition: the last place we saw is where the car is
        long lastFixAt = sp.getLong("lastAt", 0);
        if (lastFixAt > parkAt) {
            parkLat = sp.getFloat("lastLat", 0);
            parkLon = sp.getFloat("lastLon", 0);
            parkAt = lastFixAt;
            sp.edit().putFloat("parkLat", (float) parkLat).putFloat("parkLon", (float) parkLon).putLong("parkAt", parkAt).apply();
        }
        loadToday();
        try {
            tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        } catch (Throwable ignored) {
        }
        tts = new TextToSpeech(this, this);
        screen = new Resume.Screen();
        android.content.IntentFilter sf = new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF);
        Compat.ownOnly(this, screen, sf);
    }

    private Resume.Screen screen;
    private boolean hotWarned;
    private final Tracks.Timer timer = new Tracks.Timer();
    // volume with speed: the owner's own level, and what we added on top
    private int volBase = -1, volAdded, volSet = -1;
    private long volAt;

    /** Start or stop the optional parts to match Settings > Features. */
    private void features() {
        if (Features.on(prefs, "obd")) Obd.start(this, prefs);
        else Obd.stop();
        if (Features.on(prefs, "tpms")) Tpms.start(this, prefs);
        else Tpms.stop();
        if (Features.on(prefs, "backup")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Backup.daily(Drive.this, prefs);
                }
            }).start();
        }
        if (Features.on(prefs, "gpx")) Tracks.prune(this);
        Eq.apply(this, prefs, 0);
        if (!Features.on(prefs, "speedVolume")) speedVolume(0);
        if (Features.on(prefs, "dashcam")) Dashcam.start(this, prefs);
        else Dashcam.stop();
        Quiet.apply(this, prefs);
    }

    /** Raise the volume a step at a time with speed, and back down when slower. */
    private void speedVolume(float kmh) {
        long now = System.currentTimeMillis();
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volSet >= 0 && cur != volSet) volBase = cur - volAdded;   // the owner turned it: that's their level now
            if (volBase < 0) volBase = cur;
            int per = new int[]{0, 30, 20, 15}[Math.max(0, Math.min(3, prefs.num("speedVolPer", 2)))];
            int want = Features.on(prefs, "speedVolume") ? Tracks.extraSteps(kmh, per) : 0;
            if (want == volAdded && volSet >= 0) return;
            if (now - volAt < 4000 && want != 0) return;   // no pumping up and down at a steady speed
            int target = Math.max(0, Math.min(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), volBase + want));
            volAdded = target - volBase;
            volSet = target;
            volAt = now;
            if (target != cur) am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        } catch (Throwable ignored) {
        }
    }


    @Override
    public void onInit(int status) {
        ttsReady = status == TextToSpeech.SUCCESS;
        if (ttsReady) {
            tts.setLanguage(new Locale("en", "IN"));
            Duck.attach(this, prefs, tts);
        }
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        foreground();
        features();
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            lm.removeUpdates(this);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        } catch (Throwable ignored) {
            // no location permission yet: HomeActivity starts us again once it's granted
        }
        return START_STICKY;
    }

    private void foreground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        nm.createNotificationChannel(new NotificationChannel("drive", Hindi.t("Trip computer"), NotificationManager.IMPORTANCE_MIN));
        b = new Notification.Builder(this, "drive");
        b.setSmallIcon(getApplicationInfo().icon).setContentTitle("OpenRath")
                .setContentText(Hindi.t("Trip computer and speed alerts")).setOngoing(true);
        try {
            startForeground(8, b.build());
        } catch (RuntimeException e) {
            stopSelf();   // not allowed now (Android 14 without location); started again once it is
        }
    }

    // ------------------------------------------------------------------ trip computer

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private void loadToday() {
        day = today();
        if (today().equals(sp.getString("day", ""))) {
            todayKm = sp.getFloat("dayKm", 0);
            todaySec = sp.getLong("daySec", 0);
            todayBrakes = sp.getInt("dayBrakes", 0);
            todayAccels = sp.getInt("dayAccels", 0);
            todaySpeedSec = sp.getLong("daySpeed", 0);
        } else {
            todayKm = 0;
            todaySec = 0;
            todayBrakes = todayAccels = 0;
            todaySpeedSec = 0;
        }
    }

    private void save() {
        JSONObject hist = history(this);
        try {
            hist.put(day, new JSONArray().put(Math.round(todayKm * 10) / 10.0).put(todaySec).put(todayBrakes)
                    .put(todayAccels).put(todaySpeedSec).put(score()));
            // keep about 14 months
            if (hist.length() > 430) {
                List<String> keys = new ArrayList<>();
                java.util.Iterator<String> it = hist.keys();
                while (it.hasNext()) keys.add(it.next());
                java.util.Collections.sort(keys);
                for (int i = 0; i < keys.size() - 430; i++) hist.remove(keys.get(i));
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString("day", day).putFloat("dayKm", (float) todayKm).putLong("daySec", todaySec)
                .putInt("dayBrakes", todayBrakes).putInt("dayAccels", todayAccels).putLong("daySpeed", todaySpeedSec)
                .putString("hist", hist.toString()).putFloat("odo", (float) odoKm).apply();
    }

    @Override
    public void onLocationChanged(Location l) {
        long now = System.currentTimeMillis();
        // time between fixes on a clock that never jumps: stereos often set their clock from GPS or
        // the network just after the first fix, right when a drive (and a 0–100 run) starts
        long tick = android.os.SystemClock.elapsedRealtime();
        if (!today().equals(day)) {   // past midnight: close yesterday, start today
            save();
            day = today();
            todayKm = 0;
            todaySec = 0;
            todayBrakes = todayAccels = 0;
            todaySpeedSec = 0;
        }
        float kmh = l.hasSpeed() ? l.getSpeed() * 3.6f : 0;
        if ((!l.hasSpeed() || kmh == 0) && last != null && tick - lastAt > 0 && tick - lastAt < 5000) {
            float dd = last.distanceTo(l);
            if (dd > 2) kmh = Math.max(kmh, dd / ((tick - lastAt) / 1000f) * 3.6f);
        }
        int obd = Obd.speedNow();
        if (obd >= 0) kmh = obd;   // the car's own speedometer reading beats GPS
        kmhNow = kmh;
        fixAt = now;
        latNow = l.getLatitude();
        lonNow = l.getLongitude();
        headingNow = l.hasBearing() && kmh > 5 ? l.getBearing() : -1;
        if (Features.on(prefs, "gpx")) Tracks.point(this, l.getLatitude(), l.getLongitude(), l.getAltitude(), kmh, now);
        if (Features.on(prefs, "accel")) {
            float t100 = timer.feed(kmh, tick);
            if (t100 > 0) accelDone(t100, timer.result60);
        }
        speedVolume(kmh);
        if (Features.on(prefs, "obd") && Obd.connected && Obd.coolant > 0) {
            int hot = prefs.num("obdHot", 105);
            if (Obd.coolant >= hot && !hotWarned) {
                hotWarned = true;
                if (ttsReady) tts.speak("Engine temperature is high. Pull over safely when you can.", TextToSpeech.QUEUE_ADD, null, "hot");
            } else if (Obd.coolant < hot - 5) hotWarned = false;
        }
        // harsh braking / hard starts: speed change over about a second
        boolean hardStart = false, hardBrake = false;
        if (prevAt != 0 && Rescue.crash(prevKmh, kmh, now - prevAt) && now - crashAt > 120000 && Features.on(prefs, "accidentHelp")) {
            crashAt = now;
            if (Features.on(prefs, "dashcam")) Dashcam.protect();
            Intent ci = new Intent(this, HomeActivity.class).setAction(CRASH).putExtra("lat", latNow).putExtra("lon", lonNow)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                startActivity(ci);
            } catch (Throwable t) {
                sendBroadcast(new Intent(CRASH).setPackage(getPackageName()).putExtra("lat", latNow).putExtra("lon", lonNow));
            }
        }
        if (kmh > 8 && driveId == 0) driveId = now;
        if (headingNow >= 0) lastHeading = headingNow;
        if (prevAt != 0 && now - prevAt <= 2500 && now - prevAt >= 500 && Features.on(prefs, "bumps")) {
            float r = (kmh - prevKmh) / ((now - prevAt) / 1000f);
            if (r < -8 && prevKmh >= 25 && kmh <= 22 && lastHeading >= 0 && driveId > 0) {
                loadSpots();
                bumps.add(latNow, lonNow, lastHeading, driveId, 1);
            }
        }
        if (prevAt != 0 && now - prevAt <= 2500 && now - prevAt >= 500) {
            float rate = (kmh - prevKmh) / ((now - prevAt) / 1000f);   // km/h per second
            if (now - lastHarsh > 5000) {
                if (rate < -12 && prevKmh > 20) {
                    hardBrake = true;
                    lastHarsh = now;
                    if (Features.on(prefs, "score")) todayBrakes++;
                    if (Features.on(prefs, "dashcam")) Dashcam.protect();
                } else if (rate > 11) {
                    hardStart = true;
                    lastHarsh = now;
                    if (Features.on(prefs, "score")) todayAccels++;
                }
            }
            if (over) todaySpeedSec += (now - prevAt) / 1000;
        }
        prevKmh = kmh;
        prevAt = now;
        double movedKm = 0;
        if (last != null && l.getAccuracy() < 40) {
            float d = last.distanceTo(l);
            long dt = tick - lastAt;
            // count real movement only: not GPS drift while parked, not jumps after a gap
            if (kmh > 4 && d < 70 * Math.max(1, dt / 1000f)) {
                movedKm = d / 1000.0;
                todayKm += d / 1000.0;
                driveKm += d / 1000.0;
                odoKm += d / 1000.0;
                if (dt < 5000) {
                    todaySec += dt / 1000;
                    driveSec += dt / 1000;
                }
            }
        }
        last = l;
        lastAt = tick;
        Coach.Trip done = drives.feed(now, kmh, movedKm, hardStart, hardBrake, l.getLatitude(), l.getLongitude());
        if (done != null) {
            if (Features.on(prefs, "coach") || Features.on(prefs, "commute")) Coach.save(prefs, done);
            restAt = 0;   // a real stop: the break clock starts again
            Monsoon.reset();
            ahead.reset();
            hill.reset();
            driveId = 0;
            spotTold.clear();
            lightsTold = false;
            beltTold = 0;
        }
        restStop(now);
        Valet.enforce(this, prefs);
        Quiet.apply(this, prefs);
        String flood = Monsoon.check(prefs, Hub.wet || !Hub.routeRain.isEmpty(), latNow, lonNow, headingNow, kmh);
        if (flood != null && ttsReady) tts.speak(flood, TextToSpeech.QUEUE_ADD, null, "flood");
        // the road ahead: tolls, one-way streets, schools and hospitals, bends in fog
        List<String> ahd = ahead.feed(latNow, lonNow, headingNow, kmh, Features.on(prefs, "tollAhead"), Features.on(prefs, "wrongWay"),
                Features.on(prefs, "zones"), Fog.now(prefs), Features.on(prefs, "overtake"), Features.on(prefs, "tolls") ? prefs.num("tollRate", 100) : 0, overpass,
                java.util.Calendar.getInstance());
        for (String s : ahd) {
            if (s.startsWith("Careful")) beep();
            if (ttsReady) tts.speak(s, TextToSpeech.QUEUE_ADD, null, "ahead");
        }
        // hill (ghat) roads, from the GPS height
        if (Features.on(prefs, "hill") && l.hasAltitude() && l.getAccuracy() < 30 && kmh > 8) {
            String tip = hill.feed(driveKm * 1000, l.getAltitude());
            if (tip != null && ttsReady) tts.speak(tip, TextToSpeech.QUEUE_ADD, null, "hill");
        }
        // highway mode: messages wait while cruising
        if (Features.on(prefs, "highway")) {
            int hw = highway.feed(kmh, now);
            if (hw != 0) {
                if (ttsReady) tts.speak(hw > 0 ? "Highway mode on. Messages will wait until you slow down." : Highway.endText(Highway.held),
                        TextToSpeech.QUEUE_ADD, null, "highway");
                sendBroadcast(new Intent(HIGHWAY).setPackage(getPackageName()));
            }
        } else if (Highway.on) {
            highway.reset();
            sendBroadcast(new Intent(HIGHWAY).setPackage(getPackageName()));
        }
        parkedMusic(kmh, now);
        learnt(kmh, now);
        carChecks(kmh, now);
        soundProfile();
        // holiday traffic, once a day, when the car reaches highway speed
        if (kmh > 60 && Features.on(prefs, "holidayTraffic") && !today().equals(prefs.str("holidayTold", ""))) {
            prefs.put("holidayTold", today());
            String h = Holidays.rush(java.util.Calendar.getInstance());
            if (h != null && ttsReady)
                tts.speak("It's " + h + ". Highways and toll plazas will be busier than usual, so allow extra time.",
                        TextToSpeech.QUEUE_ADD, null, "holiday");
        }
        if (now - fuelCheckAt > 30000 && kmh > 5) {
            fuelCheckAt = now;
            String low = FuelPlan.check(prefs);
            if (low != null) {
                if (ttsReady) tts.speak(low, TextToSpeech.QUEUE_ADD, null, "fuel");
                sendBroadcast(new Intent(FUEL_LOW).setPackage(getPackageName()));
            }
        }
        if (Features.on(prefs, "parking")) parking(l, kmh, now);
        if (Features.on(prefs, "speedAlerts") || Valet.on(prefs)) speedAlerts(l, kmh);
        else {
            limit = 0;
            cameraM = -1;
            over = false;
        }
        if (now - savedAt > 15000) {
            savedAt = now;
            save();
            if (volSet < 0 || volAdded == 0) Resume.saveVolume(this, prefs);
            sp.edit().putFloat("lastLat", (float) l.getLatitude()).putFloat("lastLon", (float) l.getLongitude())
                    .putLong("lastAt", now).apply();
            if (spotsLoaded) {
                prefs.put("bumps", bumps.save());
                prefs.put("waits", waits.save());
            }
        }
        changed();
    }

    /** Reset today's figures (from the box's menu). */
    static void resetToday(Context c) {
        todayKm = 0;
        todaySec = 0;
        todayBrakes = todayAccels = 0;
        todaySpeedSec = 0;
        c.getSharedPreferences("drive", MODE_PRIVATE).edit().putFloat("dayKm", 0).putLong("daySec", 0)
                .putInt("dayBrakes", 0).putInt("dayAccels", 0).putLong("daySpeed", 0).apply();
    }

    // ------------------------------------------------------------------ parking

    private void parking(Location l, float kmh, long now) {
        if (kmh > 8) {
            moved = true;
            stoppedSince = 0;
            return;
        }
        if (!moved) return;
        if (stoppedSince == 0) {
            stoppedSince = now;
            stopAt = l;
        } else if (now - stoppedSince > 90000 && stopAt != null) {   // stood still for 90 s after driving
            setPark(this, stopAt.getLatitude(), stopAt.getLongitude());
            moved = false;
            Snap.take(this, prefs);
        }
    }

    static void setPark(Context c, double lat, double lon) {
        parkLat = lat;
        parkLon = lon;
        parkAt = System.currentTimeMillis();
        c.getSharedPreferences("drive", MODE_PRIVATE).edit().putFloat("parkLat", (float) lat)
                .putFloat("parkLon", (float) lon).putLong("parkAt", parkAt).apply();
        c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));
    }

    // ------------------------------------------------------------------ speed limit and cameras

    private Location queriedAt;
    private long queriedTime;
    private boolean querying;
    private final List<double[]> cameras = new ArrayList<>();
    private final List<String> announced = new ArrayList<>();
    private boolean warned;

    private void speedAlerts(Location l, float kmh) {
        long now = System.currentTimeMillis();
        boolean far = queriedAt == null || queriedAt.distanceTo(l) > 250;
        if (!querying && now - queriedTime > 8000 && (far || now - queriedTime > 120000 && queriedAt.distanceTo(l) > 30)) {
            query(l);
        }
        // cameras ahead within 600 m
        int nearest = -1;
        for (double[] cam : cameras) {
            float[] r = new float[2];
            Location.distanceBetween(l.getLatitude(), l.getLongitude(), cam[0], cam[1], r);
            float d = r[0];
            float diff = Math.abs(((r[1] - l.getBearing()) % 360 + 540) % 360 - 180);
            boolean ahead = !l.hasBearing() || kmh < 5 || diff < 50;
            if (d < 600 && ahead && (nearest < 0 || d < nearest)) nearest = Math.round(d);
            String id = cam[0] + "," + cam[1];
            if (d < 600 && ahead && kmh > 10 && !announced.contains(id)) {
                announced.add(id);
                beep();
                say("Speed camera ahead" + (cam[2] > 0 ? ", limit " + (int) cam[2] : ""));
            }
        }
        cameraM = nearest;
        int warnAt = prefs.warnSpeed();
        if (Valet.on(prefs)) warnAt = warnAt > 0 ? Math.min(warnAt, Valet.speed(prefs)) : Valet.speed(prefs);
        boolean o = limit > 0 && kmh > limit + 3 || warnAt > 0 && kmh > warnAt;
        if (o && !warned) {
            beep();
            say(limit > 0 && kmh > limit + 3 ? "Speed limit " + limit : "Slow down");
        }
        warned = o;
        over = o;
    }

    private void query(final Location l) {
        querying = true;
        queriedAt = l;
        queriedTime = System.currentTimeMillis();
        final double lat = l.getLatitude(), lon = l.getLongitude();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String q = String.format(Locale.US, "[out:json][timeout:10];("
                        + "way(around:25,%1$.6f,%2$.6f)[highway][maxspeed];"
                        + "node(around:3000,%1$.6f,%2$.6f)[highway=speed_camera];"
                        + "node(around:3000,%1$.6f,%2$.6f)[enforcement=maxspeed];"
                        + ");out tags center;", lat, lon);
                int lim = 0;
                List<double[]> cams = new ArrayList<>();
                String err = "";
                try {
                    JSONArray els = new JSONObject(post("https://overpass-api.de/api/interpreter", "data=" + URLEncoder.encode(q, "UTF-8")))
                            .getJSONArray("elements");
                    for (int i = 0; i < els.length(); i++) {
                        JSONObject e = els.getJSONObject(i);
                        JSONObject tags = e.optJSONObject("tags");
                        if (tags == null) continue;
                        if ("way".equals(e.optString("type"))) {
                            int v = speed(tags.optString("maxspeed"));
                            if (v > 0 && (lim == 0 || v < lim)) lim = v;
                        } else {
                            cams.add(new double[]{e.optDouble("lat"), e.optDouble("lon"), speed(tags.optString("maxspeed"))});
                        }
                    }
                } catch (Throwable t) {
                    err = Hindi.t("speed data unreachable");
                }
                final int fl = lim;
                final List<double[]> fc = cams;
                final String fe = err;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        querying = false;
                        alertError = fe;
                        if (!fe.isEmpty()) return;
                        limit = fl;
                        cameras.clear();
                        cameras.addAll(fc);
                        changed();
                    }
                });
            }
        }).start();
    }

    /** A maxspeed tag in km/h ("50", "30 mph", "IN:urban" gives 0). */
    static int speed(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        if (!m.find()) return 0;
        int v = Integer.parseInt(m.group(1));
        return s.contains("mph") ? Math.round(v * 1.609f) : v;
    }

    private static String post(String u, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setDoOutput(true);
        c.setRequestProperty("User-Agent", "OpenRath (car head unit)");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        OutputStream o = c.getOutputStream();
        o.write(body.getBytes("UTF-8"));
        o.close();
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    private void loadSpots() {
        if (spotsLoaded) return;
        spotsLoaded = true;
        bumps.load(prefs.str("bumps", ""));
        waits.load(prefs.str("waits", ""));
    }

    /** Speed breakers and stops learnt from earlier drives: learn more, and warn about the ones ahead. */
    private void learnt(float kmh, long now) {
        boolean wantBumps = Features.on(prefs, "bumps"), wantWaits = Features.on(prefs, "waits");
        if (!wantBumps && !wantWaits) return;
        loadSpots();
        // a stop: how long it lasted, when the car moves on
        if (kmh < 3) {
            if (stopStart < 0 && driveId > 0) {
                stopStart = now;
                stopLat = latNow;
                stopLon = lonNow;
                stopHeading = lastHeading;
            }
        } else if (kmh > 10 && stopStart > 0) {
            long d = now - stopStart;
            if (wantWaits && d >= 10000 && d <= 10 * 60000 && Geo.dist(stopLat, stopLon, latNow, lonNow) < 150)
                waits.add(stopLat, stopLon, stopHeading, stopStart, d / 1000);
            stopStart = -1;
        }
        if (headingNow < 0) return;
        if (wantBumps && kmh > 25) {
            Spots.Spot b = bumps.ahead(latNow, lonNow, headingNow, 180, new Spots.Test() {
                @Override
                public boolean ok(Spots.Spot s) {
                    return Spots.drives(s) >= 3;
                }
            });
            if (b != null && spotTold.add("b" + Spots.id(b))) {
                beep();
                if (ttsReady) tts.speak("Speed breaker ahead.", TextToSpeech.QUEUE_ADD, null, "bump");
            }
        }
        if (wantWaits && kmh > 15) {
            final int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            Spots.Spot w = waits.ahead(latNow, lonNow, headingNow, 350, new Spots.Test() {
                @Override
                public boolean ok(Spots.Spot s) {
                    return s.seen.size() >= 3 && Spots.median(Spots.waitsNear(s, hour)) >= 40;
                }
            });
            if (w != null && spotTold.add("w" + Spots.id(w))) {
                long sec = Spots.median(Spots.waitsNear(w, hour));
                if (ttsReady) tts.speak(Errands.waitText(sec), TextToSpeech.QUEUE_ADD, null, "wait");
            }
        }
    }

    /** The headlight reminder, and the open-door and seat-belt reminders when the unit can see them. */
    private void carChecks(float kmh, long now) {
        if (Features.on(prefs, "lightsReminder") && !lightsTold) {
            boolean dark = Care.dark(latNow, lonNow, now);
            if (dark && kmh >= 20) {
                if (darkSince < 0) darkSince = now;
            } else darkSince = -1;
            if (darkSince > 0 && now - darkSince > 30000) {
                String mode = prefs.lights();
                String[] sig = prefs.lightSignal();
                boolean known = "on".equals(mode) || sig != null;
                boolean on = "on".equals(mode) || sig != null && Lights.isOn(Lights.read(this, sig[0]), sig);
                String t = Errands.lightsReminder(true, kmh, known, on, false);
                lightsTold = true;   // once a drive, said or not
                if (t != null && ttsReady) tts.speak(t, TextToSpeech.QUEUE_ADD, null, "lights");
            }
        }
        if (!Features.on(prefs, "chimes")) return;
        String[] door = prefs.signal("door");
        if (door != null && kmh > 10 && now - doorToldAt > 60000 && Lights.isOn(Lights.read(this, door[0]), new String[]{door[0], door[1], door[2]})) {
            doorToldAt = now;
            beep();
            if (ttsReady) tts.speak("A door is open.", TextToSpeech.QUEUE_ADD, null, "door");
        }
        String[] belt = prefs.signal("belt");
        if (belt != null && kmh > 15 && beltTold < 3 && now - beltToldAt > 120000) {
            String v = Lights.read(this, belt[0]);
            if (v != null && !Lights.isOn(v, new String[]{belt[0], belt[1], belt[2]})) {
                beltToldAt = now;
                beltTold++;
                if (ttsReady) tts.speak("Please fasten your seat belt.", TextToSpeech.QUEUE_ADD, null, "belt");
            }
        }
    }

    /** City, highway or night sound: the equalizer and a volume step to match. */
    private void soundProfile() {
        if (!Features.on(prefs, "soundProfiles")) {
            if (!soundNow.isEmpty()) {
                soundNow = "";
                Eq.apply(this, prefs, 0);
            }
            return;
        }
        String p = Errands.soundProfile(Highway.on, Care.dark(latNow, lonNow, System.currentTimeMillis()));
        if (p.equals(soundNow)) return;
        String was = soundNow;
        soundNow = p;
        int step = prefs.num("sndVol:" + p, Sound.vol(p)) - (was.isEmpty() ? 0 : prefs.num("sndVol:" + was, Sound.vol(was)));
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        for (int i = 0; i < Math.abs(step); i++)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, step > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
        Eq.apply(this, prefs, 0);
    }

    /** Music with the car parked and the engine off: warn, then pause it to save the battery. */
    private void parkedMusic(float kmh, long now) {
        if (kmh >= 3) {
            stillSince = -1;
            musicDone = "";
            return;
        }
        if (stillSince < 0) stillSince = now;
        if (!Features.on(prefs, "parkedMusic")) return;
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        boolean playing = Player.playing || am.isMusicActive();
        String what = ParkedMusic.decide(playing, now - stillSince, prefs.num("parkedMusicMin", 20),
                Obd.connected && now - Obd.readAt < 30000, Obd.rpm, Obd.volts);
        if (what.isEmpty() || what.equals(musicDone) || "pause".equals(musicDone) || "remind".equals(musicDone)) return;
        musicDone = what;
        String text;
        if ("warn".equals(what)) text = "The engine is off. The music will pause in a minute to save the battery.";
        else if ("remind".equals(what)) text = "If the engine is off, the music is using the car battery.";
        else {
            if (Player.playing) startService(new Intent(this, Player.class).setAction(Player.TOGGLE));
            else {
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE));
                am.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE));
            }
            text = "Music paused to save the battery.";
        }
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "battery");
    }

    private void beep() {
        if (tone != null && (prefs.alertsOn() || Valet.on(prefs)) && !Quiet.now(prefs)) tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250);
    }

    private void say(String s) {
        if (ttsReady && (prefs.alertsOn() || Valet.on(prefs))) tts.speak(s, TextToSpeech.QUEUE_ADD, null, "alert");
    }

    /** After the set time at the wheel without a ten-minute stop, suggest a break (again every 45 min). */
    private void restStop(long now) {
        if (!Features.on(prefs, "restStop")) return;
        long mins = drives.drivingMinutes(now);
        if (mins < prefs.num("breakMin", 120) || now - restAt < 45 * 60000) return;
        restAt = now;
        String h = mins >= 90 && mins % 60 < 15 ? (mins / 60) + (mins / 60 == 1 ? " hour" : " hours") : mins + " minutes";
        if (ttsReady) tts.speak("You've been driving for " + h + ". Time for a short break. Looking for a stop ahead.",
                TextToSpeech.QUEUE_ADD, null, "rest");
        sendBroadcast(new Intent(REST).setPackage(getPackageName()));
    }

    private void accelDone(float t100, float t60) {
        String s100 = String.format(Locale.US, "%.1f", t100);
        prefs.put("accelLast", s100 + (t60 > 0 ? "|" + String.format(Locale.US, "%.1f", t60) : "|") + "|" + System.currentTimeMillis());
        float best = 0;
        try {
            best = Float.parseFloat(prefs.str("accelBest", "0"));
        } catch (Exception ignored) {
        }
        boolean record = best <= 0 || t100 < best;
        if (record) prefs.put("accelBest", s100);
        if (ttsReady) tts.speak("Nought to hundred in " + s100 + " seconds" + (record ? ", your best" : ""),
                TextToSpeech.QUEUE_ADD, null, "accel");
        changed();
    }

    private void changed() {
        sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));
    }

    @Override public void onStatusChanged(String p, int s, Bundle b) {}
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}

    @Override
    public void onDestroy() {
        save();
        if (Features.on(prefs, "coach") || Features.on(prefs, "commute")) Coach.save(prefs, drives.close(System.currentTimeMillis()));
        Dashcam.stop();
        try {
            unregisterReceiver(screen);
        } catch (Throwable ignored) {
        }
        Obd.stop();
        Tpms.stop();
        try {
            ((LocationManager) getSystemService(LOCATION_SERVICE)).removeUpdates(this);
        } catch (Throwable ignored) {
        }
        if (tts != null) tts.shutdown();
        if (tone != null) tone.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
