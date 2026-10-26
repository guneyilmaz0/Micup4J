package com.swadedev.micup4j.managers;

import com.swadedev.micup4j.requests.restaction.AuditableRestAction;
import com.swadedev.micup4j.internal.RoleFields;
import com.swadedev.micup4j.permissions.PermissionSet;
import com.swadedev.micup4j.requests.RestAction;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiFunction;

/**
 * Fluent role modification, modelled on JDA's {@code RoleManager}. Only fields that were set are sent; executing an empty
 * manager completes without a request. Micup accepts and ignores name, hoist and mentionable changes to the everyone role.
 */
public final class RoleManager extends AuditableRestAction<Void> {
    private final RoleFields fields;

    /**
     * Creates the action.
     *
     * @param fields  the payload, shared with the request factory
     * @param factory sends the payload with the given reason
     */
    public RoleManager(RoleFields fields, BiFunction<RoleFields, String, RestAction<Void>> factory) {
        super(reason -> fields.isEmpty() ? new RestAction<>(() -> java.util.concurrent.CompletableFuture.completedFuture(null))
                : factory.apply(fields, reason), true);
        this.fields = fields;
    }

    @Override public RoleManager reason(String reason) { super.reason(reason); return this; }

    /**
     * Sets the name.
     *
     * @param name 1 to 100 characters
     * @return this action
     * @throws IllegalArgumentException if the name is empty or too long
     */
    public RoleManager setName(String name) { fields.name(name); return this; }

    /**
     * Sets the color.
     *
     * @param color the color, or {@code null} to leave it unchanged
     * @return this action
     */
    public RoleManager setColor(Color color) { fields.color(color == null ? null : color.getRGB()); return this; }

    /**
     * Sets the color.
     *
     * @param rgb a 24-bit RGB value, or {@code null} to leave it unchanged
     * @return this action
     */
    public RoleManager setColor(Integer rgb) { fields.color(rgb); return this; }

    /**
     * Sets the complete permission mask, including feature-gated bits.
     *
     * @param permissions the permissions, or {@code null} to leave it unchanged
     * @return this action
     */
    public RoleManager setPermissions(com.swadedev.micup4j.Permission... permissions) {
        return setPermissions(permissions == null ? null : Arrays.asList(permissions));
    }

    /**
     * Sets the complete permission mask, including feature-gated bits.
     *
     * @param permissions the permissions, or {@code null} to leave it unchanged
     * @return this action
     */
    public RoleManager setPermissions(Collection<com.swadedev.micup4j.Permission> permissions) { fields.permissions(permissions); return this; }

    /**
     * Sets the complete permission mask, including feature-gated bits.
     *
     * @param permissions the mask, or {@code null} to leave it unchanged
     * @return this action
     */
    public RoleManager setPermissions(PermissionSet permissions) { fields.permissions(permissions); return this; }

    /**
     * Sets the complete permission mask as a raw value.
     *
     * @param permissions the mask, or {@code null} to leave it unchanged
     * @return this action
     */
    public RoleManager setPermissions(Long permissions) {
        fields.permissions(permissions == null ? null : new PermissionSet(java.math.BigInteger.valueOf(permissions)));
        return this;
    }

    /**
     * Sets whether the role is displayed separately.
     *
     * @param hoisted the flag, or {@code null} to leave it unchanged
     * @return this action
     */
    public RoleManager setHoisted(Boolean hoisted) { fields.hoist(hoisted); return this; }

    /**
     * Sets whether everyone may mention the role.
     *
     * @param mentionable the flag, or {@code null} to leave it unchanged
     * @return this action
     */
    public RoleManager setMentionable(Boolean mentionable) { fields.mentionable(mentionable); return this; }
}
