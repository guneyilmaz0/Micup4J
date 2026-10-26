package net.guneyilmaz0.micup4j.requests.restaction;

import net.guneyilmaz0.micup4j.entities.Role;
import net.guneyilmaz0.micup4j.internal.RoleFields;
import net.guneyilmaz0.micup4j.permissions.PermissionSet;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiFunction;

/**
 * Fluent role creation, modelled on JDA's {@code RoleAction}. Omitted fields use Micup's defaults; omitted permissions
 * copy the everyone role mask.
 */
public final class RoleAction extends AuditableRestAction<Role> {
    private final RoleFields fields;

    /**
     * Creates the action.
     *
     * @param fields  the payload, shared with the request factory
     * @param factory sends the payload with the given reason
     */
    public RoleAction(RoleFields fields, BiFunction<RoleFields, String, RestAction<Role>> factory) {
        super(reason -> factory.apply(fields, reason), true);
        this.fields = fields;
    }

    @Override public RoleAction reason(String reason) { super.reason(reason); return this; }

    /**
     * Sets the name.
     *
     * @param name 1 to 100 characters
     * @return this action
     * @throws IllegalArgumentException if the name is empty or too long
     */
    public RoleAction setName(String name) { fields.name(name); return this; }

    /**
     * Sets the color.
     *
     * @param color the color, or {@code null} for the default
     * @return this action
     */
    public RoleAction setColor(Color color) { fields.color(color == null ? null : color.getRGB()); return this; }

    /**
     * Sets the color.
     *
     * @param rgb a 24-bit RGB value, or {@code null} for the default
     * @return this action
     */
    public RoleAction setColor(Integer rgb) { fields.color(rgb); return this; }

    /**
     * Sets the complete permission mask, including feature-gated bits.
     *
     * @param permissions the permissions, or {@code null} to copy the everyone mask
     * @return this action
     */
    public RoleAction setPermissions(net.guneyilmaz0.micup4j.Permission... permissions) {
        return setPermissions(permissions == null ? null : Arrays.asList(permissions));
    }

    /**
     * Sets the complete permission mask, including feature-gated bits.
     *
     * @param permissions the permissions, or {@code null} to copy the everyone mask
     * @return this action
     */
    public RoleAction setPermissions(Collection<net.guneyilmaz0.micup4j.Permission> permissions) { fields.permissions(permissions); return this; }

    /**
     * Sets the complete permission mask, including feature-gated bits.
     *
     * @param permissions the mask, or {@code null} to copy the everyone mask
     * @return this action
     */
    public RoleAction setPermissions(PermissionSet permissions) { fields.permissions(permissions); return this; }

    /**
     * Sets the complete permission mask as a raw value.
     *
     * @param permissions the mask, or {@code null} to copy the everyone mask
     * @return this action
     */
    public RoleAction setPermissions(Long permissions) {
        fields.permissions(permissions == null ? null : new PermissionSet(java.math.BigInteger.valueOf(permissions)));
        return this;
    }

    /**
     * Sets whether the role is displayed separately.
     *
     * @param hoisted the flag, or {@code null} for the default
     * @return this action
     */
    public RoleAction setHoisted(Boolean hoisted) { fields.hoist(hoisted); return this; }

    /**
     * Sets whether everyone may mention the role.
     *
     * @param mentionable the flag, or {@code null} for the default
     * @return this action
     */
    public RoleAction setMentionable(Boolean mentionable) { fields.mentionable(mentionable); return this; }
}
