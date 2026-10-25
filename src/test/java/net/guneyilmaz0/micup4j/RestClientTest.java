package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.internal.*;
import net.guneyilmaz0.micup4j.requests.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.time.Duration;
import net.guneyilmaz0.micup4j.config.RestConfig;
import java.net.http.HttpClient;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RestClientTest {
    MockWebServer server;
    HttpClient http;
    RestClient rest;
    @BeforeEach void setup() throws Exception {
        server = new MockWebServer(); server.start();
        http = HttpClient.newHttpClient(); rest = new RestClient(http, "secret-token", 4);
        rest.setBase(server.url("/api").uri());
    }
    @AfterEach void cleanup() throws Exception { rest.close(); http.shutdownNow(); server.shutdown(); }
    MockResponse json(String text) { return new MockResponse().setHeader("Content-Type", "application/json").setBody(text); }

    @Test void actionIsLazyAndUsesDiscoveredBaseAndBotHeader() throws Exception {
        server.enqueue(json("{\"id\":\"1\"}"));
        var action = rest.request("POST", "/channels/2/messages", Json.object().put("content", "Merhaba"));
        assertEquals(0, server.getRequestCount());
        assertEquals("1", action.submit().get(5, TimeUnit.SECONDS).path("id").asText());
        var request = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("/api/v1/channels/2/messages", request.getPath());
        assertEquals("Bot secret-token", request.getHeader("Authorization"));
        assertEquals("Merhaba", Json.parse(request.getBody().readUtf8()).path("content").asText());
    }
    @Test void retriesFractional429Delay() throws Exception {
        server.enqueue(json("{\"code\":\"RATE_LIMITED\",\"retry_after\":0.08,\"global\":false}").setResponseCode(429));
        server.enqueue(json("{\"ok\":true}"));
        long start = System.nanoTime();
        assertTrue(rest.request("GET", "/users/1", null).submit().get(5, TimeUnit.SECONDS).path("ok").asBoolean());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 75);
        assertEquals(2, server.getRequestCount());
    }
    @Test void respectsSuccessfulResponseExhaustion() throws Exception {
        server.enqueue(json("{}").setHeader("X-RateLimit-Remaining", "0").setHeader("X-RateLimit-Reset-After", "0.1"));
        server.enqueue(json("{}"));
        rest.request("GET", "/users/1", null).complete();
        long start = System.nanoTime();
        rest.request("GET", "/users/1", null).complete();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 85);
    }
    @Test void uncertainWriteIsNotRetried() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        assertThrows(CompletionException.class, () -> rest.request("POST", "/channels/2/messages", Json.object().put("content", "x")).complete());
        assertEquals(1, server.getRequestCount());
    }
    @Test void callbackOmitsAuthorizationAndErrorOmitsToken() throws Exception {
        server.enqueue(json("{\"code\":\"UNKNOWN_WEBHOOK\",\"message\":\"secret-interaction-token\"}").setResponseCode(404));
        var error = assertThrows(CompletionException.class, () -> rest.request("POST", "/interactions/1/secret-interaction-token/callback", Json.object(), false).complete());
        assertInstanceOf(ApiException.class, error.getCause());
        assertFalse(error.toString().contains("secret-interaction-token"));
        assertFalse(error.getCause().toString().contains("secret-interaction-token"));
        assertNull(server.takeRequest().getHeader("Authorization"));
    }
    @Test void empty204BecomesNullNode() {
        server.enqueue(new MockResponse().setResponseCode(204));
        assertTrue(rest.request("DELETE", "/channels/1/messages/2", null).complete().isNull());
    }
    @Test void closingCompletesWaitingWorkAndRejectsNewWork() throws Exception {
        server.enqueue(json("{\"retry_after\":60,\"global\":true}").setResponseCode(429));
        var pending = rest.request("GET", "/users/1", null).submit();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        rest.close();
        assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS));
        assertThrows(CompletionException.class, () -> rest.request("GET", "/users/2", null).complete());
    }
    @Test void rejectsInsecureRemoteEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> rest.setBase(URI.create("http://example.com")));
        assertThrows(IllegalArgumentException.class, () -> rest.setBase(URI.create("https://secret@example.com")));
    }
    @Test void snowflakesAreCanonicalUnsigned64Bit() {
        assertEquals("18446744073709551615", Json.id("18446744073709551615"));
        for (String id : new String[]{"0", "01", "-1", "18446744073709551616", "1/../2"})
            assertThrows(IllegalArgumentException.class, () -> Json.id(id));
    }
    @Test void finalGlobal429StillDelaysOtherRoutes() throws Exception {
        for (int i = 0; i < 5; i++)
            server.enqueue(json("{\"code\":\"RATE_LIMITED\",\"retry_after\":0.001,\"global\":false}").setResponseCode(429));
        server.enqueue(json("{\"code\":\"RATE_LIMITED\",\"retry_after\":0.25,\"global\":true}").setResponseCode(429));
        server.enqueue(json("{}"));
        var error = assertThrows(CompletionException.class, () -> rest.request("GET", "/users/1", null).complete());
        assertEquals(429, ((ApiException) error.getCause()).getStatus());
        long start = System.nanoTime();
        rest.request("GET", "/users/2", null).submit().get(5, TimeUnit.SECONDS);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 200);
        assertEquals(7, server.getRequestCount());
    }

    void policy(RestConfig config, int capacity) {
        rest.close(); rest = new RestClient(http, "secret-token", capacity, config); rest.setBase(server.url("/api").uri());
    }
    @Test void normalizedMessageIdsShareChannelCooldown() throws Exception {
        server.enqueue(json("{}").setHeader("X-RateLimit-Bucket", "shared").setHeader("X-RateLimit-Remaining", "0").setHeader("X-RateLimit-Reset-After", ".2"));
        server.enqueue(json("{}"));
        rest.request("GET", "/channels/2/messages/3", null).complete();
        long start = System.nanoTime();
        rest.request("GET", "/channels/2/messages/4", null).complete();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 160);
    }
    @Test void learnedBucketMergesDifferentRoutesWithoutMergingChannels() {
        server.enqueue(json("{}").setHeader("X-RateLimit-Bucket", "shared"));
        server.enqueue(json("{}").setHeader("X-RateLimit-Bucket", "shared").setHeader("X-RateLimit-Remaining", "0").setHeader("X-RateLimit-Reset-After", ".2"));
        server.enqueue(json("{}"));
        server.enqueue(json("{}"));
        rest.request("GET", "/channels/2/messages/3", null).complete();
        rest.request("POST", "/channels/2/messages", Json.object().put("content", "x")).complete();
        // Another major resource is eligible despite the shared server hash.
        rest.request("GET", "/channels/9/messages/3", null).complete();
        long start = System.nanoTime();
        rest.request("GET", "/channels/2/messages/5", null).complete();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 100);
    }
    @Test void cancellationOfMappedQueuedActionReleasesAdmission() throws Exception {
        policy(RestConfig.defaults(), 1);
        server.enqueue(json("{}").setHeader("X-RateLimit-Remaining", "0").setHeader("X-RateLimit-Reset-After", "1"));
        rest.request("GET", "/users/1", null).complete();
        var waiting = rest.request("GET", "/users/1", null).map(value -> value.path("id").asText()).submit();
        assertTrue(waiting.cancel(true));
        assertEquals(0, rest.getMetrics().queued());
        assertEquals(1, rest.getMetrics().cancelled());
        server.enqueue(json("{}"));
        rest.request("GET", "/users/2", null).submit().get(3, TimeUnit.SECONDS);
        assertEquals(2, server.getRequestCount());
    }
    @Test void totalDeadlineIncludesRateLimitWait() {
        policy(RestConfig.defaults().withTotalTimeout(Duration.ofMillis(120)), 4);
        server.enqueue(json("{\"retry_after\":10}").setResponseCode(429));
        var error = assertThrows(CompletionException.class, () -> rest.request("GET", "/users/1", null).complete());
        assertInstanceOf(TimeoutException.class, error.getCause());
        assertEquals(1, server.getRequestCount());
        assertEquals(0, rest.getMetrics().queued());
    }
    @Test void attemptTimeoutDoesNotRetryUncertainWrites() {
        policy(RestConfig.defaults().withRequestTimeout(Duration.ofMillis(80)), 4);
        server.enqueue(json("{}").setHeadersDelay(300, TimeUnit.MILLISECONDS));
        var error = assertThrows(CompletionException.class, () -> rest.request("POST", "/channels/2/messages", Json.object().put("content", "x")).complete());
        assertInstanceOf(TimeoutException.class, error.getCause());
        assertEquals(1, server.getRequestCount());
        assertEquals(0, rest.getMetrics().retries());
    }
    @Test void callbackHasReservedAdmissionAndTransportSlot() throws Exception {
        policy(RestConfig.defaults().withMaxConcurrency(2), 1);
        server.enqueue(json("{}").setHeadersDelay(1, TimeUnit.SECONDS));
        server.enqueue(new MockResponse().setResponseCode(204));
        var normal = rest.request("GET", "/users/1", null).submit();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        assertThrows(CompletionException.class, () -> rest.request("GET", "/users/2", null).complete());
        rest.requestCallback("/interactions/3/callback-secret/callback", Json.object().put("type", 5),
                System.nanoTime() + TimeUnit.SECONDS.toNanos(2)).submit().get(2, TimeUnit.SECONDS);
        assertFalse(normal.isDone(), "The initial callback must not wait for the normal transport slot");
        var callback = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("/api/v1/interactions/3/callback-secret/callback", callback.getPath());
        assertNull(callback.getHeader("Authorization"));
        normal.cancel(true);
        assertEquals(0, rest.getMetrics().inFlight());
    }
    @Test void expiredCallbackIsNotSentAndDiagnosticsContainNoSecret() {
        var error = assertThrows(CompletionException.class, () -> rest.requestCallback("/interactions/3/callback-secret/callback", Json.object(), System.nanoTime() - 1).complete());
        assertInstanceOf(TimeoutException.class, error.getCause());
        assertEquals(0, server.getRequestCount());
        assertFalse(rest.getMetrics().toString().contains("callback-secret"));
    }
    @Test void routeAndServerBucketCachesRemainBounded() {
        policy(RestConfig.defaults().withBucketCacheSize(2), 4);
        for (int i = 1; i <= 10; i++) {
            server.enqueue(json("{}").setHeader("X-RateLimit-Bucket", "bucket" + i));
            rest.request("GET", "/users/" + i, null).complete();
        }
        assertTrue(rest.getMetrics().trackedRoutes() <= 2);
        assertTrue(rest.getMetrics().trackedBuckets() <= 2);
        assertEquals(10, rest.getMetrics().completed());
    }
    @Test void concurrentRequestsHonorGlobalPacing() throws Exception {
        policy(RestConfig.defaults(), 16);
        var tasks = new java.util.ArrayList<CompletableFuture<?>>();
        for (int i = 1; i <= 12; i++) server.enqueue(json("{}"));
        long started = System.nanoTime();
        for (int i = 1; i <= 12; i++) tasks.add(rest.request("GET", "/channels/" + i, null).submit());
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= 180);
        assertEquals(12, rest.getMetrics().submitted());
        assertEquals(12, rest.getMetrics().completed());
        assertEquals(0, rest.getMetrics().inFlight());
    }
    @Test void configuredRetryBudgetIsApplied() {
        policy(RestConfig.defaults().withMaxRetries(1), 4);
        server.enqueue(json("{\"code\":\"RATE_LIMITED\",\"retry_after\":0.001}").setResponseCode(429));
        server.enqueue(json("{\"code\":\"RATE_LIMITED\",\"retry_after\":0.001}").setResponseCode(429));
        assertThrows(CompletionException.class, () -> rest.request("GET", "/users/1", null).complete());
        assertEquals(2, server.getRequestCount());
        assertEquals(1, rest.getMetrics().retries());
    }

}
