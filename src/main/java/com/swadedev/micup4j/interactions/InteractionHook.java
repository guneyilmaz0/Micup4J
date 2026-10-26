package com.swadedev.micup4j.interactions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swadedev.micup4j.Micup;
import com.swadedev.micup4j.entities.ISnowflake;
import com.swadedev.micup4j.entities.Message;
import com.swadedev.micup4j.entities.MessageEmbed;
import com.swadedev.micup4j.events.InteractionCreateEvent;
import com.swadedev.micup4j.internal.Json;
import com.swadedev.micup4j.requests.RestAction;
import com.swadedev.micup4j.requests.restaction.WebhookMessageCreateAction;
import com.swadedev.micup4j.requests.restaction.WebhookMessageEditAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Typed webhook access for an acknowledged interaction, subject to Micup's 15-minute token lifetime.
 *
 * <p>Every action waits for the initial acknowledgement before it sends a request and fails when the acknowledgement
 * failed or the hook has expired.
 *
 * <p>After {@code deferReply}, the first {@code sendMessage*} creates the original reply (a {@code PATCH} of
 * {@code @original}); every later send is a followup. Message ids accept the literal {@code "@original"}, which
 * targets the original reply.
 */
public final class InteractionHook implements ISnowflake {
    private static final String ORIGINAL = "@original";

    private final InteractionCreateEvent event;
    private final long expiresAt = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
    private final AtomicBoolean originalClaimed = new AtomicBoolean();
    private volatile boolean deferred, ephemeral, defaultEphemeral;

    /**
     * Creates the hook of an interaction. Every interaction event owns exactly one hook; use
     * {@code event.getHook()} instead of calling this constructor.
     *
     * @param event the interaction
     */
    public InteractionHook(InteractionCreateEvent event) { this.event = Objects.requireNonNull(event); }

    /**
     * Records the submitted callback. Called by the interaction event; not intended for user code.
     *
     * @param callbackType the Micup callback type
     * @param ephemeral whether the callback was ephemeral
     */
    public void markCallback(int callbackType, boolean ephemeral) {
        deferred = callbackType == 5; this.ephemeral = ephemeral;
        if (!deferred) originalClaimed.set(true);
    }

    @Override public String getId() { return event.getId(); }

    /**
     * Returns the interaction this hook belongs to.
     *
     * @return the interaction
     */
    public Interaction getInteraction() { return event; }

    /**
     * Returns the session that received the interaction.
     *
     * @return the owning session
     */
    public Micup getJDA() { return event.getJDA(); }

    /**
     * Returns whether the 15-minute interaction token lifetime has passed.
     *
     * @return {@code true} once expired
     */
    public boolean isExpired() { return System.nanoTime() >= expiresAt; }

    /**
     * Returns whether the initial callback was ephemeral. This is Micup-specific and independent of
     * {@link #setEphemeral(boolean)}.
     *
     * @return {@code true} if the original answer is ephemeral
     */
    public boolean isEphemeral() { return ephemeral; }

    /**
     * Sets the default ephemeral setting of messages created later with {@code sendMessage*}. The value is read
     * when the action is created; {@link WebhookMessageCreateAction#setEphemeral(boolean)} overrides it per action.
     *
     * @param ephemeral {@code true} to make later followups ephemeral by default
     * @return this hook
     */
    public InteractionHook setEphemeral(boolean ephemeral) { defaultEphemeral = ephemeral; return this; }

    // ---------------------------------------------------------------- send

    /**
     * Sends a message. After {@code deferReply} the first send creates the original reply; later sends are
     * followups.
     *
     * @param content the content
     * @return a lazy create action
     */
    public WebhookMessageCreateAction<Message> sendMessage(String content) {
        return createAction(Json.object().put("content", content == null ? "" : content));
    }

    /**
     * Sends a message with embeds.
     *
     * @param embed the first embed
     * @param embeds further embeds; at most 10 in total
     * @return a lazy create action
     * @throws IllegalArgumentException if more than 10 embeds are given
     */
    public WebhookMessageCreateAction<Message> sendMessageEmbeds(MessageEmbed embed, MessageEmbed... embeds) {
        List<MessageEmbed> all = new ArrayList<>();
        all.add(Objects.requireNonNull(embed, "embed"));
        all.addAll(Arrays.asList(embeds));
        return sendMessageEmbeds(all);
    }

    /**
     * Sends a message with embeds.
     *
     * @param embeds the embeds, 1 to 10
     * @return a lazy create action
     * @throws IllegalArgumentException if more than 10 embeds are given
     */
    public WebhookMessageCreateAction<Message> sendMessageEmbeds(Collection<? extends MessageEmbed> embeds) {
        return createAction(Json.object()).setEmbeds(embeds);
    }

    // ---------------------------------------------------------------- original

    /**
     * Edits the content of the original reply. After a public {@code deferReply} this creates the reply.
     *
     * @param content the new content; {@code null} or {@code ""} clears it
     * @return a lazy edit action
     */
    public WebhookMessageEditAction<Message> editOriginal(String content) {
        return editAction(ORIGINAL).setContent(content);
    }

    /**
     * Replaces the embeds of the original reply.
     *
     * @param embeds the embeds, at most 10; none removes all embeds
     * @return a lazy edit action
     */
    public WebhookMessageEditAction<Message> editOriginalEmbeds(MessageEmbed... embeds) {
        return editAction(ORIGINAL).setEmbeds(embeds);
    }

    /**
     * Replaces the embeds of the original reply.
     *
     * @param embeds the embeds, at most 10; none removes all embeds
     * @return a lazy edit action
     */
    public WebhookMessageEditAction<Message> editOriginalEmbeds(Collection<? extends MessageEmbed> embeds) {
        return editAction(ORIGINAL).setEmbeds(embeds);
    }

    /**
     * Retrieves the original reply. Fails locally after a public {@code deferReply} until a message created it,
     * because Micup has no public deferred placeholder.
     *
     * @return the original message
     */
    public RestAction<Message> retrieveOriginal() {
        return afterAcknowledgement(() -> {
            // A public deferral has no retrievable placeholder, so fail locally instead of requesting an @original that does not exist yet.
            if (deferred && !ephemeral && !originalClaimed.get()) throw new IllegalStateException("Micup has no public deferred placeholder; editOriginal must create the response first");
            return event.retrieveOriginal().map(this::message);
        });
    }

    /**
     * Deletes the original reply.
     *
     * @return the delete action
     */
    public RestAction<Void> deleteOriginal() { return afterAcknowledgement(event::deleteOriginal); }

    // ---------------------------------------------------------------- by id

    /**
     * Edits the content of a message sent through this hook.
     *
     * @param messageId the message id, or {@code "@original"}
     * @param content the new content; {@code null} or {@code ""} clears it
     * @return a lazy edit action
     * @throws IllegalArgumentException if {@code messageId} is not a canonical snowflake or {@code "@original"}
     */
    public WebhookMessageEditAction<Message> editMessageById(String messageId, String content) {
        return editAction(messageId(messageId)).setContent(content);
    }

    /**
     * Edits the content of a message sent through this hook.
     *
     * @param messageId the message id
     * @param content the new content; {@code null} or {@code ""} clears it
     * @return a lazy edit action
     */
    public WebhookMessageEditAction<Message> editMessageById(long messageId, String content) {
        return editMessageById(Long.toUnsignedString(messageId), content);
    }

    /**
     * Replaces the embeds of a message sent through this hook.
     *
     * @param messageId the message id, or {@code "@original"}
     * @param embeds the embeds, at most 10; none removes all embeds
     * @return a lazy edit action
     * @throws IllegalArgumentException if {@code messageId} is not a canonical snowflake or {@code "@original"}
     */
    public WebhookMessageEditAction<Message> editMessageEmbedsById(String messageId, MessageEmbed... embeds) {
        return editAction(messageId(messageId)).setEmbeds(embeds);
    }

    /**
     * Replaces the embeds of a message sent through this hook.
     *
     * @param messageId the message id, or {@code "@original"}
     * @param embeds the embeds, at most 10; none removes all embeds
     * @return a lazy edit action
     * @throws IllegalArgumentException if {@code messageId} is not a canonical snowflake or {@code "@original"}
     */
    public WebhookMessageEditAction<Message> editMessageEmbedsById(String messageId, Collection<? extends MessageEmbed> embeds) {
        return editAction(messageId(messageId)).setEmbeds(embeds);
    }

    /**
     * Replaces the embeds of a message sent through this hook.
     *
     * @param messageId the message id
     * @param embeds the embeds, at most 10; none removes all embeds
     * @return a lazy edit action
     */
    public WebhookMessageEditAction<Message> editMessageEmbedsById(long messageId, MessageEmbed... embeds) {
        return editMessageEmbedsById(Long.toUnsignedString(messageId), embeds);
    }

    /**
     * Replaces the embeds of a message sent through this hook.
     *
     * @param messageId the message id as an unsigned 64-bit value
     * @param embeds the embeds, at most 10; an empty collection removes all embeds
     * @return a lazy edit action
     */
    public WebhookMessageEditAction<Message> editMessageEmbedsById(long messageId, Collection<? extends MessageEmbed> embeds) {
        return editMessageEmbedsById(Long.toUnsignedString(messageId), embeds);
    }

    /**
     * Deletes a message sent through this hook.
     *
     * @param messageId the message id, or {@code "@original"}
     * @return the delete action
     * @throws IllegalArgumentException if {@code messageId} is not a canonical snowflake or {@code "@original"}
     */
    public RestAction<Void> deleteMessageById(String messageId) {
        String id = messageId(messageId);
        if (id.equals(ORIGINAL)) return deleteOriginal();
        return afterAcknowledgement(() -> event.deleteFollowup(id));
    }

    /**
     * Deletes a message sent through this hook.
     *
     * @param messageId the message id
     * @return the delete action
     */
    public RestAction<Void> deleteMessageById(long messageId) { return deleteMessageById(Long.toUnsignedString(messageId)); }

    /**
     * Retrieves a message sent through this hook.
     *
     * @param messageId the message id, or {@code "@original"}
     * @return the message
     * @throws IllegalArgumentException if {@code messageId} is not a canonical snowflake or {@code "@original"}
     */
    public RestAction<Message> retrieveMessageById(String messageId) {
        String id = messageId(messageId);
        if (id.equals(ORIGINAL)) return retrieveOriginal();
        return afterAcknowledgement(() -> event.retrieveFollowup(id).map(this::message));
    }

    /**
     * Retrieves a message sent through this hook.
     *
     * @param messageId the message id as an unsigned 64-bit value
     * @return the message
     */
    public RestAction<Message> retrieveMessageById(long messageId) {
        return retrieveMessageById(Long.toUnsignedString(messageId));
    }

    // ---------------------------------------------------------------- internals

    private WebhookMessageCreateAction<Message> createAction(ObjectNode body) {
        return new WebhookMessageCreateAction<>(body, defaultEphemeral, payload -> afterAcknowledgement(() -> {
            // After a deferral, the first send claims the original response exactly once (compareAndSet), so concurrent sends
            // cannot both patch @original; every later send is a followup.
            if (deferred && originalClaimed.compareAndSet(false, true)) {
                // The visibility of the original was fixed by deferReply(ephemeral).
                ObjectNode original = payload.deepCopy();
                original.remove("flags");
                return event.editOriginal(original).map(this::message);
            }
            return event.followup(payload).map(this::message);
        }));
    }

    private WebhookMessageEditAction<Message> editAction(String id) {
        return new WebhookMessageEditAction<>(Json.object(), payload -> afterAcknowledgement(() -> {
            if (id.equals(ORIGINAL)) {
                originalClaimed.set(true);
                return event.editOriginal(payload).map(this::message);
            }
            return event.editFollowup(id, payload).map(this::message);
        }));
    }

    private Message message(JsonNode data) {
        return Message.fromContext(event.getMicup(), data, event.getGuildId());
    }

    private static String messageId(String messageId) {
        Objects.requireNonNull(messageId, "messageId");
        return messageId.equals(ORIGINAL) ? ORIGINAL : Json.id(messageId);
    }

    private <T> RestAction<T> afterAcknowledgement(Supplier<RestAction<T>> action) {
        return new RestAction<Void>(() -> {
            if (isExpired()) return CompletableFuture.failedFuture(new IllegalStateException("Interaction hook has expired"));
            return event.acknowledgement();
        }).flatMap(ignored -> {
            if (isExpired()) throw new IllegalStateException("Interaction hook has expired");
            return action.get();
        });
    }

    @Override public String toString() { return "InteractionHook[id=" + getId() + "]"; }
}
