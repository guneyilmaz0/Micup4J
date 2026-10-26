# Entities, caches, and permissions

This document describes the entity, cache, permission and moderation APIs released in `0.3.0-alpha`.

## Typed HTTP operations

All operations return lazy `RestAction` values, use the discovered public API, and follow [REST scheduling](REST.md). Reads and mutation responses return immutable, detached snapshots. They never insert into or overwrite Gateway state: HTTP and Gateway events do not share an ordering sequence.

| Operation | Client method |
| --- | --- |
| Guild configuration, roles and visible channels | `retrieveGuildById(guildId)` |
| All roles | `retrieveRoles(guildId)` |
| Visible guild channels | `retrieveGuildChannels(guildId)` |
| One guild channel, including its raw type and overwrites | `retrieveGuildChannelById(channelId)` |
| One membership / own membership | `retrieveMemberById(guildId, userId)` / `retrieveSelfMember(guildId)` |
| Bounded ascending member page | `retrieveMembers(guildId, limit, after)` |
| Guild name | `modifyGuildName(guildId, name)` |
| Member nickname; null clears | `modifyMemberNickname(guildId, userId, nickname)` |
| Assign / remove one role | `addMemberRole(guildId, userId, roleId)` / `removeMemberRole(...)` |
| Create role / rename role | `createRole(guildId)` (fluent) or `createRole(guildId, name, color, permissions)` / `modifyRoleName(...)` |
| Modify / delete role | `modifyRole(guildId, roleId)` (fluent `RoleManager`) / `deleteRole(guildId, roleId)` |
| Kick / ban / unban | `kick(guildId, userId)` / `ban(guildId, userId, deletionTimeframe, unit[, duration])` / `unban(...)` |
| Timeout | `timeoutFor(guildId, userId, duration)` / `timeoutUntil(...)` / `removeTimeout(...)` |
| Indexed member search | `searchMembers(guildId)` |

The server enforces access, hierarchy, MFA, content policy, and deployment limits. No mutation runs merely by constructing an action. Role writes send permission masks as decimal strings; an omitted mask on creation copies the everyone role. Whenever a mask is set, the request declares every gated feature in `X-Fluxer-Features` (today `view_channel_members_permission`), so the mask is stored exactly as sent, including `VIEW_CHANNEL_MEMBERS`. A `RoleManager` sends only the fields that were set, and an empty manager completes without a request. Role position and hoist-position changes are not implemented.

### Audit log reasons

```java
guild.ban(member, 1, TimeUnit.DAYS).reason("Spam").queue();
client.modifyRole(guildId, roleId).setName("Moderator").reason("Promotion").queue();
```

Every mutation above returns an `AuditableRestAction`. Micup stores `X-Audit-Log-Reason` verbatim without percent-decoding, so header reasons must be printable ASCII; other characters fail locally with `IllegalArgumentException`. Ban and timeout reasons travel in the JSON body (`reason`, `timeout_reason`) and accept any Unicode text. Reasons are trimmed; blank values send none, and values over 512 characters fail locally rather than being silently discarded by the server.

Timeouts accept at most 365 days and are computed when the action executes. Bans delete up to 7 days of messages and may last 60 seconds to 730 days; omitting the duration bans permanently.

A `Guild` stores selected identity and relationship fields, not every configuration field in the HTTP resource. `Member` stores the account, nickname, assigned role IDs, join time, timeout and voice moderation flags. JDA-style getters and client-bound helpers are described in the [compatibility guide](JDA-COMPATIBILITY.md). `User` additionally retains its avatar hash and global display name; `MessageChannel` retains its REST operations. Guild channel snapshots expose `ChannelType`, `rawType`, and `canHaveMessages()`; voice channels have messages, categories and link channels do not. The channel parser rejects private channels in the guild-only retrieval helper.

### Member pagination

```java
var page = client.retrieveMembers("123", 100, null).complete();
if (!page.isEmpty()) {
    String after = page.getLast().getId();
    var next = client.retrieveMembers("123", 100, after).complete();
}
```

A page limit is 1–1000. `after` is exclusive, and pages are ascending by unsigned user snowflake. Oversized, duplicate, out-of-order or nonadvancing responses fail. There is no automatic background roster download or unbounded `retrieveAll` operation. Stop when a page is empty; concurrent membership changes can affect pages. Applications control how many pages and members they retain. Indexed search and Gateway member requests are separate protocols, described below.

### Indexed member search

```java
var page = guild.searchMembers().setQuery("ada").setRoles(role).setLimit(50).complete();
if (page.indexing()) { /* the index is being built; retry later */ }
```

Search needs one of `MANAGE_GUILD`, `MANAGE_ROLES`, `MANAGE_NICKNAMES`, `BAN_MEMBERS`, `MODERATE_MEMBERS` or `KICK_MEMBERS`. Filters cover text (100 characters), up to 10 required roles, inclusive join and account-creation ranges in whole seconds, bot accounts, join source types and invite codes (applied only with `MANAGE_GUILD`), offset and sort order. Results are partial `MemberSearchResult.Entry` values without avatars, timeouts or voice state; they can be stale and may include members who left. An empty page with `indexing()` false can also mean search is unavailable, and `totalResultCount` may be a capped estimate. Retrieve the member before acting on current state.

Members can also be requested over the Gateway with `retrieveMembersByPrefix`, `retrieveMembersByIds` and `loadMembers`; see [member requests](GATEWAY.md#member-requests-opcode-8).

## Cache policies

```java
var client = MicupBuilder.createDefault(token)
    .setCachePolicy(new CachePolicy(64, 512, 512, 128))
    .build();
```

Import `net.guneyilmaz0.micup4j.config.CachePolicy`.

| Preset | Guilds per client | Roles per guild | Channels per guild | Members per guild |
| --- | ---: | ---: | ---: | ---: |
| `createDefault` | 128 | 512 | 512 | 256 |
| `createLight` | 0 | 0 | 0 | 0 |

Zero disables the named collection; zero guild capacity disables all entity storage. Bounds count entities, not bytes or application-held snapshots. Policy upper bounds are 10,000 guilds, 10,000 roles/channels per guild and 100,000 members per guild. Customize limits for the deployment and workload.

`getGuildById` returns a `Guild` or null, and `findGuildById` returns `Optional<Guild>` and `getGuilds` returns an immutable list. A miss means uncached; it is not proof that a guild or member does not exist. Cache reads do not issue HTTP requests. User-held snapshots do not update when the cache changes.

Guild eviction removes the least recently updated guild and all its children. Entity collection limits retain the last entries of an incoming snapshot; incremental upserts move changed entities to the end and evict the oldest entries. Reads do not change this order. Record component collections do not promise display or hierarchy order; `Guild.getRoles()` returns hierarchy order and `Role.HIERARCHY` provides descending position with ascending unsigned snowflake for ties.

### Gateway consistency and invalidation

Cache changes run on the ordered event executor before that event's user listeners. `awaitReady()` still means Gateway authentication completed: the guild burst and listener processing may follow later. Inspect state from the relevant event or retrieve it over HTTP when needed.

- `READY` clears previous session state and seeds its guild identities.
- `GUILD_CREATE` and `GUILD_SYNC` understand nested `properties`, replace full roles and visible-channel collections, and merge the partial member list.
- Guild updates replace metadata and retain collections. Role/channel create, update and bulk-update events upsert complete records.
- Role deletion also removes that ID from cached member assignments. Channel/member deletion removes the named record.
- Member add/update events and member chunks merge bounded observed memberships. This does not imply a complete roster or current subscription to every member.
- A temporarily unavailable guild keeps its identity but discards children and completeness flags, so stale permission state is not used. A later guild snapshot restores it. Permanent removal deletes the entire entry.
- Close and permanent failure clear the cache. Malformed permission-bearing entity data fails event processing rather than treating absent role assignments or channel overwrites as empty.

Enabled caches require their state events. Building a cached client that ignores a required guild, role, channel, member or chunk event fails before connecting. Use `createLight` or disable the cache to intentionally ignore those events. Raw generic event delivery remains available in light mode.

`rolesComplete` and `channelsComplete` describe collection coverage of the last observed snapshot, and become false on truncation or disabled storage. Channels are only the channels visible to this bot. Members are always a bounded observed subset. These flags do not guarantee freshness: event filters, subscriptions, connection loss and nonretained Gateway payloads can leave observed memberships stale. Member-list subscriptions and caches of other users' presences are not implemented. The session's own [presence manager](PRESENCE.md) does not create a presence cache. Retrieve current membership before sensitive decisions.

## Events for uncached guilds

Role, channel and member events for a guild that is not cached do not insert placeholder guilds and never evict a real cached guild. A `GUILD_UPDATE` for an uncached guild delivers a guild built from the event payload without caching it. Cached lookups return bound snapshots without per-call member copies, and `getTextChannelById` is a constant-time lookup. Cache updates do not hold the lock used by the Gateway thread.

## Permission masks

```java
import net.guneyilmaz0.micup4j.permissions.*;

var guild = client.retrieveGuildById("123").complete();
var member = client.retrieveMemberById("123", "456").complete();
var channel = guild.findChannelById("789").orElseThrow();
var permissions = PermissionResolver.channelPermissions(guild, member, channel);
boolean maySend = permissions.contains(Permission.SEND_MESSAGES)
    && !member.isTimedOutAt(java.time.Instant.now());
```

`PermissionSet` preserves unsigned 64-bit masks with `BigInteger`; decimal strings are compared numerically. Request serialization rejects values above signed `INT64_MAX` as Micup requires, while owner/admin results use all 64 bits. Unknown read bits are retained.

The resolver uses the documented order: owner gets all bits; null membership gets none; everyone permissions union assigned roles; an administrator bypasses overwrites; then everyone overwrite, combined role denies/allows, and finally the member overwrite. Allows take precedence within each step. A null channel requests the guild mask. The helper never adds Discord-style implicit permission stripping, parent overwrite inheritance or timeout mask changes.

Unavailable guilds, an unknown owner, incomplete role state, a missing everyone/assigned role, and entities from another guild fail explicitly. Null membership must mean an actual nonmember, not simply a cache miss. Passing a channel snapshot assumes its overwrites are complete and current. Category visibility through children and temporary voice access are separate from the computed mask.

A mask is not a promise an operation will succeed. Communication timeout, MFA enrollment, role hierarchy, visibility, age restrictions and server-side access checks remain independent. Permission calculations are checked against local fixtures derived from the documentation; they have not been compared with a live server fixture suite.

## References

- [Guilds](https://micup.gg/docs/http-api/guilds/)
- [Guild members](https://micup.gg/docs/http-api/guild-members/)
- [Guild channels](https://micup.gg/docs/http-api/guild-channels/)
- [Channels and overwrites](https://micup.gg/docs/http-api/channels/)
- [Roles and permission computation](https://micup.gg/docs/http-api/permissions/)
- [Gateway entity events](https://micup.gg/docs/gateway/events/)
- [Indexed member search](https://micup.gg/docs/http-api/guild-member-search/)
