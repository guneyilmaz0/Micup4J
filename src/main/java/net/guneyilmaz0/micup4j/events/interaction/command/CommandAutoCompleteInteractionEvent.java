package net.guneyilmaz0.micup4j.events.interaction.command;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.interactions.AutoCompleteQuery;
import net.guneyilmaz0.micup4j.interactions.commands.*;
import net.guneyilmaz0.micup4j.requests.restaction.interactions.AutoCompleteCallbackAction;
import java.util.*;

/** Typed autocomplete dispatch, sharing the initial response deadline and acknowledgement guard. */
public final class CommandAutoCompleteInteractionEvent extends net.guneyilmaz0.micup4j.events.InteractionCreateEvent {
    public CommandAutoCompleteInteractionEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }
    public AutoCompleteQuery getFocusedOption() {
        for (var option : selectedOptions()) if (option.path("focused").asBoolean()) {
            var mapped = new OptionMapping(getMicup(), getGuildId(), option, getData().path("data").path("resolved"));
            return new AutoCompleteQuery(mapped);
        }
        throw new IllegalStateException("Autocomplete interaction has no focused option");
    }
    public AutoCompleteCallbackAction replyChoices(Command.Choice... choices) { return replyChoices(Arrays.asList(choices)); }
    public AutoCompleteCallbackAction replyChoices(Collection<Command.Choice> choices) { return new AutoCompleteCallbackAction(this, choices); }
    public AutoCompleteCallbackAction replyChoiceStrings(String... choices) {
        return replyChoices(Arrays.stream(choices).map(value -> new Command.Choice(value, value)).toList());
    }
    public AutoCompleteCallbackAction replyChoiceLongs(long... choices) {
        return replyChoices(Arrays.stream(choices).mapToObj(value -> new Command.Choice(Long.toString(value), value)).toList());
    }
    public AutoCompleteCallbackAction replyChoiceDoubles(double... choices) {
        return replyChoices(Arrays.stream(choices).mapToObj(value -> new Command.Choice(Double.toString(value), value)).toList());
    }
}
