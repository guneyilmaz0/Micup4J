package net.guneyilmaz0.micup4j.events;

import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import java.time.Duration;

/** Sanitized reconnect diagnostics. Server reason text and authentication payloads are excluded. */
public final class ReconnectEvent extends GenericEvent {
    /** Why the client reconnects. */
    public enum Reason {
        /** The server sent opcode 7. */
        SERVER_REQUEST,
        /** The WebSocket failed or could not connect. */
        TRANSPORT_ERROR,
        /** A heartbeat was not acknowledged in time. */
        HEARTBEAT_TIMEOUT,
        /** Ready or Resumed did not arrive in time. */
        HANDSHAKE_TIMEOUT,
        /** The server closed the connection with a resumable or re-identifiable code. */
        CLOSE_CODE,
        /** A frame could not be processed. */
        INVALID_PAYLOAD
    }
    private final Reason reason;
    private final int attempt, closeCode;
    private final Duration delay;
    /**
     * Creates the diagnostic; called by the library.
     *
     * @param client    the session
     * @param reason    why the client reconnects
     * @param attempt   the attempt number, starting at 1
     * @param delay     the backoff before the attempt
     * @param closeCode the close code, or -1 without a close frame
     */
    public ReconnectEvent(Micup client, Reason reason, int attempt, Duration delay, int closeCode) {
        super(client, "CLIENT_RECONNECT", -1, Json.object().put("reason", reason.name()).put("attempt", attempt).put("delay_ms", delay.toMillis()).put("close_code", closeCode));
        this.reason = reason; this.attempt = attempt; this.delay = delay; this.closeCode = closeCode;
    }
    /**
     * Returns why the client reconnects.
     *
     * @return why the client reconnects
     */
    public Reason getReason() { return reason; }
    /**
     * Returns the attempt number, starting at 1.
     *
     * @return the attempt number, starting at 1
     */
    public int getAttempt() { return attempt; }
    /**
     * Returns the backoff before the attempt.
     *
     * @return the backoff before the attempt
     */
    public Duration getDelay() { return delay; }
    /**
     * Returns the close code, or -1 when no close frame was received.
     *
     * @return the close code, or -1 when no close frame was received
     */
    public int getCloseCode() { return closeCode; }
}
