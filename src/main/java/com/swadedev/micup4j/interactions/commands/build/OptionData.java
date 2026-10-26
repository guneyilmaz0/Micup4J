package com.swadedev.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.interactions.commands.*;
import com.swadedev.micup4j.entities.channel.ChannelType;
import java.util.*;

/** JDA-style value option builder. Parent definitions snapshot options when added. */
public final class OptionData {
    private final OptionType type;
    private final ObjectNode body;
    /**
     * Creates an optional option.
     *
     * @param type        a value option type
     * @param name        1-32 lowercase characters
     * @param description 1-100 characters
     * @throws IllegalArgumentException if a value is invalid
     */
    public OptionData(OptionType type, String name, String description) { this(type, name, description, false); }
    /**
     * Creates an option.
     *
     * @param type        a value option type
     * @param name        1-32 lowercase characters
     * @param description 1-100 characters
     * @param required    whether the user must fill it
     * @throws IllegalArgumentException if a value is invalid
     */
    public OptionData(OptionType type, String name, String description, boolean required) { this(type, name, description, required, false); }
    /**
     * Creates an option.
     *
     * @param type         a value option type
     * @param name         1-32 lowercase characters
     * @param description  1-100 characters
     * @param required     whether the user must fill it
     * @param autocomplete whether values come from autocomplete; only string, integer and number options
     * @throws IllegalArgumentException if a value is invalid
     */
    public OptionData(OptionType type, String name, String description, boolean required, boolean autocomplete) {
        this.type = Objects.requireNonNull(type);
        if (type.getKey() < 3) throw new IllegalArgumentException("OptionData requires a value option type");
        body = DefinitionSupport.base(type.getKey(), name, description).put("required", required);
        if (autocomplete) setAutoComplete(true);
    }
    /**
     * Returns the option type.
     *
     * @return the option type
     */
    public OptionType getType() { return type; }
    /**
     * Returns the option name.
     *
     * @return the option name
     */
    public synchronized String getName() { return body.path("name").asText(); }
    /**
     * Returns the option description.
     *
     * @return the option description
     */
    public synchronized String getDescription() { return body.path("description").asText(); }
    /**
     * Returns whether the option is required.
     *
     * @return whether the option is required
     */
    public synchronized boolean isRequired() { return body.path("required").asBoolean(); }
    /**
     * Returns whether autocomplete is enabled.
     *
     * @return whether autocomplete is enabled
     */
    public synchronized boolean isAutoComplete() { return body.path("autocomplete").asBoolean(); }
    /**
     * Sets whether the option is required. Required options must precede optional ones in their parent.
     *
     * @param value the flag
     * @return this option
     */
    public synchronized OptionData setRequired(boolean value) { body.put("required", value); return this; }
    /**
     * Enables or disables autocomplete.
     *
     * @param value the flag
     * @return this option
     * @throws IllegalArgumentException if the type has no choices or choices were added
     */
    public synchronized OptionData setAutoComplete(boolean value) {
        choiceType();
        if (value && body.path("choices").size() > 0) throw new IllegalArgumentException("Choices cannot be combined with autocomplete");
        body.put("autocomplete", value); return this;
    }
    /**
     * Adds a string choice.
     *
     * @param name  1-100 characters
     * @param value at most 100 characters
     * @return this option
     */
    public OptionData addChoice(String name, String value) { return addChoices(new Command.Choice(name, value)); }
    /**
     * Adds an integer choice.
     *
     * @param name  1-100 characters
     * @param value a JSON-safe integer
     * @return this option
     */
    public OptionData addChoice(String name, long value) { return addChoices(new Command.Choice(name, value)); }
    /**
     * Adds a number choice.
     *
     * @param name  1-100 characters
     * @param value a finite, JSON-safe number
     * @return this option
     */
    public OptionData addChoice(String name, double value) { return addChoices(new Command.Choice(name, value)); }
    /**
     * Adds predefined choices.
     *
     * @param values the choices, matching the option type
     * @return this option
     */
    public OptionData addChoices(Command.Choice... values) { return addChoices(Arrays.asList(values)); }
    /**
     * Adds predefined choices.
     *
     * @param values the choices, matching the option type; at most 25 in total
     * @return this option
     * @throws IllegalArgumentException if a type does not match, autocomplete is on, or there would be more than 25
     */
    public synchronized OptionData addChoices(Collection<? extends Command.Choice> values) {
        choiceType();
        if (isAutoComplete()) throw new IllegalArgumentException("Choices cannot be combined with autocomplete");
        var next = body.has("choices") ? body.withArray("choices").deepCopy() : com.swadedev.micup4j.internal.Json.MAPPER.createArrayNode();
        for (var value : values) {
            Objects.requireNonNull(value);
            if (value.getType() != type && !(type == OptionType.NUMBER && value.getType() == OptionType.INTEGER))
                throw new IllegalArgumentException("Choice value does not match option type");
            next.add(value.toJson());
        }
        if (next.size() > 25) throw new IllegalArgumentException("Provide at most 25 choices");
        body.set("choices", next); return this;
    }
    /**
     * Sets the smallest accepted integer or number.
     *
     * @param value a JSON-safe bound
     * @return this option
     * @throws IllegalArgumentException for non-numeric options or a minimum above the maximum
     */
    public synchronized OptionData setMinValue(long value) { return number("min_value", value, true); }
    /**
     * Sets the largest accepted integer or number.
     *
     * @param value a JSON-safe bound
     * @return this option
     * @throws IllegalArgumentException for non-numeric options or a maximum below the minimum
     */
    public synchronized OptionData setMaxValue(long value) { return number("max_value", value, true); }
    /**
     * Sets the smallest accepted number.
     *
     * @param value a finite, JSON-safe bound
     * @return this option
     * @throws IllegalArgumentException unless this is a number option, or for a minimum above the maximum
     */
    public synchronized OptionData setMinValue(double value) { return number("min_value", value, false); }
    /**
     * Sets both integer bounds, as JDA's {@code setRequiredRange}.
     *
     * @param min the smallest accepted value
     * @param max the largest accepted value
     * @return this option
     * @throws IllegalArgumentException for non-numeric options or {@code min > max}
     */
    public synchronized OptionData setRequiredRange(long min, long max) { setMinValue(min); return setMaxValue(max); }
    /**
     * Sets both number bounds, as JDA's {@code setRequiredRange}.
     *
     * @param min the smallest accepted value
     * @param max the largest accepted value
     * @return this option
     * @throws IllegalArgumentException unless this is a number option, or for {@code min > max}
     */
    public synchronized OptionData setRequiredRange(double min, double max) { setMinValue(min); return setMaxValue(max); }
    /**
     * Sets the largest accepted number.
     *
     * @param value a finite, JSON-safe bound
     * @return this option
     * @throws IllegalArgumentException unless this is a number option, or for a maximum below the minimum
     */
    public synchronized OptionData setMaxValue(double value) { return number("max_value", value, false); }
    private OptionData number(String key, double value, boolean integral) {
        if ((type != OptionType.INTEGER && type != OptionType.NUMBER) || (type == OptionType.INTEGER && !integral)
                || !Double.isFinite(value) || Math.abs(value) > 9007199254740991d)
            throw new IllegalArgumentException("Invalid numeric option bound");
        String other = key.equals("min_value") ? "max_value" : "min_value";
        if (body.has(other) && (key.equals("min_value") ? value > body.path(other).asDouble() : value < body.path(other).asDouble()))
            throw new IllegalArgumentException("Minimum cannot exceed maximum");
        if (integral) body.put(key, (long) value); else body.put(key, value);
        return this;
    }
    /**
     * Sets the shortest accepted string.
     *
     * @param value 0-6000 characters
     * @return this option
     * @throws IllegalArgumentException unless this is a string option, or for a minimum above the maximum
     */
    public synchronized OptionData setMinLength(int value) { return length("min_length", value, 0); }
    /**
     * Sets the longest accepted string.
     *
     * @param value 1-6000 characters
     * @return this option
     * @throws IllegalArgumentException unless this is a string option, or for a maximum below the minimum
     */
    public synchronized OptionData setMaxLength(int value) { return length("max_length", value, 1); }
    private OptionData length(String key, int value, int lower) {
        if (type != OptionType.STRING || value < lower || value > 6000) throw new IllegalArgumentException("Invalid string length bound");
        String other = key.equals("min_length") ? "max_length" : "min_length";
        if (body.has(other) && (key.equals("min_length") ? value > body.path(other).asInt() : value < body.path(other).asInt()))
            throw new IllegalArgumentException("Minimum cannot exceed maximum");
        body.put(key, value); return this;
    }
    /**
     * Restricts a channel option to some channel types.
     *
     * @param types the types
     * @return this option
     */
    public OptionData setChannelTypes(ChannelType... types) { return setChannelTypes(Arrays.asList(types)); }
    /**
     * Restricts a channel option to some channel types.
     *
     * @param types at most 25 known types
     * @return this option
     * @throws IllegalArgumentException unless this is a channel option, or for unknown types
     */
    public synchronized OptionData setChannelTypes(Collection<ChannelType> types) {
        if (type != OptionType.CHANNEL || types.size() > 25) throw new IllegalArgumentException("Invalid channel option filter");
        var array = com.swadedev.micup4j.internal.Json.MAPPER.createArrayNode();
        for (var channel : new LinkedHashSet<>(types)) {
            if (Objects.requireNonNull(channel) == ChannelType.UNKNOWN) throw new IllegalArgumentException("Unknown channel type");
            array.add(channel.getId());
        }
        body.set("channel_types", array); return this;
    }
    /**
     * Returns a copy of the predefined choices.
     *
     * @return a copy of the predefined choices
     */
    public synchronized List<Command.Choice> getChoices() {
        var result = new ArrayList<Command.Choice>();
        for (var entry : body.path("choices")) {
            var value = entry.path("value"); var name = entry.path("name").asText();
            result.add(value.isTextual() ? new Command.Choice(name, value.asText())
                    : value.isIntegralNumber() ? new Command.Choice(name, value.longValue()) : new Command.Choice(name, value.doubleValue()));
        }
        return List.copyOf(result);
    }
    /**
     * Returns the minimum value, or {@code null}.
     *
     * @return the minimum value, or {@code null}
     */
    public synchronized Number getMinValue() { return body.has("min_value") ? body.get("min_value").numberValue() : null; }
    /**
     * Returns the maximum value, or {@code null}.
     *
     * @return the maximum value, or {@code null}
     */
    public synchronized Number getMaxValue() { return body.has("max_value") ? body.get("max_value").numberValue() : null; }
    /**
     * Returns the minimum length, or {@code null}.
     *
     * @return the minimum length, or {@code null}
     */
    public synchronized Integer getMinLength() { return body.has("min_length") ? body.get("min_length").intValue() : null; }
    /**
     * Returns the maximum length, or {@code null}.
     *
     * @return the maximum length, or {@code null}
     */
    public synchronized Integer getMaxLength() { return body.has("max_length") ? body.get("max_length").intValue() : null; }
    /**
     * Returns the allowed channel types; empty for no restriction.
     *
     * @return the allowed channel types; empty for no restriction
     */
    public synchronized EnumSet<ChannelType> getChannelTypes() {
        var result = EnumSet.noneOf(ChannelType.class);
        for (var entry : body.path("channel_types")) result.add(ChannelType.fromId(entry.intValue()));
        return result;
    }
    private void choiceType() {
        if (type != OptionType.STRING && type != OptionType.INTEGER && type != OptionType.NUMBER)
            throw new IllegalArgumentException("Choices/autocomplete require string, integer or number options");
    }
    /**
     * Returns a copy of the option definition payload.
     *
     * @return a copy of the option definition payload
     */
    public synchronized ObjectNode toJson() { return body.deepCopy(); }
}
