# Micup4J 1.0 Release Candidate 1

**The 1.0 API, frozen and documented.**

Micup4J `1.0.0-rc.1` is the first 1.0 release candidate. It adds no new protocol features. It defines what 1.x promises, documents every supported type and member, and makes releases reproducible and verifiable.

**Tag:** `v1.0.0-rc.1`  
**Java:** 21+  
**Release date:** October 27, 2026  
**Status:** Pre-release (release candidate)

## Compatibility policy

From 1.0, Micup4J follows semantic versioning: minor releases stay source- and binary-compatible, and deprecated API is removed only in a major release. The [compatibility policy](../COMPATIBILITY.md) lists what is covered, what is excluded (such as the `internal` package), and a protocol matrix of the Micup features and deployment settings each area depends on.

The public API is recorded in `api/Micup4J.api`, and the build fails when it changes unintentionally.

## Documentation

Every supported type and member now has Javadoc, and the build enforces it with doclint errors. Guides cover [entities](../ENTITIES.md), [commands](../COMMANDS.md), [components](../COMPONENTS.md), [sharding](../SHARDING.md), [performance](../PERFORMANCE.md) and [JDA migration](../JDA-COMPATIBILITY.md).

## Deprecations

Legacy aliases from the early alphas are deprecated and keep working through 1.x. They include the flat-package `ReadyEvent` and `MessageReceivedEvent`, `entities.ChannelType`, and the `SlashCommand`/`CommandOption` definitions. Each has a JDA-style replacement listed in the policy.

## License

Micup4J is licensed under the [Apache License 2.0](../../LICENSE).

## Release tooling

- POM metadata, optional artifact signing, and a Maven Central Portal bundle (`./gradlew centralBundle`).
- `./gradlew verifyRelease` and SHA-256 checksums; builds are reproducible.
- A tag-triggered workflow that verifies the release, compares two clean builds and opens a draft GitHub release.

See [RELEASING.md](../RELEASING.md).

## Before 1.0.0

The final release waits for:

- feedback from this candidate and any release-blocking fixes;
- a sustained real-bot deployment under representative load.

## Upgrade notes

Use `com.swadedev.micup4j:Micup4J:1.0.0-rc.1`. There are no removals; recompile and follow the deprecation warnings. See the [upgrade guide](../../UPDATE.md).

## Build

```bash
./gradlew clean build
```

Outputs:

- `Micup4J-1.0.0-rc.1.jar`
- `Micup4J-1.0.0-rc.1-sources.jar`
- `Micup4J-1.0.0-rc.1-javadoc.jar`
