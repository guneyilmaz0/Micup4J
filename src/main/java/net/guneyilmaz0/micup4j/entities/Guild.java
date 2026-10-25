package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.*;

/** Immutable snapshot. Members and visible channels never imply a complete guild roster. */
public record Guild(String id, String name, String ownerId, boolean available,
                    List<Role> roles, List<GuildChannel> channels, List<Member> members,
                    boolean rolesComplete, boolean channelsComplete) {
    public Guild {
        Json.id(id); Objects.requireNonNull(name);
        if (ownerId != null) Json.id(ownerId);
        roles = List.copyOf(roles); channels = List.copyOf(channels); members = List.copyOf(members);
        if (roles.stream().anyMatch(r -> !r.guildId().equals(id)) || channels.stream().anyMatch(c -> !c.guildId().equals(id))
                || members.stream().anyMatch(m -> !m.guildId().equals(id)))
            throw new IllegalArgumentException("Entity belongs to another guild");
    }
    public Optional<Member> getMemberById(String userId) { return members.stream().filter(m -> m.getId().equals(userId)).findFirst(); }
    public Optional<Role> getRoleById(String roleId) { return roles.stream().filter(r -> r.id().equals(roleId)).findFirst(); }
    public Optional<GuildChannel> getChannelById(String channelId) { return channels.stream().filter(c -> c.id().equals(channelId)).findFirst(); }
    public static Guild fromJson(JsonNode data) {
        var properties = data.path("properties").isObject() ? data.path("properties") : data;
        String id = Json.id(data.path("id").asText(properties.path("id").asText()));
        var roles = new ArrayList<Role>(); var channels = new ArrayList<GuildChannel>(); var members = new ArrayList<Member>();
        for (var role : data.path("roles")) roles.add(Role.fromJson(id, role));
        for (var channel : data.path("channels")) channels.add(GuildChannel.fromJson(id, channel));
        for (var member : data.path("members")) members.add(Member.fromJson(id, member));
        return new Guild(id, properties.path("name").asText(""), properties.path("owner_id").asText(null),
                !data.path("unavailable").asBoolean(), roles, channels, members,
                data.path("roles").isArray(), data.path("channels").isArray());
    }
}
