package net.guneyilmaz0.micup4j.events.interaction.component;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Message;
import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import net.guneyilmaz0.micup4j.interactions.components.ActionRow;
import net.guneyilmaz0.micup4j.interactions.components.ItemComponent;
import net.guneyilmaz0.micup4j.interactions.components.LayoutComponent;
import net.guneyilmaz0.micup4j.interactions.modals.Modal;
import net.guneyilmaz0.micup4j.requests.restaction.interactions.MessageEditCallbackAction;
import net.guneyilmaz0.micup4j.requests.restaction.interactions.ModalCallbackAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A button click or select menu choice on a message the bot wrote (interaction type 3). Modelled on JDA's
 * {@code GenericComponentInteractionCreateEvent}. The custom id and values are untrusted user input.
 */
public class GenericComponentInteractionCreateEvent extends net.guneyilmaz0.micup4j.events.InteractionCreateEvent {
    private final int componentType;
    private volatile Message message;
    private volatile List<ActionRow> rows;

    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code INTERACTION_CREATE} payload
     * @throws IllegalArgumentException if the payload has no custom id
     */
    public GenericComponentInteractionCreateEvent(Micup client, long sequence, JsonNode data) {
        super(client, sequence, data);
        componentType = rawData().path("data").path("component_type").asInt();
        if (getCustomId().isEmpty()) throw new IllegalArgumentException("Component interaction has no custom id");
    }

    /**
     * Returns the custom id of the used component.
     *
     * @return the custom id of the used component
     */
    public String getComponentId() { return getCustomId(); }
    /**
     * Returns the raw component type.
     *
     * @return the raw component type
     */
    public int getComponentTypeRaw() { return componentType; }

    /**
     * Returns the message the component is on, or {@code null} when the payload has none.
     *
     * @return the message the component is on, or {@code null} when the payload has none
     */
    public Message getMessage() {
        Message current = message;
        if (current == null && rawData().path("message").isObject())
            message = current = Message.fromContext(getMicup(), rawData().path("message"), getGuildId());
        return current;
    }

    /**
     * Returns the id of the message the component is on, or {@code null}.
     *
     * @return the id of the message the component is on, or {@code null}
     */
    public String getMessageId() {
        JsonNode node = rawData().path("message").path("id");
        return node.isTextual() ? node.asText() : null;
    }

    /**
     * Returns the action rows of the message, parsed from the payload; empty when absent.
     *
     * @return the action rows of the message, parsed from the payload; empty when absent
     */
    public List<ActionRow> getMessageComponents() {
        List<ActionRow> current = rows;
        if (current == null) {
            var parsed = new ArrayList<ActionRow>();
            for (JsonNode row : rawData().path("message").path("components")) {
                try { var value = ActionRow.fromJson(row); if (value != null) parsed.add(value); }
                catch (IllegalArgumentException ignored) { /* Rows a client may draw but Micup4J cannot model are skipped. */ }
            }
            rows = current = List.copyOf(parsed);
        }
        return current;
    }

    /**
     * Returns the used component, found on the message, or {@code null}.
     *
     * @return the used component, found on the message, or {@code null}
     */
    public ItemComponent getComponent() {
        for (ActionRow row : getMessageComponents())
            for (ItemComponent item : row.getComponents())
                if (item instanceof net.guneyilmaz0.micup4j.interactions.components.ActionHandle handle && getComponentId().equals(handle.getId())) return item;
        return null;
    }

    /**
     * Acknowledges the interaction without changing the message yet (callback type 6).
     *
     * @return the action, completing with the hook
     */
    public MessageEditCallbackAction deferEdit() { return new MessageEditCallbackAction(this, 6); }

    /**
     * Edits the message content (callback type 7).
     *
     * @param content the new content
     * @return the action
     */
    public MessageEditCallbackAction editMessage(String content) { return new MessageEditCallbackAction(this, 7).setContent(content); }

    /**
     * Edits the message embeds (callback type 7).
     *
     * @param embeds the new embeds
     * @return the action
     */
    public MessageEditCallbackAction editMessageEmbeds(MessageEmbed... embeds) { return new MessageEditCallbackAction(this, 7).setEmbeds(embeds); }

    /**
     * Replaces the message components (callback type 7); pass none to remove them.
     *
     * @param rows the new rows
     * @return the action
     */
    public MessageEditCallbackAction editComponents(LayoutComponent... rows) { return editComponents(Arrays.asList(rows)); }
    /**
     * Replaces the message components (callback type 7); an empty collection removes them.
     *
     * @param rows the new rows
     * @return the action
     */
    public MessageEditCallbackAction editComponents(Collection<? extends LayoutComponent> rows) { return new MessageEditCallbackAction(this, 7).setComponents(rows); }

    /**
     * Opens a modal as the first answer.
     *
     * @param modal the modal
     * @return the action
     */
    public ModalCallbackAction replyModal(Modal modal) { return new ModalCallbackAction(this, Objects.requireNonNull(modal)); }
}
