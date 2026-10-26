package net.guneyilmaz0.micup4j.interactions;

/**
 * Legacy value option types used by {@link SlashCommand}.
 *
 * @deprecated Use {@link net.guneyilmaz0.micup4j.interactions.commands.OptionType}; kept through 1.x.
 */
@Deprecated(since = "1.0.0")
public enum OptionType {
    /** Text (3). */
    STRING(3),
    /** A 64-bit integer (4). */
    INTEGER(4),
    /** A boolean (5). */
    BOOLEAN(5),
    /** A user or member (6). */
    USER(6),
    /** A channel (7). */
    CHANNEL(7),
    /** A role (8). */
    ROLE(8),
    /** A double-precision number (10). */
    NUMBER(10);
    private final int key;
    OptionType(int key) { this.key = key; }
    /**
     * Returns the Micup wire value.
     *
     * @return the raw option type
     */
    public int getKey() { return key; }
}
