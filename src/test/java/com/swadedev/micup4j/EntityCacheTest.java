package com.swadedev.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.config.CachePolicy;
import com.swadedev.micup4j.entities.*;
import com.swadedev.micup4j.internal.*;
import com.swadedev.micup4j.permissions.*;
import com.swadedev.micup4j.permissions.Permission;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

class EntityCacheTest {
    static JsonNode json(String value) { return Json.parse(value); }
    static String role(String id, int position) { return "{\"id\":\"" + id + "\",\"position\":" + position + ",\"name\":\"R\",\"permissions\":\"3072\"}"; }
    static String member(String id) { return "{\"user\":{\"id\":\"" + id + "\",\"username\":\"User\"},\"roles\":[\"2\"]}"; }
    static JsonNode snapshot() {
        return json("{\"id\":\"1\",\"properties\":{\"name\":\"Guild\",\"owner_id\":\"9\"},\"roles\":[" + role("1", 0)
                + "," + role("2", 1) + "],\"channels\":[{\"id\":\"8\",\"type\":0,\"name\":\"chat\",\"permission_overwrites\":[]}],\"members\":[" + member("4") + "]}");
    }
    EntityCache cache() { return new EntityCache(CachePolicy.defaults()); }

    @Test void nestedSnapshotsAndIncrementalEventsProduceEquivalentState() {
        EntityCache fromSnapshot = cache(), incremental = cache();
        fromSnapshot.apply("GUILD_CREATE", snapshot());
        incremental.apply("GUILD_CREATE", json("{\"id\":\"1\",\"name\":\"Guild\",\"owner_id\":\"9\",\"roles\":[],\"channels\":[],\"members\":[]}"));
        incremental.apply("GUILD_ROLE_CREATE", json("{\"guild_id\":\"1\",\"role\":" + role("1", 0) + "}"));
        incremental.apply("GUILD_ROLE_UPDATE_BULK", json("{\"guild_id\":\"1\",\"roles\":[" + role("2", 1) + "]}"));
        incremental.apply("CHANNEL_CREATE", json("{\"id\":\"8\",\"guild_id\":\"1\",\"type\":0,\"name\":\"chat\",\"permission_overwrites\":[]}"));
        incremental.apply("GUILD_MEMBER_ADD", json("{\"guild_id\":\"1\",\"user\":{\"id\":\"4\",\"username\":\"User\"},\"roles\":[\"2\"]}"));
        assertEquals(fromSnapshot.getGuild("1"), incremental.getGuild("1"));
        Guild g = incremental.getGuild("1").orElseThrow();
        assertTrue(PermissionResolver.channelPermissions(g, g.findMemberById("4").orElseThrow(), g.findChannelById("8").orElseThrow())
                .contains(Permission.SEND_MESSAGES));
    }
    @Test void syncReplacesRolesAndChannelsButMergesPartialMembers() {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        cache.apply("GUILD_MEMBERS_CHUNK", json("{\"guild_id\":\"1\",\"members\":[" + member("5") + "]}"));
        cache.apply("GUILD_SYNC", json("{\"id\":\"1\",\"properties\":{\"name\":\"New\",\"owner_id\":\"9\"},\"roles\":[],\"channels\":[],\"members\":[]}"));
        Guild g = cache.getGuild("1").orElseThrow();
        assertEquals("New", g.name()); assertTrue(g.roles().isEmpty()); assertTrue(g.channels().isEmpty());
        assertEquals(2, g.members().size());
    }
    @Test void updatesAndDeletesNeverMutatePreviouslyReturnedSnapshots() {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        Guild previous = cache.getGuild("1").orElseThrow();
        cache.apply("GUILD_ROLE_DELETE", json("{\"guild_id\":\"1\",\"role_id\":\"2\"}"));
        Guild current = cache.getGuild("1").orElseThrow();
        assertEquals(1, current.roles().size()); assertTrue(current.members().getFirst().roleIds().isEmpty());
        assertEquals(2, previous.roles().size()); assertEquals(List.of("2"), previous.members().getFirst().roleIds());
        assertThrows(UnsupportedOperationException.class, () -> previous.roles().clear());
        cache.apply("CHANNEL_DELETE", json("{\"guild_id\":\"1\",\"id\":\"8\"}"));
        cache.apply("GUILD_MEMBER_REMOVE", json("{\"guild_id\":\"1\",\"user\":{\"id\":\"4\"}}"));
        assertTrue(cache.getGuild("1").orElseThrow().channels().isEmpty());
        assertTrue(cache.getGuild("1").orElseThrow().members().isEmpty());
    }
    @Test void unavailableGuildsDiscardPermissionStateAndPermanentDeletesRemoveIdentity() {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        cache.apply("GUILD_DELETE", json("{\"id\":\"1\",\"unavailable\":true}"));
        Guild unavailable = cache.getGuild("1").orElseThrow();
        assertFalse(unavailable.available()); assertFalse(unavailable.rolesComplete()); assertTrue(unavailable.members().isEmpty());
        cache.apply("GUILD_CREATE", snapshot()); assertTrue(cache.getGuild("1").orElseThrow().available());
        cache.apply("GUILD_DELETE", json("{\"id\":\"1\"}")); assertTrue(cache.getGuilds().isEmpty());
    }
    @Test void limitsEvictOldestUpdatesAndPreventPermissionUseOfTruncatedRoles() {
        var cache = new EntityCache(new CachePolicy(1, 1, 1, 1));
        cache.apply("GUILD_CREATE", snapshot());
        Guild limited = cache.getGuild("1").orElseThrow();
        assertEquals(1, limited.roles().size()); assertFalse(limited.rolesComplete());
        assertThrows(IllegalStateException.class, () -> PermissionResolver.guildPermissions(limited, limited.members().getFirst()));
        cache.apply("GUILD_MEMBERS_CHUNK", json("{\"guild_id\":\"1\",\"members\":[" + member("5") + "," + member("6") + "]}"));
        assertEquals("6", cache.getGuild("1").orElseThrow().members().getFirst().getId());
        cache.apply("GUILD_CREATE", json("{\"id\":\"7\",\"name\":\"Other\"}"));
        assertTrue(cache.getGuild("1").isEmpty()); assertEquals(1, cache.getGuilds().size());
    }
    @Test void readyClearsOldSessionAndLightCacheRetainsNothing() {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        cache.apply("READY", json("{\"guilds\":[{\"id\":\"7\",\"unavailable\":true}]}"));
        assertTrue(cache.getGuild("1").isEmpty()); assertFalse(cache.getGuild("7").orElseThrow().available());
        var light = new EntityCache(CachePolicy.light()); light.apply("GUILD_CREATE", snapshot()); assertTrue(light.getGuilds().isEmpty());
        cache.clear(); assertTrue(cache.getGuilds().isEmpty());
    }
    @Test void privateAndUnrelatedEventsDoNotPolluteGuildCache() {
        var cache = cache(); cache.apply("CHANNEL_CREATE", json("{\"id\":\"9\",\"type\":1}"));
        cache.apply("MESSAGE_CREATE", json("{\"id\":\"8\",\"guild_id\":\"1\"}")); assertTrue(cache.getGuilds().isEmpty());
    }
    @Test void fullGuildUpdatesPreserveCollectionsAndChannelBulkUpdatesReplaceRecords() {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        cache.apply("GUILD_UPDATE", json("{\"id\":\"1\",\"guild_id\":\"1\",\"name\":\"Renamed\",\"owner_id\":\"9\"}"));
        cache.apply("CHANNEL_UPDATE_BULK", json("{\"guild_id\":\"1\",\"channels\":[{\"id\":\"8\",\"type\":2,\"name\":\"voice\",\"permission_overwrites\":[]}]}"));
        Guild g = cache.getGuild("1").orElseThrow(); assertEquals("Renamed", g.name());
        assertEquals(2, g.roles().size()); assertEquals(ChannelType.VOICE, g.channels().getFirst().getType());
        assertTrue(g.channels().getFirst().canHaveMessages());
    }
    @Test void partialEventsForUncachedGuildsNeitherInsertStubsNorEvictRealGuilds() {
        var cache = new EntityCache(new CachePolicy(1, 10, 10, 10));
        cache.apply("GUILD_CREATE", snapshot());
        Guild real = cache.getGuild("1").orElseThrow();
        cache.apply("GUILD_UPDATE", json("{\"id\":\"2\",\"guild_id\":\"2\",\"name\":\"Ghost\",\"owner_id\":\"9\"}"));
        cache.apply("GUILD_ROLE_CREATE", json("{\"guild_id\":\"2\",\"role\":" + role("3", 0) + "}"));
        cache.apply("GUILD_ROLE_UPDATE", json("{\"guild_id\":\"2\",\"role\":" + role("3", 0) + "}"));
        cache.apply("GUILD_ROLE_UPDATE_BULK", json("{\"guild_id\":\"2\",\"roles\":[" + role("3", 0) + "]}"));
        cache.apply("GUILD_ROLE_DELETE", json("{\"guild_id\":\"2\",\"role_id\":\"3\"}"));
        cache.apply("CHANNEL_CREATE", json("{\"id\":\"5\",\"guild_id\":\"2\",\"type\":0,\"name\":\"chat\",\"permission_overwrites\":[]}"));
        cache.apply("CHANNEL_UPDATE", json("{\"id\":\"5\",\"guild_id\":\"2\",\"type\":0,\"name\":\"chat\",\"permission_overwrites\":[]}"));
        cache.apply("CHANNEL_UPDATE_BULK", json("{\"guild_id\":\"2\",\"channels\":[]}"));
        cache.apply("CHANNEL_DELETE", json("{\"id\":\"5\",\"guild_id\":\"2\"}"));
        cache.apply("GUILD_MEMBER_ADD", json("{\"guild_id\":\"2\",\"user\":{\"id\":\"6\",\"username\":\"User\"},\"roles\":[]}"));
        cache.apply("GUILD_MEMBER_UPDATE", json("{\"guild_id\":\"2\",\"user\":{\"id\":\"6\",\"username\":\"User\"},\"roles\":[]}"));
        cache.apply("GUILD_MEMBERS_CHUNK", json("{\"guild_id\":\"2\",\"members\":[" + member("6") + "]}"));
        cache.apply("GUILD_MEMBER_REMOVE", json("{\"guild_id\":\"2\",\"user\":{\"id\":\"6\"}}"));
        cache.apply("GUILD_DELETE", json("{\"id\":\"2\"}"));
        assertTrue(cache.getGuild("2").isEmpty());
        assertEquals(List.of(real), cache.getGuilds());
        assertSame(real, cache.getGuild("1").orElseThrow());
        // Full snapshots and unavailable deletes still introduce (and, at the bound, evict for) new identities.
        cache.apply("GUILD_DELETE", json("{\"id\":\"2\",\"unavailable\":true}"));
        assertFalse(cache.getGuild("2").orElseThrow().available()); assertTrue(cache.getGuild("1").isEmpty());
        cache.apply("GUILD_SYNC", snapshot()); assertEquals("Guild", cache.getGuild("1").orElseThrow().name());
        assertEquals(1, cache.getGuilds().size());
    }
    @Test void channelIndexFollowsChannelGuildEvictionAndClearEvents() {
        var cache = new EntityCache(new CachePolicy(1, 10, 10, 10));
        cache.apply("GUILD_CREATE", snapshot());
        assertEquals("1", cache.getChannel("8").orElseThrow().guildId());
        cache.apply("CHANNEL_CREATE", json("{\"id\":\"9\",\"guild_id\":\"1\",\"type\":0,\"name\":\"new\",\"permission_overwrites\":[]}"));
        assertEquals("new", cache.getChannel("9").orElseThrow().name());
        cache.apply("CHANNEL_UPDATE", json("{\"id\":\"9\",\"guild_id\":\"1\",\"type\":2,\"name\":\"renamed\",\"permission_overwrites\":[]}"));
        assertEquals("renamed", cache.getChannel("9").orElseThrow().name()); assertEquals(2, cache.getChannel("9").orElseThrow().rawType());
        cache.apply("CHANNEL_DELETE", json("{\"id\":\"9\",\"guild_id\":\"1\"}"));
        assertTrue(cache.getChannel("9").isEmpty()); assertTrue(cache.getChannel("8").isPresent());
        // A sync replaces the channel list; channels absent from it leave the index.
        cache.apply("GUILD_SYNC", json("{\"id\":\"1\",\"name\":\"Guild\",\"roles\":[],\"channels\":[{\"id\":\"7\",\"type\":0,\"name\":\"x\",\"permission_overwrites\":[]}],\"members\":[]}"));
        assertTrue(cache.getChannel("8").isEmpty()); assertTrue(cache.getChannel("7").isPresent());
        // Eviction under maxGuilds=1 drops the evicted guild's channels.
        cache.apply("GUILD_CREATE", json("{\"id\":\"2\",\"name\":\"Other\",\"roles\":[],\"channels\":[{\"id\":\"6\",\"type\":0,\"name\":\"y\",\"permission_overwrites\":[]}],\"members\":[]}"));
        assertTrue(cache.getGuild("1").isEmpty()); assertTrue(cache.getChannel("7").isEmpty());
        assertEquals("2", cache.getChannel("6").orElseThrow().guildId());
        // An unavailable delete keeps the identity but discards channels; a real delete drops everything.
        cache.apply("GUILD_DELETE", json("{\"id\":\"2\",\"unavailable\":true}")); assertTrue(cache.getChannel("6").isEmpty());
        cache.apply("GUILD_CREATE", json("{\"id\":\"2\",\"name\":\"Other\",\"roles\":[],\"channels\":[{\"id\":\"6\",\"type\":0,\"name\":\"y\",\"permission_overwrites\":[]}],\"members\":[]}"));
        cache.apply("GUILD_DELETE", json("{\"id\":\"2\"}")); assertTrue(cache.getChannel("6").isEmpty());
        cache.apply("GUILD_CREATE", snapshot()); cache.clear(); assertTrue(cache.getChannel("8").isEmpty());
        cache.apply("GUILD_CREATE", snapshot()); cache.apply("READY", json("{\"guilds\":[]}")); assertTrue(cache.getChannel("8").isEmpty());
    }
    @Test void invalidPoliciesAndIgnoredStateEventsAreRejectedBeforeConnection() {
        assertThrows(IllegalArgumentException.class, () -> new CachePolicy(-1, 1, 1, 1));
        assertThrows(IllegalStateException.class, () -> MicupBuilder.createDefault("test-token").setIgnoredEvents("GUILD_ROLE_UPDATE").build());
    }
    @Test void missingPermissionInputsAreRejectedInsteadOfAssumedEmpty() {
        assertThrows(IllegalArgumentException.class, () -> Member.fromJson("1", json("{\"user\":{\"id\":\"4\"}}")));
        assertThrows(IllegalArgumentException.class, () -> GuildChannel.fromJson("1", json("{\"id\":\"8\",\"type\":0}")));
        assertThrows(IllegalArgumentException.class, () -> Role.fromJson("1", json("{\"id\":\"2\"}")));
    }


    @Test void memberRemoveEventReadsThePreRemovalSnapshot() {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        JsonNode data = json("{\"guild_id\":\"1\",\"user\":{\"id\":\"4\"}}");
        Guild before = cache.getGuild("1").orElseThrow();
        cache.apply("GUILD_MEMBER_REMOVE", data);
        Guild after = cache.getGuild("1").orElseThrow();
        var raw = new com.swadedev.micup4j.events.GenericEvent(null, "GUILD_MEMBER_REMOVE", 7, data);
        var event = assertInstanceOf(com.swadedev.micup4j.events.guild.member.GuildMemberRemoveEvent.class,
                MemberEvents.apply(null, raw, data, before, after));
        assertEquals("4", event.getMember().getId()); assertEquals("User", event.getUser().getName());
        assertSame(after, event.getGuild()); assertNull(after.getMemberById("4")); assertEquals(7, event.getSequence());
        assertTrue(MemberEvents.accepts("GUILD_MEMBER_ADD")); assertFalse(MemberEvents.accepts("GUILD_MEMBER_UPDATE"));
        var unrelated = new com.swadedev.micup4j.events.GenericEvent(null, "GUILD_MEMBER_UPDATE", 8, data);
        assertSame(unrelated, MemberEvents.apply(null, unrelated, data, before, after));
    }

    @Test void memberRemoveForEvictedOrUncachedMemberYieldsNullMemberWithoutFailing() {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        JsonNode data = json("{\"guild_id\":\"1\",\"user\":{\"id\":\"77\",\"username\":\"Gone\"}}");
        Guild before = cache.getGuild("1").orElseThrow();
        cache.apply("GUILD_MEMBER_REMOVE", data);
        Guild after = cache.getGuild("1").orElseThrow();
        var raw = new com.swadedev.micup4j.events.GenericEvent(null, "GUILD_MEMBER_REMOVE", 9, data);
        var event = assertInstanceOf(com.swadedev.micup4j.events.guild.member.GuildMemberRemoveEvent.class,
                MemberEvents.apply(null, raw, data, before, after));
        assertNull(event.getMember()); assertEquals("77", event.getUser().getId()); assertSame(after, event.getGuild());
        assertEquals(1, after.getMembers().size());
    }
    @Test void guildSyncDropsOnlyThatGuildsChannelsFromTheIndexAndKeepsOtherGuilds() {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        cache.apply("GUILD_CREATE", json("{\"id\":\"2\",\"name\":\"Other\",\"roles\":[],\"channels\":[{\"id\":\"6\",\"type\":0,\"name\":\"y\",\"permission_overwrites\":[]}],\"members\":[]}"));
        cache.apply("GUILD_SYNC", json("{\"id\":\"1\",\"name\":\"Guild\",\"roles\":[],\"channels\":[{\"id\":\"7\",\"type\":0,\"name\":\"x\",\"permission_overwrites\":[]}],\"members\":[]}"));
        assertTrue(cache.getChannel("8").isEmpty()); assertEquals("1", cache.getChannel("7").orElseThrow().guildId());
        assertEquals("2", cache.getChannel("6").orElseThrow().guildId());
        // A channel that moves to another guild via sync is indexed under its new owner and survives the old guild's next sync.
        cache.apply("GUILD_SYNC", json("{\"id\":\"2\",\"name\":\"Other\",\"roles\":[],\"channels\":[{\"id\":\"7\",\"type\":0,\"name\":\"x\",\"permission_overwrites\":[]}],\"members\":[]}"));
        assertEquals("2", cache.getChannel("7").orElseThrow().guildId()); assertTrue(cache.getChannel("6").isEmpty());
        cache.apply("GUILD_SYNC", json("{\"id\":\"1\",\"name\":\"Guild\",\"roles\":[],\"channels\":[],\"members\":[]}"));
        assertEquals("2", cache.getChannel("7").orElseThrow().guildId());
    }

    /** An object node whose first {@code path(String)} call (made while the writer parses) parks until released. */
    static final class BlockingNode extends ObjectNode {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        BlockingNode() { super(JsonNodeFactory.instance); }
        @Override public JsonNode path(String fieldName) {
            if (entered.getCount() > 0) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("writer never released"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return super.path(fieldName);
        }
    }
    @Test void readersAreNotBlockedWhileAWriterIsMidParse() throws Exception {
        var cache = cache(); cache.apply("GUILD_CREATE", snapshot());
        var payload = new BlockingNode();
        payload.put("id", "1"); payload.put("name", "Renamed");
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = pool.submit(() -> cache.apply("GUILD_UPDATE", payload));
            assertTrue(payload.entered.await(5, TimeUnit.SECONDS), "writer never started parsing");
            Future<String> reader = pool.submit(() -> {
                String name = cache.getGuild("1").orElseThrow().name();
                cache.getChannel("8").orElseThrow(); cache.getGuilds();
                return name;
            });
            assertEquals("Guild", reader.get(5, TimeUnit.SECONDS), "reader was blocked by a writer mid-parse");
            assertFalse(writer.isDone(), "writer should still be parked mid-parse");
            payload.release.countDown();
            writer.get(5, TimeUnit.SECONDS);
            assertEquals("Renamed", cache.getGuild("1").orElseThrow().name());
        } finally { payload.release.countDown(); pool.shutdownNow(); }
    }
}
