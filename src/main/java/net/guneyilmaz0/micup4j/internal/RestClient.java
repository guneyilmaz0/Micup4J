package net.guneyilmaz0.micup4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.MicupVersion;
import net.guneyilmaz0.micup4j.config.RestConfig;
import net.guneyilmaz0.micup4j.diagnostics.RestMetrics;
import net.guneyilmaz0.micup4j.requests.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Bounded deadline-aware scheduler. No worker sleeps while holding a route lock. */
public final class RestClient implements AutoCloseable {
    private final HttpClient http;
    private final String token;
    private final int capacity;
    private final RestConfig config;
    private final Object lock = new Object();
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon().name("micup4j-rest-scheduler").factory());
    private final ExecutorService completions = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Job> waiting = new ArrayList<>();
    private final Set<Job> jobs = new HashSet<>();
    private final LinkedHashMap<String, Bucket> routes = new LinkedHashMap<>(16, .75f, true);
    private final LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>(16, .75f, true);
    private ScheduledFuture<?> wake;
    private long nextGlobal = System.nanoTime(), globalBlockedUntil = System.nanoTime(), order;
    private int normalCount, callbackCount, inFlight, normalInFlight;
    private long submitted, completed, failed, cancelled, retries, latencyCount, latencyNanos, lastLatency;
    private volatile boolean closed;
    private volatile String base;

    private static final class Bucket {
        Bucket parent;
        long next = System.nanoTime();
        int busy;
        Bucket root() {
            Bucket root = this;
            while (root.parent != null) root = root.parent;
            Bucket current = this;
            while (current.parent != null) {
                Bucket next = current.parent; current.parent = root; current = next;
            }
            return root;
        }
    }
    private static final class Job {
        final String method, path, payload, route, major;
        final boolean auth, priority;
        final long deadline, order;
        final CompletableFuture<JsonNode> result = new CompletableFuture<>();
        Bucket bucket;
        CompletableFuture<HttpResponse<String>> flight;
        ScheduledFuture<?> expiry;
        long started;
        int attempts;
        boolean running, done;
        Job(String method, String path, String payload, boolean auth, boolean priority, long deadline, long order) {
            this.method = method; this.path = path; this.payload = payload; this.auth = auth;
            this.priority = priority; this.deadline = deadline; this.order = order;
            String[] keys = routeKeys(method, path, auth);
            route = keys[0]; major = keys[1];
        }
    }
    public RestClient(HttpClient http, String token, int capacity) { this(http, token, capacity, RestConfig.defaults()); }
    public RestClient(HttpClient http, String token, int capacity, RestConfig config) {
        if (capacity < 1) throw new IllegalArgumentException("REST queue capacity must be positive");
        this.http = Objects.requireNonNull(http); this.token = Objects.requireNonNull(token);
        this.capacity = capacity; this.config = Objects.requireNonNull(config);
        scheduler.setRemoveOnCancelPolicy(true);
    }
    public void setBase(URI base) { requireEndpoint(base, false); this.base = base.toString().replaceAll("/+$", ""); }
    public static void requireEndpoint(URI uri, boolean websocket) {
        Objects.requireNonNull(uri);
        boolean secure = (websocket ? "wss" : "https").equals(uri.getScheme());
        boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(Objects.toString(uri.getHost(), ""));
        boolean localPlain = local && (websocket ? "ws" : "http").equals(uri.getScheme());
        if ((!secure && !localPlain) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Endpoint must use TLS (plain HTTP/WS allowed only on loopback), without credentials, query or fragment");
    }
    public RestAction<JsonNode> request(String method, String path, JsonNode body) { return request(method, path, body, true); }
    public RestAction<JsonNode> request(String method, String path, JsonNode body, boolean auth) {
        return action(method, path, body, auth, false, Long.MAX_VALUE);
    }
    /** Initial interaction callbacks have separate bounded admission and a monotonic local deadline. */
    public RestAction<JsonNode> requestCallback(String path, JsonNode body, long deadlineNanos) {
        return action("POST", path, body, false, true, deadlineNanos);
    }
    private RestAction<JsonNode> action(String method, String path, JsonNode body, boolean auth, boolean priority, long deadline) {
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD").contains(method)) throw new IllegalArgumentException("Invalid HTTP method");
        try {
            if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("..") || path.contains("#") || path.contains("\\")) throw new IllegalArgumentException();
            URI.create("/v1" + path);
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid API path"); }
        String payload = body == null ? null : body.toString();
        return new RestAction<>(() -> enqueue(method, path, payload, auth, priority, deadline));
    }
    private CompletableFuture<JsonNode> enqueue(String method, String path, String payload, boolean auth, boolean priority, long deadline) {
        Job job;
        synchronized (lock) {
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Client is closed"));
            if (base == null) return CompletableFuture.failedFuture(new IllegalStateException("Discovery has not completed"));
            if (priority ? callbackCount >= config.callbackCapacity() : normalCount >= capacity)
                return CompletableFuture.failedFuture(new RejectedExecutionException("REST queue is full"));
            long effective = Math.min(deadline, System.nanoTime() + config.totalTimeout().toNanos());
            if (effective <= System.nanoTime()) return CompletableFuture.failedFuture(new TimeoutException("Request deadline expired before submission"));
            job = new Job(method, path, payload, auth, priority, effective, ++order);
            if (priority) callbackCount++; else normalCount++;
            jobs.add(job); waiting.add(job); submitted++;
            job.expiry = scheduler.schedule(() -> expire(job), Math.max(0, effective - System.nanoTime()), TimeUnit.NANOSECONDS);
            schedule(0);
        }
        job.result.whenComplete((value, error) -> { if (job.result.isCancelled()) cancel(job); });
        return job.result;
    }
    // All scheduling and bucket state is protected by lock, including response-driven changes.
    private void schedule(long delay) {
        if (closed) return;
        if (wake != null) wake.cancel(false);
        wake = scheduler.schedule(this::pump, Math.max(0, delay), TimeUnit.NANOSECONDS);
    }
    private void pump() {
        Job selected = null;
        List<Job> rejected = new ArrayList<>();
        synchronized (lock) {
            wake = null;
            if (closed || inFlight >= config.maxConcurrency()) return;
            long now = System.nanoTime();
            long global = Math.max(nextGlobal, globalBlockedUntil);
            if (global > now) { schedule(global - now); return; }
            long next = Long.MAX_VALUE;
            for (Job job : waiting) {
                if (job.done || job.deadline <= now) continue; // Per-job expiry timer owns completion.
                if (!job.priority && normalInFlight >= Math.max(1, config.maxConcurrency() - 1)) continue;
                Bucket bucket = routes.get(job.route);
                if (bucket == null) {
                    if (!makeRoom(routes, now)) { rejected.add(job); continue; }
                    bucket = new Bucket(); routes.put(job.route, bucket);
                }
                job.bucket = bucket.root();
                if (job.bucket.busy != 0) continue;
                if (job.bucket.next > now) { next = Math.min(next, job.bucket.next); continue; }
                if (selected == null || (job.priority && !selected.priority)
                        || (job.priority == selected.priority && (job.priority ? job.deadline < selected.deadline : job.order < selected.order))) selected = job;
            }
            for (Job job : rejected) finishLocked(job, false, false);
            if (selected != null) {
                waiting.remove(selected); selected.running = true; selected.bucket.busy++;
                inFlight++; if (!selected.priority) normalInFlight++;
                nextGlobal = now + 20_000_000L;
                selected.started = now; selected.attempts++;
                schedule(20_000_000L);
            } else if (next != Long.MAX_VALUE) schedule(next - now);
        }
        for (Job job : rejected) complete(job, null, new RejectedExecutionException("Rate limit cache is full"));
        if (selected != null) send(selected);
    }
    private void send(Job job) {
        CompletableFuture<HttpResponse<String>> flight;
        synchronized (lock) { if (job.done || closed) return; }
        try {
            long remaining = job.deadline - System.nanoTime();
            if (remaining <= 0) { expire(job); return; }
            var builder = HttpRequest.newBuilder(URI.create(base + "/v1" + job.path))
                    .timeout(Duration.ofNanos(Math.min(remaining, config.requestTimeout().toNanos())))
                    .header("Accept", "application/json").header("User-Agent", MicupVersion.getUserAgent());
            if (job.auth) builder.header("Authorization", "Bot " + token);
            if (job.payload != null) builder.header("Content-Type", "application/json");
            flight = http.sendAsync(builder.method(job.method, job.payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(job.payload)).build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception error) { failed(job, new IllegalStateException("Could not start Micup request")); return; }
        synchronized (lock) {
            if (job.done || closed) { flight.cancel(true); return; }
            job.flight = flight;
        }
        flight.whenComplete((response, error) -> response(job, response, error));
    }
    private void response(Job job, HttpResponse<String> response, Throwable error) {
        JsonNode data = null;
        Throwable failure = null;
        if (error != null) {
            Throwable cause = error instanceof CompletionException ? error.getCause() : error;
            failure = cause instanceof HttpTimeoutException ? new TimeoutException("Micup HTTP attempt timed out")
                    : new IllegalStateException("Micup transport failed; request outcome may be unknown");
        } else {
            try { data = Json.parse(response.body()); }
            catch (IllegalArgumentException invalid) { failure = new ApiException(response.statusCode(), "INVALID_RESPONSE"); }
        }
        synchronized (lock) {
            if (job.done) return;
            long elapsed = System.nanoTime() - job.started;
            latencyNanos += elapsed; latencyCount++; lastLatency = elapsed;
            if (failure == null) {
                try {
                    Bucket bucket = learnBucket(job, response);
                    long now = System.nanoTime();
                    if (response.headers().firstValue("X-RateLimit-Remaining").orElse("").equals("0"))
                        bucket.next = Math.max(bucket.next, now + delayNanos(response.headers().firstValue("X-RateLimit-Reset-After").orElse("1")));
                    if (response.statusCode() == 429) {
                        double seconds = data.path("retry_after").asDouble(-1);
                        long delay = delayNanos(seconds >= 0 ? Double.toString(seconds) : response.headers().firstValue("Retry-After").orElse("1"));
                        bucket.next = Math.max(bucket.next, now + delay);
                        if (data.path("global").asBoolean() || response.headers().firstValue("X-RateLimit-Global").orElse("").equals("true"))
                            globalBlockedUntil = Math.max(globalBlockedUntil, now + delay);
                        if (job.attempts <= config.maxRetries()) {
                            releaseFlight(job); waiting.add(job); retries++; schedule(0); return;
                        }
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300)
                        failure = new ApiException(response.statusCode(), safeCode(data.path("code").asText("UNKNOWN")));
                } catch (Exception invalid) { failure = new IllegalStateException("Invalid rate limit response"); }
            }
            finishLocked(job, failure == null, false); schedule(0);
        }
        complete(job, data, failure);
    }
    private Bucket learnBucket(Job job, HttpResponse<?> response) {
        Bucket current = job.bucket.root();
        String id = response.headers().firstValue("X-RateLimit-Bucket").orElse("");
        if (!id.matches("[a-zA-Z0-9_-]{1,128}")) return current;
        String key = id + "#" + job.major;
        Bucket known = buckets.get(key);
        if (known == null) {
            if (makeRoom(buckets, System.nanoTime())) buckets.put(key, current);
        } else if ((known = known.root()) != current) {
            known.next = Math.max(known.next, current.next); known.busy += current.busy;
            current.parent = known; current = known;
        }
        return current;
    }
    private boolean makeRoom(LinkedHashMap<String, Bucket> map, long now) {
        if (map.size() < config.bucketCacheSize()) return true;
        var entries = map.entrySet().iterator();
        while (entries.hasNext()) {
            Bucket value = entries.next().getValue().root();
            if (value.busy == 0 && value.next <= now) { entries.remove(); return true; }
        }
        return false;
    }
    private void releaseFlight(Job job) {
        if (!job.running) return;
        job.running = false; inFlight--; if (!job.priority) normalInFlight--;
        job.bucket.root().busy--; job.flight = null;
    }
    private boolean finishLocked(Job job, boolean success, boolean cancellation) {
        if (job.done) return false;
        job.done = true; releaseFlight(job); waiting.remove(job); jobs.remove(job);
        if (job.priority) callbackCount--; else normalCount--;
        if (job.expiry != null) job.expiry.cancel(false);
        if (cancellation) cancelled++; else if (success) completed++; else failed++;
        return true;
    }
    private void complete(Job job, JsonNode value, Throwable error) {
        Runnable completion = () -> { if (error == null) job.result.complete(value); else job.result.completeExceptionally(error); };
        try { completions.execute(completion); } catch (RejectedExecutionException closed) { completion.run(); }
    }
    private void failed(Job job, Throwable error) {
        synchronized (lock) { if (!finishLocked(job, false, false)) return; schedule(0); }
        complete(job, null, error);
    }
    private void expire(Job job) {
        CompletableFuture<?> flight;
        synchronized (lock) {
            flight = job.flight;
            if (!finishLocked(job, false, false)) return;
            schedule(0);
        }
        if (flight != null) flight.cancel(true);
        complete(job, null, new TimeoutException("Request total deadline expired"));
    }
    private void cancel(Job job) {
        CompletableFuture<?> flight;
        synchronized (lock) {
            flight = job.flight;
            if (!finishLocked(job, false, true)) return;
            schedule(0);
        }
        if (flight != null) flight.cancel(true);
    }
    public RestMetrics getMetrics() {
        synchronized (lock) {
            return new RestMetrics(waiting.size(), inFlight, capacity, config.callbackCapacity(), submitted, completed, failed,
                    cancelled, retries, TimeUnit.NANOSECONDS.toMillis(lastLatency), latencyCount == 0 ? 0 : latencyNanos / (double)latencyCount / 1_000_000,
                    routes.size(), buckets.size());
        }
    }
    private static String safeCode(String code) { return code.matches("[A-Z0-9_]{1,100}") ? code : "UNKNOWN"; }
    private static long delayNanos(String text) {
        try {
            double seconds = Double.parseDouble(text);
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 3600) throw new NumberFormatException();
            return (long)(Math.max(.001, seconds) * 1_000_000_000L);
        } catch (NumberFormatException error) { throw new IllegalStateException("Invalid rate limit delay"); }
    }
    private static String[] routeKeys(String method, String path, boolean auth) {
        String[] segments = path.split("\\?", 2)[0].split("/");
        StringBuilder template = new StringBuilder();
        StringBuilder major = new StringBuilder(auth ? "bot" : "anonymous");
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            boolean secret = i >= 3 && (segments[1].equals("webhooks") || segments[1].equals("interactions")) && i == 3;
            if (secret) { template.append("/:token"); major.append('/').append(digest(segment)); continue; }
            if (segment.matches("[0-9]+")) {
                template.append("/:id");
                if (Set.of("channels", "guilds", "webhooks", "interactions", "applications", "users").contains(segments[i-1])) major.append('/').append(segments[i-1]).append(':').append(segment);
            } else template.append('/').append(segment);
        }
        String resource = major.toString();
        return new String[]{method + " " + template + "#" + resource, resource};
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }
    @Override public void close() {
        List<Job> cancelledJobs;
        List<CompletableFuture<?>> flights = new ArrayList<>();
        synchronized (lock) {
            if (closed) return; closed = true;
            if (wake != null) wake.cancel(false);
            cancelledJobs = new ArrayList<>(jobs);
            for (Job job : cancelledJobs) { if (job.flight != null) flights.add(job.flight); finishLocked(job, false, false); }
            routes.clear(); buckets.clear(); scheduler.shutdownNow();
        }
        flights.forEach(flight -> flight.cancel(true));
        for (Job job : cancelledJobs) complete(job, null, new IllegalStateException("Client closed"));
        completions.shutdown();
    }
}
