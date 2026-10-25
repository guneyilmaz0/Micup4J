package net.guneyilmaz0.micup4j.config;

/** Per-client guild bound and per-guild entity bounds. Zero disables that collection. */
public record CachePolicy(int maxGuilds, int maxRolesPerGuild, int maxChannelsPerGuild, int maxMembersPerGuild) {
    public CachePolicy {
        if (maxGuilds < 0 || maxGuilds > 10000 || maxRolesPerGuild < 0 || maxRolesPerGuild > 10000
                || maxChannelsPerGuild < 0 || maxChannelsPerGuild > 10000 || maxMembersPerGuild < 0 || maxMembersPerGuild > 100000)
            throw new IllegalArgumentException("Invalid entity cache limits");
    }
    public static CachePolicy defaults() { return new CachePolicy(128, 512, 512, 256); }
    public static CachePolicy light() { return new CachePolicy(0, 0, 0, 0); }
}
