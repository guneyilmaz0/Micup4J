package com.swadedev.micup4j.requests.restaction.interactions;

import com.swadedev.micup4j.events.InteractionCreateEvent;
import com.swadedev.micup4j.interactions.modals.Modal;
import com.swadedev.micup4j.requests.RestAction;
import java.util.Objects;

/**
 * Opens a modal as the first answer to an interaction (callback type 9), modelled on JDA's {@code ModalCallbackAction}.
 * The interaction counts as acknowledged once this action runs; the submission arrives as a separate interaction.
 */
public final class ModalCallbackAction extends RestAction<Void> {
    private final Modal modal;

    /**
     * Creates the action.
     *
     * @param event the interaction to answer
     * @param modal the modal
     */
    public ModalCallbackAction(InteractionCreateEvent event, Modal modal) {
        super(() -> event.respond(9, Objects.requireNonNull(modal, "modal").toJson()).submit());
        this.modal = modal;
    }

    /**
     * Returns the modal this action opens.
     *
     * @return the modal this action opens
     */
    public Modal getModal() { return modal; }
}
