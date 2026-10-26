package net.guneyilmaz0.micup4j.events;
import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
/**
 * The legacy flat-package ready event. The client delivers {@link net.guneyilmaz0.micup4j.events.session.ReadyEvent}.
 *
 * @deprecated Use {@link net.guneyilmaz0.micup4j.events.session.ReadyEvent}; kept for source compatibility through 1.x.
 */
@Deprecated(since = "1.0.0")
public class ReadyEvent extends GenericEvent {
    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code READY} payload
     */
    public ReadyEvent(Micup client, long sequence, JsonNode data) {
        super(client, "READY", sequence, data);
    }
}