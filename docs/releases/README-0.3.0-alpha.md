# Micup4J 0.3 Alpha

**JDA-style guild, member and moderation APIs for Java Micup bots.**

Micup4J `0.3.0-alpha` adds typed entities, bounded caches and permission resolution, then builds the moderation toolkit on top: role and member actions with audit log reasons, indexed member search, and Gateway member requests. Common JDA handler bodies keep their shape after the import-prefix and `JDA` → `Micup` rename.

**Tag:** `v0.3.0-alpha`  
**Java:** 21+  
**Release date:** October 27, 2026  
**Status:** Pre-release

## Entities, caches and permissions

- Immutable guild, member, role and typed guild-channel snapshots with JDA-style getters and object-or-null cache lookups.
- Configurable `CachePolicy` bounds; Gateway snapshots, syncs and incremental updates are applied before listeners, and unavailable guilds drop stale permission state.
- Unsigned 64-bit `PermissionSet`, Micup's permission registry and guild/channel resolution that fails explicitly on incomplete state.
- Bounded, validated HTTP member pages.

## Moderation with audit reasons

```java
guild.ban(member, 1, TimeUnit.DAYS).reason("Spam").queue();
member.timeoutFor(Duration.ofMinutes(10)).reason("Cool down").queue();
guild.createRole().setName("Helper").setColor(0x3498DB)
     .setPermissions(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND).reason("Staff").queue();
```

- `kick`, `ban` (message deletion window and Micup's optional ban duration), `unban`, `timeoutFor/Until`, `removeTimeout`, role add/remove, nicknames, and fluent role creation, modification and deletion.
- Every mutation is an `AuditableRestAction`. Header reasons must be printable ASCII because Micup stores them verbatim; ban and timeout reasons go in the body and accept any Unicode text.
- Role permission writes declare Micup's feature header, so gated bits such as `VIEW_CHANNEL_MEMBERS` are stored exactly as sent.

## Finding members

```java
var page = guild.searchMembers().setQuery("ada").setLimit(25).complete();
guild.retrieveMembersByPrefix("ad", 10).onSuccess(members -> ...);
```

- `searchMembers()` uses the indexed search route with role, join-time, account-age, bot, join-source and invite filters. Results are partial and can be stale.
- `retrieveMembersByPrefix`, `retrieveMembersByIds` and `loadMembers` use Gateway opcode 8 and return a `Task`. Requests are sent one at a time per connection, matched by nonce, rate-limit and timeout aware, and failed explicitly when the connection drops.

## Also included

JDA-style listener packages, mutable command definitions with options, subcommands and typed autocomplete, fluent message and interaction-hook actions, active mentions, guild lifecycle callbacks, coalesced presence, `RestAction` combinators and delays, and graceful shutdown. See the [changelog](../../CHANGELOG.md) for the full list.

## Upgrade notes

Use `net.guneyilmaz0.micup4j:Micup4J:0.3.0-alpha` with a sibling source checkout or Maven local. This version is not published to Maven Central. Several methods now return specialized action subtypes; source assignments to `RestAction` still compile, but consumers must be recompiled. `Status` gained `SHUTTING_DOWN` and `RestConfig` a `shutdownTimeout` component. See the [upgrade guide](../../UPDATE.md) for every change.

## Validation

The local HTTP/WebSocket suite covers cache equivalence and invalidation, permission ordering, mutation payloads and headers, reason validation, search serialization and parsing, and member-request sequencing, chunk assembly, rate limits, cancellation and reconnects. A representative JDA-style bot and Kotlin fixtures are compiled in `check`.

Permission results are checked against documentation-derived fixtures only. Comparison with live-server fixtures and verification of real guild/subscription recovery remain open, as does a remote CI run after upload.

## Build

```bash
./gradlew clean build
```

Outputs:

- `Micup4J-0.3.0-alpha.jar`
- `Micup4J-0.3.0-alpha-sources.jar`
- `Micup4J-0.3.0-alpha-javadoc.jar`

## Still planned

Component, button, select menu and modal builders, attachment uploads, role positions and channel editing, shard management, cross-session rate limit coordination, measured performance and voice support remain future work.

Alpha APIs may change. Feedback and reproducible bug reports are welcome; remove credentials and private content before sharing diagnostics.
