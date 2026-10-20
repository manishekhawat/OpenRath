package org.openrath.launcher;

import android.content.Context;
import android.media.session.MediaController;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The music and radio boxes: what is playing, with previous, play / pause and next buttons and
 * the source switch in the header. The radio box drives FM through the stereo's own services
 * when it can (with a station scan), else the radio app; for app players the launcher can't
 * see, the buttons send media keys.
 */
final class MediaCard extends Cards.Card {
    final boolean radio;
    final TextView head = Ui.label(getContext(), "");
    final TextView title = Ui.text(getContext(), 22, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
    final TextView sub = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
    final ImageView prev = new ImageView(getContext()), play = new ImageView(getContext()), next = new ImageView(getContext());
    final LinearLayout buttons = new LinearLayout(getContext()), text = new LinearLayout(getContext());
    final TextView scan = Ui.text(getContext(), 12, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
    /** The music box's button for the phone-projection app (CarPlay, Android Auto). */
    final TextView phone = Ui.text(getContext(), 12, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
    Hub.Media media;      // the other app's player, when it shows one
    Unit unit;            // the stereo: FM through it when it can (a maker's add-on), else the radio app
    boolean unitFm;       // the buttons drive FM through the unit
    boolean localOn;      // our own guess for app players we can't see
    /** Which box last started something; the other box shows paused. */
    static String owner;

    String kind() {
        return radio ? "radio" : "music";
    }

    /** This box is starting to play: the other box's sound stops. */
    void claim() {
        owner = kind();
        if (!radio && unit != null) unit.fmQuiet();
    }
    String fmNote = "";   // why FM couldn't start from the box, if it couldn't
    String source;        // what the buttons drive now: "web", "files" or "app"
    boolean audioOn;
    /** Drag to seek, for music apps that report where the track is. */
    final Progress progress = new Progress(getContext());
    android.graphics.Bitmap tintedFor;

    MediaCard(Context c, boolean isRadio) {
        super(c);
        radio = isRadio;
        addView(progress);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(title);
        text.addView(sub);
        prev.setImageBitmap(Ui.img(c, "btn_prev"));
        play.setImageBitmap(Ui.img(c, "btn_play"));
        next.setImageBitmap(Ui.img(c, "btn_next"));
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.addView(prev);
        buttons.addView(play);
        buttons.addView(next);
        addView(head);
        addView(text);
        addView(buttons);
        if (radio) {
            // FM station scan: steps the stock radio round the band and lists what it found
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
            pill.setCornerRadius(999);
            pill.setStroke(Math.max(1, Ui.px(1.5f)), Ui.EDGE);
            scan.setBackground(pill);
            scan.setText(Hindi.t("SCAN"));
            scan.setVisibility(GONE);
            addView(scan);
            clickable(scan, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!(getContext() instanceof android.app.Activity)) return;
                    unit.scan((android.app.Activity) getContext(), host.prefs(), new Runnable() {
                        @Override
                        public void run() {
                            host.refresh();
                        }
                    });
                }
            });
        }
        if (!radio) {
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
            pill.setCornerRadius(999);
            pill.setStroke(Math.max(1, Ui.px(1.5f)), Ui.EDGE);
            phone.setBackground(pill);
            phone.setText(Hindi.t("CARPLAY / AUTO"));
            phone.setVisibility(GONE);
            addView(phone);
            clickable(phone, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Projection.open(getContext(), host.prefs());
                }
            });
        }
        final String key = radio ? "media:radio" : "media:music";
        final String name = radio ? "Radio" : "Music";
        // the header is the source switch: auto, online / app / files
        clickable(head, new OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs p = host.prefs();
                String what = radio ? "radio" : "music";
                String[] all = radio ? Prefs.RADIO_SRC : Prefs.MUSIC_SRC;
                String cur = p.source(what);
                int i = 0;
                while (i < all.length && !all[i].equals(cur)) i++;
                p.setSource(what, all[(i + 1) % all.length]);
                if (Player.playing && isMine()) Player.send(getContext(), Player.STOP, null);
                host.refresh();
            }
        });
        clickable(text, new OnClickListener() {
            @Override
            public void onClick(View v) {
                if ("app".equals(source)) host.openApp(key, name);
                else Player.send(getContext(), Player.TOGGLE, source);
            }
        });
        clickable(prev, new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!"app".equals(source)) Player.send(getContext(), Player.PREV, source);
                else if (radio && unitFm && unit.fmSeek(host.prefs(), false)) host.refresh();
                else if (media != null) media.controller.getTransportControls().skipToPrevious();
                else key(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
        });
        clickable(next, new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!"app".equals(source)) Player.send(getContext(), Player.NEXT, source);
                else if (radio && unitFm && unit.fmSeek(host.prefs(), true)) host.refresh();
                else if (media != null) media.controller.getTransportControls().skipToNext();
                else key(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
            }
        });
        clickable(play, new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!"app".equals(source)) {
                    if (!audioOn) claim();
                    Resume.remember(host.prefs(), audioOn ? "" : source);
                    Player.send(getContext(), Player.TOGGLE, source);
                    return;
                }
                if (!audioOn) claim();
                if (Player.playing) Player.send(getContext(), Player.STOP, null);
                if (radio && unitFm) {
                    // FM through the stereo's own services, the way the stock launcher's radio widget does
                    final boolean start = !audioOn;
                    Prefs pr = host.prefs();
                    String err = unit.fmPlay(getContext(), pr, start);
                    Resume.remember(pr, start ? "fm" : "");
                    boolean own = unit.fmFirst(pr);
                    if (start && !own) host.startRadioService();
                    fmNote = start && err != null ? Hindi.f("FM didn't start: %1$s", Hindi.t(err))
                            : "";
                    localOn = start;
                    audioOn = start;
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (unit != null) unit.fmPoll();
                        }
                    }, 800);
                    showPlay();
                    return;
                }
                Resume.remember(host.prefs(), audioOn ? "" : "app:" + (media != null ? media.controller.getPackageName() : ""));
                if (media != null) {
                    MediaController.TransportControls t = media.controller.getTransportControls();
                    if (media.playing) t.pause();
                    else t.play();
                } else {
                    key(audioOn ? android.view.KeyEvent.KEYCODE_MEDIA_PAUSE : android.view.KeyEvent.KEYCODE_MEDIA_PLAY);
                }
                localOn = !audioOn;
                audioOn = localOn;
                showPlay();
            }
        });
    }

    /** Our own player is playing this box's kind of thing. */
    boolean isMine() {
        return radio ? Player.WEB.equals(Player.mode) : Player.FILES.equals(Player.mode);
    }

    /** Media keys, as a steering-wheel button sends, for app players we can't see. */
    void key(int code) {
        android.media.AudioManager am = (android.media.AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        long t = android.os.SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0));
    }

    @Override
    void fit(int w, int h) {
        float s = Math.min(h / (138f * Ui.k), w / (420f * Ui.k));
        int m = Ui.px(22 * s);
        int bs = Ui.px(52 * s);
        head.setTextSize(0, Math.max(Ui.MIN_TEXT, 11 * s) * Ui.k);
        head.setPadding(0, Ui.px(6 * s), Ui.px(12 * s), Ui.px(6 * s));
        head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(10 * s), 0, 0));
        title.setTextSize(0, (radio ? 30 : 24) * Ui.k * s);
        sub.setTextSize(0, Math.max(Ui.MIN_TEXT, 15 * s) * Ui.k);
        // each button takes touches across at least 76 (Android's size for use while driving), however
        // small the box; the picture keeps its size and the rest is padding, eating into the gap first
        int touch = Math.max(bs, Math.min(Ui.px(HomeDock.TARGET), Math.round(w * 0.55f / 3)));
        int pad = (touch - bs) / 2, between = Math.max(0, Ui.px(14 * s) - (touch - bs));
        int bw = 3 * touch + 2 * between;
        text.setLayoutParams(at(Math.max(w - 3 * m - bw, Ui.px(60)), -2, Gravity.CENTER_VERTICAL | Gravity.LEFT, m, Ui.px(14 * s), 0, 0));
        for (ImageView b : new ImageView[]{prev, play, next}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(touch, touch);
            p.leftMargin = b == prev ? 0 : between;
            b.setPadding(pad, pad, pad, pad);
            b.setLayoutParams(p);
        }
        buttons.setLayoutParams(at(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, Ui.px(8 * s), m, 0));
        scan.setTextSize(0, 17 * Ui.k * s);
        scan.setPadding(Ui.px(26 * s), Ui.px(9 * s), Ui.px(26 * s), Ui.px(9 * s));
        scan.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(8 * s), m, 0));
        phone.setTextSize(0, 13 * Ui.k * s);
        phone.setPadding(Ui.px(16 * s), Ui.px(6 * s), Ui.px(16 * s), Ui.px(6 * s));
        phone.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(8 * s), m, 0));
        progress.setLayoutParams(at(-1, Ui.px(30 * s), Gravity.BOTTOM, m, 0, m, Ui.px(4 * s)));
        progress.text = Math.max(Ui.MIN_TEXT, 11 * s) * Ui.k;
    }

    @Override
    public void changed(Hub h) {
        unit = h.unit;
        Prefs p = host.prefs();
        String pref = p.source(radio ? "radio" : "music");
        boolean auto = "auto".equals(pref);
        if (radio) source = auto ? (h.online ? Player.WEB : "app") : pref;
        else source = auto ? (h.online ? "app" : Player.FILES) : pref;
        String label = radio ? (Player.WEB.equals(source) ? "ONLINE RADIO" : "FM RADIO")
                : (Player.FILES.equals(source) ? "MUSIC FILES" : "MUSIC APP");
        head.setText(Hindi.t(label) + (auto ? "  ·  " + Hindi.t("AUTO") : "") + "  ▾");
        // FM through the unit, unless a radio app shows its own player (then that app's controls)
        unitFm = radio && "app".equals(source) && unit.fm(p) && (unit.fmFirst(p) || h.radio == null);
        if (radio) scan.setVisibility(unitFm && unit.canScan(p) ? VISIBLE : GONE);
        else phone.setVisibility(Features.on(p, "projection") && Projection.app(getContext(), p) != null ? VISIBLE : GONE);
        if (unitFm) {
            media = null;
            Unit.Show sh = unit.fmShow(p, fmNote, localOn);
            title.setText(sh.title);
            sub.setText(sh.sub);
            boolean on = sh.playing != null ? sh.playing : localOn;
            audioOn = on && (unit.fmFirst(p) || "radio".equals(owner));
            showPlay();
            return;
        }
        if (!"app".equals(source)) {
            media = null;
            boolean mine = isMine();
            Player.Track t = mine ? Player.current() : null;
            if (mine && !Player.status.isEmpty() && (Player.busy || !Player.playing)) {
                title.setText(t != null ? t.name : radio ? Hindi.t("Online radio") : Hindi.t("Music files"));
                sub.setText(Hindi.t(Player.status));
            } else if (t != null) {
                title.setText(t.name);
                sub.setText(Player.busy ? Hindi.t("Connecting…") : t.sub);
            } else {
                title.setText(radio ? Hindi.t("Online radio") : Hindi.t("Music files"));
                sub.setText(radio ? Hindi.t("Tap play for Indian stations") : Hindi.t("Tap play for music on the unit or USB"));
            }
            audioOn = mine && Player.playing;
        } else {
            media = radio ? h.radio : h.music;
            RadioText.Found rf = radio && media != null && Features.on(p, "radioText")
                    ? RadioText.parse(media.title, media.subtitle) : null;
            if (rf != null) {
                title.setText(rf.big());
                sub.setText(rf.station);
            } else if (media != null && media.title != null) {
                title.setText(media.title);
                sub.setText(media.subtitle == null ? "" : media.subtitle);
            } else {
                title.setText(radio ? Hindi.t("FM radio") : Hindi.t("Music app"));
                sub.setText(radio ? Hindi.t("Tap to open the radio") : Hindi.t("Tap to open the music app"));
            }
            audioOn = media != null ? media.playing : localOn && kind().equals(owner);
        }
        showPlay();
        tint(p);
        progress.follow(!radio && Features.on(p, "seek") && "app".equals(source) ? media : null);
    }

    /** The box's colour from the album cover, or the theme's panel. */
    private void tint(Prefs p) {
        android.graphics.Bitmap art = media != null && Features.on(p, "artColour") ? media.art : null;
        if (art == tintedFor) return;
        tintedFor = art;
        int col = 0;
        if (art != null) {
            try {
                android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(art, 24, 24, true);
                int[] px = new int[24 * 24];
                small.getPixels(px, 0, 24, 0, 0, 24, 24);
                col = ArtColour.pick(px);
            } catch (Throwable e) {
                Diag.note("album art colour", e);
            }
        }
        if (col == 0) {
            setBackground(Ui.panel());
            progress.ink = Ui.SILVER;
        } else {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{Ui.mix(col, Ui.PANEL, 0.35f), Ui.PANEL});
            g.setCornerRadius(Ui.RADIUS * Ui.k);
            g.setStroke(Math.max(1, Ui.px(1.2f)), Ui.EDGE);
            setBackground(g);
            progress.ink = Ui.mix(col, 0xFFFFFFFF, 0.45f);
        }
        progress.invalidate();
    }

    /** A thin bar with the time played and the length; drag along it to move in the track. */
    static final class Progress extends View {
        final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Hub.Media media;
        long duration;
        float text = 12, dragTo = -1;
        int ink = Ui.SILVER;
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                invalidate();
                if (media != null && media.playing) postDelayed(this, 1000);
            }
        };

        Progress(Context c) {
            super(c);
            setVisibility(GONE);
        }

        void follow(Hub.Media m) {
            long d = 0;
            try {
                if (m != null && m.controller != null && m.controller.getMetadata() != null)
                    d = m.controller.getMetadata().getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
            } catch (Throwable ignored) {
                // session gone: no length to show
            }
            media = d > 0 ? m : null;
            duration = d;
            setVisibility(media == null ? GONE : VISIBLE);
            removeCallbacks(tick);
            if (media != null) post(tick);
        }

        long now() {
            try {
                android.media.session.PlaybackState st = media.controller.getPlaybackState();
                if (st == null) return 0;
                return ArtColour.position(st.getPosition(), st.getLastPositionUpdateTime(),
                        st.getState() == android.media.session.PlaybackState.STATE_PLAYING ? st.getPlaybackSpeed() : 0,
                        android.os.SystemClock.elapsedRealtime(), duration);
            } catch (Throwable e) {
                return 0;
            }
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (media == null || getWidth() == 0) return false;
            float f = Math.max(0, Math.min(1, e.getX() / getWidth()));
            int act = e.getActionMasked();
            if (act == android.view.MotionEvent.ACTION_DOWN || act == android.view.MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                dragTo = f;
                invalidate();
            } else if (act == android.view.MotionEvent.ACTION_UP) {
                try {
                    media.controller.getTransportControls().seekTo((long) (f * duration));
                } catch (Throwable ex) {
                    Diag.note("seek", ex);
                }
                dragTo = -1;
                invalidate();
            } else if (act == android.view.MotionEvent.ACTION_CANCEL) {
                dragTo = -1;
                invalidate();
            }
            return true;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            if (media == null) return;
            long at = dragTo >= 0 ? (long) (dragTo * duration) : now();
            float f = duration > 0 ? at / (float) duration : 0;
            float y = getHeight() * 0.3f, w = getWidth(), th = Math.max(2, getHeight() * 0.08f);
            paint.setColor(Ui.mix(Ui.SILVER_DIM, Ui.PANEL, 0.6f));
            c.drawRect(0, y - th / 2, w, y + th / 2, paint);
            paint.setColor(ink);
            c.drawRect(0, y - th / 2, w * f, y + th / 2, paint);
            c.drawCircle(w * f, y, th * (dragTo >= 0 ? 3 : 1.8f), paint);
            paint.setTextSize(text);
            paint.setColor(Ui.SILVER_DIM);
            paint.setTextAlign(android.graphics.Paint.Align.LEFT);
            c.drawText(ArtColour.clock(at), 0, getHeight() - 2, paint);
            paint.setTextAlign(android.graphics.Paint.Align.RIGHT);
            c.drawText(ArtColour.clock(duration), w, getHeight() - 2, paint);
        }
    }

    void showPlay() {
        play.setImageBitmap(Ui.img(getContext(), audioOn ? "btn_pause" : "btn_play"));
    }
}
