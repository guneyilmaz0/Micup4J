package com.swadedev.micup4j.interactions.commands.build;

/** Factory for command definitions, as JDA's {@code Commands}. */
public final class Commands {
    private Commands() {}
    /**
     * Creates a slash command definition.
     *
     * @param name        1-32 lowercase characters
     * @param description 1-100 characters
     * @return the mutable definition
     * @throws IllegalArgumentException if a value is invalid
     */
    public static SlashCommandData slash(String name, String description) { return new SlashCommandData(name, description); }
}
