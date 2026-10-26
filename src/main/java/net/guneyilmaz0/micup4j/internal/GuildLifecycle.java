package net.guneyilmaz0.micup4j.internal;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.events.GenericEvent;
import net.guneyilmaz0.micup4j.events.guild.*;
import java.util.*;

/** Ordered session metadata, separate from entity eviction and bounded by Micup's bot-session guild limit. */
public final class GuildLifecycle {
    private enum State { INITIAL, AVAILABLE, UNAVAILABLE }
    private static final int MAX_GUILDS = 2500;
    private static final Set<String> EVENTS = Set.of("READY", "GUILD_CREATE", "GUILD_SYNC", "GUILD_UPDATE", "GUILD_DELETE");
    private final Map<String, State> states = new HashMap<>();
    public static boolean accepts(String type) { return EVENTS.contains(type); }
    public void clear() { states.clear(); }
    public GenericEvent apply(Micup client, GenericEvent event, JsonNode data, Guild before, Guild after) {
        String type = event.getType();
        if (type.equals("READY")) {
            states.clear();
            for (var entry : data.path("guilds")) put(Json.id(entry.path("id").asText()), State.INITIAL);
            return event;
        }
        String id = Json.id(data.path("guild_id").asText(data.path("id").asText()));
        State previous = states.get(id);
        if (type.equals("GUILD_CREATE")) {
            Guild guild = after == null ? Guild.fromJson(data).withClient(client) : after;
            if (!guild.available()) throw new IllegalArgumentException("Guild Create must describe an available guild");
            put(id, State.AVAILABLE);
            if (previous == State.INITIAL) return new GuildReadyEvent(client, event.getSequence(), data, guild);
            if (previous == State.UNAVAILABLE) return new GuildAvailableEvent(client, event.getSequence(), data, guild);
            if (previous == null) return new GuildJoinEvent(client, event.getSequence(), data, guild);
            return new GenericGuildEvent(client, event.getSequence(), type, data, guild);
        }
        if (type.equals("GUILD_DELETE")) {
            if (data.path("unavailable").asBoolean()) {
                put(id, State.UNAVAILABLE);
                Guild guild = after != null ? after : Guild.fromJson(data).withClient(client);
                if (previous == State.AVAILABLE) return new GuildUnavailableEvent(client, event.getSequence(), data, guild);
                return new GenericGuildEvent(client, event.getSequence(), type, data, guild);
            }
            states.remove(id);
            Guild guild = before == null ? client.getGuildReferenceById(id) : before;
            return new GuildLeaveEvent(client, event.getSequence(), data, guild);
        }
        Guild guild = after == null ? Guild.fromJson(data).withClient(client) : after;
        return new GenericGuildEvent(client, event.getSequence(), type, data, guild);
    }
    private void put(String id, State state) {
        if (!states.containsKey(id) && states.size() >= MAX_GUILDS)
            throw new IllegalStateException("Session guild lifecycle limit exceeded; use sharding");
        states.put(id, state);
    }
}
