package com.swadedev.micup4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.config.CachePolicy;
import com.swadedev.micup4j.entities.*;
import java.util.*;
import java.util.function.Function;

/**
 * Gateway-owned state. Every public view is an immutable snapshot; HTTP reads never overwrite it.
 *
 * <p>Snapshots are bound to the owning client once, when they are written, so reads return the stored instance without
 * copying members. A channel ID index is maintained alongside the guild map for constant-time channel lookups.
 *
 * <p>Locking: writers ({@link #apply} and {@link #clear}) are serialized by an internal write lock, so the cache stays
 * safe for direct callers. A writer parses and builds the next snapshot holding only that write lock, and takes this
 * instance's monitor just for the swap of the guild map and channel index. Readers take only the monitor, so they never
 * wait for a large payload such as a full {@code GUILD_CREATE} or a member chunk to be parsed. Lock order is always
 * write lock, then monitor.
 */
public final class EntityCache {
    private static final Set<String> EVENTS = Set.of("READY", "GUILD_CREATE", "GUILD_SYNC", "GUILD_UPDATE", "GUILD_DELETE",
            "GUILD_ROLE_CREATE", "GUILD_ROLE_UPDATE", "GUILD_ROLE_UPDATE_BULK", "GUILD_ROLE_DELETE", "CHANNEL_CREATE",
            "CHANNEL_UPDATE", "CHANNEL_UPDATE_BULK", "CHANNEL_DELETE", "GUILD_MEMBER_ADD", "GUILD_MEMBER_UPDATE",
            "GUILD_MEMBER_REMOVE", "GUILD_MEMBERS_CHUNK");
    private final CachePolicy policy;
    private final com.swadedev.micup4j.Micup client;
    private final LinkedHashMap<String, Guild> guilds = new LinkedHashMap<>();
    /** Channel ID to the cached guild channel snapshot; always mirrors the channels of {@link #guilds}. */
    private final HashMap<String, GuildChannel> channels = new HashMap<>();
    /**
     * Serializes writers. Every mutation of {@link #guilds} and {@link #channels} holds it (and the monitor), so a writer
     * may read {@link #guilds} without the monitor while building the next snapshot.
     */
    private final Object writeLock = new Object();
    /** Creates a detached cache whose snapshots carry no client (tests and tooling). */
    public EntityCache(CachePolicy policy) { this(policy, null); }
    /** Creates a cache whose stored snapshots are bound to {@code client} on write; {@code client} may be null. */
    public EntityCache(CachePolicy policy, com.swadedev.micup4j.Micup client) {
        this.policy = Objects.requireNonNull(policy); this.client = client;
    }
    /**
     * Returns whether the dispatch type changes cached entities.
     *
     * @param type the Micup dispatch type
     * @return {@code true} for guild, role, channel and member dispatches handled by the cache
     */
    public static boolean isEntityEvent(String type) { return EVENTS.contains(type); }
    /**
     * Returns whether this cache applies the dispatch type; always {@code false} when the policy caches no guilds.
     *
     * @param type the Micup dispatch type
     * @return {@code true} if {@link #apply} would process the type
     */
    public boolean accepts(String type) { return policy.maxGuilds() > 0 && isEntityEvent(type); }
    /**
     * Returns the stored snapshot of a guild.
     *
     * @param id the guild id
     * @return the snapshot, or empty if not cached
     */
    public synchronized Optional<Guild> getGuild(String id) { return Optional.ofNullable(guilds.get(id)); }
    /**
     * Returns a copy of all stored snapshots in insertion order.
     *
     * @return an immutable list
     */
    public synchronized List<Guild> getGuilds() { return List.copyOf(guilds.values()); }
    /** The cached guild channel with this ID, or empty when no cached guild exposes it. Constant time. */
    public synchronized Optional<GuildChannel> getChannel(String id) { return Optional.ofNullable(channels.get(id)); }
    /** Drops every cached guild and channel. */
    public void clear() {
        synchronized (writeLock) { synchronized (this) { guilds.clear(); channels.clear(); } }
    }

    /**
     * Applies one dispatch to the cache. Unaccepted types and partial events for uncached guilds are ignored.
     *
     * <p>Concurrent calls are serialized internally. The owning client additionally calls it on the event thread, under
     * its cache lock, so snapshots and lifecycle events stay consistent. The payload is parsed without blocking readers;
     * only the final swap holds this instance's monitor.
     *
     * @param type the Micup dispatch type
     * @param data the read-only dispatch payload
     * @throws IllegalArgumentException if an id in the payload is not a canonical snowflake
     */
    public void apply(String type, JsonNode data) {
        if (!accepts(type)) return;
        synchronized (writeLock) { applyLocked(type, data); }
    }
    /** Builds the next state holding only {@link #writeLock}; the monitor is taken just to swap it in. */
    private void applyLocked(String type, JsonNode data) {
        if (type.equals("READY")) {
            var prepared = new ArrayList<Guild>();
            for (var guild : data.path("guilds")) prepared.add(prepare(Guild.fromJson(guild)));
            synchronized (this) {
                guilds.clear(); channels.clear();
                for (Guild guild : prepared) store(guild);
            }
            return;
        }
        String id = data.path("guild_id").asText(data.path("id").asText(""));
        // Private channel events have no guild ID and must never create a guild entry.
        if (type.startsWith("CHANNEL_") && !data.hasNonNull("guild_id")) return;
        Json.id(id);
        // Read without the monitor: every mutation holds writeLock, which this thread owns.
        Guild cached = guilds.get(id);
        // Only full snapshots (create/sync) and unavailable deletes may introduce a guild identity. Partial events for an
        // uncached guild must neither insert a placeholder nor evict a real guild under the maxGuilds bound.
        boolean createsIdentity = type.equals("GUILD_CREATE") || type.equals("GUILD_SYNC")
                || (type.equals("GUILD_DELETE") && data.path("unavailable").asBoolean());
        if (cached == null && !createsIdentity) return;
        Guild old = cached != null ? cached : new Guild(id, "", null, false, List.of(), List.of(), List.of(), false, false);
        Guild next = old;
        switch (type) {
            case "GUILD_CREATE", "GUILD_SYNC" -> {
                Guild incoming = Guild.fromJson(data);
                var members = merge(old.members(), incoming.members(), Member::getId, policy.maxMembersPerGuild());
                next = new Guild(id, incoming.name(), incoming.ownerId(), incoming.available(), incoming.roles(),
                        incoming.channels(), members, incoming.rolesComplete(), incoming.channelsComplete());
            }
            case "GUILD_UPDATE" -> {
                Guild incoming = Guild.fromJson(data);
                next = new Guild(id, incoming.name(), incoming.ownerId(), data.has("unavailable") ? incoming.available() : old.available(),
                        old.roles(), old.channels(), old.members(), old.rolesComplete(), old.channelsComplete());
            }
            case "GUILD_DELETE" -> {
                if (!data.path("unavailable").asBoolean()) { synchronized (this) { remove(id); } return; }
                // Keep an unavailable identity, but discard data that could grant stale permissions.
                next = new Guild(id, old.name(), old.ownerId(), false, List.of(), List.of(), List.of(), false, false);
            }
            case "GUILD_ROLE_CREATE", "GUILD_ROLE_UPDATE", "GUILD_ROLE_UPDATE_BULK" -> {
                var incoming = new ArrayList<Role>();
                if (type.equals("GUILD_ROLE_UPDATE_BULK")) for (var role : data.path("roles")) incoming.add(Role.fromJson(id, role));
                else incoming.add(Role.fromJson(id, data.path("role")));
                var all = merge(old.roles(), incoming, Role::id, Integer.MAX_VALUE);
                next = copy(old, all, old.channels(), old.members(), old.rolesComplete(), old.channelsComplete());
            }
            case "GUILD_ROLE_DELETE" -> {
                String roleId = Json.id(data.path("role_id").asText());
                next = copy(old, old.roles().stream().filter(r -> !r.id().equals(roleId)).toList(), old.channels(),
                        old.members().stream().map(m -> m.withoutRole(roleId)).toList(), old.rolesComplete(), old.channelsComplete());
            }
            case "CHANNEL_CREATE", "CHANNEL_UPDATE", "CHANNEL_UPDATE_BULK" -> {
                var incoming = new ArrayList<GuildChannel>();
                if (type.equals("CHANNEL_UPDATE_BULK")) for (var channel : data.path("channels")) incoming.add(GuildChannel.fromJson(id, channel));
                else incoming.add(GuildChannel.fromJson(id, data));
                next = copy(old, old.roles(), merge(old.channels(), incoming, GuildChannel::id, Integer.MAX_VALUE), old.members(),
                        old.rolesComplete(), old.channelsComplete());
            }
            case "CHANNEL_DELETE" -> next = copy(old, old.roles(), old.channels().stream()
                    .filter(c -> !c.id().equals(data.path("id").asText())).toList(), old.members(), old.rolesComplete(), old.channelsComplete());
            case "GUILD_MEMBER_ADD", "GUILD_MEMBER_UPDATE", "GUILD_MEMBERS_CHUNK" -> {
                var incoming = new ArrayList<Member>();
                if (type.equals("GUILD_MEMBERS_CHUNK")) for (var member : data.path("members")) incoming.add(Member.fromJson(id, member));
                else incoming.add(Member.fromJson(id, data));
                next = copy(old, old.roles(), old.channels(), merge(old.members(), incoming, Member::getId, policy.maxMembersPerGuild()),
                        old.rolesComplete(), old.channelsComplete());
            }
            case "GUILD_MEMBER_REMOVE" -> next = copy(old, old.roles(), old.channels(), old.members().stream()
                    .filter(m -> !m.getId().equals(data.path("user").path("id").asText())).toList(), old.rolesComplete(), old.channelsComplete());
            default -> { }
        }
        Guild stored = prepare(next);
        synchronized (this) { store(stored); }
    }
    private Guild bound(Guild guild) {
        return copy(guild, tail(guild.roles(), policy.maxRolesPerGuild()), tail(guild.channels(), policy.maxChannelsPerGuild()),
                tail(guild.members(), policy.maxMembersPerGuild()),
                guild.rolesComplete() && policy.maxRolesPerGuild() > 0 && guild.roles().size() <= policy.maxRolesPerGuild(),
                guild.channelsComplete() && policy.maxChannelsPerGuild() > 0 && guild.channels().size() <= policy.maxChannelsPerGuild());
    }
    /** Applies the policy bounds and binds once on write (members already bound to this client are reused). No lock needed. */
    private Guild prepare(Guild guild) {
        Guild bounded = bound(guild);
        return client == null ? bounded : bounded.withClient(client);
    }
    /** Stores a prepared snapshot, re-indexes its channels and evicts the oldest guilds over the bound. Caller holds the monitor. */
    private void store(Guild stored) {
        remove(stored.id());
        guilds.put(stored.id(), stored);
        for (GuildChannel channel : stored.channels()) channels.put(channel.id(), channel);
        while (guilds.size() > policy.maxGuilds()) remove(guilds.keySet().iterator().next());
    }
    /** Caller holds the monitor. */
    private void remove(String id) {
        Guild removed = guilds.remove(id);
        if (removed == null) return;
        // Only drop index entries still owned by this guild, in case a channel ID was re-homed by a newer snapshot.
        for (GuildChannel channel : removed.channels()) channels.remove(channel.id(), channel);
    }
    /** Preserves the source snapshot's client binding so unchanged members are not rebound on the next write. */
    private static Guild copy(Guild g, List<Role> roles, List<GuildChannel> channels, List<Member> members, boolean rolesComplete, boolean channelsComplete) {
        return new Guild(g.id(), g.name(), g.ownerId(), g.available(), roles, channels, members, rolesComplete, channelsComplete, g.client());
    }
    private static <T> List<T> tail(List<T> values, int maximum) {
        return List.copyOf(values.subList(Math.max(0, values.size() - maximum), values.size()));
    }
    private static <T> List<T> merge(List<T> old, List<T> incoming, Function<T, String> key, int maximum) {
        var values = new LinkedHashMap<String, T>();
        for (T value : old) values.put(key.apply(value), value);
        for (T value : incoming) { values.remove(key.apply(value)); values.put(key.apply(value), value); }
        return tail(new ArrayList<>(values.values()), maximum);
    }
}
