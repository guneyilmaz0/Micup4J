package com.swadedev.micup4j.interactions.modals;

/**
 * One submitted modal field, modelled on JDA's {@code ModalMapping}. The value is untrusted user input.
 *
 * @param id    the text input's custom id
 * @param value the submitted text, at most 4000 characters; empty when an optional field was left blank
 */
public record ModalMapping(String id, String value) {
    /**
     * Validates the mapping; a {@code null} value becomes an empty string.
     *
     * @throws NullPointerException if the id is {@code null}
     */
    public ModalMapping { java.util.Objects.requireNonNull(id); value = value == null ? "" : value; }
    /**
     * Returns the text input's custom id.
     *
     * @return the text input's custom id
     */
    public String getId() { return id; }
    /**
     * Returns the submitted text.
     *
     * @return the submitted text
     */
    public String getAsString() { return value; }
    /**
     * Returns 4, the text input component type.
     *
     * @return 4, the text input component type
     */
    public int getTypeRaw() { return 4; }
}
