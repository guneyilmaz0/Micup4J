package net.guneyilmaz0.micup4j.entities;

import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;

/** Lightweight channel handle; creating one does not imply access or cache membership. */
public final class MessageChannel {
    private final Micup client;
    private final String id;

    public MessageChannel(Micup client, String id) {
        this.client = client;
        this.id = Json.id(id);
    }

    public String getId() {
        return id;
    }

    public RestAction<Message> sendMessage(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Message content is required");
        return client.getRestClient().request("POST", "/channels/" + id + "/messages", Json.object().put("content", content))
                .map(data -> new Message(client, data));
    }

    public RestAction<Message> sendMessageEmbeds(MessageEmbed... embeds) {
        return client.getRestClient().request("POST", "/channels/" + id + "/messages",
                net.guneyilmaz0.micup4j.internal.MessagePayload.embeds(embeds)).map(data -> new Message(client, data));
    }

    public RestAction<Message> retrieveMessageById(String messageId) {
        return client.getRestClient().request("GET", "/channels/" + id + "/messages/" + Json.id(messageId), null)
                .map(data -> new Message(client, data));
    }
}