package org.openrath.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Shown over the dashboard while reverse is engaged: parking guide lines in perspective, red
 * close to the bumper, then yellow, then green, the way factory reverse cameras draw them.
 * (Most Topway units switch to the camera picture on their own; these lines are for when they don't.)
 * Where the unit gives Android its reversing camera as an ordinary camera, its picture fills the
 * screen under the lines; without one, or when it won't open, the lines are on a dark screen.
 * The camera is let go the moment this view is taken away (reverse ends).
 */
@SuppressWarnings("deprecation")   // android.hardware.Camera: the one camera API every Android 8 unit's camera works with
final class ReverseView extends FrameLayout {

    private final Lines lines;
    private final TextureView picture;
    private final Object lock = new Object();
    /** The open camera; null when there is none (yet), or it was let go. */
    private Camera cam;
    /** Taken away: a camera that opens late is let go at once. */
    private boolean gone;

    ReverseView(Context c) {
        super(c);
        // the lines carry the dark screen, so the camera picture under them shows only once it runs
        setClickable(true);   // the dashboard underneath isn't touched by mistake
        picture = new TextureView(c);
        // (a TextureView must be drawn to get its surface, so it is never hidden; it is empty until then)
        addView(picture, new LayoutParams(-1, -1));
        lines = new Lines(c);
        lines.setBackgroundColor(0xF0050506);
        addView(lines, new LayoutParams(-1, -1));
        final int id = which(c);
        if (id >= 0) picture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture st, int w, int h) {
                final int pw = w, ph = h;
                Bg.run(new Runnable() {   // opening a camera can take a second: off the screen's thread
                    @Override
                    public void run() {
                        open(id, st, pw, ph);
                    }
                });
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                release();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
    }

    /**
     * The camera to show: the owner's choice in Settings ("none", or a number from 0), else the
     * first one facing back, else the only one. -1 for none. Pure, for the tests: `back` says which
     * of the unit's cameras face back.
     */
    static int pick(String chosen, boolean[] back) {
        int n = back == null ? 0 : back.length;
        if (n == 0 || "none".equals(chosen)) return -1;
        try {
            int i = Integer.parseInt(chosen);
            return i >= 0 && i < n ? i : -1;
        } catch (NumberFormatException e) {
            // "auto"
        }
        for (int i = 0; i < n; i++) if (back[i]) return i;
        return n == 1 ? 0 : -1;
    }

    /** The unit's cameras, true for those facing back. Empty when there is none or no permission to use them. */
    static boolean[] cameras(Context c) {
        try {
            if (c.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                return new boolean[0];
            int n = Camera.getNumberOfCameras();
            boolean[] back = new boolean[Math.max(0, n)];
            for (int i = 0; i < n; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                back[i] = info.facing == Camera.CameraInfo.CAMERA_FACING_BACK;
            }
            return back;
        } catch (Throwable e) {
            return new boolean[0];
        }
    }

    private static int which(Context c) {
        return pick(new Prefs(c).str("reverseCam", "auto"), cameras(c));
    }

    /** On its own thread: open the camera and start its picture; the lines stay on dark if it fails. */
    private void open(int id, SurfaceTexture st, int w, int h) {
        try {
            synchronized (lock) {
                if (gone) return;
                cam = Camera.open(id);
                Camera.Parameters p = cam.getParameters();
                Camera.Size best = null;
                for (Camera.Size s : p.getSupportedPreviewSizes()) {
                    if (s.width > 1920 || s.height > 1080) continue;
                    if (best == null || Math.abs(s.width - w) + Math.abs(s.height - h) < Math.abs(best.width - w) + Math.abs(best.height - h)) best = s;
                }
                if (best != null) {
                    p.setPreviewSize(best.width, best.height);
                    cam.setParameters(p);
                }
                cam.setPreviewTexture(st);
                cam.startPreview();
            }
            post(new Runnable() {
                @Override
                public void run() {
                    synchronized (lock) {
                        if (cam == null) return;
                    }
                    lines.setBackgroundColor(0);
                    setBackgroundColor(0xFF000000);
                    lines.overPicture = true;
                    lines.invalidate();
                }
            });
        } catch (Throwable e) {
            Diag.note("reverse camera", e);
            release();
        }
    }

    /** Let the camera go now; other apps (the unit's own camera app) can have it straight away. */
    private void release() {
        synchronized (lock) {
            gone = true;
            if (cam == null) return;
            try {
                cam.stopPreview();
            } catch (Throwable ignored) {
                // stopping anyway
            }
            try {
                cam.release();
            } catch (Throwable ignored) {
                // already let go
            }
            cam = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    /** The guide lines and the words, drawn over the dark screen or the camera's picture. */
    private static final class Lines extends View {

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** Over the camera's picture: the words get a shadow so they read on any scene. */
        boolean overPicture;

        Lines(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float horizon = h * 0.28f, ground = h * 0.98f, cx = w / 2;
            float nearHalf = w * 0.30f, farHalf = w * 0.11f;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(Math.max(4, h / 90f));
            if (overPicture) p.setShadowLayer(4, 0, 0, 0xC0000000);
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
            p.setTextSize(overPicture ? h / 22f : h / 14f);
            p.setTextAlign(Paint.Align.CENTER);
            p.setLetterSpacing(0.3f);
            c.drawText(Hindi.t("REVERSE"), cx, overPicture ? h * 0.08f : h * 0.14f, p);
            if (overPicture) return;   // the picture says the rest
            p.setTextSize(h / 34f);
            p.setLetterSpacing(0.05f);
            p.setColor(Ui.SILVER_DIM);
            c.drawText(Hindi.t("Check around the car. This closes when you leave reverse."), cx, h * 0.2f, p);
        }
    }

    /** Settings: which camera shows the picture in reverse, when the unit has more than none. */
    static void choose(final HomeActivity a) {
        if (a.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 0x5E7);
            android.widget.Toast.makeText(a, Hindi.t("Allow the camera, then choose again."), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final boolean[] back = cameras(a);
        if (back.length == 0) {
            android.widget.Toast.makeText(a, Hindi.t("This unit gives Android no camera, so the guide lines show on a dark screen."), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final String[] values = new String[back.length + 2];
        String[] names = new String[back.length + 2];
        values[0] = "auto";
        names[0] = Hindi.t("Find it by itself");
        values[1] = "none";
        names[1] = Hindi.t("No picture, guide lines only");
        for (int i = 0; i < back.length; i++) {
            values[i + 2] = String.valueOf(i);
            names[i + 2] = Hindi.f("Camera %1$s", i + 1) + (back[i] ? Hindi.t(" (faces back)") : "");
        }
        int at = java.util.Arrays.asList(values).indexOf(a.prefs.str("reverseCam", "auto"));
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Camera picture in reverse"))
                .setSingleChoiceItems(names, at, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        a.prefs.put("reverseCam", values[w]);
                        d.dismiss();
                        if (a.settingsView != null) a.settingsView.refresh();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    /** The choice, for the Settings row. */
    static String chosen(Prefs p) {
        String v = p.str("reverseCam", "auto");
        if ("none".equals(v)) return Hindi.t("guide lines only");
        if ("auto".equals(v)) return Hindi.t("found by itself, when the unit has one");
        try {
            return Hindi.f("Camera %1$s", Integer.parseInt(v) + 1);
        } catch (NumberFormatException e) {
            return v;
        }
    }
}
