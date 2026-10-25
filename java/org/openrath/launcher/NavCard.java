package org.openrath.launcher;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * The next-turn card on the dashboard while a navigation app guides: its arrow, the distance in big
 * figures and the instruction, at the top beside the dock. A tap opens the navigation app; a long
 * press hides the card until the next trip.
 */
final class NavCard {

    private static WeakReference<View> card = new WeakReference<>(null);
    /** The trip the owner hid the card for (its app and start), so the next trip shows it again. */
    private static String hiddenFor = "";

    static void update(final HomeActivity a) {
        View old = card.get();
        boolean want = Features.on(a.prefs, "navTurns") && NavTurn.active() && !hiddenFor.equals(NavTurn.pkg);
        if (!NavTurn.active()) hiddenFor = "";
        if (!want) {
            if (old != null) a.root.removeView(old);
            card = new WeakReference<>(null);
            return;
        }
        if (old == null) {
            old = build(a);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(420), -2, Gravity.TOP | Gravity.LEFT);
            lp.setMargins(Ui.px(112), Ui.px(12), 0, 0);
            a.root.addView(old, lp);
            card = new WeakReference<>(old);
        }
        fill(old);
    }

    private static View build(final HomeActivity a) {
        LinearLayout s = new LinearLayout(a);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setPadding(Ui.px(14), Ui.px(12), Ui.px(18), Ui.px(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.RADIUS * Ui.k);
        g.setColor(Ui.solidPanel());
        g.setStroke(Math.max(1, Ui.px(2)), Ui.ACCENT);
        s.setBackground(g);
        s.setElevation(Ui.px(10));
        ImageView arrow = new ImageView(a);
        arrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        s.addView(arrow, new LinearLayout.LayoutParams(Ui.px(76), Ui.px(76)));
        LinearLayout words = new LinearLayout(a);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView dist = Ui.text(a, 34, Ui.SILVER, "sans-serif-light", Gravity.LEFT);
        TextView what = Ui.text(a, Ui.READ, Ui.SILVER, "sans-serif", Gravity.LEFT);
        what.setSingleLine(false);
        what.setMaxLines(2);
        what.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView eta = Ui.text(a, 16, Ui.SILVER_DIM, "sans-serif", Gravity.LEFT);
        words.addView(dist);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
        wp.topMargin = Ui.px(4);
        words.addView(what, wp);
        words.addView(eta, wp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = Ui.px(14);
        s.addView(words, lp);
        s.setTag(new View[]{arrow, dist, what, eta});
        s.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tap.feedback(v, a.prefs);
                Intent i = a.getPackageManager().getLaunchIntentForPackage(NavTurn.pkg);
                if (i == null) return;
                try {
                    a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    Diag.note("open navigation app", e);
                }
            }
        });
        s.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                hiddenFor = NavTurn.pkg;
                update(a);
                android.widget.Toast.makeText(a, Hindi.t("Next-turn card hidden until the next trip"), android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        return s;
    }

    private static void fill(View v) {
        View[] parts = (View[]) v.getTag();
        ImageView arrow = (ImageView) parts[0];
        android.graphics.Bitmap b = NavTurn.arrow;
        arrow.setVisibility(b == null ? View.GONE : View.VISIBLE);
        if (b != null) arrow.setImageBitmap(b);
        TextView dist = (TextView) parts[1], what = (TextView) parts[2], eta = (TextView) parts[3];
        dist.setText(NavTurn.distance);
        dist.setVisibility(NavTurn.distance.isEmpty() ? View.GONE : View.VISIBLE);
        what.setText(NavTurn.instruction);
        eta.setText(NavTurn.eta);
        eta.setVisibility(NavTurn.eta.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private NavCard() {
    }
}
