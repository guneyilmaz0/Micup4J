package net.guneyilmaz0.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** A supported command definition; serialization returns a detached snapshot. */
public interface CommandData {
    String getName();
    String getDescription();
    ObjectNode toJson();
}
