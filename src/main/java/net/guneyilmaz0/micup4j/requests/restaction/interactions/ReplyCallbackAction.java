package net.guneyilmaz0.micup4j.requests.restaction.interactions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import net.guneyilmaz0.micup4j.events.InteractionCreateEvent;
import net.guneyilmaz0.micup4j.interactions.InteractionHook;
import net.guneyilmaz0.micup4j.internal.MessageBody;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A lazy, single-interaction callback; successful completion exposes the acknowledged hook.
 *
 * <p>Setters may be called until the first execution that passes validation; afterwards they throw
 * {@link IllegalStateException}. A reply (callback type 4) needs non-blank content or at least one embed when it is
 * executed. A deferred reply (callback type 5) carries no message, so the content and embed setters throw
 * {@link IllegalStateException} on it.
 */
public final class ReplyCallbackAction extends RestAction<InteractionHook> {
    private final State state;

    /**
     * Creates a callback action. Normally obtained from {@code reply}, {@code replyEmbeds} or {@code deferReply}.
     *
     * @param event the interaction to answer
     * @param callbackType the Micup callback type
     * @param body the initial callback data, copied
     */
    public ReplyCallbackAction(InteractionCreateEvent event, int callbackType, ObjectNode body) { this(new State(event, callbackType, body)); }
    private ReplyCallbackAction(State state) { super(state::execute); this.state = state; }

    /**
     * Sets whether the reply is only visible to the invoking user.
     *
     * @param ephemeral {@code true} for an ephemeral reply
     * @return this action
     */
    public ReplyCallbackAction setEphemeral(boolean ephemeral) { state.body.setFlag(64, ephemeral); return this; }

    /**
     * Replaces the reply content.
     *
     * @param content the new content, not blank
     * @return this action
     * @throws IllegalArgumentException if {@code content} is {@code null} or blank
     * @throws IllegalStateException if this is a deferred reply or the action was already submitted
     */
    public ReplyCallbackAction setContent(String content) {
        state.messageOnly("content");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Reply content is required");
        state.body.setContent(content);
        return this;
    }

    /**
     * Appends text to the reply content.
     *
     * @param content the text to append, not {@code null}
     * @return this action
     * @throws IllegalStateException if this is a deferred reply or the action was already submitted
     */
    public ReplyCallbackAction addContent(String content) {
        state.messageOnly("content");
        state.body.addContent(content);
        return this;
    }

    /**
     * Replaces the embeds of the reply.
     *
     * @param embeds the embeds, at most 10
     * @return this action
     * @throws IllegalArgumentException if more than 10 embeds are given
     * @throws IllegalStateException if this is a deferred reply or the action was already submitted
     */
    public ReplyCallbackAction setEmbeds(MessageEmbed... embeds) { return setEmbeds(Arrays.asList(embeds)); }

    /**
     * Replaces the embeds of the reply.
     *
     * @param embeds the embeds, at most 10
     * @return this action
     * @throws IllegalArgumentException if more than 10 embeds are given
     * @throws IllegalStateException if this is a deferred reply or the action was already submitted
     */
    public ReplyCallbackAction setEmbeds(Collection<? extends MessageEmbed> embeds) {
        state.messageOnly("embeds");
        state.body.setEmbeds(embeds, false);
        return this;
    }

    /**
     * Adds embeds to the reply.
     *
     * @param embeds the embeds to add; the total must stay at most 10
     * @return this action
     * @throws IllegalArgumentException if the total would exceed 10 embeds
     * @throws IllegalStateException if this is a deferred reply or the action was already submitted
     */
    public ReplyCallbackAction addEmbeds(MessageEmbed... embeds) { return addEmbeds(Arrays.asList(embeds)); }

    /**
     * Adds embeds to the reply.
     *
     * @param embeds the embeds to add; the total must stay at most 10
     * @return this action
     * @throws IllegalArgumentException if the total would exceed 10 embeds
     * @throws IllegalStateException if this is a deferred reply or the action was already submitted
     */
    public ReplyCallbackAction addEmbeds(Collection<? extends MessageEmbed> embeds) {
        state.messageOnly("embeds");
        state.body.setEmbeds(embeds, true);
        return this;
    }

    private static final class State {
        final InteractionCreateEvent event;
        final int callbackType;
        final MessageBody body;
        State(InteractionCreateEvent event, int callbackType, ObjectNode body) {
            this.event = Objects.requireNonNull(event); this.callbackType = callbackType;
            this.body = new MessageBody(body, "Callback action has already been submitted");
        }
        void messageOnly(String what) {
            if (body.isFrozen()) throw new IllegalStateException("Callback action has already been submitted");
            if (callbackType == 5) throw new IllegalStateException("A deferred callback has no message " + what);
        }
        CompletableFuture<InteractionHook> execute() {
            var payload = body.submit(() -> {
                if (callbackType == 4 && !body.hasVisibleContent())
                    throw new IllegalArgumentException("A reply requires content or embeds");
            });
            return event.respond(callbackType, payload).map(ignored -> event.getHook()).submit();
        }
    }
}
