# Advanced commands and fluent messages

These APIs are part of `0.3.0-alpha`. They implement a supported subset of JDA's source conventions over the Micup protocol. Recompile consuming applications after updating the checkout.

## Define options and nested commands

```java
client.updateCommands().addCommands(
    Commands.slash("help", "Find bot help")
        .addSubcommandGroups(new SubcommandGroupData("topics", "Help topics")
            .addSubcommands(new SubcommandData("find", "Find a help topic")
                .addOptions(new OptionData(OptionType.STRING, "topic", "Topic name", true, true)
                    .setMinLength(1).setMaxLength(100))))
).queue();
```

Import command builders from `com.swadedev.micup4j.interactions.commands.build` and `OptionType`/`Command` from `interactions.commands`. Existing basic `SlashCommand` and legacy option-type calls remain supported.

A command contains scalar options **or** subcommands/groups. A group contains 1–25 subcommands; a subcommand contains up to 25 scalar options. No deeper nesting is supported. Names are unique among siblings, and required scalar options precede optional options. Structural options have no `required` field. Added options/subcommands/groups are copied immediately; finish configuring a child before adding it. Registration actions copy complete definitions when added. Invalid additions leave the previous definition intact.

`OptionData` supports:

- `addChoice(name, String/long/double)` and `addChoices(Command.Choice...)` for string, integer and number options, up to 25 choices. Number options also accept integer choices.
- `setAutoComplete(true)` for those same types. Fixed choices and autocomplete cannot be combined.
- `setMinValue/setMaxValue(long)` for integer or number options, and double overloads for number options. Values must be finite and within ±9,007,199,254,740,991; minimum cannot exceed maximum.
- `setMinLength(0..6000)` and `setMaxLength(1..6000)` for strings, with minimum no greater than maximum.
- `setChannelTypes(ChannelType...)` for channel options, up to 25 entries. Unknown channel types are rejected.

Choice labels contain 1–100 Unicode code points; string choice values contain at most 100. Boolean, user, channel and role options cannot have choices or autocomplete. Micup does not support mentionable or attachment option types, so those are not exposed as supported definitions.

Bulk registration replaces the entire scope. Include all commands that should remain installed and register only when definitions change, rather than on every reconnect. Micup limits a scope to 100 commands and applies serialized size and deployment feature limits on the server; guild writes are limited to two requests per minute per application/guild.

## Read the selected command path

```java
@Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    if (!event.getFullCommandName().equals("help topics find")) return;
    String topic = event.getOption("topic").getAsString();
    event.reply("Help topic: " + topic).setEphemeral(true).queue();
}
```

`getSubcommandGroup()` and `getSubcommandName()` return null when absent. `getOptions()`, `getOption(name)` and legacy `getOptionValue(name)` access scalar options on the selected command/subcommand. Resolved entities retain the same local-only lookup behavior as basic command options.

## Answer autocomplete queries

```java
@Override public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
    if (!event.getFullCommandName().equals("help topics find")) return;
    String query = event.getFocusedOption().getValue();
    event.replyChoices(List.of("commands", "permissions", "presence").stream()
        .filter(topic -> topic.startsWith(query))
        .map(topic -> new Command.Choice(topic, topic))
        .toList()).queue();
}
```

`AutoCompleteQuery` exposes `getName()`, `getValue()` and `getType()`. The query is unvalidated text: a numeric option may contain incomplete input such as `1e-`. Reply with values of the declared type, using `replyChoices`, `replyChoiceStrings`, `replyChoiceLongs` or `replyChoiceDoubles`. An empty choice list is valid. Suggestions are hints; validate the final slash command input separately.

The lazy `AutoCompleteCallbackAction` extends `RestAction<Void>` and can add choices before execution. It sends callback type 8, with at most 25 correctly typed choices. The initial-response deadline and single-acknowledgement guard apply; autocomplete cannot be deferred. The legacy `onInteractionCreate` callback and typed `onCommandAutoCompleteInteraction` callback each run once per event. Use one callback to answer, since both share the acknowledgement guard.

## Build messages fluently

```java
channel.sendMessage("Summary").setEmbeds(embed).queue();
message.reply("Summary").addEmbeds(embed).queue();
message.editMessage("Updated summary").setEmbeds(embed).queue();
message.editMessageEmbeds(embed).setContent("Updated summary").queue();
```

Channel send methods and message reply methods return `MessageCreateAction`; message edit methods return `MessageEditAction`. Both extend `RestAction<Message>`. Existing source assignments to `RestAction<Message>` remain valid, but these return-type changes require recompiling consumers because JVM method descriptors changed.

`setContent` replaces content (`null` clears it), `setEmbeds` replaces embeds, and `addEmbeds` appends them. Embed setters accept varargs or collections and enforce the 10-embed limit before changing the payload. `setEmbeds()` explicitly clears embeds. Create requests need nonblank content or at least one embed. Partial edit requests omit untouched fields; the server validates the resulting message. Replies preserve their `message_reference`, and returned messages retain guild context even when the HTTP response omits `guild_id`.

Actions are lazy. Setters stop accepting changes after the first valid execution; repeated execution sends the same frozen payload. Validation failures before execution can be repaired. Cancellation and errors use the existing `RestAction` behavior. These fluent actions currently cover ordinary channel messages; webhook actions are described in the [compatibility guide](JDA-COMPATIBILITY.md); uploads and components remain open roadmap work.

## Verification and references

[CommandToolkitExample](../src/examples/java/com/swadedev/micup4j/examples/CommandToolkitExample.java) is compiled by the normal SDK check. Java fixtures verify nested registration, typed autocomplete dispatch/callback payloads, invalid definition rollback, lazy message requests, reply references and partial edits. The [Kotlin source fixture](../src/compatibility/kotlin/JdaSourceCompatibilityTest.kt) covers the same familiar chains in the BMO composite build; see the [migration guide](JDA-COMPATIBILITY.md) for its manual test procedure.

Protocol rules follow [Micup application commands](https://micup.gg/docs/http-api/application-commands/) and [Micup interactions](https://micup.gg/docs/http-api/interactions/). Method conventions are based on the official JDA documentation for [OptionData](https://docs.jda.wiki/net/dv8tion/jda/api/interactions/commands/build/OptionData.html) and [CommandAutoCompleteInteractionEvent](https://docs.jda.wiki/net/dv8tion/jda/api/events/interaction/command/CommandAutoCompleteInteractionEvent.html). Local fixtures do not establish live-server qualification or performance parity.
