package net.guneyilmaz0.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.interactions.SlashCommand;
import net.guneyilmaz0.micup4j.interactions.commands.OptionType;

/** Mutable JDA-style definition backed by the validated immutable Micup command model. */
public final class SlashCommandData implements CommandData {
    private SlashCommand command;
    public SlashCommandData(String name, String description) { command = new SlashCommand(name, description); }
    public synchronized SlashCommandData addOption(OptionType type, String name, String description) {
        return addOption(type, name, description, false);
    }
    public synchronized SlashCommandData addOption(OptionType type, String name, String description, boolean required) {
        command = command.addOption(type.toLegacy(), name, description, required); return this;
    }
    public synchronized SlashCommandData addOption(net.guneyilmaz0.micup4j.interactions.OptionType type,
                                                   String name, String description, boolean required) {
        command = command.addOption(type, name, description, required); return this;
    }
    @Override public synchronized String getName() { return command.name(); }
    @Override public synchronized String getDescription() { return command.description(); }
    @Override public synchronized ObjectNode toJson() { return command.toJson(); }
}
