package net.guneyilmaz0.micup4j;

import net.guneyilmaz0.micup4j.interactions.commands.*;
import net.guneyilmaz0.micup4j.interactions.commands.build.*;
import net.guneyilmaz0.micup4j.entities.channel.ChannelType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandDefinitionTest {
    @Test void nestedDefinitionsSerializeCorrectTypesWithoutRequiredOnStructuralOptions() {
        var option = new OptionData(OptionType.STRING, "query", "Search query", true, true).setMinLength(1).setMaxLength(60);
        var sub = new SubcommandData("find", "Find a user").addOptions(option);
        var group = new SubcommandGroupData("users", "User tools").addSubcommands(sub);
        var command = Commands.slash("admin", "Administration").addSubcommandGroups(group);
        option.setRequired(false); sub.addOption(OptionType.BOOLEAN, "verbose", "Extra information");
        var json = command.toJson();
        var wireGroup = json.path("options").get(0); var wireSub = wireGroup.path("options").get(0);
        assertEquals(2, wireGroup.path("type").asInt()); assertFalse(wireGroup.has("required"));
        assertEquals(1, wireSub.path("type").asInt()); assertFalse(wireSub.has("required"));
        assertEquals(1, wireSub.path("options").size());
        assertTrue(wireSub.path("options").get(0).path("required").asBoolean());
        json.removeAll(); assertEquals("admin", command.getName());
    }
    @Test void invalidTreeChangesAreAtomicAndNamesAreUniqueAcrossGroupsAndSubcommands() {
        var command = Commands.slash("tools", "Tools").addSubcommands(new SubcommandData("one", "First"));
        assertThrows(IllegalArgumentException.class, () -> command.addOptions(new OptionData(OptionType.USER, "user", "User")));
        assertThrows(IllegalArgumentException.class, () -> command.addSubcommandGroups(new SubcommandGroupData("one", "Duplicate").addSubcommands(new SubcommandData("child", "Child"))));
        assertEquals(1, command.toJson().path("options").size());
        assertThrows(IllegalStateException.class, () -> new SubcommandGroupData("empty", "Empty").toJson());
        assertThrows(IllegalArgumentException.class, () -> new OptionData(OptionType.SUB_COMMAND, "bad", "Bad"));
        assertThrows(IllegalArgumentException.class, OptionType.SUB_COMMAND::toLegacy);
    }
    @Test void optionOrderAndSiblingLimitAreEnforcedWithoutPartialMutation() {
        var sub = new SubcommandData("test", "Test").addOption(OptionType.STRING, "optional", "Optional");
        assertThrows(IllegalArgumentException.class, () -> sub.addOption(OptionType.STRING, "required", "Required", true));
        for (int i = 1; i < 25; i++) sub.addOption(OptionType.STRING, "option" + i, "Option");
        assertThrows(IllegalArgumentException.class, () -> sub.addOption(OptionType.STRING, "overflow", "Overflow"));
        assertEquals(25, sub.toJson().path("options").size());
    }
    @Test void choicesMustMatchTypeAndCannotCombineWithAutocomplete() {
        var strings = new OptionData(OptionType.STRING, "fruit", "Fruit").addChoice("Apple", "apple");
        assertThrows(IllegalArgumentException.class, () -> strings.addChoice("Wrong", 1L));
        assertThrows(IllegalArgumentException.class, () -> strings.setAutoComplete(true));
        assertEquals(1, strings.toJson().path("choices").size());
        var numeric = new OptionData(OptionType.NUMBER, "value", "Value").addChoice("One", 1L).addChoice("Half", .5);
        assertTrue(numeric.toJson().path("choices").get(0).path("value").isIntegralNumber());
        var auto = new OptionData(OptionType.INTEGER, "count", "Count", false, true);
        assertThrows(IllegalArgumentException.class, () -> auto.addChoice("One", 1L));
        assertThrows(IllegalArgumentException.class, () -> new OptionData(OptionType.USER, "user", "User").setAutoComplete(true));
    }
    @Test void choicesValidateUnicodeFiniteSafeValuesAndCapacity() {
        assertDoesNotThrow(() -> new Command.Choice("😀".repeat(100), "😀".repeat(100)));
        assertThrows(IllegalArgumentException.class, () -> new Command.Choice("Name", "😀".repeat(101)));
        assertThrows(IllegalArgumentException.class, () -> new Command.Choice("Bad", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Command.Choice("Bad", Long.MAX_VALUE));
        var option = new OptionData(OptionType.INTEGER, "number", "Number");
        for (int i = 0; i < 25; i++) option.addChoice("Choice " + i, (long) i);
        assertThrows(IllegalArgumentException.class, () -> option.addChoice("Overflow", 25L));
        assertEquals(25, option.toJson().path("choices").size());
    }
    @Test void typedBoundsAndFiltersValidateBeforeChangingState() {
        var integer = new OptionData(OptionType.INTEGER, "count", "Count").setMinValue(1L).setMaxValue(5L);
        assertThrows(IllegalArgumentException.class, () -> integer.setMinValue(6L));
        assertThrows(IllegalArgumentException.class, () -> integer.setMinValue(.5));
        assertThrows(IllegalArgumentException.class, () -> integer.setMaxValue(Long.MAX_VALUE));
        assertEquals(1, integer.toJson().path("min_value").asInt());
        var string = new OptionData(OptionType.STRING, "text", "Text").setMinLength(0).setMaxLength(6000);
        assertThrows(IllegalArgumentException.class, () -> string.setMinLength(6001));
        assertThrows(IllegalArgumentException.class, () -> integer.setMaxLength(2));
        assertThrows(IllegalArgumentException.class, () -> string.setMinValue(1L));
        var channel = new OptionData(OptionType.CHANNEL, "channel", "Channel").setChannelTypes(ChannelType.TEXT, ChannelType.VOICE);
        assertEquals(2, channel.toJson().path("channel_types").size());
        assertThrows(IllegalArgumentException.class, () -> channel.setChannelTypes(ChannelType.UNKNOWN));
        assertEquals(2, channel.toJson().path("channel_types").size());
    }
}
