package net.guneyilmaz0.micup4j.utils.concurrent;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A {@link CompletableFuture} that is also a {@link ScheduledFuture}, returned by
 * {@code RestAction.submitAfter(...)}.
 *
 * <p>The delay is backed by a timer task. {@link #getDelay(TimeUnit)} reports the remaining time until the
 * timer fires, and {@link #cancel(boolean)} cancels the pending timer before cancelling this future, so a
 * cancelled delayed action never runs. Dependent stages created from this future are plain
 * {@link CompletableFuture} instances.
 *
 * @param <T> the result type
 */
public final class DelayedCompletableFuture<T> extends CompletableFuture<T> implements ScheduledFuture<T> {
    private volatile ScheduledFuture<?> timer;

    private DelayedCompletableFuture() {}

    /**
     * Creates a future whose timer task is produced by {@code mapping} and scheduled on {@code executor}.
     *
     * <p>The mapping receives the new future so the timer task can complete it.
     *
     * @param <E> the result type
     * @param executor the executor that runs the timer task
     * @param delay the delay before the task runs
     * @param unit the unit of {@code delay}
     * @param mapping a function that returns the timer task for the given future
     * @return the new delayed future
     * @throws java.util.concurrent.RejectedExecutionException if the executor rejects the task
     */
    public static <E> DelayedCompletableFuture<E> make(final ScheduledExecutorService executor, final long delay,
            final TimeUnit unit, final Function<DelayedCompletableFuture<E>, Runnable> mapping) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mapping, "mapping");
        final DelayedCompletableFuture<E> handle = new DelayedCompletableFuture<>();
        final Runnable task = Objects.requireNonNull(mapping.apply(handle), "task");
        handle.timer = executor.schedule(task, delay, unit);
        // A cancel that raced with scheduling must still stop the timer.
        if (handle.isCancelled()) handle.timer.cancel(false);
        return handle;
    }

    /**
     * Returns the remaining delay until the timer fires; zero or negative once it has fired.
     *
     * @param unit the unit of the result
     * @return the remaining delay
     */
    @Override
    public long getDelay(final TimeUnit unit) {
        final ScheduledFuture<?> current = timer;
        return current == null ? 0 : current.getDelay(unit);
    }

    /**
     * Compares remaining delays.
     *
     * @param other the other delayed object
     * @return a negative value, zero or a positive value as this delay is shorter, equal or longer
     */
    @Override
    public int compareTo(final Delayed other) {
        return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
    }

    /**
     * Cancels the pending timer, then cancels this future.
     *
     * @param mayInterruptIfRunning ignored for the timer, passed to {@link CompletableFuture#cancel(boolean)}
     * @return whether this future was cancelled by this call
     */
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        final ScheduledFuture<?> current = timer;
        if (current != null) current.cancel(false);
        return super.cancel(mayInterruptIfRunning);
    }

    /**
     * Returns a plain incomplete {@link CompletableFuture} for dependent stages.
     *
     * @param <U> the dependent result type
     * @return a new incomplete future
     */
    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new CompletableFuture<>();
    }
}
