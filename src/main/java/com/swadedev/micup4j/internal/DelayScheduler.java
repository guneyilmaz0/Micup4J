package com.swadedev.micup4j.internal;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Process-wide timer for delayed {@code RestAction} work.
 *
 * <p>The single {@code micup4j-delay} daemon thread only fires timers. Timer tasks must hand off to
 * {@link #handOff()}, which runs user code and {@code submit()} calls on virtual threads named
 * {@code micup4j-delayed-N}. Neither ever touches the Gateway or REST scheduler threads.
 */
public final class DelayScheduler {
    private static final ThreadFactory VIRTUAL = Thread.ofVirtual().name("micup4j-delayed-", 0).factory();
    // A fresh virtual thread per task: the timer thread must only fire timers, and user callbacks may block or take long.
    private static final Executor HAND_OFF = task -> VIRTUAL.newThread(task).start();

    private DelayScheduler() {}

    private static final class Holder {
        private static final ScheduledThreadPoolExecutor TIMER = create();

        private static ScheduledThreadPoolExecutor create() {
            var executor = new ScheduledThreadPoolExecutor(1,
                    ThreadGuards.guarded(Thread.ofPlatform().daemon().name("micup4j-delay").factory()));
            executor.setRemoveOnCancelPolicy(true);
            // Let the timer thread exit when idle; it is recreated on the next schedule. A pending timer keeps it alive.
            executor.setKeepAliveTime(60, TimeUnit.SECONDS);
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }
    }

    /**
     * Returns the lazily created, process-wide timer.
     *
     * @return the shared timer executor
     */
    public static ScheduledExecutorService timer() {
        return Holder.TIMER;
    }

    /**
     * Returns an executor that runs each task on a fresh virtual thread.
     *
     * @return the hand-off executor
     */
    public static Executor handOff() {
        return HAND_OFF;
    }
}
