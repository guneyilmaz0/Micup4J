package net.guneyilmaz0.micup4j.utils.concurrent;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * An asynchronous Gateway operation that is already running, modelled on JDA's {@code Task}.
 *
 * @param <T> the result type
 */
public interface Task<T> {
    /**
     * Returns whether the operation was started. Tasks returned by Micup4J are started when they are created.
     *
     * @return {@code true} once started
     */
    boolean isStarted();

    /**
     * Registers a failure callback. It runs at most once, immediately if the task already failed.
     *
     * @param callback receives the failure, including cancellation and timeout
     * @return this task
     */
    Task<T> onError(Consumer<? super Throwable> callback);

    /**
     * Registers a success callback. It runs at most once, immediately if the task already completed.
     *
     * @param callback receives the result
     * @return this task
     */
    Task<T> onSuccess(Consumer<? super T> callback);

    /**
     * Fails the task with a {@link java.util.concurrent.TimeoutException} if it has not completed within {@code timeout}
     * of this call.
     *
     * @param timeout a positive timeout
     * @return this task
     */
    Task<T> setTimeout(Duration timeout);

    /**
     * Blocks until the task completes.
     *
     * @return the result
     * @throws java.util.concurrent.CompletionException wrapping the failure
     * @throws java.util.concurrent.CancellationException if the task was cancelled
     * @throws IllegalStateException on an internal Micup4J scheduler thread
     */
    T get();

    /** Cancels the task. A queued request is never sent; a running one stops collecting results. */
    void cancel();
}
