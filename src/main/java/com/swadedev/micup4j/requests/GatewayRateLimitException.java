package com.swadedev.micup4j.requests;

import java.time.Duration;

/** A Gateway command was refused by its server-side budget, for example a complete member list within 30 seconds. */
public final class GatewayRateLimitException extends RuntimeException {
    /** How long until the command may be retried. */
    private final Duration retryAfter;

    /**
     * Creates the exception.
     *
     * @param opcode     the refused opcode
     * @param retryAfter how long until the command can be retried
     */
    public GatewayRateLimitException(int opcode, Duration retryAfter) {
        super("Gateway opcode " + opcode + " was rate limited; retry after " + retryAfter.toMillis() + " ms");
        this.retryAfter = retryAfter;
    }

    /**
     * Returns how long to wait before retrying.
     *
     * @return the remaining window
     */
    public Duration getRetryAfter() { return retryAfter; }
}
