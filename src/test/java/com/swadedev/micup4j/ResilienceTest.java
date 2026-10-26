package com.swadedev.micup4j;

import com.swadedev.micup4j.config.GatewayConfig;
import com.swadedev.micup4j.events.GenericEvent;
import com.swadedev.micup4j.events.guild.GuildJoinEvent;
import com.swadedev.micup4j.hooks.ListenerAdapter;
import com.swadedev.micup4j.sharding.DefaultShardManagerBuilder;
import com.swadedev.micup4j.sharding.SessionStartGate;
import com.swadedev.micup4j.sharding.ShardManager;
import okhttp3.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResilienceTest extends GatewayFixture {
    private ShardManager manager;

    @AfterEach void stopManager() { if (manager != null) manager.shutdownNow(); }

    static String guild(int id) {
        return "{\"id\":\"" + id + "\",\"properties\":{\"name\":\"Guild " + id + "\",\"owner_id\":\"9\"},\"roles\":[{\"id\":\"" + id
                + "\",\"position\":0,\"name\":\"@everyone\",\"permissions\":\"1024\"}],\"channels\":[{\"id\":\"" + (id + 1_000_000)
                + "\",\"type\":0,\"name\":\"chat\",\"permission_overwrites\":[]}],\"members\":[{\"user\":{\"id\":\"4\",\"username\":\"u\"},\"roles\":[]}]}";
    }

    @Test void largeStartupBurstStaysWithinCacheBoundsAndDeliversEveryEvent() throws Exception {
        int guilds = 600;
        var joined = new CountDownLatch(guilds);
        start(new ListenerAdapter() { @Override public void onGuildJoin(GuildJoinEvent event) { joined.countDown(); } });
        WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
        for (int i = 0; i < guilds; i++) ws.send("{\"op\":0,\"t\":\"GUILD_CREATE\",\"s\":" + (i + 2) + ",\"d\":" + guild(1000 + i) + "}");
        assertTrue(joined.await(20, TimeUnit.SECONDS), "Every guild snapshot reaches listeners");
        assertEquals(128, client.getGuilds().size(), "The default policy keeps at most 128 guilds");
        assertNotNull(client.getGuildById(Integer.toString(1000 + guilds - 1)), "The newest guilds are retained");
        assertNull(client.getGuildById("1000"), "The oldest guilds are evicted");
        assertEquals(Micup.Status.CONNECTED, client.getStatus());
        assertTrue(errors.isEmpty(), errors.toString());
    }

    DefaultShardManagerBuilder shards(int total) {
        return DefaultShardManagerBuilder.createLight("test-token").setInstanceOrigin(server.url("/").uri()).setShardsTotal(total)
                .setErrorHandler(errors::add).setSessionStartGate(SessionStartGate.sequential(Duration.ZERO))
                .setGatewayConfig(new GatewayConfig(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofMillis(50), Duration.ofMillis(200)));
    }

    List<WebSocket> sockets(int count) throws Exception {
        var result = new ArrayList<WebSocket>();
        for (int i = 0; i < count; i++) result.add(sockets.poll(5, TimeUnit.SECONDS));
        assertFalse(result.contains(null));
        return result;
    }

    @Test void aSlowListenerOnOneShardDoesNotDelayAnotherShard() throws Exception {
        var release = new CountDownLatch(1);
        var fastDelivered = new CountDownLatch(1);
        var slowStarted = new CountDownLatch(1);
        manager = shards(2).addEventListeners(new ListenerAdapter() {
            @Override public void onGenericEvent(GenericEvent event) {
                if (!event.getType().equals("PROBE")) return;
                if (event.getMicup().getShardInfo().getShardId() == 0) {
                    slowStarted.countDown();
                    try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                } else fastDelivered.countDown();
            }
        }).build();
        try {
            assertTrue(manager.awaitReady(Duration.ofSeconds(10)));
            var connected = sockets(2);
            connected.get(0).send("{\"op\":0,\"t\":\"PROBE\",\"s\":2,\"d\":{}}");
            assertTrue(slowStarted.await(3, TimeUnit.SECONDS));
            long sent = System.nanoTime();
            connected.get(1).send("{\"op\":0,\"t\":\"PROBE\",\"s\":2,\"d\":{}}");
            assertTrue(fastDelivered.await(2, TimeUnit.SECONDS), "Shard 1 has its own event thread");
            assertTrue(System.nanoTime() - sent < TimeUnit.MILLISECONDS.toNanos(1_000));
        } finally { release.countDown(); }
    }

    @Test void repeatedNetworkInterruptionsResumeEveryShard() throws Exception {
        var probes = new AtomicInteger();
        manager = shards(2).addEventListeners(new ListenerAdapter() {
            @Override public void onGenericEvent(GenericEvent event) { if (event.getType().equals("PROBE")) probes.incrementAndGet(); }
        }).build();
        assertTrue(manager.awaitReady(Duration.ofSeconds(10)));
        var active = sockets(2);
        for (int round = 0; round < 3; round++) {
            for (WebSocket ws : active) ws.send("{\"op\":7}");
            active = sockets(2);
            int resumes = 0;
            while (resumes < 2) if (opcode(6).path("d").path("session_id").asText().equals("session-123")) resumes++;
            for (WebSocket ws : active) ws.send("{\"op\":0,\"t\":\"PROBE\",\"s\":" + (10 + round) + ",\"d\":{}}");
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((manager.getShardsRunning() < 2 || probes.get() < 6) && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(10);
        assertEquals(2, manager.getShardsRunning());
        assertEquals(6, probes.get(), "Events after each resume reach listeners on both shards");
        for (Micup shard : manager.getShards()) assertEquals(3, shard.getMetrics().reconnects());
        assertTrue(errors.isEmpty(), errors.toString());
    }
}
