# Roadmap

Micup4J aims to make long-running Micup bots straightforward to build in Java: asynchronous REST operations, predictable event delivery, and a familiar typed API.

Milestones describe intended scope, not guaranteed dates. Micup API availability and feedback may change priorities. Planned features are not part of the current release unless marked complete.

| Milestone | Focus | Status |
| --- | --- | --- |
| `0.1.0-alpha` | Core bot session and REST foundation | Implemented |
| `0.2.0-alpha` | Reliability, observability, and request scheduling | Implemented |
| `0.3.0-alpha` | Guild entities, configurable caches, and permissions | Planned |
| `0.4.0-alpha` | Commands, embeds, components, and uploads | Planned |
| `0.5.0-beta` | Multi-shard operation and performance qualification | Planned |
| `1.0.0` | Stable core API and documented compatibility | Planned |

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

- [ ] Add guild, member, role, and typed channel models.
- [ ] Implement guild/member/role retrieval and mutation APIs supported for bots.
- [ ] Add configurable cache policies with separate light/default presets.
- [ ] Apply complete guild snapshots and incremental events consistently.
- [ ] Implement permission bitfields, role aggregation, and channel overrides.
- [ ] Support member queries and bounded pagination.

**Completion criteria:** snapshots and incremental updates yield equivalent state in tests; cache limits and invalidation rules are documented; permission results match Micup's documented rules and server fixtures.

## 0.4.0-alpha — Bot interaction toolkit

- [ ] Add slash command options, subcommands, and autocomplete helpers.
- [ ] Add embed, button, select menu, action row, and modal builders.
- [ ] Add typed interaction option and submitted-field accessors.
- [ ] Add interaction hooks for deferred responses and followup management.
- [ ] Implement attachment uploads where Micup supports them.
- [ ] Publish examples for moderation, command routing, and interactive menus.

**Completion criteria:** supported builders validate protocol constraints before submission; tests cover reply/defer/edit/followup flows; unsupported or deployment-disabled features are clearly identified.

## 0.5.0-beta — Scale and measured performance

- [ ] Add a shard manager with controlled startup, reconnect, and shutdown.
- [ ] Coordinate rate limits across sessions sharing one bot token in a JVM.
- [ ] Publish a repeatable load harness and representative workloads.
- [ ] Measure event throughput, request latency, p95/p99 listener delay, allocation rate, and heap usage.
- [ ] Test slow consumers, large startup bursts, network interruptions, and long-running operation.
- [ ] Document extension points for external coordination across processes.

**Completion criteria:** reproducible benchmark settings and raw results are published; memory remains within documented configured bounds; recovery behavior is measured. Any JDA comparison must state workload differences and use comparable hardware and JVM settings.

## 1.0.0 — Stable core

- [ ] Finalize supported public interfaces and compatibility policy.
- [ ] Complete public API documentation and migration examples.
- [ ] Define the supported Micup protocol/deployment compatibility matrix.
- [ ] Establish package publishing, artifact signing, and release verification.
- [ ] Resolve release-blocking defects identified during beta use.
- [ ] Verify a sustained real-bot deployment under representative load.

**Completion criteria:** documented core features pass integration and recovery tests; known limitations are explicit; published artifacts can be reproduced and consumed using the documented installation paths.

## Exploratory work

Voice and music support, optional Gateway compression, coroutine adapters, and distributed coordination may become separate modules. Their scope depends on Micup's supported bot protocols and demand. They are not prerequisites for the initial stable text/interaction API.
