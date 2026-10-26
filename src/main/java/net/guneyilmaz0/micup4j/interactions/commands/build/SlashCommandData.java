package net.guneyilmaz0.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.interactions.commands.OptionType;
import java.util.*;

/** Mutable JDA-style definition. Added children are copied to keep registration snapshots stable. */
public final class SlashCommandData implements CommandData {
    private final ObjectNode body;
    public SlashCommandData(String name, String description) { body = DefinitionSupport.base(1, name, description); }
    public SlashCommandData addOption(OptionType type, String name, String description) { return addOption(type, name, description, false); }
    public SlashCommandData addOption(OptionType type, String name, String description, boolean required) { return addOptions(new OptionData(type, name, description, required)); }
    public SlashCommandData addOption(OptionType type, String name, String description, boolean required, boolean autocomplete) { return addOptions(new OptionData(type, name, description, required, autocomplete)); }
    public SlashCommandData addOption(net.guneyilmaz0.micup4j.interactions.OptionType type, String name, String description, boolean required) {
        return addOption(OptionType.fromKey(Objects.requireNonNull(type).getKey()), name, description, required);
    }
    public SlashCommandData addOptions(OptionData... options) { return addOptions(Arrays.asList(options)); }
    public synchronized SlashCommandData addOptions(Collection<? extends OptionData> options) {
        DefinitionSupport.append(body, options.stream().map(OptionData::toJson).toList(), true); return this;
    }
    public SlashCommandData addSubcommands(SubcommandData... commands) { return addSubcommands(Arrays.asList(commands)); }
    public synchronized SlashCommandData addSubcommands(Collection<? extends SubcommandData> commands) {
        DefinitionSupport.append(body, commands.stream().map(SubcommandData::toJson).toList(), false); return this;
    }
    public SlashCommandData addSubcommandGroups(SubcommandGroupData... groups) { return addSubcommandGroups(Arrays.asList(groups)); }
    public synchronized SlashCommandData addSubcommandGroups(Collection<? extends SubcommandGroupData> groups) {
        DefinitionSupport.append(body, groups.stream().map(SubcommandGroupData::toJson).toList(), false); return this;
    }
    @Override public synchronized String getName() { return body.path("name").asText(); }
    @Override public synchronized String getDescription() { return body.path("description").asText(); }
    @Override public synchronized ObjectNode toJson() { return body.deepCopy(); }
}
