# Micup4J 0.4 Alpha

**Buttons, select menus, modals and file uploads for Java Micup bots.**

Micup4J `0.4.0-alpha` completes the bot interaction toolkit: validated component builders, typed component and modal events, and multipart file uploads, all in JDA's familiar package layout.

**Tag:** `v0.4.0-alpha`  
**Java:** 21+  
**Release date:** October 27, 2026  
**Status:** Pre-release

## Interactive messages

```java
event.reply("What do you need?")
    .addActionRow(StringSelectMenu.create("menu:topic")
        .addOption("Moderation", "mod").addOption("Levels", "level").build())
    .addActionRow(Button.primary("menu:feedback", "Send feedback"), Button.link("https://micup.gg", "Website"))
    .queue();
```

- `Button`, `ActionRow`, `StringSelectMenu`, `EntitySelectMenu`, `SelectOption` and `Emoji`, immutable and checked against Micup's limits before sending.
- Components on message create/edit actions, interaction replies and webhook/hook actions.
- `onButtonInteraction`, `onStringSelectInteraction`, `onEntitySelectInteraction` with `deferEdit`, `editMessage`, `editComponents` and `editButton`.

## Modals

```java
event.replyModal(Modal.create("report", "Report a user")
    .addActionRow(TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH).build())
    .build()).queue();
```

Modals open from slash commands and components; `onModalInteraction` exposes `getValue(id).getAsString()`.

## File uploads

```java
channel.sendFiles(FileUpload.fromData(Path.of("chart.png")).setDescription("Weekly chart")).setContent("Report").queue();
```

Up to 10 files per new message, sent as `multipart/form-data` with descriptions and spoiler flags.

## Examples

Compiled examples cover [moderation](../../src/examples/java/com/swadedev/micup4j/examples/ModerationExample.java), [command routing and autocomplete](../../src/examples/java/com/swadedev/micup4j/examples/CommandToolkitExample.java), and [interactive menus](../../src/examples/java/com/swadedev/micup4j/examples/InteractiveMenuExample.java).

## Upgrade notes

Use `com.swadedev.micup4j:Micup4J:0.4.0-alpha` with a sibling source checkout or Maven local. Custom `MessageChannel` interface implementations must add `sendFiles`. Component and modal interactions now arrive as typed subclasses that still reach `onInteractionCreate`. See the [upgrade guide](../../UPDATE.md).

## Not offered by Micup

Files in interaction responses, selects and uploads inside modals, and Components v2 layouts are not currently offered by Micup and are not exposed. Editing a message's files and pre-signed large uploads are not implemented.

## Validation

Local tests cover builder limits, JSON round trips, component and modal event routing, callback types 4, 6, 7 and 9, resolved entity selects, modal field parsing and multipart encoding. Examples are compiled in `check`. Nothing in this release was exercised against a live Micup deployment; a remote CI run still needs verification after upload.

## Build

```bash
./gradlew clean build
```

Outputs:

- `Micup4J-0.4.0-alpha.jar`
- `Micup4J-0.4.0-alpha-sources.jar`
- `Micup4J-0.4.0-alpha-javadoc.jar`

## Still planned

Shard management, cross-session rate limit coordination, measured performance, role positions and channel editing, live-server permission fixtures, and voice support remain future work.

Alpha APIs may change. Feedback and reproducible bug reports are welcome; remove credentials and private content before sharing diagnostics.
