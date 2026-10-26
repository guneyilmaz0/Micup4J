package com.swadedev.micup4j.requests;

import com.swadedev.micup4j.config.RestConfig;
import com.swadedev.micup4j.internal.DelayScheduler;
import com.swadedev.micup4j.internal.ThreadGuards;
import com.swadedev.micup4j.utils.concurrent.DelayedCompletableFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Delayed;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * A lazy request, modelled on JDA's {@code RestAction}.
 *
 * <p>Nothing happens until the action is executed with {@link #queue()}, {@link #submit()} or {@link #complete()}.
 * Every execution creates a new request. Composed actions ({@link #map(Function)}, {@link #flatMap(Function)},
 * {@link #delay(Duration)}, {@link #onErrorMap(Function)}, {@link #and(RestAction, BiFunction)},
 * {@link #allOf(Collection)}, ...) propagate cancellation of their future to every request they started.
 *
 * <h2>Callback threads</h2>
 * <ul>
 *   <li>REST completions, and therefore {@code queue} callbacks, run on the REST client's completion virtual
 *       threads.</li>
 *   <li>Futures that fail synchronously (client closed, queue full) complete on the calling thread.</li>
 *   <li>Delayed work ({@code queueAfter}, {@code submitAfter}, {@code delay}) runs on virtual threads named
 *       {@code micup4j-delayed-N}, or on the {@link ScheduledExecutorService} you pass explicitly. It never runs on
 *       the Gateway or REST scheduler threads.</li>
 * </ul>
 *
 * <p>A delayed action that fires after its client has shut down fails with the REST client's rejection.
 *
 * @param <T> the result type
 */
public class RestAction<T> {
    private static final System.Logger LOGGER = System.getLogger("Micup4J");
    private static final Consumer<Object> NO_OP = value -> {};
    private static final Consumer<Throwable> LOG_FAILURE = RestAction::logFailure;

    private static volatile Consumer<Object> defaultSuccess = NO_OP;
    private static volatile Consumer<? super Throwable> defaultFailure = LOG_FAILURE;

    private final Supplier<CompletableFuture<T>> operation;

    /**
     * Creates an action that runs {@code operation} on every execution.
     *
     * @param operation supplies a new future per execution
     */
    public RestAction(final Supplier<CompletableFuture<T>> operation) {
        this.operation = Objects.requireNonNull(operation);
    }

    // ---------------------------------------------------------------- default handlers

    /**
     * Sets the process-wide success callback used when {@code queue} receives no or a {@code null} success
     * callback.
     *
     * @param callback the new default, or {@code null} to reset it to a no-op
     */
    public static void setDefaultSuccess(final Consumer<Object> callback) {
        defaultSuccess = callback == null ? NO_OP : callback;
    }

    /**
     * Sets the process-wide failure callback used when {@code queue} receives no or a {@code null} failure
     * callback.
     *
     * @param callback the new default, or {@code null} to reset it to the built-in logger
     */
    public static void setDefaultFailure(final Consumer<? super Throwable> callback) {
        defaultFailure = callback == null ? LOG_FAILURE : callback;
    }

    /**
     * Returns the process-wide default success callback.
     *
     * @return the default success callback, never {@code null}
     */
    public static Consumer<Object> getDefaultSuccess() {
        return defaultSuccess;
    }

    /**
     * Returns the process-wide default failure callback. The built-in default logs at ERROR with the cause and
     * logs cancellations at DEBUG.
     *
     * @return the default failure callback, never {@code null}
     */
    public static Consumer<? super Throwable> getDefaultFailure() {
        return defaultFailure;
    }

    // ---------------------------------------------------------------- execution

    /**
     * Executes the action and returns its future. Cancelling the future cancels the request.
     *
     * @return the future result
     */
    public CompletableFuture<T> submit() {
        try {
            return Objects.requireNonNull(operation.get());
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    /** Executes the action with the default success and failure callbacks. */
    public void queue() {
        queue(null, null);
    }

    /**
     * Executes the action.
     *
     * @param success the success callback, or {@code null} for {@link #getDefaultSuccess()}
     */
    public void queue(final Consumer<? super T> success) {
        queue(success, null);
    }

    /**
     * Executes the action. A throwing success callback is logged and does not invoke the failure callback.
     *
     * @param success the success callback, or {@code null} for {@link #getDefaultSuccess()}
     * @param failure the failure callback, or {@code null} for {@link #getDefaultFailure()}
     */
    public void queue(final Consumer<? super T> success, final Consumer<? super Throwable> failure) {
        attachCallbacks(submit(), success, failure);
    }

    /**
     * Executes the action and blocks until it finishes.
     *
     * @return the result
     * @throws IllegalStateException if called on a Micup4J Gateway, REST scheduler or delay thread
     * @throws CompletionException wrapping the failure cause
     * @throws CancellationException if the action was cancelled
     */
    public T complete() {
        ThreadGuards.checkBlockingAllowed("complete");
        return await(null);
    }

    /**
     * Executes the action and blocks for at most {@code timeout}. Expiry cancels the request.
     *
     * @param timeout the caller-side bound
     * @return the result
     * @throws IllegalStateException if called on a Micup4J Gateway, REST scheduler or delay thread
     * @throws CompletionException wrapping the failure cause or a {@link TimeoutException}
     */
    public T complete(final Duration timeout) {
        RestConfig.positive(timeout);
        ThreadGuards.checkBlockingAllowed("complete");
        return await(timeout);
    }

    // ---------------------------------------------------------------- delayed execution

    /**
     * Executes the action after a delay with the default callbacks.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @return a handle; cancelling it before the timer fires prevents the request, afterwards it cancels the request
     */
    public ScheduledFuture<?> queueAfter(final long delay, final TimeUnit unit) {
        return queueAfter(delay, unit, null, null, DelayScheduler.timer());
    }

    /**
     * Executes the action after a delay.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @param success the success callback, or {@code null} for the default
     * @return a handle that cancels the delayed action
     */
    public ScheduledFuture<?> queueAfter(final long delay, final TimeUnit unit, final Consumer<? super T> success) {
        return queueAfter(delay, unit, success, null, DelayScheduler.timer());
    }

    /**
     * Executes the action after a delay.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @param success the success callback, or {@code null} for the default
     * @param failure the failure callback, or {@code null} for the default
     * @return a handle that cancels the delayed action
     */
    public ScheduledFuture<?> queueAfter(final long delay, final TimeUnit unit, final Consumer<? super T> success,
            final Consumer<? super Throwable> failure) {
        return queueAfter(delay, unit, success, failure, DelayScheduler.timer());
    }

    /**
     * Executes the action after a delay on the given executor.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @param executor the executor that runs the timer and the request submission
     * @return a handle that cancels the delayed action
     */
    public ScheduledFuture<?> queueAfter(final long delay, final TimeUnit unit,
            final ScheduledExecutorService executor) {
        return queueAfter(delay, unit, null, null, executor);
    }

    /**
     * Executes the action after a delay on the given executor.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @param success the success callback, or {@code null} for the default
     * @param executor the executor that runs the timer and the request submission
     * @return a handle that cancels the delayed action
     */
    public ScheduledFuture<?> queueAfter(final long delay, final TimeUnit unit, final Consumer<? super T> success,
            final ScheduledExecutorService executor) {
        return queueAfter(delay, unit, success, null, executor);
    }

    /**
     * Executes the action after a delay on the given executor.
     *
     * <p>Like JDA, cancelling the returned handle invokes neither callback: the caller already knows the action was
     * cancelled. If the request itself is cancelled by other means (for example a cancelled upstream future), the
     * failure callback still receives a {@link CancellationException}.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @param success the success callback, or {@code null} for the default
     * @param failure the failure callback, or {@code null} for the default
     * @param executor the executor that runs the timer and the request submission
     * @return a handle that cancels the delayed action
     */
    public ScheduledFuture<?> queueAfter(final long delay, final TimeUnit unit, final Consumer<? super T> success,
            final Consumer<? super Throwable> failure, final ScheduledExecutorService executor) {
        final DelayedCompletableFuture<T> delayed = submitAfter(delay, unit, executor);
        final QueueAfterHandle<T> handle = new QueueAfterHandle<>(delayed);
        final Consumer<? super Throwable> onFailure = failure == null ? defaultFailure : failure;
        attachCallbacks(delayed, success, error -> {
            // A cancel through the returned handle is the caller's own decision; JDA reports it to no callback.
            if (!(error instanceof CancellationException && handle.cancelledByCaller)) onFailure.accept(error);
        });
        return handle;
    }

    /**
     * Submits the action after a delay.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @return the delayed future; cancelling it cancels the timer or the running request
     */
    public DelayedCompletableFuture<T> submitAfter(final long delay, final TimeUnit unit) {
        return submitAfter(delay, unit, DelayScheduler.timer());
    }

    /**
     * Submits the action after a delay on the given executor. The executor's thread runs {@link #submit()}.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @param executor the executor that runs the timer
     * @return the delayed future; cancelling it cancels the timer or the running request
     */
    public DelayedCompletableFuture<T> submitAfter(final long delay, final TimeUnit unit,
            final ScheduledExecutorService executor) {
        checkDelay(delay, unit);
        Objects.requireNonNull(executor, "executor");
        final boolean handOff = executor == DelayScheduler.timer();
        return DelayedCompletableFuture.make(executor, delay, unit, handle -> () -> {
            final Runnable fire = () -> {
                if (handle.isDone()) return;
                final List<Future<?>> children = new CopyOnWriteArrayList<>();
                linkCancellation(handle, children);
                final CompletableFuture<T> inner = submit();
                track(handle, children, inner);
                forward(inner, handle);
            };
            if (handOff) DelayScheduler.handOff().execute(fire);
            else fire.run();
        });
    }

    /**
     * Waits for the delay, then executes the action and blocks until it finishes.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @return the result
     * @throws IllegalStateException if called on a Micup4J Gateway, REST scheduler or delay thread
     * @throws CompletionException wrapping the failure cause, or an {@link InterruptedException}
     */
    public T completeAfter(final long delay, final TimeUnit unit) {
        checkDelay(delay, unit);
        ThreadGuards.checkBlockingAllowed("completeAfter");
        try {
            unit.sleep(delay);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CompletionException(error);
        }
        return complete();
    }

    /**
     * Returns an action that delays a successful result. Failures are not delayed.
     *
     * @param duration the delay, not negative
     * @return the delayed action
     */
    public RestAction<T> delay(final Duration duration) {
        return delay(duration, DelayScheduler.timer());
    }

    /**
     * Returns an action that delays a successful result on the given executor. Failures are not delayed.
     *
     * @param duration the delay, not negative
     * @param executor the executor that runs the delay and completes the result
     * @return the delayed action
     */
    public RestAction<T> delay(final Duration duration, final ScheduledExecutorService executor) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) throw new IllegalArgumentException("Delay must not be negative");
        return delay(saturatedNanos(duration), TimeUnit.NANOSECONDS, executor);
    }

    /**
     * Returns an action that delays a successful result. Failures are not delayed.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @return the delayed action
     */
    public RestAction<T> delay(final long delay, final TimeUnit unit) {
        return delay(delay, unit, DelayScheduler.timer());
    }

    /**
     * Returns an action that delays a successful result on the given executor. Failures are not delayed.
     * Cancelling the result cancels both the source request and a pending timer.
     *
     * @param delay the delay, not negative
     * @param unit the unit of {@code delay}
     * @param executor the executor that runs the delay and completes the result
     * @return the delayed action
     */
    public RestAction<T> delay(final long delay, final TimeUnit unit, final ScheduledExecutorService executor) {
        checkDelay(delay, unit);
        Objects.requireNonNull(executor, "executor");
        final boolean handOff = executor == DelayScheduler.timer();
        return new RestAction<>(() -> {
            final CompletableFuture<T> result = new CompletableFuture<>();
            final List<Future<?>> children = new CopyOnWriteArrayList<>();
            linkCancellation(result, children);
            final CompletableFuture<T> source = submit();
            track(result, children, source);
            source.whenComplete((value, error) -> {
                if (result.isDone()) return;
                if (error != null) {
                    transferFailure(result, error);
                    return;
                }
                final Runnable finish = () -> result.complete(value);
                try {
                    track(result, children, executor.schedule(() -> {
                        // The timer thread only fires; completing the result may run user callbacks, so hand off to a virtual thread unless
                        // the caller supplied its own executor.
                        if (handOff) DelayScheduler.handOff().execute(finish);
                        else finish.run();
                    }, delay, unit));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
            return result;
        });
    }

    // ---------------------------------------------------------------- transformation

    /**
     * Returns an action that maps the result.
     *
     * @param <R> the mapped type
     * @param mapper the mapping function; an exception fails the action
     * @return the mapped action
     */
    public <R> RestAction<R> map(final Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return new RestAction<>(() -> {
            final CompletableFuture<R> result = new CompletableFuture<>();
            final CompletableFuture<T> source = submit();
            linkCancellation(result, List.of(source));
            source.whenComplete((value, error) -> {
                if (result.isDone()) return;
                if (error != null) transferFailure(result, error);
                else complete(result, () -> mapper.apply(value));
            });
            return result;
        });
    }

    /**
     * Returns an action that runs another action with this action's result.
     *
     * @param <R> the result type of the next action
     * @param mapper returns the next action
     * @return the chained action
     */
    public <R> RestAction<R> flatMap(final Function<? super T, ? extends RestAction<R>> mapper) {
        Objects.requireNonNull(mapper);
        return new RestAction<>(() -> {
            final CompletableFuture<R> result = new CompletableFuture<>();
            final List<Future<?>> children = new CopyOnWriteArrayList<>();
            linkCancellation(result, children);
            final CompletableFuture<T> source = submit();
            track(result, children, source);
            source.whenComplete((value, error) -> {
                if (result.isDone()) return;
                if (error != null) {
                    transferFailure(result, error);
                    return;
                }
                submitChild(result, children, () -> mapper.apply(value));
            });
            return result;
        });
    }

    /**
     * Returns an action that passes the result to {@code consumer} before completing with it. An exception thrown
     * by the consumer fails the action.
     *
     * @param consumer the side effect
     * @return the new action
     */
    public RestAction<T> onSuccess(final Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return map(value -> {
            consumer.accept(value);
            return value;
        });
    }

    /**
     * Returns an action that recovers from every failure with a fallback value. Cancellation is never mapped.
     *
     * @param map maps the unwrapped failure cause to a value
     * @return the new action
     */
    public RestAction<T> onErrorMap(final Function<? super Throwable, ? extends T> map) {
        return onErrorMap(null, map);
    }

    /**
     * Returns an action that recovers from matching failures with a fallback value. Cancellation is never mapped;
     * an exception thrown by {@code condition} or {@code map} becomes the failure.
     *
     * @param condition selects the failures to recover from, or {@code null} for all
     * @param map maps the unwrapped failure cause to a value
     * @return the new action
     */
    public RestAction<T> onErrorMap(final Predicate<? super Throwable> condition,
            final Function<? super Throwable, ? extends T> map) {
        Objects.requireNonNull(map, "map");
        return new RestAction<>(() -> {
            final CompletableFuture<T> result = new CompletableFuture<>();
            final CompletableFuture<T> source = submit();
            linkCancellation(result, List.of(source));
            source.whenComplete((value, error) -> {
                if (result.isDone()) return;
                if (error == null) {
                    result.complete(value);
                    return;
                }
                final Throwable cause = unwrap(error);
                if (cause instanceof CancellationException) {
                    result.cancel(false);
                    return;
                }
                try {
                    if (condition == null || condition.test(cause)) result.complete(map.apply(cause));
                    else result.completeExceptionally(cause);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
            return result;
        });
    }

    /**
     * Returns an action that recovers from every failure by running a fallback action. Cancellation is never
     * mapped.
     *
     * @param map maps the unwrapped failure cause to a fallback action
     * @return the new action
     */
    public RestAction<T> onErrorFlatMap(final Function<? super Throwable, ? extends RestAction<? extends T>> map) {
        return onErrorFlatMap(null, map);
    }

    /**
     * Returns an action that recovers from matching failures by running a fallback action. Cancelling the result
     * also cancels the fallback request.
     *
     * @param condition selects the failures to recover from, or {@code null} for all
     * @param map maps the unwrapped failure cause to a fallback action
     * @return the new action
     */
    public RestAction<T> onErrorFlatMap(final Predicate<? super Throwable> condition,
            final Function<? super Throwable, ? extends RestAction<? extends T>> map) {
        Objects.requireNonNull(map, "map");
        return new RestAction<>(() -> {
            final CompletableFuture<T> result = new CompletableFuture<>();
            final List<Future<?>> children = new CopyOnWriteArrayList<>();
            linkCancellation(result, children);
            final CompletableFuture<T> source = submit();
            track(result, children, source);
            source.whenComplete((value, error) -> {
                if (result.isDone()) return;
                if (error == null) {
                    result.complete(value);
                    return;
                }
                final Throwable cause = unwrap(error);
                if (cause instanceof CancellationException) {
                    result.cancel(false);
                    return;
                }
                try {
                    if (condition != null && !condition.test(cause)) {
                        result.completeExceptionally(cause);
                        return;
                    }
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                    return;
                }
                submitChild(result, children, () -> map.apply(cause));
            });
            return result;
        });
    }

    // ---------------------------------------------------------------- combination

    /**
     * Returns an action that runs this and {@code other} concurrently and combines both results. The first
     * failure fails the result and cancels the other request.
     *
     * @param <U> the other result type
     * @param <O> the combined type
     * @param other the other action
     * @param accumulator combines both results
     * @return the combined action
     */
    @SuppressWarnings("unchecked")
    public <U, O> RestAction<O> and(final RestAction<U> other,
            final BiFunction<? super T, ? super U, ? extends O> accumulator) {
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(accumulator, "accumulator");
        final List<RestAction<?>> actions = List.of(this, other);
        return new RestAction<>(() -> combine(actions,
                results -> accumulator.apply((T) results.get(0), (U) results.get(1))));
    }

    /**
     * Returns an action that runs this and {@code other} concurrently and discards both results.
     *
     * @param <U> the other result type
     * @param other the other action
     * @return the combined action
     */
    public <U> RestAction<Void> and(final RestAction<U> other) {
        return and(other, (first, second) -> null);
    }

    /**
     * Returns an action that runs this and the given actions concurrently and collects the results in argument
     * order, starting with this action's result.
     *
     * @param first the first other action
     * @param other further actions
     * @return an action producing an unmodifiable list of results
     */
    @SafeVarargs
    public final RestAction<List<T>> zip(final RestAction<? extends T> first, final RestAction<? extends T>... other) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(other, "other");
        final List<RestAction<? extends T>> actions = new ArrayList<>(other.length + 2);
        actions.add(this);
        actions.add(first);
        actions.addAll(Arrays.asList(other));
        return allOf(actions);
    }

    /**
     * Returns an action that runs all actions concurrently and collects the results in argument order.
     *
     * @param <E> the element type
     * @param first the first action
     * @param others further actions
     * @return an action producing an unmodifiable list of results
     */
    @SafeVarargs
    public static <E> RestAction<List<E>> allOf(final RestAction<? extends E> first,
            final RestAction<? extends E>... others) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(others, "others");
        final List<RestAction<? extends E>> actions = new ArrayList<>(others.length + 1);
        actions.add(first);
        actions.addAll(Arrays.asList(others));
        return allOf(actions);
    }

    /**
     * Returns an action that runs all actions concurrently and collects the results in argument order. The first
     * failure fails the result and cancels the others. An empty collection completes with an empty list.
     *
     * @param <E> the element type
     * @param actions the actions
     * @return an action producing an unmodifiable list of results
     */
    @SuppressWarnings("unchecked")
    public static <E> RestAction<List<E>> allOf(final Collection<? extends RestAction<? extends E>> actions) {
        final List<RestAction<?>> copy = copyActions(actions);
        return new RestAction<>(() -> combine(copy, results -> (List<E>) results));
    }

    /**
     * Returns an action that runs all actions concurrently and reduces the results, in argument order, with a
     * collector.
     *
     * @param <E> the element type
     * @param <A> the collector's accumulation type
     * @param <O> the result type
     * @param actions the actions
     * @param collector reduces the results
     * @return the accumulated action
     */
    @SuppressWarnings("unchecked")
    public static <E, A, O> RestAction<O> accumulate(final Collection<? extends RestAction<? extends E>> actions,
            final Collector<? super E, A, ? extends O> collector) {
        Objects.requireNonNull(collector, "collector");
        final List<RestAction<?>> copy = copyActions(actions);
        return new RestAction<>(() -> combine(copy, results -> {
            final A container = collector.supplier().get();
            for (Object element : results) collector.accumulator().accept(container, (E) element);
            return collector.finisher().apply(container);
        }));
    }

    // ---------------------------------------------------------------- internals

    private static List<RestAction<?>> copyActions(final Collection<? extends RestAction<?>> actions) {
        Objects.requireNonNull(actions, "actions");
        final List<RestAction<?>> copy = new ArrayList<>(actions.size());
        for (RestAction<?> action : actions) copy.add(Objects.requireNonNull(action, "action"));
        return copy;
    }

    /** Submits every action in order, fails fast, and finishes with an unmodifiable, ordered result list. */
    private static <O> CompletableFuture<O> combine(final List<RestAction<?>> actions,
            final Function<List<Object>, ? extends O> finisher) {
        final CompletableFuture<O> result = new CompletableFuture<>();
        if (actions.isEmpty()) {
            complete(result, () -> finisher.apply(List.of()));
            return result;
        }
        final Object[] values = new Object[actions.size()];
        final AtomicInteger remaining = new AtomicInteger(actions.size());
        // Pre-sized and synchronized: allOf/accumulate may track many children, so a copy-on-write list would cost
        // O(N^2). track() re-checks the result after adding, so a child added after cancellation is still cancelled.
        final List<Future<?>> children = Collections.synchronizedList(new ArrayList<>(actions.size()));
        linkCancellation(result, children);
        for (int index = 0; index < actions.size() && !result.isDone(); index++) {
            final int slot = index;
            final CompletableFuture<?> child = actions.get(index).submit();
            track(result, children, child);
            child.whenComplete((value, error) -> {
                if (result.isDone()) return;
                if (error != null) {
                    transferFailure(result, error);
                    return;
                }
                synchronized (values) {
                    values[slot] = value;
                }
                if (remaining.decrementAndGet() == 0) {
                    final List<Object> ordered;
                    synchronized (values) {
                        ordered = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
                    }
                    complete(result, () -> finisher.apply(ordered));
                }
            });
        }
        return result;
    }

    private static <R> void submitChild(final CompletableFuture<R> result, final List<Future<?>> children,
            final Supplier<? extends RestAction<? extends R>> next) {
        try {
            final RestAction<? extends R> action = Objects.requireNonNull(next.get(), "next action");
            final CompletableFuture<? extends R> child = action.submit();
            track(result, children, child);
            forward(child, result);
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
    }

    private static <R> void forward(final CompletableFuture<? extends R> source, final CompletableFuture<R> target) {
        source.whenComplete((value, error) -> {
            if (error == null) target.complete(value);
            else transferFailure(target, error);
        });
    }

    private static <R> void complete(final CompletableFuture<R> result, final Supplier<? extends R> value) {
        try {
            result.complete(value.get());
        } catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
    }

    /**
     * When {@code result} completes exceptionally (failure or cancellation), cancels every child. Children added
     * later must go through {@link #track} so a late child is cancelled too.
     */
    private static void linkCancellation(final CompletableFuture<?> result,
            final Collection<? extends Future<?>> children) {
        result.whenComplete((value, error) -> {
            if (error == null) return;
            // toArray() is atomic for both copy-on-write and synchronized lists, so no explicit lock is needed.
            for (Object child : children.toArray()) cancelChild((Future<?>) child);
        });
    }

    private static void track(final CompletableFuture<?> result, final List<Future<?>> children,
            final Future<?> child) {
        children.add(child);
        // Re-check after registering: linkCancellation may already have walked the children, so a child added after the
        // result was cancelled would otherwise keep running.
        if (result.isCompletedExceptionally()) cancelChild(child);
    }

    private static void cancelChild(final Future<?> child) {
        // Never interrupt timer threads; the flag has no effect on CompletableFuture.
        child.cancel(!(child instanceof ScheduledFuture));
    }

    private static <T> void attachCallbacks(final CompletableFuture<T> future, final Consumer<? super T> success,
            final Consumer<? super Throwable> failure) {
        final Consumer<? super T> onSuccess = success == null ? defaultSuccess : success;
        final Consumer<? super Throwable> onFailure = failure == null ? defaultFailure : failure;
        future.whenComplete((value, error) -> {
            if (error == null) {
                try {
                    onSuccess.accept(value);
                } catch (Throwable callbackError) {
                    LOGGER.log(System.Logger.Level.ERROR, "REST success callback failed", callbackError);
                }
                return;
            }
            try {
                onFailure.accept(unwrap(error));
            } catch (Throwable callbackError) {
                LOGGER.log(System.Logger.Level.ERROR, "REST failure callback failed", callbackError);
            }
        });
    }

    private T await(final Duration timeout) {
        final CompletableFuture<T> future = submit();
        try {
            return timeout == null ? future.get() : future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CompletionException(error);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new CompletionException(error);
        } catch (ExecutionException error) {
            throw new CompletionException(unwrap(error));
        }
    }

    private static void checkDelay(final long delay, final TimeUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (delay < 0) throw new IllegalArgumentException("Delay must not be negative");
    }

    private static long saturatedNanos(final Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static void transferFailure(final CompletableFuture<?> result, final Throwable error) {
        final Throwable cause = unwrap(error);
        if (cause instanceof CancellationException) result.cancel(false);
        else result.completeExceptionally(cause);
    }

    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static void logFailure(final Throwable error) {
        if (error instanceof CancellationException) {
            LOGGER.log(System.Logger.Level.DEBUG, "REST action cancelled", error);
        } else {
            LOGGER.log(System.Logger.Level.ERROR, "REST action failed", error);
        }
    }

    /**
     * The handle returned by {@code queueAfter}. It delegates to the delayed future but remembers whether the caller
     * cancelled it, so the failure callback can be skipped for caller cancellations only.
     */
    private static final class QueueAfterHandle<T> implements ScheduledFuture<T> {
        private final DelayedCompletableFuture<T> delegate;
        private volatile boolean cancelledByCaller;

        private QueueAfterHandle(final DelayedCompletableFuture<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            // Set before cancelling so the completion callback, which runs inside cancel, already sees it. The
            // callback only consults the flag for CancellationException, so a real failure is never hidden.
            cancelledByCaller = true;
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }

        @Override
        public T get(final long timeout, final TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return delegate.getDelay(unit);
        }

        @Override
        public int compareTo(final Delayed other) {
            return delegate.compareTo(other);
        }
    }
}
