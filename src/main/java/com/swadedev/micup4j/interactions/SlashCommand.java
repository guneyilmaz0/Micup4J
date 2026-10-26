package com.swadedev.micup4j.interactions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.internal.Json;
import java.util.*;

/**
 * Legacy immutable chat-input definition. {@code addOption} returns a new definition.
 *
 * @param name        1-32 lowercase characters
 * @param description 1-100 characters
 * @param options     the value options, copied
 * @deprecated Use {@link com.swadedev.micup4j.interactions.commands.build.Commands#slash(String, String)}; kept through 1.x.
 */
@Deprecated(since = "1.0.0")
public record SlashCommand(String name, String description, List<CommandOption> options) {
    /**
     * Creates a definition without options.
     *
     * @param name        the command name
     * @param description the command description
     */
    public SlashCommand(String name, String description) { this(name, description, List.of()); }
    /**
     * Validates the definition.
     *
     * @throws IllegalArgumentException if a value is invalid
     */
    public SlashCommand {
        name = CommandOption.validName(name); CommandOption.validDescription(description);
        options = List.copyOf(options);
        if (options.size() > 25) throw new IllegalArgumentException("A command supports at most 25 options");
        Set<String> names = new HashSet<>(); boolean optional = false;
        for (CommandOption option : options) {
            if (!names.add(option.name())) throw new IllegalArgumentException("Option names must be unique");
            if (optional && option.required()) throw new IllegalArgumentException("Required options must precede optional options");
            optional |= !option.required();
        }
    }
    /**
     * Returns a copy with one more option.
     *
     * @param type        the option type
     * @param name        the option name
     * @param description the option description
     * @param required    whether the user must fill it
     * @return the new definition
     */
    public SlashCommand addOption(OptionType type, String name, String description, boolean required) {
        var values = new ArrayList<>(options); values.add(new CommandOption(type, name, description, required));
        return new SlashCommand(this.name, this.description, values);
    }
    /**
     * Returns the command payload.
     *
     * @return the command payload
     */
    public ObjectNode toJson() {
        var body = Json.object().put("type", 1).put("name", name).put("description", description);
        var values = Json.MAPPER.createArrayNode(); options.forEach(option -> values.add(option.toJson()));
        body.set("options", values); return body;
    }
}
