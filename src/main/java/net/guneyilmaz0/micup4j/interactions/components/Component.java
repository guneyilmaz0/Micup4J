package net.guneyilmaz0.micup4j.interactions.components;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** A message or modal component. Implementations are immutable and validated on construction. */
public interface Component {
    /**
     * Returns the Micup component type.
     *
     * @return the Micup component type
     */
    int getTypeRaw();

    /**
     * Returns a new JSON representation.
     *
     * @return a new JSON representation
     */
    ObjectNode toJson();

    /**
     * Returns whether this component may appear on a message.
     *
     * @return whether this component may appear on a message
     */
    default boolean isMessageCompatible() { return true; }

    /**
     * Returns whether this component may appear in a modal.
     *
     * @return whether this component may appear in a modal
     */
    default boolean isModalCompatible() { return false; }
}
