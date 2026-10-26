package com.swadedev.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.interactions.commands.OptionType;
import java.util.*;

/** Subcommand definition with up to 25 scalar options. */
public final class SubcommandData {
    private final ObjectNode body;
    /**
     * Creates a subcommand.
     *
     * @param name        1-32 lowercase characters
     * @param description 1-100 characters
     * @throws IllegalArgumentException if a value is invalid
     */
    public SubcommandData(String name, String description) { body = DefinitionSupport.base(1, name, description); }
    /**
     * Returns the subcommand name.
     *
     * @return the subcommand name
     */
    public synchronized String getName() { return body.path("name").asText(); }
    /**
     * Returns the subcommand description.
     *
     * @return the subcommand description
     */
    public synchronized String getDescription() { return body.path("description").asText(); }
    /**
     * Adds an optional value option.
     *
     * @param type        a value option type
     * @param name        the option name
     * @param description the option description
     * @return this subcommand
     */
    public SubcommandData addOption(OptionType type, String name, String description) { return addOption(type, name, description, false); }
    /**
     * Adds a value option.
     *
     * @param type        a value option type
     * @param name        the option name
     * @param description the option description
     * @param required    whether the user must fill it
     * @return this subcommand
     */
    public SubcommandData addOption(OptionType type, String name, String description, boolean required) { return addOptions(new OptionData(type, name, description, required)); }
    /**
     * Adds a value option.
     *
     * @param type         a value option type
     * @param name         the option name
     * @param description  the option description
     * @param required     whether the user must fill it
     * @param autocomplete whether values come from autocomplete
     * @return this subcommand
     */
    public SubcommandData addOption(OptionType type, String name, String description, boolean required, boolean autocomplete) { return addOptions(new OptionData(type, name, description, required, autocomplete)); }
    /**
     * Adds value options; each is copied.
     *
     * @param options the options
     * @return this subcommand
     */
    public SubcommandData addOptions(OptionData... options) { return addOptions(Arrays.asList(options)); }
    /**
     * Adds value options; each is copied.
     *
     * @param options at most 25 in total, with unique names and required options first
     * @return this subcommand
     * @throws IllegalArgumentException if the result is invalid
     */
    public synchronized SubcommandData addOptions(Collection<? extends OptionData> options) {
        DefinitionSupport.append(body, options.stream().map(OptionData::toJson).toList(), true); return this;
    }
    /**
     * Returns a copy of the subcommand payload.
     *
     * @return a copy of the subcommand payload
     */
    public synchronized ObjectNode toJson() { return body.deepCopy(); }
}
