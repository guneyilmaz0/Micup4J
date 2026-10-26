package net.guneyilmaz0.micup4j.interactions.commands;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.entities.ISnowflake;
import net.guneyilmaz0.micup4j.internal.Json;

public record Command(String id, String name, String description, int rawType) implements ISnowflake {
    public Command { Json.id(id); }
    @Override public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public static Command fromJson(JsonNode data) {
        return new Command(data.path("id").asText(), data.path("name").asText(), data.path("description").asText(), data.path("type").asInt(1));
    }
    /** Immutable predefined or autocomplete suggestion with a typed wire value. */
    public static final class Choice {
        private final String name;
        private final JsonNode value;
        public Choice(String name, String value) { this(name, Json.MAPPER.getNodeFactory().textNode(java.util.Objects.requireNonNull(value))); }
        public Choice(String name, long value) { this(name, Json.MAPPER.getNodeFactory().numberNode(value)); }
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
        public String getName() { return name; }
        public String getAsString() { return value.asText(); }
        public long getAsLong() { if (!value.isIntegralNumber()) throw new IllegalStateException("Choice is not an integer"); return value.longValue(); }
        public double getAsDouble() { if (!value.isNumber()) throw new IllegalStateException("Choice is not numeric"); return value.doubleValue(); }
        public OptionType getType() { return value.isTextual() ? OptionType.STRING : value.isIntegralNumber() ? OptionType.INTEGER : OptionType.NUMBER; }
        public com.fasterxml.jackson.databind.node.ObjectNode toJson() { return Json.object().put("name", name).set("value", value.deepCopy()); }
    }
}
