package net.guneyilmaz0.micup4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.requests.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/** Bounded admission, virtual-thread I/O and resource-specific route queues. */
public final class RestClient implements AutoCloseable {
    private final HttpClient http;
    private final String token;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore admission;
    private final ConcurrentHashMap<String, CompletableFuture<?>> pending = new ConcurrentHashMap<>();
    // Fixed stripes keep route scheduling memory bounded even with many channel IDs.
    private final Gate[] routes = new Gate[256];
    private final Object globalLock = new Object();
    private long nextGlobal;
    private volatile boolean closed;
    private volatile String base;

    private static final class Gate {
        final ReentrantLock lock = new ReentrantLock(true);
        long next;
    }

    public RestClient(HttpClient http, String token, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("REST queue capacity must be positive");
        this.http = Objects.requireNonNull(http);
        this.token = Objects.requireNonNull(token);
        this.admission = new Semaphore(capacity);
        Arrays.setAll(routes, i -> new Gate());
    }

    public void setBase(URI base) {
        requireEndpoint(base, false);
        this.base = base.toString().replaceAll("/+$", "");
    }

    public static void requireEndpoint(URI uri, boolean websocket) {
        String scheme = uri.getScheme();
        boolean secure = (websocket ? "wss" : "https").equals(scheme);
        boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(Objects.toString(uri.getHost(), ""));
        boolean localPlain = local && (websocket ? "ws" : "http").equals(scheme);
        if ((!secure && !localPlain) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Endpoint must use TLS (plain HTTP/WS allowed only on loopback), without credentials, query or fragment");
    }

    public RestAction<JsonNode> request(String method, String path, JsonNode body) {
        return request(method, path, body, true);
    }

    public RestAction<JsonNode> request(String method, String path, JsonNode body, boolean auth) {
        if (!path.startsWith("/") || path.startsWith("//") || path.contains(".."))
            throw new IllegalArgumentException("Invalid API path");
        String payload = body == null ? null : body.toString();
        return new RestAction<>(() -> {
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Client is closed"));
            if (base == null)
                return CompletableFuture.failedFuture(new IllegalStateException("Discovery has not completed"));
            if (!admission.tryAcquire())
                return CompletableFuture.failedFuture(new RejectedExecutionException("REST queue is full"));
            CompletableFuture<JsonNode> result = new CompletableFuture<>();
            String key = UUID.randomUUID().toString();
            pending.put(key, result);
            try {
                workers.execute(() -> {
                    try {
                        if (!result.isCancelled()) result.complete(execute(method, path, payload, auth));
                    } catch (Exception e) {
                        result.completeExceptionally(e);
                    } finally {
                        pending.remove(key);
                        admission.release();
                    }
                });
            } catch (RejectedExecutionException e) {
                pending.remove(key);
                admission.release();
                result.completeExceptionally(e);
            }
            return result;
        });
    }

    private JsonNode execute(String method, String path, String payload, boolean auth) throws Exception {
        Gate gate = routes[Math.floorMod((method + path).hashCode(), routes.length)];
        gate.lock.lockInterruptibly();
        try {
            for (int attempt = 0; attempt <= 5; attempt++) {
                pauseUntil(gate.next);
                acquireGlobal();
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/v1" + path))
                        .timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
                        .header("User-Agent", net.guneyilmaz0.micup4j.MicupVersion.getUserAgent());
                if (auth) builder.header("Authorization", "Bot " + token);
                if (payload != null) builder.header("Content-Type", "application/json");
                HttpResponse<String> response;
                try {
                    response = http.send(builder.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
                } catch (java.io.IOException e) {
                    // Do not retry uncertain writes, and do not expose token-bearing request URIs.
                    throw new IllegalStateException("Micup transport failed; request outcome may be unknown");
                }
                JsonNode data;
                try {
                    data = Json.parse(response.body());
                } catch (IllegalArgumentException e) {
                    throw new ApiException(response.statusCode(), "INVALID_RESPONSE");
                }
                long now = System.nanoTime();
                if (response.headers().firstValue("X-RateLimit-Remaining").orElse("").equals("0"))
                    gate.next = now + delayNanos(response.headers().firstValue("X-RateLimit-Reset-After").orElse("1"));
                if (response.statusCode() == 429) {
                    double seconds = data.path("retry_after").asDouble(-1);
                    long wait = delayNanos(seconds >= 0 ? Double.toString(seconds) : response.headers().firstValue("Retry-After").orElse("1"));
                    gate.next = Math.max(gate.next, now + wait);
                    if (data.path("global").asBoolean() || response.headers().firstValue("X-RateLimit-Global").orElse("").equals("true")) {
                        synchronized (globalLock) {
                            globalBlockedUntil = Math.max(globalBlockedUntil, now + wait);
                            nextGlobal = Math.max(nextGlobal, globalBlockedUntil);
                        }
                    }
                    if (attempt < 5) continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new ApiException(response.statusCode(), safeCode(data.path("code").asText("UNKNOWN")));
                return data;
            }
            throw new IllegalStateException("Retry budget exhausted");
        } finally {
            gate.lock.unlock();
        }
    }

    private long globalBlockedUntil;

    private void acquireGlobal() throws InterruptedException {
        while (true) {
            long deadline;
            synchronized (globalLock) {
                long now = System.nanoTime();
                deadline = Math.max(nextGlobal, globalBlockedUntil);
                if (now >= deadline) {
                    nextGlobal = now + 20_000_000L;
                    return;
                }
            }
            pauseUntil(deadline);
        }
    }

    private static String safeCode(String code) {
        return code.matches("[A-Z0-9_]{1,100}") ? code : "UNKNOWN";
    }

    private static long delayNanos(String text) {
        try {
            double seconds = Double.parseDouble(text);
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 3600) throw new NumberFormatException();
            return (long) (Math.max(0.001, seconds) * 1_000_000_000L);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid rate limit delay");
        }
    }

    private void pauseUntil(long deadline) throws InterruptedException {
        while (deadline > System.nanoTime()) {
            if (closed || Thread.currentThread().isInterrupted()) throw new InterruptedException("Client closed");
            TimeUnit.NANOSECONDS.sleep(deadline - System.nanoTime());
        }
        if (closed) throw new InterruptedException("Client closed");
    }

    @Override
    public void close() {
        closed = true;
        pending.values().forEach(f -> f.completeExceptionally(new IllegalStateException("Client closed")));
        workers.shutdownNow();
    }
}
