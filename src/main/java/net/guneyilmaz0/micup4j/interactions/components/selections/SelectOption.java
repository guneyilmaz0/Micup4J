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
    public SelectOption {
        ComponentChecks.length("Option label", label, 1, 100);
        ComponentChecks.length("Option value", value, 1, 100);
        if (description != null) ComponentChecks.length("Option description", description, 0, 100);
    }

    public static SelectOption of(String label, String value) { return new SelectOption(label, value, null, null, false); }
    public SelectOption withDescription(String description) { return new SelectOption(label, value, description, emoji, defaultValue); }
    public SelectOption withEmoji(Emoji emoji) { return new SelectOption(label, value, description, emoji, defaultValue); }
    public SelectOption withDefault(boolean isDefault) { return new SelectOption(label, value, description, emoji, isDefault); }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public String getDescription() { return description; }
    public Emoji getEmoji() { return emoji; }
    public boolean isDefault() { return defaultValue; }

    public ObjectNode toJson() {
        var node = Json.object().put("label", label).put("value", value);
        if (description != null && !description.isEmpty()) node.put("description", description);
        if (emoji != null) node.set("emoji", emoji.toJson());
        if (defaultValue) node.put("default", true);
        return node;
    }

    public static SelectOption fromJson(JsonNode node) {
        return new SelectOption(node.path("label").asText(), node.path("value").asText(),
                node.hasNonNull("description") ? node.path("description").asText() : null, Emoji.fromJson(node.path("emoji")), node.path("default").asBoolean());
    }
}
