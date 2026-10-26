package com.swadedev.micup4j.interactions.modals;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.interactions.components.ActionRow;
import com.swadedev.micup4j.interactions.components.ComponentChecks;
import com.swadedev.micup4j.interactions.components.ItemComponent;
import com.swadedev.micup4j.interactions.components.LayoutComponent;
import com.swadedev.micup4j.internal.Json;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * An immutable modal of 1-5 rows, each with one text input. Modelled on JDA's {@code Modal}. Micup opens a modal only as
 * the first answer to a slash command or component interaction; select menus and file uploads inside modals are not
 * offered. The submission arrives as a {@code ModalInteractionEvent} within 900 seconds.
 */
public final class Modal {
    /** Longest title, in characters. */
    public static final int MAX_TITLE_LENGTH = 45;
    /** Most rows. */
    public static final int MAX_COMPONENTS = 5;

    private final String id, title;
    private final List<LayoutComponent> components;

    private Modal(String id, String title, List<LayoutComponent> components) {
        ComponentChecks.customId(id);
        ComponentChecks.length("Modal title", title, 1, MAX_TITLE_LENGTH);
        if (components.isEmpty() || components.size() > MAX_COMPONENTS) throw new IllegalArgumentException("A modal holds 1-5 rows");
        var ids = new HashSet<String>();
        for (LayoutComponent row : components) {
            if (!row.isModalCompatible()) throw new IllegalArgumentException("Modal rows hold exactly one text input");
            for (ItemComponent item : row.getComponents())
                if (item instanceof com.swadedev.micup4j.interactions.components.ActionHandle handle && !ids.add(handle.getId()))
                    throw new IllegalArgumentException("Text input ids must be unique: " + handle.getId());
        }
        this.id = id; this.title = title; this.components = List.copyOf(components);
    }

    /**
     * Starts a builder.
     *
     * @param customId the id returned with the submission
     * @param title    the title, 1-45 characters
     * @return the builder
     */
    public static Builder create(String customId, String title) { return new Builder(customId, title); }

    /**
     * Returns the modal's custom id.
     *
     * @return the modal's custom id
     */
    public String getId() { return id; }
    /**
     * Returns the title.
     *
     * @return the title
     */
    public String getTitle() { return title; }
    /**
     * Returns the rows.
     *
     * @return the rows
     */
    public List<LayoutComponent> getComponents() { return components; }

    /**
     * Returns the modal callback data.
     *
     * @return the modal callback data
     */
    public ObjectNode toJson() {
        var node = Json.object().put("custom_id", id).put("title", title);
        var array = node.putArray("components");
        components.forEach(row -> array.add(row.toJson()));
        return node;
    }

    /** Fluent builder for {@link Modal}. */
    public static final class Builder {
        private String id, title;
        private final List<LayoutComponent> components = new ArrayList<>();

        Builder(String id, String title) { this.id = id; this.title = title; }
        /**
         * Sets the custom id.
         *
         * @param id 1-100 characters
         * @return this builder
         */
        public Builder setId(String id) { this.id = id; return this; }
        /**
         * Sets the title.
         *
         * @param title 1-45 characters
         * @return this builder
         */
        public Builder setTitle(String title) { this.title = title; return this; }
        /**
         * Adds one row holding a text input.
         *
         * @param components the row's components; exactly one text input
         * @return this builder
         */
        public Builder addActionRow(ItemComponent... components) { return addComponents(ActionRow.of(components)); }
        /**
         * Adds rows.
         *
         * @param rows the rows
         * @return this builder
         */
        public Builder addComponents(LayoutComponent... rows) { return addComponents(Arrays.asList(rows)); }
        /**
         * Adds rows.
         *
         * @param rows the rows; 1-5 in total
         * @return this builder
         */
        public Builder addComponents(Collection<? extends LayoutComponent> rows) { components.addAll(rows); return this; }
        /**
         * Returns the mutable row list.
         *
         * @return the mutable row list
         */
        public List<LayoutComponent> getComponents() { return components; }
        /**
         * Builds the immutable modal.
         *
         * @return the modal
         * @throws IllegalArgumentException if the modal violates Micup's limits
         */
        public Modal build() { return new Modal(id, title, components); }
    }
}
