package com.swadedev.micup4j.internal;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps the JVM alive while a session or shard manager is running, as JDA's non-daemon threads do.
 *
 * <p>Every other Micup4J thread is a daemon or virtual thread, so without this a {@code main} method that returns
 * after {@code build()} would end the process. One shared non-daemon {@code micup4j-keepalive} thread parks while at
 * least one holder's termination future is incomplete and exits when the last one completes, normally or exceptionally.
 */
public final class KeepAlive {
    private static final Object LOCK = new Object();
    /** Guarded by {@link #LOCK}. */
    private static int holders;
    /** Guarded by {@link #LOCK}; {@code null} once the last holder released and the thread is exiting. */
    private static Thread thread;

    private KeepAlive() {}

    /**
     * Keeps the JVM alive until {@code until} completes.
     *
     * @param until the holder's termination future
     */
    public static void holdUntil(final CompletableFuture<?> until) {
        Objects.requireNonNull(until, "until");
        synchronized (LOCK) {
            holders++;
            if (thread == null) thread = Thread.ofPlatform().daemon(false).name("micup4j-keepalive").start(KeepAlive::park);
        }
        until.whenComplete((ignored, error) -> release());
    }

    /**
     * Returns the number of holders currently keeping the JVM alive; for tests.
     *
     * @return the number of incomplete termination futures held
     */
    public static int holders() {
        synchronized (LOCK) { return holders; }
    }

    private static void release() {
        synchronized (LOCK) {
            if (--holders == 0) LOCK.notifyAll();
        }
    }

    private static void park() {
        synchronized (LOCK) {
            while (holders > 0) {
                // An interrupt must not end the process early; only termination of every holder releases the thread.
                try { LOCK.wait(); } catch (InterruptedException ignored) { /* keep holding */ }
            }
            thread = null;
        }
    }
}
