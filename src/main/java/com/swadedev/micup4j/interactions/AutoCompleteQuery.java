package com.swadedev.micup4j.interactions;

import com.swadedev.micup4j.interactions.commands.*;

/** The unvalidated text being typed, including incomplete numeric input. */
public final class AutoCompleteQuery {
    private final String name, value;
    private final com.swadedev.micup4j.interactions.commands.OptionType type;
    /**
     * Creates the query from the focused option.
     *
     * @param option the focused option
     */
    public AutoCompleteQuery(OptionMapping option) { name = option.getName(); value = option.getAsString(); type = option.getType(); }
    /**
     * Returns the focused option's name.
     *
     * @return the focused option's name
     */
    public String getName() { return name; }
    /**
     * Returns the raw text typed so far, even for numeric options.
     *
     * @return the raw text typed so far, even for numeric options
     */
    public String getValue() { return value; }
    /**
     * Returns the focused option's declared type.
     *
     * @return the focused option's declared type
     */
    public com.swadedev.micup4j.interactions.commands.OptionType getType() { return type; }
}
