package com.swadedev.micup4j;

import com.swadedev.micup4j.internal.ThreadGuards;
import com.swadedev.micup4j.requests.RestAction;
import com.swadedev.micup4j.utils.concurrent.DelayedCompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestActionTest {
    private static final Logger JUL = Logger.getLogger("Micup4J");

    @AfterEach void resetDefaults() {
        RestAction.setDefaultSuccess(null);
        RestAction.setDefaultFailure(null);
    }

    @Test void mappedCancellationReachesSource() {
        var upstream = new CompletableFuture<String>();
        var action = new RestAction<>(() -> upstream).map(String::length).submit();
        action.cancel(true);
        assertTrue(upstream.isCancelled());
    }
    @Test void flatMapCancellationReachesActiveChild() {
        var child = new CompletableFuture<Integer>();
        var action = new RestAction<>(() -> CompletableFuture.completedFuture("ready"))
                .flatMap(value -> new RestAction<>(() -> child)).submit();
        action.cancel(true);
        assertTrue(child.isCancelled());
    }
    @Test void blockingTimeoutCancelsSubmittedAction() {
        var source = new CompletableFuture<String>();
        var action = new RestAction<>(() -> source);
        var error = assertThrows(CompletionException.class, () -> action.complete(Duration.ofMillis(30)));
        assertInstanceOf(TimeoutException.class, error.getCause());
        assertTrue(source.isCancelled());
    }

    // ------------------------------------------------------------ queue defaults and callback failures

    @Test void queueWithNullCallbacksUsesDefaults() {
        var received = new CopyOnWriteArrayList<Object>();
        RestAction.setDefaultSuccess(received::add);
        RestAction.setDefaultFailure(received::add);
        var boom = new IllegalStateException("boom");
        completed("value").queue(null);
        new RestAction<String>(() -> CompletableFuture.failedFuture(boom)).queue(value -> {}, null);
        new RestAction<String>(() -> CompletableFuture.failedFuture(boom)).queue();
        assertEquals(List.of("value", boom, boom), received);
    }
    @Test void defaultHandlersResetOnNull() {
        RestAction.setDefaultSuccess(value -> {});
        RestAction.setDefaultFailure(error -> {});
        RestAction.setDefaultSuccess(null);
        RestAction.setDefaultFailure(null);
        assertTrue(RestAction.getDefaultSuccess() != null && RestAction.getDefaultFailure() != null);
    }
    @Test void throwingCallbacksAreLoggedWithCause() {
        var records = new CopyOnWriteArrayList<LogRecord>();
        Handler handler = capture(records);
        JUL.addHandler(handler);
        try {
            var successError = new IllegalArgumentException("success callback");
            var failureError = new IllegalArgumentException("failure callback");
            var failureCalls = new AtomicInteger();
            completed("x").queue(value -> { throw successError; }, error -> failureCalls.incrementAndGet());
            new RestAction<String>(() -> CompletableFuture.failedFuture(new RuntimeException("rest")))
                    .queue(value -> {}, error -> { throw failureError; });
            assertEquals(0, failureCalls.get(), "success callback errors must not reach the failure callback");
            assertTrue(records.stream().anyMatch(r -> "REST success callback failed".equals(r.getMessage())
                    && r.getThrown() == successError));
            assertTrue(records.stream().anyMatch(r -> "REST failure callback failed".equals(r.getMessage())
                    && r.getThrown() == failureError));
        } finally {
            JUL.removeHandler(handler);
        }
    }

    // ------------------------------------------------------------ delayed execution

    @Test void queueAfterCancelledBeforeFiringNeverSubmits() throws Exception {
        var submissions = new AtomicInteger();
        var action = new RestAction<>(() -> {
            submissions.incrementAndGet();
            return CompletableFuture.completedFuture("x");
        });
        ScheduledFuture<?> handle = action.queueAfter(150, TimeUnit.MILLISECONDS, value -> {}, error -> {});
        assertTrue(handle.cancel(false));
        Thread.sleep(300);
        assertEquals(0, submissions.get());
        assertTrue(handle.isCancelled());
    }
    @Test void cancellingQueueAfterHandleInvokesNoCallback() throws Exception {
        var calls = new CopyOnWriteArrayList<Object>();
        RestAction.setDefaultFailure(calls::add);
        ScheduledFuture<?> beforeFiring = completed("x").queueAfter(10, TimeUnit.SECONDS, calls::add, calls::add);
        ScheduledFuture<?> withDefaults = completed("y").queueAfter(10, TimeUnit.SECONDS);
        var inner = new CompletableFuture<String>();
        var fired = new CountDownLatch(1);
        ScheduledFuture<?> afterFiring = new RestAction<>(() -> {
            fired.countDown();
            return inner;
        }).queueAfter(10, TimeUnit.MILLISECONDS, calls::add, calls::add);
        assertTrue(fired.await(5, TimeUnit.SECONDS));
        assertTrue(beforeFiring.cancel(false));
        assertTrue(withDefaults.cancel(true));
        assertTrue(afterFiring.cancel(true));
        assertTrue(inner.isCancelled());
        assertTrue(beforeFiring.isCancelled() && afterFiring.isCancelled() && withDefaults.isDone());
        Thread.sleep(100);
        assertEquals(List.of(), calls, "JDA invokes no callback when the caller cancels a queueAfter handle");
    }
    @Test void innerCancellationStillReachesQueueAfterFailureCallback() throws Exception {
        var inner = new CompletableFuture<String>();
        inner.cancel(false); // The request itself is cancelled, not the caller's handle.
        var failure = new CompletableFuture<Throwable>();
        ScheduledFuture<?> handle = new RestAction<>(() -> inner)
                .queueAfter(10, TimeUnit.MILLISECONDS, value -> {}, failure::complete);
        assertInstanceOf(CancellationException.class, failure.get(5, TimeUnit.SECONDS));
        assertTrue(handle.isCancelled());
    }
    @Test void cancellingSubmitAfterFutureStillCompletesWithCancellation() {
        DelayedCompletableFuture<String> future = completed("x").submitAfter(10, TimeUnit.SECONDS);
        var observed = new AtomicReference<Throwable>();
        future.whenComplete((value, error) -> observed.set(error));
        assertTrue(future.cancel(false));
        assertInstanceOf(CancellationException.class, observed.get());
    }
    @Test void cancellingAfterFiringCancelsInnerFuture() throws Exception {
        var inner = new CompletableFuture<String>();
        var fired = new CountDownLatch(1);
        var action = new RestAction<>(() -> {
            fired.countDown();
            return inner;
        });
        DelayedCompletableFuture<String> handle = action.submitAfter(10, TimeUnit.MILLISECONDS);
        assertTrue(fired.await(5, TimeUnit.SECONDS));
        handle.cancel(true);
        assertTrue(inner.isCancelled());
    }
    @Test void submitAfterDelayDecreases() throws Exception {
        var handle = completed("x").submitAfter(10, TimeUnit.SECONDS);
        try {
            long first = handle.getDelay(TimeUnit.NANOSECONDS);
            Thread.sleep(30);
            long second = handle.getDelay(TimeUnit.NANOSECONDS);
            assertTrue(first > 0 && second < first, first + " then " + second);
        } finally {
            handle.cancel(false);
        }
    }
    @Test void submitAfterCompletesWithResult() throws Exception {
        assertEquals("x", completed("x").submitAfter(10, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS));
    }
    @Test void completeAfterBlocksForAtLeastTheDelay() {
        long start = System.nanoTime();
        assertEquals("x", completed("x").completeAfter(100, TimeUnit.MILLISECONDS));
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(100));
    }
    @Test void negativeDelaysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> completed("x").delay(Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> completed("x").queueAfter(-1, TimeUnit.SECONDS));
        assertThrows(NullPointerException.class, () -> completed("x").delay(1, null));
    }
    @Test void delayCancellationDuringTimerPhaseRemovesTimer() throws Exception {
        var timer = new ScheduledThreadPoolExecutor(1);
        timer.setRemoveOnCancelPolicy(true);
        try {
            var result = completed("x").delay(10, TimeUnit.SECONDS, timer).submit();
            assertEquals(1, timer.getQueue().size());
            result.cancel(true);
            assertTrue(result.isCancelled());
            assertTrue(timer.getQueue().isEmpty());
        } finally {
            timer.shutdownNow();
        }
    }
    @Test void delayCancelsPendingSource() {
        var source = new CompletableFuture<String>();
        new RestAction<>(() -> source).delay(Duration.ofSeconds(10)).submit().cancel(true);
        assertTrue(source.isCancelled());
    }
    @Test void delayDoesNotDelayFailures() {
        var boom = new IllegalStateException("boom");
        var future = new RestAction<String>(() -> CompletableFuture.failedFuture(boom))
                .delay(Duration.ofSeconds(30)).submit();
        var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertSame(boom, error.getCause());
    }
    @Test void delayedWorkRunsOnVirtualDelayThreads() throws Exception {
        var names = new CopyOnWriteArrayList<String>();
        var done = new CountDownLatch(2);
        completed("x").queueAfter(10, TimeUnit.MILLISECONDS, value -> {
            names.add(Thread.currentThread().getName());
            done.countDown();
        });
        completed("y").delay(Duration.ofMillis(10)).onSuccess(value -> names.add(Thread.currentThread().getName()))
                .queue(value -> done.countDown());
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(2, names.size());
        for (String name : names) {
            assertTrue(name.startsWith("micup4j-delayed-"), name);
            assertFalse(name.startsWith("micup4j-rest-scheduler") || name.startsWith("micup4j-gateway"));
        }
    }

    // ------------------------------------------------------------ error recovery

    @Test void onErrorMapRecoversOnlyMatchingFailures() throws Exception {
        var failing = new RestAction<String>(() -> CompletableFuture.failedFuture(new IllegalStateException("x")));
        assertEquals("fallback", failing.onErrorMap(IllegalStateException.class::isInstance, e -> "fallback")
                .submit().get());
        var error = assertThrows(ExecutionException.class,
                () -> failing.onErrorMap(IllegalArgumentException.class::isInstance, e -> "fallback").submit().get());
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals("all", failing.onErrorMap(e -> "all").submit().get());
    }
    @Test void onErrorMapNeverMapsCancellation() {
        var calls = new AtomicInteger();
        var source = new CompletableFuture<String>();
        var result = new RestAction<>(() -> source).onErrorMap(e -> {
            calls.incrementAndGet();
            return "mapped";
        }).submit();
        source.cancel(true);
        assertTrue(result.isCancelled());
        assertEquals(0, calls.get());
    }
    @Test void onErrorMapHandlerExceptionBecomesFailure() {
        var handlerError = new IllegalArgumentException("handler");
        var future = new RestAction<String>(() -> CompletableFuture.failedFuture(new RuntimeException()))
                .onErrorMap(e -> { throw handlerError; }).submit();
        var error = assertThrows(ExecutionException.class, future::get);
        assertSame(handlerError, error.getCause());
    }
    @Test void onErrorFlatMapRunsFallbackAndPropagatesCancellation() throws Exception {
        var failing = new RestAction<String>(() -> CompletableFuture.failedFuture(new IllegalStateException()));
        assertEquals("recovered", failing.onErrorFlatMap(e -> completed("recovered")).submit().get());
        var child = new CompletableFuture<String>();
        var result = failing.onErrorFlatMap(IllegalStateException.class::isInstance, e -> new RestAction<>(() -> child))
                .submit();
        result.cancel(true);
        assertTrue(child.isCancelled());
    }

    // ------------------------------------------------------------ combinators

    @Test void andCombinesBothResults() throws Exception {
        assertEquals("a1", completed("a").and(completed(1), (s, i) -> s + i).submit().get());
        assertEquals(null, completed("a").and(completed(1)).submit().get());
    }
    @Test void zipAndAllOfKeepArgumentOrder() throws Exception {
        var slow = new CompletableFuture<String>();
        var zipped = new RestAction<>(() -> slow).zip(completed("b"), completed("c")).submit();
        slow.complete("a");
        assertEquals(List.of("a", "b", "c"), zipped.get());
        var all = RestAction.allOf(completed(1), completed(2), completed(3)).submit().get();
        assertEquals(List.of(1, 2, 3), all);
        assertThrows(UnsupportedOperationException.class, () -> all.add(4));
    }
    @Test void allOfEmptyCompletesWithEmptyList() throws Exception {
        assertEquals(List.of(), RestAction.<String>allOf(new ArrayList<RestAction<String>>()).submit().get());
    }
    @Test void allOfFailsFastAndCancelsSiblings() {
        var pending = new CompletableFuture<String>();
        var trigger = new CompletableFuture<String>();
        var boom = new IllegalStateException("boom");
        var result = RestAction.allOf(List.of(new RestAction<>(() -> pending), new RestAction<>(() -> trigger)))
                .submit();
        trigger.completeExceptionally(boom);
        var error = assertThrows(ExecutionException.class, result::get);
        assertSame(boom, error.getCause());
        assertTrue(pending.isCancelled());
    }
    @Test void cancellingCombinedActionCancelsEveryChild() {
        var first = new CompletableFuture<String>();
        var second = new CompletableFuture<String>();
        var third = new CompletableFuture<String>();
        new RestAction<>(() -> first).and(new RestAction<>(() -> second)).submit().cancel(true);
        assertTrue(first.isCancelled() && second.isCancelled());
        var a = new CompletableFuture<String>();
        RestAction.allOf(new RestAction<>(() -> a), new RestAction<>(() -> third)).submit().cancel(true);
        assertTrue(a.isCancelled() && third.isCancelled());
    }
    @Test void accumulateUsesCollectorInOrder() throws Exception {
        var joined = RestAction.accumulate(List.of(completed("a"), completed("b"), completed("c")),
                Collectors.joining(",")).submit().get();
        assertEquals("a,b,c", joined);
    }

    // ------------------------------------------------------------ thread guards

    @Test void blockingOnGuardedThreadThrows() throws Exception {
        var errors = new AtomicReference<List<Throwable>>(new CopyOnWriteArrayList<>());
        Thread thread = ThreadGuards.guarded(Thread.ofPlatform().name("micup4j-gateway").factory()).newThread(() -> {
            for (Runnable call : List.<Runnable>of(
                    () -> completed("x").complete(),
                    () -> completed("x").complete(Duration.ofSeconds(1)),
                    () -> completed("x").completeAfter(1, TimeUnit.MILLISECONDS))) {
                try {
                    call.run();
                } catch (Throwable error) {
                    errors.get().add(error);
                }
            }
        });
        thread.start();
        thread.join(5000);
        assertEquals(3, errors.get().size());
        for (Throwable error : errors.get()) assertInstanceOf(IllegalStateException.class, error);
        assertEquals("x", completed("x").complete(), "unguarded threads may block");
    }
    @Test void cancelledDefaultFailureDoesNotThrow() {
        var source = new CompletableFuture<String>();
        new RestAction<>(() -> source).queue();
        source.cancel(true);
        assertTrue(source.isCancelled());
        assertThrows(CancellationException.class, source::join);
    }

    private static <T> RestAction<T> completed(final T value) {
        return new RestAction<>(() -> CompletableFuture.completedFuture(value));
    }

    private static Handler capture(final List<LogRecord> records) {
        return new Handler() {
            @Override public void publish(final LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
    }

    @Test void cancellingAllOfMidFlightCancelsPendingChildrenAndLeavesCompletedOnesAlone() {
        var done = CompletableFuture.completedFuture("a");
        var pendingOne = new CompletableFuture<String>();
        var pendingTwo = new CompletableFuture<String>();
        var result = RestAction.allOf(new RestAction<>(() -> done), new RestAction<>(() -> pendingOne),
                new RestAction<>(() -> pendingTwo)).submit();
        assertFalse(result.isDone());
        assertTrue(result.cancel(true));
        assertTrue(pendingOne.isCancelled() && pendingTwo.isCancelled());
        assertFalse(done.isCancelled());
        assertThrows(CancellationException.class, result::get);
    }
}
