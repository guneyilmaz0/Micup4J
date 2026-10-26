package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.internal.Json;

/**
 * A user account snapshot.
 *
 * @param id         the user id
 * @param name       the username
 * @param bot        whether the account is a bot
 * @param avatarId   the avatar hash, or {@code null} without a custom avatar
 * @param globalName the global display name, or {@code null}
 * @param client     the owning session, or {@code null} for a detached snapshot
 */
public record User(String id, String name, boolean bot, String avatarId, String globalName, Micup client) implements ISnowflake {
    /**
     * Creates a detached user without avatar or display name.
     *
     * @param id   the user id
     * @param name the username
     * @param bot  whether the account is a bot
     */
    public User(String id, String name, boolean bot) { this(id, name, bot, null, null, null); }
    /**
     * Returns a copy bound to a session, which enables {@link #getAvatarUrl()}.
     *
     * @param client the session
     * @return the bound copy
     */
    public User withClient(Micup client) { return new User(id, name, bot, avatarId, globalName, client); }
    /**
     * Parses a user object.
     *
     * @param data the user object
     * @return the detached snapshot
     */
    public static User fromJson(JsonNode data) {
        return new User(data.path("id").asText(), data.path("username").asText(), data.path("bot").asBoolean(),
                data.path("avatar").asText(null), data.path("global_name").asText(null), null);
    }
    @Override public String getId() { return id; }
    /**
     * Returns the username.
     *
     * @return the username
     */
    public String getName() { return name; }
    /**
     * Returns the global display name, else the username.
     *
     * @return the global display name, else the username
     */
    public String getEffectiveName() { return globalName == null ? name : globalName; }
    /**
     * Returns the global display name, or {@code null}.
     *
     * @return the global display name, or {@code null}
     */
    public String getGlobalName() { return globalName; }
    /**
     * Returns the avatar hash, or {@code null}.
     *
     * @return the avatar hash, or {@code null}
     */
    public String getAvatarId() { return avatarId; }
    /**
     * Returns whether the account is a bot.
     *
     * @return whether the account is a bot
     */
    public boolean isBot() { return bot; }
    /**
     * Returns the user mention, {@code <@id>}.
     *
     * @return the user mention, {@code <@id>}
     */
    public String getAsMention() { return "<@" + id + ">"; }
    /**
     * Returns the custom avatar URL on the discovered Media Proxy. {@code null} means the account has no custom avatar; no
     * undocumented default asset URL is invented.
     *
     * @return the URL, or {@code null}
     * @throws IllegalStateException if this user is detached or discovery published no media endpoint
     */
    public String getAvatarUrl() {
        if (avatarId == null) return null;
        if (client == null) throw new IllegalStateException("Detached user has no discovered media endpoint");
        String base = client.getMediaBase().toString().replaceAll("/+$", "");
        return base + "/avatars/" + Json.id(id) + "/" + Json.segment(avatarId) + (avatarId.startsWith("a_") ? ".gif" : ".webp");
    }
}
