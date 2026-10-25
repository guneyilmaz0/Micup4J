package net.guneyilmaz0.micup4j.diagnostics;

/** Credential-free point-in-time scheduler counters. Latency covers HTTP attempts, not queue time. */
public record RestMetrics(int queued, int inFlight, int normalCapacity, int callbackCapacity,
                          long submitted, long completed, long failed, long cancelled, long retries,
                          long lastLatencyMillis, double averageLatencyMillis, int trackedRoutes, int trackedBuckets) {}
