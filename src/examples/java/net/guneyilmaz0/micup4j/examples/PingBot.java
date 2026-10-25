package net.guneyilmaz0.micup4j.examples;

import net.guneyilmaz0.micup4j.MicupBuilder;

import net.guneyilmaz0.micup4j.events.*;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;

/** Example entry point. The library itself does not read environment variables. */
public final class PingBot {
    public static void main(String[] args) throws Exception {
        var client = MicupBuilder.createDefault(System.getenv("MICUP_TOKEN"))
            .addEventListeners(new ListenerAdapter() {
                @Override public void onReady(ReadyEvent event) {
                    System.out.println(event.getMicup().getSelfUser().getName() + " is ready!");
                }
                @Override public void onMessageReceived(MessageReceivedEvent event) {
                    if (event.getAuthor().isBot()) return;
                    if (event.getMessage().getContentRaw().equals("!ping")) event.getMessage().reply("Pong!").queue();
                }
                @Override public void onInteractionCreate(InteractionCreateEvent event) {
                    if (event.getInteractionType() == 2 && event.getName().equals("ping")) event.reply("Pong!", true).queue();
                }
            }).build();
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown));
        try (client) {
            client.awaitReady();
            // Register SlashCommand("ping", "Check the bot") once using updateGuildCommands.
            client.termination().join();
        }
    }
}
