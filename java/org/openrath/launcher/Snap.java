package org.openrath.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;

/**
 * A photo of where the car is parked, to find it again in a big parking lot: taken from the
 * dashcam's last minute when it is recording, or a single picture from the camera when it isn't.
 * Kept as OpenRath/parking/latest.jpg (the one before as previous.jpg).
 */
final class Snap {

    static File file() {
        return Storage.dir("parking/latest.jpg");
    }

    private static void store(Bitmap b) throws Exception {
        File f = file();
        f.getParentFile().mkdirs();
        if (f.exists()) f.renameTo(new File(f.getParentFile(), "previous.jpg"));
        store(b, f);
    }

    private static void store(Bitmap b, File f) throws Exception {
        f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        b.compress(Bitmap.CompressFormat.JPEG, 85, out);
        out.close();
    }

    static void take(final Context c, final Prefs p) {
        if (!Features.on(p, "parkPhoto") || Dashcam.cameras() == 0 || !Dashcam.allowed(c)) return;
        final File clip = Dashcam.lastClip();
        if (Dashcam.recording() && clip != null && System.currentTimeMillis() - clip.lastModified() < 3 * 60000) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MediaMetadataRetriever r = new MediaMetadataRetriever();
                    try {
                        r.setDataSource(clip.getPath());
                        long ms = Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                        Bitmap b = r.getFrameAtTime(Math.max(0, ms - 500) * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if (b != null) store(b);
                    } catch (Throwable ignored) {
                    } finally {
                        try {
                            r.release();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }).start();
            return;
        }
        if (Dashcam.recording()) return;   // the camera is busy and there's no clip yet
        shoot(c, p, null, null);
    }

    /**
     * One picture from the camera into `to` (the cab's lost-item photos); done runs on the main thread
     * with true once it is saved. False straight away when there's no camera or it is busy recording.
     */
    static boolean photo(Context c, Prefs p, File to, Runnable done) {
        if (Dashcam.cameras() == 0 || !Dashcam.allowed(c) || Dashcam.recording()) return false;
        shoot(c, p, to, done);
        return true;
    }

    private static void shoot(final Context c, final Prefs p, final File to, final Runnable done) {
        final Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                final Camera cam;
                try {
                    int id = Math.max(0, Math.min(Dashcam.cameras() - 1, p.num("dashcamId", Dashcam.cameras() - 1)));
                    cam = Camera.open(id);
                    cam.setPreviewTexture(new SurfaceTexture(10));
                    cam.startPreview();
                } catch (Throwable t) {
                    return;
                }
                // let the exposure settle, then one picture
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            cam.takePicture(null, null, new Camera.PictureCallback() {
                                @Override
                                public void onPictureTaken(final byte[] data, Camera camera) {
                                    camera.release();
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Bitmap b = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
                                                if (b != null && to == null) store(b);
                                                else if (b != null) {
                                                    store(b, to);
                                                    if (done != null) h.post(done);
                                                }
                                            } catch (Throwable ignored) {
                                            }
                                        }
                                    }).start();
                                }
                            });
                        } catch (Throwable t) {
                            cam.release();
                        }
                    }
                }, 1500);
            }
        });
    }

    private Snap() {
    }
}
