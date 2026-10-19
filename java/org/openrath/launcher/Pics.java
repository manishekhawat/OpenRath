package org.openrath.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Big pictures (layout backgrounds, theme wallpapers, the owner's own pictures) read at about the
 * screen's size instead of their full size, and kept in one cache with a size limit in bytes, so
 * old ones are let go. sampleSize has no Android types, so it is unit-tested.
 */
final class Pics {

    /**
     * The largest power of two to shrink a w x h picture by while it still covers want w x want h
     * (the screen): 1 keeps it as it is.
     */
    static int sampleSize(int w, int h, int wantW, int wantH) {
        if (w <= 0 || h <= 0 || wantW <= 0 || wantH <= 0) return 1;
        int s = 1;
        while (w / (s * 2) >= wantW && h / (s * 2) >= wantH) s *= 2;
        return s;
    }

    /** Cache size in bytes: an eighth of what Android gives the app, at least 16 MB. */
    static int cacheBytes(int memoryClassMb) {
        return Math.max(16, memoryClassMb / 8) * 1024 * 1024;
    }

    private static android.util.LruCache<String, Bitmap> cache;

    static synchronized android.util.LruCache<String, Bitmap> cache(Context c) {
        if (cache == null) {
            int mb = 64;
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) mb = am.getMemoryClass();
            } catch (Throwable ignored) {
            }
            cache = new android.util.LruCache<String, Bitmap>(cacheBytes(mb)) {
                @Override
                protected int sizeOf(String key, Bitmap b) {
                    return b.getByteCount();
                }
            };
        }
        return cache;
    }

    /** The screen's size, longer side first, so a picture fits it turned either way. */
    static int[] screen(Context c) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return new int[]{Math.max(dm.widthPixels, dm.heightPixels), Math.min(dm.widthPixels, dm.heightPixels)};
    }

    /** Options to read a picture of w x h at about the screen's size; JPEGs (never see-through) in half the memory. */
    static BitmapFactory.Options options(Context c, int w, int h, boolean jpeg) {
        int[] s = screen(c);
        BitmapFactory.Options o = new BitmapFactory.Options();
        // a landscape picture against the screen's long side, a portrait one against its short side
        o.inSampleSize = w >= h ? sampleSize(w, h, s[0], s[1]) : sampleSize(w, h, s[1], s[0]);
        if (jpeg) o.inPreferredConfig = Bitmap.Config.RGB_565;
        return o;
    }

    /** A picture file read at about the screen's size, or null. */
    static Bitmap file(Context c, java.io.File f) {
        try {
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), b);
            if (b.outWidth <= 0) return null;
            return BitmapFactory.decodeFile(f.getPath(), options(c, b.outWidth, b.outHeight, "image/jpeg".equals(b.outMimeType)));
        } catch (Throwable e) {
            return null;
        }
    }

    /** A picture in the app's assets read at about the screen's size, or null. */
    static Bitmap asset(Context c, String path) {
        try {
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            java.io.InputStream in = c.getAssets().open(path);
            try {
                BitmapFactory.decodeStream(in, null, b);
            } finally {
                in.close();
            }
            if (b.outWidth <= 0) return null;
            in = c.getAssets().open(path);
            try {
                return BitmapFactory.decodeStream(in, null, options(c, b.outWidth, b.outHeight, "image/jpeg".equals(b.outMimeType)));
            } finally {
                in.close();
            }
        } catch (Throwable e) {
            return null;
        }
    }

    private Pics() {
    }
}
