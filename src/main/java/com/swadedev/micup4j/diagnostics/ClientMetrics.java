package com.swadedev.micup4j.diagnostics;

import com.swadedev.micup4j.Micup;

/**
 * Client diagnostics without URLs, tokens, or event payloads.
 *
 * @param status                    the session status
 * @param rest                      the REST scheduler counters
 * @param queuedEvents              events waiting for listeners
 * @param eventCapacity             the event queue bound
 * @param deliveredEvents           events delivered to listeners
 * @param listenerFailures          listener exceptions caught and reported
 * @param lastEventQueueDelayMillis how long the latest event waited for the event thread
 * @param gatewayPingMillis         the latest heartbeat latency, or -1
 * @param reconnects                Gateway reconnect attempts since creation
 */
public record ClientMetrics(Micup.Status status, RestMetrics rest, int queuedEvents, int eventCapacity,
                            long deliveredEvents, long listenerFailures, long lastEventQueueDelayMillis,
                            long gatewayPingMillis, long reconnects) {}
