package com.swadedev.micup4j.entities.channel.concrete;

import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.entities.Guild;
import com.swadedev.micup4j.entities.channel.ChannelType;

/** Cached text-channel identity with the existing message REST operations. */
public final class TextChannel extends com.swadedev.micup4j.entities.MessageChannel {
    private final String name, guildId;
    /**
     * Creates a cached text channel handle; called by the library.
     *
     * @param client  the session
     * @param id      the channel id
     * @param guildId the guild id
     * @param name    the channel name
     */
    public TextChannel(Micup client, String id, String guildId, String name) {
        super(client, id, guildId); this.name = java.util.Objects.requireNonNull(name); this.guildId = guildId;
    }
    /**
     * Returns the channel name.
     *
     * @return the channel name
     */
    public String getName() { return name; }
    /**
     * Returns the cached guild, or an id reference when it is not cached.
     *
     * @return the cached guild, or an id reference when it is not cached
     */
    public Guild getGuild() { return getJDA().getGuildReferenceById(guildId); }
    @Override public ChannelType getType() { return ChannelType.TEXT; }
}
