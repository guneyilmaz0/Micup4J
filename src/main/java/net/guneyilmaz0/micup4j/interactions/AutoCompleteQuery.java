package net.guneyilmaz0.micup4j.interactions;

import net.guneyilmaz0.micup4j.interactions.commands.*;

/** The unvalidated text being typed, including incomplete numeric input. */
public final class AutoCompleteQuery {
    private final String name, value;
    private final net.guneyilmaz0.micup4j.interactions.commands.OptionType type;
    public AutoCompleteQuery(OptionMapping option) { name = option.getName(); value = option.getAsString(); type = option.getType(); }
    public String getName() { return name; }
    public String getValue() { return value; }
    public net.guneyilmaz0.micup4j.interactions.commands.OptionType getType() { return type; }
}
