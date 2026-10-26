package net.guneyilmaz0.micup4j.entities;

import java.awt.Color;
import java.util.Objects;

/** Micup's primary role color and optional static gradient color. No holographic style is implied. */
public final class RoleColors {
    public static final RoleColors DEFAULT = new RoleColors(0, null);
    private final int primary;
    private final Integer secondary;
    public RoleColors(int primary, Integer secondary) {
        if (primary < 0 || primary > 0xFFFFFF || (secondary != null && (secondary < 0 || secondary > 0xFFFFFF)))
            throw new IllegalArgumentException("Role colors must be 24-bit RGB values");
        this.primary = primary; this.secondary = secondary;
    }
    public Color getPrimary() { return primary == 0 ? null : new Color(primary); }
    public int getPrimaryRaw() { return primary; }
    public Color getSecondary() { return secondary == null ? null : new Color(secondary); }
    public int getSecondaryRaw() { return secondary == null ? 0 : secondary; }
    public boolean isDefault() { return primary == 0 && secondary == null; }
    public boolean isGradient() { return secondary != null; }
    public boolean isSolid() { return primary != 0 && secondary == null; }
    @Override public boolean equals(Object other) {
        return other instanceof RoleColors colors && primary == colors.primary && Objects.equals(secondary, colors.secondary);
    }
    @Override public int hashCode() { return Objects.hash(primary, secondary); }
}
