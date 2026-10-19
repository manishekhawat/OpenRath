package org.openrath.launcher;

import android.os.Build;
import android.view.View;

/**
 * Android 15 draws apps that target it under the status and navigation bars. OpenRath's screens are
 * laid out for the space between them, so on Android 15 and newer each full-screen view asks for
 * the bars' room as padding, as it had before.
 */
final class Edge {

    static void fit(View v) {
        if (Build.VERSION.SDK_INT >= 35) v.setFitsSystemWindows(true);
    }

    private Edge() {
    }
}
