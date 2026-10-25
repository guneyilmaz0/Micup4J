package net.guneyilmaz0.micup4j.diagnostics;

import net.guneyilmaz0.micup4j.Micup;

/** Client diagnostics without URLs, tokens, or event payloads. */
public record ClientMetrics(Micup.Status status, RestMetrics rest, int queuedEvents, int eventCapacity,
                            long deliveredEvents, long listenerFailures, long lastEventQueueDelayMillis,
                            long gatewayPingMillis, long reconnects) {}
