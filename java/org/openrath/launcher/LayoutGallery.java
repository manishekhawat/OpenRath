package org.openrath.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings > Look > Screen layout: a picture of each layout, two to a row like the theme gallery.
 * Tap one to use it. The boxes show in the current theme; the others are pictures made at build
 * time (assets/layouts/prev_*.jpg).
 */
final class LayoutGallery extends LinearLayout {

    interface Picked {
        void picked(String id);
    }

    /** {id, name, description} for every choice, in order. */
    static List<String[]> choices(Context c) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{Layouts.BOXES, "Boxes", "Five boxes you choose, with the buttons bar"});
        out.add(new String[]{HomeActivity.WIDE, "Wide map", "A large map, two cards and an app bar, like Android Auto"});
        for (Layouts.Layout l : Layouts.all(c)) out.add(new String[]{l.id, l.name, "Picture layout; parts can be hidden below"});
        return out;
    }

    private static Bitmap picture(Context c, String id) {
        try {
            InputStream in = c.getAssets().open("layouts/prev_" + id + ".jpg");
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    LayoutGallery(Context c, String current, final Picked picked) {
        super(c);
        setOrientation(VERTICAL);
        List<String[]> all = choices(c);
        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            final String[] ch = all.get(i);
            if (i % 2 == 0) {
                row = new LinearLayout(c);
                LayoutParams rp = new LayoutParams(-1, -2);
                rp.bottomMargin = Ui.px(14);
                addView(row, rp);
            }
            boolean on = ch[0].equals(current);
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(VERTICAL);
            int pad = Ui.px(8);
            tile.setPadding(pad, pad, pad, Ui.px(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * Ui.k);
            bg.setColor(on ? Ui.PANEL_TOP : Ui.PANEL);
            bg.setStroke(Math.max(1, Ui.px(on ? 2.5f : 1.2f)), on ? Ui.ACCENT : Ui.mix(Ui.EDGE, Ui.PANEL, 0.6f));
            tile.setBackground(bg);
            View pic = null;
            if (Layouts.BOXES.equals(ch[0])) {
                for (Theme.Pack p : Theme.available(c)) {
                    if (p.id.equals(Theme.id())) {
                        pic = new ThemeGallery.Preview(c, p, Theme.Look.of(p.json, Theme.paintAccent, Theme.paintEdge));
                        break;
                    }
                }
            }
            if (pic == null) {
                ImageView iv = new ImageView(c);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                Bitmap b = picture(c, ch[0]);
                if (b != null) iv.setImageBitmap(b);
                pic = iv;
            }
            tile.addView(pic, new LayoutParams(-1, -2));
            TextView name = Ui.text(c, 17, Ui.SILVER, "sans-serif-medium", Gravity.LEFT);
            name.setText(on ? Hindi.f("%1$s   ✓ in use", Hindi.t(ch[1])) : Hindi.t(ch[1]));
            name.setPadding(Ui.px(6), Ui.px(10), Ui.px(6), 0);
            tile.addView(name);
            TextView desc = Ui.text(c, 13, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
            desc.setText(Hindi.t(ch[2]));
            desc.setPadding(Ui.px(6), Ui.px(4), Ui.px(6), 0);
            tile.addView(desc);
            tile.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    picked.picked(ch[0]);
                }
            });
            LayoutParams tp = new LayoutParams(0, -2, 1f);
            if (i % 2 == 0) tp.rightMargin = Ui.px(7);
            else tp.leftMargin = Ui.px(7);
            row.addView(tile, tp);
        }
        if (all.size() % 2 == 1 && row != null) row.addView(new View(c), new LayoutParams(0, 1, 1f));
    }
}
