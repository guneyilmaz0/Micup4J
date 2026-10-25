# Contributing

Micup4J is an early alpha Java 21 library. Changes should keep the event loop responsive and make protocol behavior easy to verify.

## Local setup

Use JDK 21 and the included wrapper:

```bash
./gradlew clean build
```

Tests use local HTTP/WebSocket fixtures. Do not include real tokens or require a live Micup account for the default test suite.

## Changes and pull requests

- Describe the user-visible problem and resulting behavior.
- Keep protocol logic in the library and application command logic in examples or consuming bots.
- Add a regression test for a behavioral fix, especially lifecycle, sequencing, and rate limit changes.
- Document public API changes and record user-visible changes in the Unreleased changelog section.
- Preserve bounded work queues and avoid blocking the Gateway scheduler.
- Never include credentials, raw interaction tokens, or private message content in issues, fixtures, or logs.

When adding an endpoint or payload field, reference the official Micup documentation and distinguish bot-supported operations from client-only operations. Do not promise behavior the server or tests do not establish.

## Versioning

Change the artifact version in `gradle.properties`. Follow [UPDATE.md](UPDATE.md) for release preparation. Alpha APIs may evolve; breaking changes still need migration notes.
