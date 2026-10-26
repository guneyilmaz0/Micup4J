package com.swadedev.micup4j.entities;

/**
 * Legacy constant aliases.
 *
 * @deprecated Import {@link com.swadedev.micup4j.entities.channel.ChannelType}; kept through 1.x.
 */
@Deprecated(since = "1.0.0")
public final class ChannelType {
    private ChannelType() {}
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#TEXT}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType TEXT = com.swadedev.micup4j.entities.channel.ChannelType.TEXT;
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#PRIVATE}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType DM = com.swadedev.micup4j.entities.channel.ChannelType.PRIVATE;
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#VOICE}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType VOICE = com.swadedev.micup4j.entities.channel.ChannelType.VOICE;
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#GROUP}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType GROUP_DM = com.swadedev.micup4j.entities.channel.ChannelType.GROUP;
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#CATEGORY}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType CATEGORY = com.swadedev.micup4j.entities.channel.ChannelType.CATEGORY;
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#NEWS}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType ANNOUNCEMENT = com.swadedev.micup4j.entities.channel.ChannelType.NEWS;
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#LINK}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType LINK = com.swadedev.micup4j.entities.channel.ChannelType.LINK;
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#PERSONAL_NOTES}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType PERSONAL_NOTES = com.swadedev.micup4j.entities.channel.ChannelType.PERSONAL_NOTES;
    /** Alias of {@link com.swadedev.micup4j.entities.channel.ChannelType#UNKNOWN}. */
    public static final com.swadedev.micup4j.entities.channel.ChannelType UNKNOWN = com.swadedev.micup4j.entities.channel.ChannelType.UNKNOWN;
    /**
     * Resolves a wire value.
     *
     * @param value the raw channel type
     * @return the type
     */
    public static com.swadedev.micup4j.entities.channel.ChannelType fromValue(int value) {
        return com.swadedev.micup4j.entities.channel.ChannelType.fromValue(value);
    }
}
