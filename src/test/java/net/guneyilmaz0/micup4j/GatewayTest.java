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
                        ws.send("{\"op\":10,\"d\":{\"heartbeat_interval\":" + heartbeatInterval + "}}");
                    }
                    @Override public void onMessage(WebSocket ws, String text) {
                        JsonNode frame = Json.parse(text); frames.add(frame);
                        if (frame.path("op").asInt() == 2) ws.send(ready());
                        if (frame.path("op").asInt() == 6) ws.send("{\"op\":0,\"t\":\"RESUMED\",\"s\":1,\"d\":{}}");
                        if (frame.path("op").asInt() == 1) ws.send("{\"op\":11}");
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
            .setShard(0, 2).setIgnoredEvents("TYPING_START").setErrorHandler(errors::add).addEventListeners(listener).build();
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

}
