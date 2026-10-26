package net.guneyilmaz0.micup4j.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable per-client request policy. Only HTTP 429 responses are retried.
 *
 * @param requestTimeout   the timeout of one HTTP attempt
 * @param totalTimeout     the deadline of one request across all of its rate-limit retries
 * @param maxRetries       how many times an HTTP 429 response is retried (0 to 20)
 * @param maxConcurrency   the maximum number of requests in flight at once (1 to 256)
 * @param bucketCacheSize  the maximum number of remembered routes and rate-limit buckets (1 to 65536)
 * @param callbackCapacity the admission limit for initial interaction callbacks (1 to 1024)
 * @param shutdownTimeout  how long a graceful {@code Micup.shutdown()} lets queued and in-flight requests finish
 *                         before the remainder is cancelled
 */
public record RestConfig(Duration requestTimeout, Duration totalTimeout, int maxRetries,
                         int maxConcurrency, int bucketCacheSize, int callbackCapacity, Duration shutdownTimeout) {
    /** The default graceful shutdown drain bound. */
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Validates the policy.
     *
     * @throws NullPointerException if a timeout is {@code null}
     * @throws IllegalArgumentException if a timeout is not positive or exceeds one day, or a limit is outside its documented range
     */
    public RestConfig {
        positive(requestTimeout); positive(totalTimeout); positive(shutdownTimeout);
        if (maxRetries < 0 || maxRetries > 20 || maxConcurrency < 1 || maxConcurrency > 256
                || bucketCacheSize < 1 || bucketCacheSize > 65536 || callbackCapacity < 1 || callbackCapacity > 1024)
            throw new IllegalArgumentException("Invalid REST policy limits");
    }

    /**
     * Creates a policy with the default {@linkplain #DEFAULT_SHUTDOWN_TIMEOUT shutdown timeout}.
     * Kept for source and binary compatibility with the original six-component record.
     *
     * @param requestTimeout   the timeout of one HTTP attempt
     * @param totalTimeout     the deadline of one request across all of its retries
     * @param maxRetries       how many times an HTTP 429 response is retried
     * @param maxConcurrency   the maximum number of requests in flight at once
     * @param bucketCacheSize  the maximum number of remembered routes and buckets
     * @param callbackCapacity the admission limit for initial interaction callbacks
     */
    public RestConfig(Duration requestTimeout, Duration totalTimeout, int maxRetries,
                      int maxConcurrency, int bucketCacheSize, int callbackCapacity) {
        this(requestTimeout, totalTimeout, maxRetries, maxConcurrency, bucketCacheSize, callbackCapacity, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * Returns the default policy: 20 s per attempt, 2 min total, 5 retries, 8 concurrent requests, 4096 remembered
     * buckets, 8 concurrent initial callbacks and a 30 s shutdown drain.
     *
     * @return the default policy
     */
    public static RestConfig defaults() { return new RestConfig(Duration.ofSeconds(20), Duration.ofMinutes(2), 5, 8, 4096, 8, DEFAULT_SHUTDOWN_TIMEOUT); }
    /**
     * Returns a copy with a different value for the timeout of one HTTP attempt.
     *
     * @param value the new value; positive and at most one day
     * @return the updated policy
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is not positive or exceeds one day
     */
    public RestConfig withRequestTimeout(Duration value) { return new RestConfig(value, totalTimeout, maxRetries, maxConcurrency, bucketCacheSize, callbackCapacity, shutdownTimeout); }
    /**
     * Returns a copy with a different value for the deadline of one request across all of its rate-limit retries.
     *
     * @param value the new value; positive and at most one day
     * @return the updated policy
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is not positive or exceeds one day
     */
    public RestConfig withTotalTimeout(Duration value) { return new RestConfig(requestTimeout, value, maxRetries, maxConcurrency, bucketCacheSize, callbackCapacity, shutdownTimeout); }
    /**
     * Returns a copy with a different HTTP 429 retry limit.
     *
     * @param value how many times an HTTP 429 response is retried; 0 to 20
     * @return the updated policy
     * @throws IllegalArgumentException if {@code value} is outside 0 to 20
     */
    public RestConfig withMaxRetries(int value) { return new RestConfig(requestTimeout, totalTimeout, value, maxConcurrency, bucketCacheSize, callbackCapacity, shutdownTimeout); }
    /**
     * Returns a copy with a different in-flight request limit.
     *
     * @param value the maximum number of requests in flight at once; 1 to 256
     * @return the updated policy
     * @throws IllegalArgumentException if {@code value} is outside 1 to 256
     */
    public RestConfig withMaxConcurrency(int value) { return new RestConfig(requestTimeout, totalTimeout, maxRetries, value, bucketCacheSize, callbackCapacity, shutdownTimeout); }
    /**
     * Returns a copy with a different bound for remembered routes and rate-limit buckets.
     *
     * @param value the maximum number of remembered routes and buckets; 1 to 65536
     * @return the updated policy
     * @throws IllegalArgumentException if {@code value} is outside 1 to 65536
     */
    public RestConfig withBucketCacheSize(int value) { return new RestConfig(requestTimeout, totalTimeout, maxRetries, maxConcurrency, value, callbackCapacity, shutdownTimeout); }
    /**
     * Returns a copy with a different admission limit for initial interaction callbacks.
     *
     * @param value the admission limit; 1 to 1024
     * @return the updated policy
     * @throws IllegalArgumentException if {@code value} is outside 1 to 1024
     */
    public RestConfig withCallbackCapacity(int value) { return new RestConfig(requestTimeout, totalTimeout, maxRetries, maxConcurrency, bucketCacheSize, value, shutdownTimeout); }

    /**
     * Returns a copy with a different graceful shutdown drain bound.
     *
     * <p>Each request is also bounded by {@link #totalTimeout()}, so a drain ends at the earlier of this timeout and
     * the last pending request's own deadline.
     *
     * @param value the drain bound; positive and at most one day
     * @return the updated policy
     * @throws IllegalArgumentException if the value is not positive or exceeds one day
     */
    public RestConfig withShutdownTimeout(Duration value) { return new RestConfig(requestTimeout, totalTimeout, maxRetries, maxConcurrency, bucketCacheSize, callbackCapacity, value); }

    /**
     * Validates a timeout value. Internal helper shared by the constructor; not intended for user code.
     *
     * @param value the timeout to check
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is zero, negative or longer than one day
     */
    public static void positive(Duration value) {
        Objects.requireNonNull(value);
        if (value.isNegative() || value.isZero() || value.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Timeout must be positive and at most one day");
    }
}
