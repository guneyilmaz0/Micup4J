package net.guneyilmaz0.micup4j.interactions.components;

/** Shared validation for component limits. Not part of the supported public API. */
public final class ComponentChecks {
    private ComponentChecks() {}

    /**
     * Checks a string length in characters.
     *
     * @param what    the field name used in the message
     * @param value   the value; must not be {@code null} unless {@code minimum} is 0
     * @param minimum the shortest length
     * @param maximum the longest length
     * @return the value
     */
    public static String length(String what, String value, int minimum, int maximum) {
        int length = value == null ? 0 : value.codePointCount(0, value.length());
        if ((value == null && minimum > 0) || length < minimum || length > maximum)
            throw new IllegalArgumentException(what + " must be " + minimum + "-" + maximum + " characters");
        return value;
    }

    /**
     * Checks a custom id.
     *
     * @param id the id
     * @return the id
     */
    public static String customId(String id) { return length("Custom id", id, 1, 100); }
}
