package net.guneyilmaz0.micup4j.events.guild;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.events.GenericEvent;
import java.util.Objects;

/** One ordered wire dispatch, enriched with the relevant immutable guild snapshot. */
public class GenericGuildEvent extends GenericEvent {
    private final Guild guild;
    public GenericGuildEvent(Micup client, long sequence, String wireType, JsonNode data, Guild guild) {
        super(client, wireType, sequence, data); this.guild = Objects.requireNonNull(guild);
    }
    public Guild getGuild() { return guild; }
    public String getGuildId() { return guild.id(); }
}
