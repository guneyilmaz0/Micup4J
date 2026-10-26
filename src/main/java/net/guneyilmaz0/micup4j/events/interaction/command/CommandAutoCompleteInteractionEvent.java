package net.guneyilmaz0.micup4j.events.interaction.command;

import com.fasterxml.jackson.databind.JsonNode;
import net.guneyilmaz0.micup4j.Micup;
import net.guneyilmaz0.micup4j.interactions.AutoCompleteQuery;
import net.guneyilmaz0.micup4j.interactions.commands.*;
import net.guneyilmaz0.micup4j.requests.restaction.interactions.AutoCompleteCallbackAction;
import java.util.*;

/** Typed autocomplete dispatch, sharing the initial response deadline and acknowledgement guard. */
public final class CommandAutoCompleteInteractionEvent extends net.guneyilmaz0.micup4j.events.InteractionCreateEvent {
    /**
     * Creates the event.
     *
     * @param client   the owning client
     * @param sequence the Gateway sequence number
     * @param data     the {@code INTERACTION_CREATE} payload of an autocomplete interaction
     * @throws IllegalArgumentException if the payload is invalid, see {@link net.guneyilmaz0.micup4j.events.InteractionCreateEvent}
     */
    public CommandAutoCompleteInteractionEvent(Micup client, long sequence, JsonNode data) { super(client, sequence, data); }
    /**
     * Returns the option the user is typing in.
     *
     * @return the focused option, whose value is the raw text so far
     */
    public AutoCompleteQuery getFocusedOption() {
        for (var option : selectedOptions()) if (option.path("focused").asBoolean()) {
            var mapped = new OptionMapping(getMicup(), getGuildId(), option, rawData().path("data").path("resolved"));
            return new AutoCompleteQuery(mapped);
        }
        throw new IllegalStateException("Autocomplete interaction has no focused option");
    }
    /**
     * Answers with suggestions. At most one initial response is accepted per interaction.
     *
     * @param choices the suggestions
     * @return an action that submits the choices when queued
     */
    public AutoCompleteCallbackAction replyChoices(Command.Choice... choices) { return replyChoices(Arrays.asList(choices)); }
    /**
     * Answers with suggestions. At most one initial response is accepted per interaction.
     *
     * @param choices the suggestions
     * @return an action that submits the choices when queued
     */
    public AutoCompleteCallbackAction replyChoices(Collection<Command.Choice> choices) { return new AutoCompleteCallbackAction(this, choices); }
    /**
     * Answers with string suggestions whose name and value are identical.
     *
     * @param choices the suggestions
     * @return an action that submits the choices when queued
     */
    public AutoCompleteCallbackAction replyChoiceStrings(String... choices) {
        return replyChoices(Arrays.stream(choices).map(value -> new Command.Choice(value, value)).toList());
    }
    /**
     * Answers with integer suggestions whose name is the decimal text of the value.
     *
     * @param choices the suggestions
     * @return an action that submits the choices when queued
     */
    public AutoCompleteCallbackAction replyChoiceLongs(long... choices) {
        return replyChoices(Arrays.stream(choices).mapToObj(value -> new Command.Choice(Long.toString(value), value)).toList());
    }
    /**
     * Answers with number suggestions whose name is the text of the value.
     *
     * @param choices the suggestions
     * @return an action that submits the choices when queued
     */
    public AutoCompleteCallbackAction replyChoiceDoubles(double... choices) {
        return replyChoices(Arrays.stream(choices).mapToObj(value -> new Command.Choice(Double.toString(value), value)).toList());
    }
}
