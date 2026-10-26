package net.guneyilmaz0.micup4j.events;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

/**
 * Base of every event delivered to an {@link net.guneyilmaz0.micup4j.hooks.EventListener}.
 *
 * <p>Events are immutable. Listeners are invoked one event at a time on the session's dedicated event thread, never on the Gateway
 * scheduler or an HTTP thread; see {@link net.guneyilmaz0.micup4j.Micup}. The payload is copied once at construction.
 */
public class GenericEvent {
    private final Micup client;
    private final String type;
    private final long sequence;
    private final JsonNode data;

    /**
     * Creates an event.
     *
     * @param client   the owning client
     * @param type     the Micup dispatch type, or a library-defined type for diagnostics
     * @param sequence the Gateway sequence number
     * @param data     the payload; copied, never retained
     */
    public GenericEvent(Micup client, String type, long sequence, JsonNode data) {
        this.client = client;
        this.type = type;
        this.sequence = sequence;
        this.data = data.deepCopy();
    }

    /**
     * Returns the session that produced this event.
     *
     * @return the owning client
     */
    public Micup getMicup() {
        return client;
    }

    /** Source migration alias; this is a Micup session, never a Discord JDA instance. */
    public Micup getJDA() { return client; }
    /**
     * Returns the dispatch sequence number. JDA parity alias of {@link #getSequence()}.
     *
     * @return the sequence number
     */
    public long getResponseNumber() { return sequence; }

    /**
     * Returns the Micup dispatch type, such as {@code MESSAGE_CREATE}.
     *
     * @return the wire type
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the Gateway sequence number of the dispatch.
     *
     * @return the sequence number
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * Returns an independent deep copy of the raw payload; mutating it never affects this event.
     * Raw interaction payloads contain credentials. Never log them.
     *
     * @return a fresh copy of the payload on every call
     */
    public JsonNode getData() {
        return data.deepCopy();
    }

    /**
     * Returns the payload owned by this event without copying. Micup-only; intended for subclasses that parse
     * fields once instead of copying the payload per getter.
     * The node is shared: never mutate it, and never log it (it may contain an interaction token).
     *
     * @return the internal, read-only payload node
     */
    protected final JsonNode rawData() {
        return data;
    }

    /** Returns the type and sequence only; the payload is never included. */
    @Override
    public String toString() {
        return "MicupEvent[type=" + type + ", sequence=" + sequence + "]";
    }
}
