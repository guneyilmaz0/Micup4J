package com.swadedev.micup4j.load;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.MicupBuilder;
import com.swadedev.micup4j.config.CachePolicy;
import com.swadedev.micup4j.config.GatewayConfig;
import com.swadedev.micup4j.events.guild.GuildReadyEvent;
import com.swadedev.micup4j.events.message.MessageReceivedEvent;
import com.swadedev.micup4j.hooks.ListenerAdapter;
import com.swadedev.micup4j.internal.Json;
import com.swadedev.micup4j.sharding.DefaultShardManagerBuilder;
import com.swadedev.micup4j.sharding.SessionStartGate;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Repeatable load harness. It runs Micup4J against an in-process mock instance (discovery, Gateway with Resume replay,
 * REST) on loopback, so results measure the library and the local JVM, not Micup's servers or the network. Run it with
 * {@code ./gradlew loadTest}; results are printed and written as JSON to {@code build/reports/load/results.json}.
 */
public final class LoadHarness {
    private static final int EVENTS = Integer.getInteger("load.events", 50_000);
    private static final int GUILDS = Integer.getInteger("load.guilds", 1_000);
    private static final int REQUESTS = Integer.getInteger("load.requests", 250);
    private static final int SHARDS = Integer.getInteger("load.shards", 8);
    private static final int SOAK_SECONDS = Integer.getInteger("load.soakSeconds", 0);
    private static final int PACED_RATE = Integer.getInteger("load.pacedRate", 5_000);

    private final MockWebServer server = new MockWebServer();
    private final Map<WebSocket, AtomicLong> sequences = new ConcurrentHashMap<>();
    private final List<String> replay = java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile WebSocket socket;
    private volatile String readyGuilds = "[]";
    private volatile CountDownLatch identified = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        var harness = new LoadHarness();
        harness.server.setDispatcher(harness.dispatcher());
        harness.server.start();
        var results = Json.object();
        var environment = results.putObject("environment");
        environment.put("java", System.getProperty("java.vm.name") + " " + Runtime.version())
                .put("os", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"))
                .put("cpus", Runtime.getRuntime().availableProcessors())
                .put("maxHeapMiB", Runtime.getRuntime().maxMemory() / (1024 * 1024))
                .put("gc", ManagementFactory.getGarbageCollectorMXBeans().stream().map(b -> b.getName()).toList().toString())
                .put("transport", "in-process MockWebServer over loopback");
        try {
            results.set("eventBurst", harness.events(EVENTS, 0));
            results.set("eventsPaced", harness.events(PACED_RATE * 5, PACED_RATE));
            results.set("startupBurst", harness.startupBurst());
            results.set("restBurst", harness.rest());
            results.set("restSequential", harness.restSequential(50));
            results.set("shards", harness.shards());
            if (SOAK_SECONDS > 0) results.set("soak", harness.soak());
        } finally {
            harness.server.shutdown();
        }
        String json = Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results);
        System.out.println(json);
        String output = System.getProperty("load.output");
        if (output != null) { Files.createDirectories(Path.of(output).getParent()); Files.writeString(Path.of(output), json); }
        System.exit(0);
    }

    // ------------------------------------------------------------------ mock instance

    private Dispatcher dispatcher() {
        return new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.equals("/.well-known/fluxer"))
                    return json("{\"endpoints\":{\"api_public\":\"" + server.url("/api") + "\",\"media\":\"" + server.url("/media")
                            + "\",\"gateway\":\"" + server.url("/gateway").toString().replace("http:", "ws:") + "\"}}");
                if (path.startsWith("/gateway")) return new MockResponse().withWebSocketUpgrade(gateway());
                if (path.contains("/users/")) return json("{\"id\":\"7\",\"username\":\"load\",\"bot\":false}");
                return json("{}");
            }
        };
    }

    private WebSocketListener gateway() {
        return new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                sequences.put(ws, new AtomicLong(1));
                ws.send("{\"op\":10,\"d\":{\"heartbeat_interval\":5000}}");
            }
            @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(code, reason); }
            @Override public void onMessage(WebSocket ws, String text) {
                JsonNode frame = Json.parse(text);
                switch (frame.path("op").asInt()) {
                    case 1 -> ws.send("{\"op\":11}");
                    case 2 -> {
                        int shard = frame.path("d").path("shard").path(0).asInt(0);
                        ws.send("{\"op\":0,\"t\":\"READY\",\"s\":1,\"d\":{\"session_id\":\"load-" + shard + "\",\"user\":{\"id\":\"1\",\"username\":\"bot\",\"bot\":true},"
                                + "\"application\":{\"id\":\"42\",\"flags\":0},\"guilds\":" + readyGuilds + "}}");
                        socket = ws; identified.countDown();
                    }
                    case 6 -> {
                        // Replay retained dispatches after the client's sequence, then RESUMED, as Micup does.
                        long after = frame.path("d").path("seq").asLong();
                        var counter = new AtomicLong(after);
                        synchronized (replay) {
                            for (String dispatch : replay) {
                                long seq = Json.parse(dispatch).path("s").asLong();
                                if (seq > after) { ws.send(dispatch); counter.set(seq); }
                            }
                            sequences.put(ws, counter);
                            ws.send("{\"op\":0,\"t\":\"RESUMED\",\"s\":" + counter.get() + ",\"d\":{}}");
                            socket = ws; identified.countDown();
                        }
                    }
                    default -> { }
                }
            }
        };
    }

    private static MockResponse json(String body) { return new MockResponse().setHeader("Content-Type", "application/json").setBody(body); }

    /** Sends one dispatch, applying back-pressure when okhttp's outgoing queue grows. */
    private void dispatch(WebSocket ws, String type, String data, boolean retain) throws InterruptedException {
        while (ws.queueSize() > 4L * 1024 * 1024) TimeUnit.MICROSECONDS.sleep(200);
        long seq = sequences.get(ws).incrementAndGet();
        String frame = "{\"op\":0,\"t\":\"" + type + "\",\"s\":" + seq + ",\"d\":" + data + "}";
        if (retain) synchronized (replay) { replay.add(frame); if (replay.size() > 20_000) replay.subList(0, 10_000).clear(); }
        ws.send(frame);
    }

    private MicupBuilder client(boolean light) {
        var builder = light ? MicupBuilder.createLight("load.token") : MicupBuilder.createDefault("load.token");
        return builder.setInstanceOrigin(server.url("/").uri()).setQueueCapacity(4096, 65_536)
                .setGatewayConfig(new GatewayConfig(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofMillis(100), Duration.ofSeconds(2)));
    }

    private Micup connect(MicupBuilder builder) throws Exception {
        identified = new CountDownLatch(1);
        Micup client = builder.build();
        client.awaitReady(Duration.ofSeconds(20));
        if (!identified.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Mock Gateway was not identified");
        return client;
    }

    // ------------------------------------------------------------------ workloads

    /** Sends {@code total} messages, as fast as possible when {@code rate} is 0, otherwise at {@code rate} per second. */
    private ObjectNode events(int total, int rate) throws Exception {
        long[] delays = new long[total];
        var count = new AtomicInteger();
        var done = new CountDownLatch(1);
        var client = connect(client(true).addEventListeners(new ListenerAdapter() {
            @Override public void onMessageReceived(MessageReceivedEvent event) {
                long now = System.nanoTime();
                int index = count.getAndIncrement();
                if (index < delays.length) delays[index] = now - Long.parseLong(event.getMessage().getContentRaw());
                if (index + 1 == delays.length) done.countDown();
            }
        }));
        Thread.sleep(200);
        long allocatedBefore = allocatedBytes();
        long start = System.nanoTime();
        long interval = rate == 0 ? 0 : 1_000_000_000L / rate;
        for (int i = 0; i < total; i++) {
            if (interval > 0) { long due = start + i * interval; while (System.nanoTime() < due) Thread.onSpinWait(); }
            dispatch(socket, "MESSAGE_CREATE", "{\"id\":\"" + (1000 + i) + "\",\"channel_id\":\"2\",\"content\":\"" + System.nanoTime()
                    + "\",\"author\":{\"id\":\"5\",\"username\":\"load\"}}", false);
        }
        boolean completed = done.await(120, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - start;
        long allocated = allocatedBytes() - allocatedBefore;
        client.shutdownNow();
        var result = Json.object().put("events", total).put("offeredRatePerSecond", rate == 0 ? "unbounded" : Integer.toString(rate)).put("delivered", count.get()).put("completed", completed)
                .put("seconds", elapsed / 1e9).put("eventsPerSecond", Math.round(count.get() / (elapsed / 1e9)));
        result.set("listenerDelayMicros", percentiles(Arrays.copyOf(delays, Math.min(count.get(), delays.length)), 1_000));
        result.put("allocatedBytesPerEvent", allocated < 0 ? -1 : allocated / Math.max(1, count.get()));
        return result;
    }

    private ObjectNode startupBurst() throws Exception {
        var ids = new ArrayList<String>();
        for (int i = 0; i < GUILDS; i++) ids.add(Long.toString(10_000_000L + i));
        readyGuilds = ids.stream().map(id -> "{\"id\":\"" + id + "\",\"unavailable\":true}").toList().toString();
        var ready = new CountDownLatch(GUILDS);
        gc();
        long heapBefore = heapUsed();
        var client = connect(client(false).setCachePolicy(new CachePolicy(GUILDS, 64, 64, 64)).addEventListeners(new ListenerAdapter() {
            @Override public void onGuildReady(GuildReadyEvent event) { ready.countDown(); }
        }));
        long start = System.nanoTime();
        for (String id : ids) dispatch(socket, "GUILD_CREATE", guild(id), false);
        boolean completed = ready.await(120, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - start;
        gc();
        long heapAfter = heapUsed();
        int cached = client.getGuilds().size();
        client.shutdownNow();
        readyGuilds = "[]";
        return Json.object().put("guilds", GUILDS).put("rolesChannelsMembersPerGuild", "8/12/20").put("completed", completed)
                .put("seconds", elapsed / 1e9).put("guildsPerSecond", Math.round(GUILDS / (elapsed / 1e9))).put("cachedGuilds", cached)
                .put("retainedHeapMiB", Math.round((heapAfter - heapBefore) / 1048576.0 * 10) / 10.0)
                .put("retainedBytesPerGuild", (heapAfter - heapBefore) / Math.max(1, cached));
    }

    private static String guild(String id) {
        var roles = new StringBuilder(); var channels = new StringBuilder(); var members = new StringBuilder();
        roles.append("{\"id\":\"").append(id).append("\",\"name\":\"@everyone\",\"position\":0,\"permissions\":\"1024\"}");
        for (int r = 1; r < 8; r++) roles.append(",{\"id\":\"").append(id).append(r).append("\",\"name\":\"Role ").append(r).append("\",\"position\":").append(r).append(",\"permissions\":\"3072\"}");
        for (int c = 0; c < 12; c++) channels.append(c == 0 ? "" : ",").append("{\"id\":\"").append(id).append("9").append(c).append("\",\"type\":0,\"name\":\"chat-").append(c).append("\",\"permission_overwrites\":[]}");
        for (int m = 0; m < 20; m++) members.append(m == 0 ? "" : ",").append("{\"user\":{\"id\":\"").append(100 + m).append("\",\"username\":\"user").append(m).append("\"},\"roles\":[\"").append(id).append("1\"]}");
        return "{\"id\":\"" + id + "\",\"properties\":{\"name\":\"Guild " + id + "\",\"owner_id\":\"100\"},\"roles\":[" + roles + "],\"channels\":[" + channels + "],\"members\":[" + members + "]}";
    }

    private ObjectNode rest() throws Exception {
        var client = connect(client(true));
        long[] latencies = new long[REQUESTS];
        var futures = new ArrayList<java.util.concurrent.CompletableFuture<?>>();
        long start = System.nanoTime();
        for (int i = 0; i < REQUESTS; i++) {
            int index = i; long submitted = System.nanoTime();
            futures.add(client.retrieveUserById(Long.toString(7 + i)).submit().whenComplete((user, error) -> latencies[index] = System.nanoTime() - submitted));
        }
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new)).get(5, TimeUnit.MINUTES);
        long elapsed = System.nanoTime() - start;
        client.shutdownNow();
        var result = Json.object().put("requests", REQUESTS).put("seconds", elapsed / 1e9).put("requestsPerSecond", Math.round(REQUESTS / (elapsed / 1e9)))
                .put("note", "Includes the scheduler's 20 ms global spacing (50 requests/s per client)");
        result.set("latencyMillis", percentiles(latencies, 1_000_000));
        return result;
    }

    private ObjectNode restSequential(int count) throws Exception {
        var client = connect(client(true));
        long[] latencies = new long[count];
        for (int i = 0; i < count; i++) {
            long submitted = System.nanoTime();
            client.retrieveUserById(Long.toString(7 + i)).submit().get(10, TimeUnit.SECONDS);
            latencies[i] = System.nanoTime() - submitted;
        }
        client.shutdownNow();
        // Baseline: the same exchange with a bare java.net.http client, isolating the mock transport's own latency.
        var raw = java.net.http.HttpClient.newHttpClient();
        long[] baseline = new long[count];
        for (int i = 0; i < count; i++) {
            long submitted = System.nanoTime();
            raw.send(java.net.http.HttpRequest.newBuilder(server.url("/api/v1/users/7").uri()).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            baseline[i] = System.nanoTime() - submitted;
        }
        var result = Json.object().put("requests", count)
                .put("note", "One request at a time; compare with rawHttpBaselineMillis, the bare HttpClient latency against the same mock");
        result.set("latencyMillis", percentiles(latencies, 1_000_000));
        result.set("rawHttpBaselineMillis", percentiles(baseline, 1_000_000));
        return result;
    }

    private ObjectNode shards() throws Exception {
        long start = System.nanoTime();
        var manager = DefaultShardManagerBuilder.createLight("load.token").setInstanceOrigin(server.url("/").uri())
                .setShardsTotal(SHARDS).setSessionStartGate(SessionStartGate.sequential(Duration.ZERO)).build();
        boolean ready = manager.awaitReady(Duration.ofMinutes(2));
        long elapsed = System.nanoTime() - start;
        manager.shutdown();
        boolean stopped = manager.awaitShutdown(Duration.ofSeconds(30));
        return Json.object().put("shards", SHARDS).put("ready", ready).put("startSeconds", elapsed / 1e9)
                .put("millisPerShard", elapsed / 1e6 / SHARDS).put("gracefulShutdown", stopped)
                .put("note", "Sequential start without spacing; production default spaces starts by 1 s");
    }

    private ObjectNode soak() throws Exception {
        var received = new LongAdder();
        var reconnects = new LongAdder();
        var client = connect(client(true).addEventListeners(new ListenerAdapter() {
            @Override public void onMessageReceived(MessageReceivedEvent event) { received.increment(); }
            @Override public void onReconnect(com.swadedev.micup4j.events.ReconnectEvent event) { reconnects.increment(); }
        }));
        gc();
        long heapStart = heapUsed();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(SOAK_SECONDS);
        long sent = 0, nextDrop = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            WebSocket ws = socket;
            if (System.nanoTime() >= nextDrop) {
                identified = new CountDownLatch(1);
                // Interruption with a resumable close (4000): the client reconnects, resumes, and the mock replays missed dispatches.
                ws.close(4000, "load interruption");
                if (!identified.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Client did not resume");
                nextDrop = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                continue;
            }
            dispatch(ws, "MESSAGE_CREATE", "{\"id\":\"" + (5000 + sent) + "\",\"channel_id\":\"2\",\"content\":\"0\",\"author\":{\"id\":\"5\",\"username\":\"load\"}}", true);
            sent++;
            TimeUnit.MICROSECONDS.sleep(2_000); // About 500 events per second.
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (received.sum() < sent && System.nanoTime() < deadline) Thread.sleep(20);
        gc();
        long heapEnd = heapUsed();
        var status = client.getStatus();
        client.shutdownNow();
        return Json.object().put("seconds", SOAK_SECONDS).put("sent", sent).put("received", received.sum())
                .put("lost", sent - received.sum()).put("reconnects", reconnects.sum()).put("finalStatus", status.name())
                .put("heapDriftMiB", Math.round((heapEnd - heapStart) / 1048576.0 * 10) / 10.0);
    }

    // ------------------------------------------------------------------ measurement helpers

    private static ObjectNode percentiles(long[] values, long divisor) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        var node = Json.object();
        if (sorted.length == 0) return node;
        for (double p : new double[]{50, 95, 99, 99.9}) {
            int index = (int) Math.min(sorted.length - 1, Math.ceil(p / 100 * sorted.length) - 1);
            node.put("p" + (p == 99.9 ? "999" : Integer.toString((int) p)), Math.round(sorted[Math.max(0, index)] / (double) divisor * 10) / 10.0);
        }
        node.put("max", Math.round(sorted[sorted.length - 1] / (double) divisor * 10) / 10.0);
        return node;
    }

    private static long allocatedBytes() {
        // Counts platform threads; allocations on virtual threads are not attributed and are therefore excluded.
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()) {
            long total = 0;
            for (long id : bean.getAllThreadIds()) { long bytes = bean.getThreadAllocatedBytes(id); if (bytes > 0) total += bytes; }
            return total;
        }
        return -1;
    }

    private static long heapUsed() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }

    private static void gc() throws InterruptedException {
        for (int i = 0; i < 3; i++) { System.gc(); Thread.sleep(100); }
    }
}
