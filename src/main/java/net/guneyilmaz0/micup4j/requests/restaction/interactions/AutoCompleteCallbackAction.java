package net.guneyilmaz0.micup4j.requests.restaction.interactions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.guneyilmaz0.micup4j.interactions.commands.*;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** A lazy autocomplete response with up to 25 correctly typed choices. Empty responses are valid. */
public final class AutoCompleteCallbackAction extends RestAction<Void> {
    private final State state;
    /**
     * Creates the action; prefer {@code event.replyChoices(...)}.
     *
     * @param event   the autocomplete interaction
     * @param choices the initial choices
     */
    public AutoCompleteCallbackAction(CommandAutoCompleteInteractionEvent event, Collection<Command.Choice> choices) { this(new State(event, choices)); }
    private AutoCompleteCallbackAction(State state) { super(state::execute); this.state = state; }
    /**
     * Adds suggestions.
     *
     * @param choices the choices
     * @return this action
     */
    public AutoCompleteCallbackAction addChoices(Command.Choice... choices) { return addChoices(Arrays.asList(choices)); }
    /**
     * Adds suggestions.
     *
     * @param choices the choices; at most 25 in total
     * @return this action
     * @throws IllegalArgumentException if there would be more than 25
     */
    public AutoCompleteCallbackAction addChoices(Collection<Command.Choice> choices) { state.add(choices); return this; }
    private static final class State {
        final CommandAutoCompleteInteractionEvent event;
        final ObjectNode body = Json.object();
        boolean submitted;
        State(CommandAutoCompleteInteractionEvent event, Collection<Command.Choice> choices) { this.event = Objects.requireNonNull(event); add(choices); }
        synchronized void add(Collection<Command.Choice> choices) {
            if (submitted) throw new IllegalStateException("Autocomplete action has already been submitted");
            var next = body.has("choices") ? body.withArray("choices").deepCopy() : Json.MAPPER.createArrayNode();
            var type = event.getFocusedOption().getType();
            if (type != OptionType.STRING && type != OptionType.INTEGER && type != OptionType.NUMBER)
                throw new IllegalArgumentException("Invalid autocomplete option type");
            for (var choice : choices) {
                Objects.requireNonNull(choice);
                if (choice.getType() != type && !(type == OptionType.NUMBER && choice.getType() == OptionType.INTEGER))
                    throw new IllegalArgumentException("Choice does not match focused option type");
                next.add(choice.toJson());
            }
            if (next.size() > 25) throw new IllegalArgumentException("Provide at most 25 choices");
            body.set("choices", next);
        }
        synchronized CompletableFuture<Void> execute() { submitted = true; return event.respond(8, body.deepCopy()).submit(); }
    }
}
