# Sharding and shared rate limits

This guide covers multi-session operation, released in `0.5.0-beta`.

## When to shard

Micup's `GET /gateway/bot` always recommends one shard. A bot shard that owns more than 2,500 guilds is refused with close code 4011 (`SHARDING_REQUIRED`), so split the bot before it reaches that size. A guild belongs to shard `(guild_id >> 22) % shard_count`. Only shard 0 receives account-level dispatches that name no guild, such as direct messages. Micup has no large-bot tier and no Identify concurrency buckets.

## Shard manager

```java
ShardManager shards = DefaultShardManagerBuilder.createDefault(token)
    .setShardsTotal(4)
    .addEventListeners(new MyListener())
    .setActivity(Activity.playing("/help"))
    .build();

shards.awaitReady(Duration.ofMinutes(2));
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    shards.shutdown();
    try { shards.awaitShutdown(Duration.ofSeconds(30)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
}));
```

Import from `com.swadedev.micup4j.sharding`. The builder takes the same settings as `MicupBuilder` and applies them to every shard. `addEventListeners` shares one listener between shards, while `addEventListenerProvider(id -> ...)` creates one listener per shard. Inside a listener, `event.getJDA().getShardInfo()` identifies the shard.

| Behavior | Detail |
| --- | --- |
| Startup | One starter virtual thread starts shards in id order. It waits for each Ready, up to `setShardStartTimeout` (60 s by default), before passing the next shard through the start gate. A shard that misses the timeout is reported to the error handler and keeps connecting. |
| Spacing | The default gate spaces session starts 1 s apart, staying well inside Micup's 300 Identify attempts per address per minute. |
| Reconnect | Each shard reconnects and resumes on its own, exactly like a single session. |
| Restart | `restart(id)` stops a shard immediately and queues a fresh session through the gate. Terminal failures such as 4004 or 4011 are not retried automatically. |
| Partial deployment | `setShards(2, 3)` runs only some shards here; other processes run the rest with the same total. |
| Shutdown | `shutdown()` stops every shard gracefully, waits for them, then drains the shared REST client within `RestConfig.shutdownTimeout`. `shutdownNow()` cancels everything. `shutdown(id)` stops one shard permanently. |
| Presence | `setStatus` and `setActivity` update every running shard and shards started later. |
| Lookups | `getGuildById` asks the owning shard's cache; `getGuilds`, `getStatuses`, `getShardsRunning` and `getAverageGatewayPing` aggregate. |

Each shard keeps its own Gateway connection, event thread, event queue and entity cache. A slow listener on one shard does not delay events on another.

## Shared REST rate limits

All shards of one manager use a single REST client, so route buckets, learned server buckets, global pacing (50 requests per second) and 429 cooldowns apply across shards. A global 429 on one shard pauses every shard. The REST queue capacity from `setQueueCapacity(rest, events)` is shared too, and the event capacity applies per shard.

Separate sessions can share a client without a manager:

```java
RestClient rest = RestClient.shared(token, 2048, RestConfig.defaults());
Micup first = MicupBuilder.createDefault(token).setRestClient(rest).setShard(0, 2).build();
Micup second = MicupBuilder.createDefault(token).setRestClient(rest).setShard(1, 2).build();
// After both sessions have stopped:
rest.shutdown(Duration.ofSeconds(10)).join();
rest.close();
```

A session never drains or closes a shared client, and building fails if the client is closed or belongs to another token.

## Extension points for multiple processes

The REST scheduler coordinates only inside one JVM. When several processes run shards of one bot:

- **Session starts:** implement `SessionStartGate` and pass it to `setSessionStartGate`. `awaitTurn(shardId)` runs on the starter thread and may block, for example on a distributed lock or a shared token bucket. `started(shardId, ready)` is the place to release that permit. This keeps processes behind one address within the Identify budget and avoids simultaneous bursts.
- **REST:** each process paces itself at 50 requests per second with its own buckets. Size the work so the sum across processes stays within the bot's limits, or route heavy REST work through one process. `RestClient.getMetrics()` exposes queue, retry and latency counters for monitoring.
- **Shard assignment:** run a disjoint `setShards(...)` range per process with the same `setShardsTotal(...)`.

Distributed REST bucket sharing is not implemented.
