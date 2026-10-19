package org.openrath.launcher;

/**
 * The colour the music box takes from the album cover: the most vivid of its common colours,
 * kept dark enough that white text stays readable on it. Plain ints, so the tests can check it.
 */
final class ArtColour {

    /** A colour from the cover's pixels (ARGB), or 0 when the cover is grey or empty. */
    static int pick(int[] px) {
        if (px == null || px.length == 0) return 0;
        // 4 levels per channel = 64 bins; each bin weighs how often and how vivid its colour is
        double[] weight = new double[64];
        long[][] sum = new long[64][3];
        int[] n = new int[64];
        for (int p : px) {
            if ((p >>> 24) < 128) continue;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max < 40) continue;   // near black
            double sat = max == 0 ? 0 : (max - min) / (double) max;
            int bin = (r >> 6) * 16 + (g >> 6) * 4 + (b >> 6);
            weight[bin] += 0.2 + sat * sat;
            sum[bin][0] += r;
            sum[bin][1] += g;
            sum[bin][2] += b;
            n[bin]++;
        }
        int best = -1;
        for (int i = 0; i < 64; i++) if (n[i] > 0 && (best < 0 || weight[i] > weight[best])) best = i;
        if (best < 0) return 0;
        int r = (int) (sum[best][0] / n[best]), g = (int) (sum[best][1] / n[best]), b = (int) (sum[best][2] / n[best]);
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max - min < 30) return 0;   // grey: keep the theme's own panel
        // darken so the brightest channel is at most 150
        if (max > 150) {
            r = r * 150 / max;
            g = g * 150 / max;
            b = b * 150 / max;
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Where a track is now, in ms: the last reported position moved on by the time since, at the play speed. */
    static long position(long pos, long reportedAt, float speed, long now, long duration) {
        long p = pos + (long) ((now - reportedAt) * speed);
        return Math.max(0, duration > 0 ? Math.min(duration, p) : p);
    }

    /** "3:07" or "1:02:03". */
    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 3600 ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private ArtColour() {
    }
}
