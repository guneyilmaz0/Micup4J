package net.guneyilmaz0.micup4j;

/** JDA-style permission names mapped to Micup's registry, not Discord bit offsets. */
public enum Permission {
    CREATE_INSTANT_INVITE(net.guneyilmaz0.micup4j.permissions.Permission.CREATE_INSTANT_INVITE),
    KICK_MEMBERS(net.guneyilmaz0.micup4j.permissions.Permission.KICK_MEMBERS),
    BAN_MEMBERS(net.guneyilmaz0.micup4j.permissions.Permission.BAN_MEMBERS),
    ADMINISTRATOR(net.guneyilmaz0.micup4j.permissions.Permission.ADMINISTRATOR),
    MANAGE_CHANNEL(net.guneyilmaz0.micup4j.permissions.Permission.MANAGE_CHANNELS),
    MANAGE_SERVER(net.guneyilmaz0.micup4j.permissions.Permission.MANAGE_GUILD),
    MESSAGE_ADD_REACTION(net.guneyilmaz0.micup4j.permissions.Permission.ADD_REACTIONS),
    VIEW_AUDIT_LOGS(net.guneyilmaz0.micup4j.permissions.Permission.VIEW_AUDIT_LOG),
    PRIORITY_SPEAKER(net.guneyilmaz0.micup4j.permissions.Permission.PRIORITY_SPEAKER),
    VOICE_STREAM(net.guneyilmaz0.micup4j.permissions.Permission.STREAM),
    VIEW_CHANNEL(net.guneyilmaz0.micup4j.permissions.Permission.VIEW_CHANNEL),
    MESSAGE_SEND(net.guneyilmaz0.micup4j.permissions.Permission.SEND_MESSAGES),
    MESSAGE_TTS(net.guneyilmaz0.micup4j.permissions.Permission.SEND_TTS_MESSAGES),
    MESSAGE_MANAGE(net.guneyilmaz0.micup4j.permissions.Permission.MANAGE_MESSAGES),
    MESSAGE_EMBED_LINKS(net.guneyilmaz0.micup4j.permissions.Permission.EMBED_LINKS),
    MESSAGE_ATTACH_FILES(net.guneyilmaz0.micup4j.permissions.Permission.ATTACH_FILES),
    MESSAGE_HISTORY(net.guneyilmaz0.micup4j.permissions.Permission.READ_MESSAGE_HISTORY),
    MESSAGE_MENTION_EVERYONE(net.guneyilmaz0.micup4j.permissions.Permission.MENTION_EVERYONE),
    MESSAGE_EXT_EMOJI(net.guneyilmaz0.micup4j.permissions.Permission.USE_EXTERNAL_EMOJIS),
    VOICE_CONNECT(net.guneyilmaz0.micup4j.permissions.Permission.CONNECT),
    VOICE_SPEAK(net.guneyilmaz0.micup4j.permissions.Permission.SPEAK),
    VOICE_MUTE_OTHERS(net.guneyilmaz0.micup4j.permissions.Permission.MUTE_MEMBERS),
    VOICE_DEAF_OTHERS(net.guneyilmaz0.micup4j.permissions.Permission.DEAFEN_MEMBERS),
    VOICE_MOVE_OTHERS(net.guneyilmaz0.micup4j.permissions.Permission.MOVE_MEMBERS),
    VOICE_USE_VAD(net.guneyilmaz0.micup4j.permissions.Permission.USE_VAD),
    NICKNAME_CHANGE(net.guneyilmaz0.micup4j.permissions.Permission.CHANGE_NICKNAME),
    NICKNAME_MANAGE(net.guneyilmaz0.micup4j.permissions.Permission.MANAGE_NICKNAMES),
    MANAGE_ROLES(net.guneyilmaz0.micup4j.permissions.Permission.MANAGE_ROLES),
    MANAGE_PERMISSIONS(net.guneyilmaz0.micup4j.permissions.Permission.MANAGE_ROLES),
    MANAGE_WEBHOOKS(net.guneyilmaz0.micup4j.permissions.Permission.MANAGE_WEBHOOKS),
    MANAGE_GUILD_EXPRESSIONS(net.guneyilmaz0.micup4j.permissions.Permission.MANAGE_EXPRESSIONS),
    MESSAGE_EXT_STICKER(net.guneyilmaz0.micup4j.permissions.Permission.USE_EXTERNAL_STICKERS),
    MODERATE_MEMBERS(net.guneyilmaz0.micup4j.permissions.Permission.MODERATE_MEMBERS),
    CREATE_GUILD_EXPRESSIONS(net.guneyilmaz0.micup4j.permissions.Permission.CREATE_EXPRESSIONS),
    PIN_MESSAGES(net.guneyilmaz0.micup4j.permissions.Permission.PIN_MESSAGES),
    BYPASS_SLOWMODE(net.guneyilmaz0.micup4j.permissions.Permission.BYPASS_SLOWMODE);
    private final net.guneyilmaz0.micup4j.permissions.Permission permission;
    Permission(net.guneyilmaz0.micup4j.permissions.Permission permission) { this.permission = permission; }
    public net.guneyilmaz0.micup4j.permissions.Permission toMicupPermission() { return permission; }
    public long getRawValue() { return 1L << permission.getBit(); }
    public int getOffset() { return permission.getBit(); }
}
