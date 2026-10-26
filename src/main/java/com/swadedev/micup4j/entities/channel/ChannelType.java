package com.swadedev.micup4j.entities.channel;

/** JDA-style channel names with Micup wire values. */
public enum ChannelType {
    /** A guild text channel (0). */
    TEXT(0, true),
    /** A one-to-one direct message (1). */
    PRIVATE(1, false),
    /** A guild voice channel, which also has messages (2). */
    VOICE(2, true),
    /** A group direct message (3). */
    GROUP(3, false),
    /** A guild category (4). */
    CATEGORY(4, true),
    /** A guild announcement channel (5). */
    NEWS(5, true),
    /** A guild link channel without messages (998). */
    LINK(998, true),
    /** The current user's personal notes channel (999). */
    PERSONAL_NOTES(999, false),
    /** An unrecognized channel type. */
    UNKNOWN(-1, false);

    /** JDA-style alias of {@link #PRIVATE}. */
    public static final ChannelType DM = PRIVATE;
    /** Alias of {@link #GROUP}. */
    public static final ChannelType GROUP_DM = GROUP;
    /** Alias of {@link #NEWS}. */
    public static final ChannelType ANNOUNCEMENT = NEWS;

    private final int value;
    private final boolean guild;
    ChannelType(int value, boolean guild) { this.value = value; this.guild = guild; }

    /**
     * Returns the Micup wire value.
     *
     * @return the raw channel type
     */
    public int getId() { return value; }

    /**
     * Returns the Micup wire value; an alias of {@link #getId()}.
     *
     * @return the raw channel type
     */
    public int getValue() { return value; }

    /**
     * Returns whether channels of this type belong to a guild.
     *
     * @return {@code true} for guild channel types
     */
    public boolean isGuild() { return guild; }

    /**
     * Resolves a wire value.
     *
     * @param id the raw channel type
     * @return the type, or {@link #UNKNOWN}
     */
    public static ChannelType fromId(int id) {
        for (var type : values()) if (type.value == id) return type;
        return UNKNOWN;
    }

    /**
     * Resolves a wire value; an alias of {@link #fromId(int)}.
     *
     * @param id the raw channel type
     * @return the type, or {@link #UNKNOWN}
     */
    public static ChannelType fromValue(int id) { return fromId(id); }
}
