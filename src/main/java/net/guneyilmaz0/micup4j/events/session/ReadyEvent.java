package net.guneyilmaz0.micup4j.events.session;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

/**
 * Session authentication completed (Micup {@code READY}), as JDA's {@code ReadyEvent}. The guild burst and its
 * listeners may follow.
 */
public final class ReadyEvent extends net.guneyilmaz0.micup4j.events.ReadyEvent {
    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code READY} payload
     */
    public ReadyEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }
}
