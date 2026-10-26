package net.guneyilmaz0.micup4j.interactions.components.text;

/** Text input styles; the values match Micup's. */
public enum TextInputStyle {
    SHORT(1), PARAGRAPH(2), UNKNOWN(-1);

    private final int raw;
    TextInputStyle(int raw) { this.raw = raw; }
    public int getRaw() { return raw; }
    public static TextInputStyle fromKey(int key) {
        for (TextInputStyle style : values()) if (style.raw == key) return style;
        return UNKNOWN;
    }
}
