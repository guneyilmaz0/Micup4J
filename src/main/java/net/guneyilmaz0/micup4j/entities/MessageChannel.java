package net.guneyilmaz0.micup4j.entities;

import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;

/** Lightweight channel handle; creating one does not imply access or cache membership. */
public class MessageChannel implements net.guneyilmaz0.micup4j.entities.channel.middleman.MessageChannel {
    private final Micup client;
    private final String id;
    private final String guildId;
    private final boolean contextKnown;
    private final Integer rawType;

    public MessageChannel(Micup client, String id) { this(client, id, null, false, null); }
    public MessageChannel(Micup client, String id, String guildId) { this(client, id, guildId, true, null); }
    public MessageChannel(Micup client, String id, String guildId, Integer rawType) { this(client, id, guildId, true, rawType); }
    private MessageChannel(Micup client, String id, String guildId, boolean contextKnown, Integer rawType) {
        this.contextKnown = contextKnown;
        this.rawType = rawType;
        this.client = java.util.Objects.requireNonNull(client);
        this.id = Json.id(id);
        this.guildId = guildId == null ? null : Json.id(guildId);
    }
    public Micup getJDA() { return client; }
    public String getAsMention() { return "<#" + id + ">"; }
    public net.guneyilmaz0.micup4j.entities.channel.ChannelType getType() {
        if (rawType != null) return net.guneyilmaz0.micup4j.entities.channel.ChannelType.fromId(rawType);
        if (guildId != null) {
            Guild guild = client.getGuildById(guildId);
            GuildChannel channel = guild == null ? null : guild.getChannelById(id);
            return channel == null ? net.guneyilmaz0.micup4j.entities.channel.ChannelType.UNKNOWN : channel.getType();
        }
        return contextKnown ? net.guneyilmaz0.micup4j.entities.channel.ChannelType.PRIVATE
                : net.guneyilmaz0.micup4j.entities.channel.ChannelType.UNKNOWN;
    }

    public String getId() {
        return id;
    }

    public net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction sendMessage(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message content is required");
        return new net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction(client,
                "/channels/" + id + "/messages", guildId, Json.object().put("content", content));
    }

    public net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction sendMessageEmbeds(MessageEmbed... embeds) {
        return new net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction(client,
                "/channels/" + id + "/messages", guildId, net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds));
    }

    /**
     * Sends files as a new message.
     *
     * @param files 1-10 files
     * @return the action; add content, embeds or components before queueing
     */
    public net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction sendFiles(net.guneyilmaz0.micup4j.utils.FileUpload... files) {
        return sendFiles(java.util.Arrays.asList(files));
    }

    /**
     * Sends files as a new message.
     *
     * @param files 1-10 files
     * @return the action
     */
    public net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction sendFiles(java.util.Collection<? extends net.guneyilmaz0.micup4j.utils.FileUpload> files) {
        if (files.isEmpty()) throw new IllegalArgumentException("At least one file is required");
        return new net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction(client, "/channels/" + id + "/messages", guildId, Json.object()).setFiles(files);
    }

    public RestAction<Message> retrieveMessageById(String messageId) {
        return client.getRestClient().request("GET", "/channels/" + id + "/messages/" + Json.id(messageId), null)
                .map(data -> Message.fromContext(client, data, guildId));
    }
}