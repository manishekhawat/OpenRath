package org.openrath.launcher;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * The app's background threads: two, at low priority, shared by the network fetches and the file
 * writes that must stay off the screen's thread. Long-running loops (OBD-II, the tuner) keep their
 * own threads. serial() gives a queue whose jobs run one after another, in order, on these threads.
 */
final class Bg {

    private static ExecutorService pool;

    static synchronized ExecutorService pool() {
        if (pool == null) {
            pool = Executors.newFixedThreadPool(2, new ThreadFactory() {
                private int n;

                @Override
                public Thread newThread(final Runnable r) {
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                            } catch (Throwable ignored) {
                            }
                            r.run();
                        }
                    }, "bg-" + (++n));
                    t.setDaemon(true);
                    return t;
                }
            });
        }
        return pool;
    }

    /** Run a job on a background thread. */
    static void run(Runnable r) {
        pool().execute(r);
    }

    /** A queue of jobs that run one at a time, in the order given. */
    static Serial serial() {
        return new Serial(new Executor() {
            @Override
            public void execute(Runnable r) {
                run(r);
            }
        });
    }

    /** Jobs one after another on another executor (unit-tested with a plain one). */
    static final class Serial implements Executor {
        private final Executor on;
        private final ArrayDeque<Runnable> jobs = new ArrayDeque<>();
        private Runnable active;

        Serial(Executor on) {
            this.on = on;
        }

        @Override
        public synchronized void execute(final Runnable r) {
            jobs.offer(new Runnable() {
                @Override
                public void run() {
                    try {
                        r.run();
                    } catch (Throwable ignored) {
                        // one failed write mustn't stop the ones after it
                    } finally {
                        next();
                    }
                }
            });
            if (active == null) next();
        }

        private synchronized void next() {
            active = jobs.poll();
            if (active != null) on.execute(active);
        }
    }

    private Bg() {
    }
}
