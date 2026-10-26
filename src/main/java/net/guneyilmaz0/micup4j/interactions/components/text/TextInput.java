package net.guneyilmaz0.micup4j.interactions.components.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.interactions.components.ActionHandle;
import net.guneyilmaz0.micup4j.interactions.components.ComponentChecks;
import net.guneyilmaz0.micup4j.interactions.components.ItemComponent;
import net.guneyilmaz0.micup4j.internal.Json;

/** An immutable modal text field, modelled on JDA's {@code TextInput}. It can only be used in a modal. */
public final class TextInput implements ItemComponent, ActionHandle {
    /** Longest label, in characters. */
    public static final int MAX_LABEL_LENGTH = 45;
    /** Longest placeholder, in characters. */
    public static final int MAX_PLACEHOLDER_LENGTH = 100;
    /** Longest value, in characters. */
    public static final int MAX_VALUE_LENGTH = 4000;

    private final String id, label, placeholder, value;
    private final TextInputStyle style;
    private final int minLength, maxLength;
    private final boolean required;

    private TextInput(Builder builder) {
        ComponentChecks.customId(builder.id);
        ComponentChecks.length("Text input label", builder.label, 1, MAX_LABEL_LENGTH);
        if (builder.style == null || builder.style == TextInputStyle.UNKNOWN) throw new IllegalArgumentException("A known text input style is required");
        if (builder.placeholder != null) ComponentChecks.length("Placeholder", builder.placeholder, 0, MAX_PLACEHOLDER_LENGTH);
        if (builder.minLength < -1 || builder.minLength > MAX_VALUE_LENGTH || builder.maxLength < -1 || builder.maxLength == 0
                || builder.maxLength > MAX_VALUE_LENGTH || builder.minLength > 0 && builder.maxLength > 0 && builder.minLength > builder.maxLength)
            throw new IllegalArgumentException("Text input lengths must satisfy 0 <= min <= max <= 4000");
        if (builder.value != null) {
            int length = builder.value.codePointCount(0, builder.value.length());
            if (length > MAX_VALUE_LENGTH || builder.maxLength > 0 && length > builder.maxLength)
                throw new IllegalArgumentException("Prefilled value exceeds the maximum length");
        }
        id = builder.id; label = builder.label; style = builder.style; placeholder = builder.placeholder;
        value = builder.value; minLength = builder.minLength; maxLength = builder.maxLength; required = builder.required;
    }

    /**
     * Starts a builder.
     *
     * @param id    the custom id the submitted value is keyed by
     * @param label the label shown above the field, 1-45 characters
     * @param style the style
     * @return the builder
     */
    public static Builder create(String id, String label, TextInputStyle style) { return new Builder(id, label, style); }

    @Override public int getTypeRaw() { return 4; }
    @Override public boolean isMessageCompatible() { return false; }
    @Override public boolean isModalCompatible() { return true; }
    @Override public String getId() { return id; }
    public String getLabel() { return label; }
    public TextInputStyle getStyle() { return style; }
    public String getPlaceHolder() { return placeholder; }
    public String getValue() { return value; }
    /** @return the minimum length, or -1 when unset */
    public int getMinLength() { return minLength; }
    /** @return the maximum length, or -1 when unset */
    public int getMaxLength() { return maxLength; }
    public boolean isRequired() { return required; }

    @Override public ObjectNode toJson() {
        var node = Json.object().put("type", 4).put("custom_id", id).put("label", label).put("style", style.getRaw()).put("required", required);
        if (placeholder != null && !placeholder.isEmpty()) node.put("placeholder", placeholder);
        if (value != null) node.put("value", value);
        if (minLength >= 0) node.put("min_length", minLength);
        if (maxLength > 0) node.put("max_length", maxLength);
        return node;
    }

    public static TextInput fromJson(JsonNode node) {
        return create(node.path("custom_id").asText(), node.path("label").asText("?"), TextInputStyle.fromKey(node.path("style").asInt(1)))
                .setPlaceholder(node.hasNonNull("placeholder") ? node.path("placeholder").asText() : null)
                .setValue(node.hasNonNull("value") ? node.path("value").asText() : null)
                .setMinLength(node.path("min_length").asInt(-1)).setMaxLength(node.path("max_length").asInt(-1))
                .setRequired(node.path("required").asBoolean(true)).build();
    }

    @Override public boolean equals(Object other) { return other instanceof TextInput input && toJson().equals(input.toJson()); }
    @Override public int hashCode() { return toJson().hashCode(); }

    /** Fluent builder for {@link TextInput}. */
    public static final class Builder {
        private String id, label, placeholder, value;
        private TextInputStyle style;
        private int minLength = -1, maxLength = -1;
        private boolean required = true;

        Builder(String id, String label, TextInputStyle style) { this.id = id; this.label = label; this.style = style; }
        public Builder setId(String id) { this.id = id; return this; }
        public Builder setLabel(String label) { this.label = label; return this; }
        public Builder setStyle(TextInputStyle style) { this.style = style; return this; }
        public Builder setPlaceholder(String placeholder) { this.placeholder = placeholder; return this; }
        /** Prefills the field. */
        public Builder setValue(String value) { this.value = value; return this; }
        public Builder setMinLength(int minLength) { this.minLength = minLength; return this; }
        public Builder setMaxLength(int maxLength) { this.maxLength = maxLength; return this; }
        public Builder setRequiredRange(int min, int max) { this.minLength = min; this.maxLength = max; return this; }
        /** Whether the field must be filled; the default is {@code true}. */
        public Builder setRequired(boolean required) { this.required = required; return this; }
        public TextInput build() { return new TextInput(this); }
    }
}
