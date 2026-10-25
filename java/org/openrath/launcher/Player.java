package org.openrath.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * OpenRath's own player, so the radio and music boxes play without opening another app.
 * "web" plays internet radio stations (from the free Radio Browser directory, Indian stations
 * by popularity); "files" plays the music files on the unit, USB stick or SD card.
 * It keeps playing while another app (say Google Maps) is in front.
 */
public class Player extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    static final String WEB = "web", FILES = "files";
    static final String PLAY = "play", TOGGLE = "toggle", NEXT = "next", PREV = "prev", STOP = "stop", PICK = "pick";

    static final class Track {
        final String name, sub, src;

        Track(String name, String sub, String src) {
            this.name = name;
            this.sub = sub;
            this.src = src;
        }
    }

    // State the boxes read. Changed on the main thread only.
    static String mode;                 // what is loaded: WEB, FILES or null
    static boolean playing, busy;       // busy: connecting, scanning or buffering
    static String status = "";          // a short message when something went wrong
    static final List<Track> stations = new ArrayList<>(), songs = new ArrayList<>();
    static int station, song;

    static Track current() {
        List<Track> l = WEB.equals(mode) ? stations : songs;
        int i = WEB.equals(mode) ? station : song;
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }

    static void send(Context c, String action, String mode) {
        send(c, action, mode, -1);
    }

    static void send(Context c, String action, String mode, int index) {
        Intent i = new Intent(c, Player.class).setAction(action).putExtra("mode", mode).putExtra("index", index);
        c.startForegroundService(i);
    }

    static boolean online(Context c) {
        try {
            NetworkInfo n = ((ConnectivityManager) c.getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    /** The running player, for where the music file is (the radio and music screen's bar). */
    private static Player self;

    /** How far into the music file, in ms; 0 when no file is loaded (or a station plays). Main thread only. */
    static long position() {
        try {
            return self != null && self.mp != null && !busy && FILES.equals(mode) ? self.mp.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** How long the music file is, in ms; 0 when not known. */
    static long duration() {
        try {
            return self != null && self.mp != null && !busy && FILES.equals(mode) ? Math.max(0, self.mp.getDuration()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Move in the music file, from the screen's bar. */
    static void seek(long ms) {
        try {
            if (self != null && self.mp != null && !busy && FILES.equals(mode)) self.mp.seekTo((int) ms);
        } catch (Exception e) {
            Diag.note("music seek", e);
        }
    }

    /** Balance steps each way: -10 is all left, 10 all right, 0 the middle. Kept as the plain value "balance". */
    static final int BALANCE_MAX = 10;

    /**
     * Left and right volume for a balance and an overall level (1 = full, less while a navigation
     * prompt speaks): the far side fades out, the near side stays. Pure, for the tests.
     */
    static float[] sides(int balance, float level) {
        int b = Math.max(-BALANCE_MAX, Math.min(BALANCE_MAX, balance));
        float l = b > 0 ? 1f - b / (float) BALANCE_MAX : 1f, r = b < 0 ? 1f + b / (float) BALANCE_MAX : 1f;
        return new float[]{l * level, r * level};
    }

    /** What the balance reaches, said in its row and when it opens. No stereo service OpenRath talks to has a balance or fader command. */
    static final String BALANCE_REACH = "Only OpenRath's own online radio and music files. Other music apps, FM and the front and rear "
            + "fader follow the unit's own sound settings.";

    /** The balance changed in Settings: the player follows it now. */
    static void balanceChanged() {
        if (self != null) self.volume(self.level);
    }

    private float level = 1f;

    /** The player's volume at `lv`, with the balance. */
    private void volume(float lv) {
        level = lv;
        if (mp == null) return;
        try {
            float[] v = sides(new Prefs(this).num("balance", 0), lv);
            mp.setVolume(v[0], v[1]);
        } catch (Exception e) {
            Diag.note("music balance", e);
        }
    }

    private final Handler handler = new Handler();
    private MediaPlayer mp;
    private MediaSession session;
    private SharedPreferences sp;
    private boolean focus;

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        sp = getSharedPreferences("player", MODE_PRIVATE);
        station = sp.getInt("station", 0);
        song = sp.getInt("song", 0);
        session = new MediaSession(this, "OpenRath");
        session.setCallback(new MediaSession.Callback() {   // steering-wheel buttons
            @Override public void onPlay() { toggle(true); }
            @Override public void onPause() { toggle(false); }
            @Override public void onSkipToNext() { skip(1); }
            @Override public void onSkipToPrevious() { skip(-1); }
            @Override public void onStop() { stopPlaying(); }
        });
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        foreground();
        if (i == null || i.getAction() == null) return START_NOT_STICKY;
        String a = i.getAction(), m = i.getStringExtra("mode");
        if (m != null && !m.equals(mode) && !STOP.equals(a)) {
            release();
            mode = m;
            playing = false;
        }
        if (PLAY.equals(a)) toggle(true);
        else if (TOGGLE.equals(a)) toggle(!playing);
        else if (NEXT.equals(a)) skip(1);
        else if (PREV.equals(a)) skip(-1);
        else if (PICK.equals(a)) {
            if (WEB.equals(mode)) station = i.getIntExtra("index", 0);
            else song = i.getIntExtra("index", 0);
            release();
            toggle(true);
        } else if (STOP.equals(a)) stopPlaying();
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------------ playing

    private void toggle(boolean on) {
        if (mode == null) mode = FILES;
        if (!on) {
            if (mp != null && playing) mp.pause();
            playing = false;
            changed();
            return;
        }
        List<Track> l = WEB.equals(mode) ? stations : songs;
        if (l.isEmpty()) {
            load(true);
            return;
        }
        if (mp != null && !busy) {
            if (!grabFocus()) return;
            mp.start();
            playing = true;
            changed();
        } else if (mp == null) {
            open();
        }
    }

    private void skip(int d) {
        List<Track> l = WEB.equals(mode) ? stations : songs;
        if (l.isEmpty()) {
            load(true);
            return;
        }
        if (WEB.equals(mode)) station = (station + d + l.size()) % l.size();
        else song = (song + d + l.size()) % l.size();
        release();
        open();
    }

    private void open() {
        Track t = current();
        if (t == null) {
            if (WEB.equals(mode)) station = 0;
            else song = 0;
            t = current();
            if (t == null) return;
        }
        if (!grabFocus()) return;
        sp.edit().putInt("station", station).putInt("song", song).apply();
        release();
        mp = new MediaPlayer();
        mp.setWakeMode(getApplicationContext(), android.os.PowerManager.PARTIAL_WAKE_LOCK);   // online radio keeps playing if the CPU would sleep
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
        volume(1f);   // with the balance
        Eq.apply(this, new Prefs(this), mp.getAudioSessionId());
        try {
            if (WEB.equals(mode) && !streamable(t.src)) throw new java.io.IOException("not a web stream");
            mp.setDataSource(t.src);
            busy = true;
            status = "";
            playing = true;
            mp.prepareAsync();
        } catch (Exception e) {
            busy = false;
            playing = false;
            status = "Can't play this one";
        }
        changed();
    }

    @Override
    public void onPrepared(MediaPlayer m) {
        busy = false;
        if (playing) m.start();
        changed();
    }

    @Override
    public void onCompletion(MediaPlayer m) {
        if (FILES.equals(mode)) skip(1);
        else {
            playing = false;
            changed();
        }
    }

    private int errors;

    @Override
    public boolean onError(MediaPlayer m, int what, int extra) {
        busy = false;
        release();
        status = WEB.equals(mode) ? "Station unavailable, trying the next" : "Can't play this file, skipping";
        // move on, but don't spin forever when there is no connection at all
        if (++errors < 5) handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                skip(1);
            }
        }, 1200);
        else {
            errors = 0;
            playing = false;
            status = WEB.equals(mode) ? "Web radio unreachable" : "No playable music files";
        }
        changed();
        return true;
    }

    private void release() {
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) {
                // already released
            }
            mp = null;
        }
        busy = false;
    }

    private void stopPlaying() {
        release();
        playing = false;
        changed();
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.abandonAudioFocus(this);
        focus = false;
        session.setActive(false);
        stopForeground(true);
        stopSelf();
    }

    private boolean grabFocus() {
        if (focus) return true;
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        focus = am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!focus) status = "Another app is playing";
        session.setActive(focus);
        return focus;
    }

    @Override
    public void onAudioFocusChange(int f) {
        if (f == AudioManager.AUDIOFOCUS_LOSS) {
            // FM radio, another player or a call took over
            focus = false;
            if (mp != null && playing) mp.pause();
            playing = false;
            changed();
        } else if (f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (mp != null && playing) volume(0.2f);   // a navigation prompt: duck
        } else if (f == AudioManager.AUDIOFOCUS_GAIN) {
            focus = true;
            volume(1f);
        }
    }

    // ------------------------------------------------------------------ the lists

    private boolean loading;

    private void load(final boolean thenPlay) {
        if (loading) return;
        loading = busy = true;
        status = WEB.equals(mode) ? "Finding stations…" : "Looking for music…";
        changed();
        final String m = mode;
        final Context c = getApplicationContext();
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final List<Track> l = WEB.equals(m) ? fetchStations(c) : scanSongs(c);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = busy = false;
                        List<Track> into = WEB.equals(m) ? stations : songs;
                        into.clear();
                        into.addAll(l);
                        if (l.isEmpty()) {
                            status = WEB.equals(m) ? "Web radio unreachable" : "No music files found";
                            playing = false;
                        } else {
                            status = "";
                            if (thenPlay && m.equals(mode)) open();
                        }
                        changed();
                    }
                });
            }
        });
    }

    /** Reload the lists, for the long-press menu (new USB stick, or to refresh stations). */
    static void forget() {
        stations.clear();
        songs.clear();
    }

    /**
     * A web radio address the player may open: http or https with a host, nothing else (no file:,
     * content: or rtsp:). Plain http stays allowed because many stations only stream that way; see
     * res/xml/network_security_config.xml.
     */
    static boolean streamable(String url) {
        if (url == null) return false;
        try {
            java.net.URL u = new java.net.URL(url.trim());
            String p = u.getProtocol().toLowerCase(Locale.US);
            return (p.equals("http") || p.equals("https")) && u.getHost() != null && !u.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    static List<Track> fetchStations(Context c) {
        SharedPreferences sp = c.getSharedPreferences("player", MODE_PRIVATE);
        String json = null;
        String[] hosts = {"de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info",
                "all.api.radio-browser.info"};
        for (String h : hosts) {
            try {
                json = get("https://" + h + "/json/stations/search?countrycode=IN&hidebroken=true&order=clickcount"
                        + "&reverse=true&limit=80");
                sp.edit().putString("stations", json).apply();
                break;
            } catch (Exception e) {
                Diag.note("station list fetch", e);
            }
        }
        if (json == null) json = sp.getString("stations", null);   // the last list we saw
        List<Track> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray a = new JSONArray(json);
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < a.length() && out.size() < 50; i++) {
                JSONObject s = a.getJSONObject(i);
                String codec = s.optString("codec", "").toUpperCase(Locale.US);
                String url = s.optString("url_resolved", s.optString("url", ""));
                String name = clean(s.optString("name", ""));
                if (!streamable(url) || name.isEmpty() || seen.contains(name.toLowerCase(Locale.US))) continue;
                if (!(codec.contains("MP3") || codec.contains("AAC") || codec.isEmpty())) continue;
                seen.add(name.toLowerCase(Locale.US));
                String tags = s.optString("tags", "");
                if (tags.length() > 40) tags = tags.substring(0, 40);
                out.add(new Track(name, clean(tags.replace(",", ", ")), url));
            }
        } catch (Exception e) {
            Diag.note("station list read", e);
        }
        return out;
    }

    /** A station or show name as the directory gives it, made readable: "Bollywood_Dance " is "Bollywood Dance". */
    static String clean(String s) {
        return s == null ? "" : s.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = Compat.https(u);
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "OpenRath");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    static List<Track> scanSongs(Context c) {
        List<Track> out = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA},
                    MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DURATION + ">15000", null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE");
            while (cur != null && cur.moveToNext()) {
                String path = cur.getString(2);
                if (path == null || !new File(path).canRead()) continue;
                String artist = cur.getString(1);
                if (artist == null || artist.startsWith("<")) artist = "";
                out.add(new Track(cur.getString(0), artist, path));
                paths.add(path);
            }
        } catch (Exception e) {
            Diag.note("music scan", e);
        } finally {
            if (cur != null) cur.close();
        }
        // USB sticks the media scanner hasn't indexed: walk the storage folders too
        List<Track> walked = new ArrayList<>();
        for (String root : new String[]{"/storage", "/mnt/media_rw", "/mnt/usb_storage", "/mnt/udisk"}) {
            walk(new File(root), 0, walked, paths);
        }
        Collections.sort(walked, new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        out.addAll(walked);
        return out;
    }

    private static void walk(File dir, int depth, List<Track> out, List<String> seen) {
        if (depth > 7 || out.size() > 3000) return;
        File[] l = dir.listFiles();
        if (l == null) return;
        for (File f : l) {
            String n = f.getName();
            if (n.startsWith(".") || n.equals("Android") || n.equals("self") || n.equals("emulated") && depth > 0) continue;
            if (f.isDirectory()) walk(f, depth + 1, out, seen);
            else {
                String lower = n.toLowerCase(Locale.US);
                if (!(lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".flac")
                        || lower.endsWith(".ogg") || lower.endsWith(".wav"))) continue;
                if (seen.contains(f.getPath()) || f.length() < 200 * 1024) continue;   // skip clips and tones
                seen.add(f.getPath());
                File parent = f.getParentFile();
                out.add(new Track(n.substring(0, n.lastIndexOf('.')), parent == null ? "" : parent.getName(), f.getPath()));
            }
        }
    }

    // ------------------------------------------------------------------ telling the app

    private void changed() {
        Track t = current();
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, 0, 1f).build());
        if (playing) errors = 0;
        foreground();
        Compat.send(this, new Intent(CHANGED));
        if (t == null) return;
    }

    static final String CHANGED = "org.openrath.launcher.PLAYER_CHANGED";

    private void foreground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        nm.createNotificationChannel(new NotificationChannel("player", Hindi.t("Player"), NotificationManager.IMPORTANCE_LOW));
        b = new Notification.Builder(this, "player");
        Track t = current();
        b.setSmallIcon(getApplicationInfo().icon)
                .setContentTitle(t == null ? "OpenRath" : t.name)
                .setContentText(WEB.equals(mode) ? Hindi.t("Web radio") : Hindi.t("Music"))
                .setOngoing(true);
        try {
            startForeground(7, b.build());
        } catch (RuntimeException e) {
            stopSelf();   // not allowed now; the next play starts it again
        }
    }

    @Override
    public void onDestroy() {
        if (self == this) self = null;
        release();
        session.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
