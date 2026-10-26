package net.guneyilmaz0.micup4j.events.session;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

public final class ReadyEvent extends net.guneyilmaz0.micup4j.events.ReadyEvent {
    public ReadyEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }
}
