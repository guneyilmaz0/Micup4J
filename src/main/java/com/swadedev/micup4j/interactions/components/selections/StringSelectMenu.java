package com.swadedev.micup4j.interactions.components.selections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.entities.emoji.Emoji;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** An immutable select menu with fixed options (type 3), modelled on JDA's {@code StringSelectMenu}. */
public final class StringSelectMenu extends SelectMenu {
    private final List<SelectOption> options;

    private StringSelectMenu(String id, String placeholder, int min, int max, boolean disabled, List<SelectOption> options) {
        super(id, placeholder, min, max, disabled);
        if (options.isEmpty() || options.size() > OPTIONS_MAX_AMOUNT) throw new IllegalArgumentException("A string select needs 1-25 options");
        Set<String> values = new HashSet<>();
        for (SelectOption option : options) if (!values.add(option.value())) throw new IllegalArgumentException("Option values must be unique: " + option.value());
        if (max > options.size()) throw new IllegalArgumentException("Max values cannot exceed the number of options");
        if (options.stream().filter(SelectOption::defaultValue).count() > max) throw new IllegalArgumentException("More default options than max values");
        this.options = List.copyOf(options);
    }

    /**
     * Starts a builder.
     *
     * @param customId the custom id, 1-100 characters
     * @return the builder
     */
    public static Builder create(String customId) { return new Builder(customId); }

    @Override public int getTypeRaw() { return 3; }
    /**
     * Returns the options, in display order.
     *
     * @return the options, in display order
     */
    public List<SelectOption> getOptions() { return options; }
    @Override public StringSelectMenu withDisabled(boolean disabled) { return new StringSelectMenu(id, placeholder, minValues, maxValues, disabled, options); }
    /**
     * Returns a builder initialised from this menu.
     *
     * @return a builder initialised from this menu
     */
    public Builder createCopy() {
        var builder = create(id).setPlaceholder(placeholder).setRequiredRange(minValues, maxValues).setDisabled(disabled);
        builder.options.addAll(options);
        return builder;
    }

    @Override public ObjectNode toJson() {
        var node = baseJson();
        var array = node.putArray("options");
        options.forEach(option -> array.add(option.toJson()));
        return node;
    }

    /**
     * Parses a string select menu from a message payload.
     *
     * @param node the select menu object
     * @return the menu
     * @throws IllegalArgumentException if it violates Micup's limits
     */
    public static StringSelectMenu fromJson(JsonNode node) {
        var options = new ArrayList<SelectOption>();
        node.path("options").forEach(option -> options.add(SelectOption.fromJson(option)));
        return new StringSelectMenu(node.path("custom_id").asText(), node.hasNonNull("placeholder") ? node.path("placeholder").asText() : null,
                node.path("min_values").asInt(1), node.path("max_values").asInt(1), node.path("disabled").asBoolean(), options);
    }

    @Override public boolean equals(Object other) { return other instanceof StringSelectMenu menu && toJson().equals(menu.toJson()); }
    @Override public int hashCode() { return toJson().hashCode(); }

    /** Fluent builder for {@link StringSelectMenu}. */
    public static final class Builder extends SelectMenu.Builder<StringSelectMenu, Builder> {
        private final List<SelectOption> options = new ArrayList<>();

        Builder(String id) { super(id); }
        /**
         * Adds an option.
         *
         * @param label the shown text
         * @param value the value sent back
         * @return this builder
         */
        public Builder addOption(String label, String value) { return addOptions(SelectOption.of(label, value)); }
        /**
         * Adds an option with a description.
         *
         * @param label       the shown text
         * @param value       the value sent back
         * @param description the second line
         * @return this builder
         */
        public Builder addOption(String label, String value, String description) { return addOptions(SelectOption.of(label, value).withDescription(description)); }
        /**
         * Adds an option with a description and emoji.
         *
         * @param label       the shown text
         * @param value       the value sent back
         * @param description the second line, or {@code null}
         * @param emoji       the emoji, or {@code null}
         * @return this builder
         */
        public Builder addOption(String label, String value, String description, Emoji emoji) {
            return addOptions(SelectOption.of(label, value).withDescription(description).withEmoji(emoji));
        }
        /**
         * Adds options.
         *
         * @param options the options
         * @return this builder
         */
        public Builder addOptions(SelectOption... options) { return addOptions(Arrays.asList(options)); }
        /**
         * Adds options.
         *
         * @param options the options; at most 25 in total
         * @return this builder
         * @throws IllegalArgumentException if there would be more than 25
         */
        public Builder addOptions(Collection<? extends SelectOption> options) {
            for (SelectOption option : options) this.options.add(Objects.requireNonNull(option, "option"));
            if (this.options.size() > OPTIONS_MAX_AMOUNT) throw new IllegalArgumentException("A string select holds at most 25 options");
            return this;
        }
        /**
         * Returns the mutable option list.
         *
         * @return the mutable option list
         */
        public List<SelectOption> getOptions() { return options; }

        /**
         * Marks the options with the given values as chosen in advance and clears every other default.
         *
         * @param values the default values
         * @return this builder
         */
        public Builder setDefaultValues(String... values) {
            Set<String> chosen = Set.of(values);
            options.replaceAll(option -> option.withDefault(chosen.contains(option.value())));
            return this;
        }

        @Override public StringSelectMenu build() { return new StringSelectMenu(id, placeholder, minValues, maxValues, disabled, options); }
    }
}
