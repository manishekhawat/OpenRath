package org.openrath.launcher;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Notices the app freezing: a background thread asks the screen's thread to answer every few
 * seconds, and when no answer comes for 10 seconds, what that thread is stuck on goes into the crash
 * reports and Diagnostics (once per freeze). Nothing is stopped or killed. It runs only while the
 * screen is on, and uses the awake clock, so the stereo's deep sleep never looks like a freeze.
 */
final class Watchdog {

    static final long PING_MS = 5000, FROZEN_MS = 10000;

    /** Pings and answers, by the awake clock; pure, for the tests. */
    static final class Beat {
        private long askedAt, answeredAt;
        private boolean told;

        /** A ping went out at now, unless one is still waiting for its answer. True if one went out. */
        boolean ask(long now) {
            if (askedAt > answeredAt) return false;
            askedAt = now;
            return true;
        }

        /** The screen's thread answered. */
        void answer(long now) {
            answeredAt = Math.max(now, askedAt);
            told = false;
        }

        /** How long the screen's thread has been stuck, the first time it passes the limit in a freeze; else 0. */
        long frozen(long now, long limit) {
            if (told || askedAt <= answeredAt || now - askedAt < limit) return 0;
            told = true;
            return now - askedAt;
        }
    }

    private static Thread thread;

    /** Start watching (when the dashboard opens and when the screen comes on); nothing if already watching. */
    static synchronized void start() {
        if (thread != null) return;
        final Handler main = new Handler(Looper.getMainLooper());
        final Thread screen = Looper.getMainLooper().getThread();
        final Beat beat = new Beat();
        final Runnable pong = new Runnable() {
            @Override
            public void run() {
                synchronized (beat) {
                    beat.answer(SystemClock.uptimeMillis());
                }
            }
        };
        // its own thread: it sleeps most of the time, and on a Bg one it would hold a place for good
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        long now = SystemClock.uptimeMillis(), stuck;
                        boolean ask;
                        synchronized (beat) {
                            ask = beat.ask(now);
                            stuck = beat.frozen(now, FROZEN_MS);
                        }
                        if (ask) main.post(pong);
                        if (stuck > 0) Crash.froze(stuck, screen.getStackTrace());
                        Thread.sleep(PING_MS);
                    }
                } catch (InterruptedException ignored) {
                    // stopped
                } catch (Throwable e) {
                    Diag.note("watchdog", e);
                }
            }
        }, "watchdog");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /** Stop watching (the screen went off). */
    static synchronized void stop() {
        if (thread == null) return;
        thread.interrupt();
        thread = null;
    }

    private Watchdog() {
    }
}
