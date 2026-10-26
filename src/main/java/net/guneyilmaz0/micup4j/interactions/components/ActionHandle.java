package net.guneyilmaz0.micup4j.interactions.components;

/** A component that sends its id back to the bot when used. */
public interface ActionHandle {
    /**
     * Returns the custom id, or {@code null} for components without one, such as link buttons.
     *
     * @return the custom id, or {@code null} for components without one, such as link buttons
     */
    String getId();
}
