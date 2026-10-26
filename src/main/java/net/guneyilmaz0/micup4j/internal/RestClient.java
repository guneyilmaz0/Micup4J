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
            ThreadGuards.guarded(Thread.ofPlatform().daemon().name("micup4j-rest-scheduler").factory()));
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
    /** Set by {@link #shutdown(Duration)}: new work is refused while admitted work keeps running. Guarded by lock. */
    private volatile boolean draining;
    /** Completes once no admitted job remains after a drain, or when the client is closed. */
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private ScheduledFuture<?> drainDeadline;
    /**
     * Completions announced by {@link #finishLocked} whose result future has not been completed yet. Guarded by lock.
     * A drain ends only when both {@link #jobs} is empty and this is zero, so the last callback always runs on a
     * completion thread before {@link #drained} completes and before {@link #completions} is shut down.
     */
    private int pendingCompletions;
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
        final Map<String, String> headers;
        final byte[] raw;
        final String rawType;
        final boolean auth, priority;
        final long deadline, order;
        final CompletableFuture<JsonNode> result = new CompletableFuture<>();
        Bucket bucket;
        CompletableFuture<HttpResponse<String>> flight;
        ScheduledFuture<?> expiry;
        long started;
        int attempts;
        boolean running, done;
        Job(String method, String path, String payload, byte[] raw, String rawType, Map<String, String> headers, boolean auth, boolean priority, long deadline, long order) {
            this.method = method; this.path = path; this.payload = payload; this.raw = raw; this.rawType = rawType; this.headers = headers; this.auth = auth;
            this.priority = priority; this.deadline = deadline; this.order = order;
            String[] keys = routeKeys(method, path, auth);
            route = keys[0]; major = keys[1];
        }
    }
    /**
     * Creates a client with the {@linkplain RestConfig#defaults() default policy}.
     *
     * @param http     the HTTP client used for every request
     * @param token    the bot token; sent only in the {@code Authorization} header
     * @param capacity the maximum number of queued requests, at least 1
     * @throws NullPointerException if {@code http} or {@code token} is {@code null}
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public RestClient(HttpClient http, String token, int capacity) { this(http, token, capacity, RestConfig.defaults()); }
    /**
     * Creates a client.
     *
     * @param http     the HTTP client used for every request
     * @param token    the bot token; sent only in the {@code Authorization} header
     * @param capacity the maximum number of queued requests, at least 1
     * @param config   the request policy
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public RestClient(HttpClient http, String token, int capacity, RestConfig config) {
        if (capacity < 1) throw new IllegalArgumentException("REST queue capacity must be positive");
        this.http = Objects.requireNonNull(http); this.token = Objects.requireNonNull(token);
        this.capacity = capacity; this.config = Objects.requireNonNull(config);
        scheduler.setRemoveOnCancelPolicy(true);
    }
    /**
     * Sets the API base discovered from the instance. Requests queued before this is set fail with an
     * {@link IllegalStateException}.
     *
     * @param base the {@code api_public} endpoint; see {@link #requireEndpoint(URI, boolean)}
     * @throws IllegalArgumentException if the endpoint is not acceptable
     */
    public void setBase(URI base) { requireEndpoint(base, false); this.base = base.toString().replaceAll("/+$", ""); }
    /**
     * Validates an instance endpoint: TLS is required, and plain HTTP or WS is allowed only on loopback hosts. Credentials,
     * a query and a fragment are rejected.
     *
     * @param uri       the endpoint
     * @param websocket {@code true} to require {@code wss} (or {@code ws} on loopback), {@code false} for {@code https}
     * @throws NullPointerException if {@code uri} is {@code null}
     * @throws IllegalArgumentException if the endpoint is not acceptable
     */
    public static void requireEndpoint(URI uri, boolean websocket) {
        Objects.requireNonNull(uri);
        boolean secure = (websocket ? "wss" : "https").equals(uri.getScheme());
        boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(Objects.toString(uri.getHost(), ""));
        boolean localPlain = local && (websocket ? "ws" : "http").equals(uri.getScheme());
        if ((!secure && !localPlain) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Endpoint must use TLS (plain HTTP/WS allowed only on loopback), without credentials, query or fragment");
    }
    /**
     * Creates an authenticated request. Nothing is sent until the action is executed.
     *
     * @param method the HTTP method
     * @param path   the route below {@code /v1}, starting with a single slash
     * @param body   the JSON body, or {@code null}
     * @return the lazy request; it completes with the parsed JSON response, or fails with
     *         {@link net.guneyilmaz0.micup4j.requests.ApiException} for a non-2xx status
     * @throws IllegalArgumentException if the method or path is invalid
     */
    public RestAction<JsonNode> request(String method, String path, JsonNode body) { return request(method, path, body, true); }
    /**
     * Creates a request. Nothing is sent until the action is executed.
     *
     * <p>When executed, the action fails with a {@link java.util.concurrent.RejectedExecutionException} if the queue is full
     * or the client is shutting down, and with an {@link IllegalStateException} if discovery has not completed or the client
     * is closed. Cancelling the future cancels the request, including an exchange already in flight.
     *
     * @param method the HTTP method
     * @param path   the route below {@code /v1}, starting with a single slash
     * @param body   the JSON body, or {@code null}
     * @param auth   whether to send the bot {@code Authorization} header; {@code false} for token-in-path routes
     * @return the lazy request
     * @throws IllegalArgumentException if the method or path is invalid
     */
    public RestAction<JsonNode> request(String method, String path, JsonNode body, boolean auth) {
        return action(method, path, body, Map.of(), auth, false, Long.MAX_VALUE);
    }
    /**
     * Creates an authenticated request with extra headers, such as {@code X-Audit-Log-Reason}. Nothing is sent until the
     * action is executed.
     *
     * @param method  the HTTP method
     * @param path    the route below {@code /v1}, starting with a single slash
     * @param body    the JSON body, or {@code null}
     * @param headers extra request headers, copied; they cannot replace the standard or authentication headers
     * @return the lazy request
     * @throws IllegalArgumentException if the method, path or a header is invalid
     */
    public RestAction<JsonNode> request(String method, String path, JsonNode body, Map<String, String> headers) {
        return action(method, path, body, checkedHeaders(headers), true, false, Long.MAX_VALUE);
    }
    private static Map<String, String> checkedHeaders(Map<String, String> headers) {
        var copy = Map.copyOf(headers);
        for (var entry : copy.entrySet()) {
            String name = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!name.matches("x-[a-z0-9-]+")) throw new IllegalArgumentException("Only X- extension headers may be added");
            if (!entry.getValue().chars().allMatch(c -> c == '\t' || (c >= 0x20 && c < 0x7F)))
                throw new IllegalArgumentException("Header values must be printable ASCII");
        }
        return copy;
    }
    /** Initial interaction callbacks have separate bounded admission and a monotonic local deadline. */
    public RestAction<JsonNode> requestCallback(String path, JsonNode body, long deadlineNanos) {
        return action("POST", path, body, Map.of(), false, true, deadlineNanos);
    }
    /**
     * Creates an authenticated {@code multipart/form-data} request with a {@code payload_json} part and one
     * {@code files[N]} part per file. Nothing is sent until the action is executed; the encoded body is kept in memory so a
     * rate-limited attempt can be retried.
     *
     * @param method the HTTP method
     * @param path   the route below {@code /v1}
     * @param json   the JSON payload
     * @param files  the files, in index order
     * @return the lazy request
     * @throws IllegalArgumentException if the method or path is invalid
     */
    public RestAction<JsonNode> requestMultipart(String method, String path, JsonNode json, List<net.guneyilmaz0.micup4j.utils.FileUpload> files) {
        String boundary = "micup4j" + java.util.UUID.randomUUID().toString().replace("-", "");
        var out = new java.io.ByteArrayOutputStream();
        java.util.function.Consumer<String> text = value -> out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        text.accept("--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n");
        text.accept(json.toString());
        for (int i = 0; i < files.size(); i++) {
            var file = files.get(i);
            String name = file.getName().replace("\\", "_").replace("\"", "_");
            text.accept("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[" + i + "]\"; filename=\"" + name
                    + "\"\r\nContent-Type: " + file.getContentType() + "\r\n\r\n");
            out.writeBytes(file.rawData());
        }
        text.accept("\r\n--" + boundary + "--\r\n");
        return action(method, path, null, out.toByteArray(), "multipart/form-data; boundary=" + boundary, Map.of(), true, false, Long.MAX_VALUE);
    }
    private RestAction<JsonNode> action(String method, String path, JsonNode body, Map<String, String> headers, boolean auth, boolean priority, long deadline) {
        return action(method, path, body, null, null, headers, auth, priority, deadline);
    }
    private RestAction<JsonNode> action(String method, String path, JsonNode body, byte[] raw, String rawType, Map<String, String> headers, boolean auth, boolean priority, long deadline) {
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD").contains(method)) throw new IllegalArgumentException("Invalid HTTP method");
        try {
            if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("..") || path.contains("#") || path.contains("\\")) throw new IllegalArgumentException();
            URI.create("/v1" + path);
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid API path"); }
        String payload = body == null ? null : body.toString();
        return new RestAction<>(() -> enqueue(method, path, payload, raw, rawType, headers, auth, priority, deadline));
    }
    private CompletableFuture<JsonNode> enqueue(String method, String path, String payload, byte[] raw, String rawType, Map<String, String> headers, boolean auth, boolean priority, long deadline) {
        Job job;
        synchronized (lock) {
            if (draining) return CompletableFuture.failedFuture(new RejectedExecutionException("Client is shutting down"));
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Client is closed"));
            if (base == null) return CompletableFuture.failedFuture(new IllegalStateException("Discovery has not completed"));
            if (priority ? callbackCount >= config.callbackCapacity() : normalCount >= capacity)
                return CompletableFuture.failedFuture(new RejectedExecutionException("REST queue is full"));
            long effective = Math.min(deadline, System.nanoTime() + config.totalTimeout().toNanos());
            if (effective <= System.nanoTime()) return CompletableFuture.failedFuture(new TimeoutException("Request deadline expired before submission"));
            job = new Job(method, path, payload, raw, rawType, headers, auth, priority, effective, ++order);
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
                // Keep one concurrency slot free so an initial interaction callback never waits behind ordinary requests.
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
                // Space request starts by 20 ms so a burst cannot trip the global rate limit before any response headers are seen.
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
            if (job.raw != null) builder.header("Content-Type", job.rawType);
            job.headers.forEach(builder::header);
            flight = http.sendAsync(builder.method(job.method, job.raw != null ? HttpRequest.BodyPublishers.ofByteArray(job.raw) : job.payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(job.payload)).build(), HttpResponse.BodyHandlers.ofString());
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
                        // Only HTTP 429 is retried; the job re-enters the queue and waits for the bucket (or global) delay instead of sleeping here.
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
        // Every non-cancelling finish is followed by complete(job, ...) outside lock. Counting it here, under the same lock
        // that empties jobs, means no drain can end (and shut down completions) in the gap before that completion is submitted.
        if (!cancellation) pendingCompletions++;
        return true;
    }
    /** Whether a drain may end now: no admitted job and no announced completion remain. Caller holds lock. */
    private boolean drainReadyLocked() { return draining && !closed && jobs.isEmpty() && pendingCompletions == 0; }
    /** Must follow a successful non-cancelling {@link #finishLocked} call, outside lock. */
    private void complete(Job job, JsonNode value, Throwable error) {
        Runnable completion = () -> {
            try { if (error == null) job.result.complete(value); else job.result.completeExceptionally(error); }
            finally { completionDone(); }
        };
        // Rejection only happens after a hard close(); a drain never shuts completions down while a completion is pending.
        try { completions.execute(completion); } catch (RejectedExecutionException closed) { completion.run(); }
    }
    /** Runs on the completion thread after the job's callbacks; the last one of a drain ends it from here. */
    private void completionDone() {
        boolean ready;
        synchronized (lock) { pendingCompletions--; ready = drainReadyLocked(); }
        if (ready) finishDrain();
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
        boolean ready;
        synchronized (lock) {
            flight = job.flight;
            if (!finishLocked(job, false, true)) return;
            schedule(0);
            ready = drainReadyLocked();
        }
        if (flight != null) flight.cancel(true);
        // A cancelled job has no completion to wait for; end the drain off the cancelling thread.
        if (ready) {
            try { completions.execute(this::finishDrain); } catch (RejectedExecutionException closed) { finishDrain(); }
        }
    }
    /**
     * Returns a point-in-time snapshot of the queue and rate-limit counters.
     *
     * @return the metrics
     */
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
            // Token path segments are keyed by digest, so rate-limit state never retains or exposes a raw interaction token.
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
    /**
     * Starts a graceful drain: new requests are refused with {@link RejectedExecutionException}, while waiting and
     * in-flight requests, including HTTP 429 retries, continue until done. Whatever remains when
     * {@code drainTimeout} elapses is cancelled through {@link #close()}. Idempotent and non-blocking.
     *
     * @param drainTimeout the maximum drain duration
     * @return a future that completes once the drain ends or the client is closed; it never completes exceptionally
     */
    public CompletableFuture<Void> shutdown(Duration drainTimeout) {
        RestConfig.positive(drainTimeout);
        boolean idle;
        synchronized (lock) {
            if (closed || draining) return drained.copy();
            draining = true;
            idle = drainReadyLocked();
            if (!idle) drainDeadline = scheduler.schedule(this::close, drainTimeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        if (idle) finishDrain();
        return drained.copy();
    }

    /**
     * Returns whether a drain was started or the client was closed. New requests are refused in both cases.
     *
     * @return {@code true} after {@link #shutdown(Duration)} or {@link #close()}
     */
    public boolean isShutdown() { return draining || closed; }

    /**
     * Ends a drain whose admitted jobs have all finished and whose completions have all run. Never runs under lock.
     * Idempotent: only the first caller that observes the drain as ready ends it.
     */
    private void finishDrain() {
        synchronized (lock) {
            if (!drainReadyLocked()) return;
            closed = true;
            if (wake != null) wake.cancel(false);
            if (drainDeadline != null) drainDeadline.cancel(false);
            routes.clear(); buckets.clear(); scheduler.shutdown();
        }
        completions.shutdown();
        drained.complete(null);
    }

    /** Hard close: cancels every waiting and in-flight request, and ends a running drain immediately. */
    @Override public void close() {
        List<Job> cancelledJobs;
        List<CompletableFuture<?>> flights = new ArrayList<>();
        synchronized (lock) {
            // closed is set before the jobs are finished: finishLocked must not start a second drain completion.
            if (closed) return; closed = true;
            if (wake != null) wake.cancel(false);
            if (drainDeadline != null) drainDeadline.cancel(false);
            cancelledJobs = new ArrayList<>(jobs);
            for (Job job : cancelledJobs) { if (job.flight != null) flights.add(job.flight); finishLocked(job, false, false); }
            routes.clear(); buckets.clear(); scheduler.shutdownNow();
        }
        flights.forEach(flight -> flight.cancel(true));
        for (Job job : cancelledJobs) complete(job, null, new IllegalStateException("Client closed"));
        completions.shutdown();
        drained.complete(null);
    }
}
