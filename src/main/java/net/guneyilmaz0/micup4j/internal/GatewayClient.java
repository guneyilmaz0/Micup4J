package net.guneyilmaz0.micup4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.config.GatewayConfig;
import net.guneyilmaz0.micup4j.diagnostics.GatewayCloseCode;
import net.guneyilmaz0.micup4j.events.ReconnectEvent;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.guneyilmaz0.micup4j.managers.Presence;

/** All protocol mutations run on one scheduler. User callbacks run on a separate bounded queue. */
public final class GatewayClient implements AutoCloseable {
    private final Micup client;
    private final HttpClient http;
    private final URI endpoint;
    private final String token;
    private final GatewayConfig config;
    private volatile long reconnectCount;
    private final List<String> ignored;
    private final int shardId, shardCount;
    private final ScheduledThreadPoolExecutor state = stateExecutor();
    private static ScheduledThreadPoolExecutor stateExecutor() {
        var executor = new ScheduledThreadPoolExecutor(1,
                ThreadGuards.guarded(Thread.ofPlatform().daemon().name("micup4j-gateway").factory()));
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }
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
    private final AtomicBoolean presenceSignalQueued = new AtomicBoolean();
    private ScheduledFuture<?> presenceTask;
    private boolean authenticated, presenceInFlight;
    private long nextPresenceAt;
    private Presence.State lastPresence;
    private final ArrayDeque<MemberRequest> memberQueue = new ArrayDeque<>();
    private MemberRequest activeMembers;
    private long memberNonces;
    /** Queued member requests beyond the running one. */
    public static final int MEMBER_REQUEST_CAPACITY = 64;
    /** A running member request fails when no chunk arrives for this long, because Micup silently drops unserviceable requests. */
    public static final java.time.Duration MEMBER_CHUNK_TIMEOUT = java.time.Duration.ofSeconds(30);

    private static final class MemberRequest {
        final ObjectNode data;
        final CompletableFuture<List<JsonNode>> result = new CompletableFuture<>();
        final List<JsonNode> members = new ArrayList<>();
        final BitSet chunks = new BitSet();
        String nonce;
        int chunkCount = -1;
        ScheduledFuture<?> timer;
        MemberRequest(ObjectNode data) { this.data = data; }
    }

    /**
     * Creates a Gateway client; call {@link #start()} to connect.
     *
     * @param client     the owning session
     * @param http       the HTTP client that opens the WebSocket
     * @param endpoint   the discovered Gateway endpoint
     * @param token      the bot token; sent only in the identify payload
     * @param ignored    event types the session does not want to receive
     * @param shardId    the zero-based shard id
     * @param shardCount the total number of shards
     * @param config     the Gateway policy
     * @throws IllegalArgumentException if the endpoint is not acceptable, see {@link RestClient#requireEndpoint(URI, boolean)}
     */
    public GatewayClient(Micup client, HttpClient http, URI endpoint, String token, List<String> ignored, int shardId, int shardCount, GatewayConfig config) {
        RestClient.requireEndpoint(endpoint, true);
        this.client = client;
        this.config = config;
        this.http = http;
        this.endpoint = endpoint;
        this.token = token;
        this.ignored = List.copyOf(ignored);
        this.shardId = shardId;
        this.shardCount = shardCount;
    }

    /**
     * Signals that the desired presence changed. Signals coalesce into one pending flush on the Gateway scheduler, and the
     * latest desired state is read when it runs, so this never queues an unbounded number of Gateway commands. Non-blocking.
     */
    public void updatePresence() {
        if (closed || !presenceSignalQueued.compareAndSet(false, true)) return;
        execute(() -> { presenceSignalQueued.set(false); flushPresence(); });
    }

    private void flushPresence() {
        if (closed || !authenticated || socket == null || presenceInFlight || presenceTask != null) return;
        Presence.State desired = client.getPresence().snapshot();
        if (!desired.configured() || desired.equals(lastPresence)) return;
        long delay = Math.max(0, nextPresenceAt - System.nanoTime());
        if (delay > 0) {
            presenceTask = state.schedule(() -> { presenceTask = null; flushPresence(); }, delay, TimeUnit.NANOSECONDS);
            return;
        }
        long id = generation, epoch = sessionEpoch;
        presenceInFlight = true;
        lastPresence = desired;
        // Space presence updates by 4.1 s; changes made in between collapse into the latest desired state.
        nextPresenceAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(4100);
        send(3, desired.toJson());
        sends.whenComplete((ignored, failure) -> execute(() -> {
            if (id != generation || epoch != sessionEpoch) return;
            presenceInFlight = false;
            if (failure == null) flushPresence();
        }));
    }

    /** Starts connecting on the Gateway scheduler. Non-blocking; progress is reported through status changes. */
    public void start() {
        execute(this::connect);
    }

    /**
     * Returns how many reconnect attempts were scheduled.
     *
     * @return the reconnect count
     */
    public long getReconnectCount() { return reconnectCount; }

    /**
     * Returns the latest heartbeat round-trip time.
     *
     * @return the ping in milliseconds, or {@code -1} before the first acknowledgement
     */
    public long getPing() {
        return ping;
    }

    /**
     * Sends a Request Guild Members command and collects every chunk that answers it. Micup runs one member request per
     * connection and replaces a pending one, so requests are sent strictly one at a time, in submission order.
     *
     * <p>The result fails with a {@link java.util.concurrent.TimeoutException} if no chunk arrives within
     * {@link #MEMBER_CHUNK_TIMEOUT}, with a {@link net.guneyilmaz0.micup4j.requests.GatewayRateLimitException} when the
     * server refuses it, and with an {@link IllegalStateException} if the connection is lost while it runs, since chunks are
     * never replayed. Queued requests survive reconnects. Cancelling the result releases its slot.
     *
     * @param data the command payload without a nonce; copied
     * @return the raw member objects of every chunk, in arrival order
     */
    public CompletableFuture<List<JsonNode>> requestMembers(ObjectNode data) {
        var request = new MemberRequest(data.deepCopy());
        if (ignored.contains("GUILD_MEMBERS_CHUNK"))
            return CompletableFuture.failedFuture(new IllegalStateException("GUILD_MEMBERS_CHUNK is ignored by this session"));
        try {
            state.execute(() -> {
                synchronized (this) {
                    if (closed) { request.result.completeExceptionally(new IllegalStateException("Gateway is closed")); return; }
                    if (memberQueue.size() >= MEMBER_REQUEST_CAPACITY) {
                        request.result.completeExceptionally(new RejectedExecutionException("Member request queue is full"));
                        return;
                    }
                    memberQueue.add(request);
                    pumpMembers();
                }
            });
        } catch (RejectedExecutionException error) {
            request.result.completeExceptionally(new IllegalStateException("Gateway is closed"));
        }
        request.result.whenComplete((value, error) -> { if (request.result.isCancelled() || error != null) execute(() -> releaseMembers(request)); });
        return request.result;
    }

    private synchronized void pumpMembers() {
        if (closed || !authenticated || socket == null || activeMembers != null) return;
        MemberRequest next;
        while ((next = memberQueue.poll()) != null && next.result.isDone()) {}
        if (next == null) return;
        activeMembers = next;
        next.nonce = "m4j" + Long.toString(++memberNonces, 36);
        next.data.put("nonce", next.nonce);
        send(8, next.data);
        armMemberTimer(next);
    }

    private synchronized void armMemberTimer(MemberRequest request) {
        if (request.timer != null) request.timer.cancel(false);
        request.timer = state.schedule(() -> finishMembers(request,
                new TimeoutException("No member chunk arrived within " + MEMBER_CHUNK_TIMEOUT.toSeconds() + " seconds")),
                MEMBER_CHUNK_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
    }

    private synchronized void releaseMembers(MemberRequest request) {
        memberQueue.remove(request);
        if (activeMembers == request) finishMembers(request, null);
    }

    private synchronized void finishMembers(MemberRequest request, Throwable failure) {
        if (request.timer != null) request.timer.cancel(false);
        if (failure != null) request.result.completeExceptionally(failure);
        else request.result.complete(List.copyOf(request.members));
        if (activeMembers == request) { activeMembers = null; pumpMembers(); }
    }

    private synchronized void memberChunk(String type, JsonNode data) {
        MemberRequest request = activeMembers;
        if (request == null) return;
        if (type.equals("RATE_LIMITED")) {
            if (data.path("opcode").asInt() != 8 || !request.nonce.equals(data.path("meta").path("nonce").asText(null))) return;
            long millis = (long) Math.ceil(Math.max(0, data.path("retry_after").asDouble()) * 1000);
            finishMembers(request, new net.guneyilmaz0.micup4j.requests.GatewayRateLimitException(8, java.time.Duration.ofMillis(millis)));
            return;
        }
        if (!request.nonce.equals(data.path("nonce").asText(null))) return;
        int index = data.path("chunk_index").asInt(-1), count = data.path("chunk_count").asInt(-1);
        if (count < 1 || index < 0 || index >= count || (request.chunkCount >= 0 && request.chunkCount != count) || !data.path("members").isArray()) {
            finishMembers(request, new IllegalStateException("Invalid member chunk"));
            return;
        }
        request.chunkCount = count;
        if (request.chunks.get(index)) return;
        request.chunks.set(index);
        data.path("members").forEach(request.members::add);
        if (request.chunks.cardinality() == count) finishMembers(request, null);
        else armMemberTimer(request);
    }

    private synchronized void interruptMembers() {
        MemberRequest request = activeMembers;
        if (request != null) finishMembers(request, new IllegalStateException("Gateway connection was lost before every member chunk arrived"));
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
        authenticated = false; presenceInFlight = false; lastPresence = null;
        nextPresenceAt = System.nanoTime();
        if (presenceTask != null) { presenceTask.cancel(false); presenceTask = null; }
        long id = ++generation;
        client.updateStatus(Micup.Status.CONNECTING);
        URI url = URI.create(endpoint + "?v=1&encoding=json");
        connection = http.newWebSocketBuilder().connectTimeout(config.connectTimeout())
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
                if (error != null) retry(ReconnectEvent.Reason.TRANSPORT_ERROR, -1);
            });
        });
        try {
            watchdog = state.schedule(() -> {
                if (id == generation) retry(ReconnectEvent.Reason.HANDSHAKE_TIMEOUT, -1);
            }, config.handshakeTimeout().toNanos(), TimeUnit.NANOSECONDS);
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
                            retry(ReconnectEvent.Reason.HEARTBEAT_TIMEOUT, -1);
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
                case 7 -> retry(ReconnectEvent.Reason.SERVER_REQUEST, -1);
                case 9 -> {
                    resetSession();
                    identify();
                }
                default ->
                        System.getLogger("Micup4J").log(System.Logger.Level.DEBUG, "Ignoring unknown Gateway opcode {0}", frame.path("op").asInt());
            }
        } catch (Exception e) {
            client.reportFailure(new IllegalStateException("Invalid Gateway payload"));
            retry(ReconnectEvent.Reason.INVALID_PAYLOAD, -1);
        } finally {
            if (!closed && id == generation) ws.request(1);
        }
    }

    private void identify() {
        var data = Json.object().put("token", token);
        data.set("properties", Json.object().put("os", System.getProperty("os.name")).put("browser", "Micup4J").put("device", "Micup4J"));
        var initialPresence = client.getPresence().snapshot();
        if (initialPresence.configured()) data.set("presence", initialPresence.toJson());
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
            authenticated = true;
            client.updateStatus(Micup.Status.CONNECTED);
            flushPresence();
            pumpMembers();
        }
        if (type.equals("GUILD_MEMBERS_CHUNK") || type.equals("RATE_LIMITED")) memberChunk(type, data);
        if (!type.equals("RESUMED") && seq <= highestSeen) return;
        highestSeen = Math.max(highestSeen, seq);
        long epoch = sessionEpoch;
        client.dispatch(type, seq, data).whenComplete((value, failure) -> execute(() -> {
            if (epoch != sessionEpoch) return;
            if (failure == null) sequence = Math.max(sequence, seq);
            else {
                // Queue overflow cannot be acknowledged. Stop to avoid silently skipping events.
                fail(new IllegalStateException("Event delivery failed; check entity payloads, queue capacity and listener duration"));
            }
        }));
    }

    private void resetSession() {
        authenticated = false; presenceInFlight = false; lastPresence = null;
        if (presenceTask != null) { presenceTask.cancel(false); presenceTask = null; }
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
                if (id == generation) retry(ReconnectEvent.Reason.TRANSPORT_ERROR, -1);
            });
        });
    }

    private void ended(long id, int code) {
        if (id != generation || closed) return;
        GatewayCloseCode policy = GatewayCloseCode.fromCode(code);
        if (policy.getAction() == GatewayCloseCode.Action.STOP) {
            fail(new IllegalStateException("Gateway closed with code " + code));
            return;
        }
        if (policy.getAction() == GatewayCloseCode.Action.IDENTIFY) resetSession();
        retry(ReconnectEvent.Reason.CLOSE_CODE, code);
    }

    private void retry(ReconnectEvent.Reason reason, int closeCode) {
        if (closed || reconnect != null) return;
        generation++;
        interruptMembers();
        authenticated = false; presenceInFlight = false;
        if (presenceTask != null) { presenceTask.cancel(false); presenceTask = null; }
        if (heartbeat != null) heartbeat.cancel(false);
        if (watchdog != null) watchdog.cancel(false);
        if (socket != null) { socket.abort(); socket = null; }
        awaitingAck = false;
        client.updateStatus(Micup.Status.RECONNECTING);
        long maximum = config.reconnectMaxDelay().toNanos();
        long base = config.reconnectBaseDelay().toNanos();
        long backoff = Math.min(maximum, base * (1L << Math.min(attempts++, 16)));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, Math.min(base / 4, maximum - backoff + 1)));
        long delay = Math.min(maximum, backoff + jitter);
        reconnectCount++;
        client.dispatchDiagnostic(new ReconnectEvent(client, reason, attempts, java.time.Duration.ofNanos(delay), closeCode));
        reconnect = state.schedule(this::connect, delay, TimeUnit.NANOSECONDS);
    }

    private void fail(Throwable error) {
        client.gatewayFailed(error);
        close();
    }

    /**
     * Closes the connection and stops the Gateway scheduler. Idempotent and non-blocking; pending work on the scheduler is
     * discarded and the socket is aborted rather than closed gracefully.
     */
    @Override
    public void close() {
        closed = true;
        state.shutdownNow();
        synchronized (this) {
            var failure = new IllegalStateException("Gateway is closed");
            if (activeMembers != null) { activeMembers.result.completeExceptionally(failure); activeMembers = null; }
            memberQueue.forEach(request -> request.result.completeExceptionally(failure));
            memberQueue.clear();
        }
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
                if (id == generation) retry(ReconnectEvent.Reason.TRANSPORT_ERROR, -1);
            });
        }
    }
}
