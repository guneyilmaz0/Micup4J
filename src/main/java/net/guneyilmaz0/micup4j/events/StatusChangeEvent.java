package net.guneyilmaz0.micup4j.events;

import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;

/** Local lifecycle diagnostic; has no Gateway sequence and is not replayed. */
public final class StatusChangeEvent extends GenericEvent {
    private final Micup.Status previous, current;
    public StatusChangeEvent(Micup client, Micup.Status previous, Micup.Status current) {
        super(client, "CLIENT_STATUS_CHANGE", -1, Json.object().put("previous", previous.name()).put("current", current.name()));
        this.previous = previous; this.current = current;
    }
    public Micup.Status getPreviousStatus() { return previous; }
    public Micup.Status getNewStatus() { return current; }
}
