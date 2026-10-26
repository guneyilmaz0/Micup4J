package net.guneyilmaz0.micup4j.requests.restaction.interactions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.guneyilmaz0.micup4j.entities.MessageEmbed;
import net.guneyilmaz0.micup4j.events.InteractionCreateEvent;
import net.guneyilmaz0.micup4j.interactions.InteractionHook;
import net.guneyilmaz0.micup4j.interactions.components.ActionRow;
import net.guneyilmaz0.micup4j.interactions.components.ItemComponent;
import net.guneyilmaz0.micup4j.interactions.components.LayoutComponent;
import net.guneyilmaz0.micup4j.internal.Json;
import net.guneyilmaz0.micup4j.internal.MessageBody;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Answers a component interaction by editing the message the component is on (callback type 7), or by acknowledging the
 * click without changes yet (type 6, from {@code deferEdit()}). Modelled on JDA's {@code MessageEditCallbackAction}.
 * Omitted fields keep their stored values. After a deferred edit, change the message with
 * {@code getHook().editOriginal(...)}.
 */
public final class MessageEditCallbackAction extends RestAction<InteractionHook> {
    private final MessageBody body;
    private final int callbackType;

    /**
     * Creates the action.
     *
     * @param event        the component interaction
     * @param callbackType 6 for a deferred edit, 7 for an immediate edit
     */
    public MessageEditCallbackAction(InteractionCreateEvent event, int callbackType) { this(event, callbackType, new MessageBody(Json.object(), "Callback action has already been submitted")); }

    private MessageEditCallbackAction(InteractionCreateEvent event, int callbackType, MessageBody body) {
        super(() -> execute(event, callbackType, body));
        if (callbackType != 6 && callbackType != 7) throw new IllegalArgumentException("Edit callbacks use type 6 or 7");
        this.body = body; this.callbackType = callbackType;
    }

    private static CompletableFuture<InteractionHook> execute(InteractionCreateEvent event, int callbackType, MessageBody body) {
        ObjectNode payload = body.submit(() -> {});
        return event.respond(callbackType, callbackType == 6 ? null : payload).map(ignored -> event.getHook()).submit();
    }

    private void editable() { if (callbackType == 6) throw new IllegalStateException("A deferred edit carries no message changes; use the hook"); }

    public MessageEditCallbackAction setContent(String content) { editable(); body.setContent(content); return this; }
    public MessageEditCallbackAction setEmbeds(MessageEmbed... embeds) { return setEmbeds(Arrays.asList(embeds)); }
    public MessageEditCallbackAction setEmbeds(Collection<? extends MessageEmbed> embeds) { editable(); body.setEmbeds(embeds, false); return this; }
    public MessageEditCallbackAction setComponents(LayoutComponent... rows) { return setComponents(Arrays.asList(rows)); }
    /** An empty collection removes every component from the message. */
    public MessageEditCallbackAction setComponents(Collection<? extends LayoutComponent> rows) { editable(); body.setComponents(rows); return this; }
    /** Replaces the components with a single row. */
    public MessageEditCallbackAction setActionRow(ItemComponent... components) { return setComponents(ActionRow.of(components)); }
}
