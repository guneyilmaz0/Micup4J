package com.swadedev.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.internal.Json;
import java.util.*;

/**
 * Immutable guild snapshot. Members and visible channels never imply a complete guild roster.
 *
 * @param id               the guild id
 * @param name             the name
 * @param ownerId          the owner's user id, or {@code null} when unknown
 * @param available        whether the guild is available; unavailable guilds keep only their identity
 * @param roles            the cached roles
 * @param members          the observed members, a bounded subset
 * @param channels         the channels visible to the bot
 * @param rolesComplete    whether {@code roles} covered the last snapshot fully
 * @param channelsComplete whether {@code channels} covered the last snapshot fully
 * @param client           the owning session, or {@code null} for a detached snapshot
 */
public record Guild(String id, String name, String ownerId, boolean available,
                    List<Role> roles, List<GuildChannel> channels, List<Member> members,
                    boolean rolesComplete, boolean channelsComplete, com.swadedev.micup4j.Micup client) implements ISnowflake {
    /**
     * Creates a guild snapshot that is not bound to a client. Call {@link #withClient} before using any action method.
     *
     * @param id               the guild id, a canonical snowflake
     * @param name             the guild name
     * @param ownerId          the owner's user id, or {@code null} if unknown
     * @param available        whether the guild is available
     * @param roles            the known roles
     * @param channels         the known channels
     * @param members          the known members
     * @param rolesComplete    whether {@code roles} is the complete role list
     * @param channelsComplete whether {@code channels} is the complete channel list
     * @throws IllegalArgumentException if an entity belongs to another guild or an id is not a canonical snowflake
     */
    public Guild(String id, String name, String ownerId, boolean available, List<Role> roles,
                 List<GuildChannel> channels, List<Member> members, boolean rolesComplete, boolean channelsComplete) {
        this(id, name, ownerId, available, roles, channels, members, rolesComplete, channelsComplete, null);
    }
    /**
     * Returns this snapshot bound to {@code client}. Members already bound to the same client are reused, and this
     * instance is returned unchanged when it and all of its members are already bound.
     *
     * @param client the owning client
     * @return a snapshot bound to {@code client}
     */
    public Guild withClient(com.swadedev.micup4j.Micup client) {
        if (this.client == client && members.stream().allMatch(m -> m.client() == client)) return this;
        return new Guild(id, name, ownerId, available, roles, channels,
                members.stream().map(m -> m.client() == client ? m : m.withClient(client)).toList(),
                rolesComplete, channelsComplete, client);
    }
    /** Returns the guild id, a canonical snowflake. */
    @Override public String getId() { return id; }
    /**
     * Returns the guild name.
     *
     * @return the name
     */
    public String getName() { return name; }
    /**
     * Returns the owner's user id. Micup-only; JDA exposes the owner as a member.
     *
     * @return the owner id, or {@code null} if unknown
     */
    public String getOwnerId() { return ownerId; }
    /**
     * Returns whether the guild is available.
     *
     * @return {@code false} while the guild is unavailable (outage)
     */
    public boolean isAvailable() { return available; }
    /**
     * Returns the known roles, highest position first.
     *
     * @return an immutable list; complete only when {@link #rolesComplete()} is {@code true}
     */
    public List<Role> getRoles() { return roles.stream().sorted(Role.HIERARCHY).toList(); }
    /**
     * Returns the known members. Never a complete roster: use {@link #retrieveMemberById(String)} for a specific member.
     *
     * @return an immutable list
     */
    public List<Member> getMembers() { return members; }
    /**
     * Returns the known channels.
     *
     * @return an immutable list; complete only when {@link #channelsComplete()} is {@code true}
     */
    public List<GuildChannel> getChannels() { return channels; }
    /**
     * Looks up a known member.
     *
     * @param id the user id
     * @return the member, or {@code null} if it is not part of this snapshot
     */
    public Member getMemberById(String id) { return findMemberById(id).orElse(null); }
    /**
     * Looks up a known member.
     *
     * @param id the user id as an unsigned 64-bit value
     * @return the member, or {@code null} if it is not part of this snapshot
     */
    public Member getMemberById(long id) { return getMemberById(Long.toUnsignedString(id)); }
    /**
     * Looks up a known role.
     *
     * @param id the role id
     * @return the role, or {@code null} if it is not part of this snapshot
     */
    public Role getRoleById(String id) { return findRoleById(id).orElse(null); }
    /**
     * Looks up a known role.
     *
     * @param id the role id as an unsigned 64-bit value
     * @return the role, or {@code null} if it is not part of this snapshot
     */
    public Role getRoleById(long id) { return getRoleById(Long.toUnsignedString(id)); }
    /**
     * Looks up a known channel.
     *
     * @param id the channel id
     * @return the channel, or {@code null} if it is not part of this snapshot
     */
    public GuildChannel getChannelById(String id) { return findChannelById(id).orElse(null); }
    /**
     * Looks up a known channel.
     *
     * @param id the channel id as an unsigned 64-bit value
     * @return the channel, or {@code null} if it is not part of this snapshot
     */
    public GuildChannel getChannelById(long id) { return getChannelById(Long.toUnsignedString(id)); }
    /**
     * Looks up a known text channel and returns a handle bound to the owning client.
     *
     * @param id the channel id
     * @return the text channel, or {@code null} if the channel is unknown or not a text channel
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.entities.channel.concrete.TextChannel getTextChannelById(String id) {
        GuildChannel channel = getChannelById(id);
        return channel == null || channel.rawType() != 0 ? null
                : new com.swadedev.micup4j.entities.channel.concrete.TextChannel(getJDA(), id, this.id, channel.name());
    }
    /**
     * Looks up a known text channel.
     *
     * @param id the channel id as an unsigned 64-bit value
     * @return the text channel, or {@code null} if the channel is unknown or not a text channel
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.entities.channel.concrete.TextChannel getTextChannelById(long id) { return getTextChannelById(Long.toUnsignedString(id)); }
    /**
     * Returns the client that owns this snapshot. Named for JDA source parity.
     *
     * @return the owning client
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.Micup getJDA() {
        if (client == null) throw new IllegalStateException("Detached guild has no client");
        return client;
    }
    /**
     * Requests members by display-name prefix over the Gateway.
     *
     * @param prefix a non-empty prefix
     * @param limit  1 to 100
     * @return a running task
     * @throws IllegalStateException if this guild is detached
     * @see com.swadedev.micup4j.Micup#retrieveMembersByPrefix(String, String, int)
     */
    public com.swadedev.micup4j.utils.concurrent.Task<List<Member>> retrieveMembersByPrefix(String prefix, int limit) {
        return getJDA().retrieveMembersByPrefix(id, prefix, limit);
    }
    /**
     * Requests specific members over the Gateway.
     *
     * @param ids 1 to 100 user ids
     * @return a running task
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.utils.concurrent.Task<List<Member>> retrieveMembersByIds(String... ids) { return getJDA().retrieveMembersByIds(id, ids); }
    /**
     * Requests specific members over the Gateway.
     *
     * @param ids 1 to 100 user ids as unsigned 64-bit values
     * @return a running task
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.utils.concurrent.Task<List<Member>> retrieveMembersByIds(long... ids) {
        return retrieveMembersByIds(java.util.Arrays.stream(ids).mapToObj(Long::toUnsignedString).toArray(String[]::new));
    }
    /**
     * Requests the complete member list over the Gateway.
     *
     * @return a running task
     * @throws IllegalStateException if this guild is detached
     * @see com.swadedev.micup4j.Micup#loadMembers(String)
     */
    public com.swadedev.micup4j.utils.concurrent.Task<List<Member>> loadMembers() { return getJDA().loadMembers(id); }
    /**
     * Searches this guild's member index, a Micup extension.
     *
     * @return the fluent search action
     * @throws IllegalStateException if this guild is detached
     * @see com.swadedev.micup4j.Micup#searchMembers(String)
     */
    public com.swadedev.micup4j.requests.restaction.MemberSearchAction searchMembers() { return getJDA().searchMembers(id); }
    /**
     * Starts creating a role in this guild.
     *
     * @return the fluent creation action
     * @throws IllegalStateException if this guild is detached
     * @see com.swadedev.micup4j.Micup#createRole(String)
     */
    public com.swadedev.micup4j.requests.restaction.RoleAction createRole() { return getJDA().createRole(id); }
    /**
     * Starts modifying a role of this guild. Micup4J roles are not client-bound, so this replaces JDA's {@code role.getManager()}.
     *
     * @param role the role
     * @return the fluent role manager
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.managers.RoleManager modifyRole(ISnowflake role) { return getJDA().modifyRole(id, role.getId()); }
    /**
     * Deletes a role of this guild. Replaces JDA's {@code role.delete()}.
     *
     * @param role the role
     * @return the delete action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Void> deleteRole(ISnowflake role) { return getJDA().deleteRole(id, role.getId()); }
    /**
     * Adds a role to a member.
     *
     * @param user the member or user
     * @param role the role
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Void> addRoleToMember(ISnowflake user, ISnowflake role) {
        return getJDA().addMemberRole(id, user.getId(), role.getId());
    }
    /**
     * Removes a role from a member.
     *
     * @param user the member or user
     * @param role the role
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Void> removeRoleFromMember(ISnowflake user, ISnowflake role) {
        return getJDA().removeMemberRole(id, user.getId(), role.getId());
    }
    /**
     * Changes a member's nickname.
     *
     * @param member   the member
     * @param nickname the nickname, or {@code null} to clear it
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> modifyNickname(ISnowflake member, String nickname) {
        return getJDA().modifyMemberNickname(id, member.getId(), nickname);
    }
    /**
     * Kicks a member.
     *
     * @param user the member or user
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Void> kick(ISnowflake user) { return getJDA().kick(id, user.getId()); }
    /**
     * Bans a user permanently.
     *
     * @param user              the member or user
     * @param deletionTimeframe how far back to delete messages, at most 7 days
     * @param unit              the timeframe unit
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Void> ban(ISnowflake user, int deletionTimeframe, java.util.concurrent.TimeUnit unit) {
        return getJDA().ban(id, user.getId(), deletionTimeframe, unit);
    }
    /**
     * Removes a ban.
     *
     * @param user the banned user
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Void> unban(ISnowflake user) { return getJDA().unban(id, user.getId()); }
    /**
     * Times a member out.
     *
     * @param user     the member or user
     * @param duration a positive duration of at most 365 days
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> timeoutFor(ISnowflake user, java.time.Duration duration) {
        return getJDA().timeoutFor(id, user.getId(), duration);
    }
    /**
     * Times a member out.
     *
     * @param user   the member or user
     * @param amount the positive amount
     * @param unit   the unit
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> timeoutFor(ISnowflake user, long amount, java.util.concurrent.TimeUnit unit) {
        return timeoutFor(user, java.time.Duration.of(amount, unit.toChronoUnit()));
    }
    /**
     * Times a member out until an instant.
     *
     * @param user  the member or user
     * @param until a future instant at most 365 days ahead
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> timeoutUntil(ISnowflake user, java.time.temporal.TemporalAccessor until) {
        return getJDA().timeoutUntil(id, user.getId(), java.time.Instant.from(until));
    }
    /**
     * Removes a member's timeout.
     *
     * @param user the member or user
     * @return the action
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.restaction.AuditableRestAction<Member> removeTimeout(ISnowflake user) { return getJDA().removeTimeout(id, user.getId()); }
    /**
     * Starts a bulk overwrite of this guild's commands.
     *
     * @return the update action
     * @throws IllegalStateException if this guild is detached
     * @see com.swadedev.micup4j.Micup#updateGuildCommands(String)
     */
    public com.swadedev.micup4j.requests.restaction.CommandListUpdateAction updateCommands() { return getJDA().updateGuildCommands(id); }
    /**
     * Creates or updates one guild command.
     *
     * @param command the command definition
     * @return an action that upserts the command when queued
     * @throws IllegalStateException if this guild is detached
     * @see com.swadedev.micup4j.Micup#upsertGuildCommand(String, com.swadedev.micup4j.interactions.commands.build.CommandData)
     */
    public com.swadedev.micup4j.requests.RestAction<com.swadedev.micup4j.interactions.commands.Command> upsertCommand(
            com.swadedev.micup4j.interactions.commands.build.CommandData command) { return getJDA().upsertGuildCommand(id, command); }
    /**
     * Retrieves this guild's commands.
     *
     * @return an action that fetches the commands when queued
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.RestAction<List<com.swadedev.micup4j.interactions.commands.Command>> retrieveCommands() {
        return getJDA().retrieveGuildCommands(id);
    }
    /**
     * Deletes one guild command.
     *
     * @param commandId the command id
     * @return an action that deletes the command when queued
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.RestAction<Void> deleteCommandById(String commandId) { return getJDA().deleteGuildCommandById(id, commandId); }
    /**
     * Deletes one guild command.
     *
     * @param commandId the command id as an unsigned 64-bit value
     * @return an action that deletes the command when queued
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.RestAction<Void> deleteCommandById(long commandId) { return deleteCommandById(Long.toUnsignedString(commandId)); }
    /**
     * Retrieves a member over REST, bypassing the snapshot.
     *
     * @param userId the user id
     * @return an action that fetches the member when queued
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.RestAction<Member> retrieveMemberById(String userId) { return getJDA().retrieveMemberById(id, userId); }
    /**
     * Retrieves a member over REST, bypassing the snapshot.
     *
     * @param userId the user id as an unsigned 64-bit value
     * @return an action that fetches the member when queued
     * @throws IllegalStateException if this guild is detached
     */
    public com.swadedev.micup4j.requests.RestAction<Member> retrieveMemberById(long userId) { return retrieveMemberById(Long.toUnsignedString(userId)); }
    /**
     * Returns the bot's own member from this snapshot.
     *
     * @return the self member
     * @throws IllegalStateException if this guild is detached or the self member is not part of the snapshot
     */
    public Member getSelfMember() {
        Member result = getMemberById(getJDA().getSelfUser().id());
        if (result == null) throw new IllegalStateException("Self member is not cached; retrieve the current membership");
        return result;
    }
    /**
     * Validates and defensively copies the collections.
     *
     * @throws IllegalArgumentException if an id is not a canonical snowflake or an entity belongs to another guild
     */
    public Guild {
        Json.id(id); Objects.requireNonNull(name);
        if (ownerId != null) Json.id(ownerId);
        roles = List.copyOf(roles); channels = List.copyOf(channels); members = List.copyOf(members);
        if (roles.stream().anyMatch(r -> !r.guildId().equals(id)) || channels.stream().anyMatch(c -> !c.guildId().equals(id))
                || members.stream().anyMatch(m -> !m.guildId().equals(id)))
            throw new IllegalArgumentException("Entity belongs to another guild");
    }
    /**
     * Finds a member in this snapshot.
     *
     * @param userId the user id
     * @return the member, or empty if it is not part of this snapshot
     */
    public Optional<Member> findMemberById(String userId) { return members.stream().filter(m -> m.getId().equals(userId)).findFirst(); }
    /**
     * Finds a role in this snapshot.
     *
     * @param roleId the role id
     * @return the role, or empty if it is not part of this snapshot
     */
    public Optional<Role> findRoleById(String roleId) { return roles.stream().filter(r -> r.id().equals(roleId)).findFirst(); }
    /**
     * Finds a channel in this snapshot.
     *
     * @param channelId the channel id
     * @return the channel, or empty if it is not part of this snapshot
     */
    public Optional<GuildChannel> findChannelById(String channelId) { return channels.stream().filter(c -> c.id().equals(channelId)).findFirst(); }
    /**
     * Parses a guild from a Gateway or REST payload. The result is detached; see {@link #withClient}.
     *
     * @param data the guild object, either flat or with a nested {@code properties} object
     * @return the parsed, detached snapshot
     * @throws IllegalArgumentException if an id in the payload is not a canonical snowflake
     */
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
