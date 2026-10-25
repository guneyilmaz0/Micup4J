package net.guneyilmaz0.micup4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.Micup;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** All protocol mutations run on one scheduler. User callbacks run on a separate bounded queue. */
public final class GatewayClient implements AutoCloseable {
    private final Micup client;
    private final HttpClient http;
    private final URI endpoint;
    private final String token;
    private final List<String> ignored;
    private final int shardId, shardCount;
    private final ScheduledExecutorService state = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("micup4j-gateway").factory());
    private volatile WebSocket socket;
    private volatile CompletableFuture<WebSocket> connection;
    private CompletableFuture<Void> sends = CompletableFuture.completedFuture(null);
    private ScheduledFuture<?> heartbeat, watchdog, reconnect;
    private String sessionId;
    private long sequence, highestSeen, sessionEpoch, generation;
    private int attempts;
    private boolean awaitingAck;
    private volatile boolean closed;
    private volatile long ping = -1;
    private long heartbeatSent;

    public GatewayClient(Micup client, HttpClient http, URI endpoint, String token, List<String> ignored, int shardId, int shardCount) {
        RestClient.requireEndpoint(endpoint, true);
        this.client = client;
        this.http = http;
        this.endpoint = endpoint;
        this.token = token;
        this.ignored = List.copyOf(ignored);
        this.shardId = shardId;
        this.shardCount = shardCount;
    }

    public void start() {
        execute(this::connect);
    }

    public long getPing() {
        return ping;
    }

    private void execute(Runnable operation) {
        if (closed) return;
        try {
            state.execute(() -> {
                if (!closed) operation.run();
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void connect() {
        if (closed) return;
        reconnect = null;
        long id = ++generation;
        client.updateStatus(Micup.Status.CONNECTING);
        URI url = URI.create(endpoint + "?v=1&encoding=json");
        connection = http.newWebSocketBuilder().connectTimeout(java.time.Duration.ofSeconds(15))
                .buildAsync(url, new SocketListener(id));
        connection.whenComplete((ws, error) -> {
            if (closed) {
                if (ws != null) ws.abort();
                return;
            }
            execute(() -> {
                if (id != generation) {
                    if (ws != null) ws.abort();
                    return;
                }
                if (error != null) retry();
            });
        });
        try {
            watchdog = state.schedule(() -> {
                if (id == generation) retry();
            }, 60, TimeUnit.SECONDS);
        } catch (RejectedExecutionException error) {
            if (!closed) throw error;
        }
    }

    private void opened(long id, WebSocket ws) {
        if (id != generation || closed) {
            ws.abort();
            return;
        }
        socket = ws;
        sends = CompletableFuture.completedFuture(null);
        awaitingAck = false;
    }

    private void receive(long id, WebSocket ws, String text) {
        if (id != generation || closed) return;
        try {
            JsonNode frame = Json.parse(text);
            if (!frame.isObject() || !frame.path("op").isIntegralNumber())
                throw new IllegalArgumentException("Invalid Gateway frame");
            JsonNode data = frame.path("d");
            switch (frame.path("op").asInt()) {
                case 10 -> {
                    long interval = data.path("heartbeat_interval").asLong();
                    if (interval < 10 || interval > 300_000)
                        throw new IllegalArgumentException("Invalid heartbeat interval");
                    if (heartbeat != null) heartbeat.cancel(false);
                    if (sessionId == null) identify();
                    else send(6, Json.object().put("token", token).put("session_id", sessionId).put("seq", sequence));
                    heartbeat = state.scheduleAtFixedRate(() -> {
                        if (awaitingAck) {
                            retry();
                            return;
                        }
                        beat();
                    }, interval, interval, TimeUnit.MILLISECONDS);
                }
                case 0 -> dispatch(frame);
                case 1 -> beat();
                case 11 -> {
                    if (awaitingAck) ping = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - heartbeatSent);
                    awaitingAck = false;
                }
                case 7 -> retry();
                case 9 -> {
                    resetSession();
                    identify();
                }
                default ->
                        System.getLogger("Micup4J").log(System.Logger.Level.DEBUG, "Ignoring unknown Gateway opcode {0}", frame.path("op").asInt());
            }
        } catch (Exception e) {
            client.reportFailure(new IllegalStateException("Invalid Gateway payload"));
            retry();
        } finally {
            if (!closed && id == generation) ws.request(1);
        }
    }

    private void identify() {
        var data = Json.object().put("token", token);
        data.set("properties", Json.object().put("os", System.getProperty("os.name")).put("browser", "Micup4J").put("device", "Micup4J"));
        if (!ignored.isEmpty()) data.set("ignored_events", Json.MAPPER.valueToTree(ignored));
        if (shardCount > 0) data.set("shard", Json.MAPPER.valueToTree(new int[]{shardId, shardCount}));
        send(2, data);
        client.updateStatus(Micup.Status.IDENTIFYING);
    }

    private void dispatch(JsonNode frame) {
        String type = frame.path("t").asText();
        JsonNode data = frame.path("d");
        long seq = frame.path("s").asLong(-1);
        if (seq < 0 || type.isEmpty()) throw new IllegalArgumentException("Invalid Dispatch");
        if (type.equals("READY")) {
            resetSession();
            sessionId = data.path("session_id").asText();
            if (sessionId.isBlank()) throw new IllegalArgumentException("Missing session ID");
            client.onReady(data);
        }
        if (type.equals("READY") || type.equals("RESUMED")) {
            attempts = 0;
            if (watchdog != null) watchdog.cancel(false);
            client.updateStatus(Micup.Status.CONNECTED);
        }
        if (!type.equals("RESUMED") && seq <= highestSeen) return;
        highestSeen = Math.max(highestSeen, seq);
        long epoch = sessionEpoch;
        client.dispatch(type, seq, data).whenComplete((value, failure) -> execute(() -> {
            if (epoch != sessionEpoch) return;
            if (failure == null) sequence = Math.max(sequence, seq);
            else {
                // Queue overflow cannot be acknowledged. Stop to avoid silently skipping events.
                fail(new IllegalStateException("Event queue exhausted; increase capacity or shorten listener work"));
            }
        }));
    }

    private void resetSession() {
        sessionId = null;
        sequence = 0;
        highestSeen = 0;
        sessionEpoch++;
    }

    private void beat() {
        if (!awaitingAck) {
            awaitingAck = true;
            heartbeatSent = System.nanoTime();
        }
        send(1, sessionId == null ? Json.MAPPER.nullNode() : Json.MAPPER.valueToTree(sequence));
    }

    private void send(int opcode, JsonNode data) {
        ObjectNode frame = Json.object().put("op", opcode);
        frame.set("d", data);
        String text = frame.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > 4096) {
            fail(new IllegalArgumentException("Gateway command exceeds 4096 bytes"));
            return;
        }
        WebSocket target = socket;
        long id = generation;
        sends = sends.thenCompose(value -> target.sendText(text, true).thenApply(ws -> (Void) null));
        sends.whenComplete((value, failure) -> {
            if (failure != null) execute(() -> {
                if (id == generation) retry();
            });
        });
    }

    private void ended(long id, int code) {
        if (id != generation || closed) return;
        if (Set.of(4001, 4002, 4004, 4005, 4010, 4011, 4012).contains(code)) {
            fail(new IllegalStateException("Gateway closed with code " + code));
            return;
        }
        if (code == 4003 || code == 4007) resetSession();
        retry();
    }

    private void retry() {
        if (closed || reconnect != null) return;
        generation++;
        if (heartbeat != null) heartbeat.cancel(false);
        if (watchdog != null) watchdog.cancel(false);
        if (socket != null) {
            socket.abort();
            socket = null;
        }
        awaitingAck = false;
        client.updateStatus(Micup.Status.RECONNECTING);
        long delay = Math.min(30_000, 1000L << Math.min(attempts++, 5)) + ThreadLocalRandom.current().nextLong(250);
        reconnect = state.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void fail(Throwable error) {
        client.gatewayFailed(error);
        close();
    }

    @Override
    public void close() {
        closed = true;
        state.shutdownNow();
        if (socket != null) socket.abort();
        var pending = connection;
        if (pending != null) pending.whenComplete((ws, error) -> { if (ws != null) ws.abort(); });
    }

    private final class SocketListener implements WebSocket.Listener {
        private final long id;
        private final StringBuilder fragments = new StringBuilder();

        private SocketListener(long id) {
            this.id = id;
        }

        @Override
        public void onOpen(WebSocket ws) {
            execute(() -> {
                opened(id, ws);
                ws.request(1);
            });
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
            if (fragments.length() + text.length() > 16 * 1024 * 1024) {
                ws.abort();
                execute(() -> {
                    if (id == generation) fail(new IllegalStateException("Gateway frame exceeds client safety limit"));
                });
                return null;
            }
            fragments.append(text);
            if (last) {
                String payload = fragments.toString();
                fragments.setLength(0);
                execute(() -> receive(id, ws, payload));
            } else ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            ws.abort();
            execute(() -> {
                if (id == generation) fail(new IllegalStateException("Unexpected compressed Gateway frame"));
            });
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer data) {
            return WebSocket.Listener.super.onPing(ws, data);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket ws, ByteBuffer data) {
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            execute(() -> ended(id, code));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            execute(() -> {
                if (id == generation) retry();
            });
        }
    }
}