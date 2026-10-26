package net.guneyilmaz0.micup4j.events.guild;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.internal.Json;

/** A guild became temporarily unavailable, as JDA's {@code GuildUnavailableEvent}. Delivered after the cache update. */
public final class GuildUnavailableEvent extends GenericGuildEvent {
    /**
     * Creates the event with a minimal payload; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param guild    the guild snapshot
     */
    public GuildUnavailableEvent(Micup client, long sequence, Guild guild) { this(client, sequence, Json.object().put("id", guild.id()), guild); }
    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code GUILD_DELETE} payload
     * @param guild    the guild snapshot
     */
    public GuildUnavailableEvent(Micup client, long sequence, JsonNode data, Guild guild) { super(client, sequence, "GUILD_DELETE", data, guild); }
}
