# Micup4J 0.1 Alpha

The first alpha of **Micup4J**, an asynchronous Java library for building Micup bots, is ready for early testing.

Inspired by JDA's developer-facing API, Micup4J brings builders, event listeners, and lazy REST actions to Micup's REST and Gateway protocols through its own implementation.

**Version:** `0.1.0-alpha`  
**Tag:** `v0.1.0-alpha`  
**Java:** 21+  
**Release date:** October 25, 2026

## What's included

- **Automatic endpoint discovery** using Micup's public API and Gateway endpoints.
- **Gateway connectivity** with Identify, heartbeat/ACK, reconnect backoff, Resume, and Invalid Session recovery.
- **Event listeners** for ready, message, and interaction events, with raw access to other dispatches.
- **Asynchronous REST actions** through callbacks and `CompletableFuture`, plus blocking execution when needed.
- **Bounded queues and rate limit handling**, including HTTP 429 retries and route cooldowns.
- **Message operations** for sending, retrieving, replying, editing, and deleting messages.
- **Basic slash commands and interactions**, including ephemeral replies, deferred responses, original response editing, and followups.
- **Explicit shard configuration** for individual Gateway sessions.
- A runnable ping bot example, source and Javadoc artifacts, and a GitHub build workflow.

## Reliability fixes

This release preserves cooldowns after the final rate limit retry, closes Gateway connections that finish opening after shutdown, and handles permanent client failures consistently. The new `termination()` future lets applications wait for shutdown or failure without keeping a failed bot running indefinitely.

## Build and try it

With JDK 21 installed, run these commands from the source checkout:

```bash
./gradlew clean build
```

Set `MICUP_TOKEN` in your environment and start the included example:

```bash
./gradlew runExample
```

Invite the bot to your Micup server with View Channel and Send Messages permissions, then send `!ping`. Keep your token out of source files and logs.

For local Maven installation:

```bash
./gradlew publishToMavenLocal
```

Then use this dependency in a project configured with `mavenLocal()` and `mavenCentral()`:

```kotlin
implementation("net.guneyilmaz0.micup4j:Micup4J:0.1.0-alpha")
```

This version is not published to Maven Central. The main JAR is a library, not a standalone bot or a bundle of its dependencies.

## Validation

- **22 Micup4J tests passed**, covering Gateway and REST behavior.
- **4 BMO integration tests passed** against the library.
- A **1,000-event ordering smoke test** verifies delivery order without dropped events in the tested workload.

These results validate the tested scenarios; they are not a production benchmark or a JDA performance comparison.

## Alpha status

The public API may change as the library develops. Persistent entity caches, permission calculation, typed embed/component builders, attachment uploads, automatic shard management, and voice support are not included yet. Micup4J does not claim JDA feature or performance parity.

## What's next

The next planned milestone focuses on reliability and diagnostics: shared route bucket handling, deadline-aware interaction scheduling, cancellation behavior, and connection metrics. Later milestones cover guild entities and permissions, richer command and component APIs, and measured multi-shard performance.

## Release artifacts

The build produces:

- `Micup4J-0.1.0-alpha.jar` — library.
- `Micup4J-0.1.0-alpha-sources.jar` — source code.
- `Micup4J-0.1.0-alpha-javadoc.jar` — API documentation.

Feedback and reproducible bug reports are welcome. Include your Java version, Micup4J version, and a minimal example; remove credentials and private message content before sharing logs.
