package net.guneyilmaz0.micup4j.events;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

public class GenericEvent {
    private final Micup client;
    private final String type;
    private final long sequence;
    private final JsonNode data;

    public GenericEvent(Micup client, String type, long sequence, JsonNode data) {
        this.client = client;
        this.type = type;
        this.sequence = sequence;
        this.data = data.deepCopy();
    }

    public Micup getMicup() {
        return client;
    }

    public String getType() {
        return type;
    }

    public long getSequence() {
        return sequence;
    }

    /**
     * Raw interaction payloads contain credentials. Never log them.
     */
    public JsonNode getData() {
        return data.deepCopy();
    }

    @Override
    public String toString() {
        return "MicupEvent[type=" + type + ", sequence=" + sequence + "]";
    }
}