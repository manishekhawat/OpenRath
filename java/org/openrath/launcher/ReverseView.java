package org.openrath.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * Shown over the dashboard while reverse is engaged: parking guide lines in perspective, red
 * close to the bumper, then yellow, then green, the way factory reverse cameras draw them.
 * (Most Topway units switch to the camera picture on their own; these lines are for when they don't.)
 */
final class ReverseView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    ReverseView(Context c) {
        super(c);
        setBackgroundColor(0xF0050506);
        setClickable(true);   // the dashboard underneath isn't touched by mistake
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float horizon = h * 0.28f, ground = h * 0.98f, cx = w / 2;
        float nearHalf = w * 0.30f, farHalf = w * 0.11f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(Math.max(4, h / 90f));
        // bands from the bumper outwards: 0.5 m red, 1 m yellow, 2 m+ green
        float[] at = {0f, 0.22f, 0.5f, 1f};
        int[] colour = {0xFFE5484D, 0xFFF5C542, 0xFF3DD68C};
        for (int i = 0; i < 3; i++) {
            float y0 = ground - (ground - horizon) * at[i], y1 = ground - (ground - horizon) * at[i + 1];
            float h0 = nearHalf + (farHalf - nearHalf) * at[i], h1 = nearHalf + (farHalf - nearHalf) * at[i + 1];
            p.setColor(colour[i]);
            c.drawLine(cx - h0, y0, cx - h1, y1, p);
            c.drawLine(cx + h0, y0, cx + h1, y1, p);
            // a short tick across at the far end of each band
            float tick = (h1) * 0.25f;
            c.drawLine(cx - h1, y1, cx - h1 + tick, y1, p);
            c.drawLine(cx + h1, y1, cx + h1 - tick, y1, p);
        }
        p.setColor(Ui.SILVER);
        p.setTextSize(h / 14f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(0.3f);
        c.drawText(Hindi.t("REVERSE"), cx, h * 0.14f, p);
        p.setTextSize(h / 34f);
        p.setLetterSpacing(0.05f);
        p.setColor(Ui.SILVER_DIM);
        c.drawText(Hindi.t("Check around the car. This closes when you leave reverse."), cx, h * 0.2f, p);
    }
}
