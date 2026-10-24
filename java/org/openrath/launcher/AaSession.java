/*
 * OpenRath's built-in Android Auto receiver.
 *
 * Derived from Headunit Revived / Open Headunit by Andre Rinas
 * (github.com/andreknieriem/headunit-revived), which is based on headunit by Michael Reid
 * (github.com/mikereidis/headunit, Copyright 2011-2015 Michael A. Reid). Credit to Michael Reid
 * (headunit) and Andre Rinas (Headunit Revived / Open Headunit).
 * Copyright (C) Manish Shekhawat and the OpenRath contributors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. It is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See LICENSES/AGPL-3.0.txt for the full text.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package org.openrath.launcher;

import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.net.ssl.SSLContext;

/**
 * One Android Auto session with one phone, over any AaLink (the USB cable now, Wi-Fi later): the
 * version check, the TLS handshake, the list of what this head unit offers, then the phone's
 * picture, sound and requests until the phone leaves. Reading runs on the session's own thread;
 * what the screen, the microphone and the sensors send goes through a queue so it stays in
 * order. Any failure ends the session cleanly with a reason and never reaches the launcher.
 */
final class AaSession {

    /** The Android Auto screen, told what happens (always on the main thread). */
    interface Ui {
        /** A line to show while there is no picture yet. */
        void show(String text);

        /** The phone's "exit" button: back to the dashboard, the session goes on (sound keeps playing). */
        void leave();

        /** The session is over. */
        void ended(String why);
    }

    private static volatile AaSession current;
    private static Ui ui;
    /** Why the last session ended, for the screen opened after it; "" while none has. */
    static volatile String lastEnd = "";

    final AaLink link;
    private final Context app;
    private final AaTls tls;
    private final AaAudio audio;
    private final AaMessages.Car car;
    private final AaFrame.Joiner joiner = new AaFrame.Joiner();
    private final Bg.Serial out = Bg.serial();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final int[] sessions = new int[8];
    private final Set<Integer> sensors = new HashSet<>();
    private volatile boolean running = true, encrypted, videoStarted;
    private volatile AaVideo video;
    private volatile byte[] csd;
    private volatile String phone = "";
    private int lastDriving = -1, lastNight = -1;
    private long lastFix;

    private AaSession(Context app, AaLink link, SSLContext ssl) {
        this.app = app;
        this.link = link;
        this.tls = new AaTls(ssl);
        this.car = car(app, link.wireless());
        this.audio = new AaAudio(app, new AaAudio.Focus() {
            @Override
            public void changed(int state) {
                later(AaFrame.CH_CONTROL, AaMessages.audioFocus(state, true));
            }
        });
    }

    /** Starts a session on this link, or null when one already runs or there is no certificate. */
    static synchronized AaSession start(Context c, AaLink link) {
        if (current != null) return null;
        Context app = c.getApplicationContext();
        SSLContext ssl = AaCert.context(app);
        if (ssl == null) {
            lastEnd = Hindi.t("No head unit certificate: install Headunit Revived, or copy its two files to OpenRath's androidauto folder");
            return null;
        }
        final AaSession s = new AaSession(app, link, ssl);
        current = s;
        lastEnd = "";
        // the session's own reader: it waits on the phone for as long as Android Auto runs
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                s.run();
            }
        }, "android-auto");
        t.setDaemon(true);
        t.start();
        s.main.postDelayed(s.tick, 1000);
        return s;
    }

    static AaSession current() {
        return current;
    }

    /** The screen to tell, or null when it closes. */
    static void ui(Ui u) {
        ui = u;
    }

    boolean running() {
        return running;
    }

    /** The phone's name once it has said it ("" before). */
    String phone() {
        return phone;
    }

    int videoWidth() {
        return AaScreen.width(car.size);
    }

    int videoHeight() {
        return AaScreen.height(car.size);
    }

    /** What this car says about itself: names from the car setup, the picture size for this screen, the driver's side. */
    static AaMessages.Car car(Context c, boolean wireless) {
        AaMessages.Car car = new AaMessages.Car();
        try {
            Prefs p = new Prefs(c);
            if (!Car.brandName(p).isEmpty()) car.make = Car.brandName(p);
            if (!Car.model(p).isEmpty()) car.model = Car.model(p);
            Point size = new Point();
            ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(size);
            car.size = AaScreen.pick(size.x, size.y);
        } catch (Throwable e) {
            Diag.note("android auto car details", e);
        }
        car.rightHand = rightHand(Locale.getDefault().getCountry(), TimeZone.getDefault().getID());
        car.version = "1";
        return car;
    }

    /** Countries that drive on the left, so the driver sits on the right. */
    private static final List<String> LEFT = Arrays.asList("IN", "GB", "IE", "JP", "AU", "NZ", "ZA", "PK", "BD", "LK",
            "NP", "BT", "ID", "MY", "SG", "TH", "HK", "MO", "KE", "TZ", "UG", "ZM", "ZW", "MU", "CY", "MT");

    /** Whether the steering wheel is on the right: from the country, or the time zone when the country is unset or says US (common on Indian units). Pure, for the tests. */
    static boolean rightHand(String country, String zone) {
        if (country != null && !country.isEmpty() && !"US".equals(country)) return LEFT.contains(country);
        if (zone == null) return false;
        return zone.equals("Asia/Kolkata") || zone.equals("Asia/Calcutta") || zone.equals("Asia/Karachi") || zone.equals("Asia/Dhaka")
                || zone.equals("Asia/Colombo") || zone.equals("Asia/Kathmandu") || zone.equals("Europe/London") || zone.equals("Asia/Tokyo")
                || zone.startsWith("Australia/");
    }

    // ---- the session's thread

    private void run() {
        String why = Hindi.t("The phone left Android Auto");
        try {
            show(Hindi.t("Connecting to the phone…"));
            version();
            tls.handshake(new AaTls.Wire() {
                @Override
                public byte[] readHandshake() throws IOException {
                    while (true) {
                        Frame f = readFrame();
                        if (AaFrame.type(f.body, f.body.length) == AaMessages.SSL_HANDSHAKE) {
                            return Arrays.copyOfRange(f.body, 2, f.body.length);
                        }
                    }
                }

                @Override
                public void writeHandshake(byte[] data) throws IOException {
                    plain(AaFrame.message(AaMessages.SSL_HANDSHAKE, data));
                }
            });
            plain(AaMessages.authComplete());
            encrypted = true;
            show(Hindi.t("Starting Android Auto on the phone…"));
            while (running) {
                Frame f = readFrame();
                byte[] body = f.body;
                if ((f.flags & AaFrame.ENCRYPTED) != 0) body = tls.decrypt(body, 0, body.length);
                if (body.length == 0) continue;
                byte[] m = joiner.add(f.channel, f.flags, body, 0, body.length, f.total);
                if (m != null) handle(f.channel, f.flags, m);
            }
        } catch (javax.net.ssl.SSLException e) {
            Diag.note("android auto tls", e);
            if (!encrypted) why = Hindi.t("The phone didn't accept this head unit's certificate");
        } catch (Throwable e) {
            if (running) Diag.note("android auto session", e);
        }
        stop(why);
    }

    /** Sends the version request and waits for the phone's answer. */
    private void version() throws IOException {
        plain(AaMessages.versionRequest());
        for (int i = 0; i < 20; i++) {
            Frame f = readFrame();
            if (AaFrame.type(f.body, f.body.length) != AaMessages.VERSION_RESPONSE) continue;
            if (AaMessages.versionStatus(f.body) < 0) throw new IOException("the phone refused version 1.2");
            return;
        }
        throw new IOException("no version answer");
    }

    private static final class Frame {
        int channel, flags, total;
        byte[] body;
    }

    private final byte[] head = new byte[4];

    private Frame readFrame() throws IOException {
        Frame f = new Frame();
        full(head, 4);
        f.channel = AaFrame.channel(head);
        f.flags = AaFrame.flags(head);
        int len = AaFrame.length(head);
        if (AaFrame.hasTotal(f.flags)) {
            full(head, 4);
            f.total = AaFrame.int32(head, 0);
        }
        f.body = new byte[len];
        full(f.body, len);
        return f;
    }

    private void full(byte[] b, int len) throws IOException {
        int got = 0;
        while (got < len) {
            int n = link.read(b, got, len - got);
            if (n < 0) throw new IOException("the phone is gone");
            got += n;
        }
    }

    /** A message before encryption starts (the version check and the handshake): channel 0, whole. */
    private void plain(byte[] m) throws IOException {
        byte[] f = AaFrame.frame(AaFrame.CH_CONTROL, AaFrame.FIRST | AaFrame.LAST, m, 0, m.length);
        link.write(f, 0, f.length);
    }

    /** A message, encrypted, on a channel. Only after the handshake. */
    private synchronized void send(int channel, byte[] m) throws IOException {
        if (!encrypted || !running) return;
        int type = AaFrame.type(m, m.length);
        byte[] e = tls.encrypt(m, 0, m.length);
        byte[] f = AaFrame.frame(channel, AaFrame.flagsFor(channel, type, true), e, 0, e.length);
        link.write(f, 0, f.length);
    }

    /** Sends from another thread (the screen, the sensors, focus changes), in order; a failure ends the session. */
    private void later(final int channel, final byte[] m) {
        if (!running) return;
        out.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    send(channel, m);
                } catch (Throwable e) {
                    Diag.note("android auto send", e);
                    stop(Hindi.t("The phone left Android Auto"));
                }
            }
        });
    }

    // ---- what the phone says

    private void handle(int channel, int flags, byte[] m) throws IOException {
        int type = AaFrame.type(m, m.length);
        if (type == AaMessages.OPEN_REQUEST) {
            send(channel, AaMessages.openResponse());
            if (channel == AaFrame.CH_SENSOR) driving(true);
            return;
        }
        if (channel == AaFrame.CH_CONTROL || ((flags & AaFrame.CONTROL) != 0 && type < 0x8000)) {
            control(channel, type, m);
            return;
        }
        switch (channel) {
            case AaFrame.CH_VIDEO:
                video(type, m);
                break;
            case AaFrame.CH_MEDIA:
            case AaFrame.CH_GUIDANCE:
            case AaFrame.CH_SYSTEM:
                sound(channel, type, m);
                break;
            case AaFrame.CH_MIC:
                mic(type, m);
                break;
            case AaFrame.CH_SENSOR:
                if (type == AaMessages.SENSOR_REQUEST) {
                    int kind = (int) AaProto.find(m, 2, m.length - 2, 1, 0);
                    send(channel, AaMessages.sensorResponse());
                    synchronized (sensors) {
                        sensors.add(kind);
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            lastDriving = lastNight = -1;
                            lastFix = 0;
                        }
                    });
                }
                break;
            case AaFrame.CH_INPUT:
                if (type == AaMessages.BINDING_REQUEST) send(channel, AaMessages.bindingResponse());
                break;
            default:
                break;   // a channel OpenRath did not offer
        }
    }

    private void control(int channel, int type, byte[] m) throws IOException {
        switch (type) {
            case AaMessages.DISCOVERY_REQUEST: {
                AaProto.Reader r = new AaProto.Reader(m, 2, m.length - 2);
                while (r.next()) {
                    if (r.field == 4) phone = r.text();
                    else r.skip();
                }
                send(AaFrame.CH_CONTROL, AaMessages.discovery(car));
                break;
            }
            case AaMessages.PING_REQUEST:
                send(channel, AaMessages.pingResponse(AaProto.find(m, 2, m.length - 2, 1, 0)));
                break;
            case AaMessages.NAV_FOCUS_REQUEST:
                send(channel, AaMessages.navFocus());
                break;
            case AaMessages.AUDIO_FOCUS_REQUEST: {
                int req = (int) AaProto.find(m, 2, m.length - 2, 1, AaMessages.FOCUS_GAIN);
                audio.focus(req);
                send(channel, AaMessages.audioFocus(AaMessages.focusAnswer(req), false));
                break;
            }
            case AaMessages.BYEBYE_REQUEST:
                send(channel, AaMessages.byebyeResponse());
                stop(Hindi.t("The phone ended Android Auto"));
                break;
            case AaMessages.BYEBYE_RESPONSE:
                stop(Hindi.t("Android Auto ended"));
                break;
            default:
                break;   // pings answered, voice sessions and channel closes need nothing
        }
    }

    private void video(int type, byte[] m) throws IOException {
        switch (type) {
            case AaMessages.SETUP:
                send(AaFrame.CH_VIDEO, AaMessages.mediaConfig(8));
                send(AaFrame.CH_VIDEO, AaMessages.videoFocus(true, false));
                break;
            case AaMessages.START:
                sessions[AaFrame.CH_VIDEO] = (int) AaProto.find(m, 2, m.length - 2, 1, 0);
                videoStarted = true;
                show("");   // the picture is coming: no more words over it
                break;
            case AaMessages.CODEC_CONFIG: {
                byte[] c = Arrays.copyOfRange(m, 2, m.length);
                csd = c;
                AaVideo v = video;
                if (v != null) v.config(c, 0, c.length);
                break;
            }
            case AaMessages.DATA: {
                int at = startCode(m, 10) ? 10 : startCode(m, 2) ? 2 : 10;
                AaVideo v = video;
                if (v != null && m.length > at && !v.frame(m, at, m.length - at, at == 10 ? AaFrame.timestamp(m) : 0)) {
                    video = null;   // the decoder broke; the picture stays dark until the screen opens again
                }
                send(AaFrame.CH_VIDEO, AaMessages.ack(sessions[AaFrame.CH_VIDEO]));
                break;
            }
            case AaMessages.VIDEO_FOCUS_REQUEST: {
                if (AaProto.find(m, 2, m.length - 2, 2, 1) == 2) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (ui != null) ui.leave();
                        }
                    });
                } else {
                    send(AaFrame.CH_VIDEO, AaMessages.videoFocus(true, false));
                }
                break;
            }
            default:
                break;   // stop and acks need nothing
        }
    }

    private static boolean startCode(byte[] m, int at) {
        return m.length > at + 3 && m[at] == 0 && m[at + 1] == 0 && (m[at + 2] == 1 || (m[at + 2] == 0 && m[at + 3] == 1));
    }

    private void sound(int channel, int type, byte[] m) throws IOException {
        switch (type) {
            case AaMessages.SETUP:
                send(channel, AaMessages.mediaConfig(16));
                send(AaFrame.CH_CONTROL, AaMessages.audioFocus(AaMessages.STATE_GAIN, true));
                break;
            case AaMessages.START:
                sessions[channel] = (int) AaProto.find(m, 2, m.length - 2, 1, 0);
                break;
            case AaMessages.STOP:
                audio.stop(channel);
                break;
            case AaMessages.DATA:
                if (m.length > 10) audio.play(channel, m, 10, m.length - 10);
                send(channel, AaMessages.ack(sessions[channel]));
                break;
            default:
                break;   // plain PCM has no codec settings
        }
    }

    private void mic(int type, byte[] m) throws IOException {
        if (type == AaMessages.SETUP) {
            send(AaFrame.CH_MIC, AaMessages.mediaConfig(16));
        } else if (type == AaMessages.MIC_REQUEST) {
            boolean open = AaProto.find(m, 2, m.length - 2, 1, 0) != 0;
            if (!open) {
                audio.stopMic();
                send(AaFrame.CH_MIC, AaMessages.micResponse(0, 1));
            } else if (audio.micAllowed()) {
                send(AaFrame.CH_MIC, AaMessages.micResponse(0, 1));
                audio.startMic(this);
            } else {
                send(AaFrame.CH_MIC, AaMessages.micResponse(-7, 1));   // internal error: no microphone permission
            }
        }
    }

    /** 20 ms of microphone sound, from the recording thread. */
    void mic(long timestampUs, byte[] pcm, int len) {
        try {
            send(AaFrame.CH_MIC, AaFrame.media(timestampUs, pcm, 0, len));
        } catch (Throwable e) {
            Diag.note("android auto microphone send", e);
            audio.stopMic();
        }
    }

    // ---- the screen

    /** The screen's surface is ready: a decoder on it, the last picture settings again, and the picture asked for. */
    void attach(Surface s) {
        AaVideo old = video;
        if (old != null) old.release();
        try {
            AaVideo v = new AaVideo(s, videoWidth(), videoHeight());
            byte[] c = csd;
            if (c != null) v.config(c, 0, c.length);
            video = v;
        } catch (Throwable e) {
            Diag.note("android auto decoder", e);
            show(Hindi.t("This unit can't show the phone's picture"));
            return;
        }
        if (videoStarted) {
            // native then projected makes the phone send a whole new picture
            later(AaFrame.CH_VIDEO, AaMessages.videoFocus(false, true));
            later(AaFrame.CH_VIDEO, AaMessages.videoFocus(true, true));
        }
    }

    /** The screen went away: the decoder goes; the phone is told the car's own screen shows. */
    void detach() {
        AaVideo v = video;
        video = null;
        if (v != null) v.release();
        if (videoStarted) later(AaFrame.CH_VIDEO, AaMessages.videoFocus(false, true));
    }

    /** A touch, already moved onto the phone's picture (see AaActivity). */
    void touch(int action, int index, int[] ids, int[] xs, int[] ys) {
        if (!encrypted) return;
        later(AaFrame.CH_INPUT, AaMessages.touch(System.nanoTime(), action, index, ids, xs, ys));
    }

    private void show(final String text) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (ui != null) ui.show(text);
            }
        });
    }

    // ---- sensors, every second on the main thread

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                if (encrypted) sensors();
            } catch (Throwable e) {
                Diag.note("android auto sensors", e);
            }
            main.postDelayed(this, 1000);
        }
    };

    private boolean wants(int kind) {
        synchronized (sensors) {
            return sensors.contains(kind);
        }
    }

    private void sensors() {
        if (wants(AaMessages.SENSOR_DRIVING)) driving(false);
        if (wants(AaMessages.SENSOR_NIGHT)) {
            long now = System.currentTimeMillis();
            boolean night = Drive.fixAt > 0 ? Care.dark(Drive.latNow, Drive.lonNow, now) : ThemeAuto.night(null, now);
            if ((night ? 1 : 0) != lastNight) {
                lastNight = night ? 1 : 0;
                later(AaFrame.CH_SENSOR, AaMessages.night(night));
            }
        }
        if (wants(AaMessages.SENSOR_LOCATION) && Drive.fixAt > 0 && Drive.fixAt != lastFix) {
            lastFix = Drive.fixAt;
            later(AaFrame.CH_SENSOR, AaMessages.location(Drive.fixAt, Drive.latNow, Drive.lonNow, 10, Drive.altNow,
                    Drive.kmhNow / 3.6f, Drive.headingNow));
        }
    }

    /** Driving status: limits while the car moves (no typing, no video apps), none when stopped. */
    private void driving(boolean now) {
        int status = ChildLock.moving() ? AaMessages.RESTRICTED_MOVING : AaMessages.UNRESTRICTED;
        if (!now && status == lastDriving) return;
        lastDriving = status;
        if (now) {
            try {
                send(AaFrame.CH_SENSOR, AaMessages.driving(status));
            } catch (IOException e) {
                Diag.note("android auto driving status", e);
            }
        } else later(AaFrame.CH_SENSOR, AaMessages.driving(status));
    }

    // ---- the end

    /** Ends the session (unplugged, the phone said goodbye, or something failed); safe to call more than once. */
    void stop(final String why) {
        synchronized (AaSession.class) {
            if (!running) return;
            running = false;
            if (current == this) current = null;
            lastEnd = why == null ? "" : why;
        }
        link.close();
        if (!link.wireless()) AaUsb.ended(link.name());
        tls.close();
        audio.close();
        AaVideo v = video;
        video = null;
        if (v != null) v.release();
        main.removeCallbacks(tick);
        main.post(new Runnable() {
            @Override
            public void run() {
                if (ui != null) ui.ended(why);
            }
        });
    }
}
