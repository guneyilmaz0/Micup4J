# JDA-style source compatibility (unreleased)

Micup4J now prioritizes familiar JDA source patterns for common text bots. The target is to keep handler bodies and method chains recognizable while replacing the transport with Micup's protocol. This is an implemented subset, not a drop-in replacement for the complete JDA library or a claim of a measured compatibility percentage.

The [compiled example](../src/examples/java/net/guneyilmaz0/micup4j/examples/JdaStyleBot.java) uses the supported JDA-style package layout and method chains. `./gradlew build` compiles it and runs local protocol tests. The example has not been tested against a live Micup account in this change.

## Porting common code

Change the import prefix from `net.dv8tion.jda.api` to `net.guneyilmaz0.micup4j`. Replace the `JDA` session type with `Micup`, and `JDABuilder` with `MicupBuilder`. For the subset below, class names and handler bodies otherwise keep their familiar shape.

```java
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.MicupBuilder;
import net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.interactions.commands.OptionType;
import net.guneyilmaz0.micup4j.interactions.commands.build.Commands;

class Bot extends ListenerAdapter {
    public static void main(String[] args) throws InterruptedException {
        Micup client = MicupBuilder.createDefault(System.getenv("MICUP_TOKEN"))
            .addEventListeners(new Bot()).build().awaitReady();
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown));
        // Run when definitions change; this replaces the entire global command set.
        client.updateCommands().addCommands(
            Commands.slash("hello", "Say hello")
                .addOption(OptionType.STRING, "name", "Display name", false)
        ).queue();
    }

    @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("hello"))
            event.reply("Hello!").setEphemeral(true).queue();
    }
}
```

Micup has no Discord intents. Remove Discord intent configuration instead of expecting a no-op `enableIntents` adapter. No Discord classes or binaries are shipped by this compatibility layer. It works with the Micup token, discovery, Gateway and REST endpoints.

## Implemented source surface

| Familiar usage | Current implementation |
| --- | --- |
| `createDefault/createLight`, listener registration, `build().awaitReady()` | Real Micup lifecycle; no-argument `awaitReady` waits until ready/failure and declares `InterruptedException` |
| `events.session.ReadyEvent` and `events.message.MessageReceivedEvent` | Typed Gateway events; existing flat-package listener overrides still receive events through a bridge |
| `events.interaction.command.SlashCommandInteractionEvent` | Routed for command interactions only; legacy `onInteractionCreate` remains available |
| `event.getJDA()` | Alias returning the actual `Micup` session; `getMicup()` remains available |
| `getGuildById`, `getMemberById`, `getRoleById`, cached text-channel lookups | Object or null on a cache miss; string and unsigned-long ID overloads where provided |
| Optional cache access | Explicit `findGuildById`, `findMemberById`, `findRoleById`, `findChannelById` |
| `getId/getIdLong`, `getName`, member/user getters | Added to the modeled entities; unsigned snowflakes retain their full bits |
| `getTextChannelById(...).sendMessage(...).queue()` | Cached text-channel handle with message operations; null if uncached or not text |
| `entities.channel.middleman.MessageChannel` | Interface for the supported message-channel operations; legacy handle implements it |
| `entities.channel.ChannelType.PRIVATE` | Uses Micup channel values; legacy constants remain aliases |
| `Commands.slash(...).addOption(...)` | Mutable, validated command definition; required options precede optional ones |
| `updateCommands().addCommands(...).queue()` | Lazy typed bulk replacement globally or on a client-bound guild |
| `guild.upsertCommand`, `retrieveCommands`, `deleteCommandById` | Real command REST operations, returning typed `Command` values |
| `event.getOption/getOptions` | `OptionMapping` with strings, booleans, numeric conversions and resolved user/member/role/channel access |
| `reply(...).setEphemeral(true)` / `replyEmbeds(...)` | Fluent lazy callback action; completion returns `InteractionHook` |
| `deferReply().queue(hook -> ...)` / `getHook()` | Acknowledgement barrier, original response operations and basic followups |
| Root `EmbedBuilder`, `Color`, common author/footer overloads, copies and clearing | Micup validation, with immutable built embeds |
| Root `Permission`, `member.hasPermission`, `getPermissions` | JDA-style names mapped to Micup bits, using the existing resolver |
| Member role color | Highest assigned nonzero role color, following Micup hierarchy ordering |
| `User.getAvatarUrl()` | Discovered Media Proxy URL for a custom avatar; null when absent |

### Reply and defer flows

```java
event.reply("Only you can see this").setEphemeral(true).queue();

// For slow work, acknowledge first and produce the original response afterwards.
event.deferReply(true).queue(hook ->
    hook.editOriginal("Finished").queue(message ->
        System.out.println(message.getId())));
```

Reply/defer actions are mutable before their first submission and frozen afterwards. The interaction still allows only one initial callback. HTTP success settles the shared acknowledgement barrier before the hook is exposed to user callback consumers. Hook actions queued while acknowledgement is pending wait on an independent future; cancelling such an action does not cancel the initial callback. Failed acknowledgement prevents subsequent hook HTTP requests.

The first `hook.sendMessage(...)` after a defer creates the original response with PATCH; later sends create public followups. Set the original response's ephemeral state on the initial reply/defer. Followup ephemeral setters and the full webhook action hierarchy remain future work. Avoid concurrent writes when their order matters; compose actions or wait for completion.

**Micup has no public deferred placeholder.** A public `deferReply()` followed immediately by `hook.retrieveOriginal()` cannot return a message before one exists. Use `editOriginal(...)` or the first `sendMessage(...)` to create it. Micup4J returns an explicit error for this unsupported placeholder flow rather than creating a visible synthetic message. After creating the original, retrieval and deletion are available. Private deferred response retrieval is forwarded to the server, because the documented no-placeholder rule specifically describes public responses.

Hook validity is limited locally to roughly 15 minutes from event receipt, and the server remains authoritative. Tokens may have less remaining validity after transport delays. Initial callbacks still use the original 2.5-second local deadline including listener queue time.

### Options and entity context

`getOption(name)` returns null when absent; the overload `getOption(name, fallback, OptionMapping::getAsString)` supplies a default. Option mappings are detached payload copies. They perform no HTTP request. Boolean access requires a boolean option; integer conversions reject overflow, and ID conversions preserve unsigned snowflakes. Missing resolved users/roles/channels fail explicitly. `getAsMember()` can return null for a user without a resolved guild membership. Channel access requires a complete resolved channel or a cached one, because partial interaction channel data may omit permission overwrites.

Guild snapshots obtained through the client carry a client reference for command and member REST operations. `getGuildReferenceById(id)` supplies an ID-only reference when caching is off. Its name/roles/members are not populated; it is useful for REST operations, not permission guesses. Event guild access can use such a reference. Models created directly with `fromJson` remain detached until `withClient(client)` is called.

Member permission and role/color helpers require usable cached guild/role state. Use the explicit [permission resolver](ENTITIES.md#permission-masks) with freshly retrieved snapshots when the cache is incomplete. Timeouts, MFA and hierarchy remain separate server-side restrictions. `getGuilds` is still a bounded observed set and entities remain immutable snapshots, rather than JDA's live entity views.

## Migration from the earlier Micup4J alpha

- `getGuildById` and guild entity `get...ById` methods now return an object or null. Move Optional chains to the corresponding `find...ById` methods.
- Reply and defer methods now return `ReplyCallbackAction extends RestAction<InteractionHook>`, replacing `RestAction<Void>`. Existing calls that ignore the result remain usable; explicitly declared Void callbacks must be updated. Raw `respond(...)` keeps its Void action.
- Use `entities.channel.ChannelType` for explicit type declarations and switch statements. The old `entities.ChannelType` is a constant facade, not an enum.
- The older flat event types, `SlashCommand`, basic `CommandOption`, `entities.EmbedBuilder`, and `getOptionValue` remain available. Avoid wildcard imports that bring both legacy and JDA-style class names into scope.
- No-argument `awaitReady()` now waits without the previous 60-second bound; `awaitReady(Duration)` retains explicit timeout behavior.
- Recompile consumers. This is alpha source adaptation, not binary compatibility with previous JARs.

## Remaining compatibility work

Presence/activity configuration, member cache flag adapters, guild-ready and advanced entity events, mentions, nested commands/choices/autocomplete, component builders, fluent message creation/edit actions, uploads, complete webhook actions, user DMs and shard managers still need implementation. Discord-only capabilities will not be silently emulated where Micup has no equivalent.

There is no promise that replacing imports and the builder is sufficient for every existing bot. The compiled example and integration tests are the current coverage evidence. Extend compatibility with runnable source fixtures as more of the roadmap is implemented.

## References

- [JDA builder](https://docs.jda.wiki/net/dv8tion/jda/api/JDABuilder.html)
- [JDA command factories](https://docs.jda.wiki/net/dv8tion/jda/api/interactions/commands/build/Commands.html)
- [JDA reply callback actions](https://docs.jda.wiki/net/dv8tion/jda/api/requests/restaction/interactions/ReplyCallbackAction.html)
- [JDA interaction hooks](https://docs.jda.wiki/net/dv8tion/jda/api/interactions/InteractionHook.html)
- [JDA typed option mappings](https://docs.jda.wiki/net/dv8tion/jda/api/interactions/commands/OptionMapping.html)
- [Micup interactions](https://micup.gg/docs/http-api/interactions/)
- [Micup application commands](https://micup.gg/docs/http-api/application-commands/)
