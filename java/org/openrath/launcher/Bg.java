package org.openrath.launcher;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The app's background threads, at low priority, in three groups so one kind of job can't hold up
 * another: net() for internet fetches (three threads, as a download can take its time), save() for
 * file and settings writes (one thread, so they happen in order and a slow download never delays
 * them), and run() for the rest: reading the unit, listing apps, decoding pictures (two threads).
 * Idle threads end after a while and come back when needed. Long-running loops (OBD-II, the tuner)
 * keep their own threads. serial() gives a queue whose jobs run one after another, in order.
 */
final class Bg {

    private static ExecutorService work, net, disk;

    /** A pool of n low-priority threads named name-1, name-2, …; idle ones end after 30 seconds. */
    private static ExecutorService pool(final String name, int n) {
        ThreadPoolExecutor p = new ThreadPoolExecutor(n, n, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
            private int count;

            @Override
            public Thread newThread(final Runnable r) {
                // the pool's own threads
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        } catch (Throwable ignored) {
                            // keep the default priority
                        }
                        r.run();
                    }
                }, name + "-" + (++count));
                t.setDaemon(true);
                return t;
            }
        });
        p.allowCoreThreadTimeOut(true);
        return p;
    }

    static synchronized ExecutorService pool() {
        if (work == null) work = pool("bg", 2);
        return work;
    }

    private static synchronized ExecutorService netPool() {
        if (net == null) net = pool("net", 3);
        return net;
    }

    /** The one thread for writes: jobs run in the order given. */
    static synchronized ExecutorService saves() {
        if (disk == null) disk = pool("save", 1);
        return disk;
    }

    /** Run a job on a background thread (not for the internet, not for saving files). */
    static void run(Runnable r) {
        pool().execute(r);
    }

    /** Run a job that uses the internet. */
    static void net(Runnable r) {
        netPool().execute(r);
    }

    /** net() when the job uses the internet, else run(): for jobs that do one or the other (stations or songs). */
    static void run(boolean internet, Runnable r) {
        if (internet) net(r);
        else run(r);
    }

    /** Run a job that writes files or settings, after the writes given before it. */
    static void save(Runnable r) {
        saves().execute(r);
    }

    /** A queue of jobs that run one at a time, in the order given, on the run() threads. */
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
