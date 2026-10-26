/**
 * Library internals. Types here are public only because other Micup4J packages use them, and they may change in any
 * release without notice.
 *
 * <p>One exception is covered by the compatibility policy: the public methods of
 * {@link com.swadedev.micup4j.internal.RestClient} that applications reach through {@code Micup.getRestClient()},
 * {@code RestClient.shared(...)} and the shard manager ({@code request}, {@code requestMultipart}, {@code shared},
 * {@code getMetrics}, {@code shutdown}, {@code isShutdown} and {@code close}).
 */
package com.swadedev.micup4j.internal;
