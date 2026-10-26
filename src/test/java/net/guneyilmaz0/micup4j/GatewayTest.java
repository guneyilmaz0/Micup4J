package net.guneyilmaz0.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.events.*;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.interactions.SlashCommand;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.time.Duration;
import net.guneyilmaz0.micup4j.config.GatewayConfig;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class GatewayTest extends GatewayFixture {
    @Test void discoveryIdentifyHeartbeatAndPingReply() throws Exception {
        CountDownLatch replied = new CountDownLatch(1);
        start(new ListenerAdapter() {
            @Override public void onMessageReceived(MessageReceivedEvent event) {
                if (!event.getAuthor().isBot()) event.getMessage().reply("Pong!").queue(value -> replied.countDown(), errors::add);
            }
        });
        JsonNode identify = opcode(2).path("d");
        assertEquals("test-token", identify.path("token").asText());
        assertEquals("Micup4J", identify.path("properties").path("browser").asText());
        assertFalse(identify.has("intents"));
        assertEquals(2, identify.path("shard").get(1).asInt());
        assertEquals("TYPING_START", identify.path("ignored_events").get(0).asText());
        assertEquals("42", client.getApplicationId());
        WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
        ws.send("{\"op\":0,\"t\":\"MESSAGE_CREATE\",\"s\":2,\"d\":{\"id\":\"3\",\"channel_id\":\"2\",\"content\":\"!ping\",\"author\":{\"id\":\"5\",\"username\":\"human\",\"bot\":false}}}");
        assertTrue(replied.await(5, TimeUnit.SECONDS));
        RecordedRequest request = route("/messages");
        assertEquals("/public/v1/channels/2/messages", request.getPath());
        assertEquals("3", Json.parse(request.getBody().readUtf8()).path("message_reference").path("message_id").asText());
        assertEquals(2, opcode(1).path("d").asLong());
        assertTrue(errors.isEmpty(), errors.toString());
    }
    @Test void resumesAfterReconnectAndKeepsRawToken() throws Exception {
        start(new ListenerAdapter() {});
        opcode(2);
        sockets.poll(2, TimeUnit.SECONDS).send("{\"op\":7}");
        JsonNode resume = opcode(6).path("d");
        assertEquals("session-123", resume.path("session_id").asText());
        assertEquals("test-token", resume.path("token").asText());
        assertEquals(1, resume.path("seq").asLong());
        assertEquals(2, connections.get());
    }
    @Test void invalidSessionReidentifiesOnSameSocket() throws Exception {
        start(new ListenerAdapter() {}); opcode(2);
        sockets.poll(2, TimeUnit.SECONDS).send("{\"op\":9,\"d\":false}");
        assertEquals("test-token", opcode(2).path("d").path("token").asText());
        assertEquals(1, connections.get());
    }
    @Test void badAuthenticationFailsWithoutReconnectLoop() throws Exception {
        start(new ListenerAdapter() {});
        sockets.poll(2, TimeUnit.SECONDS).close(4004, "Invalid token");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while ((client.getStatus() != Micup.Status.FAILED || errors.isEmpty()) && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(Micup.Status.FAILED, client.getStatus());
        assertEquals(1, connections.get()); assertEquals(1, errors.size());
    }
    @Test void unknownOpcodeIsIgnoredAndListenerFailureIsIsolated() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        start(new ListenerAdapter() {
            @Override public void onReady(ReadyEvent event) { throw new IllegalStateException("secret-token"); }
            @Override public void onGenericEvent(GenericEvent event) { if (event.getType().equals("FUTURE_EVENT")) delivered.countDown(); }
        });
        WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
        ws.send("{\"op\":99}"); ws.send("{\"op\":0,\"s\":2,\"t\":\"FUTURE_EVENT\",\"d\":{}}");
        assertTrue(delivered.await(3, TimeUnit.SECONDS));
        assertEquals(Micup.Status.CONNECTED, client.getStatus());
        assertEquals(1, errors.size()); assertFalse(errors.getFirst().toString().contains("secret-token"));
    }
    @Test void interactionCallbackIsSingleUseAndCommandsUseApplicationId() throws Exception {
        var interaction = new CompletableFuture<InteractionCreateEvent>();
        start(new ListenerAdapter() { @Override public void onInteractionCreate(InteractionCreateEvent event) { interaction.complete(event); } });
        sockets.poll(2, TimeUnit.SECONDS).send("{\"op\":0,\"t\":\"INTERACTION_CREATE\",\"s\":2,\"d\":{\"id\":\"123\",\"application_id\":\"42\",\"token\":\"interaction-secret\",\"type\":2,\"data\":{\"name\":\"ping\"}}}");
        var event = interaction.get(3, TimeUnit.SECONDS);
        assertEquals("ping", event.getName()); assertFalse(event.toString().contains("interaction-secret"));
        event.reply("Pong!", true).submit().get(3, TimeUnit.SECONDS);
        var callback = route("/callback");
        assertNull(callback.getHeader("Authorization"));
        JsonNode payload = Json.parse(callback.getBody().readUtf8());
        assertEquals(4, payload.path("type").asInt()); assertEquals(64, payload.path("data").path("flags").asInt());
        assertThrows(CompletionException.class, () -> event.reply("again").complete());
        client.updateGuildCommands("8", new SlashCommand("ping", "Gecikme")).submit().get(3, TimeUnit.SECONDS);
        assertEquals("/public/v1/applications/42/guilds/8/commands", route("/commands").getPath());
    }
    @Test void heartbeatAcknowledgesOnlyCompletedListeners() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), readyDelivered = new CountDownLatch(1);
        try {
            start(new ListenerAdapter() {
                @Override public void onReady(ReadyEvent event) { readyDelivered.countDown(); }
                @Override public void onMessageReceived(MessageReceivedEvent event) {
                    entered.countDown();
                    try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            });
            assertTrue(readyDelivered.await(2, TimeUnit.SECONDS));
            // First heartbeat ensures Ready's completion has reached the protocol scheduler.
            assertEquals(1, opcode(1).path("d").asInt());
            WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
            ws.send("{\"op\":0,\"t\":\"MESSAGE_CREATE\",\"s\":2,\"d\":{\"id\":\"3\",\"channel_id\":\"2\",\"author\":{\"id\":\"5\"}}}");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            ws.send("{\"op\":1,\"d\":null}");
            assertEquals(1, opcode(1).path("d").asInt());
            release.countDown();
            assertEquals(2, opcode(1).path("d").asInt());
        } finally { release.countDown(); }
    }
    @Test void messageBurstKeepsOrderWithoutLoss() throws Exception {
        int count = 1000;
        CountDownLatch delivered = new CountDownLatch(count);
        List<Long> sequences = new CopyOnWriteArrayList<>();
        start(new ListenerAdapter() {
            @Override public void onGenericEvent(GenericEvent event) {
                if (event.getType().equals("BURST")) { sequences.add(event.getSequence()); delivered.countDown(); }
            }
        });
        WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
        long start = System.nanoTime();
        for (int i = 2; i < count + 2; i++) ws.send("{\"op\":0,\"t\":\"BURST\",\"s\":" + i + ",\"d\":{}}");
        assertTrue(delivered.await(10, TimeUnit.SECONDS));
        for (int i = 0; i < count; i++) assertEquals(i + 2L, sequences.get(i));
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        System.out.printf(java.util.Locale.ROOT, "Local smoke burst: %,d ordered events in %.3f s (%.0f events/s)%n", count, seconds, count / seconds);
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void eventQueueOverflowStopsInsteadOfAcknowledgingLostEvents() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            client = MicupBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri())
                .setQueueCapacity(4, 1).setErrorHandler(errors::add).addEventListeners(new ListenerAdapter() {
                    @Override public void onReady(ReadyEvent event) {
                        blocked.countDown();
                        try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }).build();
            client.awaitReady(Duration.ofSeconds(5)); assertTrue(blocked.await(2, TimeUnit.SECONDS));
            WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
            for (int i = 2; i <= 4; i++) ws.send("{\"op\":0,\"t\":\"BURST\",\"s\":" + i + ",\"d\":{}}");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while ((client.getStatus() != Micup.Status.FAILED || errors.isEmpty()) && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(Micup.Status.FAILED, client.getStatus());
            assertEquals(1, errors.size());
        } finally { release.countDown(); }
    }

    @Test void terminationCompletesOnNormalShutdownAndFailure() throws Exception {
        start(new ListenerAdapter() {});
        var terminated = client.termination();
        assertFalse(terminated.isDone());
        sockets.poll(2, TimeUnit.SECONDS).close(4004, "Invalid token");
        assertThrows(ExecutionException.class, () -> terminated.get(3, TimeUnit.SECONDS));
        assertEquals(Micup.Status.FAILED, client.getStatus());
    }

    @Test void cancellingTerminationViewDoesNotStopClient() throws Exception {
        start(new ListenerAdapter() {});
        client.termination().cancel(true);
        assertEquals(Micup.Status.CONNECTED, client.getStatus());
        var terminated = client.termination();
        assertFalse(terminated.isDone());
        client.close();
        assertNull(terminated.get(3, TimeUnit.SECONDS));
        assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
    }

    @Test void closingDuringHandshakeAbortsLateSocket() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().equals("/.well-known/fluxer")) {
                    return json("{\"endpoints\":{\"api_public\":\"" + server.url("/api") + "\",\"gateway\":\"" + server.url("/gateway").toString().replace("http:", "ws:") + "\"}}");
                }
                return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                    @Override public void onFailure(WebSocket ws, Throwable failure, Response response) { disconnected.countDown(); }
                    @Override public void onClosed(WebSocket ws, int code, String reason) { disconnected.countDown(); }
                }).setHeadersDelay(250, TimeUnit.MILLISECONDS);
            }
        });
        client = MicupBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri()).setErrorHandler(errors::add).build();
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)); // Discovery.
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)); // Upgrade is pending.
        client.close();
        assertNull(client.termination().get(2, TimeUnit.SECONDS));
        assertTrue(disconnected.await(5, TimeUnit.SECONDS), "The late Gateway socket must be aborted");
        assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
        assertTrue(client.ready().isCompletedExceptionally());
        assertTrue(errors.isEmpty());
    }

    GatewayConfig quickGateway() {
        return new GatewayConfig(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofMillis(20), Duration.ofMillis(60));
    }
    @Test void missedHeartbeatAckReconnectsAndPublishesSanitizedDiagnostics() throws Exception {
        heartbeatInterval = 80; acknowledge = false; gatewayConfig = quickGateway();
        var reconnect = new CompletableFuture<ReconnectEvent>();
        var statuses = new CopyOnWriteArrayList<Micup.Status>();
        start(new ListenerAdapter() {
            @Override public void onStatusChange(StatusChangeEvent event) { statuses.add(event.getNewStatus()); }
            @Override public void onReconnect(ReconnectEvent event) { reconnect.complete(event); }
        });
        var event = reconnect.get(3, TimeUnit.SECONDS);
        assertEquals(ReconnectEvent.Reason.HEARTBEAT_TIMEOUT, event.getReason());
        assertEquals(-1, event.getSequence());
        assertEquals(1, event.getAttempt());
        assertTrue(statuses.contains(Micup.Status.CONNECTED));
        assertTrue(statuses.contains(Micup.Status.RECONNECTING));
        assertEquals(1, client.getMetrics().reconnects());
        assertFalse(event.getData().toString().contains("test-token"));
        assertFalse(event.getData().toString().contains("session-123"));
        opcode(6); // The missed ACK must actually open a fresh socket and Resume.
        assertTrue(connections.get() >= 2);
    }
    @Test void handshakeTimeoutRetriesAndCloseCancelsPendingReady() throws Exception {
        hello = false;
        gatewayConfig = new GatewayConfig(Duration.ofSeconds(1), Duration.ofMillis(120), Duration.ofMillis(20), Duration.ofMillis(60));
        var reconnect = new CompletableFuture<ReconnectEvent>();
        client = MicupBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri()).setGatewayConfig(gatewayConfig)
                .setErrorHandler(errors::add).addEventListeners(new ListenerAdapter() {
                    @Override public void onReconnect(ReconnectEvent event) { reconnect.complete(event); }
                }).build();
        assertEquals(ReconnectEvent.Reason.HANDSHAKE_TIMEOUT, reconnect.get(3, TimeUnit.SECONDS).getReason());
        assertFalse(client.ready().isDone());
        client.close();
        assertThrows(ExecutionException.class, () -> client.ready().get(1, TimeUnit.SECONDS));
        assertTrue(client.termination().isDone());
        assertTrue(errors.isEmpty());
    }
    @Test void repeatedReconnectsKeepSessionAndRemainCancellable() throws Exception {
        gatewayConfig = quickGateway(); start(new ListenerAdapter() {}); opcode(2);
        WebSocket active = sockets.poll(2, TimeUnit.SECONDS);
        for (int i = 0; i < 3; i++) {
            active.send("{\"op\":7}");
            active = sockets.poll(3, TimeUnit.SECONDS);
            assertNotNull(active);
            assertEquals("session-123", opcode(6).path("d").path("session_id").asText());
        }
        assertEquals(3, client.getMetrics().reconnects());
        client.close();
        assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
    }
    @Test void invalidSequenceStartsFreshIdentifyAndDoesNotPublishServerReason() throws Exception {
        gatewayConfig = quickGateway();
        var reconnect = new CompletableFuture<ReconnectEvent>();
        start(new ListenerAdapter() { @Override public void onReconnect(ReconnectEvent event) { reconnect.complete(event); } });
        opcode(2);
        sockets.poll(2, TimeUnit.SECONDS).close(4007, "private-server-reason");
        var event = reconnect.get(3, TimeUnit.SECONDS);
        assertEquals(4007, event.getCloseCode());
        assertEquals(ReconnectEvent.Reason.CLOSE_CODE, event.getReason());
        assertFalse(event.getData().toString().contains("private-server-reason"));
        assertEquals("test-token", opcode(2).path("d").path("token").asText());
    }
    @Test void interactionDeadlineIncludesTimeWaitingForListener() throws Exception {
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failedReply = new CompletableFuture<Throwable>();
        try {
            start(new ListenerAdapter() {
                @Override public void onMessageReceived(MessageReceivedEvent event) {
                    blocked.countDown();
                    try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                }
                @Override public void onInteractionCreate(InteractionCreateEvent event) {
                    event.reply("Too late").submit().whenComplete((value, error) -> failedReply.complete(error));
                }
            });
            var ws = sockets.poll(2, TimeUnit.SECONDS);
            ws.send("{\"op\":0,\"s\":2,\"t\":\"MESSAGE_CREATE\",\"d\":{\"id\":\"2\",\"channel_id\":\"3\",\"author\":{\"id\":\"4\"}}}");
            assertTrue(blocked.await(2, TimeUnit.SECONDS));
            ws.send("{\"op\":0,\"s\":3,\"t\":\"INTERACTION_CREATE\",\"d\":{\"id\":\"5\",\"application_id\":\"42\",\"type\":2,\"token\":\"private-interaction\",\"data\":{\"name\":\"ping\"}}}");
            long wait = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (client.getMetrics().queuedEvents() == 0 && System.nanoTime() < wait) Thread.sleep(5);
            assertEquals(1, client.getMetrics().queuedEvents());
            Thread.sleep(2600); // Explicitly exercise the platform's short response deadline.
            release.countDown();
            assertInstanceOf(TimeoutException.class, failedReply.get(2, TimeUnit.SECONDS));
            assertEquals(2, server.getRequestCount()); // Discovery + WebSocket; no late callback.
        } finally { release.countDown(); }
    }

    @Test void embedRepliesAndSlashOptionsUseDocumentedPayloads() throws Exception {
        var received = new CompletableFuture<InteractionCreateEvent>();
        start(new ListenerAdapter() { @Override public void onInteractionCreate(InteractionCreateEvent event) { received.complete(event); } });
        var embed = new net.guneyilmaz0.micup4j.entities.EmbedBuilder().setTitle("Avatar", "https://example.com/avatar.webp")
                .setAuthor("Target", null, null).setImage("https://example.com/avatar.webp").setFooter("Requested by user", null).build();
        client.getMessageChannelById("2").sendMessageEmbeds(embed).submit().get(3, TimeUnit.SECONDS);
        var sent = route("/messages");
        assertEquals("Avatar", Json.parse(sent.getBody().readUtf8()).path("embeds").get(0).path("title").asText());
        sockets.poll(2, TimeUnit.SECONDS).send("{\"op\":0,\"s\":2,\"t\":\"INTERACTION_CREATE\",\"d\":{\"id\":\"123\",\"application_id\":\"42\",\"token\":\"interaction-secret\",\"type\":2,\"data\":{\"name\":\"avatar\",\"options\":[{\"type\":6,\"name\":\"user\",\"value\":\"9\"}]}}}");
        var event = received.get(3, TimeUnit.SECONDS);
        assertEquals("9", event.getOptionValue("user").orElseThrow().asText());
        assertTrue(event.getOptionValue("absent").isEmpty());
        event.replyEmbeds(true, embed).submit().get(3, TimeUnit.SECONDS);
        var callback = Json.parse(route("/callback").getBody().readUtf8());
        assertEquals(64, callback.path("data").path("flags").asInt());
        assertEquals("Avatar", callback.path("data").path("embeds").get(0).path("title").asText());
    }

    @Test void entityCacheIsUpdatedBeforeListenersAndClearedOnClose() throws Exception {
        var observed = new CompletableFuture<net.guneyilmaz0.micup4j.entities.Guild>();
        start(new ListenerAdapter() {
            @Override public void onGenericEvent(GenericEvent event) {
                if (event.getType().equals("GUILD_CREATE")) observed.complete(event.getMicup().findGuildById("1").orElseThrow());
            }
        });
        var envelope = Json.object().put("op", 0).put("t", "GUILD_CREATE").put("s", 2);
        envelope.set("d", EntityCacheTest.snapshot());
        sockets.poll(2, TimeUnit.SECONDS).send(envelope.toString());
        assertEquals("Guild", observed.get(3, TimeUnit.SECONDS).name());
        assertEquals(2, client.findGuildById("1").orElseThrow().roles().size());
        client.close(); assertTrue(client.getGuilds().isEmpty());
    }

    @Test void slowCacheApplyDoesNotStallGatewayStatusResumeOrHeartbeat() throws Exception {
        heartbeatInterval = 200;
        var observed = new CompletableFuture<net.guneyilmaz0.micup4j.entities.Guild>();
        start(new ListenerAdapter() {
            @Override public void onGenericEvent(GenericEvent event) {
                if (event.getType().equals("GUILD_CREATE")) observed.complete(event.getMicup().findGuildById("1").orElse(null));
            }
        });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        client.cacheApplyHook = () -> { entered.countDown(); awaitUninterruptibly(release); };
        try {
            var envelope = Json.object().put("op", 0).put("t", "GUILD_CREATE").put("s", 2);
            envelope.set("d", EntityCacheTest.snapshot());
            WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
            ws.send(envelope.toString());
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            frames.clear();
            // Reconnect drives RECONNECTING, CONNECTING and CONNECTED status writes on the Gateway scheduler.
            ws.send("{\"op\":7}");
            assertEquals("session-123", opcode(6).path("d").path("session_id").asText());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (client.getStatus() != Micup.Status.CONNECTED && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(Micup.Status.CONNECTED, client.getStatus());
            opcode(1);
            assertEquals(1, release.getCount(), "Gateway progress must not depend on the blocked cache apply");
            assertFalse(observed.isDone(), "Listeners must still wait for the cache apply");
        } finally {
            release.countDown();
        }
        assertEquals("Guild", observed.get(3, TimeUnit.SECONDS).name());
    }

    @Test void closeDuringCacheApplyReturnsPromptlyAndLeavesNoStaleEntities() throws Exception {
        start(new ListenerAdapter() {});
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var applier = new AtomicReference<Thread>();
        client.cacheApplyHook = () -> { applier.set(Thread.currentThread()); entered.countDown(); awaitUninterruptibly(release); };
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot());
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        try {
            CompletableFuture.runAsync(client::close).get(2, TimeUnit.SECONDS);
            assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
        } finally {
            release.countDown();
        }
        // shutdownNow retires the single event worker once the in-flight apply returns.
        applier.get().join(TimeUnit.SECONDS.toMillis(3));
        assertFalse(applier.get().isAlive());
        assertTrue(client.getGuilds().isEmpty());
        assertTrue(client.findGuildById("1").isEmpty());
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try { if (latch.await(10, TimeUnit.SECONDS)) break; else throw new IllegalStateException("Test hook was not released"); }
            catch (InterruptedException error) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    @Test void lightPresetDeliversRawEventsWithoutCachingEntities() throws Exception {
        var delivered = new CompletableFuture<Void>();
        client = MicupBuilder.createLight("test-token").setInstanceOrigin(server.url("/").uri())
                .setIgnoredEvents("GUILD_ROLE_UPDATE").setErrorHandler(errors::add).addEventListeners(new ListenerAdapter() {
                    @Override public void onGenericEvent(GenericEvent event) {
                        if (event.getType().equals("GUILD_CREATE")) delivered.complete(null);
                    }
                }).build();
        client.awaitReady(Duration.ofSeconds(5));
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        delivered.get(3, TimeUnit.SECONDS); assertTrue(client.getGuilds().isEmpty());
    }

    void entityResponses(java.util.function.Function<RecordedRequest, String> responder) {
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String body = responder.apply(request);
                return body == null ? new MockResponse().setResponseCode(204) : json(body);
            }
        });
    }

    @Test void typedEntityReadsUsePublicApiAndBoundedExclusiveMemberPages() throws Exception {
        start(new ListenerAdapter() {});
        entityResponses(request -> {
            String path = request.getPath();
            if (path.contains("/members?")) return "[" + EntityCacheTest.member("5") + "," + EntityCacheTest.member("6") + "]";
            if (path.contains("/members/")) return EntityCacheTest.member("4");
            if (path.endsWith("/roles")) return "[" + EntityCacheTest.role("1", 0) + "]";
            if (path.contains("/channels")) return path.endsWith("/channels") ? EntityCacheTest.snapshot().path("channels").toString()
                    : "{\"id\":\"8\",\"guild_id\":\"1\",\"type\":2,\"name\":\"voice\",\"permission_overwrites\":[]}";
            return EntityCacheTest.snapshot().toString();
        });
        assertEquals("Guild", client.retrieveGuildById("1").submit().get(3, TimeUnit.SECONDS).name());
        var request = route("/guilds/1"); assertEquals("Bot test-token", request.getHeader("Authorization"));
        assertEquals("/public/v1/guilds/1", request.getPath());
        assertEquals("1", client.retrieveRoles("1").submit().get(3, TimeUnit.SECONDS).getFirst().id());
        route("/roles");
        assertEquals("8", client.retrieveGuildChannels("1").submit().get(3, TimeUnit.SECONDS).getFirst().id()); route("/channels");
        assertEquals(net.guneyilmaz0.micup4j.entities.ChannelType.VOICE,
                client.retrieveGuildChannelById("8").submit().get(3, TimeUnit.SECONDS).getType()); route("/channels/8");
        assertEquals("4", client.retrieveMemberById("1", "4").submit().get(3, TimeUnit.SECONDS).getId()); route("/members/4");
        client.retrieveSelfMember("1").submit().get(3, TimeUnit.SECONDS); route("/members/@me");
        var page = client.retrieveMembers("1", 2, "4").submit().get(3, TimeUnit.SECONDS);
        assertEquals(List.of("5", "6"), page.stream().map(net.guneyilmaz0.micup4j.entities.Member::getId).toList());
        assertEquals("/public/v1/guilds/1/members?limit=2&after=4", route("/members?limit=2&after=4").getPath());
        assertThrows(UnsupportedOperationException.class, page::clear);
        assertTrue(client.getGuilds().isEmpty(), "HTTP reads must not populate Gateway state");
    }

    @Test void entityMutationsSerializeValidatedPayloadsAndLeaveCacheGatewayOwned() throws Exception {
        start(new ListenerAdapter() {});
        entityResponses(request -> {
            if (request.getPath().contains("/members/4/roles/")) return null;
            if (request.getPath().contains("/members/")) return EntityCacheTest.member("4");
            if (request.getPath().contains("/roles")) return EntityCacheTest.role("2", 1);
            return EntityCacheTest.snapshot().toString();
        });
        client.modifyGuildName("1", "Renamed").submit().get(3, TimeUnit.SECONDS);
        var request = route("/guilds/1"); assertEquals("PATCH", request.getMethod());
        assertEquals("Renamed", Json.parse(request.getBody().readUtf8()).path("name").asText());
        client.modifyMemberNickname("1", "4", null).submit().get(3, TimeUnit.SECONDS);
        assertTrue(Json.parse(route("/members/4").getBody().readUtf8()).get("nick").isNull());
        client.addMemberRole("1", "4", "2").submit().get(3, TimeUnit.SECONDS);
        assertEquals("PUT", route("/members/4/roles/2").getMethod());
        client.removeMemberRole("1", "4", "2").submit().get(3, TimeUnit.SECONDS);
        assertEquals("DELETE", route("/members/4/roles/2").getMethod());
        client.createRole("1", "Helper", 0x3498DB, net.guneyilmaz0.micup4j.permissions.PermissionSet.of(
                net.guneyilmaz0.micup4j.permissions.Permission.VIEW_CHANNEL)).submit().get(3, TimeUnit.SECONDS);
        request = route("/roles"); assertEquals("POST", request.getMethod());
        var body = Json.parse(request.getBody().readUtf8()); assertTrue(body.get("permissions").isTextual());
        assertEquals("1024", body.path("permissions").asText());
        client.modifyRoleName("1", "2", "Moderator").submit().get(3, TimeUnit.SECONDS);
        assertEquals("PATCH", route("/roles/2").getMethod());
        assertTrue(client.getGuilds().isEmpty());
    }

    @Test void memberPaginationRejectsInvalidLimitsOversizedOrNonadvancingResponses() throws Exception {
        start(new ListenerAdapter() {});
        assertThrows(IllegalArgumentException.class, () -> client.retrieveMembers("1", 0, null));
        assertThrows(IllegalArgumentException.class, () -> client.retrieveMembers("1", 1001, null));
        assertThrows(IllegalArgumentException.class, () -> client.retrieveMembers("1", 1, "../token"));
        assertThrows(IllegalArgumentException.class, () -> client.createRole("1", "R", 0, net.guneyilmaz0.micup4j.permissions.PermissionSet.ALL));
        entityResponses(request -> "[" + EntityCacheTest.member("4") + "]");
        assertThrows(ExecutionException.class, () -> client.retrieveMembers("1", 1, "4").submit().get(3, TimeUnit.SECONDS));
        entityResponses(request -> "[" + EntityCacheTest.member("5") + "," + EntityCacheTest.member("6") + "]");
        assertThrows(ExecutionException.class, () -> client.retrieveMembers("1", 1, null).submit().get(3, TimeUnit.SECONDS));
    }

    @Test void malformedPermissionEventsFailAndClearCachedStateWithoutLeakingPayloads() throws Exception {
        start(new ListenerAdapter() {});
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        assertFalse(client.getGuilds().isEmpty());
        sockets.poll(2, TimeUnit.SECONDS).send("{\"op\":0,\"s\":2,\"t\":\"GUILD_ROLE_UPDATE\",\"d\":{\"guild_id\":\"1\",\"role\":{\"id\":\"2\",\"permissions\":\"private-payload-value\"}}}");
        var error = assertThrows(ExecutionException.class, () -> client.termination().get(3, TimeUnit.SECONDS));
        assertFalse(error.toString().contains("private-payload-value"));
        assertEquals(Micup.Status.FAILED, client.getStatus()); assertTrue(client.getGuilds().isEmpty());
    }

    @Test void jdaStyleReadyAndMessageCallbacksBridgeLegacyListenersOnce() throws Exception {
        var legacyReady = new CountDownLatch(1); var modernReady = new CountDownLatch(1);
        var modernMessage = new CompletableFuture<net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent>();
        client = MicupBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri()).setErrorHandler(errors::add)
                .addEventListeners(new ListenerAdapter() {
                    @Override public void onReady(ReadyEvent event) { legacyReady.countDown(); }
                }, new ListenerAdapter() {
                    @Override public void onReady(net.guneyilmaz0.micup4j.events.session.ReadyEvent event) { modernReady.countDown(); }
                    @Override public void onMessageReceived(net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent event) { modernMessage.complete(event); }
                }).build().awaitReady();
        assertTrue(legacyReady.await(2, TimeUnit.SECONDS)); assertTrue(modernReady.await(2, TimeUnit.SECONDS));
        sockets.poll(2, TimeUnit.SECONDS).send("{\"op\":0,\"s\":2,\"t\":\"MESSAGE_CREATE\",\"d\":{\"id\":\"3\",\"channel_id\":\"2\",\"author\":{\"id\":\"4\",\"username\":\"Human\"},\"content\":\"ping\"}}");
        var event = modernMessage.get(3, TimeUnit.SECONDS);
        assertSame(client, event.getJDA()); assertEquals(3, event.getMessage().getIdLong());
        assertFalse(event.isFromGuild()); assertNull(event.getMember());
        assertEquals(net.guneyilmaz0.micup4j.entities.channel.ChannelType.PRIVATE, event.getChannelType());
        assertEquals(event.getChannelType(), event.getChannel().getType());
        assertEquals(2, event.getResponseNumber());
    }

    net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent slashFixture() {
        return new net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent(client, 2, Json.parse("""
                {"id":"123","application_id":"42","token":"interaction-secret","type":2,"guild_id":"1","channel_id":"8",
                 "member":{"user":{"id":"4","username":"Caller"},"roles":["2"]},
                 "data":{"name":"hello","options":[{"name":"name","type":3,"value":"World"},
                    {"name":"user","type":6,"value":"5"},{"name":"count","type":4,"value":7},{"name":"enabled","type":5,"value":true}],
                  "resolved":{"users":{"5":{"id":"5","username":"Target"}},"members":{"5":{"roles":["2"]}}}}}
                """));
    }

    @Test void typedSlashRepliesExposeHookAndSerializeFluentEphemeralPayload() throws Exception {
        var received = new CompletableFuture<net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent>();
        var legacy = new AtomicInteger();
        start(new ListenerAdapter() {
            @Override public void onInteractionCreate(InteractionCreateEvent event) { legacy.incrementAndGet(); }
            @Override public void onSlashCommandInteraction(net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent event) { received.complete(event); }
        });
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        var payload = slashFixture().getData();
        client.dispatch("INTERACTION_CREATE", 3, payload).get(3, TimeUnit.SECONDS);
        var event = received.get(3, TimeUnit.SECONDS); assertEquals(1, legacy.get());
        assertSame(client, event.getJDA()); assertEquals("Caller", event.getUser().getName());
        assertEquals("Guild", event.getGuild().getName()); assertEquals("4", event.getMember().getUser().getId());
        assertTrue(event.getMember().hasPermission(Permission.MESSAGE_SEND));
        assertNull(client.getGuildById("999")); assertTrue(client.findGuildById("999").isEmpty());
        assertNull(event.getOption("missing")); assertEquals("fallback", event.getOption("missing", "fallback", option -> option.getAsString()));
        assertEquals("Target", event.getOption("user").getAsUser().getName());
        assertEquals("5", event.getOption("user").getAsMember().getId());
        assertEquals(7, event.getOption("count").getAsInt()); assertTrue(event.getOption("enabled").getAsBoolean());
        assertThrows(IllegalStateException.class, () -> event.getOption("count").getAsBoolean());
        var action = event.reply("Hello").setEphemeral(true).addEmbeds(new EmbedBuilder().setTitle("Welcome").build());
        var hook = action.submit().get(3, TimeUnit.SECONDS);
        assertSame(event.getHook(), hook); assertTrue(event.isAcknowledged()); assertTrue(hook.isEphemeral());
        var callback = Json.parse(route("/callback").getBody().readUtf8());
        assertEquals(64, callback.path("data").path("flags").asInt());
        assertEquals("Welcome", callback.path("data").path("embeds").get(0).path("title").asText());
        assertThrows(IllegalStateException.class, () -> action.setEphemeral(false));
        assertThrows(ExecutionException.class, () -> event.reply("Again").submit().get(3, TimeUnit.SECONDS));
        assertFalse(hook.toString().contains("interaction-secret"));
    }

    @Test void deferredHookCreatesOriginalWithoutAPlaceholderThenSendsFollowups() throws Exception {
        start(new ListenerAdapter() {});
        var event = slashFixture();
        assertThrows(ExecutionException.class, () -> event.getHook().editOriginal("Too early").submit().get(2, TimeUnit.SECONDS));
        var hook = event.deferReply().setEphemeral(false).submit().get(3, TimeUnit.SECONDS);
        var callback = Json.parse(route("/callback").getBody().readUtf8());
        assertEquals(5, callback.path("type").asInt()); assertEquals(0, callback.path("data").path("flags").asInt());
        assertThrows(ExecutionException.class, () -> hook.retrieveOriginal().submit().get(2, TimeUnit.SECONDS));
        var original = hook.sendMessage("Original").submit().get(3, TimeUnit.SECONDS);
        var request = route("/messages/@original"); assertEquals("PATCH", request.getMethod()); assertNull(request.getHeader("Authorization"));
        assertEquals("Original", Json.parse(request.getBody().readUtf8()).path("content").asText());
        assertEquals("1", original.getGuild().getId());
        hook.sendMessage("Followup").submit().get(3, TimeUnit.SECONDS);
        request = route("/webhooks/42/interaction-secret"); assertEquals("POST", request.getMethod());
        hook.retrieveOriginal().submit().get(3, TimeUnit.SECONDS); assertEquals("GET", route("/messages/@original").getMethod());
        hook.deleteOriginal().submit().get(3, TimeUnit.SECONDS); assertEquals("DELETE", route("/messages/@original").getMethod());
    }

    @Test void cancellingHookWhileAwaitingAcknowledgementDoesNotCancelCallback() throws Exception {
        start(new ListenerAdapter() {});
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().endsWith("/callback")) return new MockResponse().setResponseCode(204).setHeadersDelay(200, TimeUnit.MILLISECONDS);
                return json("{\"id\":\"99\",\"channel_id\":\"8\",\"author\":{\"id\":\"1\"},\"content\":\"Done\"}");
            }
        });
        var event = slashFixture();
        var callback = event.deferReply().submit();
        var abandoned = event.getHook().editOriginal("Cancelled").submit();
        assertTrue(abandoned.cancel(true));
        var hook = callback.get(3, TimeUnit.SECONDS); route("/callback");
        hook.editOriginal("Done").submit().get(3, TimeUnit.SECONDS);
        var request = route("/messages/@original");
        assertEquals("Done", Json.parse(request.getBody().readUtf8()).path("content").asText());
        assertEquals(4, server.getRequestCount()); // Discovery, WS, callback and one original edit.
    }

    @Test void failedAcknowledgementPreventsHookHttpRequests() throws Exception {
        start(new ListenerAdapter() {});
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setResponseCode(403).setBody("{}"); }
        });
        var event = slashFixture();
        assertThrows(ExecutionException.class, () -> event.reply("Hello").submit().get(3, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> event.getHook().editOriginal("Never sent").submit().get(3, TimeUnit.SECONDS));
        assertEquals(3, server.getRequestCount());
    }

    @Test void jdaStyleCommandBuildersSupportGlobalAndGuildRegistration() throws Exception {
        start(new ListenerAdapter() {});
        entityResponses(request -> request.getMethod().equals("DELETE") ? null : request.getMethod().equals("POST")
                ? "{\"id\":\"111\",\"name\":\"hello\",\"description\":\"Say hello\",\"type\":1}"
                : "[{\"id\":\"111\",\"name\":\"hello\",\"description\":\"Say hello\",\"type\":1}]");
        var command = net.guneyilmaz0.micup4j.interactions.commands.build.Commands.slash("hello", "Say hello");
        command.addOption(net.guneyilmaz0.micup4j.interactions.commands.OptionType.STRING, "name", "Your name", false);
        var update = client.updateCommands().addCommands(command);
        var result = update.submit().get(3, TimeUnit.SECONDS); assertEquals("hello", result.getFirst().getName());
        var request = route("/commands"); assertEquals("/public/v1/applications/42/commands", request.getPath());
        assertEquals(3, Json.parse(request.getBody().readUtf8()).get(0).path("options").get(0).path("type").asInt());
        assertThrows(IllegalStateException.class, () -> update.addCommands(command));
        assertThrows(IllegalArgumentException.class, () -> client.updateCommands().addCommands(command, command));
        var guild = client.getGuildReferenceById("1");
        guild.updateCommands().addCommands(command).submit().get(3, TimeUnit.SECONDS);
        assertEquals("/public/v1/applications/42/guilds/1/commands", route("/commands").getPath());
        assertEquals(111L, guild.upsertCommand(command).submit().get(3, TimeUnit.SECONDS).getIdLong());
        assertEquals("POST", route("/commands").getMethod());
        assertEquals(1, guild.retrieveCommands().submit().get(3, TimeUnit.SECONDS).size()); assertEquals("GET", route("/commands").getMethod());
        guild.deleteCommandById(111L).submit().get(3, TimeUnit.SECONDS); assertEquals("DELETE", route("/commands/111").getMethod());
    }

    @Test void callbackConsumersObserveAnAlreadySettledAcknowledgementBarrier() throws Exception {
        start(new ListenerAdapter() {});
        var event = slashFixture();
        var delivered = new CompletableFuture<net.guneyilmaz0.micup4j.entities.Message>();
        event.deferReply().queue(hook -> {
            assertTrue(event.acknowledgement().isDone());
            try { delivered.complete(hook.editOriginal("Ready").complete(Duration.ofSeconds(2))); }
            catch (Throwable error) { delivered.completeExceptionally(error); }
        }, delivered::completeExceptionally);
        assertEquals("99", delivered.get(3, TimeUnit.SECONDS).getId());
        assertEquals("PATCH", route("/messages/@original").getMethod());
    }

    @Test void cachedTextChannelAliasesPreserveJdaStyleLookupAndMessageContext() throws Exception {
        start(new ListenerAdapter() {});
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        net.guneyilmaz0.micup4j.entities.channel.middleman.MessageChannel channel = client.getTextChannelById(8L);
        assertNotNull(channel); assertEquals("<#8>", channel.getAsMention());
        assertEquals("chat", client.getGuildById(1L).getTextChannelById("8").getName());
        assertNull(client.getTextChannelById("999"));
        var sent = channel.sendMessage("Hello").submit().get(3, TimeUnit.SECONDS);
        assertEquals("1", sent.getGuild().getId()); assertTrue(sent.isFromGuild());
        assertEquals("Guild", client.getGuildById(1L).getName());
        assertNotNull(client.getGuildById(1L).getMemberById(4L));
        assertNull(client.getGuildById(1L).getMemberById("999"));
    }

    @Test void cachedGuildLookupsReturnBoundSnapshotsWithoutPerCallCopies() throws Exception {
        start(new ListenerAdapter() {});
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        var guild = client.getGuildById(1L);
        assertSame(guild, client.getGuildById("1")); assertSame(guild, client.findGuildById("1").orElseThrow());
        assertSame(guild, client.getGuilds().getFirst()); assertSame(guild.getMembers(), client.getGuildById(1L).getMembers());
        var member = guild.getMemberById(4L);
        assertSame(client, guild.getJDA()); assertSame(client, member.client()); assertSame(client, member.getUser().client());
        assertSame(guild, member.getGuild());
        // A write that does not touch members rebinds nothing: existing member instances are reused.
        client.dispatch("CHANNEL_CREATE", 3, Json.parse("{\"id\":\"9\",\"guild_id\":\"1\",\"type\":0,\"name\":\"new\",\"permission_overwrites\":[]}"))
                .get(3, TimeUnit.SECONDS);
        var updated = client.getGuildById(1L);
        assertNotSame(guild, updated); assertSame(member, updated.getMemberById(4L));
        assertEquals("new", client.getTextChannelById(9L).getName());
        client.dispatch("CHANNEL_DELETE", 4, Json.parse("{\"id\":\"9\",\"guild_id\":\"1\"}")).get(3, TimeUnit.SECONDS);
        assertNull(client.getTextChannelById(9L)); assertNotNull(client.getTextChannelById(8L));
    }

    @Test void avatarGetterUsesDiscoveredMediaPrefixAndPreservesNullAvatars() throws Exception {
        start(new ListenerAdapter() {});
        var user = net.guneyilmaz0.micup4j.entities.User.fromJson(Json.parse(
                "{\"id\":\"4\",\"username\":\"Account\",\"global_name\":\"Display\",\"avatar\":\"a_hash\"}")).withClient(client);
        assertEquals(server.url("/proxy/avatars/4/a_hash.gif").toString(), user.getAvatarUrl());
        assertEquals("Display", user.getEffectiveName());
        var member = new net.guneyilmaz0.micup4j.entities.Member("1", user, null, List.of(), null, null, false, false);
        assertEquals("Display", member.getEffectiveName());
        assertNull(new net.guneyilmaz0.micup4j.entities.User("5", "No avatar", false).getAvatarUrl());
        assertEquals(-1L, new net.guneyilmaz0.micup4j.entities.User("18446744073709551615", "Unsigned", false).getIdLong());
    }

    @Test void privateDeferredOriginalRetrievalIsDecidedByServer() throws Exception {
        start(new ListenerAdapter() {});
        var hook = slashFixture().deferReply(true).submit().get(3, TimeUnit.SECONDS);
        var callback = Json.parse(route("/callback").getBody().readUtf8());
        assertEquals(64, callback.path("data").path("flags").asInt());
        assertTrue(hook.isEphemeral());
        hook.retrieveOriginal().submit().get(3, TimeUnit.SECONDS);
        assertEquals("GET", route("/messages/@original").getMethod());
    }

    @Test void malformedInteractionTokenDegradesToGenericEventWithoutFailingSession() throws Exception {
        var delivered = new LinkedBlockingQueue<GenericEvent>();
        start(new ListenerAdapter() {
            @Override public void onEvent(GenericEvent event) {
                if (event.getType().equals("INTERACTION_CREATE")) delivered.add(event);
                super.onEvent(event);
            }
        });
        WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
        String[] tokens = {"\"token\":\"   \",", "", "\"token\":12345,"};
        long seq = 2;
        for (String token : tokens) {
            ws.send("{\"op\":0,\"t\":\"INTERACTION_CREATE\",\"s\":" + seq++ + ",\"d\":{\"id\":\"123\",\"application_id\":\"42\","
                    + token + "\"type\":2,\"data\":{\"name\":\"ping\"}}}");
            var event = delivered.poll(3, TimeUnit.SECONDS);
            assertNotNull(event);
            assertSame(GenericEvent.class, event.getClass());
            assertEquals(seq - 1, event.getSequence());
        }
        assertEquals(Micup.Status.CONNECTED, client.getStatus());
        assertEquals(3, errors.size());
        for (Throwable error : errors) {
            assertEquals("Invalid INTERACTION_CREATE payload", error.getMessage());
            assertNull(error.getCause());
            assertFalse(String.valueOf(error).contains("12345"));
        }
        // A well-formed interaction afterwards is still typed.
        ws.send("{\"op\":0,\"t\":\"INTERACTION_CREATE\",\"s\":5,\"d\":{\"id\":\"123\",\"application_id\":\"42\","
                + "\"token\":\"interaction-secret\",\"type\":2,\"data\":{\"name\":\"ping\"}}}");
        assertInstanceOf(InteractionCreateEvent.class, delivered.poll(3, TimeUnit.SECONDS));
        for (Throwable error : errors) assertFalse(String.valueOf(error).contains("interaction-secret"));
    }

    /** Records the JDA-style callback order of the typed message and member events. */
    static final class TypedEventRecorder extends ListenerAdapter {
        final List<String> trace = new CopyOnWriteArrayList<>();
        final BlockingQueue<GenericEvent> events = new LinkedBlockingQueue<>();
        @Override public void onGenericEvent(GenericEvent event) {
            if (event.getType().startsWith("MESSAGE_") || event.getType().startsWith("GUILD_MEMBER_")) {
                trace.add("generic:" + event.getType()); events.add(event);
            }
        }
        @Override public void onGenericMessage(net.guneyilmaz0.micup4j.events.message.GenericMessageEvent event) { trace.add("genericMessage"); }
        @Override public void onMessageReceived(net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent event) { trace.add("received"); }
        @Override public void onMessageUpdate(net.guneyilmaz0.micup4j.events.message.MessageUpdateEvent event) { trace.add("update"); }
        @Override public void onMessageDelete(net.guneyilmaz0.micup4j.events.message.MessageDeleteEvent event) { trace.add("delete"); }
        @Override public void onGenericGuild(net.guneyilmaz0.micup4j.events.guild.GenericGuildEvent event) {
            if (event.getType().startsWith("GUILD_MEMBER_")) trace.add("genericGuild");
        }
        @Override public void onGenericGuildMember(net.guneyilmaz0.micup4j.events.guild.member.GenericGuildMemberEvent event) { trace.add("genericMember"); }
        @Override public void onGuildMemberJoin(net.guneyilmaz0.micup4j.events.guild.member.GuildMemberJoinEvent event) { trace.add("join"); }
        @Override public void onGuildMemberRemove(net.guneyilmaz0.micup4j.events.guild.member.GuildMemberRemoveEvent event) { trace.add("remove"); }
        GenericEvent next() throws InterruptedException {
            GenericEvent event = events.poll(3, TimeUnit.SECONDS); assertNotNull(event); return event;
        }
    }

    @Test void typedMessageUpdateAndDeleteEventsKeepWireTypeSequenceAndCallbackOrder() throws Exception {
        var recorder = new TypedEventRecorder();
        start(recorder);
        client.dispatch("MESSAGE_CREATE", 2, Json.parse("{\"id\":\"10\",\"channel_id\":\"8\",\"content\":\"hi\","
                + "\"author\":{\"id\":\"4\",\"username\":\"User\"}}")).get(3, TimeUnit.SECONDS);
        assertInstanceOf(net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent.class, recorder.next());
        assertEquals(List.of("generic:MESSAGE_CREATE", "genericMessage", "received"), recorder.trace);
        recorder.trace.clear();

        client.dispatch("MESSAGE_UPDATE", 3, Json.parse("{\"id\":\"10\",\"channel_id\":\"8\",\"guild_id\":\"1\","
                + "\"content\":\"edited secret\",\"author\":{\"id\":\"4\",\"username\":\"User\",\"global_name\":\"Shown\"},"
                + "\"member\":{\"nick\":\"Nick\",\"roles\":[\"2\"]}}")).get(3, TimeUnit.SECONDS);
        var update = assertInstanceOf(net.guneyilmaz0.micup4j.events.message.MessageUpdateEvent.class, recorder.next());
        assertEquals(List.of("generic:MESSAGE_UPDATE", "genericMessage", "update"), recorder.trace);
        assertEquals("MESSAGE_UPDATE", update.getType()); assertEquals(3, update.getSequence()); assertEquals(3, update.getResponseNumber());
        assertEquals("10", update.getMessageId()); assertEquals(10L, update.getMessageIdLong()); assertEquals("8", update.getChannelId());
        assertTrue(update.isFromGuild()); assertEquals("1", update.getGuildId()); assertEquals("1", update.getGuild().getId());
        assertEquals("edited secret", update.getMessage().getContentRaw());
        assertEquals("Nick", update.getMember().getNickname()); assertEquals("4", update.getMember().getId());
        assertEquals("Shown", update.getMember().getUser().getGlobalName()); assertEquals(List.of("2"), update.getMember().roleIds());
        assertSame(client, update.getJDA()); assertFalse(update.toString().contains("secret"));
        recorder.trace.clear();

        client.dispatch("MESSAGE_DELETE", 4, Json.parse("{\"id\":\"10\",\"channel_id\":\"8\",\"guild_id\":\"1\","
                + "\"content\":\"deleted secret\",\"author_id\":\"4\"}")).get(3, TimeUnit.SECONDS);
        var deleted = assertInstanceOf(net.guneyilmaz0.micup4j.events.message.MessageDeleteEvent.class, recorder.next());
        assertEquals(List.of("generic:MESSAGE_DELETE", "genericMessage", "delete"), recorder.trace);
        assertEquals("MESSAGE_DELETE", deleted.getType()); assertEquals(4, deleted.getSequence());
        assertEquals("deleted secret", deleted.getContentRaw()); assertEquals("4", deleted.getAuthorId());
        assertFalse(deleted.toString().contains("secret"));

        // Moderation and system deletes omit content and author; a DM delete has no guild.
        client.dispatch("MESSAGE_DELETE", 5, Json.parse("{\"id\":\"11\",\"channel_id\":\"7\"}")).get(3, TimeUnit.SECONDS);
        var moderated = assertInstanceOf(net.guneyilmaz0.micup4j.events.message.MessageDeleteEvent.class, recorder.next());
        assertNull(moderated.getContentRaw()); assertNull(moderated.getAuthorId()); assertNull(moderated.getGuildId());
        assertFalse(moderated.isFromGuild()); assertThrows(IllegalStateException.class, moderated::getGuild);
        assertEquals(net.guneyilmaz0.micup4j.entities.channel.ChannelType.PRIVATE, moderated.getChannelType());
        assertTrue(errors.isEmpty());
    }

    @Test void malformedMessageEventsDegradeWithoutLeakingDeletedContent() throws Exception {
        var recorder = new TypedEventRecorder();
        start(recorder);
        client.dispatch("MESSAGE_DELETE", 2, Json.parse("{\"id\":\"not-an-id\",\"channel_id\":\"7\",\"content\":\"private words\"}"))
                .get(3, TimeUnit.SECONDS);
        assertSame(GenericEvent.class, recorder.next().getClass());
        client.dispatch("MESSAGE_UPDATE", 3, Json.parse("{\"id\":\"10\",\"content\":\"private words\"}")).get(3, TimeUnit.SECONDS);
        var raw = recorder.next();
        assertSame(GenericEvent.class, raw.getClass()); assertEquals(3, raw.getSequence());
        assertEquals(Micup.Status.CONNECTED, client.getStatus());
        assertEquals(List.of("Invalid MESSAGE_DELETE payload", "Invalid MESSAGE_UPDATE payload"), errors.stream().map(Throwable::getMessage).toList());
        for (Throwable error : errors) { assertNull(error.getCause()); assertFalse(String.valueOf(error).contains("private")); }
    }

    @Test void memberJoinAndRemoveUseCacheSnapshotsAroundTheApply() throws Exception {
        var recorder = new TypedEventRecorder();
        var removedSeenInCache = new CompletableFuture<Boolean>();
        start(new ListenerAdapter() {
            @Override public void onEvent(GenericEvent event) {
                recorder.onEvent(event);
                if (event.getType().equals("GUILD_MEMBER_REMOVE"))
                    removedSeenInCache.complete(client.getGuildById("1").getMemberById("4") != null);
            }
        });
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);

        client.dispatch("GUILD_MEMBER_ADD", 3, Json.parse("{\"guild_id\":\"1\",\"user\":{\"id\":\"5\",\"username\":\"New\"},"
                + "\"nick\":\"Newbie\",\"roles\":[]}")).get(3, TimeUnit.SECONDS);
        var join = assertInstanceOf(net.guneyilmaz0.micup4j.events.guild.member.GuildMemberJoinEvent.class, recorder.next());
        assertEquals(List.of("generic:GUILD_MEMBER_ADD", "genericGuild", "genericMember", "join"), recorder.trace);
        assertEquals("GUILD_MEMBER_ADD", join.getType()); assertEquals(3, join.getSequence());
        assertEquals("5", join.getMember().getId()); assertEquals("Newbie", join.getMember().getNickname());
        assertEquals("New", join.getUser().getName()); assertSame(client, join.getMember().client());
        assertEquals("Guild", join.getGuild().getName()); assertNotNull(join.getGuild().getMemberById("5"));
        recorder.trace.clear();

        client.dispatch("GUILD_MEMBER_REMOVE", 4, Json.parse("{\"guild_id\":\"1\",\"user\":{\"id\":\"4\"}}")).get(3, TimeUnit.SECONDS);
        var remove = assertInstanceOf(net.guneyilmaz0.micup4j.events.guild.member.GuildMemberRemoveEvent.class, recorder.next());
        assertEquals(List.of("generic:GUILD_MEMBER_REMOVE", "genericGuild", "remove"), recorder.trace);
        assertFalse(net.guneyilmaz0.micup4j.events.guild.member.GenericGuildMemberEvent.class.isInstance(remove));
        assertEquals("GUILD_MEMBER_REMOVE", remove.getType()); assertEquals(4, remove.getSequence());
        assertEquals("4", remove.getMember().getId()); assertEquals(List.of("2"), remove.getMember().roleIds());
        assertEquals("User", remove.getUser().getName()); assertSame(remove.getMember().getUser(), remove.getUser());
        assertNull(remove.getGuild().getMemberById("4"));
        assertFalse(removedSeenInCache.get(3, TimeUnit.SECONDS));
        assertNull(client.getGuildById("1").getMemberById("4"));

        // An uncached guild still produces typed events with an id-only guild reference.
        client.dispatch("GUILD_MEMBER_ADD", 5, Json.parse("{\"guild_id\":\"77\",\"user\":{\"id\":\"6\",\"username\":\"Far\"},\"roles\":[]}"))
                .get(3, TimeUnit.SECONDS);
        var farJoin = assertInstanceOf(net.guneyilmaz0.micup4j.events.guild.member.GuildMemberJoinEvent.class, recorder.next());
        assertEquals("77", farJoin.getGuild().getId()); assertEquals("", farJoin.getGuild().getName());
        assertEquals("77", farJoin.getMember().guildId()); assertNull(client.getGuildById("77"));
        assertTrue(errors.isEmpty());
    }

    @Test void memberRemoveWithoutCacheExposesOnlyTheUserId() throws Exception {
        var recorder = new TypedEventRecorder();
        startLight(recorder);
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        client.dispatch("GUILD_MEMBER_REMOVE", 3, Json.parse("{\"guild_id\":\"1\",\"user\":{\"id\":\"4\"}}")).get(3, TimeUnit.SECONDS);
        var remove = assertInstanceOf(net.guneyilmaz0.micup4j.events.guild.member.GuildMemberRemoveEvent.class, recorder.next());
        assertNull(remove.getMember());
        assertEquals("4", remove.getUser().getId()); assertEquals("", remove.getUser().getName()); assertSame(client, remove.getUser().client());
        assertEquals("1", remove.getGuildId());
        client.dispatch("GUILD_MEMBER_ADD", 4, Json.parse("{\"guild_id\":\"1\",\"user\":{\"id\":\"5\",\"username\":\"New\"},\"roles\":[]}"))
                .get(3, TimeUnit.SECONDS);
        assertEquals("5", assertInstanceOf(net.guneyilmaz0.micup4j.events.guild.member.GuildMemberJoinEvent.class, recorder.next()).getMember().getId());
        assertTrue(client.getGuilds().isEmpty()); assertTrue(errors.isEmpty());
    }

    @Test void malformedMemberRemoveDegradesToGenericEventAndKeepsTheSession() throws Exception {
        var recorder = new TypedEventRecorder();
        start(recorder);
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot()).get(3, TimeUnit.SECONDS);
        // The returned future completes normally, so the sequence is acknowledged by the next heartbeat.
        client.dispatch("GUILD_MEMBER_REMOVE", 3, Json.parse("{\"guild_id\":\"1\",\"user\":{}}")).get(3, TimeUnit.SECONDS);
        var raw = recorder.next();
        assertSame(GenericEvent.class, raw.getClass()); assertEquals(3, raw.getSequence());
        assertEquals(List.of("generic:GUILD_MEMBER_REMOVE"), recorder.trace);
        assertEquals(Micup.Status.CONNECTED, client.getStatus());
        assertEquals(1, errors.size()); assertEquals("Invalid GUILD_MEMBER_REMOVE payload", errors.getFirst().getMessage());
        assertNotNull(client.getGuildById("1").getMemberById("4"));
    }

    // T7: graceful shutdown. User lookups are routed through a test-controlled handler; everything else keeps the
    // fixture behaviour.
    private void routeUsers(java.util.function.Function<RecordedRequest, MockResponse> handler) {
        var fixture = server.getDispatcher();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                return request.getPath().startsWith("/public/v1/users/") ? handler.apply(request) : fixture.dispatch(request);
            }
        });
    }
    private MockResponse user(String id) { return json("{\"id\":\"" + id + "\",\"username\":\"user\"}"); }
    private static void await(CountDownLatch latch) {
        try { latch.await(10, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    @Test void shutdownDrainsInFlightRequestAndRejectsNewWork() throws Exception {
        start(new ListenerAdapter() {});
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        routeUsers(request -> { received.countDown(); await(release); return user("7"); });
        try {
            var inFlight = client.retrieveUserById("7").submit();
            assertTrue(received.await(3, TimeUnit.SECONDS));
            client.shutdown();
            assertEquals(Micup.Status.SHUTTING_DOWN, client.getStatus());
            var rejected = assertThrows(ExecutionException.class, () -> client.retrieveUserById("8").submit().get(2, TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class, rejected.getCause());
            assertEquals("Client is shutting down", rejected.getCause().getMessage());
            assertFalse(client.awaitShutdown(Duration.ofMillis(100)), "The drain must wait for the in-flight request");
            assertFalse(client.termination().isDone());
            release.countDown();
            assertEquals("7", inFlight.get(3, TimeUnit.SECONDS).getId());
            assertTrue(client.awaitShutdown(Duration.ofSeconds(3)));
            assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
            assertNull(client.termination().get(1, TimeUnit.SECONDS));
            assertTrue(errors.isEmpty(), errors.toString());
        } finally { release.countDown(); }
    }

    @Test void shutdownLetsQueuedRateLimitRetryFinish() throws Exception {
        start(new ListenerAdapter() {});
        var attempts = new AtomicInteger();
        var limited = new CountDownLatch(1);
        routeUsers(request -> {
            if (attempts.incrementAndGet() > 1) return user("7");
            limited.countDown();
            return json("{\"code\":\"RATE_LIMITED\",\"retry_after\":0.2,\"global\":false}").setResponseCode(429);
        });
        var pending = client.retrieveUserById("7").submit();
        assertTrue(limited.await(3, TimeUnit.SECONDS));
        client.shutdown();
        assertEquals("7", pending.get(3, TimeUnit.SECONDS).getId());
        assertEquals(2, attempts.get());
        assertTrue(client.awaitShutdown(3, TimeUnit.SECONDS));
        assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
    }

    @Test void shutdownTimeoutCancelsRemainingRequests() throws Exception {
        restConfig = restConfig.withShutdownTimeout(Duration.ofMillis(200));
        start(new ListenerAdapter() {});
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        routeUsers(request -> { received.countDown(); await(release); return user("7"); });
        try {
            var stuck = client.retrieveUserById("7").submit();
            assertTrue(received.await(3, TimeUnit.SECONDS));
            long started = System.nanoTime();
            client.shutdown();
            assertNull(client.termination().get(3, TimeUnit.SECONDS));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 150, "The drain must last until its timeout");
            assertThrows(ExecutionException.class, () -> stuck.get(1, TimeUnit.SECONDS));
            assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
        } finally { release.countDown(); }
    }

    @Test void shutdownNowEscalatesARunningDrain() throws Exception {
        start(new ListenerAdapter() {});
        var received = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        routeUsers(request -> { received.countDown(); await(release); return user("7"); });
        try {
            var stuck = client.retrieveUserById("7").submit();
            assertTrue(received.await(3, TimeUnit.SECONDS));
            client.shutdown();
            assertEquals(Micup.Status.SHUTTING_DOWN, client.getStatus());
            client.shutdownNow();
            assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
            assertTrue(client.termination().isDone());
            assertThrows(ExecutionException.class, () -> stuck.get(2, TimeUnit.SECONDS));
            assertTrue(client.awaitShutdown(0, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }

    @Test void shutdownFromListenerReturnsAtOnceAndSilencesTheGateway() throws Exception {
        heartbeatInterval = 80;
        var statuses = new CopyOnWriteArrayList<Micup.Status>();
        var returned = new CompletableFuture<Long>();
        var awaited = new CompletableFuture<Boolean>();
        var laterEvents = new AtomicInteger();
        start(new ListenerAdapter() {
            @Override public void onStatusChange(StatusChangeEvent event) { statuses.add(event.getNewStatus()); }
            @Override public void onGenericEvent(GenericEvent event) {
                if (event.getType().equals("LATER")) laterEvents.incrementAndGet();
            }
            @Override public void onMessageReceived(MessageReceivedEvent event) {
                long started = System.nanoTime();
                event.getMicup().shutdown();
                returned.complete(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                // Finishing never needs the event thread, so waiting here cannot deadlock.
                try { awaited.complete(event.getMicup().awaitShutdown(Duration.ofSeconds(3))); }
                catch (Throwable error) { awaited.completeExceptionally(error); }
            }
        });
        WebSocket ws = sockets.poll(2, TimeUnit.SECONDS);
        ws.send("{\"op\":0,\"t\":\"MESSAGE_CREATE\",\"s\":2,\"d\":{\"id\":\"3\",\"channel_id\":\"2\",\"author\":{\"id\":\"5\"}}}");
        ws.send("{\"op\":0,\"t\":\"LATER\",\"s\":3,\"d\":{}}");
        assertTrue(returned.get(3, TimeUnit.SECONDS) < 1000, "shutdown() must not block a listener");
        assertTrue(awaited.get(5, TimeUnit.SECONDS));
        assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
        frames.clear();
        Thread.sleep(300); // Several heartbeat intervals.
        assertTrue(frames.isEmpty(), "The Gateway must send nothing after shutdown: " + frames);
        assertEquals(0, laterEvents.get(), "Queued Gateway events must not start after shutdown");
        assertTrue(statuses.contains(Micup.Status.SHUTTING_DOWN));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void shutdownAndCloseAreIdempotentAndReportShuttingDownOnce() throws Exception {
        var statuses = new CopyOnWriteArrayList<Micup.Status>();
        var shuttingDown = new CountDownLatch(1);
        start(new ListenerAdapter() {
            @Override public void onStatusChange(StatusChangeEvent event) {
                statuses.add(event.getNewStatus());
                if (event.getNewStatus() == Micup.Status.SHUTTING_DOWN) shuttingDown.countDown();
            }
        });
        client.shutdown();
        client.shutdown();
        assertTrue(client.awaitShutdown());
        assertTrue(shuttingDown.await(3, TimeUnit.SECONDS));
        client.close();
        client.close();
        client.shutdownNow();
        client.shutdown();
        assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
        assertEquals(1, statuses.stream().filter(status -> status == Micup.Status.SHUTTING_DOWN).count());
        assertFalse(statuses.contains(Micup.Status.FAILED));
        assertThrows(IllegalStateException.class, () -> client.getPresence().setIdle(true));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    /** Records the shutdown-related events a JDA-style listener observes, in order, with the delivering thread. */
    private static final class ShutdownRecorder extends ListenerAdapter {
        final List<String> seen = new CopyOnWriteArrayList<>();
        final CompletableFuture<net.guneyilmaz0.micup4j.events.session.ShutdownEvent> shutdown = new CompletableFuture<>();
        @Override public void onStatusChange(StatusChangeEvent event) {
            seen.add(event.getPreviousStatus() + "->" + event.getNewStatus());
        }
        @Override public void onShutdown(net.guneyilmaz0.micup4j.events.session.ShutdownEvent event) {
            seen.add("ShutdownEvent@" + Thread.currentThread().getName());
            shutdown.complete(event);
        }
    }

    @Test void gracefulShutdownDeliversFinalStatusAndShutdownEventOnce() throws Exception {
        var recorder = new ShutdownRecorder();
        start(recorder);
        client.shutdown();
        var event = recorder.shutdown.get(3, TimeUnit.SECONDS);
        assertTrue(client.awaitShutdown(Duration.ofSeconds(3)));
        assertEquals(1000, event.getCode());
        assertSame(client, event.getJDA());
        assertNotNull(event.getTimeShutdown());
        Thread.sleep(100); // A duplicate would arrive by now.
        var seen = List.copyOf(recorder.seen);
        int shuttingDown = seen.indexOf("CONNECTED->SHUTTING_DOWN");
        int shutdown = seen.indexOf("SHUTTING_DOWN->SHUTDOWN");
        assertTrue(shuttingDown >= 0 && shutdown > shuttingDown, seen.toString());
        assertEquals(seen.size() - 1, seen.indexOf("ShutdownEvent@micup4j-events"), seen.toString());
        assertEquals(1, seen.stream().filter(entry -> entry.endsWith("->SHUTDOWN")).count(), seen.toString());
        assertEquals(1, seen.stream().filter(entry -> entry.startsWith("ShutdownEvent")).count(), seen.toString());
        client.shutdownNow();
        Thread.sleep(50);
        assertEquals(seen, List.copyOf(recorder.seen), "A later shutdownNow must not deliver the events again");
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void shutdownNowDeliversFinalStatusAndShutdownEventOffTheCaller() throws Exception {
        var recorder = new ShutdownRecorder();
        start(recorder);
        client.shutdownNow();
        assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
        var event = recorder.shutdown.get(3, TimeUnit.SECONDS);
        assertEquals(1000, event.getCode());
        Thread.sleep(100);
        var seen = List.copyOf(recorder.seen);
        assertTrue(seen.contains("SHUTTING_DOWN->SHUTDOWN"), seen.toString());
        assertEquals(1, seen.stream().filter(entry -> entry.endsWith("->SHUTDOWN")).count(), seen.toString());
        assertEquals("ShutdownEvent@micup4j-shutdown", seen.get(seen.size() - 1), seen.toString());
        client.close();
        client.shutdown();
        Thread.sleep(50);
        assertEquals(seen, List.copyOf(recorder.seen));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void shutdownNowDeliversFinalEventsOnlyAfterABlockedListenerReturns() throws Exception {
        var seen = new CopyOnWriteArrayList<String>();
        var armed = new java.util.concurrent.atomic.AtomicBoolean();
        var active = new java.util.concurrent.atomic.AtomicInteger();
        var overlapped = new java.util.concurrent.atomic.AtomicBoolean();
        var entered = new CountDownLatch(1);
        var shutdownEvent = new CompletableFuture<Void>();
        start(new ListenerAdapter() {
            @Override public void onGenericEvent(GenericEvent event) {
                if (active.incrementAndGet() > 1) overlapped.set(true);
                try {
                    if (event instanceof StatusChangeEvent change) seen.add(change.getPreviousStatus() + "->" + change.getNewStatus());
                    else if (event instanceof net.guneyilmaz0.micup4j.events.session.ShutdownEvent) {
                        seen.add("ShutdownEvent");
                        shutdownEvent.complete(null);
                    } else if (armed.compareAndSet(true, false)) {
                        entered.countDown();
                        // Ignore the shutdownNow interrupt, like a listener stuck in uninterruptible I/O.
                        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400);
                        boolean interrupted = false;
                        for (long left; (left = until - System.nanoTime()) > 0; ) {
                            try { TimeUnit.NANOSECONDS.sleep(left); } catch (InterruptedException ignored) { interrupted = true; }
                        }
                        seen.add("blocked-listener-returned");
                        if (interrupted) Thread.currentThread().interrupt();
                    }
                } finally {
                    active.decrementAndGet();
                }
            }
        });
        armed.set(true);
        client.dispatch("GUILD_CREATE", 2, EntityCacheTest.snapshot());
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        long started = System.nanoTime();
        client.shutdownNow();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 300, "shutdownNow must not wait for the listener");
        assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
        // Termination must not depend on the event thread, so awaiting it while the listener is blocked succeeds.
        assertTrue(client.awaitShutdown(Duration.ofSeconds(3)));
        assertFalse(seen.contains("blocked-listener-returned"), "Termination should complete while the listener still runs");
        shutdownEvent.get(3, TimeUnit.SECONDS);
        Thread.sleep(100); // A duplicate or a late listener call would arrive by now.
        var order = List.copyOf(seen);
        int returned = order.indexOf("blocked-listener-returned");
        assertTrue(returned >= 0, order.toString());
        assertTrue(order.indexOf("SHUTTING_DOWN->SHUTDOWN") > returned, order.toString());
        assertEquals(order.size() - 1, order.indexOf("ShutdownEvent"), order.toString());
        assertEquals(1, order.stream().filter("ShutdownEvent"::equals).count(), order.toString());
        assertEquals(1, order.stream().filter(entry -> entry.endsWith("->SHUTDOWN")).count(), order.toString());
        assertFalse(overlapped.get(), "Final events must not run concurrently with another listener");
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void awaitShutdownRejectsGuardedSchedulerThreads() throws Exception {
        start(new ListenerAdapter() {});
        var outcome = new CompletableFuture<Throwable>();
        Thread guarded = net.guneyilmaz0.micup4j.internal.ThreadGuards.guarded(Thread.ofPlatform().factory()).newThread(() -> {
            try { client.awaitShutdown(Duration.ofMillis(10)); outcome.complete(null); }
            catch (Throwable error) { outcome.complete(error); }
        });
        guarded.start();
        assertInstanceOf(IllegalStateException.class, outcome.get(3, TimeUnit.SECONDS));
        assertFalse(client.awaitShutdown(Duration.ofMillis(10)), "A running client has not shut down");
    }

    @Test void shutdownBeforeDiscoveryCompletesNeverConnects() throws Exception {
        var discovering = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var fixture = server.getDispatcher();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().equals("/.well-known/fluxer")) { discovering.countDown(); await(release); }
                return fixture.dispatch(request);
            }
        });
        try {
            client = MicupBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri()).setErrorHandler(errors::add).build();
            assertTrue(discovering.await(3, TimeUnit.SECONDS));
            client.shutdown();
            assertTrue(client.awaitShutdown(3, TimeUnit.SECONDS));
            assertEquals(Micup.Status.SHUTDOWN, client.getStatus());
            assertThrows(ExecutionException.class, () -> client.ready().get(1, TimeUnit.SECONDS));
        } finally { release.countDown(); }
        Thread.sleep(200);
        assertEquals(0, connections.get());
        assertTrue(errors.isEmpty(), errors.toString());
    }
}
