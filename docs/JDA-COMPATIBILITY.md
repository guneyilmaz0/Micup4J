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

import java.time.Duration;

class Bot extends ListenerAdapter {
    public static void main(String[] args) throws InterruptedException {
        Micup client = MicupBuilder.createDefault(System.getenv("MICUP_TOKEN"))
            .addEventListeners(new Bot()).build().awaitReady();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.shutdown();
            try {
                client.awaitShutdown(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
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
| `events.StatusChangeEvent`, `events.session.ShutdownEvent`, `ListenerAdapter.onShutdown` | Listeners receive the final `SHUTTING_DOWN -> SHUTDOWN` status change and one `ShutdownEvent` (`getTimeShutdown()`, `getCode()` = 1000, the raw close code as in JDA). After `shutdown()` they run on the event thread after earlier events; after `shutdownNow()` they run on a `micup4j-shutdown` virtual thread after a bounded, best-effort wait for in-flight listeners to finish, possibly after the call returns. Discord's `CloseCode` enum and `getCloseCode()` are intentionally absent |
| `RestAction.queueAfter` cancellation | Cancelling the returned handle invokes neither callback, as in JDA; a request cancelled by other means still reaches the failure callback with `CancellationException`, and `submitAfter` futures still complete with `CancellationException` |
| `onMessageUpdate`, `onMessageDelete`, `onGenericMessage` | Typed `MessageUpdateEvent`, `MessageDeleteEvent` and `GenericMessageEvent`. Update carries the complete current message and no previous content. `MessageDeleteEvent.getContentRaw()` and `getAuthorId()` are Micup-only and nullable: the server omits them for admin, moderation or system deletions |
| `onGuildMemberJoin`, `onGuildMemberRemove`, `GenericGuildMemberEvent` | `GuildMemberRemoveEvent.getMember()` is the pre-removal snapshot or null; `getUser()` is a minimal id-only user when the account is not cached, because the payload carries only the id. In guilds above 250 members, passive sessions receive member add/update/remove only for their own user |
| `events.interaction.command.SlashCommandInteractionEvent` | Routed for command interactions only; legacy `onInteractionCreate` remains available |
| `event.getJDA()` | Alias returning the actual `Micup` session; `getMicup()` remains available |
| `getGuildById`, `getMemberById`, `getRoleById`, cached text-channel lookups | Object or null on a cache miss; string and unsigned-long ID overloads where provided |
| Optional cache access | Explicit `findGuildById`, `findMemberById`, `findRoleById`, `findChannelById` |
| `getId/getIdLong`, `getName`, member/user getters | Added to the modeled entities; unsigned snowflakes retain their full bits |
| `getTextChannelById(...).sendMessage(...).queue()` | Cached text-channel handle with message operations; null if uncached or not text |
| `entities.channel.middleman.MessageChannel` | Interface for the supported message-channel operations; legacy handle implements it |
| `entities.channel.ChannelType.PRIVATE` | Uses Micup channel values; legacy constants remain aliases |
| `Commands.slash(...).addOption(...)`, `OptionData`, subcommands/groups | Mutable definitions with copied children, typed choices and constraints; required options precede optional ones |
| `onCommandAutoCompleteInteraction`, `getFocusedOption`, `replyChoices` | Typed Gateway autocomplete with a lazy type-8 callback, 25-choice limit and shared acknowledgement/deadline handling |
| `getFullCommandName`, `getSubcommandGroup/getSubcommandName` | Selected nested command path; `getOption/getOptions` expose its scalar leaf options |
| `sendMessage/reply(...).setEmbeds(...)`, `editMessage(...).setContent(...)` | Lazy message create/edit action subtypes; setters freeze after first execution |
| `updateCommands().addCommands(...).queue()` | Lazy typed bulk replacement globally or on a client-bound guild |
| `guild.upsertCommand`, `retrieveCommands`, `deleteCommandById` | Real command REST operations, returning typed `Command` values |
| `event.getOption/getOptions` | `OptionMapping` with strings, booleans, numeric conversions and resolved user/member/role/channel access |
| `reply(...).setEphemeral(true)` / `replyEmbeds(...)` | Fluent lazy callback action; completion returns `InteractionHook` |
| `deferReply().queue(hook -> ...)` / `getHook()` | Acknowledgement barrier, original response operations and basic followups |
| `InteractionHook.sendMessage/sendMessageEmbeds`, `editOriginal*`, `editMessageById`, `editMessageEmbedsById`, `deleteMessageById`, `retrieveMessageById`, `setEphemeral`, `getInteraction`, `getJDA` | Return `WebhookMessageCreateAction`/`WebhookMessageEditAction`; the `"@original"` id routes to the original response. `setEphemeral` is ignored on the first send after a defer, because the deferral fixed visibility; an edit keeps the first answer's visibility. `ReplyCallbackAction` also has `setEmbeds`, `addEmbeds` and `addContent`. `Interaction` is a common interface of the interaction events |
| `RestAction.queue(null)`, `queue(success, null)`, `setDefaultSuccess/Failure` | Null callbacks use the configured defaults |
| `RestAction.queueAfter/submitAfter/completeAfter`, `delay`, `onSuccess`, `onErrorMap`, `onErrorFlatMap`, `and`, `zip`, `allOf`, `accumulate` | Implemented; delayed futures are `utils.concurrent.DelayedCompletableFuture`. See [REST.md](REST.md#combinators-and-delays). `complete()`, `completeAfter()` and `awaitShutdown` throw `IllegalStateException` when called on a Micup scheduler thread |
| `Micup.shutdown/shutdownNow/close`, `awaitShutdown(...)`, `Status.SHUTTING_DOWN` | Graceful drain bounded by `RestConfig.shutdownTimeout`; see [REST.md](REST.md#shutdown-drain). Exhaustive `Status` switches need a `SHUTTING_DOWN` case |
| Root `EmbedBuilder`, `Color`, common author/footer overloads, copies and clearing | Micup validation, with immutable built embeds |
| Root `Permission`, `member.hasPermission`, `getPermissions` | JDA-style names mapped to Micup bits, using the existing resolver |
| `getMentions().getUsers/getMembers/getRoles/getChannels` | Active payload mentions, text order, immutable lists and cache-only member/role/channel resolution |
| `member.getColors().getPrimary()` | Highest assigned colored role, including Micup's optional static gradient color |
| `onGuildReady/onGuildJoin/onGuildAvailable/onGuildUnavailable/onGuildLeave` | Ordered lifecycle classification with session identities tracked separately from cache eviction |
| `setStatus/setActivity/setIdle`, `getPresence()` | Actual Identify/opcode 3 presence; common activity names render as Micup custom-status text |
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

The first `hook.sendMessage(...)` after a defer creates the original response with PATCH; later sends create public followups. Set the original response's ephemeral state on the initial reply/defer; `setEphemeral` on the first send after a defer is ignored. Followups are `WebhookMessageCreateAction` values and edits are `WebhookMessageEditAction` values; attachments and components remain future work. Avoid concurrent writes when their order matters; compose actions or wait for completion.

**Micup has no public deferred placeholder.** A public `deferReply()` followed immediately by `hook.retrieveOriginal()` cannot return a message before one exists. Use `editOriginal(...)` or the first `sendMessage(...)` to create it. Micup4J returns an explicit error for this unsupported placeholder flow rather than creating a visible synthetic message. After creating the original, retrieval and deletion are available. Private deferred response retrieval is forwarded to the server, because the documented no-placeholder rule specifically describes public responses.

Hook validity is limited locally to roughly 15 minutes from event receipt, and the server remains authoritative. Tokens may have less remaining validity after transport delays. Initial callbacks still use the original 2.5-second local deadline including listener queue time.

### Options and entity context

`getOption(name)` returns null when absent; the overload `getOption(name, fallback, OptionMapping::getAsString)` supplies a default. Option mappings are detached payload copies. They perform no HTTP request. Boolean access requires a boolean option; integer conversions reject overflow, and ID conversions preserve unsigned snowflakes. Missing resolved users/roles/channels fail explicitly. `getAsMember()` can return null for a user without a resolved guild membership. Channel access requires a complete resolved channel or a cached one, because partial interaction channel data may omit permission overwrites.

Guild snapshots obtained through the client carry a client reference for command and member REST operations. `getGuildReferenceById(id)` supplies an ID-only reference when caching is off. Its name/roles/members are not populated; it is useful for REST operations, not permission guesses. Event guild access can use such a reference. Models created directly with `fromJson` remain detached until `withClient(client)` is called.

Member permission and role/color helpers require usable cached guild/role state. Use the explicit [permission resolver](ENTITIES.md#permission-masks) with freshly retrieved snapshots when the cache is incomplete. Timeouts, MFA and hierarchy remain separate server-side restrictions. `getGuilds` is still a bounded observed set and entities remain immutable snapshots, rather than JDA's live entity views.

## Mentions and BMO-style avatar code

The [BMO-style avatar example](../src/examples/java/net/guneyilmaz0/micup4j/examples/BmoStyleAvatar.java) compiles the familiar `message.getMentions().getMembers()` and `target.getColors().getPrimary()` pattern. The integration suite sends its resulting embed to a local HTTP fixture and verifies the selected user's avatar URL and role color. A [Kotlin source fixture](../src/compatibility/kotlin/JdaSourceCompatibilityTest.kt) checks the original `target.colors.primary` JavaBean property types and compiles the Kotlin mention/guild-ready/avatar flow in the sibling BMO integration project. The library's Java build compiles the Java examples; it does not run Kotlin compilation. To repeat the Kotlin check with a configured sibling BMO checkout:

```bash
cp src/compatibility/kotlin/JdaSourceCompatibilityTest.kt ../BMO-micup/src/test/kotlin/net/guneyilmaz0/bmo/
(cd ../BMO-micup && ./gradlew test)
```

Mention membership comes from the message's active `mentions`, `mention_roles`, and `mention_channels` fields. Text orders those active IDs and removes duplicates; a raw `<@id>` with no active mention entry does not become a notifying user mention. The separate `users` array contains non-notifying references and is not treated as mentions. An implicit active user with no literal token is appended in payload order.

`getUsers()` returns copied partial users from the message. `getMembers()`, `getRoles()` and `getChannels()` resolve only observed cache entries in that guild and skip misses. They never download a roster or invent missing role assignments/overwrites. `isMentioned(Role)` checks the active role ID even on a cache miss. Mention views capture the cache resolution at first access and remain immutable afterwards. A message from a private/group channel cannot supply guild members. Gateway `channel_type` now distinguishes private from group messages and identifies a guild's voice/text channel even when it is not cached.

A Micup bot's initial member list is partial, so a mentioned user may not be a cached member. Explicit lookup is available:

```java
message.getMentions().retrieveMembers().queue(members -> {
    // Complete memberships, in the same user-mention order.
});
```

This lazy helper performs sequential typed HTTP reads for at most 100 active user mentions. It does not populate the Gateway cache, stops on the first error, and propagates cancellation to the active read without starting the next one. In a private channel it returns an empty list without HTTP. Role/color helpers still need complete observed guild roles.

`RoleColors` preserves the primary color and nullable `color_secondary`, including a secondary value of black. It exposes primary/secondary getters and default/solid/gradient checks. No Discord tertiary or holographic style is synthesized. A user without a custom avatar still has a null avatar URL; the example handles that case explicitly.

## Guild lifecycle events

The same wire dispatch is enriched after cache updates and delivered once through the listener adapter. Existing generic listeners still see the original `GUILD_CREATE`/`GUILD_DELETE` type and sequence. New overrides use `events.guild` classes:

| Observed transition | Typed callback |
| --- | --- |
| First available snapshot for an ID listed in the current Ready | `onGuildReady` |
| Available snapshot for an ID not observed in this session | `onGuildJoin` |
| Previously unavailable guild becomes available | `onGuildAvailable` |
| Available guild becomes temporarily unavailable | `onGuildUnavailable` |
| Permanent guild removal | `onGuildLeave` |

`onGenericGuild` also receives these and guild metadata/sync updates. Duplicate available snapshots and `GUILD_SYNC` do not emit Ready or Join again. Resume retains classification. Fresh Ready clears the previous session's identities, allowing a new GuildReady event for each initial guild. An initially unavailable guild emits no false unavailable transition; its later recovery emits GuildAvailable. These classifications require receiving the relevant lifecycle events.

Lifecycle identity storage is capped at Micup's 2,500 guilds per bot session, independently of entity-cache limits. Exceeding that bound fails event processing rather than evicting identity and emitting false joins. A small/disabled cache does not suppress typed guild events. With caching enabled the event holds the cache's bounded snapshot; light mode uses the incoming guild snapshot without storing its children. A leave event holds the pre-removal snapshot, so its name remains readable after the cache entry disappears.

Global `awaitReady` and Ready still indicate authenticated session startup; they do not wait for Micup's subsequent guild burst. This differs from the timing many JDA bots assume. Use GuildReady for per-guild initialization, GuildAvailable for recovery, and register commands only when definitions change. These are observed protocol transitions, not proof that every member/channel in the guild is cached.

## Presence and activities

Builder and runtime presence setters now publish the real Micup presence payload. Common JDA activity factories render as custom-status text; they do not create Discord rich presence. See [presence mapping, saved state, pacing and reconnect rules](PRESENCE.md). An untouched builder preserves saved account state, and status-only updates preserve a saved custom status until an activity is explicitly set or cleared.

## Migration from the earlier Micup4J alpha

- `getGuildById` and guild entity `get...ById` methods now return an object or null. Move Optional chains to the corresponding `find...ById` methods.
- Reply and defer methods now return `ReplyCallbackAction extends RestAction<InteractionHook>`, replacing `RestAction<Void>`. Existing calls that ignore the result remain usable; explicitly declared Void callbacks must be updated. Raw `respond(...)` keeps its Void action.
- Use `entities.channel.ChannelType` for explicit type declarations and switch statements. The old `entities.ChannelType` is a constant facade, not an enum.
- The older flat event types, `SlashCommand`, basic `CommandOption`, `entities.EmbedBuilder`, and `getOptionValue` remain available. Avoid wildcard imports that bring both legacy and JDA-style class names into scope.
- No-argument `awaitReady()` now waits without the previous 60-second bound; `awaitReady(Duration)` retains explicit timeout behavior.
- Recompile consumers. This is alpha source adaptation, not binary compatibility with previous JARs.
- Replace JVM shutdown hooks that only call `client.shutdown()` with `client.shutdown(); client.awaitShutdown(Duration.ofSeconds(10));` so queued REST work can drain before the JVM exits.

## Remaining compatibility work

Streaming/rich-presence metadata, member cache flag adapters, advanced entity events and mention types, component builders, uploads, attachment/component support for webhook actions, user DMs and shard managers still need implementation. Discord-only capabilities will not be silently emulated where Micup has no equivalent.

There is no promise that replacing imports and the builder is sufficient for every existing bot. The compiled example and integration tests are the current coverage evidence. Extend compatibility with runnable source fixtures as more of the roadmap is implemented.

## References

- [JDA builder](https://docs.jda.wiki/net/dv8tion/jda/api/JDABuilder.html)
- [JDA command factories](https://docs.jda.wiki/net/dv8tion/jda/api/interactions/commands/build/Commands.html)
- [JDA reply callback actions](https://docs.jda.wiki/net/dv8tion/jda/api/requests/restaction/interactions/ReplyCallbackAction.html)
- [JDA interaction hooks](https://docs.jda.wiki/net/dv8tion/jda/api/interactions/InteractionHook.html)
- [JDA typed option mappings](https://docs.jda.wiki/net/dv8tion/jda/api/interactions/commands/OptionMapping.html)
- [Micup interactions](https://micup.gg/docs/http-api/interactions/)
- [Micup application commands](https://micup.gg/docs/http-api/application-commands/)
- [Micup client commands](https://micup.gg/docs/gateway/commands/)
- [Micup message mentions](https://micup.gg/docs/http-api/messages/)
- [JDA guild readiness](https://docs.jda.wiki/net/dv8tion/jda/api/events/guild/GuildReadyEvent.html)

See [advanced commands and fluent messages](COMMANDS.md) and the [compiled toolkit example](../src/examples/java/net/guneyilmaz0/micup4j/examples/CommandToolkitExample.java) for the supported builder chains.
