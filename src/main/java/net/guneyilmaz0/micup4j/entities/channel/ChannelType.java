package net.guneyilmaz0.micup4j.entities.channel;

/** JDA-style channel names with Micup wire values. */
public enum ChannelType {
    TEXT(0, true), PRIVATE(1, false), VOICE(2, true), GROUP(3, false), CATEGORY(4, true), NEWS(5, true),
    LINK(998, true), PERSONAL_NOTES(999, false), UNKNOWN(-1, false);
    public static final ChannelType DM = PRIVATE, GROUP_DM = GROUP, ANNOUNCEMENT = NEWS;
    private final int value;
    private final boolean guild;
    ChannelType(int value, boolean guild) { this.value = value; this.guild = guild; }
    public int getId() { return value; }
    public int getValue() { return value; }
    public boolean isGuild() { return guild; }
    public static ChannelType fromId(int id) {
        for (var type : values()) if (type.value == id) return type;
        return UNKNOWN;
    }
    public static ChannelType fromValue(int id) { return fromId(id); }
}
