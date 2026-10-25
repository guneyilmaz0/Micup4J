package net.guneyilmaz0.micup4j.entities;

public enum ChannelType {
    TEXT(0), DM(1), VOICE(2), GROUP_DM(3), CATEGORY(4), ANNOUNCEMENT(5), LINK(998), PERSONAL_NOTES(999), UNKNOWN(-1);
    private final int value;
    ChannelType(int value) { this.value = value; }
    public int getValue() { return value; }
    public static ChannelType fromValue(int value) {
        for (var type : values()) if (type.value == value) return type;
        return UNKNOWN;
    }
}
