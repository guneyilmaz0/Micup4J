package com.swadedev.micup4j.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.swadedev.micup4j.internal.Json;
import com.swadedev.micup4j.permissions.PermissionSet;
import java.util.Objects;

/**
 * A channel permission overwrite.
 *
 * @param id    the role or member id
 * @param type  what the id names
 * @param allow the bits it grants
 * @param deny  the bits it removes
 */
public record PermissionOverwrite(String id, Type type, PermissionSet allow, PermissionSet deny) {
    /** What an overwrite applies to. */
    public enum Type {
        /** A role overwrite. */
        ROLE,
        /** A member overwrite. */
        MEMBER
    }
    /**
     * Validates the id and masks.
     *
     * @throws IllegalArgumentException if the id is invalid
     */
    public PermissionOverwrite { Json.id(id); Objects.requireNonNull(type); Objects.requireNonNull(allow); Objects.requireNonNull(deny); }
    /**
     * Parses an overwrite object.
     *
     * @param data the overwrite object
     * @return the overwrite
     * @throws IllegalArgumentException if a field is invalid
     */
    public static PermissionOverwrite fromJson(JsonNode data) {
        int type = data.path("type").asInt(-1);
        if (type != 0 && type != 1) throw new IllegalArgumentException("Unknown permission overwrite type");
        return new PermissionOverwrite(data.path("id").asText(), Type.values()[type],
                PermissionSet.parse(data.path("allow").asText()), PermissionSet.parse(data.path("deny").asText()));
    }
}
