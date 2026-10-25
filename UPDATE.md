# Release and upgrade guide

## Current release

| Field | Value |
| --- | --- |
| Version | `0.1.0-alpha` |
| Display name | Micup4J 0.1 Alpha |
| Suggested Git tag | `v0.1.0-alpha` |
| Release date | October 25, 2026 |
| Java baseline | Java 21 |
| Dependency | `net.guneyilmaz0.micup4j:Micup4J:0.1.0-alpha` |
| Status | Early alpha; not published to Maven Central |

## Upgrading from the development snapshot

1. Change the dependency version from `0.1.0-SNAPSHOT` to `0.1.0-alpha`.
2. If you use `includeBuild("../Micup4J")`, update the sibling checkout and rebuild the consuming bot.
3. If you use Maven local, run `./gradlew publishToMavenLocal` before rebuilding the bot.
4. Stop the running bot, replace its distribution, and start it again. Rebuilding files does not update a running JVM.
5. Confirm that the bot reaches Ready and responds to a test command in your own server.

No token rotation, application recreation, or command re-registration is required by the version change itself. The BMO dependency has been updated to the alpha coordinate. An already-running BMO process continues using the JARs loaded when it started.

### Lifecycle improvement

Use `client.termination()` when an application needs to remain running until shutdown or failure:

```java
try (client) {
    client.awaitReady();
    client.termination().join();
}
```

Normal shutdown completes the future normally. A permanent failure completes it exceptionally. `ready()` and `awaitReady()` describe initial startup, not later connection health.

## Preparing the GitHub release

From the repository root:

```bash
./gradlew clean build
```

Upload the source repository with its Gradle wrapper, tests, example, and Markdown documentation. The `.gitignore` excludes local credentials, IDE state, logs, and build output. Keep the source repository independent of the sibling BMO project.

When creating the release, use **Micup4J 0.1 Alpha** as the title, `v0.1.0-alpha` as the tag, and mark it as a **pre-release**. Use the `0.1.0-alpha` section in [CHANGELOG.md](CHANGELOG.md) as the release notes. Attach these build outputs:

```text
build/libs/Micup4J-0.1.0-alpha.jar
build/libs/Micup4J-0.1.0-alpha-sources.jar
build/libs/Micup4J-0.1.0-alpha-javadoc.jar
```

Choose and add a project license before a public release. No license choice or remote repository URL is assumed by this preparation.

The CI workflow builds and tests the project; it does not publish packages or create a GitHub release automatically. Its remote run can only be verified after the repository is uploaded.

## Future releases

`gradle.properties` is the source of the artifact version. Update it, add a dated changelog entry, document breaking changes and migration steps here, then run the build and consuming-bot integration tests. Runtime metadata and the JAR manifest are generated from the Gradle version.

The roadmap records proposed milestones rather than promised dates. Publish a stable `1.0.0` only after the documented compatibility and reliability criteria have been met.
