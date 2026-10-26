package net.guneyilmaz0.micup4j.interactions.commands.build;

public final class Commands {
    private Commands() {}
    public static SlashCommandData slash(String name, String description) { return new SlashCommandData(name, description); }
}
