package net.guneyilmaz0.micup4j.interactions.commands;

/** JDA-style names for Micup-supported value option types. */
public enum OptionType {
    /** An unrecognized option type. */
    UNKNOWN(-1),
    /** A subcommand (1). */
    SUB_COMMAND(1),
    /** A subcommand group (2). */
    SUB_COMMAND_GROUP(2),
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

    /**
     * Resolves a wire value.
     *
     * @param key the raw option type
     * @return the type, or {@link #UNKNOWN}
     */
    public static OptionType fromKey(int key) {
        for (var type : values()) if (type.key == key) return type;
        return UNKNOWN;
    }

    /**
     * Converts to the legacy option type.
     *
     * @return the legacy constant
     * @throws IllegalArgumentException for subcommands, groups and {@link #UNKNOWN}
     * @deprecated Legacy definitions are deprecated; kept through 1.x.
     */
    @Deprecated(since = "1.0.0")
    public net.guneyilmaz0.micup4j.interactions.OptionType toLegacy() {
        if (key < 3) throw new IllegalArgumentException("Unknown option type is not a command definition");
        return net.guneyilmaz0.micup4j.interactions.OptionType.valueOf(name());
    }
}
