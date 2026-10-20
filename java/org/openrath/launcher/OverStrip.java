package org.openrath.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The mini strip over other apps while the home screen is away: the next turn from the navigation
 * app (when one guides) and what's playing, with previous, play or pause, and next. It sits along
 * the bottom; drag it up or down. A tap on the words brings the home screen back. Needs "Display over
 * other apps", like the floating mic.
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
        final int w = Math.min(sw - Ui.px(40), Ui.px(620)), h = Ui.px(84);
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
        s.setPadding(Ui.px(16), Ui.px(6), Ui.px(8), Ui.px(6));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.RADIUS * Ui.k);
        g.setColor(0xF0000000 | (Ui.PANEL_TOP & 0xFFFFFF));
        g.setStroke(Math.max(1, Ui.px(1.5f)), Ui.ACCENT);
        s.setBackground(g);
        final TextView line1 = Ui.text(app, 20, Ui.SILVER, "sans-serif", Gravity.LEFT);
        final TextView line2 = Ui.text(app, 16, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        line1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line2.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout words = new LinearLayout(app);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(line1);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(-1, -2);
        l2.topMargin = Ui.px(6);
        words.addView(line2, l2);
        words.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent home = new Intent(app, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(home);
            }
        });
        s.addView(words, new LinearLayout.LayoutParams(0, -1, 1f));
        final ImageView play = button(app, "btn_play");
        final String[] pics = {"btn_prev", "btn_play", "btn_next"};
        for (int n = 0; n < pics.length; n++) {
            final int i = n;
            ImageView b = i == 1 ? play : button(app, pics[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    MediaController c = current(app);
                    if (c == null) return;
                    MediaController.TransportControls t = c.getTransportControls();
                    if (i == 0) t.skipToPrevious();
                    else if (i == 2) t.skipToNext();
                    else if (playing(c)) t.pause();
                    else t.play();
                }
            });
            s.addView(b, new LinearLayout.LayoutParams(Ui.px(76), Ui.px(76)));
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
                fill(app, line1, line2, play);
                handler.postDelayed(this, 1500);
            }
        };
        tick.run();
    }

    private static String fraction(Prefs p) {
        String f = p.str("stripY", "0.83");
        try {
            Float.parseFloat(f);
            return f;
        } catch (Exception e) {
            return "0.83";
        }
    }

    private static ImageView button(Context c, String pic) {
        ImageView b = new ImageView(c);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(Ui.px(18), Ui.px(18), Ui.px(18), Ui.px(18));
        b.setImageBitmap(Ui.img(c, pic));
        return b;
    }

    /** The first line is the next turn while a navigation app guides, else the song; the second, the rest. */
    private static void fill(Context app, TextView line1, TextView line2, ImageView play) {
        MediaController c = current(app);
        String song = "", artist = "";
        if (c != null && c.getMetadata() != null) {
            MediaMetadata md = c.getMetadata();
            String t = md.getString(MediaMetadata.METADATA_KEY_TITLE), a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            song = t == null ? "" : t;
            artist = a == null ? "" : a;
        }
        play.setImageBitmap(Ui.img(app, c != null && playing(c) ? "btn_pause" : "btn_play"));
        if (NavTurn.active()) {
            line1.setText((NavTurn.distance.isEmpty() ? "" : NavTurn.distance + "  ·  ") + NavTurn.instruction);
            line2.setText(song.isEmpty() ? NavTurn.eta : song + (artist.isEmpty() ? "" : "  ·  " + artist));
        } else {
            line1.setText(song.isEmpty() ? Hindi.t("Nothing playing") : song);
            line2.setText(artist);
        }
    }

    /** The music app to control: the one playing, else the first with a session. */
    static MediaController current(Context c) {
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> all = m.getActiveSessions(new ComponentName(c, MediaListener.class));
            for (MediaController x : all) if (playing(x)) return x;
            return all.isEmpty() ? null : all.get(0);
        } catch (Throwable e) {
            return null;   // no notification access
        }
    }

    static boolean playing(MediaController c) {
        PlaybackState st = c.getPlaybackState();
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
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
