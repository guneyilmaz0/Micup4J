package net.guneyilmaz0.micup4j.events.interaction.command;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

/** A slash command interaction (type 2), as JDA's {@code SlashCommandInteractionEvent}. */
public final class SlashCommandInteractionEvent extends net.guneyilmaz0.micup4j.events.InteractionCreateEvent {
    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code INTERACTION_CREATE} payload
     */
    public SlashCommandInteractionEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }

    /**
     * Opens a modal as the first answer.
     *
     * @param modal the modal
     * @return the action
     */
    public net.guneyilmaz0.micup4j.requests.restaction.interactions.ModalCallbackAction replyModal(net.guneyilmaz0.micup4j.interactions.modals.Modal modal) {
        return new net.guneyilmaz0.micup4j.requests.restaction.interactions.ModalCallbackAction(this, java.util.Objects.requireNonNull(modal));
    }
}
