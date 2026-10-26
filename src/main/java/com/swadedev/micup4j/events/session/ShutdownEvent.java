package com.swadedev.micup4j.events.session;

import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.events.GenericEvent;
import com.swadedev.micup4j.internal.Json;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Local lifecycle event delivered once, after the session reached {@link Micup.Status#SHUTDOWN}, mirroring JDA's
 * {@code ShutdownEvent}.
 *
 * <p>It follows the final {@link com.swadedev.micup4j.events.StatusChangeEvent}. After {@code shutdown()} it is
 * dispatched after all previously queued events; after {@code shutdownNow()} delivery waits a bounded time for
 * in-flight listeners and is best effort. It has no Gateway sequence ({@link #getSequence()} is {@code -1}) and is
 * never replayed. Like JDA, {@link #getCode()} returns the raw close code; Micup4J exposes no Discord
 * {@code CloseCode} enum or {@code getCloseCode()}.
 */
public final class ShutdownEvent extends GenericEvent {
    /** The close code reported for a shutdown requested through {@code shutdown()} or {@code shutdownNow()}. */
    public static final int NORMAL_CLOSURE = 1000;

    private final OffsetDateTime shutdownTime;
    private final int code;

    /**
     * Creates the event. Called by the library; not intended for user code.
     *
     * @param client       the session that shut down
     * @param shutdownTime the time the session reached {@link Micup.Status#SHUTDOWN}
     * @param code         the WebSocket close code of the shutdown
     */
    public ShutdownEvent(final Micup client, final OffsetDateTime shutdownTime, final int code) {
        super(client, "CLIENT_SHUTDOWN", -1, Json.object().put("code", code));
        this.shutdownTime = Objects.requireNonNull(shutdownTime, "shutdownTime");
        this.code = code;
    }

    /**
     * Returns the time the session reached {@link Micup.Status#SHUTDOWN}.
     *
     * @return the shutdown time
     */
    public OffsetDateTime getTimeShutdown() {
        return shutdownTime;
    }

    /**
     * Returns the raw WebSocket close code; {@link #NORMAL_CLOSURE} for a requested shutdown.
     *
     * @return the close code
     */
    public int getCode() {
        return code;
    }
}
