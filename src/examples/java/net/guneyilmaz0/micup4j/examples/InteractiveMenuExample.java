package net.guneyilmaz0.micup4j.examples;

import net.guneyilmaz0.micup4j.events.interaction.ModalInteractionEvent;
import net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent;
import net.guneyilmaz0.micup4j.events.interaction.component.ButtonInteractionEvent;
import net.guneyilmaz0.micup4j.events.interaction.component.StringSelectInteractionEvent;
import net.guneyilmaz0.micup4j.hooks.ListenerAdapter;
import net.guneyilmaz0.micup4j.interactions.components.buttons.Button;
import net.guneyilmaz0.micup4j.interactions.components.selections.StringSelectMenu;
import net.guneyilmaz0.micup4j.interactions.components.text.TextInput;
import net.guneyilmaz0.micup4j.interactions.components.text.TextInputStyle;
import net.guneyilmaz0.micup4j.interactions.modals.Modal;
import java.util.Map;

/** Source-compiled example of buttons, a select menu and a modal. Register a {@code /menu} command separately. */
public final class InteractiveMenuExample extends ListenerAdapter {
    private static final Map<String, String> TOPICS = Map.of("mod", "Moderation commands: /mod kick, ban, timeout",
            "level", "Levels grow as you chat.");

    @Override public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("menu")) return;
        event.reply("What do you need?")
                .addActionRow(StringSelectMenu.create("menu:topic").setPlaceholder("Choose a topic")
                        .addOption("Moderation", "mod").addOption("Levels", "level").build())
                .addActionRow(Button.primary("menu:feedback", "Send feedback"), Button.link("https://micup.gg", "Website"))
                .queue();
    }

    @Override public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("menu:topic")) return;
        // Values come from the user's client: look them up instead of trusting them.
        String text = TOPICS.getOrDefault(event.getValues().getFirst(), "Unknown topic");
        event.editMessage(text).queue();
    }

    @Override public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().equals("menu:feedback")) return;
        event.replyModal(Modal.create("menu:feedback", "Feedback")
                .addActionRow(TextInput.create("text", "Your feedback", TextInputStyle.PARAGRAPH).setRequiredRange(5, 1000).build())
                .build()).queue();
    }

    @Override public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals("menu:feedback")) return;
        int length = event.getValue("text").getAsString().length();
        event.reply("Thanks! Received " + length + " characters.").setEphemeral(true).queue();
    }
}
