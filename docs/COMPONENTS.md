# Components, modals and uploads

This guide covers the interaction toolkit released in `0.4.0-alpha`: buttons, select menus, action rows, modals, their events, and file uploads. Class names and packages follow JDA's under the `com.swadedev.micup4j` prefix.

## Buttons and select menus

```java
event.reply("What do you need?")
    .addActionRow(StringSelectMenu.create("menu:topic").setPlaceholder("Choose a topic")
        .addOption("Moderation", "mod").addOption("Levels", "level").build())
    .addActionRow(Button.primary("menu:feedback", "Send feedback"), Button.link("https://micup.gg", "Website"))
    .queue();
```

Components can be set with `setComponents`, `addComponents`, `setActionRow` and `addActionRow` on `sendMessage`/`reply` actions, `editMessage` actions, interaction replies, and hook `sendMessage`/`editOriginal` actions. Builders are immutable and validate Micup's limits before any request:

| Rule | Limit |
| --- | --- |
| Rows per message | 5 |
| Row content | 1-5 buttons, or exactly one select menu |
| Button label / custom id / link URL | 80 / 1-100 / 512 characters; a label or emoji is required |
| Link button | `http`/`https` URL without credentials, no custom id; clicks are not sent to the bot |
| Select placeholder / options | 150 characters / 1-25 unique values |
| Select values | `0 <= min <= max`, `1 <= max <= 25`; a string select's `max` cannot exceed its options |
| Custom ids | Unique on one message |

`EntitySelectMenu.create(id, SelectTarget.USER)` maps to type 5, `ROLE` to 6, `USER`+`ROLE` to 7 (mentionable) and `CHANNEL` to 8. Channel types and default values are supported. `ActionRow.partitionOf(...)` splits a list of components into valid rows.

A message edit that omits components keeps the stored ones; `setComponents()` with no rows removes them. Components alone do not make a message visible: content, embeds or files are still required for a new message.

## Handling clicks and choices

```java
@Override public void onButtonInteraction(ButtonInteractionEvent event) {
    if (event.getComponentId().equals("vote:yes")) event.editButton(event.getButton().asDisabled()).queue();
}

@Override public void onStringSelectInteraction(StringSelectInteractionEvent event) {
    event.editMessage("Topic: " + event.getValues().getFirst()).queue();
}
```

`onGenericComponentInteractionCreate` runs before the specific callback, and `onInteractionCreate` still receives every interaction. Component events offer:

- `deferEdit()` (callback 6) to acknowledge without changes; edit later with `getHook().editOriginal(...)`.
- `editMessage`, `editMessageEmbeds`, `editComponents` (callback 7) to change the message the component is on. `ButtonInteractionEvent.editButton` replaces just the clicked button.
- `reply`, `deferReply` and `replyModal` as first answers.
- `getMessage()`, `getMessageId()` and `getMessageComponents()` from the payload; unknown component types are skipped.

`EntitySelectInteractionEvent` returns the chosen ids and resolves users, members and roles from the payload without HTTP. Custom ids and values come from the user's client. Micup only checks that they belong to the component, so validate them and never put secrets or permissions in them.

## Modals

```java
event.replyModal(Modal.create("report", "Report a user")
    .addActionRow(TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH).setRequiredRange(10, 500).build())
    .build()).queue();

@Override public void onModalInteraction(ModalInteractionEvent event) {
    String reason = event.getValue("reason").getAsString();
    event.reply("Thanks!").setEphemeral(true).queue();
}
```

A modal opens only as the first answer to a slash command or component interaction. It holds 1-5 rows with one text input each: a label of up to 45 characters, an optional placeholder of up to 100, lengths up to 4000, and an optional prefilled value. Input ids must be unique. Submissions arrive within 900 seconds as `ModalInteractionEvent`; answer them with `reply` or `deferReply`, because Micup offers no message edit callback for them. `getValue(id)` returns `null` for an id the modal did not have and an empty string for an optional field left blank. Both legacy action-row inputs and label-wrapped inputs are read.

## File uploads

```java
channel.sendFiles(FileUpload.fromData(Path.of("chart.png")).setDescription("Weekly chart"))
    .setContent("This week's report").queue();
message.replyFiles(FileUpload.fromData(bytes, "log.txt").asSpoiler()).queue();
```

Files are sent with message creation as `multipart/form-data`: a `payload_json` part and one `files[N]` part per file, with attachment metadata for descriptions and the spoiler flag. A message holds up to 10 files and requires `ATTACH_FILES`; Micup enforces size limits. `FileUpload` reads its data into memory when it is created, so retried and repeated sends reuse the same bytes. File names must not contain path separators or control characters.

## Not supported

Micup does not currently offer the following, so Micup4J does not expose them:

- Files in interaction replies, followups or hook edits. Send files to the channel instead.
- Select menus or file uploads inside modals.
- Components v2 layouts (sections, containers, galleries); the server refuses them with `FEATURE_TEMPORARILY_DISABLED`.

Editing a message's files, retaining existing attachments on edit, and the pre-signed upload flow for large files are not implemented.

## References

- [Message components](https://micup.gg/docs/http-api/messages/#message-components)
- [Interactions and modals](https://micup.gg/docs/http-api/interactions/)
- [Create message multipart body](https://micup.gg/docs/http-api/messages/#create-message)
