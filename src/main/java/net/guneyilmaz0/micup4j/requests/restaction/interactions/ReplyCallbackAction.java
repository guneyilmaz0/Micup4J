package net.guneyilmaz0.micup4j.requests.restaction.interactions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import net.guneyilmaz0.micup4j.events.InteractionCreateEvent;
import net.guneyilmaz0.micup4j.interactions.InteractionHook;
import net.guneyilmaz0.micup4j.internal.MessagePayload;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.concurrent.CompletableFuture;

/** A lazy, single-interaction callback; successful completion exposes the acknowledged hook. */
public final class ReplyCallbackAction extends RestAction<InteractionHook> {
    private final State state;
    public ReplyCallbackAction(InteractionCreateEvent event, int callbackType, ObjectNode body) { this(new State(event, callbackType, body)); }
    private ReplyCallbackAction(State state) { super(state::execute); this.state = state; }
    public ReplyCallbackAction setEphemeral(boolean ephemeral) { state.setEphemeral(ephemeral); return this; }
    public ReplyCallbackAction setContent(String content) { state.setContent(content); return this; }
    public ReplyCallbackAction addEmbeds(MessageEmbed... embeds) { state.addEmbeds(embeds); return this; }
    private static final class State {
        final InteractionCreateEvent event;
        final int callbackType;
        final ObjectNode body;
        boolean submitted;
        State(InteractionCreateEvent event, int callbackType, ObjectNode body) {
            this.event = java.util.Objects.requireNonNull(event); this.callbackType = callbackType; this.body = body.deepCopy();
        }
        void mutable() { if (submitted) throw new IllegalStateException("Callback action has already been submitted"); }
        synchronized void setEphemeral(boolean value) { mutable(); body.put("flags", value ? 64 : 0); }
        synchronized void setContent(String value) {
            mutable(); if (callbackType == 5) throw new IllegalStateException("A deferred callback has no message content");
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Reply content is required");
            body.put("content", value);
        }
        synchronized void addEmbeds(MessageEmbed... embeds) {
            mutable(); if (callbackType == 5) throw new IllegalStateException("A deferred callback has no message embeds");
            var incoming = MessagePayload.embeds(embeds).withArray("embeds");
            if (body.path("embeds").size() + incoming.size() > 10) throw new IllegalArgumentException("A message supports at most 10 embeds");
            body.withArray("embeds").addAll(incoming);
        }
        synchronized CompletableFuture<InteractionHook> execute() {
            submitted = true;
            return event.respond(callbackType, body.deepCopy()).map(ignored -> event.getHook()).submit();
        }
    }
}
