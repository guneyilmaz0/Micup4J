package com.swadedev.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** A named group of 1 to 25 subcommands. */
public final class SubcommandGroupData {
    private final ObjectNode body;
    /**
     * Creates a subcommand group.
     *
     * @param name        1-32 lowercase characters
     * @param description 1-100 characters
     * @throws IllegalArgumentException if a value is invalid
     */
    public SubcommandGroupData(String name, String description) { body = DefinitionSupport.base(2, name, description); }
    /**
     * Returns the group name.
     *
     * @return the group name
     */
    public synchronized String getName() { return body.path("name").asText(); }
    /**
     * Returns the group description.
     *
     * @return the group description
     */
    public synchronized String getDescription() { return body.path("description").asText(); }
    /**
     * Adds subcommands; each is copied.
     *
     * @param commands the subcommands
     * @return this group
     */
    public SubcommandGroupData addSubcommands(SubcommandData... commands) { return addSubcommands(Arrays.asList(commands)); }
    /**
     * Adds subcommands; each is copied.
     *
     * @param commands at most 25 in total, with unique names
     * @return this group
     * @throws IllegalArgumentException if the result is invalid
     */
    public synchronized SubcommandGroupData addSubcommands(Collection<? extends SubcommandData> commands) {
        DefinitionSupport.append(body, commands.stream().map(SubcommandData::toJson).toList(), false); return this;
    }
    /**
     * Returns a copy of the group payload.
     *
     * @return a copy of the group payload
     */
    public synchronized ObjectNode toJson() {
        if (body.path("options").isEmpty()) throw new IllegalStateException("A subcommand group requires at least one subcommand");
        return body.deepCopy();
    }
}
