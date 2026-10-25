package net.guneyilmaz0.micup4j.events;

import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;
import java.time.Duration;

/** Sanitized reconnect diagnostics. Server reason text and authentication payloads are excluded. */
public final class ReconnectEvent extends GenericEvent {
    public enum Reason { SERVER_REQUEST, TRANSPORT_ERROR, HEARTBEAT_TIMEOUT, HANDSHAKE_TIMEOUT, CLOSE_CODE, INVALID_PAYLOAD }
    private final Reason reason;
    private final int attempt, closeCode;
    private final Duration delay;
    public ReconnectEvent(Micup client, Reason reason, int attempt, Duration delay, int closeCode) {
        super(client, "CLIENT_RECONNECT", -1, Json.object().put("reason", reason.name()).put("attempt", attempt).put("delay_ms", delay.toMillis()).put("close_code", closeCode));
        this.reason = reason; this.attempt = attempt; this.delay = delay; this.closeCode = closeCode;
    }
    public Reason getReason() { return reason; }
    public int getAttempt() { return attempt; }
    public Duration getDelay() { return delay; }
    /** -1 when no close frame was received. */
    public int getCloseCode() { return closeCode; }
}
