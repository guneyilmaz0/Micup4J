package com.swadedev.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.interactions.commands.OptionType;
import java.util.*;

/** Mutable JDA-style definition. Added children are copied to keep registration snapshots stable. */
public final class SlashCommandData implements CommandData {
    private final ObjectNode body;
    /**
     * Creates a slash command definition; prefer {@link Commands#slash(String, String)}.
     *
     * @param name        1-32 lowercase characters
     * @param description 1-100 characters
     * @throws IllegalArgumentException if a value is invalid
     */
    public SlashCommandData(String name, String description) { body = DefinitionSupport.base(1, name, description); }
    /**
     * Adds an optional value option.
     *
     * @param type        a value option type
     * @param name        the option name
     * @param description the option description
     * @return this command
     */
    public SlashCommandData addOption(OptionType type, String name, String description) { return addOption(type, name, description, false); }
    /**
     * Adds a value option.
     *
     * @param type        a value option type
     * @param name        the option name
     * @param description the option description
     * @param required    whether the user must fill it
     * @return this command
     */
    public SlashCommandData addOption(OptionType type, String name, String description, boolean required) { return addOptions(new OptionData(type, name, description, required)); }
    /**
     * Adds a value option.
     *
     * @param type         a value option type
     * @param name         the option name
     * @param description  the option description
     * @param required     whether the user must fill it
     * @param autocomplete whether values come from autocomplete
     * @return this command
     */
    public SlashCommandData addOption(OptionType type, String name, String description, boolean required, boolean autocomplete) { return addOptions(new OptionData(type, name, description, required, autocomplete)); }
    /**
     * Adds a value option from a legacy type.
     *
     * @param type        the legacy type
     * @param name        the option name
     * @param description the option description
     * @param required    whether the user must fill it
     * @return this command
     * @deprecated Use the {@link OptionType} overload; kept through 1.x.
     */
    @Deprecated(since = "1.0.0")
    public SlashCommandData addOption(com.swadedev.micup4j.interactions.OptionType type, String name, String description, boolean required) {
        return addOption(OptionType.fromKey(Objects.requireNonNull(type).getKey()), name, description, required);
    }
    /**
     * Adds value options; each is copied.
     *
     * @param options the options
     * @return this command
     */
    public SlashCommandData addOptions(OptionData... options) { return addOptions(Arrays.asList(options)); }
    /**
     * Adds value options; each is copied.
     *
     * @param options at most 25 in total, with unique names and required options first
     * @return this command
     * @throws IllegalArgumentException if the result is invalid or mixes options with subcommands
     */
    public synchronized SlashCommandData addOptions(Collection<? extends OptionData> options) {
        DefinitionSupport.append(body, options.stream().map(OptionData::toJson).toList(), true); return this;
    }
    /**
     * Adds subcommands; each is copied.
     *
     * @param commands the subcommands
     * @return this command
     */
    public SlashCommandData addSubcommands(SubcommandData... commands) { return addSubcommands(Arrays.asList(commands)); }
    /**
     * Adds subcommands; each is copied.
     *
     * @param commands at most 25 children in total
     * @return this command
     * @throws IllegalArgumentException if the result is invalid or mixes subcommands with value options
     */
    public synchronized SlashCommandData addSubcommands(Collection<? extends SubcommandData> commands) {
        DefinitionSupport.append(body, commands.stream().map(SubcommandData::toJson).toList(), false); return this;
    }
    /**
     * Adds subcommand groups; each is copied.
     *
     * @param groups the groups
     * @return this command
     */
    public SlashCommandData addSubcommandGroups(SubcommandGroupData... groups) { return addSubcommandGroups(Arrays.asList(groups)); }
    /**
     * Adds subcommand groups; each is copied.
     *
     * @param groups at most 25 children in total
     * @return this command
     * @throws IllegalArgumentException if the result is invalid or mixes groups with value options
     */
    public synchronized SlashCommandData addSubcommandGroups(Collection<? extends SubcommandGroupData> groups) {
        DefinitionSupport.append(body, groups.stream().map(SubcommandGroupData::toJson).toList(), false); return this;
    }
    @Override public synchronized String getName() { return body.path("name").asText(); }
    @Override public synchronized String getDescription() { return body.path("description").asText(); }
    @Override public synchronized ObjectNode toJson() { return body.deepCopy(); }
}
