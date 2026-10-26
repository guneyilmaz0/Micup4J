# Micup4J 0.2 Alpha

**A reliability and usability update for Java Micup bots.**

Micup4J `0.2.0-alpha` improves request scheduling, interaction deadlines, cancellation, and connection diagnostics. It also introduces typed slash options and rich embed builders, while retaining the existing builder/listener/REST action workflow.

**Tag:** `v0.2.0-alpha`  
**Java:** 21+  
**Release date:** October 26, 2026  
**Status:** Pre-release

## Smarter REST scheduling

- Normalized routes and learned server bucket identifiers share cooldowns across related requests while keeping major resources separate.
- Initial interaction callbacks have separate bounded admission, deadline priority, and a reserved transport slot when concurrency allows.
- Configurable attempt/total timeouts include queue time, rate limit waits, and retries.
- Cancellation propagates through request futures, `map()`, and `flatMap()`.
- Queue and cache capacities remain bounded. Only HTTP 429 responses are retried; uncertain writes are not replayed automatically.

## Connection diagnostics

New status and reconnect events distinguish transport failures, missed heartbeat acknowledgements, handshake timeouts, server reconnect requests, and close codes. Configurable reconnect timing and credential-free queue/retry/latency metrics make failures easier to inspect.

## More expressive bot APIs

```java
var command = new SlashCommand("avatar", "Show a member's avatar")
    .addOption(OptionType.USER, "user", "Choose a member", false);

var embed = new EmbedBuilder()
    .setTitle("Avatar")
    .setColor(0x3498DB)
    .setImage("https://example.com/avatar.webp")
    .build();

event.replyEmbeds(true, embed).queue();
```

The release adds basic string/integer/boolean/user/channel/role/number options, immutable embed snapshots, channel and reply embed helpers, option value access, and original interaction response read/delete operations.

## Upgrade notes

Use `com.swadedev.micup4j:Micup4J:0.2.0-alpha` with a sibling source checkout or Maven local. This version is not published to Maven Central.

Default total REST duration is now two minutes. Initial callbacks have a local 2.5-second budget from event construction, including listener queue time. Defer slow interactions immediately. Cancellation cannot undo a write already accepted by the server.

The existing two-argument `SlashCommand` constructor and `queue()`, `submit()`, and `complete()` remain available. Recompile applications when upgrading this alpha.

## Validation

The local HTTP/WebSocket suite covers shared buckets, bounded caches, concurrent pacing, cancellation, timeouts, priority callbacks, missed ACKs, repeated reconnects, delayed handshakes, close-code recovery, event ordering, and builder payloads. BMO's integration suite verifies its existing avatar and command flows against the new library.

## Build

```bash
./gradlew clean build
```

Outputs:

- `Micup4J-0.2.0-alpha.jar`
- `Micup4J-0.2.0-alpha-sources.jar`
- `Micup4J-0.2.0-alpha-javadoc.jar`

## Still planned

Guild/member caches, full permission resolution, automatic shard orchestration, advanced command options, typed component builders, attachment uploads, and voice support remain future work. Cross-client rate limit coordination and production performance qualification are not included in this release.

Alpha APIs may change. Feedback and reproducible bug reports are welcome; remove credentials and private content before sharing diagnostics.
