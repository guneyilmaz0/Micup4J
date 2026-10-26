package com.swadedev.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** A supported command definition; serialization returns a detached snapshot. */
public interface CommandData {
    /**
     * Returns the command name.
     *
     * @return the command name
     */
    String getName();
    /**
     * Returns the command description.
     *
     * @return the command description
     */
    String getDescription();
    /**
     * Returns a copy of the definition payload.
     *
     * @return a copy of the definition payload
     */
    ObjectNode toJson();
}
