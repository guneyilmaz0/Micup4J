package com.swadedev.micup4j.interactions.components.text;

/** Text input styles; the values match Micup's. */
public enum TextInputStyle {
    /** A single-line field (1). */
    SHORT(1),
    /** A multi-line field (2). */
    PARAGRAPH(2),
    /** An unrecognized style. */
    UNKNOWN(-1);

    private final int raw;
    TextInputStyle(int raw) { this.raw = raw; }

    /**
     * Returns the Micup wire value.
     *
     * @return the raw style
     */
    public int getRaw() { return raw; }

    /**
     * Resolves a wire value.
     *
     * @param key the raw style
     * @return the style, or {@link #UNKNOWN}
     */
    public static TextInputStyle fromKey(int key) {
        for (TextInputStyle style : values()) if (style.raw == key) return style;
        return UNKNOWN;
    }
}
