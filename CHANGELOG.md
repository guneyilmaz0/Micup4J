# Changelog

User-visible changes are recorded here. Versions before `1.0.0` may introduce breaking API changes; each release should document them explicitly.

## [Unreleased]

Changes after the initial alpha will be recorded in this section.

## [0.1.0-alpha] — 2026-10-25

Initial alpha prepared for publication.

### Added

- Java 21 library with a JDA-inspired builder, listeners, and lazy REST actions.
- Instance discovery using the public API and Gateway endpoints.
- Gateway authentication, heartbeat/ACK, reconnect backoff, Resume, and Invalid Session recovery.
- Typed ready, message, and interaction events, plus generic dispatch access.
- Bounded REST admission and event queues with separate protocol and listener execution.
- REST callbacks, futures, blocking execution, and action composition.
- HTTP 429 retries, global pacing, and route exhaustion cooldowns.
- Message send/retrieve/reply/edit/delete operations and user retrieval.
- Basic guild/global slash command registration and interaction response operations.
- Explicit shard configuration for individual sessions.
- A runnable ping bot example and local HTTP/WebSocket regression tests.
- Build version metadata shared by the JAR manifest and HTTP User-Agent.
- README, upgrade guide, roadmap, contribution guidelines, and a GitHub build workflow.

### Fixed during alpha preparation

- Preserve the cooldown announced by the final HTTP 429 response after retries are exhausted.
- Abort a Gateway connection that finishes opening after the client has closed.
- Make permanent failure handling idempotent and expose a termination future.
- Ensure the example exits and closes resources after a permanent client failure.
- Remove duplicated hard-coded version strings from HTTP requests.

### Known limitations

No persistent entity cache, permission resolver, voice support, typed component builders, automatic shard manager, or distributed scheduler is included. This release does not claim feature or performance parity with JDA. See [ROADMAP.md](ROADMAP.md).
