package net.guneyilmaz0.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.entities.Message;
import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.interactions.InteractionHook;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;
import net.guneyilmaz0.micup4j.requests.restaction.WebhookMessageCreateAction;
import net.guneyilmaz0.micup4j.requests.restaction.WebhookMessageEditAction;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fluent InteractionHook webhook builders against a local MockWebServer. */
class InteractionHookBuilderTest extends GatewayFixture {
    private static final String WEBHOOK = "/webhooks/42/interaction-secret";

    SlashCommandInteractionEvent slash() {
        return new SlashCommandInteractionEvent(client, 2, Json.parse("""
                {"id":"123","application_id":"42","token":"interaction-secret","type":2,"guild_id":"1","channel_id":"8",
                 "member":{"user":{"id":"4","username":"Caller"},"roles":[]},"data":{"name":"hello"}}
                """));
    }

    MessageEmbed embed(String title) { return new EmbedBuilder().setTitle(title).build(); }

    /** Takes the next request whose path contains the interaction webhook, skipping discovery and Gateway traffic. */
    RecordedRequest webhookRequest() throws Exception {
        for (int i = 0; i < 20; i++) {
            var request = server.takeRequest(3, TimeUnit.SECONDS);
            assertNotNull(request, "Missing webhook request");
            if (request.getPath().contains(WEBHOOK)) return request;
        }
        throw new AssertionError("Missing webhook request");
    }

    JsonNode body(RecordedRequest request) { return Json.parse(request.getBody().readUtf8()); }

    InteractionHook deferred(boolean ephemeral) throws Exception {
        var event = slash();
        var hook = event.deferReply(ephemeral).submit().get(3, TimeUnit.SECONDS);
        route("/callback");
        return hook;
    }

    @Test void firstSendAfterDeferPatchesOriginalWithoutFlagsAndLaterSendsArePostedFollowups() throws Exception {
        start(new ListenerAdapter() {});
        var hook = deferred(false).setEphemeral(true);
        var first = hook.sendMessage("Original").setEphemeral(true).addEmbeds(embed("E")).submit().get(3, TimeUnit.SECONDS);
        var request = webhookRequest();
        assertEquals("PATCH", request.getMethod()); assertTrue(request.getPath().endsWith("/messages/@original"));
        var payload = body(request);
        assertFalse(payload.has("flags")); assertEquals("Original", payload.path("content").asText());
        assertEquals(1, payload.path("embeds").size()); assertEquals("1", first.getGuild().getId());

        hook.sendMessage("Hook default").submit().get(3, TimeUnit.SECONDS);
        request = webhookRequest();
        assertEquals("POST", request.getMethod()); assertTrue(request.getPath().endsWith(WEBHOOK));
        assertEquals(64, body(request).path("flags").asInt());

        hook.sendMessageEmbeds(embed("Public")).setEphemeral(false).submit().get(3, TimeUnit.SECONDS);
        request = webhookRequest();
        assertEquals("POST", request.getMethod());
        payload = body(request);
        assertEquals(0, payload.path("flags").asInt()); assertTrue(payload.has("flags"));
        assertEquals("Public", payload.path("embeds").get(0).path("title").asText());

        // The hook default is read when the action is created.
        var created = hook.sendMessage("Created while ephemeral");
        hook.setEphemeral(false);
        created.submit().get(3, TimeUnit.SECONDS);
        assertEquals(64, body(webhookRequest()).path("flags").asInt());
        hook.sendMessage("Now public").addContent("!").submit().get(3, TimeUnit.SECONDS);
        payload = body(webhookRequest());
        assertEquals(0, payload.path("flags").asInt()); assertEquals("Now public!", payload.path("content").asText());
    }

    @Test void concurrentFirstSendsAfterDeferProduceExactlyOnePatch() throws Exception {
        start(new ListenerAdapter() {});
        var hook = deferred(true);
        int count = 8;
        var ready = new CountDownLatch(1);
        var futures = new ArrayList<CompletableFuture<Message>>();
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < count; i++) {
            var action = hook.sendMessage("Message " + i);
            var future = new CompletableFuture<Message>();
            futures.add(future);
            threads.add(Thread.ofVirtual().start(() -> {
                try { ready.await(); action.submit().whenComplete((value, error) -> {
                    if (error != null) future.completeExceptionally(error); else future.complete(value);
                }); } catch (InterruptedException error) { future.completeExceptionally(error); }
            }));
        }
        ready.countDown();
        for (var future : futures) future.get(5, TimeUnit.SECONDS);
        int patches = 0, posts = 0;
        for (int i = 0; i < count; i++) {
            var request = webhookRequest();
            if (request.getMethod().equals("PATCH")) { patches++; assertFalse(body(request).has("flags")); }
            else { posts++; assertEquals(0, body(request).path("flags").asInt()); }
        }
        assertEquals(1, patches); assertEquals(count - 1, posts);
    }

    @Test void editDeleteAndRetrieveByIdRouteToFollowupsAndOriginal() throws Exception {
        start(new ListenerAdapter() {});
        var event = slash();
        var hook = event.reply("Hi").submit().get(3, TimeUnit.SECONDS);
        route("/callback");
        assertSame(event, hook.getInteraction()); assertSame(client, hook.getJDA());
        assertEquals(2, hook.getInteraction().getTypeRaw());

        hook.editMessageById("99", "Edited").setEmbeds(embed("E")).submit().get(3, TimeUnit.SECONDS);
        var request = webhookRequest();
        assertEquals("PATCH", request.getMethod()); assertTrue(request.getPath().endsWith(WEBHOOK + "/messages/99"));
        var payload = body(request);
        assertEquals("Edited", payload.path("content").asText()); assertFalse(payload.has("flags"));

        hook.editMessageById(99L, "Long id").submit().get(3, TimeUnit.SECONDS);
        assertTrue(webhookRequest().getPath().endsWith("/messages/99"));
        hook.editMessageEmbedsById(99L, embed("Only")).submit().get(3, TimeUnit.SECONDS);
        payload = body(webhookRequest());
        assertFalse(payload.has("content")); assertEquals(1, payload.path("embeds").size());

        hook.retrieveMessageById("99").submit().get(3, TimeUnit.SECONDS);
        request = webhookRequest();
        assertEquals("GET", request.getMethod()); assertTrue(request.getPath().endsWith("/messages/99"));

        hook.deleteMessageById("99").submit().get(3, TimeUnit.SECONDS);
        request = webhookRequest();
        assertEquals("DELETE", request.getMethod()); assertTrue(request.getPath().endsWith("/messages/99"));

        hook.editMessageById("@original", "Original").submit().get(3, TimeUnit.SECONDS);
        request = webhookRequest();
        assertEquals("PATCH", request.getMethod()); assertTrue(request.getPath().endsWith("/messages/@original"));
        hook.deleteMessageById("@original").submit().get(3, TimeUnit.SECONDS);
        request = webhookRequest();
        assertEquals("DELETE", request.getMethod()); assertTrue(request.getPath().endsWith("/messages/@original"));

        int before = server.getRequestCount();
        assertThrows(IllegalArgumentException.class, () -> hook.editMessageById("0099", "x"));
        assertThrows(IllegalArgumentException.class, () -> hook.deleteMessageById("abc"));
        assertThrows(IllegalArgumentException.class, () -> hook.retrieveMessageById("@ORIGINAL"));
        assertEquals(before, server.getRequestCount());
    }

    @Test void contentClearingEditsInjectZeroFlagsAndEmptyEditsAreRejectedLocally() throws Exception {
        start(new ListenerAdapter() {});
        var hook = slash().reply("Hi").submit().get(3, TimeUnit.SECONDS);
        route("/callback");
        hook.editOriginal("").submit().get(3, TimeUnit.SECONDS);
        assertEquals(Json.parse("{\"content\":\"\",\"flags\":0}"), body(webhookRequest()));
        hook.editOriginalEmbeds().submit().get(3, TimeUnit.SECONDS);
        assertEquals(Json.parse("{\"embeds\":[],\"flags\":0}"), body(webhookRequest()));
        hook.editOriginal("Text").setEmbeds(List.of()).submit().get(3, TimeUnit.SECONDS);
        assertEquals(Json.parse("{\"content\":\"Text\",\"embeds\":[]}"), body(webhookRequest()));

        var sent = new AtomicInteger();
        var empty = new WebhookMessageEditAction<JsonNode>(Json.object(), payload -> {
            sent.incrementAndGet(); return new RestAction<>(() -> CompletableFuture.completedFuture(payload));
        });
        var error = assertThrows(ExecutionException.class, () -> empty.submit().get(3, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals(0, sent.get());
        // Rejected before the freeze, so the edit can still be completed.
        assertEquals("Fixed", empty.setContent("Fixed").submit().get(3, TimeUnit.SECONDS).path("content").asText());
    }

    @Test void settersFreezeAfterFirstValidSubmitAndInvalidSubmitsCanBeRepaired() throws Exception {
        start(new ListenerAdapter() {});
        var hook = slash().reply("Hi").submit().get(3, TimeUnit.SECONDS);
        route("/callback");
        int before = server.getRequestCount();
        WebhookMessageCreateAction<Message> action = hook.sendMessage("  ");
        var error = assertThrows(ExecutionException.class, () -> action.submit().get(3, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals(before, server.getRequestCount());
        action.setContent("Repaired").submit().get(3, TimeUnit.SECONDS);
        var first = body(webhookRequest());
        assertThrows(IllegalStateException.class, () -> action.setContent("Late"));
        assertThrows(IllegalStateException.class, () -> action.setEphemeral(true));
        assertThrows(IllegalStateException.class, () -> action.addEmbeds(embed("Late")));
        action.submit().get(3, TimeUnit.SECONDS);
        assertEquals(first, body(webhookRequest()));

        var edit = hook.editOriginal("Once");
        edit.submit().get(3, TimeUnit.SECONDS); webhookRequest();
        assertThrows(IllegalStateException.class, () -> edit.setEmbeds(embed("Late")));
        assertThrows(IllegalArgumentException.class, () -> hook.sendMessage("x").addEmbeds(java.util.Collections.nCopies(11, embed("x"))));
    }

    @Test void hookActionsNeverSendBeforeAcknowledgementAndFailAfterFailedAcknowledgement() throws Exception {
        start(new ListenerAdapter() {});
        var event = slash();
        int before = server.getRequestCount();
        assertThrows(ExecutionException.class, () -> event.getHook().sendMessage("Early").submit().get(3, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> event.getHook().editMessageById("99", "Early").submit().get(3, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> event.getHook().deleteMessageById("99").submit().get(3, TimeUnit.SECONDS));
        assertEquals(before, server.getRequestCount());

        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) { return new MockResponse().setResponseCode(403).setBody("{}"); }
        });
        var failing = slash();
        assertThrows(ExecutionException.class, () -> failing.deferReply().submit().get(3, TimeUnit.SECONDS));
        int afterCallback = server.getRequestCount();
        assertThrows(ExecutionException.class, () -> failing.getHook().sendMessage("Never").submit().get(3, TimeUnit.SECONDS));
        assertEquals(afterCallback, server.getRequestCount());
    }

    @Test void expiredHookFailsActionsWithoutHttp() throws Exception {
        start(new ListenerAdapter() {});
        var hook = slash().reply("Hi").submit().get(3, TimeUnit.SECONDS);
        route("/callback");
        var field = InteractionHook.class.getDeclaredField("expiresAt");
        field.setAccessible(true);
        field.setLong(hook, System.nanoTime() - 1);
        assertTrue(hook.isExpired());
        int before = server.getRequestCount();
        var error = assertThrows(ExecutionException.class, () -> hook.sendMessage("Late").submit().get(3, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertThrows(ExecutionException.class, () -> hook.editOriginal("Late").submit().get(3, TimeUnit.SECONDS));
        assertEquals(before, server.getRequestCount());
    }

    @Test void replyCallbackActionSupportsJdaContentAndEmbedSetters() throws Exception {
        start(new ListenerAdapter() {});
        var event = slash();
        var action = event.reply("Hi").addContent(" there").setEmbeds(embed("A")).addEmbeds(List.of(embed("B")));
        action.submit().get(3, TimeUnit.SECONDS);
        var data = body(route("/callback")).path("data");
        assertEquals("Hi there", data.path("content").asText()); assertEquals(2, data.path("embeds").size());
        assertEquals("B", data.path("embeds").get(1).path("title").asText());
        assertThrows(IllegalStateException.class, () -> action.addContent("Late"));
        assertThrows(IllegalStateException.class, () -> action.setEmbeds(List.of()));

        var deferred = slash().deferReply(true);
        assertThrows(IllegalStateException.class, () -> deferred.setEmbeds(embed("x")));
        assertThrows(IllegalStateException.class, () -> deferred.addEmbeds(List.of(embed("x"))));
        assertThrows(IllegalStateException.class, () -> deferred.addContent("x"));

        var noVisible = slash().replyEmbeds(embed("Only")).setEmbeds();
        int before = server.getRequestCount();
        var error = assertThrows(ExecutionException.class, () -> noVisible.submit().get(3, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals(before, server.getRequestCount());
        noVisible.setContent("Fixed").submit().get(3, TimeUnit.SECONDS);
        assertEquals("Fixed", body(route("/callback")).path("data").path("content").asText());
    }
}
