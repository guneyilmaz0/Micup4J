package com.swadedev.micup4j.entities;

import java.awt.Color;
import java.util.Objects;

/** Micup's primary role color and optional static gradient color. No holographic style is implied. */
public final class RoleColors {
    /** The default colors: no primary color and no gradient. */
    public static final RoleColors DEFAULT = new RoleColors(0, null);
    private final int primary;
    private final Integer secondary;
    /**
     * Creates role colors.
     *
     * @param primary   the primary 24-bit RGB color, 0 for the default
     * @param secondary the static gradient color, or {@code null}
     * @throws IllegalArgumentException if a color is outside 0-0xFFFFFF
     */
    public RoleColors(int primary, Integer secondary) {
        if (primary < 0 || primary > 0xFFFFFF || (secondary != null && (secondary < 0 || secondary > 0xFFFFFF)))
            throw new IllegalArgumentException("Role colors must be 24-bit RGB values");
        this.primary = primary; this.secondary = secondary;
    }
    /**
     * Returns the primary color, or {@code null} for the default.
     *
     * @return the primary color, or {@code null} for the default
     */
    public Color getPrimary() { return primary == 0 ? null : new Color(primary); }
    /**
     * Returns the primary color as RGB, 0 for the default.
     *
     * @return the primary color as RGB, 0 for the default
     */
    public int getPrimaryRaw() { return primary; }
    /**
     * Returns the gradient color, or {@code null}.
     *
     * @return the gradient color, or {@code null}
     */
    public Color getSecondary() { return secondary == null ? null : new Color(secondary); }
    /**
     * Returns the gradient color as RGB, 0 when absent.
     *
     * @return the gradient color as RGB, 0 when absent
     */
    public int getSecondaryRaw() { return secondary == null ? 0 : secondary; }
    /**
     * Returns whether neither color is set.
     *
     * @return whether neither color is set
     */
    public boolean isDefault() { return primary == 0 && secondary == null; }
    /**
     * Returns whether a gradient color is set.
     *
     * @return whether a gradient color is set
     */
    public boolean isGradient() { return secondary != null; }
    /**
     * Returns whether only a primary color is set.
     *
     * @return whether only a primary color is set
     */
    public boolean isSolid() { return primary != 0 && secondary == null; }
    @Override public boolean equals(Object other) {
        return other instanceof RoleColors colors && primary == colors.primary && Objects.equals(secondary, colors.secondary);
    }
    @Override public int hashCode() { return Objects.hash(primary, secondary); }
}
