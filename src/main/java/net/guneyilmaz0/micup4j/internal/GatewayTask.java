package net.guneyilmaz0.micup4j.internal;

import net.guneyilmaz0.micup4j.utils.concurrent.Task;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** A {@link Task} backed by a future. Cancelling it cancels the future, which releases its Gateway request. */
public final class GatewayTask<T> implements Task<T> {
    private final CompletableFuture<T> future;

    /**
     * Wraps a running operation.
     *
     * @param future the operation's result
     */
    public GatewayTask(CompletableFuture<T> future) { this.future = Objects.requireNonNull(future); }

    @Override public boolean isStarted() { return true; }
    @Override public Task<T> onError(Consumer<? super Throwable> callback) {
        Objects.requireNonNull(callback);
        future.whenComplete((value, error) -> { if (error != null) callback.accept(error instanceof CompletionException ? error.getCause() : error); });
        return this;
    }
    @Override public Task<T> onSuccess(Consumer<? super T> callback) {
        Objects.requireNonNull(callback);
        future.whenComplete((value, error) -> { if (error == null) callback.accept(value); });
        return this;
    }
    @Override public Task<T> setTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Timeout must be positive");
        future.orTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
        return this;
    }
    @Override public T get() {
        ThreadGuards.checkBlockingAllowed("Task.get()");
        return future.join();
    }
    @Override public void cancel() { future.cancel(false); }

    /**
     * Returns the backing future.
     *
     * @return the future
     */
    public CompletableFuture<T> future() { return future; }
}
