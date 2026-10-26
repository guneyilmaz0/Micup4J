package com.swadedev.micup4j.internal;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static JsonNode parse(String value) {
        try {
            return value.isBlank() ? NullNode.instance : MAPPER.readTree(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON response", e);
        }
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static String id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,19}") ||
                new java.math.BigInteger(value).bitLength() > 64)
            throw new IllegalArgumentException("Expected a canonical positive 64-bit snowflake");
        return value;
    }

    public static String segment(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
