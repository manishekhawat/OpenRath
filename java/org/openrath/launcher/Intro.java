package org.openrath.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * The OpenRath wheel rolling in when the app opens: the rim draws round, the spokes grow out from
 * the hub, the name fades in under it, then it all fades away to the dashboard. Shorter at Reduced
 * movement, left out at Off; a tap skips it. Only when the app starts, not on every return home.
 */
final class Intro extends View {

    static final long LENGTH = 2000;

    /** Whether to play it: switched on, movement not Off, and the app has just started. */
    static boolean show(boolean on, String motion, boolean fresh) {
        return on && fresh && !"off".equals(motion);
    }

    /** How far along a step is, 0 to 1, eased out: `t` from the start, the step from `start` for `len`. */
    static float part(long t, long start, long len) {
        if (t <= start) return 0;
        if (len <= 0 || t >= start + len) return 1;
        float x = (t - start) / (float) len;
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    static void play(HomeActivity a, ViewGroup root) {
        String motion = Motion.pick(a.prefs.str("motion", "standard"));
        Intro v = new Intro(a, Motion.ms(motion, LENGTH), "reduced".equals(motion));
        root.addView(v, new ViewGroup.LayoutParams(-1, -1));
    }

    private final long length;
    private final boolean still;
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private long start = -1;
    private boolean done;
    /** The Glass look's blurred wallpaper, or null. */
    private android.graphics.Bitmap wall;

    private Intro(Context c, long length, boolean still) {
        super(c);
        this.length = Math.max(1, length);
        this.still = still;
        ink.setStrokeCap(Paint.Cap.ROUND);
        try {
            ink.setTypeface(Typeface.createFromAsset(c.getAssets(), "fonts/InterDisplay-Light.otf"));
        } catch (Exception e) {
            Diag.note("intro font", e);
        }
        if (Glass.on) {
            try {
                wall = Glass.wallpaper(c, new Prefs(c));
            } catch (Throwable e) {
                Diag.note("intro glass", e);
            }
        }
        setContentDescription("OpenRath");
        setElevation(Ui.px(30));   // above the setup screens
    }

    /** A step's progress, with the steps laid out on the standard 2-second timeline. */
    private float at(long t, long from, long len) {
        return part(t * LENGTH / length, from, len);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) finish();
        return true;
    }

    private void finish() {
        if (done) return;
        done = true;
        ViewGroup p = (ViewGroup) getParent();
        if (p != null) p.removeView(this);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (done) return;
        long now = SystemClock.uptimeMillis();
        if (start < 0) start = now;
        long t = now - start;
        if (t >= length) {
            post(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
            return;
        }
        float fade = 1 - at(t, 1600, 400);
        int alpha = Math.round(255 * fade);
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h) * 0.32f, u = size / 24;
        float cx = w / 2, cy = h / 2 - size * 0.18f;
        if (Glass.on && wall != null) {
            // the Glass look: the blurred wallpaper (the dashboard has the same one, so the fade
            // reveals the boxes on it), and a frosted glass card the wheel and name settle on
            c.drawColor((alpha << 24) | (Ui.BACKGROUND & 0xFFFFFF));
            Glass.backdrop(c, wall, (int) w, (int) h, fade);
            float in = at(t, 0, 600) * fade;
            float cw = size * 1.9f, top = cy - size * 0.72f, bottom = cy + size * 0.5f + size * 0.62f;
            r.set(w / 2 - cw / 2, top + (1 - in) * size * 0.1f, w / 2 + cw / 2, bottom + (1 - in) * size * 0.1f);
            Glass.card(c, r, Glass.RADIUS * Ui.k * 1.6f, in, Glass.night);
        } else c.drawColor((alpha << 24) | (Ui.BACKGROUND & 0xFFFFFF));
        int silver = (alpha << 24) | ((Glass.on ? Glass.TEXT : Ui.SILVER) & 0xFFFFFF);

        // rolls in from the left a little, turning as it goes
        float roll = still ? 0 : 1 - at(t, 0, 900);
        cx -= roll * size * 0.9f;
        c.save();
        c.rotate(-roll * 270, cx, cy);

        // the rim draws round
        ink.setStyle(Paint.Style.STROKE);
        ink.setColor(silver);
        ink.setStrokeWidth(2.2f * u);
        float rim = 10.7f * u;
        r.set(cx - rim, cy - rim, cx + rim, cy + rim);
        c.drawArc(r, -90, 360 * at(t, 0, 700), false, ink);
        // the hub
        float hub = 3.1f * u;
        ink.setStrokeWidth(1.8f * u);
        r.set(cx - hub, cy - hub, cx + hub, cy + hub);
        c.drawArc(r, -90, 360 * at(t, 200, 400), false, ink);
        // eight spokes, one after another, growing out from the hub
        ink.setStrokeWidth(1.5f * u);
        ink.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < 8; i++) {
            float g = at(t, 350 + i * 60, 300);
            if (g <= 0) continue;
            double ang = i * Math.PI / 4 + Math.PI / 8;
            float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
            float from = 3.6f * u, to = from + (9.9f * u - from) * g;
            c.drawLine(cx + dx * from, cy + dy * from, cx + dx * to, cy + dy * to, ink);
        }
        ink.setStrokeCap(Paint.Cap.ROUND);
        c.restore();

        // the name, and a line in the theme's colour under it
        float name = at(t, 800, 500);
        ink.setStyle(Paint.Style.FILL);
        ink.setTextAlign(Paint.Align.CENTER);
        ink.setTextSize(size * 0.3f);
        ink.setColor((Math.round(alpha * name) << 24) | ((Glass.on ? Glass.TEXT : Ui.SILVER) & 0xFFFFFF));
        float ty = cy + size * 0.5f + size * 0.38f + (1 - name) * size * 0.08f;
        c.drawText("OpenRath", w / 2, ty, ink);
        float line = at(t, 1000, 500) * size * 0.9f;
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(Math.max(2, u * 0.6f));
        ink.setColor((alpha << 24) | (Ui.ACCENT & 0xFFFFFF));
        c.drawLine(w / 2 - line / 2, ty + size * 0.12f, w / 2 + line / 2, ty + size * 0.12f, ink);

        postInvalidateOnAnimation();
    }
}
