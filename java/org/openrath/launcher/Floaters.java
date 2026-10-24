package org.openrath.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Process;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * Small things that float over the home screen: a mic button that can be dragged anywhere and
 * starts a voice command when tapped, a performance box (frames per second, CPU use and the
 * hottest temperature sensor), and the simple icons the Wide map bar uses.
 */
final class Floaters {

    /** Draws "mic", "apps" or "settings" in a 24-unit box. */
    static void icon(Canvas c, String name, float x, float y, float size, int ink) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(ink);
        line.setColor(ink);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.2f);
        line.setStrokeCap(Paint.Cap.ROUND);
        c.save();
        c.translate(x, y);
        c.scale(size / 24f, size / 24f);
        if ("mic".equals(name)) {
            c.drawRoundRect(new RectF(8.5f, 2.5f, 15.5f, 14.5f), 3.5f, 3.5f, fill);
            c.drawArc(new RectF(5.5f, 6.5f, 18.5f, 17.5f), 0, 180, false, line);
            c.drawLine(12, 17.5f, 12, 21, line);
            c.drawLine(8.5f, 21, 15.5f, 21, line);
        } else if ("apps".equals(name)) {
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3; j++) c.drawCircle(5 + i * 7, 5 + j * 7, 2.6f, fill);
        } else if ("settings".equals(name)) {
            android.graphics.Path p = new android.graphics.Path();
            for (int i = 0; i < 48; i++) {
                double a = i / 48.0 * 2 * Math.PI;
                float r = (i / 3) % 2 == 0 ? 9.5f : 7.6f;
                float px = 12 + r * (float) Math.cos(a), py = 12 + r * (float) Math.sin(a);
                if (i == 0) p.moveTo(px, py);
                else p.lineTo(px, py);
            }
            p.close();
            p.addCircle(12, 12, 3.4f, android.graphics.Path.Direction.CCW);
            p.setFillType(android.graphics.Path.FillType.EVEN_ODD);
            c.drawPath(p, fill);
        }
        c.restore();
    }

    /** A view showing one of the icons above. */
    static final class Icon extends View {
        final String name;
        final int ink;

        Icon(Context c, String name, int ink) {
            super(c);
            this.name = name;
            this.ink = ink;
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) * 0.6f;
            icon(c, name, (getWidth() - s) / 2, (getHeight() - s) / 2, s, ink);
        }
    }

    // ------------------------------------------------------------------ the floating mic

    /** Where to put the mic: the saved spot as a fraction of the screen, kept on screen. */
    static float[] place(float fx, float fy, int w, int h, int size) {
        float x = Math.max(0, Math.min(w - size, fx * w - size / 2f));
        float y = Math.max(0, Math.min(h - size, fy * h - size / 2f));
        return new float[]{x, y};
    }

    /** The floating mic's size before scaling: small, so it covers little of a box. */
    static final int MIC = 40;

    static class Mic extends View {
        private final Prefs prefs;
        private final Runnable tapped;
        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float downX, downY, startX, startY;
        private boolean dragged;

        Mic(Context c, Prefs p, Runnable tapped) {
            super(c);
            prefs = p;
            this.tapped = tapped;
            setElevation(Ui.px(8));
        }

        /** Put it where it was left (call once the parent has its size). */
        void restore(int w, int h) {
            int[] at = OverMic.spot(prefs, w, h, getWidth() > 0 ? getWidth() : Ui.px(MIC));
            setX(at[0]);
            setY(at[1]);
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = Math.min(getWidth(), getHeight()) / 2f;
            disc.setColor(0xE61E2633);
            c.drawCircle(getWidth() / 2f, getHeight() / 2f, r - 1, disc);
            disc.setColor(0xFF3E7BD8);
            disc.setStyle(Paint.Style.STROKE);
            disc.setStrokeWidth(Ui.px(2));
            c.drawCircle(getWidth() / 2f, getHeight() / 2f, r - Ui.px(2), disc);
            disc.setStyle(Paint.Style.FILL);
            icon(c, "mic", getWidth() * 0.22f, getHeight() * 0.22f, getWidth() * 0.56f, 0xFFFFFFFF);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            View parent = (View) getParent();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = getX();
                    startY = getY();
                    dragged = false;
                    setPressed(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.abs(dx) + Math.abs(dy) > Ui.px(10)) dragged = true;
                    if (dragged && parent != null) {
                        setX(Math.max(0, Math.min(parent.getWidth() - getWidth(), startX + dx)));
                        setY(Math.max(0, Math.min(parent.getHeight() - getHeight(), startY + dy)));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    setPressed(false);
                    if (dragged && parent != null && parent.getWidth() > 0) {
                        prefs.put("micX", String.format(Locale.US, "%.3f", (getX() + getWidth() / 2f) / parent.getWidth()));
                        prefs.put("micY", String.format(Locale.US, "%.3f", (getY() + getHeight() / 2f) / parent.getHeight()));
                    } else {
                        playSoundEffect(android.view.SoundEffectConstants.CLICK);
                        tapped.run();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    setPressed(false);
                    return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ the performance box

    /** CPU % from two readings of busy and total time (any units). */
    static int percent(long busy0, long total0, long busy1, long total1) {
        long t = total1 - total0;
        return t <= 0 ? 0 : (int) Math.max(0, Math.min(100, Math.round(100.0 * (busy1 - busy0) / t)));
    }

    /** The hottest thermal zone in °C (sensors report millidegrees or degrees), or -1. */
    static int hottest() {
        int best = -1;
        File[] zones = new File("/sys/class/thermal").listFiles();
        if (zones == null) return -1;
        for (File z : zones) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            String v = read(new File(z, "temp"));
            if (v == null) continue;
            try {
                long t = Long.parseLong(v.trim());
                int c = (int) (t > 1000 ? t / 1000 : t);
                if (c > 0 && c < 150 && c > best) best = c;
            } catch (Exception ignored) {
                // not a number: skip it
            }
        }
        return best;
    }

    static String read(File f) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            String s = r.readLine();
            r.close();
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** {busy, total} jiffies for the whole system from /proc/stat, or null where Android hides it. */
    static long[] system() {
        String s = read(new File("/proc/stat"));
        if (s == null || !s.startsWith("cpu ")) return null;
        String[] f = s.trim().split("\\s+");
        long total = 0, idle = 0;
        for (int i = 1; i < f.length; i++) {
            long v = Long.parseLong(f[i]);
            total += v;
            if (i == 4 || i == 5) idle += v;   // idle and iowait
        }
        return new long[]{total - idle, total};
    }

    /** This app's CPU time so far, in jiffies, from /proc/self/stat. */
    static long app() {
        String s = read(new File("/proc/" + Process.myPid() + "/stat"));
        if (s == null) return 0;
        String[] f = s.substring(s.lastIndexOf(')') + 2).split(" ");
        try {
            return Long.parseLong(f[11]) + Long.parseLong(f[12]);   // utime, stime
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Measures frames per second, the slowest frame, CPU use and temperature once a second while
     * anything shows them (the corner box or a Performance box on the dashboard).
     */
    static final class Meter implements Choreographer.FrameCallback {
        static final Meter INSTANCE = new Meter();
        int fps, worst = 0, appPct, sysPct = -1, temp = -1;
        private int frames, worstNow, users;
        private long lastFrame, windowStart, appT0, sysB0, sysT0, wall0;
        private final int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        /** Start measuring for one more viewer. */
        void start() {
            if (users++ == 0) {
                lastFrame = windowStart = 0;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        void stop() {
            if (users > 0 && --users == 0) Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override
        public void doFrame(long nanos) {
            long ms = nanos / 1000000;
            if (lastFrame > 0) worstNow = (int) Math.max(worstNow, ms - lastFrame);
            lastFrame = ms;
            frames++;
            if (windowStart == 0) windowStart = ms;
            if (ms - windowStart >= 1000) {
                fps = Math.round(frames * 1000f / (ms - windowStart));
                worst = worstNow;
                frames = 0;
                worstNow = 0;
                windowStart = ms;
                sample();
            }
            if (users > 0) Choreographer.getInstance().postFrameCallback(this);
        }

        private void sample() {
            long wall = SystemClock.elapsedRealtime(), a = app();
            if (wall0 > 0) {
                // jiffies are 1/100 s; share of all cores
                appPct = (int) Math.max(0, Math.min(100, Math.round((a - appT0) * 10.0 / (wall - wall0) * 100 / cores)));
            }
            appT0 = a;
            wall0 = wall;
            long[] s = system();
            if (s != null) {
                if (sysT0 > 0) sysPct = percent(sysB0, sysT0, s[0], s[1]);
                sysB0 = s[0];
                sysT0 = s[1];
            }
            temp = hottest();
        }
    }

    /** The small box in the top-left corner. */
    static final class Perf extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Meter m = Meter.INSTANCE;
        private final RectF box = new RectF();
        private boolean measuring;

        Perf(Context c) {
            super(c);
            bg.setColor(0xB3000000);
            ink.setColor(0xFFFFE14D);
            ink.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            measure(getWindowVisibility() == VISIBLE);
        }

        @Override
        protected void onDetachedFromWindow() {
            measure(false);
            super.onDetachedFromWindow();
        }

        // counting frames every frame only while the box can be seen
        @Override
        protected void onWindowVisibilityChanged(int v) {
            super.onWindowVisibilityChanged(v);
            measure(v == VISIBLE && isAttachedToWindow());
        }

        private void measure(boolean on) {
            if (on == measuring) return;
            measuring = on;
            if (on) {
                m.start();
                invalidate();
            } else m.stop();
        }

        @Override
        protected void onDraw(Canvas c) {
            float k = Ui.k, pad = 8 * k;
            ink.setTextSize(14 * k);
            String[] lines = {"FPS: " + m.fps, "CPU: app " + m.appPct + "%" + (m.sysPct >= 0 ? " / sys " + m.sysPct + "%" : ""),
                    "Temp: " + (m.temp < 0 ? "--" : m.temp + "°C"), "Frame: " + m.worst + " ms"};
            float w = 0;
            for (String l : lines) w = Math.max(w, ink.measureText(l));
            float lh = 18 * k;
            box.set(0, 0, w + 2 * pad, lines.length * lh + 2 * pad - 4 * k);
            c.drawRoundRect(box, 6 * k, 6 * k, bg);
            for (int i = 0; i < lines.length; i++) c.drawText(lines[i], pad, pad + (i + 1) * lh - 5 * k, ink);
            if (measuring) postInvalidateDelayed(1000);
        }
    }

    private Floaters() {
    }
}
