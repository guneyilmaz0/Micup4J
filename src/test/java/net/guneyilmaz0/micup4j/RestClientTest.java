package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.internal.*;
import net.guneyilmaz0.micup4j.requests.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import java.net.URI;
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

}
