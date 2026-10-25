package net.guneyilmaz0.micup4j.interactions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.internal.Json;
import java.util.*;

/** Immutable chat-input definition. addOption returns a new definition. */
public record SlashCommand(String name, String description, List<CommandOption> options) {
    public SlashCommand(String name, String description) { this(name, description, List.of()); }
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
    public SlashCommand addOption(OptionType type, String name, String description, boolean required) {
        var values = new ArrayList<>(options); values.add(new CommandOption(type, name, description, required));
        return new SlashCommand(this.name, this.description, values);
    }
    public ObjectNode toJson() {
        var body = Json.object().put("type", 1).put("name", name).put("description", description);
        var values = Json.MAPPER.createArrayNode(); options.forEach(option -> values.add(option.toJson()));
        body.set("options", values); return body;
    }
}
