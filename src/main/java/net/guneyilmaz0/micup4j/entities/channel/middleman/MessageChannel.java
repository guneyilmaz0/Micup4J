package net.guneyilmaz0.micup4j.entities.channel.middleman;

import net.guneyilmaz0.micup4j.entities.*;
import net.guneyilmaz0.micup4j.entities.channel.ChannelType;
import net.guneyilmaz0.micup4j.requests.RestAction;

/** Supported source-compatible subset of a JDA message channel. */
public interface MessageChannel extends ISnowflake {
    /**
     * Returns the channel mention, {@code <#id>}.
     *
     * @return the channel mention, {@code <#id>}
     */
    String getAsMention();
    /**
     * Returns the channel type, or {@code UNKNOWN} when not known.
     *
     * @return the channel type, or {@code UNKNOWN} when not known
     */
    ChannelType getType();
    /**
     * Sends a text message.
     *
     * @param content non-blank content
     * @return the action; add embeds, components or files before queueing
     */
    net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction sendMessage(String content);
    /**
     * Sends embeds.
     *
     * @param embeds 1-10 embeds
     * @return the action
     */
    net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction sendMessageEmbeds(MessageEmbed... embeds);
    /**
     * Sends files as a new message.
     *
     * @param files 1-10 files
     * @return the action
     */
    net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction sendFiles(net.guneyilmaz0.micup4j.utils.FileUpload... files);
    /**
     * Sends files as a new message.
     *
     * @param files 1-10 files
     * @return the action
     */
    net.guneyilmaz0.micup4j.requests.restaction.MessageCreateAction sendFiles(java.util.Collection<? extends net.guneyilmaz0.micup4j.utils.FileUpload> files);
    /**
     * Retrieves a message over REST.
     *
     * @param id the message id
     * @return the action
     */
    RestAction<Message> retrieveMessageById(String id);
    /**
     * Retrieves a message over REST.
     *
     * @param id the message id as an unsigned 64-bit value
     * @return the action
     */
    default RestAction<Message> retrieveMessageById(long id) { return retrieveMessageById(Long.toUnsignedString(id)); }
}
