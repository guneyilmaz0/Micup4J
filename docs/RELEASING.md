# Releasing Micup4J

Releases are built and verified by Gradle and the tag-triggered [release workflow](../.github/workflows/release.yml). Publishing to Maven Central is a deliberate manual step.

## One-time setup

1. **License.** Micup4J is licensed under the Apache License 2.0. The `LICENSE` file is shipped in every JAR's `META-INF`, and the POM declares it.
2. **Create a signing key** for the release identity, for example with `gpg --full-generate-key`, and publish the public key to a keyserver such as `keys.openpgp.org`.
3. **Add the key to GitHub** as repository secrets: `SIGNING_KEY` (the ASCII-armored private key from `gpg --armor --export-secret-keys <id>`) and `SIGNING_PASSWORD`. Never commit them.
4. **Register the namespace** `com.swadedev` on the [Maven Central Portal](https://central.sonatype.com). Central verifies it through a DNS TXT record on `swadedev.com` holding the verification key it shows; the record can be removed once the namespace is verified.

## Releasing a version

1. Set `version` in `gradle.properties`, move the changelog's Unreleased entries under the new version, and add `docs/releases/README-<version>.md`.
2. If the public API changed intentionally, run `./gradlew test --tests '*ApiSurfaceTest*' -PupdateApi` and review the diff of `api/Micup4J.api` against the [compatibility policy](COMPATIBILITY.md). A minor or patch release must not remove or change existing lines.
3. Verify locally:

   ```bash
   ./gradlew verifyRelease
   ```

   This runs the tests, the API snapshot check, Javadoc with doclint errors, the release metadata and `LICENSE` checks, then writes SHA-256 files next to the JARs.
4. Commit, then tag and push:

   ```bash
   git tag v<version> && git push origin v<version>
   ```

5. The workflow checks that the tag matches the version, runs `verifyRelease`, rebuilds from clean and compares checksums, builds `central-bundle-<version>.zip` (signed when the secrets exist), and opens a **draft** GitHub release with the JARs, checksums and bundle. Pre-release versions are marked as pre-releases.
6. Review the draft, then publish it.
7. Upload `central-bundle-<version>.zip` on the Central Portal (Publish → Deploy), check the validation, and publish. Central releases cannot be deleted.

## Verifying a release

Every release JAR has a `.sha256` file. Builds are reproducible: archive timestamps and file order are fixed, so rebuilding a tag with the same JDK yields identical checksums.

```bash
git checkout v<version>
./gradlew clean releaseChecksums
cat build/libs/*.sha256
```

Compare the output with the checksums attached to the GitHub release. A signed artifact can also be checked with `gpg --verify <file>.asc <file>` against the published key.
