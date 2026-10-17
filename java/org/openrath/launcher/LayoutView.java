package org.openrath.launcher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A picture layout on screen (see Layouts): the background picture, the parts' round and card
 * shapes, and what they show, all drawn on one canvas in a 1024 x 600 design that is scaled to the
 * screen height and centred. Shapes, pictures and the dock are drawn once into a bitmap; the time,
 * speed, music and weather are drawn on top every second. While the car moves, the road lines run
 * and the background drifts gently, if those are switched on. art/layout_preview.py draws the same.
 */
final class LayoutView extends View implements Hub.Listener {

    static final float W = 1024, H = 600;

    private final HomeActivity a;
    private final Prefs prefs;
    final Layouts.Layout lay;

    private float k = 1, ox, oy;
    private Bitmap still;
    private Bitmap bg;
    private double roadPhase;
    private long lastFrame;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint pic = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL),
            REG = Typeface.create("sans-serif", Typeface.NORMAL),
            MED = Typeface.create("sans-serif-medium", Typeface.NORMAL),
            BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    /** Somewhere on screen that does something when tapped. */
    private static final class Hit {
        final RectF r;
        final String action;

        Hit(float x, float y, float w, float h, String action) {
            r = new RectF(x, y, x + w, y + h);
            this.action = action;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final GestureDetector gestures;

    LayoutView(HomeActivity activity, Layouts.Layout l) {
        super(activity);
        a = activity;
        prefs = activity.prefs;
        lay = l;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        gestures = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Hit h = hit(e);
                if (h != null) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    tap(h.action);
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Hit h = hit(e);
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                if (h == null || !longTap(h.action)) a.openSettings();
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return gestures.onTouchEvent(e) || super.onTouchEvent(e);
    }

    private Hit hit(MotionEvent e) {
        float x = (e.getX() - ox) / k, y = (e.getY() - oy) / k;
        for (int i = hits.size() - 1; i >= 0; i--) {
            RectF r = hits.get(i).r;
            if (x >= r.left - 6 && x <= r.right + 6 && y >= r.top - 6 && y <= r.bottom + 6) return hits.get(i);
        }
        return null;
    }

    // ------------------------------------------------------------------ taps

    static final String[] DOCK_NAMES = {"navi", "Navigation", "music", "Music", "radio", "Radio", "bt", "Bluetooth",
            "phone", "Phone", "apps", "Apps", "settings", "Settings", "user", "Driver", "dash", "Dashboard",
            "mirror", "Phone mirroring"};

    static String label(String item) {
        for (int i = 0; i < DOCK_NAMES.length; i += 2) if (DOCK_NAMES[i].equals(item)) return DOCK_NAMES[i + 1];
        return item;
    }

    private void tap(String action) {
        if (action.startsWith("dock:")) {
            String n = action.substring(5);
            if ("apps".equals(n)) a.startActivity(new Intent(a, AppsActivity.class));
            else if ("settings".equals(n)) a.openSettings();
            else if ("user".equals(n)) user();
            else if ("dash".equals(n)) {
                prefs.put("layout", Layouts.BOXES);   // back to the boxes
                a.build();
            } else a.openApp(dockKey(n), label(n));
        } else if (action.startsWith("media:")) {
            String m = action.substring(6);
            if ("open".equals(m)) {
                Now n = now();
                a.openApp(n.radio ? "media:radio" : "media:music", n.radio ? "Radio" : "Music");
            } else Extras.media(a, m);
            postInvalidateDelayed(300);
        } else if (action.startsWith("place:")) {
            String name = action.substring(6);
            double[] ll = place(name);
            if (ll == null)
                Toast.makeText(a, Hindi.f("Save %1$s on the map first (the star in the map box)", Hindi.t(name)), Toast.LENGTH_LONG).show();
            else a.navigate(ll[0], ll[1], name);
        } else if (action.startsWith("fav:")) {
            final int i = Integer.parseInt(action.substring(4));
            String pkg = favourite(i);
            if (pkg == null) pickFavourite(i);
            else if (ChildLock.blocked(prefs, pkg)) Toast.makeText(a, Hindi.t(ChildLock.SORRY), Toast.LENGTH_SHORT).show();
            else {
                Intent in = a.getPackageManager().getLaunchIntentForPackage(pkg);
                if (in == null) pickFavourite(i);
                else a.startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } else if ("user".equals(action)) user();
        else if ("settings".equals(action)) a.openSettings();
    }

    /** Long-press: choose the app for a dock button or a favourite. False to open Settings instead. */
    private boolean longTap(String action) {
        if (action.startsWith("fav:")) {
            pickFavourite(Integer.parseInt(action.substring(4)));
            return true;
        }
        if (action.startsWith("dock:")) {
            final String n = action.substring(5);
            if ("apps".equals(n) || "settings".equals(n) || "user".equals(n) || "dash".equals(n)) return false;
            final String key = dockKey(n);
            AppPicker.show(a, Hindi.f("App for %1$s", Hindi.t(label(n))), new AppPicker.Picked() {
                @Override
                public void picked(String pkg) {
                    prefs.setApp(key, pkg);
                }
            });
            return true;
        }
        return false;
    }

    private void user() {
        if (Features.on(prefs, "profiles")) Profiles.menu(a, prefs);
        else a.openSettings();
    }

    static String dockKey(String name) {
        if ("music".equals(name)) return "media:music";
        if ("radio".equals(name)) return "media:radio";
        return "dock:" + name;
    }

    private double[] place(String name) {
        try {
            String[] s = prefs.str("place:" + name, "").split(",");
            return new double[]{Double.parseDouble(s[0]), Double.parseDouble(s[1])};
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ favourite apps (Cards and apps)

    static final int FAVOURITES = 6;
    private final Map<String, Object[]> appInfo = new HashMap<>();

    private String favourite(int i) {
        String[] s = prefs.str("layApps", "").split(",");
        return i < s.length && !s[i].isEmpty() ? s[i] : null;
    }

    private void pickFavourite(final int i) {
        AppPicker.show(a, Hindi.t("Choose an app"), new AppPicker.Picked() {
            @Override
            public void picked(String pkg) {
                String[] s = prefs.str("layApps", "").split(",", -1);
                String[] out = new String[FAVOURITES];
                for (int j = 0; j < FAVOURITES; j++) out[j] = j < s.length ? s[j] : "";
                out[i] = pkg;
                prefs.put("layApps", TextUtils.join(",", out));
                rebuild();
            }
        });
    }

    /** {icon, name} for an installed app, cached. */
    private Object[] app(String pkg) {
        Object[] o = appInfo.get(pkg);
        if (o != null) return o;
        try {
            PackageManager pm = a.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            o = new Object[]{pm.getApplicationIcon(ai), String.valueOf(pm.getApplicationLabel(ai))};
        } catch (Exception e) {
            o = new Object[]{null, null};
        }
        appInfo.put(pkg, o);
        return o;
    }

    // ------------------------------------------------------------------ hub and redraws

    @Override
    public void changed(Hub h) {
        invalidate();
    }

    /** Redraw the shapes and pictures (after a setting changed). */
    void rebuild() {
        still = null;
        bg = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        still = null;
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        if (v == VISIBLE) {
            bg = null;   // the hour's picture may have changed
            invalidate();
        }
    }

    private static boolean moving() {
        return Drive.kmhNow > 5 && System.currentTimeMillis() - Drive.fixAt < 5000;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        k = Math.min(h / H, w / W);   // fit the design, centred, on any shape of screen
        ox = (w - W * k) / 2;
        oy = (h - H * k) / 2;
        if (bg == null) bg = Layouts.background(a, prefs, lay);
        if (still == null) {
            try {
                still = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas sc = new Canvas(still);
                sc.translate(ox, oy);
                sc.scale(k, k);
                drawStill(sc);
            } catch (OutOfMemoryError e) {
                still = null;
            }
        }
        boolean moving = moving();
        boolean road = lay.roadHorizon > 0 && Layouts.roadOn(prefs);
        boolean drift = Layouts.animOn(prefs) && moving;
        long now = SystemClock.uptimeMillis();
        double dt = lastFrame == 0 ? 0 : Math.min(0.2, (now - lastFrame) / 1000.0);
        lastFrame = now;

        // background, filling the whole screen (a little larger and drifting while driving)
        if (bg != null) {
            float s = Math.max(w / (float) bg.getWidth(), h / (float) bg.getHeight());
            float dx = 0, dy = 0;
            if (Layouts.animOn(prefs)) {
                s *= 1.05f;
                double t = System.currentTimeMillis() / 1000.0;
                if (drift) {
                    dx = (float) (Math.sin(t / 9) * w * 0.018);
                    dy = (float) (Math.cos(t / 13) * h * 0.012);
                }
            }
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate((w - bg.getWidth() * s) / 2 + dx, (h - bg.getHeight() * s) / 2 + dy);
            c.drawBitmap(bg, m, pic);
        } else c.drawColor(lay.light ? 0xFFDDE3EA : 0xFF14181E);

        c.save();
        c.translate(ox, oy);
        c.scale(k, k);
        if (road) {
            if (moving) roadPhase = (roadPhase + Drive.kmhNow / 3.6 / 8 * dt) % 1.6;
            roadDashes(c);
        }
        c.restore();
        if (still != null) c.drawBitmap(still, 0, 0, null);
        c.save();
        c.translate(ox, oy);
        c.scale(k, k);
        hits.clear();
        drawLive(c);
        c.restore();

        if ((road || drift) && moving) postInvalidateDelayed(33);
        else postInvalidateDelayed(1000 - System.currentTimeMillis() % 1000 + 10);   // the clocks' second hands
    }

    // ------------------------------------------------------------------ the still layer

    private boolean on(Layouts.Part p) {
        return Layouts.shown(prefs, lay.id, p.opt);
    }

    private void drawStill(Canvas c) {
        line.setColor(0x32FFFFFF);
        line.setStrokeWidth(1);
        for (float[] l : lay.lines) c.drawLine(l[0], l[1], l[2], l[3], line);
        for (Layouts.Part p : lay.parts) {
            if (!on(p)) continue;
            shape(c, p, p.hasFill, lay.light || p.alpha > 0.8f);
            String kind = p.kind;
            if ("picture".equals(kind)) {
                Bitmap b = Layouts.asset(a, p.src);
                if (b != null) image(c, b, p.x, p.y, p.w, p.h, false, p.r);
            } else if ("car".equals(kind)) {
                Bitmap b = Layouts.asset(a, p.src);
                if (b != null) image(c, b, p.x, p.y, p.w, p.h, false, 0);
            } else if ("logo".equals(kind)) logo(c, p);
            else if ("dock".equals(kind)) dockStill(c, p);
            else if ("gear".equals(kind)) glyph(c, "settings", p.x, p.y, p.w, p.ink);
            else if ("apps".equals(kind)) glyph(c, "apps", p.x + p.w / 2 - p.w * 0.2f, p.y + p.h / 2 - p.w * 0.2f, p.w * 0.4f, p.ink);
            else if ("places".equals(kind)) {
                glyph(c, "home", p.x, p.y + 10, 30, p.ink);
                glyph(c, "office", p.x + p.w - 50, p.y + 10, 30, p.ink);
            } else if ("music".equals(kind) && "card".equals(p.style)) {
                Layouts.Part box = new Layouts.Part();
                box.x = p.x;
                box.y = p.y + p.h + 12;
                box.w = p.w;
                box.h = 70;
                box.r = 8;
                box.fill = box.fill2 = 0xFF151A22;
                box.alpha = 0.9f;
                shape(c, box, true, false);
            } else if ("music".equals(kind) && "record".equals(p.style)) {
                float r = Math.min(p.w, p.h) * 0.36f, cx = p.x + p.w / 2;
                fill.setShader(null);
                fill.setColor(0xE6FFFFFF);
                c.drawOval(cx - r - 8, p.y + 2, cx + r + 8, p.y + 18 + 2 * r, fill);
            } else if ("weather".equals(kind) && "calendar".equals(p.style)) {
                fill.setShader(null);
                fill.setColor(0xFF8A8076);
                for (float dx : new float[]{0.3f, 0.7f}) c.drawOval(p.x + p.w * dx - 5, p.y + 8, p.x + p.w * dx + 5, p.y + 18, fill);
                line.setColor(0xFFCFC6BA);
                line.setStrokeWidth(1);
                c.drawLine(p.x + 6, p.y + 30, p.x + p.w - 6, p.y + 30, line);
            } else if ("weather".equals(kind) && "bubble".equals(p.style)) {
                // a soft sun glow inside the bubble's top right
                c.save();
                Path clip = new Path();
                clip.addOval(new RectF(p.x, p.y, p.x + p.w, p.y + p.h), Path.Direction.CW);
                c.clipPath(clip);
                fill.setShader(null);
                fill.setColor(0x96FFD66E);
                c.drawOval(p.x + p.w * .55f, p.y - p.h * .15f, p.x + p.w * 1.15f, p.y + p.h * .45f, fill);
                c.restore();
            }
        }
    }

    /** A part's background: a circle or rounded card, with an optional top-to-bottom gradient and a soft shadow. */
    private void shape(Canvas c, Layouts.Part p, boolean has, boolean shadow) {
        if (!has) return;
        RectF r = new RectF(p.x, p.y, p.x + p.w, p.y + p.h);
        fill.setShader(new LinearGradient(0, p.y, 0, p.y + p.h, p.fill, p.fill2, Shader.TileMode.CLAMP));
        fill.setAlpha(Math.round(255 * p.alpha));
        if (shadow) fill.setShadowLayer(10, 0, 6, 0x40000000);
        if ("circle".equals(p.shape)) c.drawOval(r, fill);
        else c.drawRoundRect(r, p.r, p.r, fill);
        fill.clearShadowLayer();
        fill.setShader(null);
        fill.setAlpha(255);
    }

    private void image(Canvas c, Bitmap b, float x, float y, float w, float h, boolean circle, float radius) {
        c.save();
        if (circle || radius > 0) {
            Path clip = new Path();
            RectF r = new RectF(x, y, x + w, y + h);
            if (circle) clip.addOval(r, Path.Direction.CW);
            else clip.addRoundRect(r, radius, radius, Path.Direction.CW);
            c.clipPath(clip);
        }
        c.drawBitmap(b, null, new RectF(x, y, x + w, y + h), pic);
        c.restore();
    }

    private void logo(Canvas c, Layouts.Part p) {
        line.setColor(p.ink);
        line.setStrokeWidth(2);
        c.drawOval(p.x + 4, p.y + 4, p.x + p.w - 4, p.y + p.h - 4, line);
        Bitmap b = Ui.brand.isEmpty() ? null : Layouts.asset(a, "logo_s_" + Ui.brand);   // the brand's badge if built in
        if (b == null) b = Layouts.asset(a, "logo_s");   // else the OpenRath wheel
        if (b != null) {
            float s = p.h * 0.5f, bw = s * b.getWidth() / (float) b.getHeight();
            pic.setColorFilter(new android.graphics.PorterDuffColorFilter(p.ink, android.graphics.PorterDuff.Mode.SRC_IN));
            c.drawBitmap(b, null, new RectF(p.x + (p.w - bw) / 2, p.y + (p.h - s) / 2, p.x + (p.w + bw) / 2, p.y + (p.h + s) / 2), pic);
            pic.setColorFilter(null);
        }
    }

    /** The dock's buttons, with the mirroring button when it is switched on. */
    private List<String> dockItems(Layouts.Part p) {
        List<String> out = new ArrayList<>(p.items);
        if (Layouts.mirrorOn(prefs) && !out.contains("mirror")) out.add(out.contains("apps") ? out.indexOf("apps") : out.size(), "mirror");
        return out;
    }

    private void dockStill(Canvas c, Layouts.Part p) {
        List<String> items = dockItems(p);
        int n = items.size() + (p.clock ? 1 : 0);
        float size = Math.min(p.h * 0.5f, 34);
        for (int i = 0; i < items.size(); i++) {
            String it = items.get(i);
            float cx = p.x + p.w * (i + 0.5f) / n;
            if (p.tiles) {
                Layouts.Part t = new Layouts.Part();
                t.x = cx - p.h / 2;
                t.y = p.y;
                t.w = t.h = p.h;
                t.r = 8;
                t.fill = t.fill2 = p.fill;
                t.alpha = p.alpha;
                shape(c, t, true, false);
            }
            float gy = p.y + (p.h - size) / 2 - (p.labels ? 8 : 0);
            glyph(c, it, cx - size / 2, gy, size, p.ink);
            if (p.labels) text(c, Hindi.t(label(it)), cx, gy + size + 16, 13, p.ink, MED, "mm");
        }
    }

    // ------------------------------------------------------------------ road lines

    private void roadDashes(Canvas c) {
        float hz = lay.roadHorizon, vx = lay.roadX;
        fill.setShader(null);
        fill.setColor(lay.roadDash);
        fill.setAlpha(230);
        Path path = new Path();
        for (int i = 0; i < 40; i++) {
            double depth = i * 1.6 + 0.6 - roadPhase;
            if (depth < 0.35) continue;
            double t0 = 1 / depth, t1 = 1 / (depth + 0.8);
            float y0 = (float) (hz + (H - hz) * t0), y1 = (float) (hz + (H - hz) * t1);
            if (y1 <= hz + 6 || y1 > H) continue;
            y0 = Math.min(y0, H);
            float wd = (float) (1.5 + 4 * Math.min(1, t0));
            path.reset();
            path.moveTo(vx - wd, y0);
            path.lineTo(vx + wd, y0);
            path.lineTo(vx + wd * 0.7f, y1);
            path.lineTo(vx - wd * 0.7f, y1);
            path.close();
            c.drawPath(path, fill);
        }
        fill.setAlpha(255);
    }

    // ------------------------------------------------------------------ live content

    /** What's playing, from our own player, another app, or the radio. */
    static final class Now {
        String title = Hindi.t("Nothing playing"), sub = Hindi.t("Tap to choose music");
        boolean playing, radio, idle = true;
        Bitmap art;
        float progress = -1;
        List<String> around;   // for the lyrics card: the tracks around this one
        int at;
    }

    Now now() {
        Hub h = a.hub;
        Now n = new Now();
        Player.Track t = Player.mode != null ? Player.current() : null;
        if (t != null) {
            n.title = t.name;
            n.sub = t.sub == null ? "" : t.sub;
            n.playing = Player.playing;
            n.radio = Player.WEB.equals(Player.mode);
            n.idle = false;
            List<Player.Track> l = n.radio ? Player.stations : Player.songs;
            int i = n.radio ? Player.station : Player.song;
            n.around = new ArrayList<>();
            for (int j = Math.max(0, i - 1); j <= Math.min(l.size() - 1, i + 2); j++) n.around.add(l.get(j).name);
            n.at = i - Math.max(0, i - 1);
            return n;
        }
        Hub.Media m = h.music != null && h.music.title != null && !h.music.title.isEmpty() ? h.music
                : h.radio != null && h.radio.title != null && !h.radio.title.isEmpty() ? h.radio : null;
        if (m != null) {
            n.title = m.title;
            n.sub = m.subtitle == null ? "" : m.subtitle;
            n.playing = m.playing;
            n.art = m.art;
            n.radio = m == h.radio;
            n.idle = false;
            n.progress = progress(m);
            return n;
        }
        if (h.xt != null && !h.xt.display("").isEmpty()) {
            n.title = h.xt.display("").startsWith("Seek") ? h.xt.display("") : h.xt.display("") + " FM";
            n.sub = h.xt.name;
            n.playing = Boolean.TRUE.equals(h.xt.playing);
            n.radio = true;
            n.idle = false;
        } else if (h.fm != null && h.fm.seen) {
            n.title = h.fm.frequency() + " FM";
            n.sub = h.fm.name;
            n.playing = h.fm.on;
            n.radio = true;
            n.idle = false;
        }
        return n;
    }

    private static float progress(Hub.Media m) {
        try {
            if (m.controller == null) return -1;
            PlaybackState ps = m.controller.getPlaybackState();
            MediaMetadata md = m.controller.getMetadata();
            if (ps == null || md == null) return -1;
            long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (dur <= 0) return -1;
            long pos = ps.getPosition();
            if (ps.getState() == PlaybackState.STATE_PLAYING)
                pos += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime()) * ps.getPlaybackSpeed());
            return Math.max(0, Math.min(1, pos / (float) dur));
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean h24() {
        return android.text.format.DateFormat.is24HourFormat(a);
    }

    private static String fmt(String f) {
        return new SimpleDateFormat(f, Locale.US).format(new Date());
    }

    /** Today's name, and "11 Oct", in the owner's language (the numbers as they are). */
    private static String weekday() {
        return Hindi.t(fmt("EEEE"));
    }

    private static String dayMonth() {
        return fmt("d") + " " + Hindi.t(fmt("MMM"));
    }

    private String time() {
        return fmt(h24() ? "HH:mm" : "h:mm");
    }

    private String speed() {
        return a.hub.speed();
    }

    private int speedNum() {
        try {
            return Integer.parseInt(speed());
        } catch (Exception e) {
            return 0;
        }
    }

    private double heading() {
        if (Drive.headingNow >= 0) return Drive.headingNow;
        Location f = a.hub.fix;
        return f != null && f.hasBearing() ? f.getBearing() : -1;
    }

    private String direction() {
        double hd = heading();
        return hd < 0 ? "--" : Layouts.compass(hd) + "  " + Math.round(hd) + "°";
    }

    private String altitude() {
        Location f = a.hub.fix;
        return f != null && f.hasAltitude() ? Math.round(f.getAltitude()) + " m" : "--";
    }

    private String[] today() {
        List<String[]> f = a.hub.forecast;
        return f != null && !f.isEmpty() ? f.get(0) : null;
    }

    private String lo() {
        String[] t = today();
        return t == null ? "" : t[3];
    }

    private String hi() {
        String[] t = today();
        return t == null ? "" : t[2];
    }

    private String range() {
        return today() == null ? "" : lo() + "–" + hi();
    }

    private String driver() {
        String n = prefs.str("layUser", "").trim();
        if (n.isEmpty()) n = Profiles.current(prefs);
        return n;
    }

    private void drawLive(Canvas c) {
        for (Layouts.Part p : lay.parts) {
            if (!on(p)) continue;
            String kind = p.kind;
            if ("clock".equals(kind)) clock(c, p);
            else if ("weather".equals(kind)) weather(c, p);
            else if ("forecast".equals(kind)) forecast(c, p);
            else if ("speed".equals(kind)) speedPart(c, p);
            else if ("music".equals(kind)) music(c, p);
            else if ("stats".equals(kind)) stats(c, p);
            else if ("compass".equals(kind)) compassPart(c, p);
            else if ("user".equals(kind)) userPart(c, p);
            else if ("favourites".equals(kind)) favourites(c, p);
            else if ("picture".equals(kind) && "trip".equals(p.style)) {
                String km = Drive.todayKm < 100 ? Fuel.fmt(Drive.todayKm) : String.valueOf(Math.round(Drive.todayKm));
                text(c, km + " km", p.x + p.w / 2, p.y + p.h * 0.42f, 40, p.ink, MED, "mm");
                String pl = a.hub.place;
                text(c, pl == null || pl.isEmpty() ? Hindi.t("today") : Hindi.f("today · %1$s", pl), p.x + p.w / 2, p.y + p.h * 0.42f + 40, 15, p.sub, REG, "mm");
            } else if ("dock".equals(kind)) {
                List<String> items = dockItems(p);
                int n = items.size() + (p.clock ? 1 : 0);
                for (int i = 0; i < items.size(); i++) {
                    float cx = p.x + p.w * (i + 0.5f) / n, cw = p.w / n;
                    hits.add(new Hit(cx - cw / 2, p.y, cw, p.h, "dock:" + items.get(i)));
                }
                if (p.clock) text(c, time(), p.x + p.w - 10, p.y + p.h / 2, 26, p.ink, MED, "rm");
            } else if ("apps".equals(kind)) hits.add(new Hit(p.x, p.y, p.w, p.h, "dock:apps"));
            else if ("gear".equals(kind)) hits.add(new Hit(p.x - 8, p.y - 8, p.w + 16, p.h + 16, "settings"));
            else if ("places".equals(kind)) {
                hits.add(new Hit(p.x - 6, p.y, 60, p.h, "place:Home"));
                hits.add(new Hit(p.x + p.w - 60, p.y, 60, p.h, "place:Office"));
            }
        }
    }

    // ---- clock

    private void clock(Canvas c, Layouts.Part p) {
        float x = p.x, y = p.y, w = p.w, h = p.h, cx = x + w / 2, cy = y + h / 2;
        String st = p.style.isEmpty() ? "digital" : p.style;
        if ("digital".equals(st)) {
            text(c, time(), cx, cy - 6, h * 0.26f, p.ink, MED, "mm");
            text(c, dayMonth(), cx, cy + h * 0.17f, h * 0.09f, p.sub, REG, "mm");
        } else if ("digital-line".equals(st)) {
            text(c, time(), x, y + h * 0.5f, h * 0.62f, p.ink, MED, "ls");
            text(c, weekday() + "   " + dayMonth(), x + 4, y + h * 0.82f, h * 0.14f, p.sub, REG, "ls");
        } else if ("analog-date".equals(st)) {
            text(c, weekday() + "  " + dayMonth(), cx, y + 16, 17, p.sub, REG, "mt");
            analog(c, cx, y + 60 + (h - 60) / 2, Math.min(w, h - 60) * 0.42f, p.ink, p.sub, p.accent, 12, true, 0);
        } else if ("analog-digital".equals(st)) {
            float r = h * 0.38f;
            analog(c, x + 16 + r, cy, r, p.ink, p.sub, p.accent, 12, false, 0xFFFFFFFF);
            text(c, time(), x + 32 + 2 * r, cy - 2, h * 0.3f, p.ink, MED, "ls");
            text(c, weekday(), x + 34 + 2 * r, cy + h * 0.2f, h * 0.13f, p.sub, REG, "ls");
        } else if ("wall".equals(st)) {
            float r = h * 0.4f;
            analog(c, x + 20 + r, cy, r, p.ink, p.sub, p.accent, 60, false, 0xFFFFFFFF);
            text(c, fmt("MM"), x + w - 24, cy - 6, h * 0.34f, p.ink, BOLD, "rs");
            text(c, fmt("dd"), x + w - 24, cy + h * 0.32f, h * 0.34f, p.ink, BOLD, "rs");
        } else if ("analog-big".equals(st)) {
            analog(c, cx, cy, Math.min(w, h) / 2, p.ink, p.sub, 0, 12, false, 0);
        } else if ("analog-dark".equals(st)) {
            analog(c, cx, cy, Math.min(w, h) * 0.42f, p.ink, p.sub, p.accent, 12, true, 0);
        } else if ("digital-analog".equals(st)) {
            text(c, time(), x, y + h * 0.55f, h * 0.38f, p.ink, MED, "ls");
            text(c, weekday(), x + 2, y + h * 0.8f, h * 0.14f, p.sub, REG, "ls");
            float r = h * 0.42f;
            analog(c, x + w - r - 6, cy, r, p.ink, p.sub, 0, 60, false, 0);
        }
    }

    /** An analog clock face: ticks (12, or 60 with every fifth long), optional numbers, three hands. */
    private void analog(Canvas c, float cx, float cy, float r, int ink, int sub, int accent, int ticks, boolean numbers, int face) {
        if (face != 0) {
            fill.setShader(null);
            fill.setColor(face);
            c.drawCircle(cx, cy, r, fill);
        }
        line.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < ticks; i++) {
            double an = i / (double) ticks * 2 * Math.PI;
            boolean major = ticks != 60 || i % 5 == 0;
            float r0 = r * (major ? 0.86f : 0.92f);
            line.setColor(major ? ink : sub);
            line.setStrokeWidth((major ? 2.4f : 1.2f) * r / 100);
            c.drawLine(cx + (float) Math.sin(an) * r0, cy - (float) Math.cos(an) * r0,
                    cx + (float) Math.sin(an) * r * .97f, cy - (float) Math.cos(an) * r * .97f, line);
        }
        line.setStrokeCap(Paint.Cap.ROUND);
        if (numbers) {
            for (int n : new int[]{12, 3, 6, 9}) {
                double an = n / 12.0 * 2 * Math.PI;
                text(c, String.valueOf(n), cx + (float) Math.sin(an) * r * .7f, cy - (float) Math.cos(an) * r * .7f, r * .16f, ink, MED, "mm");
            }
        }
        Calendar now = Calendar.getInstance();
        float hr = now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60f, mn = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f;
        float sec = now.get(Calendar.SECOND);
        float[][] hands = {{hr / 12, 0.5f, 4.5f, ink}, {mn / 60, 0.75f, 3, ink}, {sec / 60, 0.85f, 1.4f, accent != 0 ? accent : sub}};
        for (float[] hd : hands) {
            double an = hd[0] * 2 * Math.PI;
            line.setColor((int) hd[3]);
            line.setStrokeWidth(hd[2] * r / 100);
            c.drawLine(cx, cy, cx + (float) Math.sin(an) * r * hd[1], cy - (float) Math.cos(an) * r * hd[1], line);
        }
        fill.setShader(null);
        fill.setColor(ink);
        c.drawCircle(cx, cy, r * .05f, fill);
    }

    // ---- weather

    private Bitmap wicon(String name) {
        return Ui.img(a, name == null ? "w_cloud" : name);
    }

    private void icon(Canvas c, String name, float x, float y, float size) {
        Bitmap b = wicon(name);
        if (b != null) c.drawBitmap(b, null, new RectF(x, y, x + size, y + size), pic);
    }

    private static String lastWord(String s) {
        String[] w = s.trim().split(" ");
        return w[w.length - 1];
    }

    private void weather(Canvas c, Layouts.Part p) {
        Hub hb = a.hub;
        float x = p.x, y = p.y, w = p.w, h = p.h, cx = x + w / 2, cy = y + h / 2;
        String st = p.style, temp = hb.temp, desc = Hindi.t(hb.weather), place = hb.place == null ? "" : hb.place;
        if ("bubble".equals(st)) {
            text(c, temp, cx, cy - 4, h * 0.26f, p.ink, MED, "mm");
            text(c, fit(desc, h * 0.085f, w * 0.8f, REG), cx, cy + h * 0.17f, h * 0.085f, p.sub, REG, "mm");
        } else if ("detail".equals(st)) {
            text(c, temp, x + 6, y + 70, 72, p.ink, BOLD, "ls");
            text(c, place, x + w, y + 18, 14, p.sub, REG, "rt");
            String[][] rows = {{Hindi.t("Humidity"), hb.humidity}, {Hindi.t("Wind"), hb.wind}, {Hindi.t("Air pressure"), hb.pressure}};
            for (int i = 0; i < rows.length; i++) {
                float yy = y + 118 + i * 38;
                text(c, rows[i][0], x + 6, yy, 15, p.sub, REG, "lm");
                text(c, rows[i][1].isEmpty() ? "--" : rows[i][1], x + w, yy, 15, p.sub, REG, "rm");
            }
        } else if ("range".equals(st)) {
            text(c, temp, x, y + h * 0.42f, h * 0.36f, p.ink, MED, "ls");
            if (today() != null) text(c, lo() + "/" + hi(), x + 2, y + h * 0.7f, h * 0.15f, p.sub, REG, "ls");
        } else if ("wide".equals(st)) {
            icon(c, hb.weatherIcon, x + w - 60, y - 6, 52);
            String t = temp.replace("°", "°C");
            text(c, t, x, y + 40, 40, p.ink, MED, "ls");
            text(c, desc, x + width(t, 40, MED) + 12, y + 40, 15, p.sub, REG, "ls");
            String line2 = place + (hb.humidity.isEmpty() ? "" : (place.isEmpty() ? "" : "  ·  ") + Hindi.f("humidity %1$s", hb.humidity));
            text(c, line2, x + 2, y + 70, 14, p.sub, REG, "ls");
        } else if ("calendar".equals(st)) {
            text(c, today() == null ? temp : range(), cx, y + 64, 19, p.ink, BOLD, "mm");
            icon(c, hb.weatherIcon, cx - 22, y + 84, 44);
            text(c, fit(lastWord(desc), 12, w - 12, REG), cx, y + 138, 12, p.sub, REG, "mm");
        } else if ("row".equals(st)) {
            icon(c, hb.weatherIcon, x, y + h / 2 - 30, 60);
            text(c, temp.replace("°", "°C"), x + w - 20, y + h / 2, 40, p.ink, MED, "rm");
        } else if ("card".equals(st)) {
            text(c, fit(place, 14, w - 28, REG), x + 16, y + 26, 14, p.sub, REG, "lm");
            text(c, temp, x + 14, y + 74, 46, p.ink, MED, "lm");
            icon(c, hb.weatherIcon, x + 16, y + 98, 28);
            text(c, lastWord(desc) + (today() == null ? "" : "  " + range()), x + 16, y + 136, 13, p.sub, REG, "lm");
        }
    }

    private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private void forecast(Canvas c, Layouts.Part p) {
        List<String[]> f = a.hub.forecast;
        if (f == null || f.size() < 2) {
            text(c, Hindi.t("Forecast after the first weather update"), p.x + p.w / 2, p.y + p.h / 2, 12, p.sub, REG, "mm");
            return;
        }
        int n = Math.min(4, f.size() - 1);
        for (int i = 0; i < n; i++) {
            String[] d = f.get(i + 1);
            float cx = p.x + p.w * (i + 0.5f) / n;
            icon(c, d[1], cx - 20, p.y + 8, 40);
            text(c, d[2], cx, p.y + 66, 16, p.ink, MED, "mm");
            String day = d[0];
            try {
                Date dt = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d[0]);
                Calendar cal = Calendar.getInstance();
                cal.setTime(dt);
                day = Hindi.t(DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]);
            } catch (Exception ignored) {
            }
            text(c, day, cx, p.y + 92, 13, p.sub, REG, "mm");
        }
    }

    // ---- speed

    private void ring(Canvas c, float cx, float cy, float r, float width, float frac, int ink, int track) {
        RectF box = new RectF(cx - r, cy - r, cx + r, cy + r);
        line.setStrokeCap(Paint.Cap.BUTT);
        line.setStrokeWidth(width);
        line.setColor(track);
        c.drawArc(box, 135, 270, false, line);
        line.setColor(ink);
        if (frac > 0) c.drawArc(box, 135, 270 * Math.min(1, frac), false, line);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    private void speedPart(Canvas c, Layouts.Part p) {
        float x = p.x, y = p.y, w = p.w, h = p.h, cx = x + w / 2, cy = y + h / 2;
        String v = speed();
        float frac = speedNum() / 180f;
        String st = p.style;
        hits.add(new Hit(x, y, w, h, "dock:navi"));
        if ("ring".equals(st)) {
            ring(c, cx, cy, w * 0.4f, w * 0.035f, frac, 0xFFFFFFFF, 0x55FFFFFF);
            text(c, v, cx, cy - 6, h * 0.27f, p.ink, MED, "mm");
            text(c, "km/h", cx, cy + h * 0.17f, h * 0.085f, p.sub, REG, "mm");
        } else if ("number".equals(st)) {
            text(c, v, cx, y + h * 0.72f, h * 0.8f, p.ink, MED, "ms");
            text(c, "km/h", cx, y + h * 0.95f, h * 0.13f, p.sub, REG, "ms");
        } else if ("badge".equals(st)) {
            text(c, v + " km/h", cx, cy, h * 0.38f, p.ink, MED, "mm");
        } else if ("gauge-small".equals(st)) {
            float r = h * 0.36f;
            ring(c, x + 18 + r, cy, r, 4, frac, p.accent, 0x22000000);
            text(c, v, x + 18 + r, cy - 2, r * 0.62f, p.ink, MED, "mm");
            text(c, "km/h", x + 18 + r, cy + r * 0.5f, 10, p.sub, REG, "mm");
            text(c, direction(), x + w - 16, cy - 10, 15, p.ink, MED, "rm");
            text(c, altitude(), x + w - 16, cy + 16, 13, p.sub, REG, "rm");
        } else if ("dial".equals(st)) {
            float r = w / 2;
            line.setStrokeCap(Paint.Cap.BUTT);
            for (int i = 0; i <= 180; i += 10) {
                double an = Math.toRadians(225 - i / 180.0 * 270);
                boolean major = i % 20 == 0;
                float r0 = r * (major ? 0.78f : 0.82f);
                line.setColor(major ? p.ink : p.sub);
                line.setStrokeWidth(major ? 3 : 1.5f);
                c.drawLine(cx + (float) Math.cos(an) * r0, cy - (float) Math.sin(an) * r0,
                        cx + (float) Math.cos(an) * r * .88f, cy - (float) Math.sin(an) * r * .88f, line);
                if (major)
                    text(c, String.valueOf(i), cx + (float) Math.cos(an) * r * .66f, cy - (float) Math.sin(an) * r * .66f,
                            r * .085f, i < 140 ? p.sub : p.accent, MED, "mm");
            }
            RectF box = new RectF(cx - r * .93f, cy - r * .93f, cx + r * .93f, cy + r * .93f);
            line.setColor(p.accent);
            line.setStrokeWidth(5);
            if (frac > 0) c.drawArc(box, 135, 270 * Math.min(1, frac), false, line);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setColor((p.sub & 0x00FFFFFF) | 0x99000000);
            line.setStrokeWidth(2);
            c.drawCircle(cx, cy, r * .42f, line);
            text(c, v, cx, cy - 2, r * 0.3f, p.ink, MED, "mm");
            text(c, "km/h", cx, cy + r * 0.24f, r * 0.09f, p.sub, REG, "mm");
        } else if ("row".equals(st)) {
            text(c, v, x, cy + 14, 52, p.ink, MED, "ls");
            text(c, "km/h", x + width(v, 52, MED) + 12, cy + 14, 15, p.sub, REG, "ls");
        } else if ("card".equals(st)) {
            text(c, v, cx, y + 64, 64, p.ink, MED, "mm");
            text(c, "km/h", cx, y + 104, 15, p.sub, REG, "mm");
            line.setColor(0xFF9AC8F0);
            line.setStrokeWidth(2);
            for (int sg : new int[]{-1, 1}) c.drawLine(cx + sg * 14, y + 140, cx + sg * 70, y + h - 20, line);
            Bitmap car = Layouts.asset(a, "car_rear_white");
            if (car != null) c.drawBitmap(car, null, new RectF(cx - 55, y + h - 110, cx + 55, y + h - 31), pic);
        }
    }

    // ---- music

    /** prev, play/pause, next spread over w, each a tap target. */
    private void controls(Canvas c, float x, float y, float w, float size, int ink, boolean playing) {
        String[] g = {"prev", playing ? "pause" : "play", "next"};
        String[] act = {"prev", "toggle", "next"};
        for (int i = 0; i < 3; i++) {
            float cx = x + w * (0.12f + 0.38f * i);
            glyph(c, g[i], cx - size / 2, y, size, ink);
            hits.add(new Hit(cx - size, y - size / 2, size * 2, size * 2, "media:" + act[i]));
        }
    }

    /** A record: a dark disc with grooves and the album cover (or a warm stand-in) in the middle. */
    private void disc(Canvas c, float cx, float cy, float r, Bitmap art) {
        fill.setShader(null);
        fill.setColor(0xFF101012);
        c.drawCircle(cx, cy, r, fill);
        line.setColor(0xFF28282C);
        line.setStrokeWidth(1);
        for (float f : new float[]{0.92f, 0.84f, 0.76f}) c.drawCircle(cx, cy, r * f, line);
        cover(c, cx - r * 0.62f, cy - r * 0.62f, r * 1.24f, r * 1.24f, art, true, 0);
    }

    /** The album cover, or a warm gradient with a soft sun when the app gives none. */
    private void cover(Canvas c, float x, float y, float w, float h, Bitmap art, boolean circle, float radius) {
        if (art != null) {
            image(c, art, x, y, w, h, circle, radius);
            return;
        }
        c.save();
        Path clip = new Path();
        RectF r = new RectF(x, y, x + w, y + h);
        if (circle) clip.addOval(r, Path.Direction.CW);
        else clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.clipPath(clip);
        fill.setShader(new LinearGradient(0, y, 0, y + h, 0xFF283C78, 0xFFDC6E50, Shader.TileMode.CLAMP));
        c.drawRect(r, fill);
        fill.setShader(null);
        fill.setColor(0xFFFABE78);
        c.drawOval(x + w * .25f, y + h * .2f, x + w * .8f, y + h * .75f, fill);
        c.restore();
    }

    private void bar(Canvas c, float x0, float x1, float y, float frac, int ink, int track, float wd) {
        line.setStrokeCap(Paint.Cap.ROUND);
        if (track != 0) {
            line.setColor(track);
            line.setStrokeWidth(wd / 2);
            c.drawLine(x0, y, x1, y, line);
        }
        if (frac < 0) return;
        line.setColor(ink);
        line.setStrokeWidth(wd);
        c.drawLine(x0, y, x0 + (x1 - x0) * frac, y, line);
    }

    private static int faded(int col, float alpha) {
        return (col & 0x00FFFFFF) | (Math.round(alpha * 255) << 24);
    }

    private void music(Canvas c, Layouts.Part p) {
        float x = p.x, y = p.y, w = p.w, h = p.h, cx = x + w / 2, cy = y + h / 2;
        Now n = now();
        String t = n.title, ar = n.sub;
        int ink = p.ink, sub = p.sub;
        hits.add(new Hit(x, y, w, h, "media:open"));
        String st = p.style;
        if ("pill".equals(st)) {
            disc(c, x + h / 2, cy, h * 0.38f, n.art);
            float tx = x + h + 6, tw = w - h - 30;
            glyph(c, n.radio ? "radio" : "music", tx, y + 36, 18, ink);
            text(c, n.radio ? Hindi.t("Radio") : Hindi.t("Music"), tx + 26, y + 45, 15, ink, MED, "lm");
            text(c, fit(t, 22, tw, MED), tx, y + 88, 22, ink, MED, "lm");
            fill.setShader(null);
            line.setColor(p.accent);
            line.setStrokeWidth(3);
            c.drawLine(tx, y + 116, tx + 60, y + 116, line);
            controls(c, tx, y + 136, tw, 24, ink, n.playing);
        } else if ("float".equals(st)) {
            text(c, fit(t, 30, w - 36, BOLD), x + 18, y + 40, 30, ink, BOLD, "lm");
            text(c, fit(ar, 15, w - 36, REG), x + 18, y + 76, 15, sub, REG, "lm");
            controls(c, x + 18, y + 112, w - 36, 24, ink, n.playing);
        } else if ("card".equals(st)) {
            cover(c, x + 8, y + 8, w - 16, h - 90, n.art, false, 4);
            text(c, fit(t, 20, w - 20, BOLD), cx, y + h - 56, 20, ink, BOLD, "mm");
            text(c, fit(ar, 13, w - 20, REG), cx, y + h - 28, 13, sub, REG, "mm");
            controls(c, x + 10, y + h + 32, w - 20, 30, ink, n.playing);
        } else if ("row".equals(st)) {
            float sz = Math.min(70, h - 64);
            cover(c, x + 14, y + 12, sz, sz, n.art, false, 6);
            text(c, fit(t, 17, w - sz - 40, MED), x + 26 + sz, y + 12 + sz * 0.35f, 17, ink, MED, "lm");
            text(c, fit(ar, 12, w - sz - 40, REG), x + 26 + sz, y + 12 + sz * 0.75f, 12, sub, REG, "lm");
            controls(c, x + 10, y + h - 42, w - 20, 22, ink, n.playing);
            bar(c, x + 14, x + w - 14, y + h - 8, n.progress, ink, 0, 3);
        } else if ("dial".equals(st)) {
            float r = w / 2;
            disc(c, cx, cy, r * 0.62f, n.art);
            line.setColor(0xFF5A6A80);
            line.setStrokeWidth(4);
            c.drawCircle(cx, cy, r * .98f, line);
            text(c, fit(t, r * 0.11f, r * 1.2f, MED), cx, y + r * 0.26f, r * 0.11f, ink, MED, "mm");
            text(c, fit(ar, r * 0.11f, r * 1.2f, MED), cx, y + h - r * 0.26f, r * 0.11f, ink, MED, "mm");
            glyph(c, "prev", x + r * 0.1f, cy - 14, 28, ink);
            glyph(c, "next", x + w - r * 0.1f - 28, cy - 14, 28, ink);
            glyph(c, n.playing ? "pause" : "play", cx - 16, cy - 16, 32, 0xFFFFFFFF);
            hits.add(new Hit(x + r * 0.1f - 10, cy - 30, 48, 60, "media:prev"));
            hits.add(new Hit(x + w - r * 0.1f - 38, cy - 30, 48, 60, "media:next"));
            hits.add(new Hit(cx - 36, cy - 36, 72, 72, "media:toggle"));
        } else if ("record".equals(st)) {
            float r = Math.min(w, h) * 0.36f;
            disc(c, cx, y + 10 + r, r, n.art);
            text(c, fit(t, 17, w - 10, MED), cx, y + 2 * r + 46, 17, sub, MED, "mm");
            bar(c, x + 10, x + w - 10, y + 2 * r + 72, n.progress, ink, faded(sub, 0.4f), 2);
            controls(c, x + 10, y + 2 * r + 92, w - 20, 30, ink, n.playing);
        } else if ("list".equals(st)) {
            glyph(c, n.radio ? "radio" : "music", x, y + 8, 30, ink);
            text(c, fit(t, 20, w - 60, MED), x + w - 20, y + 24, 20, ink, MED, "rm");
            controls(c, x - 6, y + 70, w - 20, 26, ink, n.playing);
            line.setColor(faded(sub, 0.3f));
            line.setStrokeWidth(1);
            c.drawLine(x, y + h - 4, x + w - 20, y + h - 4, line);
            c.drawLine(x, y - 10, x + w - 20, y - 10, line);
        } else if ("lyrics".equals(st)) {
            glyph(c, n.radio ? "radio" : "music", x + 24, y + 22, 18, sub);
            text(c, fit(t + (ar.isEmpty() ? "" : "  ·  " + ar), 14, w - 80, REG), x + 50, y + 31, 14, sub, REG, "lm");
            // the songs around this one, this one large (the words of songs come with the lyrics feature)
            List<String> lines = n.around;
            int at = n.at;
            if (lines == null || lines.isEmpty()) {
                lines = new ArrayList<>();
                lines.add("");
                lines.add(t);
                lines.add(ar);
                at = 1;
            }
            for (int i = 0; i < lines.size() && i < 4; i++) {
                boolean cur = i == at;
                float size = cur ? 30 : 18;
                text(c, fit(lines.get(i), size, w - 80, cur ? BOLD : REG), cx, y + 120 + i * 58, size, cur ? ink : sub, cur ? BOLD : REG, "mm");
            }
            bar(c, x + 60, x + w - 60, y + h - 96, n.progress, ink, faded(sub, 0.4f), 4);
            controls(c, x + 80, y + h - 70, w - 160, 36, ink, n.playing);
        } else if ("wide".equals(st)) {
            glyph(c, n.radio ? "radio" : "music", x + 22, y + 20, 16, sub);
            text(c, n.radio ? Hindi.t("Radio") : Hindi.t("Music"), x + 46, y + 28, 13, sub, REG, "lm");
            text(c, fit(t, 26, w * 0.6f, MED), x + 22, y + 72, 26, ink, MED, "lm");
            controls(c, x + 10, y + 108, w * 0.55f, 28, ink, n.playing);
            disc(c, x + w - 90, cy + 4, 66, n.art);
        }
    }

    // ---- stats, compass, driver

    private String[] stat(String it) {
        Location f = a.hub.fix;
        if ("temp".equals(it)) return new String[]{Hindi.t("Temp (°C)"), a.hub.temp};
        if ("altitude".equals(it)) return new String[]{Hindi.t("Altitude"), altitude()};
        if ("direction".equals(it)) {
            double hd = heading();
            return new String[]{hd < 0 ? Hindi.t("Direction") : Hindi.f("Direction %1$s", Layouts.compass(hd)), hd < 0 ? "--" : Math.round(hd) + "°"};
        }
        if ("pressure".equals(it)) return new String[]{Hindi.t("Air pressure"), a.hub.pressure.isEmpty() ? "--" : a.hub.pressure};
        if ("tyres".equals(it)) {
            StringBuilder v = new StringBuilder();
            boolean warn = false;
            for (int w = 0; w < 4; w++) {
                Tpms.Tyre t = Tpms.tyres[w];
                boolean fresh = t != null && System.currentTimeMillis() - t.at < 15 * 60 * 1000;
                v.append(w == 0 ? "" : " ").append(fresh ? Tpms.show(prefs, t.kpa) : "--");
                if (!Tpms.warning(prefs, w).isEmpty()) warn = true;
            }
            String unit = "bar".equals(prefs.str("tpmsUnit", "psi")) ? "bar" : "psi";
            return new String[]{warn ? Hindi.t("Tyres: check the pressures") : Hindi.f("Tyres (%1$s)", unit), v.toString()};
        }
        if (f == null) return new String[]{Hindi.t("Position"), "--"};
        double la = f.getLatitude(), lo = f.getLongitude();
        return new String[]{String.format(Locale.US, "%.4f° %s", Math.abs(lo), lo >= 0 ? "E" : "W"),
                String.format(Locale.US, "%.4f° %s", Math.abs(la), la >= 0 ? "N" : "S")};
    }

    private void stats(Canvas c, Layouts.Part p) {
        float x = p.x, y = p.y, h = p.h;
        List<String> items = Layouts.statItems(prefs, lay.id, p);
        if (items.isEmpty()) return;
        if ("frame".equals(p.style)) {
            cover(c, x + 16, y + 16, h - 32, h - 32, now().art, false, 2);
            float step = Math.min(56, (h - 20) / items.size());
            for (int i = 0; i < items.size(); i++) {
                String[] s = stat(items.get(i));
                float sz = items.size() > 2 ? 20 : 26;
                text(c, s[1], x + h + 10, y + 44 + i * step - (items.size() > 2 ? 14 : 0), sz, p.ink, BOLD, "lm");
                text(c, s[0], x + h + 10, y + 70 + i * step - (items.size() > 2 ? 20 : 0), 12, p.sub, REG, "lm");
            }
            return;
        }
        float step = Math.min(100, h / items.size());
        for (int i = 0; i < items.size(); i++) {
            String it = items.get(i);
            String[] s = stat(it);
            float yy = y + i * step;
            line.setColor(p.ink);
            line.setStrokeWidth(3);
            if ("temp".equals(it)) {
                c.drawArc(new RectF(x + 4, yy + 10, x + 44, yy + 50), 150, 240, false, line);
                c.drawLine(x + 24, yy + 30, x + 34, yy + 18, line);
            } else if ("altitude".equals(it)) {
                Path m = new Path();
                m.moveTo(x + 4, yy + 44);
                m.lineTo(x + 18, yy + 18);
                m.lineTo(x + 28, yy + 32);
                m.lineTo(x + 34, yy + 24);
                m.lineTo(x + 46, yy + 44);
                c.drawPath(m, line);
            } else if ("tyres".equals(it)) {
                c.drawCircle(x + 24, yy + 30, 20, line);
                c.drawCircle(x + 24, yy + 30, 9, line);
            } else {
                c.drawCircle(x + 24, yy + 30, 20, line);
                Path m = new Path();
                m.moveTo(x + 33, yy + 21);
                m.lineTo(x + 27, yy + 33);
                m.lineTo(x + 15, yy + 39);
                m.lineTo(x + 21, yy + 27);
                m.close();
                fill.setShader(null);
                fill.setColor(p.ink);
                c.drawPath(m, fill);
            }
            text(c, s[1], x + 70, yy + 30, "latlon".equals(it) || "tyres".equals(it) ? 22 : 34, p.ink, BOLD, "ls");
            text(c, s[0], x + 70, yy + 54, 14, p.sub, REG, "ls");
        }
    }

    private void compassPart(Canvas c, Layouts.Part p) {
        float x = p.x, y = p.y, w = p.w, h = p.h, cx = x + w / 2, cy = y + h * 0.5f;
        line.setColor(faded(p.sub, 0.25f));
        line.setStrokeWidth(1);
        for (int i = 0; i < 5; i++) {
            float yy = cy + 10 + i * 22;
            c.drawLine(x + 10, yy, x + w - 10, yy, line);
        }
        // the arrow points the way the car is going; north is up on the grid
        double hd = heading();
        c.save();
        if (hd >= 0) c.rotate((float) hd, cx, cy - 20);
        Path arrow = new Path();
        arrow.moveTo(cx, cy - 70);
        arrow.lineTo(cx + 46, cy + 30);
        arrow.lineTo(cx, cy + 10);
        arrow.lineTo(cx - 46, cy + 30);
        arrow.close();
        fill.setShader(null);
        fill.setColor(faded(p.ink, 0.85f));
        c.drawPath(arrow, fill);
        c.restore();
        line.setColor(faded(p.ink, 0.35f));
        line.setStrokeWidth(8);
        line.setStrokeCap(Paint.Cap.BUTT);
        c.drawLine(cx, cy + 34, cx, cy + 110, line);
        line.setStrokeCap(Paint.Cap.ROUND);
        text(c, direction(), cx, y + 12, 18, p.ink, MED, "mt");
    }

    private void avatar(Canvas c, float cx, float cy, float r) {
        fill.setShader(null);
        fill.setColor(0xFFC89A60);
        c.drawCircle(cx, cy, r, fill);
        glyph(c, "user", cx - r * 0.82f, cy - r * 0.82f, r * 1.64f, 0xFFFFFFFF);
    }

    private void userPart(Canvas c, Layouts.Part p) {
        float x = p.x, y = p.y, w = p.w, h = p.h;
        String name = driver();
        hits.add(new Hit(x, y, w, h, "user"));
        if ("line".equals(p.style)) {
            avatar(c, x + 16, y + 20, 16);
            text(c, name.isEmpty() ? Hindi.t("Let's go!") : Hindi.f("Let's go, %1$s!", name), x + 44, y + h / 2, 16, p.sub, REG, "lm");
            return;
        }
        float r = h * 0.34f;
        avatar(c, x + 18 + r, y + h / 2, r);
        text(c, fit(name.isEmpty() ? Hindi.t("Hello") : name, 20, w - 2 * r - 44, BOLD), x + 30 + 2 * r, y + h / 2 - 10, 20, p.ink, BOLD, "lm");
        text(c, Hindi.t("Drive safe"), x + 30 + 2 * r, y + h / 2 + 16, 13, p.sub, REG, "lm");
    }

    // ---- favourite apps

    private static final int[][] TILE = {{0xFFF06A4A, 0xFFF7B048}, {0xFF4AA8F0, 0xFF7A5CF0}, {0xFF38C07A, 0xFF2A9AD0},
            {0xFFF0C040, 0xFFF07A30}, {0xFF9A6AF0, 0xFFF06AB0}, {0xFF2E3440, 0xFF4C566A}};

    private void favourites(Canvas c, Layouts.Part p) {
        float x = p.x, y = p.y, w = p.w, h = p.h;
        float s = Math.min(w / 3, h / 2) * 0.62f;
        for (int i = 0; i < FAVOURITES; i++) {
            float cx = x + w * ((i % 3) + 0.5f) / 3, cy = y + h * ((i / 3) + 0.5f) / 2;
            float tx = cx - s / 2, ty = cy - s / 2 - 6;
            String pkg = favourite(i);
            Object[] info = pkg == null ? null : app(pkg);
            hits.add(new Hit(cx - w / 6, cy - h / 4, w / 3, h / 2, "fav:" + i));
            RectF r = new RectF(tx, ty, tx + s, ty + s);
            if (info != null && info[0] != null) {
                fill.setShader(new LinearGradient(0, ty, 0, ty + s, TILE[i][0], TILE[i][1], Shader.TileMode.CLAMP));
                c.drawRoundRect(r, s * 0.24f, s * 0.24f, fill);
                fill.setShader(null);
                Drawable d = (Drawable) info[0];
                float in = s * 0.16f;
                d.setBounds(Math.round(tx + in), Math.round(ty + in), Math.round(tx + s - in), Math.round(ty + s - in));
                d.draw(c);
                text(c, fit((String) info[1], 11, w / 3 - 8, REG), cx, cy + s / 2 + 6, 11, p.ink, REG, "mm");
            } else {
                fill.setShader(null);
                fill.setColor(0x33FFFFFF);
                c.drawRoundRect(r, s * 0.24f, s * 0.24f, fill);
                line.setColor(p.ink);
                line.setStrokeWidth(2.5f);
                c.drawLine(cx - s * 0.18f, ty + s / 2, cx + s * 0.18f, ty + s / 2, line);
                c.drawLine(cx, ty + s / 2 - s * 0.18f, cx, ty + s / 2 + s * 0.18f, line);
                text(c, Hindi.t("Add app"), cx, cy + s / 2 + 6, 11, p.ink, REG, "mm");
            }
        }
    }

    // ------------------------------------------------------------------ glyphs (a 24-unit box)

    void glyph(Canvas c, String name, float x, float y, float size, int ink) {
        c.save();
        c.translate(x, y);
        c.scale(size / 24f, size / 24f);
        fill.setShader(null);
        fill.setColor(ink);
        line.setColor(ink);
        line.setStrokeWidth(2);
        Path p = new Path();
        if ("navi".equals(name)) {
            poly(p, 12, 3, 20, 21, 12, 16.5f, 4, 21);
            c.drawPath(p, fill);
        } else if ("music".equals(name)) {
            p.moveTo(9, 17);
            p.lineTo(9, 5);
            p.lineTo(19, 3);
            p.lineTo(19, 15);
            c.drawPath(p, line);
            c.drawOval(3.6f, 14.6f, 9.6f, 19.4f, fill);
            c.drawOval(13.6f, 12.6f, 19.6f, 17.4f, fill);
        } else if ("radio".equals(name)) {
            c.drawRoundRect(3, 8, 21, 20, 2, 2, line);
            c.drawOval(5.5f, 11, 11.5f, 17, line);
            c.drawLine(14, 12, 18.5f, 12, line);
            c.drawLine(14, 16, 18.5f, 16, line);
            c.drawLine(6, 8, 17, 3, line);
        } else if ("bt".equals(name)) {
            p.moveTo(7, 7);
            p.lineTo(17, 16.5f);
            p.lineTo(12, 21.5f);
            p.lineTo(12, 2.5f);
            p.lineTo(17, 7.5f);
            p.lineTo(7, 17);
            c.drawPath(p, line);
        } else if ("phone".equals(name)) {
            line.setStrokeWidth(4);
            c.drawArc(new RectF(2, -6, 30, 22), 95, 80, false, line);
            c.drawOval(3.5f, 2.5f, 9.5f, 8.5f, fill);
            c.drawOval(15.5f, 14.5f, 21.5f, 20.5f, fill);
        } else if ("apps".equals(name)) {
            for (int ix = 0; ix < 2; ix++)
                for (int iy = 0; iy < 2; iy++)
                    c.drawRoundRect(3.5f + ix * 9.5f, 3.5f + iy * 9.5f, 10.5f + ix * 9.5f, 10.5f + iy * 9.5f, 2.2f, 2.2f, fill);
        } else if ("settings".equals(name)) {
            for (int i = 0; i < 48; i++) {
                double an = i / 48.0 * 2 * Math.PI;
                float r = (i / 3) % 2 == 0 ? 9.5f : 7.6f;
                float px = 12 + r * (float) Math.cos(an), py = 12 + r * (float) Math.sin(an);
                if (i == 0) p.moveTo(px, py);
                else p.lineTo(px, py);
            }
            p.close();
            p.addCircle(12, 12, 3.4f, Path.Direction.CCW);
            p.setFillType(Path.FillType.EVEN_ODD);
            c.drawPath(p, fill);
        } else if ("user".equals(name)) {
            c.drawCircle(12, 7, 4, line);
            c.drawArc(new RectF(4, 13, 20, 29), 180, 180, false, line);
        } else if ("dash".equals(name)) {
            c.drawRoundRect(3, 11, 21, 18, 2, 2, line);
            p.moveTo(6, 11);
            p.lineTo(8, 6);
            p.lineTo(16, 6);
            p.lineTo(18, 11);
            c.drawPath(p, line);
            c.drawCircle(7.5f, 19, 2, fill);
            c.drawCircle(16.5f, 19, 2, fill);
        } else if ("mirror".equals(name)) {
            c.drawRoundRect(2, 4, 18, 15, 1.5f, 1.5f, line);
            c.drawLine(7, 19, 13, 19, line);
            c.drawLine(10, 15, 10, 19, line);
            c.drawRoundRect(15.5f, 9, 22, 21, 1.5f, 1.5f, fill);
        } else if ("home".equals(name)) {
            poly(p, 4, 11, 12, 4, 20, 11, 20, 20, 4, 20);
            c.drawPath(p, line);
        } else if ("office".equals(name)) {
            c.drawRect(6, 4, 18, 21, line);
            line.setStrokeWidth(1);
            for (int yy = 8; yy <= 16; yy += 4) c.drawLine(9, yy, 15, yy, line);
        } else if ("play".equals(name)) {
            poly(p, 7, 4, 20, 12, 7, 20);
            c.drawPath(p, fill);
        } else if ("pause".equals(name)) {
            c.drawRect(6, 4, 10, 20, fill);
            c.drawRect(14, 4, 18, 20, fill);
        } else if ("next".equals(name) || "prev".equals(name)) {
            if ("prev".equals(name)) c.scale(-1, 1, 12, 12);
            poly(p, 4, 4, 16, 12, 4, 20);
            c.drawPath(p, fill);
            c.drawRect(17, 4, 20, 20, fill);
        }
        c.restore();
    }

    private static void poly(Path p, float... v) {
        p.moveTo(v[0], v[1]);
        for (int i = 2; i < v.length; i += 2) p.lineTo(v[i], v[i + 1]);
        p.close();
    }

    // ------------------------------------------------------------------ text

    private void setText(float size, int ink, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(ink);
        text.setShader(null);
    }

    float width(String s, float size, Typeface tf) {
        setText(size, 0, tf);
        return text.measureText(s);
    }

    /** The text shortened with "…" to fit maxw at that size. */
    String fit(String s, float size, float maxw, Typeface tf) {
        if (s == null) return "";
        setText(size, 0, tf);
        return TextUtils.ellipsize(s, text, maxw, TextUtils.TruncateAt.END).toString();
    }

    /**
     * Text placed like PIL's anchors: first letter l/m/r (left, middle, right), second a/t (top),
     * m (middle), s (baseline) or b (bottom).
     */
    void text(Canvas c, String s, float x, float y, float size, int ink, Typeface tf, String anchor) {
        if (s == null || s.isEmpty()) return;
        setText(size, ink, tf);
        char hz = anchor.charAt(0), vt = anchor.charAt(1);
        text.setTextAlign(hz == 'm' ? Paint.Align.CENTER : hz == 'r' ? Paint.Align.RIGHT : Paint.Align.LEFT);
        Paint.FontMetrics fm = text.getFontMetrics();
        float base = y;
        if (vt == 'm') base = y - (fm.ascent + fm.descent) / 2;
        else if (vt == 't' || vt == 'a') base = y - fm.ascent;
        else if (vt == 'b') base = y - fm.descent;
        c.drawText(s, x, base, text);
        text.setTextAlign(Paint.Align.LEFT);
    }
}
