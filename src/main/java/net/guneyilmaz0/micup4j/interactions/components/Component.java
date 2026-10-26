package net.guneyilmaz0.micup4j.interactions.components;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** A message or modal component. Implementations are immutable and validated on construction. */
public interface Component {
    /** @return the Micup component type */
    int getTypeRaw();

    /** @return a new JSON representation */
    ObjectNode toJson();

    /** @return whether this component may appear on a message */
    default boolean isMessageCompatible() { return true; }

    /** @return whether this component may appear in a modal */
    default boolean isModalCompatible() { return false; }
}
