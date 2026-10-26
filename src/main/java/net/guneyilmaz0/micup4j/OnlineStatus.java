package net.guneyilmaz0.micup4j;

public enum OnlineStatus {
    ONLINE("online"), IDLE("idle"), DO_NOT_DISTURB("dnd"), INVISIBLE("invisible"), OFFLINE("offline"), UNKNOWN("unknown");
    private final String key;
    OnlineStatus(String key) { this.key = key; }
    public String getKey() { return key; }
    public static OnlineStatus fromKey(String key) {
        for (var status : values()) if (status.key.equals(key)) return status;
        return UNKNOWN;
    }
    public static OnlineStatus normalize(OnlineStatus status) {
        if (status == UNKNOWN) throw new IllegalArgumentException("UNKNOWN is not a publishable status");
        return status == null || status == OFFLINE ? INVISIBLE : status;
    }
}
