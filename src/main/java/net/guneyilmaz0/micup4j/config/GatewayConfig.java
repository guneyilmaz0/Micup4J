package net.guneyilmaz0.micup4j.config;

import java.time.Duration;

/** Connection and reconnect timing policy. Heartbeat intervals still come from Hello. */
public record GatewayConfig(Duration connectTimeout, Duration handshakeTimeout,
                            Duration reconnectBaseDelay, Duration reconnectMaxDelay) {
    public GatewayConfig {
        RestConfig.positive(connectTimeout); RestConfig.positive(handshakeTimeout);
        RestConfig.positive(reconnectBaseDelay); RestConfig.positive(reconnectMaxDelay);
        if (reconnectBaseDelay.compareTo(reconnectMaxDelay) > 0)
            throw new IllegalArgumentException("Reconnect base delay exceeds maximum");
    }
    public static GatewayConfig defaults() {
        return new GatewayConfig(Duration.ofSeconds(15), Duration.ofSeconds(60), Duration.ofSeconds(1), Duration.ofSeconds(30));
    }
}
