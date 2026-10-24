package org.openrath.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The radio and music screen, full screen over the dashboard (a tap on the music or radio box, the
 * dock's music and radio buttons, "open music" by voice). On the left, what plays now: the cover,
 * the song or station, where it comes from, the time bar for music files and apps, big previous,
 * play / pause and next buttons, and the volume. On the right three lists: Radio (FM through the
 * stereo with seek, tune and presets, and the online stations with favourites), Music (the music
 * files by song, artist or folder, with a search while parked) and Apps (other music apps, their
 * libraries and queues through Browse). While moving, lists stop at Browse.MOVING_ROWS and the
 * search can't be typed in.
 */
final class MusicScreen extends FrameLayout {

    static final String RADIO = "radio", MUSIC = "music", APPS = "apps";
    /** Kept between opens: the list shown, the music view (songs, artists, folders) and the artist or folder opened. */
    static String tab = MUSIC, view = "songs", group;
    /** What the screen played last, so a paused source keeps showing: "own", "fm" or "app". */
    static String last = "";
    /** Our own guess for FM through the unit, for units that can't tell. */
    static boolean fmOn;
    private static boolean loadingSongs, loadingStations, triedSongs, triedStations;

    interface Closed {
        void closed();
    }

    private final HomeActivity a;
    private final Prefs p;
    private final Closed closed;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // what plays now
    private final Cover cover;
    private final TextView source, title, sub;
    private final Bar bar, volume;
    private final ImageView prev, play, next;
    // the lists
    private final TextView[] tabs = new TextView[3];
    private final LinearLayout tools, list;
    private final ScrollView scroll;
    private final TextView[] views = new TextView[3];
    private final EditText search;
    private final TextView parkedNote;
    private String fmNote = "";
    private String shown = "";
    private boolean gone;

    static final String[] TABS = {RADIO, MUSIC, APPS}, TAB_NAMES = {"Radio", "Music", "Apps"};
    static final String[] VIEWS = {"songs", "artists", "folders"}, VIEW_NAMES = {"Songs", "Artists", "Folders"};

    MusicScreen(HomeActivity act, String start, Closed c) {
        super(act);
        a = act;
        p = act.prefs;
        closed = c;
        if (start != null) tab = start;
        setBackgroundColor(Ui.BACKGROUND);
        setClickable(true);   // nothing reaches the dashboard underneath
        setFocusableInTouchMode(true);   // the screen holds the focus, so the search doesn't open the keyboard by itself
        int m = Ui.px(18);
        android.util.DisplayMetrics dm = act.getResources().getDisplayMetrics();
        boolean tall = Ui.portrait(dm.widthPixels, dm.heightPixels);

        LinearLayout all = new LinearLayout(act);
        all.setOrientation(tall ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        all.setPadding(m, m, m, m);
        addView(all, new LayoutParams(-1, -1));

        // ---- left: what plays now
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView back = Ui.text(act, 17, Ui.SILVER, "sans-serif-medium", Gravity.LEFT | Gravity.CENTER_VERTICAL);
        back.setText(Hindi.t("‹  Back to the dashboard"));
        back.setPadding(Ui.px(14), 0, Ui.px(14), 0);
        back.setBackground(pill(false));
        tap(back, new Runnable() {
            @Override
            public void run() {
                closed.closed();
            }
        });
        left.addView(back, new LinearLayout.LayoutParams(-1, Ui.px(56)));

        LinearLayout top = new LinearLayout(act);
        top.setGravity(Gravity.CENTER_VERTICAL);
        cover = new Cover(act);
        int cs = Ui.px(tall ? 140 : 172);
        top.addView(cover, new LinearLayout.LayoutParams(cs, cs));
        LinearLayout words = new LinearLayout(act);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.px(18), 0, 0, 0);
        source = Ui.label(act, "");
        source.setEllipsize(TextUtils.TruncateAt.END);
        title = Ui.text(act, 28, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.px(10), 0, Ui.px(6));
        sub = Ui.text(act, 16, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(source);
        words.addView(title);
        words.addView(sub);
        top.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = Ui.px(16);
        left.addView(top, tp);

        bar = new Bar(act);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.px(40));
        bp.topMargin = Ui.px(12);
        left.addView(bar, bp);

        LinearLayout buttons = new LinearLayout(act);
        buttons.setGravity(Gravity.CENTER);
        prev = button(act, "btn_prev");
        play = button(act, "btn_play");
        next = button(act, "btn_next");
        int touch = Ui.px(tall ? 88 : 100), pad = Ui.px(16);
        for (ImageView b : new ImageView[]{prev, play, next}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(b == play ? touch + Ui.px(12) : touch, b == play ? touch + Ui.px(12) : touch);
            lp.leftMargin = lp.rightMargin = Ui.px(10);
            b.setPadding(pad, pad, pad, pad);
            buttons.addView(b, lp);
        }
        tap(prev, new Runnable() {
            @Override
            public void run() {
                skip(false);
            }
        });
        tap(play, new Runnable() {
            @Override
            public void run() {
                toggle();
            }
        });
        tap(next, new Runnable() {
            @Override
            public void run() {
                skip(true);
            }
        });
        left.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout vol = new LinearLayout(act);
        vol.setGravity(Gravity.CENTER_VERTICAL);
        TextView down = chip(act, "−", false), up = chip(act, "+", false);
        down.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28 * Ui.k);
        up.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 28 * Ui.k);
        tap(down, new Runnable() {
            @Override
            public void run() {
                volume(false);
            }
        });
        tap(up, new Runnable() {
            @Override
            public void run() {
                volume(true);
            }
        });
        volume = new Bar(act);
        vol.addView(down, new LinearLayout.LayoutParams(Ui.px(HomeDock.TARGET), Ui.px(62)));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, Ui.px(40), 1f);
        vp.leftMargin = vp.rightMargin = Ui.px(14);
        vol.addView(volume, vp);
        vol.addView(up, new LinearLayout.LayoutParams(Ui.px(HomeDock.TARGET), Ui.px(62)));
        LinearLayout.LayoutParams volp = new LinearLayout.LayoutParams(-1, -2);
        volp.topMargin = Ui.px(10);
        left.addView(vol, volp);

        if (tall) all.addView(left, new LinearLayout.LayoutParams(-1, -2));
        else all.addView(left, new LinearLayout.LayoutParams(0, -1, 0.42f));

        View rule = new View(act);
        rule.setBackgroundColor(Ui.mix(Ui.EDGE, Ui.BACKGROUND, 0.7f));
        LinearLayout.LayoutParams rp = tall ? new LinearLayout.LayoutParams(-1, Math.max(1, Ui.px(1.2f)))
                : new LinearLayout.LayoutParams(Math.max(1, Ui.px(1.2f)), -1);
        if (tall) rp.topMargin = rp.bottomMargin = Ui.px(14);
        else rp.leftMargin = rp.rightMargin = Ui.px(22);
        all.addView(rule, rp);

        // ---- right: the lists
        LinearLayout right = new LinearLayout(act);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tabRow = new LinearLayout(act);
        for (int i = 0; i < TABS.length; i++) {
            final String t = TABS[i];
            tabs[i] = chip(act, Hindi.t(TAB_NAMES[i]), false);
            tabs[i].setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 19 * Ui.k);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(60), 1f);
            if (i > 0) lp.leftMargin = Ui.px(10);
            tabRow.addView(tabs[i], lp);
            tap(tabs[i], new Runnable() {
                @Override
                public void run() {
                    tab = t;
                    scroll.scrollTo(0, 0);
                    refresh(true);
                }
            });
        }
        right.addView(tabRow, new LinearLayout.LayoutParams(-1, -2));

        tools = new LinearLayout(act);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < VIEWS.length; i++) {
            final String v = VIEWS[i];
            views[i] = chip(act, Hindi.t(VIEW_NAMES[i]), false);
            views[i].setPadding(Ui.px(16), 0, Ui.px(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.px(54));
            lp.rightMargin = Ui.px(8);
            tools.addView(views[i], lp);
            tap(views[i], new Runnable() {
                @Override
                public void run() {
                    view = v;
                    group = null;
                    scroll.scrollTo(0, 0);
                    refresh(true);
                }
            });
        }
        search = new EditText(act);
        search.setSingleLine(true);
        search.setHint(Hindi.t("Search"));
        search.setHintTextColor(Ui.SILVER_DIM);
        search.setTextColor(Ui.SILVER);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 16 * Ui.k);
        search.setPadding(Ui.px(14), 0, Ui.px(14), 0);
        search.setBackground(pill(false));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int s, int b, int n) {
            }

            @Override
            public void onTextChanged(CharSequence t, int s, int b, int n) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
                scroll.scrollTo(0, 0);
                refresh(true);
            }
        });
        tools.addView(search, new LinearLayout.LayoutParams(0, Ui.px(54), 1f));
        parkedNote = Ui.text(act, 13, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        parkedNote.setText(Hindi.t("Search when the car is stopped"));
        tools.addView(parkedNote, new LinearLayout.LayoutParams(0, Ui.px(54), 1f));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = Ui.px(12);
        right.addView(tools, tlp);

        scroll = new ScrollView(act);
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.px(6), 0, m);
        scroll.addView(list);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1f);
        sp.topMargin = Ui.px(8);
        right.addView(scroll, sp);
        all.addView(right, tall ? new LinearLayout.LayoutParams(-1, 0, 1f) : new LinearLayout.LayoutParams(0, -1, 0.58f));

        bar.seek = new Bar.Seek() {
            @Override
            public void to(float f) {
                seekTo(f);
            }
        };
        refresh(true);
        handler.postDelayed(tick, 1000);
        post(new Runnable() {
            @Override
            public void run() {
                requestFocus();
            }
        });
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (gone) return;
            refresh(false);
            handler.postDelayed(this, 1000);
        }
    };

    /** The screen is going: stop the clock. */
    void gone() {
        gone = true;
        handler.removeCallbacksAndMessages(null);
        hideKeyboard();
    }

    // ------------------------------------------------------------------ what plays now

    /** What plays now, or what was playing last. */
    static final class Now {
        /** "own" (OpenRath's player), "fm" (FM through the stereo), "app" (another app) or "none". */
        String kind = "none", title = "", sub = "", from = "", name = "";
        boolean playing;
        Bitmap art;
        Hub.Media media;
    }

    private Unit unit() {
        return a.hub.unit;
    }

    private static boolean on(Hub.Media m) {
        return m != null && m.playing;
    }

    Now now() {
        Hub h = a.hub;
        Unit u = unit();
        Now n = new Now();
        Player.Track t = Player.mode != null ? Player.current() : null;
        boolean own = t != null && (Player.playing || Player.busy);
        boolean fm = u.fm(p) && (u.fmPlaying() || fmOn && "radio".equals(MediaCard.owner));
        Hub.Media app = on(h.music) ? h.music : on(h.radio) ? h.radio : null;
        String kind;
        if (own) kind = "own";
        else if (fm) kind = "fm";
        else if (app != null) kind = "app";
        else if ("fm".equals(last) && u.fm(p)) kind = "fm";
        else if ("own".equals(last) && t != null) kind = "own";
        else if (h.music != null || h.radio != null) kind = "app";
        else if (t != null) kind = "own";
        else kind = "none";
        n.kind = kind;
        if ("own".equals(kind)) {
            boolean web = Player.WEB.equals(Player.mode);
            n.title = t.name;
            n.name = web ? t.name : t.sub.isEmpty() ? t.name : t.sub;
            n.sub = Player.busy ? Hindi.t("Connecting…") : !Player.status.isEmpty() && !Player.playing ? Hindi.t(Player.status) : web ? "" : t.sub;
            n.from = Hindi.t(web ? "ONLINE RADIO" : "MUSIC FILES");
            n.playing = Player.playing;
        } else if ("fm".equals(kind)) {
            Unit.Show sh = u.fmShow(p, fmNote, fmOn);
            n.title = sh.title;
            n.sub = sh.sub;
            n.name = sh.title;
            n.from = Hindi.t("FM RADIO");
            n.playing = sh.playing != null ? sh.playing : fmOn;
        } else if ("app".equals(kind)) {
            Hub.Media md = app != null ? app : h.music != null ? h.music : h.radio;
            boolean radio = md == h.radio;
            n.media = md;
            n.art = md.art;
            String pkg = md.controller == null ? "" : md.controller.getPackageName();
            n.name = Browse.label(a.getPackageManager(), pkg);
            RadioText.Found rf = radio && Features.on(p, "radioText") ? RadioText.parse(md.title, md.subtitle) : null;
            if (rf != null) {
                n.title = rf.big();
                n.sub = Player.clean(rf.station);
            } else {
                n.title = md.title == null ? n.name : radio ? Player.clean(md.title) : md.title;
                n.sub = md.subtitle == null ? "" : radio ? Player.clean(md.subtitle) : md.subtitle;
            }
            n.from = n.name.toUpperCase(java.util.Locale.getDefault());
            n.playing = md.playing;
        } else {
            n.title = Hindi.t("Nothing playing");
            n.sub = Hindi.t("Pick a station or a song");
            n.from = Hindi.t("RADIO AND MUSIC");
        }
        return n;
    }

    private void showNow(Now n) {
        source.setText(n.from);
        title.setText(n.title);
        sub.setText(n.sub);
        sub.setVisibility(n.sub.isEmpty() ? GONE : VISIBLE);
        cover.set(n.art, "none".equals(n.kind) ? "" : n.name);
        play.setImageBitmap(Ui.img(getContext(), n.playing ? "btn_pause" : "btn_play"));
        // the time bar: music files and apps that say how long the song is; stations are live
        long at = 0, len = 0;
        if ("own".equals(n.kind) && Player.FILES.equals(Player.mode)) {
            at = Player.position();
            len = Player.duration();
        } else if ("app".equals(n.kind) && n.media != null && n.media.controller != null) {
            try {
                MediaController c = n.media.controller;
                if (c.getMetadata() != null) len = c.getMetadata().getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
                android.media.session.PlaybackState st = c.getPlaybackState();
                if (st != null && len > 0) at = ArtColour.position(st.getPosition(), st.getLastPositionUpdateTime(),
                        st.getState() == android.media.session.PlaybackState.STATE_PLAYING ? st.getPlaybackSpeed() : 0,
                        android.os.SystemClock.elapsedRealtime(), len);
            } catch (Throwable e) {
                len = 0;   // the app's session went away
            }
        }
        bar.setVisibility(len > 0 ? VISIBLE : INVISIBLE);
        if (len > 0) bar.show(at / (float) len, ArtColour.clock(at), ArtColour.clock(len));
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        int v = am.getStreamVolume(AudioManager.STREAM_MUSIC), max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume.show(MusicLists.share(v, max), Hindi.t("Volume"), String.valueOf(v));
    }

    private void toggle() {
        Now n = now();
        if ("own".equals(n.kind)) own(Player.TOGGLE, Player.mode, -1);
        else if ("fm".equals(n.kind)) fm(!n.playing);
        else if ("app".equals(n.kind) && n.media != null && n.media.controller != null) {
            if (Player.playing) Player.send(a, Player.STOP, null);
            MediaController.TransportControls t = n.media.controller.getTransportControls();
            Resume.remember(p, n.playing ? "" : "app:" + n.media.controller.getPackageName());
            if (n.playing) t.pause();
            else t.play();
            last = "app";
        } else if (RADIO.equals(tab) && unit().fm(p)) fm(true);
        else own(Player.TOGGLE, RADIO.equals(tab) ? Player.WEB : Player.FILES, -1);
        later();
    }

    private void skip(boolean up) {
        Now n = now();
        if ("own".equals(n.kind)) own(up ? Player.NEXT : Player.PREV, Player.mode, -1);
        else if ("fm".equals(n.kind)) {
            if (!unit().fmSeek(p, up)) tune(up ? 1 : -1);
        } else if ("app".equals(n.kind) && n.media != null && n.media.controller != null) {
            if (up) n.media.controller.getTransportControls().skipToNext();
            else n.media.controller.getTransportControls().skipToPrevious();
        }
        later();
    }

    private void seekTo(float f) {
        Now n = now();
        if ("own".equals(n.kind)) Player.seek((long) (f * Player.duration()));
        else if ("app".equals(n.kind) && n.media != null && n.media.controller != null) {
            try {
                long len = n.media.controller.getMetadata().getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
                n.media.controller.getTransportControls().seekTo((long) (f * len));
            } catch (Throwable e) {
                Diag.note("seek", e);
            }
        }
        refresh(false);
    }

    private void volume(boolean up) {
        Volume.step(getContext(), up);
        refresh(false);
    }

    /** OpenRath's own player: online radio or music files. */
    private void own(String action, String mode, int index) {
        if (mode == null) mode = Player.FILES;
        boolean web = Player.WEB.equals(mode);
        MediaCard.owner = web ? RADIO : MUSIC;
        if (unit().fmPlaying()) unit().fmQuiet();
        fmOn = false;
        boolean starting = !Player.playing || Player.PICK.equals(action) || !mode.equals(Player.mode);
        Resume.remember(p, starting ? mode : "");
        Player.send(a, action, mode, index);
        last = "own";
    }

    /** FM through the stereo, the way the radio box starts it. */
    private void fm(boolean start) {
        Unit u = unit();
        if (start && Player.playing) Player.send(a, Player.STOP, null);
        MediaCard.owner = RADIO;
        String err = u.fmPlay(a, p, start);
        Resume.remember(p, start ? "fm" : "");
        if (start && !u.fmFirst(p)) a.startRadioService();
        fmNote = start && err != null ? Hindi.f("FM didn't start: %1$s", Hindi.t(err)) : "";
        fmOn = start;
        last = "fm";
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                unit().fmPoll();
            }
        }, 800);
    }

    /** The FM station now, as "98.3". */
    private String fmNow() {
        Unit.Show sh = unit().fmShow(p, "", fmOn);
        RadioText.Found f = RadioText.parse(sh.title, "");
        String s = f != null && "FM".equals(f.band) ? f.freq : "";
        return s.isEmpty() ? MusicLists.freq(p.fmFreq()) : s;
    }

    private void tune(int tenths) {
        String f = MusicLists.step(fmNow(), tenths);
        go(f);
    }

    /** Tune FM to a station, starting FM first when it is off. */
    private void go(final String f) {
        if (f.isEmpty()) return;
        p.setFmFreq(f);
        Now n = now();
        if ("fm".equals(n.kind) && n.playing) unit().fmTune(p, f);
        else {
            fm(true);
            // the radio can take a moment to come on (or its app to open); then tune it, as voice does
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    unit().fmTune(p, f);
                    refresh(true);
                }
            }, 2500);
        }
        later();
    }

    /** Look again in a moment, once the player or the radio has answered. */
    private void later() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!gone) {
                    a.refresh();
                    refresh(true);
                }
            }
        }, 400);
    }

    // ------------------------------------------------------------------ the lists

    /** Redraw what plays now; rebuild the list when what it shows changed (or `force`). */
    void refresh(boolean force) {
        if (gone) return;
        Now n = now();
        showNow(n);
        boolean moving = ChildLock.moving();
        for (int i = 0; i < TABS.length; i++) tabs[i].setBackground(pill(TABS[i].equals(tab)));
        boolean music = MUSIC.equals(tab);
        tools.setVisibility(music ? VISIBLE : GONE);
        for (int i = 0; i < VIEWS.length; i++) views[i].setBackground(pill(VIEWS[i].equals(view)));
        // no typing while the car moves: the search goes, and what was typed with it
        if (moving && search.getVisibility() == VISIBLE) {
            search.setVisibility(GONE);
            hideKeyboard();
            if (search.length() > 0) search.setText("");   // calls back here, now with the search gone
        }
        search.setVisibility(moving ? GONE : VISIBLE);
        parkedNote.setVisibility(moving ? VISIBLE : GONE);
        String q = moving ? "" : search.getText().toString();
        StringBuilder sig = new StringBuilder();
        sig.append(tab).append('|').append(view).append('|').append(group).append('|').append(q).append('|').append(moving)
                .append('|').append(Player.stations.size()).append('|').append(Player.songs.size()).append('|').append(Player.mode)
                .append('|').append(Player.station).append('|').append(Player.song).append('|').append(Player.playing)
                .append('|').append(n.kind).append(n.title).append(n.playing).append('|').append(p.str("webFavs", ""))
                .append('|').append(p.str("fmPresets", "")).append(p.str("fmStations", "")).append('|').append(a.hub.sessionPkgs)
                .append('|').append(a.hub.online).append(loadingSongs).append(loadingStations);
        if (!force && sig.toString().equals(shown)) return;
        shown = sig.toString();
        list.removeAllViews();
        if (RADIO.equals(tab)) radioList(n, moving);
        else if (music) musicList(moving, q);
        else appsList(n, moving);
    }

    private void radioList(Now n, boolean moving) {
        final Unit u = unit();
        if (u.fm(p)) {
            heading(Hindi.t("FM RADIO"));
            boolean fm = "fm".equals(n.kind);
            final String now = fmNow();
            LinearLayout panel = new LinearLayout(getContext());
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(Ui.panel());
            panel.setPadding(Ui.px(18), Ui.px(12), Ui.px(18), Ui.px(14));
            TextView big = Ui.text(getContext(), 46, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
            big.setText(now + "  FM");
            panel.addView(big);
            LinearLayout row = new LinearLayout(getContext());
            String[] names = {"Seek down", "−", "+", "Seek up"};
            for (int i = 0; i < names.length; i++) {
                final int k = i;
                TextView b = chip(getContext(), i == 1 || i == 2 ? names[i] : Hindi.t(names[i]), false);
                if (i == 1 || i == 2) b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 26 * Ui.k);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(64), i == 1 || i == 2 ? 0.7f : 1f);
                if (i > 0) lp.leftMargin = Ui.px(8);
                row.addView(b, lp);
                tap(b, new Runnable() {
                    @Override
                    public void run() {
                        if (k == 1 || k == 2) tune(k == 1 ? -1 : 1);
                        else {
                            if (!"fm".equals(now().kind) || !now().playing) fm(true);
                            if (!u.fmSeek(p, k == 3)) tune(k == 3 ? 1 : -1);
                            later();
                        }
                    }
                });
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.px(12);
            panel.addView(row, rlp);
            LinearLayout row2 = new LinearLayout(getContext());
            final boolean playing = fm && n.playing;
            TextView start = chip(getContext(), Hindi.t(playing ? "Stop FM" : "Play FM"), playing);
            tap(start, new Runnable() {
                @Override
                public void run() {
                    fm(!playing);
                    later();
                }
            });
            row2.addView(start, new LinearLayout.LayoutParams(0, Ui.px(64), 1f));
            final List<String> mine = MusicLists.items(p.str("fmPresets", ""), ",");
            final boolean saved = mine.contains(now);
            TextView save = chip(getContext(), Hindi.t(saved ? "★  Saved" : "☆  Save station"), saved);
            tap(save, new Runnable() {
                @Override
                public void run() {
                    p.put("fmPresets", MusicLists.toggle(p.str("fmPresets", ""), now, ","));
                    refresh(true);
                }
            });
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
            slp.leftMargin = Ui.px(8);
            row2.addView(save, slp);
            if (u.canScan(p)) {
                TextView scan = chip(getContext(), Hindi.t("Scan"), false);
                tap(scan, new Runnable() {
                    @Override
                    public void run() {
                        u.scan(a, p, new Runnable() {
                            @Override
                            public void run() {
                                refresh(true);
                            }
                        });
                    }
                });
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, Ui.px(64), 0.7f);
                clp.leftMargin = Ui.px(8);
                row2.addView(scan, clp);
            }
            LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(-1, -2);
            r2.topMargin = Ui.px(8);
            panel.addView(row2, r2);
            addRow(panel);

            // the presets: saved by hand and found by a scan, as big buttons four to a line
            List<String> scanned = Scan.saved(p);
            if (scanned.isEmpty()) scanned = StockRadio.stations();
            List<String> presets = Browse.trim(MusicLists.presets(p.str("fmPresets", ""), scanned), moving, Browse.MOVING_ROWS);
            if (presets.isEmpty()) note(Hindi.t("Save a station, or scan, to keep it here"));
            LinearLayout line = null;
            for (int i = 0; i < presets.size(); i++) {
                final String f = presets.get(i);
                if (i % 4 == 0) {
                    line = new LinearLayout(getContext());
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.topMargin = Ui.px(8);
                    list.addView(line, lp);
                }
                TextView b = chip(getContext(), f, fm && f.equals(now));
                b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 20 * Ui.k);
                tap(b, new Runnable() {
                    @Override
                    public void run() {
                        go(f);
                    }
                });
                b.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        // a long press takes a preset out
                        p.put("fmPresets", MusicLists.without(p.str("fmPresets", ""), f, ","));
                        p.put("fmStations", MusicLists.without(p.str("fmStations", ""), f, ","));
                        Toast.makeText(getContext(), Hindi.f("%1$s FM taken off the presets", f), Toast.LENGTH_SHORT).show();
                        refresh(true);
                        return true;
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
                if (i % 4 > 0) lp.leftMargin = Ui.px(8);
                line.addView(b, lp);
            }
            if (line != null) for (int i = presets.size() % 4; i > 0 && i < 4; i++) line.addView(new View(getContext()), spacer(i));
        } else {
            heading(Hindi.t("FM RADIO"));
            Hub.Media r = a.hub.radio;
            row(Hindi.t("Radio app"), r != null && r.title != null ? Player.clean(r.title) : Hindi.t("Opens your radio app"), false, new Runnable() {
                @Override
                public void run() {
                    a.openApp("media:radio", "Radio");
                }
            }, null, null);
        }

        heading(Hindi.t("ONLINE RADIO"));
        if (Player.stations.isEmpty()) {
            if (!triedStations && a.hub.online) load(true);
            if (loadingStations) note(Hindi.t("Finding stations…"));
            else {
                note(a.hub.online ? Hindi.t("Web radio unreachable") : Hindi.t("Online radio needs the internet"));
                row(Hindi.t("Look again"), "", false, new Runnable() {
                    @Override
                    public void run() {
                        load(true);
                        refresh(true);
                    }
                }, null, null);
            }
            return;
        }
        Set<String> favs = new HashSet<>(MusicLists.items(p.str("webFavs", ""), "\n"));
        List<String> names = new ArrayList<>();
        for (Player.Track t : Player.stations) names.add(t.name);
        List<Integer> order = MusicLists.favFirst(names, favs);
        List<Integer> shownRows = Browse.trim(order, moving, Browse.MOVING_ROWS);
        for (final int i : shownRows) {
            final Player.Track t = Player.stations.get(i);
            boolean fav = favs.contains(t.name);
            row(t.name, t.sub, Player.WEB.equals(Player.mode) && Player.station == i, new Runnable() {
                @Override
                public void run() {
                    own(Player.PICK, Player.WEB, i);
                    later();
                }
            }, fav ? "★" : "☆", new Runnable() {
                @Override
                public void run() {
                    p.put("webFavs", MusicLists.toggle(p.str("webFavs", ""), t.name, "\n"));
                    refresh(true);
                }
            });
        }
        more(order.size(), shownRows.size());
    }

    private void musicList(boolean moving, String q) {
        if (Player.songs.isEmpty()) {
            if (!triedSongs) load(false);
            if (loadingSongs) note(Hindi.t("Looking for music…"));
            else {
                note(Hindi.t("No music files found"));
                row(Hindi.t("Look again"), Hindi.t("After putting in a USB stick or SD card"), false, new Runnable() {
                    @Override
                    public void run() {
                        triedSongs = false;
                        load(false);
                        refresh(true);
                    }
                }, null, null);
            }
            return;
        }
        List<String> names = new ArrayList<>(), subs = new ArrayList<>();
        for (Player.Track t : Player.songs) {
            names.add(t.name);
            subs.add(t.sub);
        }
        List<Integer> found = MusicLists.search(names, subs, q);
        if ("songs".equals(view)) {
            songs(found, moving);
            return;
        }
        boolean artists = "artists".equals(view);
        List<String> keys = new ArrayList<>();
        for (int i : found) {
            Player.Track t = Player.songs.get(i);
            keys.add(artists ? MusicLists.artist(t.sub, t.src) : MusicLists.folder(t.src));
        }
        Map<String, List<Integer>> groups = MusicLists.group(keys);
        if (group != null && groups.containsKey(group)) {
            row(Hindi.t(artists ? "‹  All artists" : "‹  All folders"), "", false, new Runnable() {
                @Override
                public void run() {
                    group = null;
                    scroll.scrollTo(0, 0);
                    refresh(true);
                }
            }, null, null);
            heading(group.isEmpty() ? Hindi.t(artists ? "Unknown artist" : "Other files") : group);
            List<Integer> in = new ArrayList<>();
            for (int k : groups.get(group)) in.add(found.get(k));
            songs(in, moving);
            return;
        }
        group = null;
        List<String> all = new ArrayList<>(groups.keySet());
        List<String> rows = Browse.trim(all, moving, Browse.MOVING_ROWS);
        for (final String g : rows) {
            int count = groups.get(g).size();
            row(g.isEmpty() ? Hindi.t(artists ? "Unknown artist" : "Other files") : g,
                    count == 1 ? Hindi.t("1 song") : Hindi.f("%1$s songs", count), false, new Runnable() {
                        @Override
                        public void run() {
                            group = g;
                            scroll.scrollTo(0, 0);
                            refresh(true);
                        }
                    }, "›", null);
        }
        if (all.isEmpty()) note(Hindi.t("Nothing matches the search"));
        more(all.size(), rows.size());
    }

    private void songs(List<Integer> found, boolean moving) {
        List<Integer> rows = Browse.trim(found, moving, Browse.MOVING_ROWS);
        for (final int i : rows) {
            Player.Track t = Player.songs.get(i);
            row(t.name, t.sub, Player.FILES.equals(Player.mode) && Player.song == i, new Runnable() {
                @Override
                public void run() {
                    own(Player.PICK, Player.FILES, i);
                    later();
                }
            }, null, null);
        }
        if (found.isEmpty()) note(Hindi.t("Nothing matches the search"));
        more(found.size(), rows.size());
    }

    private void appsList(Now n, boolean moving) {
        final Hub h = a.hub;
        final Runnable changed = new Runnable() {
            @Override
            public void run() {
                a.refresh();
                refresh(true);
            }
        };
        heading(Hindi.t("WHERE THE BOXES PLAY FROM"));
        row(Hindi.t("Music box"), Hindi.t("Online radio, music files or a music app"), false, new Runnable() {
            @Override
            public void run() {
                Browse.menu(a, false, h.music, changed);
            }
        }, "›", null);
        row(Hindi.t("Radio box"), Hindi.t("FM, online radio or a radio app"), false, new Runnable() {
            @Override
            public void run() {
                Browse.menu(a, true, h.radio, changed);
            }
        }, "›", null);
        if ("app".equals(n.kind) && n.media != null && n.media.controller != null) {
            final MediaController c = n.media.controller;
            java.util.List<android.media.session.MediaSession.QueueItem> queue = c.getQueue();
            if (queue != null && !queue.isEmpty()) row(Hindi.t("Up next"), n.name, false, new Runnable() {
                @Override
                public void run() {
                    Browse.queue(a, c);
                }
            }, "›", null);
        }

        heading(Hindi.t("MUSIC APPS"));
        final android.content.pm.PackageManager pm = a.getPackageManager();
        List<String> browsable = Browse.browsable(pm);
        List<String> apps = new ArrayList<>(h.sessionPkgs);
        for (String s : browsable) if (!apps.contains(s)) apps.add(s);
        apps.remove(a.getPackageName());
        List<String> rows = Browse.trim(apps, moving, Browse.MOVING_ROWS);
        for (final String pkg : rows) {
            final boolean radio = Hub.looksLikeRadio(pkg);
            final boolean lib = browsable.contains(pkg);
            Hub.Media m = h.music != null && h.music.controller != null && pkg.equals(h.music.controller.getPackageName()) ? h.music
                    : h.radio != null && h.radio.controller != null && pkg.equals(h.radio.controller.getPackageName()) ? h.radio : null;
            String line = m != null && m.title != null ? m.playing ? Hindi.f("Playing: %1$s", m.title) : Hindi.f("Paused: %1$s", m.title)
                    : Hindi.t(lib ? "Tap to choose from its library" : "Tap to open");
            row(Browse.label(pm, pkg), line, m != null && m.playing, new Runnable() {
                @Override
                public void run() {
                    String key = radio ? "media:radio" : "media:music";
                    p.setApp(key, pkg);   // the box follows this app now
                    p.setSource(radio ? "radio" : "music", "app");
                    a.refresh();
                    if (lib) Browse.open(a, pkg);
                    else launch(pkg);
                }
            }, Hindi.t("Open"), new Runnable() {
                @Override
                public void run() {
                    launch(pkg);
                }
            });
        }
        if (apps.isEmpty()) note(Hindi.t("No music apps found. Allow notification access for OpenRath to see what other apps play."));
        more(apps.size(), rows.size());
    }

    private void launch(String pkg) {
        if (ChildLock.blocked(p, pkg)) {
            Toast.makeText(a, Hindi.t(ChildLock.SORRY), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = Compat.launch(a.getPackageManager(), pkg);
        if (i == null) return;
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Diag.note("open music app", e);
        }
    }

    /** Fill OpenRath's player's list of stations or songs without playing, for the lists here. */
    private void load(final boolean web) {
        if (web ? loadingStations : loadingSongs) return;
        if (web) {
            loadingStations = true;
            triedStations = true;
        } else {
            loadingSongs = true;
            triedSongs = true;
        }
        final Context c = getContext().getApplicationContext();
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final List<Player.Track> l = web ? Player.fetchStations(c) : Player.scanSongs(c);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (web) loadingStations = false;
                        else loadingSongs = false;
                        List<Player.Track> into = web ? Player.stations : Player.songs;
                        // the player may have filled it meanwhile, and its indexes must stay right
                        if (into.isEmpty()) into.addAll(l);
                        refresh(true);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ pieces

    private void heading(String s) {
        TextView t = Ui.label(getContext(), s);
        t.setPadding(Ui.px(4), Ui.px(18), 0, Ui.px(8));
        list.addView(t);
    }

    private void note(String s) {
        TextView t = Ui.text(getContext(), 15, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        t.setSingleLine(false);
        t.setText(s);
        t.setPadding(Ui.px(4), Ui.px(10), 0, Ui.px(10));
        list.addView(t);
    }

    /** Rows left out while moving: say there are more when stopped. */
    private void more(int all, int shownRows) {
        if (all > shownRows) note(Hindi.f("%1$s more when the car is stopped", all - shownRows));
    }

    private void addRow(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(8);
        list.addView(v, lp);
    }

    private LinearLayout.LayoutParams spacer(int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
        lp.leftMargin = Ui.px(8);
        return lp;
    }

    /** One list row: two lines, lit when it is what plays, with an optional button on the right. */
    private void row(String head, String line, boolean lit, final Runnable tap, String button, final Runnable buttonTap) {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.px(HomeDock.TARGET));
        r.setPadding(Ui.px(18), Ui.px(8), Ui.px(8), Ui.px(8));
        r.setBackground(pill(lit));
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(getContext(), 19, lit ? Ui.SILVER : Ui.mix(Ui.SILVER, Ui.SILVER_DIM, 0.85f), "sans-serif", Gravity.LEFT);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(head);
        col.addView(t);
        if (line != null && !line.isEmpty()) {
            TextView s = Ui.text(getContext(), 13, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
            s.setEllipsize(TextUtils.TruncateAt.END);
            s.setText(line);
            s.setPadding(0, Ui.px(4), 0, 0);
            col.addView(s);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (button != null) {
            TextView b = Ui.text(getContext(), button.length() <= 1 ? 26 : 15, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
            b.setText(button);
            if (buttonTap != null) {
                b.setPadding(Ui.px(14), 0, Ui.px(14), 0);
                tap(b, buttonTap);
                r.addView(b, new LinearLayout.LayoutParams(-2, Ui.px(HomeDock.TARGET - 12)));
                b.setMinWidth(Ui.px(HomeDock.TARGET));
            } else {
                b.setTextColor(Ui.SILVER_DIM);
                r.addView(b, new LinearLayout.LayoutParams(Ui.px(40), -2));
            }
        }
        tap(r, tap);
        addRow(r);
    }

    private void tap(View v, final Runnable r) {
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View x) {
                Tap.feedback(x, p);
                r.run();
            }
        });
    }

    private static ImageView button(Context c, String img) {
        ImageView b = new ImageView(c);
        b.setImageBitmap(Ui.img(c, img));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setBackground(pill(false));
        return b;
    }

    private static TextView chip(Context c, String s, boolean on) {
        TextView t = Ui.text(c, 16, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
        t.setText(s);
        t.setBackground(pill(on));
        return t;
    }

    static GradientDrawable pill(boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * Ui.k);
        g.setColor(on ? Ui.PANEL_TOP : Ui.PANEL);
        g.setStroke(Math.max(1, Ui.px(on ? 1.6f : 1.2f)), on ? Ui.ACCENT : Ui.mix(Ui.EDGE, Ui.PANEL, 0.6f));
        return g;
    }

    private void hideKeyboard() {
        try {
            ((android.view.inputmethod.InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
        } catch (Throwable ignored) {
            // no keyboard up
        }
    }

    /** The album cover, or the station's or app's initials on a soft colour (a note when nothing plays). */
    static final class Cover extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private final Rect src = new Rect();
        private final Path clip = new Path();
        private Bitmap art;
        private String name = "";

        Cover(Context c) {
            super(c);
        }

        void set(Bitmap b, String n) {
            if (b == art && n.equals(name)) return;
            art = b;
            name = n;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = Ui.RADIUS * Ui.k;
            r.set(0, 0, w, h);
            c.save();
            clip.reset();
            clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.clipPath(clip);
            if (art != null) {
                // fill the square, cropping the longer side
                int bw = art.getWidth(), bh = art.getHeight(), s = Math.min(bw, bh);
                src.set((bw - s) / 2, (bh - s) / 2, (bw + s) / 2, (bh + s) / 2);
                c.drawBitmap(art, src, r, paint);
                c.restore();
                return;
            }
            String ini = LayoutView.initials(name);
            paint.setColor(ini.isEmpty() ? 0xFF5A6270 : LayoutView.SOFT[(ini.hashCode() & 0x7FFFFFFF) % LayoutView.SOFT.length]);
            c.drawRect(r, paint);
            c.restore();
            paint.setColor(0xFFFFFFFF);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            String t = ini.isEmpty() ? "♪" : ini;
            paint.setTextSize(Math.min(w, h) * (t.length() > 1 ? 0.36f : 0.44f));
            Paint.FontMetrics fm = paint.getFontMetrics();
            c.drawText(t, w / 2, h / 2 - (fm.ascent + fm.descent) / 2, paint);
        }
    }

    /** A bar with a line of text under it: the time in the song (drag to move in it) or the volume. */
    static final class Bar extends View {
        interface Seek {
            void to(float f);
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Seek seek;
        private float frac, drag = -1;
        private String left = "", right = "";

        Bar(Context c) {
            super(c);
        }

        void show(float f, String l, String r) {
            if (f == frac && l.equals(left) && r.equals(right)) return;
            frac = f;
            left = l;
            right = r;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (seek == null || getWidth() == 0) return false;
            float f = Math.max(0, Math.min(1, e.getX() / getWidth()));
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                drag = f;
                invalidate();
            } else if (act == MotionEvent.ACTION_UP) {
                drag = -1;
                seek.to(f);
                invalidate();
            } else if (act == MotionEvent.ACTION_CANCEL) {
                drag = -1;
                invalidate();
            }
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), y = getHeight() * 0.3f, th = Math.max(3, getHeight() * 0.1f), f = drag >= 0 ? drag : frac;
            float pad = th * 2;
            paint.setColor(Ui.mix(Ui.SILVER_DIM, Ui.PANEL, 0.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(th);
            c.drawLine(pad, y, w - pad, y, paint);
            paint.setColor(Ui.ACCENT == Ui.SILVER_DIM ? Ui.SILVER : Ui.ACCENT);
            c.drawLine(pad, y, pad + (w - 2 * pad) * f, y, paint);
            if (seek != null) c.drawCircle(pad + (w - 2 * pad) * f, y, th * (drag >= 0 ? 2.6f : 1.7f), paint);
            paint.setTextSize(Math.max(Ui.MIN_TEXT, 13) * Ui.k);
            paint.setColor(Ui.SILVER_DIM);
            paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(left, pad, getHeight() - 2, paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            c.drawText(right, w - pad, getHeight() - 2, paint);
        }
    }
}
