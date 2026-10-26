package net.guneyilmaz0.micup4j;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.entities.emoji.Emoji;
import net.guneyilmaz0.micup4j.events.GenericEvent;
import net.guneyilmaz0.micup4j.events.interaction.ModalInteractionEvent;
import net.guneyilmaz0.micup4j.events.interaction.component.ButtonInteractionEvent;
import net.guneyilmaz0.micup4j.events.interaction.component.EntitySelectInteractionEvent;
import net.guneyilmaz0.micup4j.events.interaction.component.StringSelectInteractionEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.interactions.components.ActionRow;
import net.guneyilmaz0.micup4j.interactions.components.buttons.Button;
import net.guneyilmaz0.micup4j.interactions.components.buttons.ButtonStyle;
import net.guneyilmaz0.micup4j.interactions.components.selections.EntitySelectMenu;
import net.guneyilmaz0.micup4j.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.guneyilmaz0.micup4j.interactions.components.selections.SelectOption;
import net.guneyilmaz0.micup4j.interactions.components.selections.StringSelectMenu;
import net.guneyilmaz0.micup4j.interactions.components.text.TextInput;
import net.guneyilmaz0.micup4j.interactions.components.text.TextInputStyle;
import net.guneyilmaz0.micup4j.interactions.modals.Modal;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.utils.FileUpload;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ComponentsTest extends GatewayFixture {
    private final AtomicInteger sequence = new AtomicInteger(1);
    private final BlockingQueue<GenericEvent> received = new LinkedBlockingQueue<>();

    @Test void buttonsAndRowsEnforceMicupLimits() {
        var button = Button.success("join", "Katıl").withEmoji(Emoji.fromUnicode("✅"));
        assertEquals(Json.parse("{\"type\":2,\"style\":3,\"disabled\":false,\"label\":\"Katıl\",\"emoji\":{\"name\":\"✅\",\"animated\":false,\"id\":null},\"custom_id\":\"join\"}"),
                button.toJson());
        assertTrue(button.asDisabled().isDisabled());
        var link = Button.link("https://micup.gg/rules", "Rules");
        assertNull(link.getId());
        assertFalse(link.toJson().has("custom_id"));
        assertThrows(IllegalArgumentException.class, () -> Button.primary("id", "x".repeat(81)));
        assertThrows(IllegalArgumentException.class, () -> Button.primary("x".repeat(101), "Label"));
        assertThrows(IllegalArgumentException.class, () -> Button.primary("id", ""));
        assertThrows(IllegalArgumentException.class, () -> Button.link("ftp://micup.gg", "Rules"));
        assertThrows(IllegalArgumentException.class, () -> Button.link("https://user:pass@micup.gg", "Rules"));
        assertThrows(IllegalArgumentException.class, () -> link.withStyle(ButtonStyle.PRIMARY));
        assertEquals(button, Button.fromJson(button.toJson()));

        var menu = StringSelectMenu.create("help").setPlaceholder("Pick").addOption("Moderation", "mod").addOption("Levels", "level", "XP").build();
        assertThrows(IllegalArgumentException.class, () -> ActionRow.of(button, menu));
        assertThrows(IllegalArgumentException.class, () -> ActionRow.of(Collections.nCopies(6, Button.primary("a", "A"))));
        assertThrows(IllegalArgumentException.class, () -> ActionRow.of());
        var rows = ActionRow.partitionOf(List.of(Button.primary("1", "1"), Button.primary("2", "2"), Button.primary("3", "3"),
                Button.primary("4", "4"), Button.primary("5", "5"), Button.primary("6", "6"), menu));
        assertEquals(List.of(5, 1, 1), rows.stream().map(row -> row.getComponents().size()).toList());
        assertThrows(IllegalArgumentException.class, () -> ActionRow.toMessageJson(List.of(ActionRow.of(Button.primary("dup", "A")), ActionRow.of(Button.primary("dup", "B")))));
        assertThrows(IllegalArgumentException.class, () -> ActionRow.toMessageJson(Collections.nCopies(6, ActionRow.of(link))));
        var input = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH).build();
        assertThrows(IllegalArgumentException.class, () -> ActionRow.toMessageJson(List.of(ActionRow.of(input))));
        assertTrue(rows.getFirst().asDisabled().getButtons().stream().allMatch(Button::isDisabled));
    }

    @Test void selectMenusAndModalsValidateBeforeSubmission() {
        var menu = StringSelectMenu.create("pick").addOptions(SelectOption.of("A", "a"), SelectOption.of("B", "b")).setRequiredRange(1, 2)
                .setDefaultValues("b").build();
        var json = menu.toJson();
        assertEquals(3, json.path("type").asInt());
        assertEquals(2, json.path("max_values").asInt());
        assertTrue(json.path("options").get(1).path("default").asBoolean());
        assertEquals(menu, StringSelectMenu.fromJson(json));
        assertThrows(IllegalArgumentException.class, () -> StringSelectMenu.create("pick").addOption("A", "a").setMaxValues(2).build());
        assertThrows(IllegalArgumentException.class, () -> StringSelectMenu.create("pick").addOption("A", "a").addOption("B", "a").build());
        assertThrows(IllegalArgumentException.class, () -> StringSelectMenu.create("pick").build());
        assertThrows(IllegalArgumentException.class, () -> StringSelectMenu.create("pick").addOption("A", "a").setPlaceholder("x".repeat(151)));

        assertEquals(5, EntitySelectMenu.create("u", SelectTarget.USER).build().getTypeRaw());
        assertEquals(6, EntitySelectMenu.create("r", SelectTarget.ROLE).build().getTypeRaw());
        assertEquals(7, EntitySelectMenu.create("m", SelectTarget.USER, SelectTarget.ROLE).build().getTypeRaw());
        var channels = EntitySelectMenu.create("c", SelectTarget.CHANNEL).setChannelTypes(net.guneyilmaz0.micup4j.entities.channel.ChannelType.TEXT)
                .setDefaultValues(EntitySelectMenu.DefaultValue.channel("8")).build();
        assertEquals(8, channels.getTypeRaw());
        assertEquals("channel", channels.toJson().path("default_values").get(0).path("type").asText());
        assertEquals(channels, EntitySelectMenu.fromJson(channels.toJson()));
        assertThrows(IllegalArgumentException.class, () -> EntitySelectMenu.create("x", SelectTarget.USER, SelectTarget.CHANNEL).build());
        assertThrows(IllegalArgumentException.class, () -> EntitySelectMenu.create("x", SelectTarget.USER).setDefaultValues(EntitySelectMenu.DefaultValue.role("2")).build());

        var modal = Modal.create("report", "Report a user")
                .addActionRow(TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH).setRequiredRange(10, 500).setPlaceholder("What happened?").build())
                .addActionRow(TextInput.create("link", "Message link", TextInputStyle.SHORT).setRequired(false).build()).build();
        var modalJson = modal.toJson();
        assertEquals("report", modalJson.path("custom_id").asText());
        var field = modalJson.path("components").get(0).path("components").get(0);
        assertEquals(4, field.path("type").asInt());
        assertEquals(2, field.path("style").asInt());
        assertEquals(10, field.path("min_length").asInt());
        assertFalse(modalJson.path("components").get(1).path("components").get(0).path("required").asBoolean());
        assertThrows(IllegalArgumentException.class, () -> Modal.create("m", "x".repeat(46)).addActionRow(TextInput.create("a", "A", TextInputStyle.SHORT).build()).build());
        assertThrows(IllegalArgumentException.class, () -> Modal.create("m", "Title").build());
        assertThrows(IllegalArgumentException.class, () -> Modal.create("m", "Title").addActionRow(Button.primary("b", "B")).build());
        assertThrows(IllegalArgumentException.class, () -> Modal.create("m", "Title").addActionRow(TextInput.create("a", "A", TextInputStyle.SHORT).build())
                .addActionRow(TextInput.create("a", "B", TextInputStyle.SHORT).build()).build());
        assertThrows(IllegalArgumentException.class, () -> TextInput.create("a", "A", TextInputStyle.SHORT).setRequiredRange(5, 2).build());
        assertThrows(IllegalArgumentException.class, () -> TextInput.create("a", "x".repeat(46), TextInputStyle.SHORT).build());
    }

    void dispatchInteraction(String id, int type, String data, String extra) {
        sockets.peek().send("{\"op\":0,\"t\":\"INTERACTION_CREATE\",\"s\":" + sequence.incrementAndGet() + ",\"d\":{\"id\":\"" + id
                + "\",\"application_id\":\"42\",\"token\":\"tok" + id + "\",\"type\":" + type + ",\"guild_id\":\"1\",\"channel_id\":\"2\","
                + "\"member\":{\"user\":{\"id\":\"4\",\"username\":\"ada\"},\"roles\":[]},\"data\":" + data + extra + "}}");
    }

    <T> T next(Class<T> type) throws Exception {
        GenericEvent event = received.poll(3, TimeUnit.SECONDS);
        assertNotNull(event, "Missing " + type.getSimpleName());
        return assertInstanceOf(type, event);
    }

    JsonNode callback(String id) throws Exception {
        RecordedRequest request = route("/interactions/" + id + "/tok" + id + "/callback");
        return Json.parse(request.getBody().readUtf8());
    }

    static final String MESSAGE = ",\"message\":{\"id\":\"77\",\"channel_id\":\"2\",\"content\":\"Pick one\",\"author\":{\"id\":\"1\",\"username\":\"bot\",\"bot\":true},"
            + "\"components\":[{\"type\":1,\"components\":[{\"type\":2,\"style\":3,\"label\":\"Join\",\"custom_id\":\"join\",\"disabled\":false},"
            + "{\"type\":2,\"style\":4,\"label\":\"Leave\",\"custom_id\":\"leave\",\"disabled\":false}]},"
            + "{\"type\":1,\"components\":[{\"type\":3,\"custom_id\":\"help\",\"min_values\":1,\"max_values\":1,\"disabled\":false,"
            + "\"options\":[{\"label\":\"Moderation\",\"value\":\"mod\"},{\"label\":\"Levels\",\"value\":\"level\"}]}]},"
            + "{\"type\":1,\"components\":[{\"type\":99,\"custom_id\":\"future\"}]}]}";

    @Test void componentInteractionsRouteToTypedCallbacksAndAnswerWithEdits() throws Exception {
        start(new ListenerAdapter() {
            @Override public void onButtonInteraction(ButtonInteractionEvent event) { received.add(event); }
            @Override public void onStringSelectInteraction(StringSelectInteractionEvent event) { received.add(event); }
            @Override public void onEntitySelectInteraction(EntitySelectInteractionEvent event) { received.add(event); }
            @Override public void onModalInteraction(ModalInteractionEvent event) { received.add(event); }
        });

        dispatchInteraction("10", 3, "{\"custom_id\":\"join\",\"component_type\":2}", MESSAGE);
        var click = next(ButtonInteractionEvent.class);
        assertEquals("join", click.getComponentId());
        assertEquals("Join", click.getButton().getLabel());
        assertEquals("77", click.getMessageId());
        assertEquals("Pick one", click.getMessage().getContentRaw());
        assertEquals(2, click.getMessageComponents().size(), "Unknown component types are skipped");
        click.editButton(click.getButton().asDisabled()).submit().get(3, TimeUnit.SECONDS);
        var edit = callback("10");
        assertEquals(7, edit.path("type").asInt());
        var components = edit.path("data").path("components");
        assertTrue(components.get(0).path("components").get(0).path("disabled").asBoolean());
        assertFalse(components.get(0).path("components").get(1).path("disabled").asBoolean());
        assertEquals("help", components.get(1).path("components").get(0).path("custom_id").asText());
        assertThrows(java.util.concurrent.ExecutionException.class, () -> click.deferEdit().submit().get(3, TimeUnit.SECONDS),
                "Only one initial response is accepted");

        dispatchInteraction("11", 3, "{\"custom_id\":\"leave\",\"component_type\":2}", MESSAGE);
        next(ButtonInteractionEvent.class).deferEdit().submit().get(3, TimeUnit.SECONDS);
        var deferred = callback("11");
        assertEquals(6, deferred.path("type").asInt());
        assertFalse(deferred.has("data"));

        dispatchInteraction("12", 3, "{\"custom_id\":\"help\",\"component_type\":3,\"values\":[\"level\"]}", MESSAGE);
        var select = next(StringSelectInteractionEvent.class);
        assertEquals(List.of("level"), select.getValues());
        assertEquals("Levels", select.getSelectedOptions().getFirst().getLabel());
        select.reply("Levels it is").setEphemeral(true).setActionRow(Button.primary("again", "Again")).submit().get(3, TimeUnit.SECONDS);
        var reply = callback("12");
        assertEquals(4, reply.path("type").asInt());
        assertEquals(64, reply.path("data").path("flags").asInt());
        assertEquals("again", reply.path("data").path("components").get(0).path("components").get(0).path("custom_id").asText());

        dispatchInteraction("13", 3, "{\"custom_id\":\"who\",\"component_type\":5,\"values\":[\"4\"],\"resolved\":{\"users\":{\"4\":{\"id\":\"4\",\"username\":\"ada\"}},"
                + "\"members\":{\"4\":{\"roles\":[]}}}}", "");
        var users = next(EntitySelectInteractionEvent.class);
        assertEquals(List.of("4"), users.getValues());
        assertEquals("ada", users.getUsers().getFirst().getName());
        assertEquals("4", users.getMembers().getFirst().getId());
        assertTrue(users.getRoles().isEmpty());
        var modal = Modal.create("report", "Report").addActionRow(TextInput.create("reason", "Reason", TextInputStyle.SHORT).build()).build();
        users.replyModal(modal).submit().get(3, TimeUnit.SECONDS);
        var opened = callback("13");
        assertEquals(9, opened.path("type").asInt());
        assertEquals("report", opened.path("data").path("custom_id").asText());

        dispatchInteraction("14", 5, "{\"custom_id\":\"report\",\"components\":[{\"type\":1,\"components\":[{\"type\":4,\"custom_id\":\"reason\",\"value\":\"spam\"}]},"
                + "{\"type\":18,\"component\":{\"type\":4,\"custom_id\":\"link\",\"value\":\"\"}}]}", "");
        var submit = next(ModalInteractionEvent.class);
        assertEquals("report", submit.getModalId());
        assertEquals("spam", submit.getValue("reason").getAsString());
        assertEquals("", submit.getValue("link").getAsString());
        assertNull(submit.getValue("missing"));
        submit.reply("Thanks").submit().get(3, TimeUnit.SECONDS);
        assertEquals(4, callback("14").path("type").asInt());
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test void slashCommandsOpenModalsAndMessagesCarryComponents() throws Exception {
        start(new ListenerAdapter() {
            @Override public void onSlashCommandInteraction(net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent event) { received.add(event); }
        });
        dispatchInteraction("20", 2, "{\"id\":\"5\",\"name\":\"report\",\"type\":1}", "");
        var slash = next(net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent.class);
        slash.replyModal(Modal.create("m", "Title").addActionRow(TextInput.create("a", "A", TextInputStyle.SHORT).build()).build()).submit().get(3, TimeUnit.SECONDS);
        assertEquals(9, callback("20").path("type").asInt());

        client.getMessageChannelById("2").sendMessage("Menu").setActionRow(Button.primary("a", "A"), Button.link("https://micup.gg", "Site"))
                .addActionRow(StringSelectMenu.create("s").addOption("One", "1").build()).submit().get(3, TimeUnit.SECONDS);
        var sent = Json.parse(route("/channels/2/messages").getBody().readUtf8());
        assertEquals(2, sent.path("components").size());
        assertEquals(5, sent.path("components").get(0).path("components").get(1).path("style").asInt());
        assertThrows(IllegalArgumentException.class, () -> client.getMessageChannelById("2").sendMessage("x")
                .setActionRow(Button.primary("a", "A")).addActionRow(Button.primary("a", "B")));
    }

    @Test void filesAreSentAsMultipartWithAttachmentMetadata() throws Exception {
        start(new ListenerAdapter() {});
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2};
        var message = client.getMessageChannelById("2").sendFiles(FileUpload.fromData(png, "chart.png").setDescription("Weekly chart").asSpoiler(),
                FileUpload.fromData("hello".getBytes(StandardCharsets.UTF_8), "notes.txt")).setContent("Report").submit().get(3, TimeUnit.SECONDS);
        assertEquals("99", message.getId());
        var request = route("/channels/2/messages");
        String type = request.getHeader("Content-Type");
        assertTrue(type.startsWith("multipart/form-data; boundary="), type);
        String boundary = type.substring(type.indexOf('=') + 1);
        byte[] body = request.getBody().readByteArray();
        String text = new String(body, StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("name=\"files[0]\"; filename=\"chart.png\"\r\nContent-Type: image/png"), text);
        assertTrue(text.contains("name=\"files[1]\"; filename=\"notes.txt\""));
        assertTrue(text.contains(new String(png, StandardCharsets.ISO_8859_1)), "File bytes are sent unchanged");
        assertTrue(text.endsWith("--" + boundary + "--\r\n"));
        int start = text.indexOf("\r\n\r\n") + 4;
        var payload = Json.parse(new String(body, start, text.indexOf("\r\n--" + boundary, start) - start, StandardCharsets.UTF_8));
        assertEquals("Report", payload.path("content").asText());
        var attachments = payload.path("attachments");
        assertEquals(0, attachments.get(0).path("id").asInt());
        assertEquals("Weekly chart", attachments.get(0).path("description").asText());
        assertEquals(8, attachments.get(0).path("flags").asInt());
        assertFalse(attachments.get(1).has("flags"));

        client.getMessageChannelById("2").sendFiles(FileUpload.fromData(png, "only.png")).submit().get(3, TimeUnit.SECONDS);
        assertTrue(route("/channels/2/messages").getHeader("Content-Type").startsWith("multipart/"), "Files alone are a valid message");
        assertThrows(IllegalArgumentException.class, () -> client.getMessageChannelById("2").sendFiles(Collections.nCopies(11, FileUpload.fromData(png, "a.png"))));
        assertThrows(IllegalArgumentException.class, () -> client.getMessageChannelById("2").sendFiles(List.of()));
        assertThrows(IllegalArgumentException.class, () -> FileUpload.fromData(png, "../secret.png"));
        assertThrows(IllegalArgumentException.class, () -> FileUpload.fromData(png, "line\nbreak.png"));
    }
}
