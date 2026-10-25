package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.time.Instant;
import java.util.*;

/** Immutable membership snapshot. A timeout is a separate restriction, not a permission-mask change. */
public record Member(String guildId, User user, String nickname, List<String> roleIds,
                     Instant joinedAt, Instant communicationDisabledUntil, boolean mute, boolean deaf) {
    public Member {
        Json.id(guildId); Objects.requireNonNull(user); Json.id(user.id());
        roleIds = List.copyOf(roleIds);
        roleIds.forEach(Json::id);
    }
    public String getId() { return user.id(); }
    public String getEffectiveName() { return nickname == null ? user.name() : nickname; }
    public boolean isTimedOutAt(Instant now) { return communicationDisabledUntil != null && communicationDisabledUntil.isAfter(now); }
    public Member withoutRole(String roleId) {
        return new Member(guildId, user, nickname, roleIds.stream().filter(id -> !id.equals(roleId)).toList(),
                joinedAt, communicationDisabledUntil, mute, deaf);
    }
    public static Member fromJson(String guildId, JsonNode data) {
        if (!data.path("roles").isArray()) throw new IllegalArgumentException("Member role set is missing");
        var roles = new ArrayList<String>();
        for (var role : data.path("roles")) roles.add(Json.id(role.asText()));
        return new Member(guildId, User.fromJson(data.path("user")), data.path("nick").asText(null), roles,
                instant(data, "joined_at"), instant(data, "communication_disabled_until"),
                data.path("mute").asBoolean(), data.path("deaf").asBoolean());
    }
    private static Instant instant(JsonNode node, String field) {
        return node.hasNonNull(field) ? Instant.parse(node.path(field).asText()) : null;
    }
}
