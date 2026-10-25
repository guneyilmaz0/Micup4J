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

Diagnostics share the bounded listener executor and are best effort. They can be omitted when the queue has little capacity or shutdown interrupts delivery. Use `getStatus()`, `getMetrics()`, and `termination()` to observe terminal state reliably; do not depend on receiving a final status callback during shutdown.

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

## Delivery and shutdown

Events remain ordered within a session. Processed sequences advance after listener delivery completes. Replayed sequences already accepted by the client are suppressed within that session. This is not durable exactly-once delivery; Micup does not retain every event for replay.

A full event queue causes a permanent client failure instead of silently acknowledging a dropped dispatch. Listener exceptions are isolated and reported without their potentially credential-bearing exception text. Shutdown aborts open or late-opening sockets, cancels retry timers, closes REST work, and completes pending lifecycle futures.

The unreleased source checkout adds ordered entity-cache updates before listeners; see [entity state and invalidation](ENTITIES.md). Invalid permission-bearing entity payloads fail processing and are not acknowledged as successful delivery.

Reference: [Micup Gateway](https://micup.gg/docs/gateway/overview/), [close codes](https://micup.gg/docs/gateway/opcodes-and-close-codes/).
