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

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * The phone's picture: H.264 decoded by the unit's own decoder (MediaCodec) straight onto the
 * Android Auto screen's surface. Frames arrive on the session's thread and are fed in order;
 * finished pictures go to the screen as soon as they are ready.
 */
final class AaVideo {

    private MediaCodec codec;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    /** A decoder for a w x h picture on this surface; throws when the unit has none. */
    AaVideo(Surface surface, int w, int h) throws java.io.IOException {
        MediaCodec c = MediaCodec.createDecoderByType("video/avc");
        try {
            MediaFormat f = MediaFormat.createVideoFormat("video/avc", w, h);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, w * h);
            c.configure(f, surface, null, 0);
            c.start();
        } catch (RuntimeException e) {
            c.release();
            throw e;
        }
        codec = c;
    }

    /** The picture's settings (SPS and PPS), sent before the first frame and again after a new screen. */
    synchronized void config(byte[] b, int off, int len) {
        feed(b, off, len, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
    }

    /** One frame; false when the decoder has failed (the session then makes a new one). */
    synchronized boolean frame(byte[] b, int off, int len, long ptsUs) {
        return feed(b, off, len, ptsUs, 0);
    }

    private boolean feed(byte[] b, int off, int len, long ptsUs, int flags) {
        if (codec == null || len <= 0) return codec != null;
        try {
            int i = codec.dequeueInputBuffer(50000);
            if (i >= 0) {
                ByteBuffer in = codec.getInputBuffer(i);
                if (in != null && in.capacity() >= len) {
                    in.clear();
                    in.put(b, off, len);
                    codec.queueInputBuffer(i, 0, len, ptsUs, flags);
                } else {
                    codec.queueInputBuffer(i, 0, 0, ptsUs, 0);   // too big for the decoder: skipped
                }
            }
            drain();
            return true;
        } catch (RuntimeException e) {
            Diag.note("android auto video", e);
            release();
            return false;
        }
    }

    /** Shows every picture that is ready. */
    private void drain() {
        while (true) {
            int o = codec.dequeueOutputBuffer(info, 0);
            if (o >= 0) codec.releaseOutputBuffer(o, true);
            else if (o != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && o != MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) return;
        }
    }

    synchronized void release() {
        if (codec == null) return;
        try {
            codec.stop();
        } catch (RuntimeException e) {
            Diag.note("android auto video stop", e);
        }
        try {
            codec.release();
        } catch (RuntimeException e) {
            Diag.note("android auto video release", e);
        }
        codec = null;
    }
}
