package net.guneyilmaz0.micup4j.managers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.*;
import net.guneyilmaz0.micup4j.entities.Activity;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.Objects;

/** Desired session presence. Updates coalesce and are published after Gateway authentication. */
public final class Presence {
    public record State(OnlineStatus status, Activity activity, boolean idle, boolean configured, boolean activityConfigured) {
        public State(OnlineStatus status, Activity activity, boolean idle, boolean configured) { this(status, activity, idle, configured, configured); }
        public State { status = OnlineStatus.normalize(status); }
        public static State defaults() { return new State(OnlineStatus.ONLINE, null, false, false, false); }
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
    public Presence(Micup client, State initial) { this.client = Objects.requireNonNull(client); desired = Objects.requireNonNull(initial); }
    public Micup getJDA() { return client; }
    public OnlineStatus getStatus() { return desired.status(); }
    public Activity getActivity() { return desired.activity(); }
    public boolean isIdle() { return desired.idle(); }
    public State snapshot() { return desired; }
    public synchronized void setStatus(OnlineStatus status) { update(new State(status, desired.activity(), desired.idle(), true, desired.activityConfigured())); }
    public synchronized void setActivity(Activity activity) { update(new State(desired.status(), activity, desired.idle(), true, true)); }
    public synchronized void setIdle(boolean idle) { update(new State(desired.status(), desired.activity(), idle, true, desired.activityConfigured())); }
    public synchronized void setPresence(OnlineStatus status, Activity activity, boolean idle) { update(new State(status, activity, idle, true, true)); }
    public synchronized void setPresence(OnlineStatus status, Activity activity) { update(new State(status, activity, desired.idle(), true, true)); }
    public synchronized void setPresence(OnlineStatus status, boolean idle) { update(new State(status, desired.activity(), idle, true, desired.activityConfigured())); }
    public synchronized void setPresence(Activity activity, boolean idle) { update(new State(desired.status(), activity, idle, true, true)); }
    private void update(State next) {
        if (client.getStatus() == Micup.Status.SHUTDOWN || client.getStatus() == Micup.Status.FAILED)
            throw new IllegalStateException("Client is no longer running");
        desired = next;
        client.presenceChanged();
    }
}
