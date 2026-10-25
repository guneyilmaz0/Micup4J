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

class GatewayTest {
    MockWebServer server;
    Micup client;
    final BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
    final BlockingQueue<WebSocket> sockets = new LinkedBlockingQueue<>();
    final List<Throwable> errors = new CopyOnWriteArrayList<>();
    final AtomicInteger connections = new AtomicInteger();
    volatile int heartbeatInterval = 1000;
    volatile boolean acknowledge = true, hello = true;
    GatewayConfig gatewayConfig = GatewayConfig.defaults();

    @BeforeEach void setup() throws Exception {
        server = new MockWebServer(); server.start();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().equals("/.well-known/fluxer")) {
                    assertNull(request.getHeader("Authorization"));
                    return json("{\"endpoints\":{\"api_public\":\"" + server.url("/public") + "\",\"api_client\":\"https://wrong.invalid\",\"gateway\":\"" + server.url("/gateway").toString().replace("http:", "ws:") + "\"}}");
                }
                if (request.getPath().startsWith("/gateway?")) return new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                    @Override public void onOpen(WebSocket ws, Response response) {
                        connections.incrementAndGet(); sockets.add(ws);
                        if (hello) ws.send("{\"op\":10,\"d\":{\"heartbeat_interval\":" + heartbeatInterval + "}}");
                    }
                    @Override public void onMessage(WebSocket ws, String text) {
                        JsonNode frame = Json.parse(text); frames.add(frame);
                        if (frame.path("op").asInt() == 2) ws.send(ready());
                        if (frame.path("op").asInt() == 6) ws.send("{\"op\":0,\"t\":\"RESUMED\",\"s\":1,\"d\":{}}");
                        if (frame.path("op").asInt() == 1 && acknowledge) ws.send("{\"op\":11}");
                    }
                    @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(code, reason); }
                });
                if (request.getPath().endsWith("/callback")) return new MockResponse().setResponseCode(204);
                if (request.getPath().endsWith("/commands")) return json("[]");
                return json("{\"id\":\"99\",\"channel_id\":\"2\",\"content\":\"Pong!\",\"author\":{\"id\":\"1\",\"username\":\"test\",\"bot\":true}}");
            }
        });
    }
    @AfterEach void cleanup() throws Exception { if (client != null) client.close(); server.shutdown(); }
    MockResponse json(String body) { return new MockResponse().setHeader("Content-Type", "application/json").setBody(body); }
    String ready() { return "{\"op\":0,\"t\":\"READY\",\"s\":1,\"d\":{\"session_id\":\"session-123\",\"user\":{\"id\":\"1\",\"username\":\"test\",\"bot\":true},\"application\":{\"id\":\"42\",\"flags\":0},\"guilds\":[]}}"; }
    Micup start(ListenerAdapter listener) throws Exception {
        client = MicupBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri())
            .setGatewayConfig(gatewayConfig).setShard(0, 2).setIgnoredEvents("TYPING_START").setErrorHandler(errors::add).addEventListeners(listener).build();
        client.awaitReady(Duration.ofSeconds(5)); return client;
    }
    JsonNode opcode(int op) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode frame = frames.poll(100, TimeUnit.MILLISECONDS);
            if (frame != null && frame.path("op").asInt() == op) return frame;
        }
        fail("Missing opcode " + op); return null;
    }
    RecordedRequest route(String suffix) throws Exception {
        for (int i = 0; i < 20; i++) {
            var request = server.takeRequest(3, TimeUnit.SECONDS);
            assertNotNull(request);
            if (request.getPath().endsWith(suffix)) return request;
        }
        fail("Missing route " + suffix); return null;
    }

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
                if (event.getType().equals("GUILD_CREATE")) observed.complete(event.getMicup().getGuildById("1").orElseThrow());
            }
        });
        var envelope = Json.object().put("op", 0).put("t", "GUILD_CREATE").put("s", 2);
        envelope.set("d", EntityCacheTest.snapshot());
        sockets.poll(2, TimeUnit.SECONDS).send(envelope.toString());
        assertEquals("Guild", observed.get(3, TimeUnit.SECONDS).name());
        assertEquals(2, client.getGuildById("1").orElseThrow().roles().size());
        client.close(); assertTrue(client.getGuilds().isEmpty());
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

}
