# Micup4J 1.0 Release Candidate 2

**Bots stay running after `main` returns.**

Micup4J `1.0.0-rc.2` is the second 1.0 release candidate. It fixes the first defect found in real-bot use and changes no API.

**Tag:** `v1.0.0-rc.2`  
**Java:** 21+  
**Release date:** October 27, 2026  
**Status:** Pre-release (release candidate)

## Fixed

Every Micup4J thread is a daemon or virtual thread, so a bot whose `main` method returned right after `build()` printed its startup message and then exited. As in JDA, a shared non-daemon `micup4j-keepalive` thread now keeps the JVM alive from `MicupBuilder.build()` or `DefaultShardManagerBuilder.build()` until the session or shard manager shuts down or fails permanently.

## Before 1.0.0

The final release still waits for:

- feedback from the release candidates and any release-blocking fixes;
- a sustained real-bot deployment under representative load.

## Upgrade notes

Use `com.swadedev.micup4j:Micup4J:1.0.0-rc.2`. There are no API changes. A process that relied on exiting by itself must now call `shutdown()` or `shutdownNow()`. See the [upgrade guide](../../UPDATE.md).

## Build

```bash
./gradlew clean build
```

Outputs:

- `Micup4J-1.0.0-rc.2.jar`
- `Micup4J-1.0.0-rc.2-sources.jar`
- `Micup4J-1.0.0-rc.2-javadoc.jar`
