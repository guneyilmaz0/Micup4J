package net.guneyilmaz0.micup4j.events.message;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

/**
 * JDA-shaped message event delivered for {@code MESSAGE_CREATE}. Mirrors JDA's {@code MessageReceivedEvent}; it extends the
 * legacy {@link net.guneyilmaz0.micup4j.events.MessageReceivedEvent} so that older listeners keep working.
 */
public final class MessageReceivedEvent extends net.guneyilmaz0.micup4j.events.MessageReceivedEvent {
    /**
     * Creates the event.
     *
     * @param client   the owning client
     * @param sequence the Gateway sequence number
     * @param data     the message payload
     * @throws IllegalArgumentException if a required id is not a canonical snowflake
     */
    public MessageReceivedEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }
}
