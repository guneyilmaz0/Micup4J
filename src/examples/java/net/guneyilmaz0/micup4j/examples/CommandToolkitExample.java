package net.guneyilmaz0.micup4j.examples;

import net.guneyilmaz0.micup4j.*;
import net.guneyilmaz0.micup4j.events.interaction.command.*;
import net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.interactions.commands.*;
import net.guneyilmaz0.micup4j.interactions.commands.build.*;
import java.util.List;
import java.util.Locale;

/** Source-compiled example; registration is an explicit installation/update step. */
public final class CommandToolkitExample extends ListenerAdapter {
    private static final List<String> TOPICS = List.of("commands", "permissions", "presence");

    public static void register(Micup client) {
        // Replaces the global command set. Include every definition that should remain installed.
        client.updateCommands().addCommands(Commands.slash("help", "Find bot help")
                .addSubcommandGroups(new SubcommandGroupData("topics", "Help topics")
                        .addSubcommands(new SubcommandData("find", "Find a help topic")
                                .addOptions(new OptionData(OptionType.STRING, "topic", "Topic name", true, true)
                                        .setMinLength(1).setMaxLength(100)))))
                .queue();
    }

    @Override public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getFullCommandName().equals("help topics find") || !event.getFocusedOption().getName().equals("topic")) return;
        String prefix = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        event.replyChoices(TOPICS.stream().filter(topic -> topic.startsWith(prefix)).limit(25)
                .map(topic -> new Command.Choice(topic, topic)).toList()).queue();
    }

    @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getFullCommandName().equals("help topics find")) return;
        String topic = event.getOption("topic").getAsString();
        // Autocomplete suggestions are hints; users can still enter another value.
        event.reply(TOPICS.contains(topic) ? "Help topic: " + topic : "Unknown topic").setEphemeral(true).queue();
    }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.getMessage().getContentRaw().equals("!help")) return;
        event.getMessage().reply("Available topics").setEmbeds(new EmbedBuilder()
                .setTitle("Help").setDescription(String.join(", ", TOPICS)).build()).queue();
    }
}
