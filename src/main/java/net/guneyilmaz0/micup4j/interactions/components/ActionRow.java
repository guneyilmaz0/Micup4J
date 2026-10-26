package net.guneyilmaz0.micup4j.interactions.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.interactions.components.buttons.Button;
import net.guneyilmaz0.micup4j.interactions.components.selections.EntitySelectMenu;
import net.guneyilmaz0.micup4j.interactions.components.selections.SelectMenu;
import net.guneyilmaz0.micup4j.interactions.components.selections.StringSelectMenu;
import net.guneyilmaz0.micup4j.interactions.components.text.TextInput;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * An immutable row of 1-5 buttons, exactly one select menu, or (in a modal) exactly one text input. Modelled on JDA's
 * {@code ActionRow}. A message holds at most {@value #MAX_ROWS} rows.
 */
public final class ActionRow implements LayoutComponent {
    /** Most rows on a message or modal. */
    public static final int MAX_ROWS = 5;
    /** Most buttons in one row. */
    public static final int MAX_BUTTONS = 5;

    private final List<ItemComponent> components;

    private ActionRow(List<ItemComponent> components) {
        if (components.isEmpty()) throw new IllegalArgumentException("An action row needs at least one component");
        long buttons = components.stream().filter(Button.class::isInstance).count();
        if (buttons != components.size() && components.size() != 1)
            throw new IllegalArgumentException("A row holds 1-5 buttons, or exactly one select menu or text input");
        if (buttons > MAX_BUTTONS) throw new IllegalArgumentException("A row holds at most 5 buttons");
        this.components = List.copyOf(components);
    }

    /**
     * Creates a row.
     *
     * @param components the components
     * @return the row
     * @throws IllegalArgumentException if the combination is not allowed
     */
    public static ActionRow of(ItemComponent... components) { return of(Arrays.asList(components)); }

    /**
     * Creates a row.
     *
     * @param components the components, no {@code null} elements
     * @return the row
     * @throws IllegalArgumentException if the combination is not allowed
     */
    public static ActionRow of(Collection<? extends ItemComponent> components) {
        var copy = new ArrayList<ItemComponent>();
        for (ItemComponent component : components) copy.add(Objects.requireNonNull(component, "component"));
        return new ActionRow(copy);
    }

    /**
     * Splits components into as many rows as needed, five buttons per row and one menu per row.
     *
     * @param components the components
     * @return the rows
     */
    public static List<ActionRow> partitionOf(Collection<? extends ItemComponent> components) {
        var rows = new ArrayList<ActionRow>();
        var current = new ArrayList<ItemComponent>();
        for (ItemComponent component : components) {
            if (!(component instanceof Button)) {
                if (!current.isEmpty()) { rows.add(new ActionRow(current)); current = new ArrayList<>(); }
                rows.add(of(component));
                continue;
            }
            current.add(component);
            if (current.size() == MAX_BUTTONS) { rows.add(new ActionRow(current)); current = new ArrayList<>(); }
        }
        if (!current.isEmpty()) rows.add(new ActionRow(current));
        return List.copyOf(rows);
    }

    @Override public int getTypeRaw() { return 1; }
    @Override public List<ItemComponent> getComponents() { return components; }
    @Override public boolean isMessageCompatible() { return components.stream().allMatch(Component::isMessageCompatible); }
    @Override public boolean isModalCompatible() { return components.size() == 1 && components.getFirst().isModalCompatible(); }

    /**
     * Returns the buttons of this row.
     *
     * @return the buttons of this row
     */
    public List<Button> getButtons() { return components.stream().filter(Button.class::isInstance).map(Button.class::cast).toList(); }

    /**
     * Returns the custom ids in this row, link buttons excluded.
     *
     * @return the custom ids in this row, link buttons excluded
     */
    public List<String> getActionIds() {
        return components.stream().filter(ActionHandle.class::isInstance).map(c -> ((ActionHandle) c).getId()).filter(Objects::nonNull).toList();
    }

    /**
     * Returns a copy of this row with every component disabled.
     *
     * @return a copy of this row with every component disabled
     */
    public ActionRow asDisabled() { return withDisabled(true); }
    /**
     * Returns a copy of this row with every component enabled.
     *
     * @return a copy of this row with every component enabled
     */
    public ActionRow asEnabled() { return withDisabled(false); }

    /**
     * Returns a copy with every button and select menu enabled or disabled.
     *
     * @param disabled whether to disable them
     * @return the copy
     */
    public ActionRow withDisabled(boolean disabled) {
        return new ActionRow(components.stream().map(c -> c instanceof Button b ? b.withDisabled(disabled)
                : c instanceof SelectMenu menu ? menu.withDisabled(disabled) : c).toList());
    }

    @Override public ObjectNode toJson() {
        var node = Json.object().put("type", 1);
        var array = node.putArray("components");
        components.forEach(component -> array.add(component.toJson()));
        return node;
    }

    /**
     * Parses a row from a message payload. Unknown component types are skipped, as clients do.
     *
     * @param node the row object
     * @return the row, or {@code null} when it holds no known component
     */
    public static ActionRow fromJson(JsonNode node) {
        var parsed = new ArrayList<ItemComponent>();
        for (JsonNode child : node.path("components")) {
            int type = child.path("type").asInt();
            if (type == 2) parsed.add(Button.fromJson(child));
            else if (type == 3) parsed.add(StringSelectMenu.fromJson(child));
            else if (type >= 5 && type <= 8) parsed.add(EntitySelectMenu.fromJson(child));
            else if (type == 4) parsed.add(TextInput.fromJson(child));
        }
        return parsed.isEmpty() ? null : new ActionRow(parsed);
    }

    /**
     * Validates rows for a message: at most five rows, message components only, unique custom ids.
     *
     * @param rows the rows
     * @return the JSON array
     */
    public static com.fasterxml.jackson.databind.node.ArrayNode toMessageJson(Collection<? extends LayoutComponent> rows) {
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("A message holds at most 5 action rows");
        var ids = new HashSet<String>();
        var array = Json.MAPPER.createArrayNode();
        for (LayoutComponent row : rows) {
            Objects.requireNonNull(row, "row");
            if (!row.isMessageCompatible()) throw new IllegalArgumentException("Text inputs can only be used in modals");
            for (ItemComponent item : row.getComponents())
                if (item instanceof ActionHandle handle && handle.getId() != null && !ids.add(handle.getId()))
                    throw new IllegalArgumentException("Custom ids must be unique on a message: " + handle.getId());
            array.add(row.toJson());
        }
        return array;
    }

    @Override public boolean equals(Object other) { return other instanceof ActionRow row && components.equals(row.components); }
    @Override public int hashCode() { return components.hashCode(); }
    @Override public String toString() { return "ActionRow" + components; }
}
