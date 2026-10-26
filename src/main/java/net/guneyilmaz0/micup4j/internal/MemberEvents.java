package net.guneyilmaz0.micup4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.entities.Member;
import net.guneyilmaz0.micup4j.events.GenericEvent;
import net.guneyilmaz0.micup4j.events.guild.member.GuildMemberJoinEvent;
import net.guneyilmaz0.micup4j.events.guild.member.GuildMemberRemoveEvent;
import java.util.Set;

/** Stateless conversion of raw member dispatches into typed events, using cache snapshots from around the apply. */
public final class MemberEvents {
    private static final Set<String> EVENTS = Set.of("GUILD_MEMBER_ADD", "GUILD_MEMBER_REMOVE");

    private MemberEvents() {
    }

    /**
     * Returns whether this class produces a typed event for the wire type.
     *
     * @param type the Micup dispatch type
     * @return {@code true} for {@code GUILD_MEMBER_ADD} and {@code GUILD_MEMBER_REMOVE}
     */
    public static boolean accepts(String type) {
        return EVENTS.contains(type);
    }

    /**
     * Builds the typed member event.
     *
     * @param client the owning client
     * @param event  the raw event, providing type and sequence
     * @param data   the read-only dispatch payload
     * @param before the cached guild snapshot read before the cache apply, or {@code null}
     * @param after  the cached guild snapshot read after the cache apply, or {@code null}
     * @return the typed event, or {@code event} itself for other types
     * @throws IllegalArgumentException if the payload is malformed
     */
    public static GenericEvent apply(Micup client, GenericEvent event, JsonNode data, Guild before, Guild after) {
        String type = event.getType();
        if (!accepts(type)) return event;
        String guildId = Json.id(data.path("guild_id").asText(null));
        if (type.equals("GUILD_MEMBER_ADD")) {
            Member member = Member.fromJson(guildId, data).withClient(client);
            Guild guild = after != null ? after : client.getGuildReferenceById(guildId);
            return new GuildMemberJoinEvent(client, event.getSequence(), data, guild, member);
        }
        String userId = Json.id(data.path("user").path("id").asText(null));
        // `before` is an immutable snapshot taken before the removal was applied, so the member is still present.
        Member cached = before == null ? null : before.getMemberById(userId);
        Guild guild = after != null ? after : before != null ? before : client.getGuildReferenceById(guildId);
        return new GuildMemberRemoveEvent(client, event.getSequence(), data, guild, cached);
    }
}
