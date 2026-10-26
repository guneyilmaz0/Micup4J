package com.swadedev.micup4j;

import com.swadedev.micup4j.hooks.EventListener;
import com.swadedev.micup4j.internal.RestClient;
import com.swadedev.micup4j.internal.EntityCache;
import com.swadedev.micup4j.config.RestConfig;
import com.swadedev.micup4j.config.CachePolicy;
import com.swadedev.micup4j.config.GatewayConfig;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

/** JDA-style builder for a single Micup Gateway session. */
public final class MicupBuilder {
    private final String token;
    private com.swadedev.micup4j.managers.Presence.State presence = com.swadedev.micup4j.managers.Presence.State.defaults();
    private CachePolicy cachePolicy = CachePolicy.defaults();
    private RestConfig restConfig = RestConfig.defaults();
    private GatewayConfig gatewayConfig = GatewayConfig.defaults();
    private URI origin = URI.create("https://micup.gg");
    private final List<EventListener> listeners = new ArrayList<>();
    private List<String> ignoredEvents = List.of();
    private int restCapacity = 1024, eventCapacity = 1024, shardId, shardCount;
    private RestClient sharedRest;
    private Consumer<Throwable> errorHandler = error -> System.getLogger("Micup4J").log(System.Logger.Level.ERROR, "Micup client failure", error);

    private MicupBuilder(String token) {
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n"))
            throw new IllegalArgumentException("A raw bot token is required");
        this.token = token.strip();
        if (this.token.startsWith("Bot "))
            throw new IllegalArgumentException("Supply the raw token without the Bot prefix");
    }

    /**
     * Creates a builder for a session with bounded entity caches ({@link CachePolicy#defaults()}).
     *
     * @param token the raw bot token, without the {@code Bot} prefix
     * @return the builder
     * @throws IllegalArgumentException if the token is blank, has line breaks or includes the prefix
     */
    public static MicupBuilder createDefault(String token) {
        return new MicupBuilder(token);
    }

    /**
     * Creates a builder for a session without persistent guild, role, channel or member caches.
     *
     * @param token the raw bot token, without the {@code Bot} prefix
     * @return the builder
     * @throws IllegalArgumentException if the token is blank, has line breaks or includes the prefix
     */
    public static MicupBuilder createLight(String token) {
        return new MicupBuilder(token).setCachePolicy(CachePolicy.light());
    }

    /**
     * Sets the Micup instance to discover. Discovery reads {@code /.well-known/fluxer} below this origin.
     *
     * @param origin an HTTPS origin, or plain HTTP on loopback for development; default {@code https://micup.gg}
     * @return this builder
     */
    public MicupBuilder setInstanceOrigin(URI origin) {
        Objects.requireNonNull(origin);
        RestClient.requireEndpoint(origin, false);
        if (!origin.getPath().isEmpty() && !origin.getPath().equals("/"))
            throw new IllegalArgumentException("Instance origin cannot contain a path");
        this.origin = origin;
        return this;
    }

    /**
     * Adds listeners that receive every event of the session.
     *
     * @param listeners the listeners
     * @return this builder
     */
    public MicupBuilder addEventListeners(EventListener... listeners) {
        for (EventListener listener : listeners) this.listeners.add(Objects.requireNonNull(listener));
        return this;
    }

    /**
     * Removes listeners added earlier.
     *
     * @param values the listeners to remove
     * @return this builder
     */
    public MicupBuilder removeEventListeners(EventListener... values) {
        for (var listener : values) listeners.remove(listener); return this;
    }

    /**
     * Sets Gateway dispatch names this session does not want to receive, at most 256. Cached sessions cannot ignore the
     * entity events their cache needs.
     *
     * @param names upper-case dispatch names, such as {@code TYPING_START}
     * @return this builder
     */
    public MicupBuilder setIgnoredEvents(String... names) {
        ignoredEvents = Arrays.stream(names).map(name -> Objects.requireNonNull(name).toUpperCase(Locale.ROOT)).distinct().toList();
        if (ignoredEvents.size() > 256 || ignoredEvents.stream().anyMatch(name -> !name.matches("[A-Z_]{1,100}") || Set.of("READY", "RESUMED", "INTERACTION_CREATE").contains(name)))
            throw new IllegalArgumentException("Invalid or critical ignored event");
        return this;
    }

    /**
     * Sets queue capacities.
     *
     * @param rest   REST operations queued or running, at least 1; default 1024
     * @param events Gateway events waiting for listeners, at least 1; default 1024
     * @return this builder
     */
    public MicupBuilder setQueueCapacity(int rest, int events) {
        if (rest < 1 || events < 1) throw new IllegalArgumentException("Queue capacities must be positive");
        restCapacity = rest;
        eventCapacity = events;
        return this;
    }

    /**
     * Sets this session's shard pair. It opens one session for that shard only; use a shard manager to run several.
     *
     * @param id    the zero-based shard id, below {@code count}
     * @param count the total shard count, 1-16,384
     * @return this builder
     */
    public MicupBuilder setShard(int id, int count) {
        if (count < 1 || count > 16384 || id < 0 || id >= count)
            throw new IllegalArgumentException("Invalid shard pair");
        shardId = id;
        shardCount = count;
        return this;
    }

    /**
     * Sets the handler for session failures, listener exceptions and sanitized diagnostics.
     *
     * @param handler the handler; the default logs at ERROR
     * @return this builder
     */
    public MicupBuilder setErrorHandler(Consumer<Throwable> handler) {
        errorHandler = Objects.requireNonNull(handler);
        return this;
    }

    /**
     * Sets the REST policy: timeouts, retries, concurrency, cache sizes and the shutdown drain.
     *
     * @param config the policy
     * @return this builder
     */
    public MicupBuilder setRestConfig(RestConfig config) {
        restConfig = Objects.requireNonNull(config); return this;
    }

    /**
     * Sets the Gateway policy: connect and handshake timeouts and reconnect backoff.
     *
     * @param config the policy
     * @return this builder
     */
    public MicupBuilder setGatewayConfig(GatewayConfig config) {
        gatewayConfig = Objects.requireNonNull(config); return this;
    }

    /**
     * Sets the status published when the session starts.
     *
     * @param status the status
     * @return this builder
     */
    public MicupBuilder setStatus(OnlineStatus status) {
        presence = new com.swadedev.micup4j.managers.Presence.State(status, presence.activity(), presence.idle(), true, presence.activityConfigured()); return this;
    }
    /**
     * Sets the activity published when the session starts, rendered as Micup custom-status text.
     *
     * @param activity the activity, or {@code null} for none
     * @return this builder
     */
    public MicupBuilder setActivity(com.swadedev.micup4j.entities.Activity activity) {
        presence = new com.swadedev.micup4j.managers.Presence.State(presence.status(), activity, presence.idle(), true, true); return this;
    }
    /**
     * Sets whether the session starts as away.
     *
     * @param idle the away flag
     * @return this builder
     */
    public MicupBuilder setIdle(boolean idle) {
        presence = new com.swadedev.micup4j.managers.Presence.State(presence.status(), presence.activity(), idle, true, presence.activityConfigured()); return this;
    }
    /**
     * Shares a REST client with other sessions of the same bot token, so their requests use one rate-limit scheduler.
     * The session never drains or closes a shared client: its owner does, after every session using it has stopped. The
     * builder's queue capacity and {@link RestConfig} do not apply to a shared client.
     *
     * @param client a client from {@link RestClient#shared(String, int, RestConfig)}, or {@code null} for a private one
     * @return this builder
     */
    public MicupBuilder setRestClient(RestClient client) { sharedRest = client; return this; }

    /**
     * Sets the entity cache bounds.
     *
     * @param policy the policy; {@link CachePolicy#light()} disables caching
     * @return this builder
     */
    public MicupBuilder setCachePolicy(CachePolicy policy) {
        cachePolicy = Objects.requireNonNull(policy); return this;
    }

    /**
     * Builds the session and starts connecting in the background. Use {@link Micup#awaitReady()} to wait for Ready.
     *
     * @return the session
     * @throws IllegalStateException if an enabled cache ignores its required events, or a shared REST client is closed or
     *                               uses another token
     */
    public Micup build() {
        if (cachePolicy.maxGuilds() > 0 && ignoredEvents.stream().anyMatch(EntityCache::isEntityEvent))
            throw new IllegalStateException("Entity cache requires its Gateway update events; disable the cache to ignore them");
        if (sharedRest != null && (!sharedRest.isForToken(token) || sharedRest.isShutdown()))
            throw new IllegalStateException("A shared REST client must be open and use the same bot token");
        return new Micup(token, origin, List.copyOf(listeners), ignoredEvents, restCapacity, eventCapacity, shardId, shardCount, errorHandler, restConfig, gatewayConfig, cachePolicy, presence, sharedRest);
    }
}
