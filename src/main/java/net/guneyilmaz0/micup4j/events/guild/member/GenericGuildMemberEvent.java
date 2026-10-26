package net.guneyilmaz0.micup4j.events.guild.member;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.entities.Member;
import net.guneyilmaz0.micup4j.entities.User;
import net.guneyilmaz0.micup4j.events.guild.GenericGuildEvent;
import java.util.Objects;

/**
 * Base of guild member events that carry a member, mirroring JDA's {@code GenericGuildMemberEvent}.
 * As in JDA 5, {@link GuildMemberRemoveEvent} does not extend this class because its member may be unknown.
 */
public abstract class GenericGuildMemberEvent extends GenericGuildEvent {
    private final Member member;

    /**
     * Creates a member event.
     *
     * @param client   the owning client
     * @param sequence the Gateway sequence number
     * @param wireType the Micup dispatch type
     * @param data     the dispatch payload
     * @param guild    the guild snapshot or id reference
     * @param member   the member, bound to {@code client}
     */
    protected GenericGuildMemberEvent(Micup client, long sequence, String wireType, JsonNode data, Guild guild, Member member) {
        super(client, sequence, wireType, data, guild);
        this.member = Objects.requireNonNull(member);
    }

    /**
     * Returns the member.
     *
     * @return the member, never {@code null}
     */
    public Member getMember() {
        return member;
    }

    /**
     * Returns the member's user account.
     *
     * @return the user, never {@code null}
     */
    public User getUser() {
        return member.getUser();
    }
}
