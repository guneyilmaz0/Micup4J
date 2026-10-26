package com.swadedev.micup4j.events.guild;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.entities.Guild;
import com.swadedev.micup4j.events.GenericEvent;
import java.util.Objects;

/** One ordered wire dispatch, enriched with the relevant immutable guild snapshot. */
public class GenericGuildEvent extends GenericEvent {
    private final Guild guild;
    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param wireType the dispatch name
     * @param data     the payload
     * @param guild    the guild snapshot
     */
    public GenericGuildEvent(Micup client, long sequence, String wireType, JsonNode data, Guild guild) {
        super(client, wireType, sequence, data); this.guild = Objects.requireNonNull(guild);
    }
    /**
     * Returns the guild snapshot, taken after the cache update.
     *
     * @return the guild snapshot, taken after the cache update
     */
    public Guild getGuild() { return guild; }
    /**
     * Returns the guild id.
     *
     * @return the guild id
     */
    public String getGuildId() { return guild.id(); }
}
