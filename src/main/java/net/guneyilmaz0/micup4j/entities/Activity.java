package net.guneyilmaz0.micup4j.entities;

import java.util.Objects;

/**
 * JDA-style activities rendered as Micup custom-status text, not Discord rich-presence objects.
 *
 * @param type the kind, which selects the rendered prefix
 * @param name the activity name
 */
public record Activity(ActivityType type, String name) {
    /** The JDA activity kinds; each renders as a prefix of the custom status text. */
    public enum ActivityType {
        /** Rendered as {@code Playing <name>}. */
        PLAYING,
        /** Rendered as {@code Listening to <name>}. */
        LISTENING,
        /** Rendered as {@code Watching <name>}. */
        WATCHING,
        /** Rendered as {@code Competing in <name>}. */
        COMPETING,
        /** Rendered as the name alone. */
        CUSTOM_STATUS
    }
    /**
     * Validates the activity.
     *
     * @throws IllegalArgumentException if the name is blank or the rendered text exceeds 128 characters
     */
    public Activity {
        Objects.requireNonNull(type); Objects.requireNonNull(name);
        String display = display(type, name);
        if (name.isBlank() || display.codePointCount(0, display.length()) > 128)
            throw new IllegalArgumentException("Rendered activity must contain 1-128 characters");
    }
    /**
     * Creates a {@code Playing <name>} activity.
     *
     * @param name the game
     * @return the activity
     */
    public static Activity playing(String name) { return new Activity(ActivityType.PLAYING, name); }
    /**
     * Creates a {@code Listening to <name>} activity.
     *
     * @param name what is being listened to
     * @return the activity
     */
    public static Activity listening(String name) { return new Activity(ActivityType.LISTENING, name); }
    /**
     * Creates a {@code Watching <name>} activity.
     *
     * @param name what is being watched
     * @return the activity
     */
    public static Activity watching(String name) { return new Activity(ActivityType.WATCHING, name); }
    /**
     * Creates a {@code Competing in <name>} activity.
     *
     * @param name the competition
     * @return the activity
     */
    public static Activity competing(String name) { return new Activity(ActivityType.COMPETING, name); }
    /**
     * Creates a plain custom status.
     *
     * @param name the status text
     * @return the activity
     */
    public static Activity customStatus(String name) { return new Activity(ActivityType.CUSTOM_STATUS, name); }
    /**
     * Creates an activity.
     *
     * @param type the kind
     * @param name the name
     * @return the activity
     */
    public static Activity of(ActivityType type, String name) { return new Activity(type, name); }
    /**
     * Returns the activity name.
     *
     * @return the activity name
     */
    public String getName() { return name; }
    /**
     * Returns the activity kind.
     *
     * @return the activity kind
     */
    public ActivityType getType() { return type; }
    /**
     * Returns the custom status text published for this activity.
     *
     * @return the custom status text published for this activity
     */
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
