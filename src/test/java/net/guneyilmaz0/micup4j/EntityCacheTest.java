package net.guneyilmaz0.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.config.CachePolicy;
import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.internal.*;
import net.guneyilmaz0.micup4j.permissions.*;
import net.guneyilmaz0.micup4j.permissions.Permission;
import org.junit.jupiter.api.Test;
import java.util.*;
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
    @Test void invalidPoliciesAndIgnoredStateEventsAreRejectedBeforeConnection() {
        assertThrows(IllegalArgumentException.class, () -> new CachePolicy(-1, 1, 1, 1));
        assertThrows(IllegalStateException.class, () -> MicupBuilder.createDefault("test-token").setIgnoredEvents("GUILD_ROLE_UPDATE").build());
    }
    @Test void missingPermissionInputsAreRejectedInsteadOfAssumedEmpty() {
        assertThrows(IllegalArgumentException.class, () -> Member.fromJson("1", json("{\"user\":{\"id\":\"4\"}}")));
        assertThrows(IllegalArgumentException.class, () -> GuildChannel.fromJson("1", json("{\"id\":\"8\",\"type\":0}")));
        assertThrows(IllegalArgumentException.class, () -> Role.fromJson("1", json("{\"id\":\"2\"}")));
    }

}
