# Roadmap

Micup4J aims to make long-running Micup bots straightforward to build in Java: asynchronous REST operations, predictable event delivery, and a familiar typed API.

Milestones describe intended scope, not guaranteed dates. Micup API availability and feedback may change priorities. Planned features are not part of the current release unless marked complete.

| Milestone | Focus | Status |
| --- | --- | --- |
| `0.1.0-alpha` | Core bot session and REST foundation | Implemented |
| `0.2.0-alpha` | Reliability, observability, and request scheduling | Implemented |
| `0.3.0-alpha` | JDA-style bot API, entities, caches, and permissions | Implemented |
| `0.4.0-alpha` | Commands, embeds, components, and uploads | Implemented |
| `0.5.0-beta` | Multi-shard operation and performance qualification | Implemented |
| `1.0.0` | Stable core API and documented compatibility | Release candidate (`1.0.0-rc.1`) |

## Source compatibility priority

Keep common JDA bot handler bodies and method chains usable after import-prefix changes and the `JDA`/`JDABuilder` to `Micup`/`MicupBuilder` rename. Prioritize working, tested equivalents over new Micup-only calling conventions. Wire behavior and limits follow Micup. Unsupported Discord features must be explicit rather than silent no-ops.

The first supported source fixture is [JdaStyleBot](src/examples/java/com/swadedev/micup4j/examples/JdaStyleBot.java); see the [compatibility matrix and migration guide](docs/JDA-COMPATIBILITY.md). This is source compatibility for an expanding subset, not a measured percentage or binary compatibility claim.

- [x] Common listener packages, typed slash callbacks and entity getter conventions.
- [x] Mutable `Commands.slash`, typed bulk command actions and guild command operations.
- [x] Fluent ephemeral callbacks, typed options and acknowledgement-aware interaction hooks.
- [x] Compile a representative JDA-style bot and verify the adapted flows against local protocol fixtures.
- [x] Add BMO-style cached-mention/avatar-color fixtures, typed guild lifecycle events, and basic presence configuration.
- [ ] Extend fixtures to the full original BMO command manager, uncached member workflows and shard configuration.
- [x] Add fluent message create/edit actions and advanced command definitions, including typed autocomplete.
- [x] Extend webhook/followup builders with compatible fluent editing and ephemeral setters.
- [x] Add compatible shard-manager entry points (`DefaultShardManagerBuilder`, `ShardManager`, `getShardInfo`).

## 0.1.0-alpha — Foundation

- [x] Java 21 build, sources JAR, and Javadoc JAR.
- [x] Endpoint discovery and bot authentication.
- [x] Gateway heartbeat, reconnect, Resume, and terminal error handling.
- [x] Listener adapter and typed ready/message/interaction events.
- [x] Lazy REST actions, bounded queues, and basic rate limit handling.
- [x] Message operations and basic slash command registration.
- [x] Local protocol tests and BMO integration tests.
- [x] Release documentation and a GitHub CI workflow definition.

The workflow definition is included; a successful remote CI run still needs verification after upload. Alpha status does not imply complete platform coverage.

## 0.2.0-alpha — Reliability and diagnostics

- [x] Map server bucket identifiers to normalized routes and shared major resources.
- [x] Give initial interaction callbacks deadline-aware scheduling.
- [x] Define request timeouts, cancellation propagation, and retry policies explicitly.
- [x] Add typed status changes, reconnect diagnostics, and queue/latency metrics.
- [x] Expand tests for missed ACKs, delayed handshakes, repeated reconnects, and global rate limits under concurrency.
- [x] Make startup and shutdown behavior consistent under cancellation and callback races.

**Completion criteria:** documented behavior for every supported Gateway close code; deterministic regression coverage for timeout/cancellation paths; no unbounded request or event queues; no credential-bearing diagnostic output.

This release also brings forward basic value-option definitions and rich embed builders. Advanced options, component builders, and attachment uploads remain in later milestones. See [release notes](docs/releases/README-0.2.0-alpha.md).

## 0.3.0-alpha — Entities, caches, and permissions

- [x] Add immutable guild, member, role, and typed guild-channel models.
- [x] Add typed guild/member/role/channel reads and basic guild names, member nicknames, role creation/renaming, and role assignment.
- [x] Add ordered guild ready/join/availability/leave callbacks with bounded session identity tracking.
- [x] Expose basic message mentions and primary/gradient role colors without implicit HTTP.
- [x] Add coalesced status/activity setters using Micup custom status and opcode 3.
- [x] Expand mutation coverage, audit reasons, and feature-gated permission writes.
- [x] Add bounded configurable cache policies with separate light/default presets.
- [x] Apply guild snapshots, syncs, incremental/bulk updates, deletions, and unavailable-state invalidation before listeners.
- [x] Implement unsigned 64-bit masks, role aggregation, channel overwrites, and explicit failures for incomplete permission state.
- [x] Add bounded HTTP member pages with exclusive, validated cursors.
- [x] Add indexed member queries and Gateway member-request helpers.
- [ ] Compare permission calculations with server fixtures and verify real guild/subscription recovery.

**Completion criteria:** snapshots and incremental updates yield equivalent state in tests; cache limits and invalidation rules are documented; permission results match Micup's documented rules and server fixtures.

Released as `0.3.0-alpha`. Comparing permission results with live-server fixtures and verifying real guild/subscription recovery need a live Micup deployment and remain open; they carry over to beta qualification. See [entities, caches, and permissions](docs/ENTITIES.md) and the [release notes](docs/releases/README-0.3.0-alpha.md).

## 0.4.0-alpha — Bot interaction toolkit

- [x] Add slash command options, constraints, choices, subcommands/groups and autocomplete helpers.
- [x] Add embed, button, select menu, action row, and modal builders.
- [x] Add typed basic interaction option accessors and resolved entity helpers.
- [x] Add nested command paths and leaf option accessors.
- [x] Add submitted modal-field accessors.
- [x] Add acknowledgement-aware hooks for deferred originals and basic followups.
- [x] Extend hooks with followup editing and ephemeral action setters.
- [x] Add component support to webhook actions. Micup refuses files in interaction responses, so hooks do not accept attachments.
- [x] Implement attachment uploads where Micup supports them (direct multipart message creation).
- [x] Publish examples for moderation, command routing, and interactive menus.

**Completion criteria:** supported builders validate protocol constraints before submission; tests cover reply/defer/edit/followup flows; unsupported or deployment-disabled features are clearly identified.

Released as `0.4.0-alpha`; see the [components guide](docs/COMPONENTS.md) and [release notes](docs/releases/README-0.4.0-alpha.md). Components v2, modal selects and response files are not offered by Micup and stay unsupported until the server enables them.

## 0.5.0-beta — Scale and measured performance

- [x] Add a shard manager with controlled startup, reconnect, and shutdown.
- [x] Coordinate rate limits across sessions sharing one bot token in a JVM.
- [x] Publish a repeatable load harness and representative workloads.
- [x] Measure event throughput, request latency, p95/p99 listener delay, allocation rate, and heap usage.
- [x] Test slow consumers, large startup bursts, network interruptions, and long-running operation.
- [x] Document extension points for external coordination across processes.

**Completion criteria:** reproducible benchmark settings and raw results are published; memory remains within documented configured bounds; recovery behavior is measured. Any JDA comparison must state workload differences and use comparable hardware and JVM settings.

Released as `0.5.0-beta`. Measurements use an in-process mock instance; see [performance](docs/PERFORMANCE.md) and [sharding](docs/SHARDING.md). No JDA comparison was made. A sustained real-bot deployment remains a 1.0 criterion.

## 1.0.0 — Stable core

- [x] Finalize supported public interfaces and compatibility policy.
- [x] Complete public API documentation and migration examples.
- [x] Define the supported Micup protocol/deployment compatibility matrix.
- [x] Establish package publishing, artifact signing, and release verification.
- [ ] Resolve release-blocking defects identified during beta use.
- [ ] Verify a sustained real-bot deployment under representative load.

**Completion criteria:** documented core features pass integration and recovery tests; known limitations are explicit; published artifacts can be reproduced and consumed using the documented installation paths.

`1.0.0-rc.1` completes the items above: the [compatibility policy and protocol matrix](docs/COMPATIBILITY.md), a locked API snapshot, complete Javadoc enforced by the build, and signed, reproducible release tooling ([releasing](docs/RELEASING.md)). Micup4J is licensed under the Apache License 2.0. The final `1.0.0` waits for the last two items, which need a real deployment.

## Exploratory work

Voice and music support, optional Gateway compression, coroutine adapters, and distributed coordination may become separate modules. Their scope depends on Micup's supported bot protocols and demand. They are not prerequisites for the initial stable text/interaction API.
