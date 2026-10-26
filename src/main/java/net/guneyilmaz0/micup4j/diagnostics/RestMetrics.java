package net.guneyilmaz0.micup4j.diagnostics;

/**
 * Credential-free point-in-time scheduler counters. Latency covers HTTP attempts, not queue time.
 *
 * @param queued               requests waiting to start
 * @param inFlight             requests with an HTTP attempt running
 * @param normalCapacity       the admission bound for ordinary requests
 * @param callbackCapacity     the extra admission bound for initial interaction callbacks
 * @param submitted            requests admitted since creation
 * @param completed            requests that succeeded
 * @param failed               requests that failed
 * @param cancelled            requests that were cancelled
 * @param retries              HTTP 429 retries
 * @param lastLatencyMillis    the most recent attempt's latency
 * @param averageLatencyMillis the mean attempt latency
 * @param trackedRoutes        normalized routes with rate-limit state
 * @param trackedBuckets       learned server buckets
 */
public record RestMetrics(int queued, int inFlight, int normalCapacity, int callbackCapacity,
                          long submitted, long completed, long failed, long cancelled, long retries,
                          long lastLatencyMillis, double averageLatencyMillis, int trackedRoutes, int trackedBuckets) {}
