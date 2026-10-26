package com.swadedev.micup4j.events.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.interactions.modals.ModalMapping;
import java.util.ArrayList;
import java.util.List;

/**
 * A modal submission (interaction type 5), modelled on JDA's {@code ModalInteractionEvent}. Answer it with
 * {@code reply(...)} or {@code deferReply()}; Micup offers no message edit callback for modal submissions. Values are
 * untrusted user input.
 */
public final class ModalInteractionEvent extends com.swadedev.micup4j.events.InteractionCreateEvent {
    private final List<ModalMapping> values;

    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code INTERACTION_CREATE} payload
     */
    public ModalInteractionEvent(Micup client, long sequence, JsonNode data) {
        super(client, sequence, data);
        var parsed = new ArrayList<ModalMapping>();
        collect(rawData().path("data").path("components"), parsed);
        values = List.copyOf(parsed);
    }

    private static void collect(JsonNode components, List<ModalMapping> into) {
        for (JsonNode component : components) {
            int type = component.path("type").asInt();
            if (type == 4 && component.hasNonNull("custom_id")) into.add(new ModalMapping(component.path("custom_id").asText(), component.path("value").asText("")));
            // Action rows carry their input in components; labels (type 18) carry it in component.
            collect(component.path("components"), into);
            if (component.path("component").isObject()) collect(com.swadedev.micup4j.internal.Json.MAPPER.createArrayNode().add(component.path("component")), into);
        }
    }

    /**
     * Returns the modal's custom id.
     *
     * @return the modal's custom id
     */
    public String getModalId() { return getCustomId(); }
    /**
     * Returns every submitted field in layout order.
     *
     * @return every submitted field in layout order
     */
    public List<ModalMapping> getValues() { return values; }

    /**
     * Returns one submitted field.
     *
     * @param id the text input's custom id
     * @return the field, or {@code null} when the modal had no such input
     */
    public ModalMapping getValue(String id) { return values.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null); }
}
