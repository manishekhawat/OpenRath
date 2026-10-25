package org.openrath.launcher;

import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The 1.12 features on the dashboard side: resume after switch-on, steering-wheel actions, the
 * notification ticker, clock screensaver and screen-off mode, app widgets, engine and tyre menus,
 * and their settings (Features, Engine and tyres, Steering-wheel buttons, Notifications, Backup).
 */
final class Extras {

    static final int WIDGET_BIND = 90, WIDGET_CONFIGURE = 91;
    private static final int DIALOG = android.R.style.Theme_Material_Dialog_Alert;

    // ------------------------------------------------------------------ resume after switch-on

    static void resumed(final HomeActivity a) {
        lastTouch = System.currentTimeMillis();
        a.dash.removeCallbacks(saverCheck(a));
        a.dash.postDelayed(saverCheck(a), 20000);
        if (Resume.due(a, a.prefs)) {
            resumeNav(a);
            // tried after 2 seconds, then every 2 seconds while the music app or radio isn't ready yet
            a.dash.postDelayed(new Runnable() {
                int tries;

                @Override
                public void run() {
                    if (!resumeAudio(a, ++tries) && tries < Resume.TRIES) a.dash.postDelayed(this, 2000);
                }
            }, 2000);
        }
    }

    /**
     * The stereo switched off mid-trip (the navigation app was still guiding a short while before):
     * open the navigation app again so the route carries on.
     */
    static void resumeNav(HomeActivity a) {
        String pkg = a.prefs.str("navApp", "");
        long at = a.prefs.longNum("navAt", 0L);
        if (!Resume.navDue(pkg, at, System.currentTimeMillis())) return;
        a.prefs.putLong("navAt", 0L);   // once
        android.content.Intent i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        try {
            a.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Diag.note("resume navigation", e);
        }
    }

    /** Carry on playing (try is 1, 2, ...); false while what played isn't ready yet (try again), true when done. */
    static boolean resumeAudio(HomeActivity a, int attempt) {
        boolean last = attempt >= Resume.TRIES;
        Prefs p = a.prefs;
        String what = p.str("resume", "");
        Resume.restoreVolume(a, p);
        if (what.isEmpty()) return true;
        try {
            AudioManager am = (AudioManager) a.getSystemService(android.content.Context.AUDIO_SERVICE);
            if (am.isMusicActive()) return true;   // the unit already carried on by itself
            if (what.startsWith("app:") && !Resume.playApp(a, what.substring(4), last)) return false;
            if (!what.startsWith("app:") && attempt < 2) return false;   // FM and the player's services need a moment after boot
            if ("fm".equals(what)) {
                MediaCard.owner = "radio";
                String err = Fm.play(p, a.hub.xt, a.hub.fm, true);
                a.startRadioService();
                if (err != null) a.startRadio();   // fall back to opening the radio app, which tunes in
            } else if (Player.WEB.equals(what) || Player.FILES.equals(what)) {
                MediaCard.owner = Player.WEB.equals(what) ? "radio" : "music";
                Player.send(a, Player.PLAY, what);
            } else if (what.startsWith("app:")) {
                MediaCard.owner = "music";
            }
            Toast.makeText(a, Hindi.t("Carrying on where you left off"), Toast.LENGTH_SHORT).show();
            if (!"text".equals(a.prefs.str("summaryHow", "voice"))) Speak.say(a, "Welcome back. Carrying on where you left off.", false);
        } catch (Throwable e) {
            Diag.note("resume playback", e);
        }
        a.hub.notifyIfChanged(true);
        return true;
    }

    static void key(android.content.Context c, int code) {
        AudioManager am = (AudioManager) c.getSystemService(android.content.Context.AUDIO_SERVICE);
        long t = android.os.SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    /** Play/pause, next, previous from a steering-wheel button, for whatever is playing. */
    static void media(HomeActivity a, String action) {
        Hub h = a.hub;
        if (Player.mode != null && (Player.playing || Player.busy)) {
            Player.send(a, "toggle".equals(action) ? Player.TOGGLE : "next".equals(action) ? Player.NEXT : Player.PREV, Player.mode);
            return;
        }
        if ("radio".equals(MediaCard.owner) && h.xt != null && h.xt.ready()) {
            if ("next".equals(action)) h.xt.next();
            else if ("prev".equals(action)) h.xt.prev();
            else {
                boolean on = h.xt.playing == null || !h.xt.playing;
                Fm.play(a.prefs, h.xt, h.fm, on);
                Resume.remember(a.prefs, on ? "fm" : "");
            }
            h.notifyIfChanged(true);
            return;
        }
        key(a, "toggle".equals(action) ? KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                : "next".equals(action) ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    // ------------------------------------------------------------------ screensaver and screen off

    private static long lastTouch;
    /** The screensaver or screen-off cover while it shows; held weakly so it never keeps an old dashboard. */
    private static WeakReference<View> cover = new WeakReference<>(null);
    private static boolean dark;
    private static Runnable saverCheck;

    static void touched(HomeActivity a) {
        lastTouch = System.currentTimeMillis();
    }

    /** The dashboard the screensaver check is for, held weakly so a closed one can be freed. */
    private static WeakReference<HomeActivity> saverOwner = new WeakReference<>(null);

    private static Runnable saverCheck(HomeActivity a) {
        if (saverOwner.get() != a) {   // the dashboard was recreated
            saverCheck = null;
            saverOwner = new WeakReference<>(a);
        }
        if (saverCheck == null) {
            final WeakReference<HomeActivity> owner = saverOwner;
            saverCheck = new Runnable() {
                @Override
                public void run() {
                    HomeActivity home = owner.get();
                    if (home == null || !HomeActivity.visible) return;
                    int min = home.prefs.num("saverMin", 5);
                    boolean parked = System.currentTimeMillis() - Drive.fixAt > 10000 || Drive.kmhNow < 5;
                    if (cover.get() == null && Features.on(home.prefs, "saver") && parked
                            && System.currentTimeMillis() - lastTouch > min * 60000L) showCover(home, false);
                    home.dash.postDelayed(this, 20000);
                }
            };
        }
        return saverCheck;
    }

    /** The dashboard was closed for good: let go of its views, so the old screen can be freed. */
    static void closed(HomeActivity a) {
        if (saverOwner.get() == a) {
            saverOwner = new WeakReference<>(null);
            saverCheck = null;
        }
        View c = cover.get();
        if (c != null && c.getContext() == a) {
            cover = new WeakReference<>(null);
            dark = false;
        }
        View s = strip.get();
        if (s != null && s.getContext() == a) strip = new WeakReference<>(null);
    }

    /** Whether the screen-off cover has the backlight down (night dimming leaves it alone then). */
    static boolean darkScreen() {
        return dark;
    }

    /** Remove the screensaver or screen-off cover; true if one was showing. */
    static boolean wake(HomeActivity a) {
        lastTouch = System.currentTimeMillis();
        View c = cover.get();
        if (c == null) return false;
        a.root.removeView(c);
        cover = new WeakReference<>(null);
        if (dark) {
            android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            a.getWindow().setAttributes(lp);
            dark = false;
        }
        return true;
    }

    static void screenOff(HomeActivity a) {
        if (cover.get() != null && dark) {
            wake(a);
            return;
        }
        wake(a);
        showCover(a, true);
    }

    /** A big clock (screensaver) or a black screen with the backlight at its lowest (screen off). */
    private static void showCover(final HomeActivity a, boolean off) {
        FrameLayout f = new FrameLayout(a);
        f.setBackgroundColor(0xFF000000);
        f.setClickable(true);
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wake(a);
            }
        });
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        TextClock time = new TextClock(a);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextSize(0, (off ? 40 : 150) * Ui.k);
        time.setTextColor(off ? 0xFF3A3C40 : Ui.SILVER);
        time.setTypeface(android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL));
        time.setGravity(Gravity.CENTER);
        col.addView(time);
        if (!off) {
            TextClock date = new TextClock(a);
            date.setFormat12Hour("EEEE  d MMMM");
            date.setFormat24Hour("EEEE  d MMMM");
            date.setTextSize(0, 22 * Ui.k);
            date.setTextColor(Ui.SILVER_DIM);
            date.setGravity(Gravity.CENTER);
            col.addView(date);
            TextView now = Ui.text(a, 18, Ui.ACCENT, "sans-serif", Gravity.CENTER);
            Player.Track t = Player.playing ? Player.current() : null;
            Hub.Media m = a.hub.music != null && a.hub.music.playing ? a.hub.music : a.hub.radio;
            String line = t != null ? t.name : m != null && m.playing && m.title != null ? m.title
                    : a.hub.xt != null && Boolean.TRUE.equals(a.hub.xt.playing) ? a.hub.xt.freqText + " FM" : "";
            now.setText(line.isEmpty() ? a.hub.temp + "  " + a.hub.weather : "♪  " + line);
            now.setPadding(0, Ui.px(26), 0, 0);
            col.addView(now);
        }
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
        a.root.addView(f, new FrameLayout.LayoutParams(-1, -1));
        cover = new WeakReference<View>(f);
        if (off) {
            android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.screenBrightness = 0.01f;
            a.getWindow().setAttributes(lp);
            dark = true;
        }
    }

    // ------------------------------------------------------------------ notification ticker

    private static WeakReference<View> strip = new WeakReference<>(null);
    private static long spokeAt;

    static void ticker(final HomeActivity a) {
        Prefs p = a.prefs;
        if (Highway.on && Features.on(p, "highway")) {   // it waits until the car slows down
            Highway.held++;
            return;
        }
        if (Features.on(p, "readAloud") && !Cab.quiet && System.currentTimeMillis() - spokeAt > 4000) {
            spokeAt = System.currentTimeMillis();
            a.speak(Ticker.spoken(p.str("readAloudMode", "names")));
        }
        if (!Features.on(p, "ticker") || !HomeActivity.visible) return;
        View old = strip.get();
        if (old != null) a.root.removeView(old);
        LinearLayout s = new LinearLayout(a);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(Ui.px(18), Ui.px(10), Ui.px(18), Ui.px(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.RADIUS * Ui.k);
        g.setColor(Ui.solidPanel());
        g.setStroke(Math.max(1, Ui.px(1.5f)), Ui.ACCENT);
        s.setBackground(g);
        s.setElevation(Ui.px(8));
        TextView app = Ui.label(a, Ticker.app.toUpperCase(Locale.getDefault()));
        s.addView(app);
        TextView msg = Ui.text(a, 17, Ui.SILVER, "sans-serif", Gravity.LEFT);
        msg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        boolean names = "names".equals(p.str("tickerShow", "all"));
        msg.setText(Ticker.title + (names || Ticker.text.isEmpty() ? "" : ":  " + Ticker.text));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
        mp.leftMargin = Ui.px(14);
        s.addView(msg, mp);
        if (Features.on(p, "voice") && Ticker.canReply()) {   // answer by voice, sent after one tap
            TextView reply = Ui.text(a, 18, Ui.SILVER, "sans-serif-medium", Gravity.CENTER);
            reply.setText(Hindi.t("Reply"));
            GradientDrawable rg = new GradientDrawable();
            rg.setCornerRadius(999);
            rg.setStroke(Math.max(1, Ui.px(1.5f)), Ui.ACCENT);
            reply.setBackground(rg);
            reply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    HomeVoice.reply(a);
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.px(130), Ui.px(HomeDock.TARGET) - Ui.px(20));
            rp.leftMargin = Ui.px(12);
            s.addView(reply, rp);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.setMargins(Ui.px(140), Ui.px(10), Ui.px(140), 0);
        a.root.addView(s, lp);
        strip = new WeakReference<View>(s);
        s.setTranslationY(-Ui.px(90));
        s.animate().translationY(0).setDuration(Motion.ms(250)).start();
        final View mine = s;
        s.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide(a, mine);
            }
        });
        s.postDelayed(new Runnable() {
            @Override
            public void run() {
                hide(a, mine);
            }
        }, 8000);
    }

    private static void hide(final HomeActivity a, final View v) {
        v.animate().translationY(-Ui.px(90)).setDuration(Motion.ms(200)).withEndAction(new Runnable() {
            @Override
            public void run() {
                a.root.removeView(v);
                if (strip.get() == v) strip = new WeakReference<>(null);
            }
        }).start();
    }

    // ------------------------------------------------------------------ widgets from other apps

    private static int pendingId = -1;
    private static String pendingContent;

    static void pickWidget(final HomeActivity a, final String content) {
        if (!Features.on(a.prefs, "widgets")) {
            Toast.makeText(a, Hindi.t("Switch on Widgets from other apps in Settings > Home screen first"), Toast.LENGTH_LONG).show();
            return;
        }
        final AppWidgetManager m = AppWidgetManager.getInstance(a);
        final List<AppWidgetProviderInfo> all = new ArrayList<>(m.getInstalledProviders());
        final PackageManager pm = a.getPackageManager();
        final List<String> names = new ArrayList<>();
        for (AppWidgetProviderInfo i : all) names.add(label(pm, i));
        // sort the two lists together by name
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) order.add(i);
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                return names.get(x).compareToIgnoreCase(names.get(y));
            }
        });
        final List<AppWidgetProviderInfo> sorted = new ArrayList<>();
        String[] items = new String[all.size() + 1];
        items[0] = Hindi.t("No widget (empty box)");
        for (int k = 0; k < order.size(); k++) {
            sorted.add(all.get(order.get(k)));
            items[k + 1] = names.get(order.get(k));
        }
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t(all.isEmpty() ? "No apps on the unit offer widgets" : "Choose a widget"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            saveWidget(a, content, -1);
                            return;
                        }
                        AppWidgetProviderInfo info = sorted.get(w - 1);
                        int id = a.widgets.allocateAppWidgetId();
                        pendingId = id;
                        pendingContent = content;
                        boolean ok = false;
                        try {
                            ok = m.bindAppWidgetIdIfAllowed(id, info.provider);
                        } catch (Throwable ignored) {
                            // not allowed yet: ask below
                        }
                        if (ok) configure(a, id);
                        else {
                            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                            try {
                                a.startActivityForResult(i, WIDGET_BIND);
                            } catch (Throwable e) {
                                a.widgets.deleteAppWidgetId(id);
                                Toast.makeText(a, Hindi.t("This unit doesn't let launchers add widgets"), Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static String label(PackageManager pm, AppWidgetProviderInfo i) {
        String app;
        try {
            app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(i.provider.getPackageName(), 0)));
        } catch (Exception e) {
            app = i.provider.getPackageName();
        }
        String w = i.label == null ? "" : i.label;
        return w.isEmpty() || w.equals(app) ? app : app + ": " + w;
    }

    private static void configure(HomeActivity a, int id) {
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(a).getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            try {
                a.startActivityForResult(new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), WIDGET_CONFIGURE);
                return;
            } catch (Throwable ignored) {
                // some widgets' set-up screens can't be opened by launchers; use the widget as it is
            }
        }
        saveWidget(a, pendingContent, id);
    }

    static void widgetResult(HomeActivity a, int code, int result) {
        if (pendingId < 0) return;
        if (result != android.app.Activity.RESULT_OK) {
            a.widgets.deleteAppWidgetId(pendingId);
            pendingId = -1;
            return;
        }
        if (code == WIDGET_BIND) configure(a, pendingId);
        else saveWidget(a, pendingContent, pendingId);
    }

    private static void saveWidget(HomeActivity a, String content, int id) {
        int old = a.prefs.num("widgetId:" + content, -1);
        if (old >= 0 && old != id) a.widgets.deleteAppWidgetId(old);
        a.prefs.put("widgetId:" + content, String.valueOf(id));
        pendingId = -1;
        a.build();
    }

    // ------------------------------------------------------------------ engine and tyres

    static void obdMenu(final HomeActivity a) {
        final Prefs p = a.prefs;
        String mac = p.str("obdMac", "");
        String[] items = {Hindi.f("Adapter: %1$s", mac.isEmpty() ? Hindi.t("choose…") : p.str("obdName", mac)), Hindi.t("Read fault codes"),
                Hindi.t("Clear fault codes…"), Hindi.f("Status: %1$s", Hindi.t(Obd.status) + (Obd.protocol.isEmpty() ? "" : " (" + Obd.protocol + ")"))};
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Engine (OBD-II)")).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                if (w == 0) chooseAdapter(a);
                else if (w == 1) readCodes(a);
                else if (w == 2) clearCodes(a);
            }
        }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    static void chooseAdapter(final HomeActivity a) {
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("What kind of OBD-II adapter?"))
                .setItems(new String[]{Hindi.t("Bluetooth"), Hindi.t("Wi-Fi")}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) chooseBluetooth(a);
                        else chooseWifi(a);
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    /** A Wi-Fi adapter: the unit joins its Wi-Fi first (often named "WiFi_OBDII"), then its address here. */
    static void chooseWifi(final HomeActivity a) {
        final android.widget.EditText e = new android.widget.EditText(a);
        e.setSingleLine(true);
        String now = a.prefs.str("obdMac", "");
        e.setText(now.startsWith("wifi:") ? now.substring(5) : Obd.WIFI_DEFAULT);
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Wi-Fi adapter address"))
                .setMessage(Hindi.t("First join the adapter's Wi-Fi in the unit's Wi-Fi settings. Most adapters are at 192.168.0.10, port 35000."))
                .setView(e)
                .setPositiveButton(Hindi.t("Connect"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String v = "wifi:" + e.getText().toString().trim();
                        if (Obd.wifiTarget(v) == null) {
                            Toast.makeText(a, Hindi.t("That address doesn't look right, for example 192.168.0.10:35000"), Toast.LENGTH_LONG).show();
                            return;
                        }
                        a.prefs.put("obdName", Hindi.t("Wi-Fi adapter"));
                        a.prefs.put("obdMac", v);
                        Obd.stop();
                        Features.set(a.prefs, "obd", true);
                        Drive.start(a);
                        Toast.makeText(a, Hindi.t("Connecting to the Wi-Fi adapter; keep the ignition on"), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton(Hindi.t("Wi-Fi settings"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                        } catch (Throwable e) {
                            Diag.note("Wi-Fi settings", e);
                        }
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    static void chooseBluetooth(final HomeActivity a) {
        final List<String> devs = Obd.paired();
        if (devs.isEmpty()) {
            new Glass.Builder(a, DIALOG).setTitle(Hindi.t("No paired Bluetooth devices"))
                    .setMessage(Hindi.t("Plug the OBD adapter (often sold as ELM327) into the socket under the steering wheel, switch the ignition on, "
                            + "and pair it in the unit's Bluetooth settings (the PIN is usually 1234 or 0000). Then come back here."))
                    .setPositiveButton(Hindi.t("Bluetooth settings"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                            } catch (Throwable e) {
                                Diag.note("Bluetooth settings", e);
                            }
                        }
                    }).setNegativeButton(Hindi.t("Close"), null).show();
            return;
        }
        String[] names = new String[devs.size()];
        for (int i = 0; i < names.length; i++) names[i] = devs.get(i).replace("|", "   ");
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Which one is the OBD-II adapter?")).setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                String[] f = devs.get(w).split("\\|");
                a.prefs.put("obdName", f[0]);
                a.prefs.put("obdMac", f[1]);
                Obd.stop();
                Features.set(a.prefs, "obd", true);
                Drive.start(a);
                Toast.makeText(a, Hindi.f("Connecting to %1$s; keep the ignition on", f[0]), Toast.LENGTH_LONG).show();
            }
        }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    static void readCodes(final HomeActivity a) {
        if (!Obd.connected) {
            Toast.makeText(a, Hindi.f("Not connected to the adapter: %1$s", Hindi.t(Obd.status)), Toast.LENGTH_LONG).show();
            return;
        }
        final long asked = System.currentTimeMillis();
        Obd.readCodes();
        Toast.makeText(a, Hindi.t("Asking the engine computer…"), Toast.LENGTH_SHORT).show();
        a.dash.postDelayed(new Runnable() {
            int tries;

            @Override
            public void run() {
                if (Obd.codesAt < asked && ++tries < 12) {
                    a.dash.postDelayed(this, 500);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                synchronized (Obd.codes) {
                    for (String c : Obd.codes) {
                        if (Features.on(a.prefs, "faultWords")) sb.append(FaultCodes.explain(c)).append("\n\n");
                        else sb.append(c).append("  ").append(Obd.meaning(c)).append('\n');
                    }
                    if (!Obd.pendingCodes.isEmpty()) {
                        sb.append('\n').append(Hindi.t("Seen once, not confirmed yet (an early warning):")).append('\n');
                        for (String c : Obd.pendingCodes) sb.append(c).append("  ").append(Obd.meaning(c)).append('\n');
                    }
                }
                new Glass.Builder(a, DIALOG).setTitle(Hindi.t(Obd.codesAt < asked ? "No answer from the engine computer"
                        : sb.length() == 0 ? "No fault codes" : "Fault codes"))
                        .setMessage(sb.length() == 0 ? Hindi.t("The engine computer reports nothing stored.") : sb.toString().trim()
                                + Hindi.t("\n\nA code points at where to look; a mechanic can confirm the cause."))
                        .setPositiveButton(Hindi.t("OK"), null).show();
            }
        }, 1500);
    }

    static void clearCodes(final HomeActivity a) {
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Clear the fault codes?"))
                .setMessage(Hindi.t("This also turns off the check-engine light until the fault is seen again. Only clear codes after "
                        + "the problem has been fixed. Do it with the engine off and the ignition on."))
                .setPositiveButton(Hindi.t("Clear"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Obd.clearCodes();
                        Toast.makeText(a, Hindi.t("Cleared"), Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    static void tyresMenu(final HomeActivity a) {
        final Prefs p = a.prefs;
        String[] items = {Hindi.t("Find my tyre sensors"), Hindi.f("Units: %1$s", p.str("tpmsUnit", "psi")), Hindi.f("Warn below %1$s psi", p.num("tpmsLow", 28)),
                Hindi.f("Status: %1$s", Hindi.t(Tpms.status))};
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Tyre pressure")).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                if (w == 0) findSensors(a);
                else if (w == 1) p.put("tpmsUnit", "bar".equals(p.str("tpmsUnit", "psi")) ? "psi" : "bar");
                else if (w == 2) {
                    int[] steps = {24, 26, 28, 30, 32, 34};
                    int i = java.util.Arrays.binarySearch(steps, p.num("tpmsLow", 28));
                    p.put("tpmsLow", String.valueOf(steps[(Math.max(0, i) + 1) % steps.length]));
                }
                a.hub.notifyIfChanged(true);
            }
        }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    /** Listen for sensors for a while, then let the owner say which wheel each one is on. */
    static void findSensors(final HomeActivity a) {
        Features.set(a.prefs, "tpms", true);
        Drive.start(a);
        Tpms.start(a, a.prefs);
        final TextView t = Ui.text(a, 16, 0xFFDDDDDD, "sans-serif", Gravity.LEFT);
        t.setSingleLine(false);
        t.setPadding(Ui.px(24), Ui.px(16), Ui.px(24), Ui.px(8));
        t.setText(Hindi.t("Listening… Sensors send every few seconds while the car moves, and less often when parked. "
                + "Driving slowly or letting a little air out wakes them up."));
        final AlertDialog dlg = new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Finding tyre sensors")).setView(t)
                .setPositiveButton(Hindi.t("Assign wheels"), null).setNegativeButton(Hindi.t("Close"), null).show();
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!dlg.isShowing()) return;
                StringBuilder sb = new StringBuilder();
                synchronized (Tpms.heard) {
                    for (Tpms.Tyre ty : Tpms.heard.values())
                        sb.append(ty.id).append("   ").append(Tpms.show(a.prefs, ty.kpa)).append(" ")
                                .append(a.prefs.str("tpmsUnit", "psi")).append("   ").append(Math.round(ty.tempC)).append("°C\n");
                }
                if (sb.length() > 0) t.setText(sb.toString().trim());
                t.postDelayed(this, 1000);
            }
        };
        t.post(tick);
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                assign(a);
            }
        });
    }

    private static void assign(final HomeActivity a) {
        final List<String> ids = new ArrayList<>();
        synchronized (Tpms.heard) {
            ids.addAll(Tpms.heard.keySet());
        }
        if (ids.isEmpty()) {
            Toast.makeText(a, Hindi.t("No sensors heard yet"), Toast.LENGTH_LONG).show();
            return;
        }
        // sensors that say which wheel they are on are placed straight away
        int auto = 0;
        for (String id : ids) {
            int w = Tpms.wheelOf(id);
            if (w >= 0) {
                a.prefs.put("tpms" + w, id);
                auto++;
            }
        }
        if (auto == 4) {
            Toast.makeText(a, Hindi.t("All four sensors placed"), Toast.LENGTH_LONG).show();
            return;
        }
        final String[] items = ids.toArray(new String[0]);
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Tap a sensor to choose its wheel")).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                final String id = items[w];
                new Glass.Builder(a, DIALOG).setTitle(id).setItems(tr(new String[]{"Front left", "Front right", "Rear left", "Rear right"}),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d2, int wheel) {
                                a.prefs.put("tpms" + wheel, id);
                                assign(a);
                            }
                        }).show();
            }
        }).setNegativeButton(Hindi.t("Done"), null).show();
    }

    // ------------------------------------------------------------------ settings rows

    interface Num {
        void set(String value);
    }

    /** Ask for a number; an empty answer is passed on as "". */
    static void number(android.app.Activity a, String title, String cur, final Num then) {
        final android.widget.EditText e = new android.widget.EditText(a);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setText(cur);
        int pad = (int) (20 * a.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t(title)).setView(box)
                .setPositiveButton(Hindi.t("Save"), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String v = e.getText().toString().trim();
                        try {
                            if (!v.isEmpty()) Double.parseDouble(v);
                            then.set(v);
                        } catch (Exception ex) {
                            android.widget.Toast.makeText(e.getContext(), Hindi.t("Enter a number"), android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static String onOff(boolean b) {
        return b ? "On" : "Off";
    }

    static void rows(final HomeActivity a, List<SettingsView.Row> r) {
        final Prefs p = a.prefs;

        // ---- Features: one switch per feature
        for (final Features.F f : Features.ALL) {
            if (!Features.shown(p, f)) continue;   // another level's, or developer mode's
            boolean on = Features.on(p, f.id);
            final boolean locked = Managed.lockedTo(p, f.id) != null;
            r.add(new SettingsView.Row("Features", f.name, locked ? Managed.NOTE : f.about, new Runnable() {
                @Override
                public void run() {
                    if (locked) {
                        android.widget.Toast.makeText(a, Hindi.t(Managed.NOTE), android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Features.set(p, f.id, !Features.on(p, f.id));
                    if ("reports".equals(f.id) && !Features.on(p, f.id)) Reports.clear();
                    featuresChanged(a);
                    if (Features.on(p, f.id)) offerSetup(a, f.id);
                }
            }).toggle(on, f.id));
        }

        CabSettings.rows(a, r);

        // ---- Dashboard extras
        if (Features.on(p, "pages")) {
            r.add(new SettingsView.Row("Dashboard", "Number of pages", Hindi.f("%1$s  (swipe sideways)", p.pages()), new Runnable() {
                @Override
                public void run() {
                    p.put("pages", String.valueOf(p.pages() >= Prefs.MAX_PAGES ? 2 : p.pages() + 1));
                    a.build();
                }
            }));
        }
        r.add(new SettingsView.Row("Dashboard", "Clock screensaver after", Hindi.f("%1$s min", p.num("saverMin", 5)),
                new Runnable() {
                    @Override
                    public void run() {
                        int[] steps = {1, 2, 5, 10, 20};
                        int i = java.util.Arrays.binarySearch(steps, p.num("saverMin", 5));
                        p.put("saverMin", String.valueOf(steps[(Math.max(0, i) + 1) % steps.length]));
                        Features.set(p, "saver", true);
                    }
                }));
        r.add(new SettingsView.Row("Dashboard", "Screen off now", "music keeps playing; tap to wake", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                screenOff(a);
            }
        }));

        // ---- Engine and tyres
        String g = "Engine and tyres";
        r.add(new SettingsView.Row(g, "OBD-II adapter", p.str("obdMac", "").isEmpty() ? "not chosen" : p.str("obdName", "") + "  ·  " + Obd.status,
                new Runnable() {
                    @Override
                    public void run() {
                        chooseAdapter(a);
                    }
                }));
        r.add(new SettingsView.Row(g, "Read fault codes", Obd.codesAt == 0 ? null : Hindi.f("%1$s found last time", Obd.codes.size()), new Runnable() {
            @Override
            public void run() {
                readCodes(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Live engine readings", Obd.connected ? null : "when the adapter is connected", new Runnable() {
            @Override
            public void run() {
                String now = Obd.connected ? Obd.readings() : "";
                new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Live engine readings"))
                        .setMessage(now.isEmpty() ? Hindi.f("Not connected to the adapter: %1$s", Hindi.t(Obd.status)) : now)
                        .setPositiveButton(Hindi.t("OK"), null).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Clear fault codes", "after a repair", new Runnable() {
            @Override
            public void run() {
                clearCodes(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Warn when the coolant is above", Hindi.f("%1$s °C", p.num("obdHot", 105)), new Runnable() {
            @Override
            public void run() {
                int[] steps = {100, 105, 110, 115};
                int i = java.util.Arrays.binarySearch(steps, p.num("obdHot", 105));
                p.put("obdHot", String.valueOf(steps[(Math.max(0, i) + 1) % steps.length]));
            }
        }));
        if (Features.on(p, "ledStrip")) r.add(new SettingsView.Row(g, "LED light strip", p.str("ledMac", "").isEmpty() ? "not chosen" : p.str("ledName", ""), new Runnable() {
            @Override
            public void run() {
                LedStrip.choose(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Tyre sensors", Tpms.status, new Runnable() {
            @Override
            public void run() {
                findSensors(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Tyre pressure units", p.str("tpmsUnit", "psi"), new Runnable() {
            @Override
            public void run() {
                p.put("tpmsUnit", "bar".equals(p.str("tpmsUnit", "psi")) ? "psi" : "bar");
            }
        }));
        r.add(new SettingsView.Row(g, "Warn when a tyre is below", p.num("tpmsLow", 28) + " psi", new Runnable() {
            @Override
            public void run() {
                int[] steps = {24, 26, 28, 30, 32, 34};
                int i = java.util.Arrays.binarySearch(steps, p.num("tpmsLow", 28));
                p.put("tpmsLow", String.valueOf(steps[(Math.max(0, i) + 1) % steps.length]));
            }
        }));

        // ---- Steering-wheel buttons
        g = "Steering-wheel buttons";
        r.add(new SettingsView.Row(g, "Learn a button", "press it when asked, then choose what it does", new Runnable() {
            @Override
            public void run() {
                learnButton(a);
            }
        }));
        for (final String[] k : Keys.map(p)) {
            r.add(new SettingsView.Row(g, k[1], Keys.actionName(k[2]), new Runnable() {
                @Override
                public void run() {
                    chooseAction(a, k[0], k[1], true);
                }
            }));
            if (Features.on(p, "longKeys")) {
                final String lng = Keys.longAction(p, k[0]);
                r.add(new SettingsView.Row(g, k[1] + ", held down", lng == null ? "nothing extra (tap to choose)" : Keys.actionName(lng), new Runnable() {
                    @Override
                    public void run() {
                        chooseLong(a, k[0], k[1]);
                    }
                }));
            }
        }
        r.add(new SettingsView.Row(g, "Buttons work in other apps too", KeyService.running ? "on" : "off: turn on OpenRath in Accessibility",
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Throwable e) {
                            Toast.makeText(a, Hindi.t("This unit has no accessibility settings screen"), Toast.LENGTH_LONG).show();
                        }
                    }
                }));

        // ---- Notifications
        g = "Notifications";
        r.add(new SettingsView.Row(g, "Ticker shows", "names".equals(p.str("tickerShow", "all")) ? "who it's from" : "who it's from and the message",
                new Runnable() {
                    @Override
                    public void run() {
                        p.put("tickerShow", "names".equals(p.str("tickerShow", "all")) ? "all" : "names");
                    }
                }));
        r.add(new SettingsView.Row(g, "Read aloud says", "all".equals(p.str("readAloudMode", "names")) ? "who it's from and the message" : "who it's from", new Runnable() {
            @Override
            public void run() {
                p.put("readAloudMode", "names".equals(p.str("readAloudMode", "names")) ? "all" : "names");
            }
        }));
        r.add(new SettingsView.Row(g, "Apps to listen to", Hindi.f("%1$s apps", Ticker.apps(p).size()), new Runnable() {
            @Override
            public void run() {
                tickerApps(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Notification access", a.hub.mediaAccess ? "allowed" : "needed: tap to allow", new Runnable() {
            @Override
            public void run() {
                try {
                    a.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } catch (Throwable e) {
                    Toast.makeText(a, Hindi.t("This unit has no notification access screen"), Toast.LENGTH_LONG).show();
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Try the ticker", null, new Runnable() {
            @Override
            public void run() {
                if (Ticker.post("OpenRath", "Ticker test", "Messages from your phone apps show here")) {
                    a.closeSettings();
                    ticker(a);
                }
            }
        }));

        // ---- Petrol price, mileage coach, breaks
        g = "Driving";
        r.add(new SettingsView.Row(g, "Petrol price", Fuel.priceText(p), new Runnable() {
            @Override
            public void run() {
                number(a, "Petrol price, ₹ a litre (empty: use your fill-ups)", p.str("fuelPrice", "0").equals("0") ? "" : p.str("fuelPrice", ""),
                        new Num() {
                            @Override
                            public void set(String v) {
                                p.put("fuelPrice", v.isEmpty() ? "0" : v);
                            }
                        });
            }
        }));
        r.add(new SettingsView.Row(g, "Mileage coach", Hindi.f("%1$s drives recorded", Coach.trips(p).size()), new Runnable() {
            @Override
            public void run() {
                Coach.show(a, p);
            }
        }));
        final int[] breaks = {90, 120, 150, 180};
        r.add(new SettingsView.Row(g, "Break reminder after", Hindi.f("%1$s hours of driving", p.num("breakMin", 120) / 60f % 1 == 0
                ? String.valueOf(p.num("breakMin", 120) / 60) : String.format(java.util.Locale.US, "%.1f", p.num("breakMin", 120) / 60f)),
                new Runnable() {
                    @Override
                    public void run() {
                        int cur = p.num("breakMin", 120), next = breaks[0];
                        for (int i = 0; i < breaks.length; i++) if (breaks[i] == cur) next = breaks[(i + 1) % breaks.length];
                        p.put("breakMin", String.valueOf(next));
                    }
                }));
        r.add(new SettingsView.Row(g, "Find a rest stop now", "dhabas, cafes and petrol pumps ahead", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                a.restStop();
            }
        }));

        // ---- Valet mode
        g = "Valet";
        r.add(new SettingsView.Row(g, "Valet mode", Valet.on(p) ? "On: Settings need the PIN" : "When someone else drives: caps the volume and speed, and Settings need your PIN", new Runnable() {
            @Override
            public void run() {
                Valet.toggle(a, p, new Runnable() {
                    @Override
                    public void run() {
                        a.valetBadge();
                    }
                });
            }
        }).toggle(Valet.on(p), null));
        final int[] vols = {30, 40, 50, 60, 70};
        r.add(new SettingsView.Row(g, "Volume limit", Hindi.f("%1$s%% of full volume", Valet.volumeCap(p)), new Runnable() {
            @Override
            public void run() {
                int cur = Valet.volumeCap(p), next = vols[0];
                for (int i = 0; i < vols.length; i++) if (vols[i] == cur) next = vols[(i + 1) % vols.length];
                p.put("valetVol", String.valueOf(next));
            }
        }));
        final int[] speeds = {60, 70, 80, 90, 100};
        r.add(new SettingsView.Row(g, "Speed limit in valet mode", Valet.speed(p) + " km/h", new Runnable() {
            @Override
            public void run() {
                int cur = Valet.speed(p), next = speeds[0];
                for (int i = 0; i < speeds.length; i++) if (speeds[i] == cur) next = speeds[(i + 1) % speeds.length];
                p.put("valetSpeed", String.valueOf(next));
            }
        }));
        r.add(new SettingsView.Row(g, Valet.pin(p).isEmpty() ? "Choose the PIN" : "Change the PIN", null, new Runnable() {
            @Override
            public void run() {
                Valet.choosePin(a, p, new Runnable() {
                    @Override
                    public void run() {
                        android.widget.Toast.makeText(a, Hindi.t("PIN saved"), android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }));

        // ---- Voice commands
        g = "Voice";
        final boolean hindi = "hi-IN".equals(p.str("voiceLang", "en-IN"));
        r.add(new SettingsView.Row(g, "Listen mainly for", hindi ? "Hindi (English words understood too)" : "English (Hindi words understood too)",
                new Runnable() {
                    @Override
                    public void run() {
                        p.put("voiceLang", hindi ? "en-IN" : "hi-IN");
                    }
                }));
        r.add(new SettingsView.Row(g, "Try a voice command", "or tap the mic on the map, or a steering-wheel button", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                a.voiceSearch(a.mapCard());
            }
        }));
        final String[] micPlaces = {"map", "float", "apps"};
        final String[] micNames = {"on the map only", "floating on the home screen", "floating, also over other apps"};
        String mp = Features.micPlace(p);
        r.add(new SettingsView.Row(g, "Mic button", Hindi.t(micNames[Math.max(0, java.util.Arrays.asList(micPlaces).indexOf(mp))])
                + ("apps".equals(mp) && !OverMic.allowed(a) ? Hindi.t("; needs permission, tap to allow") : ""), new Runnable() {
            @Override
            public void run() {
                if (OverMic.wanted(p) && !OverMic.allowed(a)) {
                    OverMic.ask(a);
                    return;
                }
                new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("Mic button"))
                        .setSingleChoiceItems(tr(micNames), java.util.Arrays.asList(micPlaces).indexOf(Features.micPlace(p)), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                p.put("micPlace", micPlaces[w]);
                                d.dismiss();
                                if (OverMic.wanted(p) && !OverMic.allowed(a)) OverMic.ask(a);
                                featuresChanged(a);
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        r.add(new SettingsView.Row(g, "What you can say", "radio, calls, music, volume, places", new Runnable() {
            @Override
            public void run() {
                new Glass.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(Hindi.t("What you can say"))
                        .setMessage(Hindi.t("Radio: \"FM 91.1\", \"radio chalao\", \"रेडियो 98.3\"\n"
                                + "Calls: \"call Mom\", \"papa ko call karo\", \"मम्मी को फोन लगाओ\" (uses your favourites)\n"
                                + "Music: \"play music\", \"gaana chalao\", \"next\", \"अगला\", \"pause\", \"रोको\"\n"
                                + "Volume: \"volume up\", \"awaaz kam karo\", \"आवाज़ बढ़ाओ\"\n"
                                + "Places: \"take me home\", \"घर ले चलो\", \"office\", \"petrol pump\", \"dhaba\", "
                                + "\"navigate to India Gate\"\n\nAnything else is searched on the map."))
                        .setPositiveButton(Hindi.t("OK"), null).show();
            }
        }));

        // ---- Night-drive preview
        r.add(new SettingsView.Row("Dashboard", "Preview night-drive mode", "for 15 seconds; tap the black area to end", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                a.nightPreviewUntil = System.currentTimeMillis() + 15000;
                a.nightDrive(a.hub);
                a.root.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        a.nightDrive(a.hub);
                    }
                }, 15500);
            }
        }));

        // ---- Fuel-low planner, commute, parking link
        g = "Driving";
        r.add(new SettingsView.Row(g, "Petrol left", FuelPlan.text(p), new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                MapCard m = a.mapCard();
                if (m != null) m.js("restStop('fuel')");
            }
        }));
        r.add(new SettingsView.Row(g, "Tank size", Hindi.f("%1$s litres", FuelPlan.tank(p)), new Runnable() {
            @Override
            public void run() {
                number(a, "cng".equals(Car.fuel(p)) ? "CNG cylinder, kg" : "Fuel tank, litres", String.valueOf(FuelPlan.tank(p)), new Num() {
                    @Override
                    public void set(String v) {
                        if (!v.isEmpty()) p.put("tankL", String.valueOf(Math.max(10, Math.min(90, Math.round(Float.parseFloat(v))))));
                    }
                });
            }
        }));
        final int[] lows = {40, 60, 80, 100};
        r.add(new SettingsView.Row(g, "Warn when the petrol is below", Hindi.f("%1$s km of driving", FuelPlan.warnKm(p)), new Runnable() {
            @Override
            public void run() {
                int cur = FuelPlan.warnKm(p), next = lows[0];
                for (int i = 0; i < lows.length; i++) if (lows[i] == cur) next = lows[(i + 1) % lows.length];
                p.put("fuelWarnKm", String.valueOf(next));
            }
        }));
        r.add(new SettingsView.Row(g, "Send the parking spot to your phone", Drive.parkAt == 0 ? "no spot saved yet" : "shows a QR code", new Runnable() {
            @Override
            public void run() {
                a.parkQr();
            }
        }));
        r.add(new SettingsView.Row(g, "Share the trip", "a QR code with where you are and the route", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                a.shareTrip();
            }
        }));

        r.add(new SettingsView.Row(g, "Split the cost", "a UPI QR code for each share", new Runnable() {
            @Override
            public void run() {
                Chores.split(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Your UPI id", p.str("upiId", "").isEmpty() ? "not set: asked the first time you split" : p.str("upiId", ""),
                new Runnable() {
                    @Override
                    public void run() {
                        p.put("upiId", "");
                        Chores.split(a);
                    }
                }));
        r.add(new SettingsView.Row(g, "Paid parking reminder", p.longNum("parkEnds", 0) > System.currentTimeMillis()
                ? Hindi.f("parking ends at %1$s", Errands.clock(p.longNum("parkEnds", 0))) : "a reminder on your phone; or say \"parking timer\"", new Runnable() {
            @Override
            public void run() {
                Chores.parkingTimer(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Pause parked music after", Hindi.f("%1$s minutes with the engine off", p.num("parkedMusicMin", 20)), new Runnable() {
            @Override
            public void run() {
                number(a, "Minutes before parked music pauses", String.valueOf(p.num("parkedMusicMin", 20)), new Num() {
                    @Override
                    public void set(String v) {
                        if (!v.isEmpty()) p.put("parkedMusicMin", String.valueOf(Math.max(5, (int) Double.parseDouble(v))));
                    }
                });
            }
        }));
        r.add(new SettingsView.Row(g, "Air quality now", Air.aqi < 0 ? "not known yet" : Hindi.f("%1$s, AQI %2$s", Hindi.t(Air.band(Air.aqi)), Air.aqi), new Runnable() {
            @Override
            public void run() {
            }
        }));
        r.add(new SettingsView.Row(g, "Start-up summary", "text".equals(p.str("summaryHow", "voice")) ? "shown only" : "spoken and shown, once a day",
                new Runnable() {
                    @Override
                    public void run() {
                        p.put("summaryHow", "text".equals(p.str("summaryHow", "voice")) ? "voice" : "text");
                    }
                }));

        r.add(new SettingsView.Row(g, "This week's report", "km, time, petrol, tolls, score; or say \"weekly report\"", new Runnable() {
            @Override
            public void run() {
                Chores.weekly(a, true);
            }
        }));
        r.add(new SettingsView.Row(g, "Last parking photo", Snap.file().exists() ? "tap to see it" : Dashcam.cameras() == 0 ? "needs a camera" : "none yet",
                new Runnable() {
                    @Override
                    public void run() {
                        Chores.parkPhoto(a);
                    }
                }));
        final Spots bumps = new Spots(0.0003, 12, 400), waits = new Spots(0.0005, 10, 300);
        bumps.load(p.str("bumps", ""));
        waits.load(p.str("waits", ""));
        int learnt = bumps.count(new Spots.Test() {
            @Override
            public boolean ok(Spots.Spot s) {
                return Spots.drives(s) >= 3;
            }
        });
        r.add(new SettingsView.Row(g, "Speed breakers learnt", Hindi.f("%1$s (%2$s places seen); tap to forget them", learnt, bumps.size()), new Runnable() {
            @Override
            public void run() {
                new Glass.Builder(a, DIALOG).setMessage(Hindi.t("Forget all learnt speed breakers?"))
                        .setPositiveButton(Hindi.t("Forget"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                p.put("bumps", "");
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Stops learnt", Hindi.f("%1$s signals and toll plazas; tap to forget them", waits.size()), new Runnable() {
            @Override
            public void run() {
                new Glass.Builder(a, DIALOG).setMessage(Hindi.t("Forget all learnt waiting times?"))
                        .setPositiveButton(Hindi.t("Forget"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                p.put("waits", "");
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));

        r.add(new SettingsView.Row("Phone", "Guest Wi-Fi", p.str("apName", "").isEmpty() ? "a code passengers scan to join the hotspot"
                : Hindi.f("\"%1$s\"; or say \"hotspot\"", p.str("apName", "")), new Runnable() {
            @Override
            public void run() {
                Chores.hotspot(a);
            }
        }));
        if (!p.str("apName", "").isEmpty()) r.add(new SettingsView.Row("Phone", "Change the hotspot name and password", "", new Runnable() {
            @Override
            public void run() {
                p.put("apName", "");
                Chores.hotspot(a);
            }
        }));

        // ---- The road ahead, fog, holidays
        g = "Driving";
        r.add(new SettingsView.Row(g, "Fog mode now", "on".equals(p.str("fogNow", "off")) ? "on (switched on by hand)"
                : Hub.fog ? "on: the weather says fog" : "off; comes on by itself in fog, or say \"fog mode\"", new Runnable() {
            @Override
            public void run() {
                p.put("fogNow", "on".equals(p.str("fogNow", "off")) ? "off" : "on");
                a.fogMode(a.hub);
            }
        }));
        r.add(new SettingsView.Row(g, "Speed in fog", Hindi.f("keep below %1$s km/h", Fog.guide(p)), new Runnable() {
            @Override
            public void run() {
                int[] s = {30, 40, 50, 60};
                int i = 0;
                while (i < s.length && s[i] != Fog.guide(p)) i++;
                p.put("fogSpeed", String.valueOf(s[(i + 1) % s.length]));
            }
        }));
        if (Money.india()) r.add(new SettingsView.Row("Dashboard", "Holidays ahead", android.text.TextUtils.join(";  ", Holidays.upcoming(java.util.Calendar.getInstance(), 3)), new Runnable() {
            @Override
            public void run() {
                new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Holidays with heavy traffic"))
                        .setMessage(android.text.TextUtils.join("\n", Holidays.upcoming(java.util.Calendar.getInstance(), 12))
                                + "")
                        .setPositiveButton(Hindi.t("OK"), null).show();
            }
        }));

        // ---- Monsoon mode
        g = "Monsoon";
        r.add(new SettingsView.Row(g, "Mark a waterlogging spot here", "or say \"waterlogging\", or set a steering-wheel button", new Runnable() {
            @Override
            public void run() {
                Monsoon.mark(a, p);
            }
        }));
        final int spots = Monsoon.spots(p).size();
        r.add(new SettingsView.Row(g, "Marked spots", spots == 0 ? "none yet" : Hindi.f(spots == 1 ? "%1$s spot; tap to clear" : "%1$s spots; tap to clear all", spots), new Runnable() {
            @Override
            public void run() {
                if (spots == 0) return;
                new Glass.Builder(a, DIALOG).setMessage(Hindi.f("Clear all %1$s waterlogging spots?", spots))
                        .setPositiveButton(Hindi.t("Clear"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Monsoon.clear(p);
                            }
                        }).setNegativeButton(Hindi.t("Keep"), null).show();
            }
        }));

        // ---- Dashcam
        g = "Dashcam";
        r.add(new SettingsView.Row(g, "Dashcam", Features.on(p, "dashcam") ? Dashcam.status : "off (switch above)", null));
        final int cams = Dashcam.cameras();
        r.add(new SettingsView.Row(g, "Camera", cams == 0 ? "none found" : Hindi.f("camera %1$s of %2$s", Math.max(0, Math.min(cams - 1, p.num("dashcamId", cams - 1))) + 1, cams),
                new Runnable() {
                    @Override
                    public void run() {
                        if (cams < 2) return;
                        p.put("dashcamId", String.valueOf((p.num("dashcamId", cams - 1) + 1) % cams));
                        Dashcam.stop();
                        if (Features.on(p, "dashcam")) Dashcam.start(a, p);
                    }
                }));
        r.add(new SettingsView.Row(g, "Space for recordings", Hindi.f("%1$s GB, oldest clips deleted first", p.num("dashcamGB", 4)), new Runnable() {
            @Override
            public void run() {
                List<Integer> c = Dashcam.spaceChoices();
                int i = c.indexOf(p.num("dashcamGB", 4));
                p.put("dashcamGB", String.valueOf(c.get((i + 1) % c.size())));
            }
        }));
        r.add(new SettingsView.Row(g, "Save the clip now", Hindi.f("%1$s saved", Dashcam.savedClips()), new Runnable() {
            @Override
            public void run() {
                Dashcam.protect();
                android.widget.Toast.makeText(a, Hindi.t("Saving the last two minutes"), android.widget.Toast.LENGTH_SHORT).show();
            }
        }));

        // ---- Service centres
        r.add(new SettingsView.Row("Car care", "Find a " + (Car.brandName(p).isEmpty() ? "" : Car.brandName(p) + " ") + "service centre", "nearest workshops, with a call button", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                MapCard m = a.mapCard();
                if (m != null) m.js("serviceCentres()");
                else android.widget.Toast.makeText(a, Hindi.t("Put the map on the dashboard first"), android.widget.Toast.LENGTH_LONG).show();
            }
        }));

        // ---- Documents and service booking
        r.add(new SettingsView.Row("Car care", "Car documents", Hindi.f("%1$s of 4 added", Wallet.count(a)), new Runnable() {
            @Override
            public void run() {
                if (Features.on(p, "wallet")) Wallet.menu(a);
            }
        }));
        String[] mine = p.str("myService", "").split("\\|", -1);
        r.add(new SettingsView.Row("Car care", "Your service centre", mine.length == 2 && !mine[0].isEmpty() ? mine[0] + (mine[1].isEmpty() ? Hindi.t(" (no phone number)") : ", " + mine[1])
                : "not set: tap Mine next to one in the service centre list", new Runnable() {
            @Override
            public void run() {
                final android.widget.EditText e = new android.widget.EditText(a);
                e.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                String[] m = p.str("myService", "").split("\\|", -1);
                e.setText(m.length == 2 ? m[1] : "");
                new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Service centre's phone number")).setView(e)
                        .setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                String[] m = p.str("myService", "").split("\\|", -1);
                                String name = m.length == 2 && !m[0].isEmpty() ? m[0] : "your service centre";
                                p.put("myService", name + "|" + e.getText().toString().replaceAll("[^0-9+]", ""));
                                if (a.settingsView != null) a.settingsView.refresh();
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));

        // ---- Child-lock and driver profiles
        g = "Family";
        r.add(new SettingsView.Row(g, "Play a game now", "spot the colour, number plates, how far", new Runnable() {
            @Override
            public void run() {
                if (Features.on(p, "kids")) {
                    a.closeSettings();
                    Kids.show(a, p);
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Apps locked while moving", ChildLock.apps(p).isEmpty() ? "none chosen" : Hindi.f("%1$s apps", ChildLock.apps(p).size()), new Runnable() {
            @Override
            public void run() {
                ChildLock.choose(a, p);
            }
        }));
        r.add(new SettingsView.Row(g, "Who's driving", Profiles.current(p).isEmpty() ? "no drivers yet: tap to add" : Profiles.current(p), new Runnable() {
            @Override
            public void run() {
                if (Features.on(p, "profiles")) Profiles.menu(a, p);
            }
        }));

        // ---- Sound (in Radio and music)
        g = "Radio and music";
        for (final String prof : Errands.SOUND) {
            final String nice = prof.substring(0, 1).toUpperCase(Locale.US) + prof.substring(1);
            String eq = Sound.eq(p, prof);
            int vol = p.num("sndVol:" + prof, Sound.vol(prof));
            r.add(new SettingsView.Row(g, nice + " sound", Hindi.f("%1$s, volume %2$s", eq.isEmpty() ? Hindi.t("your equalizer") : Hindi.t(eq), (vol > 0 ? "+" : "") + vol)
                    + (prof.equals(Drive.soundNow) ? Hindi.t("  · now") : ""), new Runnable() {
                @Override
                public void run() {
                    final String[] presets = new String[Eq.PRESETS.length];
                    presets[0] = Hindi.t("Your equalizer");
                    for (int i = 1; i < presets.length; i++) presets[i] = Hindi.t(Eq.PRESETS[i - 1]);
                    new Glass.Builder(a, DIALOG).setTitle(Hindi.f("%1$s sound: equalizer", Hindi.t(nice)))
                            .setItems(presets, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    p.put("sndEq:" + prof, w == 0 ? "" : Eq.PRESETS[w - 1]);
                                    number(a, Hindi.f("%1$s sound: volume steps (-5 to 5)", Hindi.t(nice)), String.valueOf(p.num("sndVol:" + prof, Sound.vol(prof))), new Num() {
                                        @Override
                                        public void set(String v) {
                                            if (!v.isEmpty()) p.put("sndVol:" + prof, String.valueOf(Math.max(-5, Math.min(5, (int) Double.parseDouble(v)))));
                                        }
                                    });
                                }
                            }).setNegativeButton(Hindi.t("Cancel"), null).show();
                }
            }));
        }
        final String[] perNames = {"off", "gently (a step every 30 km/h)", "medium (every 20 km/h)", "strongly (every 15 km/h)"};
        r.add(new SettingsView.Row(g, "How much louder", perNames[Math.max(1, Math.min(3, p.num("speedVolPer", 2)))],
                new Runnable() {
                    @Override
                    public void run() {
                        p.put("speedVolPer", String.valueOf(Math.max(1, Math.min(3, p.num("speedVolPer", 2))) % 3 + 1));
                        Drive.start(a);
                    }
                }));
        final int[] hours = {20, 21, 22, 23, 0};
        r.add(new SettingsView.Row(g, "Quiet hours start at", Quiet.hour(Quiet.from(p)), new Runnable() {
            @Override
            public void run() {
                int cur = Quiet.from(p), next = hours[0];
                for (int i = 0; i < hours.length; i++) if (hours[i] == cur) next = hours[(i + 1) % hours.length];
                p.put("quietFrom", String.valueOf(next));
            }
        }));
        final int[] ends = {5, 6, 7};
        r.add(new SettingsView.Row(g, "Quiet hours end at", Quiet.hour(Quiet.to(p)), new Runnable() {
            @Override
            public void run() {
                int cur = Quiet.to(p), next = ends[0];
                for (int i = 0; i < ends.length; i++) if (ends[i] == cur) next = ends[(i + 1) % ends.length];
                p.put("quietTo", String.valueOf(next));
            }
        }));
        final int[] qv = {20, 30, 40, 50};
        r.add(new SettingsView.Row(g, "Quiet-hours volume", Hindi.f("%1$s%% of full volume", p.num("quietVol", 40)), new Runnable() {
            @Override
            public void run() {
                int cur = p.num("quietVol", 40), next = qv[0];
                for (int i = 0; i < qv.length; i++) if (qv[i] == cur) next = qv[(i + 1) % qv.length];
                p.put("quietVol", String.valueOf(next));
            }
        }));
        r.add(new SettingsView.Row(g, "Equalizer settings", Hindi.t(p.str("eqPreset", "Flat")) + "  ·  " + Hindi.t(Eq.status), new Runnable() {
            @Override
            public void run() {
                equalizer(a);
            }
        }));

        // ---- Driving
        g = "Driving";
        String last = p.str("accelLast", "");
        String[] lf = last.split("\\|");
        r.add(new SettingsView.Row(g, "0–100 km/h", last.isEmpty() ? "no full run yet" : Hindi.f("last %1$s s", lf[0])
                + (lf.length > 1 && !lf[1].isEmpty() ? " (0–60 " + lf[1] + " s)" : "") + Hindi.f("  ·  best %1$s s", p.str("accelBest", "-")), new Runnable() {
            @Override
            public void run() {
                new Glass.Builder(a, DIALOG).setTitle(Hindi.t("0–100 km/h timer"))
                        .setMessage(Hindi.t("Stop completely, then accelerate. The time is spoken when you pass 100 km/h. It uses GPS (or OBD-II speed "
                                + "when connected), so it is good to about half a second. Only on a safe, empty road."))
                        .setPositiveButton(Hindi.t("OK"), null).setNeutralButton(Hindi.t("Forget the best time"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                p.put("accelBest", "");
                                p.put("accelLast", "");
                            }
                        }).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Export routes as GPX", Hindi.f("%1$s days recorded", Tracks.days(a).size()), new Runnable() {
            @Override
            public void run() {
                exportGpx(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Delete recorded routes", null, new Runnable() {
            @Override
            public void run() {
                new Glass.Builder(a, DIALOG).setMessage(Hindi.t("Delete every recorded route on the unit?"))
                        .setPositiveButton(Hindi.t("Delete"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Tracks.deleteAll(a);
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }));

        // ---- Backup
        g = "Backup";
        r.add(new SettingsView.Row(g, "Back up now", "settings, fuel log and trips", new Runnable() {
            @Override
            public void run() {
                try {
                    File f = Backup.make(a, "manual");
                    Toast.makeText(a, Hindi.f("Saved %1$s", f.getPath()), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(a, Hindi.f("Couldn't back up: %1$s", e.getMessage()), Toast.LENGTH_LONG).show();
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Restore from a backup", Hindi.f("%1$s found", Backup.found().size()), new Runnable() {
            @Override
            public void run() {
                restore(a);
            }
        }));
        long at = 0;
        try {
            at = Long.parseLong(p.str("backupLast", "0"));
        } catch (Exception ignored) {
            // not a number: keep 0
        }
        r.add(new SettingsView.Row(g, "Daily automatic backup", !Features.on(p, "backup") ? "off"
                : at == 0 ? "on; none yet" : Hindi.f("on; last %1$s", Cards.TripCard.ago(at)), new Runnable() {
            @Override
            public void run() {
                Features.set(p, "backup", !Features.on(p, "backup"));
                Drive.start(a);
            }
        }));

        // ---- Help
        g = "Help";
        r.add(new SettingsView.Row(g, "About and licences", "free and open source (GPL-3.0)", new Runnable() {
            @Override
            public void run() {
                About.show(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Privacy", "what leaves the unit, and what doesn't", new Runnable() {
            @Override
            public void run() {
                About.privacy(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Crash reports", Hindi.f("%1$s saved", Crash.files().size()), new Runnable() {
            @Override
            public void run() {
                About.crashes(a);
            }
        }));
        if (Features.on(p, "reports")) {
            r.add(new SettingsView.Row(g, "Usage report", p.str("reportUrl", "").isEmpty() ? "see it and share it" : "also sent daily to your organisation", new Runnable() {
                @Override
                public void run() {
                    About.usage(a);
                }
            }));
        }
    }

    /**
     * What a feature still needs before it can work, as the question to ask, or null when it's ready
     * (or needs nothing). Pure, for the tests: the setup state comes in as flags.
     */
    static String needs(String id, boolean indicators, boolean lightsOrDoors, boolean obdChosen, boolean tyresChosen,
                        boolean projectionFound) {
        if (("arrows".equals(id) || "cam360Turn".equals(id)) && !indicators)
            return "This needs the turn indicators set up. Set them up now?";
        if ("lightsReminder".equals(id) && !lightsOrDoors) return "This needs the headlight, door or seat-belt setup. Set up the headlights now?";
        if ("obd".equals(id) && !obdChosen) return "Choose your OBD-II Bluetooth adapter now?";
        if ("tpms".equals(id) && !tyresChosen) return "Find your tyre sensors now?";
        if (("projection".equals(id) || "projectionAuto".equals(id)) && !projectionFound) return "No CarPlay or Android Auto app was found. Choose it now?";
        return null;
    }

    /** Just switched on: if the feature needs setting up, offer to do it now instead of only saying so. */
    static void offerSetup(final HomeActivity a, final String id) {
        Prefs p = a.prefs;
        if (("overStrip".equals(id) || "callScreen".equals(id)) && !OverMic.allowed(a)) {
            new Glass.Builder(a, DIALOG).setTitle(Hindi.t(Features.get(id).name))
                    .setMessage(Hindi.t("This needs \"Display over other apps\" for OpenRath. Allow it now?"))
                    .setPositiveButton(Hindi.t("Allow"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            OverMic.ask(a);
                        }
                    }).setNegativeButton(Hindi.t("Later"), null).show();
            return;
        }
        boolean ind = p.signal("left") != null || p.signal("right") != null;
        boolean lights = p.lightSignal() != null || p.signal("door") != null || p.signal("belt") != null;
        boolean tyres = false;
        for (int i = 0; i < 4; i++) tyres |= !p.str("tpms" + i, "").isEmpty();
        boolean proj = !("projection".equals(id) || "projectionAuto".equals(id)) || Projection.app(a, p) != null;
        String q = needs(id, ind, lights, !p.str("obdMac", "").isEmpty(), tyres, proj);
        if (q == null) return;
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t(Features.get(id).name)).setMessage(Hindi.t(q))
                .setPositiveButton(Hindi.t("Set up now"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if ("arrows".equals(id) || "cam360Turn".equals(id)) {
                            Lights.deep = false;
                            Lights.indicatorSetup(a, a.prefs, new Lights.Done() {
                                @Override
                                public void done() {
                                    a.hub.stop();
                                    a.hub.start();
                                    a.hub.notifyIfChanged(true);
                                }
                            });
                        } else if ("lightsReminder".equals(id)) {
                            Lights.deep = false;
                            a.setupLights();
                        } else if ("obd".equals(id)) a.obdMenu();
                        else if ("tpms".equals(id)) a.tyresMenu();
                        else if ("projection".equals(id) || "projectionAuto".equals(id)) {
                            AppPicker.show(a, Hindi.t("Your CarPlay or Android Auto app"), new AppPicker.Picked() {
                                @Override
                                public void picked(String pkg) {
                                    a.prefs.setApp("projection", pkg);
                                    Projection.forget();
                                    a.refresh();
                                }
                            });
                        }
                    }
                }).setNegativeButton(Hindi.t("Later"), null).show();
    }

    static void featuresChanged(HomeActivity a) {
        if (Features.on(a.prefs, "dashcam") && !Dashcam.allowed(a))
            a.requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 3);
        Drive.start(a);   // starts or stops OBD, TPMS, backups to match
        a.hub.stop();
        a.hub.start();
        a.build();
        a.hub.notifyIfChanged(true);
    }

    private static void learnButton(final HomeActivity a) {
        final AlertDialog dlg = new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Press the steering-wheel button now"))
                .setMessage(Hindi.t("Press and release it once. Waiting for up to 15 seconds…"))
                .setNegativeButton(Hindi.t("Cancel"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Keys.stopLearning();
                    }
                }).show();
        // the dialog has the key focus now; send its key presses to the learner too
        dlg.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int code, KeyEvent e) {
                return code != KeyEvent.KEYCODE_BACK && Keys.onKey(e);
            }
        });
        Keys.learn(new Keys.Learnt() {
            @Override
            public void learnt(String sig, String label) {
                dlg.dismiss();
                chooseAction(a, sig, label, false);
            }
        });
        a.dash.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (dlg.isShowing()) {
                    Keys.stopLearning();
                    dlg.setMessage(Hindi.t("Nothing arrived. This button may be handled by the unit itself before any app sees it; "
                            + "try another one, or turn on \"Buttons work in other apps too\"."));
                }
            }
        }, 15000);
    }

    private static void chooseAction(final HomeActivity a, final String sig, final String label, boolean existing) {
        String[] items = tr(existing ? java.util.Arrays.copyOf(Keys.ACTION_NAMES, Keys.ACTION_NAMES.length + 1) : Keys.ACTION_NAMES);
        if (existing) items[items.length - 1] = Hindi.t("Forget this button");
        new Glass.Builder(a, DIALOG).setTitle(Hindi.f("What should %1$s do?", label))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Keys.put(a.prefs, sig, label, w < Keys.ACTIONS.length ? Keys.ACTIONS[w] : null);
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static void chooseLong(final HomeActivity a, final String sig, final String label) {
        final String[] items = tr(java.util.Arrays.copyOf(Keys.ACTION_NAMES, Keys.ACTION_NAMES.length + 1));
        items[items.length - 1] = Hindi.t("Nothing extra");
        new Glass.Builder(a, DIALOG).setTitle(Hindi.f("Holding %1$s down should", label))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Keys.putLong(a.prefs, sig, w < Keys.ACTIONS.length ? Keys.ACTIONS[w] : null);
                    }
                }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static void tickerApps(final HomeActivity a) {
        final Prefs p = a.prefs;
        PackageManager pm = a.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<String> pkgs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        List<ResolveInfo> l = pm.queryIntentActivities(main, 0);
        Collections.sort(l, new ResolveInfo.DisplayNameComparator(pm));
        for (ResolveInfo ri : l) {
            String pkg = ri.activityInfo.packageName;
            if (pkgs.contains(pkg) || pkg.equals(a.getPackageName())) continue;
            pkgs.add(pkg);
            names.add(String.valueOf(ri.loadLabel(pm)));
        }
        final List<String> chosen = Ticker.apps(p);
        final boolean[] on = new boolean[pkgs.size()];
        for (int i = 0; i < on.length; i++) on[i] = chosen.contains(pkgs.get(i));
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Show messages from"))
                .setMultiChoiceItems(names.toArray(new String[0]), on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w, boolean v) {
                        on[w] = v;
                    }
                }).setPositiveButton(Hindi.t("Save"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < on.length; i++) if (on[i]) sb.append(pkgs.get(i)).append(',');
                p.put("tickerApps", sb.length() == 0 ? "," : sb.toString());
            }
        }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static void equalizer(final HomeActivity a) {
        final Prefs p = a.prefs;
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(24), Ui.px(10), Ui.px(24), 0);
        final TextView presetT = Ui.text(a, 17, 0xFFEEEEEE, "sans-serif-medium", Gravity.LEFT);
        presetT.setPadding(0, Ui.px(8), 0, Ui.px(12));
        col.addView(presetT);
        final String[] bandNames = tr(new String[]{"60 Hz (bass)", "230 Hz", "910 Hz (voice)", "3.6 kHz", "14 kHz (treble)"});
        final SeekBar[] bars = new SeekBar[5];
        final TextView[] vals = new TextView[5];
        final int[] g = Eq.gains(p);
        final boolean[] quiet = {false};
        for (int k = 0; k < 5; k++) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView n = Ui.text(a, 14, 0xFFBBBBBB, "sans-serif", Gravity.LEFT);
            n.setText(bandNames[k]);
            row.addView(n, new LinearLayout.LayoutParams(Ui.px(150), -2));
            final SeekBar sb = new SeekBar(a);
            sb.setMax(24);
            sb.setProgress(g[k] + 12);
            bars[k] = sb;
            row.addView(sb, new LinearLayout.LayoutParams(0, -2, 1f));
            vals[k] = Ui.text(a, 14, 0xFFBBBBBB, "sans-serif", Gravity.RIGHT);
            vals[k].setText((g[k] > 0 ? "+" : "") + g[k] + " dB");
            row.addView(vals[k], new LinearLayout.LayoutParams(Ui.px(70), -2));
            final int band = k;
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int v, boolean user) {
                    vals[band].setText((v - 12 > 0 ? "+" : "") + (v - 12) + " dB");
                    if (!user || quiet[0]) return;
                    StringBuilder c = new StringBuilder();
                    for (SeekBar b : bars) c.append(b.getProgress() - 12).append(',');
                    p.put("eqCustom", c.toString());
                    p.put("eqPreset", "Custom");
                    presetT.setText(Hindi.f("Preset: %1$s  (tap to change)", Hindi.t("Custom")));
                    Eq.apply(a, p, 0);
                }

                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            col.addView(row);
        }
        final TextView bassT = Ui.text(a, 15, 0xFFEEEEEE, "sans-serif", Gravity.LEFT);
        bassT.setPadding(0, Ui.px(14), 0, Ui.px(14));
        bassT.setText(Hindi.f("Bass boost: %1$s / 10  (tap to change)", p.num("eqBass", 0)));
        bassT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                p.put("eqBass", String.valueOf((p.num("eqBass", 0) + 2) % 12));
                bassT.setText(Hindi.f("Bass boost: %1$s / 10  (tap to change)", p.num("eqBass", 0)));
                Eq.apply(a, p, 0);
            }
        });
        col.addView(bassT);
        presetT.setText(Hindi.f("Preset: %1$s  (tap to change)", Hindi.t(p.str("eqPreset", "Flat"))));
        presetT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = java.util.Arrays.asList(Eq.PRESETS).indexOf(p.str("eqPreset", "Flat"));
                String next = Eq.PRESETS[(i + 1) % Eq.PRESETS.length];
                p.put("eqPreset", next);
                presetT.setText(Hindi.f("Preset: %1$s  (tap to change)", Hindi.t(next)));
                int[] ng = Eq.gains(p);
                quiet[0] = true;
                for (int k = 0; k < 5; k++) bars[k].setProgress(ng[k] + 12);
                quiet[0] = false;
                Eq.apply(a, p, 0);
            }
        });
        Features.set(p, "eq", true);
        Eq.apply(a, p, 0);
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Equalizer")).setView(col)
                .setMessage(Hindi.t(Eq.status.startsWith("This unit") ? Eq.status : Eq.REACH))
                .setPositiveButton(Hindi.t("Done"), null)
                .setNeutralButton(Hindi.t("Turn off"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Features.set(p, "eq", false);
                        Eq.apply(a, p, 0);
                    }
                }).show();
    }

    private static void exportGpx(final HomeActivity a) {
        final List<String> days = Tracks.days(a);
        if (days.isEmpty()) {
            Toast.makeText(a, Hindi.t(Features.on(a.prefs, "gpx") ? "No routes recorded yet" : "Route recording is off in Settings > Driving"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        final String[] items = new String[days.size() + 1];
        items[0] = Hindi.f("All %1$s days", days.size());
        for (int i = 0; i < days.size(); i++) items[i + 1] = days.get(i);
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Export which day?")).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                int n = 0;
                File last = null;
                try {
                    for (int i = 0; i < days.size(); i++) {
                        if (w != 0 && i != w - 1) continue;
                        last = Tracks.export(a, days.get(i));
                        n++;
                    }
                    Toast.makeText(a, n == 1 ? Hindi.f("Saved %1$s", last.getPath()) : Hindi.f("Saved %1$s files in %2$s", n, last.getParent()), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(a, Hindi.f("Couldn't export: %1$s", e.getMessage()), Toast.LENGTH_LONG).show();
                }
            }
        }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    private static void restore(final HomeActivity a) {
        final List<File> files = Backup.found();
        if (files.isEmpty()) {
            Toast.makeText(a, Hindi.t("No backups in OpenRath/backup or Download"), Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[files.size()];
        for (int i = 0; i < names.length; i++) names[i] = files.get(i).getName();
        new Glass.Builder(a, DIALOG).setTitle(Hindi.t("Restore which backup?")).setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, final int w) {
                new Glass.Builder(a, DIALOG).setTitle(Backup.label(files.get(w)))
                        .setMessage(Hindi.t("Replace every setting, the fuel log and trip history with this backup? "
                        + "A copy of what's here now is saved first."))
                        .setPositiveButton(Hindi.t("Restore"), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d2, int x) {
                                try {
                                    Backup.make(a, "before-restore");
                                    Backup.restore(a, files.get(w));
                                    Toast.makeText(a, Hindi.t("Restored; restarting"), Toast.LENGTH_SHORT).show();
                                    a.dash.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            // a launcher is started again by Android straight away, now reading the restored settings
                                            android.os.Process.killProcess(android.os.Process.myPid());
                                        }
                                    }, 1200);
                                } catch (Exception e) {
                                    Toast.makeText(a, Hindi.f("Couldn't restore: %1$s", e.getMessage()), Toast.LENGTH_LONG).show();
                                }
                            }
                        }).setNegativeButton(Hindi.t("Cancel"), null).show();
            }
        }).setNegativeButton(Hindi.t("Cancel"), null).show();
    }

    /** A translated copy of a list shown in a dialog; the original stays English. */
    private static String[] tr(String[] in) {
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : Hindi.t(in[i]);
        return out;
    }

    private Extras() {
    }
}
