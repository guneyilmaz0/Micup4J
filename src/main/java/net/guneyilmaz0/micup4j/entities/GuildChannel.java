package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.*;

/** Immutable guild channel snapshot. Raw type is retained for forward compatibility. */
public record GuildChannel(String guildId, String id, String name, int rawType, String parentId,
                           int position, List<PermissionOverwrite> permissionOverwrites) {
    public GuildChannel {
        Json.id(guildId); Json.id(id); Objects.requireNonNull(name);
        if (parentId != null) Json.id(parentId);
        permissionOverwrites = List.copyOf(permissionOverwrites);
    }
    public ChannelType getType() { return ChannelType.fromValue(rawType); }
    public boolean canHaveMessages() { return rawType == 0 || rawType == 2 || rawType == 5; }
    public static GuildChannel fromJson(String guildId, JsonNode data) {
        if (data.hasNonNull("guild_id") && !guildId.equals(data.path("guild_id").asText()))
            throw new IllegalArgumentException("Channel belongs to another guild");
        if (!data.path("permission_overwrites").isArray())
            throw new IllegalArgumentException("Channel permission overwrites are missing");
        var overwrites = new ArrayList<PermissionOverwrite>();
        for (var entry : data.path("permission_overwrites")) overwrites.add(PermissionOverwrite.fromJson(entry));
        return new GuildChannel(guildId, data.path("id").asText(), data.path("name").asText(),
                data.path("type").asInt(-1), data.path("parent_id").asText(null), data.path("position").asInt(), overwrites);
    }
}
