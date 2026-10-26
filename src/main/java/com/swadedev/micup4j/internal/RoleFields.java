package com.swadedev.micup4j.internal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.permissions.Permission;
import com.swadedev.micup4j.permissions.PermissionSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Shared, validated role payload for role creation and modification. */
public final class RoleFields {
    private final ObjectNode body = Json.object();
    private boolean permissions;

    /**
     * Sets the role name.
     *
     * @param name 1 to 100 characters
     * @throws IllegalArgumentException if the name is empty or too long
     */
    public synchronized void name(String name) {
        if (name == null || name.isEmpty() || name.codePointCount(0, name.length()) > 100)
            throw new IllegalArgumentException("Role name must be 1-100 characters");
        body.put("name", name);
    }

    /**
     * Sets the primary color.
     *
     * @param rgb a 24-bit RGB value, or {@code null} for the default color
     * @throws IllegalArgumentException if the value is outside 0-0xFFFFFF
     */
    public synchronized void color(Integer rgb) {
        int value = rgb == null ? 0 : rgb & 0xFFFFFF;
        if (rgb != null && (rgb & 0xFF000000) != 0 && (rgb & 0xFF000000) != 0xFF000000)
            throw new IllegalArgumentException("Color must be a 24-bit RGB value");
        body.put("color", value);
    }

    /**
     * Sets the complete permission mask. Writes declare every gated feature so the mask is stored as sent.
     *
     * @param mask the mask, or {@code null} to leave it unchanged (or inherit the everyone mask on creation)
     * @throws IllegalArgumentException if the mask exceeds Micup's signed 64-bit request range
     */
    public synchronized void permissions(PermissionSet mask) {
        if (mask == null) { body.remove("permissions"); permissions = false; return; }
        body.put("permissions", mask.toRequestValue());
        permissions = true;
    }

    /**
     * Sets the complete permission mask from JDA-style names.
     *
     * @param values the permissions, or {@code null} to leave the mask unchanged
     */
    public void permissions(Collection<com.swadedev.micup4j.Permission> values) {
        permissions(values == null ? null : PermissionSet.of(values.stream()
                .map(com.swadedev.micup4j.Permission::toMicupPermission).toArray(Permission[]::new)));
    }

    /**
     * Sets whether the role is displayed separately.
     *
     * @param hoist the flag, or {@code null} to leave it unchanged
     */
    public synchronized void hoist(Boolean hoist) { flag("hoist", hoist); }

    /**
     * Sets whether everyone may mention the role.
     *
     * @param mentionable the flag, or {@code null} to leave it unchanged
     */
    public synchronized void mentionable(Boolean mentionable) { flag("mentionable", mentionable); }

    private void flag(String field, Boolean value) {
        if (value == null) body.remove(field); else body.put(field, value);
    }

    /**
     * Returns whether any field is set.
     *
     * @return {@code false} for an empty payload
     */
    public synchronized boolean isEmpty() { return body.isEmpty(); }

    /**
     * Returns a copy of the payload.
     *
     * @return the JSON body
     */
    public synchronized ObjectNode body() { return body.deepCopy(); }

    /**
     * Returns the request headers for the payload and reason.
     *
     * @param reason the checked audit reason, or {@code null}
     * @return the reason and feature declaration headers
     */
    public synchronized Map<String, String> headers(String reason) {
        var headers = new HashMap<String, String>();
        if (reason != null) headers.put("X-Audit-Log-Reason", reason);
        String features = Permission.writeFeatureDeclaration();
        if (permissions && !features.isEmpty()) headers.put("X-Fluxer-Features", features);
        return Map.copyOf(headers);
    }

}
