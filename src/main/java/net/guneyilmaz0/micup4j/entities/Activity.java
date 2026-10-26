package net.guneyilmaz0.micup4j.entities;

import java.util.Objects;

/** JDA-style activities rendered as Micup custom-status text, not Discord rich-presence objects. */
public record Activity(ActivityType type, String name) {
    public enum ActivityType { PLAYING, LISTENING, WATCHING, COMPETING, CUSTOM_STATUS }
    public Activity {
        Objects.requireNonNull(type); Objects.requireNonNull(name);
        String display = display(type, name);
        if (name.isBlank() || display.codePointCount(0, display.length()) > 128)
            throw new IllegalArgumentException("Rendered activity must contain 1-128 characters");
    }
    public static Activity playing(String name) { return new Activity(ActivityType.PLAYING, name); }
    public static Activity listening(String name) { return new Activity(ActivityType.LISTENING, name); }
    public static Activity watching(String name) { return new Activity(ActivityType.WATCHING, name); }
    public static Activity competing(String name) { return new Activity(ActivityType.COMPETING, name); }
    public static Activity customStatus(String name) { return new Activity(ActivityType.CUSTOM_STATUS, name); }
    public static Activity of(ActivityType type, String name) { return new Activity(type, name); }
    public String getName() { return name; }
    public ActivityType getType() { return type; }
    public String getDisplayText() { return display(type, name); }
    private static String display(ActivityType type, String name) {
        return switch (type) {
            case PLAYING -> "Playing " + name;
            case LISTENING -> "Listening to " + name;
            case WATCHING -> "Watching " + name;
            case COMPETING -> "Competing in " + name;
            case CUSTOM_STATUS -> name;
        };
    }
}
