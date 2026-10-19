package org.openrath.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Night-drive mode: after sunset, while moving, everything but the map goes black, the map gets a
 * dim red tint (kinder to night vision), and the speed is shown very large in deep red. Touches
 * on the map still reach it; a tap anywhere else closes the mode until the next drive.
 */
final class NightDrive extends View {

    interface Closed {
        void closed();
    }

    private final Rect hole = new Rect();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // kept between frames rather than made for each one
    private final RectF area = new RectF();
    private final int[] mapAt = new int[2], selfAt = new int[2];
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL),
            MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private String speed = "--";
    private final Closed closed;
    static final int RED = 0xFFC62828, RED_DIM = 0xFF7A1C1C;

    NightDrive(Context c, Closed closed) {
        super(c);
        this.closed = closed;
    }

    /** Where the map sits, in this view's coordinates; empty when the map isn't on the page. */
    void setMap(View map) {
        hole.setEmpty();
        if (map != null && map.isShown()) {
            int[] a = mapAt, b = selfAt;
            map.getLocationInWindow(a);
            getLocationInWindow(b);
            hole.set(a[0] - b[0], a[1] - b[1], a[0] - b[0] + map.getWidth(), a[1] - b[1] + map.getHeight());
        }
        invalidate();
    }

    void setSpeed(String s) {
        if (s.equals(speed)) return;
        speed = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        paint.setColor(0xFF000000);
        if (hole.isEmpty()) cv.drawRect(0, 0, w, h, paint);
        else {
            cv.drawRect(0, 0, w, hole.top, paint);
            cv.drawRect(0, hole.bottom, w, h, paint);
            cv.drawRect(0, hole.top, hole.left, hole.bottom, paint);
            cv.drawRect(hole.right, hole.top, w, hole.bottom, paint);
            paint.setColor(0x8C2A0000);   // dim red over the map
            cv.drawRect(hole, paint);
        }
        // the speed goes in the biggest black area: beside the map, or the whole screen
        area.set(0, 0, w, h);
        if (!hole.isEmpty()) {
            float left = hole.left, right = w - hole.right, top = hole.top, bottom = h - hole.bottom;
            float best = Math.max(Math.max(left * h, right * h), Math.max(top * w, bottom * w));
            if (best == left * h) area.set(0, 0, hole.left, h);
            else if (best == right * h) area.set(hole.right, 0, w, h);
            else if (best == top * w) area.set(0, 0, w, hole.top);
            else area.set(0, hole.bottom, w, h);
        }
        paint.setTypeface(LIGHT);
        paint.setTextAlign(Paint.Align.CENTER);
        float size = Math.min(area.height() * 0.42f, area.width() * 0.5f);
        paint.setTextSize(size);
        paint.setColor(RED);
        float cx = area.centerX(), cy = area.centerY();
        cv.drawText(speed, cx, cy + size * 0.3f, paint);
        paint.setTypeface(MEDIUM);
        paint.setTextSize(size * 0.16f);
        paint.setColor(RED_DIM);
        paint.setLetterSpacing(0.2f);
        cv.drawText(Units.speedLabel().toUpperCase(java.util.Locale.ROOT), cx, cy + size * 0.62f, paint);
        paint.setTextSize(size * 0.2f);
        paint.setLetterSpacing(0);
        cv.drawText(Times.now("h:mm"), cx, area.top + size * 0.35f, paint);
        paint.setTextSize(Math.max(12, size * 0.09f));
        cv.drawText(Hindi.t("Tap here to leave night-drive mode"), cx, area.bottom - size * 0.12f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!hole.isEmpty() && hole.contains((int) e.getX(), (int) e.getY())) return false;   // the map's
        if (e.getAction() == MotionEvent.ACTION_UP) closed.closed();
        return true;
    }
}
