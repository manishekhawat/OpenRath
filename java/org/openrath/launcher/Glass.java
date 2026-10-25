package org.openrath.launcher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * The Glass look (Settings > Look > Themes), like a phone's frosted glass: a soft, colourful,
 * blurred wallpaper behind every screen, and see-through panels with a thin light edge, a brighter
 * top and big round corners over it. The car's units run Android 8 to 10, which can't blur what
 * is behind a view, so the wallpaper is blurred once, small, and drawn big and smooth: see-through
 * panels over a blurred picture read as frosted glass, at no cost per frame. The colour theme's
 * accent stays; the glass is darker at night. With Glass off nothing here changes the look.
 */
final class Glass {

    /** Glass is the look now (set by Theme.load from the setting). */
    static boolean on;
    /** Darker glass and wallpaper after dark (ThemeAuto.check says when). */
    static boolean night;
    private static boolean nightKnown;

    static final String[] WALLS = {"aurora", "sunset", "ocean", "mine"};
    static final String[] WALL_NAMES = {"Aurora", "Sunset", "Ocean", "My picture"};

    // the glass by day (white, see-through) and at night (darker)
    static final int DAY_FILL = 0x26FFFFFF, DAY_TOP = 0x3DFFFFFF, DAY_EDGE = 0x47FFFFFF;
    static final int NIGHT_FILL = 0x59000000, NIGHT_TOP = 0x1AFFFFFF, NIGHT_EDGE = 0x2EFFFFFF;
    /** Text on glass: white, and a light grey that still reads in daylight. */
    static final int TEXT = 0xFFFFFFFF, DAY_DIM = 0xFFD3D9E4, NIGHT_DIM = 0xFFB4BCCA;
    /** Corner radius of glass panels, in design px. */
    static final float RADIUS = 24;
    /** How much of the wallpaper's brightness stays (darker so white text reads on it). */
    static final float DAY_SHADE = 0.72f, NIGHT_SHADE = 0.42f;
    /** The size the wallpaper is made and blurred at; it is drawn big, smoothed. */
    static final int SMALL_W = 320, SMALL_H = 180;

    static boolean wanted(Prefs p) {
        return "on".equals(p.str("glass", "off"));
    }

    /** The wallpaper chosen: one of WALLS. */
    static String wall(Prefs p) {
        String w = p.str("glassWall", WALLS[0]);
        return Arrays.asList(WALLS).contains(w) ? w : WALLS[0];
    }

    static String wallName(Prefs p) {
        return WALL_NAMES[Arrays.asList(WALLS).indexOf(wall(p))];
    }

    /** Night or day now; true when Glass is on and that changes its look (rebuild then). */
    static boolean setNight(boolean n) {
        nightKnown = true;
        if (n == night) return false;
        night = n;
        return on;
    }

    /**
     * Turn the theme's chrome colours into glass ones when Glass is on; Theme.load calls this after
     * setting the theme's own, before the pictures are tinted. The accent stays the theme's.
     */
    static void apply(Context c, Prefs p) {
        on = wanted(p);
        if (!on) return;
        if (!nightKnown) {
            night = ThemeAuto.night(p, null, System.currentTimeMillis());
            nightKnown = true;
        }
        int oldDim = Ui.SILVER_DIM;
        Ui.SILVER = TEXT;
        Ui.SILVER_DIM = night ? NIGHT_DIM : DAY_DIM;
        // the silver paint's grey labels become the light grey, so "plain" highlights stay white
        if (Ui.ACCENT == oldDim) Ui.ACCENT = Ui.SILVER_DIM;
        Ui.PANEL = night ? NIGHT_FILL : DAY_FILL;
        Ui.PANEL_TOP = night ? NIGHT_TOP : DAY_TOP;
        Ui.EDGE = night ? NIGHT_EDGE : DAY_EDGE;
        Ui.RADIUS = RADIUS;
        Ui.CHROME = false;
        if (Ui.ICONS == 0) Ui.ICONS = 0xFFFFFFFF;   // the dock and media pictures in white
        Ui.ROAD_FILL = 0x47000000;
        Ui.ROAD_LINE = 0xD9FFFFFF;
        wallpaper(c, p);
        Ui.BACKGROUND = background;
    }

    // ------------------------------------------------------------------ the wallpaper

    private static Bitmap wallBmp;
    private static String wallFor;
    /** An opaque colour like the wallpaper's, darker: under everything and for mixing text colours. */
    private static int background = 0xFF101320;

    /** The blurred wallpaper, made again only when the choice, the picture or night changes. */
    static Bitmap wallpaper(Context c, Prefs p) {
        String w = wall(p);
        File mine = null;
        Bitmap themeBg = null;
        if ("mine".equals(w)) {
            List<File> l = Layouts.myWalls();
            if (!l.isEmpty()) mine = l.get(0);
            else themeBg = Theme.background();
        }
        String key = w + "|" + night + "|" + (mine == null ? "" : mine.getPath() + mine.lastModified())
                + "|" + (themeBg == null ? 0 : System.identityHashCode(themeBg));
        if (key.equals(wallFor) && wallBmp != null) return wallBmp;
        int[] px = null;
        try {
            Bitmap src = mine != null ? Pics.file(c, mine) : themeBg;
            if (src != null) px = blur(small(src), SMALL_W, SMALL_H, 8);
        } catch (Throwable e) {
            Diag.note("glass wallpaper", e);
        }
        if (px == null) px = blur(paint("mine".equals(w) ? 0 : Arrays.asList(WALLS).indexOf(w), SMALL_W, SMALL_H), SMALL_W, SMALL_H, 4);
        px = shade(px, night ? NIGHT_SHADE : DAY_SHADE);
        background = shade(new int[]{average(px)}, 0.7f)[0];
        try {
            wallBmp = Bitmap.createBitmap(px, SMALL_W, SMALL_H, Bitmap.Config.ARGB_8888);
            wallFor = key;
        } catch (Throwable e) {
            Diag.note("glass wallpaper", e);
            wallBmp = null;
        }
        return wallBmp;
    }

    /** A picture cropped to fill SMALL_W x SMALL_H, as pixels. */
    private static int[] small(Bitmap src) {
        Bitmap s = Bitmap.createBitmap(SMALL_W, SMALL_H, Bitmap.Config.ARGB_8888);
        float sc = Math.max(SMALL_W / (float) src.getWidth(), SMALL_H / (float) src.getHeight());
        float w = src.getWidth() * sc, h = src.getHeight() * sc;
        new Canvas(s).drawBitmap(src, null, new RectF((SMALL_W - w) / 2, (SMALL_H - h) / 2, (SMALL_W + w) / 2, (SMALL_H + h) / 2),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] px = new int[SMALL_W * SMALL_H];
        s.getPixels(px, 0, SMALL_W, 0, 0, SMALL_W, SMALL_H);
        s.recycle();
        for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000;
        return px;
    }

    /** A small, blurred copy of a picture (a layout's), for the frost inside glass parts; null if it can't be made. */
    static Bitmap frost(Bitmap src) {
        try {
            float sc = 240f / Math.max(src.getWidth(), src.getHeight());
            int w = Math.max(1, Math.round(src.getWidth() * sc)), h = Math.max(1, Math.round(src.getHeight() * sc));
            Bitmap s = Bitmap.createScaledBitmap(src, w, h, true);
            int[] px = new int[w * h];
            s.getPixels(px, 0, w, 0, 0, w, h);
            if (s != src) s.recycle();
            for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000;
            return Bitmap.createBitmap(blur(px, w, h, 5), w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable e) {
            Diag.note("glass frost", e);
            return null;
        }
    }

    // the built-in wallpapers: a dark gradient, top to bottom, with soft spots of colour on it
    private static final int[][] BASE = {{0xFF0B1233, 0xFF1A0F33}, {0xFF2B1036, 0xFF140A26}, {0xFF06213A, 0xFF041222}};
    private static final int[][] SPOT_COLOURS = {
            {0xFF1FA3A0, 0xFF3E5BD8, 0xFF8A3FC8, 0xFFD2477E, 0xFF1D7FC4},
            {0xFFF0743E, 0xFFD8406A, 0xFF6A2FA8, 0xFFF2A63C, 0xFF9C2D6B},
            {0xFF1E88C8, 0xFF14B3A6, 0xFF2A4FB8, 0xFF58C7E0, 0xFF164E8A}};
    /** Each spot's centre (share of the width, of the height) and size (share of the height). */
    private static final float[][][] SPOTS = {
            {{.12f, .20f, .60f}, {.52f, .10f, .55f}, {.88f, .55f, .62f}, {.32f, .92f, .55f}, {.72f, 1.0f, .45f}},
            {{.15f, .25f, .60f}, {.55f, .15f, .55f}, {.92f, .60f, .62f}, {.30f, .95f, .55f}, {.75f, .98f, .45f}},
            {{.18f, .18f, .62f}, {.70f, .28f, .58f}, {.50f, .95f, .62f}, {.95f, .82f, .40f}, {.02f, .75f, .48f}}};
    private static final float SPOT_STRENGTH = 0.85f;

    /** Built-in wallpaper `which` (0 Aurora, 1 Sunset, 2 Ocean; anything else Aurora) as w x h opaque pixels. Pure, for the tests. */
    static int[] paint(int which, int w, int h) {
        if (which < 0 || which >= BASE.length) which = 0;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            int row = Ui.mix(BASE[which][1], BASE[which][0], h > 1 ? y / (float) (h - 1) : 0);
            for (int x = 0; x < w; x++) {
                int c = row;
                for (int s = 0; s < SPOTS[which].length; s++) {
                    float[] sp = SPOTS[which][s];
                    float dx = (x - sp[0] * w) / h, dy = (y - sp[1] * h) / h;
                    float t = 1 - (float) Math.sqrt(dx * dx + dy * dy) / sp[2];
                    if (t <= 0) continue;
                    float wt = SPOT_STRENGTH * t * t * (3 - 2 * t);   // smooth to nothing at the spot's edge
                    c = Ui.mix(SPOT_COLOURS[which][s], c, wt);
                }
                px[y * w + x] = c;
            }
        }
        return px;
    }

    /** Opaque pixels blurred: three box blurs of `radius` each way, edges held, any radius at one cost. Pure, for the tests. */
    static int[] blur(int[] px, int w, int h, int radius) {
        int[] a = px.clone(), t = new int[px.length];
        if (radius < 1 || w < 1 || h < 1) return a;
        for (int pass = 0; pass < 3; pass++) {
            box(a, t, w, h, radius, true);
            box(t, a, w, h, radius, false);
        }
        return a;
    }

    /** One box blur along rows (across) or columns, with a running sum. */
    private static void box(int[] in, int[] out, int w, int h, int rad, boolean across) {
        int lines = across ? h : w, len = across ? w : h, n = 2 * rad + 1;
        for (int l = 0; l < lines; l++) {
            int r = 0, g = 0, b = 0;
            for (int d = -rad; d <= rad; d++) {
                int p = in[at(l, Math.max(0, Math.min(len - 1, d)), w, across)];
                r += (p >> 16) & 255;
                g += (p >> 8) & 255;
                b += p & 255;
            }
            for (int i = 0; i < len; i++) {
                out[at(l, i, w, across)] = 0xFF000000 | ((r + n / 2) / n) << 16 | ((g + n / 2) / n) << 8 | ((b + n / 2) / n);
                int add = in[at(l, Math.min(len - 1, i + rad + 1), w, across)], drop = in[at(l, Math.max(0, i - rad), w, across)];
                r += ((add >> 16) & 255) - ((drop >> 16) & 255);
                g += ((add >> 8) & 255) - ((drop >> 8) & 255);
                b += (add & 255) - (drop & 255);
            }
        }
    }

    private static int at(int line, int i, int w, boolean across) {
        return across ? line * w + i : i * w + line;
    }

    /** Each pixel's colour times f (0 black to 1 as it was), opaque. Pure, for the tests. */
    static int[] shade(int[] px, float f) {
        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            out[i] = 0xFF000000 | Math.round(((p >> 16) & 255) * f) << 16 | Math.round(((p >> 8) & 255) * f) << 8 | Math.round((p & 255) * f);
        }
        return out;
    }

    /** The average colour of opaque pixels. Pure, for the tests. */
    static int average(int[] px) {
        if (px.length == 0) return 0xFF000000;
        long r = 0, g = 0, b = 0;
        for (int p : px) {
            r += (p >> 16) & 255;
            g += (p >> 8) & 255;
            b += p & 255;
        }
        return 0xFF000000 | (int) (r / px.length) << 16 | (int) (g / px.length) << 8 | (int) (b / px.length);
    }

    /**
     * a over b by t (1 all a, 0 all b), see-through colours included: the alpha mixes as the colour
     * does. Ui.mix uses this while Glass is on, so a colour mixed with a glass panel stays glass. Pure.
     */
    static int mix(int a, int b, float t) {
        int al = Math.round(((a >>> 24) & 255) * t + ((b >>> 24) & 255) * (1 - t));
        int r = Math.round(((a >> 16) & 255) * t + ((b >> 16) & 255) * (1 - t));
        int g = Math.round(((a >> 8) & 255) * t + ((b >> 8) & 255) * (1 - t));
        int bl = Math.round((a & 255) * t + (b & 255) * (1 - t));
        return al << 24 | r << 16 | g << 8 | bl;
    }

    /** What a see-through colour looks like over an opaque one. Pure, for the tests. */
    static int over(int top, int under) {
        float a = ((top >>> 24) & 255) / 255f;
        return Ui.mix(top | 0xFF000000, under | 0xFF000000, a);
    }

    // ------------------------------------------------------------------ drawing

    // made on first use (the tests run without Android's drawing classes)
    private static Paint smooth, shadowPaint;
    private static Rect DST;
    private static Path CUT;
    private static RectF R;

    /** The wallpaper filling w x h without stretching, smoothed (it is small and blurred). */
    static void drawWall(Canvas c, int w, int h) {
        Bitmap b = wallBmp;
        if (b == null) {
            c.drawColor(Ui.BACKGROUND);
            return;
        }
        if (smooth == null) {
            smooth = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            DST = new Rect();
        }
        float sc = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        int bw = Math.round(b.getWidth() * sc), bh = Math.round(b.getHeight() * sc);
        DST.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        c.drawBitmap(b, null, DST, smooth);
    }

    /** The wallpaper as a background for a whole screen. */
    static Drawable backdrop() {
        return new Drawable() {
            @Override
            public void draw(Canvas c) {
                Rect r = getBounds();
                c.save();
                c.translate(r.left, r.top);
                drawWall(c, r.width(), r.height());
                c.restore();
            }

            @Override
            public void setAlpha(int a) {
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter f) {
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.OPAQUE;
            }
        };
    }

    /** A whole screen's background: the wallpaper with Glass on, else the theme's background colour. */
    static void screen(View v) {
        if (on) v.setBackground(backdrop());
        else v.setBackgroundColor(Ui.BACKGROUND);
    }

    /**
     * A soft shadow under a glass panel at l, t, r, b (corner `radius` px), drawn outside the panel
     * only, so it doesn't darken the glass.
     */
    static void shadow(Canvas c, float l, float t, float r, float b, float radius) {
        if (shadowPaint == null) {
            shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            CUT = new Path();
            R = new RectF();
        }
        c.save();
        CUT.reset();
        R.set(l, t, r, b);
        CUT.addRoundRect(R, radius, radius, Path.Direction.CW);
        c.clipOutPath(CUT);
        float step = Math.max(1, Ui.px(2)), drop = Ui.px(4);
        shadowPaint.setColor(night ? 0x1A000000 : 0x14000000);
        for (int i = 4; i >= 1; i--) {
            float e = step * i;
            R.set(l - e, t - e + drop, r + e, b + e + drop);
            c.drawRoundRect(R, radius + e, radius + e, shadowPaint);
        }
        c.restore();
    }

    /** A glass panel: see-through, brighter at the top, a thin light edge; `radius` in design px. */
    static GradientDrawable pane(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.PANEL_TOP, Ui.PANEL, Ui.PANEL});
        g.setCornerRadius(radius * Ui.k);
        g.setStroke(Math.max(1, Ui.px(1)), Ui.EDGE);
        return g;
    }

    /** An opaque panel colour for pop-ups over other apps, where nothing is behind the glass to show. */
    static int solid() {
        return over(night ? 0x1FFFFFFF : 0x2EFFFFFF, Ui.BACKGROUND);
    }

    // ------------------------------------------------------------------ dialogs

    /** AlertDialog.Builder that dresses its dialogs in a glass card while Glass is on. */
    static final class Builder extends AlertDialog.Builder {
        Builder(Context c) {
            super(c);
        }

        Builder(Context c, int theme) {
            super(c, theme);
        }

        @Override
        public AlertDialog create() {
            AlertDialog d = super.create();
            if (on) dress(d);
            return d;
        }
    }

    /** A dark glass card behind the dialog, and its buttons as glass pills. */
    static void dress(final Dialog d) {
        Window w = d.getWindow();
        if (w == null) return;
        GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                night ? new int[]{0xF0232734, 0xF00E1016} : new int[]{0xEB394056, 0xEB1B1F2D});
        card.setCornerRadius(RADIUS * Ui.k);
        card.setStroke(Math.max(1, Ui.px(1)), night ? NIGHT_EDGE : DAY_EDGE);
        int in = Ui.px(16);
        w.setBackgroundDrawable(new android.graphics.drawable.InsetDrawable(card, in, in, in, in));
        if (!(d instanceof AlertDialog)) return;
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface x) {
                int[] which = {DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL};
                for (int i : which) {
                    Button b = ((AlertDialog) d).getButton(i);
                    if (b == null || b.getVisibility() != View.VISIBLE) continue;
                    GradientDrawable pill = new GradientDrawable();
                    pill.setCornerRadius(999);
                    pill.setColor(i == DialogInterface.BUTTON_POSITIVE ? 0x40FFFFFF : 0x1FFFFFFF);
                    pill.setStroke(Math.max(1, Ui.px(1)), 0x40FFFFFF);
                    b.setBackground(new android.graphics.drawable.InsetDrawable(pill, Ui.px(4), Ui.px(6), Ui.px(4), Ui.px(6)));
                    b.setTextColor(TEXT);
                    b.setPadding(Ui.px(22), 0, Ui.px(22), 0);
                }
            }
        });
    }

    private Glass() {
    }
}
