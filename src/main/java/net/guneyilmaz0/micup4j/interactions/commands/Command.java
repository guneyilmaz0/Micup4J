package net.guneyilmaz0.micup4j.interactions.commands;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.entities.ISnowflake;
import net.guneyilmaz0.micup4j.internal.Json;

/**
 * A registered application command.
 *
 * @param id          the command id
 * @param name        the name
 * @param description the description
 * @param rawType     the Micup command type; 1 for slash commands
 */
public record Command(String id, String name, String description, int rawType) implements ISnowflake {
    /**
     * Validates the id.
     *
     * @throws IllegalArgumentException if the id is invalid
     */
    public Command { Json.id(id); }
    @Override public String getId() { return id; }
    /**
     * Returns the command name.
     *
     * @return the command name
     */
    public String getName() { return name; }
    /**
     * Returns the command description.
     *
     * @return the command description
     */
    public String getDescription() { return description; }
    /**
     * Parses an application command object.
     *
     * @param data the command object
     * @return the command
     * @throws IllegalArgumentException if the id is invalid
     */
    public static Command fromJson(JsonNode data) {
        return new Command(data.path("id").asText(), data.path("name").asText(), data.path("description").asText(), data.path("type").asInt(1));
    }
    /** Immutable predefined or autocomplete suggestion with a typed wire value. */
    public static final class Choice {
        private final String name;
        private final JsonNode value;
        /**
         * Creates a string choice.
         *
         * @param name  1-100 characters
         * @param value at most 100 characters
         * @throws IllegalArgumentException if a length is invalid
         */
        public Choice(String name, String value) { this(name, Json.MAPPER.getNodeFactory().textNode(java.util.Objects.requireNonNull(value))); }
        /**
         * Creates an integer choice.
         *
         * @param name  1-100 characters
         * @param value a JSON-safe integer
         * @throws IllegalArgumentException if a value is invalid
         */
        public Choice(String name, long value) { this(name, Json.MAPPER.getNodeFactory().numberNode(value)); }
        /**
         * Creates a number choice.
         *
         * @param name  1-100 characters
         * @param value a finite, JSON-safe number
         * @throws IllegalArgumentException if a value is invalid
         */
        public Choice(String name, double value) { this(name, Json.MAPPER.getNodeFactory().numberNode(value)); }
        private Choice(String name, JsonNode value) {
            if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 100)
                throw new IllegalArgumentException("Choice name must contain 1 to 100 characters");
            if (value.isTextual() && value.asText().codePointCount(0, value.asText().length()) > 100)
                throw new IllegalArgumentException("Choice string must contain at most 100 characters");
            if (value.isNumber() && (!Double.isFinite(value.doubleValue()) || Math.abs(value.doubleValue()) > 9007199254740991d))
                throw new IllegalArgumentException("Choice number exceeds the JSON safe integer range");
            this.name = name; this.value = value.deepCopy();
        }
        /**
         * Returns the name shown to the user.
         *
         * @return the name shown to the user
         */
        public String getName() { return name; }
        /**
         * Returns the value as text.
         *
         * @return the value as text
         */
        public String getAsString() { return value.asText(); }
        /**
         * Returns the value as an integer.
         *
         * @return the value
         * @throws IllegalStateException if the value is not an integer
         */
        public long getAsLong() { if (!value.isIntegralNumber()) throw new IllegalStateException("Choice is not an integer"); return value.longValue(); }
        /**
         * Returns the value as a number.
         *
         * @return the value
         * @throws IllegalStateException if the value is not numeric
         */
        public double getAsDouble() { if (!value.isNumber()) throw new IllegalStateException("Choice is not numeric"); return value.doubleValue(); }
        /**
         * Returns {@code STRING}, {@code INTEGER} or {@code NUMBER}, from the value.
         *
         * @return {@code STRING}, {@code INTEGER} or {@code NUMBER}, from the value
         */
        public OptionType getType() { return value.isTextual() ? OptionType.STRING : value.isIntegralNumber() ? OptionType.INTEGER : OptionType.NUMBER; }
        /**
         * Returns the choice payload.
         *
         * @return the choice payload
         */
        public com.fasterxml.jackson.databind.node.ObjectNode toJson() { return Json.object().put("name", name).set("value", value.deepCopy()); }
    }
}
