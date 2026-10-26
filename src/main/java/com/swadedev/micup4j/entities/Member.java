package com.swadedev.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.internal.Json;
import java.time.Instant;
import java.util.*;

/**
 * Immutable membership snapshot. A timeout is a separate restriction, not a permission-mask change.
 *
 * @param guildId                    the guild
 * @param user                       the account
 * @param nickname                   the guild nickname, or {@code null}
 * @param roleIds                    the assigned role ids, without the everyone role
 * @param joinedAt                   when the account joined, or {@code null} when unknown
 * @param communicationDisabledUntil when a timeout ends, or {@code null}; may lie in the past
 * @param mute                       whether a moderator muted the member in voice
 * @param deaf                       whether a moderator deafened the member in voice
 * @param client                     the owning session, or {@code null} for a detached snapshot
 */
public record Member(String guildId, User user, String nickname, List<String> roleIds,
                     Instant joinedAt, Instant communicationDisabledUntil, boolean mute, boolean deaf, com.swadedev.micup4j.Micup client) implements ISnowflake {
    /**
     * Creates a detached snapshot.
     *
     * @param guildId                    the guild
     * @param user                       the account
     * @param nickname                   the nickname, or {@code null}
     * @param roleIds                    the assigned role ids
     * @param joinedAt                   the join time, or {@code null}
     * @param communicationDisabledUntil the timeout end, or {@code null}
     * @param mute                       the voice mute flag
     * @param deaf                       the voice deafen flag
     */
    public Member(String guildId, User user, String nickname, List<String> roleIds, Instant joinedAt,
                  Instant communicationDisabledUntil, boolean mute, boolean deaf) {
        this(guildId, user, nickname, roleIds, joinedAt, communicationDisabledUntil, mute, deaf, null);
    }
    /**
     * Returns a copy bound to a session, enabling cache lookups and REST helpers.
     *
     * @param client the session
     * @return the bound copy
     */
    public Member withClient(com.swadedev.micup4j.Micup client) {
        return new Member(guildId, user.withClient(client), nickname, roleIds, joinedAt, communicationDisabledUntil, mute, deaf, client);
    }
    private com.swadedev.micup4j.Micup requireClient() {
        if (client == null) throw new IllegalStateException("Detached member has no client");
        return client;
    }
    /**
     * Kicks this member.
     *
     * @return the action; supports an audit reason
     * @throws IllegalStateException if this snapshot is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Void> kick() { return requireClient().kick(guildId, getId()); }
    /**
     * Bans this member permanently; see {@link com.swadedev.micup4j.Micup#ban(String, String, int, java.util.concurrent.TimeUnit)}.
     *
     * @param deletionTimeframe how far back to delete messages, at most 7 days
     * @param unit              the timeframe unit
     * @return the action; supports a reason
     * @throws IllegalStateException if this snapshot is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Void> ban(int deletionTimeframe, java.util.concurrent.TimeUnit unit) {
        return requireClient().ban(guildId, getId(), deletionTimeframe, unit);
    }
    /**
     * Times this member out.
     *
     * @param duration a positive duration of at most 365 days
     * @return the action; supports a reason
     * @throws IllegalStateException if this snapshot is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> timeoutFor(java.time.Duration duration) {
        return requireClient().timeoutFor(guildId, getId(), duration);
    }
    /**
     * Times this member out.
     *
     * @param amount a positive amount
     * @param unit   its unit; the total must be at most 365 days
     * @return the action; supports a reason
     * @throws IllegalStateException if this snapshot is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> timeoutFor(long amount, java.util.concurrent.TimeUnit unit) {
        return timeoutFor(java.time.Duration.of(amount, unit.toChronoUnit()));
    }
    /**
     * Removes this member's timeout.
     *
     * @return the action; supports an audit reason
     * @throws IllegalStateException if this snapshot is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> removeTimeout() { return requireClient().removeTimeout(guildId, getId()); }
    /**
     * Changes this member's nickname.
     *
     * @param nickname the nickname, 1-32 characters, or {@code null} to clear it
     * @return the action; supports an audit reason
     * @throws IllegalStateException if this snapshot is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> modifyNickname(String nickname) {
        return requireClient().modifyMemberNickname(guildId, getId(), nickname);
    }
    /**
     * Returns the account.
     *
     * @return the account
     */
    public User getUser() { return user; }
    /**
     * Returns the guild nickname, or {@code null}.
     *
     * @return the guild nickname, or {@code null}
     */
    public String getNickname() { return nickname; }
    /**
     * Returns the user mention, {@code <@id>}.
     *
     * @return the user mention, {@code <@id>}
     */
    public String getAsMention() { return user.getAsMention(); }
    /**
     * Returns the cached guild of this member.
     *
     * @return the guild
     * @throws IllegalStateException if this snapshot is detached or the guild is not cached
     */
    public Guild getGuild() {
        if (client == null) throw new IllegalStateException("Detached member has no client");
        Guild guild = client.getGuildById(guildId);
        if (guild == null) throw new IllegalStateException("Guild is not cached; retrieve a complete snapshot for permission calculation");
        return guild;
    }
    /**
     * Returns the assigned roles in hierarchy order, highest first.
     *
     * @return the roles
     * @throws IllegalStateException if the guild is not cached, its roles are incomplete, or an assigned role is missing
     */
    public List<Role> getRoles() {
        Guild guild = getGuild();
        if (!guild.rolesComplete()) throw new IllegalStateException("Complete role state is required");
        var roles = new ArrayList<Role>();
        for (String id : roleIds) {
            Role role = guild.getRoleById(id);
            if (role == null) throw new IllegalStateException("An assigned role is missing");
            roles.add(role);
        }
        return roles.stream().sorted(Role.HIERARCHY).toList();
    }
    /**
     * Returns the colors of the highest colored role, as BMO-style bots read them.
     *
     * @return the colors, or {@link RoleColors#DEFAULT}
     * @throws IllegalStateException if the roles cannot be resolved
     */
    public RoleColors getColors() { return getRoles().stream().map(Role::getColors).filter(c -> !c.isDefault()).findFirst().orElse(RoleColors.DEFAULT); }
    /**
     * Returns the highest role's primary color, or {@code null} for the default color.
     *
     * @return the highest role's primary color, or {@code null} for the default color
     */
    public java.awt.Color getColor() { return getColors().getPrimary(); }
    /**
     * Returns the highest role's primary color as RGB, 0 for the default.
     *
     * @return the highest role's primary color as RGB, 0 for the default
     */
    public int getColorRaw() { return getColors().getPrimaryRaw(); }
    /**
     * Returns whether the member holds every given permission at guild level, computed from the cached guild.
     *
     * @param permissions the permissions
     * @return {@code true} if all are held
     * @throws IllegalStateException if the permission state is incomplete
     */
    public boolean hasPermission(com.swadedev.micup4j.Permission... permissions) {
        var mask = com.swadedev.micup4j.permissions.PermissionResolver.guildPermissions(getGuild(), this);
        return Arrays.stream(permissions).allMatch(p -> mask.contains(p.toMicupPermission()));
    }
    /**
     * Returns whether the member holds every given permission in a channel, including its overwrites.
     *
     * @param channel     the channel snapshot; its overwrites are taken as complete
     * @param permissions the permissions
     * @return {@code true} if all are held
     * @throws IllegalStateException if the permission state is incomplete
     */
    public boolean hasPermission(GuildChannel channel, com.swadedev.micup4j.Permission... permissions) {
        var mask = com.swadedev.micup4j.permissions.PermissionResolver.channelPermissions(getGuild(), this, channel);
        return Arrays.stream(permissions).allMatch(p -> mask.contains(p.toMicupPermission()));
    }
    /**
     * Returns the member's guild-level permissions as JDA names.
     *
     * @return the held permissions
     * @throws IllegalStateException if the permission state is incomplete
     */
    public java.util.EnumSet<com.swadedev.micup4j.Permission> getPermissions() {
        var mask = com.swadedev.micup4j.permissions.PermissionResolver.guildPermissions(getGuild(), this);
        var result = java.util.EnumSet.noneOf(com.swadedev.micup4j.Permission.class);
        for (var permission : com.swadedev.micup4j.Permission.values())
            if (mask.contains(permission.toMicupPermission())) result.add(permission);
        return result;
    }
    /**
     * Returns whether a timeout is active now.
     *
     * @return whether a timeout is active now
     */
    public boolean isTimedOut() { return isTimedOutAt(Instant.now()); }
    /**
     * Validates the ids and copies the role list.
     *
     * @throws IllegalArgumentException if an id is not a canonical snowflake
     */
    public Member {
        Json.id(guildId); Objects.requireNonNull(user); Json.id(user.id());
        roleIds = List.copyOf(roleIds);
        roleIds.forEach(Json::id);
    }
    public String getId() { return user.id(); }
    /**
     * Returns the nickname, else the user's effective name.
     *
     * @return the nickname, else the user's effective name
     */
    public String getEffectiveName() { return nickname == null ? user.getEffectiveName() : nickname; }
    /**
     * Returns whether a timeout is active at an instant.
     *
     * @param now the instant to test
     * @return {@code true} if the timeout ends after {@code now}
     */
    public boolean isTimedOutAt(Instant now) { return communicationDisabledUntil != null && communicationDisabledUntil.isAfter(now); }
    /**
     * Returns a copy without one assigned role.
     *
     * @param roleId the role to drop
     * @return the copy
     */
    public Member withoutRole(String roleId) {
        return new Member(guildId, user, nickname, roleIds.stream().filter(id -> !id.equals(roleId)).toList(),
                joinedAt, communicationDisabledUntil, mute, deaf, client);
    }
    /**
     * Parses a guild member object.
     *
     * @param guildId the guild the member belongs to
     * @param data    the member object with its nested user
     * @return the detached snapshot
     * @throws IllegalArgumentException if the role set is missing or an id is invalid
     */
    public static Member fromJson(String guildId, JsonNode data) {
        if (!data.path("roles").isArray()) throw new IllegalArgumentException("Member role set is missing");
        var roles = new ArrayList<String>();
        for (var role : data.path("roles")) roles.add(Json.id(role.asText()));
        return new Member(guildId, User.fromJson(data.path("user")), data.path("nick").asText(null), roles,
                instant(data, "joined_at"), instant(data, "communication_disabled_until"),
                data.path("mute").asBoolean(), data.path("deaf").asBoolean());
    }
    private static Instant instant(JsonNode node, String field) {
        return node.hasNonNull(field) ? Instant.parse(node.path(field).asText()) : null;
    }
}
