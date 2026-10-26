package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;

public final class Message implements ISnowflake {
    private final Micup client;
    private final String id, channelId, content, guildId;
    private final JsonNode memberData;
    private final User author;
    private final JsonNode mentionData;
    private volatile Mentions mentions;
    private final Integer channelType;

    public Message(Micup client, JsonNode data) {
        this.client = client;
        id = data.path("id").asText();
        channelId = data.path("channel_id").asText();
        content = data.path("content").asText("");
        guildId = data.path("guild_id").asText(null);
        channelType = data.path("channel_type").isInt() ? data.path("channel_type").asInt() : null;
        var mentionCopy = Json.object().put("content", content).put("mention_everyone", data.path("mention_everyone").asBoolean());
        for (String field : java.util.List.of("mentions", "mention_roles", "mention_channels"))
            if (data.has(field)) mentionCopy.set(field, data.path(field).deepCopy());
        mentionData = mentionCopy;
        memberData = data.path("member").deepCopy();
        author = User.fromJson(data.path("author")).withClient(client);
    }

    public static Message fromContext(Micup client, JsonNode data, String guildId) {
        if (guildId == null || data.hasNonNull("guild_id")) return new Message(client, data);
        var copy = (com.fasterxml.jackson.databind.node.ObjectNode) data.deepCopy();
        copy.put("guild_id", guildId); return new Message(client, copy);
    }

    public String getId() {
        return id;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getContentRaw() {
        return content;
    }

    public User getAuthor() {
        return author;
    }

    public MessageChannel getChannel() {
        return new MessageChannel(client, channelId, guildId, channelType);
    }

    public Mentions getMentions() {
        var result = mentions;
        if (result == null) synchronized (this) {
            result = mentions;
            if (result == null) mentions = result = new Mentions(client, guildId, mentionData);
        }
        return result;
    }
    public Micup getJDA() { return client; }
    public boolean isFromGuild() { return guildId != null; }
    public Guild getGuild() {
        if (!isFromGuild()) throw new IllegalStateException("Message is not from a guild");
        return client.getGuildReferenceById(guildId);
    }
    public Member getMember() {
        if (!isFromGuild()) return null;
        if (memberData.isObject()) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) memberData.deepCopy();
            var user = Json.object().put("id", author.id()).put("username", author.name()).put("bot", author.bot());
            if (author.globalName() != null) user.put("global_name", author.globalName());
            if (author.avatarId() != null) user.put("avatar", author.avatarId());
            body.set("user", user);
            return Member.fromJson(guildId, body).withClient(client);
        }
        Guild guild = client.getGuildById(guildId);
        return guild == null ? null : guild.getMemberById(author.id());
    }

    public RestAction<Message> reply(String content) {
        return client.getRestClient().request("POST", "/channels/" + Json.id(channelId) + "/messages",
                        Json.object().put("content", content).set("message_reference", Json.object().put("message_id", id)))
                .map(data -> Message.fromContext(client, data, guildId));
    }

    public RestAction<Message> replyEmbeds(MessageEmbed... embeds) {
        var body = net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds);
        body.set("message_reference", Json.object().put("message_id", Json.id(id)));
        return client.getRestClient().request("POST", "/channels/" + Json.id(channelId) + "/messages", body)
                .map(data -> Message.fromContext(client, data, guildId));
    }

    public RestAction<Message> editMessage(String content) {
        return client.getRestClient().request("PATCH", "/channels/" + Json.id(channelId) + "/messages/" + Json.id(id), Json.object().put("content", content))
                .map(data -> Message.fromContext(client, data, guildId));
    }

    public RestAction<Void> delete() {
        return client.getRestClient().request("DELETE", "/channels/" + Json.id(channelId) + "/messages/" + Json.id(id), null).map(data -> null);
    }
}