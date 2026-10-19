package org.openrath.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything you can change on the dashboard, kept between restarts. */
final class Prefs {

    // What a box can show
    static final String CAR = "car", CLOCK = "clock", WEATHER = "weather", MAP = "map",
            RADIO = "radio", MUSIC = "music", TRIP = "trip", FUEL = "fuel", PHONE = "phone",
            OBD = "obd", TYRES = "tyres", WIDGET1 = "widget1", WIDGET2 = "widget2", PERF = "perf", NEWS = "news", CAB = "cab",
            COMPASS = "compass", SOUNDS = "sounds", CALENDAR = "calendar", VIDEO = "video";
    static final String[] CONTENTS = {CAR, CLOCK, WEATHER, MAP, RADIO, MUSIC, TRIP, FUEL, PHONE, OBD, TYRES, NEWS, CAB, COMPASS, SOUNDS, CALENDAR, VIDEO, PERF, WIDGET1, WIDGET2};
    static final String[] CONTENT_NAMES = {"Car and speed", "Clock and date", "Weather", "Map",
            "Radio", "Music", "Trip computer", "Fuel log", "Phone", "Engine (OBD-II)", "Tyre pressure", "News (top 5 headlines)", "Cab driver (earnings, meter)",
            "Compass and altitude", "Soundboard (horn and fun sounds)", "Calendar", "Video (picture only when parked)", "Performance (FPS, CPU, temperature)", "Widget from another app", "Second widget"};

    // Boxes: left column top (50%), middle (25%), bottom (25%); right column top (75%), bottom (25%)
    static final int SLOTS = 5;
    private static final String[][] DEFAULT_SLOTS = {{CAR, CLOCK, WEATHER, MAP, RADIO},
            {TRIP, FUEL, OBD, WIDGET1, MUSIC}, {CLOCK, TYRES, PHONE, WIDGET2, RADIO}};
    static final int MAX_PAGES = 3;
    /** The dashboard page on screen (0 = the first). Each page has its own five boxes. */
    static int page;

    private static String slotKey(int i) {
        return page == 0 ? "slot" + i : "p" + page + "slot" + i;
    }

    /** What box i shows on page pg (any page, not only the one on screen). */
    String slotOn(int pg, int i) {
        return p.getString(pg == 0 ? "slot" + i : "p" + pg + "slot" + i, DEFAULT_SLOTS[Math.min(pg, MAX_PAGES - 1)][i]);
    }

    /** Whether any dashboard page has the map. */
    boolean mapOnAnyPage() {
        for (int pg = 0; pg < pages(); pg++)
            for (int i = 0; i < SLOTS; i++) if (MAP.equals(slotOn(pg, i))) return true;
        return false;
    }

    /** How many pages: 1 unless extra pages are switched on. */
    int pages() {
        return Features.on(this, "pages") ? Math.max(1, Math.min(MAX_PAGES, num("pages", 2))) : 1;
    }

    // Dock buttons, top to bottom
    static final String[] DOCK = {"navi", "music", "radio", "bt", "phone", "apps"};
    static final String[] DOCK_NAMES = {"Navigation", "Music", "Radio", "Bluetooth", "Phone", "All apps"};

    private final SharedPreferences p;

    Prefs(Context c) {
        p = c.getSharedPreferences("home", Context.MODE_PRIVATE);
    }

    String slot(int i) {
        return p.getString(slotKey(i), DEFAULT_SLOTS[Math.min(page, MAX_PAGES - 1)][i]);
    }

    /** Put `content` in box i; whatever box already showed it gets box i's old content. */
    void setSlot(int i, String content) {
        String old = slot(i);
        SharedPreferences.Editor e = p.edit();
        for (int j = 0; j < SLOTS; j++) {
            if (j != i && slot(j).equals(content)) e.putString(slotKey(j), old);
        }
        e.putString(slotKey(i), content).apply();
    }

    /** Swap what two boxes show (drag and drop). */
    void swapSlots(int a, int b) {
        String x = slot(a), y = slot(b);
        p.edit().putString(slotKey(a), y).putString(slotKey(b), x).apply();
    }

    boolean swapped() {
        return p.getBoolean("swapped", false);
    }

    void setSwapped(boolean v) {
        p.edit().putBoolean("swapped", v).apply();
    }

    /** "auto" follows night mode (the headlight wire), or "on" / "off". */
    String lights() {
        return p.getString("lights", "auto");
    }

    void setLights(String v) {
        p.edit().putString("lights", v).apply();
    }

    /** The learnt headlight signal (see Lights): key, its value with the lights off, and on. */
    String[] lightSignal() {
        String k = p.getString("lightKey", null);
        return k == null ? null : new String[]{k, p.getString("lightOff", ""), p.getString("lightOn", "")};
    }

    void setLightSignal(String key, String off, String on) {
        p.edit().putString("lightKey", key).putString("lightOff", off).putString("lightOn", on).apply();
    }

    // Where the radio, music and navigation come from. "auto" picks by the internet connection.
    static final String[] RADIO_SRC = {"auto", "web", "app"}, MUSIC_SRC = {"auto", "app", "files"},
            NAV_SRC = {"own", "google"};

    String source(String what) {
        String def = "navi".equals(what) ? "own" : "auto";
        String v = p.getString("src:" + what, def);
        return "dzire".equals(v) ? "own" : v;   // the app's own map was called "dzire" before the rename
    }

    void setSource(String what, String v) {
        p.edit().putString("src:" + what, v).apply();
    }

    static String sourceName(String v) {
        if ("web".equals(v)) return "online";
        if ("app".equals(v)) return "app";
        if ("files".equals(v)) return "music files";
        if ("google".equals(v)) return "Google Maps";
        if ("own".equals(v)) return "OpenRath map";
        return "auto";
    }

    /** Speak and beep for speed limits and cameras. */
    boolean alertsOn() {
        return p.getBoolean("alerts", true);
    }

    void setAlertsOn(boolean v) {
        p.edit().putBoolean("alerts", v).apply();
    }

    /** Warn above this speed anywhere (0 = only where the road's limit is known). */
    int warnSpeed() {
        return p.getInt("warnSpeed", 0);
    }

    void setWarnSpeed(int v) {
        p.edit().putInt("warnSpeed", v).apply();
    }

    String fuelLog() {
        return p.getString("fuel", "[]");
    }

    void setFuelLog(String json) {
        p.edit().putString("fuel", json).apply();
    }

    /** The radio-service command that starts / stops FM on this unit, once found ("" = not found yet). */
    String fmCmd(String kind) {
        return p.getString("fm:" + kind, "");
    }

    void setFmCmd(String kind, String cmd) {
        p.edit().putString("fm:" + kind, cmd).apply();
    }

    /** Last FM frequency, as shown ("98.3"). */
    String fmFreq() {
        return p.getString("fmFreq", "98.3");
    }

    void setFmFreq(String f) {
        p.edit().putString("fmFreq", f).apply();
    }

    // plain values for the newer settings (service reminders, tolls, theme, phone favourites…)
    String str(String key, String def) {
        return p.getString("x:" + key, def);
    }

    void put(String key, String v) {
        p.edit().putString("x:" + key, v).apply();
    }

    int num(String key, int def) {
        try {
            return Integer.parseInt(str(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    long longNum(String key, long def) {
        try {
            return Long.parseLong(str(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    void putLong(String key, long v) {
        put(key, String.valueOf(v));
    }

    /** Shown under the car; empty hides the line. */
    String welcome() {
        return p.getString("welcome", "");
    }

    void setWelcome(String v) {
        p.edit().putString("welcome", v).apply();
    }

    /** A learnt turn-indicator signal ("left" / "right"): key, value when off, values seen while blinking. */
    String[] signal(String name) {
        String k = p.getString("sigKey_" + name, null);
        return k == null ? null : new String[]{k, p.getString("sigOff_" + name, ""), p.getString("sigOn_" + name, "")};
    }

    void setSignal(String name, String key, String off, String onValues) {
        SharedPreferences.Editor e = p.edit();
        if (key == null) e.remove("sigKey_" + name);
        else e.putString("sigKey_" + name, key).putString("sigOff_" + name, off).putString("sigOn_" + name, onValues);
        e.apply();
    }

    /** Package chosen for a dock button or a media box ("dock:navi", "media:radio"), or null. */
    String app(String key) {
        String def = "dock:navi".equals(key) ? "com.google.android.apps.maps" : null;
        return p.getString("app:" + key, def);
    }

    void setApp(String key, String pkg) {
        p.edit().putString("app:" + key, pkg).apply();
    }

    /** Back to the default layout; the learnt headlight signal is kept. */
    /** Every stored setting (for driver profiles). */
    java.util.Map<String, ?> all() {
        return p.getAll();
    }

    /** Remove `drop` and store `put`, keeping each value's type (for driver profiles). */
    void replace(java.util.List<String> drop, java.util.Map<String, Object> put) {
        SharedPreferences.Editor e = p.edit();
        for (String k : drop) e.remove(k);
        for (java.util.Map.Entry<String, Object> x : put.entrySet()) {
            Object v = x.getValue();
            if (v instanceof Boolean) e.putBoolean(x.getKey(), (Boolean) v);
            else if (v instanceof Integer) e.putInt(x.getKey(), (Integer) v);
            else if (v instanceof Long) e.putLong(x.getKey(), (Long) v);
            else if (v instanceof Double || v instanceof Float) e.putFloat(x.getKey(), ((Number) v).floatValue());
            else if (v != null) e.putString(x.getKey(), String.valueOf(v));
        }
        e.apply();
    }

    void reset() {
        // keep what was learnt about the car and the name; only the layout and apps go back to defaults
        java.util.Map<String, ?> all = p.getAll();
        SharedPreferences.Editor e = p.edit();
        for (String k : all.keySet()) {
            if (k.startsWith("slot") || k.matches("p[0-9]slot[0-9]+") || k.equals("swapped") || k.startsWith("app:")) e.remove(k);
        }
        e.apply();
    }
}
