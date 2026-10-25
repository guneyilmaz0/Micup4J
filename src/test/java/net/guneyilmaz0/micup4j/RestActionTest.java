package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.requests.RestAction;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RestActionTest {
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
}
