package net.guneyilmaz0.micup4j.interactions.modals;

/**
 * One submitted modal field, modelled on JDA's {@code ModalMapping}. The value is untrusted user input.
 *
 * @param id    the text input's custom id
 * @param value the submitted text, at most 4000 characters; empty when an optional field was left blank
 */
public record ModalMapping(String id, String value) {
    public ModalMapping { java.util.Objects.requireNonNull(id); value = value == null ? "" : value; }
    public String getId() { return id; }
    public String getAsString() { return value; }
    /** @return {@link net.guneyilmaz0.micup4j.interactions.components.text.TextInput} type 4 */
    public int getTypeRaw() { return 4; }
}
