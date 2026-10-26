package net.guneyilmaz0.micup4j.events.interaction.component;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Member;
import net.guneyilmaz0.micup4j.entities.Role;
import net.guneyilmaz0.micup4j.entities.User;
import net.guneyilmaz0.micup4j.interactions.commands.OptionMapping;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.ArrayList;
import java.util.List;

/**
 * A user, role, mentionable or channel select choice, modelled on JDA's {@code EntitySelectInteractionEvent}. Entities
 * come from the payload's resolved data and never trigger HTTP.
 */
public final class EntitySelectInteractionEvent extends GenericComponentInteractionCreateEvent {
    private final List<String> values;

    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code INTERACTION_CREATE} payload
     * @throws IllegalArgumentException if a value is not a canonical snowflake
     */
    public EntitySelectInteractionEvent(Micup client, long sequence, JsonNode data) {
        super(client, sequence, data);
        var parsed = new ArrayList<String>();
        rawData().path("data").path("values").forEach(value -> parsed.add(Json.id(value.asText())));
        values = List.copyOf(parsed);
    }

    /**
     * Returns the chosen entity ids.
     *
     * @return the chosen entity ids
     */
    public List<String> getValues() { return values; }

    // Option types 6 (user) and 8 (role) reuse the command option resolver for the select's resolved data.
    private OptionMapping mapping(String id, int optionType) {
        return new OptionMapping(getMicup(), getGuildId(), Json.object().put("name", getComponentId()).put("type", optionType).put("value", id),
                rawData().path("data").path("resolved"));
    }

    private boolean resolved(String kind, String id) { return rawData().path("data").path("resolved").path(kind).has(id); }

    /**
     * Returns the chosen users that were resolved.
     *
     * @return the chosen users that were resolved
     */
    public List<User> getUsers() { return values.stream().filter(id -> resolved("users", id)).map(id -> mapping(id, 6).getAsUser()).toList(); }
    /**
     * Returns the chosen members that were resolved.
     *
     * @return the chosen members that were resolved
     */
    public List<Member> getMembers() { return values.stream().filter(id -> resolved("members", id) && resolved("users", id)).map(id -> mapping(id, 6).getAsMember()).toList(); }
    /**
     * Returns the chosen roles that were resolved.
     *
     * @return the chosen roles that were resolved
     */
    public List<Role> getRoles() { return values.stream().filter(id -> resolved("roles", id)).map(id -> mapping(id, 8).getAsRole()).toList(); }
}
