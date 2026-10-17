package org.openrath.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * The floating mic on top of other apps (maps, music, the radio) while the home screen is away.
 * Drag it anywhere; tap it and the home screen comes forward to listen for a voice command.
 * Android asks once for "Display over other apps"; without it the mic stays on the home screen.
 */
final class OverMic {

    private static View shown;

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    static boolean wanted(Prefs p) {
        return Features.on(p, "floatMic") && Features.on(p, "micOverApps");
    }

    /** Open Android's page for the permission. */
    static void ask(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + a.getPackageName())));
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception ignored) {
                android.widget.Toast.makeText(a, "This unit has no page for that permission", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Where the mic goes: its saved spot as a share of the screen, kept fully on it. */
    static int[] spot(Prefs p, int w, int h, int size) {
        float fx = 0.94f, fy = 0.80f;
        try {
            fx = Float.parseFloat(p.str("micX", "0.94"));
            fy = Float.parseFloat(p.str("micY", "0.80"));
        } catch (Exception ignored) {
        }
        float[] at = Floaters.place(fx, fy, w, h, size);
        return new int[]{Math.round(at[0]), Math.round(at[1])};
    }

    static void show(final Context app, final Prefs p) {
        if (shown != null || !wanted(p) || !allowed(app)) return;
        final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        final int size = Ui.px(64);
        final int sw = app.getResources().getDisplayMetrics().widthPixels, sh = app.getResources().getDisplayMetrics().heightPixels;
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        int[] at = spot(p, sw, sh, size);
        lp.x = at[0];
        lp.y = at[1];
        final View v = new Floaters.Mic(app, p, null) {
            float downX, downY;
            int startX, startY;
            boolean dragged;

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        dragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) + Math.abs(dy) > Ui.px(10)) dragged = true;
                        if (dragged) {
                            lp.x = Math.max(0, Math.min(sw - size, Math.round(startX + dx)));
                            lp.y = Math.max(0, Math.min(sh - size, Math.round(startY + dy)));
                            try {
                                wm.updateViewLayout(this, lp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragged) {
                            p.put("micX", String.format(java.util.Locale.US, "%.3f", (lp.x + size / 2f) / sw));
                            p.put("micY", String.format(java.util.Locale.US, "%.3f", (lp.y + size / 2f) / sh));
                        } else {
                            playSoundEffect(android.view.SoundEffectConstants.CLICK);
                            app.startActivity(new Intent(app, HomeActivity.class).setAction(Keys.ACTION)
                                    .putExtra("action", "voice").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }
                        return true;
                }
                return false;
            }
        };
        try {
            wm.addView(v, lp);
            shown = v;
        } catch (Exception ignored) {
            // the permission was taken back
        }
    }

    static void hide(Context app) {
        if (shown == null) return;
        try {
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(shown);
        } catch (Exception ignored) {
        }
        shown = null;
    }

    private OverMic() {
    }
}
