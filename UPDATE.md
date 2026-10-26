# Release and upgrade guide

## Current release

| Field | Value |
| --- | --- |
| Version | `1.0.0-rc.1` |
| Display name | Micup4J 1.0 RC 1 |
| Suggested Git tag | `v1.0.0-rc.1` |
| Release date | October 27, 2026 |
| Java baseline | Java 21 |
| Dependency | `net.guneyilmaz0.micup4j:Micup4J:1.0.0-rc.1` |
| Status | Release candidate; not yet published to Maven Central |

## Upgrading from 0.5.0-beta

Change the dependency to `1.0.0-rc.1` and recompile. There are no removals.

- Deprecation warnings point at legacy aliases; the [compatibility policy](docs/COMPATIBILITY.md#deprecated-in-10) lists each replacement. They keep working through 1.x.
- From 1.0, minor releases are source- and binary-compatible. The policy describes what is excluded, such as the `internal` package.

## Upgrading from 0.4.0-alpha

Change the dependency to `0.5.0-beta` and recompile. There are no breaking API changes.

- Bots that ran several `Micup` sessions by hand can move to `DefaultShardManagerBuilder`, or pass one `RestClient.shared(...)` to each `MicupBuilder.setRestClient(...)` so that their requests share rate limits.
- A shard manager starts shards about one second apart and waits for each Ready. Allow for that when sizing startup health checks.
- Shut a manager down with `shutdown()` and `awaitShutdown(...)`; it drains the shared REST client after every shard has stopped.

## Upgrading from 0.3.0-alpha

Change the dependency to `0.4.0-alpha` and recompile.

- Custom implementations of `entities.channel.middleman.MessageChannel` must add `sendFiles(FileUpload...)` and `sendFiles(Collection)`.
- Component and modal interactions now arrive as `ButtonInteractionEvent`, `StringSelectInteractionEvent`, `EntitySelectInteractionEvent` and `ModalInteractionEvent`. Existing `onInteractionCreate` handlers still receive them; move logic to the typed callbacks to use `deferEdit`, `editMessage`, `getValues` and `getValue`.
- Prefer the component builders over raw `respond(type, JsonNode)` payloads; the builders check Micup's limits before sending.
- Interaction replies, followups and hook edits cannot carry files on Micup. Send files with `channel.sendFiles(...)` instead.

## Upgrading from 0.2.0-alpha

Change the dependency to `0.3.0-alpha` and recompile. This release targets JDA-style source usage; review the [compatibility guide](docs/JDA-COMPATIBILITY.md) before updating an existing alpha consumer:

- Message send/reply/edit methods now return fluent action subtypes. Recompile consumers; existing `RestAction<Message>` source assignments remain valid.
- Advanced commands use `OptionData`, `SubcommandData` and `SubcommandGroupData`; see the [command toolkit guide](docs/COMMANDS.md) for autocomplete and message chains.
- Cache getters return objects or null; move Optional access to `find...ById`.
- Reply/defer actions complete with `InteractionHook`; update explicitly typed `RestAction<Void>` usages.
- Import canonical channel types from `entities.channel.ChannelType`.
- No-argument `awaitReady()` has no automatic timeout; use `awaitReady(Duration)` for a bound.
- Existing flat event handlers remain bridged. New code can use the JDA-style event subpackages and `onSlashCommandInteraction`.
- **Binary break:** `InteractionHook.sendMessage`, `editOriginal` and `editOriginalEmbeds` now return `WebhookMessageCreateAction`/`WebhookMessageEditAction`. Source assignments to `RestAction<Message>` still compile; recompile consumers.
- **Source break:** `Status` has a new `SHUTTING_DOWN` constant. Add a case to exhaustive `switch` expressions over `Status`.
- **Source break:** `RestConfig` has a seventh component, `shutdownTimeout` (default 30 seconds). Construct it with the existing six-argument constructor or `withShutdownTimeout`; update any six-part record patterns.
- Shutdown hooks should call `client.shutdown(); client.awaitShutdown(Duration.ofSeconds(10));`. `shutdown()` now drains queued REST work and rejects new work with `RejectedExecutionException`; use `shutdownNow()` or `close()` for an immediate stop.
- `complete()`, `completeAfter()` and `awaitShutdown` throw `IllegalStateException` on the internal `micup4j-gateway`, `micup4j-rest-scheduler` and `micup4j-delay` threads. The `micup4j-events` listener thread is not guarded, so blocking in listeners is allowed as in JDA, though `queue` or `submit` is still preferred.
- `awaitShutdown()` and `termination()` may return before the final `StatusChangeEvent(SHUTDOWN)` and `ShutdownEvent` reach listeners; a shutdown hook must not rely on `onShutdown` having run.
- `editMessage(null)` now sends an empty string rather than failing; `Message.reply` validates the message id.
- Malformed typed-event payloads now arrive as plain `GenericEvent`; override `onGenericEvent` if you need to observe them.
- Micup public deferred responses have no placeholder: create the original with `hook.editOriginal(...)` before retrieving it.
- **Binary break:** guild mutation methods return `AuditableRestAction`, and `createRole(guildId, name, color, permissions)` returns `RoleAction`. Source assignments to `RestAction` still compile; recompile consumers.
- `createRole` with an explicit mask now writes `VIEW_CHANNEL_MEMBERS` exactly as supplied; previously the server cleared it.
- Audit reasons sent as headers must be printable ASCII; use ban/timeout body reasons for other text.

## Upgrading from 0.1.0-alpha to 0.2.0-alpha

1. Change the dependency version to `0.2.0-alpha` and recompile the consuming application.
2. For a composite build, update the sibling Micup4J checkout. For Maven local, run `./gradlew publishToMavenLocal` in the library checkout.
3. Review the scheduling and timeout changes below.
4. Run the application's integration tests and rebuild its distribution.
5. Restart the bot when you want to deploy the new library. Rebuilding files alone does not update a running JVM.

The BMO source dependency has been updated and its integration suite is used for compatibility verification. The release preparation itself does not restart the live bot or alter its credentials/commands. No token rotation is required by this upgrade.

## 0.2.0-alpha behavior changes

### REST scheduling

Default HTTP attempt timeout remains 20 seconds. A new two-minute total timeout includes queue time, rate limit waits, and retries. Configure these limits with `RestConfig` before `build()`.

Normal request capacity includes running and waiting requests. Initial interaction callbacks have eight additional reserved admission slots by default. With concurrency greater than one, one transport slot is reserved for those callbacks. All starts still obey the client's global pacing and server cooldowns.

`submit().cancel(true)` now removes queued work and requests cancellation of active HTTP I/O. Cancellation propagates through `map()` and `flatMap()`. A server may already have applied an active write; cancellation does not undo it.

`complete(Duration)` cancels its operation on a caller-side timeout. Interrupted blocking callers cancel the operation and retain the interrupt flag.

See [REST.md](docs/REST.md) for exact defaults, retry limits, cache behavior, and cancellation semantics.

### Interaction deadlines

The first response has a local deadline 2.5 seconds after the interaction event object is created. Time waiting for a listener counts against it. A late submission fails locally instead of sending a request that has already exceeded this local budget.

Use `deferReply(...)` immediately for slow operations, then edit the original response when work completes. Local priority cannot guarantee arrival within the server's three-second window.

### New command and embed APIs

```java
var command = new SlashCommand("avatar", "Show an avatar")
    .addOption(OptionType.USER, "user", "Choose a member", false);

var embed = new EmbedBuilder().setTitle("Avatar")
    .setImage("https://example.com/avatar.webp").build();
event.replyEmbeds(true, embed).queue();
```

Imports: `net.guneyilmaz0.micup4j.interactions.SlashCommand`, `net.guneyilmaz0.micup4j.interactions.OptionType`, and `net.guneyilmaz0.micup4j.entities.EmbedBuilder`.

`SlashCommand` remains immutable. Its two-argument constructor and name/description accessors are retained; the definition now also has an option list. Retain the returned value when calling `addOption`.

### Lifecycle diagnostics

`ListenerAdapter` now offers `onStatusChange` and `onReconnect`. These local events use sequence `-1` and do not acknowledge Gateway data. Diagnostics are best effort; terminal state should be observed with `getStatus()` and `termination()`.

See [GATEWAY.md](docs/GATEWAY.md) for connection timing and all documented close-code actions.

## Preparing a release

See [RELEASING.md](docs/RELEASING.md). In short: update the version, changelog and release notes, run `./gradlew verifyRelease`, then push a `v<version>` tag. The release workflow checks reproducibility, builds the signed Central bundle and opens a draft GitHub release.

## Versioning and history

`gradle.properties` remains the artifact version source. Runtime metadata and the JAR manifest are generated from it.

- [0.5 Beta release notes](docs/releases/README-0.5.0-beta.md)
- [0.4 Alpha release notes](docs/releases/README-0.4.0-alpha.md)
- [0.3 Alpha release notes](docs/releases/README-0.3.0-alpha.md)
- [0.2 Alpha release notes](docs/releases/README-0.2.0-alpha.md)
- [0.1 Alpha release notes](docs/releases/README-0.1.0-alpha.md)
- [Compatibility policy](docs/COMPATIBILITY.md)
- [Full changelog](CHANGELOG.md)
- [Roadmap](ROADMAP.md)
