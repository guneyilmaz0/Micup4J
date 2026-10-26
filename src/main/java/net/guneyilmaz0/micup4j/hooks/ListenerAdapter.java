package net.guneyilmaz0.micup4j.hooks;
import net.guneyilmaz0.micup4j.events.*;
public abstract class ListenerAdapter implements EventListener {
    @Override
    public void onEvent(GenericEvent event) {
        onGenericEvent(event);
        if (event instanceof net.guneyilmaz0.micup4j.events.session.ReadyEvent ready) onReady(ready);
        else if (event instanceof ReadyEvent ready) onReady(ready);
        else if (event instanceof net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent message) onMessageReceived(message);
        else if (event instanceof MessageReceivedEvent message) onMessageReceived(message);
        else if (event instanceof net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent slash) {
            onInteractionCreate(slash);
            onSlashCommandInteraction(slash);
        }
        else if (event instanceof InteractionCreateEvent interaction) onInteractionCreate(interaction);
        else if (event instanceof StatusChangeEvent status) onStatusChange(status);
        else if (event instanceof ReconnectEvent reconnect) onReconnect(reconnect);
    }

    public void onReady(net.guneyilmaz0.micup4j.events.session.ReadyEvent event) { onReady((ReadyEvent) event); }
    public void onMessageReceived(net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent event) { onMessageReceived((MessageReceivedEvent) event); }
    public void onSlashCommandInteraction(net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent event) {}

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
