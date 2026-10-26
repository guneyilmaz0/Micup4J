package com.swadedev.micup4j.managers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.*;
import com.swadedev.micup4j.entities.Activity;
import com.swadedev.micup4j.internal.Json;
import java.util.Objects;

/** Desired session presence. Updates coalesce and are published after Gateway authentication. */
public final class Presence {
    /**
     * Immutable value of the desired presence.
     *
     * <p>The status is normalized: {@code null} and {@code OFFLINE} become {@code INVISIBLE}, and {@code UNKNOWN} is rejected
     * with an {@link IllegalArgumentException}.
     *
     * @param status              the online status
     * @param activity            the custom status, or {@code null} for none
     * @param idle                whether the session is marked away
     * @param configured          whether the status or idle flag was set explicitly
     * @param activityConfigured  whether the activity was set explicitly; only then is it sent, so an activity that was never set
     *                            is never published as a clear
     */
    public record State(OnlineStatus status, Activity activity, boolean idle, boolean configured, boolean activityConfigured) {
        /**
         * Creates a state in which the activity counts as configured exactly when {@code configured} is {@code true}.
         *
         * @param status     the online status
         * @param activity   the custom status, or {@code null}
         * @param idle       whether the session is marked away
         * @param configured whether the presence was set explicitly
         */
        public State(OnlineStatus status, Activity activity, boolean idle, boolean configured) { this(status, activity, idle, configured, configured); }
        /**
         * Normalizes the status.
         *
         * @throws IllegalArgumentException if the status is {@code UNKNOWN}
         */
        public State { status = OnlineStatus.normalize(status); }
        /**
         * Returns the state of a session whose presence was never configured: online, no activity, not idle.
         *
         * @return the default state
         */
        public static State defaults() { return new State(OnlineStatus.ONLINE, null, false, false, false); }
        /**
         * Serializes the state as a Gateway presence payload. The activity is included only when it was configured.
         *
         * @return a new JSON object
         */
        public ObjectNode toJson() {
            var body = Json.object().put("status", status.getKey()).put("afk", idle).put("mobile", false);
            if (activityConfigured) {
                if (activity == null) body.putNull("custom_status");
                else body.set("custom_status", Json.object().put("text", activity.getDisplayText()));
            }
            return body;
        }
    }
    private final Micup client;
    private volatile State desired;
    /**
     * Creates the manager. Obtain it from {@link Micup#getPresence()} instead.
     *
     * @param client  the owning client
     * @param initial the initial desired state
     */
    public Presence(Micup client, State initial) { this.client = Objects.requireNonNull(client); desired = Objects.requireNonNull(initial); }
    /**
     * Returns the owning client.
     *
     * @return the client
     */
    public Micup getJDA() { return client; }
    /**
     * Returns the desired online status.
     *
     * @return the status, never {@code null}
     */
    public OnlineStatus getStatus() { return desired.status(); }
    /**
     * Returns the desired activity.
     *
     * @return the activity, or {@code null} for none
     */
    public Activity getActivity() { return desired.activity(); }
    /**
     * Returns whether the session is desired to be marked away.
     *
     * @return {@code true} if idle
     */
    public boolean isIdle() { return desired.idle(); }
    /**
     * Returns the current desired state as an immutable value.
     *
     * @return the state
     */
    public State snapshot() { return desired; }
    /**
     * Sets the online status; the activity and idle flag are kept.
     *
     * @param status the new status; {@code null} and {@code OFFLINE} become {@code INVISIBLE}
     * @throws IllegalArgumentException if {@code status} is {@code UNKNOWN}
     * @throws IllegalStateException if the client is shutting down, shut down or failed
     */
    public synchronized void setStatus(OnlineStatus status) { update(new State(status, desired.activity(), desired.idle(), true, desired.activityConfigured())); }
    /**
     * Sets the activity; {@code null} clears the custom status. The status and idle flag are kept.
     *
     * @param activity the new activity, or {@code null}
     * @throws IllegalStateException if the client is shutting down, shut down or failed
     */
    public synchronized void setActivity(Activity activity) { update(new State(desired.status(), activity, desired.idle(), true, true)); }
    /**
     * Sets the idle flag; the status and activity are kept.
     *
     * @param idle whether to mark the session away
     * @throws IllegalStateException if the client is shutting down, shut down or failed
     */
    public synchronized void setIdle(boolean idle) { update(new State(desired.status(), desired.activity(), idle, true, desired.activityConfigured())); }
    /**
     * Replaces the whole presence.
     *
     * @param status   the new status; {@code null} and {@code OFFLINE} become {@code INVISIBLE}
     * @param activity the new activity, or {@code null} to clear it
     * @param idle     whether to mark the session away
     * @throws IllegalArgumentException if {@code status} is {@code UNKNOWN}
     * @throws IllegalStateException if the client is shutting down, shut down or failed
     */
    public synchronized void setPresence(OnlineStatus status, Activity activity, boolean idle) { update(new State(status, activity, idle, true, true)); }
    /**
     * Sets the status and activity; the idle flag is kept.
     *
     * @param status   the new status; {@code null} and {@code OFFLINE} become {@code INVISIBLE}
     * @param activity the new activity, or {@code null} to clear it
     * @throws IllegalArgumentException if {@code status} is {@code UNKNOWN}
     * @throws IllegalStateException if the client is shutting down, shut down or failed
     */
    public synchronized void setPresence(OnlineStatus status, Activity activity) { update(new State(status, activity, desired.idle(), true, true)); }
    /**
     * Sets the status and idle flag; the activity is kept.
     *
     * @param status the new status; {@code null} and {@code OFFLINE} become {@code INVISIBLE}
     * @param idle   whether to mark the session away
     * @throws IllegalArgumentException if {@code status} is {@code UNKNOWN}
     * @throws IllegalStateException if the client is shutting down, shut down or failed
     */
    public synchronized void setPresence(OnlineStatus status, boolean idle) { update(new State(status, desired.activity(), idle, true, desired.activityConfigured())); }
    /**
     * Sets the activity and idle flag; the status is kept.
     *
     * @param activity the new activity, or {@code null} to clear it
     * @param idle     whether to mark the session away
     * @throws IllegalStateException if the client is shutting down, shut down or failed
     */
    public synchronized void setPresence(Activity activity, boolean idle) { update(new State(desired.status(), activity, idle, true, true)); }
    private void update(State next) {
        Micup.Status status = client.getStatus();
        if (status == Micup.Status.SHUTTING_DOWN || status == Micup.Status.SHUTDOWN || status == Micup.Status.FAILED)
            throw new IllegalStateException("Client is no longer running");
        desired = next;
        client.presenceChanged();
    }
}
