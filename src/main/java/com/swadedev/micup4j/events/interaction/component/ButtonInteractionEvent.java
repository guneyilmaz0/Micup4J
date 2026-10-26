package com.swadedev.micup4j.events.interaction.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.interactions.components.ActionRow;
import com.swadedev.micup4j.interactions.components.ItemComponent;
import com.swadedev.micup4j.interactions.components.buttons.Button;

/** A button click, modelled on JDA's {@code ButtonInteractionEvent}. */
public final class ButtonInteractionEvent extends GenericComponentInteractionCreateEvent {
    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code INTERACTION_CREATE} payload
     */
    public ButtonInteractionEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }

    /**
     * Returns the clicked button, found on the message, or {@code null} when the payload lacks it.
     *
     * @return the clicked button, found on the message, or {@code null} when the payload lacks it
     */
    public Button getButton() { return getComponent() instanceof Button button ? button : null; }

    /**
     * Replaces the clicked button on the message, keeping every other component (callback type 7).
     *
     * @param button the replacement, or {@code null} to remove it
     * @return the action
     * @throws IllegalStateException if the message components are not in the payload
     */
    public com.swadedev.micup4j.requests.restaction.interactions.MessageEditCallbackAction editButton(Button button) {
        var rows = new java.util.ArrayList<ActionRow>();
        boolean found = false;
        for (ActionRow row : getMessageComponents()) {
            var items = new java.util.ArrayList<ItemComponent>();
            for (ItemComponent item : row.getComponents()) {
                if (item instanceof Button existing && getComponentId().equals(existing.getId())) { found = true; if (button != null) items.add(button); }
                else items.add(item);
            }
            if (!items.isEmpty()) rows.add(ActionRow.of(items));
        }
        if (!found) throw new IllegalStateException("The clicked button is not in the message payload");
        return editComponents(rows);
    }
}
