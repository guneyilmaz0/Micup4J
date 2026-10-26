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
 * A lazy edit of an interaction webhook message, created by {@code InteractionHook.editOriginal*} and
 * {@code InteractionHook.editMessage*ById}.
 *
 * <p>Only the fields that were set are sent; Micup keeps every omitted field. An edit that leaves no visible content
 * in the request body (for example {@code setContent("")} to clear the text while keeping the stored embeds) is sent
 * with {@code "flags":0}, which Micup accepts; the message keeps its original visibility either way. There is
 * deliberately no {@code setEphemeral}: an edit cannot change visibility.
 *
 * <p>Setters may be called until the first execution; afterwards they throw {@link IllegalStateException} and every
 * further execution sends the same payload again.
 *
 * @param <T> the result type
 */
public final class WebhookMessageEditAction<T> extends RestAction<T> {
    private final MessageBody body;

    /**
     * Creates the action. Built by {@code InteractionHook}; not intended for user code.
     *
     * @param initialBody the initial edit payload, copied
     * @param sender sends the payload; called once per execution
     */
    public WebhookMessageEditAction(final ObjectNode initialBody,
                                    final Function<? super ObjectNode, ? extends RestAction<T>> sender) {
        this(new MessageBody(initialBody), sender);
    }

    private WebhookMessageEditAction(final MessageBody body,
                                     final Function<? super ObjectNode, ? extends RestAction<T>> sender) {
        super(() -> execute(body, sender));
        Objects.requireNonNull(sender);
        this.body = body;
    }

    private static <T> CompletableFuture<T> execute(final MessageBody body,
                                                    final Function<? super ObjectNode, ? extends RestAction<T>> sender) {
        ObjectNode payload = body.submit(() -> {
            if (body.isEmpty()) throw new IllegalStateException("A message edit requires at least one field");
        });
        // Micup rejects an edit body without visible content, non-empty embeds or flags; flags 0 keeps visibility.
        if (payload.path("content").asText("").isBlank() && payload.path("embeds").isEmpty() && !payload.has("flags"))
            payload.put("flags", 0);
        return sender.apply(payload).submit();
    }

    /**
     * Replaces the content.
     *
     * @param content the new content; {@code null} or {@code ""} clears it
     * @return this action
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageEditAction<T> setContent(final String content) {
        body.setContent(content);
        return this;
    }

    /**
     * Replaces the embeds. An empty array removes all embeds.
     *
     * @param embeds the embeds, at most 10
     * @return this action
     * @throws IllegalArgumentException if more than 10 embeds are given
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageEditAction<T> setEmbeds(final MessageEmbed... embeds) {
        return setEmbeds(Arrays.asList(embeds));
    }

    /**
     * Replaces the embeds. An empty collection removes all embeds.
     *
     * @param embeds the embeds, at most 10
     * @return this action
     * @throws IllegalArgumentException if more than 10 embeds are given
     * @throws IllegalStateException if the action was already submitted
     */
    public WebhookMessageEditAction<T> setEmbeds(final Collection<? extends MessageEmbed> embeds) {
        body.setEmbeds(embeds, false);
        return this;
    }
}
