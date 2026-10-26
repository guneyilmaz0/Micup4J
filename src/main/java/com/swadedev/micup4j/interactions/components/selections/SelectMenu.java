package com.swadedev.micup4j.interactions.components.selections;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.interactions.components.ActionHandle;
import com.swadedev.micup4j.interactions.components.ComponentChecks;
import com.swadedev.micup4j.interactions.components.ItemComponent;
import com.swadedev.micup4j.internal.Json;

/** Common state of string and entity select menus. A select menu fills a whole action row. */
public abstract sealed class SelectMenu implements ItemComponent, ActionHandle permits StringSelectMenu, EntitySelectMenu {
    /** Longest placeholder, in characters. */
    public static final int PLACEHOLDER_MAX_LENGTH = 150;
    /** Most selectable values and options. */
    public static final int OPTIONS_MAX_AMOUNT = 25;

    final String id, placeholder;
    final int minValues, maxValues;
    final boolean disabled;

    SelectMenu(String id, String placeholder, int minValues, int maxValues, boolean disabled) {
        ComponentChecks.customId(id);
        if (placeholder != null) ComponentChecks.length("Placeholder", placeholder, 0, PLACEHOLDER_MAX_LENGTH);
        if (minValues < 0 || minValues > OPTIONS_MAX_AMOUNT || maxValues < 1 || maxValues > OPTIONS_MAX_AMOUNT || minValues > maxValues)
            throw new IllegalArgumentException("Select values must satisfy 0 <= min <= max, 1 <= max <= 25");
        this.id = id; this.placeholder = placeholder == null || placeholder.isEmpty() ? null : placeholder;
        this.minValues = minValues; this.maxValues = maxValues; this.disabled = disabled;
    }

    @Override public String getId() { return id; }
    @Override public int getMaxPerRow() { return 5; }
    /**
     * Returns the placeholder, or {@code null}.
     *
     * @return the placeholder, or {@code null}
     */
    public String getPlaceholder() { return placeholder; }
    /**
     * Returns the minimum number of values.
     *
     * @return the minimum number of values
     */
    public int getMinValues() { return minValues; }
    /**
     * Returns the maximum number of values.
     *
     * @return the maximum number of values
     */
    public int getMaxValues() { return maxValues; }
    /**
     * Returns whether the menu refuses input.
     *
     * @return whether the menu refuses input
     */
    public boolean isDisabled() { return disabled; }
    /**
     * Returns a disabled copy.
     *
     * @return a disabled copy
     */
    public SelectMenu asDisabled() { return withDisabled(true); }
    /**
     * Returns an enabled copy.
     *
     * @return an enabled copy
     */
    public SelectMenu asEnabled() { return withDisabled(false); }

    /**
     * Returns a copy with the disabled flag set.
     *
     * @param disabled whether the menu refuses input
     * @return the copy
     */
    public abstract SelectMenu withDisabled(boolean disabled);

    ObjectNode baseJson() {
        var node = Json.object().put("type", getTypeRaw()).put("custom_id", id).put("min_values", minValues)
                .put("max_values", maxValues).put("disabled", disabled);
        if (placeholder != null) node.put("placeholder", placeholder);
        return node;
    }

    /**
     * Fluent configuration shared by the select menu builders.
     *
     * @param <T> the menu type
     * @param <B> the builder type
     */
    @SuppressWarnings("unchecked")
    public abstract static sealed class Builder<T extends SelectMenu, B extends Builder<T, B>> permits StringSelectMenu.Builder, EntitySelectMenu.Builder {
        String id, placeholder;
        int minValues = 1, maxValues = 1;
        boolean disabled;

        Builder(String id) { this.id = ComponentChecks.customId(id); }
        /**
         * Sets the custom id.
         *
         * @param id 1-100 characters
         * @return this builder
         */
        public B setId(String id) { this.id = ComponentChecks.customId(id); return (B) this; }
        /**
         * Sets the placeholder.
         *
         * @param placeholder at most 150 characters, or {@code null}
         * @return this builder
         */
        public B setPlaceholder(String placeholder) {
            if (placeholder != null) ComponentChecks.length("Placeholder", placeholder, 0, PLACEHOLDER_MAX_LENGTH);
            this.placeholder = placeholder; return (B) this;
        }
        /**
         * Sets the minimum number of values.
         *
         * @param minValues 0-25
         * @return this builder
         */
        public B setMinValues(int minValues) { this.minValues = minValues; return (B) this; }
        /**
         * Sets the maximum number of values.
         *
         * @param maxValues 1-25
         * @return this builder
         */
        public B setMaxValues(int maxValues) { this.maxValues = maxValues; return (B) this; }
        /**
         * Sets both value bounds.
         *
         * @param min the minimum number of values
         * @param max the maximum number of values
         * @return this builder
         */
        public B setRequiredRange(int min, int max) { this.minValues = min; this.maxValues = max; return (B) this; }
        /**
         * Sets whether the menu refuses input.
         *
         * @param disabled the flag
         * @return this builder
         */
        public B setDisabled(boolean disabled) { this.disabled = disabled; return (B) this; }
        /**
         * Returns the custom id.
         *
         * @return the custom id
         */
        public String getId() { return id; }

        /**
         * Builds the immutable menu.
         *
         * @return the menu
         * @throws IllegalArgumentException if the configuration violates Micup's limits
         */
        public abstract T build();
    }
}
