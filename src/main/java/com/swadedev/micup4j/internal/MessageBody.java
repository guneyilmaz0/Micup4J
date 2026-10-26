package com.swadedev.micup4j.internal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.entities.MessageEmbed;
import java.util.Collection;
import java.util.Objects;

/**
 * Internal, thread-safe message payload shared by the fluent message builders.
 *
 * <p>The body is mutable until {@link #freeze()} is called. Builders freeze it only after their own validation passed,
 * so a payload that failed validation can still be fixed and submitted again. Every mutator called after the freeze
 * throws {@link IllegalStateException}.
 *
 * <p>Not part of the supported public API.
 */
public final class MessageBody {
    /** Maximum number of embeds Micup accepts on a single message. */
    public static final int MAX_EMBEDS = 10;

    private final ObjectNode body;
    private final String frozenMessage;
    private boolean frozen;

    /**
     * Creates a body from a deep copy of {@code initial}.
     *
     * @param initial the initial payload, not retained
     */
    public MessageBody(final ObjectNode initial) {
        this(initial, "Message action has already been submitted");
    }

    /**
     * Creates a body from a deep copy of {@code initial} with a custom message for mutations after the freeze.
     *
     * @param initial the initial payload, not retained
     * @param frozenMessage the message of the {@link IllegalStateException} thrown by mutators after the freeze
     */
    public MessageBody(final ObjectNode initial, final String frozenMessage) {
        this.body = Objects.requireNonNull(initial).deepCopy();
        this.frozenMessage = Objects.requireNonNull(frozenMessage);
    }

    /**
     * Replaces the content.
     *
     * @param value the new content; {@code null} clears it to an empty string
     */
    public synchronized void setContent(final String value) {
        mutable();
        body.put("content", value == null ? "" : value);
    }

    /**
     * Appends to the content.
     *
     * @param value the text to append, not {@code null}
     */
    public synchronized void addContent(final String value) {
        Objects.requireNonNull(value, "content");
        mutable();
        body.put("content", body.path("content").asText("") + value);
    }

    /**
     * Replaces or extends the embeds. The body is left unchanged when the result would be invalid.
     *
     * @param values the embeds, no {@code null} elements
     * @param append {@code true} to append to the current embeds, {@code false} to replace them
     * @throws IllegalArgumentException if the result would hold more than {@value #MAX_EMBEDS} embeds
     */
    public synchronized void setEmbeds(final Collection<? extends MessageEmbed> values, final boolean append) {
        Objects.requireNonNull(values, "embeds");
        mutable();
        ArrayNode next = append && body.path("embeds").isArray()
                ? body.withArray("embeds").deepCopy() : Json.MAPPER.createArrayNode();
        for (MessageEmbed value : values) next.add(Objects.requireNonNull(value, "embed").toJson());
        if (next.size() > MAX_EMBEDS) throw new IllegalArgumentException("A message supports at most 10 embeds");
        body.set("embeds", next);
    }

    /**
     * Replaces the message components. An empty collection removes every component; Micup keeps stored components
     * when an edit omits the field entirely.
     *
     * @param rows the action rows, at most five, message components only, unique custom ids
     * @throws IllegalArgumentException if the rows are invalid for a message
     */
    public synchronized void setComponents(final Collection<? extends com.swadedev.micup4j.interactions.components.LayoutComponent> rows) {
        Objects.requireNonNull(rows, "components");
        mutable();
        body.set("components", com.swadedev.micup4j.interactions.components.ActionRow.toMessageJson(rows));
    }

    /**
     * Appends message components to the current ones.
     *
     * @param rows the action rows to add
     * @throws IllegalArgumentException if the result would be invalid for a message
     */
    public synchronized void addComponents(final Collection<? extends com.swadedev.micup4j.interactions.components.LayoutComponent> rows) {
        Objects.requireNonNull(rows, "components");
        mutable();
        var combined = new java.util.ArrayList<com.swadedev.micup4j.interactions.components.LayoutComponent>();
        for (var row : body.path("components")) {
            var parsed = com.swadedev.micup4j.interactions.components.ActionRow.fromJson(row);
            if (parsed != null) combined.add(parsed);
        }
        combined.addAll(rows);
        body.set("components", com.swadedev.micup4j.interactions.components.ActionRow.toMessageJson(combined));
    }

    /**
     * Sets or clears a bit of the {@code flags} field, creating the field when absent.
     *
     * @param bit the flag bit
     * @param on whether the bit is set
     */
    public synchronized void setFlag(final int bit, final boolean on) {
        mutable();
        int flags = body.path("flags").asInt(0);
        body.put("flags", on ? flags | bit : flags & ~bit);
    }

    /**
     * Returns whether the body has non-blank content or at least one embed.
     *
     * @return {@code true} when the message would be visible
     */
    public synchronized boolean hasVisibleContent() {
        return !body.path("content").asText("").isBlank() || !body.path("embeds").isEmpty();
    }

    /**
     * Returns whether the body holds no field at all, without copying it.
     *
     * @return {@code true} when no field has been set
     */
    public synchronized boolean isEmpty() {
        return body.isEmpty();
    }

    /**
     * Returns whether {@link #freeze()} has been called.
     *
     * @return {@code true} once frozen
     */
    public synchronized boolean isFrozen() {
        return frozen;
    }

    /**
     * Marks the body as frozen and returns a deep copy of it.
     *
     * @return a copy the caller may modify freely
     */
    public synchronized ObjectNode freeze() {
        frozen = true;
        return body.deepCopy();
    }

    /**
     * Returns a deep copy of the current body without freezing it.
     *
     * @return a copy the caller may modify freely
     */
    public synchronized ObjectNode frozenSnapshot() {
        return body.deepCopy();
    }

    /**
     * Returns the payload for one execution. The first call runs {@code validation} and freezes the body only when
     * it returns normally; later calls return the frozen payload again without validating.
     *
     * @param validation checks the body and throws to reject it; runs while this body is locked
     * @return a copy of the payload to send
     */
    public synchronized ObjectNode submit(final Runnable validation) {
        // Validation and freeze run under one monitor so a concurrent setter cannot slip in between the check and the freeze.
        if (frozen) return body.deepCopy();
        validation.run();
        return freeze();
    }

    private void mutable() {
        if (frozen) throw new IllegalStateException(frozenMessage);
    }
}
