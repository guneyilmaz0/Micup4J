package com.swadedev.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.config.CachePolicy;
import com.swadedev.micup4j.config.GatewayConfig;
import com.swadedev.micup4j.entities.*;
import com.swadedev.micup4j.events.*;
import com.swadedev.micup4j.events.guild.*;
import com.swadedev.micup4j.hooks.ListenerAdapter;
import com.swadedev.micup4j.internal.Json;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CompatibilityContinuationTest extends GatewayFixture {
    Micup connect(MicupBuilder builder, ListenerAdapter listener) throws Exception {
        client = builder.setInstanceOrigin(server.url("/").uri()).setErrorHandler(errors::add).addEventListeners(listener).build();
        client.awaitReady(Duration.ofSeconds(5)); return client;
    }
    ListenerAdapter quiet() { return new ListenerAdapter() {}; }

    @Test void configuredPresenceIsSentInIdentifyAndReappliedAfterReady() throws Exception {
        connect(MicupBuilder.createDefault("test-token").setStatus(OnlineStatus.DO_NOT_DISTURB)
                .setActivity(Activity.playing("BMO")).setIdle(true), quiet());
        var identify = opcode(2).path("d").path("presence");
        assertEquals("dnd", identify.path("status").asText()); assertTrue(identify.path("afk").asBoolean());
        assertEquals("Playing BMO", identify.path("custom_status").path("text").asText());
        var update = opcode(3).path("d"); assertEquals(identify, update); assertFalse(update.has("activities"));
        assertEquals(OnlineStatus.DO_NOT_DISTURB, client.getPresence().getStatus()); assertTrue(client.getPresence().isIdle());
    }

    @Test void presenceChangesCoalesceWithoutBlockingHeartbeatProcessing() throws Exception {
        connect(MicupBuilder.createDefault("test-token").setActivity(Activity.customStatus("Initial")), quiet());
        opcode(2); opcode(3);
        for (int i = 0; i < 1000; i++) client.getPresence().setActivity(Activity.customStatus("Change " + i));
        client.getPresence().setPresence(OnlineStatus.IDLE, Activity.watching("Final"), true);
        assertEquals(1, opcode(1).path("d").asInt());
        var latest = opcode(3).path("d");
        assertEquals("Watching Final", latest.path("custom_status").path("text").asText());
        assertEquals("idle", latest.path("status").asText()); assertTrue(latest.path("afk").asBoolean());
        assertEquals(Micup.Status.CONNECTED, client.getStatus()); assertTrue(errors.isEmpty());
    }

    @Test void presenceWaitsForAuthenticationAndUsesTheLatestInitialValue() throws Exception {
        hello = false;
        client = MicupBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri()).setErrorHandler(errors::add).build();
        var socket = sockets.poll(3, TimeUnit.SECONDS); assertNotNull(socket);
        client.getPresence().setPresence(OnlineStatus.OFFLINE, Activity.customStatus("Before Hello"));
        assertNull(frames.poll(50, TimeUnit.MILLISECONDS));
        socket.send("{\"op\":10,\"d\":{\"heartbeat_interval\":1000}}");
        assertEquals("invisible", opcode(2).path("d").path("presence").path("status").asText());
        assertEquals("Before Hello", opcode(3).path("d").path("custom_status").path("text").asText());
        client.awaitReady(Duration.ofSeconds(3));
    }

    @Test void reconnectResumesWithTheLatestPresenceAndClosedClientsRejectUpdates() throws Exception {
        connect(MicupBuilder.createDefault("test-token").setActivity(Activity.customStatus("Initial"))
                .setGatewayConfig(new GatewayConfig(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofMillis(20), Duration.ofMillis(60))), quiet());
        opcode(2); opcode(3);
        hello = false;
        sockets.poll(2, TimeUnit.SECONDS).send("{\"op\":7}");
        var fresh = sockets.poll(3, TimeUnit.SECONDS); assertNotNull(fresh);
        client.getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.listening("New"), false);
        fresh.send("{\"op\":10,\"d\":{\"heartbeat_interval\":1000}}");
        opcode(6);
        assertEquals("Listening to New", opcode(3).path("d").path("custom_status").path("text").asText());
        assertThrows(IllegalArgumentException.class, () -> client.getPresence().setStatus(OnlineStatus.UNKNOWN));
        client.close();
        assertThrows(IllegalStateException.class, () -> client.getPresence().setActivity(null));
    }

    @Test void statusOnlyChangesPreserveSavedCustomStatusAndActivityValidationIsBounded() throws Exception {
        start(quiet()); assertFalse(opcode(2).path("d").has("presence"));
        client.getPresence().setStatus(null);
        var body = opcode(3).path("d");
        assertEquals("invisible", body.path("status").asText()); assertFalse(body.has("custom_status"));
        assertNull(client.getPresence().getActivity());
        assertThrows(IllegalArgumentException.class, () -> Activity.customStatus("x".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> Activity.playing("x".repeat(121)));
        assertEquals(128, Activity.customStatus("😀".repeat(128)).getName().codePointCount(0, 256));
    }

    @Test void guildLifecycleDistinguishesReadyJoinRecoveryAndFreshSessions() throws Exception {
        readyGuilds = "[{\"id\":\"1\",\"unavailable\":true}]";
        var ready = new CountDownLatch(1); var trace = new ArrayList<String>(); var rawCreates = new java.util.concurrent.atomic.AtomicInteger();
        start(new ListenerAdapter() {
            @Override public void onReady(ReadyEvent event) { ready.countDown(); }
            @Override public void onGenericEvent(GenericEvent event) { if (event.getType().equals("GUILD_CREATE")) rawCreates.incrementAndGet(); }
            @Override public void onGuildReady(GuildReadyEvent event) {
                assertEquals("Guild", event.getGuild().getName()); assertNotNull(client.getGuildById("1")); trace.add("ready");
            }
            @Override public void onGuildJoin(GuildJoinEvent event) { trace.add("join"); }
            @Override public void onGuildAvailable(GuildAvailableEvent event) { trace.add("available"); }
            @Override public void onGuildUnavailable(GuildUnavailableEvent event) { assertFalse(event.getGuild().available()); trace.add("unavailable"); }
            @Override public void onGuildLeave(GuildLeaveEvent event) { assertEquals("Guild", event.getGuild().getName()); assertNull(client.getGuildById("1")); trace.add("leave"); }
        });
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_SYNC", 3, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        client.dispatch("RESUMED", 3, Json.object()).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_CREATE", 4, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_DELETE", 5, Json.parse("{\"id\":\"1\",\"unavailable\":true}")).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_CREATE", 6, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_DELETE", 7, Json.parse("{\"id\":\"1\"}")).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_CREATE", 8, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        client.dispatch("READY", 1, Json.parse("{\"guilds\":[{\"id\":\"1\",\"unavailable\":true}]}")).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        assertEquals(List.of("ready", "unavailable", "available", "leave", "join", "ready"), trace);
        assertEquals(5, rawCreates.get()); assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void lifecycleIdentitySurvivesEntityEvictionAndLightClientsStillReceiveGuildEvents() throws Exception {
        readyGuilds = "[{\"id\":\"1\",\"unavailable\":true}]";
        var ready = new CountDownLatch(1); var trace = new ArrayList<String>();
        connect(MicupBuilder.createDefault("test-token").setCachePolicy(new CachePolicy(1, 10, 10, 10)), new ListenerAdapter() {
            @Override public void onReady(ReadyEvent event) { ready.countDown(); }
            @Override public void onGuildReady(GuildReadyEvent event) { trace.add("ready"); }
            @Override public void onGuildJoin(GuildJoinEvent event) { trace.add("join:" + event.getGuildId()); }
        });
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_CREATE", 3, Json.parse("{\"id\":\"7\",\"name\":\"Other\",\"owner_id\":\"9\",\"roles\":[],\"channels\":[],\"members\":[]}")).get(3, TimeUnit.SECONDS);
        assertNull(client.getGuildById("1"));
        client.dispatch("GUILD_CREATE", 4, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        assertEquals(List.of("ready", "join:7"), trace);
        client.close(); client = null;
        var lightReady = new CountDownLatch(1); var guildEvent = new CompletableFuture<GuildReadyEvent>();
        connect(MicupBuilder.createLight("test-token"), new ListenerAdapter() {
            @Override public void onReady(ReadyEvent event) { lightReady.countDown(); }
            @Override public void onGuildReady(GuildReadyEvent event) { guildEvent.complete(event); }
        });
        assertTrue(lightReady.await(2, TimeUnit.SECONDS));
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        assertEquals("Guild", guildEvent.get(3, TimeUnit.SECONDS).getGuild().getName());
        assertTrue(client.getGuilds().isEmpty());
    }
    @Test void explicitNullActivityClearsCustomStatus() throws Exception {
        start(quiet()); opcode(2);
        client.getPresence().setActivity(null);
        assertTrue(opcode(3).path("d").get("custom_status").isNull());
    }

    @Test void initiallyUnavailableGuildDoesNotEmitASpuriousUnavailableTransition() throws Exception {
        readyGuilds = "[{\"id\":\"1\",\"unavailable\":true}]";
        var ready = new CountDownLatch(1); var trace = new ArrayList<String>();
        start(new ListenerAdapter() {
            @Override public void onReady(ReadyEvent event) { ready.countDown(); }
            @Override public void onGuildUnavailable(GuildUnavailableEvent event) { trace.add("unavailable"); }
            @Override public void onGuildReady(GuildReadyEvent event) { trace.add("ready"); }
            @Override public void onGuildAvailable(GuildAvailableEvent event) { trace.add("available"); }
        });
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        client.dispatch("GUILD_DELETE", 2, Json.parse("{\"id\":\"1\",\"unavailable\":true}")).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_CREATE", 3, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        assertEquals(List.of("available"), trace);
    }


}
