package net.guneyilmaz0.micup4j.interactions.commands;

/** JDA-style names for Micup-supported value option types. */
public enum OptionType {
    UNKNOWN(-1), STRING(3), INTEGER(4), BOOLEAN(5), USER(6), CHANNEL(7), ROLE(8), NUMBER(10);
    private final int key;
    OptionType(int key) { this.key = key; }
    public int getKey() { return key; }
    public static OptionType fromKey(int key) {
        for (var type : values()) if (type.key == key) return type;
        return UNKNOWN;
    }
    public net.guneyilmaz0.micup4j.interactions.OptionType toLegacy() {
        if (this == UNKNOWN) throw new IllegalArgumentException("Unknown option type is not a command definition");
        return net.guneyilmaz0.micup4j.interactions.OptionType.valueOf(name());
    }
}
