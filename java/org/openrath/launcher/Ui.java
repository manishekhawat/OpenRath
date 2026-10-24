package org.openrath.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared look: sizes are designed for a 1024 x 600 screen and scaled to fit the real one (any size,
 * landscape or portrait), times the owner's size setting; colours match the boot logo.
 */
final class Ui {
    // set from the theme pack (Theme.load); these are the built-in chrome values
    static int SILVER = 0xFFD8DCE2;
    static int SILVER_DIM = 0xFF8A8F96;
    static int PANEL = 0xFF0B0C0E, PANEL_TOP = 0xFF111215, BACKGROUND = 0xFF000000;
    static float RADIUS = 14;
    static boolean ROAD = true;
    static int EDGE = 0xFF4C5056;
    /** Silver gradient on the car name and welcome line; flat text colour when false. */
    static boolean CHROME = true;
    /** One colour for the built-in dock and media button pictures, or 0 to keep them chrome. */
    static int ICONS = 0;
    static int ROAD_FILL = 0xFF1D1F23, ROAD_LINE = 0xFFC9CDD3;
    /** Labels and highlights: silver, or the chosen body colour. */
    static int ACCENT = SILVER_DIM;

    // accent colours (named after car body paints), bright enough to read on black
    static final String[] THEMES = {"Splendid Silver", "Magma Grey", "Arctic White", "Oxford Blue", "Gallant Red",
            "Bluish Black", "Nutmeg Brown", "Alluring Blue"};
    private static final int[] THEME_COLOURS = {0xFF8A8F96, 0xFF9AA0A8, 0xFFE8E8E2, 0xFF6F8FC8, 0xFFD5525A,
            0xFF7D879C, 0xFFB98F6E, 0xFF5D9BE0};

    /** The accent colour of THEMES[i]. */
    static int colour(int i) {
        return THEME_COLOURS[Math.max(0, Math.min(THEME_COLOURS.length - 1, i))];
    }

    static void theme(Prefs p) {
        int i = Math.max(0, java.util.Arrays.asList(THEMES).indexOf(p.str("theme", THEMES[0])));
        ACCENT = THEME_COLOURS[i];
        // panel edges: the accent, darkened
        EDGE = i == 0 ? 0xFF4C5056 : mix(ACCENT, 0xFF000000, 0.45f);
    }

    /** a over b by t (1 all a); opaque, except with Glass on, where see-through colours stay see-through (Glass.mix). */
    static int mix(int a, int b, float t) {
        if (Glass.on) return Glass.mix(a, b, t);
        int r = Math.round(((a >> 16) & 255) * t + ((b >> 16) & 255) * (1 - t));
        int g = Math.round(((a >> 8) & 255) * t + ((b >> 8) & 255) * (1 - t));
        int bl = Math.round((a & 255) * t + (b & 255) * (1 - t));
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static float k = 1f;   // the design's scale on this screen, set by the dashboard (see scale)
    /** Settings > Look > Size, as a factor (1 = as designed). */
    static float user = 1f;
    /** The car profile's brand id (Car.brand), for a built-in badge img/logo_<brand>.png if there is one. */
    static String brand = "";
    /** The car profile's body, "car" (hatchback or saloon) or "suv", for img/car_on_suv.png and the like. */
    static String body = "car";

    /** The built-in pictures to try for a name, most specific first: car_on_suv_<brand>, car_on_suv, car_on. */
    static java.util.List<String> variants(String name) {
        java.util.List<String> v = new java.util.ArrayList<>();
        String b = "suv".equals(body) && name.startsWith("car_") ? "_suv" : "";
        if (!brand.isEmpty() && (name.equals("logo") || name.startsWith("car_"))) v.add(name + b + "_" + brand);
        if (!b.isEmpty()) v.add(name + b);
        v.add(name);
        return v;
    }

    /** How much to scale the 1024 x 600 design on a w x h screen: fit both ways, then the owner's size. */
    static float scale(int w, int h, float user) {
        if (w <= 0 || h <= 0) return user;
        return Math.min(h / 600f, w / 1024f) * user;
    }

    /** Portrait screens (and near-square ones) stack the boxes instead of using two columns. */
    static boolean portrait(int w, int h) {
        return h > w * 1.05f;
    }

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    static int px(float v) {
        return Math.round(v * k);
    }

    static GradientDrawable panel() {
        if (Glass.on) return Glass.pane(RADIUS);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{PANEL_TOP, PANEL});
        g.setCornerRadius(RADIUS * k);
        g.setStroke(Math.max(1, px(1.2f)), EDGE);
        return g;
    }

    /** The smallest text OpenRath draws (design px): smaller is hard to read at a glance in a moving car. */
    static final float MIN_TEXT = 12;

    static TextView text(Context c, float size, int colour, String family, int gravity) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.max(MIN_TEXT, size) * k);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setGravity(gravity);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        if (Glass.on) t.setShadowLayer(2 * k, 0, k, 0x59000000);   // white text keeps its edge on bright glass
        return t;
    }

    /** An opaque panel colour, for pop-ups and strips that may sit over other apps. */
    static int solidPanel() {
        return Glass.on ? Glass.solid() : PANEL_TOP | 0xFF000000;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, 12, ACCENT, "sans-serif-medium", Gravity.LEFT);
        t.setLetterSpacing(0.16f);
        t.setText(s);
        return t;
    }

    /** A PNG from assets/img, decoded once. */
    static Bitmap img(Context c, String name) {
        Bitmap b = CACHE.get(name);
        if (b != null) return b;
        b = custom(c, name);     // a picture the owner picked in Settings
        if (b == null) b = Theme.image(name);   // else the theme pack's, if it has one
        if (b != null) {
            CACHE.put(name, b);
            return b;
        }
        try {
            InputStream in = null;
            for (String v : variants(name)) {
                try {
                    in = c.getAssets().open("img/" + v + ".png");
                    break;
                } catch (java.io.IOException none) {
                    in = null;   // e.g. no badge for this brand: the OpenRath wheel
                }
            }
            if (in == null) return null;
            b = BitmapFactory.decodeStream(in);
            in.close();
            if (ICONS != 0 && b != null && (name.startsWith("dock_") || name.startsWith("btn_"))) b = tint(b, ICONS);
            else if (!CHROME && b != null && name.equals("logo")) b = tint(b, SILVER);   // flat themes: logo in the text colour
            CACHE.put(name, b);
        } catch (Exception e) {
            Diag.note("icon load", e);
        }
        return b;
    }

    /** Pictures picked in Settings > Car live in the app's own files as custom/<name>.png. */
    static java.io.File customFile(Context c, String name) {
        return new java.io.File(new java.io.File(c.getFilesDir(), "custom"), name + ".png");
    }

    private static Bitmap custom(Context c, String name) {
        java.io.File f = customFile(c, name);
        return f.exists() ? Pics.file(c, f) : null;   // no bigger than the screen needs
    }

    /** The picture's shape in one flat colour. */
    static Bitmap tint(Bitmap b, int colour) {
        Bitmap out = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new android.graphics.PorterDuffColorFilter(colour, android.graphics.PorterDuff.Mode.SRC_IN));
        new android.graphics.Canvas(out).drawBitmap(b, 0, 0, p);
        return out;
    }

    static void clearImages() {
        CACHE.clear();
    }

    private Ui() {
    }
}
