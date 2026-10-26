package com.swadedev.micup4j;

import com.swadedev.micup4j.internal.KeepAlive;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class KeepAliveTest {
    static Thread keepAliveThread() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().equals("micup4j-keepalive")).findFirst().orElse(null);
    }

    @Test void aNonDaemonThreadRunsUntilEveryHolderTerminates() {
        int before = KeepAlive.holders();
        var first = new CompletableFuture<Void>();
        var second = new CompletableFuture<Void>();
        KeepAlive.holdUntil(first);
        KeepAlive.holdUntil(second);
        assertEquals(before + 2, KeepAlive.holders());
        Thread thread = keepAliveThread();
        assertNotNull(thread, "a keep-alive thread must run while a holder is active");
        assertFalse(thread.isDaemon(), "the keep-alive thread must hold the JVM open");

        first.complete(null);
        second.completeExceptionally(new IllegalStateException("failed"));
        assertEquals(before, KeepAlive.holders(), "normal and exceptional termination both release");
    }

    @Test void anAlreadyTerminatedHolderReleasesImmediately() {
        int before = KeepAlive.holders();
        KeepAlive.holdUntil(CompletableFuture.completedFuture(null));
        assertEquals(before, KeepAlive.holders());
    }
}
