package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.permissions.PermissionSet;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Objects;

/**
 * Immutable role snapshot; hierarchy order is highest position first, then lower snowflake first.
 *
 * @param guildId        the guild
 * @param id             the role id; the everyone role's id equals the guild id
 * @param name           the name
 * @param color          the primary 24-bit RGB color, 0 for the default
 * @param position       the hierarchy position
 * @param permissions    the permission mask
 * @param hoist          whether members are shown separately
 * @param mentionable    whether everyone may mention the role
 * @param secondaryColor the optional static gradient color, or {@code null}
 */
public record Role(String guildId, String id, String name, int color, int position,
                   PermissionSet permissions, boolean hoist, boolean mentionable, Integer secondaryColor) implements ISnowflake {
    /**
     * Creates a role without a secondary color.
     *
     * @param guildId     the guild
     * @param id          the role id
     * @param name        the name
     * @param color       the primary color
     * @param position    the hierarchy position
     * @param permissions the permission mask
     * @param hoist       the hoist flag
     * @param mentionable the mentionable flag
     */
    public Role(String guildId, String id, String name, int color, int position, PermissionSet permissions, boolean hoist, boolean mentionable) {
        this(guildId, id, name, color, position, permissions, hoist, mentionable, null);
    }
    /**
     * Returns the primary and secondary colors.
     *
     * @return the primary and secondary colors
     */
    public RoleColors getColors() { return new RoleColors(color, secondaryColor); }
    public static final Comparator<Role> HIERARCHY = Comparator.comparingInt(Role::position).reversed()
            .thenComparing(role -> new BigInteger(role.id()));
    /**
     * Validates the ids, name, mask and colors.
     *
     * @throws IllegalArgumentException if an id or color is invalid
     */
    public Role { Json.id(guildId); Json.id(id); Objects.requireNonNull(name); Objects.requireNonNull(permissions); new RoleColors(color, secondaryColor); }
    @Override public String getId() { return id; }
    /**
     * Returns the role name.
     *
     * @return the role name
     */
    public String getName() { return name; }
    /**
     * Returns the primary color as RGB, 0 for the default.
     *
     * @return the primary color as RGB, 0 for the default
     */
    public int getColorRaw() { return color; }
    /**
     * Returns the primary color, or {@code null} for the default color.
     *
     * @return the primary color, or {@code null} for the default color
     */
    public java.awt.Color getColor() { return color == 0 ? null : new java.awt.Color(color); }
    /**
     * Returns the role's permissions as JDA names. Micup bits without a JDA name are omitted; see {@link #permissions()}.
     *
     * @return the permissions
     */
    public java.util.EnumSet<net.guneyilmaz0.micup4j.Permission> getPermissions() {
        var result = java.util.EnumSet.noneOf(net.guneyilmaz0.micup4j.Permission.class);
        for (var permission : net.guneyilmaz0.micup4j.Permission.values())
            if (permissions.contains(permission.toMicupPermission())) result.add(permission);
        return result;
    }
    /**
     * Returns the hierarchy position.
     *
     * @return the hierarchy position
     */
    public int getPosition() { return position; }
    /**
     * Returns the role mention, {@code <@&id>}.
     *
     * @return the role mention, {@code <@&id>}
     */
    public String getAsMention() { return "<@&" + id + ">"; }
    /**
     * Returns whether members holding the role are shown separately.
     *
     * @return whether members holding the role are shown separately
     */
    public boolean isHoisted() { return hoist; }
    /**
     * Returns whether everyone may mention the role.
     *
     * @return whether everyone may mention the role
     */
    public boolean isMentionable() { return mentionable; }
    /**
     * Parses a guild role object.
     *
     * @param guildId the guild
     * @param data    the role object
     * @return the snapshot
     * @throws IllegalArgumentException if an id or the mask is invalid
     */
    public static Role fromJson(String guildId, JsonNode data) {
        return new Role(guildId, data.path("id").asText(), data.path("name").asText(), data.path("color").asInt(),
                data.path("position").asInt(), PermissionSet.parse(data.path("permissions").asText()),
                data.path("hoist").asBoolean(), data.path("mentionable").asBoolean(),
                data.hasNonNull("color_secondary") ? data.path("color_secondary").asInt() : null);
    }
}
