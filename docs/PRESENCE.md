# Session presence

The source checkout supports the familiar JDA builder and presence manager names using Micup's Identify presence and opcode 3. These additions are part of `0.3.0-alpha`; they were not in the `0.2.0-alpha` release.

```java
var client = MicupBuilder.createDefault(token)
    .setStatus(OnlineStatus.ONLINE)
    .setActivity(Activity.customStatus("Ready for commands"))
    .setIdle(false)
    .build();

client.getPresence().setPresence(
    OnlineStatus.DO_NOT_DISTURB,
    Activity.playing("BMO"),
    false);
```

Import `OnlineStatus` from the root package and `Activity` from `entities`. `getPresence()` returns `managers.Presence`. It provides `getStatus`, `getActivity`, `isIdle`, `getJDA`, and the supported `setStatus`, `setActivity`, `setIdle`, and `setPresence` overloads. Setters are asynchronous void operations; they publish requested state rather than returning a server acknowledgement.

## Wire mapping

Micup publishes a custom status, not a Discord activities array. The adapter renders these factories into `custom_status.text`:

| Factory | Published text |
| --- | --- |
| `Activity.customStatus("Ready")` | `Ready` |
| `Activity.playing("BMO")` | `Playing BMO` |
| `Activity.listening("music")` | `Listening to music` |
| `Activity.watching("commands")` | `Watching commands` |
| `Activity.competing("an event")` | `Competing in an event` |

The type remains available in the local activity value. Other users see the rendered custom-status text; they do not receive Discord game/stream metadata, an activity list, streaming URLs, artwork, buttons or rich presence. Streaming and rich-presence factories are not implemented. The rendered text, including the prefix, must fit Micup's 128-code-point limit. Blank activity names are rejected.

`ONLINE`, `IDLE`, `DO_NOT_DISTURB`, and `INVISIBLE` map to `online`, `idle`, `dnd`, and `invisible`. `OFFLINE` and null normalize to `INVISIBLE`, as in JDA's presence manager; `UNKNOWN` is rejected before any send. `isIdle`/`setIdle` controls the separate `afk` field, rather than changing the online status. This implementation always sends `mobile: false`.

## Saved state, clearing, and reconnect

An untouched builder omits Identify's presence entirely and sends no unsolicited opcode 3. This preserves the account's saved state. The manager getters expose local desired defaults (`ONLINE`, no activity, `idle: false`), not a readback of the account's saved configuration.

A status-only or idle-only change omits `custom_status` until an activity has been explicitly configured. This keeps a saved custom status intact. Calling `setActivity(null)` explicitly sends `custom_status: null` and clears it. `setPresence(status, activity, ...)` explicitly configures the activity, including null. After activity has been configured, later status/idle updates include that same desired activity or explicit clearing value.

Configured state is included in Identify and reapplied after Ready. The second send is deliberate: Micup can prefer a saved account status/custom status over Identify's initial fields. Changes during discovery, handshake or reconnect replace the pending desired state. No presence update is sent before Ready or Resumed authenticates that socket. Successful Resume reapplies the latest configured state; a fresh Identify also reads the latest state. Terminal failure and shutdown reject further setters.

There is no acknowledgement for opcode 3. Entitlements, account restrictions, saved status resolution, other account sessions and server validation can affect what other users actually see. The getters indicate requested state, not proof of publication.

## Scheduling and bounds

Micup accepts at most five presence updates per 20 seconds on one WebSocket. The client spaces its updates by at least 4.1 seconds on that socket. The first configured update after authentication can send immediately. Later calls coalesce into the latest desired state, so intermediate values can be skipped.

Only one presence signal is queued for the protocol executor, with one scheduled retry timer and one active presence send. Heartbeats continue while a presence update waits. A new WebSocket starts a new local presence pacing window; re-identifying on the same socket retains the current window. Cancelled Gateway timers are removed from the scheduler, and reconnect/shutdown cancel pending presence timers. These rules apply per client/socket; process-wide or cross-client coordination remains future work.

Reference: [Micup client commands: Identify and Presence Update](https://micup.gg/docs/gateway/commands/), [JDA Presence](https://docs.jda.wiki/net/dv8tion/jda/api/managers/Presence.html), [JDA Activity](https://docs.jda.wiki/net/dv8tion/jda/api/entities/Activity.html).
