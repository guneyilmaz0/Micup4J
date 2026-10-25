package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
public record User(String id, String name, boolean bot) {
    public static User fromJson(JsonNode data) {
        return new User(data.path("id").asText(), data.path("username").asText(), data.path("bot").asBoolean());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isBot() {
        return bot;
    }

    public String getAsMention() {
        return "<@" + id + ">";
    }
}