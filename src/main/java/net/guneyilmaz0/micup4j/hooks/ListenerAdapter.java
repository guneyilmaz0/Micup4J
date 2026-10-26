package net.guneyilmaz0.micup4j.hooks;
import net.guneyilmaz0.micup4j.events.GenericEvent;
import net.guneyilmaz0.micup4j.events.InteractionCreateEvent;
import net.guneyilmaz0.micup4j.events.MessageReceivedEvent;
import net.guneyilmaz0.micup4j.events.ReadyEvent;
import net.guneyilmaz0.micup4j.events.ReconnectEvent;
import net.guneyilmaz0.micup4j.events.StatusChangeEvent;
import net.guneyilmaz0.micup4j.events.guild.member.GenericGuildMemberEvent;
import net.guneyilmaz0.micup4j.events.guild.member.GuildMemberJoinEvent;
import net.guneyilmaz0.micup4j.events.guild.member.GuildMemberRemoveEvent;
import net.guneyilmaz0.micup4j.events.message.GenericMessageEvent;
import net.guneyilmaz0.micup4j.events.message.MessageDeleteEvent;
import net.guneyilmaz0.micup4j.events.message.MessageUpdateEvent;
import net.guneyilmaz0.micup4j.events.session.ShutdownEvent;
/**
 * Convenience base class of {@link EventListener}, mirroring JDA's {@code ListenerAdapter}: override only the callbacks
 * you need.
 *
 * <pre>{@code
 * public final class PingListener extends ListenerAdapter {
 *     @Override
 *     public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
 *         event.reply("Pong!").queue();
 *     }
 * }
 * }</pre>
 *
 * <p>Callbacks run on the session's single event thread, one event at a time, so a blocking listener delays every later
 * event. Avoid blocking calls such as {@code RestAction.complete()} in a listener; prefer
 * {@code queue}. An exception thrown by a callback is reported to the error handler and does not stop the other
 * listeners.
 */
public abstract class ListenerAdapter implements EventListener {
    /**
     * Routes the event to the typed callbacks. {@link #onGenericEvent} runs first, then the most specific callbacks.
     *
     * @param event the event
     */
    @Override
    public void onEvent(GenericEvent event) {
        onGenericEvent(event);
        if (event instanceof net.guneyilmaz0.micup4j.events.session.ReadyEvent ready) onReady(ready);
        else if (event instanceof ReadyEvent ready) onReady(ready);
        else if (event instanceof GenericMessageEvent message) {
            onGenericMessage(message);
            if (message instanceof net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent received) onMessageReceived(received);
            else if (message instanceof MessageReceivedEvent received) onMessageReceived(received);
            else if (message instanceof MessageUpdateEvent updated) onMessageUpdate(updated);
            else if (message instanceof MessageDeleteEvent deleted) onMessageDelete(deleted);
        }
        else if (event instanceof net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent slash) {
            onInteractionCreate(slash);
            onSlashCommandInteraction(slash);
        }
        else if (event instanceof net.guneyilmaz0.micup4j.events.interaction.command.CommandAutoCompleteInteractionEvent autocomplete) {
            onInteractionCreate(autocomplete);
            onCommandAutoCompleteInteraction(autocomplete);
        }
        else if (event instanceof InteractionCreateEvent interaction) onInteractionCreate(interaction);
        else if (event instanceof net.guneyilmaz0.micup4j.events.guild.GenericGuildEvent guild) {
            onGenericGuild(guild);
            if (guild instanceof GenericGuildMemberEvent member) onGenericGuildMember(member);
            if (guild instanceof GuildMemberJoinEvent joined) onGuildMemberJoin(joined);
            else if (guild instanceof GuildMemberRemoveEvent removed) onGuildMemberRemove(removed);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildReadyEvent ready) onGuildReady(ready);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildJoinEvent joined) onGuildJoin(joined);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildAvailableEvent available) onGuildAvailable(available);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildUnavailableEvent unavailable) onGuildUnavailable(unavailable);
            else if (guild instanceof net.guneyilmaz0.micup4j.events.guild.GuildLeaveEvent left) onGuildLeave(left);
        }
        else if (event instanceof StatusChangeEvent status) onStatusChange(status);
        else if (event instanceof ShutdownEvent shutdown) onShutdown(shutdown);
        else if (event instanceof ReconnectEvent reconnect) onReconnect(reconnect);
    }

    /**
     * Called when the session is ready. By default delegates to {@link #onReady(ReadyEvent)}.
     *
     * @param event the ready event
     */
    public void onReady(net.guneyilmaz0.micup4j.events.session.ReadyEvent event) { onReady((ReadyEvent) event); }
    /**
     * Called when a message was received ({@code MESSAGE_CREATE}). By default delegates to {@link #onMessageReceived(MessageReceivedEvent)}.
     *
     * @param event the message event
     */
    public void onMessageReceived(net.guneyilmaz0.micup4j.events.message.MessageReceivedEvent event) { onMessageReceived((MessageReceivedEvent) event); }
    /**
     * Called when a slash command was invoked. {@link #onInteractionCreate} runs first. Answer within 2.5 seconds, for example with {@code event.reply(...)} or {@code event.deferReply()}.
     *
     * @param event the command event
     */
    public void onSlashCommandInteraction(net.guneyilmaz0.micup4j.events.interaction.command.SlashCommandInteractionEvent event) {}

    /**
     * Called when a command option asks for autocomplete suggestions. {@link #onInteractionCreate} runs first. Answer with one of the {@code replyChoice*} methods.
     *
     * @param event the autocomplete event
     */
    public void onCommandAutoCompleteInteraction(net.guneyilmaz0.micup4j.events.interaction.command.CommandAutoCompleteInteractionEvent event) {}

    /**
     * Called for every message event before its specific callback, including received, updated and deleted messages.
     *
     * @param event the message event
     */
    public void onGenericMessage(GenericMessageEvent event) {}

    /**
     * Called when a message was edited ({@code MESSAGE_UPDATE}).
     *
     * @param event the update event carrying the complete current message
     */
    public void onMessageUpdate(MessageUpdateEvent event) {}

    /**
     * Called when a message was deleted ({@code MESSAGE_DELETE}).
     *
     * @param event the delete event
     */
    public void onMessageDelete(MessageDeleteEvent event) {}

    /**
     * Called after {@link #onGenericGuild} for member events that carry a member, such as joins.
     *
     * @param event the member event
     */
    public void onGenericGuildMember(GenericGuildMemberEvent event) {}

    /**
     * Called when a member joined a guild ({@code GUILD_MEMBER_ADD}).
     *
     * @param event the join event
     */
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {}

    /**
     * Called when a member left or was removed from a guild ({@code GUILD_MEMBER_REMOVE}).
     *
     * @param event the remove event
     */
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {}

    /**
     * Called for every guild event before its specific callback.
     *
     * @param event the guild event
     */
    public void onGenericGuild(net.guneyilmaz0.micup4j.events.guild.GenericGuildEvent event) {}
    /**
     * Called when a guild of the initial {@code READY} payload became available to the session.
     *
     * @param event the guild event
     */
    public void onGuildReady(net.guneyilmaz0.micup4j.events.guild.GuildReadyEvent event) {}
    /**
     * Called when the bot joined a guild.
     *
     * @param event the guild event
     */
    public void onGuildJoin(net.guneyilmaz0.micup4j.events.guild.GuildJoinEvent event) {}
    /**
     * Called when a guild became available again after an outage.
     *
     * @param event the guild event
     */
    public void onGuildAvailable(net.guneyilmaz0.micup4j.events.guild.GuildAvailableEvent event) {}
    /**
     * Called when a guild became unavailable because of an outage.
     *
     * @param event the guild event
     */
    public void onGuildUnavailable(net.guneyilmaz0.micup4j.events.guild.GuildUnavailableEvent event) {}
    /**
     * Called when the bot left or was removed from a guild.
     *
     * @param event the guild event
     */
    public void onGuildLeave(net.guneyilmaz0.micup4j.events.guild.GuildLeaveEvent event) {}

    /**
     * Called for every event before any other callback, including raw {@link GenericEvent} dispatches that have no typed class.
     *
     * @param event the event
     */
    public void onGenericEvent(GenericEvent event) {
    }

    /**
     * Called when the session status changed.
     *
     * @param event the status change
     */
    public void onStatusChange(StatusChangeEvent event) {}

    /**
     * Called once after the session shut down; the last callback a listener receives.
     *
     * @param event the shutdown event
     */
    public void onShutdown(ShutdownEvent event) {}

    /**
     * Called when a Gateway reconnect was scheduled.
     *
     * @param event the reconnect event
     */
    public void onReconnect(ReconnectEvent event) {}

    /**
     * Called when the session is ready.
     *
     * @param event the ready event
     */
    public void onReady(ReadyEvent event) {
    }

    /**
     * Called when a message was received ({@code MESSAGE_CREATE}).
     *
     * @param event the message event
     */
    public void onMessageReceived(MessageReceivedEvent event) {
    }

    /**
     * Called for every interaction, before the typed interaction callbacks.
     *
     * @param event the interaction event
     */
    public void onInteractionCreate(InteractionCreateEvent event) {
    }
}
