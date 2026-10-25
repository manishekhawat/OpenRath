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
 * The picture the phone sends: which size to ask for on this screen, and where it sits on the
 * screen (kept in proportion, centred, with black bars if the shapes differ), so a tap lands on
 * the same spot of the phone's picture. Pure, for the tests.
 */
final class AaScreen {

    /** Android Auto's size codes and their pixels (landscape only). */
    static final int SD = 1, HD = 2, FULL_HD = 3;

    /** The biggest landscape size that fits the screen: 1920x1080, 1280x720, else 800x480. */
    static int pick(int screenW, int screenH) {
        int w = Math.max(screenW, screenH), h = Math.min(screenW, screenH);
        if (w >= 1920 && h >= 1080) return FULL_HD;
        if (w >= 1280 && h >= 720) return HD;
        return SD;
    }

    static int width(int code) {
        return code == FULL_HD ? 1920 : code == HD ? 1280 : 800;
    }

    static int height(int code) {
        return code == FULL_HD ? 1080 : code == HD ? 720 : 480;
    }

    /** Dots per inch to announce, so buttons come out about the same size whichever size is sent. */
    static int density(int code) {
        return code == FULL_HD ? 320 : code == HD ? 213 : 160;
    }

    /** Where a video of vw x vh sits in a view of w x h, kept in proportion and centred: {left, top, width, height}. */
    static int[] fit(int w, int h, int vw, int vh) {
        if (w <= 0 || h <= 0 || vw <= 0 || vh <= 0) return new int[]{0, 0, Math.max(w, 0), Math.max(h, 0)};
        double s = Math.min((double) w / vw, (double) h / vh);
        int fw = (int) Math.round(vw * s), fh = (int) Math.round(vh * s);
        return new int[]{(w - fw) / 2, (h - fh) / 2, fw, fh};
    }

    /** A touch at `x` on the screen to the phone's picture: the picture starts at `start`, is `shown` pixels across and `size` pixels on the phone. */
    static int toVideo(float x, int start, int shown, int size) {
        if (shown <= 0 || size <= 0) return 0;
        int v = (int) ((x - start) * size / shown);
        return Math.max(0, Math.min(size - 1, v));
    }

    private AaScreen() {
    }
}
