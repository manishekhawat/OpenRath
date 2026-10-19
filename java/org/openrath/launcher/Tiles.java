package org.openrath.launcher;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps the map pictures (tiles) the map has shown in OpenRath/maps/tiles, so the places you have
 * looked at or driven through still show without internet. With internet, tiles come from the map
 * service as before (and a kept copy is refreshed after 30 days); without it, the kept copy is used.
 * Only tiles the map asks for are kept: nothing is bulk-downloaded, as the map services require.
 */
final class Tiles {

    static final long FRESH_MS = 30L * 24 * 3600 * 1000;
    static final long CAP_BYTES = 400L * 1024 * 1024;
    private static volatile int written;

    /** True for a map tile address this keeps. */
    static boolean isTile(String url) {
        return url.startsWith("https://server.arcgisonline.com/") && url.contains("/tile/")
                || url.startsWith("https://tile.openstreetmap.org/");
    }

    /** The kept file for a tile address: its host and path, made safe as a file name. */
    static String key(String url) {
        String u = url.replaceFirst("^https?://", "");
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        StringBuilder b = new StringBuilder();
        for (char c : u.toCharArray()) b.append(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '/' || c == '-' ? c : '_');
        return b.toString().replace("..", "_");
    }

    static File dir() {
        return Storage.dir("maps/tiles");
    }

    /** The tile for the map: kept, fetched and kept, or null to let the map show its blank square. */
    static WebResourceResponse serve(Context c, String url) {
        File f = new File(dir(), key(url));
        boolean online = Player.online(c);
        if (f.isFile() && (!online || System.currentTimeMillis() - f.lastModified() < FRESH_MS)) return file(f);
        if (!online) return null;
        try {
            HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(10000);
            h.setRequestProperty("User-Agent", "OpenRath (car head unit; open source home screen)");
            if (h.getResponseCode() != 200) return f.isFile() ? file(f) : null;
            InputStream in = h.getInputStream();
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
            in.close();
            byte[] data = o.toByteArray();
            try {
                File p = f.getParentFile();
                if (p.isDirectory() || p.mkdirs()) {
                    FileOutputStream w = new FileOutputStream(f);
                    w.write(data);
                    w.close();
                    if (++written % 300 == 0) new Thread(new Runnable() {
                        @Override
                        public void run() {
                            prune();
                        }
                    }).start();
                }
            } catch (Exception ignored) {
                // no storage permission or full: the map still shows the tile
            }
            return new WebResourceResponse(type(h.getContentType()), null, new ByteArrayInputStream(data));
        } catch (Exception e) {
            return f.isFile() ? file(f) : null;
        }
    }

    private static String type(String t) {
        return t == null || !t.startsWith("image/") ? "image/png" : t.split(";")[0];
    }

    private static WebResourceResponse file(File f) {
        try {
            return new WebResourceResponse("image/png", null, new FileInputStream(f));
        } catch (Exception e) {
            return null;
        }
    }

    /** Megabytes kept, for Settings. */
    static long sizeMb() {
        long[] t = {0};
        walk(dir(), null, t);
        return t[0] / (1024 * 1024);
    }

    /** Over the cap: delete the longest-unused tiles until well under it. */
    static synchronized void prune() {
        List<File> all = new ArrayList<>();
        long[] total = {0};
        walk(dir(), all, total);
        if (total[0] <= CAP_BYTES) return;
        Collections.sort(all, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (File f : all) {
            if (total[0] <= CAP_BYTES * 8 / 10) break;
            total[0] -= f.length();
            f.delete();
        }
    }

    static void clear() {
        List<File> all = new ArrayList<>();
        walk(dir(), all, new long[1]);
        for (File f : all) f.delete();
    }

    private static void walk(File d, List<File> out, long[] total) {
        File[] l = d.listFiles();
        if (l == null) return;
        for (File f : l) {
            if (f.isDirectory()) walk(f, out, total);
            else {
                total[0] += f.length();
                if (out != null) out.add(f);
            }
        }
    }

    private Tiles() {
    }
}
