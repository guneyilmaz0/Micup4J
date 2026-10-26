package com.swadedev.micup4j.events.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.entities.Member;
import com.swadedev.micup4j.entities.Message;
import com.swadedev.micup4j.entities.User;

/**
 * Fired for a Micup {@code MESSAGE_UPDATE} dispatch, which carries the complete current message. Mirrors JDA's
 * {@code MessageUpdateEvent}. Micup does not include the previous content.
 */
public final class MessageUpdateEvent extends GenericMessageEvent {
    private final Message message;

    /**
     * Creates the event from a {@code MESSAGE_UPDATE} payload.
     *
     * @param client   the owning client
     * @param sequence the Gateway sequence number
     * @param data     the complete current message, extended with {@code guild_id} and {@code member} in guilds
     * @throws IllegalArgumentException if a required id is not a canonical snowflake
     */
    public MessageUpdateEvent(Micup client, long sequence, JsonNode data) {
        super(client, "MESSAGE_UPDATE", sequence, data);
        message = new Message(client, rawData());
    }

    /**
     * Returns the message as it is after the edit.
     *
     * @return the updated message
     */
    public Message getMessage() {
        return message;
    }

    /**
     * Returns the author of the updated message.
     *
     * @return the author
     */
    public User getAuthor() {
        return message.getAuthor();
    }

    /**
     * Returns the author's guild member, built from the payload's {@code member} object merged with the author, or
     * looked up in the cache.
     *
     * @return the member, or {@code null} outside guilds or when unknown
     */
    public Member getMember() {
        return message.getMember();
    }
}
