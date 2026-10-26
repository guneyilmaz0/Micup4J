package net.guneyilmaz0.micup4j.events.guild;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.internal.Json;

public final class GuildLeaveEvent extends GenericGuildEvent {
    public GuildLeaveEvent(Micup client, long sequence, Guild guild) { this(client, sequence, Json.object().put("id", guild.id()), guild); }
    public GuildLeaveEvent(Micup client, long sequence, JsonNode data, Guild guild) { super(client, sequence, "GUILD_DELETE", data, guild); }
}
