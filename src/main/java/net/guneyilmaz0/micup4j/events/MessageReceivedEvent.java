package net.guneyilmaz0.micup4j.events;
import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.*;
public class MessageReceivedEvent extends GenericEvent {
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

    public boolean isFromGuild() { return message.isFromGuild(); }
    public Guild getGuild() { return message.getGuild(); }
    public Member getMember() { return message.getMember(); }
    public net.guneyilmaz0.micup4j.entities.channel.ChannelType getChannelType() {
        return isFromGuild() ? getChannel().getType() : net.guneyilmaz0.micup4j.entities.channel.ChannelType.PRIVATE;
    }
    public boolean isFromType(net.guneyilmaz0.micup4j.entities.channel.ChannelType type) { return getChannelType() == type; }

    public String getGuildId() {
        return getData().path("guild_id").asText(null);
    }
}
