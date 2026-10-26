# Compatibility policy and protocol matrix

This document defines what Micup4J 1.x promises and which Micup protocol features it supports. JDA source compatibility is covered separately in [JDA-COMPATIBILITY.md](JDA-COMPATIBILITY.md).

## Versioning

Micup4J follows [Semantic Versioning](https://semver.org) from 1.0.0:

- **Patch** releases (1.0.x) fix defects without API changes.
- **Minor** releases (1.x.0) add features in a source- and binary-compatible way. They may deprecate API, but never remove it.
- **Major** releases (2.0.0) may remove deprecated API and make breaking changes, documented in the upgrade guide.
- Pre-releases (`-rc.N`, `-beta`) may still change before their final release.

The Java baseline is Java 21 for all of 1.x. Raising it requires a major release.

## Supported API

The supported API is every public and protected type and member outside `com.swadedev.micup4j.internal`. It is recorded in [`api/Micup4J.api`](../api/Micup4J.api), and `ApiSurfaceTest` fails the build when it changes unintentionally. An intended addition is accepted with `./gradlew test --tests '*ApiSurfaceTest*' -PupdateApi` and reviewed in the diff.

Within 1.x the following are **not** covered:

- Types in `com.swadedev.micup4j.internal`, except the public methods of `RestClient` that applications reach through `Micup.getRestClient()`, `RestClient.shared(...)` and the shard manager (`request`, `requestMultipart`, `shared`, `getMetrics`, `shutdown`, `isShutdown`, `close`).
- Constructors of events, actions, managers and entity mappings that are documented as "called by the library". Obtain these objects from the client.
- Methods documented as "not part of the supported public API", such as `FileUpload.rawData()`.
- Exact exception messages, log text, thread names, and the iteration order of collections documented as unordered.
- Defaults that tune behavior, such as timeouts, cache sizes and pacing, which may change in minor releases when Micup's limits change. Set them explicitly to pin them.
- Micup wire behavior. When the server changes, Micup4J follows it, as described below.

## Deprecated in 1.0

These remain functional for all of 1.x and may be removed in 2.0:

| Deprecated | Replacement |
| --- | --- |
| `events.ReadyEvent`, `ListenerAdapter.onReady(events.ReadyEvent)` | `events.session.ReadyEvent` and its `onReady` overload |
| `events.MessageReceivedEvent`, `ListenerAdapter.onMessageReceived(events.MessageReceivedEvent)` | `events.message.MessageReceivedEvent` and its overload |
| `entities.ChannelType` constants | `entities.channel.ChannelType` |
| `interactions.SlashCommand`, `interactions.CommandOption`, `interactions.OptionType` | `Commands.slash(...)`, `OptionData`, `interactions.commands.OptionType` |
| `Micup.updateCommands(SlashCommand...)`, `updateGuildCommands(String, SlashCommand...)` | `updateCommands()`, `updateGuildCommands(String)` |
| `SlashCommandData.addOption(interactions.OptionType, ...)`, `OptionType.toLegacy()` | The `interactions.commands.OptionType` overloads |

## Protocol and deployment matrix

Checked against the Micup documentation at [micup.gg/docs](https://micup.gg/docs/) on October 27, 2026.

| Area | Micup4J 1.0 | Notes |
| --- | --- | --- |
| Discovery | `/.well-known/fluxer`; REST from `endpoints.api_public`, Media Proxy from `endpoints.media` | HTTPS and WSS required except on loopback |
| REST | API v1, JSON and multipart bodies | `X-Audit-Log-Reason`, `X-Fluxer-Features` |
| Gateway | `v=1`, JSON encoding, no compression | Heartbeat, Identify, Resume, Invalid Session, opcode 7, documented close codes |
| Sharding | `[shard_id, shard_count]`, 1-16,384 shards, `(guild_id >> 22) % count` | 2,500 guilds per bot shard (4011); shard 0 receives account-level events |
| Session start limits | Sequential, spaced starts; `SessionStartGate` for multiple processes | 300 Identify attempts per address per minute; no concurrency buckets |
| Presence | Opcode 3 and Identify presence; custom status text | No presence cache of other users |
| Typed events | Ready, message create/update/delete, interactions, guild lifecycle, member add/remove, status/reconnect/shutdown | Other dispatches arrive as `GenericEvent` |
| Entity cache | Guild, role, channel and observed member state from guild snapshots, syncs and updates | Bounded by `CachePolicy`; no complete roster |
| Members | Bounded HTTP pages, indexed search, opcode 8 requests | Search can be stale; `loadMembers` once per 30 s per guild |
| Moderation | Kick, ban (with Micup ban duration), unban, timeout, nicknames, role assignment | Server enforces hierarchy, MFA and permissions |
| Roles | Create, modify, delete; feature-declared permission writes | Position and hoist-position routes not implemented |
| Permissions | Micup v1 registry, unsigned 64-bit masks, guild and channel resolution | Checked against documentation-derived fixtures, not a live server |
| Messages | Send, reply, edit, delete, retrieve; embeds; up to 10 files by multipart | Edits cannot change files; no reactions, pins or bulk delete helpers |
| Commands | Global and guild slash commands, options, subcommands and groups, autocomplete | Context menu commands are not offered by Micup |
| Interactions | Reply, defer, component edits (6/7), modals (9), autocomplete (8), hooks and followups | Files in responses are not offered by Micup |
| Components | Buttons, string and entity selects, action rows, text-input modals | Depend on the deployment's `feature_bot_components` and `feature_bot_select_menus` keys |
| Components v2 | Not supported | Refused with `FEATURE_TEMPORARILY_DISABLED` until `feature_bot_components_v2` is enabled |
| Voice, Lazy Request (op 14), counts (ops 15-16), webhooks outside interactions, emojis, stickers, invites, audit log reads | Not implemented | Raw REST through `getRestClient().request(...)` remains available |

### Deployment-dependent behavior

Some behavior depends on how an instance is configured, not on Micup4J:

- **Bot keys:** component and select menu writes require the instance's bot keys to be open; otherwise Micup drops the `components` field.
- **Search backend:** Elasticsearch reports exact totals and supports deep offsets; Meilisearch reports estimates capped at 10,000 and returns empty pages past that.
- **Gateway admission:** operators can cap connections and sessions per node; a refused session closes with 4008 and Micup4J resumes after backoff.
- **Limits:** message length, attachment count and file size come from the instance. Micup4J checks only the documented defaults it can know locally.

When Micup changes a documented limit or behavior, a patch or minor release follows it. Removing a feature from Micup does not remove Micup4J API in 1.x. The affected methods then fail with the server's error instead.
