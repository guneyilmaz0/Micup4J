package net.guneyilmaz0.micup4j.permissions;

/** Permission flags defined by the Micup v1 permission registry. */
public enum Permission {
    /** Create invites (bit 0). */
    CREATE_INSTANT_INVITE(0),
    /** Remove members from the guild (bit 1). */
    KICK_MEMBERS(1),
    /** Ban and unban users (bit 2). */
    BAN_MEMBERS(2),
    /** Every permission, bypassing channel overwrites (bit 3). */
    ADMINISTRATOR(3),
    /** Create, edit and delete channels (bit 4). */
    MANAGE_CHANNELS(4),
    /** Edit guild settings (bit 5). */
    MANAGE_GUILD(5),
    /** Add new reactions (bit 6). */
    ADD_REACTIONS(6),
    /** Read the audit log (bit 7). */
    VIEW_AUDIT_LOG(7),
    /** Speak with priority in voice (bit 8). */
    PRIORITY_SPEAKER(8),
    /** Stream in voice channels (bit 9). */
    STREAM(9),
    /** See a channel and read its messages (bit 10). */
    VIEW_CHANNEL(10),
    /** Send messages (bit 11). */
    SEND_MESSAGES(11),
    /** Send text-to-speech messages (bit 12). */
    SEND_TTS_MESSAGES(12),
    /** Delete and manage others' messages (bit 13). */
    MANAGE_MESSAGES(13),
    /** Show link embeds (bit 14). */
    EMBED_LINKS(14),
    /** Upload files (bit 15). */
    ATTACH_FILES(15),
    /** Read earlier messages (bit 16). */
    READ_MESSAGE_HISTORY(16),
    /** Mention everyone, here and all roles (bit 17). */
    MENTION_EVERYONE(17),
    /** Use emojis from other guilds (bit 18). */
    USE_EXTERNAL_EMOJIS(18),
    /** Join voice channels (bit 20). */
    CONNECT(20),
    /** Speak in voice channels (bit 21). */
    SPEAK(21),
    /** Mute members in voice (bit 22). */
    MUTE_MEMBERS(22),
    /** Deafen members in voice (bit 23). */
    DEAFEN_MEMBERS(23),
    /** Move members between voice channels (bit 24). */
    MOVE_MEMBERS(24),
    /** Use voice activity detection (bit 25). */
    USE_VAD(25),
    /** Change one's own nickname (bit 26). */
    CHANGE_NICKNAME(26),
    /** Change other members' nicknames (bit 27). */
    MANAGE_NICKNAMES(27),
    /** Create, edit and assign roles below one's own (bit 28). */
    MANAGE_ROLES(28),
    /** Manage webhooks (bit 29). */
    MANAGE_WEBHOOKS(29),
    /** Manage any emoji, sticker and sound (bit 30). */
    MANAGE_EXPRESSIONS(30),
    /** Use stickers from other guilds (bit 37). */
    USE_EXTERNAL_STICKERS(37),
    /** Time members out (bit 40). */
    MODERATE_MEMBERS(40),
    /** Create emojis, stickers and sounds and manage one's own (bit 43). */
    CREATE_EXPRESSIONS(43),
    /** Pin and unpin messages (bit 51). */
    PIN_MESSAGES(51),
    /** Ignore slowmode (bit 52). */
    BYPASS_SLOWMODE(52),
    /** Change a voice channel's region (bit 53). */
    UPDATE_RTC_REGION(53),
    /** See a channel's member list; feature-gated on write (bit 54). */
    VIEW_CHANNEL_MEMBERS(54);

    private final int bit;
    Permission(int bit) { this.bit = bit; }
    /**
     * Returns the bit position in Micup's 64-bit permission mask.
     *
     * @return the bit, 0-63
     */
    public int getBit() { return bit; }

    /**
     * Returns the feature a role or overwrite write must declare in {@code X-Fluxer-Features} to set or clear this bit.
     * Without the declaration Micup keeps the stored value of a gated bit.
     *
     * @return the feature name, or {@code null} when the bit is not feature-gated
     */
    public String getWriteFeature() { return this == VIEW_CHANNEL_MEMBERS ? "view_channel_members_permission" : null; }

    /**
     * Returns the comma-separated declaration of every feature that gates a permission write, so a submitted mask is
     * stored exactly as sent.
     *
     * @return the {@code X-Fluxer-Features} header value
     */
    public static String writeFeatureDeclaration() {
        return java.util.Arrays.stream(values()).map(Permission::getWriteFeature).filter(java.util.Objects::nonNull)
                .distinct().collect(java.util.stream.Collectors.joining(","));
    }
}
