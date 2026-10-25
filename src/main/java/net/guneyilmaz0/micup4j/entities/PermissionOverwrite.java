package net.guneyilmaz0.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.permissions.PermissionSet;
import java.util.Objects;

public record PermissionOverwrite(String id, Type type, PermissionSet allow, PermissionSet deny) {
    public enum Type { ROLE, MEMBER }
    public PermissionOverwrite { Json.id(id); Objects.requireNonNull(type); Objects.requireNonNull(allow); Objects.requireNonNull(deny); }
    public static PermissionOverwrite fromJson(JsonNode data) {
        int type = data.path("type").asInt(-1);
        if (type != 0 && type != 1) throw new IllegalArgumentException("Unknown permission overwrite type");
        return new PermissionOverwrite(data.path("id").asText(), Type.values()[type],
                PermissionSet.parse(data.path("allow").asText()), PermissionSet.parse(data.path("deny").asText()));
    }
}
