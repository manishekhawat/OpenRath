package org.openrath.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dashcam: records the camera (a USB dashcam the unit lists as a camera) in one-minute clips to
 * OpenRath/dashcam, deleting the oldest when the space set in Settings is used up. A sudden jolt
 * (harsh braking) or the "save clip" button moves the clip before and the one being recorded to
 * OpenRath/dashcam/saved, which is never cleared automatically.
 */
final class Dashcam {

    static volatile String status = "off";
    private static final String NEEDS = "needs camera permission: open Settings once to allow it";
    private static final String NO_CAMERA = "no camera found: plug in the USB dashcam";
    private static Camera camera;
    private static MediaRecorder rec;
    private static SurfaceTexture texture;
    private static File current, previous;
    private static boolean keepCurrent;
    private static Context app;
    private static Prefs prefs;
    private static final Handler main = new Handler(Looper.getMainLooper());

    static File dir() {
        return Storage.dir("dashcam");
    }

    static File savedDir() {
        return new File(dir(), "saved");
    }

    static int cameras() {
        try {
            return Camera.getNumberOfCameras();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** The last finished loop clip, or null. */
    static File lastClip() {
        File f = previous;
        return f != null && f.exists() ? f : null;
    }

    static boolean recording() {
        return rec != null;
    }

    static boolean allowed(Context c) {
        return c.checkCallingOrSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    static void start(final Context c, final Prefs p) {
        app = c.getApplicationContext();
        prefs = p;
        main.post(new Runnable() {
            @Override
            public void run() {
                if (rec != null) return;
                if (!allowed(app)) {
                    status = Hindi.t(NEEDS);
                    return;
                }
                if (cameras() == 0) {
                    status = Hindi.t(NO_CAMERA);
                    return;
                }
                segment();
            }
        });
    }

    static void stop() {
        main.post(new Runnable() {
            @Override
            public void run() {
                finish();
                if (camera != null) {
                    try {
                        camera.release();
                    } catch (Throwable ignored) {
                        // already released
                    }
                    camera = null;
                }
                if (!status.equals(Hindi.t(NO_CAMERA)) && !status.equals(Hindi.t(NEEDS)) && !status.startsWith("no camera") && !status.startsWith("needs")) status = "off";
            }
        });
    }

    /** Keep the clip before and the one being recorded (a jolt, or the button). */
    static void protect() {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (rec == null) return;
                keepCurrent = true;
                if (previous != null && previous.exists()) keep(previous);
            }
        });
    }

    private static void keep(File f) {
        File d = savedDir();
        d.mkdirs();
        if (f.renameTo(new File(d, f.getName()))) status = Hindi.t("recording · clip saved");
    }

    /** Record the next minute. */
    private static void segment() {
        finish();
        try {
            int id = Math.max(0, Math.min(cameras() - 1, prefs.num("dashcamId", cameras() - 1)));
            if (camera == null) camera = Camera.open(id);
            if (texture == null) texture = new SurfaceTexture(10);
            camera.setPreviewTexture(texture);
            camera.unlock();
            dir().mkdirs();
            current = new File(dir(), new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".mp4");
            rec = new MediaRecorder();
            rec.setCamera(camera);
            rec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            CamcorderProfile prof = profile(id);
            if (prof != null) {
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                rec.setVideoSize(prof.videoFrameWidth, prof.videoFrameHeight);
                rec.setVideoFrameRate(Math.min(30, prof.videoFrameRate));
                rec.setVideoEncodingBitRate(Math.min(6000000, prof.videoBitRate));
            } else {
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                rec.setVideoSize(640, 480);
                rec.setVideoEncodingBitRate(2500000);
            }
            rec.setOutputFile(current.getPath());
            rec.setPreviewDisplay(new Surface(texture));
            rec.setMaxDuration(60000);
            rec.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                segment();
                            }
                        });
                    }
                }
            });
            rec.prepare();
            rec.start();
            status = Hindi.t("recording");
            prune();
        } catch (Throwable t) {
            finish();
            if (camera != null) {
                try {
                    camera.release();
                } catch (Throwable ignored) {
                    // already released
                }
                camera = null;
            }
            status = Hindi.f("camera error: %1$s", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (rec == null && prefs != null && Features.on(prefs, "dashcam")) start(app, prefs);
                }
            }, 60000);
        }
    }

    private static CamcorderProfile profile(int id) {
        for (int q : new int[]{CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_480P, CamcorderProfile.QUALITY_HIGH}) {
            try {
                if (CamcorderProfile.hasProfile(id, q)) return CamcorderProfile.get(id, q);
            } catch (Throwable ignored) {
                // no such quality on this camera: try the next
            }
        }
        return null;
    }

    /** Stop the clip being recorded and move it to saved/ if it was asked for. */
    private static void finish() {
        if (rec == null) return;
        try {
            rec.stop();
        } catch (Throwable ignored) {
            // too short to be a clip
        }
        try {
            rec.release();
        } catch (Throwable ignored) {
            // already released
        }
        rec = null;
        try {
            if (camera != null) camera.reconnect();
        } catch (Throwable e) {
            Diag.note("dashcam reconnect", e);
        }
        if (current != null && current.length() == 0) current.delete();
        if (keepCurrent && current != null && current.exists()) keep(current);
        keepCurrent = false;
        previous = current;
        current = null;
    }

    /** Delete the oldest loop clips past the space allowed. */
    private static void prune() {
        File[] f = dir().listFiles();
        if (f == null) return;
        Arrays.sort(f, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });
        long cap = prefs.num("dashcamGB", 4) * 1024L * 1024 * 1024, used = 0;
        for (File x : f) if (x.isFile()) used += x.length();
        for (File x : f) {
            if (used <= cap) break;
            if (!x.isFile() || x.equals(current)) continue;
            used -= x.length();
            x.delete();
        }
    }

    static int savedClips() {
        File[] f = savedDir().listFiles();
        return f == null ? 0 : f.length;
    }

    static List<Integer> spaceChoices() {
        return Arrays.asList(2, 4, 8, 16, 32);
    }

    private Dashcam() {
    }
}
