package net.guneyilmaz0.micup4j.entities;

/**
 * Legacy constant aliases.
 *
 * @deprecated Import {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType}; kept through 1.x.
 */
@Deprecated(since = "1.0.0")
public final class ChannelType {
    private ChannelType() {}
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#TEXT}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType TEXT = net.guneyilmaz0.micup4j.entities.channel.ChannelType.TEXT;
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#PRIVATE}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType DM = net.guneyilmaz0.micup4j.entities.channel.ChannelType.PRIVATE;
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#VOICE}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType VOICE = net.guneyilmaz0.micup4j.entities.channel.ChannelType.VOICE;
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#GROUP}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType GROUP_DM = net.guneyilmaz0.micup4j.entities.channel.ChannelType.GROUP;
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#CATEGORY}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType CATEGORY = net.guneyilmaz0.micup4j.entities.channel.ChannelType.CATEGORY;
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#NEWS}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType ANNOUNCEMENT = net.guneyilmaz0.micup4j.entities.channel.ChannelType.NEWS;
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#LINK}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType LINK = net.guneyilmaz0.micup4j.entities.channel.ChannelType.LINK;
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#PERSONAL_NOTES}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType PERSONAL_NOTES = net.guneyilmaz0.micup4j.entities.channel.ChannelType.PERSONAL_NOTES;
    /** Alias of {@link net.guneyilmaz0.micup4j.entities.channel.ChannelType#UNKNOWN}. */
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType UNKNOWN = net.guneyilmaz0.micup4j.entities.channel.ChannelType.UNKNOWN;
    /**
     * Resolves a wire value.
     *
     * @param value the raw channel type
     * @return the type
     */
    public static net.guneyilmaz0.micup4j.entities.channel.ChannelType fromValue(int value) {
        return net.guneyilmaz0.micup4j.entities.channel.ChannelType.fromValue(value);
    }
}
