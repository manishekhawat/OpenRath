package org.openrath.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The owner's car in 3D, drawn by OpenRath itself: a low-poly body in one of four shapes (hatchback,
 * saloon, SUV, MUV, and the compact saloon for the models that are one), in the car's colour, with the number plate, lamps and wheels, on a 360 ground
 * ring. No model files and no 3D library: the faces are built here and painted back to front on a
 * Canvas. Metres; x to the car's right, y up, z forward.
 */
final class Car3D {

    static final String[] SHAPES = {"hatch", "sedan", "suv", "muv"};
    static final String[] SHAPE_NAMES = {"Hatchback", "Saloon (sedan)", "SUV", "MUV / people carrier"};
    /**
     * The short Indian saloon under four metres (Dzire, Amaze, Aura, Tigor): drawn for a saloon of
     * one of those models instead of the long one. Not one of the SHAPES to pick: it comes with the model.
     */
    static final String COMPACT = "compact";
    private static final String[] COMPACT_MODELS = {"dzire", "amaze", "aura", "tigor", "xcent", "aspire", "ameo", "zest"};

    static final int BODY = 0, GLASS = 1, TYRE = 2, HUB = 3, HEAD = 4, TAIL = 5, GRILLE = 6, PLATE = 7, UNDER = 8;

    /** Common Indian car colours: name and colour. */
    static final String[] COLOUR_NAMES = {"Pearl white", "Silver", "Grey", "Black", "Red", "Blue", "Brown", "Orange", "Green", "Beige"};
    static final int[] COLOURS = {0xFFE9ECEF, 0xFFB7BCC2, 0xFF5E646B, 0xFF16181B, 0xFFB3121B, 0xFF1E4F9C, 0xFF5A3A26, 0xFFD3601E, 0xFF2F5E3A, 0xFFCDBA96};

    /** A flat polygon, its outward normal and the smaller shapes painted on it. */
    static final class Face {
        final double[][] p;
        final int kind;
        final double[] n;
        final double[] c;
        /** {kind, u0, u1, v0, v1}: windows, lamps, the plate, laid on a four-cornered face. */
        final List<double[]> decals = new ArrayList<>();
        /** Drawing layer: underbody 0, wheels 1, lower body 2, cabin 3. */
        final int layer;

        Face(int kind, int layer, double[] centre, double[]... pts) {
            this.kind = kind;
            this.layer = layer;
            p = pts;
            c = centroid(pts);
            double[] nn = normal(pts);
            // outward: away from the part's centre
            if (dot(nn, sub(c, centre)) < 0) nn = new double[]{-nn[0], -nn[1], -nn[2]};
            n = nn;
        }

        Face decal(int k, double u0, double u1, double v0, double v1) {
            decals.add(new double[]{k, u0, u1, v0, v1});
            return this;
        }
    }

    /** Sizes of each shape: length, width, height, ground clearance, waist, bonnet, wheelbase, tyre radius, and where the cabin starts and ends. */
    static double[] dims(String shape) {
        //                    L     W     H     sill  belt  hood  wb    r     rearGlassBase roofRear roofFront windscreenBase
        if ("sedan".equals(shape)) return new double[]{4.40, 1.73, 1.48, .17, .92, .78, 2.60, .30, -1.35, -.75, .35, 1.05};
        // short bonnet, stubby boot, a little lower: a Dzire is 3.99 m long
        if (COMPACT.equals(shape)) return new double[]{3.99, 1.73, 1.50, .17, .92, .78, 2.45, .29, -1.40, -.80, .28, .98};
        if ("suv".equals(shape)) return new double[]{4.30, 1.79, 1.68, .24, 1.05, .98, 2.60, .34, -2.07, -1.97, .55, 1.15};
        if ("muv".equals(shape)) return new double[]{4.40, 1.75, 1.75, .20, 1.00, .90, 2.75, .32, -2.14, -2.08, .75, 1.40};
        return new double[]{3.85, 1.73, 1.50, .18, .95, .80, 2.45, .30, -1.81, -1.63, .35, 1.05};
    }

    static String shape(Prefs p) {
        String s = p.str("carShape", "");
        for (String k : SHAPES) if (k.equals(s)) return s;
        return "suv".equals(p.str("carBody", "car")) ? "suv" : "hatch";
    }

    /** The shape to draw: the one picked, or the compact saloon for a saloon (or a car with no shape yet) of those models. */
    static String drawn(Prefs p) {
        String s = shape(p);
        boolean saloon = "sedan".equals(s) || p.str("carShape", "").isEmpty();
        return saloon && compact(Car.model(p)) ? COMPACT : s;
    }

    /** Whether a model is one of India's sub-four-metre saloons ("Swift Dzire", "Amaze", "Aura"). */
    static boolean compact(String model) {
        String m = model == null ? "" : model.toLowerCase(Locale.US);
        for (String k : COMPACT_MODELS) if (m.matches(".*\\b" + k + "\\b.*")) return true;
        return false;
    }

    static int colour(Prefs p) {
        return p.num("carColour", COLOURS[0]);
    }

    /** The number plate: the cab's vehicle number when there is one. */
    static String plate(Prefs p) {
        String s = p.str("carPlate", "");
        if (s.isEmpty()) s = p.str("cabVehicle", "");
        return s.trim().toUpperCase(Locale.US);
    }

    /** Every face of the car. */
    static List<Face> build(String shape) {
        double[] d = dims(shape);
        double L = d[0], W = d[1], H = d[2], sill = d[3], belt = d[4], hood = d[5], wb = d[6], r = d[7];
        double zRB = d[8], zRR = d[9], zRF = d[10], zWS = d[11];
        List<Face> f = new ArrayList<>();
        double[] lowC = {0, (sill + belt) / 2, 0};

        // lower body: five stations from the rear bumper to the nose
        double[] zs = {-L / 2, -L / 2 + .12, zWS, L / 2 - .12, L / 2};
        double[] top = {belt - .05, belt, belt, hood, hood - .10};
        double[] bot = {sill + .12, sill, sill, sill, sill + .15};
        double[] hw = {W / 2 - .10, W / 2, W / 2, W / 2 - .04, W / 2 - .14};
        for (int i = 0; i < 4; i++)
            f.add(new Face(BODY, 2, lowC, v(-hw[i], top[i], zs[i]), v(hw[i], top[i], zs[i]), v(hw[i + 1], top[i + 1], zs[i + 1]), v(-hw[i + 1], top[i + 1], zs[i + 1])));
        // the sides, with the wheel arches cut out
        for (int side = -1; side <= 1; side += 2) {
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i < 5; i++) pts.add(v(side * hw[i], top[i], zs[i]));
            pts.add(v(side * hw[4], bot[4], zs[4]));
            double ra = r + .05;
            for (double zc : new double[]{wb / 2, -wb / 2}) {
                double t0 = Math.asin(Math.max(-1, (sill - r) / ra));
                for (int k = 0; k <= 10; k++) {
                    double t = t0 + (Math.PI - 2 * t0) * k / 10;
                    pts.add(v(side * W / 2, r + ra * Math.sin(t), zc + ra * Math.cos(t)));
                }
            }
            pts.add(v(side * hw[0], bot[0], zs[0]));
            f.add(new Face(BODY, 2, lowC, pts.toArray(new double[0][])));
        }
        // rear and front, seen from outside: bottom-left, bottom-right, top-right, top-left
        f.add(new Face(BODY, 2, lowC, v(hw[0], bot[0], zs[0]), v(-hw[0], bot[0], zs[0]), v(-hw[0], top[0], zs[0]), v(hw[0], top[0], zs[0]))
                .decal(TAIL, .03, .24, .62, .90).decal(TAIL, .76, .97, .62, .90).decal(PLATE, .35, .65, .22, .48));
        f.add(new Face(BODY, 2, lowC, v(-hw[4], bot[4], zs[4]), v(hw[4], bot[4], zs[4]), v(hw[4], top[4], zs[4]), v(-hw[4], top[4], zs[4]))
                .decal(HEAD, .05, .30, .58, .90).decal(HEAD, .70, .95, .58, .90).decal(GRILLE, .34, .66, .38, .80).decal(PLATE, .37, .63, .06, .30));
        // underbody, so nothing shows through under the car
        f.add(new Face(UNDER, 0, v(0, sill + 1, 0), v(-hw[2], sill, zs[0]), v(hw[2], sill, zs[0]), v(hw[2], sill, zs[4]), v(-hw[2], sill, zs[4])));

        // the cabin: a frustum from the waist to the roof
        double bw = W / 2 - .03, rw = W / 2 - .15;
        double[] cabC = {0, (belt + H) / 2, (zRB + zWS) / 2};
        f.add(new Face(BODY, 3, cabC, v(-rw, H, zRR), v(rw, H, zRR), v(rw, H, zRF), v(-rw, H, zRF)));
        f.add(new Face(BODY, 3, cabC, v(bw, belt, zRB), v(-bw, belt, zRB), v(-rw, H, zRR), v(rw, H, zRR)).decal(GLASS, .07, .93, .10, .90));
        f.add(new Face(BODY, 3, cabC, v(-bw, belt, zWS), v(bw, belt, zWS), v(rw, H, zRF), v(-rw, H, zRF)).decal(GLASS, .05, .95, .06, .92));
        for (int side = -1; side <= 1; side += 2) {
            double[] a = v(side * bw, belt, side < 0 ? zWS : zRB), b = v(side * bw, belt, side < 0 ? zRB : zWS);
            double[] c = v(side * rw, H, side < 0 ? zRR : zRF), e = v(side * rw, H, side < 0 ? zRF : zRR);
            Face s = new Face(BODY, 3, cabC, a, b, c, e);
            // two windows either side of the B pillar
            s.decal(GLASS, .05, .48, .10, .88).decal(GLASS, .53, .95, .10, .88);
            f.add(s);
        }

        // wheels: twelve-sided tyres with a hub
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                double xo = sx * (W / 2 + .01), xi = sx * (W / 2 - .19), zc = sz * wb / 2;
                double[] wc = {sx * (W / 2 - .09), r, zc};
                int n = 12;
                double[][] cap = new double[n][];
                double[][] hub = new double[n][];
                for (int k = 0; k < n; k++) {
                    double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n;
                    cap[k] = v(xo, r + r * Math.sin(a0), zc + r * Math.cos(a0));
                    hub[k] = v(xo + sx * .005, r + .62 * r * Math.sin(a0), zc + .62 * r * Math.cos(a0));
                    f.add(new Face(TYRE, 1, wc, v(xo, r + r * Math.sin(a0), zc + r * Math.cos(a0)), v(xo, r + r * Math.sin(a1), zc + r * Math.cos(a1)),
                            v(xi, r + r * Math.sin(a1), zc + r * Math.cos(a1)), v(xi, r + r * Math.sin(a0), zc + r * Math.cos(a0))));
                }
                f.add(new Face(TYRE, 1, wc, cap));
                f.add(new Face(HUB, 1, wc, hub));
            }
        return f;
    }

    // ------------------------------------------------------------------ the camera

    /** Where the eye is, orbiting the car: yaw 0 looks at the nose, yaw pi at the tail; pitch 0 level, pi/2 from above. */
    static final class Cam {
        double yaw = .7, pitch = .42, dist = 6.8;
        final double[] target = {0, .70, 0};
        double[] eye, f, r, u;
        float cx, cy, focal;

        void setup(float w, float h) {
            double cp = Math.cos(pitch);
            // further back from above, so the whole car and its ring fit
            double dd = dist + 3.2 * Math.pow(pitch / 1.53, 3);
            eye = new double[]{target[0] + dd * cp * Math.sin(yaw), target[1] + dd * Math.sin(pitch), target[2] + dd * cp * Math.cos(yaw)};
            f = norm(sub(target, eye));
            r = norm(cross(f, new double[]{0, 1, 0}));
            u = cross(r, f);
            cx = w / 2;
            cy = h / 2;
            focal = Math.min(w, h * 1.45f) * 1.05f;
        }

        /** Screen x, y and depth. */
        float[] project(double[] p) {
            double[] d = sub(p, eye);
            double z = Math.max(.05, dot(d, f));
            return new float[]{(float) (cx + dot(d, r) / z * focal), (float) (cy - dot(d, u) / z * focal), (float) z};
        }
    }

    // ------------------------------------------------------------------ drawing

    private static final double[] LIGHT = norm(new double[]{-.30, .90, .30});
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Bitmap plateBmp;
    private String plateFor;

    Car3D() {
        line.setStyle(Paint.Style.STROKE);
    }

    void draw(Canvas c, Cam cam, List<Face> faces, double[] dims, int colour, String plate, boolean yellow, boolean lightsOn) {
        cam.setup(c.getWidth(), c.getHeight());
        ground(c, cam, dims);
        List<Face> vis = new ArrayList<>();
        for (Face fa : faces) if (fa.layer == 0 || dot(fa.n, sub(cam.eye, fa.c)) > 0) vis.add(fa);
        final Cam k = cam;
        Collections.sort(vis, new Comparator<Face>() {
            @Override
            public int compare(Face a, Face b) {
                if (a.layer != b.layer) return a.layer - b.layer;
                return Double.compare(len(sub(b.c, k.eye)), len(sub(a.c, k.eye)));
            }
        });
        for (Face fa : vis) {
            float[][] s = new float[fa.p.length][];
            for (int i = 0; i < s.length; i++) s[i] = cam.project(fa.p[i]);
            fill.setColor(shade(base(fa.kind, colour, lightsOn), fa.n, fa.kind));
            poly(c, s, fill);
            for (double[] dc : fa.decals) {
                int kind = (int) dc[0];
                float[][] q = {bilerp(s, dc[1], dc[3]), bilerp(s, dc[2], dc[3]), bilerp(s, dc[2], dc[4]), bilerp(s, dc[1], dc[4])};
                if (kind == PLATE) {
                    plate(c, q, plate, yellow);
                    continue;
                }
                fill.setColor(shade(base(kind, colour, lightsOn), fa.n, kind));
                poly(c, q, fill);
            }
        }
    }

    /** The 360 ring around the car, and its shadow. */
    private void ground(Canvas c, Cam cam, double[] d) {
        line.setStrokeWidth(Math.max(1, c.getWidth() / 500f));
        for (double rad : new double[]{3.0, 4.6}) {
            line.setColor(rad < 4 ? 0x40FFC14D : 0x2EFFFFFF);
            float[][] ring = new float[48][];
            for (int i = 0; i < 48; i++) {
                double a = 2 * Math.PI * i / 48;
                ring[i] = cam.project(v(rad * Math.sin(a) * .82, 0, rad * Math.cos(a)));
            }
            path.reset();
            path.moveTo(ring[0][0], ring[0][1]);
            for (int i = 1; i < 48; i++) path.lineTo(ring[i][0], ring[i][1]);
            path.close();
            c.drawPath(path, line);
        }
        line.setColor(0x22FFFFFF);
        for (int i = 0; i < 8; i++) {
            double a = 2 * Math.PI * i / 8 + Math.PI / 8;
            float[] p0 = cam.project(v(2.3 * Math.sin(a) * .82, 0, 2.3 * Math.cos(a))), p1 = cam.project(v(4.6 * Math.sin(a) * .82, 0, 4.6 * Math.cos(a)));
            c.drawLine(p0[0], p0[1], p1[0], p1[1], line);
        }
        float[][] sh = new float[24][];
        for (int i = 0; i < 24; i++) {
            double a = 2 * Math.PI * i / 24;
            sh[i] = cam.project(v(d[1] * .62 * Math.sin(a), 0, d[0] * .56 * Math.cos(a)));
        }
        fill.setColor(0xA0000000);
        poly(c, sh, fill);
    }

    private void poly(Canvas c, float[][] s, Paint p) {
        path.reset();
        path.moveTo(s[0][0], s[0][1]);
        for (int i = 1; i < s.length; i++) path.lineTo(s[i][0], s[i][1]);
        path.close();
        c.drawPath(path, p);
    }

    private void plate(Canvas c, float[][] q, String text, boolean yellow) {
        String t = text.isEmpty() ? "OPENRATH" : text;
        String key = t + yellow;
        if (plateBmp == null || !key.equals(plateFor)) {
            plateFor = key;
            plateBmp = Bitmap.createBitmap(520, 112, Bitmap.Config.ARGB_8888);
            Canvas pc = new Canvas(plateBmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(yellow ? 0xFFF2C21B : 0xFFF4F4F2);
            pc.drawRoundRect(new RectF(2, 2, 518, 110), 14, 14, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            p.setColor(0xFF111111);
            pc.drawRoundRect(new RectF(6, 6, 514, 106), 12, 12, p);
            p.setStyle(Paint.Style.FILL);
            // the blue IND strip of India's number plates
            p.setColor(0xFF1F4BA5);
            pc.drawRect(14, 16, 58, 96, p);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(20);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            pc.drawText("IND", 36, 90, p);
            p.setColor(0xFF111111);
            p.setTextSize(64);
            float tw = p.measureText(t);
            if (tw > 430) p.setTextSize(64 * 430 / tw);
            pc.drawText(t, 290, 56 - (p.ascent() + p.descent()) / 2, p);
        }
        Matrix m = new Matrix();
        float bw = plateBmp.getWidth(), bh = plateBmp.getHeight();
        // the decal's corners: bottom-left, bottom-right, top-right, top-left
        float[] src = {0, bh, bw, bh, bw, 0, 0, 0};
        float[] dst = {q[0][0], q[0][1], q[1][0], q[1][1], q[2][0], q[2][1], q[3][0], q[3][1]};
        if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return;
        c.drawBitmap(plateBmp, m, fill);
    }

    private static int base(int kind, int colour, boolean lightsOn) {
        switch (kind) {
            case GLASS:
                return 0xFF233140;
            case TYRE:
                return 0xFF17181A;
            case HUB:
                return 0xFFC3C7CC;
            case HEAD:
                return lightsOn ? 0xFFFFF6D8 : 0xFFD9E2EC;
            case TAIL:
                return lightsOn ? 0xFFFF2A2A : 0xFFA3121A;
            case GRILLE:
                return 0xFF101113;
            case UNDER:
                return 0xFF0B0B0C;
            default:
                return colour;
        }
    }

    /** Lambert light from above, front left; glass and lamps keep more of their colour. */
    static int shade(int argb, double[] n, int kind) {
        double l = Math.max(0, dot(n, LIGHT));
        double k = kind == HEAD || kind == TAIL ? .80 + .20 * l : kind == GLASS ? .55 + .65 * l : .46 + .62 * l;
        int r = (int) Math.min(255, ((argb >> 16) & 255) * k), g = (int) Math.min(255, ((argb >> 8) & 255) * k), b = (int) Math.min(255, (argb & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A point on a four-cornered screen polygon (bottom-left, bottom-right, top-right, top-left). */
    static float[] bilerp(float[][] q, double u, double v) {
        float[] a = lerp(q[0], q[1], u), b = lerp(q[3], q[2], u);
        return lerp(a, b, v);
    }

    private static float[] lerp(float[] a, float[] b, double t) {
        return new float[]{(float) (a[0] + (b[0] - a[0]) * t), (float) (a[1] + (b[1] - a[1]) * t)};
    }

    // ------------------------------------------------------------------ vectors

    static double[] v(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double len(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    static double[] norm(double[] a) {
        double l = len(a);
        return l == 0 ? a : new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    static double[] centroid(double[][] p) {
        double[] c = new double[3];
        for (double[] q : p) for (int i = 0; i < 3; i++) c[i] += q[i] / p.length;
        return c;
    }

    /** Newell's normal: right for any flat-ish polygon, whatever its first three points. */
    static double[] normal(double[][] p) {
        double[] n = new double[3];
        for (int i = 0; i < p.length; i++) {
            double[] a = p[i], b = p[(i + 1) % p.length];
            n[0] += (a[1] - b[1]) * (a[2] + b[2]);
            n[1] += (a[2] - b[2]) * (a[0] + b[0]);
            n[2] += (a[0] - b[0]) * (a[1] + b[1]);
        }
        return norm(n);
    }
}
