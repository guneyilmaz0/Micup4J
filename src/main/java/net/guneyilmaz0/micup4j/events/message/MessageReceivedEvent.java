package net.guneyilmaz0.micup4j.events.message;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

public final class MessageReceivedEvent extends net.guneyilmaz0.micup4j.events.MessageReceivedEvent {
    public MessageReceivedEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }
}
