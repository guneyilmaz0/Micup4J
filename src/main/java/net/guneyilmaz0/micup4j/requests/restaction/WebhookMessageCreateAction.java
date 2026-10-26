package net.guneyilmaz0.micup4j.requests.restaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import net.guneyilmaz0.micup4j.internal.MessageBody;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A lazy interaction webhook message, created by {@code InteractionHook.sendMessage} and
 * {@code InteractionHook.sendMessageEmbeds}.
 *
 * <p>The message needs non-blank content or at least one embed; this is checked when the action is executed and a
 * failing execution can be fixed and executed again. Setters may be called until the first execution that passes
 * validation; afterwards they throw {@link IllegalStateException} and every further execution sends the same payload
 * again.
 *
 * <p>The first send after {@code deferReply} creates the original reply instead of a followup. Its visibility was
 * already fixed by {@code deferReply(ephemeral)}, so {@link #setEphemeral(boolean)} has no effect on it, as in JDA.
 * Executing such an action a second time sends a new followup. Micup allows at most 20 followups per interaction;
 * Micup4J does not enforce that limit locally.
 *
 * @param <T> the result type
 */
public final class WebhookMessageCreateAction<T> extends RestAction<T> {
    private final MessageBody body;

    /**
     * Creates the action. Built by {@code InteractionHook}; not intended for user code.
     *
     * @param initialBody the initial message payload, copied
     * @param ephemeral the initial ephemeral setting
     * @param sender sends the validated payload, including its {@code flags}; called once per execution
     */
    public WebhookMessageCreateAction(final ObjectNode initialBody, final boolean ephemeral,
                                      final Function<? super ObjectNode, ? extends RestAction<T>> sender) {
        this(new MessageBody(initialBody), ephemeral, sender);
    }

    private WebhookMessageCreateAction(final MessageBody body, final boolean ephemeral,
                                       final Function<? super ObjectNode, ? extends RestAction<T>> sender) {
        super(() -> execute(body, sender));
        Objects.requireNonNull(sender);
        this.body = body;
        body.setFlag(64, ephemeral);
    }

    private static <T> CompletableFuture<T> execute(final MessageBody body,
                                                    final Function<? super ObjectNode, ? extends RestAction<T>> sender) {
        ObjectNode payload = body.submit(() -> {
            if (!body.hasVisibleContent()) throw new IllegalArgumentException("A message requires content or embeds");
        });
        return sender.apply(payload).submit();
    }

    /**
     * Sets whether the message is only visible to the invoking user. Overrides
     * {@code InteractionHook.setEphemeral} for this action. Ignored when the action creates the original reply of a
     * deferred interaction.
     *
     * @param ephemeral {@code true} for an ephemeral message
     * @return this action
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageCreateAction<T> setEphemeral(final boolean ephemeral) {
        body.setFlag(64, ephemeral);
        return this;
    }

    /**
     * Replaces the content.
     *
     * @param content the new content; {@code null} clears it
     * @return this action
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageCreateAction<T> setContent(final String content) {
        body.setContent(content);
        return this;
    }

    /**
     * Appends text to the content.
     *
     * @param content the text to append, not {@code null}
     * @return this action
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageCreateAction<T> addContent(final String content) {
        body.addContent(content);
        return this;
    }

    /**
     * Replaces the embeds.
     *
     * @param embeds the embeds, at most 10
     * @return this action
     * @throws IllegalArgumentException if more than 10 embeds are given
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageCreateAction<T> setEmbeds(final MessageEmbed... embeds) {
        return setEmbeds(Arrays.asList(embeds));
    }

    /**
     * Replaces the embeds.
     *
     * @param embeds the embeds, at most 10
     * @return this action
     * @throws IllegalArgumentException if more than 10 embeds are given
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageCreateAction<T> setEmbeds(final Collection<? extends MessageEmbed> embeds) {
        body.setEmbeds(embeds, false);
        return this;
    }

    /**
     * Adds embeds.
     *
     * @param embeds the embeds to add; the total must stay at most 10
     * @return this action
     * @throws IllegalArgumentException if the total would exceed 10 embeds
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageCreateAction<T> addEmbeds(final MessageEmbed... embeds) {
        return addEmbeds(Arrays.asList(embeds));
    }

    /**
     * Adds embeds.
     *
     * @param embeds the embeds to add; the total must stay at most 10
     * @return this action
     * @throws IllegalArgumentException if the total would exceed 10 embeds
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageCreateAction<T> addEmbeds(final Collection<? extends MessageEmbed> embeds) {
        body.setEmbeds(embeds, true);
        return this;
    }
    /**
     * Replaces the message components; an empty call removes them.
     *
     * @param rows at most five action rows, message components only, unique custom ids
     * @return this action
     * @throws IllegalArgumentException if the rows are invalid for a message
     */
    public WebhookMessageCreateAction<T> setComponents(net.guneyilmaz0.micup4j.interactions.components.LayoutComponent... rows) { return setComponents(java.util.Arrays.asList(rows)); }

    /**
     * Replaces the message components; an empty collection removes them.
     *
     * @param rows at most five action rows, message components only, unique custom ids
     * @return this action
     * @throws IllegalArgumentException if the rows are invalid for a message
     */
    public WebhookMessageCreateAction<T> setComponents(java.util.Collection<? extends net.guneyilmaz0.micup4j.interactions.components.LayoutComponent> rows) { body.setComponents(rows); return this; }

    /**
     * Appends action rows to the current components.
     *
     * @param rows the rows to add
     * @return this action
     * @throws IllegalArgumentException if the result would be invalid for a message
     */
    public WebhookMessageCreateAction<T> addComponents(net.guneyilmaz0.micup4j.interactions.components.LayoutComponent... rows) { body.addComponents(java.util.Arrays.asList(rows)); return this; }

    /**
     * Replaces the components with one row.
     *
     * @param components the row's components
     * @return this action
     */
    public WebhookMessageCreateAction<T> setActionRow(net.guneyilmaz0.micup4j.interactions.components.ItemComponent... components) { return setComponents(net.guneyilmaz0.micup4j.interactions.components.ActionRow.of(components)); }

    /**
     * Appends one row.
     *
     * @param components the row's components
     * @return this action
     */
    public WebhookMessageCreateAction<T> addActionRow(net.guneyilmaz0.micup4j.interactions.components.ItemComponent... components) { return addComponents(net.guneyilmaz0.micup4j.interactions.components.ActionRow.of(components)); }
}
