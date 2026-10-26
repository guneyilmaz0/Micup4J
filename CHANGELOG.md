# Changelog

User-visible changes are recorded here. Versions before `1.0.0` may introduce breaking API changes; each release should document them explicitly.

## [Unreleased]

### Fixed

- The JVM no longer exits when `main` returns right after `build()`. As in JDA, a shared non-daemon `micup4j-keepalive` thread keeps the process alive from `MicupBuilder.build()` or `DefaultShardManagerBuilder.build()` until the session or shard manager shuts down or fails permanently.

## [1.0.0-rc.1] — 2026-10-27

### Breaking

- Packages move from `net.guneyilmaz0.micup4j` to `com.swadedev.micup4j`, and the Maven coordinates become `com.swadedev.micup4j:Micup4J`. Class names and APIs are otherwise unchanged; replace the import prefix and the dependency, then recompile.

### Added

- [Compatibility policy and protocol matrix](docs/COMPATIBILITY.md): semantic versioning from 1.0, the supported API surface, exclusions, deprecations and Micup feature/deployment coverage.
- A recorded public API snapshot (`api/Micup4J.api`) checked by `ApiSurfaceTest`; accept intended changes with `-PupdateApi`.
- Complete Javadoc for the supported API; javadoc now runs with `-Xdoclint:all -Werror`, excluding the internal package.
- Release tooling: POM project, SCM and developer metadata, optional in-memory signing, a local staging repository, `centralBundle` for the Maven Central Portal, `releaseChecksums`, `verifyRelease`, and a tag-triggered [release workflow](.github/workflows/release.yml) that checks reproducibility and opens a draft GitHub release. See [RELEASING.md](docs/RELEASING.md).
- `OptionData.setRequiredRange` documentation and an explicit `ListenerAdapter` constructor.

### Deprecated

- Legacy aliases kept through 1.x: flat-package `ReadyEvent`/`MessageReceivedEvent` and their `ListenerAdapter` overloads, `entities.ChannelType`, `interactions.SlashCommand`/`CommandOption`/`OptionType`, the `SlashCommand` overloads of `updateCommands`/`updateGuildCommands`, `SlashCommandData.addOption(legacy type)` and `OptionType.toLegacy()`.

### Notes

- Micup4J is licensed under the [Apache License 2.0](LICENSE). The license is declared in the POM and included in every JAR.
- Release candidate: the API is frozen for 1.0. The final release requires beta feedback and a sustained real-bot deployment.

## [0.5.0-beta] — 2026-10-27

### Added

- `sharding.DefaultShardManagerBuilder` and `ShardManager`: sequential, Ready-gated shard startup through a `SessionStartGate` (1 s spacing by default), start timeouts reported to the error handler, `restart(id)`, partial shard ranges, per-shard listener providers, aggregated guild/status/ping lookups, presence for every shard, and graceful or immediate shutdown that drains the shared REST client last.
- `SessionStartGate`, the extension point for coordinating session starts across processes.
- Shared REST rate limiting: `RestClient.shared(...)` and `MicupBuilder.setRestClient(...)` let sessions of one token share buckets, global pacing, 429 cooldowns and queue capacity. Shared clients are never drained or closed by a session.
- `Micup.getShardInfo()` and `isRestClientShared()`.
- A repeatable load harness (`./gradlew loadTest`) with event burst, paced listener delay, startup burst, REST burst/sequential, shard start and soak workloads, and published [results](docs/performance/results-0.5.0-beta.json).
- Resilience tests for large startup bursts within cache bounds, per-shard slow listeners and repeated interruptions across shards.
- [Sharding](docs/SHARDING.md) and [performance](docs/PERFORMANCE.md) guides.

### Notes

- Measurements use an in-process mock instance over loopback. They are not production figures and include no JDA comparison.

## [0.4.0-alpha] — 2026-10-27

### Added

- Immutable, validated component builders in JDA's packages: `Button`/`ButtonStyle`, `ActionRow` (with `partitionOf`), `StringSelectMenu`/`SelectOption`, `EntitySelectMenu` (user, role, mentionable and channel targets with default values), `TextInput`/`TextInputStyle`, `Modal`, and `entities.emoji.Emoji`. Limits (labels, ids, URLs, option counts, value ranges, five rows, unique custom ids) are checked before submission.
- `setComponents`, `addComponents`, `setActionRow` and `addActionRow` on message create/edit actions, interaction replies and webhook create/edit actions.
- Component and modal events: `ButtonInteractionEvent`, `StringSelectInteractionEvent`, `EntitySelectInteractionEvent`, `GenericComponentInteractionCreateEvent` and `ModalInteractionEvent`, with matching `ListenerAdapter` callbacks. Component events expose the source message and its parsed rows, `deferEdit()`, `editMessage`, `editMessageEmbeds`, `editComponents` and `ButtonInteractionEvent.editButton`; modal submissions expose `getValue(id)`/`getValues()` as `ModalMapping`.
- `replyModal(Modal)` on slash command and component events (callback type 9), and `MessageEditCallbackAction` for callback types 6 and 7.
- File uploads: `utils.FileUpload`, `MessageCreateAction.addFiles/setFiles`, `MessageChannel.sendFiles` and `Message.replyFiles`, sent as `multipart/form-data` with attachment descriptions and spoiler flags; up to 10 files per message.
- `OptionData.setRequiredRange(...)`.
- Compiled [moderation](src/examples/java/com/swadedev/micup4j/examples/ModerationExample.java) and [interactive menu](src/examples/java/com/swadedev/micup4j/examples/InteractiveMenuExample.java) examples, and a [components guide](docs/COMPONENTS.md).

### Breaking

- `entities.channel.middleman.MessageChannel` declares `sendFiles(...)`; custom implementations of that interface must add it.
- Component (type 3) and modal (type 5) interactions are delivered as the new typed event subclasses. They still extend `InteractionCreateEvent` and still reach `onInteractionCreate`.

### Not supported by Micup

- Files in interaction responses and followups, select menus and uploads inside modals, and Components v2 layouts (currently refused with `FEATURE_TEMPORARILY_DISABLED`) are not offered. Editing a message's files and the pre-signed large-upload flow are not implemented.

## [0.3.0-alpha] — 2026-10-27

### Added

- `AuditableRestAction.reason(...)` on every guild, member, role and ban mutation. Header reasons (`X-Audit-Log-Reason`) must be printable ASCII; ban and timeout reasons travel in the body and accept any Unicode text. Reasons over 512 characters fail locally instead of being dropped by the server.
- Fluent `RoleAction` (`createRole(guildId)`/`guild.createRole()`) and `RoleManager` (`modifyRole`/`guild.modifyRole(role)`) with name, color, permission, hoist and mentionable setters, plus `deleteRole`.
- Moderation actions: `kick`, `ban` (message deletion window and Micup's optional ban duration), `unban`, `timeoutFor`, `timeoutUntil` and `removeTimeout`, with JDA-style `Guild` and `Member` delegates and `addRoleToMember`/`removeRoleFromMember`/`modifyNickname`.
- Feature-gated permission writes: role writes that set a mask declare `X-Fluxer-Features`, so gated bits such as `VIEW_CHANNEL_MEMBERS` are stored exactly as sent. `Permission.getWriteFeature()` exposes the registry.
- Indexed member search through `searchMembers(guildId)`/`guild.searchMembers()` with query, role, join-time, account-age, bot, join-source, invite and sort filters, returning partial `MemberSearchResult` entries.
- Gateway member requests (opcode 8): `retrieveMembersByPrefix`, `retrieveMembersByIds` and `loadMembers` returning a JDA-style `utils.concurrent.Task`. Requests run one at a time per connection with unique nonces, collect every chunk, fail on `RATE_LIMITED` with `GatewayRateLimitException`, time out after 30 seconds without a chunk, and survive reconnects while queued.
- `RestClient.request(..., Map<String, String> headers)` for `X-` extension headers.

- JDA-style `OptionData`, `SubcommandData`, and `SubcommandGroupData`, with copied children, choice typing, numeric/string/channel constraints, sibling limits, and atomic validation.
- Nested command paths and leaf option access, typed `CommandAutoCompleteInteractionEvent`/`AutoCompleteQuery`, and lazy type-8 callbacks with shared deadlines and acknowledgement protection.
- Fluent `MessageCreateAction`/`MessageEditAction` content and embed setters, preserving reply references and guild context. Payloads freeze on first execution; partial edits retain omitted fields.
- A compiled command toolkit example and expanded Java/Kotlin compatibility fixtures.

- Immutable guild, member, role, typed guild-channel and permission-overwrite snapshots.
- Typed HTTP reads and basic guild naming, member nickname, role creation/renaming and role-assignment actions.
- Bounded member pages with explicit limits, unsigned ascending cursors and response validation.
- Configurable `CachePolicy`, bounded Gateway-owned entity state, bulk updates, sync handling and deletion/unavailability invalidation.
- Unsigned 64-bit `PermissionSet`, the Micup permission registry and guild/channel permission resolution with incomplete-state validation.
- Snapshot/incremental equivalence, eviction, permission-ordering and HTTP/Gateway integration tests.
- [Entity API guide](docs/ENTITIES.md) documenting the 0.3 milestone scope.
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
- `RestAction` delays and combinators: `queueAfter`, `submitAfter`, `completeAfter`, `delay`, `onSuccess`, `onErrorMap`, `onErrorFlatMap`, `and`, `zip`, `allOf`, `accumulate`, default callbacks, and `utils.concurrent.DelayedCompletableFuture`.
- `InteractionHook` webhook actions (`WebhookMessageCreateAction`, `WebhookMessageEditAction`): `setEphemeral`, `getInteraction`, `getJDA`, `editMessageById`, `deleteMessageById`, `retrieveMessageById`, `"@original"` routing, plus `ReplyCallbackAction` `setEmbeds`/`addEmbeds`/`addContent` and an `Interaction` interface.
- Graceful shutdown: `shutdown()` drains REST work within `RestConfig.shutdownTimeout`; `awaitShutdown` overloads, `Status.SHUTTING_DOWN`, `events.session.ShutdownEvent` and `ListenerAdapter.onShutdown`.
- Typed `MessageUpdateEvent`, `MessageDeleteEvent`, `GenericMessageEvent`, `GuildMemberJoinEvent`, `GuildMemberRemoveEvent`, `GenericGuildMemberEvent` and matching `ListenerAdapter` callbacks.
- Kotlin compatibility sources are compiled as part of `check`; Kotlin stays out of the published POM.

### Breaking

- Guild mutation methods (`modifyGuildName`, `modifyMemberNickname`, `addMemberRole`, `removeMemberRole`, `modifyRoleName`) return `AuditableRestAction`, and `createRole(guildId, name, color, permissions)` returns `RoleAction`. Source assignments to `RestAction` remain valid; recompile because JVM descriptors changed.
- `InteractionHook.sendMessage`, `editOriginal` and `editOriginalEmbeds` return new action types. Source is compatible; the JVM descriptors changed, so recompile.
- `Status` gains `SHUTTING_DOWN`; exhaustive switches over it no longer compile without a case.
- `RestConfig` gains a seventh record component, `shutdownTimeout`. The six-argument constructor remains, but six-component record patterns no longer match.

### Changed

- `createRole` with an explicit permission mask now declares the gated-permission feature, so `VIEW_CHANNEL_MEMBERS` is written as supplied instead of being cleared by the server.
- Message channel send methods and message reply/edit methods now return specialized `RestAction<Message>` subtypes. Source assignments to `RestAction<Message>` remain valid; recompile consumers because JVM method descriptors changed.

- In this checkout, `createDefault` now caches bounded entity state; `createLight` retains none. Enabled caches reject ignored state events at build time.
- Cache updates precede the matching event's listeners. HTTP responses remain detached to avoid overwriting newer Gateway state.
- Invalid permission-bearing entity events fail processing with sanitized diagnostics. Missing member roles and channel overwrites are not interpreted as empty sets.
- Cache getters now follow JDA's object-or-null convention; Optional access moves to `find...ById`.
- Reply/defer actions now complete with a typed hook instead of Void; direct `respond` retains its Void result.
- No-argument `awaitReady()` now waits until Ready or failure without an implicit timeout. Use the duration overload for a bound.
- Canonical channel types move to `entities.channel.ChannelType`; legacy constants remain a facade. Recompile alpha consumers.
- Message channel handles now use Gateway `channel_type` when available, preserving group/private and uncached voice/text distinctions.
- Gateway tests now separately await authentication and Ready delivery before injecting synthetic followup events, removing a startup-order race in the fixture.
- `shutdown()` is graceful and refuses new REST calls with `RejectedExecutionException`; `shutdownNow()` and `close()` cancel immediately. `complete()`, `completeAfter()` and `awaitShutdown` on the internal `micup4j-gateway`, `micup4j-rest-scheduler` and `micup4j-delay` threads throw `IllegalStateException`.
- Events no longer deep-copy payloads per getter. `getData()` still copies; `selectedOptions()` returns a shared read-only node.
- `Message.reply` validates the message id, and `editMessage(null)` sends an empty string (JDA rejects null).

### Fixed

- Role, channel and member events and `GUILD_UPDATE` for uncached guilds no longer insert placeholder guilds or evict real ones; an uncached `GUILD_UPDATE` delivers a guild built from its payload.
- Cached lookups return bound snapshots without per-call member copies, and `getTextChannelById` is constant time.
- Cache work and payload parsing no longer hold locks used by the Gateway thread.
- Malformed typed-event payloads (for example a blank interaction token or invalid snowflakes in `MESSAGE_CREATE`) are delivered as plain `GenericEvent` with a reported failure instead of failing the session.
- Cancelling a `queueAfter` handle invokes no callback, matching JDA.

Permission calculations are verified against documentation-derived local fixtures; comparison with live-server fixtures and real guild/subscription recovery remain open and carry over to the next milestone.

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
