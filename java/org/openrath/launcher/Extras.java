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
            a.dash.postDelayed(new Runnable() {
                @Override
                public void run() {
                    resumeAudio(a);
                }
            }, 6000);   // the radio service and the audio system need a moment after boot
        }
    }

    static void resumeAudio(HomeActivity a) {
        Prefs p = a.prefs;
        String what = p.str("resume", "");
        Resume.restoreVolume(a, p);
        if (what.isEmpty()) return;
        try {
            AudioManager am = (AudioManager) a.getSystemService(android.content.Context.AUDIO_SERVICE);
            if (am.isMusicActive()) return;   // the unit already carried on by itself
            if ("fm".equals(what)) {
                Cards.MediaCard.owner = "radio";
                String err = Fm.play(p, a.hub.xt, a.hub.fm, true);
                a.startRadioService();
                if (err != null) a.startRadio();   // fall back to opening the radio app, which tunes in
            } else if (Player.WEB.equals(what) || Player.FILES.equals(what)) {
                Cards.MediaCard.owner = Player.WEB.equals(what) ? "radio" : "music";
                Player.send(a, Player.PLAY, what);
            } else if (what.startsWith("app:")) {
                Cards.MediaCard.owner = "music";
                key(a, KeyEvent.KEYCODE_MEDIA_PLAY);
            }
            Toast.makeText(a, "Carrying on where you left off", Toast.LENGTH_SHORT).show();
            if (!"text".equals(a.prefs.str("summaryHow", "voice"))) Speak.say(a, "Welcome back. Carrying on where you left off.", false);
        } catch (Throwable ignored) {
        }
        a.hub.notifyIfChanged(true);
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
        if ("radio".equals(Cards.MediaCard.owner) && h.xt != null && h.xt.ready()) {
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
    private static View cover;
    private static boolean dark;
    private static Runnable saverCheck;

    static void touched(HomeActivity a) {
        lastTouch = System.currentTimeMillis();
    }

    private static HomeActivity saverOwner;

    private static Runnable saverCheck(final HomeActivity a) {
        if (saverOwner != a) saverCheck = null;   // the dashboard was recreated
        saverOwner = a;
        if (saverCheck == null) saverCheck = new Runnable() {
            @Override
            public void run() {
                if (!HomeActivity.visible) return;
                int min = a.prefs.num("saverMin", 5);
                boolean parked = System.currentTimeMillis() - Drive.fixAt > 10000 || Drive.kmhNow < 5;
                if (cover == null && Features.on(a.prefs, "saver") && parked
                        && System.currentTimeMillis() - lastTouch > min * 60000L) showCover(a, false);
                a.dash.postDelayed(this, 20000);
            }
        };
        return saverCheck;
    }

    /** Remove the screensaver or screen-off cover; true if one was showing. */
    static boolean wake(HomeActivity a) {
        lastTouch = System.currentTimeMillis();
        if (cover == null) return false;
        a.root.removeView(cover);
        cover = null;
        if (dark) {
            android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            a.getWindow().setAttributes(lp);
            dark = false;
        }
        return true;
    }

    static void screenOff(HomeActivity a) {
        if (cover != null && dark) {
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
        cover = f;
        if (off) {
            android.view.WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.screenBrightness = 0.01f;
            a.getWindow().setAttributes(lp);
            dark = true;
        }
    }

    // ------------------------------------------------------------------ notification ticker

    private static View strip;
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
        if (strip != null) a.root.removeView(strip);
        LinearLayout s = new LinearLayout(a);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(Ui.px(18), Ui.px(10), Ui.px(18), Ui.px(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.RADIUS * Ui.k);
        g.setColor(Ui.PANEL_TOP | 0xFF000000);
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
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.setMargins(Ui.px(140), Ui.px(10), Ui.px(140), 0);
        a.root.addView(s, lp);
        strip = s;
        s.setTranslationY(-Ui.px(90));
        s.animate().translationY(0).setDuration(250).start();
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
        v.animate().translationY(-Ui.px(90)).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                a.root.removeView(v);
                if (strip == v) strip = null;
            }
        }).start();
    }

    // ------------------------------------------------------------------ widgets from other apps

    private static int pendingId = -1;
    private static String pendingContent;

    static void pickWidget(final HomeActivity a, final String content) {
        if (!Features.on(a.prefs, "widgets")) {
            Toast.makeText(a, "Switch on Widgets from other apps in Settings > Home screen first", Toast.LENGTH_LONG).show();
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
        items[0] = "No widget (empty box)";
        for (int k = 0; k < order.size(); k++) {
            sorted.add(all.get(order.get(k)));
            items[k + 1] = names.get(order.get(k));
        }
        new AlertDialog.Builder(a, DIALOG).setTitle(all.isEmpty() ? "No apps on the unit offer widgets" : "Choose a widget")
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
                                Toast.makeText(a, "This unit doesn't let launchers add widgets", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
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
        String[] items = {"Adapter: " + (mac.isEmpty() ? "choose…" : p.str("obdName", mac)), "Read fault codes",
                "Clear fault codes…", "Status: " + Obd.status + (Obd.protocol.isEmpty() ? "" : " (" + Obd.protocol + ")")};
        new AlertDialog.Builder(a, DIALOG).setTitle("Engine (OBD-II)").setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                if (w == 0) chooseAdapter(a);
                else if (w == 1) readCodes(a);
                else if (w == 2) clearCodes(a);
            }
        }).setNegativeButton("Close", null).show();
    }

    static void chooseAdapter(final HomeActivity a) {
        final List<String> devs = Obd.paired();
        if (devs.isEmpty()) {
            new AlertDialog.Builder(a, DIALOG).setTitle("No paired Bluetooth devices")
                    .setMessage("Plug the ELM327 adapter into the OBD socket under the steering wheel, switch the ignition on, "
                            + "and pair it in the unit's Bluetooth settings (the PIN is usually 1234 or 0000). Then come back here.")
                    .setPositiveButton("Bluetooth settings", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                a.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                            } catch (Throwable ignored) {
                            }
                        }
                    }).setNegativeButton("Close", null).show();
            return;
        }
        String[] names = new String[devs.size()];
        for (int i = 0; i < names.length; i++) names[i] = devs.get(i).replace("|", "   ");
        new AlertDialog.Builder(a, DIALOG).setTitle("Which one is the OBD-II adapter?").setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                String[] f = devs.get(w).split("\\|");
                a.prefs.put("obdName", f[0]);
                a.prefs.put("obdMac", f[1]);
                Obd.stop();
                Features.set(a.prefs, "obd", true);
                Drive.start(a);
                Toast.makeText(a, "Connecting to " + f[0] + "; keep the ignition on", Toast.LENGTH_LONG).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    static void readCodes(final HomeActivity a) {
        if (!Obd.connected) {
            Toast.makeText(a, "Not connected to the adapter: " + Obd.status, Toast.LENGTH_LONG).show();
            return;
        }
        final long asked = System.currentTimeMillis();
        Obd.readCodes();
        Toast.makeText(a, "Asking the engine computer…", Toast.LENGTH_SHORT).show();
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
                    for (String c : Obd.codes) sb.append(c).append("  ").append(Obd.meaning(c)).append('\n');
                }
                new AlertDialog.Builder(a, DIALOG).setTitle(Obd.codesAt < asked ? "No answer from the engine computer"
                        : sb.length() == 0 ? "No fault codes" : "Fault codes")
                        .setMessage(sb.length() == 0 ? "The engine computer reports nothing stored." : sb.toString().trim()
                                + "\n\nA code points at where to look; a mechanic can confirm the cause.")
                        .setPositiveButton("OK", null).show();
            }
        }, 1500);
    }

    static void clearCodes(final HomeActivity a) {
        new AlertDialog.Builder(a, DIALOG).setTitle("Clear the fault codes?")
                .setMessage("This also turns off the check-engine light until the fault is seen again. Only clear codes after "
                        + "the problem has been fixed. Do it with the engine off and the ignition on.")
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Obd.clearCodes();
                        Toast.makeText(a, "Cleared", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static void tyresMenu(final HomeActivity a) {
        final Prefs p = a.prefs;
        String[] items = {"Find my tyre sensors", "Units: " + p.str("tpmsUnit", "psi"), "Warn below " + p.num("tpmsLow", 28) + " psi",
                "Status: " + Tpms.status};
        new AlertDialog.Builder(a, DIALOG).setTitle("Tyre pressure").setItems(items, new DialogInterface.OnClickListener() {
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
        }).setNegativeButton("Close", null).show();
    }

    /** Listen for sensors for a while, then let the owner say which wheel each one is on. */
    static void findSensors(final HomeActivity a) {
        Features.set(a.prefs, "tpms", true);
        Drive.start(a);
        Tpms.start(a, a.prefs);
        final TextView t = Ui.text(a, 16, 0xFFDDDDDD, "sans-serif", Gravity.LEFT);
        t.setSingleLine(false);
        t.setPadding(Ui.px(24), Ui.px(16), Ui.px(24), Ui.px(8));
        t.setText("Listening… Sensors send every few seconds while the car moves, and less often when parked. "
                + "Driving slowly or letting a little air out wakes them up.");
        final AlertDialog dlg = new AlertDialog.Builder(a, DIALOG).setTitle("Finding tyre sensors").setView(t)
                .setPositiveButton("Assign wheels", null).setNegativeButton("Close", null).show();
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
            Toast.makeText(a, "No sensors heard yet", Toast.LENGTH_LONG).show();
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
            Toast.makeText(a, "All four sensors placed", Toast.LENGTH_LONG).show();
            return;
        }
        final String[] items = ids.toArray(new String[0]);
        new AlertDialog.Builder(a, DIALOG).setTitle("Tap a sensor to choose its wheel").setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                final String id = items[w];
                new AlertDialog.Builder(a, DIALOG).setTitle(id).setItems(new String[]{"Front left", "Front right", "Rear left", "Rear right"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d2, int wheel) {
                                a.prefs.put("tpms" + wheel, id);
                                assign(a);
                            }
                        }).show();
            }
        }).setNegativeButton("Done", null).show();
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
        new android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setView(box)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String v = e.getText().toString().trim();
                        try {
                            if (!v.isEmpty()) Double.parseDouble(v);
                            then.set(v);
                        } catch (Exception ex) {
                            android.widget.Toast.makeText(e.getContext(), "Enter a number", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null).show();
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
            r.add(new SettingsView.Row("Features", f.name, f.about, new Runnable() {
                @Override
                public void run() {
                    Features.set(p, f.id, !Features.on(p, f.id));
                    featuresChanged(a);
                }
            }).toggle(on, f.id));
        }

        Cab.rows(a, r);

        // ---- Dashboard extras
        if (Features.on(p, "pages")) {
            r.add(new SettingsView.Row("Dashboard", "Number of pages", String.valueOf(p.pages()) + "  (swipe sideways)", new Runnable() {
                @Override
                public void run() {
                    p.put("pages", String.valueOf(p.pages() >= Prefs.MAX_PAGES ? 2 : p.pages() + 1));
                    a.build();
                }
            }));
        }
        r.add(new SettingsView.Row("Dashboard", "Clock screensaver after", Features.on(p, "saver") ? p.num("saverMin", 5) + " min idle while parked" : "off (switch above)",
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
        r.add(new SettingsView.Row(g, "Read fault codes", Obd.codesAt == 0 ? null : Obd.codes.size() + " found last time", new Runnable() {
            @Override
            public void run() {
                readCodes(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Clear fault codes", "after a repair", new Runnable() {
            @Override
            public void run() {
                clearCodes(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Warn when the coolant is above", p.num("obdHot", 105) + " °C", new Runnable() {
            @Override
            public void run() {
                int[] steps = {100, 105, 110, 115};
                int i = java.util.Arrays.binarySearch(steps, p.num("obdHot", 105));
                p.put("obdHot", String.valueOf(steps[(Math.max(0, i) + 1) % steps.length]));
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
                            Toast.makeText(a, "This unit has no accessibility settings screen", Toast.LENGTH_LONG).show();
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
        r.add(new SettingsView.Row(g, "Read aloud", !Features.on(p, "readAloud") ? "off"
                : "all".equals(p.str("readAloudMode", "names")) ? "who it's from and the message" : "who it's from", new Runnable() {
            @Override
            public void run() {
                if (!Features.on(p, "readAloud")) Features.set(p, "readAloud", true);
                else if ("names".equals(p.str("readAloudMode", "names"))) p.put("readAloudMode", "all");
                else {
                    p.put("readAloudMode", "names");
                    Features.set(p, "readAloud", false);
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Apps to listen to", Ticker.apps(p).size() + " apps", new Runnable() {
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
                    Toast.makeText(a, "This unit has no notification access screen", Toast.LENGTH_LONG).show();
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
        r.add(new SettingsView.Row(g, "Mileage coach", Coach.trips(p).size() + " drives recorded", new Runnable() {
            @Override
            public void run() {
                Coach.show(a, p);
            }
        }));
        final int[] breaks = {90, 120, 150, 180};
        r.add(new SettingsView.Row(g, "Break reminder after", p.num("breakMin", 120) / 60f % 1 == 0
                ? p.num("breakMin", 120) / 60 + " hours of driving" : String.format(java.util.Locale.US, "%.1f hours of driving", p.num("breakMin", 120) / 60f),
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

        // ---- Kids or valet mode
        g = "Valet";
        r.add(new SettingsView.Row(g, "Kids or valet mode", Valet.on(p) ? "On: Settings need the PIN" : "Limits volume and speed, locks Settings", new Runnable() {
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
        r.add(new SettingsView.Row(g, "Volume limit", Valet.volumeCap(p) + "% of full volume", new Runnable() {
            @Override
            public void run() {
                int cur = Valet.volumeCap(p), next = vols[0];
                for (int i = 0; i < vols.length; i++) if (vols[i] == cur) next = vols[(i + 1) % vols.length];
                p.put("valetVol", String.valueOf(next));
            }
        }));
        final int[] speeds = {60, 70, 80, 90, 100};
        r.add(new SettingsView.Row(g, "Speed warning above", Valet.speed(p) + " km/h", new Runnable() {
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
                        android.widget.Toast.makeText(a, "PIN saved", android.widget.Toast.LENGTH_SHORT).show();
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
        r.add(new SettingsView.Row(g, "Mic over other apps", !OverMic.wanted(p) ? "off (Settings > Features)"
                : OverMic.allowed(a) ? "on; drag it anywhere over maps and music" : "needs permission; tap to allow", new Runnable() {
            @Override
            public void run() {
                if (OverMic.wanted(p) && !OverMic.allowed(a)) OverMic.ask(a);
            }
        }));
        r.add(new SettingsView.Row(g, "What you can say", "radio, calls, music, volume, places", new Runnable() {
            @Override
            public void run() {
                new android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert).setTitle("What you can say")
                        .setMessage("Radio: \"FM 91.1\", \"radio chalao\", \"रेडियो 98.3\"\n"
                                + "Calls: \"call Mom\", \"papa ko call karo\", \"मम्मी को फोन लगाओ\" (uses your favourites)\n"
                                + "Music: \"play music\", \"gaana chalao\", \"next\", \"अगला\", \"pause\", \"रोको\"\n"
                                + "Volume: \"volume up\", \"awaaz kam karo\", \"आवाज़ बढ़ाओ\"\n"
                                + "Places: \"take me home\", \"घर ले चलो\", \"office\", \"petrol pump\", \"dhaba\", "
                                + "\"navigate to India Gate\"\n\nAnything else is searched on the map.")
                        .setPositiveButton("OK", null).show();
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
        r.add(new SettingsView.Row(g, "Petrol left", Features.on(p, "fuelPlan") ? FuelPlan.text(p) : "off (switch above)", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                Cards.MapCard m = a.mapCard();
                if (m != null) m.js("restStop('fuel')");
            }
        }));
        r.add(new SettingsView.Row(g, "Tank size", FuelPlan.tank(p) + " litres", new Runnable() {
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
        r.add(new SettingsView.Row(g, "Warn when the petrol is below", FuelPlan.warnKm(p) + " km of driving", new Runnable() {
            @Override
            public void run() {
                int cur = FuelPlan.warnKm(p), next = lows[0];
                for (int i = 0; i < lows.length; i++) if (lows[i] == cur) next = lows[(i + 1) % lows.length];
                p.put("fuelWarnKm", String.valueOf(next));
            }
        }));
        r.add(new SettingsView.Row(g, "Commute insights", "the quickest time to leave, from your drives", new Runnable() {
            @Override
            public void run() {
                Commute.show(a, p);
            }
        }));
        r.add(new SettingsView.Row(g, "Send the parking spot to your phone", Drive.parkAt == 0 ? "no spot saved yet" : "shows a QR code", new Runnable() {
            @Override
            public void run() {
                a.parkQr();
            }
        }));
        r.add(new SettingsView.Row(g, "Share the trip", Features.on(p, "tripShare") ? "a QR code with where you are and the route; or say \"share my trip\""
                : "off (Settings > Features)", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                a.shareTrip();
            }
        }));

        r.add(new SettingsView.Row(g, "Split the cost", Features.on(p, "split") ? "a UPI QR code for each share; or say \"split the cost\""
                : "off (Settings > Features)", new Runnable() {
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
                ? "parking ends at " + Errands.clock(p.longNum("parkEnds", 0)) : "a reminder on your phone; or say \"parking timer\"", new Runnable() {
            @Override
            public void run() {
                Chores.parkingTimer(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Pause parked music after", p.num("parkedMusicMin", 20) + " minutes with the engine off", new Runnable() {
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
        r.add(new SettingsView.Row(g, "Air quality now", Air.aqi < 0 ? "not known yet" : Air.band(Air.aqi) + ", AQI " + Air.aqi, new Runnable() {
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
        r.add(new SettingsView.Row(g, "Car washed today", p.longNum("washAt", 0) == 0 ? "tap after a wash"
                : "last wash " + (System.currentTimeMillis() - p.longNum("washAt", 0)) / 86400000L + " days ago", new Runnable() {
            @Override
            public void run() {
                p.putLong("washAt", System.currentTimeMillis());
                Toast.makeText(a, "Noted. The next reminder is in " + p.num("washDays", 14) + " days.", Toast.LENGTH_SHORT).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Wash reminder every", p.num("washDays", 14) + " days", new Runnable() {
            @Override
            public void run() {
                number(a, "Days between car washes", String.valueOf(p.num("washDays", 14)), new Num() {
                    @Override
                    public void set(String v) {
                        if (!v.isEmpty()) p.put("washDays", String.valueOf(Math.max(3, (int) Double.parseDouble(v))));
                    }
                });
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
        r.add(new SettingsView.Row(g, "Speed breakers learnt", learnt + " (" + bumps.size() + " places seen); tap to forget them", new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, DIALOG).setMessage("Forget all learnt speed breakers?")
                        .setPositiveButton("Forget", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                p.put("bumps", "");
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Stops learnt", waits.size() + " signals and toll plazas; tap to forget them", new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, DIALOG).setMessage("Forget all learnt waiting times?")
                        .setPositiveButton("Forget", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                p.put("waits", "");
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));

        r.add(new SettingsView.Row("Phone", "Guest Wi-Fi", p.str("apName", "").isEmpty() ? "a code passengers scan to join the hotspot"
                : "\"" + p.str("apName", "") + "\"; or say \"hotspot\"", new Runnable() {
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

        // ---- The road ahead, fog, accident help, holidays
        g = "Driving";
        r.add(new SettingsView.Row(g, "Fog mode now", "on".equals(p.str("fogNow", "off")) ? "on (switched on by hand)"
                : Hub.fog ? "on: the weather says fog" : "off; comes on by itself in fog, or say \"fog mode\"", new Runnable() {
            @Override
            public void run() {
                p.put("fogNow", "on".equals(p.str("fogNow", "off")) ? "off" : "on");
                a.fogMode(a.hub);
            }
        }));
        r.add(new SettingsView.Row(g, "Speed in fog", "keep below " + Fog.guide(p) + " km/h", new Runnable() {
            @Override
            public void run() {
                int[] s = {30, 40, 50, 60};
                int i = 0;
                while (i < s.length && s[i] != Fog.guide(p)) i++;
                p.put("fogSpeed", String.valueOf(s[(i + 1) % s.length]));
            }
        }));
        r.add(new SettingsView.Row(g, "Emergency contact", Rescue.contact(p).isEmpty() ? "not set (accident help calls 112 only if you tap it)"
                : Rescue.contactName(p) + (Features.on(p, "accidentHelp") ? "" : " (accident help is off)"), new Runnable() {
            @Override
            public void run() {
                Rescue.edit(a, p, new Runnable() {
                    @Override
                    public void run() {
                        if (a.settingsView != null) a.settingsView.refresh();
                    }
                });
            }
        }));
        r.add(new SettingsView.Row(g, "Try accident help", "shows the countdown; doesn't call", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                Rescue.show(a, p, Drive.latNow, Drive.lonNow, true);
            }
        }));
        r.add(new SettingsView.Row(g, "Holidays ahead", android.text.TextUtils.join(";  ", Holidays.upcoming(java.util.Calendar.getInstance(), 3)), new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, DIALOG).setTitle("Holidays with heavy traffic")
                        .setMessage(android.text.TextUtils.join("\n", Holidays.upcoming(java.util.Calendar.getInstance(), 12))
                                + "\n\nThe warning comes once a day when you reach highway speed, from the day before (two days for Diwali) "
                                + "and over long weekends.")
                        .setPositiveButton("OK", null).show();
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
        r.add(new SettingsView.Row(g, "Marked spots", spots == 0 ? "none yet" : spots + (spots == 1 ? " spot; tap to clear" : " spots; tap to clear all"), new Runnable() {
            @Override
            public void run() {
                if (spots == 0) return;
                new AlertDialog.Builder(a, DIALOG).setMessage("Clear all " + spots + " waterlogging spots?")
                        .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Monsoon.clear(p);
                            }
                        }).setNegativeButton("Keep", null).show();
            }
        }));

        // ---- Dashcam
        g = "Dashcam";
        r.add(new SettingsView.Row(g, "Dashcam", Features.on(p, "dashcam") ? Dashcam.status : "off (switch above)", null));
        final int cams = Dashcam.cameras();
        r.add(new SettingsView.Row(g, "Camera", cams == 0 ? "none found" : "camera " + (Math.max(0, Math.min(cams - 1, p.num("dashcamId", cams - 1))) + 1) + " of " + cams,
                new Runnable() {
                    @Override
                    public void run() {
                        if (cams < 2) return;
                        p.put("dashcamId", String.valueOf((p.num("dashcamId", cams - 1) + 1) % cams));
                        Dashcam.stop();
                        if (Features.on(p, "dashcam")) Dashcam.start(a, p);
                    }
                }));
        r.add(new SettingsView.Row(g, "Space for recordings", p.num("dashcamGB", 4) + " GB, oldest clips deleted first", new Runnable() {
            @Override
            public void run() {
                List<Integer> c = Dashcam.spaceChoices();
                int i = c.indexOf(p.num("dashcamGB", 4));
                p.put("dashcamGB", String.valueOf(c.get((i + 1) % c.size())));
            }
        }));
        r.add(new SettingsView.Row(g, "Save the clip now", Dashcam.savedClips() + " saved in OpenRath/dashcam/saved", new Runnable() {
            @Override
            public void run() {
                Dashcam.protect();
                android.widget.Toast.makeText(a, "Saving the last two minutes", android.widget.Toast.LENGTH_SHORT).show();
            }
        }));

        // ---- Service centres
        r.add(new SettingsView.Row("Car care", "Find a " + (Car.brandName(p).isEmpty() ? "" : Car.brandName(p) + " ") + "service centre", "nearest workshops, with a call button", new Runnable() {
            @Override
            public void run() {
                a.closeSettings();
                Cards.MapCard m = a.mapCard();
                if (m != null) m.js("serviceCentres()");
                else android.widget.Toast.makeText(a, "Put the map on the dashboard first", android.widget.Toast.LENGTH_LONG).show();
            }
        }));

        // ---- Documents and service booking
        r.add(new SettingsView.Row("Car care", "Car documents", Features.on(p, "wallet") ? Wallet.count(a) + " of 4 added; say \"documents\" to show"
                : "off (switch above)", new Runnable() {
            @Override
            public void run() {
                if (Features.on(p, "wallet")) Wallet.menu(a);
            }
        }));
        String[] mine = p.str("myService", "").split("\\|", -1);
        r.add(new SettingsView.Row("Car care", "Your service centre", mine.length == 2 && !mine[0].isEmpty() ? mine[0] + (mine[1].isEmpty() ? " (no phone number)" : ", " + mine[1])
                : "not set: tap Mine next to one in the service centre list", new Runnable() {
            @Override
            public void run() {
                final android.widget.EditText e = new android.widget.EditText(a);
                e.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                String[] m = p.str("myService", "").split("\\|", -1);
                e.setText(m.length == 2 ? m[1] : "");
                new AlertDialog.Builder(a, DIALOG).setTitle("Service centre's phone number").setView(e)
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                String[] m = p.str("myService", "").split("\\|", -1);
                                String name = m.length == 2 && !m[0].isEmpty() ? m[0] : "your service centre";
                                p.put("myService", name + "|" + e.getText().toString().replaceAll("[^0-9+]", ""));
                                if (a.settingsView != null) a.settingsView.refresh();
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));

        // ---- Child-lock and driver profiles
        g = "Family";
        r.add(new SettingsView.Row(g, "Trip games for children", Features.on(p, "kids") ? "spot the colour, number plates, how far; say \"games\""
                : "off (switch above)", new Runnable() {
            @Override
            public void run() {
                if (Features.on(p, "kids")) {
                    a.closeSettings();
                    Kids.show(a, p);
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Apps locked while moving", ChildLock.apps(p).isEmpty() ? "none chosen" : ChildLock.apps(p).size() + " apps", new Runnable() {
            @Override
            public void run() {
                ChildLock.choose(a, p);
            }
        }));
        r.add(new SettingsView.Row(g, "Who's driving", !Features.on(p, "profiles") ? "off (switch above)"
                : Profiles.current(p).isEmpty() ? "no drivers yet: tap to add" : Profiles.current(p), new Runnable() {
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
            r.add(new SettingsView.Row(g, nice + " sound", (Features.on(p, "soundProfiles") ? "" : "(profiles off) ")
                    + (eq.isEmpty() ? "your equalizer" : eq) + ", volume " + (vol > 0 ? "+" : "") + vol
                    + (prof.equals(Drive.soundNow) ? "  · now" : ""), new Runnable() {
                @Override
                public void run() {
                    final String[] presets = new String[Eq.PRESETS.length];
                    presets[0] = "Your equalizer";
                    System.arraycopy(Eq.PRESETS, 0, presets, 1, Eq.PRESETS.length - 1);
                    new AlertDialog.Builder(a, DIALOG).setTitle(nice + " sound: equalizer")
                            .setItems(presets, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    p.put("sndEq:" + prof, w == 0 ? "" : Eq.PRESETS[w - 1]);
                                    number(a, nice + " sound: volume steps (-5 to 5)", String.valueOf(p.num("sndVol:" + prof, Sound.vol(prof))), new Num() {
                                        @Override
                                        public void set(String v) {
                                            if (!v.isEmpty()) p.put("sndVol:" + prof, String.valueOf(Math.max(-5, Math.min(5, (int) Double.parseDouble(v)))));
                                        }
                                    });
                                }
                            }).setNegativeButton("Cancel", null).show();
                }
            }));
        }
        r.add(new SettingsView.Row(g, "Resume after switch-on", onOff(Features.on(p, "resume")), new Runnable() {
            @Override
            public void run() {
                Features.set(p, "resume", !Features.on(p, "resume"));
            }
        }));
        final String[] perNames = {"off", "gently (a step every 30 km/h)", "medium (every 20 km/h)", "strongly (every 15 km/h)"};
        r.add(new SettingsView.Row(g, "Louder with speed", Features.on(p, "speedVolume") ? perNames[Math.max(1, Math.min(3, p.num("speedVolPer", 2)))] : "off",
                new Runnable() {
                    @Override
                    public void run() {
                        int i = Features.on(p, "speedVolume") ? p.num("speedVolPer", 2) : 0;
                        i = (i + 1) % 4;
                        Features.set(p, "speedVolume", i > 0);
                        if (i > 0) p.put("speedVolPer", String.valueOf(i));
                        Drive.start(a);
                    }
                }));
        final int[] hours = {20, 21, 22, 23, 0};
        r.add(new SettingsView.Row(g, "Quiet hours", Features.on(p, "quiet") ? "from " + Quiet.hour(Quiet.from(p)) + " to " + Quiet.hour(Quiet.to(p))
                + ", music at " + p.num("quietVol", 40) + "%" : "off", new Runnable() {
            @Override
            public void run() {
                int cur = Quiet.from(p), next = hours[0];
                for (int i = 0; i < hours.length; i++) if (hours[i] == cur) next = hours[(i + 1) % hours.length];
                p.put("quietFrom", String.valueOf(next));
                Features.set(p, "quiet", true);
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
        r.add(new SettingsView.Row(g, "Quiet-hours volume", p.num("quietVol", 40) + "% of full volume", new Runnable() {
            @Override
            public void run() {
                int cur = p.num("quietVol", 40), next = qv[0];
                for (int i = 0; i < qv.length; i++) if (qv[i] == cur) next = qv[(i + 1) % qv.length];
                p.put("quietVol", String.valueOf(next));
            }
        }));
        r.add(new SettingsView.Row(g, "Equalizer", Features.on(p, "eq") ? p.str("eqPreset", "Flat") + "  ·  " + Eq.status : "off", new Runnable() {
            @Override
            public void run() {
                equalizer(a);
            }
        }));

        // ---- Driving
        g = "Driving";
        String last = p.str("accelLast", "");
        String[] lf = last.split("\\|");
        r.add(new SettingsView.Row(g, "0–100 km/h", last.isEmpty() ? "no full run yet" : "last " + lf[0] + " s"
                + (lf.length > 1 && !lf[1].isEmpty() ? " (0–60 " + lf[1] + " s)" : "") + "  ·  best " + p.str("accelBest", "-") + " s", new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, DIALOG).setTitle("0–100 km/h timer")
                        .setMessage("Stop completely, then accelerate. The time is spoken when you pass 100 km/h. It uses GPS (or OBD-II speed "
                                + "when connected), so it is good to about half a second. Only on a safe, empty road.")
                        .setPositiveButton("OK", null).setNeutralButton("Forget the best time", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                p.put("accelBest", "");
                                p.put("accelLast", "");
                            }
                        }).show();
            }
        }));
        r.add(new SettingsView.Row(g, "Export routes as GPX", Tracks.days(a).size() + " days recorded", new Runnable() {
            @Override
            public void run() {
                exportGpx(a);
            }
        }));
        r.add(new SettingsView.Row(g, "Delete recorded routes", null, new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(a, DIALOG).setMessage("Delete every recorded route on the unit?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Tracks.deleteAll(a);
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }));

        // ---- Backup
        g = "Backup";
        r.add(new SettingsView.Row(g, "Back up now", "to OpenRath/backup", new Runnable() {
            @Override
            public void run() {
                try {
                    File f = Backup.make(a, "manual");
                    Toast.makeText(a, "Saved " + f.getPath(), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(a, "Couldn't back up: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }));
        r.add(new SettingsView.Row(g, "Restore from a backup", Backup.found().size() + " found", new Runnable() {
            @Override
            public void run() {
                restore(a);
            }
        }));
        long at = 0;
        try {
            at = Long.parseLong(p.str("backupLast", "0"));
        } catch (Exception ignored) {
        }
        r.add(new SettingsView.Row(g, "Daily automatic backup", !Features.on(p, "backup") ? "off"
                : at == 0 ? "on; none yet" : "on; last " + Cards.TripCard.ago(at), new Runnable() {
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
        r.add(new SettingsView.Row(g, "Crash reports", Crash.files().size() + " saved", new Runnable() {
            @Override
            public void run() {
                About.crashes(a);
            }
        }));
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
        final AlertDialog dlg = new AlertDialog.Builder(a, DIALOG).setTitle("Press the steering-wheel button now")
                .setMessage("Press and release it once. Waiting for up to 15 seconds…")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
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
                    dlg.setMessage("Nothing arrived. This button may be handled by the unit itself before any app sees it; "
                            + "try another one, or turn on \"Buttons work in other apps too\".");
                }
            }
        }, 15000);
    }

    private static void chooseAction(final HomeActivity a, final String sig, final String label, boolean existing) {
        String[] items = existing ? java.util.Arrays.copyOf(Keys.ACTION_NAMES, Keys.ACTION_NAMES.length + 1) : Keys.ACTION_NAMES;
        if (existing) items[items.length - 1] = "Forget this button";
        new AlertDialog.Builder(a, DIALOG).setTitle("What should " + label + " do?")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Keys.put(a.prefs, sig, label, w < Keys.ACTIONS.length ? Keys.ACTIONS[w] : null);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private static void chooseLong(final HomeActivity a, final String sig, final String label) {
        final String[] items = java.util.Arrays.copyOf(Keys.ACTION_NAMES, Keys.ACTION_NAMES.length + 1);
        items[items.length - 1] = "Nothing extra";
        new AlertDialog.Builder(a, DIALOG).setTitle("Holding " + label + " down should")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Keys.putLong(a.prefs, sig, w < Keys.ACTIONS.length ? Keys.ACTIONS[w] : null);
                    }
                }).setNegativeButton("Cancel", null).show();
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
        new AlertDialog.Builder(a, DIALOG).setTitle("Show messages from")
                .setMultiChoiceItems(names.toArray(new String[0]), on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w, boolean v) {
                        on[w] = v;
                    }
                }).setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < on.length; i++) if (on[i]) sb.append(pkgs.get(i)).append(',');
                p.put("tickerApps", sb.length() == 0 ? "," : sb.toString());
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private static void equalizer(final HomeActivity a) {
        final Prefs p = a.prefs;
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(24), Ui.px(10), Ui.px(24), 0);
        final TextView presetT = Ui.text(a, 17, 0xFFEEEEEE, "sans-serif-medium", Gravity.LEFT);
        presetT.setPadding(0, Ui.px(8), 0, Ui.px(12));
        col.addView(presetT);
        final String[] bandNames = {"60 Hz (bass)", "230 Hz", "910 Hz (voice)", "3.6 kHz", "14 kHz (treble)"};
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
                    presetT.setText("Preset: Custom  (tap to change)");
                    Eq.apply(a, p, 0);
                }

                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            col.addView(row);
        }
        final TextView bassT = Ui.text(a, 15, 0xFFEEEEEE, "sans-serif", Gravity.LEFT);
        bassT.setPadding(0, Ui.px(14), 0, Ui.px(14));
        bassT.setText("Bass boost: " + p.num("eqBass", 0) + " / 10  (tap to change)");
        bassT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                p.put("eqBass", String.valueOf((p.num("eqBass", 0) + 2) % 12));
                bassT.setText("Bass boost: " + p.num("eqBass", 0) + " / 10  (tap to change)");
                Eq.apply(a, p, 0);
            }
        });
        col.addView(bassT);
        presetT.setText("Preset: " + p.str("eqPreset", "Flat") + "  (tap to change)");
        presetT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int i = java.util.Arrays.asList(Eq.PRESETS).indexOf(p.str("eqPreset", "Flat"));
                String next = Eq.PRESETS[(i + 1) % Eq.PRESETS.length];
                p.put("eqPreset", next);
                presetT.setText("Preset: " + next + "  (tap to change)");
                int[] ng = Eq.gains(p);
                quiet[0] = true;
                for (int k = 0; k < 5; k++) bars[k].setProgress(ng[k] + 12);
                quiet[0] = false;
                Eq.apply(a, p, 0);
            }
        });
        Features.set(p, "eq", true);
        Eq.apply(a, p, 0);
        new AlertDialog.Builder(a, DIALOG).setTitle("Equalizer").setView(col)
                .setPositiveButton("Done", null)
                .setNeutralButton("Turn off", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Features.set(p, "eq", false);
                        Eq.apply(a, p, 0);
                    }
                }).show();
        if (Eq.status.startsWith("This unit")) Toast.makeText(a, Eq.status, Toast.LENGTH_LONG).show();
    }

    private static void exportGpx(final HomeActivity a) {
        final List<String> days = Tracks.days(a);
        if (days.isEmpty()) {
            Toast.makeText(a, Features.on(a.prefs, "gpx") ? "No routes recorded yet" : "Route recording is off in Settings > Driving",
                    Toast.LENGTH_LONG).show();
            return;
        }
        final String[] items = new String[days.size() + 1];
        items[0] = "All " + days.size() + " days";
        for (int i = 0; i < days.size(); i++) items[i + 1] = days.get(i);
        new AlertDialog.Builder(a, DIALOG).setTitle("Export which day?").setItems(items, new DialogInterface.OnClickListener() {
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
                    Toast.makeText(a, n == 1 ? "Saved " + last.getPath() : "Saved " + n + " files in " + last.getParent(), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(a, "Couldn't export: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private static void restore(final HomeActivity a) {
        final List<File> files = Backup.found();
        if (files.isEmpty()) {
            Toast.makeText(a, "No backups in OpenRath/backup or Download", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[files.size()];
        for (int i = 0; i < names.length; i++) names[i] = files.get(i).getName();
        new AlertDialog.Builder(a, DIALOG).setTitle("Restore which backup?").setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, final int w) {
                new AlertDialog.Builder(a, DIALOG).setMessage("Replace every setting, the fuel log and trip history with this backup? "
                        + "A copy of what's here now is saved first.")
                        .setPositiveButton("Restore", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d2, int x) {
                                try {
                                    Backup.make(a, "before-restore");
                                    Backup.restore(a, files.get(w));
                                    Toast.makeText(a, "Restored; restarting", Toast.LENGTH_SHORT).show();
                                    a.dash.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            // a launcher is started again by Android straight away, now reading the restored settings
                                            android.os.Process.killProcess(android.os.Process.myPid());
                                        }
                                    }, 1200);
                                } catch (Exception e) {
                                    Toast.makeText(a, "Couldn't restore: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            }
                        }).setNegativeButton("Cancel", null).show();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private Extras() {
    }
}
