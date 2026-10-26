package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;

/**
 * A received or edited chat message.
 *
 * <p>Instances are immutable snapshots of the payload. Replies and edits are lazy {@link RestAction}-based builders and
 * send nothing until queued. Message content is private user data: never log it.
 */
public final class Message implements ISnowflake {
    private final Micup client;
    private final String id, channelId, content, guildId;
    private final JsonNode memberData;
    private final User author;
    private final JsonNode mentionData;
    private volatile Mentions mentions;
    private final Integer channelType;

    /**
     * Parses a message from a Micup payload.
     *
     * @param client the owning client
     * @param data   the message object; copied where retained
     */
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

    /**
     * Parses a message, filling in the guild id when the payload omits it (for example webhook responses).
     *
     * @param client  the owning client
     * @param data    the message object
     * @param guildId the guild the message belongs to, or {@code null} for direct messages
     * @return the parsed message
     */
    public static Message fromContext(Micup client, JsonNode data, String guildId) {
        if (guildId == null || data.hasNonNull("guild_id")) return new Message(client, data);
        var copy = (com.fasterxml.jackson.databind.node.ObjectNode) data.deepCopy();
        copy.put("guild_id", guildId); return new Message(client, copy);
    }

    /**
     * Returns the message id.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the id of the channel the message was sent to.
     *
     * @return the channel id
     */
    public String getChannelId() {
        return channelId;
    }

    /**
     * Returns the raw text content. Private user data: never log it.
     *
     * @return the content, empty if the payload carried none
     */
    public String getContentRaw() {
        return content;
    }

    /**
     * Returns the author.
     *
     * @return the author, bound to the owning client
     */
    public User getAuthor() {
        return author;
    }

    /**
     * Returns a lightweight handle to the channel. Creating it performs no request.
     *
     * @return the channel
     */
    public MessageChannel getChannel() {
        return new MessageChannel(client, channelId, guildId, channelType);
    }

    /**
     * Returns the mentions of this message. Parsed lazily on first use; thread-safe.
     *
     * @return the mentions
     */
    public Mentions getMentions() {
        var result = mentions;
        if (result == null) synchronized (this) {
            result = mentions;
            if (result == null) mentions = result = new Mentions(client, guildId, mentionData);
        }
        return result;
    }
    /**
     * Returns the owning client. Named for JDA source parity.
     *
     * @return the client
     */
    public Micup getJDA() { return client; }
    /**
     * Returns whether the message was sent in a guild.
     *
     * @return {@code true} for guild messages
     */
    public boolean isFromGuild() { return guildId != null; }
    /**
     * Returns the guild: the cached snapshot, or an id-only reference when it is not cached.
     *
     * @return the guild
     * @throws IllegalStateException if the message is not from a guild
     */
    public Guild getGuild() {
        if (!isFromGuild()) throw new IllegalStateException("Message is not from a guild");
        return client.getGuildReferenceById(guildId);
    }
    /**
     * Returns the author's guild member, built from the payload's {@code member} object merged with the author, or looked
     * up in the guild cache when the payload has none.
     *
     * @return the member, or {@code null} outside guilds or when unknown
     */
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

    /**
     * Replies to this message in the same channel.
     *
     * @param content the reply content
     * @return an action that sends the reply when queued
     * @throws IllegalArgumentException if the content is blank, or the channel or message id is not a canonical snowflake
     */
    public net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction reply(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message content is required");
        return new net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction(client,
                "/channels/" + Json.id(channelId) + "/messages", guildId,
                Json.object().put("content", content).set("message_reference", Json.object().put("message_id", Json.id(id))));
    }

    /**
     * Replies to this message with embeds.
     *
     * @param embeds the embeds to send
     * @return an action that sends the reply when queued
     * @throws IllegalArgumentException if the embeds are invalid or an id is not a canonical snowflake
     */
    public net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction replyEmbeds(MessageEmbed... embeds) {
        var body = net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds);
        body.set("message_reference", Json.object().put("message_id", Json.id(id)));
        return new net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction(client,
                "/channels/" + Json.id(channelId) + "/messages", guildId, body);
    }

    /**
     * Edits this message's content. A {@code null} content is sent as an empty string, matching
     * {@code MessageEditAction}; unlike JDA, which rejects {@code null}.
     *
     * @param content the new content, or {@code null} to clear it
     * @return an action that performs the edit when queued
     * @throws IllegalArgumentException if the channel or message id is not a canonical snowflake
     */
    public net.guneyilmaz0.micup4j.requests.restaction.MessageEditAction editMessage(String content) {
        return new net.guneyilmaz0.micup4j.requests.restaction.MessageEditAction(client,
                "/channels/" + Json.id(channelId) + "/messages/" + Json.id(id), guildId,
                Json.object().put("content", content == null ? "" : content));
    }

    /**
     * Replaces the embeds of this message.
     *
     * @param embeds the new embeds
     * @return an action that performs the edit when queued
     * @throws IllegalArgumentException if the embeds are invalid or an id is not a canonical snowflake
     */
    public net.guneyilmaz0.micup4j.requests.restaction.MessageEditAction editMessageEmbeds(MessageEmbed... embeds) {
        return new net.guneyilmaz0.micup4j.requests.restaction.MessageEditAction(client,
                "/channels/" + Json.id(channelId) + "/messages/" + Json.id(id), guildId,
                net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds));
    }

    /**
     * Deletes this message.
     *
     * @return an action that deletes the message when queued; it completes with {@code null}
     * @throws IllegalArgumentException if the channel or message id is not a canonical snowflake
     */
    public RestAction<Void> delete() {
        return client.getRestClient().request("DELETE", "/channels/" + Json.id(channelId) + "/messages/" + Json.id(id), null).map(data -> null);
    }
}
