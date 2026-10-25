package net.guneyilmaz0.micup4j.requests;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.*;

/** A lazy action. Each submit, queue or complete executes a new request. */
public final class RestAction<T> {
    private final Supplier<CompletableFuture<T>> operation;

    public RestAction(Supplier<CompletableFuture<T>> operation) {
        this.operation = Objects.requireNonNull(operation);
    }

    public CompletableFuture<T> submit() {
        try {
            return operation.get();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public void queue() {
        queue(value -> {
        }, error -> System.getLogger("Micup4J").log(System.Logger.Level.ERROR, "REST action failed", error));
    }

    public void queue(Consumer<? super T> success) {
        queue(success, error -> System.getLogger("Micup4J").log(System.Logger.Level.ERROR, "REST action failed", error));
    }

    public void queue(Consumer<? super T> success, Consumer<? super Throwable> failure) {
        submit().whenComplete((value, error) -> {
            if (error == null) success.accept(value);
            else failure.accept(unwrap(error));
        });
    }

    public T complete() {
        return submit().join();
    }

    public <R> RestAction<R> map(Function<? super T, ? extends R> mapper) {
        return new RestAction<>(() -> submit().thenApply(mapper));
    }

    public <R> RestAction<R> flatMap(Function<? super T, RestAction<R>> mapper) {
        return new RestAction<>(() -> submit().thenCompose(value -> mapper.apply(value).submit()));
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
