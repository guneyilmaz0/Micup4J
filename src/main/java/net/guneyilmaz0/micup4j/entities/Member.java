package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.time.Instant;
import java.util.*;

/** Immutable membership snapshot. A timeout is a separate restriction, not a permission-mask change. */
public record Member(String guildId, User user, String nickname, List<String> roleIds,
                     Instant joinedAt, Instant communicationDisabledUntil, boolean mute, boolean deaf, net.guneyilmaz0.micup4j.Micup client) implements ISnowflake {
    public Member(String guildId, User user, String nickname, List<String> roleIds, Instant joinedAt,
                  Instant communicationDisabledUntil, boolean mute, boolean deaf) {
        this(guildId, user, nickname, roleIds, joinedAt, communicationDisabledUntil, mute, deaf, null);
    }
    public Member withClient(net.guneyilmaz0.micup4j.Micup client) {
        return new Member(guildId, user.withClient(client), nickname, roleIds, joinedAt, communicationDisabledUntil, mute, deaf, client);
    }
    private net.guneyilmaz0.micup4j.Micup requireClient() {
        if (client == null) throw new IllegalStateException("Detached member has no client");
        return client;
    }
    /** Kicks this member. Requires a client-bound member. */
    public net.guneyilmaz0.micup4j.requests.restaction.AuditableRestAction<Void> kick() { return requireClient().kick(guildId, getId()); }
    /** Bans this member permanently; see {@link net.guneyilmaz0.micup4j.Micup#ban(String, String, int, java.util.concurrent.TimeUnit)}. */
    public net.guneyilmaz0.micup4j.requests.restaction.AuditableRestAction<Void> ban(int deletionTimeframe, java.util.concurrent.TimeUnit unit) {
        return requireClient().ban(guildId, getId(), deletionTimeframe, unit);
    }
    /** Times this member out for a positive duration of at most 365 days. */
    public net.guneyilmaz0.micup4j.requests.restaction.AuditableRestAction<Member> timeoutFor(java.time.Duration duration) {
        return requireClient().timeoutFor(guildId, getId(), duration);
    }
    /** Times this member out for a positive amount of at most 365 days. */
    public net.guneyilmaz0.micup4j.requests.restaction.AuditableRestAction<Member> timeoutFor(long amount, java.util.concurrent.TimeUnit unit) {
        return timeoutFor(java.time.Duration.of(amount, unit.toChronoUnit()));
    }
    /** Removes this member's timeout. */
    public net.guneyilmaz0.micup4j.requests.restaction.AuditableRestAction<Member> removeTimeout() { return requireClient().removeTimeout(guildId, getId()); }
    /** Changes this member's nickname; {@code null} clears it. */
    public net.guneyilmaz0.micup4j.requests.restaction.AuditableRestAction<Member> modifyNickname(String nickname) {
        return requireClient().modifyMemberNickname(guildId, getId(), nickname);
    }
    public User getUser() { return user; }
    public String getNickname() { return nickname; }
    public String getAsMention() { return user.getAsMention(); }
    public Guild getGuild() {
        if (client == null) throw new IllegalStateException("Detached member has no client");
        Guild guild = client.getGuildById(guildId);
        if (guild == null) throw new IllegalStateException("Guild is not cached; retrieve a complete snapshot for permission calculation");
        return guild;
    }
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
    public RoleColors getColors() { return getRoles().stream().map(Role::getColors).filter(c -> !c.isDefault()).findFirst().orElse(RoleColors.DEFAULT); }
    public java.awt.Color getColor() { return getColors().getPrimary(); }
    public int getColorRaw() { return getColors().getPrimaryRaw(); }
    public boolean hasPermission(net.guneyilmaz0.micup4j.Permission... permissions) {
        var mask = net.guneyilmaz0.micup4j.permissions.PermissionResolver.guildPermissions(getGuild(), this);
        return Arrays.stream(permissions).allMatch(p -> mask.contains(p.toMicupPermission()));
    }
    public boolean hasPermission(GuildChannel channel, net.guneyilmaz0.micup4j.Permission... permissions) {
        var mask = net.guneyilmaz0.micup4j.permissions.PermissionResolver.channelPermissions(getGuild(), this, channel);
        return Arrays.stream(permissions).allMatch(p -> mask.contains(p.toMicupPermission()));
    }
    public java.util.EnumSet<net.guneyilmaz0.micup4j.Permission> getPermissions() {
        var mask = net.guneyilmaz0.micup4j.permissions.PermissionResolver.guildPermissions(getGuild(), this);
        var result = java.util.EnumSet.noneOf(net.guneyilmaz0.micup4j.Permission.class);
        for (var permission : net.guneyilmaz0.micup4j.Permission.values())
            if (mask.contains(permission.toMicupPermission())) result.add(permission);
        return result;
    }
    public boolean isTimedOut() { return isTimedOutAt(Instant.now()); }
    public Member {
        Json.id(guildId); Objects.requireNonNull(user); Json.id(user.id());
        roleIds = List.copyOf(roleIds);
        roleIds.forEach(Json::id);
    }
    public String getId() { return user.id(); }
    public String getEffectiveName() { return nickname == null ? user.getEffectiveName() : nickname; }
    public boolean isTimedOutAt(Instant now) { return communicationDisabledUntil != null && communicationDisabledUntil.isAfter(now); }
    public Member withoutRole(String roleId) {
        return new Member(guildId, user, nickname, roleIds.stream().filter(id -> !id.equals(roleId)).toList(),
                joinedAt, communicationDisabledUntil, mute, deaf, client);
    }
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
