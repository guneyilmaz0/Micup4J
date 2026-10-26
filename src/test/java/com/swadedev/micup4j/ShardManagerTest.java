package com.swadedev.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.events.session.ReadyEvent;
import com.swadedev.micup4j.hooks.ListenerAdapter;
import com.swadedev.micup4j.internal.RestClient;
import com.swadedev.micup4j.sharding.DefaultShardManagerBuilder;
import com.swadedev.micup4j.sharding.SessionStartGate;
import com.swadedev.micup4j.sharding.ShardManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ShardManagerTest extends GatewayFixture {
    private ShardManager manager;

    @AfterEach void stopManager() { if (manager != null) manager.shutdownNow(); }

    DefaultShardManagerBuilder builder(int total) {
        return DefaultShardManagerBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri())
                .setShardsTotal(total).setErrorHandler(errors::add).setSessionStartGate(SessionStartGate.sequential(Duration.ZERO));
    }

    List<JsonNode> identifies(int count) throws Exception {
        var result = new ArrayList<JsonNode>();
        while (result.size() < count) result.add(opcode(2).path("d"));
        return result;
    }

    @Test void shardsStartOneAtATimeThroughTheGateAndShareOneRestClient() throws Exception {
        var turns = new CopyOnWriteArrayList<String>();
        var readyShards = Collections.synchronizedList(new ArrayList<Integer>());
        manager = builder(3).setSessionStartGate(new SessionStartGate() {
            @Override public void awaitTurn(int shardId) { turns.add("turn " + shardId); }
            @Override public void started(int shardId, boolean ready) { turns.add((ready ? "ready " : "late ") + shardId); }
        }).addEventListenerProvider(id -> new ListenerAdapter() {
            @Override public void onReady(ReadyEvent event) { readyShards.add(id * 10 + event.getJDA().getShardInfo().getShardId()); }
        }).build();

        assertTrue(manager.awaitReady(Duration.ofSeconds(10)));
        var frames = identifies(3);
        assertEquals(List.of(0, 1, 2), frames.stream().map(frame -> frame.path("shard").get(0).asInt()).toList());
        assertTrue(frames.stream().allMatch(frame -> frame.path("shard").get(1).asInt() == 3));
        assertEquals(List.of("turn 0", "ready 0", "turn 1", "ready 1", "turn 2", "ready 2"), turns);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (readyShards.size() < 3 && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(10);
        assertEquals(List.of(0, 11, 22), readyShards.stream().sorted().toList(), "Each shard gets its own provided listener");

        assertEquals(3, manager.getShardsRunning());
        assertEquals(0, manager.getShardsQueued());
        RestClient rest = manager.getRestClient();
        for (Micup shard : manager.getShards()) {
            assertSame(rest, shard.getRestClient());
            assertTrue(shard.isRestClientShared());
        }
        assertEquals("[1 / 3]", manager.getShardById(1).getShardInfo().getShardString());
        // (guild_id >> 22) % 3 for snowflakes built from known timestamp bits.
        assertEquals(0, manager.getShardIdForGuild(Long.toString(3L << 22)));
        assertEquals(2, manager.getShardIdForGuild(Long.toString((5L << 22) | 12345)));
        assertEquals(0, manager.getShardIdForGuild("18446744073709551615"), "Unsigned snowflakes keep their high bit");
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void aGlobalRateLimitOnOneShardDelaysEveryShard() throws Exception {
        manager = builder(2).build();
        assertTrue(manager.awaitReady(Duration.ofSeconds(10)));
        var starts = new CopyOnWriteArrayList<Long>();
        var limited = new java.util.concurrent.atomic.AtomicBoolean();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                starts.add(System.nanoTime());
                if (limited.compareAndSet(false, true))
                    return json("{\"code\":\"RATE_LIMITED\",\"retry_after\":0.4,\"global\":true}").setResponseCode(429).setHeader("X-RateLimit-Global", "true");
                return json("{\"id\":\"7\",\"username\":\"ada\"}");
            }
        });
        var first = manager.getShardById(0).retrieveUserById("7").submit();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!limited.get() && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(5);
        long limitedAt = System.nanoTime();
        manager.getShardById(1).retrieveUserById("8").submit().get(5, TimeUnit.SECONDS);
        first.get(5, TimeUnit.SECONDS);
        long secondShardStart = starts.stream().filter(start -> start > limitedAt).min(Long::compare).orElseThrow();
        assertTrue(secondShardStart - limitedAt >= TimeUnit.MILLISECONDS.toNanos(300),
                "Shard 1 must wait for shard 0's global cooldown on the shared scheduler");
    }

    @Test void restartReconnectsOneShardAndShutdownDrainsTheSharedClient() throws Exception {
        manager = builder(2).build();
        assertTrue(manager.awaitReady(Duration.ofSeconds(10)));
        identifies(2);
        Micup before = manager.getShardById(1);
        int connectionsBefore = connections.get();
        manager.restart(1);
        assertEquals(1, opcode(2).path("d").path("shard").get(0).asInt());
        assertTrue(manager.awaitReady(Duration.ofSeconds(10)));
        assertNotSame(before, manager.getShardById(1));
        assertEquals(Micup.Status.SHUTDOWN, before.getStatus());
        assertEquals(connectionsBefore + 1, connections.get());
        assertSame(manager.getRestClient(), manager.getShardById(1).getRestClient());
        assertThrows(IllegalArgumentException.class, () -> manager.restart(7));

        var shards = manager.getShards();
        manager.shutdown();
        assertTrue(manager.awaitShutdown(Duration.ofSeconds(10)));
        for (Micup shard : shards) assertEquals(Micup.Status.SHUTDOWN, shard.getStatus());
        assertTrue(manager.getRestClient().isShutdown());
        var rejected = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> shards.getFirst().retrieveUserById("7").submit().get(3, TimeUnit.SECONDS));
        assertTrue(rejected.getCause() instanceof RejectedExecutionException || rejected.getCause() instanceof IllegalStateException);
        assertThrows(IllegalStateException.class, () -> manager.restart(0));
    }

    @Test void theManagerKeepsTheJvmAliveFromBuildUntilShutdown() throws Exception {
        int before = com.swadedev.micup4j.internal.KeepAlive.holders();
        manager = builder(1).build();
        assertTrue(com.swadedev.micup4j.internal.KeepAlive.holders() > before, "build() must hold the JVM before any shard starts");
        assertFalse(KeepAliveTest.keepAliveThread().isDaemon());
        manager.shutdownNow();
        assertTrue(manager.awaitShutdown(Duration.ofSeconds(5)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (com.swadedev.micup4j.internal.KeepAlive.holders() > before && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(before, com.swadedev.micup4j.internal.KeepAlive.holders(), "the manager and its shards must release after shutdown");
    }

    @Test void buildersValidateShardRangesAndSharedClients() {
        assertThrows(IllegalArgumentException.class, () -> builder(2).setShards(0, 2).build());
        assertThrows(IllegalArgumentException.class, () -> builder(0));
        assertThrows(IllegalArgumentException.class, () -> DefaultShardManagerBuilder.createDefault("Bot abc"));
        var shared = RestClient.shared("other-token", 16, com.swadedev.micup4j.config.RestConfig.defaults());
        try {
            assertThrows(IllegalStateException.class, () -> MicupBuilder.createLight("test-token").setRestClient(shared).build());
            var closed = RestClient.shared("test-token", 16, com.swadedev.micup4j.config.RestConfig.defaults());
            closed.close();
            assertThrows(IllegalStateException.class, () -> MicupBuilder.createLight("test-token").setRestClient(closed).build());
        } finally { shared.close(); }
    }

    @Test void onlySelectedShardsRunInThisProcess() throws Exception {
        manager = builder(4).setShards(2, 3).build();
        assertTrue(manager.awaitReady(Duration.ofSeconds(10)));
        assertEquals(List.of(2, 3), identifies(2).stream().map(frame -> frame.path("shard").get(0).asInt()).toList());
        assertNull(manager.getShardById(0));
        assertEquals(java.util.Set.of(2, 3), manager.getStatuses().keySet());
        manager.shutdown(3);
        assertNull(manager.getShardById(3));
    }
}
