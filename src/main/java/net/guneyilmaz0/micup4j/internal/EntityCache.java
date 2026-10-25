package net.guneyilmaz0.micup4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.config.CachePolicy;
import net.guneyilmaz0.micup4j.entities.*;
import java.util.*;
import java.util.function.Function;

/** Gateway-owned state. Every public view is an immutable snapshot; HTTP reads never overwrite it. */
public final class EntityCache {
    private static final Set<String> EVENTS = Set.of("READY", "GUILD_CREATE", "GUILD_SYNC", "GUILD_UPDATE", "GUILD_DELETE",
            "GUILD_ROLE_CREATE", "GUILD_ROLE_UPDATE", "GUILD_ROLE_UPDATE_BULK", "GUILD_ROLE_DELETE", "CHANNEL_CREATE",
            "CHANNEL_UPDATE", "CHANNEL_UPDATE_BULK", "CHANNEL_DELETE", "GUILD_MEMBER_ADD", "GUILD_MEMBER_UPDATE",
            "GUILD_MEMBER_REMOVE", "GUILD_MEMBERS_CHUNK");
    private final CachePolicy policy;
    private final LinkedHashMap<String, Guild> guilds = new LinkedHashMap<>();
    public EntityCache(CachePolicy policy) { this.policy = Objects.requireNonNull(policy); }
    public static boolean isEntityEvent(String type) { return EVENTS.contains(type); }
    public boolean accepts(String type) { return policy.maxGuilds() > 0 && isEntityEvent(type); }
    public synchronized Optional<Guild> getGuild(String id) { return Optional.ofNullable(guilds.get(id)); }
    public synchronized List<Guild> getGuilds() { return List.copyOf(guilds.values()); }
    public synchronized void clear() { guilds.clear(); }

    public synchronized void apply(String type, JsonNode data) {
        if (!accepts(type)) return;
        if (type.equals("READY")) {
            guilds.clear();
            for (var guild : data.path("guilds")) put(bound(Guild.fromJson(guild)));
            return;
        }
        String id = data.path("guild_id").asText(data.path("id").asText(""));
        // Private channel events have no guild ID and must never create a guild entry.
        if (type.startsWith("CHANNEL_") && !data.hasNonNull("guild_id")) return;
        Json.id(id);
        Guild old = guilds.getOrDefault(id, new Guild(id, "", null, false, List.of(), List.of(), List.of(), false, false));
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
                if (!data.path("unavailable").asBoolean()) { guilds.remove(id); return; }
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
        put(bound(next));
    }
    private Guild bound(Guild guild) {
        return copy(guild, tail(guild.roles(), policy.maxRolesPerGuild()), tail(guild.channels(), policy.maxChannelsPerGuild()),
                tail(guild.members(), policy.maxMembersPerGuild()),
                guild.rolesComplete() && policy.maxRolesPerGuild() > 0 && guild.roles().size() <= policy.maxRolesPerGuild(),
                guild.channelsComplete() && policy.maxChannelsPerGuild() > 0 && guild.channels().size() <= policy.maxChannelsPerGuild());
    }
    private void put(Guild guild) {
        guilds.remove(guild.id()); guilds.put(guild.id(), guild);
        while (guilds.size() > policy.maxGuilds()) guilds.remove(guilds.keySet().iterator().next());
    }
    private static Guild copy(Guild g, List<Role> roles, List<GuildChannel> channels, List<Member> members, boolean rolesComplete, boolean channelsComplete) {
        return new Guild(g.id(), g.name(), g.ownerId(), g.available(), roles, channels, members, rolesComplete, channelsComplete);
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
