package net.guneyilmaz0.micup4j.requests;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

/** A lazy action. Each execution creates a new request; cancellation propagates through composition. */
public class RestAction<T> {
    private final Supplier<CompletableFuture<T>> operation;
    public RestAction(Supplier<CompletableFuture<T>> operation) { this.operation = Objects.requireNonNull(operation); }
    public CompletableFuture<T> submit() {
        try { return Objects.requireNonNull(operation.get()); }
        catch (Exception error) { return CompletableFuture.failedFuture(error); }
    }
    public void queue() { queue(value -> {}, RestAction::logFailure); }
    public void queue(Consumer<? super T> success) { queue(success, RestAction::logFailure); }
    public void queue(Consumer<? super T> success, Consumer<? super Throwable> failure) {
        Objects.requireNonNull(success); Objects.requireNonNull(failure);
        submit().whenComplete((value, error) -> {
            try { if (error == null) success.accept(value); else failure.accept(unwrap(error)); }
            catch (Throwable callbackError) { logFailure(new IllegalStateException("REST callback failed")); }
        });
    }
    public T complete() { return await(null); }
    /** A caller-side bound. Expiry also cancels the submitted/composed operation. */
    public T complete(Duration timeout) {
        net.guneyilmaz0.micup4j.config.RestConfig.positive(timeout);
        return await(timeout);
    }
    private T await(Duration timeout) {
        CompletableFuture<T> future = submit();
        try { return timeout == null ? future.get() : future.get(timeout.toNanos(), TimeUnit.NANOSECONDS); }
        catch (InterruptedException error) { future.cancel(true); Thread.currentThread().interrupt(); throw new CompletionException(error); }
        catch (TimeoutException error) { future.cancel(true); throw new CompletionException(error); }
        catch (ExecutionException error) { throw new CompletionException(unwrap(error)); }
    }
    public <R> RestAction<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return new RestAction<>(() -> {
            CompletableFuture<T> source = submit();
            CompletableFuture<R> result = new CompletableFuture<>();
            result.whenComplete((value, error) -> { if (result.isCancelled()) source.cancel(true); });
            source.whenComplete((value, error) -> {
                if (result.isDone()) return;
                if (error != null) transferFailure(result, error);
                else try { result.complete(mapper.apply(value)); } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            return result;
        });
    }
    public <R> RestAction<R> flatMap(Function<? super T, RestAction<R>> mapper) {
        Objects.requireNonNull(mapper);
        return new RestAction<>(() -> {
            CompletableFuture<T> source = submit();
            CompletableFuture<R> result = new CompletableFuture<>();
            AtomicReference<CompletableFuture<R>> next = new AtomicReference<>();
            result.whenComplete((value, error) -> {
                if (result.isCancelled()) { source.cancel(true); var child = next.get(); if (child != null) child.cancel(true); }
            });
            source.whenComplete((value, error) -> {
                if (result.isDone()) return;
                if (error != null) { transferFailure(result, error); return; }
                try {
                    var child = mapper.apply(value).submit(); next.set(child);
                    if (result.isCancelled()) child.cancel(true);
                    child.whenComplete((mapped, failure) -> { if (failure == null) result.complete(mapped); else transferFailure(result, failure); });
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            return result;
        });
    }
    private static void transferFailure(CompletableFuture<?> result, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof CancellationException) result.cancel(false); else result.completeExceptionally(cause);
    }
    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        return error;
    }
    private static void logFailure(Throwable error) { System.getLogger("Micup4J").log(System.Logger.Level.ERROR, "REST action failed", error); }
}
