package net.guneyilmaz0.micup4j.interactions.components.selections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.entities.emoji.Emoji;
import net.guneyilmaz0.micup4j.interactions.components.ComponentChecks;
import net.guneyilmaz0.micup4j.internal.Json;

/**
 * An immutable string select option, modelled on JDA's {@code SelectOption}.
 *
 * @param label        the shown text, 1-100 characters
 * @param value        the value sent back, 1-100 characters
 * @param description  a second line of at most 100 characters, or {@code null}
 * @param emoji        an emoji, or {@code null}
 * @param defaultValue whether the option is chosen in advance
 */
public record SelectOption(String label, String value, String description, Emoji emoji, boolean defaultValue) {
    /**
     * Validates the option.
     *
     * @throws IllegalArgumentException if a length is invalid
     */
    public SelectOption {
        ComponentChecks.length("Option label", label, 1, 100);
        ComponentChecks.length("Option value", value, 1, 100);
        if (description != null) ComponentChecks.length("Option description", description, 0, 100);
    }

    /**
     * Creates an option.
     *
     * @param label the shown text, 1-100 characters
     * @param value the value sent back, 1-100 characters
     * @return the option
     */
    public static SelectOption of(String label, String value) { return new SelectOption(label, value, null, null, false); }
    /**
     * Returns a copy with a description.
     *
     * @param description at most 100 characters, or {@code null}
     * @return the copy
     */
    public SelectOption withDescription(String description) { return new SelectOption(label, value, description, emoji, defaultValue); }
    /**
     * Returns a copy with an emoji.
     *
     * @param emoji the emoji, or {@code null}
     * @return the copy
     */
    public SelectOption withEmoji(Emoji emoji) { return new SelectOption(label, value, description, emoji, defaultValue); }
    /**
     * Returns a copy that is or is not chosen in advance.
     *
     * @param isDefault whether the option is preselected
     * @return the copy
     */
    public SelectOption withDefault(boolean isDefault) { return new SelectOption(label, value, description, emoji, isDefault); }
    /**
     * Returns the shown text.
     *
     * @return the shown text
     */
    public String getLabel() { return label; }
    /**
     * Returns the value sent back.
     *
     * @return the value sent back
     */
    public String getValue() { return value; }
    /**
     * Returns the description, or {@code null}.
     *
     * @return the description, or {@code null}
     */
    public String getDescription() { return description; }
    /**
     * Returns the emoji, or {@code null}.
     *
     * @return the emoji, or {@code null}
     */
    public Emoji getEmoji() { return emoji; }
    /**
     * Returns whether the option is chosen in advance.
     *
     * @return whether the option is chosen in advance
     */
    public boolean isDefault() { return defaultValue; }

    /**
     * Returns the option payload.
     *
     * @return the option payload
     */
    public ObjectNode toJson() {
        var node = Json.object().put("label", label).put("value", value);
        if (description != null && !description.isEmpty()) node.put("description", description);
        if (emoji != null) node.set("emoji", emoji.toJson());
        if (defaultValue) node.put("default", true);
        return node;
    }

    /**
     * Parses an option from a message payload.
     *
     * @param node the option object
     * @return the option
     * @throws IllegalArgumentException if a length is invalid
     */
    public static SelectOption fromJson(JsonNode node) {
        return new SelectOption(node.path("label").asText(), node.path("value").asText(),
                node.hasNonNull("description") ? node.path("description").asText() : null, Emoji.fromJson(node.path("emoji")), node.path("default").asBoolean());
    }
}
