package org.openrath.launcher;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The picture layouts (assets/layouts/layouts.json): a full-screen picture with round, pill and
 * card parts on it, as an alternative to the five boxes. Settings > Look > Layout picks one, and
 * each part can be shown or hidden.
 */
final class Layouts {

    static final String BOXES = "boxes";

    static final class Part {
        String kind, style, shape, src, opt;
        float x, y, w, h, r;
        int fill, fill2, ink, sub, accent;
        float alpha = 1;
        boolean hasFill, labels, tiles, clock;
        List<String> items = new ArrayList<>();
    }

    static final class Layout {
        String id, name, bg;
        boolean light;
        float roadHorizon = -1, roadX, roadHalf;
        int roadDash;
        final List<float[]> lines = new ArrayList<>();
        final List<Part> parts = new ArrayList<>();
    }

    private static List<Layout> all;

    static synchronized List<Layout> all(Context c) {
        if (all != null) return all;
        List<Layout> out = new ArrayList<>();
        try {
            InputStream in = c.getAssets().open("layouts/layouts.json");
            byte[] b = new byte[in.available()];
            int n = 0;
            while (n < b.length) {
                int k = in.read(b, n, b.length - n);
                if (k < 0) break;
                n += k;
            }
            in.close();
            JSONArray ls = new JSONObject(new String(b, 0, n, "UTF-8")).getJSONArray("layouts");
            for (int i = 0; i < ls.length(); i++) out.add(parse(ls.getJSONObject(i)));
        } catch (Exception e) {
            Diag.note("layouts read", e);
        }
        all = out;
        return out;
    }

    static Layout parse(JSONObject o) throws org.json.JSONException {
        Layout l = new Layout();
        l.id = o.getString("id");
        l.name = o.getString("name");
        l.bg = o.getString("bg");
        l.light = o.optBoolean("light", true);
        JSONObject road = o.optJSONObject("road");
        if (road != null) {
            l.roadHorizon = (float) road.getDouble("horizon");
            l.roadX = (float) road.getDouble("x");
            l.roadHalf = (float) road.getDouble("half");
            l.roadDash = color(road.optString("dash", "#FFFFFF"));
        }
        JSONArray lines = o.optJSONArray("lines");
        for (int i = 0; lines != null && i < lines.length(); i++) {
            JSONArray a = lines.getJSONArray(i);
            l.lines.add(new float[]{(float) a.getDouble(0), (float) a.getDouble(1), (float) a.getDouble(2), (float) a.getDouble(3)});
        }
        JSONArray ps = o.getJSONArray("parts");
        for (int i = 0; i < ps.length(); i++) {
            JSONObject j = ps.getJSONObject(i);
            Part p = new Part();
            p.kind = j.getString("kind");
            p.style = j.optString("style", "");
            p.shape = j.optString("shape", "");
            p.src = j.optString("src", "");
            p.opt = j.optString("opt", "");
            p.x = (float) j.getDouble("x");
            p.y = (float) j.getDouble("y");
            p.w = (float) j.getDouble("w");
            p.h = (float) j.getDouble("h");
            p.r = (float) j.optDouble("r", 12);
            p.hasFill = j.has("fill");
            p.alpha = (float) j.optDouble("alpha", 1);
            p.fill = color(j.optString("fill", "#000000"));
            p.fill2 = color(j.optString("fill2", j.optString("fill", "#000000")));
            p.ink = color(j.optString("ink", "#FFFFFF"));
            p.sub = color(j.optString("sub", j.optString("ink", "#FFFFFF")));
            p.accent = color(j.optString("accent", "#3A8DFF"));
            p.labels = j.optBoolean("labels");
            p.tiles = j.optBoolean("tiles");
            p.clock = j.optBoolean("clock");
            JSONArray it = j.optJSONArray("items");
            for (int k = 0; it != null && k < it.length(); k++) p.items.add(it.getString(k));
            l.parts.add(p);
        }
        return l;
    }

    /** "#RRGGBB" to an opaque colour. */
    static int color(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.substring(1, 7), 16);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    static String current(Prefs p) {
        return p.str("layout", BOXES);
    }

    static Layout get(Context c, String id) {
        for (Layout l : all(c)) if (l.id.equals(id)) return l;
        return null;
    }

    // ------------------------------------------------------------------ parts that can be hidden

    /** What a switch is called in Settings. */
    static final Map<String, String> OPT_NAMES = new LinkedHashMap<>();

    static {
        OPT_NAMES.put("speed", "Speed");
        OPT_NAMES.put("time", "Time");
        OPT_NAMES.put("clock", "Clock");
        OPT_NAMES.put("weather", "Weather");
        OPT_NAMES.put("forecast", "Weather for the next days");
        OPT_NAMES.put("media", "Music and radio");
        OPT_NAMES.put("direction", "Compass and direction");
        OPT_NAMES.put("stats", "Temperature, altitude and direction");
        OPT_NAMES.put("user", "Driver's name");
        OPT_NAMES.put("car", "The car");
        OPT_NAMES.put("trip", "Today's distance");
    }

    /** Things in a list part (stats) that can each be shown. */
    static final String[] STAT_ITEMS = {"temp", "altitude", "direction", "pressure", "latlon", "tyres"};
    static final String[] STAT_NAMES = {"Temperature", "Altitude", "Direction", "Air pressure", "Latitude and longitude",
            "Tyre pressures"};

    /** The parts switched off, read once for each change of the setting (it is asked for every frame). */
    private static final Map<String, Object[]> HIDDEN = new HashMap<>();

    /** The parts switched off; don't change the set (setShown copies it). */
    static Set<String> hidden(Prefs p, String id) {
        String raw = p.str("lay:" + id + ":off", "");
        synchronized (HIDDEN) {
            Object[] kept = HIDDEN.get(id);
            if (kept != null && raw.equals(kept[0])) {
                @SuppressWarnings("unchecked") Set<String> h = (Set<String>) kept[1];
                return h;
            }
            Set<String> h = java.util.Collections.unmodifiableSet(new HashSet<>(Arrays.asList(raw.split(","))));
            HIDDEN.put(id, new Object[]{raw, h});
            return h;
        }
    }

    static boolean shown(Prefs p, String id, String opt) {
        return opt == null || opt.isEmpty() || !hidden(p, id).contains(opt);
    }

    static void setShown(Prefs p, String id, String opt, boolean on) {
        Set<String> h = new HashSet<>(hidden(p, id));
        h.remove("");
        if (on) h.remove(opt);
        else h.add(opt);
        StringBuilder s = new StringBuilder();
        for (String x : h) s.append(x).append(',');
        p.put("lay:" + id + ":off", s.toString());
    }

    /** The items a stats part shows: its own by default, the extra ones once switched on. */
    static List<String> statItems(Prefs p, String id, Part part) {
        List<String> out = new ArrayList<>();
        Set<String> h = hidden(p, id);
        for (String it : statOrder(p, id, part)) {
            String key = "stat-" + it;
            boolean def = part.items.contains(it);
            boolean on = def ? !h.contains(key) : h.contains("+" + key);
            if (on) out.add(it);
        }
        return out;
    }

    /** All the list's items in the owner's order: the part's own first, then the rest. */
    static List<String> statOrder(Prefs p, String id, Part part) {
        List<String> out = new ArrayList<>();
        for (String it : p.str("lay:" + id + ":order", "").split(","))
            if (Arrays.asList(STAT_ITEMS).contains(it) && !out.contains(it)) out.add(it);
        for (String it : part.items) if (!out.contains(it)) out.add(it);
        for (String it : STAT_ITEMS) if (!out.contains(it)) out.add(it);
        return out;
    }

    /** Move an item to the top of the list. */
    static void toTop(Prefs p, String id, Part part, String item) {
        List<String> o = statOrder(p, id, part);
        o.remove(item);
        o.add(0, item);
        StringBuilder s = new StringBuilder();
        for (String x : o) s.append(x).append(',');
        p.put("lay:" + id + ":order", s.toString());
    }

    static String statName(String item) {
        int i = Arrays.asList(STAT_ITEMS).indexOf(item);
        return i < 0 ? item : STAT_NAMES[i];
    }

    /** The first part of a kind, or null. */
    static Part part(Layout l, String kind) {
        for (Part x : l.parts) if (x.kind.equals(kind)) return x;
        return null;
    }

    /** The switches a layout has, in order, without repeats. */
    static List<String> opts(Layout l) {
        List<String> out = new ArrayList<>();
        for (Part x : l.parts) if (!x.opt.isEmpty() && !out.contains(x.opt)) out.add(x.opt);
        return out;
    }

    static void setStat(Prefs p, String id, Part part, String item, boolean on) {
        String key = "stat-" + item;
        if (part.items.contains(item)) setShown(p, id, key, on);
        else setShown(p, id, "+" + key, !on);
    }

    /** Layout-wide switches: moving road lines, gentle background movement, picture switching. */
    static boolean roadOn(Prefs p) {
        return Features.on(p, "road");   // the same switch as the dashboard's moving road
    }

    /** A phone-mirroring button added to each layout's dock. */
    static boolean mirrorOn(Prefs p) {
        return "on".equals(p.str("layMirror", "off"));
    }

    static boolean animOn(Prefs p) {
        return "on".equals(p.str("layAnim", "off"));
    }

    /** "layout" = the layout's own picture, "mine" = from OpenRath/wallpapers, "cycle" = take turns every hour. */
    static String wall(Prefs p) {
        return p.str("layWall", "layout");
    }

    static File wallDir() {
        return Storage.dir("wallpapers");
    }

    static List<File> myWalls() {
        List<File> out = new ArrayList<>();
        File[] f = wallDir().listFiles();
        if (f == null) return out;
        Arrays.sort(f);
        for (File x : f) {
            String n = x.getName().toLowerCase(Locale.US);
            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) out.add(x);
        }
        return out;
    }

    // ------------------------------------------------------------------ pictures

    /**
     * A picture from assets/layouts (".jpg" scenes, ".png" cars), read at about the screen's size and
     * kept in the shared picture cache, which lets the least used go when it is full.
     */
    static Bitmap asset(Context c, String name) {
        android.util.LruCache<String, Bitmap> pics = Pics.cache(c);
        Bitmap b = pics.get("layouts/" + name);
        if (b != null) return b;
        for (String ext : new String[]{".jpg", ".png"}) {
            b = Pics.asset(c, "layouts/" + name + ext);
            if (b != null) break;
        }
        if (b != null) pics.put("layouts/" + name, b);
        return b;
    }

    /** The background now: the layout's, or one of the owner's pictures. */
    static Bitmap background(Context c, Prefs p, Layout l) {
        String mode = wall(p);
        List<File> mine = "layout".equals(mode) ? new ArrayList<File>() : myWalls();
        if (!mine.isEmpty()) {
            int pick;
            if ("mine".equals(mode)) pick = 0;
            else {
                long hour = System.currentTimeMillis() / 3600000L;
                pick = (int) (hour % (mine.size() + 1)) - 1;   // -1: the layout's own turn
            }
            if (pick >= 0) {
                File f = mine.get(pick);
                android.util.LruCache<String, Bitmap> pics = Pics.cache(c);
                String key = "wall:" + f.getPath();
                Bitmap b = pics.get(key);
                if (b == null) {
                    b = Pics.file(c, f);
                    if (b != null) {
                        // keep one of the owner's pictures in memory at a time (the layouts' own stay)
                        for (String k : pics.snapshot().keySet()) if (k.startsWith("wall:")) pics.remove(k);
                        pics.put(key, b);
                    }
                }
                if (b != null) return b;
            }
        }
        return asset(c, l.bg);
    }

    /** Compass point for a bearing: N, NE, E… */
    static String compass(double deg) {
        String[] n = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return n[(int) Math.round(((deg % 360) + 360) % 360 / 45) % 8];
    }

    private Layouts() {
    }
}
