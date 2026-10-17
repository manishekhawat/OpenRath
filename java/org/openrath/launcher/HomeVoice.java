package org.openrath.launcher;

import android.widget.Toast;

/**
 * What the home screen does with what was heard: a voice command (FM, calls, music, volume, the
 * cab tools, places on the map and more), or a place to search for on the map; and reading the
 * news aloud. Steering-wheel buttons run the same commands.
 */
final class HomeVoice {

    /** Act on what was heard: a command (FM, call, music, volume, places), else a map search. */
    static void heard(final HomeActivity a, java.util.List<String> said, MapCard map) {
        if (said == null || said.isEmpty()) {
            if (map != null) map.voiceResult(null);
            return;
        }
        Voice.Command cmd = null;
        if (Features.on(a.prefs, "voice")) {
            // the recogniser's best guess first; a later guess wins only if it is a real command
            for (String h : said) {
                Voice.Command c = Voice.parse(h);
                if (c == null) continue;
                if (cmd == null) cmd = c;
                if (!"search".equals(c.kind)) {
                    cmd = c;
                    break;
                }
            }
        }
        if (cmd == null || "search".equals(cmd.kind)) {
            if (map != null) map.voiceResult(cmd == null ? said.get(0) : cmd.arg);
            else Toast.makeText(a, Hindi.f("Heard \"%1$s\". Put the map on the dashboard to search for places.", said.get(0)), Toast.LENGTH_LONG).show();
            return;
        }
        if (map != null) map.voiceResult(null);
        run(a, cmd);
    }

    /** Read the top headlines aloud; if there are none yet, fetch them and read them when they come. */
    static void readNews(final HomeActivity a) {
        if (!Features.on(a.prefs, "news")) {
            a.speak("The news is switched off in Settings, Features.");
            return;
        }
        if (Speak.speaking()) Speak.stop();
        final String topic = News.topicName(a.prefs.str("newsTopic", "top"));
        if (!News.items.isEmpty() && (System.currentTimeMillis() - News.at < News.FRESH_MS || !Player.online(a))) {
            Speak.sayLong(a, News.read(News.items, topic));
            return;
        }
        final int v = News.version;
        News.refresh(a, a.prefs, true);
        if (!Player.online(a)) {
            a.speak("There's no internet, and no headlines were saved yet.");
            return;
        }
        final android.os.Handler h = new android.os.Handler();
        h.postDelayed(new Runnable() {
            int tries;

            @Override
            public void run() {
                if (News.version != v || ++tries > 12) Speak.sayLong(a, News.read(News.items, topic));
                else h.postDelayed(this, 500);
            }
        }, 500);
    }

    static void run(final HomeActivity a, Voice.Command cmd) {
        String k = cmd.kind;
        final Unit unit = a.hub.unit;
        if ("fm".equals(k)) {
            if (!cmd.arg.isEmpty()) a.prefs.setFmFreq(cmd.arg);
            MediaCard.owner = "radio";
            String err;
            if (unit.fm(a.prefs)) err = unit.fmPlay(a, a.prefs, true);
            else {
                // plain Android: the radio app plays it
                a.openApp("media:radio", "Radio");
                err = null;
            }
            Resume.remember(a.prefs, "fm");
            if (!cmd.arg.isEmpty() && unit.fm(a.prefs)) {
                final String f = cmd.arg;
                // the radio app takes a moment to open; then tune it
                a.root.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        unit.fmTune(a.prefs, f);
                    }
                }, 2500);
            }
            Toast.makeText(a, err == null ? (cmd.arg.isEmpty() ? Hindi.t("FM radio") : "FM " + cmd.arg) : Hindi.f("FM didn't start: %1$s", err), Toast.LENGTH_SHORT).show();
        } else if ("call".equals(k)) {
            String num = Voice.number(cmd.arg), who = cmd.arg;
            if (num.isEmpty()) {
                for (int i = 0; i < 3; i++) {
                    String[] f = PhoneCard.fav(a.prefs, i);
                    if (f != null && Voice.sameName(f[0], cmd.arg)) {
                        num = f[1];
                        who = f[0];
                        break;
                    }
                }
            }
            if (num.isEmpty()) {
                a.speak("I couldn't find " + cmd.arg + " in your favourites");
                Toast.makeText(a, Hindi.f("No favourite called \"%1$s\". Add one in Settings > Phone and messages.", cmd.arg), Toast.LENGTH_LONG).show();
            } else {
                String err = unit.call(a, num);
                Toast.makeText(a, err == null ? Hindi.f("Calling %1$s…", who) : Hindi.f("Couldn't call: %1$s", err), Toast.LENGTH_SHORT).show();
            }
        } else if ("park".equals(k)) {
            a.parkQr();
        } else if ("clip".equals(k)) {
            if (!Features.on(a.prefs, "dashcam")) Toast.makeText(a, Hindi.t("Turn on the dashcam in Settings > Driving"), Toast.LENGTH_LONG).show();
            else {
                Dashcam.protect();
                a.speak("Clip saved");
            }
        } else if ("flood".equals(k)) {
            Monsoon.mark(a, a.prefs);
        } else if ("docs".equals(k)) {
            if (Features.on(a.prefs, "wallet")) Wallet.show(a, 0);
            else Toast.makeText(a, Hindi.t("Turn on the documents wallet in Settings > Car"), Toast.LENGTH_LONG).show();
        } else if ("kids".equals(k)) {
            if (Features.on(a.prefs, "kids")) Kids.show(a, a.prefs);
            else Toast.makeText(a, Hindi.t("Turn on trip games in Settings > Family"), Toast.LENGTH_LONG).show();
        } else if ("fog".equals(k)) {
            if (!Features.on(a.prefs, "fog")) Toast.makeText(a, Hindi.t("Turn on fog mode in Settings > Driving"), Toast.LENGTH_LONG).show();
            else {
                boolean on = !"on".equals(a.prefs.str("fogNow", "off"));
                a.prefs.put("fogNow", on ? "on" : "off");
                a.fogDismissed = false;
                a.speak(on ? "Fog mode on" : "Fog mode off");
                a.fogMode(a.hub);
            }
        } else if ("share".equals(k)) {
            a.shareTrip();
        } else if ("hotspot".equals(k)) {
            Chores.hotspot(a);
        } else if ("weekly".equals(k)) {
            Chores.weekly(a, true);
        } else if ("parkPhoto".equals(k)) {
            Chores.parkPhoto(a);
        } else if ("split".equals(k)) {
            Chores.split(a);
        } else if ("parkTimer".equals(k)) {
            Chores.parkingTimer(a);
        } else if ("news".equals(k)) {
            readNews(a);
        } else if ("cab".equals(k)) {
            Cab.menu(a);
        } else if ("sos".equals(k)) {
            if (Cab.on(a.prefs, "cabSos")) CabSos.sos(a);
            else Toast.makeText(a, Hindi.t("Turn on One-tap SOS in Settings > Cab driver"), Toast.LENGTH_LONG).show();
        } else if ("meter".equals(k)) {
            if (!Cab.on(a.prefs, "cabMeter")) Toast.makeText(a, Hindi.t("Turn on the fare meter in Settings > Cab driver"), Toast.LENGTH_LONG).show();
            else if (a.prefs.longNum("cabMeterAt", 0) > 0 && "stop".equals(cmd.arg)) CabMeter.stopMeter(a);
            else if ("stop".equals(cmd.arg)) Toast.makeText(a, Hindi.t("The fare meter isn't running"), Toast.LENGTH_SHORT).show();
            else {
                if (a.prefs.longNum("cabMeterAt", 0) == 0) CabMeter.startMeter(a);
                CabMeter.showMeter(a);
            }
            a.hub.notifyIfChanged(true);
        } else if ("view360".equals(k)) {
            // the stereo's 360 cameras when it has them, else OpenRath's 3D car
            if (!View360.openCam(a, a.prefs)) View360.show(a);
        } else if ("earnings".equals(k)) {
            boolean logOn = Cab.on(a.prefs, "cabEarnings");
            String t = logOn ? CabEarnings.spoken(a.prefs) : "Turn on the earnings log in Settings > Cab driver.";
            Toast.makeText(a, logOn ? t : Hindi.t("Turn on the earnings log in Settings > Cab driver."), Toast.LENGTH_LONG).show();
            a.speak(t);
        } else if ("aqi".equals(k)) {
            String t = Air.aqi < 0 ? "The air quality isn't known yet." : "The air quality is " + Air.band(Air.aqi).toLowerCase(java.util.Locale.US)
                    + ", A Q I " + Air.aqi + ".";
            Toast.makeText(a, Air.aqi < 0 ? Hindi.t("The air quality isn't known yet.") : Hindi.f("The air quality is %1$s, AQI %2$s.",
                    Hindi.t(Air.band(Air.aqi)).toLowerCase(java.util.Locale.US), String.valueOf(Air.aqi)), Toast.LENGTH_LONG).show();
            a.speak(t);
        } else if ("music".equals(k)) {
            MediaCard.owner = "music";
            if (!(Player.playing || a.hub.music != null && a.hub.music.playing)) Extras.media(a, "toggle");
        } else if ("next".equals(k) || "prev".equals(k)) {
            Extras.media(a, k);
        } else if ("pause".equals(k)) {
            if (Player.playing || a.hub.music != null && a.hub.music.playing || unit.fmPlaying())
                Extras.media(a, "toggle");
        } else if ("volUp".equals(k) || "volDown".equals(k) || "mute".equals(k)) {
            android.media.AudioManager am = (android.media.AudioManager) a.getSystemService(android.content.Context.AUDIO_SERVICE);
            int dir = "volUp".equals(k) ? android.media.AudioManager.ADJUST_RAISE
                    : "volDown".equals(k) ? android.media.AudioManager.ADJUST_LOWER : android.media.AudioManager.ADJUST_TOGGLE_MUTE;
            for (int i = 0; i < ("mute".equals(k) ? 1 : 2); i++)
                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI);
        } else {
            MapCard m = a.mapCard();
            if (m == null) {
                Toast.makeText(a, Hindi.t("Put the map on the dashboard for directions"), Toast.LENGTH_LONG).show();
                return;
            }
            if ("home".equals(k)) m.js("goFav('Home')");
            else if ("office".equals(k)) m.js("goFav('Office')");
            else if ("fuel".equals(k)) m.js("goFuel()");
            else if ("rest".equals(k)) m.js("restStop()");
            else if ("service".equals(k)) m.js("serviceCentres()");
        }
    }

    private HomeVoice() {
    }
}
