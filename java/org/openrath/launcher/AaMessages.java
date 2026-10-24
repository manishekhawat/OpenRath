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

/**
 * The Android Auto messages OpenRath sends, each built as its type and protobuf payload (see
 * AaFrame.message), and the numbers of the ones it reads. Field numbers follow the reference's
 * control.proto, media.proto, input.proto and sensors.proto. Pure, for the tests.
 */
final class AaMessages {

    // control channel (and control messages on any channel)
    static final int VERSION_REQUEST = 1, VERSION_RESPONSE = 2, SSL_HANDSHAKE = 3, AUTH_COMPLETE = 4,
            DISCOVERY_REQUEST = 5, DISCOVERY_RESPONSE = 6, OPEN_REQUEST = 7, OPEN_RESPONSE = 8, CLOSE = 9,
            PING_REQUEST = 11, PING_RESPONSE = 12, NAV_FOCUS_REQUEST = 13, NAV_FOCUS = 14,
            BYEBYE_REQUEST = 15, BYEBYE_RESPONSE = 16, VOICE_SESSION = 17, AUDIO_FOCUS_REQUEST = 18, AUDIO_FOCUS = 19;
    // media channels (video, sound, microphone)
    static final int DATA = 0, CODEC_CONFIG = 1, SETUP = 0x8000, START = 0x8001, STOP = 0x8002, CONFIG = 0x8003,
            ACK = 0x8004, MIC_REQUEST = 0x8005, MIC_RESPONSE = 0x8006, VIDEO_FOCUS_REQUEST = 0x8007, VIDEO_FOCUS = 0x8008;
    // sensor channel
    static final int SENSOR_REQUEST = 0x8001, SENSOR_RESPONSE = 0x8002, SENSOR_EVENT = 0x8003;
    // input channel
    static final int INPUT_EVENT = 0x8001, BINDING_REQUEST = 0x8002, BINDING_RESPONSE = 0x8003;

    // sensor types
    static final int SENSOR_LOCATION = 1, SENSOR_NIGHT = 10, SENSOR_DRIVING = 13;
    /** Driving status: no limits when parked; no typing and no video apps while moving. */
    static final int UNRESTRICTED = 0, RESTRICTED_MOVING = 1 | 2;
    // audio focus the phone asks for, and the answers
    static final int FOCUS_GAIN = 1, FOCUS_TRANSIENT = 2, FOCUS_MAY_DUCK = 3, FOCUS_RELEASE = 4;
    static final int STATE_GAIN = 1, STATE_GAIN_TRANSIENT = 2, STATE_LOSS = 3, STATE_LOSS_CAN_DUCK = 4,
            STATE_LOSS_TRANSIENT = 5, STATE_GUIDANCE_ONLY = 7;
    // codecs and audio stream kinds
    private static final int PCM = 1, H264 = 3;
    private static final int SPEECH = 1, SYSTEM = 2, MEDIA = 3;
    /** Keys the touch screen's input service claims (home, back, call, end call, search, media keys). */
    private static final int[] KEYS = {3, 4, 5, 6, 84, 85, 87, 88, 126, 127};

    /** What the car says about itself in the service list. */
    static final class Car {
        String make = "OpenRath", model = "Car", year = "2024", id = "openrath", huMake = "OpenRath",
                huModel = "OpenRath", version = "1", name = "OpenRath";
        boolean rightHand;
        /** The picture size code (AaScreen) and frames per second (30 or 60). */
        int size = AaScreen.SD, fps = 30;
        boolean mic = true, location = true;
    }

    static byte[] versionRequest() {
        return AaFrame.message(VERSION_REQUEST, new byte[]{0, 1, 0, 2});   // version 1.2, as the reference asks
    }

    /** The version answer's status: 0 is accepted, negative is refused, -100 when it is too short to read. */
    static int versionStatus(byte[] m) {
        if (m == null || m.length < 8) return -100;
        return (short) (((m[6] & 0xFF) << 8) | (m[7] & 0xFF));
    }

    static byte[] authComplete() {
        return AaFrame.message(AUTH_COMPLETE, new AaProto.Writer().num(1, 0).bytes());
    }

    /** The list of everything this head unit offers: the answer to the phone's service discovery request. */
    static byte[] discovery(Car car) {
        AaProto.Writer r = new AaProto.Writer();
        // sensors: driving status, night, and where the car is
        AaProto.Writer sensors = new AaProto.Writer()
                .msg(1, new AaProto.Writer().num(1, SENSOR_DRIVING))
                .msg(1, new AaProto.Writer().num(1, SENSOR_NIGHT));
        if (car.location) sensors.msg(1, new AaProto.Writer().num(1, SENSOR_LOCATION));
        r.msg(1, new AaProto.Writer().num(1, AaFrame.CH_SENSOR).msg(2, sensors));
        // the picture: H.264 at one size
        AaProto.Writer video = new AaProto.Writer()
                .num(1, car.size)
                .num(2, car.fps >= 60 ? 1 : 2)
                .num(3, 0).num(4, 0)
                .num(5, AaScreen.density(car.size))
                .num(10, H264);
        r.msg(1, new AaProto.Writer().num(1, AaFrame.CH_VIDEO).msg(3, new AaProto.Writer()
                .num(1, H264).num(2, 0).msg(4, video).bool(5, true)));
        // the touch screen, as big as the picture
        AaProto.Writer input = new AaProto.Writer();
        for (int k : KEYS) input.num(1, k);
        input.msg(2, new AaProto.Writer().num(1, AaScreen.width(car.size)).num(2, AaScreen.height(car.size)));
        r.msg(1, new AaProto.Writer().num(1, AaFrame.CH_INPUT).msg(4, input));
        // sound: system and guidance 16 kHz mono, media 48 kHz stereo, all plain PCM
        r.msg(1, audio(AaFrame.CH_SYSTEM, SYSTEM, 16000, 1));
        r.msg(1, audio(AaFrame.CH_GUIDANCE, SPEECH, 16000, 1));
        r.msg(1, audio(AaFrame.CH_MEDIA, MEDIA, 48000, 2));
        // the microphone: 16 kHz mono PCM
        if (car.mic) {
            r.msg(1, new AaProto.Writer().num(1, AaFrame.CH_MIC).msg(5, new AaProto.Writer()
                    .num(1, PCM).msg(2, audioConfig(16000, 1))));
        }
        r.text(2, car.make).text(3, car.model).text(4, car.year).text(5, car.id)
                .num(6, car.rightHand ? 1 : 0)
                .text(7, car.huMake).text(8, car.huModel).text(9, car.version).text(10, car.version)
                .bool(11, false).bool(12, false).text(14, car.name)
                .msg(17, new AaProto.Writer().text(1, car.make).text(2, car.model).text(3, car.year).text(4, car.id)
                        .text(5, car.huMake).text(6, car.huModel).text(7, car.version).text(8, car.version));
        return AaFrame.message(DISCOVERY_RESPONSE, r.bytes());
    }

    private static AaProto.Writer audio(int channel, int kind, int rate, int channels) {
        return new AaProto.Writer().num(1, channel).msg(3, new AaProto.Writer()
                .num(1, PCM).num(2, kind).msg(3, audioConfig(rate, channels)));
    }

    private static AaProto.Writer audioConfig(int rate, int channels) {
        return new AaProto.Writer().num(1, rate).num(2, 16).num(3, channels);
    }

    /** Yes to a channel the phone opens. */
    static byte[] openResponse() {
        return AaFrame.message(OPEN_RESPONSE, new AaProto.Writer().num(1, 0).bytes());
    }

    static byte[] pingResponse(long timestamp) {
        return AaFrame.message(PING_RESPONSE, new AaProto.Writer().num(1, timestamp).bytes());
    }

    /** Navigation shows on the phone's picture (projected). */
    static byte[] navFocus() {
        return AaFrame.message(NAV_FOCUS, new AaProto.Writer().num(1, 2).bytes());
    }

    static byte[] byebyeResponse() {
        return AaFrame.message(BYEBYE_RESPONSE, new byte[0]);
    }

    /** The answer to an audio focus request: what the phone may now do. */
    static int focusAnswer(int request) {
        switch (request) {
            case FOCUS_GAIN:
                return STATE_GAIN;
            case FOCUS_TRANSIENT:
                return STATE_GAIN_TRANSIENT;
            case FOCUS_MAY_DUCK:
                return STATE_GUIDANCE_ONLY;
            default:
                return STATE_LOSS;
        }
    }

    static byte[] audioFocus(int state, boolean unsolicited) {
        return AaFrame.message(AUDIO_FOCUS, new AaProto.Writer().num(1, state).bool(2, unsolicited).bytes());
    }

    /** Ready for a media channel's data, with up to `maxUnacked` messages in flight. */
    static byte[] mediaConfig(int maxUnacked) {
        return AaFrame.message(CONFIG, new AaProto.Writer().num(1, 2).num(2, maxUnacked).num(3, 0).bytes());
    }

    /** One media message received. */
    static byte[] ack(int session) {
        return AaFrame.message(ACK, new AaProto.Writer().num(1, session).num(2, 1).bytes());
    }

    /** The picture shows (projected) or the car's own screen does (native). */
    static byte[] videoFocus(boolean projected, boolean unsolicited) {
        return AaFrame.message(VIDEO_FOCUS, new AaProto.Writer().num(1, projected ? 1 : 2).bool(2, unsolicited).bytes());
    }

    static byte[] micResponse(int status, int session) {
        return AaFrame.message(MIC_RESPONSE, new AaProto.Writer().num(1, status).num(2, session).bytes());
    }

    static byte[] sensorResponse() {
        return AaFrame.message(SENSOR_RESPONSE, new AaProto.Writer().num(1, 0).bytes());
    }

    static byte[] bindingResponse() {
        return AaFrame.message(BINDING_RESPONSE, new AaProto.Writer().num(1, 0).bytes());
    }

    static byte[] driving(int status) {
        return AaFrame.message(SENSOR_EVENT, new AaProto.Writer().msg(13, new AaProto.Writer().num(1, status)).bytes());
    }

    static byte[] night(boolean night) {
        return AaFrame.message(SENSOR_EVENT, new AaProto.Writer().msg(10, new AaProto.Writer().bool(1, night)).bytes());
    }

    /**
     * Where the car is: degrees (sent times 10^7), accuracy and speed in metres and metres a second
     * (times 1000), height in metres (times 100), heading in degrees (times 10^6, left out when < 0).
     */
    static byte[] location(long timeMs, double lat, double lon, float accuracyM, double altM, float speedMs, double heading) {
        AaProto.Writer d = new AaProto.Writer().num(1, timeMs)
                .num(2, (int) Math.round(lat * 1e7)).num(3, (int) Math.round(lon * 1e7))
                .num(4, (int) (Math.max(0, accuracyM) * 1000));
        if (!Double.isNaN(altM)) d.num(5, (int) Math.round(altM * 100));
        if (speedMs >= 0) d.num(6, (int) (speedMs * 1000));
        if (heading >= 0) d.num(7, (int) Math.round(heading * 1e6));
        return AaFrame.message(SENSOR_EVENT, new AaProto.Writer().msg(1, d).bytes());
    }

    /**
     * A touch: Android's action (down 0, up 1, move 2, cancel 3, pointer down 5, pointer up 6, the
     * same numbers Android Auto uses), which finger it is about, and every finger's id and spot
     * on the phone's picture.
     */
    static byte[] touch(long timeNs, int action, int index, int[] ids, int[] xs, int[] ys) {
        AaProto.Writer t = new AaProto.Writer();
        for (int i = 0; i < ids.length; i++) {
            t.msg(1, new AaProto.Writer().num(1, xs[i]).num(2, ys[i]).num(3, ids[i]));
        }
        t.num(2, index).num(3, action);
        return AaFrame.message(INPUT_EVENT, new AaProto.Writer().num(1, timeNs).msg(3, t).bytes());
    }

    private AaMessages() {
    }
}
