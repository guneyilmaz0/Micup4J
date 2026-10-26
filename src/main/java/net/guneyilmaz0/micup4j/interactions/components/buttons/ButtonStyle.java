package net.guneyilmaz0.micup4j.interactions.components.buttons;

/** Button styles; the values match Micup's. */
public enum ButtonStyle {
    /** The main action (1). */
    PRIMARY(1),
    /** A secondary action (2). */
    SECONDARY(2),
    /** A positive action (3). */
    SUCCESS(3),
    /** A destructive action (4). */
    DANGER(4),
    /** Opens a URL and sends nothing to the bot (5). */
    LINK(5),
    /** An unrecognized style. */
    UNKNOWN(-1);

    private final int key;
    ButtonStyle(int key) { this.key = key; }

    /**
     * Returns the Micup wire value.
     *
     * @return the raw style
     */
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
