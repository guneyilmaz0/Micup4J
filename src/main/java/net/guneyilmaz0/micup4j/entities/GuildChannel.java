package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.*;

/**
 * Immutable guild channel snapshot. The raw type is retained for forward compatibility.
 *
 * @param guildId              the guild
 * @param id                   the channel id
 * @param name                 the name
 * @param rawType              the Micup channel type
 * @param parentId             the category id, or {@code null}
 * @param position             the sort position
 * @param permissionOverwrites the overwrites, as last observed
 */
public record GuildChannel(String guildId, String id, String name, int rawType, String parentId,
                           int position, List<PermissionOverwrite> permissionOverwrites) implements ISnowflake {
    /**
     * Validates the ids and copies the overwrites.
     *
     * @throws IllegalArgumentException if an id is invalid
     */
    public GuildChannel {
        Json.id(guildId); Json.id(id); Objects.requireNonNull(name);
        if (parentId != null) Json.id(parentId);
        permissionOverwrites = List.copyOf(permissionOverwrites);
    }
    /**
     * Returns the channel type, or {@code UNKNOWN} for a new raw type.
     *
     * @return the channel type, or {@code UNKNOWN} for a new raw type
     */
    public net.guneyilmaz0.micup4j.entities.channel.ChannelType getType() { return ChannelType.fromValue(rawType); }
    @Override public String getId() { return id; }
    /**
     * Returns the channel name.
     *
     * @return the channel name
     */
    public String getName() { return name; }
    /**
     * Returns the channel mention, {@code <#id>}.
     *
     * @return the channel mention, {@code <#id>}
     */
    public String getAsMention() { return "<#" + id + ">"; }
    /**
     * Returns whether the channel holds messages (text, voice and announcement channels).
     *
     * @return whether the channel holds messages (text, voice and announcement channels)
     */
    public boolean canHaveMessages() { return rawType == 0 || rawType == 2 || rawType == 5; }
    /**
     * Parses a guild channel object.
     *
     * @param guildId the guild
     * @param data    the channel object
     * @return the snapshot
     * @throws IllegalArgumentException if an id is invalid or the channel is private
     */
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
