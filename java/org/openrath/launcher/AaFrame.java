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
 * Android Auto's framing. Every frame starts with four bytes: the channel, the flags and the
 * body's length (two bytes, high first). A frame that begins a message split over several frames
 * (FIRST without LAST) has four more bytes: the whole message's length. The body is TLS data once
 * the handshake is done (ENCRYPTED), and the plain message before. A message is its type (two
 * bytes, high first) and then its payload.
 */
final class AaFrame {

    static final int FIRST = 0x01, LAST = 0x02, CONTROL = 0x04, ENCRYPTED = 0x08;
    /** The four-byte header. */
    static final int HEADER = 4;
    /** The longest message put back together; anything longer is the stream gone wrong. */
    static final int MAX_MESSAGE = 8 * 1024 * 1024;

    /** The channels OpenRath offers (the reference's numbers). */
    static final int CH_CONTROL = 0, CH_SENSOR = 1, CH_VIDEO = 2, CH_INPUT = 3,
            CH_GUIDANCE = 4, CH_SYSTEM = 5, CH_MEDIA = 6, CH_MIC = 7;

    /** The flags of a whole message we send: FIRST and LAST, ENCRYPTED after the handshake, CONTROL for a control message on another channel. */
    static int flagsFor(int channel, int type, boolean encrypted) {
        int f = FIRST | LAST;
        if (encrypted) f |= ENCRYPTED;
        if (channel != CH_CONTROL && type >= 1 && type <= 26) f |= CONTROL;
        return f;
    }

    /** Whether a frame with these flags carries the whole message's length after its header. */
    static boolean hasTotal(int flags) {
        return (flags & FIRST) != 0 && (flags & LAST) == 0;
    }

    /** A frame: header and body. */
    static byte[] frame(int channel, int flags, byte[] body, int off, int len) {
        byte[] f = new byte[HEADER + len];
        header(f, channel, flags, len);
        System.arraycopy(body, off, f, HEADER, len);
        return f;
    }

    /** Fills the four header bytes. */
    static void header(byte[] into, int channel, int flags, int len) {
        into[0] = (byte) channel;
        into[1] = (byte) flags;
        into[2] = (byte) (len >> 8);
        into[3] = (byte) len;
    }

    static int channel(byte[] h) {
        return h[0] & 0xFF;
    }

    static int flags(byte[] h) {
        return h[1] & 0xFF;
    }

    /** The body length in a header. */
    static int length(byte[] h) {
        return ((h[2] & 0xFF) << 8) | (h[3] & 0xFF);
    }

    /** A big-endian four-byte number (the whole message's length). */
    static int int32(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    /** A message: its type, then the payload. */
    static byte[] message(int type, byte[] payload) {
        byte[] m = new byte[2 + payload.length];
        m[0] = (byte) (type >> 8);
        m[1] = (byte) type;
        System.arraycopy(payload, 0, m, 2, payload.length);
        return m;
    }

    /** A message's type, or -1 when it is too short to have one. */
    static int type(byte[] m, int len) {
        return len < 2 ? -1 : ((m[0] & 0xFF) << 8) | (m[1] & 0xFF);
    }

    /** A media message's timestamp: the eight bytes after the type, high first. */
    static long timestamp(byte[] m) {
        long v = 0;
        for (int i = 2; i < 10 && i < m.length; i++) v = (v << 8) | (m[i] & 0xFF);
        return v;
    }

    /** A media DATA message we send (the microphone): type 0, the timestamp in microseconds, then the sound. */
    static byte[] media(long timestampUs, byte[] pcm, int off, int len) {
        byte[] m = new byte[10 + len];
        for (int i = 9; i >= 2; i--) {
            m[i] = (byte) timestampUs;
            timestampUs >>>= 8;
        }
        System.arraycopy(pcm, off, m, 10, len);
        return m;
    }

    /**
     * Puts split messages back together, one run per channel. Feed it every frame's plain bytes in
     * order; it gives back a whole message when one is done, else null. A broken run (a frame out
     * of place, too long, or short of the length the first frame promised) is dropped quietly, and
     * the next FIRST starts afresh.
     */
    static final class Joiner {
        private final byte[][] runs = new byte[256][];
        private final int[] used = new int[256];

        byte[] add(int channel, int flags, byte[] b, int off, int len, int total) {
            channel &= 0xFF;
            boolean first = (flags & FIRST) != 0, last = (flags & LAST) != 0;
            if (first && last) {
                runs[channel] = null;
                byte[] m = new byte[len];
                System.arraycopy(b, off, m, 0, len);
                return m;
            }
            if (first) {
                if (total < len || total > MAX_MESSAGE || total < 2) {
                    runs[channel] = null;
                    return null;
                }
                runs[channel] = new byte[total];
                used[channel] = 0;
            }
            byte[] run = runs[channel];
            if (run == null) return null;   // a middle without its beginning
            if (used[channel] + len > run.length) {
                runs[channel] = null;
                return null;
            }
            System.arraycopy(b, off, run, used[channel], len);
            used[channel] += len;
            if (!last) return null;
            runs[channel] = null;
            return used[channel] == run.length ? run : null;
        }
    }

    private AaFrame() {
    }
}
