package com.swadedev.micup4j.hooks;
import com.swadedev.micup4j.events.GenericEvent;
/** Receives every event of a session. Implement it directly, or extend {@link ListenerAdapter} for typed callbacks. */
@FunctionalInterface
public interface EventListener {
    /**
     * Called on the session's event thread for every event, in order.
     *
     * @param event the event
     */
    void onEvent(GenericEvent event);
}
