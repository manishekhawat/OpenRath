package org.openrath.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Fog mode: in dense fog (from the weather, or switched on by hand or voice) while driving, the
 * screen shows only the speed against a slow guide speed and the next bend on this road as a big
 * arrow, with a reminder about lamps and gaps. A voice says each bend too. Tap to hide it for
 * the rest of the drive.
 */
final class Fog extends View {

    static int guide(Prefs p) {
        return p.num("fogSpeed", 40);
    }

    /** Fog now: the weather says so, or the driver switched it on. */
    static boolean now(Prefs p) {
        return Features.on(p, "fog") && (Hub.fog || "on".equals(p.str("fogNow", "off")));
    }

    /** "Bend to the right in 150 m", "Road ahead looks straight", or "" when the road isn't known. */
    static String bendText(double m, int dir) {
        if (m < 0) return "";
        if (m < 25) return dir > 0 ? Hindi.t("Bend to the right now") : Hindi.t("Bend to the left now");
        return Hindi.f(dir > 0 ? "Bend to the right in %1$s m" : "Bend to the left in %1$s m", Math.round(m / 10) * 10);
    }

    interface Closed {
        void closed();
    }

    private final Prefs prefs;
    private final Closed closed;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String speed = "--";

    Fog(Context c, Prefs p, Closed closed) {
        super(c);
        prefs = p;
        this.closed = closed;
    }

    void setSpeed(String s) {
        speed = s;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) closed.closed();
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), k = h / 600f;
        c.drawColor(0xFF15181C);
        int v;
        try {
            v = Integer.parseInt(speed);
        } catch (Exception e) {
            v = 0;
        }
        int g = guide(prefs);
        boolean over = v > g;
        float cx = w * 0.3f;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-light", 0));
        paint.setColor(over ? 0xFFFFB02E : 0xFFF2F2F2);
        paint.setTextSize(220 * k);
        c.drawText(speed, cx, h * 0.52f, paint);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        paint.setTextSize(26 * k);
        paint.setColor(0xFF9AA4B0);
        c.drawText("km/h", cx, h * 0.52f + 40 * k, paint);
        paint.setTextSize(30 * k);
        paint.setColor(over ? 0xFFFFB02E : 0xFFCFE3FF);
        c.drawText(over ? Hindi.f("Fog: slow down to %1$s", g) : Hindi.f("Fog: keep below %1$s km/h", g), cx, h * 0.72f, paint);
        paint.setTextSize(20 * k);
        paint.setColor(0xFF9AA4B0);
        c.drawText(Hindi.t("Low beams and fog lamps on  ·  keep a long gap"), cx, h * 0.80f, paint);

        // the bend ahead
        float bx = w * 0.74f, by = h * 0.42f, r = 130 * k;
        double m = Ahead.bendM;
        int dir = Ahead.bendDir;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(26 * k);
        paint.setColor(m >= 0 && m < 150 ? 0xFFFFB02E : 0xFFE6E9EE);
        Path p = new Path();
        float tipX, tipY, ang;
        if (m < 0 || dir == 0) {
            p.moveTo(bx, by + r);
            p.lineTo(bx, by - r * 0.6f);
            tipX = bx;
            tipY = by - r;
            ang = -90;
        } else {
            float s = dir;
            p.moveTo(bx - s * r * 0.3f, by + r);
            p.lineTo(bx - s * r * 0.3f, by);
            p.quadTo(bx - s * r * 0.3f, by - r * 0.5f, bx + s * r * 0.2f, by - r * 0.5f);
            p.lineTo(bx + s * r * 0.45f, by - r * 0.5f);
            tipX = bx + s * r * 0.95f;
            tipY = by - r * 0.5f;
            ang = s > 0 ? 0 : 180;
        }
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);
        Path head = new Path();
        double a = Math.toRadians(ang);
        float hl = 70 * k, hw = 48 * k;
        float baseX = tipX - (float) Math.cos(a) * hl, baseY = tipY - (float) Math.sin(a) * hl;
        head.moveTo(tipX, tipY);
        head.lineTo(baseX - (float) Math.sin(a) * hw, baseY + (float) Math.cos(a) * hw);
        head.lineTo(baseX + (float) Math.sin(a) * hw, baseY - (float) Math.cos(a) * hw);
        head.close();
        c.drawPath(head, paint);
        paint.setTextSize(28 * k);
        paint.setColor(0xFFE6E9EE);
        String t = m < 0 ? Hindi.t("No bend known ahead") : bendText(m, dir);
        c.drawText(t, bx, by + r + 60 * k, paint);
        paint.setTextSize(16 * k);
        paint.setColor(0xFF6E7884);
        c.drawText(Hindi.t("Tap to close for this drive"), w / 2, h - 20 * k, paint);
        postInvalidateDelayed(500);
    }
}
