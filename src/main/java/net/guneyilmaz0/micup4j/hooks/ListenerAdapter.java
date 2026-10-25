package net.guneyilmaz0.micup4j.hooks;
import net.guneyilmaz0.micup4j.events.*;
public abstract class ListenerAdapter implements EventListener {
    @Override
    public void onEvent(GenericEvent event) {
        onGenericEvent(event);
        if (event instanceof ReadyEvent ready) onReady(ready);
        else if (event instanceof MessageReceivedEvent message) onMessageReceived(message);
        else if (event instanceof InteractionCreateEvent interaction) onInteractionCreate(interaction);
        else if (event instanceof StatusChangeEvent status) onStatusChange(status);
        else if (event instanceof ReconnectEvent reconnect) onReconnect(reconnect);
    }

    public void onGenericEvent(GenericEvent event) {
    }

    public void onStatusChange(StatusChangeEvent event) {}

    public void onReconnect(ReconnectEvent event) {}

    public void onReady(ReadyEvent event) {
    }

    public void onMessageReceived(MessageReceivedEvent event) {
    }

    public void onInteractionCreate(InteractionCreateEvent event) {
    }
}
