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

    public RestAction<Message> sendMessage(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message content is required");
        return client.getRestClient().request("POST", "/channels/" + id + "/messages", Json.object().put("content", content))
                .map(data -> Message.fromContext(client, data, guildId));
    }

    public RestAction<Message> sendMessageEmbeds(MessageEmbed... embeds) {
        return client.getRestClient().request("POST", "/channels/" + id + "/messages",
                net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds)).map(data -> Message.fromContext(client, data, guildId));
    }

    public RestAction<Message> retrieveMessageById(String messageId) {
        return client.getRestClient().request("GET", "/channels/" + id + "/messages/" + Json.id(messageId), null)
                .map(data -> Message.fromContext(client, data, guildId));
    }
}