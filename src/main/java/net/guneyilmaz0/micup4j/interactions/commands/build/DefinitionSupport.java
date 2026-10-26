package net.guneyilmaz0.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.interactions.CommandOption;
import net.guneyilmaz0.micup4j.interactions.OptionType;
import java.util.*;

final class DefinitionSupport {
    private DefinitionSupport() {}
    static ObjectNode base(int type, String name, String description) {
        // Reuse the existing Unicode normalization and description validation.
        var validated = new CommandOption(OptionType.STRING, name, description, false).toJson();
        validated.remove("required"); return validated.put("type", type);
    }
    static void append(ObjectNode target, Collection<ObjectNode> additions, boolean values) {
        var next = target.has("options") ? target.withArray("options").deepCopy()
                : net.guneyilmaz0.micup4j.internal.Json.MAPPER.createArrayNode();
        additions.forEach(v -> next.add(Objects.requireNonNull(v).deepCopy()));
        if (next.size() > 25) throw new IllegalArgumentException("Provide at most 25 options");
        var names = new HashSet<String>(); boolean optional = false;
        for (var option : next) {
            if (!names.add(option.path("name").asText())) throw new IllegalArgumentException("Option names must be unique");
            if ((option.path("type").asInt() >= 3) != values) throw new IllegalArgumentException("Cannot mix value options and subcommands/groups");
            if (values) {
                if (option.path("required").asBoolean() && optional) throw new IllegalArgumentException("Required options must precede optional ones");
                optional |= !option.path("required").asBoolean();
            }
        }
        target.set("options", next);
    }
}
