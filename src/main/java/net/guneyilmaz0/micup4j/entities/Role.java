package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.permissions.PermissionSet;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Objects;

/** Immutable role snapshot; hierarchy order is highest first, then lower snowflake first. */
public record Role(String guildId, String id, String name, int color, int position,
                   PermissionSet permissions, boolean hoist, boolean mentionable) {
    public static final Comparator<Role> HIERARCHY = Comparator.comparingInt(Role::position).reversed()
            .thenComparing(role -> new BigInteger(role.id()));
    public Role { Json.id(guildId); Json.id(id); Objects.requireNonNull(name); Objects.requireNonNull(permissions); }
    public static Role fromJson(String guildId, JsonNode data) {
        return new Role(guildId, data.path("id").asText(), data.path("name").asText(), data.path("color").asInt(),
                data.path("position").asInt(), PermissionSet.parse(data.path("permissions").asText()),
                data.path("hoist").asBoolean(), data.path("mentionable").asBoolean());
    }
}
