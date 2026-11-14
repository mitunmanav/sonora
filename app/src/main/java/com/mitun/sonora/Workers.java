package com.mitun.sonora;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Named daemon threads for decode and bake work off the main thread. */
public final class Workers {

    private Workers() {
    }

    /** A single daemon thread under this name. */
    public static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(factory(name, Thread.NORM_PRIORITY));
    }

    /** A single daemon thread at background priority. */
    public static ExecutorService background(String name) {
        return Executors.newSingleThreadExecutor(factory(name, Thread.MIN_PRIORITY));
    }

    private static ThreadFactory factory(String name, int priority) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        };
    }
}
