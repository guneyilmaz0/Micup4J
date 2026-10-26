package com.swadedev.micup4j.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.entities.Member;
import com.swadedev.micup4j.entities.Message;
import com.swadedev.micup4j.entities.MessageChannel;
import com.swadedev.micup4j.entities.User;
import com.swadedev.micup4j.events.message.GenericMessageEvent;

/**
 * Fired for a Micup {@code MESSAGE_CREATE} dispatch. Prefer the JDA-style subclass
 * {@code com.swadedev.micup4j.events.message.MessageReceivedEvent}, which is what the client delivers.
 *
 * @deprecated Use {@link com.swadedev.micup4j.events.message.MessageReceivedEvent}; kept for source compatibility
 *             through 1.x.
 */
@Deprecated(since = "1.0.0")
public class MessageReceivedEvent extends GenericMessageEvent {
    private final Message message;

    /**
     * Creates the event from a {@code MESSAGE_CREATE} payload.
     *
     * @param client   the owning client
     * @param sequence the Gateway sequence number
     * @param data     the message payload
     * @throws IllegalArgumentException if a required id is not a canonical snowflake
     */
    public MessageReceivedEvent(Micup client, long sequence, JsonNode data) {
        super(client, "MESSAGE_CREATE", sequence, data);
        message = new Message(client, rawData());
    }

    /**
     * Returns the received message.
     *
     * @return the message
     */
    public Message getMessage() {
        return message;
    }

    /**
     * Returns the author of the message.
     *
     * @return the author
     */
    public User getAuthor() {
        return message.getAuthor();
    }

    /**
     * Returns a lightweight handle to the channel of the message; creating it performs no request.
     *
     * @return the channel
     */
    @Override
    public MessageChannel getChannel() {
        return message.getChannel();
    }

    /**
     * Returns the author's guild member.
     *
     * @return the member, or {@code null} outside guilds or when unknown
     */
    public Member getMember() {
        return message.getMember();
    }
}
