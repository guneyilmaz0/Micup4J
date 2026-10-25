package net.guneyilmaz0.micup4j.interactions;

/** Value option types supported by Micup. Subcommands and option constraints are not modeled yet. */
public enum OptionType {
    STRING(3), INTEGER(4), BOOLEAN(5), USER(6), CHANNEL(7), ROLE(8), NUMBER(10);
    private final int key;
    OptionType(int key) { this.key = key; }
    public int getKey() { return key; }
}
