package com.swadedev.micup4j.examples;

import com.swadedev.micup4j.EmbedBuilder;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.MicupBuilder;
import com.swadedev.micup4j.OnlineStatus;
import com.swadedev.micup4j.entities.Activity;
import com.swadedev.micup4j.entities.channel.ChannelType;
import com.swadedev.micup4j.events.interaction.command.SlashCommandInteractionEvent;
import com.swadedev.micup4j.events.message.MessageReceivedEvent;
import com.swadedev.micup4j.events.session.ReadyEvent;
import com.swadedev.micup4j.hooks.ListenerAdapter;
import com.swadedev.micup4j.interactions.commands.OptionMapping;
import com.swadedev.micup4j.interactions.commands.OptionType;
import com.swadedev.micup4j.interactions.commands.build.Commands;

/** Common JDA-style usage. Register commands separately when definitions change. */
public final class JdaStyleBot extends ListenerAdapter {
    public static void main(String[] args) throws InterruptedException {
        Micup client = MicupBuilder.createDefault(System.getenv("MICUP_TOKEN"))
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.customStatus("Ready for commands"))
                .addEventListeners(new JdaStyleBot())
                .build().awaitReady();
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown));
        // Call once when installing/updating definitions, not on every Ready/reconnect:
        // registerCommands(client);
    }

    public static void registerCommands(Micup client) {
        client.updateCommands().addCommands(
                Commands.slash("hello", "Say hello")
                        .addOption(OptionType.STRING, "name", "Your display name", false)
        ).queue();
    }

    @Override public void onReady(ReadyEvent event) {
        System.out.println("Ready as " + event.getJDA().getSelfUser().getName());
    }

    @Override public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getChannelType() == ChannelType.PRIVATE) return;
        if (event.getMessage().getContentRaw().equals("!ping")) event.getMessage().reply("Pong!").queue();
    }

    @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("hello")) return;
        String name = event.getOption("name", "friend", OptionMapping::getAsString);
        event.deferReply().setEphemeral(true).queue(hook ->
                hook.editOriginalEmbeds(new EmbedBuilder()
                        .setTitle("Hello")
                        .setDescription("Welcome, " + name)
                        .setColor(java.awt.Color.BLUE)
                        .setFooter("Micup4J")
                        .build()).queue());
    }
}
