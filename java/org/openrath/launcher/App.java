package org.openrath.launcher;

import android.app.Application;

/**
 * Starts before anything else in OpenRath (the dashboard, the boot receiver, the trip and music
 * services), so a crash in any of them is recorded for the diagnostics screen, not only one after
 * the dashboard has opened.
 */
public final class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Fm.app = this;
        Crash.install(this);
    }
}
