package org.openrath.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings > Themes: every theme as a small picture of the home screen drawn in that theme's
 * colours, two to a row. Tapping one switches to it. A theme folder can ship its own
 * preview.png to show instead.
 */
final class ThemeGallery extends LinearLayout {

    interface Picked {
        void picked(Theme.Pack p);
    }

    ThemeGallery(Context c, List<Theme.Pack> packs, String current, int paintAccent, int paintEdge, final Picked picked) {
        super(c);
        setOrientation(VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < packs.size(); i++) {
            final Theme.Pack p = packs.get(i);
            if (i % 2 == 0) {
                row = new LinearLayout(c);
                LayoutParams rp = new LayoutParams(-1, -2);
                rp.bottomMargin = Ui.px(14);
                addView(row, rp);
            }
            boolean on = p.id.equals(current);
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(VERTICAL);
            int pad = Ui.px(8);
            tile.setPadding(pad, pad, pad, Ui.px(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * Ui.k);
            bg.setColor(on ? Ui.PANEL_TOP : Ui.PANEL);
            bg.setStroke(Math.max(1, Ui.px(on ? 2.5f : 1.2f)), on ? Ui.ACCENT : Ui.mix(Ui.EDGE, Ui.PANEL, 0.6f));
            tile.setBackground(bg);
            tile.addView(new Preview(c, p, Theme.Look.of(p.json, paintAccent, paintEdge)), new LayoutParams(-1, -2));
            TextView name = Ui.text(c, 17, Ui.SILVER, "sans-serif-medium", Gravity.LEFT);
            name.setText(on ? p.name + "   ✓ in use" : p.name);
            name.setPadding(Ui.px(6), Ui.px(10), Ui.px(6), 0);
            tile.addView(name);
            String about = p.description;
            if (!p.builtIn() && !p.author.isEmpty()) about = (about.isEmpty() ? "" : about + "  ·  ") + "by " + p.author;
            if (!p.builtIn()) about = (about.isEmpty() ? "" : about + "  ·  ") + "OpenRath/themes/" + p.dir.getName();
            TextView desc = Ui.text(c, 13, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
            desc.setSingleLine(false);
            desc.setMaxLines(2);
            desc.setText(about.isEmpty() ? " " : about);
            desc.setPadding(Ui.px(6), Ui.px(4), Ui.px(6), 0);
            tile.addView(desc);
            tile.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    picked.picked(p);
                }
            });
            LayoutParams tp = new LayoutParams(0, -2, 1f);
            if (i % 2 == 0) tp.rightMargin = Ui.px(7);
            else tp.leftMargin = Ui.px(7);
            row.addView(tile, tp);
        }
        if (packs.size() % 2 == 1 && row != null) row.addView(new View(c), new LayoutParams(0, 1, 1f));
    }

    private static final Map<String, Bitmap> RAW = new HashMap<>();

    /** A built-in picture straight from the app, ignoring the theme in use. */
    private static Bitmap raw(Context c, String name) {
        if (RAW.containsKey(name)) return RAW.get(name);
        Bitmap b = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = name.startsWith("car") ? 2 : 1;
            InputStream in = c.getAssets().open("img/" + name + ".png");
            b = BitmapFactory.decodeStream(in, null, o);
            in.close();
        } catch (Throwable ignored) {
        }
        RAW.put(name, b);
        return b;
    }

    /** The home screen in miniature: dock, five boxes, the car and a few numbers. */
    static final class Preview extends View {
        private final Theme.Pack pack;
        private final Theme.Look look;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
                text = new Paint(Paint.ANTI_ALIAS_FLAG), pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private Bitmap own;

        Preview(Context c, Theme.Pack p, Theme.Look l) {
            super(c);
            pack = p;
            look = l;
            stroke.setStyle(Paint.Style.STROKE);
            if (p.dir != null) {
                File f = new File(p.dir, "preview.png");
                if (!f.exists()) f = new File(p.dir, "preview.jpg");
                if (f.exists()) {
                    try {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inSampleSize = 2;
                        own = BitmapFactory.decodeFile(f.getPath(), o);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            setMeasuredDimension(width, Math.round(width * 600f / 1024f));
        }

        @Override
        protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight(), s = H / 600f;
            c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(new RectF(0, 0, W, H), 8 * Ui.k, 8 * Ui.k, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            if (own != null) {
                c.drawBitmap(own, null, new RectF(0, 0, W, H), pic);
                c.restore();
                return;
            }
            // background
            if (look.gradient != null) {
                fill.setShader(new LinearGradient(0, 0, W, H, look.gradient, null, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, W, H, fill);
                fill.setShader(null);
            } else c.drawColor(look.background);
            if (pack.dir != null) {
                File bg = new File(pack.dir, "background.jpg");
                if (!bg.exists()) bg = new File(pack.dir, "background.png");
                Bitmap wall = bg.exists() ? small(bg) : null;
                if (wall != null) c.drawBitmap(wall, null, new RectF(0, 0, W, H), pic);
            }
            // dock
            String[] dock = {"navi", "music", "radio", "bt", "phone", "apps"};
            for (int i = 0; i < dock.length; i++) {
                Bitmap b = raw(getContext(), "dock_" + dock[i]);
                if (b == null) continue;
                pic.setColorFilter(look.icons != 0 ? new PorterDuffColorFilter(look.icons, PorterDuff.Mode.SRC_IN) : null);
                float cy = (65 + i * 95) * s, r = 30 * s;
                c.drawBitmap(b, null, new RectF(48 * s - r, cy - r, 48 * s + r, cy + r), pic);
            }
            pic.setColorFilter(null);
            // boxes
            box(c, s, 110, 18, 497, 285);
            box(c, s, 110, 300, 497, 433);
            box(c, s, 110, 448, 497, 580);
            box(c, s, 512, 18, 1004, 428);
            box(c, s, 512, 444, 1004, 580);
            // car box: road, car, name and speed
            if (look.road) {
                android.graphics.Path road = new android.graphics.Path();
                road.moveTo(290 * s, 200 * s);
                road.lineTo(317 * s, 200 * s);
                road.lineTo(470 * s, 282 * s);
                road.lineTo(137 * s, 282 * s);
                road.close();
                fill.setShader(new LinearGradient(0, 200 * s, 0, 282 * s, look.roadFill & 0x00FFFFFF, look.roadFill, Shader.TileMode.CLAMP));
                c.drawPath(road, fill);
                fill.setShader(null);
                fill.setColor(look.roadLine);
                c.drawRect(301 * s, 262 * s, 306 * s, 282 * s, fill);
            }
            Bitmap car = raw(getContext(), "car_on");
            if (car != null) {
                float cw = 300 * s, ch = cw * car.getHeight() / car.getWidth();
                c.drawBitmap(car, null, new RectF(303 * s - cw / 2, 262 * s - ch, 303 * s + cw / 2, 262 * s), pic);
            }
            text(c, look.text, 13 * s, "sans-serif", 160 * s, 50 * s, false).setLetterSpacing(0.3f);
            c.drawText("OPENRATH", 132 * s, 50 * s, text);
            text.setLetterSpacing(0);
            text(c, look.text, 38 * s, "sans-serif-light", 405 * s, 62 * s, false);
            c.drawText("62", 382 * s, 62 * s, text);
            text(c, look.accent, 12 * s, "sans-serif-medium", 0, 0, false).setLetterSpacing(0.18f);
            c.drawText("LIGHTS ON", 128 * s, 272 * s, text);
            c.drawText("FM1", 534 * s, 480 * s, text);
            text.setLetterSpacing(0);
            // clock, weather, radio
            centre(c, "17:31", look.text, 58 * s, "sans-serif-light", 303 * s, 372 * s);
            centre(c, "Sunday 11 October", look.textDim, 15 * s, "sans-serif", 303 * s, 408 * s);
            text(c, look.text, 42 * s, "sans-serif-light", 0, 0, false);
            c.drawText("28°", 210 * s, 520 * s, text);
            text(c, look.text, 16 * s, "sans-serif-medium", 0, 0, true);
            c.drawText("Cloudy", 475 * s, 505 * s, text);
            text(c, look.textDim, 13 * s, "sans-serif", 0, 0, true);
            c.drawText("New Delhi", 475 * s, 535 * s, text);
            text.setTextAlign(Paint.Align.LEFT);
            Bitmap cloud = raw(getContext(), "w_cloud");
            if (cloud != null) c.drawBitmap(cloud, null, new RectF(138 * s, 482 * s, 194 * s, 538 * s), pic);
            centre(c, "98.30", look.text, 46 * s, "sans-serif-light", 758 * s, 528 * s);
            Bitmap prev = raw(getContext(), "btn_prev"), next = raw(getContext(), "btn_next");
            pic.setColorFilter(look.icons != 0 ? new PorterDuffColorFilter(look.icons, PorterDuff.Mode.SRC_IN) : null);
            if (prev != null) c.drawBitmap(prev, null, new RectF(545 * s, 490 * s, 590 * s, 535 * s), pic);
            if (next != null) c.drawBitmap(next, null, new RectF(927 * s, 490 * s, 972 * s, 535 * s), pic);
            pic.setColorFilter(null);
            // map: a few streets and the route in the accent colour
            stroke.setStrokeWidth(Math.max(1, 2 * s));
            stroke.setColor(Ui.mix(look.textDim, look.panel, 0.25f));
            float[][] streets = {{530, 60, 990, 40}, {520, 160, 1000, 150}, {520, 280, 1000, 300}, {600, 25, 640, 420},
                    {720, 25, 700, 420}, {850, 25, 890, 420}, {950, 25, 940, 420}};
            for (float[] l : streets) c.drawLine(l[0] * s, l[1] * s, l[2] * s, l[3] * s, stroke);
            stroke.setStrokeWidth(5 * s);
            stroke.setColor(look.accent);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            android.graphics.Path route = new android.graphics.Path();
            route.moveTo(760 * s, 330 * s);
            route.lineTo(760 * s, 245 * s);
            route.lineTo(818 * s, 190 * s);
            route.lineTo(818 * s, 100 * s);
            route.lineTo(905 * s, 52 * s);
            c.drawPath(route, stroke);
            c.restore();
        }

        private void box(Canvas c, float s, float l, float t, float r, float b) {
            RectF rect = new RectF(l * s, t * s, r * s, b * s);
            float rad = look.radius * s;
            fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, look.panelTop, look.panel, Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, rad, rad, fill);
            fill.setShader(null);
            if (Color.alpha(look.edge) > 0) {
                stroke.setStrokeWidth(Math.max(1, 1.6f * s));
                stroke.setColor(look.edge);
                c.drawRoundRect(rect, rad, rad, stroke);
            }
        }

        private Paint text(Canvas c, int colour, float size, String family, float x, float y, boolean right) {
            text.setColor(colour);
            text.setTextSize(size);
            text.setTypeface(Typeface.create(family, Typeface.NORMAL));
            text.setTextAlign(right ? Paint.Align.RIGHT : Paint.Align.LEFT);
            return text;
        }

        private void centre(Canvas c, String s, int colour, float size, String family, float x, float y) {
            text(c, colour, size, family, x, y, false).setTextAlign(Paint.Align.CENTER);
            c.drawText(s, x, y, text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private Bitmap wallCache;

        private Bitmap small(File f) {
            if (wallCache != null) return wallCache;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;
                wallCache = BitmapFactory.decodeFile(f.getPath(), o);
            } catch (Throwable ignored) {
            }
            return wallCache;
        }
    }

    private static final class Color {
        static int alpha(int c) {
            return c >>> 24;
        }
    }
}
