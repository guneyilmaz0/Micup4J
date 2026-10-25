package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.hooks.EventListener;
import net.guneyilmaz0.micup4j.internal.RestClient;
import net.guneyilmaz0.micup4j.config.RestConfig;
import net.guneyilmaz0.micup4j.config.GatewayConfig;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

/** JDA-style builder for a single Micup Gateway session. */
public final class MicupBuilder {
    private final String token;
    private RestConfig restConfig = RestConfig.defaults();
    private GatewayConfig gatewayConfig = GatewayConfig.defaults();
    private URI origin = URI.create("https://micup.gg");
    private final List<EventListener> listeners = new ArrayList<>();
    private List<String> ignoredEvents = List.of();
    private int restCapacity = 1024, eventCapacity = 1024, shardId, shardCount;
    private Consumer<Throwable> errorHandler = error -> System.getLogger("Micup4J").log(System.Logger.Level.ERROR, "Micup client failure", error);

    private MicupBuilder(String token) {
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n"))
            throw new IllegalArgumentException("A raw bot token is required");
        this.token = token.strip();
        if (this.token.startsWith("Bot "))
            throw new IllegalArgumentException("Supply the raw token without the Bot prefix");
    }

    public static MicupBuilder createDefault(String token) {
        return new MicupBuilder(token);
    }

    /**
     * Currently identical to default: no persistent entity cache.
     */
    public static MicupBuilder createLight(String token) {
        return new MicupBuilder(token);
    }

    public MicupBuilder setInstanceOrigin(URI origin) {
        Objects.requireNonNull(origin);
        RestClient.requireEndpoint(origin, false);
        if (!origin.getPath().isEmpty() && !origin.getPath().equals("/"))
            throw new IllegalArgumentException("Instance origin cannot contain a path");
        this.origin = origin;
        return this;
    }

    public MicupBuilder addEventListeners(EventListener... listeners) {
        for (EventListener listener : listeners) this.listeners.add(Objects.requireNonNull(listener));
        return this;
    }

    public MicupBuilder setIgnoredEvents(String... names) {
        ignoredEvents = Arrays.stream(names).map(name -> Objects.requireNonNull(name).toUpperCase(Locale.ROOT)).distinct().toList();
        if (ignoredEvents.size() > 256 || ignoredEvents.stream().anyMatch(name -> !name.matches("[A-Z_]{1,100}") || Set.of("READY", "RESUMED", "INTERACTION_CREATE").contains(name)))
            throw new IllegalArgumentException("Invalid or critical ignored event");
        return this;
    }

    public MicupBuilder setQueueCapacity(int rest, int events) {
        if (rest < 1 || events < 1) throw new IllegalArgumentException("Queue capacities must be positive");
        restCapacity = rest;
        eventCapacity = events;
        return this;
    }

    public MicupBuilder setShard(int id, int count) {
        if (count < 1 || count > 16384 || id < 0 || id >= count)
            throw new IllegalArgumentException("Invalid shard pair");
        shardId = id;
        shardCount = count;
        return this;
    }

    public MicupBuilder setErrorHandler(Consumer<Throwable> handler) {
        errorHandler = Objects.requireNonNull(handler);
        return this;
    }

    public MicupBuilder setRestConfig(RestConfig config) {
        restConfig = Objects.requireNonNull(config); return this;
    }

    public MicupBuilder setGatewayConfig(GatewayConfig config) {
        gatewayConfig = Objects.requireNonNull(config); return this;
    }

    public Micup build() {
        return new Micup(token, origin, List.copyOf(listeners), ignoredEvents, restCapacity, eventCapacity, shardId, shardCount, errorHandler, restConfig, gatewayConfig);
    }
}
