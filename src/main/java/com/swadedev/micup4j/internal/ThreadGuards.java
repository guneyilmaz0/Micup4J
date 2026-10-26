package com.swadedev.micup4j.internal;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * Marks internal scheduler threads on which blocking calls such as {@code RestAction.complete()} are forbidden.
 *
 * <p>Threads created through {@link #guarded(ThreadFactory)} set a thread-local flag before running their task.
 * Blocking APIs call {@link #checkBlockingAllowed(String)} and fail fast instead of deadlocking the Gateway,
 * the REST rate-limit pump or the delay timer.
 */
public final class ThreadGuards {
    private static final ThreadLocal<Boolean> NON_BLOCKING = new ThreadLocal<>();

    private ThreadGuards() {}

    /**
     * Wraps a thread factory so that every created thread is marked as non-blocking.
     *
     * @param base the factory that creates the actual threads
     * @return a factory whose threads reject blocking calls
     */
    public static ThreadFactory guarded(final ThreadFactory base) {
        Objects.requireNonNull(base, "base");
        return task -> {
            Objects.requireNonNull(task, "task");
            return base.newThread(() -> {
                NON_BLOCKING.set(Boolean.TRUE);
                task.run();
            });
        };
    }

    /**
     * Returns whether the current thread was created by a guarded factory.
     *
     * @return {@code true} on a guarded scheduler thread
     */
    public static boolean isGuarded() {
        // Plain ThreadLocal: unguarded threads (including virtual threads) never allocate a map entry just by being checked.
        return NON_BLOCKING.get() == Boolean.TRUE;
    }

    /**
     * Throws if the current thread is a guarded scheduler thread.
     *
     * @param operation the blocking operation name used in the error message
     * @throws IllegalStateException on a guarded thread
     */
    public static void checkBlockingAllowed(final String operation) {
        if (isGuarded()) throw new IllegalStateException(operation + " must not block a Micup4J scheduler thread");
    }
}
