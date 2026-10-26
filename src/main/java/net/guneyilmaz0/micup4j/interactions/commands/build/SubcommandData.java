package net.guneyilmaz0.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.interactions.commands.OptionType;
import java.util.*;

/** Subcommand definition with up to 25 scalar options. */
public final class SubcommandData {
    private final ObjectNode body;
    public SubcommandData(String name, String description) { body = DefinitionSupport.base(1, name, description); }
    public synchronized String getName() { return body.path("name").asText(); }
    public synchronized String getDescription() { return body.path("description").asText(); }
    public SubcommandData addOption(OptionType type, String name, String description) { return addOption(type, name, description, false); }
    public SubcommandData addOption(OptionType type, String name, String description, boolean required) { return addOptions(new OptionData(type, name, description, required)); }
    public SubcommandData addOption(OptionType type, String name, String description, boolean required, boolean autocomplete) { return addOptions(new OptionData(type, name, description, required, autocomplete)); }
    public SubcommandData addOptions(OptionData... options) { return addOptions(Arrays.asList(options)); }
    public synchronized SubcommandData addOptions(Collection<? extends OptionData> options) {
        DefinitionSupport.append(body, options.stream().map(OptionData::toJson).toList(), true); return this;
    }
    public synchronized ObjectNode toJson() { return body.deepCopy(); }
}
