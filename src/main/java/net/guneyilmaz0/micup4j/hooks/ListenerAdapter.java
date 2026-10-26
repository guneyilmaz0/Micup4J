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
        else if (event instanceof net.guneyilmaz0.micup4j.events.guild.GenericGuildEvent guild) {
            onGenericGuild(guild);
            if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildReadyEvent ready) onGuildReady(ready);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildJoinEvent joined) onGuildJoin(joined);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildAvailableEvent available) onGuildAvailable(available);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildUnavailableEvent unavailable) onGuildUnavailable(unavailable);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildLeaveEvent left) onGuildLeave(left);
        }
        else if (event instanceof StatusChangeEvent status) onStatusChange(status);
        else if (event instanceof ReconnectEvent reconnect) onReconnect(reconnect);
    }

    public void onReady(net.guneyilmaz0.micup4j.events.session.ReadyEvent event) { onReady((ReadyEvent) event); }
    public void onMessageReceived(net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent event) { onMessageReceived((MessageReceivedEvent) event); }
    public void onSlashCommandInteraction(net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent event) {}

    public void onGenericGuild(net.guneyilmaz0.micup4j.events.guild.GenericGuildEvent event) {}
    public void onGuildReady(net.guneyilmaz0.micup4j.events.guild.GuildReadyEvent event) {}
    public void onGuildJoin(net.guneyilmaz0.micup4j.events.guild.GuildJoinEvent event) {}
    public void onGuildAvailable(net.guneyilmaz0.micup4j.events.guild.GuildAvailableEvent event) {}
    public void onGuildUnavailable(net.guneyilmaz0.micup4j.events.guild.GuildUnavailableEvent event) {}
    public void onGuildLeave(net.guneyilmaz0.micup4j.events.guild.GuildLeaveEvent event) {}

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
