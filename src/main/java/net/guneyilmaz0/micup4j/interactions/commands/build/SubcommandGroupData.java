package net.guneyilmaz0.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** A named group of 1 to 25 subcommands. */
public final class SubcommandGroupData {
    private final ObjectNode body;
    public SubcommandGroupData(String name, String description) { body = DefinitionSupport.base(2, name, description); }
    public synchronized String getName() { return body.path("name").asText(); }
    public synchronized String getDescription() { return body.path("description").asText(); }
    public SubcommandGroupData addSubcommands(SubcommandData... commands) { return addSubcommands(Arrays.asList(commands)); }
    public synchronized SubcommandGroupData addSubcommands(Collection<? extends SubcommandData> commands) {
        DefinitionSupport.append(body, commands.stream().map(SubcommandData::toJson).toList(), false); return this;
    }
    public synchronized ObjectNode toJson() {
        if (body.path("options").isEmpty()) throw new IllegalStateException("A subcommand group requires at least one subcommand");
        return body.deepCopy();
    }
}
