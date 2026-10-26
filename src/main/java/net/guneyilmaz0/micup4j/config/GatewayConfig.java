package net.guneyilmaz0.micup4j.config;

import java.time.Duration;

/**
 * Connection and reconnect timing policy. Heartbeat intervals still come from Hello.
 *
 * @param connectTimeout     the WebSocket connect timeout
 * @param handshakeTimeout   how long to wait from connecting until Ready or Resumed
 * @param reconnectBaseDelay the first reconnect delay; it doubles per attempt, with jitter
 * @param reconnectMaxDelay  the reconnect delay cap
 */
public record GatewayConfig(Duration connectTimeout, Duration handshakeTimeout,
                            Duration reconnectBaseDelay, Duration reconnectMaxDelay) {
    /**
     * Validates the policy.
     *
     * @throws IllegalArgumentException if a duration is not positive or the base delay exceeds the maximum
     */
    public GatewayConfig {
        RestConfig.positive(connectTimeout); RestConfig.positive(handshakeTimeout);
        RestConfig.positive(reconnectBaseDelay); RestConfig.positive(reconnectMaxDelay);
        if (reconnectBaseDelay.compareTo(reconnectMaxDelay) > 0)
            throw new IllegalArgumentException("Reconnect base delay exceeds maximum");
    }
    /**
     * Returns the defaults: 15 s connect, 60 s handshake, and reconnect delays from 1 s to 30 s.
     *
     * @return the defaults: 15 s connect, 60 s handshake, and reconnect delays from 1 s to 30 s
     */
    public static GatewayConfig defaults() {
        return new GatewayConfig(Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(1), Duration.ofSeconds(30));
    }
}
