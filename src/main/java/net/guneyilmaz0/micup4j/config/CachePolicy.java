package net.guneyilmaz0.micup4j.config;

/**
 * Per-client guild bound and per-guild entity bounds. Zero disables that collection; zero guilds disables all storage.
 *
 * @param maxGuilds           guilds per client, 0-10,000
 * @param maxRolesPerGuild    roles per guild, 0-10,000
 * @param maxChannelsPerGuild channels per guild, 0-10,000
 * @param maxMembersPerGuild  observed members per guild, 0-100,000
 */
public record CachePolicy(int maxGuilds, int maxRolesPerGuild, int maxChannelsPerGuild, int maxMembersPerGuild) {
    /**
     * Validates the bounds.
     *
     * @throws IllegalArgumentException if a bound is outside its range
     */
    public CachePolicy {
        if (maxGuilds < 0 || maxGuilds > 10000 || maxRolesPerGuild < 0 || maxRolesPerGuild > 10000
                || maxChannelsPerGuild < 0 || maxChannelsPerGuild > 10000 || maxMembersPerGuild < 0 || maxMembersPerGuild > 100000)
            throw new IllegalArgumentException("Invalid entity cache limits");
    }
    /**
     * Returns the default bounds: 128 guilds, 512 roles and channels, and 256 members per guild.
     *
     * @return the default bounds: 128 guilds, 512 roles and channels, and 256 members per guild
     */
    public static CachePolicy defaults() { return new CachePolicy(128, 512, 512, 256); }
    /**
     * Returns bounds that cache nothing.
     *
     * @return bounds that cache nothing
     */
    public static CachePolicy light() { return new CachePolicy(0, 0, 0, 0); }
}
