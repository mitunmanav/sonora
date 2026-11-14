package com.mitun.files;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Named daemon threads for scan and decode work off the main thread. */
final class Workers {

    private Workers() {
    }

    /** A single daemon thread at background priority. */
    static ExecutorService background(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }
}
