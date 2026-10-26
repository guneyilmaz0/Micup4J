package com.swadedev.micup4j.entities;

import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.internal.Json;
import com.swadedev.micup4j.requests.RestAction;

/** Lightweight channel handle; creating one does not imply access or cache membership. */
public class MessageChannel implements com.swadedev.micup4j.entities.channel.middleman.MessageChannel {
    private final Micup client;
    private final String id;
    private final String guildId;
    private final boolean contextKnown;
    private final Integer rawType;

    /**
     * Creates a handle for a channel whose guild is unknown.
     *
     * @param client the session
     * @param id     the channel id
     */
    public MessageChannel(Micup client, String id) { this(client, id, null, false, null); }
    /**
     * Creates a handle for a channel in a known context.
     *
     * @param client  the session
     * @param id      the channel id
     * @param guildId the guild id, or {@code null} for a direct message
     */
    public MessageChannel(Micup client, String id, String guildId) { this(client, id, guildId, true, null); }
    /**
     * Creates a handle with a known context and Micup channel type.
     *
     * @param client  the session
     * @param id      the channel id
     * @param guildId the guild id, or {@code null} for a direct message
     * @param rawType the Micup channel type, or {@code null} when unknown
     */
    public MessageChannel(Micup client, String id, String guildId, Integer rawType) { this(client, id, guildId, true, rawType); }
    private MessageChannel(Micup client, String id, String guildId, boolean contextKnown, Integer rawType) {
        this.contextKnown = contextKnown;
        this.rawType = rawType;
        this.client = java.util.Objects.requireNonNull(client);
        this.id = Json.id(id);
        this.guildId = guildId == null ? null : Json.id(guildId);
    }
    /**
     * Returns the session.
     *
     * @return the session
     */
    public Micup getJDA() { return client; }
    public String getAsMention() { return "<#" + id + ">"; }
    public com.swadedev.micup4j.entities.channel.ChannelType getType() {
        if (rawType != null) return com.swadedev.micup4j.entities.channel.ChannelType.fromId(rawType);
        if (guildId != null) {
            Guild guild = client.getGuildById(guildId);
            GuildChannel channel = guild == null ? null : guild.getChannelById(id);
            return channel == null ? com.swadedev.micup4j.entities.channel.ChannelType.UNKNOWN : channel.getType();
        }
        return contextKnown ? com.swadedev.micup4j.entities.channel.ChannelType.PRIVATE
                : com.swadedev.micup4j.entities.channel.ChannelType.UNKNOWN;
    }

    public String getId() {
        return id;
    }

    public com.swadedev.micup4j.requests.restaction.MessageCreateAction sendMessage(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message content is required");
        return new com.swadedev.micup4j.requests.restaction.MessageCreateAction(client,
                "/channels/" + id + "/messages", guildId, Json.object().put("content", content));
    }

    public com.swadedev.micup4j.requests.restaction.MessageCreateAction sendMessageEmbeds(MessageEmbed... embeds) {
        return new com.swadedev.micup4j.requests.restaction.MessageCreateAction(client,
                "/channels/" + id + "/messages", guildId, com.swadedev.micup4j.internal.MessagePayload.embeds(embeds));
    }

    /**
     * Sends files as a new message.
     *
     * @param files 1-10 files
     * @return the action; add content, embeds or components before queueing
     */
    public com.swadedev.micup4j.requests.restaction.MessageCreateAction sendFiles(com.swadedev.micup4j.utils.FileUpload... files) {
        return sendFiles(java.util.Arrays.asList(files));
    }

    /**
     * Sends files as a new message.
     *
     * @param files 1-10 files
     * @return the action
     */
    public com.swadedev.micup4j.requests.restaction.MessageCreateAction sendFiles(java.util.Collection<? extends com.swadedev.micup4j.utils.FileUpload> files) {
        if (files.isEmpty()) throw new IllegalArgumentException("At least one file is required");
        return new com.swadedev.micup4j.requests.restaction.MessageCreateAction(client, "/channels/" + id + "/messages", guildId, Json.object()).setFiles(files);
    }

    public RestAction<Message> retrieveMessageById(String messageId) {
        return client.getRestClient().request("GET", "/channels/" + id + "/messages/" + Json.id(messageId), null)
                .map(data -> Message.fromContext(client, data, guildId));
    }
}