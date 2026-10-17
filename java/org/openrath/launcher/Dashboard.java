package org.openrath.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Places the dock and the five boxes. Left column: 50% / 25% / 25% of the height; right column:
 * 75% / 25%. The columns swap sides when the setting says so. On a portrait screen the boxes stack
 * instead, with the app row along the bottom. Sizes are in units of the 1024 x 600 design (Ui.scale),
 * so 1024x600, 1280x720 and 1920x720 come out the same shape.
 */
final class Dashboard extends ViewGroup {

    View dock;
    final View[] slots = new View[Prefs.SLOTS];
    boolean swapped;

    // ------------------------------------------------------------------ moving boxes

    interface Mover {
        /** A box was dropped on another: swap what they show. */
        void swap(int from, int to);
    }

    Mover mover;
    private boolean editing;
    private int dragging = -1;
    private float downX, downY;

    /** In edit mode every touch drags a box; boxes shrink a little to show they can move. */
    void setEditing(boolean on) {
        editing = on;
        for (View v : slots) {
            if (v == null) continue;
            v.animate().scaleX(on ? 0.94f : 1f).scaleY(on ? 0.94f : 1f).alpha(on ? 0.85f : 1f).setDuration(150).start();
        }
    }

    boolean editing() {
        return editing;
    }

    // ------------------------------------------------------------------ pages

    interface Pager {
        /** Swiped: +1 for the next page, -1 for the previous. */
        void turn(int dir);
    }

    Pager pager;
    int pages = 1, page;
    private float swipeX, swipeY;
    private boolean swipeOk, swiped;
    private final android.graphics.Paint dot = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (editing) return true;
        if (pages < 2 || pager == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swipeX = e.getX();
                swipeY = e.getY();
                swiped = false;
                int at = slotAt(swipeX, swipeY);
                // the map and other apps' widgets use sideways drags themselves
                swipeOk = at < 0 || !(slots[at] instanceof Cards.MapCard || slots[at] instanceof Cards.WidgetCard);
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - swipeX, dy = e.getY() - swipeY;
                if (swipeOk && !swiped && Math.abs(dx) > Ui.px(90) && Math.abs(dx) > 2 * Math.abs(dy)) {
                    swiped = true;
                    pager.turn(dx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas c) {
        super.dispatchDraw(c);
        if (pages < 2) return;
        float r = Ui.px(3.5f), gap = Ui.px(14);
        float cx = getWidth() / 2f - (pages - 1) * gap / 2f, y = getHeight() - Ui.px(7);
        if (dock != null && dock.getTop() > getHeight() / 2) y = dock.getTop() - Ui.px(5);   // bar along the bottom
        for (int i = 0; i < pages; i++) {
            dot.setColor(i == page ? Ui.SILVER : Ui.mix(Ui.SILVER_DIM, Ui.BACKGROUND, 0.5f));
            c.drawCircle(cx + i * gap, y, r, dot);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!editing) return swiped || super.onTouchEvent(e);
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = slotAt(x, y);
                downX = x;
                downY = y;
                if (dragging >= 0) {
                    slots[dragging].bringToFront();
                    slots[dragging].animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(100).start();
                    slots[dragging].setElevation(20);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging >= 0) {
                    slots[dragging].setTranslationX(x - downX);
                    slots[dragging].setTranslationY(y - downY);
                    int over = slotAt(x, y);
                    for (int i = 0; i < slots.length; i++) {
                        if (i != dragging && slots[i] != null) slots[i].setAlpha(i == over ? 0.5f : 0.85f);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging >= 0) {
                    View v = slots[dragging];
                    int target = e.getActionMasked() == MotionEvent.ACTION_UP ? slotAt(x, y) : -1;
                    v.setTranslationX(0);
                    v.setTranslationY(0);
                    v.setElevation(0);
                    int from = dragging;
                    dragging = -1;
                    if (target >= 0 && target != from && mover != null) mover.swap(from, target);
                    else setEditing(true);
                }
                return true;
        }
        return true;
    }

    /** Which box's place is under this point (by layout, ignoring a box being dragged). */
    private int slotAt(float x, float y) {
        int[][] r = rects(getWidth(), getHeight());
        for (int i = 0; i < Prefs.SLOTS; i++) {
            int[] q = r[i + 1];
            if (x >= q[0] && x < q[0] + q[2] && y >= q[1] && y < q[1] + q[3]) return i;
        }
        return -1;
    }

    Dashboard(Context c) {
        super(c);
        setBackgroundColor(Ui.BACKGROUND);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(MeasureSpec.getSize(ws), MeasureSpec.getSize(hs));
        int w = getMeasuredWidth(), h = getMeasuredHeight();
        int[][] r = rects(w, h);
        measure(dock, r[0]);
        for (int i = 0; i < Prefs.SLOTS; i++) measure(slots[i], r[i + 1]);
    }

    private static void measure(View v, int[] r) {
        if (v != null) v.measure(MeasureSpec.makeMeasureSpec(r[2], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(r[3], MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int[][] rs = rects(r - l, b - t);
        place(dock, rs[0]);
        for (int i = 0; i < Prefs.SLOTS; i++) place(slots[i], rs[i + 1]);
    }

    private static void place(View v, int[] r) {
        if (v != null) v.layout(r[0], r[1], r[0] + r[2], r[1] + r[3]);
    }

    /** Where the dock sits: "left", "right", "top" or "bottom". */
    String dockAt = "left";
    /** The Wide map arrangement (Settings > Look > Screen layout). */
    boolean wide;

    boolean dockAcross() {
        return "top".equals(dockAt) || "bottom".equals(dockAt);
    }

    /** [dock, slot0..slot4] as {x, y, w, h}. */
    int[][] rects(int w, int h) {
        // the on-screen keyboard (searching settings) shrinks the window: keep the text size then
        if (h > getResources().getDisplayMetrics().heightPixels * 0.75f) Ui.k = Ui.scale(w, h, Ui.user);
        if (Ui.portrait(w, h)) return stacked(w, h);
        int dockW = Ui.px(96), dockH = Ui.px(80), gap = Ui.px(14), m = Ui.px(18);
        int[] dock;
        int x0 = m, y0 = m, x1 = w - m, y1 = h - m;   // the area for the boxes
        if ("right".equals(dockAt)) {
            dock = new int[]{w - dockW, 0, dockW, h};
            x1 = w - dockW - gap;
        } else if ("top".equals(dockAt)) {
            dock = new int[]{0, 0, w, dockH};
            y0 = dockH + gap / 2;
        } else if ("bottom".equals(dockAt)) {
            dock = new int[]{0, h - dockH, w, dockH};
            y1 = h - dockH - gap / 2;
        } else {
            dock = new int[]{0, 0, dockW, h};
            x0 = dockW + gap;
        }
        if (wide) {
            // Wide map, like Android Auto: the app row along the bottom, box 4 (the map) large on the
            // left, boxes 2 and 5 stacked on the right; boxes 1 and 3 aren't shown
            int bar = Ui.px(84);
            int cw = w - 2 * m, ch = h - bar - m;
            int rw = Math.round((cw - gap) * 0.31f), lw = cw - rw - gap;
            int lx = swapped ? m + rw + gap : m, rx = swapped ? m : m + lw + gap;
            int topH = Math.round((ch - gap) * 0.42f);
            int[] none = {0, 0, 0, 0};
            return new int[][]{
                    {0, h - bar, w, bar},
                    none,
                    {rx, m, rw, topH},
                    none,
                    {lx, m, lw, ch},
                    {rx, m + topH + gap, rw, ch - topH - gap},
            };
        }
        int cw = x1 - x0, ch = y1 - y0;
        int lw = Math.round((cw - gap) * 0.44f), rw = cw - lw - gap;
        int lx = swapped ? x0 + rw + gap : x0;
        int rx = swapped ? x0 : x0 + lw + gap;
        int carH = Math.round((ch - 2 * gap) * 0.50f);
        int clockH = Math.round((ch - 2 * gap) * 0.25f);
        int weatherH = ch - carH - clockH - 2 * gap;
        int mapH = Math.round((ch - gap) * 0.75f);
        return new int[][]{
                dock,
                {lx, y0, lw, carH},
                {lx, y0 + carH + gap, lw, clockH},
                {lx, y0 + carH + clockH + 2 * gap, lw, weatherH},
                {rx, y0, rw, mapH},
                {rx, y0 + mapH + gap, rw, ch - mapH - gap},
        };
    }

    /**
     * Portrait: the app row along the bottom; the car box across the top, the map large below it, the
     * clock and the weather side by side, then box 5 across. Swapping puts the map above the car.
     */
    private int[][] stacked(int w, int h) {
        int bar = Ui.px(84), gap = Ui.px(14), m = Ui.px(18);
        int cw = w - 2 * m, ch = h - bar - m - gap / 2;
        int rows = ch - 3 * gap;
        int carH = Math.round(rows * 0.26f), mapH = Math.round(rows * 0.40f), midH = Math.round(rows * 0.17f);
        int lastH = rows - carH - mapH - midH;
        int half = (cw - gap) / 2;
        int carY = swapped ? m + mapH + gap : m, mapY = swapped ? m : m + carH + gap;
        int midY = m + carH + mapH + 2 * gap, lastY = midY + midH + gap;
        return new int[][]{
                {0, h - bar, w, bar},
                {m, carY, cw, carH},
                {m, midY, half, midH},
                {m + half + gap, midY, cw - half - gap, midH},
                {m, mapY, cw, mapH},
                {m, lastY, cw, lastH},
        };
    }
}
