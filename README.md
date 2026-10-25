# Micup4J

**An asynchronous Java library for building Micup bots.**

Micup4J provides a familiar builder, event listener, and REST action API inspired by [JDA](https://github.com/discord-jda/JDA), implemented for Micup's REST and Gateway protocols.

**Release:** `0.2.0-alpha` · **Java:** 21+ · **REST / Gateway:** v1

> Early alpha: the public API may change between releases. Micup4J does not yet provide JDA's full feature set or established performance characteristics.

[Getting started](#getting-started) · [Supported features](#supported-features) · [Roadmap](ROADMAP.md) · [Changelog](CHANGELOG.md) · [Upgrade guide](UPDATE.md)

## Unreleased development

The current source checkout also includes the first `0.3.0-alpha` milestone work: typed guild/member/role/channel reads, basic management actions, bounded member pagination, configurable entity caches and permission calculations. These additions are not in the previously prepared `0.2.0-alpha` release. See the [entity API guide](docs/ENTITIES.md) for usage, cache defaults, consistency rules and remaining scope.

## Supported features

| Area | Available in this release |
| --- | --- |
| Discovery | Instance discovery through `/.well-known/fluxer`; REST uses `endpoints.api_public` |
| Gateway | Hello, Identify, heartbeat/ACK, reconnect with backoff, Resume, and Invalid Session recovery |
| Events | Typed ready, message, and interaction events; raw access to other dispatches |
| REST | Lazy actions, callbacks, futures, bounded request admission, and virtual thread workers |
| Rate limits | Normalized routes, learned shared buckets, global pacing, and HTTP 429 cooldowns |
| Messages | Send, retrieve, reply, edit, and delete |
| Users | Retrieve a user and access the connected bot's user |
| Commands | Global/guild slash commands with typed basic value options |
| Interactions | Priority/deadline-aware replies, defer, edit/read/delete original responses, and followups |
| Embeds | Validated rich embed builder; channel, reply, and interaction helpers |
| Diagnostics | Typed status/reconnect events and queue, retry, and latency metrics |
| Shards | Explicit shard ID and shard count for a single session |

Micup does not use Discord's intent system. Micup4J supports ignored event names instead.

## Requirements

- A **JDK 21** installation to build the library; Java 21 or later to use the resulting JAR.
- A Micup bot token and a server where the bot has been invited.
- Network access to the instance's discovery, public API, and Gateway endpoints.

The Gradle wrapper is included. You do not need a separate Gradle installation.

## Installation

This release is prepared for source builds and local Maven publication. It is **not published to Maven Central**.

### Build from source

From the repository directory:

```bash
./gradlew clean build
```

On Windows, use `gradlew.bat`. Build outputs are written to `build/libs/`:

```text
Micup4J-0.2.0-alpha.jar
Micup4J-0.2.0-alpha-sources.jar
Micup4J-0.2.0-alpha-javadoc.jar
```

The main JAR is a library JAR, not a standalone executable or a bundle of dependencies.

### Use a sibling checkout

Place your bot project beside the Micup4J checkout. In your bot's `settings.gradle.kts`:

```kotlin
includeBuild("../Micup4J")
```

In your bot's `build.gradle.kts`:

```kotlin
repositories { mavenCentral() }

dependencies {
    implementation("net.guneyilmaz0.micup4j:Micup4J:0.2.0-alpha")
}
```

Gradle builds Micup4J from the local checkout. This is also how the BMO integration uses the library.

### Use Maven local

In the Micup4J checkout:

```bash
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` to the consuming project's repositories and use the dependency shown above. Local publication installs the POM and dependencies metadata as well as the JAR.

## Getting started

Create a bot in **Micup → Settings → Applications**, save its full token, and invite it to your server. A basic message bot needs **View Channel** and **Send Messages**; channel overrides must also allow access. See the [official bot guide](https://micup.gg/docs/bots/hizli-baslangic/).

The included example listens for `!ping` and responds with `Pong!`:

```java
import net.guneyilmaz0.micup4j.MicupBuilder;
import net.guneyilmaz0.micup4j.events.MessageReceivedEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;

public class Bot {
    public static void main(String[] args) throws Exception {
        var client = MicupBuilder.createDefault(System.getenv("MICUP_TOKEN"))
            .addEventListeners(new ListenerAdapter() {
                @Override
                public void onMessageReceived(MessageReceivedEvent event) {
                    if (event.getAuthor().isBot()) return;
                    if (event.getMessage().getContentRaw().equals("!ping")) {
                        event.getMessage().reply("Pong!").queue();
                    }
                }
            })
            .build();

        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown));
        try (client) {
            client.awaitReady();
            client.termination().join();
        }
    }
}
```

`build()` starts connecting asynchronously. `awaitReady()` waits up to 60 seconds for the initial Ready event; it does not wait for every guild startup dispatch. `termination()` completes on shutdown and fails exceptionally on a permanent client failure.

### Run the included example

Set `MICUP_TOKEN` in your IDE or shell environment, then run:

```bash
./gradlew runExample
```

A Bash prompt that keeps the token out of command history:

```bash
read -rsp 'Micup bot token: ' MICUP_TOKEN
export MICUP_TOKEN
printf '\n'
./gradlew runExample
unset MICUP_TOKEN
```

The library and example read no `.env` file automatically. `.env` loading belongs to the consuming application; BMO provides its own loader.

Full source: [PingBot.java](src/examples/java/net/guneyilmaz0/micup4j/examples/PingBot.java).

## Working with REST actions

Actions are lazy. **Every call to `queue()`, `submit()`, or `complete()` executes a new operation.** Retain the returned future if you need to observe one execution from several places.

```java
var channel = client.getMessageChannelById("123456789012345678");

// Callback-based execution.
channel.sendMessage("Hello, Micup!").queue(
    message -> System.out.println("Sent message: " + message.getId()),
    error -> System.err.println("Send failed: " + error.getClass().getSimpleName())
);

// Future-based execution.
var sent = channel.sendMessage("Another message").submit();
sent.thenAccept(message -> System.out.println(message.getId()));

// Blocking execution; use outside event listeners.
var message = channel.retrieveMessageById("234567890123456789").complete();
```

Channel handles do not perform a lookup or guarantee permission to access a channel. The server validates access when a request executes.

`map()` and `flatMap()` compose actions. Prefer asynchronous execution in listeners; slow work delays later events and may make interaction responses miss their deadline.

## Slash commands and interactions

After the client is ready, register a command:

```java
import net.guneyilmaz0.micup4j.interactions.SlashCommand;

client.updateGuildCommands(
    "123456789012345678",
    new SlashCommand("ping", "Check whether the bot is available")
).queue();
```

**Command updates replace the entire command set in that scope.** Register only when definitions change. `updateCommands(...)` targets the global scope.

Handle the command in your listener:

```java
@Override
public void onInteractionCreate(InteractionCreateEvent event) {
    if (event.getInteractionType() == 2 && event.getName().equals("ping")) {
        event.reply("Pong!", true).queue(); // Ephemeral response.
    }
}
```

Import `net.guneyilmaz0.micup4j.events.InteractionCreateEvent` for this handler.

For longer work, submit `deferReply(true)` first and chain `editOriginal(...)` after it completes. Micup requires the initial response within **3 seconds** and permits later edits and followups within **15 minutes**. Those deadlines are enforced by the server. See [Interactions](https://micup.gg/docs/http-api/interactions/).

`respond(type, JsonNode)` exposes supported callback shapes for component updates, autocomplete, and modals. Typed component builders, subcommands, choices, and advanced option constraints remain planned. Basic typed value options and rich embed builders are available.

## Embeds and typed options

```java
import net.guneyilmaz0.micup4j.entities.EmbedBuilder;
import net.guneyilmaz0.micup4j.interactions.OptionType;

var embed = new EmbedBuilder()
    .setTitle("Avatar", "https://example.com/avatar.webp")
    .setColor(0x3498DB)
    .setImage("https://example.com/avatar.webp")
    .setFooter("Requested by a member", null)
    .build();
client.getMessageChannelById("123456789012345678").sendMessageEmbeds(embed).queue();

var command = new SlashCommand("avatar", "Show a member's avatar")
    .addOption(OptionType.USER, "user", "Choose a member", false);
```

Embed snapshots are immutable and validate Micup's documented per-field limits. Message helpers accept 1–10 embeds. `SlashCommand` is immutable: retain the definition returned by `addOption`. Required options must precede optional options and names must be unique.

Interaction handlers can use `getOptionValue("user")`, `replyEmbeds(true, embed)`, and `editOriginalEmbeds(embed)`. A deferred response can be composed as `event.deferReply(true).flatMap(ignored -> event.editOriginalEmbeds(embed)).queue()`.

## Configuration

```java
var builder = MicupBuilder.createLight(token)
    .setInstanceOrigin(java.net.URI.create("https://micup.gg"))
    .setIgnoredEvents("TYPING_START")
    .setQueueCapacity(256, 1024) // REST operations, then waiting events.
    .setShard(0, 2)
    .setErrorHandler(error -> System.err.println(error.getMessage()));
```

- In the unreleased source checkout, `createDefault` uses bounded entity caches and `createLight` stores none. Use `setCachePolicy(...)` to customize limits; cached clients require their state events. The prepared `0.2.0-alpha` release had no persistent entity cache.
- Queue capacity must be positive. Normal REST capacity includes running and waiting operations, with eight additional callback admission slots by default; event capacity counts waiting events, with one additional event potentially executing.
- `.setShard(0, 2)` opens **one** session for shard 0 of 2. It does not create the other shard.
- REST and discovery endpoints require HTTPS, and Gateway endpoints require WSS. Plain HTTP/WS is allowed only for loopback development endpoints.
- `getGatewayPing()` returns heartbeat latency in milliseconds, or `-1` before the first acknowledgement.
- `MicupVersion.getVersion()` exposes the build version.

## Reliability and current limits

The Gateway protocol runs separately from user listeners. Events are delivered in session order, and heartbeat sequences advance after listener processing completes. Resume suppresses duplicate dispatch sequences within the retained session; it does not provide a durable exactly-once delivery guarantee.

The REST scheduler uses normalized routes and learned server bucket identifiers, with conservative pacing of 50 requests per second per client. Initial interaction callbacks have separate bounded admission, a reserved transport slot when concurrency exceeds one, and an original local deadline. Normal requests, cooldowns, retries, and active I/O are subject to configurable timeouts. Only HTTP 429 responses are retried.

See [REST scheduling](docs/REST.md) and [Gateway lifecycle](docs/GATEWAY.md) for configuration, cancellation semantics, metrics, cache limits, and the close-code policy. Cross-client/process rate limit coordination is not implemented.

If the event queue fills, the client fails instead of silently acknowledging a dropped event. On shutdown, queued work is not persisted. Cancellation propagates through composed actions and requests active HTTP cancellation; an already-applied server write cannot be undone.

Not yet included:

- Complete entity managers, indexed member queries, Gateway member requests, and advanced mutation helpers. Basic caches and permission resolution are available in the unreleased source checkout.
- Typed component builders, attachment uploads, subcommands, choices, and advanced command option constraints.
- Automatic shard orchestration or distributed rate limit coordination.
- Voice connections, audio playback, or music support.
- A measured JDA performance comparison.

See [ROADMAP.md](ROADMAP.md) for planned stages and their completion criteria.

## Credentials and logging

Supply the **raw bot token**, without the `Bot ` prefix. HTTP authentication adds that prefix; Gateway authentication uses the raw value. Do not commit tokens or log raw interaction payloads: `GenericEvent.getData()` can include an interaction credential.

Library-generated HTTP errors omit credential-bearing URLs and response bodies. Your own callbacks and loggers remain responsible for the data they print. Permission checks for privileged commands belong in the bot; command visibility is not authorization.

## Development

```bash
./gradlew clean build             # Compile, test, compile examples, and package.
./gradlew test                    # Run local HTTP/WebSocket tests.
./gradlew javadoc                 # Generate API documentation.
./gradlew publishToMavenLocal      # Install the alpha into Maven local.
```

Tests use local mock servers and require no Micup token. They cover discovery, authentication payloads, heartbeat sequencing, reconnect/Resume, interactions, rate limits, queue handling, entity cache consistency, permission precedence, member pagination, and a 1,000-event ordering smoke test. The event burst is a correctness test, not a production benchmark.

The GitHub workflow runs the build on JDK 21. See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines and [UPDATE.md](UPDATE.md) for release preparation.

## References

- [Micup API documentation](https://micup.gg/docs/)
- [Gateway protocol](https://micup.gg/docs/gateway/overview/)
- [Gateway commands](https://micup.gg/docs/gateway/commands/)
- [Rate limits](https://micup.gg/docs/topics/rate-limits/)
- [JDA](https://github.com/discord-jda/JDA) — inspiration for the developer-facing API; Micup4J uses its own implementation.

Micup4J is a community project and is not an official Micup or JDA release.
