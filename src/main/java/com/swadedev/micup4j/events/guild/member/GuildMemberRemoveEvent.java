package com.swadedev.micup4j.events.guild.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.entities.Guild;
import com.swadedev.micup4j.entities.Member;
import com.swadedev.micup4j.entities.User;
import com.swadedev.micup4j.events.guild.GenericGuildEvent;
import com.swadedev.micup4j.internal.Json;

/**
 * Fired for a Micup {@code GUILD_MEMBER_REMOVE} dispatch (leave, kick or ban). Mirrors JDA 5's
 * {@code GuildMemberRemoveEvent}, which extends {@link GenericGuildEvent} because the member may be unknown.
 *
 * <p>Micup sends only the user id. The member and full user are resolved from the entity cache as it was before the
 * removal; when the member was not cached, {@link #getMember()} is {@code null} and {@link #getUser()} carries only
 * the id (its name is empty). In guilds with more than 250 members, passive sessions only receive this event for their
 * own user.
 */
public final class GuildMemberRemoveEvent extends GenericGuildEvent {
    private final Member member;
    private final User user;

    /**
     * Creates the event.
     *
     * @param client       the owning client
     * @param sequence     the Gateway sequence number
     * @param data         the payload with {@code guild_id} and {@code user.id}
     * @param guild        the guild snapshot, or an id reference when the guild is not cached
     * @param cachedMember the member snapshot from before the removal, or {@code null} if it was not cached
     * @throws IllegalArgumentException if {@code user.id} is missing or not a canonical snowflake
     */
    public GuildMemberRemoveEvent(Micup client, long sequence, JsonNode data, Guild guild, Member cachedMember) {
        super(client, sequence, "GUILD_MEMBER_REMOVE", data, guild);
        String userId = Json.id(rawData().path("user").path("id").asText(null));
        if (cachedMember != null && !cachedMember.getId().equals(userId))
            throw new IllegalArgumentException("Cached member does not match the removed user");
        member = cachedMember;
        user = cachedMember != null ? cachedMember.getUser() : new User(userId, "", false).withClient(client);
    }

    /**
     * Returns the member as it was cached before the removal.
     *
     * @return the pre-removal member snapshot, or {@code null} if it was not cached
     */
    public Member getMember() {
        return member;
    }

    /**
     * Returns the removed user. Only the id is reliable when the member was not cached.
     *
     * @return the user, never {@code null}
     */
    public User getUser() {
        return user;
    }
}
