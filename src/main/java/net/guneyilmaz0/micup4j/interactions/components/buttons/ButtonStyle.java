package net.guneyilmaz0.micup4j.interactions.components.buttons;

/** Button styles; the values match Micup's. */
public enum ButtonStyle {
    PRIMARY(1), SECONDARY(2), SUCCESS(3), DANGER(4), LINK(5), UNKNOWN(-1);

    private final int key;
    ButtonStyle(int key) { this.key = key; }

    /** @return the raw style */
    public int getKey() { return key; }

    /**
     * Resolves a raw style.
     *
     * @param key the raw value
     * @return the style, or {@link #UNKNOWN}
     */
    public static ButtonStyle fromKey(int key) {
        for (ButtonStyle style : values()) if (style.key == key) return style;
        return UNKNOWN;
    }
}
