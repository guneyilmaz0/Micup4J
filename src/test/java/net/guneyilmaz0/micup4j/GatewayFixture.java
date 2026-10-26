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

abstract class GatewayFixture {
    MockWebServer server;
    Micup client;
    final BlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
    final BlockingQueue<WebSocket> sockets = new LinkedBlockingQueue<>();
    final List<Throwable> errors = new CopyOnWriteArrayList<>();
    final AtomicInteger connections = new AtomicInteger();
    volatile int heartbeatInterval = 1000;
    volatile boolean acknowledge = true, hello = true;
    volatile String readyGuilds = "[]";
    GatewayConfig gatewayConfig = GatewayConfig.defaults();

    @BeforeEach void setup() throws Exception {
        server = new MockWebServer(); server.start();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath().equals("/.well-known/fluxer")) {
                    assertNull(request.getHeader("Authorization"));
                    return json("{\"endpoints\":{\"api_public\":\"" + server.url("/public") + "\",\"api_client\":\"https://wrong.invalid\",\"media\":\"" + server.url("/proxy") + "\",\"gateway\":\"" + server.url("/gateway").toString().replace("http:", "ws:") + "\"}}");
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
    String ready() { return "{\"op\":0,\"t\":\"READY\",\"s\":1,\"d\":{\"session_id\":\"session-123\",\"user\":{\"id\":\"1\",\"username\":\"test\",\"bot\":true},\"application\":{\"id\":\"42\",\"flags\":0},\"guilds\":" + readyGuilds + "}}"; }
    Micup start(ListenerAdapter listener) throws Exception {
        var readyDelivered = new CountDownLatch(1);
        client = MicupBuilder.createDefault("test-token").setInstanceOrigin(server.url("/").uri())
            .setGatewayConfig(gatewayConfig).setShard(0, 2).setIgnoredEvents("TYPING_START").setErrorHandler(errors::add).addEventListeners(new ListenerAdapter() {
                @Override public void onReady(ReadyEvent event) { readyDelivered.countDown(); }
            }, listener).build();
        client.awaitReady(Duration.ofSeconds(5));
        // Authentication wakes awaitReady before its dispatch is processed; direct fixture injections need this barrier.
        assertTrue(readyDelivered.await(3, TimeUnit.SECONDS)); return client;
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

}
