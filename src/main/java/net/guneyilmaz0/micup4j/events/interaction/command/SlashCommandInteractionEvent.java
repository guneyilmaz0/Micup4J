package net.guneyilmaz0.micup4j.events.interaction.command;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;

public final class SlashCommandInteractionEvent extends net.guneyilmaz0.micup4j.events.InteractionCreateEvent {
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
