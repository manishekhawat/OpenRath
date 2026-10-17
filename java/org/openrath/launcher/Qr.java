package org.openrath.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.openrath.qr.QrCode;

/**
 * A QR code for a phone to scan: a link to the parking spot, a trip, a UPI payment.
 * Phones read a code off a car screen badly when it is small, dim or smoothed, so the code is
 * shown full height, in sharp whole pixels, with a wide white border and the screen at full
 * brightness until it closes.
 */
final class Qr {

    /** Quiet border in modules; the standard asks for 4. */
    static final int BORDER = 4;

    /** The code's modules: true is dark. Low error correction keeps the modules big. */
    static boolean[][] modules(String text) {
        QrCode q = QrCode.encodeText(text, QrCode.Ecc.LOW);
        boolean[][] m = new boolean[q.size][q.size];
        for (int y = 0; y < q.size; y++)
            for (int x = 0; x < q.size; x++) m[y][x] = q.getModule(x, y);
        return m;
    }

    /** The code as a black-on-white bitmap at most `px` wide, every module a whole number of pixels. */
    static Bitmap bitmap(String text, int px) {
        boolean[][] m = modules(text);
        int n = m.length + 2 * BORDER;
        int scale = Math.max(1, px / n);
        int[] pixels = new int[n * scale * n * scale];
        java.util.Arrays.fill(pixels, 0xFFFFFFFF);
        int w = n * scale;
        for (int y = 0; y < m.length; y++)
            for (int x = 0; x < m.length; x++)
                if (m[y][x])
                    for (int dy = 0; dy < scale; dy++)
                        for (int dx = 0; dx < scale; dx++) pixels[((y + BORDER) * scale + dy) * w + (x + BORDER) * scale + dx] = 0xFF000000;
        Bitmap b = Bitmap.createBitmap(pixels, w, w, Bitmap.Config.ARGB_8888);
        return b;
    }

    /** An ImageView showing the code unscaled (no smoothing) on white; tap opens it full screen. */
    static ImageView view(final Activity a, final String title, final String text, final String caption, int px) {
        ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER);
        img.setBackgroundColor(0xFFFFFFFF);
        img.setImageBitmap(bitmap(text, px));
        img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(a, title, text, caption);
            }
        });
        return img;
    }

    /** The code full height on a white panel, the words beside it, the screen at full brightness. */
    static Dialog show(Activity a, String title, String text, String caption) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        int h = a.getResources().getDisplayMetrics().heightPixels;
        int size = h - Ui.px(24);
        LinearLayout row = new LinearLayout(a);
        row.setBackgroundColor(0xFFFFFFFF);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER);
        img.setImageBitmap(bitmap(text, size));
        row.addView(img, new LinearLayout.LayoutParams(size, -1));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(10), 0, Ui.px(28), 0);
        TextView t = Ui.text(a, 26, 0xFF111111, "sans-serif-medium", Gravity.LEFT);
        t.setText(title);
        col.addView(t);
        TextView c = Ui.text(a, 18, 0xFF333333, "sans-serif", Gravity.LEFT);
        c.setText(caption + "\n\nHold the phone about 30 cm away, straight on.");
        c.setPadding(0, Ui.px(12), 0, Ui.px(24));
        col.addView(c);
        TextView done = Ui.text(a, 20, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        done.setText("Done");
        done.setBackgroundColor(0xFF2F6FD6);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        col.addView(done, new LinearLayout.LayoutParams(Ui.px(180), Ui.px(64)));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        d.setContentView(row);
        WindowManager.LayoutParams lp = d.getWindow().getAttributes();
        lp.screenBrightness = 1f;   // a dimmed (night) screen is the main reason a phone can't read it
        d.getWindow().setAttributes(lp);
        d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        d.show();
        return d;
    }

    private Qr() {
    }
}
