package net.guneyilmaz0.micup4j.entities.emoji;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.internal.Json;

/**
 * A Unicode or custom guild emoji, as used on buttons and select options. Modelled on JDA's {@code Emoji}.
 *
 * @param name     the Unicode sequence, or the custom emoji's name
 * @param id       the custom emoji id, or {@code null} for a Unicode emoji
 * @param animated whether a custom emoji is animated; always {@code false} for Unicode
 */
public record Emoji(String name, String id, boolean animated) {
    /**
     * Validates the emoji.
     *
     * @throws IllegalArgumentException if the name is blank, the id is invalid, or a Unicode emoji is animated
     */
    public Emoji {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Emoji name is required");
        if (id != null) Json.id(id);
        if (id == null && animated) throw new IllegalArgumentException("Unicode emojis cannot be animated");
    }

    /**
     * Creates a Unicode emoji.
     *
     * @param code the Unicode sequence, such as {@code "✅"}
     * @return the emoji
     */
    public static Emoji fromUnicode(String code) { return new Emoji(code, null, false); }

    /**
     * Creates a custom emoji reference.
     *
     * @param name     the emoji name
     * @param id       the emoji id
     * @param animated whether it is animated
     * @return the emoji
     */
    public static Emoji fromCustom(String name, String id, boolean animated) { return new Emoji(name, Json.id(id), animated); }

    /**
     * Creates a custom emoji reference.
     *
     * @param name     the emoji name
     * @param id       the emoji id as an unsigned 64-bit value
     * @param animated whether it is animated
     * @return the emoji
     */
    public static Emoji fromCustom(String name, long id, boolean animated) { return fromCustom(name, Long.toUnsignedString(id), animated); }

    /**
     * Returns the Unicode sequence or custom name.
     *
     * @return the Unicode sequence or custom name
     */
    public String getName() { return name; }
    /**
     * Returns the custom emoji id, or {@code null} for a Unicode emoji.
     *
     * @return the custom emoji id, or {@code null} for a Unicode emoji
     */
    public String getId() { return id; }
    /**
     * Returns whether a custom emoji is animated.
     *
     * @return whether a custom emoji is animated
     */
    public boolean isAnimated() { return animated; }
    /**
     * Returns {@code <:name:id>}, {@code <a:name:id>} or the Unicode sequence.
     *
     * @return {@code <:name:id>}, {@code <a:name:id>} or the Unicode sequence
     */
    public String getFormatted() { return id == null ? name : "<" + (animated ? "a" : "") + ":" + name + ":" + id + ">"; }

    /**
     * Returns a component emoji object.
     *
     * @return a component emoji object
     */
    public ObjectNode toJson() {
        var node = Json.object().put("name", name).put("animated", animated);
        if (id == null) node.putNull("id"); else node.put("id", id);
        return node;
    }

    /**
     * Parses a component emoji object.
     *
     * @param node the object
     * @return the emoji, or {@code null} when absent
     */
    public static Emoji fromJson(JsonNode node) {
        if (node == null || !node.isObject() || !node.hasNonNull("name")) return null;
        return new Emoji(node.path("name").asText(), node.hasNonNull("id") ? node.path("id").asText() : null,
                node.hasNonNull("id") && node.path("animated").asBoolean());
    }
}
