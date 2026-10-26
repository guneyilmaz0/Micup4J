package net.guneyilmaz0.micup4j.examples;

import net.guneyilmaz0.micup4j.EmbedBuilder;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.MicupBuilder;
import net.guneyilmaz0.micup4j.OnlineStatus;
import net.guneyilmaz0.micup4j.entities.Activity;
import net.guneyilmaz0.micup4j.entities.channel.ChannelType;
import net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent;
import net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent;
import net.guneyilmaz0.micup4j.events.session.ReadyEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.interactions.commands.OptionMapping;
import net.guneyilmaz0.micup4j.interactions.commands.OptionType;
import net.guneyilmaz0.micup4j.interactions.commands.build.Commands;

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
