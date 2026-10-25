package net.guneyilmaz0.micup4j.config;

import java.time.Duration;
import java.util.Objects;

/** Immutable per-client request policy. Only HTTP 429 responses are retried. */
public record RestConfig(Duration requestTimeout, Duration totalTimeout, int maxRetries,
                         int maxConcurrency, int bucketCacheSize, int callbackCapacity) {
    public RestConfig {
        positive(requestTimeout); positive(totalTimeout);
        if (maxRetries < 0 || maxRetries > 20 || maxConcurrency < 1 || maxConcurrency > 256
                || bucketCacheSize < 1 || bucketCacheSize > 65536 || callbackCapacity < 1 || callbackCapacity > 1024)
            throw new IllegalArgumentException("Invalid REST policy limits");
    }
    public static RestConfig defaults() { return new RestConfig(Duration.ofSeconds(20), Duration.ofMinutes(2), 5, 8, 4096, 8); }
    public RestConfig withRequestTimeout(Duration value) { return new RestConfig(value, totalTimeout, maxRetries, maxConcurrency, bucketCacheSize, callbackCapacity); }
    public RestConfig withTotalTimeout(Duration value) { return new RestConfig(requestTimeout, value, maxRetries, maxConcurrency, bucketCacheSize, callbackCapacity); }
    public RestConfig withMaxRetries(int value) { return new RestConfig(requestTimeout, totalTimeout, value, maxConcurrency, bucketCacheSize, callbackCapacity); }
    public RestConfig withMaxConcurrency(int value) { return new RestConfig(requestTimeout, totalTimeout, maxRetries, value, bucketCacheSize, callbackCapacity); }
    public RestConfig withBucketCacheSize(int value) { return new RestConfig(requestTimeout, totalTimeout, maxRetries, maxConcurrency, value, callbackCapacity); }
    public RestConfig withCallbackCapacity(int value) { return new RestConfig(requestTimeout, totalTimeout, maxRetries, maxConcurrency, bucketCacheSize, value); }
    public static void positive(Duration value) {
        Objects.requireNonNull(value);
        if (value.isNegative() || value.isZero() || value.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Timeout must be positive and at most one day");
    }
}
