package com.swadedev.micup4j.events;

import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.internal.Json;

/** Local lifecycle diagnostic; has no Gateway sequence and is not replayed. */
public final class StatusChangeEvent extends GenericEvent {
    private final Micup.Status previous, current;
    /**
     * Creates the diagnostic; called by the library.
     *
     * @param client   the session
     * @param previous the previous status
     * @param current  the new status
     */
    public StatusChangeEvent(Micup client, Micup.Status previous, Micup.Status current) {
        super(client, "CLIENT_STATUS_CHANGE", -1, Json.object().put("previous", previous.name()).put("current", current.name()));
        this.previous = previous; this.current = current;
    }
    /**
     * Returns the status before the change.
     *
     * @return the status before the change
     */
    public Micup.Status getPreviousStatus() { return previous; }
    /**
     * Returns the status after the change.
     *
     * @return the status after the change
     */
    public Micup.Status getNewStatus() { return current; }
}
