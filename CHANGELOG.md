# Changelog

User-visible changes are recorded here. Versions before `1.0.0` may introduce breaking API changes; each release should document them explicitly.

## [Unreleased]

### Added

- Immutable guild, member, role, typed guild-channel and permission-overwrite snapshots.
- Typed HTTP reads and basic guild naming, member nickname, role creation/renaming and role-assignment actions.
- Bounded member pages with explicit limits, unsigned ascending cursors and response validation.
- Configurable `CachePolicy`, bounded Gateway-owned entity state, bulk updates, sync handling and deletion/unavailability invalidation.
- Unsigned 64-bit `PermissionSet`, the Micup permission registry and guild/channel permission resolution with incomplete-state validation.
- Snapshot/incremental equivalence, eviction, permission-ordering and HTTP/Gateway integration tests.
- [Entity API guide](docs/ENTITIES.md) documenting the unreleased 0.3 milestone scope.
- JDA-style event packages and `onSlashCommandInteraction`, with bridges for existing flat-package listeners.
- Mutable `Commands.slash` definitions, typed command update actions, upsert/retrieve/delete operations and client-bound guild helpers.
- Typed option mappings, fluent `setEphemeral` callbacks returning `InteractionHook`, acknowledgement barriers and deferred original response handling.
- Root `EmbedBuilder`/`Permission`, common entity getters, cached text-channel handles, unsigned ID overloads and custom avatar URL access.
- A compiled JDA-style example and a [source compatibility guide](docs/JDA-COMPATIBILITY.md).
- Active message mention views with cache-only typed resolution, explicit bounded member retrieval, and cancellation propagation.
- Primary/secondary `RoleColors` and the BMO-style `member.getColors().getPrimary()` accessor.
- Typed guild readiness, joining, temporary availability and leaving events, classified after cache updates with separately bounded session identities.
- Builder/runtime presence controls, `OnlineStatus` and common `Activity` factories rendered as Micup custom-status text.
- Coalesced presence scheduling, authentication gating and reconnect restoration; cancelled Gateway timers are removed immediately.
- A compiled BMO-style avatar fixture, local embed verification, and lifecycle/presence/mention regression tests.

### Changed

- In this checkout, `createDefault` now caches bounded entity state; `createLight` retains none. Enabled caches reject ignored state events at build time.
- Cache updates precede the matching event's listeners. HTTP responses remain detached to avoid overwriting newer Gateway state.
- Invalid permission-bearing entity events fail processing with sanitized diagnostics. Missing member roles and channel overwrites are not interpreted as empty sets.
- Cache getters now follow JDA's object-or-null convention; Optional access moves to `find...ById`.
- Reply/defer actions now complete with a typed hook instead of Void; direct `respond` retains its Void result.
- No-argument `awaitReady()` now waits until Ready or failure without an implicit timeout. Use the duration overload for a bound.
- Canonical channel types move to `entities.channel.ChannelType`; legacy constants remain a facade. Recompile alpha consumers.
- Message channel handles now use Gateway `channel_type` when available, preserving group/private and uncached voice/text distinctions.
- Gateway tests now separately await authentication and Ready delivery before injecting synthetic followup events, removing a startup-order race in the fixture.

Version remains `0.2.0-alpha` until the next release is explicitly prepared. Indexed member search, wider mutation coverage and live-server fixture qualification remain open in the 0.3 roadmap.

## [0.2.0-alpha] — 2026-10-26

### Added

- Bounded REST scheduling with normalized route keys, learned server-bucket sharing, major-resource separation, and bounded metadata caches.
- Separate callback admission, earliest-deadline priority, and a reserved HTTP slot for initial interaction responses.
- Configurable HTTP/total timeouts, maximum 429 retries, concurrency, cache capacity, and callback capacity through `RestConfig`.
- Cancellation propagation through raw requests, mapped actions, and active flatMap children; timed blocking `complete(Duration)`.
- `GatewayConfig` for connection, handshake, and reconnect timing.
- Typed `StatusChangeEvent` and `ReconnectEvent`, a documented `GatewayCloseCode` policy, and credential-free REST/client metric snapshots.
- Immutable basic slash value options: string, integer, boolean, user, channel, role, and number.
- `EmbedBuilder`, immutable embed snapshots, and channel/reply/interaction embed helpers.
- Interaction option value access and original response retrieval/deletion.
- Access to the discovered Media Proxy endpoint and raw original-response edits, supporting the BMO avatar command integration.
- Regression tests for shared buckets, cancellation, reserved callbacks, concurrent pacing, timeouts, missed ACKs, repeated reconnects, close-code recovery, builder validation, and delayed interaction handling.

### Changed

- Replaced sleeping route-lock workers with a bounded scheduler and asynchronous HTTP attempts.
- Initial interaction callbacks now expire locally 2.5 seconds after event construction; this budget includes listener queue time.
- `SlashCommand` definitions now include immutable option lists; the existing two-argument constructor remains available.
- Shutdown completes pending event futures and aborts delayed Gateway upgrades.
- BMO's source dependency now targets `0.2.0-alpha`.

### Upgrade notes

Default total REST duration is now two minutes. Normal request capacity is separate from eight reserved callback slots. Existing `queue()`, `submit()`, `complete()`, and basic slash command construction remain source-compatible. Recompile consuming applications after upgrading this alpha. See [UPDATE.md](UPDATE.md).

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
