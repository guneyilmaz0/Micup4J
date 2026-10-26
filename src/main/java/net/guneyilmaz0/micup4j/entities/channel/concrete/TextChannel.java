package net.guneyilmaz0.micup4j.entities.channel.concrete;

import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.entities.Guild;
import net.guneyilmaz0.micup4j.entities.channel.ChannelType;

/** Cached text-channel identity with the existing message REST operations. */
public final class TextChannel extends net.guneyilmaz0.micup4j.entities.MessageChannel {
    private final String name, guildId;
    public TextChannel(Micup client, String id, String guildId, String name) {
        super(client, id, guildId); this.name = java.util.Objects.requireNonNull(name); this.guildId = guildId;
    }
    public String getName() { return name; }
    public Guild getGuild() { return getJDA().getGuildReferenceById(guildId); }
    @Override public ChannelType getType() { return ChannelType.TEXT; }
}
