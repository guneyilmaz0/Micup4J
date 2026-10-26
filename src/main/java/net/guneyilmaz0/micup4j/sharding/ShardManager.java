package net.guneyilmaz0.micup4j.sharding;

import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.OnlineStatus;
import net.guneyilmaz0.micup4j.entities.Activity;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.entities.User;
import net.guneyilmaz0.micup4j.internal.RestClient;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Runs several shards of one bot in this JVM, modelled on JDA's {@code ShardManager}. Shards share one REST client, so
 * their requests share rate-limit buckets and global pacing. Each shard keeps its own Gateway connection, event thread,
 * cache and reconnect handling. Events carry their own shard through {@code event.getJDA()}.
 */
public interface ShardManager {
    /** @return the total shard count every session identifies with */
    int getShardsTotal();
    /** @return the started shards, ordered by id */
    List<Micup> getShards();
    /**
     * Returns a started shard.
     *
     * @param id the shard id
     * @return the shard, or {@code null} when it is not managed or not started yet
     */
    Micup getShardById(int id);
    /** @return the number of shards whose status is {@code CONNECTED} */
    int getShardsRunning();
    /** @return the number of managed shards not started yet */
    int getShardsQueued();
    /** @return every managed shard id mapped to its status; queued shards are absent */
    Map<Integer, Micup.Status> getStatuses();
    /** @return the shared REST client */
    RestClient getRestClient();

    /**
     * Returns the shard that owns a guild, by Micup's {@code (guild_id >> 22) % shard_count} rule.
     *
     * @param guildId the guild id
     * @return the shard id
     */
    int getShardIdForGuild(String guildId);

    /**
     * Returns a cached guild from the shard that owns it.
     *
     * @param id the guild id
     * @return the guild, or {@code null} when uncached or its shard is not running here
     */
    Guild getGuildById(String id);
    /** @return the cached guilds of every shard */
    List<Guild> getGuilds();
    /** @return the mean heartbeat latency of connected shards in milliseconds, or -1 */
    double getAverageGatewayPing();

    /**
     * Retrieves a user over the shared REST client.
     *
     * @param id the user id
     * @return the action
     * @throws IllegalStateException if no shard has started
     */
    RestAction<User> retrieveUserById(String id);

    /** Sets the status of every shard, including shards started later. */
    void setStatus(OnlineStatus status);
    /** Sets the activity of every shard, including shards started later; {@code null} clears it. */
    void setActivity(Activity activity);

    /**
     * Blocks until every managed shard was started and reached Ready.
     *
     * @param timeout the longest wait
     * @return {@code true} if every shard is ready
     * @throws InterruptedException if interrupted
     */
    boolean awaitReady(Duration timeout) throws InterruptedException;

    /**
     * Stops shard {@code id} immediately and starts a fresh session for it through the start gate.
     *
     * @param id a managed shard id
     * @throws IllegalArgumentException if the id is not managed
     * @throws IllegalStateException after shutdown
     */
    void restart(int id);

    /**
     * Gracefully shuts one shard down. It is not restarted.
     *
     * @param id the shard id
     */
    void shutdown(int id);

    /**
     * Gracefully shuts every shard down, then drains the shared REST client within its configured timeout. Non-blocking
     * and idempotent.
     */
    void shutdown();

    /** Stops every shard and cancels pending REST work immediately. */
    void shutdownNow();

    /**
     * Waits until every shard and the REST client stopped.
     *
     * @param timeout the longest wait
     * @return {@code true} if everything stopped in time
     * @throws InterruptedException if interrupted
     */
    boolean awaitShutdown(Duration timeout) throws InterruptedException;
}
