package net.guneyilmaz0.micup4j.interactions;

import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.events.InteractionCreateEvent;
import net.guneyilmaz0.micup4j.requests.RestAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Typed webhook access for an acknowledged interaction, subject to Micup's 15-minute token lifetime. */
public final class InteractionHook implements ISnowflake {
    private final InteractionCreateEvent event;
    private final long expiresAt = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
    private final AtomicBoolean originalClaimed = new AtomicBoolean();
    private volatile boolean deferred, ephemeral;
    public InteractionHook(InteractionCreateEvent event) { this.event = java.util.Objects.requireNonNull(event); }
    public void markCallback(int callbackType, boolean ephemeral) {
        deferred = callbackType == 5; this.ephemeral = ephemeral;
        if (!deferred) originalClaimed.set(true);
    }
    @Override public String getId() { return event.getId(); }
    public boolean isExpired() { return System.nanoTime() >= expiresAt; }
    public boolean isEphemeral() { return ephemeral; }
    public RestAction<Message> editOriginal(String content) {
        return afterAcknowledgement(() -> {
            originalClaimed.set(true);
            return event.editOriginal(content).map(data -> Message.fromContext(event.getMicup(), data, event.getGuildId()));
        });
    }
    public RestAction<Message> editOriginalEmbeds(MessageEmbed... embeds) {
        MessageEmbed[] copy = embeds.clone();
        return afterAcknowledgement(() -> {
            originalClaimed.set(true);
            return event.editOriginalEmbeds(copy).map(data -> Message.fromContext(event.getMicup(), data, event.getGuildId()));
        });
    }
    public RestAction<Message> retrieveOriginal() {
        return afterAcknowledgement(() -> {
            if (deferred && !ephemeral && !originalClaimed.get()) throw new IllegalStateException("Micup has no public deferred placeholder; editOriginal must create the response first");
            return event.retrieveOriginal().map(data -> Message.fromContext(event.getMicup(), data, event.getGuildId()));
        });
    }
    public RestAction<Void> deleteOriginal() { return afterAcknowledgement(event::deleteOriginal); }
    /** The first send after defer creates the original reply; later sends are public followups. */
    public RestAction<Message> sendMessage(String content) {
        return afterAcknowledgement(() -> {
            if (deferred && originalClaimed.compareAndSet(false, true))
                return event.editOriginal(content).map(data -> Message.fromContext(event.getMicup(), data, event.getGuildId()));
            return event.followup(content, false).map(data -> Message.fromContext(event.getMicup(), data, event.getGuildId()));
        });
    }
    private <T> RestAction<T> afterAcknowledgement(Supplier<RestAction<T>> action) {
        return new RestAction<Void>(() -> {
            if (isExpired()) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Interaction hook has expired"));
            return event.acknowledgement();
        }).flatMap(ignored -> {
            if (isExpired()) throw new IllegalStateException("Interaction hook has expired");
            return action.get();
        });
    }
    @Override public String toString() { return "InteractionHook[id=" + getId() + "]"; }
}
