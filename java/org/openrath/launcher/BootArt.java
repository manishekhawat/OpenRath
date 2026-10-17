package org.openrath.launcher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Makes a still boot logo (a 24-bit BMP) and a boot animation (bootanimation.zip) from the car
 * profile: the brand's badge, or the owner's own logo picture, or the OpenRath wheel, with the brand
 * and the car's name under it. Android doesn't let an app install either, so the files are saved for
 * the stereo's factory settings (logo) and its boot animation app or ADB (animation), with the steps.
 */
final class BootArt {

    private static final int DIALOG = android.R.style.Theme_Material_Dialog_Alert;

    /** 25 frames a second for 3.6 s; the last frame then holds until Android has started. */
    static final int FPS = 25, FRAMES = 90;

    // ------------------------------------------------------------------ timeline (seconds), tested

    static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    static float markAlpha(float t) {
        return ease((t - 0.3f) / 1.2f);
    }

    /** The wheel rolls in: degrees still to turn. */
    static float spin(float t) {
        return -300 * (1 - ease(t / 1.8f));
    }

    /** The light sweeping across the mark: 0 to 1 while it crosses, outside that range otherwise. */
    static float shine(float t) {
        return (t - 1.6f) / 1.0f;
    }

    static float rule(float t) {
        return ease((t - 2.0f) / 0.7f);
    }

    static float brandAlpha(float t) {
        return ease((t - 2.3f) / 0.8f);
    }

    static float nameAlpha(float t) {
        return ease((t - 2.8f) / 0.8f);
    }

    /** The animation's desc.txt: play part0 once, then hold part1 until boot completes. */
    static String desc(int w, int h) {
        return w + " " + h + " " + FPS + "\np 1 0 part0\np 0 0 part1\n";
    }

    /** A 24-bit uncompressed BMP (what stereo logo menus import): bottom-up rows padded to 4 bytes. */
    static byte[] bmp(int[] argb, int w, int h) {
        int row = (w * 3 + 3) / 4 * 4, size = 54 + row * h;
        byte[] b = new byte[size];
        b[0] = 'B';
        b[1] = 'M';
        le(b, 2, size);
        le(b, 10, 54);
        le(b, 14, 40);
        le(b, 18, w);
        le(b, 22, h);
        b[26] = 1;
        b[28] = 24;
        le(b, 34, row * h);
        le(b, 38, 2835);   // 72 dpi
        le(b, 42, 2835);
        for (int y = 0; y < h; y++) {
            int o = 54 + (h - 1 - y) * row;
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                b[o++] = (byte) c;
                b[o++] = (byte) (c >> 8);
                b[o++] = (byte) (c >> 16);
            }
        }
        return b;
    }

    private static void le(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
        b[at + 2] = (byte) (v >> 16);
        b[at + 3] = (byte) (v >> 24);
    }

    /** Add a file to a zip without compression, as Android's boot animation player requires. */
    static void stored(ZipOutputStream z, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(data.length);
        e.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        e.setCrc(crc.getValue());
        z.putNextEntry(e);
        z.write(data);
        z.closeEntry();
    }

    // ------------------------------------------------------------------ what is drawn

    /** The mark: outlines (24 units tall) of a built-in badge or the wheel, or the owner's own picture. */
    static final class Mark {
        final List<float[]> polys = new ArrayList<>();
        float w = 24, h = 24;
        boolean wheel;
        Bitmap pic;
    }

    static Mark mark(Context c, Prefs p) {
        Mark m = new Mark();
        File own = Ui.customFile(c, "logo");
        if (own.exists()) {
            m.pic = android.graphics.BitmapFactory.decodeFile(own.getPath());
            if (m.pic != null) {
                m.w = 24f * m.pic.getWidth() / m.pic.getHeight();
                return m;
            }
        }
        String brand = Car.brand(p);
        if (!brand.isEmpty()) {
            try {
                InputStream in = c.getAssets().open("badges.json");
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
                in.close();
                org.json.JSONObject b = new org.json.JSONObject(o.toString("UTF-8")).optJSONObject(brand);
                if (b != null) {
                    m.w = (float) b.getDouble("w");
                    m.h = (float) b.getDouble("h");
                    org.json.JSONArray ps = b.getJSONArray("polys");
                    for (int i = 0; i < ps.length(); i++) {
                        org.json.JSONArray pts = ps.getJSONArray(i);
                        float[] f = new float[pts.length() * 2];
                        for (int j = 0; j < pts.length(); j++) {
                            f[2 * j] = (float) pts.getJSONArray(j).getDouble(0);
                            f[2 * j + 1] = (float) pts.getJSONArray(j).getDouble(1);
                        }
                        m.polys.add(f);
                    }
                    return m;
                }
            } catch (Exception ignored) {
            }
        }
        m.wheel = true;
        return m;
    }

    /** The wheel (rim, hub, eight spokes) as separate outlines around (0, 0), u per unit. */
    private static List<Path> wheel(float u) {
        List<Path> out = new ArrayList<>();
        Path rim = new Path();
        rim.setFillType(Path.FillType.EVEN_ODD);
        rim.addCircle(0, 0, 11.8f * u, Path.Direction.CW);
        rim.addCircle(0, 0, 9.6f * u, Path.Direction.CW);
        out.add(rim);
        Path hub = new Path();
        hub.setFillType(Path.FillType.EVEN_ODD);
        hub.addCircle(0, 0, 4.0f * u, Path.Direction.CW);
        hub.addCircle(0, 0, 2.2f * u, Path.Direction.CW);
        out.add(hub);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a), px = -dy * 0.75f * u, py = dx * 0.75f * u;
            Path s = new Path();
            s.moveTo(dx * 3.6f * u + px, dy * 3.6f * u + py);
            s.lineTo(dx * 9.9f * u + px, dy * 9.9f * u + py);
            s.lineTo(dx * 9.9f * u - px, dy * 9.9f * u - py);
            s.lineTo(dx * 3.6f * u - px, dy * 3.6f * u - py);
            s.close();
            out.add(s);
        }
        return out;
    }

    // chrome: bright sky, dark horizon, bright ground (as the boot logo's artwork)
    private static final float[] CHROME_AT = {0, .38f, .5f, .56f, .8f, 1};
    private static final float[] CHROME_L = {.95f, .78f, .30f, .55f, .85f, .62f};

    private static Shader chrome(float top, float bottom) {
        int[] cols = new int[CHROME_L.length];
        for (int i = 0; i < cols.length; i++) {
            float l = CHROME_L[i];
            cols[i] = 0xFF000000 | Math.min(255, Math.round(l * .96f * 255)) << 16
                    | Math.min(255, Math.round(l * .98f * 255)) << 8 | Math.min(255, Math.round(l * 1.03f * 255));
        }
        return new LinearGradient(0, top, 0, bottom, cols, CHROME_AT, Shader.TileMode.CLAMP);
    }

    /** One frame at time t (seconds) on a w x h canvas cleared to black. */
    static void draw(Canvas c, int w, int h, float t, Mark m, String brand, String name, Typeface light) {
        float k = Math.min(h / 600f, w / 700f), cx = w / 2f;
        float oy = (h - 600 * k) / 2;   // centred on a portrait or tall screen
        float cy = oy + 222 * k, lineY = oy + 378 * k, brandY = oy + 424 * k, nameY = oy + 496 * k;
        if (name.isEmpty() && brand.isEmpty()) cy = h / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // a faint glow behind the mark
        float g = 0.10f * ease((t - 0.8f) / 1.5f);
        if (g > 0) {
            p.setShader(new RadialGradient(cx, cy, 520 * k, new int[]{Math.round(g * 255) << 24 | 0xC0CCE0, 0},
                    null, Shader.TileMode.CLAMP));
            c.save();
            c.scale(1, 300f / 520f, cx, cy);
            c.drawCircle(cx, cy, 520 * k, p);
            c.restore();
            p.setShader(null);
        }

        // the mark, fading and growing in (the wheel rolling), then a light sweeping across it
        float a = markAlpha(t);
        if (a > 0) {
            float markH = 200 * k * (m.wheel ? 0.95f : 1f), u = markH / 24 * (0.9f + 0.1f * a);
            float mw = m.w * u, mh = m.h * u;
            RectF box = new RectF(cx - mw / 2, cy - mh / 2, cx + mw / 2, cy + mh / 2);
            RectF wide = new RectF(box.left - 4 * k, box.top - 4 * k, box.right + 4 * k, box.bottom + 4 * k);
            int layer = c.saveLayerAlpha(wide, Math.round(255 * a));
            if (m.pic != null) {
                c.drawBitmap(m.pic, null, box, p);
            } else {
                p.setShader(chrome(box.top, box.bottom));
                if (m.wheel) {
                    Matrix rot = new Matrix();
                    rot.setRotate(spin(t));
                    rot.postTranslate(cx, cy);
                    for (Path s : wheel(u)) {
                        s.transform(rot);
                        c.drawPath(s, p);
                    }
                } else {
                    // one path, filled by winding like the SVG it came from, so rings keep their holes
                    Path s = new Path();
                    for (float[] f : m.polys) {
                        for (int i = 0; i < f.length; i += 2) {
                            float x = box.left + f[i] * u, y = box.top + f[i + 1] * u;
                            if (i == 0) s.moveTo(x, y);
                            else s.lineTo(x, y);
                        }
                        s.close();
                    }
                    c.drawPath(s, p);
                }
                p.setShader(null);
            }
            float sp = shine(t);
            if (sp >= 0 && sp <= 1) {
                float bx = box.left - 60 * k + sp * (box.width() + 120 * k);
                Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
                sh.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                sh.setShader(new LinearGradient(bx - 50 * k, cy - 22 * k, bx + 50 * k, cy + 22 * k,
                        new int[]{0x00FFFFFF, 0x99FFFFFF, 0xEEFFFFFF, 0x99FFFFFF, 0x00FFFFFF},
                        new float[]{0, .4f, .5f, .6f, 1}, Shader.TileMode.CLAMP));
                c.drawRect(wide, sh);
            }
            c.restoreToCount(layer);
        }

        // a thin rule growing from the middle
        float r = rule(t);
        if (r > 0 && !(brand.isEmpty() && name.isEmpty())) {
            float half = 230 * k * r;
            p.setShader(new LinearGradient(cx - half, 0, cx + half, 0, new int[]{0x00B3B8BF, 0xFFB3B8BF, 0x00B3B8BF},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(cx - half, lineY - 1.2f * k, cx + half, lineY + 1.2f * k, p);
            p.setShader(null);
        }

        // the brand, then the car's name
        text(c, brand, cx, brandY, 34 * k, 0.35f, Typeface.create("sans-serif-medium", Typeface.NORMAL), brandAlpha(t));
        text(c, name, cx, nameY, 58 * k, 0.5f, light, nameAlpha(t));
    }

    private static void text(Canvas c, String s, float x, float y, float size, float spacing, Typeface tf, float a) {
        if (s.isEmpty() || a <= 0) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setLetterSpacing(spacing);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShader(chrome(y - size * 0.75f, y + size * 0.1f));
        p.setAlpha(Math.round(255 * a));
        // keep it on screen: shrink a long name to fit
        float wd = p.measureText(s), max = c.getWidth() * 0.86f;
        if (wd > max) p.setTextSize(size * max / wd);
        c.drawText(s, x, y, p);
    }

    // ------------------------------------------------------------------ making the files

    interface Progress {
        void at(int frame);
    }

    /** Write bootlogo.bmp and bootanimation.zip for a w x h screen into dir; returns {logo, animation}. */
    static File[] make(Context c, Prefs p, int w, int h, File dir, Progress pr) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("can't create " + dir + " (allow storage access?)");
        Mark m = mark(c, p);
        String brand = brandLine(p), name = Car.name(p).toUpperCase(Locale.getDefault());
        Typeface light;
        try {
            light = Typeface.createFromAsset(c.getAssets(), "fonts/InterDisplay-Light.otf");
        } catch (RuntimeException e) {
            light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bm);
        File zip = new File(dir, "bootanimation.zip"), logo = new File(dir, "bootlogo.bmp");
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip));
        try {
            stored(z, "desc.txt", desc(w, h).getBytes("US-ASCII"));
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            for (int i = 0; i <= FRAMES; i++) {
                bm.eraseColor(0xFF000000);
                draw(cv, w, h, i / (float) FPS, m, brand, name, light);
                png.reset();
                bm.compress(Bitmap.CompressFormat.PNG, 100, png);
                // part0 is the animation; part1 is the last frame, held until boot completes
                stored(z, i < FRAMES ? String.format(Locale.US, "part0/%03d.png", i) : "part1/000.png", png.toByteArray());
                if (pr != null) pr.at(i);
            }
        } finally {
            z.close();
        }
        int[] px = new int[w * h];
        bm.getPixels(px, 0, w, 0, 0, w, h);
        bm.recycle();
        OutputStream o = new FileOutputStream(logo);
        try {
            o.write(bmp(px, w, h));
        } finally {
            o.close();
        }
        return new File[]{logo, zip};
    }

    /** The line above the car's name: the brand, else OPENRATH. */
    static String brandLine(Prefs p) {
        String b = Car.brandName(p).trim();
        return (b.isEmpty() ? "OpenRath" : b).toUpperCase(Locale.getDefault());
    }

    /** The screen's size the right way up for the panel (as the boot screen draws it). */
    static int[] nativeSize(android.app.Activity a) {
        android.graphics.Point s = new android.graphics.Point();
        android.view.Display d = a.getWindowManager().getDefaultDisplay();
        d.getRealSize(s);
        int rot = d.getRotation();
        boolean sideways = rot == android.view.Surface.ROTATION_90 || rot == android.view.Surface.ROTATION_270;
        return sideways ? new int[]{s.y, s.x} : new int[]{s.x, s.y};
    }

    /** USB sticks and SD cards whose top folder this app can write to. */
    static List<File> sticks() {
        List<File> out = new ArrayList<>();
        for (String base : new String[]{"/storage", "/mnt/media_rw", "/mnt"}) {
            File[] l = new File(base).listFiles();
            if (l == null) continue;
            for (File f : l) {
                String n = f.getName().toLowerCase(Locale.US);
                if (n.equals("emulated") || n.equals("self") || n.equals("sdcard") || n.equals("runtime") || n.equals("secure")
                        || n.equals("asec") || n.equals("obb") || n.equals("user") || n.equals("media_rw") || n.equals("expand")) continue;
                if (f.isDirectory() && f.canWrite() && !out.contains(f)) out.add(f);
            }
        }
        return out;
    }

    static void copy(File from, File to) throws IOException {
        InputStream in = new FileInputStream(from);
        OutputStream o = new FileOutputStream(to);
        try {
            byte[] b = new byte[65536];
            for (int n; (n = in.read(b)) > 0; ) o.write(b, 0, n);
        } finally {
            in.close();
            o.close();
        }
    }

    /** The firmware's own boot animation app, if it has one. */
    static ResolveInfo bootApp(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(main, 0)) {
            String l = String.valueOf(r.loadLabel(pm)).toLowerCase(Locale.US);
            if (l.contains("开机") || l.contains("boot") && (l.contains("anim") || l.contains("logo"))) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------ the page

    static void menu(final HomeActivity a) {
        int[] here = nativeSize(a);
        final int[][] sizes = {here, {1024, 600}, {1280, 720}, {1280, 800}, {1920, 720}};
        String[] names = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) names[i] = (i == 0 ? "This screen, " : "") + sizes[i][0] + " x " + sizes[i][1];
        new AlertDialog.Builder(a, DIALOG).setTitle("Make a boot logo and animation for")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        make(a, sizes[w][0], sizes[w][1]);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private static void make(final HomeActivity a, final int w, final int h) {
        final AlertDialog wait = new AlertDialog.Builder(a, DIALOG).setTitle("Making the boot animation")
                .setMessage("Drawing frame 1 of " + (FRAMES + 1) + "…").setCancelable(false).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                File[] made = null;
                final List<File> onUsb = new ArrayList<>();
                try {
                    File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS), "OpenRath boot " + w + "x" + h);
                    made = make(a, a.prefs, w, h, dir, new Progress() {
                        @Override
                        public void at(final int f) {
                            if (f % 5 != 0) return;
                            a.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    wait.setMessage("Drawing frame " + (f + 1) + " of " + (FRAMES + 1) + "…");
                                }
                            });
                        }
                    });
                    for (File s : sticks()) {
                        try {
                            for (File f : made) copy(f, new File(s, f.getName()));
                            File logoDir = new File(s, "logo");   // some firmwares look for the logo here instead
                            if (logoDir.isDirectory() || logoDir.mkdirs()) copy(made[0], new File(logoDir, made[0].getName()));
                            onUsb.add(s);
                        } catch (IOException ignored) {
                        }
                    }
                } catch (Throwable e) {
                    err = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                final String error = err;
                final File[] files = made;
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        wait.dismiss();
                        if (error != null) {
                            new AlertDialog.Builder(a, DIALOG).setTitle("Couldn't make them").setMessage(error)
                                    .setPositiveButton("OK", null).show();
                        } else done(a, files, onUsb);
                    }
                });
            }
        }).start();
    }

    private static void done(final HomeActivity a, File[] files, List<File> onUsb) {
        final ResolveInfo app = bootApp(a);
        String where = "Saved in " + files[0].getParent() + (onUsb.isEmpty() ? "" : " and on " + onUsb.get(0).getPath()) + ".";
        String usb = onUsb.isEmpty() ? "Copy bootlogo.bmp and bootanimation.zip to the top folder of a USB stick, plus bootlogo.bmp into a folder "
                + "named logo on it (with the file manager, or from a computer), and plug it into the stereo." : "They're already on the USB stick; keep it plugged in.";
        String msg = where + "\n\n" + usb + "\n\n"
                + "STILL BOOT LOGO (safe):\n"
                + "1. Open the stereo's Settings, then Factory settings (the code is in the stereo's manual or from the seller).\n"
                + "2. Find Logo, Car logo or Boot logo, choose Custom or Import, and pick bootlogo.bmp.\n"
                + "3. Note which logo was chosen before, so you can go back to it.\n\n"
                + "BOOT ANIMATION:\n"
                + (app != null ? "This stereo has \"" + app.loadLabel(a.getPackageManager()) + "\". Open it and import bootanimation.zip from the USB stick.\n"
                : "Some stereos have a Boot animation app (in the app list, or in Factory settings) that imports bootanimation.zip from a USB stick. ")
                + (app != null ? "" : "Without one it needs a computer with ADB, and a mistake can stop the stereo from starting, so "
                + "follow BOOT.md in OpenRath's source carefully and keep a copy of the original.");
        AlertDialog.Builder b = new AlertDialog.Builder(a, DIALOG).setTitle("Boot logo and animation made").setMessage(msg)
                .setPositiveButton("Done", null);
        if (app != null) b.setNeutralButton("Open " + app.loadLabel(a.getPackageManager()), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                try {
                    a.startActivity(new Intent(Intent.ACTION_MAIN).setClassName(app.activityInfo.packageName, app.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    android.widget.Toast.makeText(a, "Couldn't open it", android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
        b.show();
    }

    private BootArt() {
    }
}
