package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;

public record User(String id, String name, boolean bot, String avatarId, String globalName, Micup client) implements ISnowflake {
    public User(String id, String name, boolean bot) { this(id, name, bot, null, null, null); }
    public User withClient(Micup client) { return new User(id, name, bot, avatarId, globalName, client); }
    public static User fromJson(JsonNode data) {
        return new User(data.path("id").asText(), data.path("username").asText(), data.path("bot").asBoolean(),
                data.path("avatar").asText(null), data.path("global_name").asText(null), null);
    }
    @Override public String getId() { return id; }
    public String getName() { return name; }
    public String getEffectiveName() { return globalName == null ? name : globalName; }
    public String getGlobalName() { return globalName; }
    public String getAvatarId() { return avatarId; }
    public boolean isBot() { return bot; }
    public String getAsMention() { return "<@" + id + ">"; }
    /** Null means the account has no custom avatar; no undocumented default asset URL is invented. */
    public String getAvatarUrl() {
        if (avatarId == null) return null;
        if (client == null) throw new IllegalStateException("Detached user has no discovered media endpoint");
        String base = client.getMediaBase().toString().replaceAll("/+$", "");
        return base + "/avatars/" + Json.id(id) + "/" + Json.segment(avatarId) + (avatarId.startsWith("a_") ? ".gif" : ".webp");
    }
}
