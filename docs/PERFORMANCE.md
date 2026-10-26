# Performance and load testing

`0.5.0-beta` ships a repeatable load harness. It runs Micup4J against an in-process mock Micup instance (discovery, Gateway with Resume replay, and REST) over loopback. The numbers therefore describe the library and the JVM on the test machine, not Micup's servers, real network latency, or a production bot. No comparison with JDA was measured, and none is claimed.

## Running it

```bash
./gradlew loadTest -Pload.soakSeconds=120
```

The harness prints JSON and writes it to `build/reports/load/results.json`. Properties:

| Property | Default | Meaning |
| --- | ---: | --- |
| `load.events` | 50,000 | Messages in the unbounded burst |
| `load.pacedRate` | 5,000 | Messages per second in the paced run (5 s long) |
| `load.guilds` | 1,000 | Guild snapshots in the startup burst (8 roles, 12 channels, 20 members each) |
| `load.requests` | 250 | Concurrent REST requests in the REST burst |
| `load.shards` | 8 | Shards started by the shard manager |
| `load.soakSeconds` | 0 (off) | Soak length; one resumable interruption every 10 s at about 500 events/s |

The JVM runs with `-Xms512m -Xmx512m -XX:+UseG1GC`. Clients use a 65,536-event queue so that the unbounded burst measures backlog rather than overflow.

## Workloads and what they show

- **Event burst:** frames are sent as fast as the socket accepts them. Throughput is the useful number. Listener delay here is time spent queued behind earlier frames, so it grows with the burst size.
- **Paced events:** a fixed offered rate. Listener delay is measured from frame send to listener call (transport, parsing, queueing and dispatch) and is the latency figure to use.
- **Startup burst:** Ready followed by guild snapshots with the entity cache enabled. It reports processing rate and heap retained per cached guild after GC.
- **REST burst:** all requests submitted at once. Latency is dominated by the client's 20 ms global spacing (50 requests/s), by design.
- **REST sequential:** one request at a time, compared with a bare `java.net.http` client against the same mock. The difference is the scheduler's own overhead.
- **Shards:** time for the manager to start every shard sequentially without spacing, plus a graceful shutdown.
- **Soak:** sustained events with a resumable Gateway close every 10 seconds. The mock replays missed dispatches on Resume. Loss must be zero, and heap drift should stay small.

Allocation per event counts platform threads only (`ThreadMXBean`); allocations on virtual threads are excluded, so treat it as a lower bound.

## Results for 0.5.0-beta

Raw output: [results-0.5.0-beta.json](performance/results-0.5.0-beta.json). Machine: 8 CPUs, Linux 7.2 x86_64, OpenJDK 21.0.12 (Corretto), G1, 512 MiB heap.

| Workload | Result |
| --- | --- |
| Event burst | 50,000 events in 0.99 s, about 50,700 events/s, all delivered in order |
| Paced events (5,000/s) | listener delay p50 0.04 ms, p95 0.19 ms, p99 3.1 ms, p99.9 36 ms; about 9 KB allocated per event |
| Startup burst | 1,000 guild snapshots in 0.24 s (about 4,100/s); about 10 KB retained heap per cached guild |
| REST burst | 250 requests at 49/s; p50 2.5 s and p99 5.0 s, from global spacing |
| REST sequential | p50 43.1 ms against a 43.0 ms bare HttpClient baseline; scheduler overhead below 0.2 ms |
| Shards | 8 shards ready in 0.42 s (about 52 ms each) and shut down gracefully |
| Soak (120 s) | 56,309 events sent and received, 0 lost, 11 resumes, final status CONNECTED, heap drift 2.7 MiB |

The 43 ms REST floor comes from the mock transport on loopback, not the library: the bare client shows the same latency. The p99.9 paced listener delay includes GC pauses and harness scheduling on a shared desktop machine. Results vary between machines and runs. Rerun the harness on the deployment hardware before relying on any figure.

## Memory bounds

Entity memory is bounded by the configured `CachePolicy` counts, not by bytes. The startup burst figure of about 10 KB per guild, for the stated guild shape, gives a rough budget: for example, 1,000 cached guilds of that shape retain about 10 MiB. Event queues, the REST queue and member-request queues are bounded by their configured capacities. Pending Gateway member requests hold their results in memory until they complete.
