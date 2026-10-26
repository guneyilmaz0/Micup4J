package net.guneyilmaz0.micup4j.events;
import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
public class ReadyEvent extends GenericEvent {
    public ReadyEvent(Micup client, long sequence, JsonNode data) {
        super(client, "READY", sequence, data);
    }
}