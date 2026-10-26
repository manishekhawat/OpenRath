package org.openrath.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The mini strip over other apps while the home screen is away: the next turn from the navigation
 * app (when one guides) and what's playing (the same as the dashboard's music boxes: OpenRath's own
 * player or another app's), with previous, play or pause, and next. It sits slim along the top;
 * drag it up or down. With no turn and nothing playing it hides. A tap on the words brings the home
 * screen back. Needs "Display over other apps", like the floating mic.
 */
final class OverStrip {

    private static View shown;
    private static Handler handler;
    private static Runnable tick;

    static boolean wanted(Prefs p) {
        return Features.on(p, "overStrip");
    }

    static void show(final Context app, final Prefs p) {
        if (shown != null || !wanted(p) || !OverMic.allowed(app)) return;
        final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        final int sw = app.getResources().getDisplayMetrics().widthPixels, sh = app.getResources().getDisplayMetrics().heightPixels;
        // tall enough for the buttons to be full touch targets (HomeDock.TARGET) and the words to read at a glance
        final int w = Math.min(sw - Ui.px(40), Ui.px(680)), h = Ui.px(HomeDock.TARGET + 6);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = (sw - w) / 2;
        lp.y = Math.max(0, Math.min(sh - h, Math.round(sh * Float.parseFloat(fraction(p)))));
        final LinearLayout s = new LinearLayout(app) {
            float downY;
            int startY;
            boolean dragged;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downY = e.getRawY();
                    startY = lp.y;
                    dragged = false;
                } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE && Math.abs(e.getRawY() - downY) > Ui.px(12)) {
                    dragged = true;
                    return true;
                }
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE && dragged) {
                    lp.y = Math.max(0, Math.min(sh - h, Math.round(startY + e.getRawY() - downY)));
                    try {
                        wm.updateViewLayout(this, lp);
                    } catch (Exception ignored) {
                        // many times a second while dragging: skip this move
                    }
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP && dragged) {
                    p.put("stripY", String.format(java.util.Locale.US, "%.3f", lp.y / (float) sh));
                }
                return true;
            }
        };
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(Ui.px(16), Ui.px(3), Ui.px(8), Ui.px(3));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.RADIUS * Ui.k);
        g.setColor(0xF0000000 | (Ui.solidPanel() & 0xFFFFFF));
        g.setStroke(Math.max(1, Ui.px(1.5f)), Ui.ACCENT);
        s.setBackground(g);
        // the next turn or the song, read while moving; under it the rest, at the smallest size
        final TextView line1 = Ui.text(app, Ui.READ, Ui.SILVER, "sans-serif", Gravity.LEFT);
        final TextView line2 = Ui.text(app, Ui.MIN_TEXT, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        line1.setSingleLine(true);
        line2.setSingleLine(true);
        line1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line2.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout words = new LinearLayout(app);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(line1);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(-1, -2);
        l2.topMargin = Ui.px(1);
        words.addView(line2, l2);
        words.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent home = new Intent(app, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(home);
            }
        });
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.gravity = Gravity.CENTER_VERTICAL;
        s.addView(words, wl);
        final ImageView play = button(app, "btn_play");
        final View[] keys = new View[3];
        final String[] pics = {"btn_prev", "btn_play", "btn_next"};
        for (int n = 0; n < pics.length; n++) {
            final int i = n;
            ImageView b = i == 1 ? play : button(app, pics[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    MediaCard.Now now = MediaCard.now(app);
                    if (now == null) return;
                    if (now.controller == null) {
                        Player.send(app, i == 0 ? Player.PREV : i == 2 ? Player.NEXT : Player.TOGGLE, Player.mode);
                        return;
                    }
                    MediaController.TransportControls t = now.controller.getTransportControls();
                    if (i == 0) t.skipToPrevious();
                    else if (i == 2) t.skipToNext();
                    else if (now.playing) t.pause();
                    else t.play();
                }
            });
            keys[i] = b;
            s.addView(b, new LinearLayout.LayoutParams(Ui.px(HomeDock.TARGET), -1));
        }
        try {
            wm.addView(s, lp);
            shown = s;
        } catch (Exception e) {
            return;   // the permission was taken back
        }
        handler = new Handler(Looper.getMainLooper());
        tick = new Runnable() {
            @Override
            public void run() {
                if (shown == null) return;
                boolean on = fill(app, line1, line2, play, keys);
                // nothing to show: out of sight, and touches go through to the app below
                int flags = on ? lp.flags & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        : lp.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                if (flags != lp.flags || (s.getVisibility() == View.VISIBLE) != on) {
                    lp.flags = flags;
                    s.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
                    try {
                        wm.updateViewLayout(s, lp);
                    } catch (Exception ignored) {
                        // gone meanwhile
                    }
                }
                handler.postDelayed(this, 1500);
            }
        };
        tick.run();
    }

    /** Where the strip sits, as a share of the screen height: the top edge unless it was moved. */
    private static String fraction(Prefs p) {
        String f = p.str("stripY", "0");
        try {
            Float.parseFloat(f);
            return f;
        } catch (Exception e) {
            return "0";
        }
    }

    private static ImageView button(Context c, String pic) {
        ImageView b = new ImageView(c);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(Ui.px(22), Ui.px(20), Ui.px(22), Ui.px(20));
        b.setImageBitmap(Ui.img(c, pic));
        return b;
    }

    /**
     * The first line is the next turn while a navigation app guides, else the song; the second, the
     * rest. The buttons show only while something plays. False when there is nothing to show.
     */
    private static boolean fill(Context app, TextView line1, TextView line2, ImageView play, View[] keys) {
        MediaCard.Now now = MediaCard.now(app);
        boolean music = now != null;
        String song = music ? now.title : "", artist = music ? now.sub : "";
        for (View k : keys) k.setVisibility(music ? View.VISIBLE : View.GONE);
        play.setImageBitmap(Ui.img(app, music && now.playing ? "btn_pause" : "btn_play"));
        if (NavTurn.active()) {
            line1.setText((NavTurn.distance.isEmpty() ? "" : NavTurn.distance + "  ·  ") + NavTurn.instruction);
            line2.setText(!music ? NavTurn.eta : song + (artist.isEmpty() ? "" : "  ·  " + artist));
            line2.setVisibility(View.VISIBLE);
            return true;
        }
        line1.setText(song);
        line2.setText(artist);
        line2.setVisibility(artist.isEmpty() ? View.GONE : View.VISIBLE);
        return music;
    }

    static void hide(Context app) {
        if (handler != null && tick != null) handler.removeCallbacks(tick);
        if (shown == null) return;
        try {
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(shown);
        } catch (Exception ignored) {
            // not shown any more
        }
        shown = null;
    }

    private OverStrip() {
    }
}
