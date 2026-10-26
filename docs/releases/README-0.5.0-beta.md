# Micup4J 0.5 Beta

**Sharding, shared rate limits and measured behavior under load.**

Micup4J `0.5.0-beta` runs several shards of one bot in a JVM with controlled startup and shutdown, shares REST rate limits between them, and ships a repeatable load harness with published results.

**Tag:** `v0.5.0-beta`  
**Java:** 21+  
**Release date:** October 27, 2026  
**Status:** Pre-release (beta)

## Shard manager

```java
ShardManager shards = DefaultShardManagerBuilder.createDefault(token)
    .setShardsTotal(4)
    .addEventListeners(new MyListener())
    .build();
```

- Shards start one at a time, each waiting for Ready, through a `SessionStartGate` that spaces starts by one second by default.
- `restart(id)`, partial shard ranges for multi-process deployments, per-shard listener providers, aggregated lookups and presence.
- `shutdown()` stops every shard gracefully, then drains the shared REST client.

## Shared rate limits

Every shard of a manager uses one REST client, so buckets, global pacing and 429 cooldowns are shared. Separate sessions can do the same with `RestClient.shared(...)` and `MicupBuilder.setRestClient(...)`.

## Measured locally

`./gradlew loadTest` runs seven workloads against an in-process mock instance. On an 8-CPU Linux machine with Java 21 and a 512 MiB heap, the published run measured:

- about 50,700 events/s in a burst;
- p95 listener delay of 0.19 ms at 5,000 events/s;
- about 10 KB of retained heap per cached guild;
- scheduler overhead below 0.2 ms per REST request;
- zero events lost in a 120-second soak with 11 resumable interruptions.

See [PERFORMANCE.md](../PERFORMANCE.md) and the [raw results](../performance/results-0.5.0-beta.json). These are local mock figures, not production measurements, and include no JDA comparison.

## Extension points

`SessionStartGate` coordinates session starts across processes, for example with a distributed lock. REST pacing is per JVM; see [SHARDING.md](../SHARDING.md) for multi-process guidance.

## Upgrade notes

Use `com.swadedev.micup4j:Micup4J:0.5.0-beta`. There are no breaking API changes. See the [upgrade guide](../../UPDATE.md).

## Validation

Local tests cover sequential gated startup, shared REST clients, a global rate limit on one shard delaying another, restarts, partial ranges, graceful shutdown, large startup bursts within cache bounds, slow listeners isolated per shard, and repeated interruptions across shards. Nothing in this release was exercised against a live Micup deployment.

## Build

```bash
./gradlew clean build
```

Outputs:

- `Micup4J-0.5.0-beta.jar`
- `Micup4J-0.5.0-beta-sources.jar`
- `Micup4J-0.5.0-beta-javadoc.jar`

## Still planned

The 1.0 milestone covers stable public interfaces and a compatibility policy, the protocol compatibility matrix, artifact publishing and signing, a sustained real-bot deployment under load, and live-server permission fixtures. Distributed REST rate limit coordination and voice support remain out of scope for now.
