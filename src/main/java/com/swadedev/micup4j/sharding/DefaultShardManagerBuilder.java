package com.swadedev.micup4j.sharding;

import com.swadedev.micup4j.MicupBuilder;
import com.swadedev.micup4j.OnlineStatus;
import com.swadedev.micup4j.config.CachePolicy;
import com.swadedev.micup4j.config.GatewayConfig;
import com.swadedev.micup4j.config.RestConfig;
import com.swadedev.micup4j.entities.Activity;
import com.swadedev.micup4j.hooks.EventListener;
import com.swadedev.micup4j.internal.RestClient;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Builds a {@link ShardManager}, modelled on JDA's {@code DefaultShardManagerBuilder}. Every shard is configured like a
 * {@link MicupBuilder} with the same settings, and all shards share one REST client.
 *
 * <p>Micup recommends one shard and refuses a bot shard that owns more than 2,500 guilds with close code 4011, so set
 * {@link #setShardsTotal(int)} once a bot approaches that size. Shard 0 also receives account-level events such as
 * direct messages.
 */
public final class DefaultShardManagerBuilder {
    private final String token;
    private final boolean light;
    private final List<Consumer<MicupBuilder>> settings = new ArrayList<>();
    private final List<EventListener> listeners = new ArrayList<>();
    private final List<IntFunction<? extends EventListener>> providers = new ArrayList<>();
    private int shardsTotal = 1, restCapacity = 1024;
    private List<Integer> shardIds;
    private RestConfig restConfig = RestConfig.defaults();
    private Duration startTimeout = Duration.ofSeconds(60);
    private SessionStartGate gate = SessionStartGate.sequential(Duration.ofSeconds(1));
    private Consumer<Throwable> errorHandler = error -> System.getLogger("Micup4J").log(System.Logger.Level.ERROR, "Micup shard failure", error);

    private DefaultShardManagerBuilder(String token, boolean light) {
        MicupBuilder.createLight(token); // Validates the token format with the same rules as a single session.
        this.token = token.strip(); this.light = light;
    }

    /**
     * Creates a builder for shards with bounded entity caches, as {@link MicupBuilder#createDefault(String)}.
     *
     * @param token the raw bot token
     * @return the builder
     * @throws IllegalArgumentException if the token format is invalid
     */
    public static DefaultShardManagerBuilder createDefault(String token) { return new DefaultShardManagerBuilder(token, false); }
    /**
     * Creates a builder for shards without entity caches, as {@link MicupBuilder#createLight(String)}.
     *
     * @param token the raw bot token
     * @return the builder
     * @throws IllegalArgumentException if the token format is invalid
     */
    public static DefaultShardManagerBuilder createLight(String token) { return new DefaultShardManagerBuilder(token, true); }

    /**
     * Sets the total shard count every session identifies with.
     *
     * @param total 1 to 16,384
     * @return this builder
     */
    public DefaultShardManagerBuilder setShardsTotal(int total) {
        if (total < 1 || total > 16_384) throw new IllegalArgumentException("Shard total must be 1-16384");
        shardsTotal = total; return this;
    }

    /**
     * Runs only some shards in this JVM; other processes can run the rest.
     *
     * @param ids shard ids below the total
     * @return this builder
     */
    public DefaultShardManagerBuilder setShards(int... ids) {
        shardIds = Arrays.stream(ids).boxed().toList();
        return this;
    }

    /**
     * Runs an inclusive range of shards in this JVM.
     *
     * @param minimum the first shard id
     * @param maximum the last shard id
     * @return this builder
     */
    public DefaultShardManagerBuilder setShards(int minimum, int maximum) {
        if (minimum < 0 || maximum < minimum) throw new IllegalArgumentException("Invalid shard range");
        shardIds = java.util.stream.IntStream.rangeClosed(minimum, maximum).boxed().toList();
        return this;
    }

    /**
     * Adds listeners shared by every shard; use {@code event.getJDA()} to tell shards apart.
     *
     * @param values the listeners
     * @return this builder
     */
    public DefaultShardManagerBuilder addEventListeners(EventListener... values) { listeners.addAll(Arrays.asList(values)); return this; }
    /**
     * Creates one listener per shard, as JDA's {@code addEventListenerProvider}.
     *
     * @param provider receives the shard id and returns that shard's listener
     * @return this builder
     */
    public DefaultShardManagerBuilder addEventListenerProvider(IntFunction<? extends EventListener> provider) { providers.add(Objects.requireNonNull(provider)); return this; }
    /**
     * Sets the Micup instance every shard discovers; see {@link MicupBuilder#setInstanceOrigin(URI)}.
     *
     * @param origin the instance origin
     * @return this builder
     */
    public DefaultShardManagerBuilder setInstanceOrigin(URI origin) { settings.add(b -> b.setInstanceOrigin(origin)); return this; }
    /**
     * Sets dispatch names every shard ignores; see {@link MicupBuilder#setIgnoredEvents(String...)}.
     *
     * @param names the dispatch names
     * @return this builder
     */
    public DefaultShardManagerBuilder setIgnoredEvents(String... names) { settings.add(b -> b.setIgnoredEvents(names)); return this; }
    /**
     * Sets every shard's Gateway policy.
     *
     * @param config the policy
     * @return this builder
     */
    public DefaultShardManagerBuilder setGatewayConfig(GatewayConfig config) { settings.add(b -> b.setGatewayConfig(config)); return this; }
    /**
     * Sets every shard's entity cache bounds.
     *
     * @param policy the policy
     * @return this builder
     */
    public DefaultShardManagerBuilder setCachePolicy(CachePolicy policy) { settings.add(b -> b.setCachePolicy(policy)); return this; }
    /**
     * Sets the status every shard starts with.
     *
     * @param status the status
     * @return this builder
     */
    public DefaultShardManagerBuilder setStatus(OnlineStatus status) { settings.add(b -> b.setStatus(status)); return this; }
    /**
     * Sets the activity every shard starts with.
     *
     * @param activity the activity, or {@code null}
     * @return this builder
     */
    public DefaultShardManagerBuilder setActivity(Activity activity) { settings.add(b -> b.setActivity(activity)); return this; }
    /**
     * Sets whether every shard starts as away.
     *
     * @param idle the away flag
     * @return this builder
     */
    public DefaultShardManagerBuilder setIdle(boolean idle) { settings.add(b -> b.setIdle(idle)); return this; }

    /**
     * Sets the shared REST client's policy; its shutdown timeout bounds the manager's REST drain.
     *
     * @param config the policy
     * @return this builder
     */
    public DefaultShardManagerBuilder setRestConfig(RestConfig config) { restConfig = Objects.requireNonNull(config); return this; }

    /**
     * Sets queue capacities: the REST queue is shared by every shard, the event queue is per shard.
     *
     * @param rest   shared REST capacity
     * @param events per-shard event capacity
     * @return this builder
     */
    public DefaultShardManagerBuilder setQueueCapacity(int rest, int events) {
        if (rest < 1) throw new IllegalArgumentException("REST capacity must be positive");
        restCapacity = rest; settings.add(b -> b.setQueueCapacity(1, events)); return this;
    }

    /**
     * Sets how long the starter waits for a shard's Ready before starting the next one. A shard that misses it keeps
     * connecting in the background.
     *
     * @param timeout a positive timeout; default 60 seconds
     * @return this builder
     */
    public DefaultShardManagerBuilder setShardStartTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Start timeout must be positive");
        startTimeout = timeout; return this;
    }

    /**
     * Replaces the start gate, the extension point for cross-process coordination.
     *
     * @param gate the gate; default {@link SessionStartGate#sequential(Duration)} with one second spacing
     * @return this builder
     */
    public DefaultShardManagerBuilder setSessionStartGate(SessionStartGate gate) { this.gate = Objects.requireNonNull(gate); return this; }

    /**
     * Receives shard failures and start timeouts, and is installed on every shard.
     *
     * @param handler the handler
     * @return this builder
     */
    public DefaultShardManagerBuilder setErrorHandler(Consumer<Throwable> handler) {
        errorHandler = Objects.requireNonNull(handler); settings.add(b -> b.setErrorHandler(handler)); return this;
    }

    /**
     * Builds the manager and starts its shards one at a time on a background virtual thread. Never blocks.
     *
     * @return the running manager
     * @throws IllegalArgumentException if a shard id is outside the total
     */
    public ShardManager build() {
        var ids = new TreeSet<Integer>(shardIds == null ? java.util.stream.IntStream.range(0, shardsTotal).boxed().toList() : shardIds);
        if (ids.isEmpty() || ids.first() < 0 || ids.last() >= shardsTotal) throw new IllegalArgumentException("Shard ids must be within 0 and the total");
        var rest = RestClient.shared(token, restCapacity, restConfig);
        var configured = List.copyOf(settings);
        var shared = List.copyOf(listeners);
        var perShard = List.copyOf(providers);
        IntFunction<MicupBuilder> factory = id -> {
            MicupBuilder builder = light ? MicupBuilder.createLight(token) : MicupBuilder.createDefault(token);
            configured.forEach(setting -> setting.accept(builder));
            builder.addEventListeners(shared.toArray(EventListener[]::new));
            perShard.forEach(provider -> builder.addEventListeners(provider.apply(id)));
            return builder.setRestClient(rest).setShard(id, shardsTotal);
        };
        var manager = new DefaultShardManager(shardsTotal, List.copyOf(ids), factory, rest, restConfig.shutdownTimeout(), startTimeout, gate, errorHandler);
        manager.start();
        return manager;
    }
}
