package net.guneyilmaz0.micup4j.events;
import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.*;
public final class MessageReceivedEvent extends GenericEvent {
    private final Message message;

    public MessageReceivedEvent(Micup client, long sequence, JsonNode data) {
        super(client, "MESSAGE_CREATE", sequence, data);
        message = new Message(client, data);
    }

    public Message getMessage() {
        return message;
    }

    public User getAuthor() {
        return message.getAuthor();
    }

    public MessageChannel getChannel() {
        return message.getChannel();
    }

    public String getGuildId() {
        return getData().path("guild_id").asText(null);
    }
}
