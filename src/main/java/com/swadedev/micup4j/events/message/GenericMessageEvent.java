package com.swadedev.micup4j.events.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.entities.Guild;
import com.swadedev.micup4j.entities.MessageChannel;
import com.swadedev.micup4j.entities.channel.ChannelType;
import com.swadedev.micup4j.events.GenericEvent;
import com.swadedev.micup4j.internal.Json;

/**
 * Common base of message events, mirroring JDA's {@code GenericMessageEvent}.
 *
 * <p>The message id, channel id and optional guild id are parsed once at construction. {@link #getType()} still
 * returns the Micup wire type (for example {@code MESSAGE_UPDATE}) and {@link #getSequence()} the dispatch sequence.
 * {@link #toString()} never includes message content.
 */
public abstract class GenericMessageEvent extends GenericEvent {
    private final String messageId;
    private final String channelId;
    private final String guildId;
    private final Integer channelType;

    /**
     * Creates a message event from one Gateway dispatch.
     *
     * @param client   the owning client
     * @param wireType the Micup dispatch type, such as {@code MESSAGE_UPDATE}
     * @param sequence the Gateway sequence number
     * @param data     the dispatch payload; copied once by {@link GenericEvent}
     * @throws IllegalArgumentException if {@code id}, {@code channel_id} or a present {@code guild_id} is not a
     *                                  canonical snowflake
     */
    protected GenericMessageEvent(Micup client, String wireType, long sequence, JsonNode data) {
        super(client, wireType, sequence, data);
        JsonNode raw = rawData();
        messageId = Json.id(raw.path("id").asText(null));
        channelId = Json.id(raw.path("channel_id").asText(null));
        guildId = raw.hasNonNull("guild_id") ? Json.id(raw.path("guild_id").asText(null)) : null;
        channelType = raw.path("channel_type").isInt() ? raw.path("channel_type").asInt() : null;
    }

    /**
     * Returns the id of the affected message.
     *
     * @return the message id as a decimal string
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Returns the id of the affected message.
     *
     * @return the message id as an unsigned 64-bit value
     */
    public long getMessageIdLong() {
        return Long.parseUnsignedLong(messageId);
    }

    /**
     * Returns a lightweight handle to the channel of the message. Creating it performs no request.
     *
     * @return the message channel
     */
    public MessageChannel getChannel() {
        return new MessageChannel(getJDA(), channelId, guildId, channelType);
    }

    /**
     * Returns the id of the channel of the message.
     *
     * @return the channel id
     */
    public String getChannelId() {
        return channelId;
    }

    /**
     * Returns whether the message belongs to a guild channel.
     *
     * @return {@code true} if the payload carried a guild id
     */
    public boolean isFromGuild() {
        return guildId != null;
    }

    /**
     * Returns the guild of the message: the cached snapshot, or an id-only reference when the guild is not cached.
     *
     * @return the guild, never {@code null}
     * @throws IllegalStateException if the message is not from a guild
     */
    public Guild getGuild() {
        if (guildId == null) throw new IllegalStateException("Message is not from a guild");
        return getJDA().getGuildReferenceById(guildId);
    }

    /**
     * Returns the guild id, or {@code null} for direct messages. Micup-only convenience.
     *
     * @return the guild id, or {@code null}
     */
    public String getGuildId() {
        return guildId;
    }

    /**
     * Returns the type of the channel of the message, resolved from the payload or the entity cache.
     *
     * @return the channel type, {@link ChannelType#UNKNOWN} when it cannot be determined
     */
    public ChannelType getChannelType() {
        return getChannel().getType();
    }

    /**
     * Returns whether the channel of the message has the given type.
     *
     * @param type the type to compare with
     * @return {@code true} if {@link #getChannelType()} equals {@code type}
     */
    public boolean isFromType(ChannelType type) {
        return getChannelType() == type;
    }
}
