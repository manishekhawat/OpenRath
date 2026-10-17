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

    MediaCard(Context c, boolean isRadio) {
        super(c);
        radio = isRadio;
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
        head.setTextSize(0, 11 * Ui.k * s);
        head.setPadding(0, Ui.px(6 * s), Ui.px(12 * s), Ui.px(6 * s));
        head.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.LEFT, m, Ui.px(10 * s), 0, 0));
        title.setTextSize(0, (radio ? 30 : 24) * Ui.k * s);
        sub.setTextSize(0, 13 * Ui.k * s);
        int bw = 3 * bs + 2 * Ui.px(14 * s);
        text.setLayoutParams(at(Math.max(w - 3 * m - bw, Ui.px(60)), -2, Gravity.CENTER_VERTICAL | Gravity.LEFT, m, Ui.px(14 * s), 0, 0));
        for (ImageView b : new ImageView[]{prev, play, next}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(bs, bs);
            p.leftMargin = b == prev ? 0 : Ui.px(14 * s);
            b.setLayoutParams(p);
        }
        buttons.setLayoutParams(at(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, Ui.px(8 * s), m, 0));
        scan.setTextSize(0, 17 * Ui.k * s);
        scan.setPadding(Ui.px(26 * s), Ui.px(9 * s), Ui.px(26 * s), Ui.px(9 * s));
        scan.setLayoutParams(at(-2, -2, Gravity.TOP | Gravity.RIGHT, 0, Ui.px(8 * s), m, 0));
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
            if (media != null && media.title != null) {
                title.setText(media.title);
                sub.setText(media.subtitle == null ? "" : media.subtitle);
            } else {
                title.setText(radio ? Hindi.t("FM radio") : Hindi.t("Music app"));
                sub.setText(radio ? Hindi.t("Tap to open the radio") : Hindi.t("Tap to open the music app"));
            }
            audioOn = media != null ? media.playing : localOn && kind().equals(owner);
        }
        showPlay();
    }

    void showPlay() {
        play.setImageBitmap(Ui.img(getContext(), audioOn ? "btn_pause" : "btn_play"));
    }
}
