# REST scheduling in 0.2.0-alpha

Micup4J has one request scheduler per client. Configuration and counters are public; request URLs, payloads, bot tokens, and interaction tokens are excluded from diagnostic snapshots.

## Configuration

```java
import java.time.Duration;
import net.guneyilmaz0.micup4j.config.RestConfig;

var policy = RestConfig.defaults()
    .withRequestTimeout(Duration.ofSeconds(15))
    .withTotalTimeout(Duration.ofSeconds(90))
    .withMaxRetries(3)
    .withMaxConcurrency(8)
    .withBucketCacheSize(4096)
    .withCallbackCapacity(8);

var client = MicupBuilder.createDefault(token)
    .setRestConfig(policy)
    .setQueueCapacity(256, 1024)
    .build();
```

| Setting | Default | Meaning |
| --- | --- | --- |
| Request timeout | 20 seconds | HTTP attempt timeout, also capped by the remaining total budget |
| Total timeout | 2 minutes | Entire operation, including queue time, rate limit waits, and retries |
| Maximum retries | 5 | Additional attempts after a valid HTTP 429 response |
| Maximum concurrency | 8 | Active HTTP attempts across normal requests and callbacks |
| Bucket cache size | 4,096 | Separate upper bound for route bindings and learned server buckets |
| Callback capacity | 8 | Additional admission slots reserved for initial interaction callbacks |
| Normal capacity | 1,024 | Default maximum running plus waiting normal operations; changed by `setQueueCapacity` |

Timeouts must be positive and no longer than one day. Concurrency is 1–256, retries 0–20, bucket capacity 1–65,536, and callback capacity 1–1,024.

## Priorities and deadlines

Initial responses sent through `InteractionCreateEvent.respond`, `reply`, `replyEmbeds`, or `deferReply` use the callback queue. Eligible callbacks run before normal operations, with earlier deadlines first. One transport slot is reserved when concurrency is greater than one. With concurrency set to one, an active request cannot be preempted.

A callback has a local deadline **2.5 seconds after its event object is created**, before it enters the listener queue. This leaves a margin against Micup's three-second server deadline, but it cannot recover time already spent in the server or network. The smaller of this deadline and the configured total timeout applies. An expired callback fails locally without being sent. No scheduling priority bypasses server rate limits.

Callbacks have eight separate admission slots by default; the combined maximum outstanding operations is normal capacity plus callback capacity. Raw `RestClient.request(...)` calls use normal scheduling; the event response API supplies priority and its original deadline automatically.

## Buckets and pacing

The scheduler normalizes numeric path parameters and removes query strings from scheduling keys. Channel, guild, application, user, and webhook resources remain separate major keys. Token-bearing path segments are hashed in internal keys, never exposed in metrics.

When a response supplies `X-RateLimit-Bucket`, routes with that identifier and the same major resource share cooldown and in-flight accounting. Routes already in flight when a bucket is learned cannot be recalled. Before an identifier is known, normalized route state provides the fallback.

Successful responses with zero remaining allowance use `X-RateLimit-Reset-After`. A 429 uses fractional `retry_after`, falling back to `Retry-After`, and preserves cooldowns even on the final failed attempt. A global denial pauses the client's scheduler. Pacing remains conservative at 50 request starts per second across this client, including callback traffic.

Idle expired cache entries are evicted. If all route entries are busy or cooling down, a new route can fail admission rather than evict an active cooldown. If the server-bucket cache is full of active entries, an unlearned identifier retains route-local scheduling until cache space is available. HTTP 429 remains authoritative.

Different `Micup` clients and processes do not coordinate their budgets. Webhook exemptions and higher account allowances do not increase the default pacing in this release.

## Cancellation and retries

`submit()` returns a cancellable future. Cancellation removes pending work, releases admission, cancels its deadline timer, and requests cancellation of an active HTTP future. `map()` and `flatMap()` propagate cancellation to their source or active child.

```java
var pending = channel.sendMessage("Hello").submit();
pending.cancel(true);
```

Cancellation is best effort once a request has started: a server may already have applied a write. Timeouts have the same uncertainty. Micup4J retries **only HTTP 429 responses**, keeping the serialized request body unchanged. It does not automatically retry network errors, timeouts, HTTP 5xx, or permission failures.

`complete(Duration)` adds a caller-side timeout and cancels its submitted operation on expiry. Interrupted blocking callers also cancel their operation and retain the thread's interrupt flag.

Callbacks remain single-use locally after a response is submitted, even when submission fails. This conservative guard avoids accidentally sending a second initial response after an uncertain outcome.

## Metrics

```java
var stats = client.getMetrics();
System.out.println(stats.rest().queued());
System.out.println(stats.rest().inFlight());
System.out.println(stats.rest().retries());
System.out.println(stats.lastEventQueueDelayMillis());
```

REST counters distinguish accepted submissions, successful completions, failures, cancellations, and retries. HTTP latency includes individual attempts and excludes queue/cooldown time. Event delivery counts include local diagnostics; listener failures are counted independently. A snapshot is observational and may be slightly behind activity on another thread.

The queues, active requests, and bucket caches are bounded. User callbacks run outside the scheduler; applications remain responsible for keeping their own callback work bounded.

Reference: [Micup rate limits](https://micup.gg/docs/topics/rate-limits/).
