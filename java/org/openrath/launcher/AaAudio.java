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
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.SystemClock;

/**
 * The phone's sound and the unit's microphone. Each sound channel plays through its own
 * AudioTrack: music 48 kHz stereo, navigation voice and system sounds 16 kHz mono, all plain PCM.
 * Android's audio focus is asked for when the phone asks for it, so the radio and other players
 * pause or dip, and the phone is told when another app takes the sound. The microphone records
 * 16 kHz mono while the phone listens (Google Assistant).
 */
final class AaAudio {

    /** Says to the phone that the car's sound focus changed (another app took it, or gave it back). */
    interface Focus {
        void changed(int state);
    }

    private final Context app;
    private final AudioManager am;
    private final AudioTrack[] tracks = new AudioTrack[8];
    private boolean focused;
    private final AudioManager.OnAudioFocusChangeListener listener;
    private volatile boolean recording;

    AaAudio(Context c, final Focus focus) {
        app = c.getApplicationContext();
        am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        listener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int change) {
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    synchronized (AaAudio.this) {
                        focused = false;
                    }
                    focus.changed(AaMessages.STATE_LOSS);
                } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    focus.changed(AaMessages.STATE_LOSS_TRANSIENT);
                } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    focus.changed(AaMessages.STATE_LOSS_CAN_DUCK);
                } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                    focus.changed(AaMessages.STATE_GAIN);
                }
            }
        };
    }

    /** The phone asks for sound focus (AaMessages.FOCUS_*): ask Android for the same. */
    synchronized void focus(int request) {
        if (am == null) return;
        try {
            if (request == AaMessages.FOCUS_RELEASE) {
                if (focused) am.abandonAudioFocus(listener);
                focused = false;
                return;
            }
            int kind = request == AaMessages.FOCUS_TRANSIENT ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    : request == AaMessages.FOCUS_MAY_DUCK ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    : AudioManager.AUDIOFOCUS_GAIN;
            focused = am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, kind) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } catch (Throwable e) {
            Diag.note("android auto audio focus", e);
        }
    }

    /** Sound for a channel (the bytes after the timestamp). */
    void play(int channel, byte[] b, int off, int len) {
        AudioTrack t = track(channel);
        if (t == null || len <= 0) return;
        try {
            t.write(b, off, len);
        } catch (Throwable e) {
            Diag.note("android auto audio", e);
            stop(channel);
        }
    }

    private synchronized AudioTrack track(int channel) {
        if (channel < 0 || channel >= tracks.length) return null;
        if (tracks[channel] != null) return tracks[channel];
        boolean music = channel == AaFrame.CH_MEDIA;
        int rate = music ? 48000 : 16000, mask = music ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int usage = music ? AudioAttributes.USAGE_MEDIA : channel == AaFrame.CH_GUIDANCE
                ? AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE : AudioAttributes.USAGE_ASSISTANCE_SONIFICATION;
        try {
            int min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(usage)
                            .setContentType(music ? AudioAttributes.CONTENT_TYPE_MUSIC : AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(min, 1024) * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            t.play();
            tracks[channel] = t;
            return t;
        } catch (Throwable e) {
            Diag.note("android auto audio track", e);
            return null;
        }
    }

    /** The phone stopped a channel's sound. */
    synchronized void stop(int channel) {
        if (channel < 0 || channel >= tracks.length || tracks[channel] == null) return;
        AudioTrack t = tracks[channel];
        tracks[channel] = null;
        try {
            t.stop();
        } catch (Throwable e) {
            Diag.note("android auto audio stop", e);
        }
        t.release();
    }

    /** Whether OpenRath may record (the owner allowed the microphone). */
    boolean micAllowed() {
        return app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Records 16 kHz mono in 20 ms pieces and hands each to `out` until stopMic(); on a background thread. */
    void startMic(final AaSession out) {
        if (recording) return;
        recording = true;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                AudioRecord r = null;
                try {
                    int min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    r = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 640) * 4);
                    r.startRecording();
                    byte[] b = new byte[640];
                    // a voice question lasts seconds; it ends with stopMic() or the session
                    while (recording && out.running()) {
                        int n = r.read(b, 0, b.length);
                        if (n <= 0) break;
                        out.mic(SystemClock.elapsedRealtime() * 1000, b, n);
                    }
                } catch (Throwable e) {
                    Diag.note("android auto microphone", e);
                } finally {
                    recording = false;
                    if (r != null) {
                        try {
                            r.stop();
                        } catch (Throwable e) {
                            Diag.note("android auto microphone stop", e);
                        }
                        r.release();
                    }
                }
            }
        });
    }

    void stopMic() {
        recording = false;
    }

    /** Everything off: tracks, microphone and focus. */
    void close() {
        stopMic();
        for (int i = 0; i < tracks.length; i++) stop(i);
        focus(AaMessages.FOCUS_RELEASE);
    }
}
