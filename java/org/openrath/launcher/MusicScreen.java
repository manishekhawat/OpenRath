package org.openrath.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
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
 * dock's music and radio buttons, "open music" by voice). On the left, what plays now, on a backdrop
 * taken from the cover: the cover, the song or station, where it comes from, the time bar for music
 * files and apps (or "live" for stations), big previous, play / pause and next buttons, and the
 * volume. On the right three lists: Radio (FM through the stereo with seek, tune and presets, and the
 * online stations with favourites), Music (the music files by song, artist or folder, with a search
 * while parked) and Apps (other music apps, their libraries and queues through Browse). While moving,
 * lists stop at Browse.MOVING_ROWS and the search can't be typed in. The look is drawn to match the
 * design mock in screens/music-radio (one design px there is one Ui.px here).
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
    /** The cover inside the music file playing now (read once per song, off the main thread). */
    private static String artSrc;
    private static Bitmap artBmp;
    private static boolean artLoading;

    interface Closed {
        void closed();
    }

    private final HomeActivity a;
    private final Prefs p;
    private final Closed closed;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // what plays now
    private final Backdrop backdrop;
    private final Cover cover;
    private final TextView source, title, sub;
    private final Bar bar, volume;
    private final Key prev, play, next;
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

    /** Pill looks: a faint glass fill, a dark field, lit (what plays or is picked) and filled with the accent. */
    static final int SOFT = 0, FIELD = 1, LIT = 2, INK = 3;

    MusicScreen(HomeActivity act, String start, Closed c) {
        super(act);
        a = act;
        p = act.prefs;
        closed = c;
        if (start != null) tab = start;
        setBackgroundColor(Ui.BACKGROUND);
        setClickable(true);   // nothing reaches the dashboard underneath
        setFocusableInTouchMode(true);   // the screen holds the focus, so the search doesn't open the keyboard by itself
        int m = Ui.px(20);
        android.util.DisplayMetrics dm = act.getResources().getDisplayMetrics();
        boolean tall = Ui.portrait(dm.widthPixels, dm.heightPixels);

        backdrop = new Backdrop(act);
        addView(backdrop, new LayoutParams(-1, -1));

        LinearLayout all = new LinearLayout(act);
        all.setOrientation(tall ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        all.setPadding(m, m, m, m);
        addView(all, new LayoutParams(-1, -1));

        // ---- left: what plays now
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView back = Ui.text(act, 16, Ui.SILVER, "sans-serif-medium", Gravity.LEFT | Gravity.CENTER_VERTICAL);
        back.setText(Hindi.t("‹  Back to the dashboard"));
        back.setPadding(Ui.px(20), 0, Ui.px(20), 0);
        back.setBackground(pressable(Glass.on ? shape(Ui.PANEL_TOP, Ui.EDGE, 28) : shape(alpha(Ui.PANEL, 0.72f), alpha(Ui.EDGE, 0.55f), 28), 28));
        tap(back, new Runnable() {
            @Override
            public void run() {
                closed.closed();
            }
        });
        left.addView(back, new LinearLayout.LayoutParams(-2, Ui.px(56)));
        if (!tall) left.addView(new View(act), new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout top = new LinearLayout(act);
        top.setGravity(Gravity.CENTER_VERTICAL);
        cover = new Cover(act);
        int cs = Ui.px(tall ? 150 : 196);
        top.addView(cover, new LinearLayout.LayoutParams(cs, cs));
        LinearLayout words = new LinearLayout(act);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.px(22), 0, 0, 0);
        source = Ui.label(act, "");
        source.setEllipsize(TextUtils.TruncateAt.END);
        title = Ui.text(act, 28, Ui.SILVER, "sans-serif-medium", Gravity.LEFT);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.px(10), 0, 0);
        sub = Ui.text(act, 17, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setPadding(0, Ui.px(8), 0, 0);
        words.addView(source);
        words.addView(title);
        words.addView(sub);
        top.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        if (tall) tp.topMargin = Ui.px(16);
        left.addView(top, tp);

        bar = new Bar(act, false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.px(40));
        bp.topMargin = Ui.px(24);
        left.addView(bar, bp);

        LinearLayout buttons = new LinearLayout(act);
        buttons.setGravity(Gravity.CENTER);
        int touch = Ui.px(tall ? 68 : 76), big = Ui.px(tall ? 88 : 100);
        prev = new Key(act, "prev", false, 28);
        play = new Key(act, "play", true, 38);
        next = new Key(act, "next", false, 28);
        for (Key b : new Key[]{prev, play, next}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(b == play ? big : touch, b == play ? big : touch);
            lp.leftMargin = lp.rightMargin = Ui.px(15);
            buttons.addView(b, lp);
        }
        prev.setContentDescription(Hindi.t("Previous"));
        next.setContentDescription(Hindi.t("Next"));
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
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(-1, -2);
        kp.topMargin = Ui.px(14);
        left.addView(buttons, kp);

        LinearLayout vol = new LinearLayout(act);
        vol.setGravity(Gravity.CENTER_VERTICAL);
        Key down = new Key(act, "minus", false, 26), up = new Key(act, "plus", false, 26);
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
        volume = new Bar(act, true);
        volume.setContentDescription(Hindi.t("Volume"));
        vol.addView(down, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
        vp.leftMargin = vp.rightMargin = Ui.px(14);
        vol.addView(volume, vp);
        vol.addView(up, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        LinearLayout.LayoutParams volp = new LinearLayout.LayoutParams(-1, -2);
        volp.topMargin = Ui.px(18);
        left.addView(vol, volp);
        if (!tall) left.addView(new View(act), new LinearLayout.LayoutParams(-1, 0, 1f));

        if (tall) all.addView(left, new LinearLayout.LayoutParams(-1, -2));
        else all.addView(left, new LinearLayout.LayoutParams(0, -1, 0.42f));

        // ---- right: the lists
        LinearLayout right = new LinearLayout(act);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tabRow = new LinearLayout(act);
        tabRow.setBackground(shape(Ui.PANEL, line(), 32));
        int tpad = Ui.px(5);
        tabRow.setPadding(tpad, tpad, tpad, tpad);
        for (int i = 0; i < TABS.length; i++) {
            final String t = TABS[i];
            tabs[i] = Ui.text(act, 18, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
            tabs[i].setText(Hindi.t(TAB_NAMES[i]));
            tabRow.addView(tabs[i], new LinearLayout.LayoutParams(0, -1, 1f));
            tap(tabs[i], new Runnable() {
                @Override
                public void run() {
                    tab = t;
                    scroll.scrollTo(0, 0);
                    refresh(true);
                }
            });
        }
        right.addView(tabRow, new LinearLayout.LayoutParams(-1, Ui.px(64)));

        tools = new LinearLayout(act);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < VIEWS.length; i++) {
            final String v = VIEWS[i];
            views[i] = pill(act, Hindi.t(VIEW_NAMES[i]), FIELD, 56);
            views[i].setPadding(Ui.px(20), 0, Ui.px(20), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.px(56));
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
        search.setPadding(Ui.px(18), 0, Ui.px(18), 0);
        search.setBackground(shape(Ui.PANEL, line(), 28));
        search.setCompoundDrawables(new Glyph("search", Ui.SILVER_DIM, 20), null, null, null);
        search.setCompoundDrawablePadding(Ui.px(10));
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
        tools.addView(search, new LinearLayout.LayoutParams(0, Ui.px(56), 1f));
        parkedNote = Ui.text(act, 13, Ui.SILVER_DIM, "sans-serif", Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        parkedNote.setText(Hindi.t("Search when the car is stopped"));
        tools.addView(parkedNote, new LinearLayout.LayoutParams(0, Ui.px(56), 1f));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, Ui.px(60));
        tlp.topMargin = Ui.px(14);
        right.addView(tools, tlp);

        scroll = new ScrollView(act);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, m);
        scroll.addView(list);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1f);
        sp.topMargin = Ui.px(14);
        right.addView(scroll, sp);
        LinearLayout.LayoutParams rlp = tall ? new LinearLayout.LayoutParams(-1, 0, 1f) : new LinearLayout.LayoutParams(0, -1, 0.58f);
        if (tall) rlp.topMargin = Ui.px(20);
        else rlp.leftMargin = Ui.px(28);
        all.addView(right, rlp);

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
        /** playing: it plays (not paused); live: a station, with no time bar. */
        boolean playing, live;
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
            n.live = web;
            if (!web) n.art = songArt(t);
        } else if ("fm".equals(kind)) {
            Unit.Show sh = u.fmShow(p, fmNote, fmOn);
            n.title = sh.title;
            n.sub = sh.sub;
            n.name = sh.title;
            n.from = Hindi.t("FM RADIO");
            n.playing = sh.playing != null ? sh.playing : fmOn;
            n.live = true;
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
            n.live = radio;
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
        String name = "none".equals(n.kind) ? "" : n.name;
        cover.set(n.art, name);
        backdrop.set(n.art, name.isEmpty() ? 0 : soft(name));
        play.set(n.playing ? "pause" : "play");
        play.setContentDescription(Hindi.t(n.playing ? "Pause" : "Play"));
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
        boolean live = len <= 0 && n.live;
        bar.setVisibility(len > 0 || live ? VISIBLE : INVISIBLE);
        bar.live(live ? Hindi.t("LIVE") : null);
        if (len > 0) bar.show(at / (float) len, ArtColour.clock(at), ArtColour.clock(len));
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        int v = am.getStreamVolume(AudioManager.STREAM_MUSIC), max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume.show(MusicLists.share(v, max), "", String.valueOf(v));
    }

    /** The cover inside a music file, once read; null meanwhile, or when it has none. */
    private Bitmap songArt(Player.Track t) {
        if (t == null || t.src == null || t.src.isEmpty()) return null;
        if (t.src.equals(artSrc)) return artBmp;
        if (artLoading) return null;
        artLoading = true;
        final String src = t.src;
        final Context c = getContext().getApplicationContext();
        Bg.run(new Runnable() {
            @Override
            public void run() {
                Bitmap b = null;
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                try {
                    if (src.startsWith("content:")) r.setDataSource(c, android.net.Uri.parse(src));
                    else r.setDataSource(src);
                    byte[] pic = r.getEmbeddedPicture();
                    if (pic != null) {
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
                        o.inSampleSize = 1;
                        while (Math.min(o.outWidth, o.outHeight) / (o.inSampleSize * 2) >= 360) o.inSampleSize *= 2;
                        o.inJustDecodeBounds = false;
                        b = BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
                    }
                } catch (Throwable e) {
                    b = null;   // no cover, or a file it can't read: the initials stay
                } finally {
                    try {
                        r.release();
                    } catch (Throwable ignored) {
                        // already let go
                    }
                }
                final Bitmap got = b;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        artSrc = src;
                        artBmp = got;
                        artLoading = false;
                        refresh(false);
                    }
                });
            }
        });
        return null;
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
        for (int i = 0; i < TABS.length; i++) {
            boolean on = TABS[i].equals(tab);
            tabs[i].setBackground(on ? shape(ink(), 0, 27) : pressable(null, 27));
            tabs[i].setTextColor(on ? MusicLists.inkOn(ink()) : Ui.mix(Ui.SILVER, Ui.SILVER_DIM, 0.6f));
        }
        boolean music = MUSIC.equals(tab);
        tools.setVisibility(music ? VISIBLE : GONE);
        for (int i = 0; i < VIEWS.length; i++) style(views[i], VIEWS[i].equals(view) ? LIT : FIELD, 56);
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
            Context ctx = getContext();
            LinearLayout panel = new LinearLayout(ctx);
            panel.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Ui.PANEL_TOP, Ui.PANEL});
            card.setCornerRadius(18 * Ui.k);
            card.setStroke(Math.max(1, Ui.px(1.2f)), line());
            panel.setBackground(card);
            panel.setPadding(Ui.px(18), Ui.px(16), Ui.px(18), Ui.px(18));

            // the station in big figures, then save it and play or stop FM
            LinearLayout row1 = new LinearLayout(ctx);
            row1.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout fig = new LinearLayout(ctx);   // the figures and "MHz" on one baseline
            TextView big = Ui.text(ctx, 54, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
            big.setText(now);
            fig.addView(big, new LinearLayout.LayoutParams(-2, -2));
            TextView mhz = Ui.text(ctx, 15, Ui.SILVER_DIM, "sans-serif-medium", Gravity.LEFT);
            mhz.setLetterSpacing(0.1f);
            mhz.setText("MHz");
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
            mp.leftMargin = Ui.px(12);
            fig.addView(mhz, mp);
            row1.addView(fig, new LinearLayout.LayoutParams(-2, -2));
            row1.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
            final List<String> mine = MusicLists.items(p.str("fmPresets", ""), ",");
            final boolean saved = mine.contains(now);
            TextView save = pill(ctx, Hindi.t(saved ? "★  Saved" : "☆  Save station"), saved ? LIT : SOFT, 64);
            tap(save, new Runnable() {
                @Override
                public void run() {
                    p.put("fmPresets", MusicLists.toggle(p.str("fmPresets", ""), now, ","));
                    refresh(true);
                }
            });
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, Ui.px(64));
            slp.leftMargin = Ui.px(10);
            row1.addView(save, slp);
            final boolean playing = fm && n.playing;
            TextView start = pill(ctx, Hindi.t(playing ? "Stop FM" : "Play FM"), playing ? SOFT : INK, 64);
            start.setCompoundDrawables(new Glyph(playing ? "stop" : "play", start.getCurrentTextColor(), 18), null, null, null);
            start.setCompoundDrawablePadding(Ui.px(10));
            tap(start, new Runnable() {
                @Override
                public void run() {
                    fm(!playing);
                    later();
                }
            });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, Ui.px(64));
            plp.leftMargin = Ui.px(10);
            row1.addView(start, plp);
            panel.addView(row1, new LinearLayout.LayoutParams(-1, Ui.px(64)));

            // seek and tune a step, and scan where the unit can
            LinearLayout row = new LinearLayout(ctx);
            String[] names = {"Seek down", "−", "+", "Seek up"};
            for (int i = 0; i < names.length; i++) {
                final int k = i;
                boolean step = i == 1 || i == 2;
                TextView b = pill(ctx, step ? names[i] : Hindi.t(names[i]), SOFT, 64);
                b.setPadding(0, 0, 0, 0);
                if (step) {
                    b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 26 * Ui.k);
                    b.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(64), step ? 0.62f : 1f);
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
            if (u.canScan(p)) {
                TextView scan = pill(ctx, Hindi.t("Scan"), SOFT, 64);
                scan.setPadding(0, 0, 0, 0);
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
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, Ui.px(64), 0.62f);
                clp.leftMargin = Ui.px(8);
                row.addView(scan, clp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.px(12);
            panel.addView(row, rlp);
            list.addView(panel, new LinearLayout.LayoutParams(-1, -2));

            // the presets: saved by hand and found by a scan, as round buttons five to a line
            List<String> scanned = Scan.saved(p);
            if (scanned.isEmpty()) scanned = StockRadio.stations();
            List<String> presets = Browse.trim(MusicLists.presets(p.str("fmPresets", ""), scanned), moving, Browse.MOVING_ROWS);
            if (presets.isEmpty()) note(Hindi.t("Save a station, or scan, to keep it here"));
            LinearLayout line = null;
            for (int i = 0; i < presets.size(); i++) {
                final String f = presets.get(i);
                if (i % PER_LINE == 0) {
                    line = new LinearLayout(ctx);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.topMargin = Ui.px(i == 0 ? 10 : 8);
                    list.addView(line, lp);
                }
                TextView b = pill(ctx, f, fm && f.equals(now) ? LIT : FIELD, 64);
                b.setPadding(0, 0, 0, 0);
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
                if (i % PER_LINE > 0) lp.leftMargin = Ui.px(8);
                line.addView(b, lp);
            }
            if (line != null) for (int i = presets.size() % PER_LINE; i > 0 && i < PER_LINE; i++) line.addView(new View(ctx), spacer(i));
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
            row(Tile.initials(getContext(), t.name), t.name, t.sub, Player.WEB.equals(Player.mode) && Player.station == i, new Runnable() {
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
            row(artists ? Tile.initials(getContext(), g) : Tile.glyph(getContext(), "folder", false),
                    g.isEmpty() ? Hindi.t(artists ? "Unknown artist" : "Other files") : g,
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
            boolean lit = Player.FILES.equals(Player.mode) && Player.song == i;
            row(Tile.glyph(getContext(), lit ? "eq" : "note", lit), t.name, t.sub, lit, new Runnable() {
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
            row(Tile.icon(getContext(), pm, pkg), Browse.label(pm, pkg), line, m != null && m.playing, new Runnable() {
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

    /** Five FM presets to a line. */
    static final int PER_LINE = 5;

    private void heading(String s) {
        TextView t = Ui.label(getContext(), s);
        t.setPadding(Ui.px(4), Ui.px(list.getChildCount() == 0 ? 4 : 18), 0, Ui.px(10));
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
        lp.topMargin = Ui.px(4);
        list.addView(v, lp);
    }

    private LinearLayout.LayoutParams spacer(int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
        lp.leftMargin = Ui.px(8);
        return lp;
    }

    private void row(String head, String line, boolean lit, Runnable tap, String button, Runnable buttonTap) {
        row(null, head, line, lit, tap, button, buttonTap);
    }

    /**
     * One list row: a picture (a station's initials, an app's icon or a note), two lines, lit when it
     * is what plays (with the playing mark), and an optional button on the right: a star, "›" or a word.
     */
    private void row(View lead, String head, String line, boolean lit, final Runnable tap, String button, final Runnable buttonTap) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.px(HomeDock.TARGET));
        r.setPadding(Ui.px(lead == null ? 18 : 10), Ui.px(6), Ui.px(6), Ui.px(6));
        r.setBackground(pressable(lit ? shape(Ui.mix(ink(), Ui.PANEL, 0.13f), 0, 16) : null, 16));
        if (lead != null) r.addView(lead, new LinearLayout.LayoutParams(Ui.px(56), Ui.px(56)));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(lead == null ? 0 : Ui.px(16), 0, Ui.px(8), 0);
        TextView t = Ui.text(c, 18, lit ? litText() : Ui.mix(Ui.SILVER, Ui.SILVER_DIM, 0.85f), "sans-serif-medium", Gravity.LEFT);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(head);
        col.addView(t);
        if (line != null && !line.isEmpty()) {
            TextView s = Ui.text(c, 14, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
            s.setEllipsize(TextUtils.TruncateAt.END);
            s.setText(line);
            s.setPadding(0, Ui.px(5), 0, 0);
            col.addView(s);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        // the playing mark, unless the picture already is one
        if (lit && !(lead instanceof Tile && "eq".equals(((Tile) lead).glyph))) {
            r.addView(new Mark(c, "eq", ink(), 22), new LinearLayout.LayoutParams(Ui.px(40), Ui.px(40)));
        }
        if (button != null) {
            boolean mark = button.length() <= 1;
            TextView b = Ui.text(c, mark ? 26 : 15, "★".equals(button) ? ink() : mark ? Ui.SILVER_DIM : Ui.SILVER,
                    "sans-serif-medium", Gravity.CENTER);
            b.setText(button);
            if (buttonTap != null) {
                if (mark) b.setBackground(pressable(null, 32));
                else {
                    b.setPadding(Ui.px(20), 0, Ui.px(20), 0);
                    int in = Ui.px(6);
                    b.setBackground(pressable(new InsetDrawable(shape(alpha(Ui.SILVER, 0.07f), alpha(Ui.SILVER, 0.12f), 26), 0, in, 0, in), 26));
                }
                b.setMinWidth(Ui.px(64));
                tap(b, buttonTap);
                r.addView(b, new LinearLayout.LayoutParams(mark ? Ui.px(64) : -2, Ui.px(64)));
            } else {
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

    /** The highlight colour: the theme's accent, or silver where the accent is the plain grey of the labels. */
    static int ink() {
        return Ui.ACCENT == Ui.SILVER_DIM ? Ui.SILVER : Ui.ACCENT;
    }

    /** The name on a lit row: white on the silver look, else the accent. */
    static int litText() {
        return ink() == Ui.SILVER ? 0xFFFFFFFF : ink();
    }

    /** The thin edge round fields and cards. */
    static int line() {
        return Ui.mix(Ui.EDGE, Ui.PANEL, 0.45f);
    }

    /** A colour at `a` (0 to 1) opacity. */
    static int alpha(int c, float a) {
        return Math.round(Math.max(0, Math.min(1, a)) * 255) << 24 | (c & 0xFFFFFF);
    }

    /** The soft colour a station or artist's initials sit on, as on the dashboard's boxes. */
    static int soft(String name) {
        String ini = LayoutView.initials(name);
        return ini.isEmpty() ? 0xFF5A6270 : LayoutView.SOFT[(ini.hashCode() & 0x7FFFFFFF) % LayoutView.SOFT.length];
    }

    /** A rounded shape, `radius` in design px; no edge when `stroke` is 0. */
    static GradientDrawable shape(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radius * Ui.k);
        g.setColor(fill);
        if (stroke != 0) g.setStroke(Math.max(1, Ui.px(1.2f)), stroke);
        return g;
    }

    /** `normal` (or nothing), with a light wash over it while pressed. */
    static Drawable pressable(Drawable normal, float radius) {
        StateListDrawable s = new StateListDrawable();
        Drawable wash = shape(alpha(Ui.SILVER, 0.16f), 0, radius);
        Drawable.ConstantState cs = normal == null ? null : normal.getConstantState();
        s.addState(new int[]{android.R.attr.state_pressed},
                cs == null ? wash : new android.graphics.drawable.LayerDrawable(new Drawable[]{cs.newDrawable().mutate(), wash}));
        s.addState(new int[0], normal != null ? normal : new ColorDrawable(0));
        return s;
    }

    /** A round-ended button `h` design px high, in one of the looks SOFT, FIELD, LIT or INK. */
    private static TextView pill(Context c, String s, int look, float h) {
        TextView t = Ui.text(c, 16, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
        t.setText(s);
        t.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        style(t, look, h);
        return t;
    }

    static void style(TextView t, int look, float h) {
        float r = h / 2;
        int k = ink();
        Drawable d;
        if (look == INK) d = shape(k, 0, r);
        else if (look == LIT) {
            GradientDrawable g = shape(Ui.mix(k, Ui.PANEL, 0.18f), 0, r);
            g.setStroke(Math.max(1, Ui.px(1.6f)), k);
            d = g;
        } else if (look == FIELD) d = shape(Ui.PANEL, line(), r);
        else d = shape(alpha(Ui.SILVER, 0.07f), alpha(Ui.SILVER, 0.12f), r);
        t.setBackground(pressable(d, r));
        t.setTextColor(look == INK ? MusicLists.inkOn(k) : Ui.SILVER);
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

    // ------------------------------------------------------------------ drawing

    private static final Path GP = new Path();
    private static final RectF GR = new RectF();

    /**
     * One of the screen's small pictures, drawn in a 24-unit box `size` px wide centred on cx, cy:
     * play, pause, stop, prev, next, eq (the playing mark), note, folder, speaker, search, minus, plus.
     */
    static void glyph(Canvas c, Paint p, String kind, float cx, float cy, float size, int colour) {
        c.save();
        c.translate(cx - size / 2, cy - size / 2);
        c.scale(size / 24f, size / 24f);
        p.setShader(null);
        p.setColor(colour);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        if ("play".equals(kind)) tri(c, p, 7, 4.2f, 20.2f, 12, 7, 19.8f);
        else if ("pause".equals(kind)) {
            rr(c, p, 5.5f, 4, 10.1f, 20, 1.4f);
            rr(c, p, 13.9f, 4, 18.5f, 20, 1.4f);
        } else if ("stop".equals(kind)) rr(c, p, 6, 6, 18, 18, 2);
        else if ("prev".equals(kind)) {
            rr(c, p, 3, 4, 6, 20, 1.2f);
            tri(c, p, 19.6f, 4.8f, 8.4f, 12, 19.6f, 19.2f);
        } else if ("next".equals(kind)) {
            rr(c, p, 18, 4, 21, 20, 1.2f);
            tri(c, p, 4.4f, 4.8f, 15.6f, 12, 4.4f, 19.2f);
        } else if ("eq".equals(kind)) {
            rr(c, p, 3, 9, 7, 21, 1.3f);
            rr(c, p, 10, 3, 14, 21, 1.3f);
            rr(c, p, 17, 7, 21, 21, 1.3f);
        } else if ("note".equals(kind)) {
            c.drawCircle(6.5f, 17.5f, 3.2f, p);
            c.drawCircle(16.5f, 15.5f, 3.2f, p);
            c.drawRect(8.1f, 5.5f, 9.7f, 17.5f, p);
            c.drawRect(18.1f, 3.5f, 19.7f, 15.5f, p);
            GP.reset();
            GP.moveTo(8.1f, 5.5f);
            GP.lineTo(19.7f, 3);
            GP.lineTo(19.7f, 6.4f);
            GP.lineTo(8.1f, 8.9f);
            GP.close();
            c.drawPath(GP, p);
        } else if ("folder".equals(kind)) {
            rr(c, p, 2.5f, 4.5f, 10.5f, 9, 1.5f);
            rr(c, p, 2.5f, 6.5f, 21.5f, 19.5f, 2);
        } else if ("speaker".equals(kind)) {
            GP.reset();
            GP.moveTo(3, 9.5f);
            GP.lineTo(7, 9.5f);
            GP.lineTo(12, 5);
            GP.lineTo(12, 19);
            GP.lineTo(7, 14.5f);
            GP.lineTo(3, 14.5f);
            GP.close();
            c.drawPath(GP, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            GR.set(7, 7, 17, 17);
            c.drawArc(GR, -45, 90, false, p);
            GR.set(3.5f, 3.5f, 20.5f, 20.5f);
            c.drawArc(GR, -45, 90, false, p);
        } else if ("search".equals(kind)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.4f);
            c.drawCircle(10.5f, 10.5f, 6.5f, p);
            c.drawLine(15.5f, 15.5f, 21, 21, p);
        } else if ("minus".equals(kind) || "plus".equals(kind)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f);
            c.drawLine(5, 12, 19, 12, p);
            if ("plus".equals(kind)) c.drawLine(12, 5, 12, 19, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.restore();
    }

    private static void rr(Canvas c, Paint p, float l, float t, float r, float b, float rad) {
        GR.set(l, t, r, b);
        c.drawRoundRect(GR, rad, rad, p);
    }

    /** A triangle with softly rounded corners. */
    private static void tri(Canvas c, Paint p, float x1, float y1, float x2, float y2, float x3, float y3) {
        GP.reset();
        GP.moveTo(x1, y1);
        GP.lineTo(x2, y2);
        GP.lineTo(x3, y3);
        GP.close();
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(1.6f);
        c.drawPath(GP, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** A glyph as a drawable, `size` design px square: the search's magnifier, Play FM's triangle. */
    static final class Glyph extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String kind;
        private final int colour;

        Glyph(String kind, int colour, float size) {
            this.kind = kind;
            this.colour = colour;
            setBounds(0, 0, Ui.px(size), Ui.px(size));
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            glyph(c, paint, kind, b.exactCenterX(), b.exactCenterY(), b.width(), colour);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A glyph on its own, centred: the playing mark on a lit row. */
    static final class Mark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String kind;
        private final int colour;
        private final float size;

        Mark(Context c, String kind, int colour, float size) {
            super(c);
            this.kind = kind;
            this.colour = colour;
            this.size = size;
        }

        @Override
        protected void onDraw(Canvas c) {
            glyph(c, paint, kind, getWidth() / 2f, getHeight() / 2f, size * Ui.k, colour);
        }
    }

    /**
     * A round button with a glyph: the big play / pause filled with the accent, the others in a faint
     * glass. A theme pack's own btn_play.png (and pause, prev, next) is drawn instead when it has one.
     */
    static final class Key extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private final boolean filled;
        private final float size;
        private String kind = "";
        private Bitmap themed;

        Key(Context c, String kind, boolean filled, float glyphSize) {
            super(c);
            this.filled = filled;
            size = glyphSize;
            set(kind);
            setClickable(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            if (filled) setElevation(Ui.px(8));
        }

        void set(String k) {
            if (k.equals(kind)) return;
            kind = k;
            themed = "minus".equals(k) || "plus".equals(k) ? null : Theme.image("btn_" + k);
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = Math.min(w, h) / 2f, cx = w / 2f, cy = h / 2f;
            int k = ink();
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(filled ? k : alpha(Ui.SILVER, 0.08f));
            c.drawCircle(cx, cy, rad, paint);
            if (!filled) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1, Ui.px(1.2f)));
                paint.setColor(alpha(Ui.SILVER, 0.10f));
                c.drawCircle(cx, cy, rad - paint.getStrokeWidth() / 2, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (isPressed()) {
                paint.setColor(filled ? alpha(0xFF000000, 0.18f) : alpha(Ui.SILVER, 0.14f));
                c.drawCircle(cx, cy, rad, paint);
            }
            if (themed != null) {
                float s = rad * 1.2f, bw = themed.getWidth(), bh = themed.getHeight(), f = s / Math.max(bw, bh);
                r.set(cx - bw * f / 2, cy - bh * f / 2, cx + bw * f / 2, cy + bh * f / 2);
                c.drawBitmap(themed, null, r, paint);
                return;
            }
            glyph(c, paint, kind, cx, cy, size * Ui.k, filled ? MusicLists.inkOn(k) : Ui.ICONS != 0 ? Ui.ICONS : Ui.SILVER);
        }
    }

    /**
     * Behind the screen: the cover, blurred and faded into the background towards the lists, or for a
     * station without one a soft glow in its badge's colour.
     */
    static final class Backdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF r = new RectF();
        private Bitmap art, small;
        private int colour;

        Backdrop(Context c) {
            super(c);
        }

        void set(Bitmap b, int c) {
            if (b == art && c == colour) return;
            if (b != art) {
                art = b;
                small = null;
                if (b != null) {
                    try {
                        // a tiny copy, blurred, drawn big: as soft as a real blur, at no cost
                        int n = 24;
                        Bitmap s = Bitmap.createScaledBitmap(b, n, n, true);
                        int[] px = new int[n * n];
                        s.getPixels(px, 0, n, 0, 0, n, n);
                        px = MusicLists.blur(px, n, n, 3);
                        small = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
                    } catch (Throwable e) {
                        small = null;
                    }
                }
            }
            colour = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), k = Ui.k;
            int bg = Ui.BACKGROUND;
            if (Glass.on) {
                glass(c, w, h, k);
                return;
            }
            c.drawColor(bg);
            paint.setShader(null);
            if (small != null) {
                // the design's 720 px square from (-40, -60), at 45 %
                r.set(-40 * k, -60 * k, 680 * k, 660 * k);
                paint.setAlpha(115);
                c.drawBitmap(small, null, r, paint);
                paint.setAlpha(255);
            } else if (colour != 0) {
                float cx = w * 0.22f, cy = h * 0.38f, rad = (float) Math.hypot(w - cx, h - cy);
                paint.setShader(new RadialGradient(cx, cy, rad, new int[]{alpha(colour, 0.67f), alpha(colour, 0.27f), alpha(colour, 0)},
                        new float[]{0, 0.3f, 0.6f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, paint);
            }
            // fade to the background towards the lists, and darker at the bottom
            paint.setShader(new LinearGradient(0, 0, w, 0, new int[]{alpha(bg, 0), alpha(bg, 0.35f), bg, bg},
                    new float[]{0, 0.38f, 0.62f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, 0, h, new int[]{alpha(bg, 0), alpha(bg, 0), alpha(bg, 0.55f)},
                    new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }

        /**
         * With Glass on: the blurred wallpaper, the cover's colours softly over its left side (or a
         * glow in its colour), and a light shade, darker at the bottom, so the lists read.
         */
        private void glass(Canvas c, float w, float h, float k) {
            Glass.drawWall(c, Math.round(w), Math.round(h));
            paint.setShader(null);
            if (small != null) {
                r.set(-40 * k, -60 * k, 680 * k, 660 * k);
                paint.setAlpha(80);
                c.drawBitmap(small, null, r, paint);
                paint.setAlpha(255);
            } else if (colour != 0) {
                float cx = w * 0.22f, cy = h * 0.38f, rad = (float) Math.hypot(w - cx, h - cy);
                paint.setShader(new RadialGradient(cx, cy, rad, new int[]{alpha(colour, 0.45f), alpha(colour, 0.15f), alpha(colour, 0)},
                        new float[]{0, 0.3f, 0.6f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, paint);
            }
            paint.setShader(new LinearGradient(0, 0, 0, h, new int[]{Glass.night ? 0x40000000 : 0x1F000000, Glass.night ? 0x73000000 : 0x47000000},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    /** Initials on a soft colour, lighter at the top left, for covers and badges. */
    private static void initialsOn(Canvas c, Paint p, String ini, int colour, float w, float h, float textSize) {
        p.setShader(new LinearGradient(0, 0, w, h, Ui.mix(0xFFFFFFFF, colour, 0.14f), Ui.mix(0xFF000000, colour, 0.28f), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        p.setTextSize(textSize);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(ini, w / 2, h / 2 - (fm.ascent + fm.descent) / 2, p);
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
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 18 * Ui.k);
                }
            });
            setElevation(Ui.px(14));
        }

        void set(Bitmap b, String n) {
            if (b == art && n.equals(name)) return;
            art = b;
            name = n;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = 18 * Ui.k;
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
            } else {
                String ini = LayoutView.initials(name);
                String t = ini.isEmpty() ? "♪" : ini;
                initialsOn(c, paint, t, soft(name), w, h, Math.min(w, h) * (t.length() > 1 ? 0.36f : 0.44f));
            }
            c.restore();
        }
    }

    /** A row's picture, 56 px square: initials on a soft colour, an app's icon, or a glyph (lit: in the accent). */
    static final class Tile extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path clip = new Path();
        String glyph, ini = "";
        private int colour;
        private boolean lit;
        private Drawable icon;

        private Tile(Context c) {
            super(c);
        }

        static Tile initials(Context c, String name) {
            Tile t = new Tile(c);
            t.ini = LayoutView.initials(name);
            if (t.ini.isEmpty()) t.glyph = "note";
            else t.colour = soft(name);
            return t;
        }

        static Tile glyph(Context c, String g, boolean lit) {
            Tile t = new Tile(c);
            t.glyph = g;
            t.lit = lit;
            return t;
        }

        static Tile icon(Context c, android.content.pm.PackageManager pm, String pkg) {
            Tile t = new Tile(c);
            try {
                t.icon = pm.getApplicationIcon(pkg);
            } catch (Exception e) {
                t.glyph = "note";   // gone meanwhile
            }
            return t;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = 12 * Ui.k;
            r.set(0, 0, w, h);
            if (!ini.isEmpty()) {
                c.save();
                clip.reset();
                clip.addRoundRect(r, rad, rad, Path.Direction.CW);
                c.clipPath(clip);
                initialsOn(c, paint, ini, colour, w, h, 21 * Ui.k);
                c.restore();
                return;
            }
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(lit ? Ui.mix(ink(), Ui.PANEL, 0.22f) : Ui.PANEL_TOP);
            c.drawRoundRect(r, rad, rad, paint);
            if (!lit) {
                float sw = Math.max(1, Ui.px(1.2f));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(sw);
                paint.setColor(Ui.mix(Ui.EDGE, Ui.PANEL_TOP, 0.3f));
                r.inset(sw / 2, sw / 2);
                c.drawRoundRect(r, rad, rad, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (icon != null) {
                int in = Ui.px(6);
                icon.setBounds(in, in, (int) w - in, (int) h - in);
                icon.draw(c);
                return;
            }
            MusicScreen.glyph(c, paint, glyph, w / 2, h / 2, ("eq".equals(glyph) ? 22 : 26) * Ui.k,
                    lit ? ink() : Ui.mix(Ui.SILVER_DIM, Ui.PANEL, 0.62f));
        }
    }

    /**
     * The time in the song with a line of times under it (drag to move in it), "live" between two
     * lines for a station, or, as the volume, a speaker, the level and its number.
     */
    static final class Bar extends View {
        interface Seek {
            void to(float f);
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final boolean vol;
        Seek seek;
        private float frac, drag = -1;
        private String left = "", right = "", live;

        Bar(Context c, boolean volume) {
            super(c);
            vol = volume;
        }

        void show(float f, String l, String r) {
            if (f == frac && l.equals(left) && r.equals(right)) return;
            frac = f;
            left = l;
            right = r;
            invalidate();
        }

        /** "LIVE" in place of the times, or null for the time bar. */
        void live(String s) {
            if (s == null ? live == null : s.equals(live)) return;
            live = s;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (seek == null || live != null || getWidth() == 0) return false;
            float pad = Ui.px(8), f = Math.max(0, Math.min(1, (e.getX() - pad) / Math.max(1, getWidth() - 2 * pad)));
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

        /** A rounded line from x1 to x2, th thick, centred on y. */
        private void track(Canvas c, float x1, float x2, float y, float th, int colour) {
            if (x2 <= x1) return;
            paint.setColor(colour);
            r.set(x1, y - th / 2, x2, y + th / 2);
            c.drawRoundRect(r, th / 2, th / 2, paint);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), th = Math.max(3, Ui.px(4));
            int k = ink(), rail = alpha(Ui.SILVER, 0.18f);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            paint.setLetterSpacing(0);
            if (vol) {
                float cy = h / 2, x1 = Ui.px(34), x2 = w - Ui.px(36);
                glyph(c, paint, "speaker", Ui.px(11), cy, Ui.px(22), Ui.SILVER_DIM);
                track(c, x1, x2, cy, th, rail);
                track(c, x1, x1 + (x2 - x1) * frac, cy, th, k);
                paint.setColor(Ui.SILVER);
                paint.setTextSize(16 * Ui.k);
                paint.setTextAlign(Paint.Align.RIGHT);
                Paint.FontMetrics fm = paint.getFontMetrics();
                c.drawText(right, w, cy - (fm.ascent + fm.descent) / 2, paint);
                return;
            }
            if (live != null) {
                float cy = h / 2, tagH = Ui.px(28);
                paint.setTextSize(Math.max(Ui.MIN_TEXT, 12) * Ui.k);
                paint.setLetterSpacing(0.16f);
                paint.setTextAlign(Paint.Align.LEFT);
                float tw = paint.measureText(live), tagW = Ui.px(14) * 2 + Ui.px(16) + tw;
                float l = (w - tagW) / 2, gap = Ui.px(12);
                track(c, 0, l - gap, cy, Math.max(1, Ui.px(2)), alpha(Ui.SILVER, 0.12f));
                track(c, l + tagW + gap, w, cy, Math.max(1, Ui.px(2)), alpha(Ui.SILVER, 0.12f));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1, Ui.px(1.2f)));
                paint.setColor(alpha(LIVE, 0.6f));
                r.set(l, cy - tagH / 2, l + tagW, cy + tagH / 2);
                c.drawRoundRect(r, tagH / 2, tagH / 2, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(LIVE);
                c.drawCircle(l + Ui.px(18), cy, Ui.px(4), paint);
                paint.setColor(Ui.SILVER);
                Paint.FontMetrics fm = paint.getFontMetrics();
                c.drawText(live, l + Ui.px(30), cy - (fm.ascent + fm.descent) / 2, paint);
                return;
            }
            float pad = Ui.px(8), y = Ui.px(11), f = drag >= 0 ? drag : frac, x = pad + (w - 2 * pad) * f;
            track(c, pad, w - pad, y, th, rail);
            track(c, pad, x, y, th, k);
            paint.setColor(k);
            if (seek != null) c.drawCircle(x, y, Ui.px(drag >= 0 ? 10 : 7), paint);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(Math.max(Ui.MIN_TEXT, 13) * Ui.k);
            paint.setColor(Ui.SILVER_DIM);
            paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(left, pad, h - Ui.px(4), paint);
            paint.setTextAlign(Paint.Align.RIGHT);
            c.drawText(right, w - pad, h - Ui.px(4), paint);
        }
    }

    /** The red of the live dot. */
    static final int LIVE = 0xFFE5484D;
}
