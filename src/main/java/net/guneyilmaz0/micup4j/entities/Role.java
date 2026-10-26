package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.permissions.PermissionSet;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Objects;

/** Immutable role snapshot; hierarchy order is highest first, then lower snowflake first. */
public record Role(String guildId, String id, String name, int color, int position,
                   PermissionSet permissions, boolean hoist, boolean mentionable) implements ISnowflake {
    public static final Comparator<Role> HIERARCHY = Comparator.comparingInt(Role::position).reversed()
            .thenComparing(role -> new BigInteger(role.id()));
    public Role { Json.id(guildId); Json.id(id); Objects.requireNonNull(name); Objects.requireNonNull(permissions); }
    @Override public String getId() { return id; }
    public String getName() { return name; }
    public int getColorRaw() { return color; }
    public java.awt.Color getColor() { return color == 0 ? null : new java.awt.Color(color); }
    public java.util.EnumSet<net.guneyilmaz0.micup4j.Permission> getPermissions() {
        var result = java.util.EnumSet.noneOf(net.guneyilmaz0.micup4j.Permission.class);
        for (var permission : net.guneyilmaz0.micup4j.Permission.values())
            if (permissions.contains(permission.toMicupPermission())) result.add(permission);
        return result;
    }
    public int getPosition() { return position; }
    public String getAsMention() { return "<@&" + id + ">"; }
    public boolean isHoisted() { return hoist; }
    public boolean isMentionable() { return mentionable; }
    public static Role fromJson(String guildId, JsonNode data) {
        return new Role(guildId, data.path("id").asText(), data.path("name").asText(), data.path("color").asInt(),
                data.path("position").asInt(), PermissionSet.parse(data.path("permissions").asText()),
                data.path("hoist").asBoolean(), data.path("mentionable").asBoolean());
    }
}
