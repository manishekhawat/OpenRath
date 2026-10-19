package org.openrath.launcher;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Environment;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Theme packs: a folder (or a .zip of one) in OpenRath/themes on the unit's storage, holding a
 * theme.json and any pictures that replace the built-in ones. No app code or rebuild is needed;
 * anything a theme leaves out falls back to the built-in look. See THEMES.md for the format.
 */
final class Theme {

    /** The themes built into the app, in assets/themes/<id>; their ids start with @. */
    static final String[] BUILT_IN = {"magma-grey", "light", "minimal", "auto", "genz", "mission", "electric", "roadster"};
    static final String DEFAULT = "@magma-grey";

    /** OpenRath/themes on the unit's storage. */
    static File root() {
        return Storage.dir("themes");
    }

    /** A theme the owner can choose: built in, or a folder in OpenRath/themes. */
    static final class Pack {
        final String id, name, author, description;
        final JSONObject json;
        final File dir;   // null for a built-in theme

        Pack(String id, JSONObject json, File dir) {
            this.id = id;
            this.json = json;
            this.dir = dir;
            name = json.optString("name", id.startsWith("@") ? id.substring(1) : id);
            author = json.optString("author", "");
            description = json.optString("description", "");
        }

        boolean builtIn() {
            return dir == null;
        }
    }

    /** Colours and switches a theme sets, with the built-in look for anything left out. */
    static final class Look {
        int text, textDim, accent, edge, panelTop, panel, background, icons, roadFill, roadLine;
        int[] gradient;
        float radius;
        boolean road, chrome;
        String problem = "";

        static Look of(JSONObject json, int defAccent, int defEdge) {
            Look l = new Look();
            JSONObject col = json.optJSONObject("colours");
            if (col == null) col = json.optJSONObject("colors");
            l.text = l.colour(col, "text", 0xFFD8DCE2);
            l.textDim = l.colour(col, "textDim", 0xFF8A8F96);
            l.accent = l.colour(col, "accent", defAccent);
            l.edge = l.colour(col, "edge", defEdge);
            l.panelTop = l.colour(col, "panelTop", 0xFF111215);
            l.panel = l.colour(col, "panel", 0xFF0B0C0E);
            l.background = l.colour(col, "background", 0xFF000000);
            l.icons = l.colour(col, "icons", 0);
            l.roadFill = l.colour(col, "road", 0xFF1D1F23);
            l.roadLine = l.colour(col, "roadLine", 0xFFC9CDD3);
            org.json.JSONArray g = json.optJSONArray("backgroundGradient");
            if (g != null && g.length() >= 2) {
                l.gradient = new int[g.length()];
                for (int i = 0; i < g.length(); i++) {
                    try {
                        l.gradient[i] = android.graphics.Color.parseColor(g.getString(i));
                    } catch (Throwable e) {
                        l.gradient = null;
                        l.problem = Hindi.t("backgroundGradient needs colours like #3A0D5C");
                        break;
                    }
                }
            }
            l.radius = (float) json.optDouble("cornerRadius", 14);
            l.road = json.optBoolean("road", true);
            l.chrome = json.optBoolean("chromeText", true);
            return l;
        }

        private int colour(JSONObject o, String key, int def) {
            if (o == null || !o.has(key)) return def;
            try {
                return android.graphics.Color.parseColor(o.getString(key));
            } catch (Throwable e) {
                problem = Hindi.f("Colour %1$s isn't a colour like #D8DCE2", key);
                return def;
            }
        }
    }

    /** The car-paint accent from Settings, used by themes that don't set their own. */
    static int paintAccent = 0xFF8A8F96, paintEdge = 0xFF4C5056;
    private static Context app;
    private static Pack pack;
    private static File dir;          // the active theme's folder, or null for a built-in theme
    private static JSONObject json = new JSONObject();
    static String name = "Magma Grey", error = "";
    private static Look look;
    private static Typeface font;
    private static Bitmap background;

    /** Every theme: the built-in five first, then folders in OpenRath/themes. */
    static List<Pack> available(Context c) {
        List<Pack> out = new ArrayList<>();
        for (String id : BUILT_IN) {
            try {
                out.add(new Pack("@" + id, new JSONObject(read(c.getAssets().open("themes/" + id + "/theme.json"))), null));
            } catch (Throwable e) {
                Diag.note("built-in theme", e);
            }
        }
        File[] l = root().listFiles();
        if (l != null) {
            java.util.Arrays.sort(l);
            for (File f : l) {
                if (!f.isDirectory() || !new File(f, "theme.json").exists()) continue;
                try {
                    out.add(new Pack(f.getName(), new JSONObject(read(new File(f, "theme.json"))), f));
                } catch (Throwable e) {
                    JSONObject broken = new JSONObject();
                    try {
                        broken.put("name", f.getName());
                        broken.put("description", "theme.json has a mistake: " + e.getMessage());
                    } catch (Throwable ignored) {
                        // put of a plain string can't fail
                    }
                    out.add(new Pack(f.getName(), broken, f));
                }
            }
        }
        return out;
    }

    /** A theme picked by ThemeAuto (night, or a driver's phone) instead of the owner's own, or null. */
    static volatile String override;

    /** The chosen theme's id; the old built-in name from 1.10 means Magma Grey. */
    static String chosen(Prefs p) {
        if (override != null) return override;
        String want = p.str("themePack", DEFAULT);
        return want.isEmpty() || "Built-in Dzire chrome".equals(want) ? DEFAULT : want;
    }

    /** What is loaded: the theme's id and its folder's state (see stamp); null to read it again. */
    private static String loadedFor;
    /** What the decoded pictures were made for: the theme, the car and the colours they are tinted in. */
    private static String imagesFor;
    private static String readError = "";

    /** Read the theme and its pictures again at the next load (a new look was chosen, or a picture changed). */
    static void reload() {
        loadedFor = null;
        imagesFor = null;
    }

    /** The theme's id and, for a folder theme, how many files it has, their total size and the newest change. */
    static String stamp(String want) {
        if (want.startsWith("@")) return want;
        File d = new File(root(), want);
        long newest = d.lastModified(), size = 0;
        File[] l = d.listFiles();
        if (l != null) {
            for (File f : l) {
                newest = Math.max(newest, f.lastModified());
                size += f.length();
            }
        }
        return want + "|" + (l == null ? -1 : l.length) + "|" + size + "|" + newest;
    }

    /**
     * Load the chosen theme and apply its colours; call before building the dashboard. The theme's
     * files and pictures are read again only when another theme is chosen or its folder changed
     * (or after reload()), not on every rebuild of the dashboard.
     */
    static void load(Context c, Prefs p) {
        app = c.getApplicationContext();
        paintAccent = Ui.ACCENT;
        paintEdge = Ui.EDGE;
        String want = chosen(p);
        String now = stamp(want);
        if (pack == null || !now.equals(loadedFor)) {
            read(c, want);
            loadedFor = now;
        }
        name = pack.name;
        look = Look.of(json, Ui.ACCENT, Ui.EDGE);
        error = readError.isEmpty() ? look.problem : readError;
        Ui.SILVER = look.text;
        Ui.SILVER_DIM = look.textDim;
        Ui.ACCENT = look.accent;
        Ui.EDGE = look.edge;
        Ui.PANEL_TOP = look.panelTop;
        Ui.PANEL = look.panel;
        Ui.BACKGROUND = look.background;
        Ui.RADIUS = look.radius;
        Ui.ROAD = look.road;
        Ui.CHROME = look.chrome;
        Ui.ICONS = look.icons;
        Ui.ROAD_FILL = look.roadFill;
        Ui.ROAD_LINE = look.roadLine;
        // the pictures depend on the theme, the car's brand and body, and the colours they are tinted in
        String pics = loadedFor + "|" + Ui.brand + "|" + Ui.body + "|" + Ui.ICONS + "|" + Ui.CHROME + "|" + Ui.SILVER;
        if (!pics.equals(imagesFor)) {
            Ui.clearImages();
            imagesFor = pics;
        }
    }

    /** Read a theme's theme.json, font and wallpaper. */
    private static void read(Context c, String want) {
        pack = null;
        dir = null;
        json = new JSONObject();
        font = null;
        background = null;
        readError = "";
        try {
            if (want.startsWith("@")) {
                json = new JSONObject(read(c.getAssets().open("themes/" + want.substring(1) + "/theme.json")));
                pack = new Pack(want, json, null);
            } else {
                File d = new File(root(), want);
                json = new JSONObject(read(new File(d, "theme.json")));
                dir = d;
                pack = new Pack(want, json, d);
            }
        } catch (Throwable e) {
            readError = Hindi.f("Couldn't read %1$s/theme.json: %2$s", want, e.getMessage());
            try {
                json = new JSONObject(read(c.getAssets().open("themes/" + DEFAULT.substring(1) + "/theme.json")));
            } catch (Throwable ex) {
                Diag.note("default theme", ex);
            }
            pack = new Pack(DEFAULT, json, null);
        }
        if (dir != null) {
            File f = new File(dir, Storage.bareName(json.optString("font", "font.otf"), "font.otf"));
            if (!f.exists()) f = new File(dir, "font.ttf");
            if (f.exists()) {
                try {
                    font = Typeface.createFromFile(f);
                } catch (Throwable e) {
                    Diag.note("theme font", e);
                }
            }
            File bg = new File(dir, "background.jpg");
            if (!bg.exists()) bg = new File(dir, "background.png");
            // read at about the screen's size (a photo straight from a phone can be 50 MB in memory)
            if (bg.exists()) background = Pics.file(c, bg);
        }
    }

    static JSONObject json() {
        return json;
    }

    /** The active theme's id (@name for a built-in one). */
    static String id() {
        return pack == null ? DEFAULT : pack.id;
    }

    /** Colours for the wallpaper, top to bottom, or null for a plain background. */
    static int[] gradient() {
        return look == null ? null : look.gradient;
    }

    /** A picture from the theme folder (name.png), or null to use the built-in one. */
    static Bitmap image(String n) {
        if (dir == null) {
            if (app == null || pack == null) return null;
            try {
                InputStream in = app.getAssets().open("themes/" + pack.id.substring(1) + "/" + n + ".png");
                Bitmap b = BitmapFactory.decodeStream(in);
                in.close();
                return b;
            } catch (Throwable e) {
                return null;
            }
        }
        File f = new File(dir, n + ".png");
        if (!f.exists()) return null;
        try {
            return BitmapFactory.decodeFile(f.getPath());
        } catch (Throwable e) {
            return null;
        }
    }

    /** The theme's font for the DZIRE-style text (welcome line), or null. */
    static Typeface font() {
        return font;
    }

    static Bitmap background() {
        return background;
    }

    /** Text the theme sets, e.g. "welcomeTitle" (default "WELCOME"). */
    static String text(String key, String def) {
        return json.optString(key, def);
    }

    // ------------------------------------------------------------------ making and importing themes

    /**
     * Write an editable copy of the built-in look to OpenRath/themes/<folder>: every picture,
     * a theme.json with every colour spelled out, and a short how-to. Returns the folder.
     */
    static File starter(Context c, String folder) throws Exception {
        File d = new File(root(), folder);
        if (!d.isDirectory() && !d.mkdirs()) throw new IllegalStateException("can't create " + d);
        AssetManager a = c.getAssets();
        String[] imgs = a.list("img");
        if (imgs != null) for (String n : imgs) copy(a.open("img/" + n), new File(d, n));
        JSONObject start = new JSONObject(read(a.open("theme/theme.json")));
        if (pack != null && pack.json.length() > 0) {
            // begin from the theme in use, so "make a theme" means "change this one"
            start = new JSONObject(pack.json.toString());
            start.put("name", folder);
            start.put("author", "Your name");
            start.remove("description");
            if (dir != null) for (String n : new String[]{"font.otf", "font.ttf", "background.jpg", "background.png"}) {
                File f = new File(dir, n);
                if (f.exists()) copy(new FileInputStream(f), new File(d, n));
            }
        }
        OutputStream o = new FileOutputStream(new File(d, "theme.json"));
        o.write(start.toString(2).replace("\\/", "/").getBytes("UTF-8"));
        o.close();
        copy(a.open("theme/HOW-TO.txt"), new File(d, "HOW-TO.txt"));
        return d;
    }

    /** Largest file, and largest total, unpacked from a theme .zip. */
    static final long MAX_FILE = 8L * 1024 * 1024, MAX_TOTAL = 32L * 1024 * 1024;

    /** Unpack a theme .zip into OpenRath/themes; returns the folder name. */
    static String importZip(File zip) throws Exception {
        String folder = Storage.folderFor(zip.getName());
        if (folder == null) throw new IllegalStateException("no usable name: " + zip.getName());
        File d = new File(root(), folder);
        d.mkdirs();
        ZipFile z = new ZipFile(zip);
        try {
            // a zip of the folder itself has the files one level down
            String prefix = "";
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.endsWith("theme.json")) {
                    prefix = n.substring(0, n.length() - "theme.json".length());
                    break;
                }
            }
            en = z.entries();
            boolean found = false;
            long total = 0;
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.startsWith(prefix)) continue;
                String rel = n.substring(prefix.length());
                if (rel.contains("/") || rel.contains("..") || rel.isEmpty()) continue;   // flat folder only
                if (e.getSize() > MAX_FILE) continue;
                File to = new File(d, rel);
                // the bytes really copied count, not the size the zip claims
                long got = copy(z.getInputStream(e), to, Math.min(MAX_FILE, MAX_TOTAL - total));
                if (got < 0) {
                    to.delete();
                    if (total + MAX_FILE > MAX_TOTAL) throw new IllegalStateException("the theme is too big");
                    continue;
                }
                total += got;
                if (rel.equals("theme.json")) found = true;
            }
            if (!found) throw new IllegalStateException("no theme.json in " + zip.getName());
        } finally {
            z.close();
        }
        return folder;
    }

    /** Theme zips waiting in Download. */
    static List<File> zipsInDownload() {
        List<File> out = new ArrayList<>();
        File[] l = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).listFiles();
        if (l != null) for (File f : l) if (f.getName().toLowerCase(java.util.Locale.US).endsWith(".zip")) out.add(f);
        return out;
    }

    /** Copies at most `max` bytes; -1 when there was more. */
    private static long copy(InputStream in, File to, long max) throws Exception {
        OutputStream o = new FileOutputStream(to);
        byte[] b = new byte[65536];
        long total = 0;
        int n;
        try {
            while ((n = in.read(b)) > 0) {
                if ((total += n) > max) return -1;
                o.write(b, 0, n);
            }
        } finally {
            o.close();
            in.close();
        }
        return total;
    }

    private static void copy(InputStream in, File to) throws Exception {
        OutputStream o = new FileOutputStream(to);
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        o.close();
        in.close();
    }

    private static String read(File f) throws Exception {
        return read(new FileInputStream(f));
    }

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return o.toString("UTF-8");
    }

    private Theme() {
    }
}
