package com.swadedev.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.entities.Member;
import com.swadedev.micup4j.hooks.ListenerAdapter;
import com.swadedev.micup4j.internal.Json;
import com.swadedev.micup4j.permissions.Permission;
import com.swadedev.micup4j.permissions.PermissionSet;
import com.swadedev.micup4j.requests.GatewayRateLimitException;
import com.swadedev.micup4j.requests.restaction.MemberSearchAction;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class GuildModerationTest extends GatewayFixture {
    private final AtomicInteger sequence = new AtomicInteger(1);

    void responses(Function<RecordedRequest, String> responder) {
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String body = responder.apply(request);
                return body == null ? new MockResponse().setResponseCode(204) : json(body);
            }
        });
    }

    void respondWithEntities() {
        responses(request -> {
            String path = request.getPath();
            if (path.endsWith("/members-search")) return searchPage();
            if (path.contains("/bans/") || path.contains("/roles/") && request.getMethod().equals("DELETE")) return null;
            if (path.matches(".*/members/[0-9]+") && request.getMethod().equals("DELETE")) return null;
            if (path.contains("/members/")) return EntityCacheTest.member("4");
            if (path.contains("/roles")) return EntityCacheTest.role("2", 1);
            return EntityCacheTest.snapshot().toString();
        });
    }

    static String searchPage() {
        return "{\"guild_id\":\"1\",\"members\":[{\"id\":\"1_4\",\"guild_id\":\"1\",\"user_id\":\"4\",\"username\":\"ada\",\"discriminator\":\"0042\","
                + "\"global_name\":\"Ada\",\"nickname\":null,\"role_ids\":[\"2\"],\"joined_at\":1755158400,"
                + "\"supplemental\":{\"join_source_type\":1,\"source_invite_code\":\"aBc123\",\"inviter_id\":\"5\"},\"is_bot\":false}],"
                + "\"page_result_count\":1,\"total_result_count\":7,\"indexing\":false}";
    }

    JsonNode body(RecordedRequest request) { return Json.parse(request.getBody().readUtf8()); }

    void dispatch(String type, String data) {
        sockets.peek().send("{\"op\":0,\"t\":\"" + type + "\",\"s\":" + sequence.incrementAndGet() + ",\"d\":" + data + "}");
    }

    @Test void auditReasonsTravelInTheHeaderAndAreValidatedLocally() throws Exception {
        start(new ListenerAdapter() {});
        respondWithEntities();
        client.modifyGuildName("1", "Renamed").reason("  spring cleanup  ").submit().get(3, TimeUnit.SECONDS);
        assertEquals("spring cleanup", route("/guilds/1").getHeader("X-Audit-Log-Reason"));
        client.kick("1", "4").reason("spam").submit().get(3, TimeUnit.SECONDS);
        var kick = route("/members/4"); assertEquals("DELETE", kick.getMethod()); assertEquals("spam", kick.getHeader("X-Audit-Log-Reason"));
        client.addMemberRole("1", "4", "2").reason(" ").submit().get(3, TimeUnit.SECONDS);
        assertNull(route("/members/4/roles/2").getHeader("X-Audit-Log-Reason"));
        client.deleteRole("1", "2").reason("unused").submit().get(3, TimeUnit.SECONDS);
        var delete = route("/roles/2"); assertEquals("DELETE", delete.getMethod()); assertEquals("unused", delete.getHeader("X-Audit-Log-Reason"));
        client.unban("1", "4").reason("appeal").submit().get(3, TimeUnit.SECONDS);
        assertEquals("appeal", route("/bans/4").getHeader("X-Audit-Log-Reason"));

        assertThrows(IllegalArgumentException.class, () -> client.kick("1", "4").reason("Küfür"));
        assertThrows(IllegalArgumentException.class, () -> client.kick("1", "4").reason("line\nbreak"));
        assertThrows(IllegalArgumentException.class, () -> client.kick("1", "4").reason("x".repeat(513)));
        assertEquals(512, client.kick("1", "4").reason("x".repeat(512)).getReason().length());
    }

    @Test void banAndTimeoutSendUnicodeReasonsInTheBody() throws Exception {
        start(new ListenerAdapter() {});
        respondWithEntities();
        client.ban("1", "4", 1, TimeUnit.DAYS).reason("Küfür ve spam").submit().get(3, TimeUnit.SECONDS);
        var ban = route("/bans/4"); assertEquals("PUT", ban.getMethod()); assertNull(ban.getHeader("X-Audit-Log-Reason"));
        var banBody = body(ban);
        assertEquals(86_400, banBody.path("delete_message_seconds").asInt());
        assertEquals("Küfür ve spam", banBody.path("reason").asText());
        assertFalse(banBody.has("ban_duration_seconds"));
        client.ban("1", "4", 0, TimeUnit.SECONDS, Duration.ofHours(2)).submit().get(3, TimeUnit.SECONDS);
        assertEquals(7200, body(route("/bans/4")).path("ban_duration_seconds").asInt());
        assertThrows(IllegalArgumentException.class, () -> client.ban("1", "4", 8, TimeUnit.DAYS));
        assertThrows(IllegalArgumentException.class, () -> client.ban("1", "4", -1, TimeUnit.SECONDS));
        assertThrows(IllegalArgumentException.class, () -> client.ban("1", "4", 0, TimeUnit.SECONDS, Duration.ofSeconds(59)));

        Instant before = Instant.now();
        Member member = client.timeoutFor("1", "4", Duration.ofMinutes(10)).reason("cool down").submit().get(3, TimeUnit.SECONDS);
        assertEquals("4", member.getId());
        var timeout = body(route("/members/4"));
        Instant until = Instant.parse(timeout.path("communication_disabled_until").asText());
        assertFalse(until.isBefore(before.plus(Duration.ofMinutes(10)).minusSeconds(1)));
        assertEquals("cool down", timeout.path("timeout_reason").asText());
        client.removeTimeout("1", "4").submit().get(3, TimeUnit.SECONDS);
        assertTrue(body(route("/members/4")).get("communication_disabled_until").isNull());
        assertThrows(IllegalArgumentException.class, () -> client.timeoutFor("1", "4", Duration.ofDays(366)));
        assertThrows(IllegalArgumentException.class, () -> client.timeoutFor("1", "4", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> client.timeoutUntil("1", "4", Instant.now().minusSeconds(1)));
    }

    @Test void roleActionsSendOnlySetFieldsAndDeclareGatedPermissionFeatures() throws Exception {
        start(new ListenerAdapter() {});
        respondWithEntities();
        var role = client.createRole("1").setName("Helper").setColor(new java.awt.Color(0x3498DB)).setHoisted(true)
                .setPermissions(com.swadedev.micup4j.Permission.VIEW_CHANNEL).reason("staff").submit().get(3, TimeUnit.SECONDS);
        assertEquals("2", role.id());
        var create = route("/roles"); var created = body(create);
        assertEquals("POST", create.getMethod());
        assertEquals("Helper", created.path("name").asText());
        assertEquals(0x3498DB, created.path("color").asInt());
        assertTrue(created.path("hoist").asBoolean());
        assertEquals("1024", created.path("permissions").asText());
        assertFalse(created.has("mentionable"));
        assertEquals("view_channel_members_permission", create.getHeader("X-Fluxer-Features"));
        assertEquals("staff", create.getHeader("X-Audit-Log-Reason"));

        client.createRole("1").setName("Plain").submit().get(3, TimeUnit.SECONDS);
        var plain = route("/roles"); assertNull(plain.getHeader("X-Fluxer-Features")); assertFalse(body(plain).has("permissions"));

        client.modifyRole("1", "2").setMentionable(false).setPermissions(PermissionSet.of(Permission.VIEW_CHANNEL_MEMBERS))
                .submit().get(3, TimeUnit.SECONDS);
        var modify = route("/roles/2"); var modified = body(modify);
        assertEquals("PATCH", modify.getMethod());
        assertEquals(List.of("mentionable", "permissions"), fieldNames(modified));
        assertEquals(PermissionSet.of(Permission.VIEW_CHANNEL_MEMBERS).toRequestValue(), modified.path("permissions").asText());
        assertEquals("view_channel_members_permission", modify.getHeader("X-Fluxer-Features"));

        int before = server.getRequestCount();
        assertNull(client.modifyRole("1", "2").submit().get(3, TimeUnit.SECONDS));
        assertEquals(before, server.getRequestCount(), "An empty manager must not send a request");
        assertThrows(IllegalArgumentException.class, () -> client.createRole("1").setName(""));
        assertThrows(IllegalArgumentException.class, () -> client.createRole("1").setColor(0x1000000));
        assertThrows(IllegalArgumentException.class, () -> client.createRole("1").setPermissions(PermissionSet.ALL));
    }

    static List<String> fieldNames(JsonNode node) {
        var names = new java.util.ArrayList<String>(); node.fieldNames().forEachRemaining(names::add); return names;
    }

    @Test void memberSearchSerializesFiltersAndParsesPartialResults() throws Exception {
        start(new ListenerAdapter() {});
        respondWithEntities();
        var result = client.searchMembers("1").setQuery("  ada ").setLimit(10).setOffset(20).setRoleIds(List.of("2"))
                .setJoinedBetween(Instant.ofEpochSecond(100), Instant.ofEpochSecond(200)).setBot(false)
                .setJoinSourceTypes(1, 2).setSourceInviteCodes("aBc123").setSort(MemberSearchAction.SortField.JOINED_AT, true)
                .submit().get(3, TimeUnit.SECONDS);
        var request = route("/members-search"); var sent = body(request);
        assertEquals("POST", request.getMethod());
        assertEquals("ada", sent.path("query").asText());
        assertEquals(10, sent.path("limit").asInt());
        assertEquals(20, sent.path("offset").asInt());
        assertEquals("2", sent.path("role_ids").get(0).asText());
        assertEquals(100, sent.path("joined_at_gte").asLong());
        assertEquals(200, sent.path("joined_at_lte").asLong());
        assertFalse(sent.path("is_bot").asBoolean(true));
        assertEquals(2, sent.path("join_source_type").get(1).asInt());
        assertEquals("aBc123", sent.path("source_invite_code").get(0).asText());
        assertEquals("joinedAt", sent.path("sort_by").asText());
        assertEquals("asc", sent.path("sort_order").asText());

        assertEquals(7, result.totalResultCount());
        assertFalse(result.indexing());
        var entry = result.members().getFirst();
        assertEquals("4", entry.getId());
        assertEquals("Ada", entry.getEffectiveName());
        assertEquals(Instant.ofEpochSecond(1755158400), entry.joinedAt());
        assertEquals(1, entry.joinSourceType());
        assertEquals("5", entry.inviterId());

        client.searchMembers("1").setQuery(" ").submit().get(3, TimeUnit.SECONDS);
        assertFalse(body(route("/members-search")).has("query"), "Blank queries are omitted; Micup rejects empty strings");
        assertThrows(IllegalArgumentException.class, () -> client.searchMembers("1").setLimit(0));
        assertThrows(IllegalArgumentException.class, () -> client.searchMembers("1").setLimit(101));
        assertThrows(IllegalArgumentException.class, () -> client.searchMembers("1").setQuery("q".repeat(101)));
        assertThrows(IllegalArgumentException.class, () -> client.searchMembers("1").setRoleIds(java.util.Collections.nCopies(11, "2")));
        assertThrows(IllegalArgumentException.class, () -> client.searchMembers("1")
                .setJoinedBetween(Instant.ofEpochSecond(2), Instant.ofEpochSecond(1)));
    }

    @Test void oversizedOrMalformedSearchPagesAreRejected() throws Exception {
        start(new ListenerAdapter() {});
        responses(request -> searchPage().replace("\"members\":[{", "\"members\":[{\"user_id\":\"7\",\"guild_id\":\"1\"},{"));
        var failure = assertThrows(ExecutionException.class, () -> client.searchMembers("1").setLimit(1).submit().get(3, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        responses(request -> searchPage().replace("\"user_id\":\"4\"", "\"user_id\":\"../4\""));
        assertThrows(ExecutionException.class, () -> client.searchMembers("1").submit().get(3, TimeUnit.SECONDS));
    }

    @Test void gatewayMemberRequestsAreSerializedAndCollectEveryChunk() throws Exception {
        start(new ListenerAdapter() {});
        var first = client.retrieveMembersByPrefix("1", "us", 50);
        var second = client.retrieveMembersByIds("1", "4", "5", "4");
        JsonNode request = opcode(8);
        assertEquals("1", request.path("d").path("guild_id").asText());
        assertEquals("us", request.path("d").path("query").asText());
        assertEquals(50, request.path("d").path("limit").asInt());
        String nonce = request.path("d").path("nonce").asText();
        assertFalse(nonce.isEmpty());
        assertTrue(nonce.length() <= 32);
        assertNull(nextOpcode(8, 300), "The second request must wait until the first completes");

        dispatch("GUILD_MEMBERS_CHUNK", "{\"guild_id\":\"1\",\"nonce\":\"other\",\"chunk_index\":0,\"chunk_count\":1,\"members\":[" + EntityCacheTest.member("9") + "]}");
        dispatch("GUILD_MEMBERS_CHUNK", "{\"guild_id\":\"1\",\"nonce\":\"" + nonce + "\",\"chunk_index\":1,\"chunk_count\":2,\"members\":[" + EntityCacheTest.member("6") + "]}");
        dispatch("GUILD_MEMBERS_CHUNK", "{\"guild_id\":\"1\",\"nonce\":\"" + nonce + "\",\"chunk_index\":0,\"chunk_count\":2,\"members\":[" + EntityCacheTest.member("5") + "]}");
        var members = first.setTimeout(Duration.ofSeconds(3)).get();
        assertEquals(List.of("6", "5"), members.stream().map(Member::getId).toList());
        assertEquals("1", members.getFirst().guildId());

        JsonNode next = opcode(8);
        assertEquals(List.of("4", "5"), values(next.path("d").path("user_ids")));
        assertNotEquals(nonce, next.path("d").path("nonce").asText());
        dispatch("GUILD_MEMBERS_CHUNK", "{\"guild_id\":\"1\",\"nonce\":\"" + next.path("d").path("nonce").asText() + "\",\"chunk_index\":0,\"chunk_count\":1,\"members\":[]}");
        assertTrue(second.setTimeout(Duration.ofSeconds(3)).get().isEmpty());
        assertTrue(errors.isEmpty(), errors.toString());
    }

    JsonNode nextOpcode(int op, long millis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < deadline) {
            JsonNode frame = frames.poll(20, TimeUnit.MILLISECONDS);
            if (frame != null && frame.path("op").asInt() == op) return frame;
        }
        return null;
    }

    static List<String> values(JsonNode array) {
        var values = new java.util.ArrayList<String>(); array.forEach(value -> values.add(value.asText())); return values;
    }

    @Test void fullMemberListRateLimitsAndCancellationReleaseTheConnection() throws Exception {
        start(new ListenerAdapter() {});
        var load = client.loadMembers("1");
        JsonNode request = opcode(8);
        assertEquals("", request.path("d").path("query").asText());
        assertEquals(0, request.path("d").path("limit").asInt());
        var queued = client.retrieveMembersByPrefix("1", "a", 1);
        var after = client.retrieveMembersByPrefix("1", "b", 1);
        queued.cancel();
        dispatch("RATE_LIMITED", "{\"opcode\":8,\"retry_after\":12.5,\"meta\":{\"guild_id\":\"1\",\"nonce\":\"" + request.path("d").path("nonce").asText() + "\"}}");
        var failure = assertThrows(CompletionException.class, load::get);
        var limited = assertInstanceOf(GatewayRateLimitException.class, failure.getCause());
        assertEquals(Duration.ofMillis(12_500), limited.getRetryAfter());
        assertThrows(CancellationException.class, queued::get);

        JsonNode next = opcode(8);
        assertEquals("b", next.path("d").path("query").asText(), "A cancelled queued request is never sent");
        var errorSeen = new java.util.concurrent.CompletableFuture<Throwable>();
        after.onError(errorSeen::complete).cancel();
        assertInstanceOf(CancellationException.class, errorSeen.get(3, TimeUnit.SECONDS));
        var replacement = client.retrieveMembersByPrefix("1", "c", 1);
        assertEquals("c", opcode(8).path("d").path("query").asText(), "Cancelling the running request frees the connection");
        replacement.cancel();

        assertThrows(IllegalArgumentException.class, () -> client.retrieveMembersByPrefix("1", "", 1));
        assertThrows(IllegalArgumentException.class, () -> client.retrieveMembersByPrefix("1", "a", 101));
        assertThrows(IllegalArgumentException.class, () -> client.retrieveMembersByIds("1", List.of()));
    }

    @Test void connectionLossFailsTheRunningRequestAndClosingFailsQueuedOnes() throws Exception {
        gatewayConfig = new com.swadedev.micup4j.config.GatewayConfig(Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofMillis(50), Duration.ofMillis(200));
        start(new ListenerAdapter() {});
        var running = client.retrieveMembersByPrefix("1", "a", 1);
        opcode(8);
        var queued = client.retrieveMembersByPrefix("1", "b", 1);
        sockets.poll(2, TimeUnit.SECONDS).send("{\"op\":7}");
        var lost = assertThrows(CompletionException.class, () -> running.setTimeout(Duration.ofSeconds(5)).get());
        assertInstanceOf(IllegalStateException.class, lost.getCause());
        assertEquals("b", opcode(8).path("d").path("query").asText(), "Queued requests are sent after reconnecting");
        client.close();
        var closed = assertThrows(CompletionException.class, () -> queued.setTimeout(Duration.ofSeconds(3)).get());
        assertInstanceOf(IllegalStateException.class, closed.getCause());
    }
}
