package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;

public final class Message {
    private final Micup client;
    private final String id, channelId, content;
    private final User author;

    public Message(Micup client, JsonNode data) {
        this.client = client;
        id = data.path("id").asText();
        channelId = data.path("channel_id").asText();
        content = data.path("content").asText("");
        author = User.fromJson(data.path("author"));
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
        return client.getMessageChannelById(channelId);
    }

    public RestAction<Message> reply(String content) {
        return client.getRestClient().request("POST", "/channels/" + Json.id(channelId) + "/messages",
                        Json.object().put("content", content).set("message_reference", Json.object().put("message_id", id)))
                .map(data -> new Message(client, data));
    }

    public RestAction<Message> editMessage(String content) {
        return client.getRestClient().request("PATCH", "/channels/" + Json.id(channelId) + "/messages/" + Json.id(id), Json.object().put("content", content))
                .map(data -> new Message(client, data));
    }

    public RestAction<Void> delete() {
        return client.getRestClient().request("DELETE", "/channels/" + Json.id(channelId) + "/messages/" + Json.id(id), null).map(data -> null);
    }
}