package net.guneyilmaz0.micup4j.permissions;

import net.guneyilmaz0.micup4j.entities.*;
import java.util.*;

/** Computes masks only. Server-side MFA, hierarchy, visibility and timeouts remain separate checks. */
public final class PermissionResolver {
    private PermissionResolver() {}
    /**
     * Computes a member's guild-level permissions: owner gets everything; otherwise the everyone role unioned with the
     * assigned roles, and administrators get everything.
     *
     * @param guild  a guild snapshot with complete role state
     * @param member the member, or {@code null} for a nonmember
     * @return the mask
     * @throws IllegalStateException if the guild is unavailable, its owner is unknown, or role state is incomplete
     * @throws IllegalArgumentException if the member belongs to another guild
     */
    public static PermissionSet guildPermissions(Guild guild, Member member) {
        Objects.requireNonNull(guild);
        if (!guild.available()) throw new IllegalStateException("Guild is unavailable");
        if (member == null) return PermissionSet.NONE;
        if (!guild.id().equals(member.guildId())) throw new IllegalArgumentException("Member belongs to another guild");
        if (guild.ownerId() == null) throw new IllegalStateException("Guild owner is unknown");
        if (member.getId().equals(guild.ownerId())) return PermissionSet.ALL;
        if (!guild.rolesComplete()) throw new IllegalStateException("Complete role state is required");
        var roles = new HashMap<String, Role>();
        guild.roles().forEach(role -> roles.put(role.id(), role));
        Role everyone = roles.get(guild.id());
        if (everyone == null) throw new IllegalStateException("Everyone role is missing");
        var result = everyone.permissions();
        for (String id : member.roleIds()) {
            Role role = roles.get(id);
            if (role == null) throw new IllegalStateException("An assigned role is missing");
            result = result.union(role.permissions());
        }
        return result.contains(Permission.ADMINISTRATOR) ? PermissionSet.ALL : result;
    }
    /**
     * Computes a member's permissions in a channel: the guild mask, then the everyone overwrite, the combined role
     * overwrites and the member overwrite. Administrators bypass overwrites. A {@code null} channel returns the guild mask.
     *
     * @param guild   a guild snapshot with complete role state
     * @param member  the member, or {@code null} for a nonmember
     * @param channel the channel snapshot, taken as having complete overwrites, or {@code null}
     * @return the mask
     * @throws IllegalStateException if the permission state is incomplete
     * @throws IllegalArgumentException if an entity belongs to another guild
     */
    public static PermissionSet channelPermissions(Guild guild, Member member, GuildChannel channel) {
        if (channel != null && !guild.id().equals(channel.guildId())) throw new IllegalArgumentException("Channel belongs to another guild");
        var result = guildPermissions(guild, member);
        if (channel == null || member == null || result.equals(PermissionSet.ALL)) return result;
        var roleIds = new HashSet<>(member.roleIds());
        PermissionSet allow = PermissionSet.NONE, deny = PermissionSet.NONE;
        for (var overwrite : channel.permissionOverwrites()) {
            if (overwrite.type() == PermissionOverwrite.Type.ROLE && overwrite.id().equals(guild.id()))
                result = result.overwrite(overwrite.allow(), overwrite.deny());
        }
        for (var overwrite : channel.permissionOverwrites()) {
            if (overwrite.type() == PermissionOverwrite.Type.ROLE && !overwrite.id().equals(guild.id()) && roleIds.contains(overwrite.id())) {
                allow = allow.union(overwrite.allow()); deny = deny.union(overwrite.deny());
            }
        }
        result = result.overwrite(allow, deny);
        for (var overwrite : channel.permissionOverwrites()) {
            if (overwrite.type() == PermissionOverwrite.Type.MEMBER && overwrite.id().equals(member.getId()))
                result = result.overwrite(overwrite.allow(), overwrite.deny());
        }
        return result;
    }
}
