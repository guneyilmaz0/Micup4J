package net.guneyilmaz0.micup4j.events.interaction.component;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.interactions.components.selections.SelectOption;
import net.guneyilmaz0.micup4j.interactions.components.selections.StringSelectMenu;
import java.util.ArrayList;
import java.util.List;

/** A string select menu choice, modelled on JDA's {@code StringSelectInteractionEvent}. */
public final class StringSelectInteractionEvent extends GenericComponentInteractionCreateEvent {
    private final List<String> values;

    /**
     * Creates the event; called by the library.
     *
     * @param client   the session
     * @param sequence the dispatch sequence
     * @param data     the {@code INTERACTION_CREATE} payload
     */
    public StringSelectInteractionEvent(Micup client, long sequence, JsonNode data) {
        super(client, sequence, data);
        var parsed = new ArrayList<String>();
        rawData().path("data").path("values").forEach(value -> parsed.add(value.asText()));
        values = List.copyOf(parsed);
    }

    /**
     * Returns the chosen values; Micup checked they are among the stored options.
     *
     * @return the chosen values; Micup checked they are among the stored options
     */
    public List<String> getValues() { return values; }

    /**
     * Returns the used menu, found on the message, or {@code null}.
     *
     * @return the used menu, found on the message, or {@code null}
     */
    public StringSelectMenu getSelectMenu() { return getComponent() instanceof StringSelectMenu menu ? menu : null; }

    /**
     * Returns the chosen options, or an empty list when the menu is not in the payload.
     *
     * @return the chosen options, or an empty list when the menu is not in the payload
     */
    public List<SelectOption> getSelectedOptions() {
        StringSelectMenu menu = getSelectMenu();
        return menu == null ? List.of() : menu.getOptions().stream().filter(option -> values.contains(option.value())).toList();
    }
}
