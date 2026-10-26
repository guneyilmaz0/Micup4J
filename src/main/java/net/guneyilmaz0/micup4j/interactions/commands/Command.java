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
}
