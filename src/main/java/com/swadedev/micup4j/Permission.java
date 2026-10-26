package com.swadedev.micup4j;

/** JDA-style permission names mapped to Micup's registry, not Discord bit offsets. */
public enum Permission {
    /** Create invites; maps to {@link com.swadedev.micup4j.permissions.Permission#CREATE_INSTANT_INVITE}. */
    CREATE_INSTANT_INVITE(com.swadedev.micup4j.permissions.Permission.CREATE_INSTANT_INVITE),
    /** Remove members from the guild; maps to {@link com.swadedev.micup4j.permissions.Permission#KICK_MEMBERS}. */
    KICK_MEMBERS(com.swadedev.micup4j.permissions.Permission.KICK_MEMBERS),
    /** Ban and unban users; maps to {@link com.swadedev.micup4j.permissions.Permission#BAN_MEMBERS}. */
    BAN_MEMBERS(com.swadedev.micup4j.permissions.Permission.BAN_MEMBERS),
    /** Every permission, bypassing channel overwrites; maps to {@link com.swadedev.micup4j.permissions.Permission#ADMINISTRATOR}. */
    ADMINISTRATOR(com.swadedev.micup4j.permissions.Permission.ADMINISTRATOR),
    /** Create, edit and delete channels; maps to {@link com.swadedev.micup4j.permissions.Permission#MANAGE_CHANNELS}. */
    MANAGE_CHANNEL(com.swadedev.micup4j.permissions.Permission.MANAGE_CHANNELS),
    /** Edit guild settings; maps to {@link com.swadedev.micup4j.permissions.Permission#MANAGE_GUILD}. */
    MANAGE_SERVER(com.swadedev.micup4j.permissions.Permission.MANAGE_GUILD),
    /** Add new reactions; maps to {@link com.swadedev.micup4j.permissions.Permission#ADD_REACTIONS}. */
    MESSAGE_ADD_REACTION(com.swadedev.micup4j.permissions.Permission.ADD_REACTIONS),
    /** Read the audit log; maps to {@link com.swadedev.micup4j.permissions.Permission#VIEW_AUDIT_LOG}. */
    VIEW_AUDIT_LOGS(com.swadedev.micup4j.permissions.Permission.VIEW_AUDIT_LOG),
    /** Speak with priority in voice; maps to {@link com.swadedev.micup4j.permissions.Permission#PRIORITY_SPEAKER}. */
    PRIORITY_SPEAKER(com.swadedev.micup4j.permissions.Permission.PRIORITY_SPEAKER),
    /** Stream in voice channels; maps to {@link com.swadedev.micup4j.permissions.Permission#STREAM}. */
    VOICE_STREAM(com.swadedev.micup4j.permissions.Permission.STREAM),
    /** See a channel and read its messages; maps to {@link com.swadedev.micup4j.permissions.Permission#VIEW_CHANNEL}. */
    VIEW_CHANNEL(com.swadedev.micup4j.permissions.Permission.VIEW_CHANNEL),
    /** Send messages; maps to {@link com.swadedev.micup4j.permissions.Permission#SEND_MESSAGES}. */
    MESSAGE_SEND(com.swadedev.micup4j.permissions.Permission.SEND_MESSAGES),
    /** Send text-to-speech messages; maps to {@link com.swadedev.micup4j.permissions.Permission#SEND_TTS_MESSAGES}. */
    MESSAGE_TTS(com.swadedev.micup4j.permissions.Permission.SEND_TTS_MESSAGES),
    /** Delete and manage others' messages; maps to {@link com.swadedev.micup4j.permissions.Permission#MANAGE_MESSAGES}. */
    MESSAGE_MANAGE(com.swadedev.micup4j.permissions.Permission.MANAGE_MESSAGES),
    /** Show link embeds; maps to {@link com.swadedev.micup4j.permissions.Permission#EMBED_LINKS}. */
    MESSAGE_EMBED_LINKS(com.swadedev.micup4j.permissions.Permission.EMBED_LINKS),
    /** Upload files; maps to {@link com.swadedev.micup4j.permissions.Permission#ATTACH_FILES}. */
    MESSAGE_ATTACH_FILES(com.swadedev.micup4j.permissions.Permission.ATTACH_FILES),
    /** Read earlier messages; maps to {@link com.swadedev.micup4j.permissions.Permission#READ_MESSAGE_HISTORY}. */
    MESSAGE_HISTORY(com.swadedev.micup4j.permissions.Permission.READ_MESSAGE_HISTORY),
    /** Mention everyone, here and all roles; maps to {@link com.swadedev.micup4j.permissions.Permission#MENTION_EVERYONE}. */
    MESSAGE_MENTION_EVERYONE(com.swadedev.micup4j.permissions.Permission.MENTION_EVERYONE),
    /** Use emojis from other guilds; maps to {@link com.swadedev.micup4j.permissions.Permission#USE_EXTERNAL_EMOJIS}. */
    MESSAGE_EXT_EMOJI(com.swadedev.micup4j.permissions.Permission.USE_EXTERNAL_EMOJIS),
    /** Join voice channels; maps to {@link com.swadedev.micup4j.permissions.Permission#CONNECT}. */
    VOICE_CONNECT(com.swadedev.micup4j.permissions.Permission.CONNECT),
    /** Speak in voice channels; maps to {@link com.swadedev.micup4j.permissions.Permission#SPEAK}. */
    VOICE_SPEAK(com.swadedev.micup4j.permissions.Permission.SPEAK),
    /** Mute members in voice; maps to {@link com.swadedev.micup4j.permissions.Permission#MUTE_MEMBERS}. */
    VOICE_MUTE_OTHERS(com.swadedev.micup4j.permissions.Permission.MUTE_MEMBERS),
    /** Deafen members in voice; maps to {@link com.swadedev.micup4j.permissions.Permission#DEAFEN_MEMBERS}. */
    VOICE_DEAF_OTHERS(com.swadedev.micup4j.permissions.Permission.DEAFEN_MEMBERS),
    /** Move members between voice channels; maps to {@link com.swadedev.micup4j.permissions.Permission#MOVE_MEMBERS}. */
    VOICE_MOVE_OTHERS(com.swadedev.micup4j.permissions.Permission.MOVE_MEMBERS),
    /** Use voice activity detection; maps to {@link com.swadedev.micup4j.permissions.Permission#USE_VAD}. */
    VOICE_USE_VAD(com.swadedev.micup4j.permissions.Permission.USE_VAD),
    /** Change one's own nickname; maps to {@link com.swadedev.micup4j.permissions.Permission#CHANGE_NICKNAME}. */
    NICKNAME_CHANGE(com.swadedev.micup4j.permissions.Permission.CHANGE_NICKNAME),
    /** Change other members' nicknames; maps to {@link com.swadedev.micup4j.permissions.Permission#MANAGE_NICKNAMES}. */
    NICKNAME_MANAGE(com.swadedev.micup4j.permissions.Permission.MANAGE_NICKNAMES),
    /** Create, edit and assign roles below one's own; maps to {@link com.swadedev.micup4j.permissions.Permission#MANAGE_ROLES}. */
    MANAGE_ROLES(com.swadedev.micup4j.permissions.Permission.MANAGE_ROLES),
    /** Create, edit and assign roles below one's own; maps to {@link com.swadedev.micup4j.permissions.Permission#MANAGE_ROLES}. */
    MANAGE_PERMISSIONS(com.swadedev.micup4j.permissions.Permission.MANAGE_ROLES),
    /** Manage webhooks; maps to {@link com.swadedev.micup4j.permissions.Permission#MANAGE_WEBHOOKS}. */
    MANAGE_WEBHOOKS(com.swadedev.micup4j.permissions.Permission.MANAGE_WEBHOOKS),
    /** Manage any emoji, sticker and sound; maps to {@link com.swadedev.micup4j.permissions.Permission#MANAGE_EXPRESSIONS}. */
    MANAGE_GUILD_EXPRESSIONS(com.swadedev.micup4j.permissions.Permission.MANAGE_EXPRESSIONS),
    /** Use stickers from other guilds; maps to {@link com.swadedev.micup4j.permissions.Permission#USE_EXTERNAL_STICKERS}. */
    MESSAGE_EXT_STICKER(com.swadedev.micup4j.permissions.Permission.USE_EXTERNAL_STICKERS),
    /** Time members out; maps to {@link com.swadedev.micup4j.permissions.Permission#MODERATE_MEMBERS}. */
    MODERATE_MEMBERS(com.swadedev.micup4j.permissions.Permission.MODERATE_MEMBERS),
    /** Create emojis, stickers and sounds and manage one's own; maps to {@link com.swadedev.micup4j.permissions.Permission#CREATE_EXPRESSIONS}. */
    CREATE_GUILD_EXPRESSIONS(com.swadedev.micup4j.permissions.Permission.CREATE_EXPRESSIONS),
    /** Pin and unpin messages; maps to {@link com.swadedev.micup4j.permissions.Permission#PIN_MESSAGES}. */
    PIN_MESSAGES(com.swadedev.micup4j.permissions.Permission.PIN_MESSAGES),
    /** Ignore slowmode; maps to {@link com.swadedev.micup4j.permissions.Permission#BYPASS_SLOWMODE}. */
    BYPASS_SLOWMODE(com.swadedev.micup4j.permissions.Permission.BYPASS_SLOWMODE);
    private final com.swadedev.micup4j.permissions.Permission permission;
    Permission(com.swadedev.micup4j.permissions.Permission permission) { this.permission = permission; }
    /**
     * Returns the Micup registry entry this name maps to.
     *
     * @return the Micup permission
     */
    public com.swadedev.micup4j.permissions.Permission toMicupPermission() { return permission; }
    /**
     * Returns the single-bit mask, as JDA's {@code getRawValue()}.
     *
     * @return {@code 1L << offset}; may be negative for bit 63
     */
    public long getRawValue() { return 1L << permission.getBit(); }
    /**
     * Returns the bit position, as JDA's {@code getOffset()}.
     *
     * @return the Micup bit
     */
    public int getOffset() { return permission.getBit(); }
}
