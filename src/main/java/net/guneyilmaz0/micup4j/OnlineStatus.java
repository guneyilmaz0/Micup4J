package net.guneyilmaz0.micup4j;

/** Session statuses, with Micup's wire values. Modelled on JDA's {@code OnlineStatus}. */
public enum OnlineStatus {
    /** Online and available. */
    ONLINE("online"),
    /** Away. */
    IDLE("idle"),
    /** Do not disturb. */
    DO_NOT_DISTURB("dnd"),
    /** Shown as offline while connected. */
    INVISIBLE("invisible"),
    /** Offline; published as {@link #INVISIBLE}. */
    OFFLINE("offline"),
    /** An unrecognized wire value; cannot be published. */
    UNKNOWN("unknown");
    private final String key;
    OnlineStatus(String key) { this.key = key; }
    /**
     * Returns the Micup wire value.
     *
     * @return the wire value
     */
    public String getKey() { return key; }
    /**
     * Resolves a wire value.
     *
     * @param key the wire value
     * @return the status, or {@link #UNKNOWN}
     */
    public static OnlineStatus fromKey(String key) {
        for (var status : values()) if (status.key.equals(key)) return status;
        return UNKNOWN;
    }
    /**
     * Normalizes a status for publishing: {@code null} and {@link #OFFLINE} become {@link #INVISIBLE}.
     *
     * @param status the status
     * @return the publishable status
     * @throws IllegalArgumentException for {@link #UNKNOWN}
     */
    public static OnlineStatus normalize(OnlineStatus status) {
        if (status == UNKNOWN) throw new IllegalArgumentException("UNKNOWN is not a publishable status");
        return status == null || status == OFFLINE ? INVISIBLE : status;
    }
}
