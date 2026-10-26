# Gateway lifecycle in 0.2.0-alpha

Micup4J uses Gateway v1 with JSON text frames and no compression. Heartbeat timing is taken from Hello. A missed acknowledgement reconnects the socket; a new socket attempts Resume when a session is retained.

## Configuration

```java
import java.time.Duration;
import net.guneyilmaz0.micup4j.config.GatewayConfig;

var timing = new GatewayConfig(
    Duration.ofSeconds(15), // WebSocket connection timeout.
    Duration.ofSeconds(60), // Startup: connection through Ready/Resumed.
    Duration.ofSeconds(1),  // Initial reconnect delay.
    Duration.ofSeconds(30)  // Maximum reconnect delay, including jitter.
);
builder.setGatewayConfig(timing);
```

All durations must be positive and no longer than one day. Reconnect uses exponential backoff with jitter; its base delay must not exceed its maximum. A successful Ready or Resumed resets the backoff attempt count.

The handshake timer bounds each connection attempt, not the client's total lifetime. `awaitReady(Duration)` bounds the caller's wait but does not implicitly stop the client; close it if startup should be abandoned.

## Events and metrics

```java
@Override
public void onStatusChange(StatusChangeEvent event) {
    System.out.println(event.getPreviousStatus() + " -> " + event.getNewStatus());
}

@Override
public void onReconnect(ReconnectEvent event) {
    System.out.println(event.getReason() + ", retry in " + event.getDelay());
}
```

Both event classes live in `net.guneyilmaz0.micup4j.events`. They have sequence `-1`: these are local diagnostics, not Gateway dispatches, and they never advance a heartbeat acknowledgement.

Reasons distinguish a server reconnect request, transport error, missed heartbeat ACK, handshake timeout, invalid payload, and close frame. A close diagnostic contains its numeric code, never the server-provided reason text or authentication payload.

Diagnostics share the bounded listener executor and are best effort. They can be omitted when the queue has little capacity or shutdown interrupts delivery. Use `getStatus()`, `getMetrics()`, and `termination()` to observe terminal state reliably. A graceful `shutdown()` reports `SHUTTING_DOWN` while REST work drains, then delivers a `StatusChangeEvent` for `SHUTDOWN` and one `events.session.ShutdownEvent` (`ListenerAdapter.onShutdown`); delivery stays best effort, particularly after `shutdownNow()`.

`ClientMetrics` includes reconnect count, queued events, delivered events, listener failures, latest queue delay, REST counters, and Gateway heartbeat latency.

## Close-code policy

| Code | Meaning | Client action |
| --- | --- | --- |
| 4000 | Unknown/server drain error | Reconnect and attempt Resume |
| 4001 | Unknown opcode | Stop; inspect client/protocol configuration |
| 4002 | Decode error | Stop; avoid repeating an invalid payload |
| 4003 | Not authenticated | Discard local session state and Identify |
| 4004 | Authentication failed | Stop; correct credentials |
| 4005 | Already authenticated | Stop; avoid repeated authentication misuse |
| 4007 | Invalid sequence | Discard local session state and Identify |
| 4008 | Rate limited/capacity exceeded | Reconnect with backoff and attempt Resume |
| 4009 | Session heartbeat timeout | Reconnect and attempt Resume |
| 4010 | Invalid shard | Stop; correct the shard configuration |
| 4011 | Sharding required | Stop; configure sufficient shards |
| 4012 | Invalid API version | Stop; correct protocol configuration |
| 1000, 1001, transport loss, unassigned codes | Ordinary/unknown closure | Reconnect and attempt Resume |

`GatewayCloseCode.fromCode(code)` exposes this policy. Stop is a conservative client decision for malformed protocol/authentication cases, even where the server may retain a session. A failed Resume that receives Invalid Session starts a fresh Identify on the current socket.

## Presence

Builder/runtime presence now uses Identify's initial presence and authenticated opcode 3. Updates coalesce to the latest desired state, are paced at least 4.1 seconds apart on one socket, and are restored after Ready/Resumed. Micup publishes custom status rather than Discord activities; see [presence mapping and limits](PRESENCE.md).

## Typed message and member events

Gateway `MESSAGE_UPDATE`, `MESSAGE_DELETE`, `GUILD_MEMBER_ADD` and `GUILD_MEMBER_REMOVE` dispatches are delivered as `MessageUpdateEvent`, `MessageDeleteEvent`, `GuildMemberJoinEvent` and `GuildMemberRemoveEvent`, with `GenericMessageEvent`/`GenericGuildMemberEvent` parents and matching `ListenerAdapter` callbacks. See the [compatibility guide](JDA-COMPATIBILITY.md).

- Update events carry the complete current message; there is no previous content.
- `MessageDeleteEvent.getContentRaw()` and `getAuthorId()` are nullable: Micup omits them when an admin, moderator or system removed the message.
- `GUILD_MEMBER_REMOVE` carries only the user id, so the account is resolved from held state; `getMember()` is the pre-removal snapshot or null.
- In guilds above 250 members, passive sessions receive member add, update and remove dispatches only for their own user.

**Malformed payloads.** If a typed event payload is malformed (for example a blank interaction token or an invalid snowflake in `MESSAGE_CREATE`), the dispatch is delivered as a plain `GenericEvent` and a failure is reported through the listener failure path. The session is not failed.

`GenericEvent.getData()` still returns a copy; typed getters read the payload without per-call deep copies. Subclasses use the protected `rawData()` accessor.

## Delivery and shutdown

Events remain ordered within a session. Processed sequences advance after listener delivery completes. Replayed sequences already accepted by the client are suppressed within that session. This is not durable exactly-once delivery; Micup does not retain every event for replay.

A full event queue causes a permanent client failure instead of silently acknowledging a dropped dispatch. Listener exceptions are isolated and reported without their potentially credential-bearing exception text. Shutdown aborts open or late-opening sockets, cancels retry timers, closes REST work, and completes pending lifecycle futures.

Since `0.3.0-alpha`, the client applies ordered entity-cache updates before listeners; see [entity state and invalidation](ENTITIES.md). Typed guild lifecycle callbacks run on that same event, after cache updates; identity classification is bounded separately from entity eviction. See the [lifecycle rules](JDA-COMPATIBILITY.md#guild-lifecycle-events). Invalid permission-bearing entity payloads fail processing and are not acknowledged as successful delivery.

Reference: [Micup Gateway](https://micup.gg/docs/gateway/overview/), [close codes](https://micup.gg/docs/gateway/opcodes-and-close-codes/).

## Member requests (opcode 8)

`retrieveMembersByPrefix(guildId, prefix, limit)`, `retrieveMembersByIds(guildId, ids)` and `loadMembers(guildId)` (and their `Guild` equivalents) send Request Guild Members and return a running `Task<List<Member>>`. Micup runs one member request per connection and replaces a pending one, so Micup4J sends them strictly one at a time in submission order, each with a unique nonce, and holds at most 64 more in a local queue. Chunks are matched by nonce and collected until `chunk_count` is reached; the merged member cache is updated by the same events.

- No chunk for 30 seconds fails the task with `TimeoutException`; Micup silently drops requests for guilds the session is not connected to. `Task.setTimeout` adds an overall bound.
- `loadMembers` is admitted once per bot and guild every 30 seconds; a refusal fails with `GatewayRateLimitException.getRetryAfter()`.
- Chunks are never replayed on Resume, so a connection loss fails the running request. Queued requests are sent after the session is restored. Closing the client fails every pending request.
- Ignoring `GUILD_MEMBERS_CHUNK` makes these helpers fail immediately. Results are held in memory in full; `loadMembers` may return up to 100,000 members.
