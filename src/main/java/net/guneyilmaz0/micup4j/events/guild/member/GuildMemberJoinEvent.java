package net.guneyilmaz0.micup4j.events.guild.member;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.entities.Member;

/**
 * Fired for a Micup {@code GUILD_MEMBER_ADD} dispatch. Mirrors JDA's {@code GuildMemberJoinEvent}.
 *
 * <p>In guilds with more than 250 members, passive sessions only receive this event for their own user.
 */
public final class GuildMemberJoinEvent extends GenericGuildMemberEvent {
    /**
     * Creates the event.
     *
     * @param client   the owning client
     * @param sequence the Gateway sequence number
     * @param data     the complete guild member object with {@code guild_id}
     * @param guild    the guild snapshot, or an id reference when the guild is not cached
     * @param member   the member parsed from the payload
     */
    public GuildMemberJoinEvent(Micup client, long sequence, JsonNode data, Guild guild, Member member) {
        super(client, sequence, "GUILD_MEMBER_ADD", data, guild, member);
    }
}
