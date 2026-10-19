package org.openrath.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The car documents wallet: photos of the RC, insurance, PUC and driving licence kept inside the
 * app (so they show with no network and nobody can delete them by accident), to show at a check.
 * Add them from the gallery, or copy them to OpenRath/documents named rc, insurance, puc or
 * licence (.jpg or .png) and import.
 */
final class Wallet {

    static final String[] DOCS = {"rc", "insurance", "puc", "licence"};
    static final String[] NAMES = {"RC (registration)", "Insurance", "PUC certificate", "Driving licence"};
    static final int CODE = 81;   // request codes 81–84

    static File file(Activity a, int i) {
        return new File(new File(a.getFilesDir(), "documents"), DOCS[i] + ".jpg");
    }

    static File folder() {
        return Storage.dir("documents");
    }

    static int count(Activity a) {
        int n = 0;
        for (int i = 0; i < DOCS.length; i++) if (file(a, i).exists()) n++;
        return n;
    }

    /** Settings > Car > Documents: show, add or remove each. */
    static void menu(final Activity a) {
        String[] rows = new String[DOCS.length + 1];
        for (int i = 0; i < DOCS.length; i++) rows[i] = Hindi.t(NAMES[i]) + (file(a, i).exists() ? "" : Hindi.t("  (not added)"));
        rows[DOCS.length] = Hindi.t("Import from OpenRath/documents");
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t("Car documents"))
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w == DOCS.length) importFolder(a);
                        else if (file(a, w).exists()) one(a, w);
                        else pick(a, w);
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    private static void one(final Activity a, final int i) {
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(Hindi.t(NAMES[i]))
                .setItems(new String[]{Hindi.t("Show"), Hindi.t("Replace with another photo"), Hindi.t("Remove")}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) show(a, i);
                        else if (w == 1) pick(a, i);
                        else {
                            file(a, i).delete();
                            Toast.makeText(a, Hindi.f("%1$s removed", Hindi.t(NAMES[i])), Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton(Hindi.t("Close"), null).show();
    }

    static void pick(Activity a, int i) {
        try {
            a.startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE), CODE + i);
        } catch (Exception e) {
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setMessage(Hindi.f("No picture picker on this unit. Copy the photo to %1$s named %2$s.jpg, then use Import.", folder().getPath(), DOCS[i]))
                    .setPositiveButton(Hindi.t("OK"), null).show();
        }
    }

    /** A picked photo: shrink it to at most 2000 px and keep a copy. */
    static void picked(Activity a, int code, Uri uri) {
        int i = code - CODE;
        if (i < 0 || i >= DOCS.length || uri == null) return;
        try {
            InputStream in = a.getContentResolver().openInputStream(uri);
            save(a, i, in);
            Toast.makeText(a, Hindi.f("%1$s saved", Hindi.t(NAMES[i])), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(a, Hindi.f("Couldn't use that photo: %1$s", e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private static void save(Activity a, int i, InputStream in) throws Exception {
        byte[] bytes = readAll(in);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 2000) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        if (b == null) throw new IllegalStateException("not a picture");
        File f = file(a, i);
        f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        b.compress(Bitmap.CompressFormat.JPEG, 90, out);
        out.close();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toByteArray();
    }

    /** Which document a file in OpenRath/documents is, by its name, or -1. */
    static int which(String fileName) {
        String n = fileName.toLowerCase(Locale.US);
        if (!(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) return -1;
        if (n.startsWith("rc") || n.contains("registration")) return 0;
        if (n.startsWith("insurance") || n.startsWith("policy")) return 1;
        if (n.startsWith("puc") || n.contains("pollution")) return 2;
        if (n.startsWith("licence") || n.startsWith("license") || n.startsWith("dl")) return 3;
        return -1;
    }

    private static void importFolder(Activity a) {
        File[] fs = folder().listFiles();
        List<String> got = new ArrayList<>();
        if (fs != null) {
            for (File f : fs) {
                int i = which(f.getName());
                if (i < 0) continue;
                try {
                    save(a, i, new java.io.FileInputStream(f));
                    got.add(Hindi.t(NAMES[i]));
                } catch (Exception e) {
                    Diag.note("wallet import", e);
                }
            }
        }
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setMessage(got.isEmpty() ? Hindi.f("Nothing to import. Put photos named rc, insurance, puc or licence (.jpg or .png) in %1$s.", folder().getPath())
                        : Hindi.f("Imported: %1$s. You can delete the copies in OpenRath/documents now.", android.text.TextUtils.join(", ", got)))
                .setPositiveButton(Hindi.t("OK"), null).show();
    }

    /** Show the documents full screen, starting at one; tap to go to the next, back to close. */
    static void show(final Activity a, int start) {
        final List<Integer> have = new ArrayList<>();
        for (int i = 0; i < DOCS.length; i++) if (file(a, i).exists()) have.add(i);
        if (have.isEmpty()) {
            Toast.makeText(a, Hindi.t("No documents added yet: Settings > Car > Car documents"), Toast.LENGTH_LONG).show();
            menu(a);
            return;
        }
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final FrameLayout f = new FrameLayout(a);
        final ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        f.addView(img, new FrameLayout.LayoutParams(-1, -1));
        final TextView label = Ui.text(a, 16, 0xFFFFFFFF, "sans-serif-medium", Gravity.CENTER);
        label.setBackgroundColor(0x99000000);
        label.setPadding(Ui.px(16), Ui.px(8), Ui.px(16), Ui.px(8));
        f.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        final int[] at = {Math.max(0, have.indexOf(start))};
        final Runnable load = new Runnable() {
            @Override
            public void run() {
                int i = have.get(at[0]);
                img.setImageBitmap(BitmapFactory.decodeFile(file(a, i).getPath()));
                label.setText(Hindi.t(NAMES[i]) + (have.size() > 1 ? Hindi.f("   ·   tap for the next (%1$s of %2$s)", at[0] + 1, have.size()) : ""));
            }
        };
        load.run();
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (have.size() < 2) d.dismiss();
                else {
                    at[0] = (at[0] + 1) % have.size();
                    load.run();
                }
            }
        });
        Edge.fit(f);
        d.setContentView(f);
        d.show();
    }

    private Wallet() {
    }
}
