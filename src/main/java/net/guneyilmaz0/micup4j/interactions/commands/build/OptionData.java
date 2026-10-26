package net.guneyilmaz0.micup4j.interactions.commands.build;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.interactions.commands.*;
import net.guneyilmaz0.micup4j.entities.channel.ChannelType;
import java.util.*;

/** JDA-style value option builder. Parent definitions snapshot options when added. */
public final class OptionData {
    private final OptionType type;
    private final ObjectNode body;
    public OptionData(OptionType type, String name, String description) { this(type, name, description, false); }
    public OptionData(OptionType type, String name, String description, boolean required) { this(type, name, description, required, false); }
    public OptionData(OptionType type, String name, String description, boolean required, boolean autocomplete) {
        this.type = Objects.requireNonNull(type);
        if (type.getKey() < 3) throw new IllegalArgumentException("OptionData requires a value option type");
        body = DefinitionSupport.base(type.getKey(), name, description).put("required", required);
        if (autocomplete) setAutoComplete(true);
    }
    public OptionType getType() { return type; }
    public synchronized String getName() { return body.path("name").asText(); }
    public synchronized String getDescription() { return body.path("description").asText(); }
    public synchronized boolean isRequired() { return body.path("required").asBoolean(); }
    public synchronized boolean isAutoComplete() { return body.path("autocomplete").asBoolean(); }
    public synchronized OptionData setRequired(boolean value) { body.put("required", value); return this; }
    public synchronized OptionData setAutoComplete(boolean value) {
        choiceType();
        if (value && body.path("choices").size() > 0) throw new IllegalArgumentException("Choices cannot be combined with autocomplete");
        body.put("autocomplete", value); return this;
    }
    public OptionData addChoice(String name, String value) { return addChoices(new Command.Choice(name, value)); }
    public OptionData addChoice(String name, long value) { return addChoices(new Command.Choice(name, value)); }
    public OptionData addChoice(String name, double value) { return addChoices(new Command.Choice(name, value)); }
    public OptionData addChoices(Command.Choice... values) { return addChoices(Arrays.asList(values)); }
    public synchronized OptionData addChoices(Collection<? extends Command.Choice> values) {
        choiceType();
        if (isAutoComplete()) throw new IllegalArgumentException("Choices cannot be combined with autocomplete");
        var next = body.has("choices") ? body.withArray("choices").deepCopy() : net.guneyilmaz0.micup4j.internal.Json.MAPPER.createArrayNode();
        for (var value : values) {
            Objects.requireNonNull(value);
            if (value.getType() != type && !(type == OptionType.NUMBER && value.getType() == OptionType.INTEGER))
                throw new IllegalArgumentException("Choice value does not match option type");
            next.add(value.toJson());
        }
        if (next.size() > 25) throw new IllegalArgumentException("Provide at most 25 choices");
        body.set("choices", next); return this;
    }
    public synchronized OptionData setMinValue(long value) { return number("min_value", value, true); }
    public synchronized OptionData setMaxValue(long value) { return number("max_value", value, true); }
    public synchronized OptionData setMinValue(double value) { return number("min_value", value, false); }
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
    public synchronized OptionData setMinLength(int value) { return length("min_length", value, 0); }
    public synchronized OptionData setMaxLength(int value) { return length("max_length", value, 1); }
    private OptionData length(String key, int value, int lower) {
        if (type != OptionType.STRING || value < lower || value > 6000) throw new IllegalArgumentException("Invalid string length bound");
        String other = key.equals("min_length") ? "max_length" : "min_length";
        if (body.has(other) && (key.equals("min_length") ? value > body.path(other).asInt() : value < body.path(other).asInt()))
            throw new IllegalArgumentException("Minimum cannot exceed maximum");
        body.put(key, value); return this;
    }
    public OptionData setChannelTypes(ChannelType... types) { return setChannelTypes(Arrays.asList(types)); }
    public synchronized OptionData setChannelTypes(Collection<ChannelType> types) {
        if (type != OptionType.CHANNEL || types.size() > 25) throw new IllegalArgumentException("Invalid channel option filter");
        var array = net.guneyilmaz0.micup4j.internal.Json.MAPPER.createArrayNode();
        for (var channel : new LinkedHashSet<>(types)) {
            if (Objects.requireNonNull(channel) == ChannelType.UNKNOWN) throw new IllegalArgumentException("Unknown channel type");
            array.add(channel.getId());
        }
        body.set("channel_types", array); return this;
    }
    public synchronized List<Command.Choice> getChoices() {
        var result = new ArrayList<Command.Choice>();
        for (var entry : body.path("choices")) {
            var value = entry.path("value"); var name = entry.path("name").asText();
            result.add(value.isTextual() ? new Command.Choice(name, value.asText())
                    : value.isIntegralNumber() ? new Command.Choice(name, value.longValue()) : new Command.Choice(name, value.doubleValue()));
        }
        return List.copyOf(result);
    }
    public synchronized Number getMinValue() { return body.has("min_value") ? body.get("min_value").numberValue() : null; }
    public synchronized Number getMaxValue() { return body.has("max_value") ? body.get("max_value").numberValue() : null; }
    public synchronized Integer getMinLength() { return body.has("min_length") ? body.get("min_length").intValue() : null; }
    public synchronized Integer getMaxLength() { return body.has("max_length") ? body.get("max_length").intValue() : null; }
    public synchronized EnumSet<ChannelType> getChannelTypes() {
        var result = EnumSet.noneOf(ChannelType.class);
        for (var entry : body.path("channel_types")) result.add(ChannelType.fromId(entry.intValue()));
        return result;
    }
    private void choiceType() {
        if (type != OptionType.STRING && type != OptionType.INTEGER && type != OptionType.NUMBER)
            throw new IllegalArgumentException("Choices/autocomplete require string, integer or number options");
    }
    public synchronized ObjectNode toJson() { return body.deepCopy(); }
}
