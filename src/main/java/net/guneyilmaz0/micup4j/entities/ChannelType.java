package net.guneyilmaz0.micup4j.entities;

/** Legacy constant aliases. New source should import entities.channel.ChannelType. */
public final class ChannelType {
    private ChannelType() {}
    public static final net.guneyilmaz0.micup4j.entities.channel.ChannelType
        TEXT = net.guneyilmaz0.micup4j.entities.channel.ChannelType.TEXT,
        DM = net.guneyilmaz0.micup4j.entities.channel.ChannelType.PRIVATE,
        VOICE = net.guneyilmaz0.micup4j.entities.channel.ChannelType.VOICE,
        GROUP_DM = net.guneyilmaz0.micup4j.entities.channel.ChannelType.GROUP,
        CATEGORY = net.guneyilmaz0.micup4j.entities.channel.ChannelType.CATEGORY,
        ANNOUNCEMENT = net.guneyilmaz0.micup4j.entities.channel.ChannelType.NEWS,
        LINK = net.guneyilmaz0.micup4j.entities.channel.ChannelType.LINK,
        PERSONAL_NOTES = net.guneyilmaz0.micup4j.entities.channel.ChannelType.PERSONAL_NOTES,
        UNKNOWN = net.guneyilmaz0.micup4j.entities.channel.ChannelType.UNKNOWN;
    public static net.guneyilmaz0.micup4j.entities.channel.ChannelType fromValue(int value) {
        return net.guneyilmaz0.micup4j.entities.channel.ChannelType.fromValue(value);
    }
}
