# Release and upgrade guide

## Unreleased source migration

The checkout now targets JDA-style source usage. This is not a new prepared release; the release version below remains unchanged. Review the [compatibility guide](docs/JDA-COMPATIBILITY.md) before updating an existing alpha consumer:

- Cache getters return objects or null; move Optional access to `find...ById`.
- Reply/defer actions complete with `InteractionHook`; update explicitly typed `RestAction<Void>` usages.
- Import canonical channel types from `entities.channel.ChannelType`.
- No-argument `awaitReady()` has no automatic timeout; use `awaitReady(Duration)` for a bound.
- Existing flat event handlers remain bridged. New code can use the JDA-style event subpackages and `onSlashCommandInteraction`.
- Micup public deferred responses have no placeholder: create the original with `hook.editOriginal(...)` before retrieving it.

## Current release

| Field | Value |
| --- | --- |
| Version | `0.2.0-alpha` |
| Display name | Micup4J 0.2 Alpha |
| Suggested Git tag | `v0.2.0-alpha` |
| Release date | October 26, 2026 |
| Java baseline | Java 21 |
| Dependency | `net.guneyilmaz0.micup4j:Micup4J:0.2.0-alpha` |
| Status | Early alpha; not published to Maven Central |

## Upgrading from 0.1.0-alpha

1. Change the dependency version to `0.2.0-alpha` and recompile the consuming application.
2. For a composite build, update the sibling Micup4J checkout. For Maven local, run `./gradlew publishToMavenLocal` in the library checkout.
3. Review the scheduling and timeout changes below.
4. Run the application's integration tests and rebuild its distribution.
5. Restart the bot when you want to deploy the new library. Rebuilding files alone does not update a running JVM.

The BMO source dependency has been updated and its integration suite is used for compatibility verification. The release preparation itself does not restart the live bot or alter its credentials/commands. No token rotation is required by this upgrade.

## Behavior changes

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

## Preparing the GitHub release

```bash
./gradlew clean build
```

Upload the source repository with the wrapper, tests, example, and documentation. Local credentials, IDE state, logs, and build output are excluded by `.gitignore`.

Create a **pre-release** titled **Micup4J 0.2 Alpha**, tagged `v0.2.0-alpha`. Paste the contents of [the release README](docs/releases/README-0.2.0-alpha.md) into the release description. Attach:

```text
build/libs/Micup4J-0.2.0-alpha.jar
build/libs/Micup4J-0.2.0-alpha-sources.jar
build/libs/Micup4J-0.2.0-alpha-javadoc.jar
```

The CI workflow builds and tests; it does not automatically publish packages or create a release. A remote CI run can only be verified after upload. No Maven Central publication or license choice is implied by preparing these files.

## Versioning and history

`gradle.properties` remains the artifact version source. Runtime metadata and the JAR manifest are generated from it.

- [0.1 Alpha release notes](docs/releases/README-0.1.0-alpha.md)
- [Full changelog](CHANGELOG.md)
- [Roadmap](ROADMAP.md)
