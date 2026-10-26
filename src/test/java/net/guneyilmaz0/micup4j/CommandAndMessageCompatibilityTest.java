package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.entities.Message;
import net.guneyilmaz0.micup4j.events.InteractionCreateEvent;
import net.guneyilmaz0.micup4j.events.interaction.command.*;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.interactions.commands.*;
import net.guneyilmaz0.micup4j.interactions.commands.build.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CommandAndMessageCompatibilityTest extends GatewayFixture {
    com.fasterxml.jackson.databind.JsonNode command(int type, String leaf) {
        return Json.parse("""
                {"id":"123","application_id":"42","token":"fixture-token","type":%d,"channel_id":"2","guild_id":"1",
                 "data":{"name":"admin","options":[{"type":2,"name":"users","options":[{"type":1,"name":"find","options":[%s]}]}]}}
                """.formatted(type, leaf));
    }
    @Test void selectedSubcommandExposesLeafOptionsAndRawAccessWithoutHttp() throws Exception {
        start(new ListenerAdapter() {});
        var event = new SlashCommandInteractionEvent(client, 2, command(2, """
                {"type":3,"name":"query","value":"hello"},{"type":4,"name":"limit","value":3}
                """));
        int before = server.getRequestCount();
        assertEquals("admin users find", event.getFullCommandName());
        assertEquals("users", event.getSubcommandGroup()); assertEquals("find", event.getSubcommandName());
        assertEquals(2, event.getOptions().size()); assertEquals(3L, event.getOption("limit").getAsLong());
        assertEquals("hello", event.getOptionValue("query").orElseThrow().asText());
        assertNull(event.getOption("users")); assertEquals(before, server.getRequestCount());
    }
    @Test void autocompleteDispatchBridgesLegacyOnceAndWritesTypedCallback() throws Exception {
        var legacy = new AtomicInteger(); var typed = new AtomicInteger();
        var response = new CompletableFuture<Void>();
        start(new ListenerAdapter() {
            @Override public void onInteractionCreate(InteractionCreateEvent event) { legacy.incrementAndGet(); }
            @Override public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
                typed.incrementAndGet();
                assertEquals("admin users find", event.getFullCommandName());
                assertEquals("query", event.getFocusedOption().getName());
                assertEquals("ap", event.getFocusedOption().getValue());
                event.replyChoices(new Command.Choice("Apple", "apple")).queue(response::complete, response::completeExceptionally);
            }
        });
        client.dispatch("INTERACTION_CREATE", 2, command(4, """
                {"type":3,"name":"query","value":"ap","focused":true}
                """)).get(3, TimeUnit.SECONDS);
        response.get(3, TimeUnit.SECONDS);
        var request = route("/callback"); var body = Json.parse(request.getBody().readUtf8());
        assertEquals("/public/v1/interactions/123/fixture-token/callback", request.getPath());
        assertNull(request.getHeader("Authorization")); assertEquals(8, body.path("type").asInt());
        assertEquals("apple", body.path("data").path("choices").get(0).path("value").asText());
        assertEquals(1, legacy.get()); assertEquals(1, typed.get()); assertTrue(errors.isEmpty());
    }
    @Test void autocompleteAcceptsIncompleteNumbersButRejectsMismatchedSuggestionsAndDuplicateResponses() throws Exception {
        start(new ListenerAdapter() {});
        var event = new CommandAutoCompleteInteractionEvent(client, 2, command(4, """
                {"type":10,"name":"amount","value":"1e-","focused":true}
                """));
        assertEquals("1e-", event.getFocusedOption().getValue());
        assertThrows(IllegalArgumentException.class, () -> event.replyChoiceStrings("wrong"));
        assertThrows(IllegalArgumentException.class, () -> event.replyChoices(Collections.nCopies(26, new Command.Choice("One", 1L))));
        var action = event.replyChoices(new Command.Choice("Half", .5)).addChoices(new Command.Choice("One", 1L));
        action.submit().get(3, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, () -> action.addChoices(new Command.Choice("Two", 2L)));
        assertThrows(ExecutionException.class, () -> event.replyChoices().submit().get(3, TimeUnit.SECONDS));
        assertThrows(ExecutionException.class, () -> event.deferReply().submit().get(3, TimeUnit.SECONDS));
        var body = Json.parse(route("/callback").getBody().readUtf8());
        assertEquals(2, body.path("data").path("choices").size());
    }
    @Test void emptyAutocompleteSuggestionsAreValid() throws Exception {
        start(new ListenerAdapter() {});
        var event = new CommandAutoCompleteInteractionEvent(client, 2, command(4, """
                {"type":3,"name":"query","value":"","focused":true}
                """));
        event.replyChoices().submit().get(3, TimeUnit.SECONDS);
        assertEquals(0, Json.parse(route("/callback").getBody().readUtf8()).path("data").path("choices").size());
    }
    @Test void nestedRegistrationCopiesDefinitionsBeforeExecution() throws Exception {
        start(new ListenerAdapter() {});
        var definition = Commands.slash("admin", "Admin").addSubcommands(new SubcommandData("find", "Find")
                .addOptions(new OptionData(OptionType.STRING, "query", "Query", true, true)));
        var action = client.updateCommands().addCommands(definition);
        definition.addSubcommands(new SubcommandData("delete", "Delete"));
        action.submit().get(3, TimeUnit.SECONDS);
        var body = Json.parse(route("/commands").getBody().readUtf8());
        assertEquals(1, body.get(0).path("options").size());
        assertTrue(body.get(0).path("options").get(0).path("options").get(0).path("autocomplete").asBoolean());
    }
    @Test void commandScopeLimitRejectsOverflowAtomically() throws Exception {
        start(new ListenerAdapter() {});
        var definitions = new ArrayList<SlashCommandData>();
        for (int i = 0; i < 100; i++) definitions.add(Commands.slash("command" + i, "Command"));
        var action = client.updateCommands().addCommands(definitions);
        assertThrows(IllegalArgumentException.class, () -> action.addCommands(Commands.slash("overflow", "Overflow")));
        action.submit().get(3, TimeUnit.SECONDS);
        assertEquals(100, Json.parse(route("/commands").getBody().readUtf8()).size());
    }
    Message message() {
        return Message.fromContext(client, Json.parse("""
                {"id":"3","channel_id":"2","content":"Hello","author":{"id":"5","username":"human"}}
                """), "1");
    }
    @Test void fluentReplyPreservesReferenceGuildContextAndFreezesAfterFirstSubmit() throws Exception {
        start(new ListenerAdapter() {});
        var embed = new EmbedBuilder().setTitle("Example").build();
        int before = server.getRequestCount();
        var action = message().reply("First").setContent("Final").setEmbeds(embed).addEmbeds(embed);
        assertEquals(before, server.getRequestCount());
        var sent = action.submit().get(3, TimeUnit.SECONDS);
        var request = route("/messages"); var body = Json.parse(request.getBody().readUtf8());
        assertEquals("POST", request.getMethod()); assertEquals("Final", body.path("content").asText());
        assertEquals("3", body.path("message_reference").path("message_id").asText());
        assertEquals(2, body.path("embeds").size()); assertEquals("1", sent.getGuild().getId());
        assertThrows(IllegalStateException.class, () -> action.setContent("Late"));
        action.submit().get(3, TimeUnit.SECONDS);
        assertEquals(body, Json.parse(route("/messages").getBody().readUtf8()));
    }
    @Test void messageChannelInterfaceKeepsFluentTypesAndInvalidMutationsAreAtomic() throws Exception {
        start(new ListenerAdapter() {});
        net.guneyilmaz0.micup4j.entities.channel.middleman.MessageChannel channel = message().getChannel();
        var embed = new EmbedBuilder().setTitle("Only embed").build();
        var action = channel.sendMessage("Text").setContent(null).setEmbeds(embed);
        assertThrows(IllegalArgumentException.class, () -> action.addEmbeds(Collections.nCopies(10, embed)));
        action.submit().get(3, TimeUnit.SECONDS);
        var body = Json.parse(route("/messages").getBody().readUtf8());
        assertEquals("", body.path("content").asText()); assertEquals(1, body.path("embeds").size());
        var invalid = channel.sendMessage("Text").setContent("");
        assertThrows(ExecutionException.class, () -> invalid.submit().get(3, TimeUnit.SECONDS));
        // Validation failure before submission can be repaired without issuing an invalid request.
        invalid.setEmbeds(embed).submit().get(3, TimeUnit.SECONDS);
    }
    @Test void partialEditsCanClearEmbedsWithoutResendingUnchangedContent() throws Exception {
        start(new ListenerAdapter() {});
        var embed = new EmbedBuilder().setTitle("Updated").build();
        message().editMessageEmbeds(embed).setEmbeds().submit().get(3, TimeUnit.SECONDS);
        var request = route("/messages/3"); var body = Json.parse(request.getBody().readUtf8());
        assertEquals("PATCH", request.getMethod()); assertFalse(body.has("content")); assertEquals(0, body.path("embeds").size());
        message().editMessage("Updated").setEmbeds(embed).submit().get(3, TimeUnit.SECONDS);
        assertEquals("Updated", Json.parse(route("/messages/3").getBody().readUtf8()).path("content").asText());
    }
}
